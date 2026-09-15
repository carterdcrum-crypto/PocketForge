package com.pocketforge.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Dialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Locale

private val StudioBg = Color(0xFF0A0D0B)
private val StudioCard = Color(0xFF141A15)
private val StudioRaised = Color(0xFF1C251D)
private val StudioLine = Color(0xFF2F3A30)
private val StudioText = Color(0xFFF4F7F1)
private val StudioMuted = Color(0xFFA7B2A6)
private val StudioAccent = Color(0xFFB9F178)

class AdvancedWorkspaceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AgentWorkspaceV2App() }
    }
}

class StudioHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PocketForgeStudioApp() }
    }
}

class StudioEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
        setContent { PocketForgeEditorApp(projectId = projectId, onClose = ::finish) }
    }
}

private const val EXTRA_PROJECT_ID = "pocketforge.project_id"

@Composable
fun PocketForgeStudioApp() {
    val context = LocalContext.current
    val workspace = remember { WorkspaceStore(context) }
    val studio = remember { StudioStore(context) }
    var projects by remember { mutableStateOf(workspace.projects()) }
    var showStarters by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    fun open(project: WorkspaceProject, preview: Boolean = false) {
        ensureDesign(studio, project.id)
        val target = if (preview) GeneratedPreviewActivity::class.java else StudioEditorActivity::class.java
        context.startActivity(Intent(context, target).putExtra(EXTRA_PROJECT_ID, project.id))
    }

    MaterialTheme(colorScheme = studioColors()) {
        Scaffold(containerColor = StudioBg) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().widthIn(max = 760.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = StudioAccent, shape = RoundedCornerShape(12.dp)) {
                                    Text("P", color = Color(0xFF132011), fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text("PocketForge", color = StudioText, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                            Text("Build useful apps in plain English.", color = StudioMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                        TextButton(onClick = { showHelp = true }) { Text("Help") }
                    }
                }
                item {
                    OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = StudioCard), border = BorderStroke(1.dp, StudioLine), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Start with what you want to make", color = StudioText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("Choose a ready-made starting point or describe your own idea. You never need to open a source file.", color = StudioMuted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
                            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { showStarters = true }, colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = Color(0xFF132011)), modifier = Modifier.weight(1f)) { Text("Choose a starter") }
                                OutlinedButton(onClick = { showStarters = true }, modifier = Modifier.weight(1f)) { Text("Describe an idea") }
                            }
                        }
                    }
                }
                item { Text("YOUR APPS", color = StudioMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 8.dp)) }
                items(projects, key = { it.id }) { project ->
                    val current = studio.latest(project.id)?.spec
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().clickable { open(project) },
                        colors = CardDefaults.outlinedCardColors(containerColor = StudioCard),
                        border = BorderStroke(1.dp, StudioLine), shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = colorOrFallback(current?.accent), shape = CircleShape, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Text((current?.name ?: project.name).take(1).uppercase(Locale.US), color = Color(0xFF142012), fontWeight = FontWeight.Black, fontSize = 20.sp) } }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(current?.name ?: project.name, color = StudioText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(current?.tagline ?: "Ready for your first plain-English instruction.", color = StudioMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                            }
                            TextButton(onClick = { open(project, preview = true) }) { Text("Preview") }
                        }
                    }
                }
                item {
                    HorizontalDivider(color = StudioLine, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { context.startActivity(Intent(context, AdvancedWorkspaceActivity::class.java)) }, modifier = Modifier.weight(1f)) { Text("Advanced workspace") }
                        OutlinedButton(onClick = { context.startActivity(Intent(context, IntegrationCenterActivity::class.java)) }, modifier = Modifier.weight(1f)) { Text("Connections") }
                    }
                    Text("Your apps are saved on this phone. No account is required for starter apps.", color = StudioMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
                }
            }
        }
    }

    if (showStarters) {
        StarterDialog(
            onDismiss = { showStarters = false },
            onStarter = { starter ->
                val created = workspace.createProject(starter.spec.name)
                studio.save(created.id, starter.spec, "Created from ${starter.title} starter")
                projects = workspace.projects()
                showStarters = false
                open(created)
            },
            onDescribe = { name ->
                val created = workspace.createProject(name)
                studio.save(created.id, StudioStarters.all.first().spec.copy(name = name), "Created a safe starter for a new idea")
                projects = workspace.projects()
                showStarters = false
                open(created)
            }
        )
    }

    if (showHelp) HelpDialog { showHelp = false }
}

