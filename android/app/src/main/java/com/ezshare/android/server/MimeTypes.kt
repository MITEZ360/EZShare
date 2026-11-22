package com.ezshare.android.server

object MimeTypes {
    private val map = mapOf(
        "html" to "text/html; charset=utf-8", "htm" to "text/html; charset=utf-8",
        "txt" to "text/plain; charset=utf-8", "md" to "text/markdown; charset=utf-8",
        "json" to "application/json; charset=utf-8", "xml" to "application/xml; charset=utf-8",
        "csv" to "text/csv; charset=utf-8", "js" to "text/javascript; charset=utf-8",
        "css" to "text/css; charset=utf-8", "pdf" to "application/pdf",
        "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
        "gif" to "image/gif", "webp" to "image/webp", "svg" to "image/svg+xml",
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "wav" to "audio/wav",
        "mp4" to "video/mp4", "mov" to "video/quicktime", "mkv" to "video/x-matroska",
        "zip" to "application/zip", "rar" to "application/x-rar-compressed",
        "7z" to "application/x-7z-compressed", "tar" to "application/x-tar",
        "gz" to "application/gzip", "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "apk" to "application/vnd.android.package-archive"
    )

    fun of(name: String): String = map[name.substringAfterLast('.').lowercase()]
        ?: "application/octet-stream"

    fun label(name: String, isDir: Boolean): String = when {
        isDir -> "folder"
        name.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "heic") -> "image"
        name.substringAfterLast('.').lowercase() in setOf("mp4", "mov", "mkv", "avi") -> "video"
        name.substringAfterLast('.').lowercase() in setOf("mp3", "m4a", "wav", "flac") -> "audio"
        name.substringAfterLast('.').lowercase() in setOf("zip", "rar", "7z", "tar", "gz") -> "archive"
        else -> "file"
    }
}
