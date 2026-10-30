"""Konsep aset pixel-art Art Bible: kartu 32x48 (2x kerapatan item vanilla) + profil revolver 1/32 blok."""
import sys, os
from PIL import Image, ImageDraw
OUT = sys.argv[1]
PAPER, PAPER2, INK, INK2, GOLD, GOLD2, RED = (238, 226, 198), (214, 196, 160), (22, 17, 14), (58, 46, 38), (201, 160, 74), (140, 104, 40), (214, 32, 42)
GLYPH = {  # 5x7
 "K": ["X...X", "X..X.", "X.X..", "XX...", "X.X..", "X..X.", "X...X"],
 "Q": [".XXX.", "X...X", "X...X", "X...X", "X.X.X", "X..X.", ".XX.X"],
 "A": ["..X..", ".X.X.", "X...X", "X...X", "XXXXX", "X...X", "X...X"],
 "J": ["X.X.X", ".XXX.", "XXXXX", ".XXX.", "X.X.X", ".....", "....."],  # bintang = wild
}
PIP = ["..X..", ".XXX.", "XXXXX", "XXXXX", "..X..", ".XXX."]

def glyph(img, g, x, y, col):
    for j, row in enumerate(g):
        for i, c in enumerate(row):
            if c == "X": img.putpixel((x + i, y + j), col)

def frame(rank):
    im = Image.new("RGBA", (32, 48), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 31, 47], fill=PAPER)
    for p in [(0, 0), (31, 0), (0, 47), (31, 47)]: im.putpixel(p, (0, 0, 0, 0))
    d.rectangle([0, 46, 31, 47], fill=PAPER2); d.rectangle([30, 1, 31, 46], fill=PAPER2)  # tebal kertas
    d.rectangle([2, 2, 29, 45], outline=INK2)
    for (x, y) in [(2, 2), (29, 2), (2, 45), (29, 45)]:  # sudut deco emas
        d.point([(x, y)], fill=GOLD)
    col = GOLD2 if rank == "J" else INK
    glyph(im, GLYPH[rank], 4, 4, col)
    glyph(im, GLYPH[rank], 23, 37, col)  # indeks kanan-bawah tegak (tidak dibalik): selalu kebaca
    return im, d

def art_king(d):
    # fedora
    d.rectangle([9, 13, 22, 17], fill=INK); d.point([(15, 13), (16, 13)], fill=PAPER)  # pinch
    d.rectangle([6, 18, 25, 19], fill=INK); d.rectangle([9, 17, 22, 17], fill=GOLD)  # pita emas
    # wajah: setengah gelap (Mignola)
    d.rectangle([11, 20, 20, 27], fill=INK); d.rectangle([11, 20, 14, 26], fill=PAPER2)
    d.point([(13, 22)], fill=INK); d.point([(17, 22)], fill=PAPER)  # mata
    d.rectangle([12, 25, 14, 25], fill=INK)
    # rokok + bara merah
    d.line([(17, 26), (21, 26)], fill=PAPER); d.point([(22, 26)], fill=GOLD)
    # bahu + kerah V + dasi
    d.polygon([(6, 41), (9, 30), (13, 28), (18, 28), (22, 30), (25, 41)], fill=INK)
    d.polygon([(13, 28), (15, 34), (18, 28)], fill=PAPER); d.line([(15, 30), (15, 37)], fill=GOLD)

def art_queen(d):
    d.ellipse([9, 12, 22, 22], fill=INK); d.rectangle([7, 19, 24, 20], fill=INK)  # cloche + brim
    d.rectangle([9, 18, 22, 18], fill=GOLD)
    d.rectangle([9, 21, 11, 29], fill=INK); d.rectangle([20, 21, 22, 29], fill=INK)  # bob
    d.rectangle([12, 21, 19, 29], fill=PAPER2); d.rectangle([16, 21, 19, 29], fill=INK)
    d.point([(14, 23)], fill=INK); d.rectangle([14, 27, 15, 27], fill=(150, 96, 84))
    d.polygon([(7, 41), (10, 32), (14, 30), (17, 30), (21, 32), (24, 41)], fill=INK)
    for x, y in [(12, 32), (13, 33), (14, 34), (15, 34), (16, 34), (17, 33), (18, 32)]:
        d.point([(x, y)], fill=PAPER)  # mutiara

def art_ace(d):
    # sekop besar dengan lubang silinder revolver
    d.ellipse([7, 20, 17, 32], fill=INK); d.ellipse([14, 20, 24, 32], fill=INK)
    d.polygon([(15, 10), (6, 25), (25, 25), (16, 10)], fill=INK)
    d.polygon([(15, 30), (12, 38), (19, 38), (16, 30)], fill=INK)
    for x, y in [(15, 18), (12, 21), (19, 21), (12, 26), (19, 26), (15, 28)]:
        d.rectangle([x, y, x + 1, y + 1], fill=PAPER)
    d.rectangle([15, 22, 16, 25], fill=GOLD)  # poros silinder

