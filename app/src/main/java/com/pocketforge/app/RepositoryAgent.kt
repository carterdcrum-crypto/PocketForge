package com.pocketforge.app

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.zip.ZipInputStream

data class RepoExecutionResult(
    val repository: String,
    val branch: String,
    val changedFiles: List<String>,
    val model: String,
    val buildConclusion: String,
    val verified: Boolean,
    val repairAttempts: Int
)

private data class RepoFile(
    val path: String,
    val sha: String,
    val content: String
)

private data class TreeEntry(
    val path: String,
    val size: Long
)

private data class ProposedChange(
    val path: String,
    val action: String,
    val content: String,
    val reason: String
)

private data class BuildRun(
    val id: Long,
    val status: String,
    val conclusion: String,
    val url: String
)

/**
 * Branch-isolated repository execution agent.
 *
 * The important safety property is structural, not prompt-based: this class never writes
 * to the configured default branch. Every run gets a new PocketForge branch. A verified
 * build is required before the result can be described as green, and this beta deliberately
 * does not auto-merge.
 */
object RepositoryAgent {
    private const val MAX_TREE_PATHS = 550
    private const val MAX_FILES_TO_READ = 10
    private const val MAX_CHANGES = 8
    private const val MAX_FILE_CHARS = 120_000
    private const val MAX_CONTEXT_CHARS = 260_000
    private const val MAX_LOG_CHARS = 120_000
    private const val MAX_REPAIRS = 2

    private const val SCOUT_SYSTEM = """
You are PocketForge's repository scout. Choose the smallest set of existing text files that must be read before implementing the user's Android change safely.
Return JSON only: {"filesToRead":["path/a","path/b"]}.
Choose at most 10 files. Prefer source files directly related to the request plus the relevant Gradle/manifest files. Do not choose generated build output, secrets, signing material, binaries, or caches.
"""

    private const val EDIT_SYSTEM = """
You are PocketForge's implementation agent. You are editing a protected branch, never the green branch.
Return JSON only with this schema:
{"summary":"short summary","changes":[{"path":"repo/path","action":"update|create","content":"COMPLETE FILE CONTENT","reason":"why"}]}
Rules:
- Make the smallest complete change that satisfies the execution contract.
- For updates, return the COMPLETE replacement file, not a diff and not an excerpt.
- You may update only files PocketForge supplied in REPOSITORY FILES.
- You may create a new text source/config file only when necessary.
- Never output secrets, API keys, local.properties, signing keys, keystores, generated build directories, or binary files.
- Do not modify CI/workflow files unless the user's requested scope explicitly requires CI/build workflow changes.
- Preserve unrelated working behavior.
- Keep the change set at 8 files or fewer.
"""

    private const val REPAIR_SYSTEM = """
You are PocketForge's build repair agent. A real Android CI run failed after a bounded repository change.
Return JSON only using the same change schema as the implementation agent.
Use the build log as authority. Make the smallest repair needed to get tests, lint, compile, APK assembly, and signature verification green.
Do not broaden product scope. Do not hide or delete tests to make the build pass. Do not weaken lint or verification gates. Do not touch secrets or signing material.
"""

