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

data class RoutedAiResponse(val provider: String, val text: String)

enum class FreeAiProvider(val label: String) {
    GEMINI("Gemini 3.8 Flash"),
    OPENROUTER("OpenRouter Free"),
    GROQ("Groq GPT-OSS 120B")
}

enum class AiJobComplexity(val label: String, val thinkingLevel: String) {
    QUICK("Quick", "low"),
    STANDARD("Standard", "medium"),
    DEEP("Deep", "high")
}

object AiRouter {
    private const val PLAN_SYSTEM = """
You are PocketForge's software architect. Turn the user's plain-English app request into a safe implementation contract for an Android app.
Return JSON only with these string keys: summary, scope, protected, verify, implementationNotes.
Be concise. Protect unrelated working behavior. Verification must include compiling and testing.
"""

    private const val REVIEW_SYSTEM = """
You are PocketForge's independent senior reviewer. Inspect the user's request and another architect's proposed implementation contract.
Find missing requirements, risky assumptions, regressions, security/privacy issues, build risks, and opportunities to make the product more useful without broadening scope recklessly.
Return concise plain text. Do not praise the proposal. Focus on concrete corrections.
"""

    private const val STUDIO_SYSTEM = """
You are PocketForge's no-code app designer. Turn the user's plain-English idea into a small, useful, offline-first app design.
Return JSON only with this exact shape: {"schemaVersion":1,"name":"...","tagline":"...","accent":"#RRGGBB","dark":true,"screens":[{"id":"lowercase_id","title":"Short title","type":"list|checklist|ledger|calculator|info","description":"...","fields":[{"id":"lowercase_id","label":"...","type":"text|number|date|multiline","required":true}],"operation":"sum|product|difference|ratio","unit":"$","content":"..."}]}
Use 1–4 screens and keep it practical for a beginner. Use a ledger for money entries, checklist for tasks, calculator only with number fields, list for records, and info for instructions. No accounts, network services, fake data, or unsupported features. Keep names and labels short.
"""

    private val deepSignals = listOf(
        "repo", "repository", "architecture", "refactor", "migration", "gradle",
        "build fail", "compiler", "multiple files", "end-to-end", "backend", "database",
        "authentication", "auth", "github", "release", "deploy", "security", "crash",
        "dependency", "api integration", "workflow", "ci", "entire app", "whole app"
    )

    private val quickSignals = listOf(
        "rename", "change text", "copy", "color", "padding", "spacing", "typo",
        "label", "icon", "wording", "font size"
    )

    fun classify(prompt: String): AiJobComplexity {
        val normalized = prompt.lowercase()
        val deepHits = deepSignals.count { signal -> normalized.contains(signal) }
        return when {
            prompt.length > 900 || deepHits >= 2 -> AiJobComplexity.DEEP
            prompt.length < 220 && deepHits == 0 && quickSignals.any { signal -> normalized.contains(signal) } -> AiJobComplexity.QUICK
            else -> AiJobComplexity.STANDARD
        }
    }

    fun configuredProviders(vault: SecretVault): List<FreeAiProvider> = buildList {
        if (vault.has(IntegrationKeys.GEMINI)) add(FreeAiProvider.GEMINI)
        if (vault.has(IntegrationKeys.OPENROUTER)) add(FreeAiProvider.OPENROUTER)
        if (vault.has(IntegrationKeys.GROQ)) add(FreeAiProvider.GROQ)
    }

    fun planBest(prompt: String, vault: SecretVault): AiPlan {
        val routed = askBest(PLAN_SYSTEM.trim(), prompt, vault, expectJson = true)
        return normalize(routed.provider, routed.text)
    }

    fun generateStudioSpec(prompt: String, vault: SecretVault): StudioSpec {
        val routed = askBest(STUDIO_SYSTEM.trim(), prompt, vault, expectJson = true)
        return StudioSpec.parse(routed.text)
    }

    /**
     * Multi-agent planning mode. Two providers independently reason about the request;
     * a third (or the first when only two are configured) synthesizes the final contract.
     * With one configured provider this safely falls back to the normal planner.
     */
    fun planConsensus(prompt: String, vault: SecretVault): AiPlan {
        val providers = configuredProviders(vault)
        if (providers.size < 2) return planBest(prompt, vault)

        val architect = providers[0]
        val reviewer = providers[1]
        val architectRaw = ask(architect, PLAN_SYSTEM.trim(), prompt, vault, expectJson = true)
        val firstPlan = normalize(architect.label, architectRaw)

        val reviewPrompt = buildString {
            appendLine("USER REQUEST:")
            appendLine(prompt)
            appendLine()
            appendLine("ARCHITECT CONTRACT:")
            appendLine("Summary: ${firstPlan.summary}")
            appendLine("Scope: ${firstPlan.scope}")
            appendLine("Protected: ${firstPlan.protected}")
            appendLine("Verify: ${firstPlan.verify}")
            appendLine("Implementation notes: ${firstPlan.implementationNotes}")
        }
        val critique = ask(reviewer, REVIEW_SYSTEM.trim(), reviewPrompt, vault)

        val synthesizer = providers.getOrElse(2) { architect }
        val synthesisPrompt = buildString {
            appendLine("Create the final safe Android implementation contract.")
            appendLine("Return JSON only with keys summary, scope, protected, verify, implementationNotes.")
            appendLine()
            appendLine("USER REQUEST:")
            appendLine(prompt)
            appendLine()
            appendLine("FIRST CONTRACT:")
            appendLine(architectRaw)
            appendLine()
            appendLine("INDEPENDENT REVIEW:")
            appendLine(critique)
            appendLine()
            appendLine("Resolve conflicts conservatively. Keep requested functionality, protect working behavior, and make verification executable.")
        }
        val finalRaw = ask(
            synthesizer,
            "You are PocketForge's lead integrator. Reconcile an architect plan and independent code-review critique into one implementation contract.",
            synthesisPrompt,
            vault,
            expectJson = true
        )
        val finalPlan = normalize(synthesizer.label, finalRaw)
        val label = "Consensus · ${architect.label} + ${reviewer.label} → ${synthesizer.label}"
        return finalPlan.copy(provider = label)
    }

