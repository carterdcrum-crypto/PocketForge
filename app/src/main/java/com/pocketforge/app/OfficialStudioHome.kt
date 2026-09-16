package com.pocketforge.app

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.util.Locale

private val OfficialBg = Color(0xFF08100C)
private val OfficialSurface = Color(0xFF111A15)
private val OfficialRaised = Color(0xFF18241D)
private val OfficialLine = Color(0xFF2A3A30)
private val OfficialText = Color(0xFFF4F7F2)
private val OfficialMuted = Color(0xFFA7B3AA)
private val OfficialAccent = Color(0xFF8FF0A4)
private val OfficialAccentSoft = Color(0xFF183D25)
private val OfficialBlue = Color(0xFF83B9FF)
private val OfficialPurple = Color(0xFFBE95FF)

private enum class StudioDestination(val label: String, val glyph: String) {
    HOME("Home", "⌂"),
    TEMPLATES("Templates", "▦"),
    CREATE("Create", "+"),
    LIBRARY("Library", "▣"),
    SETTINGS("Settings", "⚙")
}

@Composable
fun PocketForgeOfficialApp() {
    val context = LocalContext.current
    val workspace = remember { WorkspaceStore(context) }
    val studio = remember { StudioStore(context) }
    val vault = remember { SecretVault(context) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    var destination by remember { mutableStateOf(StudioDestination.HOME) }
    var projects by remember { mutableStateOf(workspace.projects()) }
    var idea by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var createStatus by remember { mutableStateOf<String?>(null) }
    var showUpdater by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    fun refresh() {
        projects = workspace.projects()
    }

    fun openProject(project: WorkspaceProject, preview: Boolean = false) {
        ensureOfficialDesign(studio, project.id)
        val target = if (preview) GeneratedPreviewActivity::class.java else StudioEditorActivity::class.java
        context.startActivity(Intent(context, target).putExtra("pocketforge.project_id", project.id))
    }

    fun createFromStarter(starter: StudioStarter) {
        val created = workspace.createProject(starter.spec.name)
        studio.save(created.id, starter.spec, "Created from ${starter.title} template")
        refresh()
        openProject(created)
    }

    fun createFromIdea() {
        val request = idea.trim()
        if (request.isBlank() || creating) return
        creating = true
        createStatus = if (AiRouter.configuredProviders(vault).isEmpty()) {
            "Creating a safe local starter. Connect AI later for broader generation."
        } else {
            "PocketForge is turning that idea into a working app structure…"
        }

        Thread {
            val result = runCatching {
                val spec = if (AiRouter.configuredProviders(vault).isNotEmpty()) {
                    AiRouter.generateStudioSpec(request, vault).validate()
                } else {
                    val localName = request
                        .replace(Regex("[^A-Za-z0-9 ]"), " ")
                        .trim()
                        .split(Regex("\\s+"))
                        .take(4)
                        .joinToString(" ")
                        .ifBlank { "My app" }
                        .take(50)
                    StudioStarters.all.first().spec.copy(
                        name = localName,
                        tagline = "Built from your idea. Refine it in plain English."
                    )
                }
                val created = workspace.createProject(spec.name)
                studio.save(created.id, spec, "Created from plain-English idea")
                created
            }
            handler.post {
                creating = false
                result.onSuccess { project ->
                    idea = ""
                    createStatus = null
                    refresh()
                    openProject(project)
                }.onFailure { error ->
                    createStatus = error.message ?: "PocketForge could not create that app yet."
                }
            }
        }.start()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = OfficialAccent,
            background = OfficialBg,
            surface = OfficialSurface,
            surfaceVariant = OfficialRaised,
            onPrimary = Color(0xFF07140B),
            onBackground = OfficialText,
            onSurface = OfficialText,
            outline = OfficialLine
        )
    ) {
        Scaffold(
            containerColor = OfficialBg,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0D1611), tonalElevation = 0.dp) {
                    StudioDestination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = {
                                if (item == StudioDestination.CREATE) destination = StudioDestination.HOME
                                else destination = item
                            },
                            icon = {
                                if (item == StudioDestination.CREATE) {
                                    Surface(color = OfficialAccent, shape = CircleShape) {
                                        Text("+", color = Color(0xFF07140B), fontSize = 27.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                    }
                                } else {
                                    Text(item.glyph, fontSize = 18.sp)
                                }
                            },
                            label = { Text(item.label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = OfficialAccent,
                                selectedTextColor = OfficialAccent,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = OfficialMuted,
                                unselectedTextColor = OfficialMuted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            when (destination) {
                StudioDestination.HOME, StudioDestination.CREATE -> OfficialHome(
                    modifier = Modifier.padding(padding),
                    projects = projects,
                    studio = studio,
                    idea = idea,
                    onIdea = { idea = it.take(800) },
                    creating = creating,
                    createStatus = createStatus,
                    onCreate = ::createFromIdea,
                    onProject = { openProject(it) },
                    onPreview = { openProject(it, preview = true) },
                    onNewProject = { destination = StudioDestination.TEMPLATES },
                    onAdvanced = { context.startActivity(Intent(context, AdvancedWorkspaceActivity::class.java)) },
                    onConnections = { context.startActivity(Intent(context, IntegrationCenterActivity::class.java)) },
                    onUpdater = { showUpdater = true },
                    onHelp = { showHelp = true }
                )
                StudioDestination.TEMPLATES -> OfficialTemplates(
                    modifier = Modifier.padding(padding),
                    onCreate = ::createFromStarter,
                    onBack = { destination = StudioDestination.HOME }
                )
                StudioDestination.LIBRARY -> OfficialLibrary(
                    modifier = Modifier.padding(padding),
                    projects = projects,
                    studio = studio,
                    onProject = { openProject(it) },
                    onPreview = { openProject(it, preview = true) }
                )
                StudioDestination.SETTINGS -> OfficialSettings(
                    modifier = Modifier.padding(padding),
                    providerCount = AiRouter.configuredProviders(vault).size,
                    onConnections = { context.startActivity(Intent(context, IntegrationCenterActivity::class.java)) },
                    onAdvanced = { context.startActivity(Intent(context, AdvancedWorkspaceActivity::class.java)) },
                    onUpdater = { showUpdater = true },
                    onHelp = { showHelp = true }
                )
            }
        }
    }

    if (BuildConfig.ENABLE_SIDELOAD_UPDATER && showUpdater) {
        Dialog(onDismissRequest = { showUpdater = false }) {
            Surface(color = OfficialSurface, shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, OfficialLine)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PocketForge updates", color = OfficialText, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showUpdater = false }) { Text("Done") }
                    }
                    PocketForgeUpdaterCard()
                }
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            containerColor = OfficialSurface,
            title = { Text("PocketForge, in plain English") },
            text = {
                Text(
                    "1. Describe an app idea or choose a template.\n\n2. Edit the generated app with normal language.\n\n3. Preview it on your phone.\n\n4. Use Advanced Workspace when you want repository, AI-agent, and build controls.\n\n5. Beta builds keep the in-app updater until production release.",
                    color = OfficialMuted,
                    lineHeight = 19.sp
                )
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Got it") } }
        )
    }
}

@Composable
private fun OfficialHome(
    modifier: Modifier,
    projects: List<WorkspaceProject>,
    studio: StudioStore,
    idea: String,
    onIdea: (String) -> Unit,
    creating: Boolean,
    createStatus: String?,
    onCreate: () -> Unit,
    onProject: (WorkspaceProject) -> Unit,
    onPreview: (WorkspaceProject) -> Unit,
    onNewProject: () -> Unit,
    onAdvanced: () -> Unit,
    onConnections: () -> Unit,
    onUpdater: () -> Unit,
    onHelp: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 760.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { OfficialHeader(onUpdater = onUpdater, onHelp = onHelp) }

        item {
            Surface(
                color = Color(0xFF14251B),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Color(0xFF355440))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("TURN IDEAS INTO APPS", color = OfficialAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("Start with an idea", color = OfficialText, fontSize = 29.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Describe what you want to build in plain English. PocketForge turns it into an app structure you can edit, preview, and build.",
                        color = OfficialMuted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = idea,
                        onValueChange = onIdea,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        placeholder = { Text("Try: A pump-friendly tracker for mothers who are breast pumping") },
                        leadingIcon = { Text("✦", color = OfficialAccent, fontSize = 19.sp) },
                        trailingIcon = {
                            Button(
                                onClick = onCreate,
                                enabled = idea.isNotBlank() && !creating,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = OfficialAccent, contentColor = Color(0xFF07140B))
                            ) { Text("→", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OfficialAccent,
                            unfocusedBorderColor = OfficialLine,
                            focusedContainerColor = Color(0xFF0E1712),
                            unfocusedContainerColor = Color(0xFF0E1712),
                            focusedTextColor = OfficialText,
                            unfocusedTextColor = OfficialText
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    if (creating || createStatus != null) {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (creating) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            if (creating) Spacer(Modifier.width(8.dp))
                            Text(createStatus ?: "Creating…", color = OfficialMuted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TrustPoint("⚡", "Real apps")
                        TrustPoint("▣", "On your device")
                        TrustPoint("◎", "Beginner friendly")
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Your projects", color = OfficialText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onNewProject) { Text("New project  +", color = OfficialAccent) }
            }
        }

        if (projects.isEmpty()) {
            item {
                OfficialEmptyCard(onNewProject)
            }
        } else {
            items(projects.take(5), key = { it.id }) { project ->
                OfficialProjectCard(project, studio, onProject, onPreview)
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeActionCard("◉", "Preview", "Test your latest app", modifier = Modifier.weight(1f)) {
                    projects.firstOrNull()?.let(onPreview) ?: onNewProject()
                }
                HomeActionCard("⌁", "Build", "Create and verify", modifier = Modifier.weight(1f), onClick = onAdvanced)
                HomeActionCard("↗", "Connections", "AI & services", modifier = Modifier.weight(1f), onClick = onConnections)
            }
        }

        item {
            Text(
                "Private by default · durable project memory · verified builds outrank AI confidence",
                color = OfficialMuted,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
    }
}

@Composable
private fun OfficialHeader(onUpdater: () -> Unit, onHelp: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = OfficialAccentSoft, shape = RoundedCornerShape(16.dp), modifier = Modifier.size(52.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("⌁", color = OfficialAccent, fontSize = 29.sp, fontWeight = FontWeight.Black) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("PocketForge", color = OfficialText, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text("Build useful apps in plain English.", color = OfficialMuted, fontSize = 12.sp)
        }
        if (BuildConfig.ENABLE_SIDELOAD_UPDATER) {
            OutlinedButton(
                onClick = onUpdater,
                border = BorderStroke(1.dp, Color(0xFF4C8E5A)),
                shape = RoundedCornerShape(100.dp),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)
            ) { Text("↓  Update", color = OfficialAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        } else {
            TextButton(onClick = onHelp) { Text("Help") }
        }
    }
}

@Composable
private fun TrustPoint(glyph: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = OfficialAccentSoft, shape = CircleShape) {
            Text(glyph, color = OfficialAccent, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, color = OfficialMuted, fontSize = 10.sp)
    }
}

@Composable
private fun OfficialProjectCard(
    project: WorkspaceProject,
    studio: StudioStore,
    onProject: (WorkspaceProject) -> Unit,
    onPreview: (WorkspaceProject) -> Unit
) {
    val current = remember(project.id, project.updatedAt) { studio.latest(project.id)?.spec }
    val accent = officialColorOrFallback(current?.accent)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onProject(project) },
        color = OfficialSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, OfficialLine)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accent.copy(alpha = 0.18f), shape = RoundedCornerShape(15.dp), modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text((current?.name ?: project.name).take(1).uppercase(Locale.US), color = accent, fontWeight = FontWeight.Black, fontSize = 21.sp)
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(current?.name ?: project.name, color = OfficialText, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(current?.tagline ?: "Ready for your next plain-English instruction.", color = OfficialMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(relativeTime(project.updatedAt), color = OfficialMuted.copy(alpha = 0.75f), fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
            }
            TextButton(onClick = { onPreview(project) }) { Text("Preview", color = OfficialAccent, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun OfficialEmptyCard(onCreate: () -> Unit) {
    Surface(color = OfficialSurface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, OfficialLine)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No projects yet", color = OfficialText, fontWeight = FontWeight.Bold)
            Text("Start with a template or describe an idea above.", color = OfficialMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            Button(onClick = onCreate, colors = ButtonDefaults.buttonColors(containerColor = OfficialAccent, contentColor = Color(0xFF07140B))) { Text("Create my first app") }
        }
    }
}

@Composable
private fun HomeActionCard(glyph: String, title: String, detail: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = OfficialSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, OfficialLine)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(glyph, color = OfficialAccent, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(title, color = OfficialText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(detail, color = OfficialMuted, fontSize = 9.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun OfficialTemplates(modifier: Modifier, onCreate: (StudioStarter) -> Unit, onBack: () -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Home") }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("Templates", color = OfficialText, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text("Start useful. Customize everything later.", color = OfficialMuted, fontSize = 12.sp)
                }
            }
        }
        items(StudioStarters.all) { starter ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onCreate(starter) },
                color = OfficialSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, OfficialLine)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = OfficialAccentSoft, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(starter.symbol, color = OfficialAccent, fontSize = 22.sp) }
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(starter.title, color = OfficialText, fontWeight = FontWeight.Bold)
                        Text(starter.detail, color = OfficialMuted, fontSize = 11.sp)
                    }
                    Text("›", color = OfficialAccent, fontSize = 25.sp)
                }
            }
        }
    }
}

@Composable
private fun OfficialLibrary(
    modifier: Modifier,
    projects: List<WorkspaceProject>,
    studio: StudioStore,
    onProject: (WorkspaceProject) -> Unit,
    onPreview: (WorkspaceProject) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Library", color = OfficialText, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("Every app and project on this phone.", color = OfficialMuted, fontSize = 12.sp)
        }
        items(projects, key = { it.id }) { project -> OfficialProjectCard(project, studio, onProject, onPreview) }
    }
}

