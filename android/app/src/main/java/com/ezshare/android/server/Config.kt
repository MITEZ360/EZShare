package com.ezshare.android.server

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ShareSettings(
    var port: Int = 8080,
    var allowUpload: Boolean = true,
    var allowDelete: Boolean = false,
    var requireCode: Boolean = false,
    var accessCode: String = "",
    var treeUri: String = ""
)

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("ezshare_config", Context.MODE_PRIVATE)

    init {
        if (prefs.getString("accessCode", "").isNullOrEmpty()) {
            prefs.edit().putString("accessCode", generateCode()).apply()
        }
    }

    val settings: ShareSettings
        get() = ShareSettings(
            port = prefs.getInt("port", 8080),
            allowUpload = prefs.getBoolean("allowUpload", true),
            allowDelete = prefs.getBoolean("allowDelete", false),
            requireCode = prefs.getBoolean("requireCode", false),
            accessCode = prefs.getString("accessCode", "") ?: "",
            treeUri = prefs.getString("treeUri", "") ?: ""
        )

    fun update(s: ShareSettings) {
        prefs.edit()
            .putInt("port", s.port)
            .putBoolean("allowUpload", s.allowUpload)
            .putBoolean("allowDelete", s.allowDelete)
            .putBoolean("requireCode", s.requireCode)
            .putString("accessCode", s.accessCode)
            .putString("treeUri", s.treeUri)
            .apply()
    }

    fun regenerateCode(): String {
        val code = generateCode()
        prefs.edit().putString("accessCode", code).apply()
        return code
    }

    companion object {
        fun generateCode(): String {
            val digits = "23456789"
            val letters = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ"
            val all = digits + letters
            while (true) {
                val code = (0 until 6).map { all.random() }.joinToString("")
                if (code.any { it in digits } && code.any { it in letters }) return code
            }
        }
    }
}

data class TextSnippet(val id: String, val content: String, val createdAt: Long)

class TextStore(context: Context) {
    private val prefs = context.getSharedPreferences("ezshare_texts", Context.MODE_PRIVATE)
    private val lock = Any()

    fun list(): List<TextSnippet> = synchronized(lock) {
        val raw = prefs.getString("items", "[]") ?: "[]"
        val result = mutableListOf<TextSnippet>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result += TextSnippet(
                    id = obj.optString("id"),
                    content = obj.optString("content"),
                    createdAt = obj.optLong("at")
                )
            }
        } catch (_: Exception) {
            // 数据损坏时按空处理，不崩溃
        }
        result.sortedByDescending { it.createdAt }
    }

    fun count(): Int = list().size

    fun add(content: String): TextSnippet? {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || trimmed.length > 1_000_000) return null
        val item = TextSnippet(UUID.randomUUID().toString(), content, System.currentTimeMillis())
        synchronized(lock) {
            val all = list().toMutableList()
            all.add(0, item)
            while (all.size > 200) all.removeAt(all.size - 1)
            save(all)
        }
        return item
    }

    fun delete(id: String) {
        synchronized(lock) {
            save(list().filter { it.id != id })
        }
    }

    private fun save(items: List<TextSnippet>) {
        val array = JSONArray()
        items.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("content", it.content)
            obj.put("at", it.createdAt)
            array.put(obj)
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
