package com.pocketforge.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WorkspaceProject(
    val id: String,
    val name: String,
    val brain: String,
    val updatedAt: Long
)

data class WorkspaceMessage(
    val id: String,
    val role: String,
    val text: String,
    val createdAt: Long
)

class WorkspaceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pocketforge_workspace", Context.MODE_PRIVATE)

    fun projects(): List<WorkspaceProject> {
        val raw = prefs.getString(KEY_PROJECTS, null)
        if (raw.isNullOrBlank()) {
            val starter = WorkspaceProject(
                id = "pocketforge",
                name = "PocketForge",
                brain = "PocketForge is a conversational software-building workspace. Protect working behavior, show visible agent progress, and require verified green builds.",
                updatedAt = System.currentTimeMillis()
            )
            saveProjects(listOf(starter))
            return listOf(starter)
        }
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                WorkspaceProject(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    brain = item.optString("brain"),
                    updatedAt = item.optLong("updatedAt")
                )
            }
        }.getOrElse { emptyList() }
    }

    fun createProject(name: String): WorkspaceProject {
        val clean = name.trim().ifBlank { "Untitled project" }
        val project = WorkspaceProject(
            id = UUID.randomUUID().toString(),
            name = clean,
            brain = "Goal and durable project decisions will accumulate here as PocketForge works.",
            updatedAt = System.currentTimeMillis()
        )
        saveProjects(listOf(project) + projects())
        return project
    }

    fun updateBrain(projectId: String, brain: String) {
        val updated = projects().map {
            if (it.id == projectId) it.copy(brain = brain.take(6000), updatedAt = System.currentTimeMillis()) else it
        }
        saveProjects(updated)
    }

    fun messages(projectId: String): List<WorkspaceMessage> {
        val raw = prefs.getString(messageKey(projectId), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                WorkspaceMessage(
                    id = item.getString("id"),
                    role = item.getString("role"),
                    text = item.getString("text"),
                    createdAt = item.optLong("createdAt")
                )
            }
        }.getOrElse { emptyList() }
    }

    fun appendMessage(projectId: String, role: String, text: String): WorkspaceMessage {
        val message = WorkspaceMessage(
            id = UUID.randomUUID().toString(),
            role = role,
            text = text,
            createdAt = System.currentTimeMillis()
        )
        val next = (messages(projectId) + message).takeLast(200)
        saveMessages(projectId, next)
        touch(projectId)
        return message
    }

    fun replaceMessages(projectId: String, messages: List<WorkspaceMessage>) {
        saveMessages(projectId, messages.takeLast(200))
        touch(projectId)
    }

    private fun touch(projectId: String) {
        saveProjects(projects().map {
            if (it.id == projectId) it.copy(updatedAt = System.currentTimeMillis()) else it
        })
    }

    private fun saveProjects(projects: List<WorkspaceProject>) {
        val array = JSONArray()
        projects.sortedByDescending { it.updatedAt }.forEach { project ->
            array.put(JSONObject()
                .put("id", project.id)
                .put("name", project.name)
                .put("brain", project.brain)
                .put("updatedAt", project.updatedAt))
        }
        prefs.edit().putString(KEY_PROJECTS, array.toString()).apply()
    }

    private fun saveMessages(projectId: String, messages: List<WorkspaceMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(JSONObject()
                .put("id", message.id)
                .put("role", message.role)
                .put("text", message.text)
                .put("createdAt", message.createdAt))
        }
        prefs.edit().putString(messageKey(projectId), array.toString()).apply()
    }

    private fun messageKey(projectId: String) = "messages_$projectId"

    companion object {
        private const val KEY_PROJECTS = "projects"
    }
}
