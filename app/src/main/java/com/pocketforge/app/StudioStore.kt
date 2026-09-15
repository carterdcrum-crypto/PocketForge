package com.pocketforge.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class StudioVersion(val id: String, val time: Long, val summary: String, val spec: StudioSpec)
class StudioStore(context: Context) {
    private val directory = File(context.filesDir, "studio").apply { mkdirs() }
    private fun file(id: String): AtomicFile {
        require(Regex("[a-zA-Z0-9-]{1,80}").matches(id)) { "Invalid project identity." }
        return AtomicFile(File(directory, "$id.json"))
    }
    @Synchronized fun versions(id: String): List<StudioVersion> {
        val source = file(id)
        if (!source.baseFile.exists() && !File(source.baseFile.path + ".bak").exists()) return emptyList()
        val list = JSONArray(source.openRead().use { it.bufferedReader().readText() })
        return (0 until list.length()).map { i ->
            val v = list.getJSONObject(i)
            StudioVersion(v.getString("id"), v.getLong("time"), v.getString("summary"), StudioSpec.parse(v.getJSONObject("spec").toString()))
        }
    }
    @Synchronized fun save(id: String, spec: StudioSpec, summary: String): StudioVersion {
        spec.validate()
        val version = StudioVersion(UUID.randomUUID().toString(), System.currentTimeMillis(), summary.take(500), spec)
        val all = (versions(id) + version).takeLast(40)
        val data = JSONArray().also { list -> all.forEach { list.put(JSONObject().put("id", it.id).put("time", it.time).put("summary", it.summary).put("spec", it.spec.json())) } }
        val target = file(id)
        val stream = target.startWrite()
        try { stream.write(data.toString().toByteArray(Charsets.UTF_8)); target.finishWrite(stream) }
        catch (e: Exception) { target.failWrite(stream); throw e }
        return version
    }

    @Synchronized fun latest(id: String): StudioVersion? = versions(id).maxByOrNull { it.time }
}

object StudioDocument {
    fun config(spec: StudioSpec, projectId: String): String = JSONObject().put("projectId", projectId).put("spec", spec.json()).toString()
        .replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    fun html(context: Context, spec: StudioSpec, projectId: String): String {
        val assets = context.assets
        fun asset(name: String) = assets.open("studio/$name").bufferedReader().use { it.readText() }
        return asset("shell.html").replace("__STYLE__", asset("app.css")).replace("__SCRIPT__", asset("app.js")).replace("__CONFIG__", config(spec, projectId))
    }
    fun backup(spec: StudioSpec): String = JSONObject().put("format", "pocketforge-design").put("version", 1).put("spec", spec.json()).toString(2)
    fun restore(raw: String): StudioSpec {
        require(raw.length <= 70_000) { "This file is too large for an app design." }
        val json = JSONObject(raw)
        require(json.optString("format") == "pocketforge-design" && json.optInt("version") == 1) { "Choose a PocketForge app design file." }
        return StudioSpec.parse(json.getJSONObject("spec").toString())
    }
}
