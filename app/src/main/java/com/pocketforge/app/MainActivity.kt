package com.pocketforge.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ForgeBg = Color(0xFF0A0C10)
private val ForgeCard = Color(0xFF12161D)
private val ForgeLine = Color(0xFF232A35)
private val ForgeAccent = Color(0xFF7CFFB2)
private val ForgeText = Color(0xFFF2F5F7)
private val ForgeMuted = Color(0xFF9AA6B2)
private val ForgeWarn = Color(0xFFFFC66D)

private val PreviewBg = Color(0xFF07111E)
private val PreviewCard = Color(0xFF0F1C2B)
private val PreviewLine = Color(0xFF203249)
private val PreviewAccent = Color(0xFF5CE1E6)
private val PreviewText = Color(0xFFF5FAFF)
private val PreviewMuted = Color(0xFF93A8BE)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PocketForgeApp() }
    }
}

enum class ForgeTab(val label: String) {
    Build("Build"), Preview("Preview"), Changes("Changes"), Health("Health"), Publish("Publish")
}

enum class DemoScreen(val label: String, val glyph: String) {
    Today("Today", "⌂"), History("History", "≡"), Settings("Settings", "⚙")
}

@Composable
fun PocketForgeApp() {
    var tab by remember { mutableStateOf(ForgeTab.Build) }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ForgeAccent,
            background = ForgeBg,
            surface = ForgeCard,
            onPrimary = Color.Black,
            onBackground = ForgeText,
            onSurface = ForgeText
        )
    ) {
        Scaffold(
            containerColor = ForgeBg,
            topBar = { if (tab != ForgeTab.Preview) ForgeTopBar() },
            bottomBar = {
                if (tab != ForgeTab.Preview) {
                    NavigationBar(containerColor = Color(0xFF0E1117)) {
                        ForgeTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Text(tabGlyph(item), fontSize = 17.sp) },
                                label = { Text(item.label, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ForgeAccent,
                                    selectedTextColor = ForgeAccent,
                                    indicatorColor = Color(0xFF183326),
                                    unselectedIconColor = ForgeMuted,
                                    unselectedTextColor = ForgeMuted
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    ForgeTab.Build -> BuildScreen(onPreview = { tab = ForgeTab.Preview })
                    ForgeTab.Preview -> FullAppPreview(onExit = { tab = ForgeTab.Build })
                    ForgeTab.Changes -> ChangesScreen()
                    ForgeTab.Health -> HealthScreen()
                    ForgeTab.Publish -> PublishScreen()
                }
            }
        }
    }
}

private fun tabGlyph(tab: ForgeTab) = when (tab) {
    ForgeTab.Build -> "✦"
    ForgeTab.Preview -> "◫"
    ForgeTab.Changes -> "↻"
    ForgeTab.Health -> "♥"
    ForgeTab.Publish -> "↑"
}

@Composable
private fun ForgeTopBar() {
    Surface(color = ForgeBg) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).background(ForgeAccent, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) { Text("P", color = Color.Black, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("PocketForge", color = ForgeText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("You describe it. It builds it.", color = ForgeMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            StatusPill("BETA")
        }
    }
}

@Composable
private fun BuildScreen(onPreview: () -> Unit) {
    val context = LocalContext.current
    val vault = remember { SecretVault(context) }
    var prompt by remember { mutableStateOf("Make me an app that tracks my work hours and tells me what my paycheck should be.") }
    var plan by remember { mutableStateOf<AiPlan?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val hasAi = vault.has(IntegrationKeys.OPENROUTER) || vault.has(IntegrationKeys.GEMINI) || vault.has(IntegrationKeys.GROQ)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("What do you want to build?", fontSize = 26.sp, fontWeight = FontWeight.Black, color = ForgeText)
        Spacer(Modifier.height(6.dp))
        Text("Talk normally. PocketForge routes the job to the best connected AI and protects unrelated working code.", color = ForgeMuted)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it; plan = null; error = null },
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
            placeholder = { Text("Describe your app or the change you want…") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ForgeAccent,
                unfocusedBorderColor = ForgeLine,
                focusedContainerColor = ForgeCard,
                unfocusedContainerColor = ForgeCard,
                focusedTextColor = ForgeText,
                unfocusedTextColor = ForgeText
            ),
            shape = RoundedCornerShape(18.dp)
        )
        Spacer(Modifier.height(12.dp))

        if (!hasAi) {
            ForgeCardBlock {
                Text("Connect a free AI first", color = ForgeText, fontWeight = FontWeight.Bold)
                Text("OpenRouter, Gemini, or Groq can power the planner. Two or more gives automatic failover.", color = ForgeMuted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { context.startActivity(Intent(context, IntegrationCenterActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ForgeAccent, contentColor = Color.Black)
                ) { Text("Open Integration Center", fontWeight = FontWeight.Bold) }
            }
        } else {
            Button(
                onClick = {
                    busy = true
                    error = null
                    plan = null
                    Thread {
                        val result = runCatching { AiRouter.planBest(prompt, vault) }
                        Handler(Looper.getMainLooper()).post {
                            busy = false
                            result.onSuccess { plan = it }.onFailure { error = it.message }
                        }
                    }.start()
                },
                enabled = !busy && prompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ForgeAccent, contentColor = Color.Black)
            ) {
                Text(if (busy) "AI is planning…" else "Plan with best free AI", fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp))
            }
        }

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Color(0xFFFF9D9D), fontSize = 12.sp)
        }

        plan?.let {
            Spacer(Modifier.height(20.dp))
            ChangeContract(it, onPreview)
        }

        Spacer(Modifier.height(22.dp))
        AiTeamCard(vault)
    }
}

