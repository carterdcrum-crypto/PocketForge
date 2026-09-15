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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import java.util.Locale

private val PfBg = Color(0xFF101010)
private val PfSurface = Color(0xFF171717)
private val PfRaised = Color(0xFF212121)
private val PfUser = Color(0xFF2B2B2B)
private val PfLine = Color(0xFF303030)
private val PfText = Color(0xFFF4F4F4)
private val PfMuted = Color(0xFFA8A8A8)
private val PfGood = Color(0xFF8CE99A)
private val PfWarn = Color(0xFFFFD166)
private val PfBad = Color(0xFFFF8A8A)

enum class ForgeMode(val label: String, val shortDescription: String) {
    AUTO("Auto", "PocketForge chooses the cheapest capable team."),
    SWARM("Swarm", "Multiple AIs independently plan, challenge, and reconcile."),
    BUILD("Build", "Route the current project through the build system.")
}

@Composable
fun AgentWorkspaceV2App() {
    val context = LocalContext.current
    val store = remember { WorkspaceStore(context) }
    val vault = remember { SecretVault(context) }
    var projects by remember { mutableStateOf(store.projects()) }
    var project by remember { mutableStateOf(projects.first()) }
    val messages = remember { mutableStateListOf<WorkspaceMessage>() }
    val steps = remember { mutableStateListOf<AgentStep>() }
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(ForgeMode.AUTO) }
    var showModeMenu by remember { mutableStateOf(false) }
    var showNewProject by remember { mutableStateOf(false) }
    var showUpdater by remember { mutableStateOf(false) }
    var showCapabilities by remember { mutableStateOf(false) }
    var topMenuOpen by remember { mutableStateOf(false) }
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
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(project.id) {
        if (messages.isEmpty()) messages.addAll(store.messages(project.id))
    }

    LaunchedEffect(messages.size, steps.size) {
        val total = messages.size + if (steps.isNotEmpty()) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
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

    fun saveBrain(goal: String, plan: AiPlan, modeUsed: ForgeMode) {
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
            }
        )
        refreshProject(project.id)
    }

    fun runBuild(crossCheck: Boolean) {
        if (busy) return
        busy = true
        steps.clear()
        addMessage("user", if (crossCheck) "Cross-check this project on every connected APK builder." else "Build this project with the best connected APK builder.")
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
                    addMessage("assistant", "The build job is dispatched. I’m treating the compiler, tests, lint, and APK verification as the authority—not the AI’s confidence.\n\n$detail")
                }.onFailure { error ->
                    updateStep(AgentStep("build-route", "Build Router", "Build dispatch blocked", error.message ?: "Unknown error", AgentStepState.FAILED, "CI Router"))
                    addMessage("assistant", "I couldn’t start the build: ${error.message ?: "unknown error"}. Open AI & build connectors and connect at least one builder.")
                }
            }
        }.start()
    }

    fun runGoal(goal: String) {
        if (busy || goal.isBlank()) return
        val cleanGoal = goal.trim()
        addMessage("user", cleanGoal)
        prompt = ""
        steps.clear()

        if (mode == ForgeMode.BUILD) {
            runBuild(crossCheck = false)
            return
        }

        if (AiRouter.configuredProviders(vault).isEmpty()) {
            addMessage("assistant", "I can run the workspace, memory, previews, updater, and build tools, but I need at least one free AI key for reasoning. Open AI & build connectors. Two providers unlock independent review; three unlock a full architect → reviewer → lead team.")
            return
        }

        busy = true
        updateStep(AgentStep("route", "Router", "Choosing the agent team", "Mode: ${mode.label}. Checking connected free providers and assigning roles.", AgentStepState.WORKING, "Free-first router"))

        Thread {
            val result = runCatching {
                if (mode == ForgeMode.SWARM) {
                    updateStep(AgentStep("route", "Router", "Swarm assembled", "Multiple providers will create independent views before reconciliation.", AgentStepState.DONE, AiRouter.configuredProviders(vault).joinToString(" · ") { it.label }))
                    updateStep(AgentStep("swarm", "Swarm", "Independent planning + critique", "Running separate architecture and review passes.", AgentStepState.WORKING, "Multi-model consensus"))
                    val plan = AiRouter.planConsensus(cleanGoal, vault)
                    updateStep(AgentStep("swarm", "Swarm", "Consensus contract ready", plan.summary.take(600), AgentStepState.DONE, plan.provider))
                    updateStep(AgentStep("gate", "Verifier", "Green-build gate prepared", plan.verify, AgentStepState.DONE, "Compiler + CI"))
                    plan
                } else {
                    updateStep(AgentStep("route", "Router", "Team assigned", "Using architect, independent reviewer when available, and lead synthesis.", AgentStepState.DONE, "Auto router"))
                    val run = AgentOrchestrator.runGoal(cleanGoal, vault, ::updateStep)
                    run.plan
                }
            }

            handler.post {
                busy = false
                result.onSuccess { plan ->
                    saveBrain(cleanGoal, plan, mode)
                    addMessage(
                        "assistant",
                        buildString {
                            appendLine(plan.summary)
                            appendLine()
                            appendLine("Scope: ${plan.scope}")
                            appendLine()
                            appendLine("Protected: ${plan.protected}")
                            appendLine()
                            appendLine("Verification: ${plan.verify}")
                            appendLine()
                            append("I have the execution contract. The next autonomy layer will apply this contract to the bound repository, build it, inspect failures, repair the smallest safe set of files, and stop only at a verified APK or a blocker that needs you.")
                        }
                    )
                }.onFailure { error ->
                    updateStep(AgentStep("failed-${System.currentTimeMillis()}", "PocketForge", "Run stopped", error.message ?: "Unknown error", AgentStepState.FAILED))
                    addMessage("assistant", "I hit a blocker: ${error.message ?: "unknown error"}. I did not mark the job complete or pretend it worked.")
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
            primary = Color.White,
            background = PfBg,
            surface = PfSurface,
            surfaceVariant = PfRaised,
            onPrimary = Color.Black,
            onBackground = PfText,
            onSurface = PfText
        )
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
                        onPreview = { context.startActivity(Intent(context, GeneratedPreviewActivity::class.java)) },
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
                    PfTopBar(
                        projectName = project.name,
                        providerCount = AiRouter.configuredProviders(vault).size,
                        mode = mode,
                        menuOpen = topMenuOpen,
                        onMenuOpen = { topMenuOpen = it },
                        onDrawer = { scope.launch { drawerState.open() } },
                        onPreview = { context.startActivity(Intent(context, GeneratedPreviewActivity::class.java)) },
                        onBuild = { runBuild(false) },
                        onCrossCheck = { runBuild(true) },
                        onIntegrations = { context.startActivity(Intent(context, IntegrationCenterActivity::class.java)) },
                        onCapabilities = { showCapabilities = true },
                        onUpdater = { showUpdater = true }
                    )
                },
                bottomBar = {
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
                        onPreview = { context.startActivity(Intent(context, GeneratedPreviewActivity::class.java)) }
                    )
                }
            ) { padding ->
                if (messages.isEmpty() && steps.isEmpty()) {
                    PfEmptyState(
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        providerCount = AiRouter.configuredProviders(vault).size,
                        onPrompt = { prompt = it },
                        onCapabilities = { showCapabilities = true }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(messages, key = { it.id }) { message -> PfMessage(message) }
                        if (steps.isNotEmpty()) item(key = "work-trace") { PfWorkTrace(steps, busy) }
                    }
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

    if (showUpdater) {
        Dialog(onDismissRequest = { showUpdater = false }) {
            Surface(color = PfSurface, shape = RoundedCornerShape(24.dp)) {
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

    if (showCapabilities) {
        PfCapabilitiesDialog(onDismiss = { showCapabilities = false })
    }
}

@Composable
private fun PfTopBar(
    projectName: String,
    providerCount: Int,
    mode: ForgeMode,
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDrawer, contentPadding = PaddingValues(8.dp)) { Text("☰", color = PfText, fontSize = 20.sp) }
            Column(Modifier.weight(1f)) {
                Text(projectName, color = PfText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${mode.label} · ${if (providerCount == 0) "connect free AI" else "$providerCount provider${if (providerCount == 1) "" else "s"}"}", color = PfMuted, fontSize = 10.sp)
            }
            Box {
                TextButton(onClick = { onMenuOpen(true) }, contentPadding = PaddingValues(8.dp)) { Text("•••", color = PfText, fontSize = 17.sp) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpen(false) }, containerColor = PfRaised) {
                    DropdownMenuItem(text = { Text("Open full app preview") }, onClick = { onMenuOpen(false); onPreview() })
                    DropdownMenuItem(text = { Text("Build current project") }, onClick = { onMenuOpen(false); onBuild() })
                    DropdownMenuItem(text = { Text("Cross-check APK build") }, onClick = { onMenuOpen(false); onCrossCheck() })
                    DropdownMenuItem(text = { Text("AI & build connectors") }, onClick = { onMenuOpen(false); onIntegrations() })
                    DropdownMenuItem(text = { Text("Capability map") }, onClick = { onMenuOpen(false); onCapabilities() })
                    DropdownMenuItem(text = { Text("Check for PocketForge updates") }, onClick = { onMenuOpen(false); onUpdater() })
                }
            }
        }
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
        Text("PocketForge", color = PfText, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(12.dp))
        NavigationDrawerItem(label = { Text("＋ New project") }, selected = false, onClick = onNewProject, colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent, unselectedTextColor = PfText))
        Spacer(Modifier.height(8.dp))
        Text("PROJECTS", color = PfMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            projects.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    selected = current.id == item.id,
                    onClick = { onProject(item) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = PfRaised,
                        selectedTextColor = PfText,
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = PfText
                    )
                )
            }
            Spacer(Modifier.height(14.dp))
            Surface(color = PfRaised, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Project brain", color = PfText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(current.brain, color = PfMuted, fontSize = 10.sp, maxLines = 10, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("TOOLS", color = PfMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            PfDrawerAction("Full app preview", onPreview)
            PfDrawerAction("Build", onBuild)
            PfDrawerAction("Cross-check build", onCrossCheck)
            PfDrawerAction("AI & build connectors", onIntegrations)
            PfDrawerAction("Capability map", onCapabilities)
        }
        HorizontalDivider(color = PfLine)
        TextButton(onClick = onUpdater, modifier = Modifier.fillMaxWidth()) { Text("PocketForge updates", color = PfText) }
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
        Surface(color = PfText, shape = CircleShape) {
            Text("P", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("What should we build?", color = PfText, fontWeight = FontWeight.SemiBold, fontSize = 25.sp)
        Spacer(Modifier.height(7.dp))
        Text(
            when {
                providerCount >= 3 -> "Three free AIs are available for architect, reviewer, and lead roles."
                providerCount == 2 -> "Two free AIs are available for independent cross-checking."
                providerCount == 1 -> "One free AI is ready. Add another to unlock independent review."
                else -> "Connect a free AI to start reasoning. The workspace and build tools stay local until you do."
            },
            color = PfMuted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(24.dp))
        PfSuggestion("Build me a complete Android app from this idea", onPrompt)
        Spacer(Modifier.height(8.dp))
        PfSuggestion("Inspect my project, challenge the architecture, and find the smartest next move", onPrompt)
        Spacer(Modifier.height(8.dp))
        PfSuggestion("Design the smallest safe fix, then verify it across every connected builder", onPrompt)
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onCapabilities) { Text("See what PocketForge can do", color = PfMuted) }
    }
}

