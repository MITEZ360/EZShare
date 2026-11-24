import SwiftUI
import AppKit

enum Palette {
    // 与 Android/Windows/Web 共用：近单色灰阶 + 单一 teal 强调色。
    static let accent = Color(red: 0.000, green: 0.761, blue: 0.604)
    static let accentDeep = Color(red: 0.031, green: 0.471, blue: 0.400)
    static let running = Color(red: 0.000, green: 0.761, blue: 0.604)
    static let warning = Color(red: 0.851, green: 0.576, blue: 0.106)
    static let danger = Color(red: 0.753, green: 0.224, blue: 0.169)
    static let idle = Color(nsColor: .secondaryLabelColor)

    static func background(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.118, green: 0.118, blue: 0.118)
                        : Color(red: 0.969, green: 0.969, blue: 0.969)
    }
    static func card(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.145, green: 0.145, blue: 0.145) : .white
    }
}

/// 品牌图标：使用与三端一致的 EZShare 新 logo（branding/export/EZShare-final.png）。
/// 资源未打包时回退到简化图形，避免 UI 出现空洞。
struct BrandMark: View {
    var size: CGFloat = 34

    private var logoImage: NSImage? {
        guard let url = Bundle.main.url(forResource: "BrandMark", withExtension: "png") else { return nil }
        return NSImage(contentsOf: url)
    }

    var body: some View {
        Group {
            if let image = logoImage {
                Image(nsImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
            } else {
                RoundedRectangle(cornerRadius: size * 0.26, style: .continuous)
                    .fill(Palette.accent)
                    .overlay(
                        Image(systemName: "square.and.arrow.up")
                            .foregroundColor(.white)
                            .font(.system(size: size * 0.5, weight: .semibold))
                    )
            }
        }
        .frame(width: size, height: size)
    }
}

struct AppBackground: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        Palette.background(scheme).ignoresSafeArea()
    }
}

struct Card<Content: View>: View {
    let title: String
    let systemImage: String
    var trailing: AnyView?
    @ViewBuilder var content: () -> Content
    @Environment(\.colorScheme) private var scheme

    init(
        _ title: String,
        systemImage: String,
        trailing: AnyView? = nil,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.title = title
        self.systemImage = systemImage
        self.trailing = trailing
        self.content = content
    }

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: 16, style: .continuous)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 13) {
            HStack(spacing: 7) {
                Image(systemName: systemImage)
                    .font(.system(size: 12, weight: .regular))
                    .foregroundStyle(.secondary)
                Text(title)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.secondary)
                Spacer(minLength: 8)
                if let trailing { trailing }
            }
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(shape.fill(Palette.card(scheme)))
        .shadow(color: .black.opacity(scheme == .dark ? 0 : 0.05), radius: 5, x: 0, y: 2)
    }
}

/// 悬停反馈：背景色渐变 + 手型光标（macOS 13 兼容）。
struct HoverSurface<Content: View>: View {
    var corner: CGFloat = 8
    var base: CGFloat = 0
    var hover: CGFloat = 0.08
    var pointer: Bool = true
    @ViewBuilder var content: (Bool) -> Content

    @State private var hovering = false

    var body: some View {
        content(hovering)
            .background(
                RoundedRectangle(cornerRadius: corner, style: .continuous)
                    .fill(Color.primary.opacity(hovering ? hover : base))
            )
            .onHover { active in
                guard active != hovering else { return }
                hovering = active
                if pointer {
                    if active { NSCursor.pointingHand.push() } else { NSCursor.pointingHand.pop() }
                }
            }
            .animation(.easeOut(duration: 0.12), value: hovering)
    }
}

struct StatusPill: View {
    let text: String
    let color: Color

    var body: some View {
        HStack(spacing: 5) {
            Circle()
                .fill(color)
                .frame(width: 6, height: 6)
            Text(text)
                .font(.system(size: 12, weight: .medium))
                .lineLimit(1)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(Capsule().fill(color.opacity(0.10)))
        .foregroundStyle(color)
        .animation(.easeInOut(duration: 0.25), value: color)
    }
}

struct PrimaryActionButton: View {
    let isRunning: Bool
    let isBusy: Bool
    let action: () -> Void

