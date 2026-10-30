"""Revolver in-game (pilihan user 2026-10-31, bukan docs/artbible/revolver.bbmodel; lihat tools/revolver_geo.py).

Model revolver GeckoLib + tekstur, dibuat prosedural (buatan sendiri, lihat CREDITS.txt).

    python tools/make_revolver.py            -> tulis model, tekstur, animasi kosong, model item
    python tools/make_revolver.py --preview  -> juga gambar pratinjau orthografis ke argumen berikutnya

Satuan piksel model (1/16 blok). Sumbu: x kanan, y atas, laras ke -z. Titik
asal (0,0,0) = tengah gagang, tempat tangan menggenggam. Bone yang digerakkan
kode (RevolverRenderer): crane (silinder terbuka ke samping), cylinder (putar
di sumbu z), hammer (kokang di sumbu x), trigger, "muzzle" (tanpa kubus,
titik api moncong/asap), dan "bullet" (satu peluru di chamber atas, anak
cylinder: disembunyikan / digeser keluar sepanjang sumbu chamber saat diisi).
Nama dan pivot bone jangan diubah.

Bahan dipilih per sisi supaya bentuk terbaca di model kotak-kotak: sisi atas
paling terang (pantulan lampu gantung), sisi samping menengah dengan garis
highlight di tepi atas, sisi bawah gelap.
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "liarsbar")
TW, TH = 64, 64

# Daerah tekstur (u, v, w, h) per bahan.
MAT = {
    "top": (0, 0, 16, 16),        # baja kena cahaya dari atas
    "side": (16, 0, 16, 16),      # baja samping, highlight di tepi atas
    "dark": (32, 0, 16, 16),      # baja bawah / dalam
    "rib": (48, 0, 16, 16),       # rib laras bergerigi
    "wood": (0, 16, 16, 16),      # walnut dengan checkering
    "woodside": (16, 16, 16, 16),  # walnut tepi (serat saja)
    "chambers": (32, 16, 16, 16),  # muka belakang silinder: enam chamber
    "flutes": (48, 16, 16, 16),   # sisi silinder beralur
    "bore": (0, 32, 8, 8),        # lubang laras
    "brass": (8, 32, 8, 8),       # medali gagang
    "check": (16, 32, 16, 16),    # gerigi hammer / pelatuk
    "front": (32, 32, 16, 16),    # muka depan silinder
    "case": (48, 32, 8, 8),       # selongsong kuningan peluru
    "lead": (56, 32, 8, 8),       # kepala peluru timah (round nose .38, abu-abu: merah hanya aksen)
    "headstamp": (48, 40, 8, 8),  # pantat selongsong: kuningan dengan primer di tengah
}

# Peluru: ukuran nyata .38 Special dibandingkan silinder (panjang 3,9 px), sedikit lebih gemuk dari lubang
# chamber supaya terbaca. Dipakai juga oleh BulletRenderer (salinan angkanya, blok = px / 16 * skala tangan).
BULLET_CASE = 3.0
BULLET_NOSE = 0.6
BULLET_D = 0.66
BULLET_RIM = 0.8
BULLET_RIM_T = 0.12

OY = -1.0
OZ = -2.4

SIDE_FACES = {"north": "side", "south": "side", "east": "side", "west": "side", "up": "top", "down": "dark"}


def faces(**over):
    f = dict(SIDE_FACES)
    f.update(over)
    return f


def face_uv(mat):
    u, v, w, h = MAT[mat]
    return {"uv": [u, v], "uv_size": [w, h]}


def cube(origin, size, mats=None, pivot=None, rotation=None, inflate=0.0):
    if mats is None:
        mats = SIDE_FACES
    if isinstance(mats, str):
        mats = {f: mats for f in ("north", "south", "east", "west", "up", "down")}
    c = {"origin": [round(origin[0], 3), round(origin[1] + OY, 3), round(origin[2] + OZ, 3)],
         "size": [round(s, 3) for s in size],
         "uv": {f: face_uv(m) for f, m in mats.items()}}
    if pivot is not None:
        c["pivot"] = [pivot[0], pivot[1] + OY, pivot[2] + OZ]
    if rotation is not None:
        c["rotation"] = rotation
    if inflate:
        c["inflate"] = inflate
    return c


def bone(name, pivot, cubes=(), parent=None, rotation=None):
    b = {"name": name, "pivot": [pivot[0], pivot[1] + OY, pivot[2] + OZ]}
    if parent:
        b["parent"] = parent
    if rotation:
        b["rotation"] = rotation
    if cubes:
        b["cubes"] = list(cubes)
    return b


def bullet_bone(cc, rear):
    """Peluru di chamber atas (texture: chamber i=0 di puncak muka belakang): pivot di tengah lubang di muka
    belakang silinder, pelek sedikit menonjol di belakangnya, kepala peluru di dalam silinder."""
    y = cc[1] + 5.0 / 16 * 2.9
    r, rr, rn = BULLET_D / 2, BULLET_RIM / 2, BULLET_D / 2 * 0.8
    brass = {f: "case" for f in ("north", "south", "east", "west", "up", "down")}
    return bone("bullet", (0, y, rear), parent="cylinder", cubes=[
        cube((-r, y - r, rear - BULLET_CASE), (BULLET_D, BULLET_D, BULLET_CASE), brass),
        cube((-rn, y - rn, rear - BULLET_CASE - BULLET_NOSE), (rn * 2, rn * 2, BULLET_NOSE), "lead"),
        cube((-rr, y - rr, rear), (BULLET_RIM, BULLET_RIM, BULLET_RIM_T), dict(brass, south="headstamp")),
    ])


def geometry():
    cc = (0.0, 2.875, -1.2)      # pusat silinder
    r = 1.45
    length = 3.9
    z0 = cc[2] - length / 2
    cyl = {"north": "front", "south": "chambers", "east": "flutes", "west": "flutes", "up": "flutes", "down": "flutes"}
    cylinder_cubes = [
        cube((cc[0] - r, cc[1] - r, z0), (r * 2, r * 2, length), cyl, pivot=cc, rotation=[0, 0, a])
        for a in (0, 30, 60)
    ]
    # Ejector star dan batangnya di depan silinder.
    cylinder_cubes.append(cube((-0.35, cc[1] - 0.35, z0 - 0.5), (0.7, 0.7, 0.5), faces(north="dark")))
    bones = [
        bone("root", (0, 0, 0)),
        bone("frame", (0, 0, 0), parent="root", cubes=[
            # Frame bawah di bawah silinder.
            cube((-1.0, 0.55, -3.4), (2.0, 0.95, 5.3)),
            # Top strap dengan alur bidik (dua lis tipis di tepinya).
            cube((-0.8, 4.3, -3.4), (1.6, 0.55, 5.6), faces(up="rib")),
            cube((-0.8, 4.85, 1.2), (0.45, 0.35, 1.0)),
            cube((0.35, 4.85, 1.2), (0.45, 0.35, 1.0)),
            # Dinding depan dan belakang jendela silinder (recoil shield).
            cube((-1.05, 0.55, -4.3), (2.1, 4.3, 0.95)),
            cube((-1.05, 0.55, 0.95), (2.1, 4.0, 1.85)),
            # Punggung frame di belakang hammer.
            cube((-0.85, 3.3, 2.8), (1.7, 1.55, 0.8)),
            # Side plate (sedikit menonjol, sekrup terlihat dari tekstur samping).
            cube((-1.12, 0.9, 0.95), (2.24, 2.4, 1.6), faces(north="dark", south="dark")),
            # Pelindung pelatuk: tiga ruas melengkung.
            cube((-0.28, -1.55, -2.1), (0.56, 0.5, 2.6), "dark"),
            cube((-0.28, -1.4, -2.55), (0.56, 2.0, 0.5), "dark", pivot=(0, -1.0, -2.3), rotation=[-18, 0, 0]),
            cube((-0.28, -1.2, 0.45), (0.56, 1.8, 0.5), "dark", pivot=(0, -0.6, 0.7), rotation=[22, 0, 0]),
        ]),
        bone("barrel", (0, 3.75, -4.3), parent="frame", cubes=[
            # Laras dan rib ventilasi di atasnya.
            cube((-0.78, 3.0, -11.3), (1.56, 1.5, 7.0), faces(north="bore")),
            cube((-0.55, 4.5, -11.1), (1.1, 0.4, 6.8), faces(up="rib")),
            # Underlug penuh (selubung batang ejector).
            cube((-0.62, 1.95, -10.6), (1.24, 1.05, 6.3), faces(north="dark")),
            # Mahkota moncong sedikit lebih tebal.
            cube((-0.88, 2.9, -11.55), (1.76, 1.7, 0.45), faces(north="bore")),
            # Pisir depan (ramp).
            cube((-0.18, 4.9, -11.0), (0.36, 0.75, 1.1), "dark", pivot=(0, 4.9, -10.5), rotation=[12, 0, 0]),
        ]),
        bone("muzzle", (0, 3.75, -11.6), parent="barrel"),
        # Engsel crane di sisi kanan JSON: GeckoLib mencerminkan sumbu x, jadi di render ada di kiri revolver
        # (seperti revolver swing-out asli) dan silinder keluar ke kiri penembak (RevolverRenderer: rotZ +).
        bone("crane", (1.0, 1.5, -1.2), parent="frame", cubes=[
            cube((0.53, 1.4, -3.3), (0.62, 0.65, 0.55), "dark"),
        ]),
        bone("cylinder", cc, parent="crane", cubes=cylinder_cubes),
        bullet_bone(cc, z0 + length),
        bone("hammer", (0, 4.0, 2.2), parent="frame", cubes=[
            cube((-0.38, 3.7, 2.2), (0.76, 1.8, 0.7), pivot=(0, 4.0, 2.2), rotation=[-10, 0, 0]),
            cube((-0.5, 5.25, 2.4), (1.0, 0.5, 1.5), faces(up="check"), pivot=(0, 4.0, 2.2), rotation=[-14, 0, 0]),
        ]),
        bone("trigger", (0, 0.5, -0.8), parent="frame", cubes=[
            cube((-0.2, -1.05, -1.05), (0.4, 1.55, 0.5), faces(north="check")),
        ]),
        bone("grip", (0, 0.5, 2.0), parent="frame", rotation=[-18, 0, 0], cubes=[
            # Gagang membulat: badan + dua sisi miring di belakang.
            cube((-1.15, -5.5, 1.15), (2.3, 6.0, 2.3), {"north": "woodside", "south": "woodside",
                                                         "east": "wood", "west": "wood", "up": "dark", "down": "dark"}),
            cube((-0.95, -5.4, 3.0), (1.9, 5.8, 0.75), {"north": "woodside", "south": "woodside", "east": "wood",
                                                         "west": "wood", "up": "dark", "down": "dark"}),
            # Backstrap logam.
            cube((-0.55, -5.45, 3.65), (1.1, 6.0, 0.4)),
            # Butt cap.
            cube((-1.2, -5.95, 1.1), (2.4, 0.5, 2.9), "dark"),
            # Medali kuningan di kedua sisi.
            cube((-1.2, -2.9, 2.0), (0.06, 0.9, 0.9), "brass"),
            cube((1.14, -2.9, 2.0), (0.06, 0.9, 0.9), "brass"),
        ]),
    ]
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {"identifier": "geometry.liarsbar.revolver", "texture_width": TW, "texture_height": TH,
                            "visible_bounds_width": 2, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0.25, 0]},
            "bones": bones,
        }],
    }


def texture():
    rnd = random.Random(5)
    img = Image.new("RGBA", (TW, TH), (0, 0, 0, 0))
    px = img.load()

    def fill(region, base, var, shade=None):
        u, v, w, h = region
        for y in range(v, v + h):
            for x in range(u, u + w):
                k = rnd.uniform(-var, var)
                if shade:
                    k += shade((x - u) / max(1, w - 1), (y - v) / max(1, h - 1))
                px[x, y] = tuple(max(0, min(255, int(c + k))) for c in base) + (255,)

    # Baja kebiruan (blued steel): atas terang dengan pantulan, samping bergradasi, bawah gelap.
    fill(MAT["top"], (92, 98, 112), 4, lambda s, t: 26 * math.exp(-((s - 0.35) ** 2) / 0.02) - 8 * t)
    fill(MAT["side"], (52, 56, 66), 4, lambda s, t: 34 * math.exp(-t * 9) - 10 * t + 6 * math.sin(s * 3.1))
    fill(MAT["dark"], (28, 30, 36), 3)
    fill(MAT["rib"], (74, 79, 92), 3, lambda s, t: -26 if int(t * 16) % 3 == 0 else 6)
    # Walnut: serat panjang + checkering belah ketupat di tengah.
    def walnut(s, t):
        grain = 12 * math.sin(s * 9.0 + math.sin(t * 7.0) * 2.0)
        inside = 0.15 < s < 0.85 and 0.2 < t < 0.8
        diamond = (int(s * 16 + t * 16) % 2 == 0) != (int(s * 16 - t * 16) % 2 == 0)
        return grain + (-16 if inside and diamond else 0) + 8 * math.exp(-((s - 0.2) ** 2) / 0.01)
    fill(MAT["wood"], (98, 52, 28), 4, walnut)
    fill(MAT["woodside"], (90, 47, 25), 4, lambda s, t: 12 * math.sin(s * 9.0 + t * 2))
    fill(MAT["bore"], (6, 6, 8), 1)
    fill(MAT["brass"], (176, 138, 66), 8, lambda s, t: 30 * (1 - math.hypot(s - 0.35, t - 0.35) * 2))
    fill(MAT["check"], (64, 68, 78), 2, lambda s, t: -24 if (int(s * 16) + int(t * 16)) % 2 else 10)
    fill(MAT["flutes"], (60, 64, 75), 4, lambda s, t: (-30 if int(s * 16) % 4 in (1, 2) else 8) + 14 * math.exp(-t * 8))
    fill(MAT["front"], (64, 68, 79), 4)
    # Peluru: kuningan dengan pantulan memanjang, timah kusam, pantat dengan primer.
    fill(MAT["case"], (170, 128, 58), 3, lambda s, t: 40 * math.exp(-((s - 0.3) ** 2) / 0.02) - 22 * s)
    fill(MAT["lead"], (104, 106, 112), 3, lambda s, t: 22 * math.exp(-((s - 0.3) ** 2) / 0.03) - 14 * s)
    fill(MAT["headstamp"], (160, 120, 54), 3, lambda s, t: 26 * (1 - math.hypot(s - 0.4, t - 0.4) * 2))
    # Muka belakang silinder: enam chamber kosong identik (tidak pernah membocorkan letak peluru).
    d = ImageDraw.Draw(img)
    u, v, w, h = MAT["chambers"]
    fill(MAT["chambers"], (70, 74, 86), 4, lambda s, t: 18 * math.exp(-((s - 0.3) ** 2 + (t - 0.3) ** 2) / 0.05))
    cx, cy = u + w / 2, v + h / 2
    for i in range(6):
        a = math.pi / 2 + i * math.pi / 3
        x, y = cx + math.cos(a) * 5.0, cy - math.sin(a) * 5.0
        d.ellipse([x - 1.6, y - 1.6, x + 1.6, y + 1.6], fill=(40, 42, 48, 255))
        d.ellipse([x - 1.1, y - 1.1, x + 1.1, y + 1.1], fill=(8, 8, 10, 255))
    d.ellipse([cx - 1.0, cy - 1.0, cx + 1.0, cy + 1.0], fill=(34, 36, 42, 255))
    u, v, w, h = MAT["headstamp"]
    d.ellipse([u + 2.5, v + 2.5, u + 5.5, v + 5.5], fill=(122, 96, 60, 255))
    d.ellipse([u + 3.2, v + 3.2, u + 4.8, v + 4.8], fill=(150, 124, 84, 255))
    # Muka depan silinder: enam lubang lebih kecil.
    u, v, w, h = MAT["front"]
    cx, cy = u + w / 2, v + h / 2
    for i in range(6):
        a = math.pi / 2 + i * math.pi / 3
        x, y = cx + math.cos(a) * 5.0, cy - math.sin(a) * 5.0
        d.ellipse([x - 1.2, y - 1.2, x + 1.2, y + 1.2], fill=(10, 10, 12, 255))
    # Sekrup side plate di tekstur samping.
    u, v, w, h = MAT["side"]
    for sx, sy in ((5, 9), (11, 11)):
        d.ellipse([u + sx - 1, v + sy - 1, u + sx + 1, v + sy + 1], fill=(86, 90, 102, 255))
        d.point((u + sx, v + sy), fill=(30, 32, 38, 255))
    return img


def preview(out):
    """Pratinjau kasar: proyeksi samping dan atas dari kotak-kotak (tanpa rotasi kubus)."""
    geo = geometry()["minecraft:geometry"][0]["bones"]
    scale = 22
    img = Image.new("RGB", (40 * scale // 2, 22 * scale // 2), (40, 40, 46))
    d = ImageDraw.Draw(img)
    for b in geo:
        for c in b.get("cubes", []):
            x, y, z = c["origin"]
            sx, sy, sz = c["size"]
            col = (150, 110, 70) if "wood" in json.dumps(c["uv"]) else (110, 118, 136)
            # Samping: z ke kanan, y ke atas.
            x0 = (z + 13) * scale // 2
            y0 = (10 - (y + sy)) * scale // 2
            d.rectangle([x0, y0, x0 + sz * scale // 2, y0 + sy * scale // 2], outline=col)
    img.save(out)


def main():
    geo_dir = os.path.join(ROOT, "geo", "item")
    os.makedirs(geo_dir, exist_ok=True)
    with open(os.path.join(geo_dir, "revolver.geo.json"), "w", encoding="utf-8") as f:
        json.dump(geometry(), f, indent=1)
    tex_dir = os.path.join(ROOT, "textures", "item")
    os.makedirs(tex_dir, exist_ok=True)
    texture().save(os.path.join(tex_dir, "revolver.png"))
    anim_dir = os.path.join(ROOT, "animations", "item")
    os.makedirs(anim_dir, exist_ok=True)
    with open(os.path.join(anim_dir, "revolver.animation.json"), "w", encoding="utf-8") as f:
        json.dump({"format_version": "1.8.0", "animations": {}}, f)
    model_dir = os.path.join(ROOT, "models", "item")
    os.makedirs(model_dir, exist_ok=True)
    with open(os.path.join(model_dir, "revolver.json"), "w", encoding="utf-8") as f:
        json.dump({"parent": "builtin/entity", "gui_light": "front"}, f, indent=1)
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
    print("revolver model, texture, animation, item model written")


if __name__ == "__main__":
    main()
