package com.pocketforge.app

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Locale

private val PfBg = Color(0xFF07100C)
private val PfSurface = Color(0xFF111A15)
private val PfRaised = Color(0xFF17221C)
private val PfHero = Color(0xFF12301F)
private val PfLine = Color(0xFF2A3A30)
private val PfText = Color(0xFFF4F7F2)
private val PfMuted = Color(0xFFAAB4AD)
private val PfAccent = Color(0xFF83F39B)
private val PfAccentSoft = Color(0xFF183D25)
private val PfBlue = Color(0xFF69B8FF)
private val PfPurple = Color(0xFFA66BFF)
private val PfYellow = Color(0xFFFFD86A)
private val PfDanger = Color(0xFFFF7777)

private enum class PhotoDestination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    TEMPLATES("Templates", Icons.Outlined.GridView),
    CREATE("Create", Icons.Outlined.Add),
    LIBRARY("Library", Icons.Outlined.Folder),
    SETTINGS("Settings", Icons.Outlined.Settings)
}

private enum class TopDestination(val label: String, val icon: ImageVector) {
    STUDIO("Studio", Icons.Outlined.Home),
    TEMPLATES("Templates", Icons.Outlined.GridView),
    COMMUNITY("Community", Icons.Outlined.People),
    LEARN("Learn", Icons.Outlined.MenuBook)
}

@Composable
fun PocketForgePhotoSpecApp() {
    val context = LocalContext.current
    val workspace = remember { WorkspaceStore(context) }
    val studio = remember { StudioStore(context) }
    val vault = remember { SecretVault(context) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    var destination by remember { mutableStateOf(PhotoDestination.HOME) }
    var projects by remember { mutableStateOf(workspace.projects()) }
    var idea by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var creationStatus by remember { mutableStateOf<String?>(null) }
    var showUpdater by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf<String?>(null) }
    var showMembership by remember { mutableStateOf(false) }

    fun refresh() { projects = workspace.projects() }

    fun ensureProjectSpec(project: WorkspaceProject): StudioSpec {
        studio.latest(project.id)?.spec?.let { return it }
        val starter = StudioStarters.all.first().spec.copy(name = project.name)
        studio.save(project.id, starter, "Created visual starter")
        return starter
    }

    fun openProject(project: WorkspaceProject, preview: Boolean = false) {
        ensureProjectSpec(project)
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
        creationStatus = if (AiRouter.configuredProviders(vault).isEmpty()) {
            "Creating a safe local starter…"
        } else {
            "PocketForge is turning your idea into an app…"
        }
        Thread {
            val result = runCatching {
                val spec = if (AiRouter.configuredProviders(vault).isNotEmpty()) {
                    AiRouter.generateStudioSpec(request, vault).validate()
                } else {
                    val name = request.replace(Regex("[^A-Za-z0-9 ]"), " ")
                        .trim().split(Regex("\\s+")).take(4).joinToString(" ")
                        .ifBlank { "My app" }.take(50)
                    StudioStarters.all.first().spec.copy(
                        name = name,
                        tagline = "Built from your idea. Refine it in plain English."
                    )
                }
                val project = workspace.createProject(spec.name)
                studio.save(project.id, spec, "Created from plain-English idea")
                project
            }
            handler.post {
                creating = false
                result.onSuccess { project ->
                    idea = ""
                    creationStatus = null
                    refresh()
                    openProject(project)
                }.onFailure { error ->
                    creationStatus = error.message ?: "PocketForge could not create that app yet."
                }
            }
        }.start()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PfAccent,
            background = PfBg,
            surface = PfSurface,
            surfaceVariant = PfRaised,
            onPrimary = Color(0xFF06140A),
            onBackground = PfText,
            onSurface = PfText,
            outline = PfLine,
            error = PfDanger
        ),
        typography = PocketForgeTypography
    ) {
        Scaffold(
            containerColor = PfBg,
            bottomBar = {
                PhotoBottomBar(destination) { selected ->
                    destination = if (selected == PhotoDestination.CREATE) PhotoDestination.HOME else selected
                }
            }
        ) { padding ->
            when (destination) {
                PhotoDestination.HOME, PhotoDestination.CREATE -> PhotoHomeScreen(
                    modifier = Modifier.padding(padding),
                    projects = projects,
                    studio = studio,
                    idea = idea,
                    onIdea = { idea = it.take(900) },
                    creating = creating,
                    status = creationStatus,
                    onCreate = ::createFromIdea,
                    onTemplates = { destination = PhotoDestination.TEMPLATES },
                    onProject = { openProject(it) },
                    onPreview = { openProject(it, true) },
                    onAdvanced = { context.startActivity(Intent(context, AdvancedWorkspaceActivity::class.java)) },
                    onConnections = { context.startActivity(Intent(context, IntegrationCenterActivity::class.java)) },
                    onUpdater = { showUpdater = true },
                    onCommunity = { showInfo = "Community is part of the visual spec and will be enabled once account/community services are connected." },
                    onLearn = { showInfo = "PocketForge Learn is coming as a guided non-coder onboarding flow." }
                )
                PhotoDestination.TEMPLATES -> PhotoTemplatesScreen(
                    modifier = Modifier.padding(padding),
                    onStarter = ::createFromStarter,
                    onDescribe = { destination = PhotoDestination.HOME },
                    onUpdater = { showUpdater = true },
                    onCommunity = { showInfo = "Community templates will appear here after the community backend is connected." },
                    onLearn = { showInfo = "Guided tutorials will live here once the core builder is locked." }
                )
                PhotoDestination.LIBRARY -> PhotoLibraryScreen(
                    modifier = Modifier.padding(padding),
                    projects = projects,
                    studio = studio,
                    onProject = { openProject(it) },
                    onPreview = { openProject(it, true) },
                    onTemplates = { destination = PhotoDestination.TEMPLATES }
                )
                PhotoDestination.SETTINGS -> PhotoSettingsScreen(
                    modifier = Modifier.padding(padding),
                    providerCount = AiRouter.configuredProviders(vault).size,
                    onConnections = { context.startActivity(Intent(context, IntegrationCenterActivity::class.java)) },
                    onAdvanced = { context.startActivity(Intent(context, AdvancedWorkspaceActivity::class.java)) },
                    onUpdater = { showUpdater = true },
                    onMembership = { showMembership = true },
                    onInfo = { showInfo = it }
                )
            }
        }
    }

    if (BuildConfig.ENABLE_SIDELOAD_UPDATER && showUpdater) {
        Dialog(onDismissRequest = { showUpdater = false }) {
            Surface(
                color = PfSurface,
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, PfLine)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PocketForge updates", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showUpdater = false }) { Text("Done") }
                    }
                    PocketForgeUpdaterCard()
                }
            }
        }
    }

    showInfo?.let { message ->
        AlertDialog(
            onDismissRequest = { showInfo = null },
            containerColor = PfSurface,
            title = { Text("PocketForge") },
            text = { Text(message, color = PfMuted) },
            confirmButton = { TextButton(onClick = { showInfo = null }) { Text("Got it") } }
        )
    }

    if (showMembership) {
        PhotoMembershipDialog(onDismiss = { showMembership = false })
    }
}