@Composable
private fun StarterDialog(onDismiss: () -> Unit, onStarter: (StudioStarter) -> Unit, onDescribe: (String) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = StudioCard, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(if (custom) "Describe your app" else "Choose a starting point", color = StudioText, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f)); TextButton(onClick = onDismiss) { Text("Close") } }
                if (custom) {
                    Text("Give it a name now. You can explain the rest after the preview opens.", color = StudioMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("App name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { onDescribe(name.trim()) }, enabled = name.trim().isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 14.dp), colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = Color(0xFF132011))) { Text("Open my app") }
                    TextButton(onClick = { custom = false }, modifier = Modifier.fillMaxWidth()) { Text("Back to starters") }
                } else {
                    Text("These work without API keys, accounts, or setup. Change everything later in plain English.", color = StudioMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    StudioStarters.all.forEach { starter ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onStarter(starter) }, colors = CardDefaults.outlinedCardColors(containerColor = StudioRaised), border = BorderStroke(1.dp, StudioLine), shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(starter.symbol, color = StudioAccent, fontSize = 22.sp, modifier = Modifier.width(34.dp))
                                Column { Text(starter.title, color = StudioText, fontWeight = FontWeight.SemiBold); Text(starter.detail, color = StudioMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)) }
                            }
                        }
                    }
                    OutlinedButton(onClick = { custom = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Describe my own idea") }
                }
            }
        }
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = StudioCard, title = { Text("PocketForge, in plain English") }, text = { Text("1. Pick a starter or name an idea.\n\n2. Open Preview to use the real app.\n\n3. In Edit, type changes like “rename the app to Family Budget,” “add a screen called shopping,” or “make it green.”\n\n4. Save a version before you share or build. PocketForge keeps recent versions and protects existing fields from accidental replacement.\n\nAI keys are optional for starters. They only add broader conversational design changes.", color = StudioMuted, lineHeight = 18.sp) }, confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } })
}

