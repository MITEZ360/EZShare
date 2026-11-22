package com.ezshare.android.server

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.util.Collections
import java.util.Locale
import java.util.UUID

data class Req(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>
)

data class Res(
    val status: Int = 200,
    val contentType: String = "text/plain; charset=utf-8",
    val body: ByteArray = ByteArray(0),
    val headers: Map<String, String> = emptyMap(),
    val stream: InputStream? = null,
    val streamLength: Long = -1,
    val offset: Long = 0
)

class Router(
    private val context: Context,
    private val config: ConfigStore,
    private val root: ShareRoot,
    private val texts: TextStore,
    private val web: WebUI
) {
    private val sessions = Collections.synchronizedSet(mutableSetOf<String>())

    private fun okJson(obj: JSONObject, status: Int = 200) = Res(
        status, "application/json; charset=utf-8", obj.toString().toByteArray(Charsets.UTF_8)
    )

    private fun html(body: String, status: Int = 200) = Res(
        status, "text/html; charset=utf-8", body.toByteArray(Charsets.UTF_8)
    )

    private fun text(body: String, status: Int = 200) = Res(
        status, "text/plain; charset=utf-8", body.toByteArray(Charsets.UTF_8)
    )

    private fun baseUrl(req: Req): String {
        val host = req.headers["host"] ?: "localhost"
        return "http://$host"
    }

    private fun authorized(req: Req): Boolean {
        val settings = config.settings
        if (!settings.requireCode) return true
        val cookie = req.headers["cookie"] ?: return false
        return sessions.any { cookie.contains("ezs=$it") }
    }

    private fun unauthorized(req: Req): Res? {
        if (authorized(req)) return null
        val accept = req.headers["accept"] ?: ""
        if (accept.contains("application/json") || req.path.startsWith("/api/") || req.path == "/download") {
            return Res(401, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"需要访问码\"}".toByteArray(Charsets.UTF_8))
        }
        return html(web.login(null, root.displayName), 401)
    }

    fun handle(req: Req, input: InputStream): Res {
        unauthorized(req)?.let { return it }

        return when {
            req.path == "/health" -> text("ok")

            req.path == "/login" && req.method == "POST" -> {
                val body = readBody(input, req.headers, 4096).toString(Charsets.UTF_8)
                val code = formValue(body, "code")
                if (code == config.settings.accessCode) {
                    val token = UUID.randomUUID().toString().replace("-", "")
                    sessions.add(token)
                    Res(
                        302,
                        "text/plain; charset=utf-8",
                        "OK".toByteArray(),
                        mapOf(
                            "Set-Cookie" to "ezs=$token; Path=/; HttpOnly; SameSite=Lax",
                            "Location" to "/"
                        )
                    )
                } else {
                    html(web.login("访问码不正确", root.displayName), 401)
                }
            }

            req.path == "/" && req.method == "GET" -> {
                val dir = req.query["path"] ?: ""
                val settings = config.settings
                html(web.listing(dir, root.list(dir), settings, root.displayName, baseUrl(req)))
            }

            req.path == "/text" && req.method == "GET" -> {
                val settings = config.settings
                html(web.textPage(texts.list(), settings, root.displayName, baseUrl(req)))
            }

            req.path == "/download" && req.method == "GET" -> {
                val path = req.query["path"] ?: ""
                val file = root.resolve(path)
                if (file == null || !file.isFile) {
                    html(web.errorPage(404, "找不到这个文件"), 404)
                } else {
                    val stream = try {
                        context.contentResolver.openInputStream(file.uri)
                    } catch (_: Exception) {
                        null
                    }
                    if (stream == null) {
                        text("无法读取文件", 500)
                    } else {
                        val len = file.length()
                        var offset = 0L
                        var end = len - 1
                        var status = 200
                        val extra = mutableMapOf<String, String>()
                        val range = req.headers["range"]
                        if (!range.isNullOrEmpty() && range.startsWith("bytes=")) {
                            val spec = range.removePrefix("bytes=")
                            val parts = spec.split("-")
                            val parsedStart = parts.getOrNull(0)?.toLongOrNull()
                            val parsedEnd = parts.getOrNull(1)?.toLongOrNull()
                            if (parsedStart != null && len > 0 && parsedStart < len) {
                                offset = parsedStart
                                end = parsedEnd?.coerceAtMost(len - 1) ?: (len - 1)
                                status = 206
                                extra["Content-Range"] = "bytes $offset-$end/$len"
                            }
                        }
                        val length = if (len <= 0) 0 else end - offset + 1
                        Res(
                            status, MimeTypes.of(file.name ?: path),
                            ByteArray(0),
                            extra + mapOf(
                                "Content-Disposition" to "attachment; filename*=UTF-8''${WebUI.enc(file.name ?: "file")}"
                            ),
                            stream, length, offset
                        )
                    }
                }
            }

            req.path == "/api/upload" && req.method == "PUT" -> {
                if (!config.settings.allowUpload) {
                    return okJson(JSONObject().put("ok", false).put("error", "当前不允许上传"), 403)
                }
                val dir = req.query["dir"] ?: ""
                val name = req.query["name"] ?: ""
                val file = root.createFile(dir, name)
                if (file == null) {
                    okJson(JSONObject().put("ok", false).put("error", "无法创建文件"), 500)
                } else {
                    val output = try {
                        context.contentResolver.openOutputStream(file.uri, "wt")
                    } catch (_: Exception) {
                        null
                    }
                    if (output == null) {
                        okJson(JSONObject().put("ok", false).put("error", "无法写入文件"), 500)
                    } else {
                        val written = copyStream(input, output, req.headers)
                        try { output.flush() } catch (_: Exception) {}
                        try { output.close() } catch (_: Exception) {}
                        okJson(
                            JSONObject()
                                .put("ok", true)
                                .put("name", file.name ?: name)
                                .put("size", written)
                        )
                    }
                }
            }

            req.path == "/api/mkdir" && req.method == "POST" -> {
                if (!config.settings.allowUpload) {
                    okJson(JSONObject().put("ok", false).put("error", "当前不允许新建"), 403)
                } else {
                    val obj = jsonBody(input, req.headers)
                    val dir = obj?.optString("dir") ?: ""
                    val name = obj?.optString("name") ?: ""
                    val created = root.createDir(dir, name)
                    if (created == null) {
                        okJson(JSONObject().put("ok", false).put("error", "创建失败"), 500)
                    } else {
                        okJson(JSONObject().put("ok", true).put("name", created.name ?: name))
                    }
                }
            }

            req.path == "/api/delete" && req.method == "POST" -> {
                if (!config.settings.allowDelete) {
                    okJson(JSONObject().put("ok", false).put("error", "当前不允许删除"), 403)
                } else {
                    val obj = jsonBody(input, req.headers)
                    val path = obj?.optString("path") ?: ""
                    if (path.isEmpty() || path == "/") {
                        okJson(JSONObject().put("ok", false).put("error", "不能删除根目录"), 400)
                    } else if (root.delete(path)) {
                        okJson(JSONObject().put("ok", true))
                    } else {
                        okJson(JSONObject().put("ok", false).put("error", "删除失败"), 500)
                    }
                }
            }

            req.path == "/api/texts" && req.method == "POST" -> {
                if (!config.settings.allowUpload) {
                    okJson(JSONObject().put("ok", false).put("error", "当前不允许写入"), 403)
                } else {
                    val obj = jsonBody(input, req.headers)
                    val content = obj?.optString("content") ?: ""
                    val item = texts.add(content)
                    if (item == null) {
                        okJson(JSONObject().put("ok", false).put("error", "内容不能为空或过长"), 400)
                    } else {
                        okJson(JSONObject().put("ok", true).put("id", item.id))
                    }
                }
            }

            req.path == "/api/text" && req.method == "GET" -> {
                val id = req.query["id"] ?: ""
                val item = texts.list().firstOrNull { it.id == id }
                if (item == null) {
                    okJson(JSONObject().put("ok", false).put("error", "找不到这条文本"), 404)
                } else {
                    okJson(JSONObject().put("ok", true).put("id", item.id).put("content", item.content).put("at", item.createdAt))
                }
            }

            req.path == "/api/text/delete" && req.method == "POST" -> {
                if (!config.settings.allowDelete) {
                    okJson(JSONObject().put("ok", false).put("error", "当前不允许删除"), 403)
                } else {
                    val obj = jsonBody(input, req.headers)
                    texts.delete(obj?.optString("id") ?: "")
                    okJson(JSONObject().put("ok", true))
                }
            }

            req.path == "/api/text/download" && req.method == "GET" -> {
                val id = req.query["id"] ?: ""
                val item = texts.list().firstOrNull { it.id == id }
                if (item == null) {
                    text("找不到文本", 404)
                } else {
                    val name = "文本-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(java.util.Date(item.createdAt)) + ".txt"
                    Res(
                        200, "text/plain; charset=utf-8", item.content.toByteArray(Charsets.UTF_8),
                        mapOf("Content-Disposition" to "attachment; filename*=UTF-8''${WebUI.enc(name)}")
                    )
                }
            }

            else -> html(web.errorPage(404, "找不到这个地址"), 404)
        }
    }

    private fun jsonBody(input: InputStream, headers: Map<String, String>): JSONObject? {
        val raw = readBody(input, headers, 2_000_000).toString(Charsets.UTF_8)
        return try { JSONObject(raw) } catch (_: Exception) { null }
    }

    private fun readBody(input: InputStream, headers: Map<String, String>, limit: Int): ByteArray {
        val chunked = (headers["transfer-encoding"] ?: "").contains("chunked")
        val declared = headers["content-length"]?.toIntOrNull() ?: limit
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        if (chunked) {
            val chunks = ChunkedInput(input)
            while (total < limit) {
                val n = chunks.read(buffer, 0, minOf(buffer.size, limit - total))
                if (n < 0) break
                out.write(buffer, 0, n)
                total += n
            }
        } else {
            var remaining = minOf(declared, limit)
            while (remaining > 0) {
                val n = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (n < 0) break
                out.write(buffer, 0, n)
                remaining -= n
                total += n
            }
        }
        return out.toByteArray()
    }

    private fun copyStream(input: InputStream, output: OutputStream, headers: Map<String, String>): Long {
        val chunked = (headers["transfer-encoding"] ?: "").contains("chunked")
        val length = headers["content-length"]?.toLongOrNull() ?: -1
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        if (chunked) {
            val chunks = ChunkedInput(input)
            while (true) {
                val n = chunks.read(buffer)
                if (n < 0) break
                output.write(buffer, 0, n)
                total += n
            }
        } else {
            while (length < 0 || total < length) {
                val n = input.read(buffer, 0, if (length >= 0) minOf(buffer.size, (length - total).toInt()) else buffer.size)
                if (n < 0) break
                output.write(buffer, 0, n)
                total += n
            }
        }
        return total
    }

    private fun formValue(body: String, key: String): String {
        body.split('&').forEach { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size == 2 && parts[0] == key) {
                return URLDecoder.decode(parts[1], "UTF-8")
            }
        }
        return ""
    }
}

/// 最小 chunked 解码，兼容 curl 等非浏览器客户端。
class ChunkedInput(private val source: InputStream) : InputStream() {
    private var remaining = 0

    private fun nextChunk(): Boolean {
        val line = ByteArrayOutputStream()
        while (true) {
            val b = source.read()
            if (b < 0) return false
            if (b == '\r'.code) continue
            if (b == '\n'.code) break
            line.write(b)
        }
        val hex = line.toString(Charsets.US_ASCII).substringBefore(';').trim()
        remaining = hex.toIntOrNull(16) ?: return false
        return remaining > 0
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (remaining == 0 && !nextChunk()) return -1
        val n = source.read(b, off, minOf(len, remaining))
        if (n < 0) return -1
        remaining -= n
        if (remaining == 0) {
            source.read()
            source.read()
        }
        return n
    }
}
