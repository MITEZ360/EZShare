using System;
using System.IO;
using System.Text.Json;

namespace EZShare.Core
{
    /// <summary>配置持久化到 %APPDATA%/EZShare/config.json，程序级而非会话级。</summary>
    public sealed class ConfigStore
    {
        private static readonly JsonSerializerOptions JsonOptions = new JsonSerializerOptions { WriteIndented = true };
        private readonly object _lock = new object();
        private readonly string _file;
        private ShareSettings _settings;

        public ConfigStore()
        {
            string dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "EZShare");
            Directory.CreateDirectory(dir);
            _file = Path.Combine(dir, "config.json");
            Load();
        }

        private void Load()
        {
            _settings = new ShareSettings();
            try
            {
                if (File.Exists(_file))
                {
                    var saved = JsonSerializer.Deserialize<ShareSettings>(File.ReadAllText(_file), JsonOptions);
                    if (saved != null) _settings = saved;
                }
            }
            catch { }

            bool changed = false;
            if (string.IsNullOrWhiteSpace(_settings.RootPath) || !Directory.Exists(_settings.RootPath))
            {
                string downloads = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads", "EZShare");
                try { Directory.CreateDirectory(downloads); } catch { }
                _settings.RootPath = downloads;
                changed = true;
            }
            if (string.IsNullOrEmpty(_settings.AccessCode) || IsLegacyCode(_settings.AccessCode))
            {
                _settings.AccessCode = GenerateCode();
                changed = true;
            }
            if (_settings.Port < 1024 || _settings.Port > 65535) { _settings.Port = 8080; changed = true; }
            if (changed) Save();
        }

        private static bool IsLegacyCode(string code)
        {
            return code != null && code.Length == 4 && char.IsDigit(code[0]) && char.IsDigit(code[1])
                && char.IsDigit(code[2]) && char.IsDigit(code[3]);
        }

        public ShareSettings Snapshot
        {
            get { lock (_lock) { return new ShareSettings {
                RootPath = _settings.RootPath, Port = _settings.Port,
                AllowUpload = _settings.AllowUpload, AllowDelete = _settings.AllowDelete,
                RequireCode = _settings.RequireCode, AccessCode = _settings.AccessCode
            }; } }
        }

        public void Update(ShareSettings next)
        {
            lock (_lock)
            {
                _settings.RootPath = next.RootPath;
                _settings.Port = next.Port;
                _settings.AllowUpload = next.AllowUpload;
                _settings.AllowDelete = next.AllowDelete;
                _settings.RequireCode = next.RequireCode;
                _settings.AccessCode = next.AccessCode;
                Save();
            }
        }

        public string RegenerateCode()
        {
            lock (_lock) { _settings.AccessCode = GenerateCode(); Save(); return _settings.AccessCode; }
        }

        private void Save()
        {
            try { File.WriteAllText(_file, JsonSerializer.Serialize(_settings, JsonOptions)); } catch { }
        }

        public static string GenerateCode()
        {
            const string digits = "23456789";
            const string letters = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ";
            string all = digits + letters;
            var random = new Random(Guid.NewGuid().GetHashCode());
            while (true)
            {
                var chars = new char[6];
                bool hasDigit = false, hasLetter = false;
                for (int i = 0; i < chars.Length; i++)
                {
                    char c = all[random.Next(all.Length)];
                    chars[i] = c;
                    if (digits.IndexOf(c) >= 0) hasDigit = true; else hasLetter = true;
                }
                if (hasDigit && hasLetter) return new string(chars);
            }
        }
    }
}
