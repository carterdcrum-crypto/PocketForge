package com.pocketforge.app

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val RELEASES_URL = "https://api.github.com/repos/carterdcrum-crypto/PocketForge/releases?per_page=1"
private const val INSTALL_PREFS = "pocketforge_beta_install"
private const val INSTALL_STATUS_KEY = "last_status"
internal const val INSTALL_STATUS_UI_ACTION = "com.pocketforge.app.BETA_INSTALL_STATUS_UI"
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

/**
 * Beta-only updater surface. ENABLE_SIDELOAD_UPDATER is false in the Play/release build,
 * letting R8 remove this path from the optimized production artifact.
 */
@Composable
fun PocketForgeUpdaterCard() {
    if (!BuildConfig.ENABLE_SIDELOAD_UPDATER) return

    val context = LocalContext.current
    val storedStatus = remember { betaInstallStatus(context) }
    var status by remember { mutableStateOf(storedStatus ?: "Tap below to check the newest green build.") }
    var update by remember { mutableStateOf<AvailableUpdate?>(null) }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                intent?.getStringExtra("message")?.takeIf { it.isNotBlank() }?.let { status = it }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(INSTALL_STATUS_UI_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = UpdaterCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, UpdaterLine)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("PocketForge updater", color = UpdaterText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(5.dp))
            Text(status, color = UpdaterMuted, fontSize = 12.sp, lineHeight = 17.sp)
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
                        PocketForgeUpdater.installUpdate(context, ready) { message ->
                            status = message
                            saveBetaInstallStatus(context, message)
                        }
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
            OutlinedButton(
                onClick = { context.startActivity(Intent(context, IntegrationCenterActivity::class.java)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("AI & build connectors")
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "PocketForge verifies the downloaded APK belongs to this app and is signed by a certificate Android can update before opening the installer.",
                color = UpdaterMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

private object PocketForgeUpdater {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun check(context: Context, callback: (Result<AvailableUpdate?>) -> Unit) {
        check(BuildConfig.ENABLE_SIDELOAD_UPDATER) { "The sideload updater is disabled in this build." }
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
                    if (connection.responseCode !in 200..299) error("GitHub returned ${connection.responseCode}")
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
                        if (asset.optString("name").equals("app-debug.apk", ignoreCase = true)) {
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
        check(BuildConfig.ENABLE_SIDELOAD_UPDATER) { "The sideload updater is disabled in this build." }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            onStatus("Allow PocketForge to install updates once, then return and tap Update PocketForge again.")
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }

        val downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: run {
                onStatus("Android did not provide a private download folder. Try again after restarting PocketForge.")
                return
            }
        val apkFile = File(downloads, "PocketForge-build-${update.buildNumber}.apk")
        runCatching { if (apkFile.exists()) apkFile.delete() }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("PocketForge ${update.versionLabel}")
            .setDescription("Downloading the newest green beta build")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(apkFile))

        val downloadId = manager.enqueue(request)
        onStatus("Downloading build ${update.buildNumber}…")

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
                runCatching { context.unregisterReceiver(this) }

                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = manager.query(query)
                val downloadResult = runCatching {
                    cursor.use {
                        if (!it.moveToFirst()) error("Android lost the download record.")
                        val statusIndex = it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        val statusValue = it.getInt(statusIndex)
                        if (statusValue != DownloadManager.STATUS_SUCCESSFUL) {
                            val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            error("Download failed (Android reason $reason).")
                        }
                    }
                }
                downloadResult.onFailure {
                    onStatus("${it.message ?: "Download failed."} Tap Update PocketForge to try again.")
                    return
                }

                val preflight = preflightApk(context, apkFile, update.buildNumber)
                if (preflight.isFailure) {
                    onStatus(preflight.exceptionOrNull()?.message ?: "The downloaded APK did not pass PocketForge verification.")
                    return
                }

                onStatus("Download verified. Preparing Android’s secure update confirmation…")
                runCatching { installWithPackageInstaller(context, apkFile) }
                    .onFailure { error ->
                        onStatus("Could not start Android’s installer: ${error.message ?: "unknown error"}")
                    }
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun preflightApk(context: Context, apkFile: File, expectedBuild: Long): Result<Unit> = runCatching {
        require(apkFile.isFile && apkFile.length() > 0L) { "The downloaded APK file is missing or empty." }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: error("Android could not read the downloaded APK.")
        require(archive.packageName == context.packageName) {
            "Update rejected: the downloaded APK belongs to ${archive.packageName}, not PocketForge."
        }
        require(packageVersionCode(archive) >= expectedBuild) {
            "Update rejected: the downloaded APK version does not match build $expectedBuild."
        }

        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val installedSigners = signerDigests(installed)
        val archiveSigners = signerDigests(archive)
        require(installedSigners.isNotEmpty() && archiveSigners.isNotEmpty()) {
            "Update rejected: Android could not verify the APK signing certificate."
        }
        require(installedSigners.intersect(archiveSigners).isNotEmpty()) {
            "This beta APK is signed with a different key than the PocketForge version installed on your phone. Android cannot update it in place. This is the old beta-signing bug; do not keep waiting on the installer."
        }
    }

    private fun installWithPackageInstaller(context: Context, apkFile: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            FileInputStream(apkFile).use { input ->
                session.openWrite("PocketForge.apk", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val resultIntent = Intent(context, BetaInstallStatusReceiver::class.java)
                .setAction("${context.packageName}.BETA_INSTALL.$sessionId")
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, resultIntent, flags)
            session.commit(pendingIntent.intentSender)
            saveBetaInstallStatus(context, "Waiting for Android’s install confirmation…")
        } finally {
            session.close()
        }
    }

    private fun currentBuildNumber(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return packageVersionCode(info)
    }

    private fun packageVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            (if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.signatures.orEmpty()
        }
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }.toSet()
    }
}

/** Receives PackageInstaller status for beta builds and opens Android's required confirmation UI. */
class BetaInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (confirmation != null) {
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmation)
                    "Android is asking you to confirm the PocketForge update."
                } else {
                    "Android requested install confirmation but did not provide the confirmation screen."
                }
            }
            PackageInstaller.STATUS_SUCCESS -> "PocketForge updated successfully."
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Update cancelled. Your current PocketForge build was left unchanged."
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the update. Check Install unknown apps permission for PocketForge."
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "Android rejected the update because the installed app and new APK conflict, usually because their signing certificates do not match."
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Android says this APK is not compatible with the device."
            PackageInstaller.STATUS_FAILURE_INVALID -> "Android rejected the downloaded APK as invalid."
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Android could not install the update because there is not enough usable storage."
            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                "PocketForge update failed${if (detail.isBlank()) "." else ": $detail"}"
            }
        }
        saveBetaInstallStatus(context, message)
        context.sendBroadcast(
            Intent(INSTALL_STATUS_UI_ACTION)
                .setPackage(context.packageName)
                .putExtra("message", message)
        )
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}

private fun betaInstallStatus(context: Context): String? =
    context.getSharedPreferences(INSTALL_PREFS, Context.MODE_PRIVATE)
        .getString(INSTALL_STATUS_KEY, null)

private fun saveBetaInstallStatus(context: Context, message: String) {
    context.getSharedPreferences(INSTALL_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(INSTALL_STATUS_KEY, message)
        .apply()
}