    fun execute(
        goal: String,
        plan: AiPlan,
        vault: SecretVault,
        emit: (AgentStep) -> Unit
    ): RepoExecutionResult {
        val repo = vault.get(IntegrationKeys.GITHUB_REPO).trim()
        val token = vault.get(IntegrationKeys.GITHUB_TOKEN).trim()
        val workflow = vault.get(IntegrationKeys.GITHUB_WORKFLOW).ifBlank { "android.yml" }
        require(repo.count { it == '/' } == 1) { "Connect a GitHub repository in Integration Center using owner/repository format." }
        require(token.isNotBlank()) { "A GitHub token with repository contents and Actions access is required for Autopilot." }

        val client = GitHubRepoClient(repo, token)
        emit(AgentStep("repo-inspect", "Repository Agent", "Inspecting the bound repository", "Reading repository metadata and the current green/default branch without changing it.", AgentStepState.WORKING, "GitHub"))
        val defaultBranch = client.defaultBranch()
        val baseCommit = client.branchCommitSha(defaultBranch)
        val baseTree = client.commitTreeSha(baseCommit)
        val branch = "pocketforge/run-${System.currentTimeMillis()}"
        client.createBranch(branch, baseCommit)
        emit(AgentStep("repo-inspect", "Repository Agent", "Protected checkpoint created", "$branch from $defaultBranch @ ${baseCommit.take(8)}. The default branch will not be edited.", AgentStepState.DONE, "GitHub"))

        var tree = client.textTree(baseTree)
        emit(AgentStep("repo-scout", "Scout", "Selecting the files that actually matter", "Giving the AI file paths first instead of dumping the whole repository into a prompt.", AgentStepState.WORKING, "AI router"))
        var selectedPaths = selectFiles(goal, plan, tree, vault)
        var snapshots = readSelectedFiles(client, branch, tree, selectedPaths)
        emit(AgentStep("repo-scout", "Scout", "Relevant project context loaded", snapshots.joinToString { it.path }.take(900), AgentStepState.DONE, "AI router"))

        emit(AgentStep("repo-edit", "Implementation Agent", "Writing a bounded change set", "The model can update only files it was shown and may create only safe text files.", AgentStepState.WORKING, "AI router"))
        val firstProposal = proposeChanges(goal, plan, snapshots, null, vault, repair = false)
        var changed = applyProposal(client, branch, snapshots, firstProposal.second)
        var usedModel = firstProposal.first
        emit(AgentStep("repo-edit", "Implementation Agent", "Protected branch updated", changed.joinToString().ifBlank { "No files changed" }, AgentStepState.DONE, usedModel))
        require(changed.isNotEmpty()) { "The implementation agent did not produce any safe file changes." }

        var repairs = 0
        var verified = false
        var conclusion = "not run"

        while (true) {
            val dispatchStarted = Instant.now().minusSeconds(3)
            emit(AgentStep("repo-build", "Verifier", if (repairs == 0) "Building the changed branch" else "Rebuilding after repair $repairs", "Dispatching $workflow on $branch. Tests, lint, compilation, APK assembly, and signature verification outrank the AI's opinion.", AgentStepState.WORKING, "GitHub Actions"))
            BuildConnectors.triggerGitHub(repo, token, workflow, branch)
            val run = client.waitForWorkflow(branch, dispatchStarted) { status ->
                emit(AgentStep("repo-build", "Verifier", "CI · ${status.status}", status.url.ifBlank { "Waiting for GitHub Actions" }, AgentStepState.WORKING, "GitHub Actions"))
            }

            if (run == null) {
                conclusion = "build dispatched; verification still running"
                emit(AgentStep("repo-build", "Verifier", "Build is still running", "The branch exists and CI was dispatched, but PocketForge did not receive a completed result inside the verification window. It will not call this green.", AgentStepState.DONE, "GitHub Actions"))
                break
            }

            conclusion = run.conclusion.ifBlank { run.status }
            if (run.conclusion.equals("success", ignoreCase = true)) {
                verified = true
                emit(AgentStep("repo-build", "Verifier", "Verified green APK build", run.url, AgentStepState.DONE, "GitHub Actions"))
                break
            }

            emit(AgentStep("repo-build", "Verifier", "Build failed", "GitHub conclusion: ${run.conclusion}. Pulling the real failing job logs before making another change.", AgentStepState.FAILED, "GitHub Actions"))
            if (repairs >= MAX_REPAIRS) break

            repairs += 1
            val logs = client.failedLogs(run.id).takeLast(MAX_LOG_CHARS)
            require(logs.isNotBlank()) { "CI failed, but PocketForge could not read the failing job logs for repair." }

            val headCommit = client.branchCommitSha(branch)
            tree = client.textTree(client.commitTreeSha(headCommit))
            selectedPaths = selectFiles("Repair this failed build for the original goal: $goal\n\nBUILD LOG:\n${logs.take(50_000)}", plan, tree, vault)
            val repairPaths = (selectedPaths + changed).distinct().take(MAX_FILES_TO_READ)
            snapshots = readSelectedFiles(client, branch, tree, repairPaths)

            emit(AgentStep("repo-repair", "Repair Agent", "Diagnosing the failed build", "Repair attempt $repairs/$MAX_REPAIRS is constrained to the build log and a small set of relevant files.", AgentStepState.WORKING, "AI router"))
            val repairProposal = proposeChanges(goal, plan, snapshots, logs, vault, repair = true)
            usedModel = repairProposal.first
            val repaired = applyProposal(client, branch, snapshots, repairProposal.second)
            require(repaired.isNotEmpty()) { "Repair agent could not identify a safe corrective file change." }
            changed = (changed + repaired).distinct()
            emit(AgentStep("repo-repair", "Repair Agent", "Repair applied", repaired.joinToString(), AgentStepState.DONE, usedModel))
        }

        return RepoExecutionResult(
            repository = repo,
            branch = branch,
            changedFiles = changed.distinct(),
            model = usedModel,
            buildConclusion = conclusion,
            verified = verified,
            repairAttempts = repairs
        )
    }

