"""Kartu Liar's Deck, pixel art 64x96 gaya Balatro (indeks pojok tegak, tanpa merah: merah hanya untuk LIAR!/DOR).

    python tools/make_cards.py            -> tulis ke src/main/resources/assets/liarsbar/textures/card
    python tools/make_cards.py <dir>      -> tulis ke <dir> (pratinjau)

Gaya: figur chunky di panel potret berwarna, outline gelap 1 px di setiap bagian, warna rata + satu nada
bayangan di sisi kanan-bawah + kilap 1 px di kiri-atas (cahaya konsisten dari kiri-atas, bukan pillow shading).
Bentuk digambar sebagai vektor 8x lalu di-threshold (tepi pixel tegas, tanpa anti-aliasing). Buatan sendiri
(lihat CREDITS.txt).
"""
import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

W, H, S = 64, 96, 8
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "liarsbar", "textures", "card")

LINE = (28, 24, 44)
CREAM, CREAM2, CREAM0 = (242, 236, 220), (206, 196, 178), (255, 252, 242)
WHITE, GREY, GREY2 = (250, 250, 246), (196, 198, 210), (138, 140, 160)
SKIN, SKIN2, SKIN0 = (244, 200, 156), (206, 150, 112), (255, 226, 190)
GOLD, GOLD2, GOLD0 = (250, 194, 56), (196, 128, 28), (255, 236, 150)
BLUE, BLUE2, BLUE0 = (64, 112, 206), (40, 70, 150), (120, 168, 240)
SKY, SKY2 = (150, 196, 236), (112, 160, 214)
PURP, PURP2, PURP0 = (140, 84, 176), (94, 52, 130), (186, 132, 214)
TEAL, TEAL2, TEAL0 = (48, 164, 150), (28, 112, 110), (110, 210, 190)
MINT, MINT2 = (162, 220, 196), (120, 186, 166)
HAIR, HAIR2 = (112, 70, 42), (76, 46, 28)

GLYPH = {
    "K": ["XX...XX", "XX..XX.", "XX.XX..", "XXXX...", "XXX....", "XXXX...", "XX.XX..", "XX..XX.", "XX...XX", "XX...XX"],
    "Q": [".XXXXX.", "XX...XX", "XX...XX", "XX...XX", "XX...XX", "XX...XX", "XX.X.XX", "XX..XX.", ".XXXXXX", ".....XX"],
    "A": ["..XXX..", "..XXX..", ".XX.XX.", ".XX.XX.", ".XX.XX.", "XX...XX", "XXXXXXX", "XX...XX", "XX...XX", "XX...XX"],
    "J": ["...X...", "...X...", "..XXX..", "XXXXXXX", ".XXXXX.", "..XXX..", ".XX.XX.", ".X...X.", "X.....X", "......."],
}
# Ikon kecil di bawah indeks (pengganti simbol suit): mahkota, tiara, sekop, bintang.
ICON = {
    "K": ["X.X.X", "XXXXX", "XXXXX"],
    "Q": [".X.X.", "XXXXX", ".XXX."],
    "A": ["..X..", ".XXX.", "XXXXX", "X.X.X", "..X.."],
    "J": ["..X..", "XXXXX", ".XXX.", ".X.X."],
}


def shift(m, dx, dy):
    """out(x, y) = m(x + dx, y + dy), di luar kanvas = 0."""
    o = Image.new("L", m.size, 0)
    o.paste(m, (-dx, -dy))
    return o


