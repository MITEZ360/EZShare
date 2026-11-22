package com.ezshare.android.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class RemoteInfo(
    val name: String,
    val allowUpload: Boolean,
    val allowDelete: Boolean,
    val requireCode: Boolean
)

data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: String
)

data class RemoteFiles(
    val path: String,
    val entries: List<RemoteFile>,
    val allowUpload: Boolean,
    val allowDelete: Boolean
)

data class RemoteText(
    val id: String,
    val content: String,
    val createdAt: String
)

/// 加入其他设备共享时使用的轻量 HTTP 客户端。Cookie 保存在内存中，断开即失效。
class RemoteClient(val baseUrl: String) {
    private val session = LinkedHashMap<String, String>()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                for (cookie in cookies) session[cookie.name] = cookie.value
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val result = mutableListOf<Cookie>()
                for ((name, value) in session) {
                    Cookie.parse(url, "$name=$value")?.let { result.add(it) }
                }
                return result
            }
        })
        .build()

    val hasSession: Boolean get() = session.isNotEmpty()

    fun clearSession() {
        session.clear()
    }

    suspend fun login(code: String): Boolean = withContext(Dispatchers.IO) {
        val body = "code=${encode(code)}"
            .toRequestBody("application/x-www-form-urlencoded; charset=utf-8".toMediaType())
        val request = Request.Builder().url("$baseUrl/login").post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code !in 200..399) return@withContext false
            }
            info() != null
        } catch (_: Exception) {
            false
        }
    }

    suspend fun info(): RemoteInfo? = withContext(Dispatchers.IO) {
        val obj = json("/api/info") ?: return@withContext null
        if (!obj.optBoolean("ok")) return@withContext null
        RemoteInfo(
            name = obj.optString("name", "EZShare"),
            allowUpload = obj.optBoolean("allowUpload"),
            allowDelete = obj.optBoolean("allowDelete"),
            requireCode = obj.optBoolean("requireCode")
        )
    }

    suspend fun files(path: String): RemoteFiles? = withContext(Dispatchers.IO) {
        val obj = json("/api/files?path=${encode(path)}") ?: return@withContext null
        if (!obj.optBoolean("ok")) return@withContext null
        val array = obj.optJSONArray("entries")
        val entries = mutableListOf<RemoteFile>()
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                entries.add(
                    RemoteFile(
                        name = item.optString("name"),
                        path = item.optString("path"),
                        isDirectory = item.optBoolean("isDirectory"),
                        size = item.optLong("size"),
                        modified = item.optString("modified")
                    )
                )
            }
        }
        RemoteFiles(
            path = obj.optString("path"),
            entries = entries,
            allowUpload = obj.optBoolean("allowUpload"),
            allowDelete = obj.optBoolean("allowDelete")
        )
    }

    suspend fun texts(): List<RemoteText> = withContext(Dispatchers.IO) {
        val obj = json("/api/texts") ?: return@withContext emptyList()
        val array = obj.optJSONArray("items") ?: return@withContext emptyList()
        val result = mutableListOf<RemoteText>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(
                RemoteText(
                    id = item.optString("id"),
                    content = item.optString("content"),
                    createdAt = item.optString("createdAt")
                )
            )
        }
        result
    }

    suspend fun addText(content: String): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("content", content).toString()
        ok(postJson("/api/texts", payload))
    }

    suspend fun deleteText(id: String): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("id", id).toString()
        ok(postJson("/api/text/delete", payload))
    }

    suspend fun mkdir(dir: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("dir", dir).put("name", name).toString()
        ok(postJson("/api/mkdir", payload))
    }

    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("path", path).toString()
        ok(postJson("/api/delete", payload))
    }

    suspend fun upload(dir: String, name: String, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/upload?dir=${encode(dir)}&name=${encode(name)}"
        val body = data.toRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder().url(url).put(body).build()
        try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful && response.body?.string()?.let { runCatching { JSONObject(it).optBoolean("ok") }.getOrDefault(false) } == true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun downloadUrl(path: String): String = "$baseUrl/download?path=${encode(path)}"

    fun textDownloadUrl(id: String): String = "$baseUrl/api/text/download?id=${encode(id)}"

    private suspend fun json(path: String): JSONObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl$path").get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val raw = response.body?.string() ?: return@withContext null
                JSONObject(raw)
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun postJson(path: String, payload: String): JSONObject? = withContext(Dispatchers.IO) {
        val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url("$baseUrl$path").post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string() ?: return@withContext null
                JSONObject(raw)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun ok(obj: JSONObject?): Boolean = obj?.optBoolean("ok") == true

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
