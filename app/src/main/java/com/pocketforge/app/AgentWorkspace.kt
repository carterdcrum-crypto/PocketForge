package com.pocketforge.app

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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

private val ChatBg = Color(0xFF101010)
private val ChatSurface = Color(0xFF171717)
private val ChatElevated = Color(0xFF212121)
private val ChatUser = Color(0xFF2B2B2B)
private val ChatLine = Color(0xFF303030)
private val ChatText = Color(0xFFF4F4F4)
private val ChatMuted = Color(0xFFA8A8A8)
private val ChatAccent = Color(0xFFFFFFFF)
private val ChatGood = Color(0xFF8CE99A)
private val ChatWarn = Color(0xFFFFD166)
private val ChatBad = Color(0xFFFF8A8A)

@Composable
fun AgentWorkspaceApp() {
    val context = LocalContext.current
    val store = remember { WorkspaceStore(context) }
    val vault = remember { SecretVault(context) }
    var projects by remember { mutableStateOf(store.projects()) }
    var project by remember { mutableStateOf(projects.first()) }
    val messages = remember { mutableStateListOf<WorkspaceMessage>() }
    val steps = remember { mutableStateListOf<AgentStep>() }
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showNewProject by remember { mutableStateOf(false) }
    var showUpdater by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val handler = remember { Handler(Looper.getMainLooper()) }

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
        val extra = if (steps.isNotEmpty()) 1 else 0
        if (messages.isNotEmpty() || extra > 0) {
            listState.animateScrollToItem((messages.size + extra - 1).coerceAtLeast(0))
        }
    }

    fun addMessage(role: String, text: String) {
        val item = store.appendMessage(project.id, role, text)
        messages.add(item)
    }

    fun updateStep(step: AgentStep) {
        handler.post {
            val index = steps.indexOfFirst { it.id == step.id }
            if (index >= 0) steps[index] = step else steps.add(step)
        }
    }

    fun runGoal(goal: String) {
        if (busy || goal.isBlank()) return
        addMessage("user", goal.trim())
        prompt = ""
        steps.clear()

        if (AiRouter.configuredProviders(vault).isEmpty()) {
            addMessage("assistant", "I’m ready to work, but no AI provider is connected yet. Open Integration Center and add at least one free key. Two or three providers unlock independent cross-checking.")
            return
        }

        busy = true
        Thread {
            val result = runCatching {
                AgentOrchestrator.runGoal(goal, vault, ::updateStep)
            }
            handler.post {
                busy = false
                result.onSuccess { run ->
                    addMessage("assistant", run.answer)
                    store.updateBrain(
                        project.id,
                        "Latest goal: $goal\nLatest contract: ${run.plan.summary}\nScope: ${run.plan.scope}\nProtected: ${run.plan.protected}\nVerification: ${run.plan.verify}"
                    )
                    projects = store.projects()
                    project = projects.firstOrNull { it.id == project.id } ?: project
                }.onFailure { error ->
                    steps.add(AgentStep("failed-${System.currentTimeMillis()}", "PocketForge", "Run stopped", error.message ?: "Unknown error", AgentStepState.FAILED))
                    addMessage("assistant", "I hit a blocker: ${error.message ?: "unknown error"}. Your project was not marked complete.")
                }
            }
        }.start()
    }

    fun runBuild(crossCheck: Boolean) {
        if (busy) return
        busy = true
        steps.clear()
        addMessage("user", if (crossCheck) "Cross-check the current project build on every connected builder." else "Build the current project with the best available builder.")
        updateStep(AgentStep("build", "Build Router", "Dispatching Android build", if (crossCheck) "Sending the same project to every configured CI provider." else "Using primary builder with automatic fallback.", AgentStepState.WORKING, "CI Router"))
        Thread {
            val result = runCatching {
                if (crossCheck) BuildConnectors.triggerAllConfigured(vault).joinToString(" · ") { "${it.provider}: ${it.message}" }
                else BuildConnectors.triggerBestAvailable(vault).let { "${it.provider}: ${it.message}" }
            }
            handler.post {
                busy = false
                result.onSuccess { detail ->
                    updateStep(AgentStep("build", "Build Router", "Build dispatched", detail, AgentStepState.DONE, "CI Router"))
                    addMessage("assistant", "Build dispatched successfully. $detail")
                }.onFailure { error ->
                    updateStep(AgentStep("build", "Build Router", "Build dispatch failed", error.message ?: "Unknown error", AgentStepState.FAILED, "CI Router"))
                    addMessage("assistant", "The build router couldn’t start a build: ${error.message ?: "unknown error"}. Open Integration Center to connect a builder.")
                }
            }
        }.start()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ChatAccent,
            background = ChatBg,
            surface = ChatSurface,
            surfaceVariant = ChatElevated,
            onPrimary = Color.Black,
            onBackground = ChatText,
            onSurface = ChatText
        )
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = ChatSurface,
                    modifier = Modifier.widthIn(max = 330.dp)
                ) {
                    DrawerContent(
                        current = project,
                        projects = projects,
                        onProject = ::loadProject,
                        onNewProject = { showNewProject = true },
                        onIntegrations = { context.startActivity(Intent(context, IntegrationCenterActivity::class.java)) },
                        onUpdater = { showUpdater = true }
                    )
                }
            }
        ) {
            Scaffold(
                containerColor = ChatBg,
                topBar = {
                    ChatTopBar(
                        projectName = project.name,
                        providerCount = AiRouter.configuredProviders(vault).size,
                        onMenu = { scope.launch { drawerState.open() } },
                        menuOpen = menuOpen,
                        onMenuOpen = { menuOpen = it },
                        onIntegrations = { context.startActivity(Intent(context, IntegrationCenterActivity::class.java)) },
                        onBuild = { runBuild(false) },
                        onCrossCheck = { runBuild(true) },
                        onUpdater = { showUpdater = true }
                    )
                },
                bottomBar = {
                    Composer(
                        value = prompt,
                        onValue = { prompt = it },
                        busy = busy,
                        onSend = { runGoal(prompt) },
                        onIntegrations = { context.startActivity(Intent(context, IntegrationCenterActivity::class.java)) }
                    )
                }
            ) { padding ->
                if (messages.isEmpty() && steps.isEmpty()) {
                    EmptyWorkspace(
                        Modifier.padding(padding).fillMaxSize(),
                        project = project,
                        providerCount = AiRouter.configuredProviders(vault).size,
                        onPrompt = { prompt = it }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            ChatMessageRow(message)
                        }
                        if (steps.isNotEmpty()) {
                            item(key = "work-card") {
                                WorkCard(steps = steps, busy = busy)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewProject) {
        NewProjectDialog(
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
            Surface(color = ChatSurface, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Updates", color = ChatText, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showUpdater = false }) { Text("Done") }
                    }
                    PocketForgeUpdaterCard()
                }
            }
        }
    }
}

@Composable
private fun ChatTopBar(
    projectName: String,
    providerCount: Int,
    onMenu: () -> Unit,
    menuOpen: Boolean,
    onMenuOpen: (Boolean) -> Unit,
    onIntegrations: () -> Unit,
    onBuild: () -> Unit,
    onCrossCheck: () -> Unit,
    onUpdater: () -> Unit
) {
    Surface(color = ChatBg) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onMenu, contentPadding = PaddingValues(8.dp)) {
                Text("☰", color = ChatText, fontSize = 20.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(projectName, color = ChatText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (providerCount > 0) "Auto · $providerCount free AI${if (providerCount == 1) "" else "s"}" else "Auto · connect free AI",
                    color = ChatMuted,
                    fontSize = 10.sp
                )
            }
            Box {
                TextButton(onClick = { onMenuOpen(true) }, contentPadding = PaddingValues(8.dp)) {
                    Text("•••", color = ChatText, fontSize = 17.sp)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { onMenuOpen(false) },
                    containerColor = ChatElevated
                ) {
                    DropdownMenuItem(text = { Text("AI & build connectors") }, onClick = { onMenuOpen(false); onIntegrations() })
                    DropdownMenuItem(text = { Text("Build current project") }, onClick = { onMenuOpen(false); onBuild() })
                    DropdownMenuItem(text = { Text("Cross-check build") }, onClick = { onMenuOpen(false); onCrossCheck() })
                    DropdownMenuItem(text = { Text("Check for updates") }, onClick = { onMenuOpen(false); onUpdater() })
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    current: WorkspaceProject,
    projects: List<WorkspaceProject>,
    onProject: (WorkspaceProject) -> Unit,
    onNewProject: () -> Unit,
    onIntegrations: () -> Unit,
    onUpdater: () -> Unit
) {
    Column(Modifier.fillMaxHeight().padding(horizontal = 10.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("PocketForge", color = ChatText, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(12.dp))
        NavigationDrawerItem(
            label = { Text("＋ New project") },
            selected = false,
            onClick = onNewProject,
            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent, unselectedTextColor = ChatText)
        )
        Spacer(Modifier.height(8.dp))
        Text("PROJECTS", color = ChatMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            projects.forEach { project ->
                NavigationDrawerItem(
                    label = { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    selected = current.id == project.id,
                    onClick = { onProject(project) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = ChatElevated,
                        selectedTextColor = ChatText,
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = ChatText
                    )
                )
            }
            Spacer(Modifier.height(14.dp))
            Surface(color = ChatElevated, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Project brain", color = ChatText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(current.brain, color = ChatMuted, fontSize = 10.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        HorizontalDivider(color = ChatLine)
        TextButton(onClick = onIntegrations, modifier = Modifier.fillMaxWidth()) { Text("AI & build connectors", color = ChatText) }
        TextButton(onClick = onUpdater, modifier = Modifier.fillMaxWidth()) { Text("PocketForge updates", color = ChatText) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun EmptyWorkspace(modifier: Modifier, project: WorkspaceProject, providerCount: Int, onPrompt: (String) -> Unit) {
    Column(
        modifier.padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(color = ChatText, shape = CircleShape) {
            Text("P", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("What are we building?", color = ChatText, fontWeight = FontWeight.SemiBold, fontSize = 25.sp)
        Spacer(Modifier.height(7.dp))
        Text(
            if (providerCount > 1) "$providerCount free AIs are ready to plan, challenge each other, and verify the work."
            else if (providerCount == 1) "One free AI is connected. Add another for independent review."
            else "Connect a free AI in Integration Center to start real agent work.",
            color = ChatMuted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(24.dp))
        Suggestion("Build me a clean Android habit tracker", onPrompt)
        Spacer(Modifier.height(8.dp))
        Suggestion("Inspect this project and tell me the smartest next feature", onPrompt)
        Spacer(Modifier.height(8.dp))
        Suggestion("Find the smallest safe fix for my next build error", onPrompt)
    }
}

@Composable
private fun Suggestion(text: String, onPrompt: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onPrompt(text) },
        color = ChatSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ChatLine)
    ) {
        Text(text, color = ChatText, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun ChatMessageRow(message: WorkspaceMessage) {
    if (message.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(color = ChatUser, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 320.dp)) {
                Text(message.text, color = ChatText, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp))
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Surface(color = ChatText, shape = CircleShape) {
                Text("P", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(message.text, color = ChatText, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f).padding(top = 3.dp))
        }
    }
}

@Composable
private fun WorkCard(steps: List<AgentStep>, busy: Boolean) {
    var expanded by remember { mutableStateOf(true) }
    val done = steps.count { it.state == AgentStepState.DONE }
    val failed = steps.any { it.state == AgentStepState.FAILED }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        color = ChatSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ChatLine)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (busy) "◌" else if (failed) "!" else "✓", color = if (failed) ChatBad else if (busy) ChatWarn else ChatGood, fontSize = 17.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (busy) "Working on it" else if (failed) "Stopped at a blocker" else "Work trace", color = ChatText, fontWeight = FontWeight.SemiBold)
                    Text("$done/${steps.size} steps complete", color = ChatMuted, fontSize = 10.sp)
                }
                Text(if (expanded) "⌃" else "⌄", color = ChatMuted)
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                steps.forEachIndexed { index, step ->
                    WorkStepRow(step)
                    if (index != steps.lastIndex) HorizontalDivider(color = ChatLine, modifier = Modifier.padding(start = 26.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkStepRow(step: AgentStep) {
    val marker = when (step.state) {
        AgentStepState.WAITING -> "○"
        AgentStepState.WORKING -> "◌"
        AgentStepState.DONE -> "✓"
        AgentStepState.FAILED -> "×"
    }
    val markerColor = when (step.state) {
        AgentStepState.WAITING -> ChatMuted
        AgentStepState.WORKING -> ChatWarn
        AgentStepState.DONE -> ChatGood
        AgentStepState.FAILED -> ChatBad
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Text(marker, color = markerColor, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step.agent, color = ChatText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                step.provider?.let {
                    Spacer(Modifier.width(7.dp))
                    Text(it, color = ChatMuted, fontSize = 9.sp)
                }
            }
            Text(step.title, color = ChatText, fontSize = 12.sp)
            if (step.detail.isNotBlank()) Text(step.detail, color = ChatMuted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValue: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onIntegrations: () -> Unit
) {
    Surface(color = ChatBg, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Surface(color = ChatElevated, shape = RoundedCornerShape(25.dp), border = BorderStroke(1.dp, ChatLine)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
                    TextField(
                        value = value,
                        onValueChange = onValue,
                        placeholder = { Text(if (busy) "PocketForge is working…" else "Message PocketForge", color = ChatMuted) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedTextColor = ChatText,
                            unfocusedTextColor = ChatText
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onIntegrations, enabled = !busy, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)) {
                            Text("＋", color = ChatText, fontSize = 20.sp)
                        }
                        Surface(color = ChatSurface, shape = RoundedCornerShape(100.dp)) {
                            Text("Auto", color = ChatMuted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            modifier = Modifier.size(34.dp).clickable(enabled = !busy && value.isNotBlank(), onClick = onSend),
                            color = if (!busy && value.isNotBlank()) ChatText else ChatLine,
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(if (busy) "■" else "↑", color = if (!busy && value.isNotBlank()) Color.Black else ChatMuted, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text("PocketForge can make mistakes. Verified builds outrank AI confidence.", color = ChatMuted, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ChatSurface,
        title = { Text("New project") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Project name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
