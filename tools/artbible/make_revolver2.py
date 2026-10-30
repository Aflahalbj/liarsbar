"""Revolver Art Bible (Fase B langkah 3): model GeckoLib + tekstur 64x64.

Siluet klasik dari profil art bible: laras panjang dengan lug bawah dan mata depan, silinder bulat (oktagon)
beralur, gagang walnut miring dengan ujung melengkung (bird's head), hammer spur baja, trigger guard melingkar.
Nama bone dan porosnya sama dengan model lama (frame, barrel, muzzle, crane, cylinder, hammer, trigger, grip)
supaya RevolverRenderer/RevolverPose dan genggaman tangan tetap cocok.

Unit geo = 1/16 blok (piksel model). Barel ke -z. Tekstur: tiap sisi kubus dipetakan ke swatch material
dengan 3 nada (atas terang, samping, bawah gelap); alur silinder dan checkering gagang digambar tangan.

Jalankan: python tools/artbible/make_revolver2.py  (menulis geo, tekstur, dan pratinjau di run/screenshots)
"""
import json
import os
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "liarsbar")

# ---------------------------------------------------------------- palet (3+ nada per material)
MATS = {
    # nama: (atas, samping, bawah)
    "steel": ((92, 102, 116), (66, 74, 86), (40, 45, 54)),
    "steel_hi": ((170, 182, 196), (130, 142, 156), (90, 100, 112)),
    "steel_dk": ((52, 58, 68), (36, 40, 48), (22, 25, 30)),
    "walnut": ((132, 78, 40), (104, 58, 30), (70, 38, 20)),
    "brass": ((236, 196, 110), (201, 160, 74), (140, 104, 40)),
    "bore": ((8, 8, 10), (8, 8, 10), (8, 8, 10)),
}
SW = 4  # ukuran swatch (piksel)


def build_texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    swatch = {}
    # Baris swatch polos: tiap material 3 nada x 4x4 piksel di y = 0..
    for row, (name, tones) in enumerate(MATS.items()):
        for k, c in enumerate(tones):
            x, y = k * SW, row * SW
            d.rectangle([x, y, x + SW - 1, y + SW - 1], fill=c + (255,))
            swatch[(name, k)] = (x, y)
    # Silinder beralur: 16x8, alur gelap tiap 4 piksel (dipakai untuk sisi silinder).
    fx, fy = 16, 0
    for x in range(16):
        for y in range(8):
            flute = (x % 4) in (1, 2) and 1 <= y <= 6
            c = MATS["steel"][2] if flute else MATS["steel"][1]
            if (x % 4) == 0:
                c = MATS["steel_hi"][2]
            img.putpixel((fx + x, fy + y), c + (255,))
    swatch[("cyl_side", 0)] = (fx, fy)
    # Muka silinder: lingkar 8x8 dengan 6 lubang chamber.
    cx, cy = 32, 0
    face = ["..XXXX..", ".XoXXoX.", "XXXXXXXX", "XoXXXXoX", "XXXXXXXX", ".XoXXoX.", "..XXXX..", "........"]
    for y, rowv in enumerate(face):
        for x, ch in enumerate(rowv):
            if ch == "X":
                img.putpixel((cx + x, cy + y), MATS["steel"][1] + (255,))
            elif ch == "o":
                img.putpixel((cx + x, cy + y), MATS["bore"][0] + (255,))
    swatch[("cyl_face", 0)] = (cx, cy)
    # Gagang walnut dengan checkering (pola belah ketupat) dan medali kuningan: 16x16.
    gx, gy = 16, 16
    for x in range(16):
        for y in range(16):
            base = MATS["walnut"][1]
            if 2 <= x <= 13 and 3 <= y <= 13 and ((x + y) % 3 == 0 or (x - y) % 3 == 0):
                base = MATS["walnut"][2]
            if x in (0, 15) or y in (0, 15):
                base = MATS["walnut"][0]
            img.putpixel((gx + x, gy + y), base + (255,))
    for (x, y) in [(7, 2), (8, 2), (7, 3), (8, 3)]:
        img.putpixel((gx + x, gy + y), MATS["brass"][1] + (255,))
    swatch[("grip_panel", 0)] = (gx, gy)
    # Lubang laras (muka depan laras): 4x4 hitam dengan tepi baja.
    bx, by = 40, 0
    for x in range(4):
        for y in range(4):
            edge = x in (0, 3) or y in (0, 3)
            img.putpixel((bx + x, by + y), (MATS["steel"][0] if edge else MATS["bore"][0]) + (255,))
    swatch[("muzzle_face", 0)] = (bx, by)
    return img, swatch


