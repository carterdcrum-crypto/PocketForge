package com.pocketforge.app

import org.json.JSONArray
import org.json.JSONObject

data class StudioField(val id: String, val label: String, val type: String = "text", val required: Boolean = true)
data class StudioScreen(
    val id: String, val title: String, val type: String, val description: String = "",
    val fields: List<StudioField> = emptyList(), val operation: String = "sum", val unit: String = "",
    val content: String = ""
)
data class StudioSpec(val name: String, val tagline: String, val accent: String, val dark: Boolean, val screens: List<StudioScreen>) {
    fun json(): JSONObject = JSONObject().put("schemaVersion", 1).put("name", name).put("tagline", tagline)
        .put("accent", accent).put("dark", dark).put("screens", JSONArray().also { list ->
            screens.forEach { screen ->
                list.put(JSONObject().put("id", screen.id).put("title", screen.title).put("type", screen.type)
                    .put("description", screen.description).put("operation", screen.operation).put("unit", screen.unit)
                    .put("content", screen.content).put("fields", JSONArray().also { fields ->
                        screen.fields.forEach { fields.put(JSONObject().put("id", it.id).put("label", it.label).put("type", it.type).put("required", it.required)) }
                    }))
            }
        })

    fun validate(previous: StudioSpec? = null): StudioSpec {
        require(name.isNotBlank() && name.length <= 60) { "Give the app a name of 1–60 characters." }
        require(tagline.length <= 180 && Regex("#[0-9a-fA-F]{6}").matches(accent)) { "Choose a valid accent color and a short description." }
        require(screens.size in 1..5) { "An app needs 1–5 screens." }
        require(screens.map { it.id }.distinct().size == screens.size) { "Each screen needs its own identity." }
        screens.forEach { s ->
            require(validId(s.id) && s.title.isNotBlank() && s.title.length <= 24) { "Each screen needs a short name." }
            require(s.type in TYPES) { "That screen needs a feature this builder doesn't support yet." }
            require(s.description.length <= 300 && s.content.length <= 4000 && s.unit.length <= 12) { "Keep screen descriptions concise." }
            require(s.fields.size <= 6 && s.fields.map { it.id }.distinct().size == s.fields.size) { "Use up to six distinct fields per screen." }
            require(s.operation in OPERATIONS) { "Choose a supported calculation." }
            s.fields.forEach { f ->
                require(validId(f.id) && f.label.isNotBlank() && f.label.length <= 40 && f.type in FIELD_TYPES) { "A form field has an unsupported format." }
            }
            if (s.type != "info") require(s.fields.isNotEmpty()) { "Add a field to ${s.title}." }
            if (s.type == "ledger") require(s.fields.count { it.type == "number" } == 1) { "A total screen needs exactly one amount field." }
            if (s.type == "calculator") require(s.fields.size in 2..6 && s.fields.all { it.type == "number" }) { "Calculators need 2–6 number fields." }
        }
        previous?.screens?.forEach { old ->
            val next = screens.find { it.id == old.id }
            require(next != null && next.type == old.type) { "This change would replace ${old.title}. Ask for an additional screen instead." }
            old.fields.forEach { f -> require(next.fields.any { it.id == f.id && it.type == f.type }) { "This change would replace a saved field. Ask to rename it or add a new field instead." } }
        }
        return this
    }

    companion object {
        val TYPES = setOf("list", "checklist", "ledger", "calculator", "info")
        val FIELD_TYPES = setOf("text", "number", "date", "multiline")
        val OPERATIONS = setOf("sum", "product", "difference", "ratio")
        fun validId(id: String) = Regex("[a-z][a-z0-9_]{0,31}").matches(id)
        fun parse(raw: String): StudioSpec {
            require(raw.length <= 64_000) { "The app description is too large." }
            val json = JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            require(json.optInt("schemaVersion", 1) == 1) { "Update PocketForge to open this app." }
            val screens = json.getJSONArray("screens")
            return StudioSpec(json.getString("name"), json.optString("tagline"), json.getString("accent"), json.optBoolean("dark", true),
                (0 until screens.length()).map { index ->
                    val s = screens.getJSONObject(index)
                    val fields = s.optJSONArray("fields") ?: JSONArray()
                    StudioScreen(s.getString("id"), s.getString("title"), s.getString("type"), s.optString("description"),
                        (0 until fields.length()).map { i -> val f = fields.getJSONObject(i); StudioField(f.getString("id"), f.getString("label"), f.optString("type", "text"), f.optBoolean("required", true)) },
                        s.optString("operation", "sum"), s.optString("unit"), s.optString("content"))
                }).validate()
        }
    }
}

data class StudioStarter(val title: String, val detail: String, val symbol: String, val spec: StudioSpec)
object StudioStarters {
    val all = listOf(
        StudioStarter("Daily wins", "A checklist that keeps your day simple", "✓", StudioSpec("Daily wins", "Make room for what matters.", "#B9F178", true, listOf(
            StudioScreen("tasks", "Today", "checklist", "One small step at a time.", listOf(StudioField("task", "What do you want to do?"), StudioField("notes", "A little detail", "multiline", false))),
            StudioScreen("notes", "Notes", "list", "Ideas worth keeping.", listOf(StudioField("title", "Title"), StudioField("body", "Your note", "multiline")))
        ))),
        StudioStarter("Money journal", "Log expenses and see your real total", "$", StudioSpec("Money journal", "A clearer picture, one entry at a time.", "#8BC8FF", true, listOf(
            StudioScreen("expenses", "Expenses", "ledger", "Your entries stay on this device.", listOf(StudioField("item", "What was it?"), StudioField("amount", "Amount", "number"), StudioField("date", "Date", "date")), unit = "$"),
            StudioScreen("notes", "Notes", "list", "Keep your money goals here.", listOf(StudioField("goal", "Goal"), StudioField("detail", "Details", "multiline", false)))
        ))),
        StudioStarter("My clients", "Keep clients, jobs, and follow-ups together", "◎", StudioSpec("My clients", "Good work starts with good organization.", "#F8BD8D", false, listOf(
            StudioScreen("clients", "Clients", "list", "A personal address book for your work.", listOf(StudioField("name", "Client name"), StudioField("contact", "Contact", required = false), StudioField("notes", "Notes", "multiline", false))),
            StudioScreen("jobs", "Jobs", "checklist", "Keep track of the next step.", listOf(StudioField("job", "Job or follow-up"), StudioField("due", "Due date", "date", false)))
        ))),
        StudioStarter("Idea notebook", "Capture thoughts and turn them into actions", "✎", StudioSpec("Idea notebook", "Your next good idea belongs here.", "#D4B5FF", true, listOf(
            StudioScreen("ideas", "Ideas", "list", "No perfect wording needed.", listOf(StudioField("title", "Title"), StudioField("idea", "What's on your mind?", "multiline"))),
            StudioScreen("next", "Next steps", "checklist", "Give an idea a little momentum.", listOf(StudioField("step", "Next step")))
        ))),
        StudioStarter("Quick estimate", "A calculator you can tailor to your work", "×", StudioSpec("Quick estimate", "Get an answer without the spreadsheet.", "#7DDED5", false, listOf(
            StudioScreen("estimate", "Estimate", "calculator", "Multiply quantity by price or hours by rate.", listOf(StudioField("quantity", "Quantity", "number"), StudioField("rate", "Rate", "number")), operation = "product", unit = "$"),
            StudioScreen("quotes", "Saved quotes", "list", "Keep a note of estimates you want to remember.", listOf(StudioField("name", "Customer or job"), StudioField("quote", "Estimate details", "multiline")))
        )))
    )
}
