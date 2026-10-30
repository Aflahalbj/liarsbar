"""Board Art Bible Fase A (1920x1080): cover, moodboard, resep lighting, HUD, shot list, aset, perbandingan."""
import sys, os, math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
sys.path.insert(0, os.path.dirname(__file__))
from hud import font, spaced, PAPER, BRASS, RED, INK, CYAN, keyhints, skin_face
from cam import *

SP = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUT = SP + "/frames"
REF = SP + "/refvid/full"
BG = (11, 11, 14)
DIM = (150, 144, 132)
W_, H_ = 1920, 1080


def canvas():
    im = Image.new("RGBA", (W_, H_), BG + (255,))
    rng = np.random.default_rng(4)
    g = (rng.normal(0, 3.0, (H_, W_))).astype(np.float32)
    a = np.asarray(im, np.float32)
    a[..., :3] += g[..., None]
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


def header(im, no, title, kicker):
    d = ImageDraw.Draw(im)
    spaced(d, (80, 58), "LIAR'S TABLE  ·  ART BIBLE  ·  FASE A", font("mono", 20), BRASS + (255,), 2)
    spaced(d, (W_ - 80, 58), f"{no:02d}", font("mono", 20), DIM + (255,), 2, anchor="ra")
    d.text((76, 92), title, font=font("disp", 92, "Black"), fill=PAPER + (255,))
    if kicker:
        d.text((80, 196), kicker, font=font("serif_i", 30), fill=DIM + (255,))
    d.line([(80, 250), (W_ - 80, 250)], fill=(60, 54, 46, 255), width=2)


def wrap(d, text, f, width):
    lines, cur = [], ""
    for para in text.split("\n"):
        cur = ""
        for w in para.split(" "):
            t = (cur + " " + w).strip()
            if d.textlength(t, font=f) <= width:
                cur = t
            else:
                lines.append(cur)
                cur = w
        lines.append(cur)
    return lines


def para(d, xy, text, f, fill, width, lh=None):
    x, y = xy
    lh = lh or int(f.size * 1.38)
    for ln in wrap(d, text, f, width):
        d.text((x, y), ln, font=f, fill=fill)
        y += lh
    return y


def fit(img, w, h):
    s = max(w / img.width, h / img.height)
    im = img.resize((int(img.width * s + 0.5), int(img.height * s + 0.5)), Image.LANCZOS)
    l, t = (im.width - w) // 2, (im.height - h) // 2
    return im.crop((l, t, l + w, t + h))


def contain(img, w, h):
    s = min(w / img.width, h / img.height)
    return img.resize((int(img.width * s), int(img.height * s)), Image.LANCZOS)


def save(im, name):
    os.makedirs(OUT, exist_ok=True)
    im.convert("RGB").save(f"{OUT}/{name}.png")
    print("saved", name)


# ---------------------------------------------------------------- cover
def cover():
    base = Image.open(OUT + "/sf_rt.png").convert("RGB")
    im = Image.new("RGBA", (W_, H_), (0, 0, 0, 255))
    im.paste(base, (460, 0))
    shade = Image.new("RGBA", im.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shade)
    for x in range(1100):
        sd.line([(x, 0), (x, H_)], fill=(8, 8, 10, int(250 * min(1, (1 - x / 1100) * 1.6) ** 1.2)))
    im.alpha_composite(shade)
    d = ImageDraw.Draw(im)
    spaced(d, (96, 190), "ART BIBLE  ·  FASE A", font("mono", 24), BRASS + (255,), 3)
    d.text((90, 230), "LIAR'S", font=font("disp", 190, "Black"), fill=PAPER + (255,))
    d.text((90, 400), "TABLE", font=font("disp", 190, "Black"), fill=PAPER + (255,))
    d.text((96, 610), "Noir, kotak, dan satu peluru.", font=font("serif_i", 44), fill=PAPER + (230,))
    pillars = [("01", "Hitam pekat, cahaya tegas", "Kolam lampu, rim cyan, asap cuma di berkas."),
               ("02", "Info ada di dunia", "Kartu di tangan, lampu = giliran, kapur = chamber."),
               ("03", "Pistol adalah bintang", "Siluet jelas, tidak tertutup lengan, kamera menunggu.")]
    y = 720
    for no, t, s in pillars:
        spaced(d, (98, y + 4), no, font("mono_b", 22), BRASS + (255,), 2)
        d.text((150, y - 6), t, font=font("disp", 38, "Bold"), fill=PAPER + (255,))
        d.text((150, y + 38), s, font=font("sans", 23), fill=DIM + (255,))
        y += 92
    save(im, "00_cover")