class Card:
    def __init__(self, base=None):
        self.im = Image.new("RGBA", (W, H), (0, 0, 0, 0) if base is None else base + (255,))

    def mask(self, fn):
        m = Image.new("L", (W * S, H * S), 0)
        fn(Pen(ImageDraw.Draw(m)))
        return m.resize((W, H), Image.BOX).point(lambda v: 255 if v >= 128 else 0)

    def part(self, col, fn, shade=None, hi=None, line=True, depth=2):
        """Satu bagian sprite: outline 1 px, isi rata, bayangan kanan-bawah, kilap kiri-atas."""
        m = self.mask(fn)
        if line:
            self.im.paste(LINE + (255,), (0, 0), m.filter(ImageFilter.MaxFilter(3)))
        self.im.paste(col + (255,), (0, 0), m)
        if shade:
            band = ImageChops.subtract(m, shift(m, depth, depth // 2))
            self.im.paste(shade + (255,), (0, 0), band)
        if hi:
            band = ImageChops.subtract(m, shift(m, -1, -1))
            self.im.paste(hi + (255,), (0, 0), band)
        return m

    def px(self, col, *pts):
        for x, y in pts:
            self.im.putpixel((x, y), col + (255,))

    def rect(self, col, x0, y0, x1, y1):
        ImageDraw.Draw(self.im).rectangle([x0, y0, x1, y1], fill=col + (255,))

    def glyph(self, rows, x, y, col):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == "X":
                    self.px(col, (x + i, y + j))


class Pen:
    """ImageDraw dengan koordinat ruang pixel kartu."""

    def __init__(self, d):
        self.d = d

    def poly(self, *pts):
        self.d.polygon([(x * S, y * S) for x, y in pts], fill=255)

    def ell(self, x0, y0, x1, y1):
        self.d.ellipse([x0 * S, y0 * S, x1 * S, y1 * S], fill=255)

    def rect(self, x0, y0, x1, y1):
        self.d.rectangle([x0 * S, y0 * S, x1 * S, y1 * S], fill=255)

    def line(self, pts, w):
        self.d.line([(x * S, y * S) for x, y in pts], fill=255, width=max(1, int(w * S)), joint="curve")


# ---------------------------------------------------------------- bingkai

PANEL = (13, 15, 50, 80)  # panel potret: x0, y0, x1, y1


def blank(face=CREAM):
    """Kartu kosong: kertas krem, tepi gelap membulat, bayangan tebal kertas di kanan-bawah."""
    c = Card()
    d = ImageDraw.Draw(c.im)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=4, fill=LINE + (255,))
    d.rounded_rectangle([1, 1, W - 2, H - 2], radius=3, fill=CREAM2 + (255,))
    d.rounded_rectangle([1, 1, W - 3, H - 3], radius=3, fill=face + (255,))
    d.line([(4, 1), (W - 6, 1)], fill=CREAM0 + (255,))
    return c


def index(c, rank, col, icon_col):
    g, ic = GLYPH[rank], ICON[rank]
    c.glyph(g, 4, 4, col)
    c.glyph(ic, 5, 16, icon_col)
    # Kanan-bawah tegak (tidak dibalik): selalu kebaca.
    c.glyph(g, W - 11, H - 14, col)
    c.glyph(ic, W - 10, H - 14 - len(ic) - 2, icon_col)


def panel(c, bg, bg2, rays=None):
    x0, y0, x1, y1 = PANEL
    d = ImageDraw.Draw(c.im)
    d.rectangle([x0 - 1, y0 - 1, x1 + 1, y1 + 1], fill=LINE + (255,))
    d.rectangle([x0, y0, x1, y1], fill=bg + (255,))
    if rays:  # sinar lebar halus dari tengah, khas latar kartu wajah
        m = Image.new("L", (W * S, H * S), 0)
        p = Pen(ImageDraw.Draw(m))
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2 - 4
        for i in range(12):
            a = 2 * math.pi * i / 12
            p.poly((cx, cy), (cx + math.cos(a - 0.13) * 80, cy + math.sin(a - 0.13) * 80),
                   (cx + math.cos(a + 0.13) * 80, cy + math.sin(a + 0.13) * 80))
        m = m.resize((W, H), Image.BOX).point(lambda v: 255 if v >= 128 else 0)
        clip = Image.new("L", (W, H), 0)
        ImageDraw.Draw(clip).rectangle([x0, y0, x1, y1], fill=255)
        c.im.paste(rays + (255,), (0, 0), ImageChops.multiply(m, clip))
    d.line([(x0, y1), (x1, y1)], fill=bg2 + (255,))
    d.line([(x1, y0), (x1, y1)], fill=bg2 + (255,))


def clip_panel(c, under):
    """Potong semua yang keluar panel (kecuali kertas di bawahnya) supaya figur tidak bocor ke bingkai."""
    x0, y0, x1, y1 = PANEL
    keep = Image.new("L", (W, H), 0)
    ImageDraw.Draw(keep).rectangle([x0, y0, x1, y1], fill=255)
    c.im = Image.composite(c.im, under, keep)


# ---------------------------------------------------------------- figur

def king(c):
    """Raja tua: mahkota emas besar, janggut putih lebat, jubah biru berkerah ermine, pedang di bahu."""
    # Pedang di belakang bahu kanan
    c.part(GREY, lambda p: p.poly((45, 17), (48, 21), (48, 64), (44, 64), (44, 21)), shade=GREY2, hi=WHITE)
    # Jubah
    c.part(BLUE, lambda p: p.poly((12, 82), (13, 70), (18, 64), (27, 61), (37, 61), (46, 64), (51, 70), (52, 82)),
           shade=BLUE2, hi=BLUE0, depth=4)
    c.part(GOLD, lambda p: p.rect(30.5, 66, 33.5, 82), shade=GOLD2, hi=GOLD0)  # lis emas
    # Kerah ermine
    c.part(WHITE, lambda p: p.poly((14, 70), (19, 63), (27, 60), (37, 60), (45, 63), (50, 70), (46, 72), (38, 67),
                                   (32, 68), (26, 67), (18, 72)), shade=GREY)
    for x, y in ((19, 67), (25, 64), (32, 64), (39, 64), (45, 67)):
        c.px(LINE, (x, y), (x, y + 1))
    # Gagang pedang + kepalan
    c.part(GOLD, lambda p: p.rect(40, 62, 52, 64.5), shade=GOLD2, hi=GOLD0)
    c.part(SKIN, lambda p: p.ell(41, 64, 49, 71), shade=SKIN2, hi=SKIN0)
    c.px(SKIN2, (43, 66), (43, 68))
    # Kepala
    c.part(SKIN, lambda p: p.ell(22, 28, 42, 52), shade=SKIN2, hi=SKIN0)
    c.part(SKIN, lambda p: p.ell(19.5, 37, 24, 44), shade=SKIN2)  # telinga
    c.part(SKIN, lambda p: p.ell(40, 37, 44.5, 44), shade=SKIN2)
    # Rambut putih di samping
    c.part(WHITE, lambda p: p.poly((21, 32), (24, 30), (24, 38), (21, 40)), shade=GREY)
    c.part(WHITE, lambda p: p.poly((43, 32), (40, 30), (40, 38), (43, 40)), shade=GREY)
    # Janggut putih lebat
    c.part(WHITE, lambda p: p.poly((21, 41), (25, 45), (32, 47), (39, 45), (43, 41), (43, 52), (39, 60), (35, 64),
                                   (32, 66), (29, 64), (25, 60), (21, 52)), shade=GREY, depth=3)
    c.px(GREY, (27, 53), (28, 54), (32, 55), (33, 56), (36, 53), (29, 59), (34, 60))
    # Mata, alis, hidung
    c.rect(LINE, 26, 37, 28, 38)
    c.rect(LINE, 36, 37, 38, 38)
    c.px(WHITE, (26, 37), (36, 37))
    c.rect(GREY2, 25, 35, 29, 35)  # alis putih tebal (tanpa outline: biar tidak kebaca kacamata)
    c.rect(GREY2, 35, 35, 39, 35)
    c.px(GREY2, (24, 36), (40, 36))
    c.part(SKIN, lambda p: p.poly((31, 38), (33, 38), (34.5, 43), (29.5, 43)), shade=SKIN2, hi=SKIN0)
    # Kumis + mulut
    c.part(WHITE, lambda p: p.poly((24.5, 46), (28, 43.5), (32, 44.5), (36, 43.5), (39.5, 46), (36, 47.5),
                                   (32, 46.5), (28, 47.5)), shade=GREY)
    c.rect(LINE, 30, 48, 34, 48)
    # Mahkota
    c.part(GOLD, lambda p: p.poly((21, 31), (20, 17), (25, 23), (28.5, 14), (32, 22), (35.5, 14), (39, 23),
                                  (44, 17), (43, 31)), shade=GOLD2, hi=GOLD0, depth=3)
    c.part(GOLD, lambda p: p.rect(20.5, 27, 43.5, 31), shade=GOLD2, hi=GOLD0)
    for x, col in ((25, TEAL), (32, BLUE), (39, TEAL)):
        c.part(col, lambda p, x=x: p.ell(x - 1.6, 27.5, x + 1.6, 30.7), hi=WHITE)
    for x, y in ((20, 16), (28.5, 13), (35.5, 13), (44, 16)):
        c.part(GOLD0, lambda p, x=x, y=y: p.ell(x - 1.4, y - 1.4, x + 1.4, y + 1.4))


def queen(c):
    """Ratu: mahkota emas ramping, rambut cokelat bergelombang, gaun ungu, kalung mutiara, setangkai bunga."""
    # Rambut belakang (jatuh ke bahu)
    c.part(HAIR, lambda p: p.poly((19, 36), (22, 27), (32, 24), (42, 27), (45, 36), (46, 52), (44, 62), (20, 62),
                                  (18, 52)), shade=HAIR2, depth=3)
    # Gaun
    c.part(PURP, lambda p: p.poly((12, 82), (13, 70), (18, 64), (26, 61), (38, 61), (46, 64), (51, 70), (52, 82)),
           shade=PURP2, hi=PURP0, depth=4)
    c.part(SKIN, lambda p: p.poly((23, 61), (41, 61), (37, 68), (32, 70), (27, 68)), shade=SKIN2)  # dada
    c.part(GOLD, lambda p: p.poly((22, 62), (27, 68), (32, 70), (37, 68), (42, 62), (43, 64), (37, 71), (32, 73),
                                  (27, 71), (21, 64)), shade=GOLD2, hi=GOLD0)  # garis leher
    # Leher + kepala
    c.part(SKIN, lambda p: p.rect(28.5, 50, 35.5, 61), shade=SKIN2)
    c.part(SKIN, lambda p: p.ell(23, 30, 41, 54), shade=SKIN2, hi=SKIN0)
    # Kalung mutiara
    for i in range(7):
        t = i / 6
        x = round(28 + t * 8)
        y = round(58 + math.sin(t * math.pi) * 2.5)
        c.px(WHITE, (x, y))
    # Poni bergelombang
    c.part(HAIR, lambda p: p.poly((22, 40), (23, 30), (32, 27), (41, 30), (42, 40), (39, 34), (35, 33), (32, 36),
                                  (29, 33), (25, 34)), shade=HAIR2, hi=(150, 100, 62))
    # Mata (bulu mata), pipi, hidung, bibir ungu tua
    c.rect(LINE, 27, 41, 29, 42)
    c.rect(LINE, 35, 41, 37, 42)
    c.px(WHITE, (28, 41), (36, 41))
    c.px(LINE, (26, 40), (38, 40))
    c.px(SKIN2, (32, 43), (32, 44), (31, 45))
    c.px((236, 170, 150), (26, 46), (27, 46), (37, 46), (38, 46))  # rona pipi (oranye pucat, bukan merah)
    c.rect(PURP2, 30, 48, 34, 48)
    c.px(PURP, (31, 49), (32, 49), (33, 49))
    # Anting
    c.part(WHITE, lambda p: p.ell(21.5, 46, 24, 48.5))
    c.part(WHITE, lambda p: p.ell(40, 46, 42.5, 48.5))
    # Bunga di tangan kiri
    c.part(TEAL2, lambda p: p.line([(19, 80), (20, 72), (19, 66)], 1.0))
    c.part(SKIN, lambda p: p.ell(16, 71, 23, 78), shade=SKIN2, hi=SKIN0)
    c.part(SKY, lambda p: p.ell(15, 61, 23, 69), shade=SKY2, hi=WHITE)
    c.px(GOLD, (19, 65), (18, 64))
    # Mahkota
    c.part(GOLD, lambda p: p.poly((24, 31), (23.5, 23), (27.5, 26.5), (32, 18), (36.5, 26.5), (40.5, 23), (40, 31)),
           shade=GOLD2, hi=GOLD0)
    c.part(GOLD, lambda p: p.rect(23.5, 28, 40.5, 31), shade=GOLD2, hi=GOLD0)
    c.part(PURP, lambda p: p.ell(30.3, 21.5, 33.7, 25), hi=WHITE)
    for x in (27, 37):
        c.part(TEAL, lambda p, x=x: p.ell(x - 1.3, 28.6, x + 1.3, 30.6))


def ace(c):
    """Ace of Spades: sekop biru tua besar berkilap, cincin emas berisi silinder revolver, sulur emas."""
    cx = 31.5

    def spade(p):
        p.ell(14, 35, 32.5, 58)
        p.ell(30.5, 35, 49, 58)
        p.poly((cx, 17), (14.5, 45), (48.5, 45))
        p.poly((cx - 2, 54), (cx - 3, 63), (cx - 6, 69), (cx - 9, 72), (cx + 9, 72), (cx + 6, 69), (cx + 3, 63),
               (cx + 2, 54))

    c.part(BLUE2, spade, shade=LINE, hi=BLUE, depth=3)
    c.part(BLUE, lambda p: p.poly((cx - 1, 22), (18, 42), (21, 42)), line=False)  # kilap besar sisi cahaya
    c.part(BLUE, lambda p: p.ell(17, 39, 22, 47), line=False)
    # Cincin emas + silinder
    c.part(GOLD, lambda p: p.ell(cx - 9, 34, cx + 9, 52), shade=GOLD2, hi=GOLD0)
    c.part(GREY, lambda p: p.ell(cx - 6.5, 36.5, cx + 6.5, 49.5), shade=GREY2, hi=WHITE)
    for i in range(6):
        a = math.radians(-90 + 60 * i)
        hx, hy = cx + math.cos(a) * 3.8, 43 + math.sin(a) * 3.8
        c.part(LINE, lambda p, hx=hx, hy=hy: p.ell(hx - 1.3, hy - 1.3, hx + 1.3, hy + 1.3), line=False)
    c.part(GOLD, lambda p: p.ell(cx - 1, 37.5, cx + 1, 39.5), line=False)  # satu peluru
    c.px(GREY2, (31, 43))


def joker(c):
    """Joker: badut menyeringai lebar, topi tiga ujung ungu/emas/teal berlonceng, kerah ruff, latar teal."""
    # Kerah ruff + baju
    c.part(PURP, lambda p: p.poly((12, 82), (14, 72), (22, 66), (42, 66), (50, 72), (52, 82)), shade=PURP2, hi=PURP0,
           depth=4)
    for i, x in enumerate((20, 32, 44)):
        c.part(GOLD, lambda p, x=x: p.poly((x, 70), (x + 4, 76), (x, 82), (x - 4, 76)), shade=GOLD2, hi=GOLD0)

    def ruff(p):
        for i in range(8):
            x = 16 + i * 4.6
            p.ell(x - 3.4, 57, x + 3.4, 69)

    c.part(WHITE, ruff, shade=GREY, depth=2)
    # Kepala
    c.part(SKIN0, lambda p: p.ell(21, 32, 43, 60), shade=SKIN, hi=WHITE, depth=3)
    # Cat badut: berlian teal di mata, pipi
    for x in (27, 37):
        c.part(TEAL, lambda p, x=x: p.poly((x, 38.5), (x + 3.2, 42.5), (x, 46.5), (x - 3.2, 42.5)), shade=TEAL2)
        c.rect(LINE, x - 1, 42, x, 43)
        c.px(WHITE, (x - 1, 42))
    c.part(PURP, lambda p: p.ell(29.8, 45, 34.2, 49.4), shade=PURP2, hi=PURP0)  # hidung bola
    # Seringai lebar dengan gigi
    c.part(LINE, lambda p: p.poly((23.5, 49), (27, 52.5), (32, 53.5), (37, 52.5), (40.5, 49), (38.5, 55), (32, 57.5),
                                  (25.5, 55)), line=False)
    c.rect(WHITE, 27, 53, 37, 54)
    for x in (29, 32, 35):
        c.px(LINE, (x, 53), (x, 54))
    c.px(SKIN, (23, 48), (41, 48))
    # Topi tiga ujung
    c.part(PURP, lambda p: p.poly((21, 37), (19, 29), (14, 24), (13, 30), (11, 35), (15, 33), (17, 37)),
           shade=PURP2, hi=PURP0)
    c.part(TEAL, lambda p: p.poly((43, 37), (45, 29), (50, 24), (51, 30), (53, 35), (49, 33), (47, 37)),
           shade=TEAL2, hi=TEAL0)
    c.part(GOLD, lambda p: p.poly((20, 38), (22, 28), (27, 22), (32, 15), (37, 22), (42, 28), (44, 38)),
           shade=GOLD2, hi=GOLD0, depth=3)
    c.part(PURP, lambda p: p.poly((27, 38), (28.5, 27), (32, 19), (35.5, 27), (37, 38)), shade=PURP2, hi=PURP0)
    c.part(WHITE, lambda p: p.rect(19.5, 35, 44.5, 39), shade=GREY)  # pita topi
    for x in (23, 28, 33, 38, 42):
        c.px(GOLD, (x, 37))
    for x, y in ((11, 35.5), (53, 35.5), (32, 14)):  # lonceng
        c.part(GOLD, lambda p, x=x, y=y: p.ell(x - 2.4, y - 2.4, x + 2.4, y + 2.4), shade=GOLD2, hi=GOLD0)


def back():
    """Punggung: biru tua, tepi krem, kisi belah ketupat emas, medali silinder di tengah."""
    c = blank()
    d = ImageDraw.Draw(c.im)
    d.rounded_rectangle([4, 4, W - 5, H - 5], radius=2, fill=LINE + (255,))
    d.rectangle([5, 5, W - 6, H - 6], fill=BLUE2 + (255,))
    for y in range(5, H - 5):
        for x in range(5, W - 5):
            u, v = (x + y) % 10, (x - y) % 10
            if u == 0 or v == 0:
                c.px(BLUE, (x, y))
            if u == 5 and v == 5:
                c.px(GOLD, (x, y))
    d.rectangle([7, 7, W - 8, H - 8], outline=GOLD + (255,))
    # Medali tengah
    c.part(GOLD, lambda p: p.poly((32, 26), (48, 48), (32, 70), (16, 48)), shade=GOLD2, hi=GOLD0, depth=3)
    c.part(BLUE2, lambda p: p.poly((32, 31), (44, 48), (32, 65), (20, 48)), shade=LINE, hi=BLUE)
    c.part(GOLD, lambda p: p.ell(25, 41, 39, 55), shade=GOLD2, hi=GOLD0)
    for i in range(6):
        a = math.radians(-90 + 60 * i)
        hx, hy = 32 + math.cos(a) * 3.6, 48 + math.sin(a) * 3.6
        c.part(LINE, lambda p, hx=hx, hy=hy: p.ell(hx - 1.2, hy - 1.2, hx + 1.2, hy + 1.2), line=False)
    for y in (17, H - 18):
        c.part(GOLD, lambda p, y=y: p.poly((32, y - 3), (35, y), (32, y + 3), (29, y)), shade=GOLD2, hi=GOLD0)
    return c.im


def face(rank, art, bg, bg2, rays, ink, icon):
    c = blank()
    panel(c, bg, bg2, rays)
    under = c.im.copy()
    art(c)
    clip_panel(c, under)
    index(c, rank, ink, icon)
    return c.im


def main():
    os.makedirs(OUT, exist_ok=True)
    cards = {
        "king": face("K", king, MINT, MINT2, (178, 230, 208), BLUE2, GOLD2),
        "queen": face("Q", queen, SKY, SKY2, (170, 210, 242), PURP2, GOLD2),
        "ace": face("A", ace, CREAM, CREAM2, CREAM0, LINE, BLUE2),
        "joker": face("J", joker, TEAL, TEAL2, TEAL0, PURP2, GOLD2),
        "back": back(),
    }
    for name, im in cards.items():
        path = os.path.join(OUT, name + ".png")
        im.save(path)
        print("wrote", os.path.relpath(path))


if __name__ == "__main__":
    main()
