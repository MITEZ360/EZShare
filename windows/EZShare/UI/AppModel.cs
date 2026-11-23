using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using System.Windows;
using EZShare.Client;
using EZShare.Core;
using EZShare.Server;

namespace EZShare.UI
{
    public sealed class AppModel : IDisposable
    {
        private readonly ConfigStore _config = new ConfigStore();
        private readonly TextStore _texts = new TextStore();
        private readonly MdnsService _discovery = new MdnsService();
        private HttpServer _server;
        private Router _router;
        private RemoteClient _remote;

        public bool IsRunning { get; private set; }
        public bool IsStarting { get; private set; }
        public bool IsJoined { get { return _remote != null; } }
        public int BoundPort { get; private set; }
        public string StatusText { get; private set; } = "未启动";
        public string BrowsePath { get; set; } = "";
        public List<ShareEntry> FileEntries { get; private set; } = new List<ShareEntry>();
        public List<TextSnippet> Snippets { get; private set; } = new List<TextSnippet>();
        public List<DiscoveredPeer> Peers { get; private set; } = new List<DiscoveredPeer>();
        public List<string> Addresses { get; private set; } = new List<string>();
        public RemoteInfo RemoteInfo { get; private set; }
        public string LastError { get; private set; }

        public Action OnChanged;
        private void Changed() { OnChanged?.Invoke(); }

        public AppModel()
        {
            BoundPort = _config.Snapshot.Port;
            Addresses = NetworkInfo.IPv4();
            _discovery.OnPeers = list => { Peers = list; Changed(); };
            RefreshContent();
            _discovery.StartBrowsing();
        }

        public ShareSettings Settings { get { return _config.Snapshot; } }
        public string ShareName { get { return _config.Snapshot.ShareName; } }
        public int DisplayPort { get { return IsRunning ? BoundPort : _config.Snapshot.Port; } }
        public List<string> Urls { get { return Addresses.Select(a => "http://" + a + ":" + DisplayPort).ToList(); } }
        public string PrimaryUrl { get { var u = Urls; return u.Count > 0 ? u[0] : null; } }

        public string BrowseDisplayName
        {
            get
            {
                var parts = BrowsePath.Split(new[] { '/' }, StringSplitOptions.RemoveEmptyEntries);
                return parts.Length > 0 ? parts[parts.Length - 1] : ShareName;
            }
        }

        // ---------- 服务控制 ----------

        public void Toggle()
        {
            if (IsRunning || IsStarting) Stop(); else Start();
        }

        public void Start()
        {
            if (IsRunning || IsStarting) return;
            if (IsJoined) LeaveRemote();
            var settings = _config.Snapshot;
            _router = new Router(_config, _texts);
            _server = new HttpServer(_router);
            _server.OnState = (state, port, note) =>
            {
                switch (state)
                {
                    case ServerState.Starting:
                        IsStarting = true; StatusText = "正在启动…"; break;
                    case ServerState.Running:
                        IsRunning = true; IsStarting = false; BoundPort = port;
                        StatusText = note ?? "正在共享";
                        _discovery.Advertise(port, NetworkInfo.MachineName(), settings.RequireCode);
                        break;
                    case ServerState.Failed:
                    case ServerState.Idle:
                        IsRunning = false; IsStarting = false; StatusText = state == ServerState.Idle ? "已停止" : "启动失败：" + note;
                        _discovery.StopAdvertising();
                        break;
                }
                Changed();
            };
            IsStarting = true;
            StatusText = "正在启动…";
            Changed();
            _server.Start(settings.Port);
        }

        public void Stop()
        {
            _server?.Stop();
            _server?.Dispose();
            _server = null;
            _discovery.StopAdvertising();
            IsRunning = false; IsStarting = false;
            StatusText = "已停止";
            BoundPort = _config.Snapshot.Port;
            Changed();
        }

        // ---------- 发现与加入 ----------

        public void RefreshPeers() { _discovery.Refresh(); }

        public async Task<bool> JoinAsync(DiscoveredPeer peer, string code)
        {
            LeaveRemote();
            var client = new RemoteClient(peer.Url);
            bool ok = await client.LoginAsync(code ?? "").ConfigureAwait(false);
            if (!ok) { client.Dispose(); LastError = "加入失败：访问码不正确或无法连接"; Changed(); return false; }
            var info = await client.InfoAsync().ConfigureAwait(false);
            if (info == null) { client.Dispose(); LastError = "加入失败：无法读取共享信息"; Changed(); return false; }
            _remote = client;
            RemoteInfo = info;
            BrowsePath = "";
            StatusText = "已连接 · " + info.Name;
            LastError = null;
            RefreshContent();
            Changed();
            return true;
        }