def art_joker(d):
    d.polygon([(6, 14), (11, 22), (15, 11), (20, 22), (25, 14), (22, 25), (9, 25)], fill=INK)  # topi badut
    for x, y in [(6, 13), (15, 10), (25, 13)]: d.rectangle([x - 1, y - 1, x, y], fill=GOLD)
    d.ellipse([9, 24, 22, 37], fill=INK)
    d.rectangle([11, 28, 13, 29], fill=PAPER); d.rectangle([18, 28, 20, 29], fill=PAPER)
    d.arc([10, 26, 21, 35], 20, 160, fill=GOLD); d.arc([10, 27, 21, 35], 20, 160, fill=GOLD)

def back():
    im = Image.new("RGBA", (32, 48), (20, 16, 14, 255)); d = ImageDraw.Draw(im)
    for p in [(0, 0), (31, 0), (0, 47), (31, 47)]: im.putpixel(p, (0, 0, 0, 0))
    d.rectangle([2, 2, 29, 45], outline=GOLD2); d.rectangle([4, 4, 27, 43], outline=GOLD)
    for y in range(7, 41, 4):
        for x in range(7, 26, 4):
            d.point([(x, y), (x + 2, y + 2)], fill=GOLD2)
    d.polygon([(15, 14), (22, 24), (15, 34), (9, 24)], outline=GOLD, fill=(20, 16, 14))
    d.rectangle([14, 23, 16, 25], fill=GOLD)
    return im

cards = {}
for r, fn in (("K", art_king), ("Q", art_queen), ("A", art_ace), ("J", art_joker)):
    im, d = frame(r); fn(d)
    d.rectangle([22, 36, 28, 44], fill=PAPER)
    glyph(im, GLYPH[r], 23, 37, GOLD2 if r == "J" else INK)
    cards[r] = im; im.save(os.path.join(OUT, f"card_{r}.png"))
b = back(); b.save(os.path.join(OUT, "card_back.png"))

# Revolver: profil samping (barel ke kanan), 1 px = 1/32 blok
REV = [
 "...HH.....................",
 "....HHH...................",
 ".....HHLLLLLLLLLLLLLLLLLS.",
 "....FFFFCCCCCBBBBBBBBBBBBB",
 "....FFFFKKKKKBBBBBBBBBBBBB",
 "....FFFFCCCCCRRRRRRRRRRR..",
 "....FFFFKKKKKF............",
 "...WFFFFCCCCCF............",
 "..WWWFFFFFTFFF............",
 "..WWWW...T..G.............",
 ".WWMWW....GG..............",
 ".WWWW.....................",
 "WWWWW.....................",
 "WWWW......................",
 ".WW.......................",
]
COL = {"B": (44, 50, 58), "L": (150, 162, 176), "S": (205, 212, 218), "R": (30, 34, 40), "F": (52, 58, 66), "C": (110, 120, 132), "K": (20, 22, 26),
       "H": (40, 44, 50), "T": (30, 32, 36), "G": (52, 58, 66), "W": (96, 54, 30), "M": (201, 160, 74)}
rv = Image.new("RGBA", (len(REV[0]), len(REV)), (0, 0, 0, 0))
for y, row in enumerate(REV):
    for x, c in enumerate(row):
        if c in COL: rv.putpixel((x, y), COL[c] + (255,))
rv.save(os.path.join(OUT, "revolver_profile.png"))
open(os.path.join(OUT, "revolver_profile.txt"), "w").write("\n".join(REV))

# lembar pratinjau 8x
S = 8
sheet = Image.new("RGB", (5 * 32 * S + 6 * 24, 48 * S + 48 + 13 * S * 2), (30, 26, 24))
for i, k in enumerate(["K", "Q", "A", "J"]):
    sheet.paste(cards[k].resize((32 * S, 48 * S), Image.NEAREST), (24 + i * (32 * S + 24), 24), cards[k].resize((32 * S, 48 * S), Image.NEAREST))
sheet.paste(b.resize((32 * S, 48 * S), Image.NEAREST), (24 + 4 * (32 * S + 24), 24), b.resize((32 * S, 48 * S), Image.NEAREST))
R = rv.resize((rv.width * S * 2, rv.height * S * 2), Image.NEAREST); sheet.paste(R, (24, 48 * S + 48), R)
sheet.save(os.path.join(OUT, "pixel_sheet.png")); print(sheet.size)
