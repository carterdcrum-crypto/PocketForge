package com.pocketforge.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CenterBg = Color(0xFF0A0C10)
private val CenterCard = Color(0xFF12161D)
private val CenterLine = Color(0xFF232A35)
private val CenterAccent = Color(0xFF7CFFB2)
private val CenterText = Color(0xFFF2F5F7)
private val CenterMuted = Color(0xFF9AA6B2)

class IntegrationCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IntegrationCenterScreen(onClose = ::finish) }
    }
}

private enum class CenterTab { AI, BUILDS }

@Composable
private fun IntegrationCenterScreen(onClose: () -> Unit) {
    var tab by remember { mutableStateOf(CenterTab.AI) }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = CenterAccent,
            background = CenterBg,
            surface = CenterCard,
            onPrimary = Color.Black,
            onBackground = CenterText,
            onSurface = CenterText
        )
    ) {
        Scaffold(containerColor = CenterBg) { padding ->
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Integration Center", color = CenterText, fontSize = 27.sp, fontWeight = FontWeight.Black)
                        Text("Free AI + redundant Android builds", color = CenterMuted, fontSize = 12.sp)
                    }
                    TextButton(onClick = onClose) { Text("Done") }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = tab == CenterTab.AI,
                        onClick = { tab = CenterTab.AI },
                        label = { Text("AI providers") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = tab == CenterTab.BUILDS,
                        onClick = { tab = CenterTab.BUILDS },
                        label = { Text("Build connectors") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(18.dp))
                when (tab) {
                    CenterTab.AI -> AiProvidersPanel()
                    CenterTab.BUILDS -> BuildConnectorsPanel()
                }
            }
        }
    }
}

@Composable
private fun AiProvidersPanel() {
    val context = LocalContext.current
    val vault = remember { SecretVault(context) }
    var openRouter by remember { mutableStateOf(vault.get(IntegrationKeys.OPENROUTER)) }
    var gemini by remember { mutableStateOf(vault.get(IntegrationKeys.GEMINI)) }
    var groq by remember { mutableStateOf(vault.get(IntegrationKeys.GROQ)) }
    var status by remember { mutableStateOf("Connect one provider minimum; two or three gives PocketForge failover.") }
    var busy by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("Build a simple Android app that tracks expenses and shows a monthly total.") }
    var plan by remember { mutableStateOf<AiPlan?>(null) }

    SectionCard("FREE AI ROUTER", "Keys stay encrypted on this phone for the beta.") {
        SecretField("OpenRouter API key", openRouter, { openRouter = it }, "Free model router + coding pool")
        ProviderButtons(
            enabled = openRouter.isNotBlank() && !busy,
            onSave = { vault.put(IntegrationKeys.OPENROUTER, openRouter); status = "OpenRouter key saved." },
            onTest = {
                vault.put(IntegrationKeys.OPENROUTER, openRouter)
                busy = true
                status = "Testing OpenRouter…"
                runAsync(
                    work = { AiRouter.test("openrouter", openRouter) },
                    done = { result -> busy = false; status = result.fold({ "OpenRouter connected: $it" }, { "OpenRouter failed: ${it.message}" }) }
                )
            }
        )
        HorizontalDivider(color = CenterLine, modifier = Modifier.padding(vertical = 12.dp))

        SecretField("Google Gemini API key", gemini, { gemini = it }, "Planner + independent review")
        ProviderButtons(
            enabled = gemini.isNotBlank() && !busy,
            onSave = { vault.put(IntegrationKeys.GEMINI, gemini); status = "Gemini key saved." },
            onTest = {
                vault.put(IntegrationKeys.GEMINI, gemini)
                busy = true
                status = "Testing Gemini…"
                runAsync(
                    work = { AiRouter.test("gemini", gemini) },
                    done = { result -> busy = false; status = result.fold({ "Gemini connected: $it" }, { "Gemini failed: ${it.message}" }) }
                )
            }
        )
        HorizontalDivider(color = CenterLine, modifier = Modifier.padding(vertical = 12.dp))

        SecretField("Groq API key", groq, { groq = it }, "Fast repair + fallback")
        ProviderButtons(
            enabled = groq.isNotBlank() && !busy,
            onSave = { vault.put(IntegrationKeys.GROQ, groq); status = "Groq key saved." },
            onTest = {
                vault.put(IntegrationKeys.GROQ, groq)
                busy = true
                status = "Testing Groq…"
                runAsync(
                    work = { AiRouter.test("groq", groq) },
                    done = { result -> busy = false; status = result.fold({ "Groq connected: $it" }, { "Groq failed: ${it.message}" }) }
                )
            }
        )
        Spacer(Modifier.height(12.dp))
        Text(status, color = if (status.contains("failed", true)) Color(0xFFFFA8A8) else CenterMuted, fontSize = 12.sp)
    }

    Spacer(Modifier.height(16.dp))
    SectionCard("AI BUILD LAB", "This is a real provider call, not a mock preview.") {
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("What should PocketForge build?") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
            colors = centerFieldColors(),
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                vault.put(IntegrationKeys.OPENROUTER, openRouter)
                vault.put(IntegrationKeys.GEMINI, gemini)
                vault.put(IntegrationKeys.GROQ, groq)
                busy = true
                plan = null
                status = "Routing to the best connected free AI…"
                runAsync(
                    work = { AiRouter.planBest(prompt, vault) },
                    done = { result ->
                        busy = false
                        result.onSuccess { plan = it; status = "Plan created by ${it.provider}." }
                            .onFailure { status = "AI planning failed: ${it.message}" }
                    }
                )
            },
            enabled = !busy && prompt.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CenterAccent, contentColor = Color.Black)
        ) { Text(if (busy) "Working…" else "Plan with best free AI", fontWeight = FontWeight.Bold) }

        plan?.let { item ->
            Spacer(Modifier.height(16.dp))
            ContractLine("Provider", item.provider)
            ContractLine("Summary", item.summary)
            ContractLine("Scope", item.scope)
            ContractLine("Protect", item.protected)
            ContractLine("Verify", item.verify)
            ContractLine("Notes", item.implementationNotes)
        }
    }

    Spacer(Modifier.height(12.dp))
    Text(
        "Routing order is Gemini → OpenRouter Free → Groq today. PocketForge can change that order later without exposing your keys or locking projects to one vendor.",
        color = CenterMuted,
        fontSize = 11.sp
    )
}

