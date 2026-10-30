"""Tekstur placeholder Fase 2, dibuat prosedural (buatan sendiri, lihat CREDITS.txt).

    python tools/make_textures.py   -> tulis ke src/main/resources/assets/liarsbar/textures

Huruf kartu memakai font bawaan Pillow (Aileron, CC0). Tekstur final (gaya
game asli) menyusul di fase presentasi.
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "liarsbar", "textures")


def save(img, *path):
    out = os.path.join(ROOT, *path)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print("wrote", os.path.relpath(out))


def wood(size, base, planks, seed, grain=0.18):
    """Papan kayu horizontal: tiap papan sedikit beda warna, serat bergelombang, garis sambungan gelap."""
    rnd = random.Random(seed)
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    plank_h = h / planks
    shades = [rnd.uniform(0.88, 1.08) for _ in range(planks)]
    phases = [rnd.uniform(0, 6.28) for _ in range(planks)]
    for y in range(h):
        p = min(planks - 1, int(y / plank_h))
        edge = (y % plank_h) < 1.0
        for x in range(w):
            wave = math.sin(x / w * 6.28 * 2 + phases[p] + math.sin(y * 0.9 + p) * 0.6)
            streak = math.sin(y * 2.3 + wave * 2.0 + p * 1.7) * 0.5 + 0.5
            k = shades[p] * (1 - grain + grain * streak) * (0.97 + rnd.random() * 0.06)
            if edge:
                k *= 0.55
            px[x, y] = tuple(max(0, min(255, int(c * k))) for c in base)
    return img


def smoke():
    """Kepulan asap lembut: noise berlapis dikali gradien radial (alpha), abu-abu terang."""
    size = 64
    rnd = random.Random(11)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    blobs = [(rnd.uniform(18, 46), rnd.uniform(18, 46), rnd.uniform(8, 16)) for _ in range(9)]
    for y in range(size):
        for x in range(size):
            r = math.hypot(x - 31.5, y - 31.5) / 31.5
            base = max(0.0, 1 - r) ** 1.6
            n = sum(math.exp(-((x - bx) ** 2 + (y - by) ** 2) / (2 * br * br)) for bx, by, br in blobs)
            a = min(1.0, base * (0.45 + 0.35 * n)) * (0.85 + 0.15 * rnd.random())
            g = int(225 + 20 * rnd.random())
            px[x, y] = (g, g, g, int(a * 255))
    return img.filter(ImageFilter.GaussianBlur(1.2))


def muzzle_flash():
    """Kilat moncong: inti putih kekuningan dengan lima lidah api runcing."""
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = size / 2
    for i in range(5):
        a = i * 2 * math.pi / 5 + 0.3
        tip = (c + math.cos(a) * 30, c + math.sin(a) * 30)
        l = (c + math.cos(a + 0.35) * 8, c + math.sin(a + 0.35) * 8)
        r = (c + math.cos(a - 0.35) * 8, c + math.sin(a - 0.35) * 8)
        d.polygon([l, tip, r], fill=(255, 170, 60, 230))
    for rad, col in ((14, (255, 200, 90, 240)), (9, (255, 236, 170, 255)), (5, (255, 255, 240, 255))):
        d.ellipse([c - rad, c - rad, c + rad, c + rad], fill=col)
    return img.filter(ImageFilter.GaussianBlur(1.0))


def lamp_shade():
    """Kap lampu bar: enamel hijau tua di luar, kuning pucat di dalam (atas/bawah tekstur)."""
    w, h = 32, 32
    rnd = random.Random(3)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    px = img.load()
    for y in range(h):
        for x in range(w):
            k = rnd.uniform(-8, 8)
            if y < h // 2:
                px[x, y] = (int(28 + k), int(58 + k), int(38 + k), 255)
            else:
                px[x, y] = (int(232 + k), int(214 + k), int(160 + k), 255)
    return img


def blood_splat(seed, size=128, drops=60, spray=True):
    """Cipratan darah: inti tak beraturan + tetesan memanjang ke satu arah (arah semburan = +x)."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = size / 2
    for _ in range(14):
        r = rnd.uniform(size * 0.05, size * 0.13)
        x = c + rnd.gauss(0, size * 0.06)
        y = c + rnd.gauss(0, size * 0.06)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(96, 6, 8, 235))
    for _ in range(drops):
        ang = rnd.gauss(0, 0.55) if spray else rnd.uniform(0, 6.283)
        dist = abs(rnd.gauss(0, size * 0.22)) + size * 0.08
        x = c + math.cos(ang) * dist
        y = c + math.sin(ang) * dist
        r = max(0.8, rnd.uniform(0.6, 3.6) * (1 - dist / size))
        # Tetesan memanjang searah semburan.
        ex = math.cos(ang) * r * 1.8
        ey = math.sin(ang) * r * 1.8
        d.ellipse([x - r - abs(ex) * 0.2, y - r, x + r + abs(ex), y + r], fill=(110, 8, 10, 220))
    img = img.filter(ImageFilter.GaussianBlur(0.8))
    # Kilap basah: titik terang kecil di inti.
    px = img.load()
    for y in range(size):
        for x in range(size):
            r, g, b, a = px[x, y]
            if a > 0:
                k = rnd.uniform(-10, 10)
                px[x, y] = (max(0, min(255, int(r + k))), g, b, min(255, int(a * 1.1)))
    return img