@Composable
private fun ChangeContract(plan: AiPlan, onPreview: () -> Unit) {
    SectionTitle("AI CHANGE CONTRACT")
    ForgeCardBlock {
        ContractRow("AI", plan.provider)
        ContractRow("Summary", plan.summary)
        ContractRow("Scope", plan.scope)
        ContractRow("Protect", plan.protected)
        ContractRow("Verify", plan.verify)
        Spacer(Modifier.height(8.dp))
        Text(plan.implementationNotes, color = ForgeMuted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onPreview,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20262F), contentColor = ForgeText)
        ) { Text("Open full app preview") }
        Spacer(Modifier.height(7.dp))
        Text("Current beta: planning is live. Automatic repository editing from this contract is the next agent layer.", color = ForgeMuted, fontSize = 10.sp)
    }
}

@Composable
private fun AiTeamCard(vault: SecretVault) {
    SectionTitle("AI TEAM · LIVE ROUTER")
    ForgeCardBlock {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Best connected free model", color = ForgeText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Automatic fallback when a provider is unavailable or out of quota.", color = ForgeMuted, fontSize = 12.sp)
            }
            StatusPill("AUTO")
        }
        Spacer(Modifier.height(14.dp))
        ProviderState("Gemini", vault.has(IntegrationKeys.GEMINI), "planner / long-context reasoning")
        HorizontalDivider(color = ForgeLine)
        ProviderState("OpenRouter Free", vault.has(IntegrationKeys.OPENROUTER), "diverse coding pool")
        HorizontalDivider(color = ForgeLine)
        ProviderState("Groq", vault.has(IntegrationKeys.GROQ), "fast repair fallback")
    }
}

@Composable
private fun ProviderState(name: String, connected: Boolean, note: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (connected) "●" else "○", color = if (connected) ForgeAccent else ForgeMuted)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = ForgeText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(note, color = ForgeMuted, fontSize = 11.sp)
        }
        Text(if (connected) "CONNECTED" else "ADD KEY", color = if (connected) ForgeAccent else ForgeMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ContractRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label.uppercase(), color = ForgeAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = ForgeText, fontSize = 13.sp)
    }
}

