package com.pocketforge.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

/** Full-screen preview of the selected user's declarative app design. */
class GeneratedPreviewActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectId = intent.getStringExtra("pocketforge.project_id").orEmpty()
        val store = StudioStore(this)
        val spec = ensureDesign(store, projectId)
        if (projectId.isNotBlank() && store.latest(projectId) == null) {
            store.save(projectId, spec, "Created a safe offline starter")
        }
        val web = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = WebViewClient()
        }
        setContentView(web)
        web.loadDataWithBaseURL("https://pocketforge.local/", StudioDocument.html(this, spec, projectId.ifBlank { "preview" }), "text/html", "UTF-8", null)
    }
}
