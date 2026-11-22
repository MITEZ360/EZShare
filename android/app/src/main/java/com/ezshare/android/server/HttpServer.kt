package com.ezshare.android.server

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/// 轻量 HTTP/1.1 服务：纯 Kotlin + Java 网络库，无第三方服务端框架。
class HttpServer(private val router: Router) {
    private val pool = Executors.newCachedThreadPool()
    private var socket: ServerSocket? = null
    private val runningFlag = AtomicBoolean(false)

    val running: Boolean get() = runningFlag.get()
    var port: Int = 0
        private set

    var onState: ((String) -> Unit)? = null

    fun start(preferred: Int): Int {
        stop()
        for (candidate in preferred..(preferred + 12)) {
            if (candidate > 65535) break
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress("0.0.0.0", candidate))
                socket = server
                port = candidate
                runningFlag.set(true)
                onState?.invoke("running")
                acceptLoop(server)
                return port
            } catch (_: Exception) {
                // 端口被占用，顺延
            }
        }
        onState?.invoke("failed")
        return 0
    }

    fun stop() {
        runningFlag.set(false)
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        onState?.invoke("idle")
    }

    fun shutdown() {
        stop()
        pool.shutdownNow()
    }

    private fun acceptLoop(server: ServerSocket) {
        pool.execute {
            while (runningFlag.get()) {
                try {
                    val client = server.accept()
                    pool.execute { serve(client) }
                } catch (_: Exception) {
                    if (!runningFlag.get()) break
                }
            }
        }
    }

    private fun serve(client: Socket) {
        try {
            client.soTimeout = 300_000
            val input = BufferedInputStream(client.getInputStream(), 16 * 1024)
            val head = readHead(input) ?: return client.close()
            val lines = head.split("\r\n")
            val first = lines.firstOrNull()?.split(' ') ?: return client.close()
            if (first.size < 2) return client.close()
            val method = first[0].uppercase()
            val target = first[1]

            val headers = mutableMapOf<String, String>()
            for (i in 1 until lines.size) {
                val line = lines[i]
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).lowercase()] = line.substring(colon + 1).trim()
                }
            }

            val qIndex = target.indexOf('?')
            val path = if (qIndex >= 0) target.substring(0, qIndex) else target
            val query = mutableMapOf<String, String>()
            if (qIndex >= 0 && qIndex < target.length - 1) {
                target.substring(qIndex + 1).split('&').forEach { pair ->
                    val eq = pair.indexOf('=')
                    if (eq > 0) {
                        val key = URLDecoder.decode(pair.substring(0, eq), "UTF-8")
                        val value = URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
                        query[key] = value
                    }
                }
            }

            val response = router.handle(Req(method, path, query, headers), input)
            val output = client.getOutputStream()
            writeResponse(output, response)
            output.flush()
        } catch (_: Exception) {
            // 客户端提前断开属正常情况
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun readHead(input: BufferedInputStream): String? {
        val buffer = ByteArrayOutputStream()
        val one = ByteArray(1)
        while (buffer.size() < 64 * 1024) {
            val n = input.read(one)
            if (n < 0) return null
            buffer.write(one[0].toInt())
            val size = buffer.size()
            if (size >= 4) {
                val bytes = buffer.toByteArray()
                if (bytes[size - 4] == '\r'.code.toByte() &&
                    bytes[size - 3] == '\n'.code.toByte() &&
                    bytes[size - 2] == '\r'.code.toByte() &&
                    bytes[size - 1] == '\n'.code.toByte()
                ) {
                    return String(bytes, 0, size - 4, Charsets.ISO_8859_1)
                }
            }
        }
        return null
    }

    private fun writeResponse(out: OutputStream, res: Res) {
        val reason = when (res.status) {
            200 -> "OK"; 206 -> "Partial Content"; 302 -> "Found"
            400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"
            404 -> "Not Found"; 431 -> "Request Header Fields Too Large"
            else -> "Error"
        }
        val head = StringBuilder()
        head.append("HTTP/1.1 ${res.status} $reason\r\n")
        head.append("Date: ${java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC))}\r\n")
        head.append("Server: EZShare/1.0\r\n")
        head.append("Connection: close\r\n")
        head.append("X-Content-Type-Options: nosniff\r\n")
        head.append("Content-Type: ${res.contentType}\r\n")
        for ((key, value) in res.headers) {
            if (key.lowercase() != "content-length") head.append("$key: $value\r\n")
        }
        head.append("Content-Length: ${res.streamLength.takeIf { it >= 0 } ?: res.body.size}\r\n\r\n")
        out.write(head.toString().toByteArray(Charsets.ISO_8859_1))

        if (res.body.isNotEmpty()) out.write(res.body)

        val stream = res.stream
        if (stream != null && res.streamLength > 0) {
            var toSkip = res.offset
            val skipBuffer = ByteArray(16 * 1024)
            while (toSkip > 0) {
                val n = stream.read(skipBuffer, 0, minOf(skipBuffer.size, toSkip.toInt()))
                if (n < 0) break
                toSkip -= n
            }
            val buffer = ByteArray(64 * 1024)
            var remaining = res.streamLength
            while (remaining > 0) {
                val n = stream.read(buffer, 0, minOf(buffer.size, remaining.toInt()))
                if (n < 0) break
                out.write(buffer, 0, n)
                remaining -= n
            }
        }
        try { stream?.close() } catch (_: Exception) {}
    }
}
