using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;

namespace EZShare.Core
{
    /// <summary>精简 DNS-SD / mDNS 实现，仅依赖 BCL，与 Apple Bonjour / Android NSD 互通。</summary>
    public sealed class MdnsService : IDisposable
    {
        public const string ServiceType = "_ezshare._tcp.local.";
        private static readonly IPAddress MulticastAddress = IPAddress.Parse("224.0.0.251");
        private const int Port = 5353;

        private UdpClient _udp;
        private readonly object _sendLock = new object();
        private readonly object _peerLock = new object();
        private readonly Dictionary<string, DiscoveredPeer> _peers = new Dictionary<string, DiscoveredPeer>();
        private System.Threading.Timer _queryTimer;
        private bool _disposed;
        private bool _browsing;

        // 本机发布信息
        private string _instanceName;
        private string _hostName;
        private int _advertisePort;
        private bool _advertiseLock;
        private string _advertiseCode;
        private bool _advertising;

        public Action<List<DiscoveredPeer>> OnPeers;

        public MdnsService()
        {
            _hostName = "ezshare-" + Guid.NewGuid().ToString("N").Substring(0, 8) + ".local.";
            try
            {
                _udp = new UdpClient();
                _udp.ExclusiveAddressUse = false;
                _udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
                _udp.Client.Bind(new IPEndPoint(IPAddress.Any, Port));
                TryJoinMulticast();
                _udp.BeginReceive(OnReceive, null);
            }
            catch
            {
                // 端口被占用（如系统 Bonjour 服务）：改用临时端口，仍能收发多播与单播响应。
                try
                {
                    _udp?.Close();
                    _udp = new UdpClient();
                    _udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
                    _udp.Client.Bind(new IPEndPoint(IPAddress.Any, 0));
                    TryJoinMulticast();
                    _udp.BeginReceive(OnReceive, null);
                }
                catch { _udp = null; }
            }
        }

        private void TryJoinMulticast()
        {
            try
            {
                foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (nic.OperationalStatus != OperationalStatus.Up) continue;
                    foreach (var ua in nic.GetIPProperties().UnicastAddresses)
                    {
                        if (ua.Address.AddressFamily == AddressFamily.InterNetwork)
                        {
                            try { _udp.JoinMulticastGroup(MulticastAddress, ua.Address); } catch { }
                        }
                    }
                }
            }
            catch { }
        }

        public void Advertise(int port, string deviceName, bool requireCode)
        {
            _advertisePort = port;
            _advertiseLock = requireCode;
            _advertiseCode = requireCode ? "1" : "0";
            _instanceName = SanitizeInstance(deviceName + "-" + port + (requireCode ? "-lock" : ""));
            _advertising = true;
            SendAnnouncement(0);
        }

        public void StopAdvertising()
        {
            if (!_advertising) return;
            _advertising = false;
            SendAnnouncement(120); // TTL=0 的 goodbye 包
        }

        private static string SanitizeInstance(string value)
        {
            var sb = new StringBuilder();
            foreach (char c in value)
                sb.Append(char.IsLetterOrDigit(c) || c == '-' ? c : '-');
            if (sb.Length == 0) sb.Append("EZShare");
            if (sb.Length > 60) sb.Length = 60;
            return sb.ToString();
        }

        public void StartBrowsing()
        {
            if (_browsing) return;
            _browsing = true;
            SendQuery();
            _queryTimer = new System.Threading.Timer(_ => SendQuery(), null, 3000, 3000);
        }

        public void StopBrowsing()
        {
            _browsing = false;
            _queryTimer?.Dispose();
            _queryTimer = null;
        }

        public void Refresh()
        {
            lock (_peerLock) { _peers.Clear(); }
            Emit();
            SendQuery();
        }

        private void Emit()
        {
            List<DiscoveredPeer> list;
            lock (_peerLock) { list = _peers.Values.OrderBy(p => p.Name).ToList(); }
            OnPeers?.Invoke(list);
        }

