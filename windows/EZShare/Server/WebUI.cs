using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using EZShare.Core;

namespace EZShare.Server
{
    public static class WebUI
    {
        public const string BrandMark = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#fff\" stroke-width=\"1.9\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M12 15.5V4\"/><path d=\"M7.8 8.2L12 4l4.2 4.2\"/><path d=\"M4.6 14v4.2a1.9 1.9 0 0 0 1.9 1.9h11a1.9 1.9 0 0 0 1.9-1.9V14\"/></svg>";

        private const string FolderIcon = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M3 7.5A2 2 0 0 1 5 5.5h3.6c.5 0 1 .2 1.4.6l1 1c.4.4.9.6 1.4.6H19a2 2 0 0 1 2 2v7.8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z\"/></svg>";
        private const string ImageIcon = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><rect x=\"3.5\" y=\"4.5\" width=\"17\" height=\"15\" rx=\"2.6\"/><circle cx=\"8.8\" cy=\"9.8\" r=\"1.5\"/><path d=\"M4.6 17.2l4.1-4.1a1.6 1.6 0 0 1 2.3 0l2.8 2.8\"/><path d=\"M14.4 15.2l1.7-1.7a1.6 1.6 0 0 1 2.3 0l1.1 1.1\"/></svg>";
        private const string VideoIcon = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><rect x=\"3.5\" y=\"5.5\" width=\"17\" height=\"13\" rx=\"2.6\"/><path d=\"M10.4 9.4l4.6 2.6-4.6 2.6z\" fill=\"currentColor\" stroke=\"none\"/></svg>";
        private const string AudioIcon = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M9.5 17.5V7.4l7.5-1.4v9.6\"/><circle cx=\"7.2\" cy=\"17.8\" r=\"2.3\"/><circle cx=\"14.7\" cy=\"16.2\" r=\"2.3\"/></svg>";
        private const string ArchiveIcon = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><rect x=\"3.5\" y=\"4.5\" width=\"17\" height=\"15\" rx=\"2.6\"/><path d=\"M3.5 9.5h17\"/><path d=\"M10.4 13.4h3.2\"/></svg>";
        private const string CodeIcon = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M9.2 8.4L5.6 12l3.6 3.6\"/><path d=\"M14.8 8.4L18.4 12l-3.6 3.6\"/><path d=\"M13 6.6l-2 10.8\"/></svg>";
        private const string DocumentIcon = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M13.4 3.6H7a2 2 0 0 0-2 2v12.8a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V9.2z\"/><path d=\"M13.4 3.6V9.2H19\"/><path d=\"M8.6 13.2h6.8M8.6 16.2h4.2\"/></svg>";
        private const string FileIcon = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M13.4 3.6H7a2 2 0 0 0-2 2v12.8a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V9.2z\"/><path d=\"M13.4 3.6V9.2H19\"/></svg>";
        private const string DownloadIcon = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M12 4.5v10\"/><path d=\"M8 11l4 3.5 4-3.5\"/><path d=\"M5 18.5h14\"/></svg>";
        private const string TrashIcon = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M4.8 7h14.4\"/><path d=\"M9.4 7V5.4a1.4 1.4 0 0 1 1.4-1.4h2.4a1.4 1.4 0 0 1 1.4 1.4V7\"/><path d=\"M6.6 7l.8 11.2a1.8 1.8 0 0 0 1.8 1.6h5.6a1.8 1.8 0 0 0 1.8-1.6L17.4 7\"/></svg>";
        private const string CopyIcon = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><rect x=\"9\" y=\"9\" width=\"11\" height=\"11\" rx=\"2.2\"/><path d=\"M5.5 14.5A1.8 1.8 0 0 1 4 12.7V5.8A1.8 1.8 0 0 1 5.8 4h6.9a1.8 1.8 0 0 1 1.8 1.5\"/></svg>";
        private const string RefreshIcon = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M19 12a7 7 0 1 1-2-4.9\"/><path d=\"M19 4.5V8h-3.5\"/></svg>";
        private const string SearchIcon = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><circle cx=\"11\" cy=\"11\" r=\"6.2\"/><path d=\"M16 16l3.4 3.4\"/></svg>";
        private const string PlusIcon = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.9\" stroke-linecap=\"round\"><path d=\"M12 5.5v13M5.5 12h13\"/></svg>";
        private const string UploadIcon = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M12 16V5\"/><path d=\"M7.8 9.2L12 5l4.2 4.2\"/><path d=\"M5 18.5h14\"/></svg>";
        private const string EmptyIcon = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.4\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M3.5 8.5A2 2 0 0 1 5.5 6.5h3.2c.5 0 1 .2 1.3.5l.9.9c.3.3.8.5 1.3.5h6.3a2 2 0 0 1 2 2v6.2a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z\"/></svg>";

