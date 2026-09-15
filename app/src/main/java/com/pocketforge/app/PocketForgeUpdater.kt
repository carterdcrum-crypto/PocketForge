package com.pocketforge.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

private const val RELEASES_URL = "https://api.github.com/repos/carterdcrum-crypto/PocketForge/releases?per_page=1"
private val UpdaterCard = Color(0xFF12161D)
private val UpdaterLine = Color(0xFF232A35)
private val UpdaterAccent = Color(0xFF7CFFB2)
private val UpdaterText = Color(0xFFF2F5F7)
private val UpdaterMuted = Color(0xFF9AA6B2)

private data class AvailableUpdate(
    val buildNumber: Long,
    val versionLabel: String,
    val apkUrl: String
)

@Composable
fun PocketForgeUpdaterCard() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Tap below to check the newest green build.") }
    var update by remember { mutableStateOf<AvailableUpdate?>(null) }
    var busy by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = UpdaterCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, UpdaterLine)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("PocketForge updater", color = UpdaterText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(5.dp))
            Text(status, color = UpdaterMuted, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    val ready = update
                    if (ready == null) {
                        busy = true
                        status = "Checking GitHub for a newer green build…"
                        PocketForgeUpdater.check(context) { result ->
                            busy = false
                            result.onSuccess { found ->
                                update = found
                                status = if (found == null) {
                                    "You already have the newest green build."
                                } else {
                                    "Build ${found.buildNumber} is ready. Tap Update PocketForge."
                                }
                            }.onFailure {
                                status = "Could not check for updates: ${it.message ?: "unknown error"}"
                            }
                        }
                    } else {
                        PocketForgeUpdater.installUpdate(context, ready) { message -> status = message }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = UpdaterAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    when {
                        busy -> "Checking…"
                        update != null -> "Update PocketForge"
                        else -> "Check for updates"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Android will show its normal install/update confirmation. The beta updater never installs silently.", color = UpdaterMuted, fontSize = 11.sp)
        }
    }
}

private object PocketForgeUpdater {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun check(context: Context, callback: (Result<AvailableUpdate?>) -> Unit) {
        Thread {
            val result = runCatching {
                val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "PocketForge-Android")
                }
                try {
                    if (connection.responseCode !in 200..299) {
                        error("GitHub returned ${connection.responseCode}")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val releases = JSONArray(body)
                    if (releases.length() == 0) return@runCatching null

                    val release = releases.getJSONObject(0)
                    val tag = release.optString("tag_name")
                    val remoteBuild = tag.removePrefix("build-").toLongOrNull() ?: return@runCatching null
                    val currentBuild = currentBuildNumber(context)
                    if (remoteBuild <= currentBuild) return@runCatching null

                    val assets = release.optJSONArray("assets") ?: return@runCatching null
                    var apkUrl: String? = null
                    for (index in 0 until assets.length()) {
                        val asset = assets.getJSONObject(index)
                        val name = asset.optString("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                    val resolvedUrl = apkUrl?.takeIf { it.isNotBlank() } ?: return@runCatching null
                    AvailableUpdate(remoteBuild, release.optString("name", tag), resolvedUrl)
                } finally {
                    connection.disconnect()
                }
            }
            mainHandler.post { callback(result) }
        }.start()
    }

    fun installUpdate(context: Context, update: AvailableUpdate, onStatus: (String) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            onStatus("Allow PocketForge to install updates once, then return and tap Update PocketForge again.")
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("PocketForge ${update.versionLabel}")
            .setDescription("Downloading the newest green beta build")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "PocketForge-build-${update.buildNumber}.apk")

        val downloadId = manager.enqueue(request)
        onStatus("Downloading build ${update.buildNumber}…")

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
                runCatching { context.unregisterReceiver(this) }
                val apkUri = manager.getUriForDownloadedFile(downloadId)
                if (apkUri == null) {
                    onStatus("Download failed. Tap Update PocketForge to try again.")
                    return
                }
                onStatus("Download complete. Opening Android installer…")
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(installIntent)
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun currentBuildNumber(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }
}
