using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

namespace EZShare.Server
{
    public sealed class HttpRequest
    {
        public string Method { get; set; } = "GET";
        public string Path { get; set; } = "/";
        public Dictionary<string, string> Query { get; set; } = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        public Dictionary<string, string> Headers { get; set; } = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        public byte[] Body { get; set; } = new byte[0];

        public string Header(string key) { return Headers.TryGetValue(key, out var v) ? v : null; }
        public string QueryValue(string key) { return Query.TryGetValue(key, out var v) ? v : null; }
        public string Cookie(string name)
        {
            string raw = Header("cookie");
            if (string.IsNullOrEmpty(raw)) return null;
            foreach (var pair in raw.Split(';'))
            {
                var parts = pair.Split(new[] { '=' }, 2);
                if (parts.Length == 2 && parts[0].Trim() == name) return parts[1].Trim();
            }
            return null;
        }
        public string BodyString { get { return Encoding.UTF8.GetString(Body); } }
    }

    public sealed class HttpResponse
    {
        public int Status { get; set; } = 200;
        public string ContentType { get; set; } = "text/plain; charset=utf-8";
        public byte[] Body { get; set; } = new byte[0];
        public Dictionary<string, string> Headers { get; set; } = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        public string FilePath { get; set; }
        public long FileOffset { get; set; }
        public long FileLength { get; set; } = -1;

        public static HttpResponse Text(string text, int status = 200)
        {
            return new HttpResponse { Status = status, Body = Encoding.UTF8.GetBytes(text ?? "") };
        }
        public static HttpResponse Json(string json, int status = 200)
        {
            return new HttpResponse { Status = status, ContentType = "application/json; charset=utf-8", Body = Encoding.UTF8.GetBytes(json ?? "{}") };
        }
        public static HttpResponse Html(string html, int status = 200)
        {
            return new HttpResponse { Status = status, ContentType = "text/html; charset=utf-8", Body = Encoding.UTF8.GetBytes(html ?? "") };
        }
        public static HttpResponse Redirect(string location, string setCookie = null)
        {
            var r = new HttpResponse { Status = 302, Body = new byte[0] };
            r.Headers["Location"] = location;
            if (setCookie != null) r.Headers["Set-Cookie"] = setCookie;
            return r;
        }
    }

    public static class HttpStatus
    {
        public static string Reason(int status)
        {
            switch (status)
            {
                case 200: return "OK";
                case 206: return "Partial Content";
                case 302: return "Found";
                case 400: return "Bad Request";
                case 401: return "Unauthorized";
                case 403: return "Forbidden";
                case 404: return "Not Found";
                case 405: return "Method Not Allowed";
                case 409: return "Conflict";
                case 413: return "Payload Too Large";
                case 416: return "Range Not Satisfiable";
                case 431: return "Request Header Fields Too Large";
                case 500: return "Internal Server Error";
                default: return "OK";
            }
        }
    }
}
