using System;
using System.IO;

namespace EZShare.Core
{
    public static class PathResolver
    {
        public static string Resolve(string relative, string root)
        {
            string full = System.IO.Path.GetFullPath(root);
            string target = System.IO.Path.GetFullPath(System.IO.Path.Combine(full, relative ?? ""));
            if (string.Equals(target, full, StringComparison.OrdinalIgnoreCase)) return target;
            if (target.StartsWith(full + System.IO.Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase)) return target;
            return null;
        }

        public static string RelativePathOf(string path, string root)
        {
            string full = System.IO.Path.GetFullPath(root);
            string target = System.IO.Path.GetFullPath(path);
            if (!target.StartsWith(full, StringComparison.OrdinalIgnoreCase)) return "";
            string rel = target.Substring(full.Length).TrimStart('\\', '/');
            return rel;
        }

        public static string SanitizeName(string raw)
        {
            if (string.IsNullOrWhiteSpace(raw)) return null;
            string name = raw.Replace('/', '_').Replace('\\', '_').Replace("\0", "").Trim();
            if (name == "." || name == "..") return null;
            if (name.Length > 200) name = name.Substring(0, 200);
            return name.Length == 0 ? null : name;
        }

        public static string UniquePath(string desired)
        {
            if (!File.Exists(desired) && !Directory.Exists(desired)) return desired;
            string dir = System.IO.Path.GetDirectoryName(desired);
            string name = System.IO.Path.GetFileName(desired);
            string ext = System.IO.Path.GetExtension(name);
            string baseName = ext.Length > 0 ? name.Substring(0, name.Length - ext.Length) : name;
            for (int i = 1; i < 10000; i++)
            {
                string candidate = System.IO.Path.Combine(dir, baseName + " (" + i + ")" + ext);
                if (!File.Exists(candidate) && !Directory.Exists(candidate)) return candidate;
            }
            return System.IO.Path.Combine(dir, Guid.NewGuid().ToString("N") + ext);
        }

        public static string NormalizeRelative(string relative)
        {
            return (relative ?? "").Trim('/', '\\').Replace('\\', '/');
        }
    }
}
