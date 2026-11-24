import SwiftUI
import AppKit

@main
enum Launcher {
    static func main() {
        if CommandLine.arguments.contains("--headless") {
            HeadlessRunner.run()
        } else {
            EZShareApp.main()
        }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    static weak var shared: AppDelegate?
    private var statusItem: NSStatusItem?
    private var menu: NSMenu?
    weak var model: AppModel?
    /// 主窗口引用：SwiftUI 会另建状态栏/菜单等窗口，靠 NSApp.windows.first 拿不准
    weak var mainWindow: NSWindow?

    override init() {
        super.init()
        AppDelegate.shared = self
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        // 关闭窗口只隐藏，不退出；服务继续在后台运行。
        false
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        NSApp.activate(ignoringOtherApps: true)
        setupMenuBar()
    }

    /// 由界面里的 WindowAccessor 回调，锁定真正的主窗口
    func attach(window: NSWindow) {
        guard mainWindow !== window else { return }
        mainWindow = window
        window.isReleasedWhenClosed = false
        window.delegate = self
    }

    private func setupMenuBar() {
        let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        if let button = item.button {
            if let url = Bundle.main.url(forResource: "BrandMark", withExtension: "png"),
               let image = NSImage(contentsOf: url) {
                image.size = NSSize(width: 18, height: 18)
                button.image = image
            } else {
                button.title = "EZ"
            }
            button.toolTip = "EZShare"
        }
        let menu = NSMenu()
        menu.addItem(withTitle: "显示 EZShare", action: #selector(toggleMainWindow(_:)), keyEquivalent: "")
        menu.addItem(withTitle: "启动 / 停止共享", action: #selector(toggleShare(_:)), keyEquivalent: "")
        menu.addItem(NSMenuItem.separator())
        menu.addItem(withTitle: "退出 EZShare", action: #selector(quitApp(_:)), keyEquivalent: "q")
        menu.delegate = self
        item.menu = menu
        self.menu = menu
        statusItem = item
    }

    @objc private func toggleMainWindow(_ sender: Any?) {
        guard let window = mainWindow ?? NSApp.windows.first(where: { $0.canBecomeMain }) else {
            // 窗口还没建好（启动瞬间点菜单），稍后再试
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { [weak self] in
                self?.toggleMainWindow(nil)
            }
            return
        }
        if window.isVisible {
            window.orderOut(nil)
            NSApp.setActivationPolicy(.accessory)
        } else {
            NSApp.setActivationPolicy(.regular)
            NSApp.activate(ignoringOtherApps: true)
            window.makeKeyAndOrderFront(nil)
        }
    }

    @objc private func toggleShare(_ sender: Any?) {
        model?.toggle()
    }

    @objc private func quitApp(_ sender: Any?) {
        NSApp.terminate(nil)
    }
}

extension AppDelegate: NSWindowDelegate {
    /// 点红叉 / Cmd+W 只隐藏窗口，不销毁，保证托盘能再叫出来
    func windowShouldClose(_ sender: NSWindow) -> Bool {
        sender.orderOut(nil)
        NSApp.setActivationPolicy(.accessory)
        return false
    }
}

extension AppDelegate: NSMenuDelegate {
    func menuNeedsUpdate(_ menu: NSMenu) {
        menu.items.first?.title = (mainWindow?.isVisible ?? false) ? "隐藏 EZShare" : "显示 EZShare"
    }
}

/// 把 SwiftUI 的主窗口交给 AppDelegate，用于托盘的显示 / 隐藏
struct WindowAccessor: NSViewRepresentable {
    func makeNSView(context: Context) -> NSView {
        let view = NSView(frame: .zero)
        DispatchQueue.main.async {
            if let window = view.window {
                AppDelegate.shared?.attach(window: window)
            }
        }
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {}
}

struct EZShareApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup("EZShare") {
            ContentView()
                .environmentObject(model)
                .background(WindowAccessor())
                .onAppear { AppDelegate.shared?.model = model }
        }
        .windowStyle(.hiddenTitleBar)
        .windowResizability(.contentMinSize)
        .commands {
            CommandGroup(replacing: .newItem) {}
            CommandMenu("共享") {
                Button(model.isRunning ? "停止共享" : "启动共享") {
                    model.toggle()
                }
                .keyboardShortcut("r", modifiers: .command)

                Button("在浏览器中打开") {
                    if let url = model.primaryURL { model.openInBrowser(url) }
                }
                .keyboardShortcut("o", modifiers: .command)
                .disabled(model.primaryURL == nil)

                Button("复制访问地址") {
                    if let url = model.primaryURL { model.copy(url) }
                }
                .keyboardShortcut("c", modifiers: [.command, .shift])
                .disabled(model.primaryURL == nil)

                Divider()

                Button("在访达中显示共享目录") {
                    model.revealInFinder()
                }
            }
        }
    }
}

/// 命令行模式：便于在终端或 CI 中直接验证 HTTP 服务。
enum HeadlessRunner {
    private static var keepAlive: HTTPServer?

    static func run() {
        var root = FileManager.default.currentDirectoryPath
        var port: UInt16 = 8080
        var requireCode = false
        var code = ""

        let arguments = CommandLine.arguments
        var index = 1
        while index < arguments.count {
            switch arguments[index] {
            case "--root" where index + 1 < arguments.count:
                root = arguments[index + 1]
                index += 1
            case "--port" where index + 1 < arguments.count:
                port = UInt16(arguments[index + 1]) ?? port
                index += 1
            case "--code" where index + 1 < arguments.count:
                requireCode = true
                code = arguments[index + 1]
                index += 1
            default:
                break
            }
            index += 1
        }

        let settings = ShareSettings(
            rootPath: root,
            allowUpload: true,
            allowDelete: true,
            requireCode: requireCode,
            accessCode: code
        )
        let server = HTTPServer(router: Router(config: ConfigStore(settings)))
        server.onLog = { log in
            print("[\(log.status)] \(log.method) \(log.path) \(log.bytes)B \(log.peer)")
        }
        server.onState = { state in
            switch state {
            case .running(let port):
                print("EZShare listening on http://0.0.0.0:\(port) root=\(root)")
            case .failed(let message):
                print("EZShare failed: \(message)")
                exit(1)
            default:
                break
            }
        }
        keepAlive = server
        server.start(preferredPort: port)
        RunLoop.main.run()
    }
}
