package com.pocketforge.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import java.util.Locale

private val EditorBg = Color(0xFF08100C)
private val EditorSurface = Color(0xFF111A15)
private val EditorRaised = Color(0xFF18241D)
private val EditorLine = Color(0xFF2A3A30)
private val EditorText = Color(0xFFF4F7F2)
private val EditorMuted = Color(0xFFA7B3AA)
private val EditorAccent = Color(0xFF8FF0A4)
private val EditorAccentSoft = Color(0xFF183D25)
private val EditorWarn = Color(0xFFFFD166)
private val EditorBad = Color(0xFFFF8A8A)

private val EditorTypography = Typography(
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

private enum class EditorSection(val label: String) {
    BUILDER("Builder"),
    DESIGN("Design"),
    DATA("Data"),
    SETTINGS("Settings")
}

class OfficialStudioEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectId = intent.getStringExtra("pocketforge.project_id").orEmpty()
        setContent { OfficialStudioEditorApp(projectId = projectId, onClose = ::finish) }
    }
}

@Composable
private fun OfficialStudioEditorApp(projectId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val studio = remember { StudioStore(context) }
    val workspace = remember { WorkspaceStore(context) }
    val vault = remember { SecretVault(context) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val project = remember(projectId) { workspace.projects().firstOrNull { it.id == projectId } }

    var spec by remember { mutableStateOf(loadEditorSpec(studio, projectId, project?.name)) }
    var section by remember { mutableStateOf(EditorSection.BUILDER) }
    var selectedScreenId by remember { mutableStateOf(spec.screens.first().id) }
    var prompt by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Draft ready. Changes stay in the editor until you save a version.") }
    var busy by remember { mutableStateOf(false) }
    var showAddScreen by remember { mutableStateOf(false) }
    var showAddField by remember { mutableStateOf(false) }
    var showVersions by remember { mutableStateOf(false) }
    var deleteScreenId by remember { mutableStateOf<String?>(null) }

    val selectedScreen = spec.screens.firstOrNull { it.id == selectedScreenId } ?: spec.screens.first()

    fun replaceSpec(next: StudioSpec, success: String) {
        runCatching { next.validate() }
            .onSuccess {
                spec = it
                if (spec.screens.none { screen -> screen.id == selectedScreenId }) selectedScreenId = spec.screens.first().id
                status = success
            }
            .onFailure { status = "I kept the current draft safe. ${it.message ?: "That edit would make the app invalid."}" }
    }

    fun saveVersion(summary: String = "Saved from visual editor") {
        runCatching { studio.save(projectId, spec.validate(), summary) }
            .onSuccess { status = "Saved as a new version. Full preview and builds now use this version." }
            .onFailure { status = "Could not save. ${it.message ?: "Check the app structure and try again."}" }
    }

    fun openFullPreview() {
        runCatching { studio.save(projectId, spec.validate(), "Saved before full preview") }
            .onSuccess {
                context.startActivity(Intent(context, GeneratedPreviewActivity::class.java).putExtra("pocketforge.project_id", projectId))
            }
            .onFailure { status = "Preview stayed on the current saved version. ${it.message ?: "Fix the draft first."}" }
    }

    fun applyPlainEnglishChange() {
        val request = prompt.trim()
        if (request.isBlank() || busy) return
        busy = true
        status = "PocketForge is shaping that change…"
        Thread {
            val result = runCatching {
                if (AiRouter.configuredProviders(vault).isNotEmpty()) {
                    AiRouter.generateStudioSpec(
                        "Current app design:\n${spec.json()}\n\nRequested change:\n$request\n\nPreserve existing screen IDs, screen types, and field IDs/types unless the user explicitly asks to add something. Return the complete updated design.",
                        vault
                    ).validate(spec)
                } else {
                    applyLocalEditorChange(spec, request).validate()
                }
            }
            handler.post {
                busy = false
                result.onSuccess { next ->
                    spec = next
                    selectedScreenId = next.screens.firstOrNull { it.id == selectedScreenId }?.id ?: next.screens.first().id
                    prompt = ""
                    status = "Change applied to the draft. The live preview is using it now; save a version when it looks right."
                }.onFailure { error ->
                    status = "I kept the current draft safe. ${error.message ?: "That change needs a little more detail."}"
                }
            }
        }.start()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = EditorAccent,
            background = EditorBg,
            surface = EditorSurface,
            surfaceVariant = EditorRaised,
            onPrimary = Color(0xFF07140B),
            onBackground = EditorText,
            onSurface = EditorText,
            outline = EditorLine
        ),
        typography = EditorTypography
    ) {
        Scaffold(
            containerColor = EditorBg,
            topBar = {
                Column {
                    EditorHeader(
                        name = spec.name,
                        onClose = onClose,
                        onSave = { saveVersion() },
                        onPreview = ::openFullPreview,
                        onBuild = { context.startActivity(Intent(context, AdvancedWorkspaceActivity::class.java)) }
                    )
                    EditorTabs(section = section, onSection = { section = it })
                }
            }
        ) { padding ->
            when (section) {
                EditorSection.BUILDER -> BuilderSection(
                    modifier = Modifier.padding(padding),
                    spec = spec,
                    selectedScreenId = selectedScreenId,
                    onSelectScreen = { selectedScreenId = it },
                    onAddScreen = { showAddScreen = true },
                    onMoveScreen = { screenId, direction ->
                        replaceSpec(moveScreen(spec, screenId, direction), "Screen order updated. Live preview refreshed.")
                    },
                    onDeleteScreen = { deleteScreenId = it },
                    prompt = prompt,
                    onPrompt = { prompt = it.take(1600) },
                    busy = busy,
                    status = status,
                    onApply = ::applyPlainEnglishChange,
                    projectId = projectId
                )
                EditorSection.DESIGN -> DesignSection(
                    modifier = Modifier.padding(padding),
                    spec = spec,
                    onSpec = { replaceSpec(it, "Design updated. Live preview refreshed.") },
                    projectId = projectId
                )
                EditorSection.DATA -> DataSection(
                    modifier = Modifier.padding(padding),
                    spec = spec,
                    selectedScreenId = selectedScreenId,
                    onSelectScreen = { selectedScreenId = it },
                    onSpec = { replaceSpec(it, "Data structure updated. Live preview refreshed.") },
                    onAddField = { showAddField = true }
                )
                EditorSection.SETTINGS -> EditorSettingsSection(
                    modifier = Modifier.padding(padding),
                    projectId = projectId,
                    projectName = project?.name ?: spec.name,
                    spec = spec,
                    versions = studio.versions(projectId),
                    providerCount = AiRouter.configuredProviders(vault).size,
                    status = status,
                    onSave = { saveVersion() },
                    onVersions = { showVersions = true },
                    onPreview = ::openFullPreview,
                    onBuild = { context.startActivity(Intent(context, AdvancedWorkspaceActivity::class.java)) },
                    onConnections = { context.startActivity(Intent(context, IntegrationCenterActivity::class.java)) },
                    onShare = { shareEditorDesign(context, spec) }
                )
            }
        }
    }

    if (showAddScreen) {
        AddScreenDialog(
            spec = spec,
            onDismiss = { showAddScreen = false },
            onAdd = { title, type ->
                val next = addScreen(spec, title, type)
                showAddScreen = false
                replaceSpec(next, "Screen added. Live preview refreshed.")
                selectedScreenId = next.screens.last().id
            }
        )
    }

    if (showAddField) {
        AddFieldDialog(
            screen = selectedScreen,
            onDismiss = { showAddField = false },
            onAdd = { label, type, required ->
                showAddField = false
                replaceSpec(addField(spec, selectedScreen.id, label, type, required), "Field added to ${selectedScreen.title}.")
            }
        )
    }

    deleteScreenId?.let { id ->
        val screen = spec.screens.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { deleteScreenId = null },
            containerColor = EditorSurface,
            title = { Text("Delete ${screen?.title ?: "screen"}?") },
            text = { Text("This removes the screen from the current draft. Save a version only if you want to keep that deletion.", color = EditorMuted) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteScreenId = null
                        if (spec.screens.size <= 1) {
                            status = "An app needs at least one screen."
                        } else {
                            replaceSpec(spec.copy(screens = spec.screens.filterNot { it.id == id }), "Screen removed from the draft.")
                        }
                    }
                ) { Text("Delete", color = EditorBad) }
            },
            dismissButton = { TextButton(onClick = { deleteScreenId = null }) { Text("Cancel") } }
        )
    }

    if (showVersions) {
        EditorVersionsDialog(
            versions = studio.versions(projectId),
            onDismiss = { showVersions = false },
            onRestore = { restored ->
                spec = restored
                selectedScreenId = restored.screens.first().id
                status = "Version restored into the draft. Save it as a new version when ready."
                showVersions = false
            }
        )
    }
}

