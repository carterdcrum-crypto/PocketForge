package com.pocketforge.app

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import java.util.Locale

private val PfBg = Color(0xFF08100C)
private val PfSurface = Color(0xFF111A15)
private val PfRaised = Color(0xFF18241D)
private val PfUser = Color(0xFF203027)
private val PfLine = Color(0xFF2A3A30)
private val PfText = Color(0xFFF4F7F2)
private val PfMuted = Color(0xFFA7B3AA)
private val PfAccent = Color(0xFF8FF0A4)
private val PfAccentSoft = Color(0xFF183D25)
private val PfGood = Color(0xFF8CE99A)
private val PfWarn = Color(0xFFFFD166)
private val PfBad = Color(0xFFFF8A8A)
private val PfBlue = Color(0xFF83B9FF)

private val PfTypography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = FontFamily.SansSerif),
    displayMedium = Typography().displayMedium.copy(fontFamily = FontFamily.SansSerif),
    displaySmall = Typography().displaySmall.copy(fontFamily = FontFamily.SansSerif),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = FontFamily.SansSerif),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = FontFamily.SansSerif),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = FontFamily.SansSerif),
    titleLarge = Typography().titleLarge.copy(fontFamily = FontFamily.SansSerif),
    titleMedium = Typography().titleMedium.copy(fontFamily = FontFamily.SansSerif),
    titleSmall = Typography().titleSmall.copy(fontFamily = FontFamily.SansSerif),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = FontFamily.SansSerif),
    bodySmall = Typography().bodySmall.copy(fontFamily = FontFamily.SansSerif),
    labelLarge = Typography().labelLarge.copy(fontFamily = FontFamily.SansSerif),
    labelMedium = Typography().labelMedium.copy(fontFamily = FontFamily.SansSerif),
    labelSmall = Typography().labelSmall.copy(fontFamily = FontFamily.SansSerif)
)

enum class ForgeMode(val label: String, val shortDescription: String) {
    AUTO("Auto", "PocketForge chooses the cheapest capable team."),
    SWARM("Swarm", "Multiple AIs independently plan, challenge, and reconcile."),
    AUTOPILOT("Autopilot", "Plan, edit a protected branch, build, repair, and verify."),
    BUILD("Build", "Route the current project through the build system.")
}

private enum class WorkspaceSection(val label: String) {
    WORKSPACE("Workspace"),
    FILES("Files"),
    LOGS("Logs"),
    SETTINGS("Settings")
}

private data class GoalOutcome(
    val plan: AiPlan,
    val execution: RepoExecutionResult? = null
)

