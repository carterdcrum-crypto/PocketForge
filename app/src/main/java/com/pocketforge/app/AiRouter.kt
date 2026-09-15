package com.pocketforge.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AiPlan(
    val provider: String,
    val summary: String,
    val scope: String,
    val protected: String,
    val verify: String,
    val implementationNotes: String
)

object AiRouter {
    private const val SYSTEM_PROMPT = """
You are PocketForge's software architect. Turn the user's plain-English app request into a safe implementation contract for an Android app.
Return JSON only with these string keys: summary, scope, protected, verify, implementationNotes.
Be concise. Protect unrelated working behavior. Verification must include compiling and testing.
"""

    fun planBest(prompt: String, vault: SecretVault): AiPlan {
        val failures = mutableListOf<String>()

        val gemini = vault.get(IntegrationKeys.GEMINI)
        if (gemini.isNotBlank()) {
            runCatching { return normalize("Gemini", callGemini(gemini, prompt)) }
                .onFailure { failures += "Gemini: ${it.message}" }
        }

        val openRouter = vault.get(IntegrationKeys.OPENROUTER)
        if (openRouter.isNotBlank()) {
            runCatching { return normalize("OpenRouter Free", callOpenRouter(openRouter, prompt)) }
                .onFailure { failures += "OpenRouter: ${it.message}" }
        }

        val groq = vault.get(IntegrationKeys.GROQ)
        if (groq.isNotBlank()) {
            runCatching { return normalize("Groq", callGroq(groq, prompt)) }
                .onFailure { failures += "Groq: ${it.message}" }
        }

        if (gemini.isBlank() && openRouter.isBlank() && groq.isBlank()) {
            error("Connect at least one AI provider in Integration Center.")
        }
        error("Every configured AI provider failed. ${failures.joinToString(" | ")}")
    }

    fun test(provider: String, key: String): String = when (provider) {
        "openrouter" -> extractText(callOpenRouter(key, "Reply with exactly: PocketForge OpenRouter connected"))
        "gemini" -> extractText(callGemini(key, "Reply with exactly: PocketForge Gemini connected"))
        "groq" -> extractText(callGroq(key, "Reply with exactly: PocketForge Groq connected"))
        else -> error("Unknown provider")
    }

    private fun callOpenRouter(key: String, userPrompt: String): String {
        val body = JSONObject()
            .put("model", "openrouter/free")
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT.trim()))
                .put(JSONObject().put("role", "user").put("content", userPrompt)))
            .put("temperature", 0.2)
        val result = requestJson(
            "https://openrouter.ai/api/v1/chat/completions",
            body,
            mapOf(
                "Authorization" to "Bearer $key",
                "HTTP-Referer" to "https://github.com/carterdcrum-crypto/PocketForge",
                "X-Title" to "PocketForge"
            )
        )
        return JSONObject(result)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content")
    }

    private fun callGemini(key: String, userPrompt: String): String {
        val encoded = URLEncoder.encode(key, Charsets.UTF_8.name())
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT.trim()))))
            .put("contents", JSONArray().put(JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.2)
                .put("responseMimeType", "application/json"))
        val result = requestJson(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=$encoded",
            body,
            emptyMap()
        )
        return JSONObject(result)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .optString("text")
    }

    private fun callGroq(key: String, userPrompt: String): String {
        val body = JSONObject()
            .put("model", "openai/gpt-oss-120b")
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT.trim()))
                .put(JSONObject().put("role", "user").put("content", userPrompt)))
            .put("temperature", 0.2)
        val result = requestJson(
            "https://api.groq.com/openai/v1/chat/completions",
            body,
            mapOf("Authorization" to "Bearer $key")
        )
        return JSONObject(result)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content")
    }

    private fun normalize(provider: String, raw: String): AiPlan {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val json = runCatching { JSONObject(cleaned) }.getOrNull()
        return AiPlan(
            provider = provider,
            summary = json?.optString("summary")?.takeIf { it.isNotBlank() } ?: extractText(raw),
            scope = json?.optString("scope")?.takeIf { it.isNotBlank() } ?: "Requested app/change only",
            protected = json?.optString("protected")?.takeIf { it.isNotBlank() } ?: "Unrelated working screens, data, and behavior",
            verify = json?.optString("verify")?.takeIf { it.isNotBlank() } ?: "Compile, run tests, and reject a red build",
            implementationNotes = json?.optString("implementationNotes")?.takeIf { it.isNotBlank() } ?: "Use the existing architecture and make the smallest safe change."
        )
    }

    private fun extractText(raw: String): String = raw.trim().ifBlank { "Provider returned an empty response." }

    private fun requestJson(url: String, body: JSONObject, headers: Map<String, String>): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PocketForge-Android")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        return try {
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(response).optJSONObject("error")?.optString("message") }.getOrNull()
                error(message?.takeIf { it.isNotBlank() } ?: "HTTP $code")
            }
            response
        } finally {
            connection.disconnect()
        }
    }
}