@Composable
private fun EditorHeader(
    name: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onPreview: () -> Unit,
    onBuild: () -> Unit
) {
    Surface(color = EditorBg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)) { Text("‹ Apps", color = EditorMuted) }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(name, color = EditorText, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Visual app editor", color = EditorMuted, fontSize = 9.sp)
                }
                OutlinedButton(onClick = onSave, shape = RoundedCornerShape(100.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("Save version", fontSize = 9.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 7.dp)) { Text("Preview", fontSize = 10.sp) }
                Button(onClick = onBuild, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EditorAccent, contentColor = Color(0xFF07140B)), contentPadding = PaddingValues(vertical = 7.dp)) { Text("Build beta", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun EditorTabs(section: EditorSection, onSection: (EditorSection) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        EditorSection.entries.forEach { item ->
            Surface(
                modifier = Modifier.weight(1f).clickable { onSection(item) },
                color = if (section == item) EditorAccentSoft else Color.Transparent,
                shape = RoundedCornerShape(11.dp),
                border = if (section == item) BorderStroke(1.dp, Color(0xFF355440)) else null
            ) {
                Text(
                    item.label,
                    color = if (section == item) EditorAccent else EditorMuted,
                    fontSize = 10.sp,
                    fontWeight = if (section == item) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BuilderSection(
    modifier: Modifier,
    spec: StudioSpec,
    selectedScreenId: String,
    onSelectScreen: (String) -> Unit,
    onAddScreen: () -> Unit,
    onMoveScreen: (String, Int) -> Unit,
    onDeleteScreen: (String) -> Unit,
    prompt: String,
    onPrompt: (String) -> Unit,
    busy: Boolean,
    status: String,
    onApply: () -> Unit,
    projectId: String
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("App screens", color = EditorText, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("Select, reorder, or add screens. Data fields live in the Data tab.", color = EditorMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Button(onClick = onAddScreen, enabled = spec.screens.size < 5, colors = ButtonDefaults.buttonColors(containerColor = EditorAccent, contentColor = Color(0xFF07140B)), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text("+ Add") }
        }

        Spacer(Modifier.height(12.dp))
        spec.screens.forEachIndexed { index, screen ->
            val selected = screen.id == selectedScreenId
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelectScreen(screen.id) },
                color = if (selected) EditorAccentSoft else EditorSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (selected) Color(0xFF3E654A) else EditorLine)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = editorColor(spec.accent), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(screen.title.take(1).uppercase(Locale.US), color = Color(0xFF07140B), fontWeight = FontWeight.Black) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(screen.title, color = EditorText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("${screen.type.replaceFirstChar { it.uppercase() }} · ${screen.fields.size} field${if (screen.fields.size == 1) "" else "s"}", color = EditorMuted, fontSize = 9.sp)
                    }
                    TextButton(onClick = { onMoveScreen(screen.id, -1) }, enabled = index > 0, contentPadding = PaddingValues(4.dp)) { Text("↑") }
                    TextButton(onClick = { onMoveScreen(screen.id, 1) }, enabled = index < spec.screens.lastIndex, contentPadding = PaddingValues(4.dp)) { Text("↓") }
                    TextButton(onClick = { onDeleteScreen(screen.id) }, enabled = spec.screens.size > 1, contentPadding = PaddingValues(4.dp)) { Text("×", color = if (spec.screens.size > 1) EditorBad else EditorMuted) }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        EditorCard("Live draft preview", "This is the real generated app surface using the current unsaved draft.") {
            DraftLivePreview(spec = spec, projectId = projectId)
        }

        Spacer(Modifier.height(14.dp))
        EditorCard("Make changes with plain English", if (busy) "PocketForge is working…" else "AI can reshape the full design; starter mode handles common local edits.") {
            OutlinedTextField(
                value = prompt,
                onValueChange = onPrompt,
                placeholder = { Text("Try: add a screen called Shopping, rename the app, or make it blue") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                maxLines = 7
            )
            Button(
                onClick = onApply,
                enabled = !busy && prompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EditorAccent, contentColor = Color(0xFF07140B))
            ) { Text(if (busy) "Working…" else "Apply change", fontWeight = FontWeight.Bold) }
            Text(status, color = if (status.contains("safe", true) || status.contains("could not", true)) EditorWarn else EditorMuted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 9.dp))
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun DesignSection(modifier: Modifier, spec: StudioSpec, onSpec: (StudioSpec) -> Unit, projectId: String) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Design", color = EditorText, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text("Brand the generated app without touching source code.", color = EditorMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))

        EditorCard("App identity", "Used in the generated preview and saved design.") {
            OutlinedTextField(value = spec.name, onValueChange = { onSpec(spec.copy(name = it.take(60))) }, label = { Text("App name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = spec.tagline, onValueChange = { onSpec(spec.copy(tagline = it.take(180))) }, label = { Text("Tagline") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), maxLines = 3)
        }

        Spacer(Modifier.height(12.dp))
        EditorCard("Theme", "Color and appearance update the live preview immediately.") {
            Text("Accent color", color = EditorMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 7.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("#8FF0A4", "#8BC8FF", "#F8BD8D", "#D4B5FF", "#7DDED5", "#FF9DA4").forEach { color ->
                    Surface(
                        modifier = Modifier.size(42.dp).clickable { onSpec(spec.copy(accent = color)) },
                        color = editorColor(color),
                        shape = CircleShape,
                        border = if (spec.accent.equals(color, true)) BorderStroke(3.dp, EditorText) else BorderStroke(1.dp, EditorLine)
                    ) {}
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dark appearance", color = EditorText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("The generated app uses a dark surface when enabled.", color = EditorMuted, fontSize = 9.sp)
                }
                Switch(checked = spec.dark, onCheckedChange = { onSpec(spec.copy(dark = it)) })
            }
        }

        Spacer(Modifier.height(12.dp))
        EditorCard("Live design preview", "Draft-only preview data is isolated from the saved app.") {
            DraftLivePreview(spec = spec, projectId = projectId)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun DataSection(
    modifier: Modifier,
    spec: StudioSpec,
    selectedScreenId: String,
    onSelectScreen: (String) -> Unit,
    onSpec: (StudioSpec) -> Unit,
    onAddField: () -> Unit
) {
    val screen = spec.screens.firstOrNull { it.id == selectedScreenId } ?: spec.screens.first()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Data", color = EditorText, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text("Define what each screen actually stores. Field IDs and types stay stable while labels can change.", color = EditorMuted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            spec.screens.forEach { item ->
                FilterChip(selected = item.id == screen.id, onClick = { onSelectScreen(item.id) }, label = { Text(item.title) })
            }
        }

        Spacer(Modifier.height(12.dp))
        EditorCard(screen.title, "${screen.type.replaceFirstChar { it.uppercase() }} screen · ${screen.fields.size} fields") {
            if (screen.fields.isEmpty()) {
                Text("This info screen has no saved fields yet.", color = EditorMuted, fontSize = 10.sp)
            }
            screen.fields.forEach { field ->
                FieldEditorRow(
                    field = field,
                    canDelete = canDeleteField(screen, field),
                    onLabel = { label ->
                        val next = screen.copy(fields = screen.fields.map { if (it.id == field.id) it.copy(label = label.take(40)) else it })
                        onSpec(spec.copy(screens = spec.screens.map { if (it.id == screen.id) next else it }))
                    },
                    onRequired = { required ->
                        val next = screen.copy(fields = screen.fields.map { if (it.id == field.id) it.copy(required = required) else it })
                        onSpec(spec.copy(screens = spec.screens.map { if (it.id == screen.id) next else it }))
                    },
                    onDelete = {
                        val next = screen.copy(fields = screen.fields.filterNot { it.id == field.id })
                        onSpec(spec.copy(screens = spec.screens.map { if (it.id == screen.id) next else it }))
                    }
                )
                HorizontalDivider(color = EditorLine, modifier = Modifier.padding(vertical = 5.dp))
            }
            OutlinedButton(onClick = onAddField, enabled = screen.fields.size < 6, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("+ Add field")
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun FieldEditorRow(
    field: StudioField,
    canDelete: Boolean,
    onLabel: (String) -> Unit,
    onRequired: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = field.label,
                onValueChange = onLabel,
                label = { Text(field.type.replaceFirstChar { it.uppercase() }) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onDelete, enabled = canDelete, contentPadding = PaddingValues(7.dp)) {
                Text("Delete", color = if (canDelete) EditorBad else EditorMuted, fontSize = 9.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ID: ${field.id}", color = EditorMuted, fontSize = 8.sp, modifier = Modifier.weight(1f))
            Text("Required", color = EditorMuted, fontSize = 9.sp)
            Switch(checked = field.required, onCheckedChange = onRequired, modifier = Modifier.padding(start = 5.dp))
        }
    }
}

@Composable
private fun EditorSettingsSection(
    modifier: Modifier,
    projectId: String,
    projectName: String,
    spec: StudioSpec,
    versions: List<StudioVersion>,
    providerCount: Int,
    status: String,
    onSave: () -> Unit,
    onVersions: () -> Unit,
    onPreview: () -> Unit,
    onBuild: () -> Unit,
    onConnections: () -> Unit,
    onShare: () -> Unit
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Settings", color = EditorText, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text("Versioning, build handoff, connections, and project backup.", color = EditorMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))

        EditorCard("Project", projectName) {
            EditorKeyValue("App", spec.name)
            EditorKeyValue("Project ID", projectId)
            EditorKeyValue("Screens", spec.screens.size.toString())
            EditorKeyValue("Saved versions", versions.size.toString())
        }

        Spacer(Modifier.height(12.dp))
        EditorCard("Version history", "Save checkpoints before major changes.") {
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = EditorAccent, contentColor = Color(0xFF07140B))) { Text("Save new version", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onVersions, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Browse ${versions.size} saved version${if (versions.size == 1) "" else "s"}") }
            Text(status, color = EditorMuted, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(12.dp))
        EditorCard("Preview & build", "Preview uses the saved design. Build opens the verified Advanced Workspace pipeline.") {
            OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) { Text("Open full app preview") }
            Button(onClick = onBuild, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = EditorAccent, contentColor = Color(0xFF07140B))) { Text("Build beta APK", fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(12.dp))
        EditorCard("Connections", "$providerCount AI provider${if (providerCount == 1) "" else "s"} connected") {
            OutlinedButton(onClick = onConnections, modifier = Modifier.fillMaxWidth()) { Text("AI & build connections") }
        }

        Spacer(Modifier.height(12.dp))
        EditorCard("Backup", "Share the declarative PocketForge design — not API keys or secrets.") {
            OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text("Share app design backup") }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun EditorCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = EditorSurface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, EditorLine), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = EditorText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = EditorMuted, fontSize = 9.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            content()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DraftLivePreview(spec: StudioSpec, projectId: String) {
    val context = LocalContext.current
    val snapshot = remember(spec) { spec.json().toString() }
    key(snapshot) {
        Surface(color = Color(0xFF050806), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, EditorLine), modifier = Modifier.fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webViewClient = WebViewClient()
                        loadDataWithBaseURL(
                            "https://pocketforge.local/",
                            StudioDocument.html(context, spec, "${projectId.ifBlank { "preview" }}_draft"),
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(500.dp)
            )
        }
    }
}

@Composable
private fun AddScreenDialog(spec: StudioSpec, onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("list") }
    var menu by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = EditorSurface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, EditorLine)) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add screen", color = EditorText, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
                Text("PocketForge supports up to five app screens in this builder version.", color = EditorMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 10.dp))
                OutlinedTextField(value = title, onValueChange = { title = it.take(24) }, label = { Text("Screen name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text("Type: ${type.replaceFirstChar { it.uppercase() }}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        StudioSpec.TYPES.forEach { option ->
                            DropdownMenuItem(text = { Text(option.replaceFirstChar { it.uppercase() }) }, onClick = { type = option; menu = false })
                        }
                    }
                }
                Button(
                    onClick = { onAdd(title.trim(), type) },
                    enabled = title.trim().isNotBlank() && spec.screens.size < 5,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EditorAccent, contentColor = Color(0xFF07140B))
                ) { Text("Add screen", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun AddFieldDialog(screen: StudioScreen, onDismiss: () -> Unit, onAdd: (String, String, Boolean) -> Unit) {
    var label by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(if (screen.type == "calculator") "number" else "text") }
    var required by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    val allowedTypes = when (screen.type) {
        "calculator" -> listOf("number")
        "ledger" -> StudioSpec.FIELD_TYPES.filterNot { it == "number" && screen.fields.any { field -> field.type == "number" } }
        else -> StudioSpec.FIELD_TYPES.toList()
    }
    if (type !in allowedTypes) type = allowedTypes.firstOrNull() ?: "text"

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = EditorSurface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, EditorLine)) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add field", color = EditorText, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
                Text("Add a stable data field to ${screen.title}.", color = EditorMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 10.dp))
                OutlinedTextField(value = label, onValueChange = { label = it.take(40) }, label = { Text("Field label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text("Type: ${type.replaceFirstChar { it.uppercase() }}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        allowedTypes.forEach { option ->
                            DropdownMenuItem(text = { Text(option.replaceFirstChar { it.uppercase() }) }, onClick = { type = option; menu = false })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Required", color = EditorText, modifier = Modifier.weight(1f))
                    Switch(checked = required, onCheckedChange = { required = it })
                }
                Button(
                    onClick = { onAdd(label.trim(), type, required) },
                    enabled = label.trim().isNotBlank() && screen.fields.size < 6,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EditorAccent, contentColor = Color(0xFF07140B))
                ) { Text("Add field", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun EditorVersionsDialog(versions: List<StudioVersion>, onDismiss: () -> Unit, onRestore: (StudioSpec) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = EditorSurface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, EditorLine)) {
            Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Saved versions", color = EditorText, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
                if (versions.isEmpty()) {
                    Text("No saved versions yet.", color = EditorMuted, modifier = Modifier.padding(top = 15.dp))
                } else {
                    versions.sortedByDescending { it.time }.forEachIndexed { index, version ->
                        Surface(color = EditorRaised, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (index == 0) "Latest saved version" else "Saved version ${versions.size - index}", color = EditorText, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                    Text(version.summary, color = EditorMuted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                TextButton(onClick = { onRestore(version.spec) }) { Text("Restore") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorKeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = EditorMuted, fontSize = 9.sp, modifier = Modifier.width(92.dp))
        Text(value, color = EditorText, fontSize = 9.sp, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

private fun loadEditorSpec(studio: StudioStore, projectId: String, projectName: String?): StudioSpec {
    studio.latest(projectId)?.spec?.let { return it }
    val starter = StudioStarters.all.first().spec.copy(name = projectName?.takeIf { it.isNotBlank() } ?: "My app")
    if (projectId.isNotBlank()) runCatching { studio.save(projectId, starter, "Created editor starter") }
    return starter
}

private fun addScreen(spec: StudioSpec, title: String, type: String): StudioSpec {
    require(spec.screens.size < 5) { "This builder supports up to five screens." }
    val cleanTitle = title.trim().take(24)
    require(cleanTitle.isNotBlank()) { "Give the screen a name." }
    var id = editorId(cleanTitle, "screen_${spec.screens.size + 1}")
    var suffix = 2
    while (spec.screens.any { it.id == id }) {
        id = "${editorId(cleanTitle, "screen")}_${suffix++}".take(32)
    }
    val fields = defaultFields(type)
    val screen = StudioScreen(
        id = id,
        title = cleanTitle,
        type = type,
        description = "A space you can shape in PocketForge.",
        fields = fields,
        operation = if (type == "calculator") "product" else "sum",
        unit = if (type == "ledger") "$" else ""
    )
    return spec.copy(screens = spec.screens + screen).validate()
}

private fun addField(spec: StudioSpec, screenId: String, label: String, requestedType: String, required: Boolean): StudioSpec {
    val screen = spec.screens.first { it.id == screenId }
    require(screen.fields.size < 6) { "A screen supports up to six fields." }
    val cleanLabel = label.trim().take(40)
    require(cleanLabel.isNotBlank()) { "Give the field a label." }
    var id = editorId(cleanLabel, "field_${screen.fields.size + 1}")
    var suffix = 2
    while (screen.fields.any { it.id == id }) id = "${editorId(cleanLabel, "field")}_${suffix++}".take(32)
    val type = when {
        screen.type == "calculator" -> "number"
        screen.type == "ledger" && requestedType == "number" && screen.fields.any { it.type == "number" } -> "text"
        else -> requestedType
    }
    val nextScreen = screen.copy(fields = screen.fields + StudioField(id, cleanLabel, type, required))
    return spec.copy(screens = spec.screens.map { if (it.id == screenId) nextScreen else it }).validate()
}

private fun moveScreen(spec: StudioSpec, screenId: String, direction: Int): StudioSpec {
    val index = spec.screens.indexOfFirst { it.id == screenId }
    if (index < 0) return spec
    val target = (index + direction).coerceIn(0, spec.screens.lastIndex)
    if (target == index) return spec
    val list = spec.screens.toMutableList()
    val item = list.removeAt(index)
    list.add(target, item)
    return spec.copy(screens = list)
}

private fun canDeleteField(screen: StudioScreen, field: StudioField): Boolean {
    return when (screen.type) {
        "info" -> true
        "calculator" -> screen.fields.size > 2
        "ledger" -> field.type != "number" && screen.fields.size > 1
        else -> screen.fields.size > 1
    }
}

private fun defaultFields(type: String): List<StudioField> = when (type) {
    "info" -> emptyList()
    "calculator" -> listOf(StudioField("quantity", "Quantity", "number"), StudioField("rate", "Rate", "number"))
    "ledger" -> listOf(StudioField("item", "Item"), StudioField("amount", "Amount", "number"))
    "checklist" -> listOf(StudioField("task", "Task"))
    else -> listOf(StudioField("entry", "Entry"))
}

private fun editorId(value: String, fallback: String): String {
    val cleaned = value.lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .let { if (it.firstOrNull()?.isLetter() == true) it else "field_$it" }
        .take(32)
    return cleaned.ifBlank { fallback }.take(32)
}

private fun applyLocalEditorChange(spec: StudioSpec, request: String): StudioSpec {
    val lower = request.lowercase(Locale.US)
    var next = spec
    when {
        "make it green" in lower -> next = next.copy(accent = "#8FF0A4")
        "make it blue" in lower -> next = next.copy(accent = "#8BC8FF")
        "make it purple" in lower -> next = next.copy(accent = "#D4B5FF")
        "dark mode" in lower || "make it dark" in lower -> next = next.copy(dark = true)
        "light mode" in lower || "make it light" in lower -> next = next.copy(dark = false)
        lower.startsWith("rename the app to ") -> {
            val name = request.substringAfter("rename the app to ", "").trim().trim('"').take(60)
            require(name.isNotBlank()) { "Tell me the new app name." }
            next = next.copy(name = name)
        }
        lower.startsWith("add a screen called ") -> {
            val title = request.substringAfter("add a screen called ", "").trim().trim('"').take(24)
            next = addScreen(next, title, "list")
        }
        else -> error("Connect an AI provider for broader plain-English edits, or use the Builder, Design, and Data controls directly.")
    }
    return next
}

private fun editorColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(EditorAccent)

private fun shareEditorDesign(context: android.content.Context, spec: StudioSpec) {
    val body = StudioDocument.backup(spec)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "${spec.name} · PocketForge design backup")
                putExtra(Intent.EXTRA_TEXT, body)
            },
            "Share PocketForge design"
        )
    )
}