    private fun selectFiles(goal: String, plan: AiPlan, tree: List<TreeEntry>, vault: SecretVault): List<String> {
        val pathList = tree.take(MAX_TREE_PATHS).joinToString("\n") { it.path }
        val input = """
USER GOAL:
$goal

EXECUTION CONTRACT:
Summary: ${plan.summary}
Scope: ${plan.scope}
Protected: ${plan.protected}
Implementation notes: ${plan.implementationNotes}

AVAILABLE TEXT FILES:
$pathList
""".trim()
        val routed = AiRouter.askBest(SCOUT_SYSTEM.trim(), input, vault, expectJson = true)
        val json = parseJson(routed.text)
        val requested = json?.optJSONArray("filesToRead")
        val available = tree.associateBy { it.path }
        val selected = mutableListOf<String>()
        if (requested != null) {
            for (i in 0 until requested.length()) {
                val path = requested.optString(i).trim()
                if (path in available && isSafeTextPath(path) && path !in selected) selected += path
                if (selected.size >= MAX_FILES_TO_READ) break
            }
        }
        if (selected.isNotEmpty()) return selected

        val fallback = listOf(
            "settings.gradle.kts",
            "build.gradle.kts",
            "gradle.properties",
            "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml"
        )
        selected += fallback.filter { it in available }
        selected += tree.asSequence()
            .map { it.path }
            .filter { it.endsWith(".kt") || it.endsWith(".java") }
            .filterNot { it in selected }
            .take((MAX_FILES_TO_READ - selected.size).coerceAtLeast(0))
            .toList()
        return selected.distinct().take(MAX_FILES_TO_READ)
    }

    private fun readSelectedFiles(
        client: GitHubRepoClient,
        branch: String,
        tree: List<TreeEntry>,
        selected: List<String>
    ): List<RepoFile> {
        val sizes = tree.associate { it.path to it.size }
        var used = 0
        val files = mutableListOf<RepoFile>()
        for (path in selected) {
            if (!isSafeTextPath(path)) continue
            if ((sizes[path] ?: 0L) > MAX_FILE_CHARS) continue
            val file = runCatching { client.readFile(path, branch) }.getOrNull() ?: continue
            if (file.content.length > MAX_FILE_CHARS) continue
            if (used + file.content.length > MAX_CONTEXT_CHARS) break
            files += file
            used += file.content.length
        }
        require(files.isNotEmpty()) { "PocketForge could not load any safe repository files for this change." }
        return files
    }

    private fun proposeChanges(
        goal: String,
        plan: AiPlan,
        files: List<RepoFile>,
        buildLog: String?,
        vault: SecretVault,
        repair: Boolean
    ): Pair<String, List<ProposedChange>> {
        val repositoryFiles = buildString {
            files.forEach { file ->
                appendLine("\n===== FILE: ${file.path} =====")
                appendLine(file.content)
                appendLine("===== END FILE =====")
            }
        }
        val prompt = buildString {
            appendLine("USER GOAL:")
            appendLine(goal)
            appendLine()
            appendLine("EXECUTION CONTRACT:")
            appendLine("Summary: ${plan.summary}")
            appendLine("Scope: ${plan.scope}")
            appendLine("Protected: ${plan.protected}")
            appendLine("Verify: ${plan.verify}")
            appendLine("Implementation notes: ${plan.implementationNotes}")
            if (!buildLog.isNullOrBlank()) {
                appendLine()
                appendLine("REAL BUILD FAILURE LOG:")
                appendLine(buildLog.takeLast(MAX_LOG_CHARS))
            }
            appendLine()
            appendLine("REPOSITORY FILES:")
            append(repositoryFiles)
        }
        val routed = AiRouter.askBest(
            if (repair) REPAIR_SYSTEM.trim() else EDIT_SYSTEM.trim(),
            prompt,
            vault,
            expectJson = true
        )
        val json = parseJson(routed.text) ?: error("${routed.provider} returned an invalid structured change manifest.")
        val array = json.optJSONArray("changes") ?: JSONArray()
        val changes = mutableListOf<ProposedChange>()
        for (i in 0 until minOf(array.length(), MAX_CHANGES)) {
            val item = array.optJSONObject(i) ?: continue
            val path = item.optString("path").trim()
            val action = item.optString("action", "update").trim().lowercase()
            val content = item.optString("content")
            val reason = item.optString("reason")
            if (!isSafeTextPath(path)) continue
            if (action !in setOf("update", "create")) continue
            if (content.isBlank() || content.length > MAX_FILE_CHARS) continue
            changes += ProposedChange(path, action, content, reason)
        }
        return routed.provider to changes
    }

