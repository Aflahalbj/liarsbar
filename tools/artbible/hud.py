"""Overlay HUD/tipografi untuk style frame (font OFL: Big Shoulders Display, Playfair Display, Courier Prime)."""
import sys, os, math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops
sys.path.insert(0, os.path.dirname(__file__))
from cam import *

SP = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
FD = SP + "/fonts/"
PAPER, BRASS, RED, INK, CYAN = (237, 227, 201), (201, 160, 74), (214, 32, 42), (10, 10, 12), (110, 200, 235)


def font(kind, size, weight=None):
    path = {"disp": "bigshouldersdisplay_BigShouldersDisplay[wght].ttf", "serif_i": "playfairdisplay_PlayfairDisplay-Italic[wght].ttf",
            "serif": "playfairdisplay_PlayfairDisplay[wght].ttf", "mono": "courierprime_CourierPrime-Regular.ttf",
            "mono_b": "courierprime_CourierPrime-Bold.ttf", "sans": "ibmplexsans_IBMPlexSans[wdth,wght].ttf"}[kind]
    f = ImageFont.truetype(FD + path, size)
    if weight:
        try:
            f.set_variation_by_name(weight)
        except Exception:
            pass
    return f


def spaced(draw, xy, text, f, fill, spacing=0, anchor="la"):
    """Teks dengan letter-spacing (PIL tidak punya tracking)."""
    x, y = xy
    total = sum(draw.textlength(ch, font=f) + spacing for ch in text) - spacing
    if anchor[0] == "m":
        x -= total / 2
    elif anchor[0] == "r":
        x -= total
    for ch in text:
        draw.text((x, y), ch, font=f, fill=fill, anchor="l" + anchor[1])
        x += draw.textlength(ch, font=f) + spacing
    return total


