using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using EZShare.Core;

namespace EZShare.Client
{
    public sealed class RemoteInfo
    {
        public string Name { get; set; } = "EZShare";
        public bool AllowUpload { get; set; }
        public bool AllowDelete { get; set; }
        public bool RequireCode { get; set; }
    }

    public sealed class RemoteFiles
    {
        public string Path { get; set; } = "";
        public List<ShareEntry> Entries { get; set; } = new List<ShareEntry>();
        public bool AllowUpload { get; set; }
        public bool AllowDelete { get; set; }
    }

    /// <summary>加入其他设备共享时使用的轻量 HTTP 客户端，Cookie 保存在内存，断开即失效。</summary>
    public sealed class RemoteClient : IDisposable
    {
        private readonly string _baseUrl;
        private readonly HttpClient _http;
        private readonly CookieContainer _cookies = new CookieContainer();
        private static readonly JsonSerializerOptions JsonOptions = new JsonSerializerOptions { PropertyNameCaseInsensitive = true };

        public RemoteClient(string baseUrl)
        {
            _baseUrl = (baseUrl ?? "").TrimEnd('/');
            var handler = new HttpClientHandler { CookieContainer = _cookies, UseCookies = true, AllowAutoRedirect = true };
            _http = new HttpClient(handler);
            _http.Timeout = TimeSpan.FromSeconds(30);
        }

        private class Envelope<T>
        {
            public bool ok { get; set; }
            public string error { get; set; }
            public string name { get; set; }
            public string path { get; set; }
            public bool allowUpload { get; set; }
            public bool allowDelete { get; set; }
            public bool requireCode { get; set; }
            public List<EntryDto> entries { get; set; }
            public List<TextDto> items { get; set; }
            public string content { get; set; }
            public string id { get; set; }
            public string createdAt { get; set; }
        }
        private class EntryDto
        {
            public string name { get; set; }
            public string path { get; set; }
            public bool isDirectory { get; set; }
            public long size { get; set; }
            public string modified { get; set; }
        }
        private class TextDto
        {
            public string id { get; set; }
            public string content { get; set; }
            public string createdAt { get; set; }
        }

        private async Task<Envelope<T>> GetJson<T>(string path)
        {
            try
            {
                var raw = await _http.GetStringAsync(_baseUrl + path).ConfigureAwait(false);
                return JsonSerializer.Deserialize<Envelope<T>>(raw, JsonOptions);
            }
            catch { return null; }
        }

        private async Task<Envelope<T>> PostJson<T>(string path, string payload)
        {
            try
            {
                var content = new StringContent(payload, Encoding.UTF8, "application/json");
                var response = await _http.PostAsync(_baseUrl + path, content).ConfigureAwait(false);
                var raw = await response.Content.ReadAsStringAsync().ConfigureAwait(false);
                return JsonSerializer.Deserialize<Envelope<T>>(raw, JsonOptions);
            }
            catch { return null; }
        }

        public async Task<bool> LoginAsync(string code)
        {
            try
            {
                var form = new FormUrlEncodedContent(new[] { new KeyValuePair<string, string>("code", code) });
                var response = await _http.PostAsync(_baseUrl + "/login", form).ConfigureAwait(false);
                if ((int)response.StatusCode >= 400) return false;
                return await InfoAsync() != null;
            }
            catch { return false; }
        }

        public async Task<RemoteInfo> InfoAsync()
        {
            var env = await GetJson<object>("/api/info").ConfigureAwait(false);
            if (env == null || !env.ok) return null;
            return new RemoteInfo {
                Name = string.IsNullOrEmpty(env.name) ? "EZShare" : env.name,
                AllowUpload = env.allowUpload, AllowDelete = env.allowDelete, RequireCode = env.requireCode
            };
        }

        public async Task<RemoteFiles> FilesAsync(string path)
        {
            var env = await GetJson<object>("/api/files?path=" + Uri.EscapeDataString(path ?? "")).ConfigureAwait(false);
            if (env == null || !env.ok) return null;
            var result = new RemoteFiles {
                Path = env.path ?? "", AllowUpload = env.allowUpload, AllowDelete = env.allowDelete
            };
            if (env.entries != null)
            {
                foreach (var e in env.entries)
                {
                    DateTime modified = DateTime.TryParse(e.modified, out var dt) ? dt : DateTime.UnixEpoch;
                    result.Entries.Add(new ShareEntry {
                        Name = e.name, RelativePath = e.path, IsDirectory = e.isDirectory,
                        Size = e.size, Modified = modified
                    });
                }
            }
            return result;
        }

        public async Task<List<TextSnippet>> TextsAsync()
        {
            var env = await GetJson<object>("/api/texts").ConfigureAwait(false);
            var result = new List<TextSnippet>();
            if (env == null || !env.ok || env.items == null) return result;
            foreach (var t in env.items)
                result.Add(new TextSnippet {
                    Id = t.id, Content = t.content,
                    CreatedAt = DateTime.TryParse(t.createdAt, out var dt) ? dt : DateTime.UnixEpoch
                });
            return result;
        }

        public async Task<bool> AddTextAsync(string content)
        {
            string payload = JsonSerializer.Serialize(new Dictionary<string, object> { { "content", content } });
            var env = await PostJson<object>("/api/texts", payload).ConfigureAwait(false);
            return env != null && env.ok;
        }

        public async Task<bool> DeleteTextAsync(string id)
        {
            string payload = JsonSerializer.Serialize(new Dictionary<string, object> { { "id", id } });
            var env = await PostJson<object>("/api/text/delete", payload).ConfigureAwait(false);
            return env != null && env.ok;
        }

        public async Task<bool> MkdirAsync(string dir, string name)
        {
            string payload = JsonSerializer.Serialize(new Dictionary<string, object> { { "dir", dir }, { "name", name } });
            var env = await PostJson<object>("/api/mkdir", payload).ConfigureAwait(false);
            return env != null && env.ok;
        }

        public async Task<bool> DeleteAsync(string path)
        {
            string payload = JsonSerializer.Serialize(new Dictionary<string, object> { { "path", path } });
            var env = await PostJson<object>("/api/delete", payload).ConfigureAwait(false);
            return env != null && env.ok;
        }

        public async Task<bool> UploadAsync(string dir, string name, byte[] data)
        {
            try
            {
                string url = _baseUrl + "/api/upload?dir=" + Uri.EscapeDataString(dir ?? "") + "&name=" + Uri.EscapeDataString(name ?? "");
                using (var content = new ByteArrayContent(data))
                {
                    content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("application/octet-stream");
                    var response = await _http.PutAsync(url, content).ConfigureAwait(false);
                    var raw = await response.Content.ReadAsStringAsync().ConfigureAwait(false);
                    var env = JsonSerializer.Deserialize<Envelope<object>>(raw, JsonOptions);
                    return response.IsSuccessStatusCode && env != null && env.ok;
                }
            }
            catch { return false; }
        }

        public string DownloadUrl(string path) { return _baseUrl + "/download?path=" + Uri.EscapeDataString(path ?? ""); }
        public string TextDownloadUrl(string id) { return _baseUrl + "/api/text/download?id=" + Uri.EscapeDataString(id ?? ""); }

        public void Dispose() { try { _http.Dispose(); } catch { } }
    }
}
