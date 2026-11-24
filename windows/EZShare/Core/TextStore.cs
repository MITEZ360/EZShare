using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;

namespace EZShare.Core
{
    public sealed class TextStore
    {
        private static readonly JsonSerializerOptions JsonOptions = new JsonSerializerOptions { WriteIndented = false };
        private readonly object _lock = new object();
        private readonly string _file;
        private List<TextSnippet> _items = new List<TextSnippet>();
        private const int MaxItems = 200;
        private const int MaxLength = 1_000_000;

        public TextStore()
        {
            string dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "EZShare");
            Directory.CreateDirectory(dir);
            _file = Path.Combine(dir, "texts.json");
            Load();
        }

        private class Dto { public string id { get; set; } public string content { get; set; } public string at { get; set; } }

        private void Load()
        {
            try
            {
                if (!File.Exists(_file)) return;
                var raw = JsonSerializer.Deserialize<List<Dto>>(File.ReadAllText(_file), JsonOptions);
                if (raw == null) return;
                _items = raw.Select(d => new TextSnippet {
                    Id = d.id, Content = d.content,
                    CreatedAt = TryParseDate(d.at)
                }).Where(x => !string.IsNullOrEmpty(x.Id)).ToList();
            }
            catch { _items = new List<TextSnippet>(); }
        }

        private static DateTime TryParseDate(string value)
        {
            return DateTime.TryParse(value, out var dt) ? dt : TimeCompat.UnixEpoch;
        }

        private void Persist()
        {
            var dto = _items.Select(x => new Dto {
                id = x.Id, content = x.Content,
                at = x.CreatedAt.ToUniversalTime().ToString("o")
            }).ToList();
            try { File.WriteAllText(_file, JsonSerializer.Serialize(dto, JsonOptions)); } catch { }
        }

        public int Count { get { lock (_lock) return _items.Count; } }

        public List<TextSnippet> List()
        {
            lock (_lock) { return _items.Select(x => new TextSnippet {
                Id = x.Id, Content = x.Content, CreatedAt = x.CreatedAt
            }).ToList(); }
        }

        public TextSnippet Find(string id)
        {
            lock (_lock)
            {
                var x = _items.FirstOrDefault(i => i.Id == id);
                return x == null ? null : new TextSnippet { Id = x.Id, Content = x.Content, CreatedAt = x.CreatedAt };
            }
        }

        public TextSnippet Add(string raw)
        {
            if (string.IsNullOrWhiteSpace(raw)) return null;
            string content = raw.Trim();
            if (content.Length > MaxLength) return null;
            lock (_lock)
            {
                var item = new TextSnippet { Id = Guid.NewGuid().ToString("N"), Content = content, CreatedAt = DateTime.UtcNow };
                _items.Insert(0, item);
                if (_items.Count > MaxItems) _items.RemoveRange(MaxItems, _items.Count - MaxItems);
                Persist();
                return item;
            }
        }

        public bool Remove(string id)
        {
            lock (_lock)
            {
                int index = _items.FindIndex(i => i.Id == id);
                if (index < 0) return false;
                _items.RemoveAt(index);
                Persist();
                return true;
            }
        }
    }
}
