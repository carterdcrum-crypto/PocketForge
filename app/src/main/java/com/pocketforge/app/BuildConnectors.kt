package com.pocketforge.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ConnectorResult(val provider: String, val message: String)

object BuildConnectors {
    fun testGitHub(repo: String, token: String): ConnectorResult {
        require(repo.contains('/')) { "Use owner/repository format." }
        request(
            "https://api.github.com/repos/$repo/actions/workflows",
            "GET",
            null,
            githubHeaders(token)
        )
        return ConnectorResult("GitHub Actions", "Connected to $repo")
    }

    fun triggerGitHub(repo: String, token: String, workflow: String, branch: String = "main"): ConnectorResult {
        require(repo.contains('/')) { "Use owner/repository format." }
        val workflowId = URLEncoder.encode(workflow.ifBlank { "android.yml" }, Charsets.UTF_8.name())
        val body = JSONObject().put("ref", branch)
        request(
            "https://api.github.com/repos/$repo/actions/workflows/$workflowId/dispatches",
            "POST",
            body,
            githubHeaders(token),
            acceptedCodes = setOf(204)
        )
        return ConnectorResult("GitHub Actions", "Build dispatched on $branch")
    }

    fun testCodemagic(appId: String, token: String): ConnectorResult {
        require(appId.isNotBlank()) { "Codemagic App ID is required." }
        request(
            "https://api.codemagic.io/apps/${url(appId)}",
            "GET",
            null,
            mapOf("x-auth-token" to token)
        )
        return ConnectorResult("Codemagic", "Connected to app $appId")
    }

    fun triggerCodemagic(appId: String, workflowId: String, token: String, branch: String = "main"): ConnectorResult {
        val body = JSONObject()
            .put("appId", appId)
            .put("workflowId", workflowId.ifBlank { "android" })
            .put("branch", branch)
        val response = request(
            "https://api.codemagic.io/builds",
            "POST",
            body,
            mapOf("x-auth-token" to token)
        )
        val id = runCatching { JSONObject(response).optString("_id") }.getOrDefault("")
        return ConnectorResult("Codemagic", if (id.isBlank()) "Build queued" else "Build queued · $id")
    }

    fun testBitrise(appSlug: String, token: String): ConnectorResult {
        require(appSlug.isNotBlank()) { "Bitrise app slug is required." }
        request(
            "https://api.bitrise.io/v0.1/apps/${url(appSlug)}",
            "GET",
            null,
            mapOf("Authorization" to token)
        )
        return ConnectorResult("Bitrise", "Connected to app $appSlug")
    }

    fun triggerBitrise(appSlug: String, workflowId: String, token: String, branch: String = "main"): ConnectorResult {
        val body = JSONObject()
            .put("hook_info", JSONObject().put("type", "bitrise"))
            .put("build_params", JSONObject()
                .put("branch", branch)
                .put("workflow_id", workflowId.ifBlank { "primary" }))
        val response = request(
            "https://api.bitrise.io/v0.1/apps/${url(appSlug)}/builds",
            "POST",
            body,
            mapOf("Authorization" to token)
        )
        val slug = runCatching {
            JSONObject(response).optJSONObject("build_triggered")?.optString("build_slug")
        }.getOrNull().orEmpty()
        return ConnectorResult("Bitrise", if (slug.isBlank()) "Build queued" else "Build queued · $slug")
    }