@Composable
fun AgentWorkspaceV2App() {
    val context = LocalContext.current
    val store = remember { WorkspaceStore(context) }
    val studio = remember { StudioStore(context) }
    val vault = remember { SecretVault(context) }
    var projects by remember { mutableStateOf(store.projects()) }
    var project by remember { mutableStateOf(projects.first()) }
    val messages = remember { mutableStateListOf<WorkspaceMessage>() }
    val steps = remember { mutableStateListOf<AgentStep>() }
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(ForgeMode.AUTO) }
    var section by remember { mutableStateOf(WorkspaceSection.WORKSPACE) }
    var showModeMenu by remember { mutableStateOf(false) }
    var showNewProject by remember { mutableStateOf(false) }
    var showUpdater by remember { mutableStateOf(false) }
    var showCapabilities by remember { mutableStateOf(false) }
    var topMenuOpen by remember { mutableStateOf(false) }
    var lastExecution by remember { mutableStateOf<RepoExecutionResult?>(null) }
    var lastGoal by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val handler = remember { Handler(Looper.getMainLooper()) }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!heard.isNullOrBlank()) prompt = heard
        }
    }

    fun refreshProject(updatedId: String = project.id) {
        projects = store.projects()
        project = projects.firstOrNull { it.id == updatedId } ?: projects.first()
    }

    fun loadProject(next: WorkspaceProject) {
        project = next
        messages.clear()
        messages.addAll(store.messages(next.id))
        steps.clear()
        lastExecution = null
        lastGoal = ""
        section = WorkspaceSection.WORKSPACE
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(project.id) {
        if (messages.isEmpty()) messages.addAll(store.messages(project.id))
    }

    LaunchedEffect(messages.size, steps.size, section) {
        if (section == WorkspaceSection.WORKSPACE) {
            val total = messages.size + if (steps.isNotEmpty()) 1 else 0
            if (total > 0) listState.animateScrollToItem(total - 1)
        }
    }

    fun addMessage(role: String, text: String) {
        messages.add(store.appendMessage(project.id, role, text))
    }

    fun updateStep(step: AgentStep) {
        handler.post {
            val index = steps.indexOfFirst { it.id == step.id }
            if (index >= 0) steps[index] = step else steps.add(step)
        }
    }

    fun saveBrain(goal: String, plan: AiPlan, modeUsed: ForgeMode, execution: RepoExecutionResult? = null) {
        store.updateBrain(
            project.id,
            buildString {
                appendLine("Project: ${project.name}")
                appendLine("Operating mode: ${modeUsed.label}")
                appendLine("Latest goal: $goal")
                appendLine("Latest contract: ${plan.summary}")
                appendLine("Scope: ${plan.scope}")
                appendLine("Protected: ${plan.protected}")
                appendLine("Verification: ${plan.verify}")
                appendLine("Implementation notes: ${plan.implementationNotes}")
                execution?.let {
                    appendLine("Autopilot branch: ${it.branch}")
                    appendLine("Changed files: ${it.changedFiles.joinToString()}")
                    appendLine("Build conclusion: ${it.buildConclusion}")
                    appendLine("Verified: ${it.verified}")
                }
            }
        )
        refreshProject(project.id)
    }

    fun runBuild(crossCheck: Boolean, announce: Boolean = true) {
        if (busy) return
        busy = true
        section = WorkspaceSection.WORKSPACE
        steps.clear()
        if (announce) {
            addMessage("user", if (crossCheck) "Cross-check this project on every connected APK builder." else "Build this project with the best connected APK builder.")
        }
        updateStep(
            AgentStep(
                id = "build-route",
                agent = "Build Router",
                title = if (crossCheck) "Cross-checking build infrastructure" else "Choosing build infrastructure",
                detail = if (crossCheck) "Dispatching the same Android project to every configured CI provider." else "Using the preferred builder and falling back if it rejects the job.",
                state = AgentStepState.WORKING,
                provider = "GitHub · Codemagic · Bitrise"
            )
        )
        Thread {
            val result = runCatching {
                if (crossCheck) {
                    BuildConnectors.triggerAllConfigured(vault).joinToString("\n") { "${it.provider}: ${it.message}" }
                } else {
                    BuildConnectors.triggerBestAvailable(vault).let { "${it.provider}: ${it.message}" }
                }
            }
            handler.post {
                busy = false
                result.onSuccess { detail ->
                    updateStep(AgentStep("build-route", "Build Router", "Build dispatched", detail, AgentStepState.DONE, "CI Router"))
                    addMessage("assistant", "The build job is dispatched. Compiler, tests, lint, APK assembly, and verification remain the authority.\n\n$detail")
                }.onFailure { error ->
                    updateStep(AgentStep("build-route", "Build Router", "Build dispatch blocked", error.message ?: "Unknown error", AgentStepState.FAILED, "CI Router"))
                    addMessage("assistant", "I couldn’t start the build: ${error.message ?: "unknown error"}. Open Connections and connect at least one builder.")
                }
            }
        }.start()
    }

    fun runGoal(goal: String) {
        if (busy || goal.isBlank()) return
        val cleanGoal = goal.trim()
        lastGoal = cleanGoal
        lastExecution = null
        addMessage("user", cleanGoal)
        prompt = ""
        steps.clear()
        section = WorkspaceSection.WORKSPACE

        if (mode == ForgeMode.BUILD) {
            runBuild(crossCheck = false, announce = false)
            return
        }

        if (AiRouter.configuredProviders(vault).isEmpty()) {
            addMessage("assistant", "Connect at least one AI provider in Connections. Two providers unlock independent review; three unlock a full architect → reviewer → lead team.")
            return
        }

        if (mode == ForgeMode.AUTOPILOT && (!vault.has(IntegrationKeys.GITHUB_REPO) || !vault.has(IntegrationKeys.GITHUB_TOKEN))) {
            addMessage("assistant", "Autopilot needs a bound GitHub repository and repository token in Connections. Tokens stay in the encrypted Android vault.")
            return
        }

        busy = true
        updateStep(AgentStep("route", "Router", "Choosing the agent team", "Mode: ${mode.label}. Checking connected providers and assigning roles.", AgentStepState.WORKING, "Auto router"))

        Thread {
            val result = runCatching {
                when (mode) {
                    ForgeMode.SWARM -> {
                        updateStep(AgentStep("route", "Router", "Swarm assembled", "Multiple providers will create independent views before reconciliation.", AgentStepState.DONE, AiRouter.configuredProviders(vault).joinToString(" · ") { it.label }))
                        updateStep(AgentStep("swarm", "Swarm", "Independent planning + critique", "Running separate architecture and review passes.", AgentStepState.WORKING, "Multi-model consensus"))
                        val plan = AiRouter.planConsensus(cleanGoal, vault)
                        updateStep(AgentStep("swarm", "Swarm", "Consensus contract ready", plan.summary.take(600), AgentStepState.DONE, plan.provider))
                        updateStep(AgentStep("gate", "Verifier", "Green-build gate prepared", plan.verify, AgentStepState.DONE, "Compiler + CI"))
                        GoalOutcome(plan)
                    }
                    ForgeMode.AUTOPILOT -> {
                        updateStep(AgentStep("route", "Router", "Autopilot team assembled", "Planning first; repository writes stay isolated to a new PocketForge branch.", AgentStepState.DONE, AiRouter.configuredProviders(vault).joinToString(" · ") { it.label }))
                        updateStep(AgentStep("autopilot-plan", "Lead", "Creating the execution contract", "Using independent review when at least two providers are connected.", AgentStepState.WORKING, "AI team"))
                        val plan = if (AiRouter.configuredProviders(vault).size >= 2) {
                            AiRouter.planConsensus(cleanGoal, vault)
                        } else {
                            AgentOrchestrator.runGoal(cleanGoal, vault, ::updateStep).plan
                        }
                        updateStep(AgentStep("autopilot-plan", "Lead", "Execution contract approved", plan.summary.take(600), AgentStepState.DONE, plan.provider))
                        val execution = RepositoryAgent.execute(cleanGoal, plan, vault, ::updateStep)
                        GoalOutcome(plan, execution)
                    }
                    ForgeMode.AUTO -> {
                        updateStep(AgentStep("route", "Router", "Team assigned", "Using architect, independent reviewer when available, and lead synthesis.", AgentStepState.DONE, "Auto router"))
                        val run = AgentOrchestrator.runGoal(cleanGoal, vault, ::updateStep)
                        GoalOutcome(run.plan)
                    }
                    ForgeMode.BUILD -> error("Build mode is routed before agent execution.")
                }
            }

            handler.post {
                busy = false
                result.onSuccess { outcome ->
                    lastExecution = outcome.execution
                    saveBrain(cleanGoal, outcome.plan, mode, outcome.execution)
                    val execution = outcome.execution
                    if (execution != null) {
                        addMessage(
                            "assistant",
                            buildString {
                                appendLine(outcome.plan.summary)
                                appendLine()
                                appendLine("Protected branch: ${execution.branch}")
                                appendLine("Changed files: ${execution.changedFiles.size}")
                                appendLine("Build: ${execution.buildConclusion}")
                                appendLine("Repair attempts: ${execution.repairAttempts}")
                                appendLine()
                                if (execution.verified) {
                                    append("✓ Verified green. The branch passed the configured build gate and was not auto-merged.")
                                } else {
                                    append("Not verified green. PocketForge left the green/default branch untouched.")
                                }
                            }
                        )
                    } else {
                        addMessage(
                            "assistant",
                            buildString {
                                appendLine(outcome.plan.summary)
                                appendLine()
                                appendLine("Scope: ${outcome.plan.scope}")
                                appendLine("Protected: ${outcome.plan.protected}")
                                append("Verification: ${outcome.plan.verify}")
                            }
                        )
                    }
                }.onFailure { error ->
                    updateStep(AgentStep("failed-${System.currentTimeMillis()}", "PocketForge", "Run stopped", error.message ?: "Unknown error", AgentStepState.FAILED))
                    addMessage("assistant", "I hit a blocker: ${error.message ?: "unknown error"}. The green/default branch was not changed to hide the failure.")
                }
            }
        }.start()
    }

    fun launchVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell PocketForge what to build")
        }
        runCatching { voiceLauncher.launch(intent) }
            .onFailure { addMessage("assistant", "Voice recognition isn’t available on this device right now. You can still type the request.") }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PfAccent,
            background = PfBg,
            surface = PfSurface,
            surfaceVariant = PfRaised,
            onPrimary = Color(0xFF07140B),
            onBackground = PfText,
            onSurface = PfText,
            outline = PfLine
        ),
        typography = PfTypography
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = PfSurface, modifier = Modifier.widthIn(max = 335.dp)) {
                    PfDrawer(
                        current = project,
                        projects = projects,
                        onProject = ::loadProject,
                        onNewProject = { showNewProject = true },
                        onPreview = { context.startActivity(Intent(context, GeneratedPreviewActivity::class.java).putExtra("pocketforge.project_id", project.id)) },
                        onBuild = { runBuild(false) },
                        onCrossCheck = { runBuild(true) },
                        onIntegrations = { context.startActivity(Intent(context, IntegrationCenterActivity::class.java)) },
                        onCapabilities = { showCapabilities = true },
                        onUpdater = { showUpdater = true }
                    )
                }
            }
        ) {
            Scaffold(
                containerColor = PfBg,
                topBar = {
                    Column {
                        PfTopBar(
                            projectName = project.name,
                            providerCount = AiRouter.configuredProviders(vault).size,
                            mode = mode,
                            activeProvider = steps.lastOrNull { it.state == AgentStepState.WORKING }?.provider,
                            menuOpen = topMenuOpen,
                            onMenuOpen = { topMenuOpen = it },
                            onDrawer = { scope.launch { drawerState.open() } },
                            onPreview = { context.startActivity(Intent(context, GeneratedPreviewActivity::class.java).putExtra("pocketforge.project_id", project.id)) },
                            onBuild = { runBuild(false) },
                            onCrossCheck = { runBuild(true) },
                            onIntegrations = { context.startActivity(Intent(context, IntegrationCenterActivity::class.java)) },
                            onCapabilities = { showCapabilities = true },
                            onUpdater = { showUpdater = true }
                        )
                        PfSectionTabs(section = section, onSection = { section = it })
                    }
                },
                bottomBar = {
                    if (section == WorkspaceSection.WORKSPACE) {
                        PfComposer(
                            value = prompt,
                            onValue = { prompt = it },
                            busy = busy,
                            mode = mode,
                            modeMenuOpen = showModeMenu,
                            onModeMenuOpen = { showModeMenu = it },
                            onMode = { mode = it; showModeMenu = false },
                            onSend = { runGoal(prompt) },
                            onVoice = ::launchVoice,
                            onTools = { context.startActivity(Intent(context, IntegrationCenterActivity::class.java)) },
                            onPreview = { context.startActivity(Intent(context, GeneratedPreviewActivity::class.java).putExtra("pocketforge.project_id", project.id)) }
                        )
                    }
                }
            ) { padding ->
                when (section) {
                    WorkspaceSection.WORKSPACE -> PfWorkspacePanel(
                        modifier = Modifier.padding(padding),
                        messages = messages,
                        steps = steps,
                        busy = busy,
                        providerCount = AiRouter.configuredProviders(vault).size,
                        lastGoal = lastGoal,
                        execution = lastExecution,
                        listState = listState,
                        onPrompt = { prompt = it },
                        onCapabilities = { showCapabilities = true },
                        onFiles = { section = WorkspaceSection.FILES },
                        onLogs = { section = WorkspaceSection.LOGS }
                    )
                    WorkspaceSection.FILES -> PfFilesPanel(
                        modifier = Modifier.padding(padding),
                        project = project,
                        spec = studio.latest(project.id)?.spec,
                        execution = lastExecution
                    )
                    WorkspaceSection.LOGS -> PfLogsPanel(
                        modifier = Modifier.padding(padding),
                        steps = steps,
                        execution = lastExecution,
                        busy = busy
                    )
                    WorkspaceSection.SETTINGS -> PfSettingsPanel(
                        modifier = Modifier.padding(padding),
                        vault = vault,
                        mode = mode,
                        onMode = { mode = it },
                        onConnections = { context.startActivity(Intent(context, IntegrationCenterActivity::class.java)) },
                        onBuild = { runBuild(false) },
                        onCrossCheck = { runBuild(true) },
                        onCapabilities = { showCapabilities = true },
                        onUpdater = { showUpdater = true }
                    )
                }
            }
        }
    }

    if (showNewProject) {
        PfNewProjectDialog(
            onDismiss = { showNewProject = false },
            onCreate = { name ->
                val created = store.createProject(name)
                projects = store.projects()
                showNewProject = false
                loadProject(created)
            }
        )
    }

    if (BuildConfig.ENABLE_SIDELOAD_UPDATER && showUpdater) {
        Dialog(onDismissRequest = { showUpdater = false }) {
            Surface(color = PfSurface, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PfLine)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PocketForge updates", color = PfText, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showUpdater = false }) { Text("Done") }
                    }
                    PocketForgeUpdaterCard()
                }
            }
        }
    }

    if (showCapabilities) PfCapabilitiesDialog(onDismiss = { showCapabilities = false })
}

