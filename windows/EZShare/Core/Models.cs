using System;
using System.Collections.Generic;

namespace EZShare.Core
{
    public sealed class ShareSettings
    {
        public string RootPath { get; set; } = "";
        public int Port { get; set; } = 8080;
        public bool AllowUpload { get; set; } = true;
        public bool AllowDelete { get; set; } = true;
        public bool RequireCode { get; set; }
        public string AccessCode { get; set; } = "";

        public string ShareName
        {
            get
            {
                if (string.IsNullOrEmpty(RootPath)) return "EZShare";
                string name = System.IO.Path.GetFileName(RootPath.TrimEnd('\\', '/'));
                return string.IsNullOrEmpty(name) ? "EZShare" : name;
            }
        }
    }

    public sealed class ShareEntry
    {
        public string Name { get; set; } = "";
        public string RelativePath { get; set; } = "";
        public bool IsDirectory { get; set; }
        public long Size { get; set; }
        public DateTime Modified { get; set; }
    }

    public sealed class TextSnippet
    {
        public string Id { get; set; } = "";
        public string Content { get; set; } = "";
        public DateTime CreatedAt { get; set; }
    }

    public sealed class DiscoveredPeer
    {
        public string Id { get; set; } = "";
        public string Name { get; set; } = "";
        public string Host { get; set; } = "";
        public int Port { get; set; }
        public bool RequiresCode { get; set; }
        public string Url { get { return "http://" + Host + ":" + Port; } }
    }

    public sealed class AccessLog
    {
        public DateTime Time { get; set; }
        public string Peer { get; set; } = "";
        public string Method { get; set; } = "";
        public string Path { get; set; } = "";
        public int Status { get; set; }
        public long Bytes { get; set; }
    }

    /// <summary>net48 兼容层：DateTime.UnixEpoch 在 .NET Framework 中不存在。</summary>
    public static class TimeCompat
    {
        public static readonly DateTime UnixEpoch = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);
    }
}