@Composable
private fun FullAppPreview(onExit: () -> Unit) {
    var screen by remember { mutableStateOf(DemoScreen.Today) }
    var clockedIn by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PreviewAccent,
            background = PreviewBg,
            surface = PreviewCard,
            onPrimary = Color(0xFF001719),
            onBackground = PreviewText,
            onSurface = PreviewText
        )
    ) {
        Box(Modifier.fillMaxSize().background(PreviewBg)) {
            Scaffold(
                containerColor = PreviewBg,
                topBar = { DemoTopBar() },
                bottomBar = {
                    NavigationBar(containerColor = Color(0xFF091522)) {
                        DemoScreen.entries.forEach { item ->
                            NavigationBarItem(
                                selected = screen == item,
                                onClick = { screen = item },
                                icon = { Text(item.glyph, fontSize = 18.sp) },
                                label = { Text(item.label, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PreviewAccent,
                                    selectedTextColor = PreviewAccent,
                                    indicatorColor = Color(0xFF123B43),
                                    unselectedIconColor = PreviewMuted,
                                    unselectedTextColor = PreviewMuted
                                )
                            )
                        }
                    }
                }
            ) { inner ->
                Box(Modifier.padding(inner).fillMaxSize()) {
                    when (screen) {
                        DemoScreen.Today -> DemoToday(clockedIn) { clockedIn = !clockedIn }
                        DemoScreen.History -> DemoHistory()
                        DemoScreen.Settings -> DemoSettings()
                    }
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 8.dp).clickable(onClick = onExit),
                color = Color(0xE60A0C10),
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(1.dp, ForgeLine)
            ) {
                Text("‹ PocketForge", color = ForgeAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
            }
        }
    }
}

@Composable
private fun DemoTopBar() {
    Surface(color = PreviewBg) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Paycheck Check", color = PreviewText, fontWeight = FontWeight.Black, fontSize = 21.sp)
                Text("Your work. Your numbers.", color = PreviewMuted, fontSize = 11.sp)
            }
            Surface(color = Color(0xFF123B43), shape = CircleShape) {
                Text("$", color = PreviewAccent, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun DemoToday(clockedIn: Boolean, onToggleClock: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text("THIS WEEK", color = PreviewMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(6.dp))
        Text("$684.50", color = PreviewText, fontSize = 43.sp, fontWeight = FontWeight.Black)
        Text("Estimated gross pay", color = PreviewMuted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        Surface(color = PreviewCard, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, PreviewLine)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    DemoMetric("38h 30m", "Hours", Modifier.weight(1f))
                    DemoMetric("$17.00", "Rate", Modifier.weight(1f))
                    DemoMetric("2h 30m", "Overtime", Modifier.weight(1f))
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onToggleClock,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (clockedIn) Color(0xFFFF7676) else PreviewAccent,
                        contentColor = Color(0xFF001719)
                    )
                ) { Text(if (clockedIn) "Clock out" else "Clock in", fontWeight = FontWeight.Black, fontSize = 16.sp) }
                if (clockedIn) {
                    Spacer(Modifier.height(10.dp))
                    Text("● Shift running · started just now", color = PreviewAccent, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("TODAY", color = PreviewMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(8.dp))
        DemoShift("8:02 AM – 4:31 PM", "8h 29m", "$144.22")
        Spacer(Modifier.height(10.dp))
        DemoShift("6:10 PM – 8:05 PM", "1h 55m", "$32.58")
        Spacer(Modifier.height(18.dp))
        Surface(color = Color(0xFF10261F), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF21483B))) {
            Column(Modifier.padding(16.dp)) {
                Text("✓ Paycheck looks right", color = ForgeAccent, fontWeight = FontWeight.Bold)
                Text("Your recorded hours match the expected gross-pay calculation so far.", color = PreviewMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DemoMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = PreviewText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = PreviewMuted, fontSize = 11.sp)
    }
}

@Composable
private fun DemoShift(time: String, duration: String, pay: String) {
    Surface(color = PreviewCard, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, PreviewLine)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(time, color = PreviewText, fontWeight = FontWeight.SemiBold)
                Text(duration, color = PreviewMuted, fontSize = 12.sp)
            }
            Text(pay, color = PreviewAccent, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
    }
}

@Composable
private fun DemoHistory() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Pay history", color = PreviewText, fontWeight = FontWeight.Black, fontSize = 28.sp)
        Text("Compare what you worked with what you should be paid.", color = PreviewMuted)
        Spacer(Modifier.height(20.dp))
        DemoPayPeriod("Sep 7 – Sep 13", "$684.50", "40h 00m", true)
        Spacer(Modifier.height(12.dp))
        DemoPayPeriod("Aug 31 – Sep 6", "$631.13", "36h 45m", true)
        Spacer(Modifier.height(12.dp))
        DemoPayPeriod("Aug 24 – Aug 30", "$712.88", "41h 10m", false)
    }
}