@Composable
private fun PfTopBar(
    projectName: String,
    providerCount: Int,
    mode: ForgeMode,
    activeProvider: String?,
    menuOpen: Boolean,
    onMenuOpen: (Boolean) -> Unit,
    onDrawer: () -> Unit,
    onPreview: () -> Unit,
    onBuild: () -> Unit,
    onCrossCheck: () -> Unit,
    onIntegrations: () -> Unit,
    onCapabilities: () -> Unit,
    onUpdater: () -> Unit
) {
    Surface(color = PfBg) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDrawer, contentPadding = PaddingValues(8.dp)) { Text("☰", color = PfText, fontSize = 20.sp) }
            Surface(color = PfAccentSoft, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("P", color = PfAccent, fontWeight = FontWeight.Black) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("PocketForge", color = PfText, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(projectName, color = PfMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(mode.label)
                        append(" · ")
                        append(if (providerCount == 0) "connect AI" else "$providerCount provider${if (providerCount == 1) "" else "s"}")
                        if (!activeProvider.isNullOrBlank()) append(" · $activeProvider")
                    },
                    color = if (activeProvider != null) PfAccent else PfMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (BuildConfig.ENABLE_SIDELOAD_UPDATER) {
                OutlinedButton(
                    onClick = onUpdater,
                    border = BorderStroke(1.dp, Color(0xFF4C8E5A)),
                    shape = RoundedCornerShape(100.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) { Text("↓ Update", color = PfAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }
            Box {
                TextButton(onClick = { onMenuOpen(true) }, contentPadding = PaddingValues(8.dp)) { Text("•••", color = PfText, fontSize = 16.sp) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpen(false) }, containerColor = PfRaised) {
                    DropdownMenuItem(text = { Text("Open full app preview") }, onClick = { onMenuOpen(false); onPreview() })
                    DropdownMenuItem(text = { Text("Build current project") }, onClick = { onMenuOpen(false); onBuild() })
                    DropdownMenuItem(text = { Text("Cross-check APK build") }, onClick = { onMenuOpen(false); onCrossCheck() })
                    DropdownMenuItem(text = { Text("AI & build connections") }, onClick = { onMenuOpen(false); onIntegrations() })
                    DropdownMenuItem(text = { Text("Capability map") }, onClick = { onMenuOpen(false); onCapabilities() })
                    if (BuildConfig.ENABLE_SIDELOAD_UPDATER) {
                        DropdownMenuItem(text = { Text("Check for PocketForge updates") }, onClick = { onMenuOpen(false); onUpdater() })
                    }
                }
            }
        }
    }
}

@Composable
private fun PfSectionTabs(section: WorkspaceSection, onSection: (WorkspaceSection) -> Unit) {
    Surface(color = PfBg) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WorkspaceSection.entries.forEach { item ->
                Surface(
                    modifier = Modifier.weight(1f).clickable { onSection(item) },
                    color = if (section == item) PfAccentSoft else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    border = if (section == item) BorderStroke(1.dp, Color(0xFF355440)) else null
                ) {
                    Text(
                        item.label,
                        color = if (section == item) PfAccent else PfMuted,
                        fontSize = 11.sp,
                        fontWeight = if (section == item) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 9.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun PfWorkspacePanel(
    modifier: Modifier,
    messages: List<WorkspaceMessage>,
    steps: List<AgentStep>,
    busy: Boolean,
    providerCount: Int,
    lastGoal: String,
    execution: RepoExecutionResult?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onPrompt: (String) -> Unit,
    onCapabilities: () -> Unit,
    onFiles: () -> Unit,
    onLogs: () -> Unit
) {
    if (messages.isEmpty() && steps.isEmpty()) {
        PfEmptyState(modifier.fillMaxSize(), providerCount, onPrompt, onCapabilities)
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (lastGoal.isNotBlank()) {
            item(key = "request-card") {
                Surface(color = PfSurface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, PfLine)) {
                    Column(Modifier.padding(15.dp)) {
                        Text("YOUR REQUEST", color = PfAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                        Text(lastGoal, color = PfText, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }

        items(messages, key = { it.id }) { message -> PfMessage(message) }
        if (steps.isNotEmpty()) item(key = "work-trace") { PfWorkTrace(steps, busy) }
        execution?.let { run ->
            item(key = "run-result") {
                PfRunResultCard(run, onFiles = onFiles, onLogs = onLogs)
            }
        }
    }
}

@Composable
private fun PfRunResultCard(execution: RepoExecutionResult, onFiles: () -> Unit, onLogs: () -> Unit) {
    val verified = execution.verified
    Surface(
        color = if (verified) Color(0xFF10261A) else Color(0xFF261A13),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (verified) Color(0xFF355F42) else Color(0xFF6A4A35))
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = if (verified) PfGood.copy(alpha = 0.14f) else PfWarn.copy(alpha = 0.14f), shape = CircleShape) {
                    Text(if (verified) "✓" else "!", color = if (verified) PfGood else PfWarn, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (verified) "Verified green branch" else "Verification incomplete", color = PfText, fontWeight = FontWeight.Bold)
                    Text(execution.buildConclusion, color = PfMuted, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            PfKeyValue("Branch", execution.branch)
            PfKeyValue("Model", execution.model)
            PfKeyValue("Files changed", execution.changedFiles.size.toString())
            PfKeyValue("Repair attempts", execution.repairAttempts.toString())
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onFiles, modifier = Modifier.weight(1f)) { Text("Changed files") }
                OutlinedButton(onClick = onLogs, modifier = Modifier.weight(1f)) { Text("Run logs") }
            }
        }
    }
}

@Composable
private fun PfFilesPanel(modifier: Modifier, project: WorkspaceProject, spec: StudioSpec?, execution: RepoExecutionResult?) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Files & structure", color = PfText, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text("What PocketForge can prove about the current project and the latest Autopilot run.", color = PfMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        }
        item {
            PfInfoCard("Project", project.name) {
                Text(project.brain, color = PfMuted, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 10, overflow = TextOverflow.Ellipsis)
            }
        }
        if (spec != null) {
            item {
                PfInfoCard("Generated app structure", "${spec.screens.size} screen${if (spec.screens.size == 1) "" else "s"}") {
                    spec.screens.forEach { screen ->
                        PfFileRow("${screen.title}", "${screen.type} · ${screen.fields.size} field${if (screen.fields.size == 1) "" else "s"}")
                    }
                }
            }
        }
        item {
            PfInfoCard("Latest repository run", execution?.branch ?: "No Autopilot branch in this session") {
                if (execution == null) {
                    Text("Run Autopilot to create an isolated branch and see the exact files it changes here.", color = PfMuted, fontSize = 11.sp, lineHeight = 16.sp)
                } else if (execution.changedFiles.isEmpty()) {
                    Text("No repository files were changed.", color = PfMuted, fontSize = 11.sp)
                } else {
                    execution.changedFiles.forEach { path -> PfFileRow(path.substringAfterLast('/'), path) }
                }
            }
        }
    }
}

@Composable
private fun PfLogsPanel(modifier: Modifier, steps: List<AgentStep>, execution: RepoExecutionResult?, busy: Boolean) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Run logs", color = PfText, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text("A readable audit trail of the agent and verification stages from this session.", color = PfMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (steps.isEmpty()) {
            item { PfEmptyPanel("No run logs yet", "Start a workspace request or build. PocketForge will record each visible stage here.") }
        } else {
            items(steps, key = { it.id }) { step ->
                PfLogRow(step)
            }
        }
        execution?.let { run ->
            item {
                PfInfoCard("Verification summary", if (run.verified) "GREEN" else "NOT VERIFIED") {
                    PfKeyValue("Repository", run.repository)
                    PfKeyValue("Branch", run.branch)
                    PfKeyValue("Build", run.buildConclusion)
                    PfKeyValue("Repairs", run.repairAttempts.toString())
                }
            }
        }
        if (busy) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PfAccent)
                    Spacer(Modifier.width(9.dp))
                    Text("PocketForge is still working. This log updates as stages change.", color = PfMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun PfSettingsPanel(
    modifier: Modifier,
    vault: SecretVault,
    mode: ForgeMode,
    onMode: (ForgeMode) -> Unit,
    onConnections: () -> Unit,
    onBuild: () -> Unit,
    onCrossCheck: () -> Unit,
    onCapabilities: () -> Unit,
    onUpdater: () -> Unit
) {
    val providers = AiRouter.configuredProviders(vault)
    val githubReady = vault.has(IntegrationKeys.GITHUB_REPO) && vault.has(IntegrationKeys.GITHUB_TOKEN)
    val codemagicReady = vault.has(IntegrationKeys.CODEMAGIC_TOKEN) && vault.has(IntegrationKeys.CODEMAGIC_APP_ID)
    val bitriseReady = vault.has(IntegrationKeys.BITRISE_TOKEN) && vault.has(IntegrationKeys.BITRISE_APP_SLUG)

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Workspace settings", color = PfText, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Text("Connections, execution mode, build infrastructure, and beta tools.", color = PfMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))

        PfInfoCard("AI team", "${providers.size} connected") {
            if (providers.isEmpty()) Text("No AI provider connected.", color = PfWarn, fontSize = 11.sp)
            providers.forEach { provider -> PfStatusRow(provider.label, true, "Ready for Auto routing") }
            OutlinedButton(onClick = onConnections, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Manage connections") }
        }

        Spacer(Modifier.height(12.dp))
        PfInfoCard("Execution mode", mode.label) {
            ForgeMode.entries.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clickable { onMode(item) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = mode == item, onClick = { onMode(item) })
                    Column(Modifier.padding(start = 6.dp)) {
                        Text(item.label, color = PfText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(item.shortDescription, color = PfMuted, fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        PfInfoCard("Build infrastructure", "Compiler is the authority") {
            PfStatusRow("GitHub", githubReady, if (githubReady) "Repository + Actions ready" else "Connect repository and token")
            PfStatusRow("Codemagic", codemagicReady, if (codemagicReady) "Connected" else "Optional")
            PfStatusRow("Bitrise", bitriseReady, if (bitriseReady) "Connected" else "Optional")
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBuild, modifier = Modifier.weight(1f)) { Text("Build") }
                OutlinedButton(onClick = onCrossCheck, modifier = Modifier.weight(1f)) { Text("Cross-check") }
            }
        }

        Spacer(Modifier.height(12.dp))
        PfInfoCard("Product tools", if (BuildConfig.ENABLE_SIDELOAD_UPDATER) "Beta build" else "Production build") {
            TextButton(onClick = onCapabilities, modifier = Modifier.fillMaxWidth()) { Text("Capability map", modifier = Modifier.fillMaxWidth()) }
            if (BuildConfig.ENABLE_SIDELOAD_UPDATER) {
                TextButton(onClick = onUpdater, modifier = Modifier.fillMaxWidth()) { Text("PocketForge updater", modifier = Modifier.fillMaxWidth()) }
            }
            Text(
                if (BuildConfig.ENABLE_SIDELOAD_UPDATER) "The sideload updater is intentionally enabled for debugging." else "The Play production build compiles the sideload updater out.",
                color = PfMuted,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PfDrawer(
    current: WorkspaceProject,
    projects: List<WorkspaceProject>,
    onProject: (WorkspaceProject) -> Unit,
    onNewProject: () -> Unit,
    onPreview: () -> Unit,
    onBuild: () -> Unit,
    onCrossCheck: () -> Unit,
    onIntegrations: () -> Unit,
    onCapabilities: () -> Unit,
    onUpdater: () -> Unit
) {
    Column(Modifier.fillMaxHeight().padding(horizontal = 10.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("PocketForge", color = PfText, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.padding(12.dp))
        NavigationDrawerItem(label = { Text("＋ New project") }, selected = false, onClick = onNewProject, colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent, unselectedTextColor = PfText))
        Text("PROJECTS", color = PfMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            projects.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    selected = current.id == item.id,
                    onClick = { onProject(item) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = PfAccentSoft,
                        selectedTextColor = PfAccent,
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = PfText
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = PfRaised, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Project brain", color = PfText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(current.brain, color = PfMuted, fontSize = 10.sp, maxLines = 9, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            PfDrawerAction("Full app preview", onPreview)
            PfDrawerAction("Build", onBuild)
            PfDrawerAction("Cross-check build", onCrossCheck)
            PfDrawerAction("AI & build connections", onIntegrations)
            PfDrawerAction("Capability map", onCapabilities)
        }
        HorizontalDivider(color = PfLine)
        if (BuildConfig.ENABLE_SIDELOAD_UPDATER) TextButton(onClick = onUpdater, modifier = Modifier.fillMaxWidth()) { Text("PocketForge updates", color = PfText) }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun PfDrawerAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, color = PfText, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PfEmptyState(modifier: Modifier, providerCount: Int, onPrompt: (String) -> Unit, onCapabilities: () -> Unit) {
    Column(modifier.padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(color = PfAccentSoft, shape = RoundedCornerShape(18.dp), modifier = Modifier.size(58.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("P", color = PfAccent, fontWeight = FontWeight.Black, fontSize = 23.sp) }
        }
        Spacer(Modifier.height(17.dp))
        Text("Advanced Workspace", color = PfText, fontWeight = FontWeight.Black, fontSize = 25.sp)
        Text(
            when {
                providerCount >= 3 -> "Architect, reviewer, and lead roles are available across three connected providers."
                providerCount == 2 -> "Two providers are ready for independent cross-checking."
                providerCount == 1 -> "One provider is ready. Connect another to unlock independent review."
                else -> "Connect an AI provider to start repository reasoning and agent workflows."
            },
            color = PfMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 7.dp)
        )
        Spacer(Modifier.height(22.dp))
        PfSuggestion("Use Autopilot to inspect my repo, make the smallest safe change, and verify the APK", onPrompt)
        Spacer(Modifier.height(8.dp))
        PfSuggestion("Challenge this project architecture and show me the safest next move", onPrompt)
        Spacer(Modifier.height(8.dp))
        PfSuggestion("Build this project and treat the compiler as the final authority", onPrompt)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onCapabilities) { Text("See PocketForge capabilities", color = PfAccent) }
    }
}

@Composable
private fun PfSuggestion(text: String, onPrompt: (String) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable { onPrompt(text) }, color = PfSurface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, PfLine)) {
        Text(text, color = PfText, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun PfMessage(message: WorkspaceMessage) {
    if (message.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(color = PfUser, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 330.dp)) {
                Text(message.text, color = PfText, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp))
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Surface(color = PfAccentSoft, shape = CircleShape) {
                Text("P", color = PfAccent, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(message.text, color = PfText, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f).padding(top = 3.dp))
        }
    }
}

@Composable
private fun PfWorkTrace(steps: List<AgentStep>, busy: Boolean) {
    val done = steps.count { it.state == AgentStepState.DONE }
    val failed = steps.any { it.state == AgentStepState.FAILED }
    val active = steps.lastOrNull { it.state == AgentStepState.WORKING }
    Surface(color = PfSurface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, PfLine)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Live work trace", color = PfText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        if (busy) active?.title ?: "PocketForge is working" else if (failed) "Stopped at a blocker" else "Run stages complete",
                        color = if (failed) PfBad else if (busy) PfAccent else PfMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Surface(color = if (failed) PfBad.copy(alpha = 0.13f) else if (busy) PfAccentSoft else PfGood.copy(alpha = 0.13f), shape = RoundedCornerShape(100.dp)) {
                    Text(
                        if (busy) "$done/${steps.size} RUNNING" else if (failed) "BLOCKED" else "$done/${steps.size} DONE",
                        color = if (failed) PfBad else if (busy) PfAccent else PfGood,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            steps.forEachIndexed { index, step ->
                PfStepRow(step)
                if (index != steps.lastIndex) HorizontalDivider(color = PfLine, modifier = Modifier.padding(start = 30.dp))
            }
        }
    }
}

@Composable
private fun PfStepRow(step: AgentStep) {
    var expanded by remember(step.id) { mutableStateOf(step.state == AgentStepState.WORKING || step.state == AgentStepState.FAILED) }
    val marker = when (step.state) {
        AgentStepState.WAITING -> "○"
        AgentStepState.WORKING -> "◌"
        AgentStepState.DONE -> "✓"
        AgentStepState.FAILED -> "×"
    }
    val color = when (step.state) {
        AgentStepState.WAITING -> PfMuted
        AgentStepState.WORKING -> PfAccent
        AgentStepState.DONE -> PfGood
        AgentStepState.FAILED -> PfBad
    }
    Row(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(marker, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.width(26.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step.agent, color = PfText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                step.provider?.let {
                    Spacer(Modifier.width(7.dp))
                    Surface(color = PfRaised, shape = RoundedCornerShape(100.dp)) {
                        Text(it, color = PfMuted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                    }
                }
            }
            Text(step.title, color = if (step.state == AgentStepState.WORKING) PfAccent else PfText, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            if (expanded && step.detail.isNotBlank()) {
                Text(step.detail, color = PfMuted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 5.dp))
            }
        }
        Text(if (expanded) "⌃" else "⌄", color = PfMuted, fontSize = 10.sp)
    }
}

@Composable
private fun PfLogRow(step: AgentStep) {
    val color = when (step.state) {
        AgentStepState.WAITING -> PfMuted
        AgentStepState.WORKING -> PfAccent
        AgentStepState.DONE -> PfGood
        AgentStepState.FAILED -> PfBad
    }
    Surface(color = PfSurface, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, PfLine)) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step.agent, color = PfText, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text(step.state.name, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            Text(step.title, color = PfText, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            if (step.detail.isNotBlank()) Text(step.detail, color = PfMuted, fontSize = 9.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 4.dp))
            step.provider?.let { Text("Provider: $it", color = PfBlue, fontSize = 8.sp, modifier = Modifier.padding(top = 5.dp)) }
        }
    }
}

@Composable
private fun PfComposer(
    value: String,
    onValue: (String) -> Unit,
    busy: Boolean,
    mode: ForgeMode,
    modeMenuOpen: Boolean,
    onModeMenuOpen: (Boolean) -> Unit,
    onMode: (ForgeMode) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onTools: () -> Unit,
    onPreview: () -> Unit
) {
    Surface(color = PfBg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Surface(color = PfRaised, shape = RoundedCornerShape(25.dp), border = BorderStroke(1.dp, PfLine)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
                    TextField(
                        value = value,
                        onValueChange = onValue,
                        placeholder = { Text(if (busy) "PocketForge is working…" else "Tell PocketForge what to build next…", color = PfMuted) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedTextColor = PfText,
                            unfocusedTextColor = PfText
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onTools, enabled = !busy, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("＋", color = PfText, fontSize = 20.sp) }
                        Box {
                            Surface(modifier = Modifier.clickable(enabled = !busy) { onModeMenuOpen(true) }, color = PfSurface, shape = RoundedCornerShape(100.dp)) {
                                Text("${mode.label} ⌄", color = PfText, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                            }
                            DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { onModeMenuOpen(false) }, containerColor = PfRaised) {
                                ForgeMode.entries.forEach { item ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(item.label)
                                                Text(item.shortDescription, color = PfMuted, fontSize = 10.sp)
                                            }
                                        },
                                        onClick = { onMode(item) }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        TextButton(onClick = onPreview, enabled = !busy, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("Preview", color = PfMuted, fontSize = 10.sp) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onVoice, enabled = !busy, contentPadding = PaddingValues(7.dp)) { Text("◉", color = PfText, fontSize = 17.sp) }
                        Surface(
                            modifier = Modifier.size(34.dp).clickable(enabled = !busy && value.isNotBlank(), onClick = onSend),
                            color = if (!busy && value.isNotBlank()) PfAccent else PfLine,
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(if (busy) "■" else "↑", color = if (!busy && value.isNotBlank()) Color(0xFF07140B) else PfMuted, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text("Visible work trace · protected branches · verified builds outrank AI confidence", color = PfMuted, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun PfInfoCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = PfSurface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, PfLine), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = PfText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = PfMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            content()
        }
    }
}

@Composable
private fun PfFileRow(name: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = PfAccentSoft, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(30.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("▤", color = PfAccent, fontSize = 12.sp) }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = PfText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = PfMuted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PfStatusRow(label: String, ready: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ready) "●" else "○", color = if (ready) PfGood else PfMuted, fontSize = 10.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = PfText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = PfMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun PfKeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = PfMuted, fontSize = 9.sp, modifier = Modifier.width(88.dp))
        Text(value, color = PfText, fontSize = 9.sp, lineHeight = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PfEmptyPanel(title: String, detail: String) {
    Surface(color = PfSurface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, PfLine)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = PfText, fontWeight = FontWeight.Bold)
            Text(detail, color = PfMuted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun PfCapabilitiesDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = PfSurface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, PfLine)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PocketForge capability map", color = PfText, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
                Spacer(Modifier.height(8.dp))
                PfCapability("LIVE", "Plain-English workspace", "Projects, tools, memory, previews, and builds hang off the conversation surface.", PfGood)
                PfCapability("LIVE", "Visible agent work trace", "See router, architect, reviewer, lead, repository, repair, and verifier stages while they run.", PfGood)
                PfCapability("LIVE", "Provider-aware Auto routing", "Connected AI providers can hand work off when one is unavailable.", PfGood)
                PfCapability("LIVE", "Protected repository Autopilot", "Writes go to an isolated branch; the green/default branch stays untouched until explicitly promoted.", PfGood)
                PfCapability("LIVE", "Compiler-first verification", "Tests, lint, compilation, APK assembly, and signature checks outrank model confidence.", PfGood)
                PfCapability("LIVE", "Files + Logs console", "Inspect generated app structure, changed repository paths, live stage logs, and verification summary.", PfGood)
                PfCapability("NEXT", "Review + merge controls", "Inspect exact diffs and explicitly promote a verified branch.", PfWarn)
                PfCapability("NEXT", "Artifact-aware workspace", "Treat APKs, screenshots, diffs, schemas, and test output as first-class objects.", PfWarn)
            }
        }
    }
}

@Composable
private fun PfCapability(state: String, title: String, body: String, stateColor: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Surface(color = stateColor.copy(alpha = 0.14f), shape = RoundedCornerShape(100.dp)) {
            Text(state, color = stateColor, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = PfText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(body, color = PfMuted, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun PfNewProjectDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PfSurface,
        title = { Text("New project") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, placeholder = { Text("Project name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