@Composable
private fun PhotoHomeScreen(
    modifier: Modifier,
    projects: List<WorkspaceProject>,
    studio: StudioStore,
    idea: String,
    onIdea: (String) -> Unit,
    creating: Boolean,
    status: String?,
    onCreate: () -> Unit,
    onTemplates: () -> Unit,
    onProject: (WorkspaceProject) -> Unit,
    onPreview: (WorkspaceProject) -> Unit,
    onAdvanced: () -> Unit,
    onConnections: () -> Unit,
    onUpdater: () -> Unit,
    onCommunity: () -> Unit,
    onLearn: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PhotoHeader(onUpdater)
            Spacer(Modifier.height(16.dp))
            PhotoTopTabs(
                selected = TopDestination.STUDIO,
                onSelected = {
                    when (it) {
                        TopDestination.STUDIO -> Unit
                        TopDestination.TEMPLATES -> onTemplates()
                        TopDestination.COMMUNITY -> onCommunity()
                        TopDestination.LEARN -> onLearn()
                    }
                }
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, Color(0xFF335842))
            ) {
                Column(
                    Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF173D27), Color(0xFF10271C), Color(0xFF111A15))
                            )
                        )
                        .padding(22.dp)
                ) {
                    Text("TURN IDEAS INTO APPS", color = PfAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Start with an idea", color = PfText, fontSize = 31.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        "Describe what you want to build in plain English. PocketForge will turn it into a real, working app.",
                        color = Color(0xFFD5DED8),
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = idea,
                        onValueChange = onIdea,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Describe your app idea…", color = PfMuted) },
                        leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, tint = PfAccent) },
                        trailingIcon = {
                            FilledIconButton(
                                onClick = onCreate,
                                enabled = idea.isNotBlank() && !creating,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = PfAccent, contentColor = Color(0xFF07140B)),
                                modifier = Modifier.size(48.dp)
                            ) { Icon(Icons.Outlined.ArrowForward, null) }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xAA0D1711),
                            unfocusedContainerColor = Color(0xAA0D1711),
                            focusedBorderColor = Color(0xFF4A6A55),
                            unfocusedBorderColor = Color(0xFF3A4A40),
                            focusedTextColor = PfText,
                            unfocusedTextColor = PfText
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
                    if (creating || status != null) {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (creating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            if (creating) Spacer(Modifier.width(8.dp))
                            Text(status ?: "Creating…", color = PfMuted, fontSize = 11.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeroSubAction(Icons.Outlined.Description, "Choose a starter", "Try an example idea", Modifier.weight(1f), onTemplates)
                        HeroSubAction(Icons.Outlined.Lightbulb, "Need inspiration?", "Browse popular ideas", Modifier.weight(1f), onTemplates)
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PhotoActionTile(Icons.Outlined.Visibility, "Preview", "Test on device", PfBlue, Modifier.weight(1f)) {
                    projects.firstOrNull()?.let(onPreview) ?: onTemplates()
                }
                PhotoActionTile(Icons.Outlined.Build, "Build", "Create an APK", Color(0xFFC5D2DB), Modifier.weight(1f), onAdvanced)
                PhotoActionTile(Icons.Outlined.Link, "Connections", "APIs & services", PfAccent, Modifier.weight(1f), onConnections)
                PhotoActionTile(Icons.Outlined.Tune, "Advanced", "Full workspace", PfAccent, Modifier.weight(1f), onAdvanced)
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your apps", color = PfText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onTemplates) { Text("See all  ›", color = PfAccent) }
            }
        }

        if (projects.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onTemplates),
                    color = PfSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, PfLine)
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        PhotoIconTile(Icons.Outlined.Add, PfAccent)
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text("Create your first app", color = PfText, fontWeight = FontWeight.Bold)
                            Text("Start with a template or describe an idea.", color = PfMuted, fontSize = 12.sp)
                        }
                        Icon(Icons.Outlined.ArrowForward, null, tint = PfMuted)
                    }
                }
            }
        } else {
            items(projects.take(3), key = { it.id }) { project ->
                val spec = studio.latest(project.id)?.spec
                PhotoProjectCard(project, spec, onProject, onPreview)
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onTemplates),
                color = Color.Transparent,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF3B7D4C))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = PfAccent, shape = CircleShape, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, null, tint = Color(0xFF07140B)) }
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text("Create a new app", color = PfText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Turn your next idea into something real", color = PfMuted, fontSize = 11.sp)
                    }
                    Icon(Icons.Outlined.ArrowForward, null, tint = PfMuted)
                }
            }
        }

        item {
            Surface(
                color = PfSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, PfLine),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.School, null, tint = PfAccent)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("New to app building?", color = PfText, fontWeight = FontWeight.SemiBold)
                        Text("PocketForge keeps the code out of your way.", color = PfMuted, fontSize = 11.sp)
                    }
                    Icon(Icons.Outlined.ArrowForward, null, tint = PfMuted)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PhotoTemplatesScreen(
    modifier: Modifier,
    onStarter: (StudioStarter) -> Unit,
    onDescribe: () -> Unit,
    onUpdater: () -> Unit,
    onCommunity: () -> Unit,
    onLearn: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var chip by remember { mutableStateOf("Starters") }
    val filtered = StudioStarters.all.filter {
        search.isBlank() || it.title.contains(search, true) || it.detail.contains(search, true)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PhotoHeader(onUpdater)
            Spacer(Modifier.height(16.dp))
            PhotoTopTabs(TopDestination.TEMPLATES) {
                when (it) {
                    TopDestination.STUDIO -> onDescribe()
                    TopDestination.TEMPLATES -> Unit
                    TopDestination.COMMUNITY -> onCommunity()
                    TopDestination.LEARN -> onLearn()
                }
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it.take(60) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search templates…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { Icon(Icons.Outlined.FilterList, null, tint = PfMuted) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PfSurface,
                    unfocusedContainerColor = PfSurface,
                    focusedBorderColor = Color(0xFF405347),
                    unfocusedBorderColor = PfLine
                )
            )
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Starters", "Popular", "Business", "Lifestyle", "Utility", "Education").forEach { item ->
                    FilterChip(selected = chip == item, onClick = { chip = item }, label = { Text(item) })
                }
            }
        }
        StudioStarters.all.firstOrNull()?.let { featured ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, Color(0xFF335842))
                ) {
                    Column(
                        Modifier.background(Brush.linearGradient(listOf(Color(0xFF173D27), Color(0xFF10271C)))).padding(20.dp)
                    ) {
                        Text("FEATURED STARTER", color = PfAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                        Text(featured.title, color = PfText, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp))
                        Text(featured.detail, color = Color(0xFFD5DED8), fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 5.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(onClick = {}, label = { Text("Beginner friendly") }, leadingIcon = { Icon(Icons.Outlined.CheckCircle, null, Modifier.size(15.dp)) })
                            Spacer(Modifier.weight(1f))
                            Button(
                                onClick = { onStarter(featured) },
                                colors = ButtonDefaults.buttonColors(containerColor = PfAccent, contentColor = Color(0xFF07140B)),
                                shape = RoundedCornerShape(22.dp)
                            ) { Text("Use starter  →", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
        item {
            Text("All templates", color = PfText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        items(filtered, key = { it.title }) { starter ->
            PhotoTemplateCard(starter, onStarter)
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDescribe),
                color = PfSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, PfLine)
            ) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = PfAccent, shape = CircleShape, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, null, tint = Color(0xFF07140B)) }
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text("Describe my own idea", color = PfText, fontWeight = FontWeight.Bold)
                        Text("Turn your idea into a custom app with AI.", color = PfMuted, fontSize = 11.sp)
                    }
                    Icon(Icons.Outlined.ArrowForward, null, tint = PfMuted)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PhotoLibraryScreen(
    modifier: Modifier,
    projects: List<WorkspaceProject>,
    studio: StudioStore,
    onProject: (WorkspaceProject) -> Unit,
    onPreview: (WorkspaceProject) -> Unit,
    onTemplates: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Library", color = PfText, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Every app you create stays organized here.", color = PfMuted, modifier = Modifier.padding(top = 4.dp))
        }
        if (projects.isEmpty()) {
            item {
                Button(onClick = onTemplates, colors = ButtonDefaults.buttonColors(containerColor = PfAccent, contentColor = Color(0xFF07140B))) {
                    Text("Choose a starter")
                }
            }
        } else {
            items(projects, key = { it.id }) { project ->
                PhotoProjectCard(project, studio.latest(project.id)?.spec, onProject, onPreview)
            }
        }
    }
}

