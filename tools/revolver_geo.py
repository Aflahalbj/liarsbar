"""TIDAK DIPAKAI: revolver in-game dibuat tools/make_revolver.py (pilihan user 2026-10-31). Skrip ini menimpanya,
jadi main() menolak tanpa --bbmodel.

Revolver dari sumber Blockbench (docs/artbible/revolver.bbmodel, revolver Art Bible 2026-10-27).

    python tools/revolver_geo.py   -> tulis geo/item/revolver.geo.json + textures/item/revolver.png

Konversi sama dengan ekspor GeckoLib Blockbench (x dibalik, uv atas/bawah dibalik), ditambah dua hal yang tidak
ada di .bbmodel:
  - engsel crane dicerminkan ke sisi kiri revolver: GeckoLib mencerminkan sumbu x, jadi engsel di x JSON -1
    (hasil ekspor) membuka silinder ke KANAN penembak. Revolver swing-out asli (dan tangan kiri yang
    memutarnya) membuka ke kiri. RevolverRenderer memutar crane dengan rotZ positif.
  - bone "bullet": satu peluru di chamber kanan atas muka belakang silinder (anak cylinder), disembunyikan /
    digeser keluar sepanjang sumbu chamber saat diisi (RevolverRenderer, RouletteDirector.chamberBullet).
Model dan tekstur diedit di Blockbench (tools/artbible/bb_revolver.py), lalu jalankan skrip ini lagi.
"""
import base64
import io
import json
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "docs", "artbible", "revolver.bbmodel")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "liarsbar")

# Peluru .38 (piksel model; silinder panjang 4,9, lubang chamber +-1 piksel tekstur = 0,43): sama dengan
# BulletRenderer dan RevolverRenderer.BULLET_LENGTH (CASE + NOSE).
BULLET_CASE = 3.8
BULLET_NOSE = 0.7
# Diameter 1,3x ukuran nyata supaya siluetnya terbaca di samping kepalan Minecraft (lubang chamber tetap tertutup).
BULLET_D = 0.72
BULLET_RIM = 0.96
BULLET_RIM_T = 0.12
# Swatch tekstur (u, v kiri atas, 3x3 di dalam kotak 4x4) seperti kubus lain di model ini.
BRASS, BRASS_LIGHT, DARK = (4.5, 16.5), (0.5, 16.5), (4.5, 8.5)


def load():
    raw = json.load(open(SRC, encoding="utf-8"))
    return json.loads(raw) if isinstance(raw, str) else raw


def face_uv(key, uv):
    u1, v1, u2, v2 = uv
    if key in ("up", "down"):
        return {"uv": [u2, v2], "uv_size": [u1 - u2, v1 - v2]}
    return {"uv": [u1, v1], "uv_size": [u2 - u1, v2 - v1]}


def cube(e):
    f, t = e["from"], e["to"]
    c = {"origin": [round(-t[0], 4), f[1], f[2]], "size": [round(t[i] - f[i], 4) for i in range(3)],
         "uv": {k: face_uv(k, v["uv"]) for k, v in e["faces"].items() if v.get("texture") is not None}}
    rot = e.get("rotation")
    if rot and any(rot):
        o = e["origin"]
        c["pivot"] = [-o[0], o[1], o[2]]
        c["rotation"] = [-rot[0], -rot[1], rot[2]]
    if e.get("inflate"):
        c["inflate"] = e["inflate"]
    return c


def swatch(uv, faces=("north", "south", "east", "west", "up", "down")):
    return {k: {"uv": list(uv), "uv_size": [3, 3]} for k in faces}


def bullet_bone():
    """Chamber kanan atas di muka belakang silinder (pusat silinder y 1,9; muka belakang z -1,2)."""
    x, y, rear = 0.64, 2.75, -1.2
    r, rr, rn = BULLET_D / 2, BULLET_RIM / 2, BULLET_D / 2 * 0.8
    case = swatch(BRASS)
    return {"name": "bullet", "parent": "cylinder", "pivot": [x, y, rear], "cubes": [
        {"origin": [x - r, y - r, rear - BULLET_CASE], "size": [BULLET_D, BULLET_D, BULLET_CASE], "uv": case},
        {"origin": [x - rn, y - rn, rear - BULLET_CASE - BULLET_NOSE], "size": [rn * 2, rn * 2, BULLET_NOSE],
         "uv": swatch(DARK)},
        {"origin": [x - rr, y - rr, rear], "size": [BULLET_RIM, BULLET_RIM, BULLET_RIM_T],
         "uv": dict(case, south={"uv": list(BRASS_LIGHT), "uv_size": [3, 3]})},
    ]}


def geometry(m):
    els = {e["uuid"]: e for e in m["elements"]}
    groups = {g["uuid"]: g for g in m.get("groups", [])}
    bones = []

    def walk(node, parent):
        g = groups[node["uuid"]]
        o, rot = g["origin"], g.get("rotation") or [0, 0, 0]
        b = {"name": g["name"], "pivot": [-o[0], o[1], o[2]]}
        if parent:
            b["parent"] = parent
        if any(rot):
            b["rotation"] = [-rot[0], -rot[1], rot[2]]
        cubes = [cube(els[c]) for c in node["children"] if isinstance(c, str)]
        if cubes:
            b["cubes"] = cubes
        bones.append(b)
        for c in node["children"]:
            if not isinstance(c, str):
                walk(c, g["name"])

    for n in m["outliner"]:
        walk(n, None)
    for b in bones:
        if b["name"] == "crane":
            b["pivot"][0] *= -1
            for c in b.get("cubes", []):
                c["origin"][0] = round(-(c["origin"][0] + c["size"][0]), 4)
    bones.insert([b["name"] for b in bones].index("cylinder") + 1, bullet_bone())
    res = m["resolution"]
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.liarsbar.revolver", "texture_width": res["width"],
                        "texture_height": res["height"], "visible_bounds_width": 3, "visible_bounds_height": 2,
                        "visible_bounds_offset": [0, 0.25, 0]},
        "bones": bones}]}


def main():
    if "--bbmodel" not in sys.argv:
        sys.exit("tidak dipakai: revolver in-game dari tools/make_revolver.py; --bbmodel untuk tetap menimpanya")
    m = load()
    with open(os.path.join(ASSETS, "geo", "item", "revolver.geo.json"), "w", encoding="utf-8") as f:
        json.dump(geometry(m), f, indent=1)
    src = m["textures"][0]["source"]
    Image.open(io.BytesIO(base64.b64decode(src.split(",", 1)[1]))).save(
        os.path.join(ASSETS, "textures", "item", "revolver.png"))
    print("revolver geo + texture from", os.path.relpath(SRC, ROOT))


if __name__ == "__main__":
    main()
