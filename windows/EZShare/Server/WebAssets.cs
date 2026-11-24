using System;
using System.IO;
using System.Reflection;

namespace EZShare.Server
{
    /// <summary>把 CSS/JS 作为嵌入式资源打进程序集，与 Android/Mac 共用同一份文件。</summary>
    public static class WebAssets
    {
        private static byte[] LoadBytes(string name)
        {
            try
            {
                var assembly = Assembly.GetExecutingAssembly();
                using (var stream = assembly.GetManifestResourceStream("EZShare.Assets." + name))
                {
                    if (stream != null)
                    {
                        using (var memory = new MemoryStream())
                        {
                            stream.CopyTo(memory);
                            return memory.ToArray();
                        }
                    }
                }
            }
            catch { }

            try
            {
                string path = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Assets", name);
                if (File.Exists(path)) return File.ReadAllBytes(path);
            }
            catch { }
            return new byte[0];
        }

        private static string Load(string name)
        {
            try
            {
                var assembly = Assembly.GetExecutingAssembly();
                string resourceName = "EZShare.Assets." + name;
                using (var stream = assembly.GetManifestResourceStream(resourceName))
                {
                    if (stream != null)
                    {
                        using (var reader = new StreamReader(stream))
                            return reader.ReadToEnd();
                    }
                }
            }
            catch { }

            // 回退：从运行目录读取（便于调试）
            try
            {
                string path = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Assets", name);
                if (File.Exists(path)) return File.ReadAllText(path);
            }
            catch { }
            return "";
        }

        public static byte[] BrandPng { get { return _brandPng ?? (_brandPng = LoadBytes("brand.png")); } }
        private static byte[] _brandPng;

        public static string Css { get { return _css ?? (_css = Load("web.css")); } }
        private static string _css;

        public static string Js { get { return _js ?? (_js = Load("web.js")); } }
        private static string _js;
    }
}