@Composable
private fun OfficialSettings(
    modifier: Modifier,
    providerCount: Int,
    onConnections: () -> Unit,
    onAdvanced: () -> Unit,
    onUpdater: () -> Unit,
    onHelp: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Settings", color = OfficialText, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("Keep the simple stuff simple. Advanced tools stay one tap away.", color = OfficialMuted, fontSize = 12.sp)
        }
        item { SettingsRow("AI & build connections", "$providerCount AI provider${if (providerCount == 1) "" else "s"} connected", onConnections) }
        item { SettingsRow("Advanced workspace", "Agents, repository tools, build trace and verification", onAdvanced) }
        if (BuildConfig.ENABLE_SIDELOAD_UPDATER) item { SettingsRow("PocketForge updates", "Install the newest verified beta build", onUpdater) }
        item { SettingsRow("Help & product guide", "How projects, previews, builds and AI work", onHelp) }
        item {
            Surface(color = OfficialAccentSoft, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF31553B))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Production hardening", color = OfficialAccent, fontWeight = FontWeight.Bold)
                    Text("Play releases disable the sideload updater. Release builds use shrinking and obfuscation, while premium entitlements and valuable service logic are designed to move server-side.", color = OfficialMuted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, detail: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = OfficialSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, OfficialLine)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = OfficialText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(detail, color = OfficialMuted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Text("›", color = OfficialAccent, fontSize = 24.sp)
        }
    }
}

private fun ensureOfficialDesign(store: StudioStore, projectId: String): StudioSpec {
    return store.latest(projectId)?.spec ?: StudioStarters.all.first().spec.also {
        store.save(projectId, it, "Created a safe starter design")
    }
}

private fun officialColorOrFallback(hex: String?): Color {
    return runCatching {
        if (hex == null || !Regex("#[0-9A-Fa-f]{6}").matches(hex)) return@runCatching OfficialAccent
        Color(android.graphics.Color.parseColor(hex))
    }.getOrDefault(OfficialAccent)
}

private fun relativeTime(time: Long): String {
    val delta = (System.currentTimeMillis() - time).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60L * minute
    val day = 24L * hour
    return when {
        delta < minute -> "Updated just now"
        delta < hour -> "Updated ${delta / minute}m ago"
        delta < day -> "Updated ${delta / hour}h ago"
        delta < 7L * day -> "Updated ${delta / day}d ago"
        else -> "Updated recently"
    }
}