@Composable
private fun PhotoSettingsScreen(
    modifier: Modifier,
    providerCount: Int,
    onConnections: () -> Unit,
    onAdvanced: () -> Unit,
    onUpdater: () -> Unit,
    onMembership: () -> Unit,
    onInfo: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Settings & Connections", color = PfText, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Connect your tools. Customize your experience.", color = PfMuted, fontSize = 13.sp)
                }
                OutlinedButton(onClick = onMembership, shape = RoundedCornerShape(22.dp)) { Text("Go Premium") }
            }
        }
        item {
            SettingsCard(Icons.Outlined.AutoAwesome, "AI Providers", "Connect AI providers to power app generation.") {
                SettingsRow("Connected providers", "$providerCount active", if (providerCount > 0) PfAccent else PfYellow, onConnections)
                SettingsRow("Open provider settings", "Gemini · OpenRouter · Groq", PfMuted, onConnections)
            }
        }
        item {
            SettingsCard(Icons.Outlined.Code, "GitHub", "Connect repositories and keep generated code in sync.") {
                Button(onClick = onAdvanced, colors = ButtonDefaults.buttonColors(containerColor = PfAccent, contentColor = Color(0xFF07140B))) { Text("Open Advanced Workspace") }
            }
        }
        item {
            SettingsCard(Icons.Outlined.Extension, "Build Connectors", "GitHub Actions, Codemagic, Bitrise and future services.") {
                SettingsRow("Manage build connections", "Build and verify APKs", PfAccent, onConnections)
            }
        }
        item {
            SettingsCard(Icons.Outlined.Security, "Security", "Your beta secrets stay encrypted with Android Keystore.") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecurityPoint(Icons.Outlined.Key, "Encrypted keys", Modifier.weight(1f))
                    SecurityPoint(Icons.Outlined.VerifiedUser, "Verified builds", Modifier.weight(1f))
                    SecurityPoint(Icons.Outlined.VisibilityOff, "Private by default", Modifier.weight(1f))
                }
            }
        }
        item {
            SettingsCard(Icons.Outlined.Science, "Beta Updater", "Early updates stay available while PocketForge is being debugged.") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (BuildConfig.ENABLE_SIDELOAD_UPDATER) "Enabled in this beta" else "Removed from Play release", color = PfAccent, modifier = Modifier.weight(1f))
                    if (BuildConfig.ENABLE_SIDELOAD_UPDATER) Button(onClick = onUpdater) { Text("Open") }
                }
            }
        }
        item {
            SettingsCard(Icons.Outlined.Settings, "General Settings", "PocketForge production preferences.") {
                SettingsRow("Appearance", "System dark", PfMuted) { onInfo("Appearance controls will follow the approved Design screen system after the visual parity pass.") }
                SettingsRow("Notifications", "Build and update status", PfMuted) { onInfo("Notification controls will be enabled with the production backend.") }
                SettingsRow("Project backup", "Local project memory", PfMuted) { onInfo("Cloud backup will be attached to Premium after the account backend is ready.") }
            }
        }
        item { Spacer(Modifier.height(6.dp)) }
    }
}