# ---------------------------------------------------------------- kubus
def cube(origin, size, mat, rotation=None, pivot=None, special=None):
    return {"origin": origin, "size": size, "mat": mat, "rotation": rotation, "pivot": pivot, "special": special or {}}


def face_uv(swatch, mat, face, size, special):
    """UV per sisi: tiap sisi ke swatch nada (atas/samping/bawah) materialnya, atau tekstur khusus."""
    if face in special:
        key, (w, h) = special[face]
        u, v = swatch[(key, 0)]
        return {"uv": [u, v], "uv_size": [w, h]}
    tone = 0 if face == "up" else 2 if face == "down" else 1
    u, v = swatch[(mat, tone)]
    return {"uv": [u + 0.5, v + 0.5], "uv_size": [SW - 1, SW - 1]}


def bones():
    B = {}
    # frame (badan): kubus terbuka di tengah untuk silinder.
    B["frame"] = dict(pivot=[0, -1.0, -2.4], parent="root", cubes=[
        cube([-1.1, -0.6, -6.7], [2.2, 1.0, 6.3], "steel"),            # alas frame
        cube([-0.9, 3.4, -6.8], [1.8, 0.6, 6.5], "steel_hi"),          # top strap (sorot terang)
        cube([-1.15, -0.6, -7.2], [2.3, 4.6, 0.6], "steel"),            # tiang depan
        cube([-1.2, -0.6, -1.0], [2.4, 4.4, 0.8], "steel"),             # perisai belakang
        cube([-0.35, 3.95, -1.0], [0.7, 0.25, 0.9], "steel_dk"),        # alur bidik di strap
        cube([-0.3, -2.95, -4.9], [0.6, 0.45, 3.4], "steel"),           # trigger guard bawah
        cube([-0.3, -2.95, -5.25], [0.6, 2.4, 0.45], "steel"),          # guard depan
        cube([-0.3, -2.95, -1.85], [0.6, 2.4, 0.45], "steel", rotation=[-20, 0, 0], pivot=[0, -2.95, -1.6]),
        cube([-0.6, 0.6, -0.4], [1.2, 2.2, 0.6], "steel_dk"),           # tonjolan belakang (di depan hammer)
    ])
    B["barrel"] = dict(pivot=[0, 2.8, -7.0], parent="frame", cubes=[
        cube([-0.85, 2.0, -16.5], [1.7, 1.6, 9.6], "steel", special={"north": ("muzzle_face", (4, 4))}),
        cube([-0.62, 1.85, -16.4], [1.24, 1.9, 9.5], "steel"),          # bulatkan atas-bawah (oktagon)
        cube([-0.55, 0.9, -13.4], [1.1, 1.1, 6.4], "steel_dk"),         # lug ejektor
        cube([-0.95, 1.9, -16.75], [1.9, 1.8, 0.35], "steel_hi"),       # mahkota laras
        cube([-0.18, 3.6, -16.2], [0.36, 0.7, 1.0], "steel_dk"),        # mata depan
    ])
    B["muzzle"] = dict(pivot=[0, 2.8, -16.8], parent="barrel", cubes=[])
    B["crane"] = dict(pivot=[-1.0, 0.5, -3.6], parent="frame", cubes=[
        cube([-1.2, 0.3, -6.3], [0.7, 0.6, 0.6], "steel_dk"),
    ])
    cyl_side = {"east": ("cyl_side", (16, 8)), "west": ("cyl_side", (16, 8)), "up": ("cyl_side", (16, 8)),
                "down": ("cyl_side", (16, 8)), "south": ("cyl_face", (8, 7)), "north": ("cyl_face", (8, 7))}
    B["cylinder"] = dict(pivot=[0, 1.9, -3.6], parent="crane", cubes=[
        cube([-1.7, 0.2, -6.1], [3.4, 3.4, 4.9], "steel", special=cyl_side),
        cube([-1.7, 0.2, -6.1], [3.4, 3.4, 4.9], "steel", rotation=[0, 0, 45], pivot=[0, 1.9, -3.65], special=cyl_side),
        cube([-0.3, 1.6, -6.7], [0.6, 0.6, 0.6], "steel_hi"),           # pin poros depan
    ])
    B["hammer"] = dict(pivot=[0, 3.0, -0.5], parent="frame", cubes=[
        cube([-0.4, 2.6, -0.7], [0.8, 1.7, 0.8], "steel"),
        cube([-0.5, 4.0, -0.3], [1.0, 0.55, 1.7], "steel_hi", rotation=[-25, 0, 0], pivot=[0, 4.1, -0.3]),  # spur
    ])
    B["trigger"] = dict(pivot=[0, -0.6, -3.0], parent="frame", cubes=[
        cube([-0.2, -2.3, -3.35], [0.4, 1.8, 0.5], "steel_hi", rotation=[12, 0, 0], pivot=[0, -0.6, -3.1]),
    ])
    grip_panel = {"east": ("grip_panel", (16, 16)), "west": ("grip_panel", (16, 16))}
    B["grip"] = dict(pivot=[0, -0.5, -0.6], parent="frame", cubes=[
        cube([-1.2, -6.6, -1.1], [2.4, 6.2, 2.6], "walnut", rotation=[-18, 0, 0], pivot=[0, -0.5, -0.6], special=grip_panel),
        cube([-0.7, -6.4, 1.4], [1.4, 6.0, 0.5], "steel", rotation=[-18, 0, 0], pivot=[0, -0.5, -0.6]),   # backstrap
        cube([-1.25, -7.6, -0.4], [2.5, 1.2, 2.8], "walnut", rotation=[-40, 0, 0], pivot=[0, -6.6, 0.4]),  # bird's head
        cube([-1.3, -8.1, 0.3], [2.6, 0.5, 2.0], "steel", rotation=[-40, 0, 0], pivot=[0, -6.6, 0.4]),     # tutup butt
    ])
    return B