    /**
     * Cost-aware primary path: use the first configured connector that accepts the build.
     * GitHub first because the current PocketForge repository already builds there.
     */
    fun triggerBestAvailable(vault: SecretVault, branch: String = "main"): ConnectorResult {
        val failures = mutableListOf<String>()

        val repo = vault.get(IntegrationKeys.GITHUB_REPO)
        val githubToken = vault.get(IntegrationKeys.GITHUB_TOKEN)
        if (repo.isNotBlank() && githubToken.isNotBlank()) {
            runCatching {
                return triggerGitHub(repo, githubToken, vault.get(IntegrationKeys.GITHUB_WORKFLOW), branch)
            }.onFailure { failures += "GitHub: ${it.message}" }
        }

        val cmToken = vault.get(IntegrationKeys.CODEMAGIC_TOKEN)
        val cmApp = vault.get(IntegrationKeys.CODEMAGIC_APP_ID)
        if (cmToken.isNotBlank() && cmApp.isNotBlank()) {
            runCatching {
                return triggerCodemagic(cmApp, vault.get(IntegrationKeys.CODEMAGIC_WORKFLOW), cmToken, branch)
            }.onFailure { failures += "Codemagic: ${it.message}" }
        }

        val bitriseToken = vault.get(IntegrationKeys.BITRISE_TOKEN)
        val bitriseApp = vault.get(IntegrationKeys.BITRISE_APP_SLUG)
        if (bitriseToken.isNotBlank() && bitriseApp.isNotBlank()) {
            runCatching {
                return triggerBitrise(bitriseApp, vault.get(IntegrationKeys.BITRISE_WORKFLOW), bitriseToken, branch)
            }.onFailure { failures += "Bitrise: ${it.message}" }
        }

        if (failures.isEmpty()) error("Connect a build provider first.")
        error("No build provider accepted the job. ${failures.joinToString(" | ")}")
    }

    /** Cross-check mode intentionally triggers every configured provider. */
    fun triggerAllConfigured(vault: SecretVault, branch: String = "main"): List<ConnectorResult> {
        val results = mutableListOf<ConnectorResult>()
        val errors = mutableListOf<String>()

        fun attempt(label: String, block: () -> ConnectorResult) {
            runCatching(block).onSuccess(results::add).onFailure { errors += "$label: ${it.message}" }
        }

        val repo = vault.get(IntegrationKeys.GITHUB_REPO)
        val gh = vault.get(IntegrationKeys.GITHUB_TOKEN)
        if (repo.isNotBlank() && gh.isNotBlank()) attempt("GitHub") {
            triggerGitHub(repo, gh, vault.get(IntegrationKeys.GITHUB_WORKFLOW), branch)
        }

        val cm = vault.get(IntegrationKeys.CODEMAGIC_TOKEN)
        val cmApp = vault.get(IntegrationKeys.CODEMAGIC_APP_ID)
        if (cm.isNotBlank() && cmApp.isNotBlank()) attempt("Codemagic") {
            triggerCodemagic(cmApp, vault.get(IntegrationKeys.CODEMAGIC_WORKFLOW), cm, branch)
        }

        val br = vault.get(IntegrationKeys.BITRISE_TOKEN)
        val brApp = vault.get(IntegrationKeys.BITRISE_APP_SLUG)
        if (br.isNotBlank() && brApp.isNotBlank()) attempt("Bitrise") {
            triggerBitrise(brApp, vault.get(IntegrationKeys.BITRISE_WORKFLOW), br, branch)
        }

        if (results.isEmpty()) {
            if (errors.isNotEmpty()) error(errors.joinToString(" | "))
            error("No build connectors are configured.")
        }
        return results
    }

    private fun githubHeaders(token: String) = mapOf(
        "Authorization" to "Bearer $token",
        "Accept" to "application/vnd.github+json",
        "X-GitHub-Api-Version" to "2022-11-28"
    )

    private fun url(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun request(
        endpoint: String,
        method: String,
        body: JSONObject?,
        headers: Map<String, String>,
        acceptedCodes: Set<Int> = (200..299).toSet()
    ): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PocketForge-Android")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return try {
            if (body != null) connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in acceptedCodes) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in acceptedCodes) {
                val message = runCatching {
                    val json = JSONObject(response)
                    json.optString("message").ifBlank {
                        json.optJSONObject("error")?.optString("message").orEmpty()
                    }
                }.getOrDefault("")
                error(message.ifBlank { "HTTP $code" })
            }
            response
        } finally {
            connection.disconnect()
        }
    }
}