@Composable
private fun BuildConnectorsPanel() {
    val context = LocalContext.current
    val vault = remember { SecretVault(context) }

    var repo by remember { mutableStateOf(vault.get(IntegrationKeys.GITHUB_REPO).ifBlank { "carterdcrum-crypto/PocketForge" }) }
    var githubToken by remember { mutableStateOf(vault.get(IntegrationKeys.GITHUB_TOKEN)) }
    var githubWorkflow by remember { mutableStateOf(vault.get(IntegrationKeys.GITHUB_WORKFLOW).ifBlank { "android.yml" }) }

    var cmToken by remember { mutableStateOf(vault.get(IntegrationKeys.CODEMAGIC_TOKEN)) }
    var cmApp by remember { mutableStateOf(vault.get(IntegrationKeys.CODEMAGIC_APP_ID)) }
    var cmWorkflow by remember { mutableStateOf(vault.get(IntegrationKeys.CODEMAGIC_WORKFLOW).ifBlank { "android" }) }

    var brToken by remember { mutableStateOf(vault.get(IntegrationKeys.BITRISE_TOKEN)) }
    var brApp by remember { mutableStateOf(vault.get(IntegrationKeys.BITRISE_APP_SLUG)) }
    var brWorkflow by remember { mutableStateOf(vault.get(IntegrationKeys.BITRISE_WORKFLOW).ifBlank { "primary" }) }

    var status by remember { mutableStateOf("GitHub Actions is primary. Codemagic and Bitrise are optional redundancy.") }
    var busy by remember { mutableStateOf(false) }

    fun saveAll() {
        vault.put(IntegrationKeys.GITHUB_REPO, repo)
        vault.put(IntegrationKeys.GITHUB_TOKEN, githubToken)
        vault.put(IntegrationKeys.GITHUB_WORKFLOW, githubWorkflow)
        vault.put(IntegrationKeys.CODEMAGIC_TOKEN, cmToken)
        vault.put(IntegrationKeys.CODEMAGIC_APP_ID, cmApp)
        vault.put(IntegrationKeys.CODEMAGIC_WORKFLOW, cmWorkflow)
        vault.put(IntegrationKeys.BITRISE_TOKEN, brToken)
        vault.put(IntegrationKeys.BITRISE_APP_SLUG, brApp)
        vault.put(IntegrationKeys.BITRISE_WORKFLOW, brWorkflow)
    }

    SectionCard("GITHUB ACTIONS · PRIMARY", "Already used by PocketForge. Add a fine-grained token only when the app itself needs to dispatch builds.") {
        PlainField("Repository", repo, { repo = it })
        PlainField("Workflow file", githubWorkflow, { githubWorkflow = it })
        SecretField("GitHub token", githubToken, { githubToken = it }, "Actions: read/write for target repositories")
        TestConnectorButton("Save + test GitHub", !busy && repo.isNotBlank() && githubToken.isNotBlank()) {
            saveAll(); busy = true; status = "Testing GitHub Actions…"
            runAsync(
                work = { BuildConnectors.testGitHub(repo, githubToken) },
                done = { result -> busy = false; status = result.fold({ it.message }, { "GitHub failed: ${it.message}" }) }
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionCard("CODEMAGIC · BACKUP", "Good second build environment for catching CI-specific problems.") {
        PlainField("App ID", cmApp, { cmApp = it })
        PlainField("Workflow ID", cmWorkflow, { cmWorkflow = it })
        SecretField("Codemagic API token", cmToken, { cmToken = it }, "Stored in Android Keystore")
        TestConnectorButton("Save + test Codemagic", !busy && cmApp.isNotBlank() && cmToken.isNotBlank()) {
            saveAll(); busy = true; status = "Testing Codemagic…"
            runAsync(
                work = { BuildConnectors.testCodemagic(cmApp, cmToken) },
                done = { result -> busy = false; status = result.fold({ it.message }, { "Codemagic failed: ${it.message}" }) }
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionCard("BITRISE · OPTIONAL BACKUP", "A third mobile CI path when you want maximum redundancy.") {
        PlainField("App slug", brApp, { brApp = it })
        PlainField("Workflow ID", brWorkflow, { brWorkflow = it })
        SecretField("Bitrise access token", brToken, { brToken = it }, "Stored in Android Keystore")
        TestConnectorButton("Save + test Bitrise", !busy && brApp.isNotBlank() && brToken.isNotBlank()) {
            saveAll(); busy = true; status = "Testing Bitrise…"
            runAsync(
                work = { BuildConnectors.testBitrise(brApp, brToken) },
                done = { result -> busy = false; status = result.fold({ it.message }, { "Bitrise failed: ${it.message}" }) }
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionCard("BUILD ROUTER", "Primary mode saves free minutes. Cross-check mode intentionally burns more CI quota.") {
        Button(
            onClick = {
                saveAll(); busy = true; status = "Dispatching to the best connected builder…"
                runAsync(
                    work = { BuildConnectors.triggerBestAvailable(vault) },
                    done = { result -> busy = false; status = result.fold({ "${it.provider}: ${it.message}" }, { "Build dispatch failed: ${it.message}" }) }
                )
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CenterAccent, contentColor = Color.Black)
        ) { Text("Build with primary / fallback", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                saveAll(); busy = true; status = "Dispatching cross-check builds…"
                runAsync(
                    work = { BuildConnectors.triggerAllConfigured(vault) },
                    done = { result ->
                        busy = false
                        status = result.fold(
                            { list -> list.joinToString(" · ") { "${it.provider}: queued" } },
                            { "Cross-check failed: ${it.message}" }
                        )
                    }
                )
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Cross-check on every connected builder") }
        Spacer(Modifier.height(10.dp))
        Text(status, color = if (status.contains("failed", true)) Color(0xFFFFA8A8) else CenterMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CenterCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CenterLine)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = CenterText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = CenterMuted, fontSize = 11.sp)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SecretField(label: String, value: String, onValue: (String) -> Unit, helper: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        supportingText = { Text(helper) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = centerFieldColors(),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun PlainField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = centerFieldColors(),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun ProviderButtons(enabled: Boolean, onSave: () -> Unit, onTest: () -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        TextButton(onClick = onSave, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Save") }
        OutlinedButton(onClick = onTest, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Save + test") }
    }
}

@Composable
private fun TestConnectorButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
private fun ContractLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(label.uppercase(), color = CenterAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = CenterText, fontSize = 13.sp)
    }
}

@Composable
private fun centerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CenterAccent,
    unfocusedBorderColor = CenterLine,
    focusedContainerColor = CenterCard,
    unfocusedContainerColor = CenterCard,
    focusedTextColor = CenterText,
    unfocusedTextColor = CenterText
)

private fun <T> runAsync(work: () -> T, done: (Result<T>) -> Unit) {
    Thread {
        val result = runCatching(work)
        Handler(Looper.getMainLooper()).post { done(result) }
    }.start()
}