def geo(swatch):
    out_bones = [{"name": "root", "pivot": [0, -1.0, -2.4]}]
    for name, b in bones().items():
        entry = {"name": name, "parent": b["parent"], "pivot": b["pivot"], "cubes": []}
        for c in b["cubes"]:
            faces = {f: face_uv(swatch, c["mat"], f, c["size"], c["special"])
                     for f in ("north", "south", "east", "west", "up", "down")}
            ce = {"origin": c["origin"], "size": c["size"], "uv": faces}
            if c["rotation"]:
                ce["rotation"] = c["rotation"]
                ce["pivot"] = c["pivot"]
            entry["cubes"].append(ce)
        if not entry["cubes"]:
            del entry["cubes"]
        out_bones.append(entry)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.liarsbar.revolver", "texture_width": 64, "texture_height": 64,
                        "visible_bounds_width": 3, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0.25, 0]},
        "bones": out_bones}]}


def preview(path):
    """Profil samping (seperti art bible) untuk dicek tanpa membuka game."""
    import math
    S = 24
    im = Image.new("RGB", (30 * S, 16 * S), (20, 20, 24))
    d = ImageDraw.Draw(im)
    for name, b in bones().items():
        for c in b["cubes"]:
            (x, y, z), (sx, sy, sz) = c["origin"], c["size"]
            pts = [(z, y), (z + sz, y), (z + sz, y + sy), (z, y + sy)]
            if c["rotation"] and c["rotation"][0]:
                a = math.radians(c["rotation"][0])
                py_, pz = c["pivot"][1], c["pivot"][2]
                rot = []
                for (zz, yy) in pts:
                    dy, dz = yy - py_, zz - pz
                    rot.append((pz + dz * math.cos(a) - dy * math.sin(a), py_ + dz * math.sin(a) + dy * math.cos(a)))
                pts = rot
            col = MATS[c["mat"]][1]
            d.polygon([((-zz + 4) * S, (6 - yy) * S) for zz, yy in pts], fill=col, outline=(0, 0, 0))
    im.save(path)


if __name__ == "__main__":
    tex, sw = build_texture()
    tex.save(os.path.join(ASSETS, "textures", "item", "revolver.png"))
    json.dump(geo(sw), open(os.path.join(ASSETS, "geo", "item", "revolver.geo.json"), "w"), indent=1)
    preview(os.path.join(ROOT, "run", "screenshots", "revolver2_profile.png"))
    print("ok")