@Composable
fun PocketForgeEditorApp(projectId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val studio = remember { StudioStore(context) }
    val workspace = remember { WorkspaceStore(context) }
    val project = remember(projectId) { workspace.projects().firstOrNull { it.id == projectId } }
    var spec by remember { mutableStateOf(ensureDesign(studio, projectId)) }
    var prompt by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Describe one change at a time. Your current app stays safe until you save it.") }
    var busy by remember { mutableStateOf(false) }
    var showScreens by remember { mutableStateOf(false) }
    var showVersions by remember { mutableStateOf(false) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val vault = remember { SecretVault(context) }

    fun saveVersion(summary: String) {
        runCatching { studio.save(projectId, spec, summary) }.onSuccess { status = "Saved as a new version. Your preview is ready." }.onFailure { status = it.message ?: "Could not save this version." }
    }

    fun applyChange() {
        if (busy || prompt.trim().isBlank()) return
        val request = prompt.trim(); busy = true; status = "PocketForge is shaping that change…"
        Thread {
            val result = runCatching {
                if (AiRouter.configuredProviders(vault).isNotEmpty()) {
                    AiRouter.generateStudioSpec("Current app design:\n${spec.json()}\n\nRequested change:\n$request\n\nPreserve every existing screen and field unless the user explicitly asks for an addition.", vault).validate(spec)
                } else {
                    applySimpleChange(spec, request).first.validate(spec)
                }
            }
            handler.post {
                busy = false
                result.onSuccess { next -> spec = next; prompt = ""; status = "Change ready. Tap Save version when it looks right." }
                    .onFailure { error -> status = "I kept your current app safe. ${error.message ?: "That change needs a little more detail."}" }
            }
        }.start()
    }

    MaterialTheme(colorScheme = studioColors()) {
        Scaffold(containerColor = StudioBg) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text("‹ Apps") }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) { Text(spec.name, color = StudioText, fontWeight = FontWeight.Black, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("No-code editor", color = StudioMuted, fontSize = 11.sp) }
                    TextButton(onClick = { showVersions = true }) { Text("Versions") }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = StudioCard), border = BorderStroke(1.dp, StudioLine), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("App identity", color = StudioMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        OutlinedTextField(value = spec.name, onValueChange = { spec = spec.copy(name = it.take(60)) }, label = { Text("App name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 7.dp))
                        OutlinedTextField(value = spec.tagline, onValueChange = { spec = spec.copy(tagline = it.take(180)) }, label = { Text("One-line description") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        Text("Color", color = StudioMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("#B9F178", "#8BC8FF", "#F8BD8D", "#D4B5FF", "#7DDED5").forEach { color ->
                                FilterChip(selected = spec.accent.equals(color, true), onClick = { spec = spec.copy(accent = color) }, label = { Text("●") })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = StudioCard), border = BorderStroke(1.dp, StudioLine), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("What should change?", color = StudioText, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Use normal words. Examples: add a shopping screen, rename the app, or make it green.", color = StudioMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 5.dp)) }; Text(if (AiRouter.configuredProviders(vault).isEmpty()) "Starter mode" else "AI assist", color = StudioAccent, fontSize = 10.sp) }
                        OutlinedTextField(value = prompt, onValueChange = { prompt = it }, placeholder = { Text("Tell PocketForge what to do…") }, modifier = Modifier.fillMaxWidth().height(112.dp).padding(top = 12.dp))
                        Button(onClick = ::applyChange, enabled = !busy && prompt.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 10.dp), colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = Color(0xFF132011))) { Text(if (busy) "Working…" else "Apply change") }
                        Text(status, color = if (status.contains("safe", true)) StudioMuted else StudioAccent, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = StudioCard), border = BorderStroke(1.dp, StudioLine), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Screens", color = StudioText, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f)); TextButton(onClick = { showScreens = true }) { Text("Manage") } }
                        spec.screens.forEach { screen ->
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) { Surface(color = colorOrFallback(spec.accent), shape = CircleShape, modifier = Modifier.size(32.dp)) { Box(contentAlignment = Alignment.Center) { Text(screen.title.take(1), color = Color(0xFF132011), fontWeight = FontWeight.Bold) } }; Column(Modifier.padding(start = 10.dp)) { Text(screen.title, color = StudioText, fontWeight = FontWeight.SemiBold); Text(screen.type.replaceFirstChar { it.uppercase() } + " · " + screen.fields.size + " fields", color = StudioMuted, fontSize = 11.sp) } }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { studio.save(projectId, spec, "Saved from editor"); context.startActivity(Intent(context, GeneratedPreviewActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = Color(0xFF132011))) { Text("Save & preview") }
                    OutlinedButton(onClick = { saveVersion("Saved from editor") }, modifier = Modifier.weight(1f)) { Text("Save version") }
                }
                OutlinedButton(onClick = { shareDesign(context, spec) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Share / back up app design") }
                Text("Preview is the real app surface. Starter apps work offline; connected AI adds broader design changes. PocketForge never hides a build failure behind a green badge.", color = StudioMuted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 14.dp))
            }
        }
    }

    if (showScreens) ScreenManagerDialog(spec, onDismiss = { showScreens = false }, onChange = { spec = it })
    if (showVersions) VersionDialog(studio.versions(projectId), onDismiss = { showVersions = false }, onRestore = { restored -> spec = restored; showVersions = false; status = "Version restored in the editor. Save it as a new version when ready." })
}

@Composable
private fun ScreenManagerDialog(spec: StudioSpec, onDismiss: () -> Unit, onChange: (StudioSpec) -> Unit) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("list") }
    var menu by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = StudioCard, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Manage screens", color = StudioText, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f)); TextButton(onClick = onDismiss) { Text("Done") } }
                Text("Existing screens and fields are protected. Add another screen below.", color = StudioMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                spec.screens.forEach { screen -> Text("• ${screen.title} · ${screen.type}", color = StudioText, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                HorizontalDivider(color = StudioLine, modifier = Modifier.padding(vertical = 12.dp))
                OutlinedTextField(value = title, onValueChange = { title = it.take(24) }, label = { Text("New screen name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text("Type: ${type.replaceFirstChar { it.uppercase() }}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { StudioSpec.TYPES.forEach { option -> DropdownMenuItem(text = { Text(option.replaceFirstChar { it.uppercase() }) }, onClick = { type = option; menu = false }) } }
                }
                Button(onClick = { val clean = title.trim(); if (clean.isNotBlank() && spec.screens.size < 5) { val id = clean.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "screen_${spec.screens.size + 1}" }.take(32); val unique = if (spec.screens.any { it.id == id }) "${id}_${spec.screens.size + 1}" else id; val fields = when (type) { "info" -> emptyList(); "calculator" -> listOf(StudioField("quantity", "Quantity", "number"), StudioField("rate", "Rate", "number")); else -> listOf(StudioField("entry", "Entry")) }; onChange(spec.copy(screens = spec.screens + StudioScreen(unique, clean, type, "A space you can shape later.", fields, operation = if (type == "calculator") "product" else "sum", unit = if (type == "ledger") "$" else ""))); title = "" } }, enabled = title.trim().isNotBlank() && spec.screens.size < 5, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = Color(0xFF132011))) { Text("Add screen") }
            }
        }
    }
}

@Composable
private fun VersionDialog(versions: List<StudioVersion>, onDismiss: () -> Unit, onRestore: (StudioSpec) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = StudioCard, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Saved versions", color = StudioText, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f)); TextButton(onClick = onDismiss) { Text("Done") } }
                if (versions.isEmpty()) Text("No saved versions yet.", color = StudioMuted, modifier = Modifier.padding(top = 16.dp))
                versions.sortedByDescending { it.time }.forEach { version ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.outlinedCardColors(containerColor = StudioRaised), border = BorderStroke(1.dp, StudioLine), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(version.summary, color = StudioText, fontWeight = FontWeight.SemiBold); Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(version.time)), color = StudioMuted, fontSize = 11.sp) }; TextButton(onClick = { onRestore(version.spec) }) { Text("Restore") } }
                    }
                }
            }
        }
    }
}