        private void SendQuery()
        {
            if (_udp == null) return;
            try
            {
                var writer = new DnsWriter();
                writer.WriteUInt16(0);      // ID
                writer.WriteUInt16(0);      // flags: standard query
                writer.WriteUInt16(1);      // QDCOUNT
                writer.WriteUInt16(0); writer.WriteUInt16(0); writer.WriteUInt16(0);
                writer.WriteName(ServiceType);
                writer.WriteUInt16(12);     // PTR
                writer.WriteUInt16(1);      // IN
                Send(writer.ToArray());
            }
            catch { }
        }

        private void SendAnnouncement(uint ttl)
        {
            if (_udp == null || !_advertising && ttl != 0) return;
            try
            {
                string fqdn = _instanceName + "._ezshare._tcp.local.";
                var writer = new DnsWriter();
                writer.WriteUInt16(0);
                writer.WriteUInt16(0x8400); // response, authoritative
                writer.WriteUInt16(0);      // QDCOUNT
                writer.WriteUInt16(4);      // ANCOUNT
                writer.WriteUInt16(0); writer.WriteUInt16(0);

                // PTR
                writer.WriteName(ServiceType);
                writer.WriteUInt16(12); writer.WriteUInt16(1); writer.WriteUInt32(ttl);
                var rdata = new DnsWriter(); rdata.WriteName(fqdn);
                writer.WriteLengthPrefixed(rdata.ToArray());

                // SRV
                writer.WriteName(fqdn);
                writer.WriteUInt16(33); writer.WriteUInt16(1); writer.WriteUInt32(ttl);
                var srv = new DnsWriter();
                srv.WriteUInt16(0); srv.WriteUInt16(0); srv.WriteUInt16((ushort)_advertisePort);
                srv.WriteName(_hostName);
                writer.WriteLengthPrefixed(srv.ToArray());

                // TXT
                writer.WriteName(fqdn);
                writer.WriteUInt16(16); writer.WriteUInt16(1); writer.WriteUInt32(ttl);
                var txt = new DnsWriter();
                txt.WriteTxtString("name=" + _instanceName);
                txt.WriteTxtString("port=" + _advertisePort);
                txt.WriteTxtString("v=1");
                txt.WriteTxtString("code=" + _advertiseCode);
                writer.WriteLengthPrefixed(txt.ToArray());

                // A
                writer.WriteName(_hostName);
                writer.WriteUInt16(1); writer.WriteUInt16(1); writer.WriteUInt32(ttl);
                var a = new DnsWriter();
                string ip = NetworkInfo.IPv4().FirstOrDefault(x => x != "127.0.0.1") ?? "127.0.0.1";
                a.WriteBytes(IPAddress.Parse(ip).GetAddressBytes());
                writer.WriteLengthPrefixed(a.ToArray());

                Send(writer.ToArray());
                if (ttl == 0) Send(writer.ToArray());
            }
            catch { }
        }

        private void Send(byte[] data)
        {
            if (_udp == null) return;
            try
            {
                lock (_sendLock)
                    _udp.Send(data, data.Length, new IPEndPoint(MulticastAddress, Port));
            }
            catch { }
        }

        private void SendUnicast(byte[] data, IPEndPoint to)
        {
            if (_udp == null) return;
            try { lock (_sendLock) _udp.Send(data, data.Length, to); } catch { }
        }

        private void OnReceive(IAsyncResult ar)
        {
            if (_disposed || _udp == null) return;
            IPEndPoint remote = new IPEndPoint(IPAddress.Any, 0);
            byte[] data;
            try { data = _udp.EndReceive(ar, ref remote); }
            catch { return; }

            try { ProcessPacket(data, remote); } catch { }

            if (!_disposed && _udp != null)
            {
                try { _udp.BeginReceive(OnReceive, null); } catch { }
            }
        }