@Composable
private fun PfSuggestion(text: String, onPrompt: (String) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable { onPrompt(text) }, color = PfSurface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, PfLine)) {
        Text(text, color = PfText, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun PfMessage(message: WorkspaceMessage) {
    if (message.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(color = PfUser, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 330.dp)) {
                Text(message.text, color = PfText, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp))
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Surface(color = PfText, shape = CircleShape) {
                Text("P", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(message.text, color = PfText, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f).padding(top = 3.dp))
        }
    }
}

@Composable
private fun PfWorkTrace(steps: List<AgentStep>, busy: Boolean) {
    var expanded by remember { mutableStateOf(true) }
    val done = steps.count { it.state == AgentStepState.DONE }
    val failed = steps.any { it.state == AgentStepState.FAILED }
    val active = steps.lastOrNull { it.state == AgentStepState.WORKING }
    Surface(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }, color = PfSurface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, PfLine)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (busy) "◌" else if (failed) "!" else "✓", color = if (failed) PfBad else if (busy) PfWarn else PfGood, fontSize = 17.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (busy) active?.title ?: "Working" else if (failed) "Stopped at a blocker" else "Work complete", color = PfText, fontWeight = FontWeight.SemiBold)
                    Text("$done/${steps.size} stages complete · tap for live trace", color = PfMuted, fontSize = 10.sp)
                }
                Text(if (expanded) "⌃" else "⌄", color = PfMuted)
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                steps.forEachIndexed { index, step ->
                    PfStepRow(step)
                    if (index != steps.lastIndex) HorizontalDivider(color = PfLine, modifier = Modifier.padding(start = 26.dp))
                }
            }
        }
    }
}