fun ensureDesign(store: StudioStore, projectId: String): StudioSpec = store.latest(projectId)?.spec ?: StudioStarters.all.first().spec

private fun colorOrFallback(raw: String?): Color = runCatching {
    if (raw.isNullOrBlank()) StudioAccent else Color(android.graphics.Color.parseColor(raw))
}.getOrDefault(StudioAccent)

private fun studioColors() = darkColorScheme(primary = StudioAccent, background = StudioBg, surface = StudioCard, surfaceVariant = StudioRaised, onPrimary = Color(0xFF132011), onBackground = StudioText, onSurface = StudioText)

private fun shareDesign(context: Context, spec: StudioSpec) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, StudioDocument.backup(spec)); putExtra(Intent.EXTRA_TITLE, "${spec.name} PocketForge design") }, "Back up app design"))
}

private fun applySimpleChange(spec: StudioSpec, request: String): Pair<StudioSpec, String> {
    val lower = request.lowercase(Locale.US)
    var next = spec
    val rename = Regex("(?:rename|call|name)(?: the app)?(?: to| it)?\\s+[\\\"']?([^\\\"']{1,60})[\\\"']?$", RegexOption.IGNORE_CASE).find(request)
    if (rename != null) next = next.copy(name = rename.groupValues[1].trim().trimEnd('.'))
    val tagline = Regex("(?:description|tagline)(?: to|:)?\\s+[\\\"']?(.{3,180})[\\\"']?$", RegexOption.IGNORE_CASE).find(request)
    if (tagline != null) next = next.copy(tagline = tagline.groupValues[1].trim().trimEnd('.'))
    val colors = mapOf("green" to "#B9F178", "blue" to "#8BC8FF", "orange" to "#F8BD8D", "purple" to "#D4B5FF", "teal" to "#7DDED5")
    colors.entries.firstOrNull { lower.contains(it.key) && (lower.contains("color") || lower.contains("colour") || lower.contains("make it")) }?.let { next = next.copy(accent = it.value) }
    val add = Regex("(?:add|create)(?: a| an)?(?: new)? screen(?: called| named)?\\s+[\\\"']?([a-zA-Z0-9 _-]{2,24})[\\\"']?", RegexOption.IGNORE_CASE).find(request)
    if (add != null) {
        require(next.screens.size < 5) { "Apps can have up to five screens." }
        val title = add.groupValues[1].trim().trimEnd('.', '?')
        val id = title.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').take(32).ifBlank { "screen_${next.screens.size + 1}" }
        val unique = if (next.screens.any { it.id == id }) "${id}_${next.screens.size + 1}" else id
        next = next.copy(screens = next.screens + StudioScreen(unique, title, "list", "A new space for your entries.", listOf(StudioField("entry", "Entry"))))
    }
    require(next != spec || lower.contains("rename") || lower.contains("call") || lower.contains("name") || lower.contains("color") || lower.contains("colour") || lower.contains("add") || lower.contains("create") || lower.contains("description") || lower.contains("tagline")) { "Try “rename the app to…”, “make it green,” or “add a screen called…”." }
    return next to "Applied a beginner-friendly change"
}