    @State private var hovering = false

    private var label: String {
        if isBusy { return "处理中…" }
        return isRunning ? "停止共享" : "启动共享"
    }

    private var symbol: String {
        if isBusy { return "hourglass" }
        return isRunning ? "stop.fill" : "play.fill"
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: symbol)
                    .font(.system(size: 9.5, weight: .bold))
                Text(label)
                    .font(.system(size: 12.5, weight: .semibold))
            }
            .padding(.horizontal, 15)
            .frame(height: 32)
            .background(
                Capsule().fill(isRunning ? AnyShapeStyle(Color.primary.opacity(0.06)) : AnyShapeStyle(Palette.accent))
            )
            .foregroundStyle(isRunning ? Color.primary : Color.white)
        }
        .buttonStyle(.plain)
        .disabled(isBusy)
        .opacity(isBusy ? 0.7 : (hovering ? 0.9 : 1))
        .scaleEffect(hovering && !isBusy ? 1.02 : 1)
        .animation(.easeOut(duration: 0.12), value: hovering)
        .onHover { active in
            guard active != hovering else { return }
            hovering = active
            if active { NSCursor.pointingHand.push() } else { NSCursor.pointingHand.pop() }
        }
    }
}

struct MiniButton: View {
    let symbol: String
    var help: String = ""
    var spinsOnTap: Bool = false
    let action: () -> Void

    @State private var spinTurns = 0

    var body: some View {
        Button {
            if spinsOnTap {
                withAnimation(.easeInOut(duration: 0.55)) { spinTurns += 1 }
            }
            action()
        } label: {
            HoverSurface(corner: 6, hover: 0.09) { active in
                Image(systemName: symbol)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(active ? Color.primary : Color.secondary)
                    .frame(width: 24, height: 24)
                    .contentShape(Rectangle())
                    .rotationEffect(.degrees(Double(spinTurns) * 360))
            }
        }
        .buttonStyle(.plain)
        .help(help)
    }
}

struct SettingRow<Content: View>: View {
    let title: String
    var subtitle: String?
    @ViewBuilder var control: () -> Content

    var body: some View {
        HStack(alignment: .center, spacing: 10) {
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.system(size: 12.5, weight: .medium))
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.system(size: 10.5))
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
            Spacer(minLength: 8)
            control()
        }
    }
}

struct ValueChip: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.system(size: 11, weight: .medium))
            .foregroundStyle(.secondary)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(Capsule().fill(Color.primary.opacity(0.055)))
    }
}

struct SoftButton: View {
    let title: String
    var symbol: String?
    var spinsOnTap: Bool = false
    let action: () -> Void

    @State private var hovering = false
    @State private var spinTurns = 0

    var body: some View {
        Button {
            if spinsOnTap {
                withAnimation(.easeInOut(duration: 0.55)) { spinTurns += 1 }
            }
            action()
        } label: {
            HStack(spacing: 4) {
                if let symbol {
                    Image(systemName: symbol)
                        .font(.system(size: 9.5, weight: .semibold))
                        .rotationEffect(.degrees(Double(spinTurns) * 360))
                }
                Text(title)
                    .font(.system(size: 11.5, weight: .medium))
            }
            .padding(.horizontal, 9)
            .frame(height: 25)
            .background(
                RoundedRectangle(cornerRadius: 7, style: .continuous)
                    .fill(Color.primary.opacity(hovering ? 0.13 : 0.06))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 7, style: .continuous)
                    .strokeBorder(Color.primary.opacity(hovering ? 0.12 : 0.05), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .foregroundStyle(hovering ? Color.primary : Color.secondary)
        .animation(.easeOut(duration: 0.12), value: hovering)
        .onHover { active in
            guard active != hovering else { return }
            hovering = active
            if active { NSCursor.pointingHand.push() } else { NSCursor.pointingHand.pop() }
        }
    }
}
