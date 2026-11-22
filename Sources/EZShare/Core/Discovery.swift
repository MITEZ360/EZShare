import Foundation
import Network

/// 局域网内发现的另一个 EZShare 实例。
struct DiscoveredPeer: Identifiable, Equatable {
    let id: String
    let name: String
    let host: String
    let port: Int

    var url: String { "http://\(host):\(port)" }
}

/// 基于 Bonjour / mDNS 的局域网发现：发布本机服务并浏览同类服务。
final class LANDiscovery {
    static let serviceType = "_ezshare._tcp"

    private let queue = DispatchQueue(label: "com.ezshare.discovery")
    private var browser: NWBrowser?
    private var probes: [NWConnection] = []
    private var generation = 0

    var onUpdate: (([DiscoveredPeer]) -> Void)?

    /// 重新浏览一次。会丢弃上一次结果，回调在主线程触发。
    func refresh() {
        queue.async(group: nil, qos: .unspecified, flags: []) {
            self.generation += 1
            let current = self.generation

            self.browser?.browseResultsChangedHandler = nil
            self.browser?.stateUpdateHandler = nil
            self.browser?.cancel()
            self.browser = nil
            self.probes.forEach { $0.stateUpdateHandler = nil; $0.cancel() }
            self.probes.removeAll()

            let descriptor = NWBrowser.Descriptor.bonjour(type: Self.serviceType, domain: "local.")
            let browser = NWBrowser(for: descriptor, using: NWParameters())
            self.browser = browser

            var found: [String: DiscoveredPeer] = [:]

            browser.browseResultsChangedHandler = { results, _ in
                var seen = Set<String>()
                for result in results {
                    guard case .service(let name, _, _, _) = result.endpoint else { continue }
                    let key = name
                    seen.insert(key)
                    guard found[key] == nil else { continue }

                    // 用一次短连接把 Bonjour 服务解析成可访问的 IP + 端口
                    let probe = NWConnection(to: result.endpoint, using: .tcp)
                    self.probes.append(probe)
                    probe.stateUpdateHandler = { [weak self] state in
                        guard let self else { return }
                        switch state {
                        case .ready:
                            if let path = probe.currentPath,
                               case let .hostPort(host, port) = path.remoteEndpoint {
                                let peer = DiscoveredPeer(
                                    id: key,
                                    name: name,
                                    host: Self.hostString(host),
                                    port: Int(port.rawValue)
                                )
                                self.queue.async(group: nil, qos: .unspecified, flags: []) {
                                    guard current == self.generation else { return }
                                    found[key] = peer
                                    self.publish(found)
                                }
                            }
                            probe.stateUpdateHandler = nil
                            probe.cancel()
                        case .failed, .cancelled:
                            probe.stateUpdateHandler = nil
                        default:
                            break
                        }
                    }
                    probe.start(queue: self.queue)
                }

                self.queue.async(group: nil, qos: .unspecified, flags: []) {
                    guard current == self.generation else { return }
                    let stale = found.keys.filter { !seen.contains($0) }
                    guard !stale.isEmpty else { return }
                    stale.forEach { found.removeValue(forKey: $0) }
                    self.publish(found)
                }
            }

            browser.stateUpdateHandler = { state in
                if case .failed = state {
                    // 权限被拒或网络不可用时静默失败，界面显示空状态
                }
            }
            browser.start(queue: self.queue)
        }
    }

    private func publish(_ found: [String: DiscoveredPeer]) {
        let peers = found.values.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
        let callback = onUpdate
        DispatchQueue.main.async { callback?(peers) }
    }

    private static func hostString(_ host: NWEndpoint.Host) -> String {
        switch host {
        case .ipv4(let address):
            return String(describing: address)
        case .ipv6(let address):
            let text = String(describing: address)
            return text.contains("%") ? text.split(separator: "%", maxSplits: 1).first.map(String.init) ?? text : text
        case .name(let name, _):
            return name
        @unknown default:
            return ""
        }
    }
}
