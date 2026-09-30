package com.knotes.nibras

import org.json.JSONObject

/** A task as the Contacts page shows it — enough to recognise and to act on. */
data class ContactTask(
    val id: Long,
    val summary: String,
    val priority: Int?,
    val status: String,
    val due: String,
    val urgent: Boolean,
) {
    companion object {
        fun fromJson(o: JSONObject): ContactTask? = runCatching {
            ContactTask(
                id = o.getLong("id"),
                summary = str(o, "summary").ifBlank { return null },
                priority = if (o.isNull("priority")) null else o.optInt("priority"),
                status = str(o, "status"),
                due = str(o, "due"),
                urgent = o.optBoolean("urgent"),
            )
        }.getOrNull()
    }
}

/**
 * A bookmarked person and the work still open with them.
 *
 * Bookmarked is the selection rule, as it is everywhere else in Nibras: the contact table holds
 * nearly two hundred people and the handful that are bookmarked are the ones being worked with now.
 */
data class ContactWithTasks(
    val id: Long,
    val name: String,
    val jobTitle: String,
    val telephones: String,
    val email: String,
    val summary: String,
    val tasks: List<ContactTask>,
) {
    companion object {
        fun fromJson(o: JSONObject): ContactWithTasks? = runCatching {
            val arr = o.optJSONArray("tasks")
            ContactWithTasks(
                id = o.getLong("id"),
                name = str(o, "name").ifBlank { "(unnamed)" },
                jobTitle = str(o, "jobTitle"),
                telephones = str(o, "telephones"),
                email = str(o, "email"),
                summary = str(o, "summary"),
                tasks = if (arr == null) emptyList()
                else (0 until arr.length()).mapNotNull { ContactTask.fromJson(arr.getJSONObject(it)) },
            )
        }.getOrNull()
    }
}

/** `optString` returns the literal "null" for a JSON null; this does not. */
private fun str(o: JSONObject, key: String): String =
    if (o.isNull(key)) "" else o.optString(key)
