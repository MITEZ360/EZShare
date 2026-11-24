#!/usr/bin/env python3
"""从权威 SVG 渲染 EZShare 图标资产（保留透明通道）。

为什么需要这个脚本：
  macOS 自带的 sips 渲染 SVG 时会把透明区域压成白色，无法直接得到透明 PNG。
  这里用「双背景合成 + 反解 alpha」的办法还原真实 alpha：
    同一画面分别铺白底与绿幕底各渲染一次，
      Cw = a*F + (1-a)*255
      Cm = a*F + (1-a)*0
    相减即可解出 a，再回推 F。模糊投影、抗锯齿都是线性合成，同样适用。

产物（branding/export/）：
  EZShare-final.png        1024×1024 RGBA（透明背景，权威位图）
  EZShare-final-180.png    180×180 预览
  BrandMark.png            256×256 程序内品牌标（透明）
  AppIcon.icns             macOS（透明）
  AppIcon.ico              Windows（32bpp alpha，透明）
  android/res/mipmap-*/    Android 传统图标 PNG（透明）
"""

import os
import shutil
import struct
import subprocess
import sys
import tempfile
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SVG_PATH = os.path.join(ROOT, "branding/export/EZShare-final.svg")
EXPORT = os.path.join(ROOT, "branding/export")
ANDROID_RES = os.path.join(ROOT, "android/app/src/main/res")


# --------------------------------------------------------------------------
# 渲染：SVG -> BMP（sips）
# --------------------------------------------------------------------------
def render_bmp(svg_text, size, bg_hex, tmpdir, tag):
    """把 SVG 铺上指定底色渲染成 BMP，返回 (width, height, BGRA bytes)。"""
    body = svg_text.replace('width="512" height="512"', f'width="{size}" height="{size}"', 1)
    bg = f'<rect x="0" y="0" width="512" height="512" fill="{bg_hex}"/>'
    idx = body.index(">") + 1
    body = body[:idx] + bg + body[idx:]
    svg_file = os.path.join(tmpdir, f"{tag}.svg")
    bmp_file = os.path.join(tmpdir, f"{tag}.bmp")
    with open(svg_file, "w") as f:
        f.write(body)
    subprocess.run(["sips", "-s", "format", "bmp", svg_file, "--out", bmp_file],
                   check=True, capture_output=True)
    with open(bmp_file, "rb") as f:
        data = f.read()
    offset = struct.unpack("<I", data[10:14])[0]
    width = struct.unpack("<i", data[18:22])[0]
    height = struct.unpack("<i", data[22:26])[0]
    bpp = struct.unpack("<H", data[28:30])[0]
    if bpp != 32:
        raise SystemExit(f"意外的 BMP 位深 {bpp}")
    top_down = height < 0
    height = abs(height)
    return width, height, top_down, data[offset:]


def bmp_to_rgba(buf, width, height, top_down):
    """BMP(32bpp, BGRA) -> 顶行优先的 RGBA bytes。"""
    out = bytearray(width * height * 4)
    stride = width * 4
    for y in range(height):
        src_y = y if top_down else height - 1 - y
        row = buf[src_y * stride:(src_y + 1) * stride]
        dst = y * stride
        out[dst:dst + stride:4] = row[2::4]      # R
        out[dst + 1:dst + stride:4] = row[1::4]  # G
        out[dst + 2:dst + stride:4] = row[0::4]  # B
        out[dst + 3:dst + stride:4] = b"\xff" * width
    return out