@Composable
private fun PfStepRow(step: AgentStep) {
    val marker = when (step.state) {
        AgentStepState.WAITING -> "○"
        AgentStepState.WORKING -> "◌"
        AgentStepState.DONE -> "✓"
        AgentStepState.FAILED -> "×"
    }
    val color = when (step.state) {
        AgentStepState.WAITING -> PfMuted
        AgentStepState.WORKING -> PfWarn
        AgentStepState.DONE -> PfGood
        AgentStepState.FAILED -> PfBad
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Text(marker, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step.agent, color = PfText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                step.provider?.let {
                    Spacer(Modifier.width(7.dp))
                    Text(it, color = PfMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(step.title, color = PfText, fontSize = 12.sp)
            if (step.detail.isNotBlank()) Text(step.detail, color = PfMuted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
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
                        placeholder = { Text(if (busy) "PocketForge is working…" else "Message PocketForge", color = PfMuted) },
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
                            color = if (!busy && value.isNotBlank()) PfText else PfLine,
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(if (busy) "■" else "↑", color = if (!busy && value.isNotBlank()) Color.Black else PfMuted, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text("Visible work trace · durable project memory · verified builds outrank AI confidence", color = PfMuted, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun PfCapabilitiesDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = PfSurface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PocketForge capability map", color = PfText, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
                Spacer(Modifier.height(10.dp))
                PfCapability("LIVE", "Conversation-first workspace", "Chat is the command surface; projects, tools, memory, previews, and builds hang off it.", PfGood)
                PfCapability("LIVE", "Visible agent work trace", "See architect, reviewer, lead, router, and verifier stages while they are running.", PfGood)
                PfCapability("LIVE", "Free multi-model swarm", "Gemini, OpenRouter free models, and Groq can independently challenge one another before a contract is accepted.", PfGood)
                PfCapability("LIVE", "Durable project brain", "Important project intent, protected behavior, and verification rules survive across chats.", PfGood)
                PfCapability("LIVE", "Multi-builder verification", "GitHub Actions is primary; Codemagic and Bitrise can cross-check the same APK job.", PfGood)
                PfCapability("LIVE", "Full-screen app preview", "Preview takes over the screen instead of living in a tiny card.", PfGood)
                PfCapability("LIVE", "Voice prompt capture", "Use Android speech recognition without paying another AI provider.", PfGood)
                PfCapability("NEXT", "Repository execution agent", "Apply approved contracts on a protected branch, commit exact diffs, build, inspect failures, repair, and retry.", PfWarn)
                PfCapability("NEXT", "Automatic rollback + snapshots", "Every agent change gets a recoverable checkpoint before it can become the new green state.", PfWarn)
                PfCapability("NEXT", "Artifact-aware chat", "APK, screenshots, logs, diffs, database schema, and test output become first-class conversation objects.", PfWarn)
                Spacer(Modifier.height(8.dp))
                Text("Free models can make the system powerful operationally, but they do not magically become smarter than paid frontier models. PocketForge’s advantage is orchestration, memory, tools, verification, and model diversity.", color = PfMuted, fontSize = 11.sp, lineHeight = 16.sp)
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
            OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("Project name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