# ---------------------------------------------------------------- moodboard
REFS = [
    ("deerhunter_06", "The Deer Hunter", "1978 · Michael Cimino · DP Vilmos Zsigmond", "FRAMING / AKTING",
     "Blocking roulette: two-shot lintas meja, siku keluar, pistol profil di pelipis. Kamera nahan lama sebelum tarikan."),
    ("basterds_14:0,60,430,262", "Inglourious Basterds", "2009 · adegan La Louisiane · DP Robert Richardson", "LIGHTING / PACING",
     "Lampu praktikal selalu masuk frame. Tegang dibangun pelan lewat insert tangan & gelas sebelum meledak."),
    ("casino_19", "Casino Royale", "2006 · Martin Campbell · DP Phil Méheux", "INSERT / REVEAL",
     "Insert kartu & tangan setinggi meja, felt gelap di bawah kolam lampu. Reveal kartu satu per satu."),
    ("goodbad_10", "The Good, the Bad and the Ugly", "1966 · Sergio Leone · DP Tonino Delli Colli", "EDITING",
     "Potongan makin pendek menjelang tembakan. Tangan + pistol jadi bintang, bukan muka penuh layar."),
    ("bladerunner_02", "Blade Runner", "1982 · Ridley Scott · DP Jordan Cronenweth", "LIGHTING / HAZE",
     "Berkas cahaya keras menembus asap, rim dari jendela. Amber di dalam, cyan dari luar."),
    ("skyfall_08", "Skyfall — Shanghai", "2012 · Sam Mendes · DP Roger Deakins", "SILUET",
     "Siluet hitam murni melawan latar cyan terang, tanpa fill. Cocok banget untuk model kotak."),
    ("sincity_08", "Sin City", "2005 · Robert Rodriguez & Frank Miller", "DISIPLIN WARNA",
     "Hitam pekat, putih keras, dan cuma satu warna aksen. Kita: merah, maksimal ~2% frame."),
    ("arcane_07", "Arcane — The Last Drop", "2021 · Fortiche / Riot Games", "STYLIZED + ANIMASI",
     "Karakter stylized di bar gelap: kolam praktikal hangat, rim tegas, akting dengan berat dan jeda."),
    ("mignola_04", "Hellboy (komik)", "Mike Mignola", "BENTUK & BAYANGAN",
     "Bentuk dibangun dari blok hitam besar; separuh wajah boleh tenggelam. Resep untuk kepala kotak MC."),
    ("bass_04", "Anatomy of a Murder — titles", "1959 · title design Saul Bass", "TIPOGRAFI / UI",
     "Huruf kondensasi + potongan kertas, hitam + satu warna. Bahasa UI, stempel LIAR!, champion card."),
    ("inscryption_09:150,40,854,352", "Inscryption", "2021 · Daniel Mullins Games", "HUD DIEGETIK",
     "Info ada di benda: lilin, timbangan, kartu fisik. Di luar lingkar lampu: kosong."),
    ("buckshot_08", "Buckshot Roulette", "2023 · Mike Klubnika", "HUD MINIMAL",
     "“Diegetic systems and minimal UI”: penghitung di objek meja, satu lampu, pistol di pusat meja."),
]


def trim(img):
    a = np.asarray(img.convert("L"), np.float32)
    rows = np.where(a.mean(1) > 14)[0]
    cols = np.where(a.mean(0) > 14)[0]
    if len(rows) and len(cols):
        img = img.crop((cols[0], rows[0], cols[-1] + 1, rows[-1] + 1))
    return img


def ref_img(key):
    name, _, box = key.partition(":")
    im = Image.open(f"{REF}/{name}.png").convert("RGB")
    if box:
        im = im.crop(tuple(int(v) for v in box.split(",")))
    return trim(im)


def moodboard(part):
    im = canvas()
    header(im, 6 + part, "MOODBOARD" + (" I" if part == 0 else " II"),
           "Referensi dari film, komik, dan game — bukan dari Liar's Bar. Yang diambil ditulis spesifik.")
    d = ImageDraw.Draw(im)
    refs = REFS[part * 6:(part + 1) * 6]
    cw, ch = 560, 236
    for k, (img, title, meta, tag, take) in enumerate(refs):
        col, row = k % 3, k // 3
        x, y = 80 + col * (cw + 40), 280 + row * 390
        pic = fit(ref_img(img), cw, ch)
        im.paste(pic, (x, y))
        spaced(d, (x, y + ch + 14), tag, font("mono_b", 16), BRASS + (255,), 2)
        d.text((x, y + ch + 34), title, font=font("disp", 32, "Bold"), fill=PAPER + (255,))
        d.text((x, y + ch + 72), meta, font=font("sans", 16), fill=DIM + (255,))
        para(d, (x, y + ch + 96), take, font("sans", 19), (205, 198, 186, 255), cw, 25)
    save(im, f"0{6 + part}_moodboard_{'ab'[part]}")