        public static string Esc(string text)
        {
            if (text == null) return "";
            return text.Replace("&", "&amp;").Replace("<", "&lt;").Replace(">", "&gt;")
                .Replace("\"", "&quot;").Replace("'", "&#39;");
        }

        public static string UrlEncode(string value) { return Uri.EscapeDataString(value ?? ""); }

        public static string SizeText(long bytes)
        {
            if (bytes <= 0) return "0 B";
            string[] units = { "B", "KB", "MB", "GB", "TB" };
            double value = bytes;
            int index = 0;
            while (value >= 1024 && index < units.Length - 1) { value /= 1024; index++; }
            if (index == 0) return bytes + " B";
            return value.ToString(value >= 100 ? "0" : "0.0", CultureInfo.InvariantCulture) + " " + units[index];
        }

        private static string DateText(DateTime dt)
        {
            if (dt <= TimeCompat.UnixEpoch.AddSeconds(1)) return "—";
            return dt.ToLocalTime().ToString("yyyy-MM-dd HH:mm");
        }

        private static string IconFor(FileCategory category)
        {
            switch (category)
            {
                case FileCategory.Folder: return FolderIcon;
                case FileCategory.Image: return ImageIcon;
                case FileCategory.Video: return VideoIcon;
                case FileCategory.Audio: return AudioIcon;
                case FileCategory.Archive: return ArchiveIcon;
                case FileCategory.Code: return CodeIcon;
                case FileCategory.Document: return DocumentIcon;
                default: return FileIcon;
            }
        }

        private static string Shell(string title, string bodyAttributes, string content, bool includeScript)
        {
            return "<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n" +
                "<meta charset=\"utf-8\">\n" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\">\n" +
                "<meta name=\"color-scheme\" content=\"light dark\">\n" +
                "<title>" + Esc(title) + "</title>\n" +
                "<style>" + WebAssets.Css + "</style>\n" +
                "</head>\n<body " + bodyAttributes + ">\n" + content + "\n" +
                "<div id=\"drop\"><div class=\"zone\"><h2>松开以上传</h2><p>文件会保存到当前目录</p></div></div>\n" +
                "<div id=\"modal\"></div>\n<div id=\"toast\"></div>\n<div id=\"dock\"><div id=\"dock-list\"></div></div>\n" +
                (includeScript ? "<script>" + WebAssets.Js + "</script>" : "") + "\n</body>\n</html>";
        }

        private static string Topbar(string shareName, string active)
        {
            string filesTab = active == "files" ? "tab active" : "tab";
            string textTab = active == "text" ? "tab active" : "tab";
            return "<header class=\"topbar\"><div class=\"topbar-inner\">" +
                "<div class=\"brand\"><span class=\"mark\">" + BrandMark + "</span>" +
                "<span class=\"title\">EZShare</span><span class=\"sub\">" + Esc(shareName) + "</span></div>" +
                "<nav class=\"tabs\"><a class=\"" + filesTab + "\" href=\"/\">文件</a><a class=\"" + textTab + "\" href=\"/text\">文本</a></nav>" +
                "<span class=\"spacer\"></span><span class=\"chip\"><span class=\"dot\"></span>局域网共享中</span>" +
                "</div></header>";
        }

        private const string Footer = "<footer><span>EZShare</span><span class=\"dot\"></span><span>仅同一局域网内可访问</span><span class=\"dot\"></span><span>本地直连 · 不经云端</span></footer>";

