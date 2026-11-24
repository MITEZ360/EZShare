# EZ 家族图标系统

## 最终稿（用户指定）

`export/EZShare-final.svg` 是 EZShare 的唯一权威源文件，PNG 由它渲染生成。不再使用 Swift/CoreGraphics 渲染器。

### 结构（512 viewBox）

1. **底层三张斜切卡片**，各自带深色模糊投影，依次错位叠放：
   - 蓝紫 `#4F7CFF → #7C5CFF`（右上）
   - 青 `#22D3EE → #06B6D4`（左侧）
   - 暖色 `#FB923C → #F472B6`（下方，斜向渐变）
2. **白色圆角方卡** `x=116 y=116 w=280 h=280 rx=64`，带投影，压在三色卡之上。
3. **分享符号**：蓝紫渐变三点两线，尖端朝左；左中节点 `(204,256)`，右上 `(308,196)`，右下 `(308,316)`；整体 `translate(-8,-8)`。
4. **右下角深色圆形徽章** `translate(310,310)`，渐变 `#4338CA → #312E81 → #1E1B4B`，内嵌三条白色圆头横线。这是 EZ 系列的家族标识位。

### 家族化方法

后续 EZ 系列软件沿用同一套结构：保留三色斜切卡 + 白色圆卡 + 右下角深色徽章，只替换白色卡片内的功能符号和徽章内的字符。配色保持一致即可形成系列辨识。

## 色板

| 用途 | 色值 |
|---|---|
| 蓝紫 | `#4F7CFF` → `#7C5CFF` |
| 青 | `#22D3EE` → `#06B6D4` |
| 暖 | `#FB923C` → `#F472B6` |
| 徽章深蓝 | `#4338CA` → `#312E81` → `#1E1B4B` |
| 投影 | `#0F172A` opacity 0.22–0.44 + Gaussian blur |

## 资产文件

- `export/EZShare-final.svg` — 权威矢量源文件（512×512 viewBox）
- `export/EZShare-final.png` — 渲染成品 1024×1024（RGBA）
- `export/EZShare-final-180.png` — 180px 预览（检查小尺寸可读性）

## 重新渲染

```bash
cd /Volumes/Mac-SSD-1/Dev/Project/EZLive00/EZShare/branding/export
sips -s format png -z 1024 1024 EZShare-final.svg --out EZShare-final.png
sips -z 180 180 EZShare-final.png --out EZShare-final-180.png
```

## 应用到项目

当前仅为设计稿，未替换三端图标。应用时需同步：

- macOS：`tools/make-icon.swift` 生成的 `build/Resources/AppIcon.icns`
- Android：`android/app/src/main/res/drawable/ic_launcher.xml`
- Windows：`EZShare.csproj` 的 `<ApplicationIcon>`