def blood_pool(seed, size=128):
    """Genangan darah yang menyebar pelan: tepi bergelombang, tengah lebih gelap."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    lobes = [(rnd.uniform(0, 6.28), rnd.uniform(0.02, 0.07), f) for f in (1, 2, 3, 5, 7, 11)]
    c = size / 2
    for y in range(size):
        for x in range(size):
            dx, dy = (x - c) / c, (y - c) / c
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            edge = 0.72 + sum(m * math.cos(f * a + p) for p, m, f in lobes)
            if r < edge:
                t = r / edge
                shade = int(70 + 30 * t)
                alpha = int(255 * min(1.0, (1 - t) * 6))
                px[x, y] = (shade, 4, 6, alpha)
    return img.filter(ImageFilter.GaussianBlur(1.0))


def leather():
    """Jok kulit merah tua yang aus, dengan jahitan di tepi."""
    w = h = 32
    rnd = random.Random(9)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    px = img.load()
    for y in range(h):
        for x in range(w):
            edge = min(x, y, w - 1 - x, h - 1 - y)
            k = rnd.uniform(-9, 9) + (-25 if edge == 0 else 0) + (14 if edge == 2 and (x + y) % 3 == 0 else 0)
            wear = 18 * math.exp(-((x - 16) ** 2 + (y - 17) ** 2) / 60)
            px[x, y] = (int(108 + k + wear), int(30 + k * 0.5 + wear * 0.4), int(28 + k * 0.5), 255)
    return img


def chair_wood():
    """Kayu kursi gelap mengilap: serat vertikal, tepi lebih gelap."""
    return wood((32, 64), (84, 44, 26), 2, seed=21, grain=0.22).transpose(Image.ROTATE_90)


def main():
    save(Image.new("RGBA", (4, 4), (255, 255, 255, 255)), "entity", "white.png")
    # Daun meja: kayu jingga kemerahan seperti meja di game asli (ditile tiap 2 blok).
    top = wood((128, 128), (178, 82, 48), 8, seed=7)
    top = top.filter(ImageFilter.SMOOTH)
    save(top, "entity", "table_top.png")
    save(wood((64, 64), (112, 52, 30), 4, seed=3, grain=0.25), "entity", "table_side.png")
    # Kartu: lihat tools/make_cards.py
    save(smoke(), "fx", "smoke.png")
    save(muzzle_flash(), "fx", "muzzle_flash.png")
    save(lamp_shade(), "entity", "lamp_shade.png")
    save(blood_splat(31), "fx", "blood_splat.png")
    save(blood_splat(47, spray=False), "fx", "blood_splat2.png")
    save(blood_pool(5), "fx", "blood_pool.png")
    save(leather(), "entity", "chair_leather.png")
    save(chair_wood(), "entity", "chair_wood.png")


if __name__ == "__main__":
    main()
