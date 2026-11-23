using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using EZShare.Core;

namespace EZShare.Server
{
    public enum ServerState { Idle, Starting, Running, Failed }

    public sealed class HttpServer : IDisposable
    {
        private readonly Router _router;
        private TcpListener _listener;
        private CancellationTokenSource _cts;
        private readonly object _lock = new object();
        private bool _disposed;
        private const int MaxHead = 64 * 1024;
        private const int MaxBufferedBody = 8 * 1024 * 1024;

        public Action<ServerState, int, string> OnState;
        public Action<AccessLog> OnLog;
        public Action<long> OnTraffic;

        public HttpServer(Router router) { _router = router; }

        public void Start(int preferredPort)
        {
            _cts = new CancellationTokenSource();
            var token = _cts.Token;
            OnState?.Invoke(ServerState.Starting, preferredPort, null);
            Task.Run(() => ListenLoop(preferredPort, token));
        }

        private void ListenLoop(int preferredPort, CancellationToken token)
        {
            int port = preferredPort;
            for (int attempt = 0; attempt < 12 && !token.IsCancellationRequested; attempt++)
            {
                try
                {
                    var listener = new TcpListener(IPAddress.Any, port);
                    listener.Start(32);
                    lock (_lock) _listener = listener;
                    OnState?.Invoke(ServerState.Running, port, port == preferredPort ? null : "端口 " + preferredPort + " 被占用，已改用 " + port);
                    AcceptLoop(listener, token);
                    return;
                }
                catch (SocketException)
                {
                    port++;
                    if (port > 65535) break;
                }
                catch (Exception ex)
                {
                    OnState?.Invoke(ServerState.Failed, preferredPort, ex.Message);
                    return;
                }
            }
            if (!token.IsCancellationRequested) OnState?.Invoke(ServerState.Failed, preferredPort, "没有可用端口");
        }

        private void AcceptLoop(TcpListener listener, CancellationToken token)
        {
            while (!token.IsCancellationRequested)
            {
                TcpClient client;
                try { client = listener.AcceptTcpClient(); }
                catch { return; }
                try { client.NoDelay = true; } catch { }
                string peer = "-";
                try { peer = ((IPEndPoint)client.Client.RemoteEndPoint).Address.ToString(); } catch { }
                Task.Run(() => HandleClient(client, peer, token));
            }
        }

