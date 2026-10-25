#!/usr/bin/env python3
"""Generate the Android launcher / splash icon set for the Parent App.

Source artwork lives in icon.zip (an Android Studio "image asset" export with
legacy mipmap-*dpi/ic_launcher(_round).webp files).  This script:

  1. copies the legacy square + round launcher icons into app/src/main/res,
  2. builds adaptive-icon layers (foreground / monochrome) by extracting the
     white glyph from the provided round icon, so the adaptive icon uses the
     brand teal as its background and stays sharp on every density,
  3. renders the splash logo at native densities.

Run from the repository root:  python3 tools/make_icons.py
"""
import os
import zipfile
from io import BytesIO

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")
ZIP = os.path.join(ROOT, "icon.zip")

# Brand teal sampled from the supplied artwork.
BRAND = (0, 86, 119)

DENSITIES = {
    "mdpi": (48, 108, 96),
    "hdpi": (72, 162, 144),
    "xhdpi": (96, 216, 192),
    "xxhdpi": (144, 324, 288),
    "xxxhdpi": (192, 432, 384),
}
LEGACY_DIRS = {
    "mdpi": "mipmap-mdpi-v4",
    "hdpi": "mipmap-hdpi-v4",
    "xhdpi": "mipmap-xhdpi-v4",
    "xxhdpi": "mipmap-xxhdpi-v4",
    "xxxhdpi": "mipmap-xxxhdpi-v4",
}


def load_from_zip(name):
    with zipfile.ZipFile(ZIP) as zf:
        return Image.open(BytesIO(zf.read(name))).convert("RGBA")


def glyph_alpha(round_icon):
    """Return an RGBA image holding only the white glyph of the artwork.

    The source is white-on-teal, so a luminance ramp is a clean alpha mask and
    keeps the anti-aliased edges.  Pixels outside the brand disc (drop shadow)
    are dropped so the adaptive icon has no grey ring.
    """
    w, h = round_icon.size
    src = round_icon.load()
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dst = out.load()
    cx, cy = w / 2.0, h / 2.0
    inner = (min(w, h) / 2.0) * 0.94
    base_lum = 0.299 * BRAND[0] + 0.587 * BRAND[1] + 0.114 * BRAND[2]
    for y in range(h):
        for x in range(w):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            if dx * dx + dy * dy > inner * inner:
                continue
            r, g, b, a = src[x, y]
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            alpha = int(round(max(0.0, min(1.0, (lum - base_lum) / (255.0 - base_lum))) * a))
            if alpha:
                dst[x, y] = (255, 255, 255, alpha)
    return out


def fit(img, box, canvas):
    """Scale img to fit inside a box x box area and center it on a canvas."""
    scale = min(box / img.width, box / img.height)
    size = (max(1, round(img.width * scale)), max(1, round(img.height * scale)))
    scaled = img.resize(size, Image.LANCZOS)
    out = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    out.paste(scaled, ((canvas - size[0]) // 2, (canvas - size[1]) // 2), scaled)
    return out


def main():
    square = load_from_zip("mipmap-xxxhdpi-v4/ic_launcher.webp")
    round_icon = load_from_zip("mipmap-xxxhdpi-v4/ic_launcher_round.webp")
    glyph = glyph_alpha(round_icon)
    bbox = glyph.getbbox()
    glyph = glyph.crop(bbox)

    for density, (legacy, adaptive, splash) in DENSITIES.items():
        leg_dir = os.path.join(RES, "mipmap-" + density)
        os.makedirs(leg_dir, exist_ok=True)

        # 1. Legacy launcher icons straight from the supplied artwork.
        for target, src in (("ic_launcher.webp", "ic_launcher.webp"),
                            ("ic_launcher_round.webp", "ic_launcher_round.webp")):
            with zipfile.ZipFile(ZIP) as zf:
                data = zf.read("mipmap-%s-v4/%s" % (density, src))
            with open(os.path.join(leg_dir, target), "wb") as fh:
                fh.write(data)

        # 2. Adaptive icon layers: the glyph should live in the 66dp safe zone
        #    of the 108dp foreground, hence ~0.61 * canvas.
        fg = fit(glyph, adaptive * 0.60, adaptive)
        fg.save(os.path.join(leg_dir, "ic_launcher_foreground.png"))
        monochrome = fit(glyph, adaptive * 0.60, adaptive)
        monochrome.save(os.path.join(leg_dir, "ic_launcher_monochrome.png"))

        # 3. Splash logo: full round icon at native density (same look as the
        #    launcher, no upscaling artefacts).
        splash_dir = os.path.join(RES, "drawable-" + density)
        os.makedirs(splash_dir, exist_ok=True)
        round_icon.resize((splash, splash), Image.LANCZOS).save(
            os.path.join(splash_dir, "ic_splash_logo.png"))

    # Play Store / web asset reference kept in assets.
    assets = os.path.join(ROOT, "app/src/main/assets")
    os.makedirs(assets, exist_ok=True)
    store_icon = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    inner = square.resize((512, 512), Image.LANCZOS)
    store_icon.paste(inner, (0, 0), inner)
    store_icon.save(os.path.join(assets, "icon_512.png"))


if __name__ == "__main__":
    main()
