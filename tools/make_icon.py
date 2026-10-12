"""Generates the launcher icon (adaptive vectors + legacy PNGs). Run from repo root: python3 tools/make_icon.py"""
import math, io, cairosvg
from PIL import Image
RES = "app/src/main/res"
BG, FG, ACC = "#0B6E4F", "#FFFFFF", "#F4C95D"
# crescent from two circles
cx, cy, R = 54, 33, 12.5; kx, ky, r = 59.5, 30, 10.5
d = math.hypot(kx - cx, ky - cy); a = (R*R - r*r + d*d) / (2*d); h = math.sqrt(R*R - a*a)
mx, my = cx + a*(kx-cx)/d, cy + a*(ky-cy)/d
p1 = (mx + h*(ky-cy)/d, my - h*(kx-cx)/d); p2 = (mx - h*(ky-cy)/d, my + h*(kx-cx)/d)
cres = f"M{p1[0]:.2f},{p1[1]:.2f}A{R},{R} 0 1 0 {p2[0]:.2f},{p2[1]:.2f}A{r},{r} 0 0 1 {p1[0]:.2f},{p1[1]:.2f}Z"
def rr(x, y, w, h, r=2.2):
    return f"M{x+r},{y}h{w-2*r}a{r},{r} 0 0 1 {r},{r}v{h-2*r}a{r},{r} 0 0 1 {-r},{r}h{-(w-2*r)}a{r},{r} 0 0 1 {-r},{-r}v{-(h-2*r)}a{r},{r} 0 0 1 {r},{-r}z"
keys = ""
for row, (y, n) in enumerate([(52, 5), (62, 4)]):
    w = 8.4; gap = 2.4; total = n*w + (n-1)*gap; x0 = 54 - total/2
    for i in range(n): keys += rr(x0 + i*(w+gap), y, w, 7.2)
keys += rr(34, 72, 40, 7.2)  # space bar
fg_paths = [(cres, ACC), (keys, FG)]
def svg(background=True, size=108):
    paths = "".join(f'<path d="{d}" fill="{c}"/>' for d, c in fg_paths)
    bg = f'<rect width="108" height="108" fill="{BG}"/>' if background else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{size}" height="{size}">{bg}{paths}</svg>'
def vec(paths, filled_bg=None):
    s = '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="108dp"\n    android:height="108dp"\n    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
    if filled_bg: s += f'  <path android:fillColor="{filled_bg}" android:pathData="M0,0h108v108h-108z"/>\n'
    for d, c in paths: s += f'  <path android:fillColor="{c}" android:pathData="{d}"/>\n'
    return s + '</vector>\n'
open(f"{RES}/drawable/ic_launcher_foreground.xml", "w").write(vec(fg_paths))
open(f"{RES}/drawable-v24/ic_launcher_background.xml", "w").write(vec([], BG))
import os
for f in ["drawable/ic_launcher_background.xml"]:
    if os.path.exists(f"{RES}/{f}"): open(f"{RES}/{f}", "w").write(vec([], BG))
# monochrome layer should be one colour: keep the same foreground (system tints it)
sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for dens, px in sizes.items():
    png = cairosvg.svg2png(bytestring=svg(True, px*108//72*1).encode(), output_width=px*108//72, output_height=px*108//72)
    im = Image.open(io.BytesIO(png)).convert("RGBA")
    # crop adaptive safe area (72/108) and apply rounded-square / circle masks
    off = (im.width - px) // 2; im = im.crop((off, off, off + px, off + px))
    for name, radius in (("ic_launcher", px*0.22), ("ic_launcher_round", px/2)):
        mask = Image.new("L", (px*4, px*4), 0)
        from PIL import ImageDraw
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, px*4-1, px*4-1), radius=radius*4, fill=255)
        mask = mask.resize((px, px), Image.LANCZOS)
        out = Image.new("RGBA", (px, px), (0, 0, 0, 0)); out.paste(im, (0, 0), mask)
        out.save(f"{RES}/mipmap-{dens}/{name}.png")
open("art/lafz/icon.svg", "w").write(svg(True, 512))
cairosvg.svg2png(bytestring=svg(True, 512).encode(), write_to="art/lafz/icon_512.png", output_width=512, output_height=512)
