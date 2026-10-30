"""Tekstur block bar (pixel art buatan sendiri; lihat CREDITS.txt).

    python tools/make_blocks.py          -> tulis ke src/main/resources/assets/liarsbar/textures/block
    python tools/make_blocks.py --sheet  -> juga contact sheet 8x di build/block_sheet.png

Palet mengikuti Art Bible: walnut #3A2416, felt #1E3B33, kuningan, tungsten #FFA75A, cyan jendela #5CC8F0,
hitam #07080B. Tanpa noise acak: semua pola dirancang (ramp 4-6 nada per material, serat dan detail
ditaruh dengan tangan atau dengan aturan yang deterministik). Merah tidak dipakai (aksen khusus LIAR!/DOR).
Arsitektur 16 px/blok (sama dengan dunia), properti 32 px/blok.
"""
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "liarsbar", "textures", "block")


def hx(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


def ramp(*cols):
    return [hx(c) for c in cols]


CLEAR = (0, 0, 0, 0)

# ------------------------------------------------------------------ palet
WALNUT = ramp("#140c07", "#1e120b", "#2b1a10", "#3a2416", "#4e3220", "#66432a", "#80573a")
FLOOR = ramp("#120b07", "#1a100a", "#24160e", "#2f1d12", "#3b2517", "#4a2f1d", "#5a3b26")
PAPER = ramp("#0a1714", "#0e1d19", "#132520", "#182e28", "#1e3830", "#254238")
GOLD_THREAD = hx("#3a3320")
BRASS = ramp("#2a1d0c", "#4a3414", "#6e4f22", "#9c7536", "#c99d55", "#ebc888", "#fbe9bf")
IRON = ramp("#0d0f12", "#16191d", "#20242a", "#2d3239", "#3c434c", "#525a64", "#6d7680")
TIN = ramp("#0d0b09", "#12100d", "#171410", "#1c1813", "#221d17", "#28221a", "#30291f")
BRICK = ramp("#1c0f0b", "#281510", "#341c15", "#41241a", "#4f2d21", "#5e382a")
MORTAR = ramp("#141414", "#1c1b1b", "#262525")
STONE = ramp("#15171a", "#1c1f23", "#25292e", "#30353b", "#3d434a", "#4d545c")
TAR = ramp("#0f0f11", "#161619", "#1e1e22", "#28282d")
LEATHER = ramp("#170b07", "#24120b", "#331a10", "#442416", "#57301d", "#6c3e26", "#84502f")
GREEN = ramp("#0a1612", "#10221c", "#173028", "#1e3b33", "#284b41", "#335a4e")
CREAM = ramp("#5c5546", "#867c66", "#ab9f84", "#cbbf9f", "#e3d8ba")
AMBER = ramp("#2a1406", "#4a250a", "#73400f", "#a35f1c", "#d4872e", "#f0aa4e", "#ffd28c")
GLASS_FROST = ramp("#2c3634", "#3d4a47", "#52615d", "#677a75", "#80938d", "#9db0a9")
BULB = hx("#ffe2b0")
TUNGSTEN = hx("#ffa75a")
CYAN = hx("#5cc8f0")
BLACK = hx("#07080b")


def new(w, h, fill=CLEAR):
    return Image.new("RGBA", (w, h), fill)


def put(img, x, y, c):
    w, h = img.size
    img.putpixel((x % w, y % h), c)


def get(img, x, y):
    w, h = img.size
    return img.getpixel((x % w, y % h))


def rect(img, x0, y0, x1, y1, c):
    """Inklusif."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, c)


def hline(img, x0, x1, y, c):
    rect(img, x0, y, x1, y, c)


def vline(img, x, y0, y1, c):
    rect(img, x, y0, x, y1, c)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3],)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def bevel(img, x0, y0, x1, y1, light, dark):
    """Tepi timbul: atas/kiri terang, bawah/kanan gelap."""
    hline(img, x0, x1, y0, light)
    vline(img, x0, y0, y1, light)
    hline(img, x0, x1, y1, dark)
    vline(img, x1, y0, y1, dark)


def save(img, name):
    os.makedirs(os.path.dirname(os.path.join(ROOT, name)), exist_ok=True)
    img.save(os.path.join(ROOT, name))
    OUT[name] = img


OUT = {}


def lcg(seed):
    """Urutan deterministik untuk memilih variasi dari daftar yang dirancang (bukan noise per piksel)."""
    s = seed & 0xFFFFFFFF
    while True:
        s = (s * 1103515245 + 12345) & 0x7FFFFFFF
        yield s >> 8


# ================================================================== 16 px: arsitektur


def floor():
    """Parket herringbone walnut 8x2: papan H di (2t+8s, 2t-8s), papan V di sebelah ujungnya."""
    img = new(16, 16)
    tones_h = [3, 4, 3, 3, 4, 3, 2, 4]
    tones_v = [4, 3, 3, 4, 3, 4, 2, 3]
    flecks = [5, 2, 6, 3, 1, 4, 6, 2]
    for t in range(8):
        ox, oy = 2 * t, 2 * t
        b = tones_h[t]
        for i in range(8):
            put(img, ox + i, oy, FLOOR[b + (1 if i == 0 else 0)] if i < 7 else FLOOR[b - 2])
            put(img, ox + i, oy + 1, FLOOR[b - 1] if i < 7 else FLOOR[b - 2])
        put(img, ox + flecks[t], oy, FLOOR[min(6, b + 1)])
        put(img, ox + (flecks[t] + 3) % 7, oy + 1, FLOOR[b - 2])
        vx, vy = 8 + 2 * t, 2 * t - 6
        b = tones_v[t]
        for j in range(8):
            put(img, vx, vy + j, FLOOR[b + (1 if j == 0 else 0)] if j < 7 else FLOOR[b - 2])
            put(img, vx + 1, vy + j, FLOOR[b - 1] if j < 7 else FLOOR[b - 2])
        put(img, vx, vy + flecks[(t + 3) % 8], FLOOR[min(6, b + 1)])
    save(img, "bar_floor.png")


DAMASK = [
    "m..............m",
    ".m............m.",
    "..m....hh....m..",
    ".......mm.......",
    "......m..m......",
    ".....m.mm.m.....",
    "....m.mhhm.m....",
    "...mm.mggm.mm...",
    "..m..mmhhmm..m..",
    "...m..m..m..m...",
    "....m..mm..m....",
    ".....mm..mm.....",
    "......m..m......",
    ".......mm.......",
    "..m....mm....m..",
    ".m............m.",
]


def damask(img, ox=0, oy=0, rows=range(16)):
    pal = {".": PAPER[2], "m": PAPER[4], "h": PAPER[5], "g": GOLD_THREAD}
    for y in rows:
        for x in range(16):
            put(img, ox + x, oy + y, pal[DAMASK[y][x]])
    # Emboss: bayangan satu piksel di kanan-bawah tiap garis motif.
    for y in rows:
        for x in range(16):
            if DAMASK[y][x] != "." and DAMASK[(y + 1) % 16][(x + 1) % 16] == "." and (y + 1) in rows:
                put(img, ox + x + 1, oy + y + 1, PAPER[1])
    # Garis vertikal halus tiap 8 px (sambungan gulungan kertas).
    for y in rows:
        if DAMASK[y][0] == ".":
            put(img, ox + 0, oy + y, PAPER[1])


def wallpaper():
    img = new(16, 16)
    damask(img)
    save(img, "bar_wallpaper.png")


def molding(img, y0, depth=4):
    """List walnut: highlight, badan, alur, dentil, bayangan."""
    hline(img, 0, 15, y0, WALNUT[6])
    hline(img, 0, 15, y0 + 1, WALNUT[4])
    for x in range(16):
        put(img, x, y0 + 2, WALNUT[5] if x % 4 < 2 else WALNUT[2])
    if depth > 3:
        hline(img, 0, 15, y0 + 3, WALNUT[3])
    hline(img, 0, 15, y0 + depth, WALNUT[0])


def wallpaper_top():
    img = new(16, 16)
    damask(img)
    molding(img, 0)
    save(img, "bar_wallpaper_top.png")


def wainscot():
    img = new(16, 16)
    # List kursi (chair rail) di atas.
    hline(img, 0, 15, 0, WALNUT[6])
    hline(img, 0, 15, 1, WALNUT[5])
    hline(img, 0, 15, 2, WALNUT[0])
    # Rangka (stile dan rail).
    rect(img, 0, 3, 15, 15, WALNUT[3])
    for x, y0, y1 in ((0, 4, 14), (15, 4, 14)):
        vline(img, x, y0, y1, WALNUT[2])
    # Panel timbul.
    rect(img, 3, 5, 12, 13, WALNUT[3])
    bevel(img, 2, 4, 13, 14, WALNUT[1], WALNUT[5])
    bevel(img, 3, 5, 12, 13, WALNUT[5], WALNUT[1])
    rect(img, 4, 6, 11, 12, WALNUT[4])
    # Serat panel: garis vertikal putus-putus yang dirancang.
    for x, y0, y1 in ((5, 6, 9), (8, 8, 12), (10, 6, 8), (6, 11, 12)):
        vline(img, x, y0, y1, WALNUT[3])
    put(img, 9, 7, WALNUT[5])
    # Alas (plint) gelap.
    hline(img, 0, 15, 15, WALNUT[1])
    save(img, "bar_wainscot.png")


def tin():
    """Pressed tin: ubin dengan bingkai timbul, cincin, dan roset tengah."""
    img = new(16, 16, TIN[3])
    bevel(img, 0, 0, 15, 15, TIN[5], TIN[0])
    bevel(img, 1, 1, 14, 14, TIN[4], TIN[1])
    rect(img, 2, 2, 13, 13, TIN[2])
    bevel(img, 4, 4, 11, 11, TIN[1], TIN[5])
    bevel(img, 5, 5, 10, 10, TIN[5], TIN[1])
    rect(img, 6, 6, 9, 9, TIN[3])
    # Roset: salib + titik.
    for p in ((7, 6), (8, 6), (6, 7), (6, 8), (9, 7), (9, 8), (7, 9), (8, 9)):
        put(img, *p, TIN[4])
    for p in ((7, 7),):
        put(img, *p, TIN[6])
    put(img, 8, 8, TIN[1])
    # Titik paku sudut.
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(img, *p, TIN[5])
    for p in ((3, 3), (12, 3), (3, 12), (12, 12)):
        put(img, *p, TIN[1])
    save(img, "bar_ceiling.png")


def tar():
    img = new(16, 16, TAR[1])
    for y in (0, 8):
        hline(img, 0, 15, y, TAR[0])
        hline(img, 0, 15, y + 1, TAR[2])
    for x, y in ((3, 4), (11, 2), (7, 6), (13, 12), (2, 13), (9, 11), (5, 10), (14, 5)):
        put(img, x, y, TAR[3])
    for x, y in ((4, 4), (12, 12), (8, 6)):
        put(img, x, y, TAR[0])
    save(img, "bar_roof.png")


def brick():
    """Bata jelaga 8x4 (3 baris bata + 1 nat), ikatan running bond, tiap bata nada berbeda."""
    img = new(16, 16, MORTAR[1])
    tones = [3, 2, 4, 3, 2, 3, 4, 2]
    chips = [(5, 1), (2, 2), (6, 0), (1, 1), (3, 2), (6, 1), (2, 0), (4, 2)]
    k = 0
    for row in range(4):
        y0 = row * 4
        off = 0 if row % 2 == 0 else 4
        for col in range(2):
            x0 = off + col * 8
            b = tones[k]
            for dy in range(3):
                for dx in range(7):
                    c = BRICK[b + 1] if dy == 0 else (BRICK[b - 1] if dy == 2 else BRICK[b])
                    put(img, x0 + dx, y0 + dy, c)
            cx, cy = chips[k]
            put(img, x0 + cx, y0 + cy, BRICK[b - 2])
            put(img, x0 + 6, y0 + 2, BRICK[max(0, b - 2)])
            k += 1
            # Nat vertikal
            vline(img, x0 + 7, y0, y0 + 2, MORTAR[0])
        hline(img, 0, 15, y0 + 3, MORTAR[2] if row % 2 else MORTAR[1])
    save(img, "bar_brick.png")


def stone():
    """Batu potong gelap: dua balok 16x8 berbevel (pondasi, ambang, puncak parapet)."""
    img = new(16, 16, STONE[2])
    for y0 in (0, 8):
        rect(img, 0, y0, 15, y0 + 7, STONE[2])
        bevel(img, 0, y0, 15, y0 + 7, STONE[4], STONE[0])
        hline(img, 1, 14, y0 + 1, STONE[3])
        vline(img, 1, y0 + 1, y0 + 6, STONE[3])
    for x, y in ((5, 3), (11, 5), (3, 12), (9, 10), (13, 13)):
        put(img, x, y, STONE[1])
    for x, y in ((6, 3), (12, 13)):
        put(img, x, y, STONE[4])
    vline(img, 10, 9, 14, STONE[0])
    save(img, "bar_stone.png")


def walnut_planks():
    """Papan walnut vertikal (sisi counter, trim, bilah kipas)."""
    img = new(16, 16)
    tones = [3, 4, 3, 2]
    for p in range(4):
        b = tones[p]
        for y in range(16):
            for dx in range(4):
                x = p * 4 + dx
                c = WALNUT[b + 1] if dx == 0 else (WALNUT[b - 1] if dx == 3 else WALNUT[b])
                put(img, x, y, c)
    for x, y0, y1 in ((1, 2, 6), (2, 9, 13), (6, 1, 4), (9, 6, 11), (13, 3, 8), (14, 12, 15)):
        vline(img, x, y0, y1, WALNUT[2])
    for x, y in ((5, 7), (10, 13)):
        put(img, x, y, WALNUT[2])
    save(img, "walnut_planks.png")


# ================================================================== 32 px: properti


def brass_rod(n=32):
    """Kuningan silinder melintang (footrail, pegangan): gradasi melintang + kilau."""
    img = new(n, n)
    prof = [1, 2, 3, 5, 6, 5, 4, 3, 2, 1]
    for y in range(n):
        c = BRASS[prof[int(y / n * len(prof))]]
        hline(img, 0, n - 1, y, c)
    for x in range(0, n, 8):
        put(img, x + 3, int(n * 0.38), BRASS[6])
    save(img, "brass.png")


def iron_tex(name="iron.png", n=16):
    img = new(n, n)
    prof = [1, 2, 3, 4, 5, 4, 3, 2]
    for y in range(n):
        hline(img, 0, n - 1, y, IRON[prof[int(y / n * len(prof))]])
    for x, y in ((3, 2), (11, 13), (7, 9)):
        put(img, x, y, IRON[1])
    # Sedikit karat kering di sambungan.
    for x, y in ((12, 3), (13, 3), (4, 12)):
        put(img, x, y, hx("#3a2418"))
    save(img, name)


def counter_front():
    """Depan counter: dua panel timbul dengan pilaster beralur di antaranya, kick plate kuningan."""
    img = new(32, 32, WALNUT[3])
    # Lis atas di bawah daun counter (bayangan).
    hline(img, 0, 31, 0, WALNUT[0])
    hline(img, 0, 31, 1, WALNUT[1])
    hline(img, 0, 31, 2, WALNUT[5])
    hline(img, 0, 31, 3, WALNUT[4])
    # Pilaster beralur di x 0..3 dan 16..19 (alur tiap 2 px).
    for px in (0, 16):
        rect(img, px, 4, px + 3, 26, WALNUT[2])
        for dx in (0, 2):
            vline(img, px + dx, 5, 25, WALNUT[4])
            vline(img, px + dx + 1, 5, 25, WALNUT[1])
        hline(img, px, px + 3, 4, WALNUT[5])
        hline(img, px, px + 3, 26, WALNUT[0])
    # Panel timbul di x 4..15 dan 20..31.
    for x0 in (4, 20):
        x1 = x0 + 11
        rect(img, x0, 4, x1, 26, WALNUT[3])
        bevel(img, x0 + 1, 6, x1 - 1, 24, WALNUT[1], WALNUT[5])
        bevel(img, x0 + 2, 7, x1 - 2, 23, WALNUT[5], WALNUT[1])
        rect(img, x0 + 3, 8, x1 - 3, 22, WALNUT[4])
        for gx, gy0, gy1 in ((x0 + 4, 9, 14), (x0 + 6, 13, 21), (x0 + 8, 8, 11), (x0 + 5, 18, 21)):
            vline(img, gx, gy0, gy1, WALNUT[3])
        put(img, x0 + 7, 16, WALNUT[5])
    # Plint gelap + kick plate kuningan yang kusam.
    rect(img, 0, 27, 31, 31, WALNUT[1])
    hline(img, 0, 31, 27, WALNUT[0])
    rect(img, 0, 28, 31, 30, BRASS[2])
    hline(img, 0, 31, 28, BRASS[4])
    hline(img, 0, 31, 30, BRASS[1])
    for x in (3, 11, 19, 27):
        put(img, x, 29, BRASS[5])
    hline(img, 0, 31, 31, WALNUT[0])
    save(img, "counter_front.png")


def counter_top():
    """Daun counter: papan walnut mengilap memanjang (x), kilau pernis, inlay kuningan di tepi depan."""
    img = new(32, 32)
    tones = [4, 3, 4, 5]
    for b in range(4):
        t = tones[b]
        for y in range(b * 8, b * 8 + 8):
            dy = y - b * 8
            c = WALNUT[t + 1] if dy == 0 else (WALNUT[t - 2] if dy == 7 else WALNUT[t])
            hline(img, 0, 31, y, c)
    # Serat memanjang (garis putus) + kilau pernis diagonal.
    for x0, x1, y in ((2, 11, 3), (14, 26, 5), (5, 17, 11), (20, 30, 13), (1, 9, 19), (12, 24, 21), (6, 15, 27),
                      (18, 29, 29)):
        hline(img, x0, x1, y, WALNUT[3])
    for i in range(9):
        put(img, 6 + i * 2, 2 + i * 3, WALNUT[6])
        put(img, 7 + i * 2, 2 + i * 3, WALNUT[6])
    # Tepi depan (y 30..31): inlay kuningan.
    hline(img, 0, 31, 30, BRASS[4])
    hline(img, 0, 31, 31, BRASS[2])
    save(img, "counter_top.png")


def cabinet_front():
    """Lemari back bar: dua pintu panel dengan kenop kuningan."""
    img = new(32, 32, WALNUT[2])
    hline(img, 0, 31, 0, WALNUT[5])
    hline(img, 0, 31, 31, WALNUT[0])
    for x0 in (1, 17):
        x1 = x0 + 13
        rect(img, x0, 2, x1, 29, WALNUT[3])
        bevel(img, x0, 2, x1, 29, WALNUT[5], WALNUT[0])
        bevel(img, x0 + 2, 5, x1 - 2, 26, WALNUT[1], WALNUT[5])
        rect(img, x0 + 3, 6, x1 - 3, 25, WALNUT[4])
        for gx, gy0, gy1 in ((x0 + 5, 7, 13), (x0 + 8, 12, 24), (x0 + 6, 19, 24)):
            vline(img, gx, gy0, gy1, WALNUT[3])
    # Kenop kuningan di dekat celah tengah.
    for kx in (13, 18):
        rect(img, kx, 14, kx + 1, 15, BRASS[4])
        put(img, kx, 14, BRASS[6])
        put(img, kx + 1, 15, BRASS[2])
    vline(img, 15, 2, 29, WALNUT[0])
    vline(img, 16, 2, 29, WALNUT[1])
    save(img, "cabinet_front.png")


# ---------------- botol

BOTTLES = [
    # (lebar, tinggi, leher, gelas, cairan, isi 0..1, label, tutup)
    (5, 12, 3, "#3b2210", "#8a4a14", 0.7, "#cbbf9f", "brass"),   # whiskey kotak
    (4, 14, 5, "#0f2419", "#14301f", 0.85, "#e3d8ba", "black"),  # anggur
    (5, 13, 3, "#5d7270", "#8fa8a6", 0.6, "#2b4a52", "iron"),    # gin bening
    (4, 11, 4, "#20380f", "#3d6a1e", 0.5, None, "cork"),         # absinth
    (6, 10, 2, "#2a1408", "#5a2a0e", 0.8, "#867c66", "brass"),   # rum bulat
    (3, 9, 3, "#3a2a14", "#6b3a12", 0.65, "#e3d8ba", "black"),   # bitters
    (5, 13, 4, "#452812", "#b06a20", 0.4, "#5c5546", "cork"),    # cognac
    (4, 12, 4, "#16231f", "#2c4a40", 0.9, "#ab9f84", "iron"),    # vermouth
]


def bottle(img, x, base_y, spec, glint):
    w, h, neck, glass, liquid, fill, label, cap = spec
    g = hx(glass)
    lq = hx(liquid)
    body_h = h - neck - 1
    top = base_y - h + 1
    capc = {"brass": BRASS[4], "black": IRON[1], "iron": IRON[4], "cork": hx("#6b4a2a")}[cap]
    # Tutup
    nx = x + w // 2 - 1 if w >= 4 else x + 1
    nw = 2 if w >= 4 else 1
    rect(img, nx, top, nx + nw - 1, top, capc)
    # Leher
    rect(img, nx, top + 1, nx + nw - 1, top + neck - 1, shade(g, 1.15))
    # Bahu + badan
    body_top = top + neck
    for y in range(body_top, base_y + 1):
        inset = 1 if y == body_top and w > 3 else 0
        for dx in range(inset, w - inset):
            frac = (base_y - y) / max(1, body_h)
            c = lq if frac < fill else g
            if dx == 0 or dx == w - 1:
                c = shade(c, 0.6)
            img.putpixel((x + dx, y), c)
    # Label
    if label:
        ly0 = body_top + max(2, body_h // 3)
        lh = max(2, body_h // 3)
        lc = hx(label)
        for y in range(ly0, min(base_y, ly0 + lh)):
            for dx in range(1 if w > 3 else 0, w - (1 if w > 3 else 0)):
                img.putpixel((x + dx, y), lc if y != ly0 + lh - 1 else shade(lc, 0.7))
        if w >= 5:
            img.putpixel((x + 2, ly0 + 1 if lh > 2 else ly0), shade(lc, 0.45))
    # Kilau kaca: satu garis vertikal di kiri badan.
    for y in range(body_top + 1, base_y - 1):
        if (y - body_top) % 5 != 3:
            img.putpixel((x + 1 if w > 3 else x, y), mix(img.getpixel((x + 1 if w > 3 else x, y)), BULB, 0.35))
    # Lapisan glow: hanya dasar cairan yang tertangkap strip lampu dari bawah (emissive, redup).
    for dx in range(1, w - 1):
        glint.putpixel((x + dx, base_y), mix(lq, TUNGSTEN, 0.35))


def bottle_rows():
    """Dua variasi baris botol (32x16 per baris, digabung 32x32): A di atas, B di bawah."""
    img = new(32, 32)
    glint = new(32, 32)
    layouts = [
        [(2, 0), (9, 4), (24, 1)],
        [(4, 6), (13, 3), (20, 7)],
    ]
    for r, lay in enumerate(layouts):
        base = r * 16 + 15
        for x, k in lay:
            spec = BOTTLES[k]
            if x + spec[0] <= 32:
                bottle(img, x, base, spec, glint)
    save(img, "bottles.png")
    save(glint, "bottles_glow.png")


def shelf_back():
    """Panel belakang rak: walnut gelap dengan bayangan rak, cermin sempit di tengah."""
    img = new(32, 32, WALNUT[1])
    for x in range(0, 32, 8):
        vline(img, x, 0, 31, WALNUT[0])
        vline(img, x + 1, 0, 31, WALNUT[2])
    for x, y0, y1 in ((4, 3, 10), (12, 17, 26), (20, 5, 12), (27, 19, 29)):
        vline(img, x, y0, y1, WALNUT[2])
    save(img, "shelf_back.png")


def strip_glow():
    img = new(16, 4)
    for x in range(16):
        put(img, x, 0, AMBER[3])
        put(img, x, 1, AMBER[5])
        put(img, x, 2, AMBER[4])
        put(img, x, 3, AMBER[3])
    save(img, "strip_glow.png")


def mirror():
    """Cermin back bar 3x2 tile (96x64): kaca asap, kilau diagonal, bingkai kuningan ber-ornamen."""
    W, H = 96, 64
    img = new(W, H)
    for y in range(H):
        for x in range(W):
            # Lebih terang di tengah (pantulan ruang), gelap ke tepi.
            dx, dy = (x - W / 2) / (W / 2), (y - H * 0.45) / (H / 2)
            d = min(1, dx * dx * 0.7 + dy * dy)
            band = int((1 - d) * 3.2)
            img.putpixel((x, y), [hx("#121719"), hx("#1a1f1e"), hx("#241f1a"), hx("#2e261d")][min(3, band)])
    # Kilau diagonal (dua garis paralel) + pantulan lampu hangat redup.
    for i in range(-10, 60):
        for w0, c in ((0, hx("#3a4c4a")), (1, hx("#2c3a3a")), (7, hx("#2c3a3a"))):
            x, y = 18 + i + w0, 54 - i
            if 3 < x < W - 4 and 3 < y < H - 4:
                img.putpixel((x, y), c)
    for cx, cy in ((30, 22), (66, 22)):
        for dx, dy, c in ((0, 0, hx("#7a5228")), (1, 0, hx("#4a3218")), (-1, 0, hx("#4a3218")), (0, 1, hx("#4a3218")),
                          (0, -1, hx("#3a2814"))):
            img.putpixel((cx + dx, cy + dy), c)
    # Bingkai kuningan 4 px + ornamen sudut.
    for k, c in enumerate((BRASS[1], BRASS[5], BRASS[3], BRASS[2])):
        bevel(img, k, k, W - 1 - k, H - 1 - k, c if k != 1 else BRASS[5], c if k != 1 else BRASS[1])
    bevel(img, 4, 4, W - 5, H - 5, hx("#05070a"), hx("#2a3638"))
    for cx, cy in ((5, 5), (W - 6, 5), (5, H - 6), (W - 6, H - 6)):
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            img.putpixel((cx + dx - (1 if cx > 50 else 0), cy + dy - (1 if cy > 30 else 0)), BRASS[4])
    # Ukiran tengah atas.
    for dx in range(-4, 5):
        img.putpixel((W // 2 + dx, 1), BRASS[6] if abs(dx) < 2 else BRASS[4])
    img.putpixel((W // 2, 2), BRASS[6])
    for ty in range(2):
        for tx in range(3):
            save(img.crop((tx * 32, ty * 32, tx * 32 + 32, ty * 32 + 32)), f"mirror_{tx}_{ty}.png")
    OUT["_mirror_full"] = img


# ---------------- jendela kota malam


def city(w, h, seed, single=False):
    """Kota malam di balik jendela: langit, gedung jauh berkabut, gedung dekat siluet, jendela tungsten/cyan."""
    img = new(w, h)
    rnd = lcg(seed)
    sky = [hx("#03060a"), hx("#050b12"), hx("#08121b"), hx("#0b1a26"), hx("#0f2433"), hx("#143043"), hx("#1a3d52")]
    for y in range(h):
        band = int((y / h) ** 1.4 * (len(sky) - 0.01))
        hline(img, 0, w - 1, y, sky[band])
    # Bulan tertutup awan (pucat, kecil) di salah satu scene.
    if seed % 3 == 1:
        mx, my = w - 14, 9
        for dy in range(-3, 4):
            for dx in range(-3, 4):
                if dx * dx + dy * dy <= 9:
                    img.putpixel((mx + dx, my + dy), hx("#4f6a72") if dx * dx + dy * dy > 4 else hx("#8aa4a8"))
        # Awan tipis menyaput separuh bawah bulan (bukan garis tegas).
        for dy in range(1, 4):
            for dx in range(-4, 5):
                if (dx + dy) % 2 == 0:
                    img.putpixel((mx + dx, my + dy), mix(img.getpixel((mx + dx, my + dy)), hx("#0b1a26"), 0.6))
    far = hx("#0a1620")
    farwin = [hx("#1c4558"), hx("#2a5a6a"), hx("#3a4a3e")]
    x = 0
    while x < w:
        bw = 5 + next(rnd) % 7
        top = int(h * (0.42 + (next(rnd) % 18) / 100))
        rect(img, x, top, min(w - 1, x + bw - 1), h - 1, far)
        for wy in range(top + 2, h - 2, 3):
            for wx in range(x + 1, min(w - 1, x + bw - 1), 2):
                if next(rnd) % 9 == 0:
                    img.putpixel((wx, wy), farwin[next(rnd) % 3])
        x += bw + (next(rnd) % 2)
    # Kabut cyan tipis di cakrawala.
    for y in range(int(h * 0.62), int(h * 0.72)):
        for xx in range(w):
            c = img.getpixel((xx, y))
            img.putpixel((xx, y), mix(c, hx("#1d4b60"), 0.35))
    near = hx("#030405")
    x = -2 if not single else 0
    while x < w:
        bw = 9 + next(rnd) % 9
        top = int(h * (0.3 + (next(rnd) % 26) / 100))
        rect(img, max(0, x), top, min(w - 1, x + bw - 1), h - 1, near)
        kind = next(rnd) % 4
        if kind == 0 and x + 4 < w:
            vline(img, max(0, x + 3), top - 6, top - 1, near)
            img.putpixel((max(0, x + 3), top - 6), hx("#6a5a3a"))
        elif kind == 1:
            rect(img, max(0, x + 2), top - 3, min(w - 1, x + 6), top - 1, near)
            hline(img, max(0, x + 1), min(w - 1, x + 7), top - 4, near)
        # Jendela gedung dekat: kisi 2x2 px, kebanyakan gelap.
        for wy in range(top + 3, h - 3, 4):
            for wx in range(x + 2, x + bw - 2, 3):
                if 0 <= wx < w - 1 and next(rnd) % 6 == 0:
                    r = next(rnd) % 10
                    c = hx("#d08a3e") if r < 6 else (hx("#7a5a30") if r < 8 else hx("#4aa6c8"))
                    rect(img, wx, wy, wx + 1, wy + 1, c)
                    if r < 6:
                        img.putpixel((wx, wy), hx("#ffc070"))
        x += bw + 2 + next(rnd) % 4
    # Lampu jalan / mobil di bawah (bokeh 3x3 lembut).
    for k in range(3 if single else 5):
        bx, by = 2 + next(rnd) % (w - 4), h - 2 - next(rnd) % 4
        c = hx("#c0803a") if k % 2 == 0 else hx("#3a8aa8")
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            if 0 <= bx + dx < w and 0 <= by + dy < h:
                img.putpixel((bx + dx, by + dy), c if (dx, dy) == (0, 0) else mix(c, img.getpixel((bx + dx, by + dy)), 0.5))
    return img


def windows():
    for scene, seed in ((0, 7), (1, 22)):
        img = city(64, 64, seed)
        for ty, half in ((0, "top"), (1, "bottom")):
            for tx, side in ((0, "left"), (1, "right")):
                save(img.crop((tx * 32, ty * 32, tx * 32 + 32, ty * 32 + 32)), f"window/city_{scene}_{side}_{half}.png")
        OUT[f"_city_{scene}"] = img
    img = city(32, 64, 40, single=True)
    for ty, half in ((0, "top"), (1, "bottom")):
        save(img.crop((0, ty * 32, 32, ty * 32 + 32)), f"window/city_s_single_{half}.png")
    OUT["_city_s"] = img
    rain()
    window_outside()


def rain():
    """Hujan animasi: 16 frame 32x32, garis jatuh agak miring (dua kecepatan), alfa cutout."""
    frames = 16
    img = new(32, 32 * frames)
    streaks = []
    rnd = lcg(5)
    for i in range(22):
        streaks.append((next(rnd) % 32, next(rnd) % 32, 3 + next(rnd) % 4, 2 if i % 3 else 4))
    for f in range(frames):
        for sx, sy, length, speed in streaks:
            y0 = sy + f * speed * 2
            for k in range(length):
                y = (y0 + k) % 32
                x = (sx + (y0 + k) // 12) % 32
                c = hx("#5a7c88") if k == length - 1 else hx("#2f4a56")
                img.putpixel((x, f * 32 + y), c)
    save(img, "window/rain.png")
    with open(os.path.join(ROOT, "window", "rain.png.mcmeta"), "w") as fh:
        fh.write('{\n  "animation": {\n    "frametime": 1,\n    "interpolate": false\n  }\n}\n')


def drops():
    """Butir air di kaca (statis, cutout)."""
    img = new(32, 32)
    pts = [(3, 4, 2), (11, 2, 1), (19, 7, 3), (27, 3, 1), (6, 14, 1), (15, 17, 2), (24, 13, 1), (29, 20, 2),
           (2, 24, 1), (9, 27, 3), (20, 25, 1), (26, 29, 1), (13, 9, 1), (31, 11, 1)]
    for x, y, trail in pts:
        img.putpixel((x, y), hx("#9dc4d0"))
        img.putpixel((x, (y + 1) % 32), hx("#3c5866"))
        for t in range(trail):
            img.putpixel((x, (y - 1 - t) % 32), hx("#24404c"))
    save(img, "window/drops.png")


def window_outside():
    """Sisi luar jendela: kaca gelap berembun dengan pendar tungsten dari dalam, kilau langit."""
    img = new(32, 32)
    for y in range(32):
        for x in range(32):
            t = y / 31
            c = mix(hx("#0d1216"), hx("#4a2c12"), t ** 1.8 * 0.9)
            img.putpixel((x, y), c)
    for i in range(14):
        img.putpixel((4 + i, 20 - i), hx("#3a4a52"))
        img.putpixel((9 + i, 26 - i), hx("#26323a"))
    # Kusen kayu luar.
    for k, c in enumerate((WALNUT[1], WALNUT[3])):
        bevel(img, k, k, 31 - k, 31 - k, c, WALNUT[0])
    vline(img, 15, 2, 29, WALNUT[2])
    vline(img, 16, 2, 29, WALNUT[0])
    save(img, "window/outside.png")


def window_frame():
    img = new(16, 16, WALNUT[2])
    for y in range(16):
        put(img, 0, y, WALNUT[4])
        put(img, 15, y, WALNUT[0])
    for x, y0, y1 in ((4, 2, 7), (9, 6, 13), (12, 1, 4)):
        vline(img, x, y0, y1, WALNUT[3])
    save(img, "window/frame.png")


def reveal():
    """Kusen dalam relung jendela: plester gelap."""
    img = new(16, 16, hx("#141312"))
    for x, y in ((3, 4), (10, 2), (6, 11), (13, 13)):
        put(img, x, y, hx("#1c1a18"))
    save(img, "window/reveal.png")


# ---------------- sconce, stool, kipas, pipa


def sconce():
    """Sheet 32x32: pelat kuningan, lengan, kap kaca amber (redup), bohlam."""
    img = new(32, 32)
    # Pelat dinding oval (0..11, 0..15)
    for y in range(16):
        for x in range(12):
            dx, dy = (x - 5.5) / 6, (y - 7.5) / 8
            if dx * dx + dy * dy <= 1:
                c = BRASS[3]
                if dx * dx + dy * dy > 0.7:
                    c = BRASS[5] if dx + dy < 0 else BRASS[1]
                img.putpixel((x, y), c)
    for p in ((5, 3), (6, 12)):
        img.putpixel(p, BRASS[6])
    # Lengan (16..31, 0..3): batang kuningan.
    for y, c in enumerate((BRASS[5], BRASS[4], BRASS[3], BRASS[1])):
        hline(img, 16, 31, y, c)
    # Kap amber (0..15, 16..31): kaca bergaris rusuk, terang di bawah (bohlam di dalam).
    for y in range(16, 32):
        t = (y - 16) / 15
        for x in range(16):
            c = mix(hx("#4a260c"), hx("#f2a85a"), t ** 1.2)
            if x % 4 == 0:
                c = shade(c, 0.7)
            img.putpixel((x, y), c)
    hline(img, 0, 15, 16, BRASS[4])
    hline(img, 0, 15, 31, BRASS[5])
    # Bohlam (16..23, 16..23)
    rect(img, 16, 16, 23, 23, BULB)
    rect(img, 18, 18, 21, 21, hx("#fff6e4"))
    # Bagian bawah kap (24..31,16..23): bukaan bawah, terang.
    rect(img, 24, 16, 31, 23, hx("#f2a85a"))
    rect(img, 26, 18, 29, 21, BULB)
    save(img, "sconce.png")


def stool():
    """Sheet 32x32: atas dudukan kulit dengan kancing, sisi kulit dengan paku kuningan, kuningan, besi."""
    img = new(32, 32)
    # Atas dudukan (0..19, 0..19): kulit, kilau melengkung, kancing tengah.
    for y in range(20):
        for x in range(20):
            dx, dy = (x - 9.5) / 10, (y - 9.5) / 10
            r = math.sqrt(dx * dx + dy * dy)
            k = 4
            if r > 0.85:
                k = 2
            elif r > 0.65:
                k = 3
            if -0.75 < dx + dy < -0.35 and r < 0.8:
                k = 5
            img.putpixel((x, y), LEATHER[k])
    for p in ((9, 9), (10, 10)):
        img.putpixel(p, LEATHER[1])
    img.putpixel((9, 10), LEATHER[6])
    for x, y in ((4, 9), (15, 10), (9, 4), (10, 15)):
        img.putpixel((x, y), LEATHER[2])
    # Sisi dudukan (0..31, 20..25): kulit, baris paku kuningan.
    for y in range(20, 26):
        k = [5, 4, 4, 3, 2, 1][y - 20]
        hline(img, 0, 31, y, LEATHER[k])
    for x in range(1, 32, 3):
        img.putpixel((x, 23), BRASS[5])
    # Kuningan (20..31, 0..9): tiang.
    for x in range(20, 32):
        c = BRASS[[2, 3, 4, 5, 6, 5, 4, 3, 2, 2, 1, 1][x - 20]]
        vline(img, x, 0, 9, c)
    # Besi kaki (20..31, 10..19).
    for x in range(20, 32):
        c = IRON[[2, 3, 4, 5, 4, 3, 2, 2, 1, 1, 1, 1][x - 20]]
        vline(img, x, 10, 19, c)
    # Kulit gelap bawah dudukan (0..31, 26..31).
    rect(img, 0, 26, 31, 31, LEATHER[1])
    save(img, "stool.png")


def fan():
    """Bilah kipas walnut (32x8 per bilah di 32x32), rumah motor kuningan."""
    img = new(32, 32)
    for y in range(8):
        k = [5, 4, 4, 3, 3, 3, 2, 1][y]
        hline(img, 0, 31, y, WALNUT[k])
    for x0, x1, y in ((3, 12, 3), (15, 26, 4), (8, 18, 5)):
        hline(img, x0, x1, y, WALNUT[2])
    rect(img, 0, 0, 5, 7, BRASS[3])
    hline(img, 0, 5, 0, BRASS[5])
    put(img, 2, 3, BRASS[1])
    put(img, 2, 5, BRASS[1])
    # Rumah motor (0..15, 8..23)
    for y in range(8, 24):
        k = [5, 5, 4, 4, 3, 3, 3, 3, 2, 2, 2, 2, 1, 1, 1, 0][y - 8]
        hline(img, 0, 15, y, BRASS[k])
    for x in range(0, 16, 3):
        put(img, x, 14, BRASS[1])
    # Batang (16..19, 8..31) besi.
    for x, k in zip(range(16, 20), (3, 5, 3, 1)):
        vline(img, x, 8, 31, IRON[k])
    save(img, "fan.png")


def pipe():
    img = new(16, 16)
    prof = [1, 2, 3, 5, 6, 5, 4, 3, 2, 2, 1, 1, 1, 0, 0, 0]
    for y in range(16):
        hline(img, 0, 15, y, IRON[prof[y]])
    for x, y in ((5, 6), (12, 7), (2, 9)):
        put(img, x, y, hx("#3a2418"))
    put(img, 6, 6, hx("#4a2e1c"))
    # Flens (bagian kanan atas, untuk elemen cincin): 4 px.
    save(img, "pipe.png")
    fl = new(16, 16)
    for y in range(16):
        hline(fl, 0, 15, y, IRON[[3, 4, 5, 4, 3, 3, 2, 2, 2, 1, 1, 1, 1, 0, 0, 0][y]])
    for x in (2, 7, 12):
        put(fl, x, 4, IRON[6])
        put(fl, x, 5, IRON[0])
    save(fl, "pipe_flange.png")


# ---------------- poster potongan kertas (gaya Saul Bass)


def poly(d, pts, c):
    d.polygon([(int(x), int(y)) for x, y in pts], fill=c)


REVOLVER_PROFILE = os.path.join(os.path.dirname(__file__), "..", "docs", "artbible", "revolver_profile.txt")


def poster_pull():
    """THE PULL: revolver profil (siluet dari art bible) digenggam tangan hitam, lingkaran amber, potongan kertas."""
    W, H = 32, 64
    img = new(W, H, CREAM[3])
    d = ImageDraw.Draw(img)
    d.ellipse((3, 5, 29, 31), fill=CREAM[1])
    d.ellipse((5, 7, 27, 29), fill=CREAM[2])
    # Bidang hitam miring di bawah (potongan kertas).
    poly(d, [(0, 42), (32, 36), (32, 64), (0, 64)], BLACK)
    # Lengan masuk dari kiri bawah, manset krem (aksen gaya Bass), kepalan di gagang.
    poly(d, [(0, 34), (6, 27), (12, 30), (8, 40), (0, 46)], hx("#141008"))
    poly(d, [(0, 32), (5, 26), (7, 28), (1, 35)], CREAM[4])
    with open(REVOLVER_PROFILE) as fh:
        rows = [r.rstrip() for r in fh if r.strip()]
    ox, oy = 4, 10
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != ".":
                img.putpixel((ox + i, oy + j), BLACK)
    # Kilau tipis di laras dan alur silinder (kertas krem disobek).
    hline(img, ox + 9, ox + 23, oy + 2, CREAM[1])
    for x in (ox + 9, ox + 11):
        vline(img, x, oy + 3, oy + 7, CREAM[0])
    # Kepalan: menutup gagang.
    poly(d, [(ox + 1, oy + 9), (ox + 7, oy + 8), (ox + 9, oy + 13), (ox + 6, oy + 17), (ox, oy + 16)], hx("#141008"))
    hline(img, ox + 2, ox + 6, oy + 11, hx("#2a2214"))
    hline(img, ox + 2, ox + 6, oy + 13, hx("#2a2214"))
    # Strip "tipografi": balok kertas krem di bidang hitam (tanpa huruf terbaca).
    for y, x0, x1 in ((48, 3, 9), (48, 22, 29), (52, 3, 14), (56, 3, 10), (59, 3, 7)):
        rect(img, x0, y, x1, y + 1, CREAM[3])
    rect(img, 21, 52, 29, 57, AMBER[4])
    for x in range(0, 32, 3):
        y = int(42 - x * 6 / 32)
        img.putpixel((x, y - 1), CREAM[2])
    bevel(img, 0, 0, W - 1, H - 1, CREAM[1], CREAM[0])
    for p in ((1, 1), (30, 1), (1, 62), (30, 62)):
        img.putpixel(p, BRASS[5])
    for ty, half in ((0, "top"), (1, "bottom")):
        save(img.crop((0, ty * 32, 32, ty * 32 + 32)), f"poster/pull_{half}.png")
    OUT["_poster_pull"] = img


def poster_bluff():
    """THE BLUFF: latar hitam, tiga kartu krem berkipas, mata amber di kartu tengah, strip hijau."""
    W, H = 32, 64
    img = new(W, H, GREEN[1])
    d = ImageDraw.Draw(img)
    poly(d, [(0, 0), (32, 0), (32, 20), (0, 28)], BLACK)
    # Kartu berkipas (potongan kertas miring).
    poly(d, [(3, 22), (13, 18), (18, 40), (8, 44)], CREAM[2])
    poly(d, [(19, 18), (29, 22), (24, 44), (14, 40)], CREAM[2])
    poly(d, [(10, 14), (22, 14), (22, 42), (10, 42)], CREAM[4])
    # Mata amber di kartu tengah.
    poly(d, [(11, 28), (16, 24), (21, 28), (16, 32)], BLACK)
    poly(d, [(13, 28), (16, 26), (19, 28), (16, 30)], AMBER[5])
    rect(img, 16, 27, 16, 29, BLACK)
    # Bayangan kartu (offset gelap) untuk kesan berlapis.
    for y in range(15, 43):
        img.putpixel((22, y), CREAM[0])
    # Strip tipografi di bawah.
    for y, x0, x1 in ((49, 4, 27), (53, 4, 15), (53, 18, 27), (57, 4, 11)):
        rect(img, x0, y, x1, y + 1, CREAM[3])
    rect(img, 22, 56, 27, 58, AMBER[4])
    bevel(img, 0, 0, W - 1, H - 1, CREAM[1], CREAM[0])
    for p in ((1, 1), (30, 1), (1, 62), (30, 62)):
        img.putpixel(p, BRASS[5])
    for ty, half in ((0, "top"), (1, "bottom")):
        save(img.crop((0, ty * 32, 32, ty * 32 + 32)), f"poster/bluff_{half}.png")
    OUT["_poster_bluff"] = img


# ---------------- pintu, papan nama, kanopi


def door():
    """Pintu walnut dengan kaca buram (atas), panel timbul (bawah), pelat tendang kuningan, emblem sekop emas."""
    top = new(32, 32, WALNUT[3])
    bot = new(32, 32, WALNUT[3])
    for img in (top, bot):
        vline(img, 0, 0, 31, WALNUT[5])
        vline(img, 1, 0, 31, WALNUT[4])
        vline(img, 30, 0, 31, WALNUT[1])
        vline(img, 31, 0, 31, WALNUT[0])
    # Kaca buram: gradasi, dither halus terstruktur (pola 2x2), bingkai dalam.
    rect(top, 4, 4, 27, 31, GLASS_FROST[3])
    for y in range(4, 32):
        for x in range(4, 28):
            t = (y - 4) / 27
            c = mix(GLASS_FROST[4], GLASS_FROST[2], t)
            if (x + y) % 2 == 0 and (x // 2 + y // 2) % 3 == 0:
                c = shade(c, 1.08)
            top.putpixel((x, y), c)
    bevel(top, 3, 3, 28, 31, WALNUT[1], WALNUT[5])
    hline(top, 0, 31, 0, WALNUT[5])
    # Emblem sekop daun emas.
    spade = ["....g....", "...ggg...", "..ggggg..", ".ggggggg.", "ggggggggg", "ggggggggg", ".gg.g.gg.", "....g....",
             "...ggg..."]
    for j, row in enumerate(spade):
        for i, ch in enumerate(row):
            if ch == "g":
                top.putpixel((11 + i, 12 + j), BRASS[5] if (i + j) % 3 else BRASS[6])
    # Garis emas tipis di bawah emblem.
    hline(top, 9, 22, 23, BRASS[4])
    # Bawah: rel tengah, panel timbul, pegangan, pelat tendang.
    rect(bot, 2, 0, 29, 2, WALNUT[3])
    hline(bot, 2, 29, 0, WALNUT[1])
    hline(bot, 2, 29, 2, WALNUT[4])
    rect(bot, 5, 5, 26, 22, WALNUT[3])
    bevel(bot, 4, 4, 27, 23, WALNUT[1], WALNUT[5])
    bevel(bot, 5, 5, 26, 22, WALNUT[5], WALNUT[1])
    rect(bot, 7, 7, 24, 20, WALNUT[4])
    for x, y0, y1 in ((10, 8, 14), (16, 12, 19), (21, 8, 12)):
        vline(bot, x, y0, y1, WALNUT[3])
    rect(bot, 2, 25, 29, 30, BRASS[3])
    hline(bot, 2, 29, 25, BRASS[5])
    hline(bot, 2, 29, 30, BRASS[1])
    for x in (4, 27):
        bot.putpixel((x, 27), BRASS[6])
        bot.putpixel((x, 28), BRASS[1])
    hline(bot, 0, 31, 31, WALNUT[0])
    # Pegangan kuningan (di atas panel, sisi kanan).
    rect(top, 25, 30, 26, 31, BRASS[4])
    rect(bot, 25, 0, 26, 3, BRASS[4])
    bot.putpixel((25, 0), BRASS[6])
    save(top, "door_top.png")
    save(bot, "door_bottom.png")
    item = new(16, 16)
    small_t = top.resize((8, 8), Image.NEAREST)
    small_b = bot.resize((8, 8), Image.NEAREST)
    item.paste(small_t, (4, 0))
    item.paste(small_b, (4, 8))
    save(item, "../item/frosted_door.png")


FONT5 = {
    "L": ["x...", "x...", "x...", "x...", "xxxx"],
    "I": ["xxx", ".x.", ".x.", ".x.", "xxx"],
    "A": [".xx.", "x..x", "xxxx", "x..x", "x..x"],
    "R": ["xxx.", "x..x", "xxx.", "x.x.", "x..x"],
    "'": ["x", "x", ".", ".", "."],
    "S": [".xxx", "x...", ".xx.", "...x", "xxx."],
    "T": ["xxx", ".x.", ".x.", ".x.", ".x."],
    "B": ["xxx.", "x..x", "xxx.", "x..x", "xxx."],
    "E": ["xxxx", "x...", "xxx.", "x...", "xxxx"],
}


def text(img, s, x, y, c, scale=1, shadow=None):
    cx = x
    for ch in s:
        g = FONT5[ch]
        for j, row in enumerate(g):
            for i, p in enumerate(row):
                if p == "x":
                    for sy in range(scale):
                        for sx in range(scale):
                            if shadow:
                                img.putpixel((cx + i * scale + sx + 1, y + j * scale + sy + 1), shadow)
                            img.putpixel((cx + i * scale + sx, y + j * scale + sy), c)
        cx += (len(g[0]) + 1) * scale
    return cx


def text_width(s, scale=1):
    return sum((len(FONT5[ch][0]) + 1) * scale for ch in s) - scale


def sign():
    """Papan nama gantung 64x32 (2 tile): enamel hitam, bingkai kuningan, LIAR'S besar, TABLE kecil, bohlam marquee."""
    W, H = 64, 32
    img = new(W, H, hx("#0b0b0d"))
    bevel(img, 0, 0, W - 1, H - 1, BRASS[5], BRASS[1])
    bevel(img, 1, 1, W - 2, H - 2, BRASS[3], BRASS[2])
    bevel(img, 2, 2, W - 3, H - 3, hx("#020203"), hx("#18181b"))
    big = "LIAR'S"
    w = text_width(big, 2)
    text(img, big, (W - w) // 2, 7, BRASS[5], 2, shadow=BRASS[1])
    small = "TABLE"
    w = text_width(small, 1)
    text(img, small, (W - w) // 2, 20, CREAM[3])
    hline(img, 10, (W - w) // 2 - 3, 22, BRASS[3])
    hline(img, (W + w) // 2 + 2, W - 11, 22, BRASS[3])
    glow = new(W, H)
    # Bohlam marquee di sepanjang bingkai (tiap 5 px).
    for x in range(4, W - 3, 5):
        for y in (4, H - 5):
            img.putpixel((x, y), AMBER[3])
            glow.putpixel((x, y), BULB)
    for y in range(9, H - 6, 5):
        for x in (4, W - 5):
            img.putpixel((x, y), AMBER[3])
            glow.putpixel((x, y), BULB)
    for tx, side in ((0, "left"), (1, "right")):
        save(img.crop((tx * 32, 0, tx * 32 + 32, 32)), f"sign/{side}.png")
        save(glow.crop((tx * 32, 0, tx * 32 + 32, 32)), f"sign/{side}_glow.png")
    OUT["_sign"] = img


def awning():
    """Kanopi kanvas: garis hijau tua / hijau felt, valance bergerigi di bawah (cutout)."""
    img = new(32, 32)
    for x in range(32):
        stripe = (x // 4) % 2
        for y in range(32):
            c = GREEN[3] if stripe else GREEN[1]
            if y < 2:
                c = shade(c, 1.25)
            if y > 26:
                c = shade(c, 0.8)
            img.putpixel((x, y), c)
    for x in range(0, 32, 4):
        vline(img, x, 0, 31, shade(GREEN[1], 0.8))
    save(img, "awning.png")
    val = new(32, 16)
    for x in range(32):
        stripe = (x // 4) % 2
        depth = 10 + (2 if (x % 8) in (3, 4) else (1 if (x % 8) in (2, 5) else 0))
        for y in range(depth):
            c = GREEN[3] if stripe else GREEN[1]
            if y == 0:
                c = BRASS[3]
            val.putpixel((x, y), c)
    save(val, "awning_valance.png")


def bracket():
    img = new(16, 16, IRON[2])
    for y in range(16):
        put(img, 0, y, IRON[4])
        put(img, 15, y, IRON[0])
    hline(img, 0, 15, 0, IRON[5])
    save(img, "iron_bracket.png")


def booth():
    """Booth kulit: sandaran capitonné (kancing berlian), dudukan, panel walnut."""
    img = new(32, 32)
    # Sandaran (0..31, 0..19): kulit dengan lipatan berlian dan kancing.
    for y in range(20):
        for x in range(32):
            u, v = (x % 8) - 3.5, (y % 8) - 3.5
            d = abs(u) + abs(v)
            k = 4 if d < 2.5 else (5 if d < 3.5 and u + v < 0 else 3)
            if d >= 3.9:
                k = 2
            img.putpixel((x, y), LEATHER[k])
    for y in range(0, 20, 8):
        for x in range(0, 32, 8):
            img.putpixel((x, y), LEATHER[1])
            img.putpixel((x + 1, y + 1 if y + 1 < 20 else y), LEATHER[6])
    hline(img, 0, 31, 0, LEATHER[5])
    # Dudukan atas (0..31, 20..27).
    for y in range(20, 28):
        k = [5, 5, 4, 4, 4, 3, 3, 2][y - 20]
        hline(img, 0, 31, y, LEATHER[k])
    for x in range(0, 32, 16):
        vline(img, x, 20, 27, LEATHER[1])
    # Panel walnut bawah (0..31, 28..31) + paku kuningan.
    rect(img, 0, 28, 31, 31, WALNUT[2])
    hline(img, 0, 31, 28, WALNUT[4])
    for x in range(2, 32, 4):
        img.putpixel((x, 29), BRASS[4])
    save(img, "booth.png")


def table_lamp():
    """Meja kecil walnut + lampu banker: kap kaca hijau (dalam menyala), kaki kuningan."""
    img = new(32, 32)
    # Daun meja (0..15, 0..15): walnut bulat-ish dengan tepi gelap.
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 8, (y - 7.5) / 8
            r = dx * dx + dy * dy
            c = WALNUT[4] if r < 0.6 else (WALNUT[3] if r < 0.85 else WALNUT[1])
            if -0.5 < dx + dy < -0.2 and r < 0.6:
                c = WALNUT[5]
            img.putpixel((x, y), c)
    # Kap hijau (16..31, 0..7): luar hijau tua mengilap.
    for y in range(8):
        k = [5, 4, 4, 3, 3, 2, 2, 1][y]
        hline(img, 16, 31, y, GREEN[k])
    for x in range(16, 32, 5):
        put(img, x, 1, hx("#5e8a74"))
    # Dalam kap menyala (16..31, 8..15): tungsten.
    for y in range(8, 16):
        hline(img, 16, 31, y, mix(BULB, TUNGSTEN, (y - 8) / 8))
    # Kuningan (0..15, 16..23), tepi daun (0..31, 24..27), kaki (16..31, 16..23).
    for y in range(16, 24):
        hline(img, 0, 15, y, BRASS[[5, 4, 4, 3, 3, 2, 2, 1][y - 16]])
        hline(img, 16, 31, y, WALNUT[[4, 3, 3, 2, 2, 2, 1, 1][y - 16]])
    for y in range(24, 28):
        hline(img, 0, 31, y, WALNUT[[5, 3, 2, 1][y - 24]])
    save(img, "table_lamp.png")


def marquee():
    """Papan marquee di atas pintu 96x32 (3 tile): LIAR'S besar, TABLE kecil, bohlam keliling."""
    W, H = 96, 32
    img = new(W, H, hx("#0b0b0d"))
    bevel(img, 0, 0, W - 1, H - 1, BRASS[5], BRASS[1])
    bevel(img, 1, 1, W - 2, H - 2, BRASS[3], BRASS[2])
    bevel(img, 2, 2, W - 3, H - 3, hx("#020203"), hx("#18181b"))
    big = "LIAR'S"
    w = text_width(big, 3)
    text(img, big, (W - w) // 2, 5, BRASS[5], 3, shadow=BRASS[1])
    small = "TABLE"
    w2 = text_width(small, 1)
    text(img, small, (W - w2) // 2, 23, CREAM[3])
    hline(img, 12, (W - w2) // 2 - 4, 25, BRASS[3])
    hline(img, (W + w2) // 2 + 3, W - 13, 25, BRASS[3])
    glow = new(W, H)
    for x in range(5, W - 4, 6):
        for y in (4, H - 5):
            img.putpixel((x, y), AMBER[3])
            glow.putpixel((x, y), BULB)
    for y in range(10, H - 6, 6):
        for x in (4, W - 5):
            img.putpixel((x, y), AMBER[3])
            glow.putpixel((x, y), BULB)
    for tx in range(3):
        save(img.crop((tx * 32, 0, tx * 32 + 32, 32)), f"marquee/{tx}.png")
        save(glow.crop((tx * 32, 0, tx * 32 + 32, 32)), f"marquee/{tx}_glow.png")
    OUT["_marquee"] = img


def blinds():
    """Bilah kerai kayu krem (diterangi cyan dari luar di tepi atasnya) + tali tangga."""
    slat = new(16, 16)
    for y in range(16):
        k = [4, 4, 3, 3, 3, 2, 2, 1][y % 8]
        hline(slat, 0, 15, y, CREAM[k] if k > 0 else CREAM[0])
    for x in (3, 12):
        vline(slat, x, 0, 15, CREAM[1])
    save(slat, "window/slat.png")
    glow = new(16, 4)
    for x in range(16):
        put(glow, x, 0, hx("#9fdcf4"))
        put(glow, x, 1, hx("#5cc8f0"))
        put(glow, x, 2, hx("#3a8fb0"))
        put(glow, x, 3, hx("#24607a"))
    save(glow, "window/slat_glow.png")


def bistro():
    """Sheet meja bistro + kursi bentwood 32x32: daun walnut + bibir kuningan, besi cor, rotan dudukan."""
    img = new(32, 32)
    # Daun meja (0..15, 0..15) bundar.
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 8, (y - 7.5) / 8
            r = dx * dx + dy * dy
            if r > 0.92:
                c = BRASS[3]
            elif r > 0.8:
                c = WALNUT[2]
            else:
                c = WALNUT[4] if (x + y) % 7 else WALNUT[3]
                if -0.6 < dx + dy < -0.3:
                    c = WALNUT[5]
            img.putpixel((x, y), c)
    # Besi cor (16..31, 0..7).
    for y in range(8):
        hline(img, 16, 31, y, IRON[[4, 3, 3, 2, 2, 2, 1, 1][y]])
    # Kayu bengkok kursi (16..31, 8..15): walnut gelap mengilap.
    for y in range(8, 16):
        hline(img, 16, 31, y, WALNUT[[5, 4, 3, 3, 2, 2, 1, 1][y - 8]])
    # Anyaman rotan dudukan (0..15, 16..31).
    for y in range(16, 32):
        for x in range(16):
            c = hx("#8a6a3a") if (x + y) % 4 < 2 else hx("#6a4e28")
            if (x - y) % 4 == 0:
                c = hx("#4a3418")
            img.putpixel((x, y), c)
    # Gelas whiskey (16..23, 16..23): kaca bening, cairan amber, kilau.
    rect(img, 16, 16, 23, 23, hx("#5d7270"))
    rect(img, 17, 19, 22, 23, hx("#8a4a14"))
    vline(img, 17, 16, 22, hx("#c4d6d4"))
    hline(img, 17, 22, 19, hx("#c07a2a"))
    # Asbak besi (24..31, 16..23).
    rect(img, 24, 16, 31, 23, IRON[3])
    rect(img, 26, 18, 29, 21, IRON[1])
    put(img, 27, 19, hx("#5a5048"))
    save(img, "bistro.png")


def items():
    """Ikon item: gelas whiskey dan botol whiskey (16 px)."""
    g = new(16, 16)
    rect(g, 4, 6, 11, 14, hx("#5d7270"))
    rect(g, 5, 9, 10, 13, hx("#8a4a14"))
    hline(g, 5, 10, 9, hx("#c07a2a"))
    vline(g, 5, 6, 12, hx("#c4d6d4"))
    hline(g, 4, 11, 14, hx("#3a4848"))
    rect(g, 6, 10, 7, 11, hx("#d8e8e6"))
    save(g, "../item/whiskey_glass.png")
    b = new(16, 16)
    rect(b, 7, 1, 8, 1, BRASS[4])
    rect(b, 7, 2, 8, 5, hx("#3b2210"))
    rect(b, 5, 6, 10, 14, hx("#3b2210"))
    rect(b, 6, 9, 9, 14, hx("#8a4a14"))
    rect(b, 6, 8, 9, 11, CREAM[3])
    hline(b, 6, 9, 11, CREAM[1])
    vline(b, 6, 6, 13, hx("#7a5a3a"))
    hline(b, 5, 10, 15, hx("#24140a"))
    save(b, "../item/whiskey_bottle.png")


def particle_icons():
    """Ikon item untuk block bertile (pakai potongan tengah)."""
    pass


def sheet():
    """Contact sheet 8x untuk direview."""
    items = [(k, v) for k, v in OUT.items()]
    scale = 6
    cell = 64 * scale // 2
    cols = 6
    big = [(k, v) for k, v in items if k.startswith("_")]
    small = [(k, v) for k, v in items if not k.startswith("_") and "/" not in k or k.startswith("window/rain")]
    small = [(k, v) for k, v in small if v.size[0] <= 32 and v.size[1] <= 32]
    rows = (len(small) + cols - 1) // cols
    W = cols * (cell + 10) + 10
    H = rows * (cell + 30) + 10
    out = Image.new("RGB", (W, H), (40, 40, 44))
    d = ImageDraw.Draw(out)
    for i, (k, v) in enumerate(small):
        x = 10 + (i % cols) * (cell + 10)
        y = 10 + (i // cols) * (cell + 30)
        im = v.crop((0, 0, v.size[0], min(v.size[1], 32))) if k.startswith("window/rain") else v
        # Tile 2x2 untuk tekstur 16 px supaya kelihatan kesinambungan.
        if im.size == (16, 16):
            t = Image.new("RGBA", (32, 32))
            for a in (0, 16):
                for b in (0, 16):
                    t.paste(im, (a, b))
            im = t
        im = im.resize((cell, cell * im.size[1] // im.size[0]), Image.NEAREST)
        out.paste(im, (x, y), im)
        d.text((x, y + cell + 4), k, fill=(220, 220, 220))
    path = os.path.join(os.path.dirname(__file__), "..", "build", "block_sheet.png")
    out.save(path)
    bigw = sum(v.size[0] * 5 + 10 for _, v in big) + 10
    bigh = max(v.size[1] * 5 for _, v in big) + 20
    out2 = Image.new("RGB", (bigw, bigh), (40, 40, 44))
    x = 10
    for k, v in big:
        im = v.resize((v.size[0] * 5, v.size[1] * 5), Image.NEAREST)
        out2.paste(im, (x, 10), im)
        x += im.size[0] + 10
    out2.save(os.path.join(os.path.dirname(__file__), "..", "build", "block_sheet_big.png"))
    print("sheets written")


def main():
    floor()
    wallpaper()
    wallpaper_top()
    wainscot()
    tin()
    tar()
    brick()
    stone()
    walnut_planks()
    brass_rod()
    iron_tex()
    counter_front()
    counter_top()
    cabinet_front()
    bottle_rows()
    shelf_back()
    strip_glow()
    mirror()
    windows()
    window_frame()
    reveal()
    sconce()
    stool()
    fan()
    pipe()
    poster_pull()
    poster_bluff()
    door()
    sign()
    awning()
    bracket()
    booth()
    table_lamp()
    marquee()
    blinds()
    bistro()
    items()
    print("wrote", len([k for k in OUT if not k.startswith("_")]), "textures")
    if "--sheet" in sys.argv:
        sheet()


if __name__ == "__main__":
    main()
