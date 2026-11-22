package com.ezshare.android.server

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ezshare.android.client.RemoteClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

enum class SessionMode { IDLE, LOCAL, REMOTE }

data class Peer(
    val name: String,
    val host: String,
    val port: Int,
    val requiresCode: Boolean = false
) {
    val url: String get() = "http://$host:$port"
}

data class UiState(
    val mode: SessionMode = SessionMode.IDLE,
    val running: Boolean = false,
    val peer: Peer? = null,
    val port: Int = 8080,
    val shareName: String = "EZShare",
    val settings: ShareSettings = ShareSettings(),
    val addresses: List<String> = emptyList(),
    val peers: List<Peer> = emptyList(),
    val fileEntries: List<ShareEntry> = emptyList(),
    val snippets: List<TextSnippet> = emptyList(),
    val currentPath: String = "",
    val allowUpload: Boolean = true,
    val allowDelete: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null
)

/// 全局状态与服务编排。Local 模式启动本机服务，Remote 模式加入其他设备的共享。
object ShareManager {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var app: Context
    lateinit var config: ConfigStore
        private set
    lateinit var texts: TextStore
        private set
    lateinit var web: WebUI
        private set
    lateinit var root: ShareRoot
        private set
    lateinit var router: Router
        private set
    lateinit var discovery: LanDiscovery
        private set

    private var server: HttpServer? = null
    private var remote: RemoteClient? = null

