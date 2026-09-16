package com.pocketforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

class AgentLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PocketForgeLauncherRoot() }
    }
}

@Composable
private fun PocketForgeLauncherRoot() {
    var showUpdater by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        PocketForgeStudioApp()

        if (BuildConfig.ENABLE_SIDELOAD_UPDATER) {
            Button(
                onClick = { showUpdater = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 14.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB9F178),
                    contentColor = Color(0xFF132011)
                )
            ) {
                Text("Update", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (BuildConfig.ENABLE_SIDELOAD_UPDATER && showUpdater) {
        Dialog(onDismissRequest = { showUpdater = false }) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp)
            ) {
                Box(Modifier.padding(14.dp)) {
                    PocketForgeUpdaterCard()
                }
            }
        }
    }
}