    private fun applyProposal(
        client: GitHubRepoClient,
        branch: String,
        suppliedFiles: List<RepoFile>,
        changes: List<ProposedChange>
    ): List<String> {
        val supplied = suppliedFiles.associateBy { it.path }
        val applied = mutableListOf<String>()
        var totalChars = 0

        changes.take(MAX_CHANGES).forEach { change ->
            totalChars += change.content.length
            require(totalChars <= MAX_CONTEXT_CHARS) { "Proposed edit set is too large for a safe beta run." }
            require(isSafeTextPath(change.path)) { "Unsafe path rejected: ${change.path}" }

            when (change.action) {
                "update" -> {
                    val existing = supplied[change.path]
                        ?: error("Update rejected because the model was not given the original file: ${change.path}")
                    client.writeFile(
                        path = change.path,
                        content = change.content,
                        branch = branch,
                        sha = existing.sha,
                        message = "PocketForge: ${change.reason.ifBlank { "update ${change.path}" }.take(120)}"
                    )
                    applied += change.path
                }
                "create" -> {
                    require(change.path !in supplied) { "Create rejected because ${change.path} already exists in the supplied context." }
                    client.writeFile(
                        path = change.path,
                        content = change.content,
                        branch = branch,
                        sha = null,
                        message = "PocketForge: ${change.reason.ifBlank { "create ${change.path}" }.take(120)}"
                    )
                    applied += change.path
                }
            }
        }
        return applied
    }

    private fun parseJson(raw: String): JSONObject? {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return runCatching { JSONObject(cleaned) }.getOrNull()
    }

    private fun isSafeTextPath(path: String): Boolean {
        val p = path.trim().replace('\\', '/')
        if (p.isBlank() || p.startsWith("/") || ".." in p.split('/')) return false
        val lower = p.lowercase()
        val blockedParts = listOf(
            "/build/", "/.gradle/", "/.idea/", "/node_modules/", "/.git/",
            "local.properties", ".env", "keystore", ".jks", ".p12", ".pem", ".key"
        )
        if (blockedParts.any { lower.contains(it) }) return false
        val allowed = listOf(
            ".kt", ".kts", ".java", ".xml", ".gradle", ".properties", ".toml",
            ".json", ".md", ".txt", ".yml", ".yaml", ".pro", ".cfg"
        )
        return allowed.any(lower::endsWith) || lower.endsWith("gradlew")
    }
}