        private void ProcessPacket(byte[] data, IPEndPoint remote)
        {
            var reader = new DnsReader(data);
            reader.ReadUInt16(); // ID
            int flags = reader.ReadUInt16();
            bool isResponse = (flags & 0x8000) != 0;
            int qd = reader.ReadUInt16();
            int an = reader.ReadUInt16();
            int ns = reader.ReadUInt16();
            int ar = reader.ReadUInt16();

            // 处理查询：任何人问 _ezshare._tcp 就回应本机服务
            if (!isResponse && _advertising && qd > 0)
            {
                bool asksEzshare = false;
                for (int i = 0; i < qd; i++)
                {
                    string name = reader.ReadName();
                    int type = reader.ReadUInt16();
                    reader.ReadUInt16();
                    if (type == 12 && name.IndexOf("_ezshare", StringComparison.OrdinalIgnoreCase) >= 0)
                        asksEzshare = true;
                }
                if (asksEzshare) SendAnnouncement(120);
                return;
            }

            if (!isResponse) return;

            // 解析回答段：PTR 找实例名，SRV 得端口+主机，TXT 得 code，A 得 IP
            var instanceNames = new List<string>();
            var srv = new Dictionary<string, Tuple<string, int>>();
            var txt = new Dictionary<string, string>();
            var addresses = new Dictionary<string, string>();

            for (int i = 0; i < an + ar; i++)
            {
                string name = reader.ReadName();
                int type = reader.ReadUInt16();
                int klass = reader.ReadUInt16();
                uint ttl = reader.ReadUInt32();
                int len = reader.ReadUInt16();
                int end = reader.Position + len;

                if (type == 12 && klass == 1)
                {
                    string target = reader.ReadName();
                    if (name.IndexOf("_ezshare", StringComparison.OrdinalIgnoreCase) >= 0 ||
                        target.IndexOf("_ezshare", StringComparison.OrdinalIgnoreCase) >= 0)
                        instanceNames.Add(target);
                }
                else if (type == 33 && klass == 1)
                {
                    reader.ReadUInt16(); reader.ReadUInt16();
                    int port = reader.ReadUInt16();
                    string host = reader.ReadName();
                    srv[name] = Tuple.Create(host, port);
                }
                else if (type == 16 && klass == 1)
                {
                    string text = reader.ReadTxt();
                    txt[name] = text;
                }
                else if (type == 1 && klass == 1)
                {
                    if (len >= 4)
                    {
                        var bytes = reader.ReadBytes(4);
                        addresses[name] = new IPAddress(bytes).ToString();
                    }
                }
                reader.Position = end;
            }

            foreach (string fqdn in instanceNames)
            {
                if (!srv.TryGetValue(fqdn, out var srvInfo)) continue;
                string host = srvInfo.Item1;
                int port = srvInfo.Item2;
                string ip = null;
                if (host != null)
                {
                    string key = host.EndsWith(".") ? host : host + ".";
                    if (addresses.TryGetValue(key, out var found)) ip = found;
                }
                if (string.IsNullOrEmpty(ip) && remote != null && remote.Address.AddressFamily == AddressFamily.InterNetwork
                    && !remote.Address.Equals(MulticastAddress))
                    ip = remote.Address.ToString();
                if (string.IsNullOrEmpty(ip)) continue;

                string display = fqdn.Split('.')[0];
                bool lockCode = display.EndsWith("-lock", StringComparison.OrdinalIgnoreCase);
                if (lockCode) display = display.Substring(0, display.Length - 5);
                txt.TryGetValue(fqdn, out var textRecord);
                if (!string.IsNullOrEmpty(textRecord))
                {
                    var nameEntry = ParseTxtField(textRecord, "name=");
                    if (!string.IsNullOrEmpty(nameEntry)) display = nameEntry.Replace("-lock", "");
                    var codeEntry = ParseTxtField(textRecord, "code=");
                    if (codeEntry == "1") lockCode = true;
                }

                var peer = new DiscoveredPeer {
                    Id = fqdn,
                    Name = string.IsNullOrEmpty(display) ? "EZShare" : display,
                    Host = ip,
                    Port = port,
                    RequiresCode = lockCode
                };
                bool changed = false;
                lock (_peerLock)
                {
                    if (!_peers.TryGetValue(peer.Id, out var old) || old.Host != peer.Host || old.Port != peer.Port || old.RequiresCode != peer.RequiresCode)
                    { _peers[peer.Id] = peer; changed = true; }
                }
                if (changed) Emit();
            }
        }

        private static string ParseTxtField(string joined, string prefix)
        {
            foreach (var part in joined.Split('\n'))
                if (part.StartsWith(prefix, StringComparison.Ordinal)) return part.Substring(prefix.Length);
            return "";
        }