# --------------------------------------------------------------------------
# 反解 alpha
# --------------------------------------------------------------------------
def unmatte(white, green, width, height):
    """white/green 均是合成到不透明底色后的 RGBA，返回还原的真实 RGBA。"""
    out = bytearray(width * height * 4)
    n = width * height
    for i in range(n):
        o = i * 4
        wr, wg, wb = white[o], white[o + 1], white[o + 2]
        mr, mg, mb = green[o], green[o + 1], green[o + 2]
        # 绿幕底色为 (0,255,0)：R/B 两通道差值都能反映 (1-a)，取均值降噪
        inv_a = ((wr - mr) + (wb - mb)) / 510.0
        if inv_a <= 0.0015:
            a = 255
        elif inv_a >= 0.9985:
            a = 0
        else:
            a = int(round((1.0 - inv_a) * 255))
        if a == 0:
            out[o:o + 4] = b"\x00\x00\x00\x00"
            continue
        k = (1.0 - a / 255.0) * 255.0
        out[o] = max(0, min(255, int(round((wr - k) / (a / 255.0)))))
        out[o + 1] = max(0, min(255, int(round((wg - k) / (a / 255.0)))))
        out[o + 2] = max(0, min(255, int(round((wb - k) / (a / 255.0)))))
        out[o + 3] = a
    return out


# --------------------------------------------------------------------------
# 输出：PNG / ICNS / ICO
# --------------------------------------------------------------------------
def write_png(path, width, height, rgba):
    raw = bytearray()
    stride = width * 4
    for y in range(height):
        raw.append(0)
        raw += rgba[y * stride:(y + 1) * stride]
    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def resize(src, dst, size):
    subprocess.run(["sips", "-z", str(size), str(size), src, "--out", dst],
                   check=True, capture_output=True)


def make_icns(png_1024, tmpdir, out):
    iconset = os.path.join(tmpdir, "AppIcon.iconset")
    shutil.rmtree(iconset, ignore_errors=True)
    os.makedirs(iconset)
    for size in (16, 32, 128, 256, 512):
        resize(png_1024, os.path.join(iconset, f"icon_{size}x{size}.png"), size)
        resize(png_1024, os.path.join(iconset, f"icon_{size}x{size}@2x.png"), size * 2)
    subprocess.run(["iconutil", "-c", "icns", iconset, "-o", out], check=True)


