"""Sesi Blockbench (MCP) untuk revolver Art Bible.

    python tools/artbible/bb_revolver.py import     -> project GeckoLib baru, impor geo + tekstur dari assets
    python tools/artbible/bb_revolver.py shots NAME -> pratinjau 4 sudut (samping, 3/4, atas, belakang) ke run/bb/NAME.png
    python tools/artbible/bb_revolver.py eval FILE  -> jalankan JS (dibungkus Undo) di project revolver
    python tools/artbible/bb_revolver.py export     -> ekspor geo GeckoLib + tekstur + .bbmodel ke assets / docs
"""
import base64
import io
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import bbmcp  # noqa: E402
from PIL import Image  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "liarsbar")
GEO = os.path.join(ASSETS, "geo", "item", "revolver.geo.json")
TEX = os.path.join(ASSETS, "textures", "item", "revolver.png")
OUT = os.path.join(ROOT, "run", "bb")
VIEW = "rev"

# Sudut kamera (unit Blockbench = piksel model, model di sekitar titik asal; laras ke -z).
ANGLES = {
    "side": ([-60, 0, -6], [0, 0, -6]),
    "three_q": ([-40, 22, -40], [0, -1, -6]),
    "top": ([0, 70, -6.01], [0, 0, -6]),
    "back": ([22, 10, 40], [0, 0, -6]),
}


def ensure_view():
    try:
        bbmcp.call("create_offscreen_view", {"id": VIEW, "width": 900, "height": 600, "antialias": False,
                                            "copy_view": "none"})
    except RuntimeError:
        pass


def shot(angle):
    pos, target = ANGLES[angle]
    bbmcp.call("set_camera_angle", {"view": VIEW, "position": pos, "target": target, "projection": "orthographic",
                                    "zoom": 0.9 if angle != "three_q" else 0.75})
    res = bbmcp.call("capture_screenshot", {"view": VIEW, "format": "png"})
    for c in res.get("content", []):
        if c.get("type") == "image":
            return Image.open(io.BytesIO(base64.b64decode(c["data"]))).convert("RGB")
    raise RuntimeError(bbmcp.text(res))


def js(code):
    res = bbmcp.call("risky_eval", {"code": "(() => {" + code + "})()"})
    return bbmcp.text(res)


def main():
    bbmcp.connect()
    os.makedirs(OUT, exist_ok=True)
    cmd = sys.argv[1]
    if cmd == "import":
        # Tanpa akses file dari plugin (tidak memicu dialog izin): geo dan tekstur dikirim inline.
        print(bbmcp.text(bbmcp.call("create_project", {"name": "revolver", "format": "geckolib_model"})))
        r = bbmcp.call("from_geo_json", {"geojson": open(GEO, encoding="utf-8").read()})
        print(bbmcp.text(r)[:400])
        data = base64.b64encode(open(TEX, "rb").read()).decode()
        print(js("const t = new Texture({name: 'revolver'}).fromDataURL('data:image/png;base64,%s').add(false); "
                 "Cube.all.forEach(c => c.applyTexture(t, true)); Canvas.updateAll(); "
                 "return {cubes: Cube.all.length, groups: Group.all.map(g => g.name)};" % data))
    elif cmd == "shots":
        ensure_view()
        name = sys.argv[2] if len(sys.argv) > 2 else "rev"
        tiles = [shot(a) for a in ANGLES]
        w, h = tiles[0].size
        sheet = Image.new("RGB", (w * 2, h * 2))
        for i, t in enumerate(tiles):
            sheet.paste(t, ((i % 2) * w, (i // 2) * h))
        path = os.path.join(OUT, name + ".png")
        sheet.save(path)
        print(path)
    elif cmd == "eval":
        code = open(sys.argv[2], encoding="utf-8").read()
        print(js(code))
    elif cmd == "export":
        # Plugin hanya mengompilasi; file ditulis dari sini (plugin tidak perlu izin file).
        r = bbmcp.call("geckolib_export_model", {"mode": "compile", "max_content_length": 2000000})
        import json
        wrapper = json.loads(bbmcp.text(r))
        geo = json.loads(wrapper["content"])
        geo["minecraft:geometry"][0]["description"]["identifier"] = "geometry.liarsbar.revolver"
        content = json.dumps(geo, indent=1)
        open(GEO, "w", encoding="utf-8").write(content)
        b64 = js("return Texture.all[0].getBase64();").strip().strip('"')
        open(TEX, "wb").write(base64.b64decode(b64))
        model = js("return Codecs.project.compile({compressed: false});")
        open(os.path.join(ROOT, "docs", "artbible", "revolver.bbmodel"), "w", encoding="utf-8").write(model)
        print("exported", len(content), "chars geo")


if __name__ == "__main__":
    main()