@Composable
private fun DemoPayPeriod(period: String, pay: String, hours: String, matched: Boolean) {
    Surface(color = PreviewCard, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, PreviewLine)) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(period, color = PreviewText, fontWeight = FontWeight.Bold)
                    Text(hours, color = PreviewMuted, fontSize = 12.sp)
                }
                Text(pay, color = PreviewText, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (matched) "✓ Matches expected pay" else "! Review this paycheck",
                color = if (matched) ForgeAccent else ForgeWarn,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DemoSettings() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Settings", color = PreviewText, fontWeight = FontWeight.Black, fontSize = 28.sp)
        Text("Make the calculator match your real job.", color = PreviewMuted)
        Spacer(Modifier.height(22.dp))
        DemoSettingRow("Hourly rate", "$17.00")
        DemoSettingRow("Overtime", "1.5× after 40h")
        DemoSettingRow("Pay frequency", "Weekly")
        DemoSettingRow("Estimated withholding", "12%")
        Spacer(Modifier.height(18.dp))
        Surface(color = PreviewCard, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, PreviewLine)) {
            Column(Modifier.padding(16.dp)) {
                Text("Interactive preview", color = PreviewText, fontWeight = FontWeight.Bold)
                Text("Generated projects will use this full-screen preview surface instead of a tiny mock card.", color = PreviewMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DemoSettingRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = PreviewText, modifier = Modifier.weight(1f))
        Text(value, color = PreviewAccent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
    HorizontalDivider(color = PreviewLine)
}

@Composable
private fun ChangesScreen() {
    Page("Changes", "Project history in human language.") {
        ChangeItem("Connected free AI router", "Gemini, OpenRouter Free and Groq with automatic fallback")
        ChangeItem("Added Integration Center", "Encrypted provider keys and connection tests")
        ChangeItem("Added redundant APK builders", "GitHub Actions primary with Codemagic and Bitrise backups")
        ChangeItem("Kept full-screen previews", "Generated app preview still uses its own navigation and state")
        ChangeItem("Kept rolling updater", "Only green GitHub builds become installable beta updates")
    }
}

@Composable
private fun ChangeItem(title: String, body: String) {
    ForgeCardBlock {
        Row {
            Column(Modifier.weight(1f)) {
                Text(title, color = ForgeText, fontWeight = FontWeight.SemiBold)
                Text(body, color = ForgeMuted, fontSize = 13.sp)
            }
            Text("✓", color = ForgeAccent)
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun HealthScreen() {
    val context = LocalContext.current
    val vault = remember { SecretVault(context) }
    Page("Health", "No compiler gibberish.") {
        HealthRow("App structure", "Working", true)
        HealthRow("Full-screen preview", "Enabled", true)
        HealthRow("Free AI providers", connectedAiCount(vault).let { if (it == 0) "Connect keys" else "$it connected" }, connectedAiCount(vault) > 0)
        HealthRow("GitHub verification build", "Tests + lint + APK", true)
        HealthRow("Backup builders", "Codemagic + Bitrise ready", true)
        Spacer(Modifier.height(16.dp))
        PocketForgeUpdaterCard()
        Spacer(Modifier.height(12.dp))
        Text("Provider secrets are encrypted at rest with a non-exportable Android Keystore key.", color = ForgeMuted, fontSize = 11.sp)
    }
}

private fun connectedAiCount(vault: SecretVault): Int = listOf(
    IntegrationKeys.GEMINI,
    IntegrationKeys.OPENROUTER,
    IntegrationKeys.GROQ
).count(vault::has)

@Composable
private fun HealthRow(name: String, state: String, healthy: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("●", color = if (healthy) ForgeAccent else ForgeWarn)
        Spacer(Modifier.width(10.dp))
        Text(name, color = ForgeText, modifier = Modifier.weight(1f))
        Text(state, color = ForgeMuted, fontSize = 12.sp)
    }
    HorizontalDivider(color = ForgeLine)
}

@Composable
private fun PublishScreen() {
    Page("Publish", "A green APK should come from verified code, not an AI promise.") {
        ForgeCardBlock {
            Text("Android build matrix", color = ForgeText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(15.dp))
            StatusLine("GitHub Actions", "Primary")
            StatusLine("Codemagic", "Optional verification")
            StatusLine("Bitrise", "Optional verification")
            StatusLine("Gate", "Unit tests + lint + APK signature")
            StatusLine("Updater", "Latest green GitHub release")
            Spacer(Modifier.height(12.dp))
            Text("Cross-check mode in Integration Center can intentionally run the same commit on every configured build provider.", color = ForgeMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = ForgeMuted, modifier = Modifier.weight(1f))
        Text(value, color = ForgeText)
    }
}

@Composable
private fun Page(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(title, color = ForgeText, fontWeight = FontWeight.Black, fontSize = 27.sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = ForgeMuted)
        Spacer(Modifier.height(20.dp))
        content()
    }
}

@Composable
private fun ForgeCardBlock(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ForgeCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ForgeLine)
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = ForgeMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun StatusPill(text: String) {
    Surface(color = Color(0xFF183326), shape = RoundedCornerShape(100.dp)) {
        Text(text, color = ForgeAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}
