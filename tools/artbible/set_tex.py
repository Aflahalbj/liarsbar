"""Tekstur konsep untuk set: kaca jendela hujan (kota malam cyan) + poster berbingkai gaya potongan kertas."""
import sys, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
OUT = sys.argv[1]; random.seed(3); rng = np.random.default_rng(3)
W, H = 96, 112
img = np.zeros((H, W, 3), np.float32)
y = np.linspace(0, 1, H)[:, None]
img[:] = np.stack([0.02 + 0.06 * y, 0.10 + 0.20 * y, 0.16 + 0.26 * y], -1)  # langit kota memantul cyan
glow = Image.new("RGB", (W, H)); d = ImageDraw.Draw(glow)
for _ in range(14):  # bokeh lampu kota
    x, yy, r = random.randint(0, W), random.randint(H // 3, H), random.randint(3, 9)
    c = random.choice([(80, 200, 230), (60, 160, 210), (120, 230, 240), (230, 170, 90)])
    d.ellipse([x - r, yy - r, x + r, yy + r], fill=c)
glow = np.asarray(glow.filter(ImageFilter.GaussianBlur(4)), np.float32) / 255
img = img + glow * 0.55
for _ in range(220):  # tetes & alur hujan di kaca
    x = random.randint(0, W - 1); y0 = random.randint(0, H - 1); L = random.randint(2, 9)
    img[y0:y0 + L, x] += 0.10 + random.random() * 0.12
    img[min(H - 1, y0 + L), x] += 0.25
img = np.clip(img, 0, 1)
Image.fromarray((img * 255).astype(np.uint8)).resize((W, H), Image.NEAREST).save(os.path.join(OUT, "rain.png"))

PAPER, INK, RED, GOLD = (120, 108, 88), (20, 16, 14), (200, 30, 40), (190, 150, 70)
def poster(name, draw):
    im = Image.new("RGB", (24, 32), PAPER); d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 23, 31], outline=(60, 40, 30)); draw(d); im.save(os.path.join(OUT, name))
poster("poster_fight.png", lambda d: (d.rectangle([7, 6, 16, 14], fill=INK), d.rectangle([9, 15, 13, 22], fill=INK),
       d.rectangle([3, 25, 20, 27], fill=INK)))
poster("poster_ship.png", lambda d: (d.polygon([(4, 20), (20, 20), (17, 24), (7, 24)], fill=INK), d.polygon([(12, 5), (12, 19), (5, 19)], fill=INK),
       d.polygon([(13, 8), (13, 19), (19, 19)], fill=(90, 80, 70)), d.rectangle([3, 27, 20, 28], fill=INK)))
poster("poster_eye.png", lambda d: (d.ellipse([4, 9, 19, 19], fill=INK), d.ellipse([9, 11, 14, 17], fill=PAPER), d.rectangle([11, 13, 12, 15], fill=INK),
       d.rectangle([5, 24, 18, 25], fill=INK), d.rectangle([7, 27, 16, 28], fill=INK)))
print("ok")
