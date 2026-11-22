package com.ezshare.android.server

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream

data class ShareEntry(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long
)

/// 共享目录（用户通过系统文件授权选择的目录树）的安全访问封装。
class ShareRoot(private val context: Context, uriString: String) {
    private val root: DocumentFile? = try {
        if (uriString.isEmpty()) null else DocumentFile.fromTreeUri(context, Uri.parse(uriString))
    } catch (_: Exception) {
        null
    }

    val isValid: Boolean get() = root != null && root.canRead()

    val displayName: String get() = root?.name ?: "EZShare"

    fun list(dir: String): List<ShareEntry> {
        val node = resolve(dir) ?: return emptyList()
        return node.listFiles()
            .filter { !it.name.isNullOrEmpty() }
            .map { file ->
                val name = file.name!!
                val relative = if (dir.isEmpty()) name else "$dir/$name"
                ShareEntry(name, relative, file.isDirectory, file.length(), file.lastModified())
            }
            .sortedWith(compareBy<ShareEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun resolve(path: String): DocumentFile? {
        val parts = safeParts(path) ?: return null
        var node = root ?: return null
        for (part in parts) {
            node = node.findFile(part) ?: return null
        }
        return node
    }

    fun openInput(path: String): InputStream? {
        val file = resolve(path) ?: return null
        if (!file.isFile) return null
        return try {
            context.contentResolver.openInputStream(file.uri)
        } catch (_: Exception) {
            null
        }
    }

    fun createFile(dir: String, name: String): DocumentFile? {
        val parent = resolve(dir) ?: return null
        val safeName = name.substringAfterLast('/').substringAfterLast('\\')
        if (safeName.isEmpty()) return null
        val finalName = uniqueName(dir, safeName)
        return try {
            parent.createFile(MimeTypes.of(finalName), finalName)
        } catch (_: Exception) {
            null
        }
    }

    fun createDir(dir: String, name: String): DocumentFile? {
        val parent = resolve(dir) ?: return null
        val safeName = name.substringAfterLast('/').substringAfterLast('\\')
        if (safeName.isEmpty()) return null
        val finalName = uniqueName(dir, safeName)
        return try {
            parent.createDirectory(finalName)
        } catch (_: Exception) {
            null
        }
    }

    fun delete(path: String): Boolean {
        val file = resolve(path) ?: return false
        if (safeParts(path).isNullOrEmpty()) return false
        return try {
            file.delete()
        } catch (_: Exception) {
            false
        }
    }

    fun uniqueName(dir: String, name: String): String {
        val parent = resolve(dir) ?: return name
        if (parent.findFile(name) == null) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (parent.findFile(candidate) == null) return candidate
            i++
        }
    }

    private fun safeParts(path: String): List<String>? {
        val parts = path.split('/').filter { it.isNotEmpty() }
        if (parts.any { it == "." || it == ".." || it.contains('\\') }) return null
        return parts
    }
}