def downscale(rgba, width, height, size):
    """按面积平均缩放（仅用于生成小尺寸 ICO 位图，质量足够）。"""
    out = bytearray(size * size * 4)
    scale = width / size
    for y in range(size):
        y0 = int(y * scale)
        y1 = max(y0 + 1, int((y + 1) * scale))
        for x in range(size):
            x0 = int(x * scale)
            x1 = max(x0 + 1, int((x + 1) * scale))
            r = g = b = a = n = 0
            for sy in range(y0, y1):
                base = (sy * width + x0) * 4
                for sx in range(x1 - x0):
                    o = base + sx * 4
                    pa = rgba[o + 3]
                    r += rgba[o] * pa
                    g += rgba[o + 1] * pa
                    b += rgba[o + 2] * pa
                    a += pa
                    n += 1
            o = (y * size + x) * 4
            if a == 0:
                out[o:o + 4] = b"\x00\x00\x00\x00"
            else:
                out[o] = min(255, r // a)
                out[o + 1] = min(255, g // a)
                out[o + 2] = min(255, b // a)
                out[o + 3] = min(255, a // n)
    return out


def dib_entry(rgba, size):
    """ICO 内嵌的 32bpp DIB（XP 以来所有版本都支持，.NET Framework 兼容性最好）。"""
    header = struct.pack("<IiiHHIIiiII", 40, size, size * 2, 1, 32, 0,
                         size * size * 4, 0, 0, 0, 0)
    pixels = bytearray()
    for y in range(size - 1, -1, -1):          # DIB 自下而上
        row = rgba[y * size * 4:(y + 1) * size * 4]
        for x in range(size):
            o = x * 4
            pixels += bytes((row[o + 2], row[o + 1], row[o], row[o + 3]))
    mask_stride = ((size + 31) // 32) * 4
    mask = b"\x00" * (mask_stride * size)      # 透明度由 alpha 决定，AND 掩码全 0
    return header + bytes(pixels) + mask


def make_ico(rgba, width, height, tmpdir, out, sizes=(16, 24, 32, 48, 64, 128, 256)):
    entries = []
    for size in sizes:
        small = downscale(rgba, width, height, size)
        if size >= 256:
            tmp = os.path.join(tmpdir, f"ico-{size}.png")
            write_png(tmp, size, size, small)
            with open(tmp, "rb") as f:
                entries.append((size, f.read()))
        else:
            entries.append((size, dib_entry(small, size)))
    header = struct.pack("<HHH", 0, 1, len(entries))
    offset = 6 + 16 * len(entries)
    directory = b""
    payload = b""
    for size, data in entries:
        dim = 0 if size >= 256 else size
        directory += struct.pack("<BBBBHHII", dim, dim, 0, 0, 1, 32, len(data), offset)
        payload += data
        offset += len(data)
    with open(out, "wb") as f:
        f.write(header + directory + payload)


# --------------------------------------------------------------------------
def main():
    with open(SVG_PATH) as f:
        svg = f.read()
    size = 1024
    with tempfile.TemporaryDirectory() as tmpdir:
        print("[icons] 渲染白底 / 绿幕底 ...")
        w1, h1, td1, b1 = render_bmp(svg, size, "#FFFFFF", tmpdir, "white")
        w2, h2, td2, b2 = render_bmp(svg, size, "#00FF00", tmpdir, "key")
        if (w1, h1) != (w2, h2):
            raise SystemExit("两遍渲染尺寸不一致")
        white = bmp_to_rgba(b1, w1, h1, td1)
        key = bmp_to_rgba(b2, w2, h2, td2)

        print("[icons] 反解 alpha ...")
        rgba = unmatte(white, key, w1, h1)

        master = os.path.join(EXPORT, "EZShare-final.png")
        write_png(master, w1, h1, rgba)
        print(f"[icons] {master}")

        resize(master, os.path.join(EXPORT, "EZShare-final-180.png"), 180)
        resize(master, os.path.join(EXPORT, "BrandMark.png"), 256)

        make_icns(master, tmpdir, os.path.join(EXPORT, "AppIcon.icns"))
        print("[icons] AppIcon.icns")

        make_ico(rgba, w1, h1, tmpdir, os.path.join(EXPORT, "AppIcon.ico"))
        print("[icons] AppIcon.ico")

        # 分发到各端
        brand_mark = os.path.join(EXPORT, "BrandMark.png")
        web_mark = os.path.join(EXPORT, "brand-web.png")
        resize(master, web_mark, 160)  # 网页头部 34px 显示 + 浏览器标签页图标
        for target in (
            os.path.join(ANDROID_RES, "drawable-nodpi/brand_mark.png"),
            os.path.join(ROOT, "windows/EZShare/Assets/BrandMark.png"),
        ):
            os.makedirs(os.path.dirname(target), exist_ok=True)
            shutil.copyfile(brand_mark, target)
        for target in (
            os.path.join(ROOT, "android/app/src/main/assets/brand.png"),
            os.path.join(ROOT, "windows/EZShare/Assets/brand.png"),
        ):
            os.makedirs(os.path.dirname(target), exist_ok=True)
            shutil.copyfile(web_mark, target)
        shutil.copyfile(os.path.join(EXPORT, "AppIcon.ico"),
                        os.path.join(ROOT, "windows/EZShare/Assets/AppIcon.ico"))
        print("[icons] 已分发 BrandMark / brand-web / AppIcon.ico")

        # Android 传统图标（API < 26）：平台支持 alpha，保持透明
        densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
        for dpi, px in densities.items():
            folder = os.path.join(ANDROID_RES, f"mipmap-{dpi}")
            os.makedirs(folder, exist_ok=True)
            resize(master, os.path.join(folder, "ic_launcher.png"), px)
        print("[icons] android mipmap-*/ic_launcher.png")


if __name__ == "__main__":
    main()