# ---------------------------------------------------------------- lighting
def swatch(d, x, y, col, name, hexs, note, w=250):
    d.rectangle([x, y, x + w, y + 110], fill=col + (255,), outline=(70, 64, 56, 255), width=1)
    d.text((x, y + 122), name, font=font("disp", 28, "Bold"), fill=PAPER + (255,))
    d.text((x, y + 156), hexs, font=font("mono", 19), fill=BRASS + (255,))
    para(d, (x, y + 184), note, font("sans", 18), DIM + (255,), w, 24)


def rig_diagram(im, x0, y0, w, h):
    d = ImageDraw.Draw(im)
    d.text((x0, y0), "RIG (TAMPAK ATAS, 4 KURSI)", font=font("mono_b", 18), fill=BRASS + (255,))
    s = 58
    cx, cy = x0 + 400, y0 + 215
    wx = cx - 4.6 * s
    ov = Image.new("RGBA", im.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    for z0, z1 in ((-2.4, -0.8), (0.8, 2.4)):
        od.polygon([(wx, cy + z0 * s * 0.55), (wx, cy + z1 * s * 0.55), (cx - 1.3 * s, cy + (z1 * 0.55 + 0.5) * s),
                    (cx - 1.3 * s, cy + (z0 * 0.55 + 0.5) * s)], fill=(92, 200, 240, 46))
        od.rectangle([wx - 7, cy + z0 * s * 0.55, wx, cy + z1 * s * 0.55], fill=CYAN + (255,))
    for i in range(4):
        th = math.radians(-90 + 90 * i)
        sx, sz = 1.95 * math.cos(th), -1.95 * math.sin(th)
        k = 1 - 0.75 / 1.95
        r = (0.62 if i == 0 else 0.5) * s
        od.ellipse([cx + sx * k * s - r, cy + sz * k * s - r, cx + sx * k * s + r, cy + sz * k * s + r],
                   fill=(255, 167, 90, 150 if i == 0 else 40))
    d.ellipse([cx - 1.5 * s, cy - 1.5 * s, cx + 1.5 * s, cy + 1.5 * s], fill=(30, 59, 51, 255), outline=(90, 58, 36, 255), width=6)
    im.alpha_composite(ov)
    d = ImageDraw.Draw(im)
    for i in range(4):
        th = math.radians(-90 + 90 * i)
        sx, sz = 1.95 * math.cos(th), -1.95 * math.sin(th)
        k = 1 - 0.75 / 1.95
        d.ellipse([cx + sx * k * s - 5, cy + sz * k * s - 5, cx + sx * k * s + 5, cy + sz * k * s + 5], fill=(255, 226, 176, 255))
        d.rectangle([cx + sx * s - 13, cy + sz * s - 13, cx + sx * s + 13, cy + sz * s + 13], fill=(80, 74, 66, 255))
        d.text((cx + sx * s, cy + sz * s), str(i), font=font("mono_b", 16), fill=INK + (255,), anchor="mm")
    d.text((wx - 8, cy - 1.95 * s), "jendela hujan: rim + berkas cyan", font=font("sans", 17), fill=CYAN + (255,))
    para(d, (x0, cy + 2.3 * s), "Lampu gantung 2,45 blok di atas lantai, tepat di atas bibir meja depan tiap kursi, kerucut 72°. "
         "Kursi 0 sedang giliran: lampunya 100%, lainnya ±17%.", font("sans", 17), DIM + (255,), 590, 23)


def lighting():
    im = canvas()
    header(im, 8, "RESEP LIGHTING", "Tungsten lawan cyan, satu aksen merah. Hitam yang benar-benar hitam.")
    d = ImageDraw.Draw(im)
    sw = [((255, 167, 90), "TUNGSTEN 2700K", "#FFA75A", "Key semua lampu gantung. Inti bohlam #FFE2B0."),
          ((92, 200, 240), "CYAN JENDELA", "#5CC8F0", "Rim + berkas dari jendela hujan & kota."),
          ((214, 32, 42), "AKSEN: MERAH", "#D6202A", "Cuma: stempel LIAR!, kartu bohong terbakar, DOR. ≤ 2% frame."),
          ((7, 8, 11), "HITAM", "#07080B", "Lantai hitam. Bukan cokelat. Bukan 0,0,0 rata.")]
    for k, (c, n, hx, note) in enumerate(sw):
        swatch(d, 80 + k * 290, 290, c, n, hx, note)
    mats = [((30, 59, 51), "Felt", "#1E3B33"), ((58, 36, 22), "Walnut", "#3A2416"), ((201, 160, 74), "Kuningan", "#C9A04A"),
            ((237, 227, 201), "Kertas", "#EDE3C9")]
    d.text((80, 560), "MATERIAL KUNCI", font=font("mono_b", 18), fill=BRASS + (255,))
    for k, (c, n, hx) in enumerate(mats):
        x = 80 + k * 145
        d.rectangle([x, 592, x + 125, 652], fill=c + (255,))
        d.text((x, 662), n, font=font("sans", 18), fill=PAPER + (255,))
        d.text((x, 686), hx, font=font("mono", 16), fill=DIM + (255,))
    d.text((80, 740), "RASIO & NILAI", font=font("mono_b", 18), fill=BRASS + (255,))
    rules = [("Key : fill (pemain aktif)", "8 : 1  (3 stop)"),
             ("Lampu pemain lain", "≈ 17% lampu aktif (−2,5 stop)"),
             ("Rim cyan", "0,6–1× key, hanya tepi yang lawan latar"),
             ("Latar / dinding", "≤ 4% luma, kecuali praktikal"),
             ("Haze", "di dalam kerucut lampu saja, ±46°"),
             ("Histogram", "±75–85% piksel di bawah 12% luma (di luar letterbox)")]
    y = 772
    for a, b in rules:
        d.text((80, y), a, font=font("sans", 21), fill=(205, 198, 186, 255))
        d.text((470, y), b, font=font("mono_b", 21), fill=PAPER + (255,))
        y += 36
    rig_diagram(im, 1240, 290, 600, 420)
    d.text((1240, 760), "WAJIB DI TIAP FRAME", font=font("mono_b", 18), fill=BRASS + (255,))
    must = ["Muka pemain aktif selalu kebaca (pantulan meja + key)", "Berkas cahaya + asap kelihatan di kerucut lampu",
            "Bohlam glow (emissive + bloom) setiap kali lampu masuk frame", "Tidak ada permukaan oranye rata: meja felt + walnut",
            "Lampu kursi yang mati = padam total"]
    y = 792
    for m in must:
        d.rectangle([1240, y + 9, 1250, y + 19], fill=RED + (255,))
        d.text((1266, y), m, font=font("sans", 21), fill=(205, 198, 186, 255))
        y += 36
    save(im, "08_lighting")


# ---------------------------------------------------------------- HUD
def tab_panel(im, x, y):
    d = ImageDraw.Draw(im)
    w, h = 520, 330
    panel = Image.new("RGBA", (w, h), (10, 10, 12, 215))
    pd = ImageDraw.Draw(panel)
    pd.rectangle([0, 0, w - 1, h - 1], outline=(70, 60, 45, 255), width=2)
    spaced(pd, (24, 22), "TAB · MEJA #1 · RONDE 3", font("mono_b", 17), BRASS + (255,), 2)
    rows = [("efe", "Dev", 3, 1, False, True), ("steve", "Steve", 2, 3, False, False), ("alex", "Alex", 4, 0, False, False),
            ("makena", "Makena", 0, 4, True, False)]
    yy = 64
    for skin, name, cards, pulls, dead, me in rows:
        face = skin_face(SP + f"/mc/assets/minecraft/textures/entity/player/wide/{skin}.png", 5)
        if dead:
            face = Image.fromarray((np.asarray(face.convert("L").convert("RGBA"), np.float32) * [0.35, 0.35, 0.35, 1]).astype(np.uint8))
        panel.alpha_composite(face, (24, yy))
        nf = font("serif_i", 30)
        pd.text((84, yy + 2), name, font=nf, fill=(PAPER if not dead else (110, 104, 96)) + (255,))
        if dead:
            pd.line([(84, yy + 22), (84 + pd.textlength(name, font=nf), yy + 22)], fill=(110, 104, 96, 255), width=3)
        if me:
            pd.text((84 + pd.textlength(name, font=nf) + 10, yy + 12), "(kamu)", font=font("mono", 16), fill=DIM + (255,))
        for c in range(cards):
            pd.rounded_rectangle([270 + c * 16, yy + 6, 270 + c * 16 + 14, yy + 32], 2, fill=(30, 24, 20, 255), outline=BRASS + (255,))
        for k in range(6):
            cxk = 370 + k * 22
            col = RED if (dead and k == pulls - 1) else (PAPER if k < pulls else (60, 56, 50))
            pd.ellipse([cxk, yy + 12, cxk + 14, yy + 26], fill=col + (255,))
        yy += 62
    pd.text((24, h - 34), "kartu di tangan · tarikan (dari 6) · merah = DOR", font=font("mono", 15), fill=DIM + (255,))
    im.alpha_composite(panel, (x, y))


def hudboard():
    im = canvas()
    header(im, 9, "HUD DIEGETIK", "Info ada di dunia. Layar cuma pegang yang benar-benar tidak bisa di-diegetik-kan.")
    d = ImageDraw.Draw(im)
    pov = Image.open(OUT + "/sf_pov.png").convert("RGBA")
    pw, ph = 1120, 630
    small = pov.resize((pw, ph), Image.LANCZOS)
    ox, oy = 80, 290
    im.alpha_composite(small, (ox, oy))
    s = pw / 1920
    c = povcam(0, 0, 8)
    pts = {1: (960, 880), 2: tuple(c.project(np.array([0, 1.05, 0]))[0][:2]), 3: tuple(c.project(np.array([0.36, 0.9, 0.12]))[0][:2]),
           4: tuple(c.project(np.array([0.55, 0.8, 1.05]))[0][:2]), 5: (360, 549),
           6: tuple(c.project(np.array([1.33, 0.81, 0.0]))[0][:2]), 7: (352, 262), 8: (1600, 1015)}
    for k, (px, py) in pts.items():
        X, Y = ox + px * s, oy + py * s
        d.ellipse([X - 17, Y - 17, X + 17, Y + 17], fill=RED + (255,))
        d.text((X, Y), str(k), font=font("mono_b", 20), fill=PAPER + (255,), anchor="mm")
    notes = [(1, "Kartu 3D di tangan", "Kipas 5 kartu; yang dipilih naik 0,05 blok; yang ditandai miring + tepi kuningan."),
             (2, "Table card", "Stand kuningan 3 sisi di tengah meja (+ neon di bar saat reveal)."),
             (3, "Timer", "Jam pasir dibalik tiap giliran. 5 dtk terakhir: tik + bohlam berkedip."),
             (4, "Giliran", "Lampu kursi aktif menyala penuh; yang lain redup."),
             (5, "Kartu lawan", "Kipas kartu di tangan mereka = jumlah kartu."),
             (6, "Chamber", "Goresan kapur di bibir meja (1 per tarikan) + takik di gagang."),
             (7, "Klaim", "Subtitle nempel di pembicara, hilang 3 dtk."),
             (8, "Hint kontrol", "Hanya ronde pertama.")]
    y = 290
    for k, t, n in notes:
        d.ellipse([1240, y + 2, 1270, y + 32], fill=RED + (255,))
        d.text((1255, y + 17), str(k), font=font("mono_b", 18), fill=PAPER + (255,), anchor="mm")
        d.text((1286, y), t, font=font("disp", 28, "Bold"), fill=PAPER + (255,))
        y = para(d, (1286, y + 34), n, font("sans", 19), DIM + (255,), 560, 25) + 12
    d.text((80, 950), "TIDAK ADA:", font=font("mono_b", 18), fill=RED + (255,))
    d.text((205, 948), "nametag melayang · cincin di meja · panel 10 pemain permanen · font pixel vanilla · HUD saat sinematik (letterbox 2,39:1 = mode sinematik)",
           font=font("sans", 20), fill=(205, 198, 186, 255))
    save(im, "09_hud")


def hudboard2():
    im = canvas()
    header(im, 10, "LAYAR: TAB & TIPOGRAFI", "Yang tetap di layar dibuat seperti barang cetakan: kertas, tinta, kuningan.")
    d = ImageDraw.Draw(im)
    tab_panel(im, 80, 300)
    para(d, (80, 650), "Daftar pemain cuma muncul selama Tab ditahan. Isinya wajah dari skin, nama, kartu di tangan, dan 6 titik chamber. "
         "Yang sudah mati dicoret abu-abu; titik merah cuma tarikan yang DOR.", font("sans", 20), DIM + (255,), 560, 27)
    x = 720
    d.text((x, 300), "BIG SHOULDERS DISPLAY", font=font("mono_b", 18), fill=BRASS + (255,))
    d.text((x, 326), "LIAR! 3 × ACE", font=font("disp", 96, "Black"), fill=PAPER + (255,))
    d.text((x, 440), "Judul, stempel, angka. OFL.", font=font("sans", 19), fill=DIM + (255,))
    d.text((x, 500), "PLAYFAIR DISPLAY ITALIC", font=font("mono_b", 18), fill=BRASS + (255,))
    d.text((x, 524), "“Two aces.”  Alex", font=font("serif_i", 72), fill=PAPER + (255,))
    d.text((x, 624), "Nama, kutipan, subtitle klaim. OFL.", font=font("sans", 19), fill=DIM + (255,))
    d.text((x, 684), "COURIER PRIME", font=font("mono_b", 18), fill=BRASS + (255,))
    d.text((x, 708), "3 pulls survived · 7 rounds", font=font("mono", 40), fill=PAPER + (255,))
    d.text((x, 762), "Info kecil, hint, statistik. OFL.", font=font("sans", 19), fill=DIM + (255,))
    strip = Image.new("RGBA", (1100, 60), (0, 0, 0, 0))
    keyhints(strip, [(["A", "D"], "SELECT"), (["SPACE"], "MARK"), (["E"], "THROW"), (["X"], "CALL LIAR")], right=1080, bottom=45)
    im.alpha_composite(strip, (720, 830))
    d.text((x, 900), "Semua teks lewat lang file (en_us + id_id). Font dibundel sebagai TTF provider Minecraft.", font=font("sans", 19), fill=DIM + (255,))
    save(im, "10_hud_screen")


# ---------------------------------------------------------------- shot list
def shot_table(im, x, y, title, total, rows, w=1760):
    d = ImageDraw.Draw(im)
    d.text((x, y), title, font=font("disp", 40, "Black"), fill=PAPER + (255,))
    d.text((x + d.textlength(title, font=font("disp", 40, "Black")) + 18, y + 14), total, font=font("mono", 19), fill=BRASS + (255,))
    y += 58
    cols = [("#", 46), ("WAKTU", 112), ("FRAMING", 400), ("GERAK KAMERA", 240), ("AKSI", 520), ("AUDIO", 442)]
    xx = x
    for n, cw in cols:
        d.text((xx, y), n, font=font("mono_b", 16), fill=DIM + (255,))
        xx += cw
    y += 26
    d.line([(x, y), (x + w, y)], fill=(60, 54, 46, 255), width=1)
    for r in rows:
        xx = x
        hmax = 0
        for (n, cw), cell in zip(cols, r):
            f = font("mono_b", 17) if n in ("#", "WAKTU") else font("sans", 17)
            col = BRASS if n == "#" else PAPER if n == "FRAMING" else (205, 198, 186)
            yy = para(d, (xx, y + 6), cell, f, col + (255,), cw - 16, 21)
            hmax = max(hmax, yy - y)
            xx += cw
        y += hmax + 4
        d.line([(x, y), (x + w, y)], fill=(38, 34, 30, 255), width=1)
    return y


def shots_roulette():
    im = canvas()
    header(im, 11, "SHOT LIST: ROULETTE", "Semua roulette sinematik. Versi pendek = beat yang sama, lebih rapat. 20 tick = 1 detik.")
    rows = [("R1", "0–1,6s", "WIDE dari belakang kursi seberang; penembak di bawah lampunya", "Push-in pelan 0,3 m", "Penembak menatap pistol; yang lain bersandar menjauh", "Musik tinggal bass; hujan naik"),
            ("R2", "1,6–3,2", "INSERT setinggi felt: tangan + pistol", "Diam, sedikit rack", "Tangan masuk, genggam, angkat (antisipasi turun dulu)", "Gesekan pistol di felt, kursi berderit"),
            ("R3", "3,2–5,4", "INSERT profil pistol di dada; silinder isi 60% frame, tangan di tepi", "Orbit 10°", "Gate buka, ibu jari putar silinder, tutup dengan sentakan", "Klik gate, ratchet berputar (pitch turun), snap"),
            ("R4", "5,4–7,4", "MCU depan-kiri 15° (style frame): siku keluar, pistol profil di pelipis", "Dolly in sangat pelan", "Hammer dikokang ibu jari; kepala miring menjauh", "Kokang berat; low-pass mulai; detak jantung masuk"),
            ("R5", "7,4–9,4", "OTS dari belakang bahu penembak ke pemain lain", "Diam", "Reaksi: satu menutup mata, satu menahan napas, gelas berhenti di bibir", "Detak + napas; dunia makin teredam"),
            ("R6", "9,4–10,6", "SILUET profil lawan jendela cyan (Skyfall)", "Diam 1,2s", "Jari mengencang. Vignette + CA berdenyut ikut jantung", "Tinggal detak jantung"),
            ("R7", "10,6", "Potong balik ke MCU R4", "—", "TARIK", "Hening 2 frame"),
            ("K", "+0–2,8", "KLIK: tahan MCU 0,6s → ease ke WIDE", "Mundur 2s", "Bahu turun, buang napas, pistol ditaruh; insert kapur 1 goresan", "Klik kering; napas lega; layer musik kembali"),
            ("D", "+0–4,5", "DOR: putih 2 frame → WIDE tinggi", "Diam, lalu pelan turun", "Jatuh dari kursi (berat), pistol jatuh, asap laras, lampu kedip 3× lalu padam, kartu terbakar", "Tembakan + ekor ruang; dengung 3s; bohlam pop")]
    y = shot_table(im, 80, 270, "PENUH", "± 10,6 s sampai tarikan · ronde pertama & momen penting", rows)
    rows2 = [("S1", "0–1,2s", "MCU sudut R4", "Dolly in", "Ambil + angkat dalam satu gerak (tanpa putar silinder)", "Pistol diangkat"),
             ("S2", "1,2–2,2", "MCU sama", "Micro push", "Kokang", "Kokang; low-pass + detak"),
             ("S3", "2,2–3,6", "SILUET profil", "Diam", "Tahan", "Detak saja"),
             ("S4", "3,6", "MCU", "—", "TARIK → klik 1,5s / DOR 3s (beat sama, dipadatkan)", "Sama dengan penuh")]
    shot_table(im, 80, y + 16, "PENDEK", "± 3,6 s sampai tarikan · selebihnya", rows2)
    save(im, "11_shots_roulette")


def shots_other():
    im = canvas()
    header(im, 12, "SHOT LIST: LIAR!, REVEAL, PEMENANG", "Muka tidak pernah penuh layar. Lengan tidak menutupi subjek.")
    rows = [("L1", "0–0,6s", "INSERT rendah lintas felt: tangan penuduh menghantam", "Diam; slow-mo 0,5× di-bake 8 tick", "Gelas loncat, kartu bergetar", "Gebrak berat + gelas berdenting; musik putus"),
            ("L2", "0,6–1,6", "OTS dari bahu tertuduh ke penuduh (style frame)", "Shake kecil 1 kali", "Penuduh setengah berdiri, menunjuk. Stempel LIAR! (0,8s)", "Teriakan (VC/bot) + stempel"),
            ("L3", "1,6–3,0", "MCU tertuduh, wajah ≤ 35% tinggi frame", "Dolly zoom (Vertigo)", "Lampu tertuduh menyala naik", "Riser tipis"),
            ("V1", "+0,6/kartu", "TOP-DOWN 90° di tumpukan kartu", "Diam", "Kartu dibalik satu-satu. Jujur: tepi emas glow. Bohong: terbakar merah → abu", "Hit audio naik per kartu; api kecil"),
            ("V2", "+1,0", "MEDIUM yang kalah; lampunya pindah jadi spotlight", "Pan pelan", "Serah ke roulette", "Hujan masuk lagi")]
    y = shot_table(im, 80, 280, "LIAR! + REVEAL", "± 3 s + 0,6 s/kartu", rows)
    rows2 = [("W1", "0–3s", "HIGH ANGLE (style frame): pemenang sendirian di bawah satu lampu", "Orbit pelan 12°/s", "Bersandar, buang napas; kursi lain kosong & gelap", "Hujan + motif sax tunggal"),
             ("W2", "3s–", "Sama + champion card masuk dari kanan", "Orbit lanjut", "Kartu kertas berputar masuk (0,4s), diam", "Musik naik penuh")]
    shot_table(im, 80, y + 30, "PEMENANG", "loop sampai lanjut", rows2)
    save(im, "12_shots_other")


# ---------------------------------------------------------------- aset
def assets():
    im = canvas()
    header(im, 13, "ASET & STANDAR", "Semua buatan sendiri atau CC0/CC-BY, dicatat di CREDITS.txt. Tidak ada aset dari Liar's Bar.")
    d = ImageDraw.Draw(im)
    sheet = Image.open(SP + "/art/pixel_sheet.png").convert("RGBA")
    cards = contain(sheet.crop((0, 0, 1424, 420)), 820, 250)
    im.alpha_composite(cards, (80, 290))
    rv = Image.open(SP + "/art/revolver_profile.png").convert("RGBA")
    rv = rv.resize((rv.width * 14, rv.height * 14), Image.NEAREST)
    im.alpha_composite(rv, (80, 580))
    d.text((80, 790), "REVOLVER  ·  Blockbench + GeckoLib", font=font("mono_b", 18), fill=BRASS + (255,))
    para(d, (80, 818), "Siluet klasik: laras panjang, silinder beralur, gagang melengkung. 1 px = 1/40 blok (panjang 0,65 ≈ 2,6× kepalan). "
         "Bone: silinder, gate, hammer, pelatuk. Takik di gagang = chamber yang sudah ditarik. 5 nada baja biru, 3 walnut, 2 kuningan.",
         font("sans", 19), (205, 198, 186, 255), 820, 25)
    d.text((80, 545), "KARTU 32×48 px (2× item vanilla) · K, Q, A, Joker, punggung", font=font("mono_b", 18), fill=BRASS + (255,))
    setimg = Image.open(SP + "/render/pov_set.png").convert("RGB")
    a = (np.asarray(setimg, np.float32) / 255) ** 1.8 * 0.8
    ys, xs = np.mgrid[0:a.shape[0], 0:a.shape[1]]
    r = np.sqrt(((xs - a.shape[1] / 2) / (a.shape[1] / 2)) ** 2 + ((ys - a.shape[0] * 0.4) / (a.shape[0] / 2)) ** 2)
    a = a * np.clip(1.25 - 0.6 * r, 0.15, 1)[..., None]
    setimg = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))
    im.paste(fit(setimg, 860, 300), (980, 290))
    d.text((980, 606), "SET BAR (blockout Blender, dengan rig lighting 08)", font=font("mono_b", 18), fill=BRASS + (255,))
    para(d, (980, 634), "Back bar 6 m: counter, 6 stool kulit, cermin, 6 rak ± 40 botol tembus cahaya + strip hangat. 2 jendela hujan di barat, "
         "1 di utara, pintu kaca buram, kipas plafon 4 bilah (berputar, bayangan menyapu), pipa besi, poster, lantai dark oak, wainscot.",
         font("sans", 19), (205, 198, 186, 255), 860, 25)
    items = [("MEJA", "Felt hijau tua + bibir walnut + apron. Stand table card, jam pasir, tumbler segi-8, asbak + cerutu."),
             ("LAMPU", "Kap enamel hijau tua, bohlam emissive (RenderType.eyes), kabel. Mati = bohlam abu, tanpa cahaya."),
             ("KARAKTER", "4 skin noir buatan sendiri + aksesori topi beda siluet (fedora, flat cap, cloche, bowler). Bot tidak boleh kembar."),
             ("SUARA", "Rekaman CC0 (Freesound): mekanik revolver, tembakan interior, kartu, gebrak, gelas, kursi, hujan di kaca, detak jantung, napas, bohlam pop, kapur. Dengung = satu-satunya tone sintetis."),
             ("MUSIK", "Jazz/noir CC0 atau CC-BY. Lisensi dicek per track (di incompetech pun beda-beda). Layer tegang dari drone CC0.")]
    y = 760
    for t, n in items:
        d.text((980, y), t, font=font("mono_b", 18), fill=BRASS + (255,))
        y = para(d, (1120, y - 2), n, font("sans", 17), (205, 198, 186, 255), 720, 22) + 6
    d.text((80, 930), "STANDAR LOLOS", font=font("mono_b", 18), fill=BRASS + (255,))
    std = ["Siluet kebaca di tinggi 64 px", "Tiap material ≥ 3 nada, tanpa noise",
           "16 px/blok karakter, 32–40 properti", "Lolos cek tanpa DOF & post"]
    for k, t in enumerate(std):
        x = 80 + (k % 2) * 420
        yy = 960 + (k // 2) * 30
        d.rectangle([x, yy + 8, x + 9, yy + 17], fill=RED + (255,))
        d.text((x + 20, yy), t, font=font("sans", 18), fill=(205, 198, 186, 255))
    save(im, "13_assets")


def compare():
    im = canvas()
    header(im, 1, "SEKARANG → TARGET", "Kiri: build sekarang, kamera AnimDev yang sama (pose beda). Kanan: style frame. Ini yang nanti dibandingin langsung.")
    d = ImageDraw.Draw(im)
    pairs = [("pov", "POV giliran"), ("liar", "LIAR!"), ("rt", "Roulette"), ("win", "Pemenang")]
    tw, th = 420, 236
    for k, (n, label) in enumerate(pairs):
        col, row = k % 2, k // 2
        x, y = 80 + col * 900, 280 + row * 380
        a = fit(Image.open(SP + f"/base/{n}_now.png").convert("RGB"), tw, th)
        b = fit(Image.open(OUT + f"/sf_{n}.png").convert("RGB"), tw, th)
        im.paste(a, (x, y + 34))
        im.paste(b, (x + tw + 40, y + 34))
        d.text((x, y), label.upper(), font=font("disp", 30, "Bold"), fill=PAPER + (255,))
        d.text((x, y + th + 44), "sekarang", font=font("mono", 17), fill=DIM + (255,))
        d.text((x + tw + 40, y + th + 44), "target", font=font("mono", 17), fill=BRASS + (255,))
        d.text((x + tw + 8, y + 34 + th // 2), "→", font=font("disp", 34, "Bold"), fill=BRASS + (255,), anchor="lm")
    save(im, "01_compare")


if __name__ == "__main__":
    which = sys.argv[1:] or ["mood", "lighting", "hud", "shots", "assets"]
    if "mood" in which:
        moodboard(0); moodboard(1)
    if "lighting" in which:
        lighting()
    if "hud" in which:
        hudboard(); hudboard2()
    if "shots" in which:
        shots_roulette(); shots_other()
    if "assets" in which:
        assets()
    if "cover" in which:
        cover()
    if "compare" in which:
        compare()