        public static string Listing(string relativePath, List<ShareEntry> entries, ShareSettings settings, string baseUrl)
        {
            var sb = new System.Text.StringBuilder();
            sb.Append("<main><nav class=\"crumbs\">").Append(Breadcrumbs(relativePath)).Append("</nav>");
            sb.Append("<div class=\"panel\"><div class=\"toolbar\">");
            sb.Append("<label class=\"search\">").Append(SearchIcon).Append("<input id=\"filter\" type=\"search\" placeholder=\"在当前目录中筛选…\" autocomplete=\"off\"></label>");
            sb.Append("<span class=\"count\" id=\"count\">").Append(entries.Count == 0 ? "空目录" : entries.Count + " 个项目").Append("</span>");
            if (settings.AllowUpload)
            {
                sb.Append("<button class=\"btn\" id=\"btn-mkdir\">").Append(PlusIcon).Append("新建文件夹</button>");
                sb.Append("<button class=\"btn primary\" id=\"btn-upload\">").Append(UploadIcon).Append("上传文件</button>");
                sb.Append("<input type=\"file\" id=\"picker\" multiple hidden>");
            }
            sb.Append("</div>");
            if (entries.Count == 0)
            {
                sb.Append("<div class=\"empty\">").Append(EmptyIcon).Append("<h3>这个目录还是空的</h3><p>")
                  .Append(settings.AllowUpload ? "拖入文件，或点击右上角上传" : "向共享目录放入文件后会显示在这里").Append("</p></div>");
            }
            else
            {
                sb.Append("<table><thead><tr><th>名称</th><th class=\"size\">大小</th><th class=\"time\">修改时间</th><th></th></tr></thead><tbody>");
                foreach (var entry in entries) sb.Append(Row(entry, relativePath, settings));
                sb.Append("</tbody></table>");
            }
            sb.Append("</div></main>").Append(Footer);

            string attrs = "data-dir=\"" + Esc(relativePath) + "\" data-upload=\"" + (settings.AllowUpload ? "1" : "0") +
                "\" data-delete=\"" + (settings.AllowDelete ? "1" : "0") + "\" data-base=\"" + Esc(baseUrl) + "\"";
            return Shell(settings.ShareName + " · EZShare", attrs, Topbar(settings.ShareName, "files") + sb, true);
        }

        private static string Breadcrumbs(string relativePath)
        {
            if (string.IsNullOrEmpty(relativePath)) return "<span class=\"current\">根目录</span>";
            var sb = new System.Text.StringBuilder("<a href=\"/\">根目录</a>");
            string accumulated = "";
            var parts = relativePath.Split('/');
            for (int i = 0; i < parts.Length; i++)
            {
                accumulated = string.IsNullOrEmpty(accumulated) ? parts[i] : accumulated + "/" + parts[i];
                sb.Append("<span class=\"sep\">/</span>");
                if (i == parts.Length - 1) sb.Append("<span class=\"current\">").Append(Esc(parts[i])).Append("</span>");
                else sb.Append("<a href=\"/?path=").Append(UrlEncode(accumulated)).Append("\">").Append(Esc(parts[i])).Append("</a>");
            }
            return sb.ToString();
        }

        private static string Row(ShareEntry entry, string relativePath, ShareSettings settings)
        {
            string childPath = string.IsNullOrEmpty(relativePath) ? entry.Name : relativePath + "/" + entry.Name;
            string encoded = UrlEncode(childPath);
            string href = entry.IsDirectory ? "/?path=" + encoded : "/download?path=" + encoded;
            var actions = new System.Text.StringBuilder();
            if (!entry.IsDirectory) actions.Append("<a class=\"btn ghost icon act\" href=\"/download?path=").Append(encoded).Append("\" title=\"下载\">").Append(DownloadIcon).Append("</a>");
            if (settings.AllowDelete)
                actions.Append("<button class=\"btn ghost icon act\" data-del=\"").Append(encoded).Append("\" data-isdir=\"")
                    .Append(entry.IsDirectory ? "1" : "0").Append("\" data-name-text=\"").Append(Esc(entry.Name)).Append("\" title=\"删除\">").Append(TrashIcon).Append("</button>");
            var category = entry.IsDirectory ? FileCategory.Folder : MimeTypes.Category(entry.Name);
            string sizeCell = entry.IsDirectory ? "—" : SizeText(entry.Size);
            return "<tr data-name=\"" + Esc(entry.Name) + "\"><td class=\"name\"><a class=\"rowlink\" href=\"" + href + "\">" +
                "<span class=\"icon " + MimeTypes.CategoryLabel(category) + "\">" + IconFor(category) + "</span>" +
                "<span class=\"fname\">" + Esc(entry.Name) + "</span></a></td>" +
                "<td class=\"size\">" + sizeCell + "</td><td class=\"time\">" + DateText(entry.Modified) + "</td>" +
                "<td class=\"actions\">" + actions + "</td></tr>";
        }

