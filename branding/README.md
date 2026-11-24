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

## 透明策略（重要）

SVG 源文件**不带背景**。各平台按「该处能否透出桌面」决定是否铺白，不要一律用同一套：

| 场景 | 处理 | 原因 |
|---|---|---|
| macOS `AppIcon.icns` | **白色**满画布 | 用户指定：Dock 里白底更整、不显空洞 |
| Windows `AppIcon.ico`（exe/任务栏/托盘） | 透明（32bpp alpha） | 窗口图标与任务栏支持 alpha |
| 程序内品牌标 `BrandMark.png` | 透明 | 落在应用自己的背景上 |
| 网页头部 logo / 浏览器标签页图标 `/brand.png` | 透明 | 页面深浅色自适应 |
| Android 自适应图标**背景层** | 白色 `#FFFFFF` | 自适应图标背景必须不透明，系统会裁切成各种形状 |

也就是说：**macOS 图标与 Android 自适应图标背景层用白色，其余全部透明。**

## 资产文件

- `export/EZShare-final.svg` — 权威矢量源文件（512×512 viewBox，透明背景）
- `export/EZShare-final.png` — 1024×1024 RGBA 成品（透明）
- `export/EZShare-final-180.png` — 180px 预览（检查小尺寸可读性）
- `export/BrandMark.png` — 256×256 程序内品牌标（透明）
- `export/brand-web.png` — 160×160 网页 logo / favicon（透明）
- `export/AppIcon.icns` / `export/AppIcon.ico` — macOS / Windows 图标

## 重新生成

```bash
python3 branding/render-icons.py
```

脚本会自动：渲染 SVG（双背景反解 alpha，绕过 `sips` 把透明压成白色的问题）→ 生成 PNG/ICNS/ICO
→ 分发到 `android/app/src/main/assets/brand.png`、`windows/EZShare/Assets/`、`android/.../mipmap-*`。

## 应用到项目

- macOS：`scripts/build.sh` 复制 `export/AppIcon.icns` 与 `BrandMark.png`、`brand-web.png` 进 App Resources
- Android：`res/mipmap-anydpi-v26/ic_launcher.xml`（白底）+ `res/drawable/ic_launcher_foreground.xml`（透明前景）
- Windows：`EZShare.csproj` 的 `ApplicationIcon` 与 `EmbeddedResource`（AppIcon.ico / brand.png / BrandMark.png）
