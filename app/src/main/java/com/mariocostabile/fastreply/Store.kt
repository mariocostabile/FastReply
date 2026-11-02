package com.mariocostabile.fastreply

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Contact(val id: String, val name: String, val phone: String, val messages: List<String>)

data class AppData(val lastContactId: String? = null, val contacts: List<Contact> = emptyList()) {
    fun current(): Contact? = contacts.firstOrNull { it.id == lastContactId } ?: contacts.firstOrNull()
}

class Store(private val file: File) {

    fun load(): AppData = try {
        val root = JSONObject(file.readText())
        val contacts = root.getJSONArray("contacts")
        AppData(
            lastContactId = if (root.isNull("lastContactId")) null else root.getString("lastContactId"),
            contacts = List(contacts.length()) { i ->
                val c = contacts.getJSONObject(i)
                val msgs = c.getJSONArray("messages")
                Contact(c.getString("id"), c.getString("name"), c.getString("phone"), List(msgs.length()) { msgs.getString(it) })
            },
        )
    } catch (e: Exception) {
        AppData() // missing or corrupt file: start empty
    }

    fun save(data: AppData) {
        val root = JSONObject()
            .put("lastContactId", data.lastContactId ?: JSONObject.NULL)
            .put("contacts", JSONArray(data.contacts.map {
                JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone)
                    .put("messages", JSONArray(it.messages))
            }))
        // Write to a temp file then rename, so a crash mid-write never destroys the existing data.
        val tmp = File(file.path + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) error("cannot write ${file.path}")
    }
}