        public static string TextPage(List<TextSnippet> snippets, ShareSettings settings)
        {
            var sb = new System.Text.StringBuilder();
            if (settings.AllowUpload)
            {
                sb.Append("<div class=\"panel composer\"><textarea id=\"text-input\" placeholder=\"粘贴或输入要共享的文本，局域网内任何设备都能查看、复制…（Ctrl+Enter 发送）\"></textarea>");
                sb.Append("<div class=\"composer-foot\"><span class=\"count\" id=\"text-chars\">0 字符</span><span class=\"spacer\"></span>");
                sb.Append("<button class=\"btn primary\" id=\"btn-text-send\">").Append(UploadIcon).Append("发送到共享</button></div></div>");
            }
            sb.Append("<div class=\"panel\" style=\"margin-top:14px\"><div class=\"toolbar\">");
            sb.Append("<span class=\"count\" id=\"text-count\">").Append(snippets.Count).Append(" 条文本</span><span class=\"spacer\"></span>");
            sb.Append("<button class=\"btn ghost\" id=\"btn-text-refresh\">").Append(RefreshIcon).Append("刷新</button></div>");
            if (snippets.Count == 0)
            {
                sb.Append("<div class=\"empty\">").Append(EmptyIcon).Append("<h3>还没有共享的文本</h3><p>")
                  .Append(settings.AllowUpload ? "在上方粘贴文字后点击发送" : "共享端未开启写入，仅可查看与复制").Append("</p></div>");
            }
            else
            {
                sb.Append("<div class=\"snippets\">");
                foreach (var item in snippets) sb.Append(SnippetRow(item, settings.AllowDelete));
                sb.Append("</div>");
            }
            sb.Append("</div></main>").Append(Footer);
            return Shell("文本 · " + settings.ShareName + " · EZShare", "data-text=\"1\"", Topbar(settings.ShareName, "text") + "<main>" + sb, true);
        }

        private static string SnippetRow(TextSnippet snippet, bool allowDelete)
        {
            var actions = new System.Text.StringBuilder();
            actions.Append("<a class=\"btn ghost icon act\" href=\"/api/text/download?id=").Append(UrlEncode(snippet.Id)).Append("\" title=\"下载为 txt\">").Append(DownloadIcon).Append("</a>");
            actions.Append("<button class=\"btn ghost icon act\" data-textcopy=\"").Append(snippet.Id).Append("\" title=\"复制内容\">").Append(CopyIcon).Append("</button>");
            if (allowDelete) actions.Append("<button class=\"btn ghost icon act\" data-textdel=\"").Append(snippet.Id).Append("\" title=\"删除\">").Append(TrashIcon).Append("</button>");
            string preview = snippet.Content != null && snippet.Content.Length > 20000
                ? snippet.Content.Substring(0, 20000) + "\n…（内容过长，仅显示前 2 万字符，下载可获取完整内容）"
                : snippet.Content;
            long bytes = System.Text.Encoding.UTF8.GetByteCount(preview ?? "");
            return "<div class=\"snippet\" data-id=\"" + snippet.Id + "\"><div class=\"s-head\"><span>" + DateText(snippet.CreatedAt) +
                "</span><span>" + SizeText(bytes) + "</span><span class=\"spacer\"></span>" + actions + "</div>" +
                "<div class=\"s-body\">" + Esc(preview) + "</div></div>";
        }

        public static string Login(string error, string shareName)
        {
            string errorLine = string.IsNullOrEmpty(error) ? "" : "<p class=\"err\">" + Esc(error) + "</p>";
            string content = "<div class=\"login-wrap\"><form class=\"login\" method=\"post\" action=\"/login\">" +
                "<div class=\"mark\">" + BrandMark + "</div><h1>" + Esc(shareName) + "</h1><p>此共享需要访问码</p>" +
                "<input type=\"password\" name=\"code\" autocomplete=\"off\" autocapitalize=\"characters\" spellcheck=\"false\" placeholder=\"••••••\" autofocus>" +
                errorLine + "<button type=\"submit\">进入共享</button></form></div>";
            return Shell("输入访问码 · EZShare", "", content, false);
        }

        public static string ErrorPage(int status, string message)
        {
            string content = "<div class=\"login-wrap\"><div class=\"login\"><div class=\"mark\">" + BrandMark + "</div>" +
                "<h1>" + status + " " + HttpStatus.Reason(status) + "</h1><p>" + Esc(message) + "</p>" +
                "<a class=\"btn\" href=\"/\" style=\"width:100%;height:38px;justify-content:center;margin-top:6px\">返回根目录</a></div></div>";
            return Shell("出错了 · EZShare", "", content, false);
        }
    }
}