        private void HandleClient(TcpClient client, string peer, CancellationToken token)
        {
            try
            {
                using (client)
                using (var stream = client.GetStream())
                {
                    stream.ReadTimeout = 30000;
                    stream.WriteTimeout = 30000;
                    var head = new List<byte>();
                    int headerEnd = ReadUntilHeader(stream, head);
                    if (headerEnd < 0)
                    {
                        SendSimple(stream, "HTTP/1.1 431 Request Header Fields Too Large\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
                        return;
                    }

                    string headText = Encoding.UTF8.GetString(head.ToArray(), 0, headerEnd);
                    string[] lines = headText.Split(new[] { "\r\n" }, StringSplitOptions.None);
                    if (lines.Length == 0 || string.IsNullOrEmpty(lines[0])) { SendBadRequest(stream); return; }

                    var requestLine = lines[0].Split(' ');
                    if (requestLine.Length < 2) { SendBadRequest(stream); return; }
                    var request = new HttpRequest { Method = requestLine[0].ToUpperInvariant() };
                    string rawTarget = requestLine[1];
                    int qIndex = rawTarget.IndexOf('?');
                    if (qIndex >= 0)
                    {
                        request.Path = Uri.UnescapeDataString(rawTarget.Substring(0, qIndex));
                        ParseQuery(request, rawTarget.Substring(qIndex + 1));
                    }
                    else request.Path = Uri.UnescapeDataString(rawTarget);

                    for (int i = 1; i < lines.Length; i++)
                    {
                        int colon = lines[i].IndexOf(':');
                        if (colon <= 0) continue;
                        request.Headers[lines[i].Substring(0, colon).Trim()] = lines[i].Substring(colon + 1).Trim();
                    }

                    // 头部之后已读到的字节
                    int bodyStart = headerEnd + 4;
                    var initial = head.GetRange(bodyStart, head.Count - bodyStart).ToArray();

                    if (request.Method == "PUT" && request.Path == "/api/upload")
                    {
                        HandleUpload(stream, request, initial, peer);
                        return;
                    }

                    request.Body = ReadBody(stream, request, initial, MaxBufferedBody);
                    var response = _router.Handle(request);
                    SendResponse(stream, request, response, peer);
                }
            }
            catch { }
        }

        private static void SendBadRequest(Stream stream)
        {
            SendSimple(stream, "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
        }

        private static void SendSimple(Stream stream, string raw)
        {
            try { var bytes = Encoding.ASCII.GetBytes(raw); stream.Write(bytes, 0, bytes.Length); stream.Flush(); } catch { }
        }

        private static void ParseQuery(HttpRequest request, string query)
        {
            if (string.IsNullOrEmpty(query)) return;
            foreach (var pair in query.Split('&'))
            {
                if (pair.Length == 0) continue;
                var parts = pair.Split(new[] { '=' }, 2);
                string key = Uri.UnescapeDataString(parts[0].Replace("+", " "));
                string value = parts.Length == 2 ? Uri.UnescapeDataString(parts[1].Replace("+", " ")) : "";
                request.Query[key] = value;
            }
        }

        private static int ReadUntilHeader(Stream stream, List<byte> buffer)
        {
            var pattern = new byte[] { (byte)'\r', (byte)'\n', (byte)'\r', (byte)'\n' };
            var byteBuffer = new byte[8192];
            while (buffer.Count <= MaxHead)
            {
                int n;
                try { n = stream.Read(byteBuffer, 0, byteBuffer.Length); }
                catch { return -1; }
                if (n <= 0) return -1;
                for (int i = 0; i < n; i++)
                {
                    buffer.Add(byteBuffer[i]);
                    int count = buffer.Count;
                    if (count >= 4 && buffer[count - 4] == pattern[0] && buffer[count - 3] == pattern[1]
                        && buffer[count - 2] == pattern[2] && buffer[count - 1] == pattern[3])
                        return count - 4;
                }
            }
            return -1;
        }

        private static byte[] ReadBody(Stream stream, HttpRequest request, byte[] initial, int limit)
        {
            var body = new List<byte>(initial);
            bool chunked = (request.Header("transfer-encoding") ?? "").IndexOf("chunked", StringComparison.OrdinalIgnoreCase) >= 0;
            if (chunked) { ReadChunked(stream, body, limit); return body.ToArray(); }
            int declared = 0;
            int.TryParse(request.Header("content-length") ?? "0", out declared);
            if (declared <= 0) return body.ToArray();
            int remaining = Math.Min(declared, limit) - body.Count;
            var buffer = new byte[8192];
            while (remaining > 0)
            {
                int n;
                try { n = stream.Read(buffer, 0, Math.Min(buffer.Length, remaining)); }
                catch { break; }
                if (n <= 0) break;
                body.AddRange(new ArraySegment<byte>(buffer, 0, n));
                remaining -= n;
            }
            return body.ToArray();
        }

        private static void ReadChunked(Stream stream, List<byte> output, int limit)
        {
            var buffer = new byte[8192];
            while (output.Count < limit)
            {
                var line = new List<byte>();
                try
                {
                    while (true)
                    {
                        int b = stream.ReadByte();
                        if (b < 0) return;
                        if (b == '\n') break;
                        if (b != '\r') line.Add((byte)b);
                    }
                }
                catch { return; }
                string sizeText = Encoding.ASCII.GetString(line.ToArray()).Split(';')[0].Trim();
                int size;
                if (!int.TryParse(sizeText, System.Globalization.NumberStyles.HexNumber, null, out size) || size <= 0) return;
                int remaining = size;
                while (remaining > 0)
                {
                    int n;
                    try { n = stream.Read(buffer, 0, Math.Min(buffer.Length, remaining)); }
                    catch { return; }
                    if (n <= 0) return;
                    if (output.Count + n <= limit) output.AddRange(new ArraySegment<byte>(buffer, 0, n));
                    remaining -= n;
                }
                try { stream.ReadByte(); stream.ReadByte(); } catch { }
            }
        }

        private void HandleUpload(Stream stream, HttpRequest request, byte[] initial, string peer)
        {
            var plan = _router.UploadPlan(request);
            string target = plan.Item1;
            if (plan.Item2 != null)
            {
                SendResponse(stream, request, plan.Item2, peer);
                return;
            }
            try
            {
                long written = 0;
                using (var file = new FileStream(target, FileMode.Create, FileAccess.Write, FileShare.None, 8192, true))
                {
                    if (initial.Length > 0) { file.Write(initial, 0, initial.Length); written += initial.Length; }
                    bool chunked = (request.Header("transfer-encoding") ?? "").IndexOf("chunked", StringComparison.OrdinalIgnoreCase) >= 0;
                    if (chunked)
                    {
                        var buffer = new byte[65536];
                        while (true)
                        {
                            var line = new List<byte>();
                            int b;
                            while (true)
                            {
                                b = stream.ReadByte();
                                if (b < 0) break;
                                if (b == '\n') break;
                                if (b != '\r') line.Add((byte)b);
                            }
                            if (b < 0) break;
                            string sizeText = Encoding.ASCII.GetString(line.ToArray()).Split(';')[0].Trim();
                            int size;
                            if (!int.TryParse(sizeText, System.Globalization.NumberStyles.HexNumber, null, out size) || size == 0) break;
                            int remaining = size;
                            while (remaining > 0)
                            {
                                int n = stream.Read(buffer, 0, Math.Min(buffer.Length, remaining));
                                if (n <= 0) break;
                                file.Write(buffer, 0, n);
                                written += n;
                                remaining -= n;
                            }
                            try { stream.ReadByte(); stream.ReadByte(); } catch { }
                        }
                    }
                    else
                    {
                        long declared = 0;
                        long.TryParse(request.Header("content-length") ?? "0", out declared);
                        var buffer = new byte[65536];
                        long remaining = declared - initial.Length;
                        while (remaining > 0)
                        {
                            int n = stream.Read(buffer, 0, (int)Math.Min(buffer.Length, remaining));
                            if (n <= 0) break;
                            file.Write(buffer, 0, n);
                            written += n;
                            remaining -= n;
                        }
                    }
                    file.Flush();
                }
                OnTraffic?.Invoke(-written);
                string json = _router.UploadCompleted(target, written);
                SendResponse(stream, request, HttpResponse.Json(json), peer, written);
            }
            catch
            {
                SendResponse(stream, request, HttpResponse.Json("{\"ok\":false,\"error\":\"写入失败\"}", 500), peer);
            }
        }

        private void SendResponse(Stream stream, HttpRequest request, HttpResponse response, string peer, long overrideBytes = -1)
        {
            try
            {
                long contentLength = response.FileLength >= 0 ? response.FileLength : response.Body.Length;
                bool headOnly = request.Method == "HEAD" || response.Status == 204;
                if (response.Status == 204) contentLength = 0;

                var sb = new StringBuilder();
                sb.Append("HTTP/1.1 ").Append(response.Status).Append(' ').Append(HttpStatus.Reason(response.Status)).Append("\r\n");
                sb.Append("Date: ").Append(DateTime.UtcNow.ToString("r")).Append("\r\n");
                sb.Append("Server: EZShare/1.0\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\n");
                foreach (var kv in response.Headers)
                    if (!kv.Key.Equals("Content-Length", StringComparison.OrdinalIgnoreCase))
                        sb.Append(kv.Key).Append(": ").Append(kv.Value).Append("\r\n");
                sb.Append("Content-Type: ").Append(response.ContentType).Append("\r\n");
                sb.Append("Content-Length: ").Append(contentLength).Append("\r\n\r\n");
                var headBytes = Encoding.ASCII.GetBytes(sb.ToString());
                stream.Write(headBytes, 0, headBytes.Length);

                long sent = 0;
                if (!headOnly)
                {
                    if (response.FilePath != null && response.FileLength > 0)
                    {
                        using (var file = new FileStream(response.FilePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite, 65536, true))
                        {
                            file.Seek(response.FileOffset, SeekOrigin.Begin);
                            var buffer = new byte[65536];
                            long remaining = response.FileLength;
                            while (remaining > 0)
                            {
                                int n = file.Read(buffer, 0, (int)Math.Min(buffer.Length, remaining));
                                if (n <= 0) break;
                                stream.Write(buffer, 0, n);
                                remaining -= n;
                                sent += n;
                            }
                        }
                    }
                    else if (response.Body.Length > 0)
                    {
                        stream.Write(response.Body, 0, response.Body.Length);
                        sent = response.Body.Length;
                    }
                }
                stream.Flush();

                long logged = overrideBytes >= 0 ? overrideBytes : sent;
                OnLog?.Invoke(new AccessLog {
                    Time = DateTime.Now, Peer = peer, Method = request.Method,
                    Path = request.Path, Status = response.Status, Bytes = logged
                });
                if (logged > 0) OnTraffic?.Invoke(logged);
            }
            catch { }
        }

        public void Stop()
        {
            try { _cts?.Cancel(); } catch { }
            try { lock (_lock) { _listener?.Stop(); _listener = null; } } catch { }
            OnState?.Invoke(ServerState.Idle, 0, null);
        }

        public void Dispose()
        {
            if (_disposed) return;
            _disposed = true;
            Stop();
            try { _cts?.Dispose(); } catch { }
        }
    }
}
