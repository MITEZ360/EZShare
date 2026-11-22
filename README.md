# EZShare

局域网文件与文本共享工具，提供 macOS 与 Android 原生客户端。Mac 端用 Swift + SwiftUI + Network.framework，零第三方依赖，App 体积约 2 MB；Android 端用 Kotlin + Compose，纯 Java 网络库手写 HTTP 服务，无服务端框架。两端可通过 Bonjour / mDNS 互相发现。

## 功能

- 一键启动/停止 HTTP 共享服务，端口被占用时自动顺延
- 文件：目录浏览、下载（支持 Range 断点）、拖拽/多文件上传（带进度）、新建文件夹、删除（进废纸篓，可恢复）
- 文本：网页端或 Mac 原生界面均可发送；任何设备可查看、一键复制回剪贴板、下载为 .txt；持久化到 Application Support，跨重启保留
- Mac 原生「共享内容」面板：浏览文件/文本、添加文件（文件选择或拖入）、发送文本、复制、删除
- 局域网地址自动检测，一键复制 / 打开，二维码供扫码
- 局域网设备发现：Bonjour / mDNS 发布 `_ezshare._tcp`，启动时自动发现一次 + 手动刷新，Mac 与 Android 互相可见
- 可选 6 位字母数字混合访问码（Cookie 会话）；上传、删除可独立开关
- Android 端：与 Mac 端同等功能（目录授权共享、文件收发、文本收发、访问码、二维码、后台前台服务），网页端复用同一套 CSS/JS
- 配置持久化：Mac 用 UserDefaults，Android 用 SharedPreferences

## 构建

只需 Command Line Tools（无需安装 Xcode / SwiftPM）：

```bash
./scripts/build.sh            # 构建本机架构 build/EZShare.app
./scripts/build.sh universal  # 构建 arm64 + x86_64 通用二进制
open build/EZShare.app
```

系统要求：macOS 13+。

## Android 构建（GitHub Actions，无需本地 SDK）

Android 项目在 `android/`，本机不需要安装 Android SDK / Gradle。推送到 `main` 后由 GitHub Actions 自动构建：

- 工作流：`.github/workflows/android.yml`
- 构建产物：Actions 页面对应运行的 `EZShare-Android` artifact，解压得到 `app-debug.apk`
- 也可在 Actions 页面点 `Run workflow` 手动触发

首次安装需允许「安装未知来源应用」。Android 端通过系统文件授权（SAF）选择共享目录；为让服务在后台持续运行，会显示常驻通知。

## 命令行模式

App 内置 headless 服务，便于脚本或 CI 验证：

```bash
./build/EZShare.app/Contents/MacOS/EZShare --headless --root ~/Downloads --port 8080 [--code K7mQ2x]
```

## 关于本机的工具链兼容性处理

本机 Command Line Tools 存在两个已知问题，构建脚本已自动处理（无需 sudo、不改系统文件）：

1. `/Library/Developer/CommandLineTools/usr/include/swift/` 中残留旧版 `module.modulemap`，与 `bridging.modulemap` 重复定义 `SwiftBridging`，导致任何 Swift 编译失败；
2. 由此引发的连锁 "SDK is not supported by the compiler" 报错。

`scripts/build.sh` 通过 `-vfsoverlay` 把残留文件映射为空文件绕过。若日后重装 CLT 后问题消失，脚本会自动检测并跳过该修复。

彻底修复本机 CLT（可选，需 sudo）：

```bash
sudo rm /Library/Developer/CommandLineTools/usr/include/swift/module.modulemap
```

## 安全说明

- 服务只读暴露共享目录内的文件；路径解析禁止 `..` 越界，删除操作仅限共享目录内部且进废纸篓
- Web 页面内置 XSS 转义；会话 Cookie 为 HttpOnly
- 仅建议在可信局域网内使用；如需防护请开启访问码

## 目录结构

```
Sources/EZShare/
├── EZShareApp.swift      # App 入口 + headless 模式
├── AppModel.swift        # 状态与配置持久化
├── Core/                 # 路径解析、网卡检测、MIME、文本存储、配置存储、Bonjour 发现
├── Net/                  # HTTP 服务、路由、网页（内嵌 CSS/JS，无外部资源）
└── Views/                # SwiftUI 界面
android/app/src/main/
├── java/com/ezshare/android/
│   ├── MainActivity.kt   # Compose 界面
│   └── server/           # HTTP 服务、路由、SAF 文件访问、NSD 发现、前台服务
└── assets/               # 与 Mac 端共用的 web.css / web.js
.github/workflows/android.yml   # 云端构建 APK
tools/make-icon.swift     # 图标生成器
scripts/build.sh          # Mac 构建打包脚本
packaging/Info.plist
```
