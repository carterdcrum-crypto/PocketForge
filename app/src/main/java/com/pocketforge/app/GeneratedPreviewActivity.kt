package com.pocketforge.app

import android.os.Bundle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GenBg = Color(0xFF07111E)
private val GenCard = Color(0xFF0F1C2B)
private val GenLine = Color(0xFF203249)
private val GenAccent = Color(0xFF5CE1E6)
private val GenText = Color(0xFFF5FAFF)
private val GenMuted = Color(0xFF93A8BE)

private enum class PreviewScreen(val label: String, val glyph: String) {
    Today("Today", "⌂"), History("History", "≡"), Settings("Settings", "⚙")
}

class GeneratedPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GeneratedAppPreview(onClose = ::finish) }
    }
}

@Composable
private fun GeneratedAppPreview(onClose: () -> Unit) {
    var screen by remember { mutableStateOf(PreviewScreen.Today) }
    var clockedIn by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GenAccent,
            background = GenBg,
            surface = GenCard,
            onPrimary = Color(0xFF001719),
            onBackground = GenText,
            onSurface = GenText
        )
    ) {
        Box(Modifier.fillMaxSize().background(GenBg)) {
            Scaffold(
                containerColor = GenBg,
                topBar = { PreviewTopBar() },
                bottomBar = {
                    NavigationBar(containerColor = Color(0xFF091522)) {
                        PreviewScreen.entries.forEach { item ->
                            NavigationBarItem(
                                selected = screen == item,
                                onClick = { screen = item },
                                icon = { Text(item.glyph, fontSize = 18.sp) },
                                label = { Text(item.label, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = GenAccent,
                                    selectedTextColor = GenAccent,
                                    indicatorColor = Color(0xFF123B43),
                                    unselectedIconColor = GenMuted,
                                    unselectedTextColor = GenMuted
                                )
                            )
                        }
                    }
                }
            ) { inner ->
                Box(Modifier.padding(inner).fillMaxSize()) {
                    when (screen) {
                        PreviewScreen.Today -> PreviewToday(clockedIn) { clockedIn = !clockedIn }
                        PreviewScreen.History -> PreviewHistory()
                        PreviewScreen.Settings -> PreviewSettings()
                    }
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 8.dp).clickable(onClick = onClose),
                color = Color(0xE6101010),
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(1.dp, Color(0xFF343434))
            ) {
                Text("‹ PocketForge", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
            }
        }
    }
}

@Composable
private fun PreviewTopBar() {
    Surface(color = GenBg) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Paycheck Check", color = GenText, fontWeight = FontWeight.Black, fontSize = 21.sp)
                Text("Full generated-app preview", color = GenMuted, fontSize = 11.sp)
            }
            Surface(color = Color(0xFF123B43), shape = CircleShape) {
                Text("$", color = GenAccent, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun PreviewToday(clockedIn: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text("THIS WEEK", color = GenMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(6.dp))
        Text("$684.50", color = GenText, fontSize = 43.sp, fontWeight = FontWeight.Black)
        Text("Estimated gross pay", color = GenMuted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        Surface(color = GenCard, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, GenLine)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    PreviewMetric("38h 30m", "Hours", Modifier.weight(1f))
                    PreviewMetric("$17.00", "Rate", Modifier.weight(1f))
                    PreviewMetric("2h 30m", "Overtime", Modifier.weight(1f))
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (clockedIn) Color(0xFFFF7676) else GenAccent,
                        contentColor = Color(0xFF001719)
                    )
                ) {
                    Text(if (clockedIn) "Clock out" else "Clock in", fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
                if (clockedIn) {
                    Spacer(Modifier.height(10.dp))
                    Text("● Shift running · started just now", color = GenAccent, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("TODAY", color = GenMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(8.dp))
        PreviewShift("8:02 AM – 4:31 PM", "8h 29m", "$144.22")
        Spacer(Modifier.height(10.dp))
        PreviewShift("6:10 PM – 8:05 PM", "1h 55m", "$32.58")
        Spacer(Modifier.height(18.dp))
        Surface(color = Color(0xFF10261F), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF21483B))) {
            Column(Modifier.padding(16.dp)) {
                Text("✓ Paycheck looks right", color = Color(0xFF7CFFB2), fontWeight = FontWeight.Bold)
                Text("This preview is interactive. Future generated projects will mount their own complete app surface here.", color = GenMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PreviewMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = GenText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = GenMuted, fontSize = 11.sp)
    }
}

@Composable
private fun PreviewShift(time: String, duration: String, pay: String) {
    Surface(color = GenCard, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, GenLine)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(time, color = GenText, fontWeight = FontWeight.SemiBold)
                Text(duration, color = GenMuted, fontSize = 12.sp)
            }
            Text(pay, color = GenAccent, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
    }
}

@Composable
private fun PreviewHistory() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Pay history", color = GenText, fontWeight = FontWeight.Black, fontSize = 28.sp)
        Text("Compare what you worked with what you should be paid.", color = GenMuted)
        Spacer(Modifier.height(20.dp))
        PreviewPeriod("Sep 7 – Sep 13", "$684.50", "40h 00m", true)
        Spacer(Modifier.height(12.dp))
        PreviewPeriod("Aug 31 – Sep 6", "$631.13", "36h 45m", true)
        Spacer(Modifier.height(12.dp))
        PreviewPeriod("Aug 24 – Aug 30", "$712.88", "41h 10m", false)
    }
}

@Composable
private fun PreviewPeriod(period: String, pay: String, hours: String, matched: Boolean) {
    Surface(color = GenCard, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, GenLine)) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(period, color = GenText, fontWeight = FontWeight.Bold)
                    Text(hours, color = GenMuted, fontSize = 12.sp)
                }
                Text(pay, color = GenText, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (matched) "✓ Matches expected pay" else "! Review this paycheck",
                color = if (matched) Color(0xFF7CFFB2) else Color(0xFFFFC66D),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PreviewSettings() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Settings", color = GenText, fontWeight = FontWeight.Black, fontSize = 28.sp)
        Text("Make the calculator match your real job.", color = GenMuted)
        Spacer(Modifier.height(22.dp))
        PreviewSetting("Hourly rate", "$17.00")
        PreviewSetting("Overtime", "1.5× after 40h")
        PreviewSetting("Pay frequency", "Weekly")
        PreviewSetting("Estimated withholding", "12%")
    }
}

@Composable
private fun PreviewSetting(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = GenText, modifier = Modifier.weight(1f))
        Text(value, color = GenAccent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
    HorizontalDivider(color = GenLine)
}
