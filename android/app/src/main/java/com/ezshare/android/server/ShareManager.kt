package com.ezshare.android.server

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

data class Peer(val name: String, val host: String, val port: Int) {
    val url: String get() = "http://$host:$port"
}

data class UiState(
    val running: Boolean = false,
    val port: Int = 8080,
    val shareName: String = "EZShare",
    val settings: ShareSettings = ShareSettings(),
    val addresses: List<String> = emptyList(),
    val peers: List<Peer> = emptyList(),
    val fileEntries: List<ShareEntry> = emptyList(),
    val snippets: List<TextSnippet> = emptyList()
)

/// 全局共享状态与服务编排。UI 只观察 state，所有实际工作在此完成。
object ShareManager {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

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
    private var currentDir = ""

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
    }

    val settings: ShareSettings get() = config.settings

    val currentPath: String get() = currentDir

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
        currentDir = ""
        _state.value = _state.value.copy(shareName = root.displayName)
        refresh()
    }

    fun start(): Boolean {
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
            _state.value = _state.value.copy(running = false)
            return false
        }
        _state.value = _state.value.copy(running = true, port = actual, settings = s)
        discovery.register(actual, "${root.displayName}-$actual")
        refresh()
        return true
    }

    fun stop() {
        discovery.unregister()
        server?.stop()
        server = null
        _state.value = _state.value.copy(running = false, port = settings.port)
    }

    fun shutdown() {
        discovery.unregister()
        server?.shutdown()
        server = null
    }

    fun refresh() {
        val addresses = mutableListOf<String>()
        try {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                .forEach { addresses.add(it.hostAddress ?: "") }
        } catch (_: Exception) {}
        _state.value = _state.value.copy(
            addresses = addresses.distinct(),
            fileEntries = root.list(currentDir),
            snippets = texts.list(),
            settings = config.settings,
            port = _state.value.port
        )
    }

    fun browse(path: String) {
        currentDir = if (path == "..") {
            currentDir.substringBeforeLast('/', "")
        } else path
        refresh()
    }

    fun addText(content: String): Boolean {
        val item = texts.add(content) ?: return false
        refresh()
        return item.id.isNotEmpty()
    }

    fun deleteText(id: String) {
        texts.delete(id)
        refresh()
    }

    fun addFiles(uris: List<Uri>): Int {
        var count = 0
        for (uri in uris) {
            val display = androidx.documentfile.provider.DocumentFile.fromSingleUri(app, uri)?.name ?: "file"
            val output = root.createFile(currentDir, display) ?: continue
            try {
                val input = app.contentResolver.openInputStream(uri) ?: continue
                val out = app.contentResolver.openOutputStream(output.uri) ?: continue
                input.use { src -> out.use { dst -> src.copyTo(dst, 64 * 1024) } }
                count++
            } catch (_: Exception) {}
        }
        refresh()
        return count
    }

    fun addFolder(uri: Uri): Int {
        val display = androidx.documentfile.provider.DocumentFile.fromSingleUri(app, uri)?.name ?: "folder"
        val target = root.createDir(currentDir, display) ?: return 0
        return copyTree(uri, target.uri, display)
    }

    private fun copyTree(source: Uri, destination: android.net.Uri, name: String): Int {
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
                    val dir = destination.let { d ->
                        androidx.documentfile.provider.DocumentFile.fromTreeUri(app, d)?.createDirectory(childName)?.uri
                    } ?: continue
                    count += copyTree(childUri, dir, childName)
                } else {
                    val file = androidx.documentfile.provider.DocumentFile.fromTreeUri(app, destination)
                        ?.createFile(MimeTypes.of(childName), childName) ?: continue
                    try {
                        val input = app.contentResolver.openInputStream(childUri) ?: continue
                        val out = app.contentResolver.openOutputStream(file.uri) ?: continue
                        input.use { src -> out.use { dst -> src.copyTo(dst, 64 * 1024) } }
                        count++
                    } catch (_: Exception) {}
                }
            }
        }
        refresh()
        return count
    }

    fun openSharedFile(entry: ShareEntry) {
        val file = root.resolve(entry.relativePath) ?: return
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            data = file.uri
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            app.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }
}
