"""Susun screenshot in-game (run/screenshots/anim_PREFIX_*.png) berdampingan dengan style frame Art Bible.

Hasil: run/screenshots/compare_PREFIX.png (kiri in-game, kanan target) dan statistik luma per frame
(Art Bible: +-75-85% piksel di bawah 12% luma di luar letterbox)."""
import sys
import os
import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SHOTS = os.path.join(ROOT, "run", "screenshots")
FRAMES = os.path.join(ROOT, "docs", "artbible", "frames")
NAMES = [("pov", "POV"), ("liar", "LIAR!"), ("rt", "Roulette"), ("win", "Pemenang")]


def dark_share(img, letterbox=False):
    a = np.asarray(img.convert("L"), np.float32) / 255
    if letterbox:
        bar = int(a.shape[0] * 0.128)
        a = a[bar:a.shape[0] - bar]
    return float((a < 0.12).mean())


def main(prefix):
    w, h = 960, 540
    sheet = Image.new("RGB", (w * 2 + 30, (h + 40) * len(NAMES)), (12, 12, 14))
    d = ImageDraw.Draw(sheet)
    for i, (key, label) in enumerate(NAMES):
        shot = os.path.join(SHOTS, f"anim_{prefix}_{key}.png")
        target = os.path.join(FRAMES, f"sf_{key}.png")
        y = i * (h + 40)
        if os.path.exists(shot):
            im = Image.open(shot).convert("RGB")
            sheet.paste(im.resize((w, h)), (0, y + 30))
            d.text((4, y + 8), f"{label} in-game  dark<12%: {dark_share(im):.0%}", fill=(230, 220, 200))
        if os.path.exists(target):
            tg = Image.open(target).convert("RGB")
            sheet.paste(tg.resize((w, h)), (w + 30, y + 30))
            d.text((w + 34, y + 8), f"{label} target  dark<12%: {dark_share(tg, key in ('liar', 'rt', 'win')):.0%}",
                   fill=(201, 160, 74))
    out = os.path.join(SHOTS, f"compare_{prefix}.png")
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "cmp")