def shadow_layer(size, draw_fn, blur=6, alpha=170):
    sh = Image.new("RGBA", size, (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(sh), (0, 0, 0, alpha))
    return sh.filter(ImageFilter.GaussianBlur(blur))


def caption(img, anchor, name, quote):
    """Subtitle yang menempel di dunia, dekat kepala pembicara."""
    x, y = anchor
    W, H = img.size
    fn, fq = font("disp", 24, "Bold"), font("serif_i", 44, "Regular")

    def draw(d, col):
        spaced(d, (x, y), name, fn, col if col[3] < 255 else BRASS + (235,), 5)
        d.text((x - 2, y + 22), "“" + quote + "”", font=fq, fill=col if col[3] < 255 else PAPER + (245,))
        d.line([(x, y + 82), (x + 46, y + 82)], fill=col if col[3] < 255 else BRASS + (200,), width=2)
    img.alpha_composite(shadow_layer(img.size, draw, 8, 200))
    draw(ImageDraw.Draw(img), (0, 0, 0, 255))


def keyhints(img, items, right=1860, bottom=1030):
    d = ImageDraw.Draw(img)
    fk, fl = font("mono_b", 19), font("disp", 20, "SemiBold")
    x = right
    for keys, label in reversed(items):
        lw = sum(d.textlength(ch, font=fl) + 3 for ch in label)
        x -= lw
        spaced(d, (x, bottom - 15), label, fl, PAPER + (190,), 3, anchor="lm")
        x -= 12
        for k in reversed(keys):
            kw = max(30, d.textlength(k, font=fk) + 16)
            x -= kw
            d.rounded_rectangle([x, bottom - 30, x + kw, bottom], 5, outline=PAPER + (150,), width=2, fill=(0, 0, 0, 90))
            d.text((x + kw / 2, bottom - 15), k, font=fk, fill=PAPER + (220,), anchor="mm")
            x -= 6
        x -= 34


def stamp(img, text, xy, size, color, rot=-6, seed=3):
    W, H = img.size
    f = font("disp", size, "Black")
    layer = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(layer)
    spaced(d, xy, text, f, 255, int(size * 0.02))
    rng = np.random.default_rng(seed)
    n = rng.random((H // 3, W // 3)).astype(np.float32)
    n = np.asarray(Image.fromarray((n * 255).astype(np.uint8)).resize((W, H), Image.BICUBIC).filter(ImageFilter.GaussianBlur(1.2)), np.float32) / 255
    a = np.asarray(layer, np.float32) / 255
    a = a * np.clip((n - 0.06) * 9.0, 0.55, 1)  # tinta cetak: hampir solid, bintik halus
    layer = Image.fromarray((a * 255).astype(np.uint8)).rotate(rot, resample=Image.BICUBIC, center=xy)
    mis = layer.transform(layer.size, Image.AFFINE, (1, 0, -5, 0, 1, 3))  # salah register cetak
    img.alpha_composite(Image.merge("RGBA", (*[Image.new("L", (W, H), c) for c in (90, 6, 10)], mis.point(lambda v: v * 0.55))))
    img.alpha_composite(Image.merge("RGBA", (*[Image.new("L", (W, H), c) for c in color], layer)))


def skin_face(path, scale):
    s = Image.open(path).convert("RGBA")
    face = s.crop((8, 8, 16, 16))
    hat = s.crop((40, 8, 48, 16))
    face.alpha_composite(hat)
    return face.resize((8 * scale, 8 * scale), Image.NEAREST)


def champion_card(img, skin, name, stats, box=(1330, 170), w=430, rot=-3):
    h = int(w * 1.5)
    card = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(card)
    d.rounded_rectangle([0, 0, w - 1, h - 1], 18, fill=PAPER + (255,))
    d.rounded_rectangle([16, 16, w - 17, h - 17], 8, outline=INK + (255,), width=3)
    d.rounded_rectangle([26, 26, w - 27, h - 27], 4, outline=BRASS + (255,), width=2)
    for cx, cy in ((26, 26), (w - 27, 26), (26, h - 27), (w - 27, h - 27)):
        d.rectangle([cx - 7, cy - 7, cx + 7, cy + 7], fill=BRASS + (255,))
    # mahkota pixel di pojok
    crown = ["X.X.X", "XXXXX", "XXXXX"]
    for j, row in enumerate(crown):
        for i, c in enumerate(row):
            if c == "X":
                d.rectangle([48 + i * 7, 48 + j * 7, 54 + i * 7, 54 + j * 7], fill=INK + (255,))
    # potret: wajah skin pemain di dalam bingkai lengkung hitam (Saul Bass)
    pf = skin_face(skin, 22)
    pw = pf.width
    px, py = (w - pw) // 2, 104
    d.rounded_rectangle([px - 22, py - 22, px + pw + 22, py + pw + 22], 30, fill=INK + (255,))
    card.alpha_composite(pf, (px, py))
    ft = font("disp", 46, "Black")
    spaced(d, (w / 2, py + pw + 66), "LAST ONE STANDING", ft, INK + (255,), 1, anchor="mm")
    fn = font("serif_i", 68, "Regular")
    d.text((w / 2, py + pw + 136), name, font=fn, fill=INK + (255,), anchor="mm")
    d.line([(w / 2 - 60, py + pw + 186), (w / 2 + 60, py + pw + 186)], fill=BRASS + (255,), width=3)
    fs = font("mono", 23)
    for k, line in enumerate(stats):
        d.text((w / 2, py + pw + 222 + k * 32), line, font=fs, fill=(60, 50, 40, 255), anchor="mm")
    card = card.rotate(rot, resample=Image.BICUBIC, expand=True)
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sh.paste((0, 0, 0, 200), (box[0] + 18, box[1] + 26), card.split()[3])
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(22)))
    img.alpha_composite(card, box)


def finalize(name):
    src = Image.open(SP + f"/render/{name}_lit.png").convert("RGBA")
    if name == "pov":
        c = povcam(0, 0, 8)
        q = c.project(seat(3)[0])[0]
        caption(src, (int(q[0]) + 210, int(q[1]) - 190), "STEVE", "Two aces.")
        keyhints(src, [(["A", "D"], "SELECT"), (["SPACE"], "MARK"), (["E"], "THROW"), (["X"], "CALL LIAR")])
    elif name == "liar":
        stamp(src, "LIAR!", (130, 320), 195, RED, rot=-6)
        d = ImageDraw.Draw(src)
        spaced(d, (150, 560), "— STEVE", font("disp", 30, "Bold"), BRASS + (230,), 6)
    elif name == "win":
        champion_card(src, SP + "/mc/assets/minecraft/textures/entity/player/wide/alex.png", "Alex",
                      ["3 pulls survived", "2 lies never caught", "7 rounds"])
    out = SP + f"/frames/sf_{name}.png"
    os.makedirs(SP + "/frames", exist_ok=True)
    src.convert("RGB").save(out)
    print("saved", out)


if __name__ == "__main__":
    for n in sys.argv[1:]:
        finalize(n)