        public void LeaveRemote()
        {
            if (_remote == null) return;
            _remote.Dispose();
            _remote = null;
            RemoteInfo = null;
            BrowsePath = "";
            StatusText = IsRunning ? "正在共享" : "未启动";
            RefreshContent();
            Changed();
        }

        // ---------- 内容浏览 ----------

        public void RefreshContent()
        {
            if (IsJoined)
            {
                LoadRemoteFiles();
                LoadRemoteTexts();
            }
            else
            {
                LoadLocalFiles();
                Snippets = _texts.List();
            }
            Changed();
        }

        private void LoadLocalFiles()
        {
            var settings = _config.Snapshot;
            string root = settings.RootPath;
            string target = PathResolver.Resolve(BrowsePath, root) ?? root;
            var list = new List<ShareEntry>();
            try
            {
                foreach (string path in Directory.EnumerateFileSystemEntries(target))
                {
                    try
                    {
                        bool isDir = (File.GetAttributes(path) & FileAttributes.Directory) != 0;
                        var info = new FileInfo(path);
                        list.Add(new ShareEntry {
                            Name = Path.GetFileName(path),
                            RelativePath = string.IsNullOrEmpty(BrowsePath) ? Path.GetFileName(path) : BrowsePath + "/" + Path.GetFileName(path),
                            IsDirectory = isDir,
                            Size = isDir ? 0 : info.Length,
                            Modified = isDir ? Directory.GetLastWriteTimeUtc(path) : info.LastWriteTimeUtc
                        });
                    }
                    catch { }
                }
            }
            catch { }
            FileEntries = list.OrderBy(e => !e.IsDirectory).ThenBy(e => e.Name, StringComparer.CurrentCultureIgnoreCase).ToList();
        }

        private async void LoadRemoteFiles()
        {
            if (_remote == null) return;
            var result = await _remote.FilesAsync(BrowsePath).ConfigureAwait(true);
            if (_remote == null) return;
            FileEntries = result?.Entries ?? new List<ShareEntry>();
            Changed();
        }

        private async void LoadRemoteTexts()
        {
            if (_remote == null) return;
            Snippets = await _remote.TextsAsync().ConfigureAwait(true);
            Changed();
        }

        public void OpenEntry(ShareEntry entry)
        {
            if (entry.IsDirectory)
            {
                BrowsePath = entry.RelativePath;
                RefreshContent();
            }
            else RevealOrDownload(entry);
        }

        public void GoUp()
        {
            var parts = BrowsePath.Split(new[] { '/' }, StringSplitOptions.RemoveEmptyEntries);
            BrowsePath = string.Join("/", parts.Take(Math.Max(0, parts.Length - 1)));
            RefreshContent();
        }

        private void RevealOrDownload(ShareEntry entry)
        {
            if (!IsJoined)
            {
                try
                {
                    string root = _config.Snapshot.RootPath;
                    string path = PathResolver.Resolve(entry.RelativePath, root);
                    if (path != null && File.Exists(path))
                        Process.Start("explorer.exe", "/select,\"" + path + "\"");
                }
                catch { }
            }
            else if (_remote != null)
            {
                try { Process.Start(new ProcessStartInfo(_remote.DownloadUrl(entry.RelativePath)) { UseShellExecute = true }); } catch { }
            }
        }

        public string LocalPath(ShareEntry entry)
        {
            return PathResolver.Resolve(entry.RelativePath, _config.Snapshot.RootPath);
        }

        // ---------- 写操作 ----------

        public bool SendText(string raw)
        {
            if (IsJoined)
            {
                if (_remote == null || string.IsNullOrWhiteSpace(raw)) return false;
                try { Task.Run(async () => { await _remote.AddTextAsync(raw).ConfigureAwait(true); RefreshContent(); }); return true; }
                catch { return false; }
            }
            var item = _texts.Add(raw);
            if (item == null) return false;
            RefreshContent();
            return true;
        }