private class GitHubRepoClient(
    private val repository: String,
    private val token: String
) {
    fun defaultBranch(): String = JSONObject(request("/repos/$repository", "GET")).optString("default_branch").ifBlank { "main" }

    fun branchCommitSha(branch: String): String {
        val response = JSONObject(request("/repos/$repository/branches/${segment(branch)}", "GET"))
        return response.getJSONObject("commit").getString("sha")
    }

    fun commitTreeSha(commitSha: String): String {
        val response = JSONObject(request("/repos/$repository/git/commits/$commitSha", "GET"))
        return response.getJSONObject("tree").getString("sha")
    }

    fun createBranch(branch: String, sha: String) {
        val body = JSONObject().put("ref", "refs/heads/$branch").put("sha", sha)
        request("/repos/$repository/git/refs", "POST", body)
    }

    fun textTree(treeSha: String): List<TreeEntry> {
        val response = JSONObject(request("/repos/$repository/git/trees/$treeSha?recursive=1", "GET"))
        val tree = response.optJSONArray("tree") ?: JSONArray()
        val result = mutableListOf<TreeEntry>()
        for (i in 0 until tree.length()) {
            val item = tree.optJSONObject(i) ?: continue
            if (item.optString("type") != "blob") continue
            val path = item.optString("path")
            val size = item.optLong("size", 0L)
            if (size <= RepositoryAgentLimit.fileBytes && safeTreePath(path)) result += TreeEntry(path, size)
        }
        return result.sortedBy { it.path }
    }

    fun readFile(path: String, branch: String): RepoFile {
        val response = JSONObject(request("/repos/$repository/contents/${encodePath(path)}?ref=${query(branch)}", "GET"))
        require(response.optString("type") == "file") { "$path is not a file." }
        val encoded = response.optString("content").replace("\n", "")
        val decoded = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        return RepoFile(path, response.getString("sha"), decoded)
    }

    fun writeFile(path: String, content: String, branch: String, sha: String?, message: String) {
        val body = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            .put("branch", branch)
        if (!sha.isNullOrBlank()) body.put("sha", sha)
        request("/repos/$repository/contents/${encodePath(path)}", "PUT", body)
    }

    fun waitForWorkflow(
        branch: String,
        notBefore: Instant,
        onStatus: (BuildRun) -> Unit
    ): BuildRun? {
        var lastStatus = ""
        repeat(36) {
            Thread.sleep(5_000)
            val run = latestWorkflowRun(branch, notBefore)
            if (run != null) {
                val signature = "${run.status}:${run.conclusion}"
                if (signature != lastStatus) {
                    lastStatus = signature
                    onStatus(run)
                }
                if (run.status == "completed") return run
            }
        }
        return null
    }

    fun failedLogs(runId: Long): String {
        val jobsResponse = JSONObject(request("/repos/$repository/actions/runs/$runId/jobs?per_page=100", "GET"))
        val jobs = jobsResponse.optJSONArray("jobs") ?: JSONArray()
        val ids = mutableListOf<Long>()
        for (i in 0 until jobs.length()) {
            val job = jobs.optJSONObject(i) ?: continue
            if (job.optString("conclusion") == "failure") ids += job.optLong("id")
        }
        if (ids.isEmpty()) {
            for (i in 0 until jobs.length()) ids += jobs.optJSONObject(i)?.optLong("id") ?: continue
        }
        val output = StringBuilder()
        ids.take(3).forEach { id ->
            runCatching { requestZipText("/repos/$repository/actions/jobs/$id/logs") }
                .onSuccess {
                    output.appendLine("===== JOB $id =====")
                    output.appendLine(it.take(RepositoryAgentLimit.logChars))
                }
        }
        return output.toString().takeLast(RepositoryAgentLimit.logChars)
    }

    private fun latestWorkflowRun(branch: String, notBefore: Instant): BuildRun? {
        val response = JSONObject(request("/repos/$repository/actions/runs?branch=${query(branch)}&event=workflow_dispatch&per_page=10", "GET"))
        val runs = response.optJSONArray("workflow_runs") ?: JSONArray()
        for (i in 0 until runs.length()) {
            val item = runs.optJSONObject(i) ?: continue
            val created = runCatching { Instant.parse(item.optString("created_at")) }.getOrNull() ?: continue
            if (created.isBefore(notBefore)) continue
            return BuildRun(
                id = item.optLong("id"),
                status = item.optString("status"),
                conclusion = item.optString("conclusion"),
                url = item.optString("html_url")
            )
        }
        return null
    }

    private fun request(path: String, method: String, body: JSONObject? = null): String {
        val connection = (URL("https://api.github.com$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "PocketForge-Android")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return try {
            if (body != null) connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrDefault("")
                error(message.ifBlank { "GitHub HTTP $code" })
            }
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun requestZipText(path: String): String {
        val connection = (URL("https://api.github.com$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "PocketForge-Android")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("GitHub logs HTTP $code")
            val bytes = connection.inputStream.use { it.readBytes() }
            val text = StringBuilder()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                val buffer = ByteArray(8192)
                while (entry != null && text.length < RepositoryAgentLimit.logChars) {
                    if (!entry.isDirectory) {
                        text.appendLine("--- ${entry.name} ---")
                        while (text.length < RepositoryAgentLimit.logChars) {
                            val count = zip.read(buffer)
                            if (count <= 0) break
                            text.append(String(buffer, 0, count, Charsets.UTF_8))
                        }
                    }
                    entry = zip.nextEntry
                }
            }
            text.toString()
        } finally {
            connection.disconnect()
        }
    }

    private fun safeTreePath(path: String): Boolean {
        val lower = path.lowercase()
        if (lower.contains("/build/") || lower.startsWith("build/") || lower.contains("/.gradle/") || lower.contains("/.git/")) return false
        return true
    }

    private fun encodePath(path: String): String = path.split('/').joinToString("/") { segment(it) }
    private fun segment(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun query(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}

private object RepositoryAgentLimit {
    const val fileBytes = 120_000L
    const val logChars = 120_000
}
