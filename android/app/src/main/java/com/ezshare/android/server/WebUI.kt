package com.ezshare.android.server

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/// 复刻 Mac 端网页结构，直接复用同一份内嵌 CSS/JS（assets/web.css、web.js）。
class WebUI(private val css: String, private val js: String) {

    fun shell(title: String, bodyAttributes: String, content: String): String = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="color-scheme" content="light dark">
<title>${esc(title)}</title>
<link rel="icon" type="image/png" href="/brand.png">
<link rel="apple-touch-icon" href="/brand.png">
<style>$css</style>
</head>
<body $bodyAttributes>
$content
<div id="drop"><div class="zone"><h2>松开以上传</h2><p>文件会保存到当前目录</p></div></div>
<div id="modal"></div>
<div id="toast"></div>
<div id="dock"><div id="dock-list"></div></div>
<script>$js</script>
</body>
</html>
"""

    private fun topbar(shareName: String, active: String, baseUrl: String): String {
        // 共享名与产品名相同时不再重复显示，避免出现两个 "EZShare"
        val sub = if (shareName.isEmpty() || shareName == "EZShare") ""
        else """<span class="sub">${esc(shareName)}</span>"""
        return """
<header class="topbar"><div class="topbar-inner">
<div class="brand">
<span class="mark">$brandMark</span>
<span class="title">EZShare</span>
$sub
</div>
<nav class="tabs">
<a class="${if (active == "files") "tab active" else "tab"}" href="/">文件</a>
<a class="${if (active == "text") "tab active" else "tab"}" href="/text">文本</a>
</nav>
<span class="spacer"></span>
<button class="btn ghost icon act" data-copy="${esc(baseUrl)}" title="复制共享地址">$copyIcon</button>
<span class="chip"><span class="dot"></span>局域网共享中</span>
</div></header>
"""
    }

    private val footer = """<footer><span>EZShare</span><span class="dot"></span><span>仅同一局域网内可访问</span><span class="dot"></span><span>本地直连 · 不经云端</span></footer>"""

    fun listing(relativePath: String, entries: List<ShareEntry>, settings: ShareSettings, shareName: String, baseUrl: String): String {
        val rows = entries.joinToString("\n") { row(it, relativePath, settings) }
        val table = if (entries.isEmpty()) {
            """<div class="empty">$emptyIcon<h3>这个目录还是空的</h3><p>${if (settings.allowUpload) "拖入文件，或点击右上角上传" else "向共享目录放入文件后会显示在这里"}</p></div>"""
        } else {
            """<table><thead><tr><th>名称</th><th class="size">大小</th><th class="time">修改时间</th><th></th></tr></thead><tbody>$rows</tbody></table>"""
        }
        val toolbarButtons = if (settings.allowUpload) """
<button class="btn" id="btn-mkdir">${plusIcon}新建文件夹</button>
<button class="btn primary" id="btn-upload">${uploadIcon}上传文件</button>
<input type="file" id="picker" multiple hidden>
""" else ""
        val countText = if (entries.isEmpty()) "空目录" else "${entries.count()} 个项目"

        val content = """
${topbar(shareName, "files", baseUrl)}
<main>
<nav class="crumbs">${breadcrumbs(relativePath)}</nav>
<div class="panel">
<div class="toolbar">
<label class="search">$searchIcon<input id="filter" type="search" placeholder="在当前目录中筛选…" autocomplete="off"></label>
<span class="count" id="count">$countText</span>
$toolbarButtons
</div>
$table
</div>
</main>
$footer
"""
        val attrs = "data-dir=\"${esc(relativePath)}\" data-upload=\"${if (settings.allowUpload) "1" else "0"}\" data-delete=\"${if (settings.allowDelete) "1" else "0"}\""
        return shell("$shareName · EZShare", attrs, content)
    }

    private fun breadcrumbs(path: String): String {
        if (path.isEmpty()) return """<span class="current">根目录</span>"""
        val parts = mutableListOf("""<a href="/">根目录</a>""")
        val accumulated = mutableListOf<String>()
        path.split('/').forEachIndexed { index, part ->
            accumulated.add(part)
            val target = accumulated.joinToString("/")
            if (index == path.split('/').size - 1) {
                parts.add("""<span class="sep">/</span><span class="current">${esc(part)}</span>""")
            } else {
                parts.add("""<span class="sep">/</span><a href="/?path=${enc(target)}">${esc(part)}</a>""")
            }
        }
        return parts.joinToString("")
    }

    private fun row(entry: ShareEntry, dir: String, settings: ShareSettings): String {
        val child = if (dir.isEmpty()) entry.name else "$dir/${entry.name}"
        val encoded = enc(child)
        val href = if (entry.isDirectory) "/?path=$encoded" else "/download?path=$encoded"
        var actions = ""
        if (!entry.isDirectory) actions += """<a class="btn ghost icon act" href="/download?path=$encoded" title="下载">$downloadIcon</a>"""
        if (settings.allowDelete) {
            actions += """<button class="btn ghost icon act" data-del="$encoded" data-isdir="${if (entry.isDirectory) "1" else "0"}" data-name-text="${esc(entry.name)}" title="删除">$trashIcon</button>"""
        }
        val size = if (entry.isDirectory) "—" else sizeText(entry.size)
        return """<tr data-name="${esc(entry.name)}"><td class="name"><a class="rowlink" href="$href"><span class="icon ${MimeTypes.label(entry.name, entry.isDirectory)}">${fileIcon}</span><span class="fname">${esc(entry.name)}</span></a></td><td class="size">$size</td><td class="time">${dateText(entry.modified)}</td><td class="actions">$actions</td></tr>"""
    }

    fun textPage(snippets: List<TextSnippet>, settings: ShareSettings, shareName: String, baseUrl: String): String {
        val composer = if (settings.allowUpload) """
<div class="panel composer">
<textarea id="text-input" placeholder="粘贴或输入要共享的文本，局域网内任何设备都能查看、复制…（⌘+Enter 发送）"></textarea>
<div class="composer-foot">
<span class="count" id="text-chars">0 字符</span>
<span class="spacer"></span>
<button class="btn primary" id="btn-text-send">${uploadIcon}发送到共享</button>
</div>
</div>
""" else ""
        val list = if (snippets.isEmpty()) {
            """<div class="empty">$emptyIcon<h3>还没有共享的文本</h3><p>${if (settings.allowUpload) "在上方粘贴文字后点击发送" else "共享端未开启写入，仅可查看与复制"}</p></div>"""
        } else {
            """<div class="snippets">${snippets.joinToString("\n") { snippetRow(it, settings.allowDelete) }}</div>"""
        }
        val content = """
${topbar(shareName, "text", baseUrl)}
<main>
$composer
<div class="panel" style="margin-top:14px">
<div class="toolbar">
<span class="count" id="text-count">${snippets.count()} 条文本</span>
<span class="spacer"></span>
<button class="btn ghost" id="btn-text-refresh">${refreshIcon}刷新</button>
</div>
$list
</div>
</main>
$footer
"""
        return shell("文本 · $shareName · EZShare", "data-text=\"1\"", content)
    }

    private fun snippetRow(snippet: TextSnippet, allowDelete: Boolean): String {
        val preview = if (snippet.content.length > 20_000) snippet.content.take(20_000) + "\n…（内容过长，仅显示前 2 万字符，下载可获取完整内容）" else snippet.content
        var actions = """<a class="btn ghost icon act" href="/api/text/download?id=${enc(snippet.id)}" title="下载为 txt">$downloadIcon</a>"""
        actions += """<button class="btn ghost icon act" data-textcopy="${snippet.id}" title="复制内容">$copyIcon</button>"""
        if (allowDelete) {
            actions += """<button class="btn ghost icon act" data-textdel="${snippet.id}" title="删除">$trashIcon</button>"""
        }
        return """<div class="snippet" data-id="${snippet.id}"><div class="s-head"><span>${dateText(snippet.createdAt)}</span><span>${sizeText(snippet.content.toByteArray().size.toLong())}</span><span class="spacer"></span>$actions</div><div class="s-body">${esc(preview)}</div></div>"""
    }

    fun login(error: String?, shareName: String): String {
        val err = if (error.isNullOrEmpty()) "" else """<p class="err">${esc(error)}</p>"""
        val content = """
<div class="login-wrap"><div class="login">
<div class="mark">$brandMark</div>
<h1>EZShare</h1><p>$shareName · 输入访问码后继续</p>
$err
<form method="post" action="/login">
<input type="password" name="code" autocomplete="off" placeholder="••••••" autofocus>
<button type="submit">进入共享</button>
</form>
</div></div>
"""
        return shell("访问码 · EZShare", "", content)
    }

    fun errorPage(status: Int, message: String): String {
        val content = """<div class="login-wrap"><div class="login"><h1>$status</h1><p>${esc(message)}</p><a class="btn primary" href="/">返回首页</a></div></div>"""
        return shell("$status · EZShare", "", content)
    }

    companion object {
        /// 品牌标直接用透明 PNG（/brand.png），与桌面图标同一份图形
        private val brandMark = """<img src="/brand.png" alt="" width="34" height="34">"""
        private val downloadIcon = """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4.5v10"/><path d="M8 11l4 3.5 4-3.5"/><path d="M5 18.5h14"/></svg>"""
        private val trashIcon = """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4.8 7h14.4"/><path d="M9.4 7V5.4a1.4 1.4 0 0 1 1.4-1.4h2.4a1.4 1.4 0 0 1 1.4 1.4V7"/><path d="M6.6 7l.8 11.2a1.8 1.8 0 0 0 1.8 1.6h5.6a1.8 1.8 0 0 0 1.8-1.6L17.4 7"/></svg>"""
        private val copyIcon = """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="11" height="11" rx="2.2"/><path d="M5.5 14.5A1.8 1.8 0 0 1 4 12.7V5.8A1.8 1.8 0 0 1 5.8 4h6.9a1.8 1.8 0 0 1 1.8 1.5"/></svg>"""
        private val refreshIcon = """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12a7 7 0 1 1-2-4.9"/><path d="M19 4.5V8h-3.5"/></svg>"""
        private val searchIcon = """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="6.2"/><path d="M16 16l3.4 3.4"/></svg>"""
        private val plusIcon = """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"><path d="M12 5.5v13M5.5 12h13"/></svg>"""
        private val uploadIcon = """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 16V5"/><path d="M7.8 9.2L12 5l4.2 4.2"/><path d="M5 18.5h14"/></svg>"""
        private val emptyIcon = """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"><path d="M3.5 8.5A2 2 0 0 1 5.5 6.5h3.2c.5 0 1 .2 1.3.5l.9.9c.3.3.8.5 1.3.5h6.3a2 2 0 0 1 2 2v6.2a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z"/></svg>"""
        private val fileIcon = """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M13.4 3.6H7a2 2 0 0 0-2 2v12.8a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V9.2z"/><path d="M13.4 3.6V9.2H19"/></svg>"""

        fun esc(text: String): String = text
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")

        fun enc(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

        private val sizeFormatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

        fun dateText(millis: Long): String = try {
            sizeFormatter.format(Date(millis))
        } catch (_: Exception) {
            ""
        }

        fun sizeText(bytes: Long): String = when {
            bytes <= 0 -> "0 B"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(Locale.US, bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(Locale.US, bytes / 1024.0 / 1024.0)
            else -> "%.1f GB".format(Locale.US, bytes / 1024.0 / 1024.0 / 1024.0)
        }
    }
}