        public void Dispose()
        {
            _disposed = true;
            StopBrowsing();
            try { StopAdvertising(); } catch { }
            try { if (_udp != null) _udp.Close(); } catch { }
            _udp = null;
        }

        // ---------- DNS 编解码 ----------

        private sealed class DnsWriter
        {
            private readonly List<byte> _bytes = new List<byte>();
            public void WriteBytes(byte[] data) { _bytes.AddRange(data); }
            public void WriteByte(byte b) { _bytes.Add(b); }
            public void WriteUInt16(int value) { _bytes.Add((byte)(value >> 8)); _bytes.Add((byte)value); }
            public void WriteUInt32(uint value)
            {
                _bytes.Add((byte)(value >> 24)); _bytes.Add((byte)(value >> 16));
                _bytes.Add((byte)(value >> 8)); _bytes.Add((byte)value);
            }
            public void WriteName(string name)
            {
                if (name.EndsWith(".")) name = name.Substring(0, name.Length - 1);
                foreach (var label in name.Split('.'))
                {
                    var raw = Encoding.UTF8.GetBytes(label);
                    WriteByte((byte)raw.Length);
                    WriteBytes(raw);
                }
                WriteByte(0);
            }
            public void WriteTxtString(string value)
            {
                var raw = Encoding.UTF8.GetBytes(value);
                if (raw.Length > 255) raw = raw.Take(255).ToArray();
                WriteByte((byte)raw.Length);
                WriteBytes(raw);
            }
            public void WriteLengthPrefixed(byte[] payload)
            {
                WriteUInt16(payload.Length);
                WriteBytes(payload);
            }
            public byte[] ToArray() { return _bytes.ToArray(); }
        }

        private sealed class DnsReader
        {
            private readonly byte[] _data;
            public int Position { get; set; }
            public DnsReader(byte[] data) { _data = data; Position = 0; }

            public int ReadByteValue()
            {
                if (Position >= _data.Length) return 0;
                return _data[Position++];
            }
            public int ReadUInt16()
            {
                int hi = ReadByteValue(), lo = ReadByteValue();
                return (hi << 8) | lo;
            }
            public uint ReadUInt32()
            {
                uint a = (uint)ReadByteValue(), b = (uint)ReadByteValue(), c = (uint)ReadByteValue(), d = (uint)ReadByteValue();
                return (a << 24) | (b << 16) | (c << 8) | d;
            }
            public byte[] ReadBytes(int count)
            {
                var result = new byte[count];
                for (int i = 0; i < count; i++) result[i] = (byte)ReadByteValue();
                return result;
            }

            public string ReadName()
            {
                var labels = new List<string>();
                int jumps = 0;
                int original = Position;
                while (true)
                {
                    if (Position >= _data.Length) break;
                    int length = _data[Position];
                    if ((length & 0xC0) == 0xC0)
                    {
                        if (Position + 1 >= _data.Length) break;
                        int pointer = ((length & 0x3F) << 8) | _data[Position + 1];
                        Position += 2;
                        if (jumps++ > 10) break;
                        int saved = Position;
                        Position = pointer;
                        string tail = ReadName();
                        if (!string.IsNullOrEmpty(tail)) labels.Add(tail);
                        Position = saved;
                        break;
                    }
                    Position++;
                    if (length == 0) break;
                    if (length > 63 || Position + length > _data.Length) break;
                    labels.Add(Encoding.UTF8.GetString(_data, Position, length));
                    Position += length;
                }
                if (labels.Count == 0) Position = Math.Max(Position, original + 1);
                return string.Join(".", labels) + ".";
            }

            public string ReadTxt()
            {
                var lines = new List<string>();
                int start = Position;
                while (Position < _data.Length)
                {
                    int length = _data[Position++];
                    if (length == 0) break;
                    if (Position + length > _data.Length) break;
                    lines.Add(Encoding.UTF8.GetString(_data, Position, length));
                    Position += length;
                }
                if (Position == start) return "";
                return string.Join("\n", lines);
            }
        }
    }
}
