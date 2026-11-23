package com.ezshare.android.server

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/// 共享条目。path 为相对共享根目录的安全路径。
data class ShareEntry(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
    val modifiedLabel: String = ""
)

/// 共享根目录访问封装。支持两种后端：
/// - file:/绝对路径：app 专属外部存储，零权限、默认使用；
/// - content://...：用户通过系统选择器授权的 SAF 目录树。
class ShareRoot(private val context: Context, spec: String) {
    private val fileRoot: File?
    private val docRoot: DocumentFile?

    init {
        if (spec.startsWith(FILE_PREFIX)) {
            val dir = File(spec.removePrefix(FILE_PREFIX))
            if (!dir.exists()) runCatching { dir.mkdirs() }
            fileRoot = if (dir.isDirectory) dir else null
            docRoot = null
        } else if (spec.isNotEmpty()) {
            fileRoot = null
            docRoot = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(spec)) }.getOrNull()
        } else {
            fileRoot = null
            docRoot = null
        }
    }

    val isFileMode: Boolean get() = fileRoot != null

    val isValid: Boolean
        get() = if (fileRoot != null) fileRoot!!.canRead() && fileRoot!!.canWrite()
                else docRoot != null && docRoot!!.canRead()

    val displayName: String
        get() = (if (fileRoot != null) fileRoot!!.name else docRoot?.name) ?: "EZShare"

    /// 根目录的完整可读路径（用于设置页展示）。
    val displayPath: String
        get() = if (fileRoot != null) fileRoot!!.absolutePath else (docRoot?.uri?.toString() ?: "")

    fun list(dir: String): List<ShareEntry> {
        val safe = safeParts(dir) ?: return emptyList()
        val result = mutableListOf<ShareEntry>()
        if (fileRoot != null) {
            val parent = resolveFile(safe) ?: return emptyList()
            val children = parent.listFiles() ?: return emptyList()
            for (child in children) {
                val name = child.name
                val rel = join(safe, name)
                result += ShareEntry(name, rel, child.isDirectory,
                    if (child.isFile) child.length() else 0L, child.lastModified())
            }
        } else {
            val parent = resolveDoc(safe) ?: return emptyList()
            for (child in parent.listFiles()) {
                val name = child.name ?: continue
                val rel = join(safe, name)
                result += ShareEntry(name, rel, child.isDirectory, child.length(), child.lastModified())
            }
        }
        return result.sortedWith(compareBy<ShareEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun node(path: String): ShareEntry? {
        val parts = safeParts(path) ?: return null
        if (parts.isEmpty()) return null
        if (fileRoot != null) {
            val f = resolveFile(parts) ?: return null
            return ShareEntry(f.name, path, f.isDirectory,
                if (f.isFile) f.length() else 0L, f.lastModified())
        }
        val d = resolveDoc(parts) ?: return null
        return ShareEntry(d.name ?: parts.last(), path, d.isDirectory, d.length(), d.lastModified())
    }

    fun openInput(path: String): InputStream? {
        val parts = safeParts(path) ?: return null
        if (fileRoot != null) {
            val f = resolveFile(parts) ?: return null
            return if (f.isFile) runCatching { FileInputStream(f) }.getOrNull() else null
        }
        val d = resolveDoc(parts) ?: return null
        if (!d.isFile) return null
        return runCatching { context.contentResolver.openInputStream(d.uri) }.getOrNull()
    }

    fun openOutput(path: String): OutputStream? {
        val parts = safeParts(path) ?: return null
        if (fileRoot != null) {
            val f = resolveFile(parts) ?: return null
            return if (f.isFile) runCatching { FileOutputStream(f, false) }.getOrNull() else null
        }
        val d = resolveDoc(parts) ?: return null
        if (!d.isFile) return null
        return runCatching { context.contentResolver.openOutputStream(d.uri, "wt") }.getOrNull()
    }

    /// 供系统“打开”使用的 URI。File 模式经 FileProvider 共享，避免 file:// 暴露异常。
    fun viewUri(path: String): Uri? {
        val parts = safeParts(path) ?: return null
        if (fileRoot != null) {
            val f = resolveFile(parts) ?: return null
            return runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            }.getOrNull()
        }
        return resolveDoc(parts)?.uri
    }

    fun createFile(dir: String, name: String): ShareEntry? {
        val safe = safeParts(dir) ?: return null
        val finalName = uniqueName(safe, sanitize(name)) ?: return null
        val rel = join(safe, finalName)
        if (fileRoot != null) {
            val parent = resolveFile(safe) ?: return null
            val f = File(parent, finalName)
            if (!runCatching { f.createNewFile() }.getOrDefault(false)) return null
            return ShareEntry(finalName, rel, false, 0L, f.lastModified())
        }
        val parent = resolveDoc(safe) ?: return null
        val created = runCatching {
            parent.createFile(MimeTypes.of(finalName), finalName)
        }.getOrNull() ?: return null
        return ShareEntry(finalName, rel, false, 0L, created.lastModified())
    }

    fun createDir(dir: String, name: String): ShareEntry? {
        val safe = safeParts(dir) ?: return null
        val finalName = uniqueName(safe, sanitize(name)) ?: return null
        val rel = join(safe, finalName)
        if (fileRoot != null) {
            val parent = resolveFile(safe) ?: return null
            val f = File(parent, finalName)
            if (!runCatching { f.mkdir() }.getOrDefault(false)) return null
            return ShareEntry(finalName, rel, true, 0L, f.lastModified())
        }
        val parent = resolveDoc(safe) ?: return null
        val created = runCatching { parent.createDirectory(finalName) }.getOrNull() ?: return null
        return ShareEntry(finalName, rel, true, 0L, created.lastModified())
    }

    fun delete(path: String): Boolean {
        val parts = safeParts(path) ?: return false
        if (parts.isEmpty()) return false
        if (fileRoot != null) {
            val f = resolveFile(parts) ?: return false
            return runCatching { if (f.isDirectory) f.deleteRecursively() else f.delete() }.getOrDefault(false)
        }
        val d = resolveDoc(parts) ?: return false
        return runCatching { d.delete() }.getOrDefault(false)
    }

    fun uniqueName(dir: String, name: String): String? {
        val parts = safeParts(dir) ?: return null
        val existing = list(dir).map { it.name }.toSet()
        if (name !in existing) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (candidate !in existing) return candidate
            i++
        }
    }

    /// 把用户选择的目录树递归导入到当前目录（支持 File 与 SAF 两种后端）。
    fun importTree(treeUri: Uri, dir: String): Int {
        val source = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        var count = 0
        fun walk(node: DocumentFile, targetDir: String) {
            for (child in node.listFiles()) {
                val name = child.name ?: continue
                when {
                    child.isDirectory -> {
                        val created = createDir(targetDir, name) ?: continue
                        walk(child, created.relativePath)
                    }
                    child.isFile -> {
                        val created = createFile(targetDir, name) ?: continue
                        val input = runCatching { context.contentResolver.openInputStream(child.uri) }.getOrNull()
                        val output = openOutput(created.relativePath)
                        if (input != null && output != null) {
                            input.use { src -> output.use { dst -> src.copyTo(dst, 64 * 1024) } }
                            count++
                        }
                    }
                }
            }
        }
        walk(source, dir)
        return count
    }

    private fun resolveFile(parts: List<String>): File? {
        var node = fileRoot ?: return null
        for (part in parts) {
            node = File(node, part)
            if (!node.isDirectory) return null
        }
        return node
    }

    private fun resolveDoc(parts: List<String>): DocumentFile? {
        var node = docRoot ?: return null
        for (part in parts) {
            node = node.findFile(part) ?: return null
        }
        return node
    }

    private fun join(dir: List<String>, name: String): String =
        if (dir.isEmpty()) name else dir.joinToString("/") + "/" + name

    private fun sanitize(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\').trim().ifEmpty { "file" }

    private fun safeParts(path: String): List<String>? {
        val parts = path.split('/').filter { it.isNotEmpty() }
        if (parts.any { it == "." || it == ".." || it.contains('\\') }) return null
        return parts
    }

    companion object {
        const val FILE_PREFIX = "file:"

        /// 默认共享目录：app 专属外部存储的 Download/EZShare。
        /// 无需任何权限，应用安装即可读写，且随应用持久化。
        fun defaultSpec(context: Context): String {
            val downloads = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            val dir = File(downloads, "EZShare")
            if (!dir.exists()) runCatching { dir.mkdirs() }
            return FILE_PREFIX + dir.absolutePath
        }
    }
}