        public bool DeleteText(TextSnippet snippet)
        {
            if (IsJoined)
            {
                Task.Run(async () => { await _remote.DeleteTextAsync(snippet.Id).ConfigureAwait(true); RefreshContent(); });
                return true;
            }
            bool ok = _texts.Remove(snippet.Id);
            RefreshContent();
            return ok;
        }

        public async Task<bool> AddFilesAsync(string[] sources)
        {
            if (IsJoined)
            {
                if (_remote == null) return false;
                int added = 0;
                foreach (var source in sources)
                {
                    try
                    {
                        byte[] data = File.ReadAllBytes(source);
                        string name = Path.GetFileName(source);
                        if (await _remote.UploadAsync(BrowsePath, name, data).ConfigureAwait(true)) added++;
                    }
                    catch { }
                }
                RefreshContent();
                return added > 0;
            }
            var settings = _config.Snapshot;
            string directory = PathResolver.Resolve(BrowsePath, settings.RootPath) ?? settings.RootPath;
            int count = 0;
            foreach (var source in sources)
            {
                try
                {
                    string name = PathResolver.SanitizeName(Path.GetFileName(source));
                    if (name == null) continue;
                    string target = PathResolver.UniquePath(Path.Combine(directory, name));
                    if ((File.GetAttributes(source) & FileAttributes.Directory) != 0)
                        CopyDirectory(source, target);
                    else File.Copy(source, target, false);
                    count++;
                }
                catch { }
            }
            RefreshContent();
            return count > 0;
        }

        private static void CopyDirectory(string source, string target)
        {
            Directory.CreateDirectory(target);
            foreach (var file in Directory.GetFiles(source)) File.Copy(file, Path.Combine(target, Path.GetFileName(file)), false);
            foreach (var dir in Directory.GetDirectories(source)) CopyDirectory(dir, Path.Combine(target, Path.GetFileName(dir)));
        }

        public async Task<bool> MkdirAsync(string name)
        {
            if (string.IsNullOrWhiteSpace(name)) return false;
            if (IsJoined)
            {
                if (_remote == null) return false;
                bool ok = await _remote.MkdirAsync(BrowsePath, name).ConfigureAwait(true);
                RefreshContent();
                return ok;
            }
            var settings = _config.Snapshot;
            string parent = PathResolver.Resolve(BrowsePath, settings.RootPath) ?? settings.RootPath;
            string safe = PathResolver.SanitizeName(name);
            if (safe == null) return false;
            string target = Path.Combine(parent, safe);
            if (Directory.Exists(target) || File.Exists(target)) return false;
            try { Directory.CreateDirectory(target); RefreshContent(); return true; }
            catch { return false; }
        }

        public async Task<bool> DeleteEntryAsync(ShareEntry entry)
        {
            if (string.IsNullOrEmpty(entry.RelativePath)) return false;
            if (IsJoined)
            {
                if (_remote == null) return false;
                bool ok = await _remote.DeleteAsync(entry.RelativePath).ConfigureAwait(true);
                RefreshContent();
                return ok;
            }
            string target = PathResolver.Resolve(entry.RelativePath, _config.Snapshot.RootPath);
            if (target == null) return false;
            try
            {
                if ((File.GetAttributes(target) & FileAttributes.Directory) != 0) Directory.Delete(target, true);
                else File.Delete(target);
                RefreshContent();
                return true;
            }
            catch { return false; }
        }

        // ---------- 设置 ----------

        public void UpdateSettings(string rootPath, int port, bool allowUpload, bool allowDelete, bool requireCode, string accessCode)
        {
            var next = new ShareSettings {
                RootPath = string.IsNullOrWhiteSpace(rootPath) ? _config.Snapshot.RootPath : rootPath,
                Port = Math.Min(Math.Max(port, 1024), 65535),
                AllowUpload = allowUpload, AllowDelete = allowDelete,
                RequireCode = requireCode, AccessCode = accessCode
            };
            try { Directory.CreateDirectory(next.RootPath); } catch { }
            _config.Update(next);
            BrowsePath = "";
            BoundPort = next.Port;
            RefreshContent();
            Changed();
        }

        public string RegenerateCode() { return _config.RegenerateCode(); }

        public void RefreshAddresses()
        {
            Addresses = NetworkInfo.IPv4();
            Changed();
        }

        public void Copy(string text)
        {
            try { Clipboard.SetText(text); } catch { }
        }

        public void Dispose()
        {
            Stop();
            _discovery.Dispose();
        }
    }
}
