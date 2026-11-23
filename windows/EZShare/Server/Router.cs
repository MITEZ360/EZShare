using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using EZShare.Core;

namespace EZShare.Server
{
    public sealed class Router
    {
        private readonly ConfigStore _config;
        private readonly TextStore _texts;
        private readonly HashSet<string> _sessions = new HashSet<string>();
        private readonly object _lock = new object();
        private static readonly JsonSerializerOptions JsonOptions = new JsonSerializerOptions { Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping };

        public Router(ConfigStore config, TextStore texts)
        {
            _config = config;
            _texts = texts;
        }

        private static HttpResponse Json(Dictionary<string, object> map, int status = 200)
        {
            return HttpResponse.Json(JsonSerializer.Serialize(map, JsonOptions), status);
        }
        private static HttpResponse Ok(int status = 200) { return Json(new Dictionary<string, object> { { "ok", true } }, status); }
        private static HttpResponse Fail(string message, int status = 400)
        {
            return Json(new Dictionary<string, object> { { "ok", false }, { "error", message } }, status);
        }

        public HttpResponse Handle(HttpRequest request)
        {
            var settings = _config.Snapshot;
            switch (request.Path)
            {
                case "/favicon.ico": return HttpResponse.Text("", 204);
                case "/health": return HttpResponse.Text("ok");
                case "/login":
                    if (request.Method == "POST") return HandleLogin(request, settings);
                    return HttpResponse.Html(WebUI.Login(null, settings.ShareName));
                case "/logout":
                    string token = request.Cookie("ezshare_token");
                    if (!string.IsNullOrEmpty(token)) lock (_lock) _sessions.Remove(token);
                    return HttpResponse.Redirect("/login", "ezshare_token=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
            }

            if (!Authorized(request, settings))
            {
                string accept = request.Header("accept") ?? "";
                if (accept.Contains("text/html") && !request.Path.StartsWith("/api/")) return HttpResponse.Redirect("/login");
                return Fail("需要访问码", 401);
            }

            switch (request.Path)
            {
                case "/": return HandleListing(request, settings);
                case "/text": return HandleTextPage(request, settings);
                case "/download": return HandleDownload(request, settings);
                case "/api/info":
                    return Json(new Dictionary<string, object> {
                        { "ok", true }, { "name", settings.ShareName },
                        { "allowUpload", settings.AllowUpload }, { "allowDelete", settings.AllowDelete },
                        { "requireCode", settings.RequireCode }, { "version", 1 }
                    });
                case "/api/files": return HandleFiles(request, settings);
                case "/api/mkdir": return HandleMkdir(request, settings);
                case "/api/delete": return HandleDelete(request, settings);
                case "/api/texts":
                    if (request.Method == "GET") return HandleTextsList();
                    return HandleTextCreate(request, settings);
                case "/api/text": return HandleTextGet(request);
                case "/api/text/delete": return HandleTextDelete(request, settings);
                case "/api/text/download": return HandleTextDownload(request);
                case "/api/upload": return Fail("上传需使用流式请求", 400);
                default: return HttpResponse.Html(WebUI.ErrorPage(404, "找不到这个地址"), 404);
            }
        }

        private bool Authorized(HttpRequest request, ShareSettings settings)
        {
            if (!settings.RequireCode || string.IsNullOrEmpty(settings.AccessCode)) return true;
            string token = request.Cookie("ezshare_token");
            if (string.IsNullOrEmpty(token)) return false;
            lock (_lock) return _sessions.Contains(token);
        }

        private HttpResponse HandleLogin(HttpRequest request, ShareSettings settings)
        {
            string form = request.BodyString;
            string code = "";
            foreach (var pair in form.Split('&'))
            {
                var parts = pair.Split(new[] { '=' }, 2);
                if (parts.Length == 2 && parts[0] == "code") { code = Uri.UnescapeDataString(parts[1].Replace("+", " ")); break; }
            }
            if (string.IsNullOrEmpty(settings.AccessCode) || code != settings.AccessCode)
                return HttpResponse.Html(WebUI.Login("访问码不正确", settings.ShareName), 401);
            string token = Guid.NewGuid().ToString("N");
            lock (_lock) _sessions.Add(token);
            return HttpResponse.Redirect("/", "ezshare_token=" + token + "; Path=/; HttpOnly; SameSite=Lax");
        }

        private HttpResponse HandleListing(HttpRequest request, ShareSettings settings)
        {
            string relative = PathResolver.NormalizeRelative(request.QueryValue("path"));
            string target = PathResolver.Resolve(relative, settings.RootPath);
            if (target == null) return HttpResponse.Html(WebUI.ErrorPage(403, "路径超出共享目录"), 403);
            if (!Directory.Exists(target))
            {
                if (File.Exists(target)) return FileResponse(target, request);
                return HttpResponse.Html(WebUI.ErrorPage(404, "目录不存在"), 404);
            }
            var entries = ListEntries(target);
            string normalized = PathResolver.RelativePathOf(target, settings.RootPath);
            return HttpResponse.Html(WebUI.Listing(normalized, entries, settings, "http://" + (request.Header("host") ?? "localhost")));
        }

        private HttpResponse HandleFiles(HttpRequest request, ShareSettings settings)
        {
            if (request.Method != "GET") return Fail("方法不允许", 405);
            string relative = PathResolver.NormalizeRelative(request.QueryValue("path"));
            string target = PathResolver.Resolve(relative, settings.RootPath);
            if (target == null) return Fail("路径越界", 403);
            if (!Directory.Exists(target)) return Fail("目录不存在", 404);
            string normalized = PathResolver.RelativePathOf(target, settings.RootPath);
            var entries = ListEntries(target).Select(e => new Dictionary<string, object> {
                { "name", e.Name }, { "path", e.RelativePath }, { "isDirectory", e.IsDirectory },
                { "size", e.Size }, { "modified", e.Modified.ToUniversalTime().ToString("o") }
            }).ToList();
            return Json(new Dictionary<string, object> {
                { "ok", true }, { "path", normalized }, { "entries", entries },
                { "allowUpload", settings.AllowUpload }, { "allowDelete", settings.AllowDelete }
            });
        }

        private List<ShareEntry> ListEntries(string directory)
        {
            var result = new List<ShareEntry>();
            try
            {
                foreach (string path in Directory.EnumerateFileSystemEntries(directory))
                {
                    try
                    {
                        var info = new FileInfo(path);
                        bool isDir = (File.GetAttributes(path) & FileAttributes.Directory) != 0;
                        result.Add(new ShareEntry {
                            Name = Path.GetFileName(path),
                            RelativePath = "",
                            IsDirectory = isDir,
                            Size = isDir ? 0 : info.Length,
                            Modified = isDir ? Directory.GetLastWriteTimeUtc(path) : info.LastWriteTimeUtc
                        });
                    }
                    catch { }
                }
            }
            catch { }
            return result.OrderBy(e => !e.IsDirectory).ThenBy(e => e.Name, StringComparer.CurrentCultureIgnoreCase).ToList();
        }

        private HttpResponse HandleDownload(HttpRequest request, ShareSettings settings)
        {
            string relative = request.QueryValue("path");
            if (string.IsNullOrEmpty(relative)) return HttpResponse.Html(WebUI.ErrorPage(400, "缺少 path 参数"), 400);
            string target = PathResolver.Resolve(PathResolver.NormalizeRelative(relative), settings.RootPath);
            if (target == null) return HttpResponse.Html(WebUI.ErrorPage(403, "路径超出共享目录"), 403);
            return FileResponse(target, request);
        }

        private HttpResponse FileResponse(string path, HttpRequest request)
        {
            if (!File.Exists(path)) return HttpResponse.Html(WebUI.ErrorPage(404, "文件不存在"), 404);
            long size = new FileInfo(path).Length;
            var response = new HttpResponse {
                ContentType = MimeTypes.Of(path),
                FilePath = path
            };
            response.Headers["Accept-Ranges"] = "bytes";
            response.Headers["Content-Disposition"] = Disposition(Path.GetFileName(path));

            long start = 0, end = size > 0 ? size - 1 : 0;
            int status = 200;
            string rangeHeader = request.Header("range");
            bool hasRange = !string.IsNullOrEmpty(rangeHeader) && rangeHeader.StartsWith("bytes=", StringComparison.OrdinalIgnoreCase);
            if (hasRange)
            {
                if (!TryParseRange(rangeHeader, size, out start, out end))
                {
                    response.Status = 416;
                    response.Headers["Content-Range"] = "bytes */" + size;
                    response.FileLength = 0;
                    return response;
                }
                status = 206;
                response.Headers["Content-Range"] = "bytes " + start + "-" + end + "/" + size;
            }
            long length = size == 0 ? 0 : end - start + 1;
            response.Status = status;
            response.FileOffset = start;
            response.FileLength = length;
            return response;
        }

        private static bool TryParseRange(string header, long size, out long start, out long end)
        {
            start = 0; end = size > 0 ? size - 1 : 0;
            if (size <= 0 || !header.StartsWith("bytes=", StringComparison.OrdinalIgnoreCase)) return false;
            string spec = header.Substring(6).Split(',')[0];
            var parts = spec.Split('-');
            if (parts.Length != 2) return false;
            string startText = parts[0].Trim(), endText = parts[1].Trim();
            if (startText.Length == 0)
            {
                if (!long.TryParse(endText, out long suffix) || suffix <= 0) return false;
                start = Math.Max(0, size - suffix); end = size - 1; return true;
            }
            if (!long.TryParse(startText, out start) || start >= size) return false;
            if (endText.Length == 0) { end = size - 1; return true; }
            if (!long.TryParse(endText, out end) || end < start) return false;
            end = Math.Min(end, size - 1);
            return true;
        }

        private static string Disposition(string name)
        {
            string ascii = new string(name.Select(c => c >= 32 && c < 127 && c != '"' ? c : '_').ToArray());
            string inlineTypes = "image/ video/ audio/ text/plain application/pdf";
            bool inline = inlineTypes.Split(' ').Any(t => MimeTypes.Of(name).StartsWith(t, StringComparison.OrdinalIgnoreCase));
            string mode = inline ? "inline" : "attachment";
            return mode + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + Uri.EscapeDataString(name);
        }

        private HttpResponse HandleTextPage(HttpRequest request, ShareSettings settings)
        {
            if (request.Method != "GET") return Fail("方法不允许", 405);
            return HttpResponse.Html(WebUI.TextPage(_texts.List(), settings));
        }

        private HttpResponse HandleTextsList()
        {
            var items = _texts.List().Select(s => new Dictionary<string, object> {
                { "id", s.Id }, { "content", s.Content }, { "createdAt", s.CreatedAt.ToUniversalTime().ToString("o") }
            }).ToList();
            return Json(new Dictionary<string, object> { { "ok", true }, { "items", items } });
        }

        private HttpResponse HandleTextCreate(HttpRequest request, ShareSettings settings)
        {
            if (request.Method != "POST") return Fail("方法不允许", 405);
            if (!settings.AllowUpload) return Fail("共享端已关闭写入", 403);
            string content = JsonString(request.BodyString, "content");
            var item = _texts.Add(content);
            if (item == null) return Fail("内容为空或超过 1MB 上限", 400);
            return Json(new Dictionary<string, object> { { "ok", true }, { "id", item.Id } });
        }

        private HttpResponse HandleTextGet(HttpRequest request)
        {
            string id = request.QueryValue("id");
            var item = _texts.Find(id);
            if (item == null) return Fail("文本不存在", 404);
            return Json(new Dictionary<string, object> {
                { "ok", true }, { "content", item.Content }, { "id", item.Id },
                { "createdAt", item.CreatedAt.ToUniversalTime().ToString("o") }
            });
        }

        private HttpResponse HandleTextDelete(HttpRequest request, ShareSettings settings)
        {
            if (request.Method != "POST") return Fail("方法不允许", 405);
            if (!settings.AllowDelete) return Fail("共享端已关闭删除", 403);
            string id = JsonString(request.BodyString, "id");
            if (string.IsNullOrEmpty(id)) return Fail("缺少 id", 400);
            if (!_texts.Remove(id)) return Fail("文本不存在", 404);
            return Ok();
        }

        private HttpResponse HandleTextDownload(HttpRequest request)
        {
            string id = request.QueryValue("id");
            var item = _texts.Find(id);
            if (item == null) return HttpResponse.Html(WebUI.ErrorPage(404, "文本不存在"), 404);
            string name = "文本-" + item.CreatedAt.ToLocalTime().ToString("yyyyMMdd-HHmmss") + ".txt";
            var response = HttpResponse.Text(item.Content ?? "");
            response.Headers["Content-Disposition"] = "attachment; filename=\"text.txt\"; filename*=UTF-8''" + Uri.EscapeDataString(name);
            return response;
        }

        private HttpResponse HandleMkdir(HttpRequest request, ShareSettings settings)
        {
            if (!settings.AllowUpload) return Fail("共享端已关闭写入", 403);
            string body = request.BodyString;
            string name = PathResolver.SanitizeName(JsonString(body, "name"));
            if (name == null) return Fail("文件夹名称无效", 400);
            string dir = PathResolver.NormalizeRelative(JsonString(body, "dir"));
            string parent = PathResolver.Resolve(dir, settings.RootPath);
            if (parent == null) return Fail("目录越界", 403);
            if (!Directory.Exists(parent)) return Fail("上级目录不存在", 404);
            string target = Path.Combine(parent, name);
            if (File.Exists(target) || Directory.Exists(target)) return Fail("同名项目已存在", 409);
            try { Directory.CreateDirectory(target); return Ok(); }
            catch (Exception ex) { return Fail(ex.Message, 500); }
        }

        private HttpResponse HandleDelete(HttpRequest request, ShareSettings settings)
        {
            if (!settings.AllowDelete) return Fail("共享端已关闭删除", 403);
            string relative = PathResolver.NormalizeRelative(JsonString(request.BodyString, "path"));
            if (string.IsNullOrEmpty(relative)) return Fail("不能删除共享根目录", 400);
            string target = PathResolver.Resolve(relative, settings.RootPath);
            if (target == null) return Fail("路径越界", 403);
            if (!File.Exists(target) && !Directory.Exists(target)) return Fail("项目不存在", 404);
            try
            {
                if ((File.GetAttributes(target) & FileAttributes.Directory) != 0) Directory.Delete(target, true);
                else File.Delete(target);
                return Ok();
            }
            catch (Exception ex) { return Fail(ex.Message, 500); }
        }

        /// <summary>上传预检：返回目标绝对路径，或错误响应。</summary>
        public Tuple<string, HttpResponse> UploadPlan(HttpRequest request)
        {
            var settings = _config.Snapshot;
            if (!Authorized(request, settings)) return Tuple.Create<string, HttpResponse>(null, Fail("需要访问码", 401));
            if (!settings.AllowUpload) return Tuple.Create<string, HttpResponse>(null, Fail("共享端已关闭上传", 403));
            string name = PathResolver.SanitizeName(request.QueryValue("name"));
            if (name == null) return Tuple.Create<string, HttpResponse>(null, Fail("文件名无效", 400));
            string dir = PathResolver.NormalizeRelative(request.QueryValue("dir"));
            string directory = PathResolver.Resolve(dir, settings.RootPath);
            if (directory == null) return Tuple.Create<string, HttpResponse>(null, Fail("目录越界", 403));
            if (!Directory.Exists(directory)) return Tuple.Create<string, HttpResponse>(null, Fail("目标目录不存在", 404));
            string target = PathResolver.UniquePath(Path.Combine(directory, name));
            return Tuple.Create<string, HttpResponse>(target, null);
        }

        public string UploadCompleted(string path, long written)
        {
            return JsonSerializer.Serialize(new Dictionary<string, object> {
                { "ok", true }, { "name", Path.GetFileName(path) }, { "size", written }
            }, JsonOptions);
        }

        private static string JsonString(string json, string key)
        {
            try
            {
                using (var doc = JsonDocument.Parse(json))
                    if (doc.RootElement.TryGetProperty(key, out var value) && value.ValueKind == JsonValueKind.String)
                        return value.GetString();
            }
            catch { }
            return "";
        }
    }
}