@Composable
private fun PhotoHeader(onUpdater: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SproutMark(58.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("PocketForge", color = PfText, fontSize = 27.sp, fontWeight = FontWeight.Black)
            Text("Build useful apps in plain English.", color = PfMuted, fontSize = 13.sp)
        }
        if (BuildConfig.ENABLE_SIDELOAD_UPDATER) {
            OutlinedButton(
                onClick = onUpdater,
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, Color(0xFF4B9D5D)),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 9.dp)
            ) {
                Icon(Icons.Outlined.Update, null, Modifier.size(18.dp), tint = PfAccent)
                Spacer(Modifier.width(6.dp))
                Text("Update", color = PfAccent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PhotoTopTabs(selected: TopDestination, onSelected: (TopDestination) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TopDestination.entries.forEach { item ->
            val active = item == selected
            Column(
                Modifier.clickable { onSelected(item) }.padding(horizontal = 4.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, null, tint = if (active) PfAccent else Color(0xFFD3DBD6), modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(item.label, color = if (active) PfAccent else Color(0xFFD3DBD6), fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.height(3.dp).width(66.dp).background(if (active) PfAccent else Color.Transparent, RoundedCornerShape(2.dp)))
            }
        }
    }
    HorizontalDivider(color = PfLine)
}

@Composable
private fun PhotoBottomBar(selected: PhotoDestination, onSelected: (PhotoDestination) -> Unit) {
    NavigationBar(containerColor = Color(0xFF0B1510), tonalElevation = 0.dp) {
        PhotoDestination.entries.forEach { item ->
            NavigationBarItem(
                selected = selected == item,
                onClick = { onSelected(item) },
                icon = {
                    if (item == PhotoDestination.CREATE) {
                        Surface(color = PfAccent, shape = CircleShape, modifier = Modifier.size(52.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, null, tint = Color(0xFF07140B), modifier = Modifier.size(29.dp)) }
                        }
                    } else {
                        Icon(item.icon, null, modifier = Modifier.size(24.dp))
                    }
                },
                label = { Text(item.label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PfAccent,
                    selectedTextColor = PfAccent,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = Color(0xFFD4DDD7),
                    unselectedTextColor = Color(0xFFD4DDD7)
                )
            )
        }
    }
}

@Composable
private fun HeroSubAction(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = Color(0x66101813),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF496354))
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = PfAccent)
            Column(Modifier.padding(start = 9.dp)) {
                Text(title, color = PfText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(subtitle, color = PfMuted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun PhotoActionTile(icon: ImageVector, title: String, subtitle: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(120.dp).clickable(onClick = onClick),
        color = PfSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PfLine)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
            Column {
                Text(title, color = PfText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(subtitle, color = PfMuted, fontSize = 9.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun PhotoProjectCard(project: WorkspaceProject, spec: StudioSpec?, onProject: (WorkspaceProject) -> Unit, onPreview: (WorkspaceProject) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onProject(project) },
        color = PfSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, PfLine)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            val accent = spec?.accent?.let(::parseColor) ?: PfAccent
            Surface(color = accent.copy(alpha = .15f), shape = RoundedCornerShape(14.dp), modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text((spec?.name ?: project.name).take(1).uppercase(Locale.US), color = accent, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(spec?.name ?: project.name, color = PfText, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(spec?.tagline ?: "Ready to edit", color = PfMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Saved project", color = PfMuted.copy(alpha = .75f), fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
            }
            OutlinedButton(onClick = { onPreview(project) }, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)) {
                Icon(Icons.Outlined.Visibility, null, Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Preview", fontSize = 10.sp)
            }
            Icon(Icons.Outlined.MoreVert, null, tint = PfMuted, modifier = Modifier.padding(start = 3.dp))
        }
    }
}

@Composable
private fun PhotoTemplateCard(starter: StudioStarter, onStarter: (StudioStarter) -> Unit) {
    Surface(color = PfSurface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, PfLine), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = PfAccentSoft, shape = RoundedCornerShape(13.dp), modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(starter.symbol, color = PfAccent, fontSize = 22.sp) }
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(starter.title, color = PfText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(starter.detail, color = PfMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Outlined.MoreVert, null, tint = PfMuted)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onStarter(starter) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Outlined.Visibility, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Preview", fontSize = 10.sp)
                }
                OutlinedButton(onClick = { onStarter(starter) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF45A05A))) {
                    Text("Use starter", color = PfAccent, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(icon: ImageVector, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = PfSurface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, PfLine), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = PfAccentSoft, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = PfAccent) }
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text(title, color = PfText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(subtitle, color = PfMuted, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsRow(title: String, detail: String, statusColor: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = PfText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = statusColor, fontSize = 10.sp)
        }
        Icon(Icons.Outlined.ArrowForward, null, tint = PfMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SecurityPoint(icon: ImageVector, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = PfAccent)
        Text(label, color = PfMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun PhotoIconTile(icon: ImageVector, tint: Color) {
    Surface(color = tint.copy(alpha = .13f), shape = RoundedCornerShape(13.dp), modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint) }
    }
}

@Composable
private fun SproutMark(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Surface(color = PfAccentSoft, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Eco, null, tint = PfAccent, modifier = Modifier.size(size * .62f))
            }
        }
    }
}

@Composable
private fun PhotoMembershipDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = PfBg, shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, PfLine)) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Eco, null, tint = PfAccent)
                    Column(Modifier.padding(start = 9.dp).weight(1f)) {
                        Text("PocketForge", color = PfText, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("Membership", color = PfMuted, fontSize = 11.sp)
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text("More ideas. More power.", color = PfText, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 13.dp))
                Text("Choose the plan that’s right for you.", color = PfMuted, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                PlanCard("Free", "$0", "A great place to start.", listOf("Up to 3 projects", "Basic app generation", "Build on your device"), false)
                Spacer(Modifier.height(10.dp))
                PlanCard("Founders Lifetime", "$49 one-time", "Limited-time early offer.", listOf("Unlimited projects", "Premium AI capacity", "Cloud builds", "Lifetime access"), true)
                Spacer(Modifier.height(10.dp))
                PlanCard("Premium Monthly", "$9.99 / month", "For new members after the Founders cutoff.", listOf("Unlimited projects", "Premium AI capacity", "New features", "Priority support"), false)
                Text("Billing is not active yet. This screen is the approved visual structure; purchase buttons will only go live with server-verified Play Billing.", color = PfMuted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun PlanCard(title: String, price: String, subtitle: String, benefits: List<String>, featured: Boolean) {
    Surface(
        color = if (featured) Color(0xFF102A1A) else PfSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (featured) 1.5.dp else 1.dp, if (featured) PfAccent else PfLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = PfText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(subtitle, color = PfMuted, fontSize = 9.sp)
                }
                Text(price, color = PfText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            benefits.forEach {
                Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = PfAccent, modifier = Modifier.size(14.dp))
                    Text(it, color = Color(0xFFD6DED8), fontSize = 9.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

private fun parseColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(PfAccent)