    val settings: ShareSettings get() = config.settings
    val currentPath: String get() = _state.value.currentPath

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        config = ConfigStore(app)
        texts = TextStore(app)
        web = WebUI(
            app.assets.open("web.css").bufferedReader().use { it.readText() },
            app.assets.open("web.js").bufferedReader().use { it.readText() }
        )
        root = ShareRoot(app, config.settings.treeUri)
        router = Router(app, config, root, texts, web)
        discovery = LanDiscovery(app)
        discovery.onPeers = { list -> _state.value = _state.value.copy(peers = list) }
        _state.value = _state.value.copy(
            port = config.settings.port,
            settings = config.settings,
            shareName = root.displayName
        )
        refresh()
        // App 启动即扫描，不需要先启动本机服务
        discovery.refresh()
    }

    fun consumeMessage() {
        if (_state.value.message != null) _state.value = _state.value.copy(message = null)
    }

    fun notify(message: String) {
        _state.value = _state.value.copy(message = message)
    }

    fun updateSettings(block: ShareSettings.() -> Unit) {
        val s = settings
        block(s)
        config.update(s)
        _state.value = _state.value.copy(settings = s, port = s.port)
    }

    fun regenerateCode(): String {
        val code = config.regenerateCode()
        refresh()
        return code
    }

    fun setTreeUri(uri: Uri) {
        app.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val s = settings
        s.treeUri = uri.toString()
        config.update(s)
        root = ShareRoot(app, s.treeUri)
        router = Router(app, config, root, texts, web)
        _state.value = _state.value.copy(currentPath = "", shareName = root.displayName)
        refresh()
    }

    fun startLocal(): Boolean {
        if (server?.running == true) return true
        if (!root.isValid) return false
        val s = settings
        val http = HttpServer(router)
        http.onState = { status ->
            _state.value = _state.value.copy(
                running = status == "running",
                port = if (status == "running") http.port else s.port
            )
        }
        val actual = http.start(s.port)
        server = http
        if (actual == 0) {
            _state.value = _state.value.copy(running = false, message = "端口启动失败")
            return false
        }
        discovery.register(actual, "${root.displayName}-$actual", s.requireCode)
        _state.value = _state.value.copy(
            mode = SessionMode.LOCAL,
            running = true,
            port = actual,
            currentPath = "",
            peer = null,
            shareName = root.displayName,
            allowUpload = s.allowUpload,
            allowDelete = s.allowDelete
        )
        refresh()
        return true
    }

    fun stopLocal() {
        discovery.unregister()
        server?.stop()
        server = null
        _state.value = _state.value.copy(
            mode = SessionMode.IDLE,
            running = false,
            port = settings.port,
            fileEntries = emptyList(),
            snippets = emptyList()
        )
    }

    /// 加入其他设备的共享；code 仅在对方开启访问码时使用。
    suspend fun joinRemote(peer: Peer, code: String): Boolean {
        stopLocal()
        remote?.clearSession()
        _state.value = _state.value.copy(busy = true, message = null)
        val client = RemoteClient(peer.url)
        val ok = withContext(Dispatchers.IO) {
            if (peer.requiresCode && !client.login(code)) return@withContext false
            client.info() != null
        }
        if (!ok) {
            _state.value = _state.value.copy(busy = false, message = "无法加入：访问码错误或服务不可达")
            return false
        }
        remote = client
        _state.value = _state.value.copy(
            mode = SessionMode.REMOTE,
            peer = peer,
            busy = false,
            running = false,
            currentPath = "",
            shareName = peer.name,
            message = "已加入 ${peer.name}"
        )
        refresh()
        return true
    }

    fun leaveRemote() {
        remote?.clearSession()
        remote = null
        _state.value = _state.value.copy(
            mode = SessionMode.IDLE,
            peer = null,
            busy = false,
            shareName = root.displayName,
            fileEntries = emptyList(),
            snippets = emptyList(),
            message = "已断开共享"
        )
    }

    fun refresh() {
        val addresses = mutableListOf<String>()
        try {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                .forEach { addresses.add(it.hostAddress ?: "") }
        } catch (_: Exception) {}
        when (_state.value.mode) {
            SessionMode.REMOTE -> {
                _state.value = _state.value.copy(addresses = addresses.distinct(), settings = config.settings)
                refreshRemote()
            }
            SessionMode.LOCAL -> {
                _state.value = _state.value.copy(
                    addresses = addresses.distinct(),
                    fileEntries = root.list(_state.value.currentPath),
                    snippets = texts.list(),
                    settings = config.settings,
                    allowUpload = config.settings.allowUpload,
                    allowDelete = config.settings.allowDelete
                )
            }
            SessionMode.IDLE -> {
                _state.value = _state.value.copy(
                    addresses = addresses.distinct(),
                    settings = config.settings,
                    fileEntries = emptyList(),
                    snippets = emptyList()
                )
            }
        }
    }

    private fun refreshRemote() {
        val client = remote ?: return
        val path = _state.value.currentPath
        scope.launch {
            _state.value = _state.value.copy(busy = true)
            val files = client.files(path)
            val snippets = client.texts()
            _state.value = _state.value.copy(
                busy = false,
                fileEntries = files?.entries?.map { entry ->
                    val (time, label) = parseIso(entry.modified)
                    ShareEntry(entry.name, entry.path, entry.isDirectory, entry.size, time, label)
                } ?: emptyList(),
                snippets = snippets.map {
                    val (time, _) = parseIso(it.createdAt)
                    TextSnippet(it.id, it.content, time)
                },
                allowUpload = files?.allowUpload ?: _state.value.allowUpload,
                allowDelete = files?.allowDelete ?: _state.value.allowDelete
            )
        }
    }

    fun browse(path: String) {
        val next = if (path == "..") {
            _state.value.currentPath.substringBeforeLast('/', "")
        } else path
        _state.value = _state.value.copy(currentPath = next)
        refresh()
    }

    suspend fun addText(content: String): Boolean {
        if (content.isBlank()) return false
        val ok = when (_state.value.mode) {
            SessionMode.REMOTE -> remote?.addText(content) == true
            else -> texts.add(content) != null
        }
        if (ok) refresh()
        return ok
    }

    suspend fun deleteText(id: String) {
        when (_state.value.mode) {
            SessionMode.REMOTE -> remote?.deleteText(id)
            else -> texts.delete(id)
        }
        refresh()
    }

    suspend fun mkdir(name: String): Boolean {
        if (name.isBlank()) return false
        val ok = when (_state.value.mode) {
            SessionMode.REMOTE -> remote?.mkdir(_state.value.currentPath, name) == true
            else -> root.createDir(_state.value.currentPath, name) != null
        }
        if (ok) refresh() else notify("创建失败")
        return ok
    }

    suspend fun deleteEntry(entry: ShareEntry): Boolean {
        val ok = when (_state.value.mode) {
            SessionMode.REMOTE -> remote?.delete(entry.relativePath) == true
            else -> root.delete(entry.relativePath)
        }
        if (ok) refresh() else notify("删除失败")
        return ok
    }

    suspend fun addFiles(uris: List<Uri>): Int {
        var count = 0
        for (uri in uris) {
            val display = DocumentFile.fromSingleUri(app, uri)?.name ?: "file"
            when (_state.value.mode) {
                SessionMode.REMOTE -> {
                    val bytes = try {
                        app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } catch (_: Exception) { null }
                    if (bytes != null && remote?.upload(_state.value.currentPath, display, bytes) == true) count++
                }
                else -> {
                    val output = root.createFile(_state.value.currentPath, display) ?: continue
                    try {
                        val input = app.contentResolver.openInputStream(uri) ?: continue
                        val out = app.contentResolver.openOutputStream(output.uri) ?: continue
                        input.use { src -> out.use { dst -> src.copyTo(dst, 64 * 1024) } }
                        count++
                    } catch (_: Exception) {}
                }
            }
        }
        refresh()
        return count
    }

    fun addFolder(uri: Uri): Int {
        if (_state.value.mode == SessionMode.REMOTE) {
            notify("加入共享时暂不支持整目录上传，请逐个选择文件")
            return 0
        }
        val display = DocumentFile.fromSingleUri(app, uri)?.name ?: "folder"
        val target = root.createDir(_state.value.currentPath, display) ?: return 0
        val count = copyTree(uri, target.uri)
        refresh()
        return count
    }

    private fun copyTree(source: Uri, destination: Uri): Int {
        var count = 0
        val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
            source, android.provider.DocumentsContract.getDocumentId(source)
        )
        val cursor = app.contentResolver.query(children, null, null, null, null) ?: return 0
        cursor.use {
            while (it.moveToNext()) {
                val idIdx = it.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = it.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = it.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                if (idIdx < 0 || nameIdx < 0 || mimeIdx < 0) continue
                val childId = it.getString(idIdx)
                val childName = it.getString(nameIdx) ?: continue
                val mime = it.getString(mimeIdx) ?: continue
                val childUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(source, childId)
                if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                    val dir = DocumentFile.fromTreeUri(app, destination)?.createDirectory(childName)?.uri ?: continue
                    count += copyTree(childUri, dir)
                } else {
                    val file = DocumentFile.fromTreeUri(app, destination)?.createFile(MimeTypes.of(childName), childName) ?: continue
                    try {
                        val input = app.contentResolver.openInputStream(childUri) ?: continue
                        val out = app.contentResolver.openOutputStream(file.uri) ?: continue
                        input.use { src -> out.use { dst -> src.copyTo(dst, 64 * 1024) } }
                        count++
                    } catch (_: Exception) {}
                }
            }
        }
        return count
    }

    fun openEntry(entry: ShareEntry): String? {
        return when (_state.value.mode) {
            SessionMode.REMOTE -> if (entry.isDirectory) null else remote?.downloadUrl(entry.relativePath)
            else -> {
                val file = root.resolve(entry.relativePath) ?: return null
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    data = file.uri
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    app.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {}
                null
            }
        }
    }

    fun shutdown() {
        discovery.release()
        server?.shutdown()
        server = null
        remote?.clearSession()
        remote = null
    }

    private fun parseIso(value: String): Pair<Long, String> {
        if (value.isEmpty()) return 0L to ""
        val time = try {
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            formatter.parse(value)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
        return time to value
    }
}