    fun askBest(
        systemPrompt: String,
        userPrompt: String,
        vault: SecretVault,
        exclude: Set<FreeAiProvider> = emptySet(),
        expectJson: Boolean = false
    ): RoutedAiResponse {
        val failures = mutableListOf<String>()
        val order = configuredProviders(vault).filterNot(exclude::contains)
        if (order.isEmpty()) error("Connect at least one AI provider in Integration Center.")

        for (provider in order) {
            runCatching {
                return RoutedAiResponse(provider.label, ask(provider, systemPrompt, userPrompt, vault, expectJson))
            }.onFailure { failures += "${provider.label}: ${it.message}" }
        }
        error("Every eligible AI provider failed. ${failures.joinToString(" | ")}")
    }

    fun ask(
        provider: FreeAiProvider,
        systemPrompt: String,
        userPrompt: String,
        vault: SecretVault,
        expectJson: Boolean = false
    ): String = when (provider) {
        FreeAiProvider.GEMINI -> callGemini(
            vault.get(IntegrationKeys.GEMINI),
            systemPrompt,
            userPrompt,
            expectJson,
            classify(userPrompt).thinkingLevel
        )
        FreeAiProvider.OPENROUTER -> callOpenRouter(vault.get(IntegrationKeys.OPENROUTER), systemPrompt, userPrompt)
        FreeAiProvider.GROQ -> callGroq(vault.get(IntegrationKeys.GROQ), systemPrompt, userPrompt)
    }

    fun test(provider: String, key: String): String = when (provider) {
        "openrouter" -> callOpenRouter(key, "You are testing an API connection.", "Reply with exactly: PocketForge OpenRouter connected").trim()
        "gemini" -> callGemini(
            key,
            "You are testing an API connection.",
            "Reply with exactly: PocketForge Gemini connected",
            false,
            AiJobComplexity.QUICK.thinkingLevel
        ).trim()
        "groq" -> callGroq(key, "You are testing an API connection.", "Reply with exactly: PocketForge Groq connected").trim()
        else -> error("Unknown provider")
    }

    private fun callOpenRouter(key: String, systemPrompt: String, userPrompt: String): String {
        require(key.isNotBlank()) { "OpenRouter key is missing." }
        val body = JSONObject()
            .put("model", "openrouter/free")
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
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

    private fun callGemini(
        key: String,
        systemPrompt: String,
        userPrompt: String,
        expectJson: Boolean,
        thinkingLevel: String
    ): String {
        require(key.isNotBlank()) { "Gemini key is missing." }
        val encoded = URLEncoder.encode(key, Charsets.UTF_8.name())
        val generationConfig = JSONObject()
            .put("thinkingConfig", JSONObject().put("thinkingLevel", thinkingLevel))
        if (expectJson) generationConfig.put("responseMimeType", "application/json")

        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("contents", JSONArray().put(JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))))
            .put("generationConfig", generationConfig)
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

    private fun callGroq(key: String, systemPrompt: String, userPrompt: String): String {
        require(key.isNotBlank()) { "Groq key is missing." }
        val body = JSONObject()
            .put("model", "openai/gpt-oss-120b")
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
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
            summary = json?.optString("summary")?.takeIf { it.isNotBlank() } ?: raw.trim(),
            scope = json?.optString("scope")?.takeIf { it.isNotBlank() } ?: "Requested app/change only",
            protected = json?.optString("protected")?.takeIf { it.isNotBlank() } ?: "Unrelated working screens, data, and behavior",
            verify = json?.optString("verify")?.takeIf { it.isNotBlank() } ?: "Compile, run tests, and reject a red build",
            implementationNotes = json?.optString("implementationNotes")?.takeIf { it.isNotBlank() } ?: "Use the existing architecture and make the smallest safe change."
        )
    }

    private fun requestJson(url: String, body: JSONObject, headers: Map<String, String>): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
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
                val message = runCatching {
                    val error = JSONObject(response).opt("error")
                    when (error) {
                        is JSONObject -> error.optString("message")
                        is String -> error
                        else -> JSONObject(response).optString("message")
                    }
                }.getOrDefault("")
                error(message.takeIf { it.isNotBlank() } ?: "HTTP $code")
            }
            response
        } finally {
            connection.disconnect()
        }
    }
}
