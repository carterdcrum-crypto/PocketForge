package com.pocketforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ForgeBg = Color(0xFF0A0C10)
private val ForgeCard = Color(0xFF12161D)
private val ForgeLine = Color(0xFF232A35)
private val ForgeAccent = Color(0xFF7CFFB2)
private val ForgeText = Color(0xFFF2F5F7)
private val ForgeMuted = Color(0xFF9AA6B2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PocketForgeApp() }
    }
}

enum class ForgeTab(val label: String) {
    Build("Build"), Preview("Preview"), Changes("Changes"), Health("Health"), Publish("Publish")
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
            topBar = { ForgeTopBar() },
            bottomBar = {
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
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                when (tab) {
                    ForgeTab.Build -> BuildScreen()
                    ForgeTab.Preview -> PreviewScreen()
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(ForgeAccent, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("P", color = Color.Black, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("PocketForge", color = ForgeText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("You describe it. It builds it.", color = ForgeMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            StatusPill("READY")
        }
    }
}

@Composable
private fun BuildScreen() {
    var prompt by remember { mutableStateOf("Make me an app that tracks my work hours and tells me what my paycheck should be.") }
    var planned by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text("What do you want to build?", fontSize = 26.sp, fontWeight = FontWeight.Black, color = ForgeText)
        Spacer(Modifier.height(6.dp))
        Text("Talk normally. PocketForge handles the code, project files and build system.", color = ForgeMuted)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it; planned = false },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp),
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
        Button(
            onClick = { planned = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ForgeAccent, contentColor = Color.Black)
        ) {
            Text("Plan this build", fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp))
        }
        Spacer(Modifier.height(22.dp))
        if (planned) ChangeContract(prompt) else StarterIdeas()
    }
}

@Composable
private fun StarterIdeas() {
    SectionTitle("STARTER IDEAS")
    ForgeCardBlock {
        Idea("Paycheck checker", "Track hours, overtime and expected pay")
        HorizontalDivider(color = ForgeLine)
        Idea("Client photo app", "Before/after photos with customer history")
        HorizontalDivider(color = ForgeLine)
        Idea("Local inventory", "Scan items and track what's in stock")
    }
}

@Composable
private fun Idea(title: String, body: String) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(title, color = ForgeText, fontWeight = FontWeight.SemiBold)
        Text(body, color = ForgeMuted, fontSize = 13.sp)
    }
}

@Composable
private fun ChangeContract(prompt: String) {
    SectionTitle("CHANGE CONTRACT")
    ForgeCardBlock {
        Text("PocketForge understood:", color = ForgeMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(prompt, color = ForgeText, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        ContractRow("Scope", "New app foundation")
        ContractRow("Allowed", "UI, local data, navigation")
        ContractRow("Protected", "Anything unrelated to this request")
        ContractRow("Verify", "Compile + smoke checks before saving")
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20262F), contentColor = ForgeText)
        ) { Text("Build from this plan") }
    }
}

@Composable
private fun ContractRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = ForgeMuted, modifier = Modifier.width(84.dp), fontSize = 13.sp)
        Text(value, color = ForgeText, fontSize = 13.sp)
    }
}

@Composable
private fun PreviewScreen() {
    Page("Preview", "See the app, not the code.") {
        ForgeCardBlock {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(Color(0xFF223148), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text("$", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Paycheck Check", color = ForgeText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Generated app preview", color = ForgeMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("THIS WEEK", color = ForgeMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("$684.50", color = ForgeText, fontSize = 38.sp, fontWeight = FontWeight.Black)
            Text("Estimated gross pay", color = ForgeMuted)
            Spacer(Modifier.height(20.dp))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Clock in") }
        }
        Spacer(Modifier.height(14.dp))
        Text("This is the product philosophy: users manipulate the result, while PocketForge manages the implementation underneath.", color = ForgeMuted, fontSize = 13.sp)
    }
}

@Composable
private fun ChangesScreen() {
    Page("Changes", "Project history in human language.") {
        ChangeItem("Created app foundation", "Home screen, navigation and local storage", "Saved")
        ChangeItem("Added paycheck estimate", "Hourly pay and overtime calculation", "Saved")
        ChangeItem("Protected project rules", "AI change contract enabled", "Saved")
    }
}

@Composable
private fun ChangeItem(title: String, body: String, status: String) {
    ForgeCardBlock {
        Row {
            Column(Modifier.weight(1f)) {
                Text(title, color = ForgeText, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(body, color = ForgeMuted, fontSize = 13.sp)
            }
            Text("✓ $status", color = ForgeAccent, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun HealthScreen() {
    Page("Health", "No compiler gibberish.") {
        HealthRow("App structure", "Working", true)
        HealthRow("Android build", "Passed", true)
        HealthRow("Smoke checks", "Passed", true)
        HealthRow("AI provider", "Not connected", false)
        Spacer(Modifier.height(12.dp))
        ForgeCardBlock {
            Text("Everything needed for this preview is healthy.", color = ForgeText, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text("When something fails, PocketForge will translate the technical error into plain English and offer the safest repair.", color = ForgeMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun HealthRow(name: String, state: String, healthy: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (healthy) "●" else "●", color = if (healthy) ForgeAccent else Color(0xFFFFCA68))
        Spacer(Modifier.width(10.dp))
        Text(name, color = ForgeText, modifier = Modifier.weight(1f))
        Text(state, color = ForgeMuted, fontSize = 13.sp)
    }
    HorizontalDivider(color = ForgeLine)
}

@Composable
private fun PublishScreen() {
    Page("Publish", "The finish line should be one button, not a tutorial.") {
        ForgeCardBlock {
            Text("Android", color = ForgeText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("PocketForge Preview · 0.1.0", color = ForgeMuted, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            StatusLine("Build", "Ready")
            StatusLine("Signing", "Debug preview")
            StatusLine("APK", "Available from GitHub build")
            Spacer(Modifier.height(18.dp))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ForgeAccent, contentColor = Color.Black)) {
                Text("Generate APK", fontWeight = FontWeight.Bold)
            }
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
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
        border = androidx.compose.foundation.BorderStroke(1.dp, ForgeLine)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
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
