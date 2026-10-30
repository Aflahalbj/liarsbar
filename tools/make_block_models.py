"""Blockstate, model block, dan model item untuk block bar (pasangan tools/make_blocks.py).

    python tools/make_block_models.py

Semua model dibangun dari elemen kotak di sini supaya ukuran (dalam 1/16 blok) mudah dibaca dan dituning.
Model dasar menghadap utara (sisi "depan" di -z); blockstate memutar y untuk arah lain.
Elemen bercahaya memakai forge_data block_light/sky_light 15 (tidak tergantung cahaya dunia).
"""
import json
import os

ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "liarsbar")
NS = "liarsbar"

FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}
GLOW = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}


def write(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


def tex(name):
    return f"{NS}:block/{name}"


def face(texture, uv=None, cull=None, rotation=None, glow=False):
    f = {"texture": texture}
    if uv:
        f["uv"] = uv
    if cull:
        f["cullface"] = cull
    if rotation:
        f["rotation"] = rotation
    if glow:
        f["forge_data"] = dict(GLOW)
    return f


def box(frm, to, faces, rotation=None, glow=False, shade=True):
    """faces: dict arah -> face(...) ; uv otomatis dari koordinat kalau tidak diberikan."""
    e = {"from": frm, "to": to, "faces": {}}
    for d, f in faces.items():
        f = dict(f)
        if "uv" not in f:
            f["uv"] = auto_uv(frm, to, d)
        e["faces"][d] = f
    if rotation:
        e["rotation"] = rotation
    if glow:
        e["forge_data"] = dict(GLOW)
    if not shade:
        e["shade"] = False
    return e


def auto_uv(frm, to, d):
    x0, y0, z0 = frm
    x1, y1, z1 = to

    def c(v):
        return max(0, min(16, v))

    if d in ("north",):
        return [c(16 - x1), c(16 - y1), c(16 - x0), c(16 - y0)]
    if d == "south":
        return [c(x0), c(16 - y1), c(x1), c(16 - y0)]
    if d == "west":
        return [c(z0), c(16 - y1), c(z1), c(16 - y0)]
    if d == "east":
        return [c(16 - z1), c(16 - y1), c(16 - z0), c(16 - y0)]
    if d == "up":
        return [c(x0), c(z0), c(x1), c(z1)]
    return [c(x0), c(16 - z1), c(x1), c(16 - z0)]


def all_faces(t, skip=(), **kw):
    return {d: face(t, **kw) for d in ("north", "south", "east", "west", "up", "down") if d not in skip}


def model(name, elements, textures, parent="block/block", render_type=None, ao=True, extra=None):
    m = {"parent": parent, "textures": textures, "elements": elements}
    if render_type:
        m["render_type"] = render_type
    if not ao:
        m["ambientocclusion"] = False
    if extra:
        m.update(extra)
    write(f"models/block/{name}.json", m)


def item_from_block(name, block_model=None):
    write(f"models/item/{name}.json", {"parent": f"{NS}:block/{block_model or name}"})


def simple_state(name, model_name=None):
    write(f"blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/{model_name or name}"}}})


def facing_variants(model_for, props=None):
    """props: list of (nama, nilai[]) tambahan; model_for(dict) -> nama model."""
    import itertools
    props = props or []
    variants = {}
    names = [p[0] for p in props]
    for facing, y in FACING_Y.items():
        for combo in itertools.product(*[p[1] for p in props]):
            state = dict(zip(names, combo))
            key = ",".join([f"facing={facing}"] + [f"{k}={v}" for k, v in state.items()])
            v = {"model": f"{NS}:block/{model_for(state)}"}
            if y:
                v["y"] = y
            variants[key] = v
    return variants


# =================================================================== kubus sederhana


def cubes():
    for name, t in (("bar_floor", "bar_floor"), ("bar_brick", "bar_brick"), ("bar_stone", "bar_stone"),
                    ("bar_walnut", "walnut_planks")):
        write(f"models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex(t)}})
        simple_state(name)
        item_from_block(name)
    write("models/block/bar_ceiling.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "bottom": tex("bar_ceiling"), "top": tex("bar_roof"), "side": tex("bar_ceiling")}})
    simple_state("bar_ceiling")
    item_from_block("bar_ceiling")


# =================================================================== dinding


def walls():
    """Dinding bar: sisi utara = interior (wainscot / wallpaper / wallpaper + list mahkota), sisi lain bata."""
    out = tex("bar_brick")
    for level, inner in (("wainscot", "bar_wainscot"), ("paper", "bar_wallpaper"), ("crown", "bar_wallpaper_top")):
        faces = {d: face("#out", cull=d) for d in ("south", "east", "west", "up", "down")}
        faces["north"] = face("#in", cull="north")
        els = [box([0, 0, 0], [16, 16, 16], faces)]
        if level == "wainscot":
            # List kursi (chair rail) menonjol 1 px.
            els.append(box([0, 13, -1], [16, 16, 0], {
                "north": face("#in", uv=[0, 0, 16, 3]), "up": face("#walnut", uv=[0, 0, 16, 1]),
                "down": face("#walnut", uv=[0, 0, 16, 1]), "east": face("#in", uv=[0, 0, 1, 3]),
                "west": face("#in", uv=[15, 0, 16, 3])}))
        if level == "crown":
            # List mahkota dua tingkat menonjol ke ruang.
            els.append(box([0, 12, -1], [16, 16, 0], {
                "north": face("#in", uv=[0, 0, 16, 4]), "down": face("#walnut", uv=[0, 0, 16, 1]),
                "east": face("#in", uv=[0, 0, 1, 4]), "west": face("#in", uv=[15, 0, 16, 4])}))
            els.append(box([0, 14, -2], [16, 16, -1], {
                "north": face("#in", uv=[0, 0, 16, 2]), "down": face("#walnut", uv=[0, 1, 16, 2]),
                "east": face("#in", uv=[0, 0, 1, 2]), "west": face("#in", uv=[15, 0, 16, 2])}))
        model(f"bar_wall_{level}", els, {"in": tex(inner), "out": out, "walnut": tex("walnut_planks"),
                                          "particle": out})
    write("blockstates/bar_wall.json", {"variants": facing_variants(lambda s: f"bar_wall_{s['level']}",
                                                                     [("level", ["wainscot", "paper", "crown"])])})
    item_from_block("bar_wall", "bar_wall_paper")


# =================================================================== counter, lemari


def counter():
    t = {"front": tex("counter_front"), "top": tex("counter_top"), "wood": tex("walnut_planks"),
         "brass": tex("brass"), "particle": tex("counter_front")}
    els = [
        # Badan (front di utara, mundur 1 px supaya daun counter menaungi).
        box([0, 0, 1], [16, 14, 16], {"north": face("#front", uv=[0, 2, 16, 16]), "south": face("#wood", cull="south"),
                                      "east": face("#wood", cull="east"), "west": face("#wood", cull="west"),
                                      "down": face("#wood", cull="down")}),
        # Daun counter, menjorok 2 px ke depan.
        box([0, 14, -2], [16, 16, 16], {"up": face("#top", uv=[0, 0, 16, 16]), "north": face("#top", uv=[0, 14, 16, 15]),
                                        "down": face("#wood"), "east": face("#wood", cull="east"),
                                        "west": face("#wood", cull="west"), "south": face("#wood", cull="south")}),
        # Footrail kuningan + dudukan.
        box([0, 2, -3.5], [16, 3.5, -2], {"north": face("#brass", uv=[0, 4, 16, 12]), "up": face("#brass", uv=[0, 0, 16, 6]),
                                          "down": face("#brass", uv=[0, 10, 16, 16]), "south": face("#brass", uv=[0, 4, 16, 12])}),
        box([7.25, 1.5, -2.5], [8.75, 3, 1], all_faces("#brass", skip=("down",))),
    ]
    model("bar_counter", els, t)
    write("blockstates/bar_counter.json", {"variants": facing_variants(lambda s: "bar_counter")})
    item_from_block("bar_counter")

    els = [box([0, 0, 0], [16, 15, 16], {"north": face("#front", uv=[0, 0, 16, 15]), "south": face("#wood", cull="south"),
                                         "east": face("#wood", cull="east"), "west": face("#wood", cull="west"),
                                         "down": face("#wood", cull="down")}),
           box([0, 15, -1], [16, 16, 16], {"up": face("#top"), "north": face("#top", uv=[0, 15, 16, 16]),
                                           "down": face("#wood"), "east": face("#wood", cull="east"),
                                           "west": face("#wood", cull="west"), "south": face("#wood", cull="south")})]
    model("back_cabinet", els, {"front": tex("cabinet_front"), "top": tex("counter_top"), "wood": tex("walnut_planks"),
                                "particle": tex("cabinet_front")})
    write("blockstates/back_cabinet.json", {"variants": facing_variants(lambda s: "back_cabinet")})
    item_from_block("back_cabinet")


# =================================================================== rak botol


def shelf():
    """Rak botol menempel di dinding (selatan), depan ke utara: papan, botol, strip lampu, rel kuningan."""
    t = {"back": tex("shelf_back"), "wood": tex("walnut_planks"), "top": tex("counter_top"), "bottles": tex("bottles"),
         "glow": tex("bottles_glow"), "strip": tex("strip_glow"), "brass": tex("brass"), "particle": tex("shelf_back")}
    for variant, rows in (("a", (0, 8)), ("b", (8, 0))):
        els = [box([0, 0, 15], [16, 16, 16], {"north": face("#back"), "south": face("#wood", cull="south"),
                                              "east": face("#wood", cull="east"), "west": face("#wood", cull="west"),
                                              "up": face("#wood", cull="up"), "down": face("#wood", cull="down")})]
        for k, y in enumerate((0, 8)):
            v0 = rows[k]
            els.append(box([0, y, 9], [16, y + 1, 15], {"up": face("#top", uv=[0, 0, 16, 6]), "down": face("#wood"),
                                                         "north": face("#wood", uv=[0, 7, 16, 8]),
                                                         "east": face("#wood", cull="east"),
                                                         "west": face("#wood", cull="west")}))
            # Botol: dua sisi (depan dan belakang) supaya tidak hilang dari sudut miring.
            els.append(box([0, y + 1, 12], [16, y + 8, 12], {
                "north": face("#bottles", uv=[16, v0 + 1, 0, v0 + 8]),
                "south": face("#bottles", uv=[0, v0 + 1, 16, v0 + 8])}))
            els.append(box([0, y + 1, 11.98], [16, y + 8, 11.98], {"north": face("#glow", uv=[16, v0 + 1, 0, v0 + 8])},
                           glow=True))
            # Strip lampu di bawah papan (terlihat dari depan sebagai garis menyala).
            els.append(box([1, y - 0.35, 12.5], [15, y, 14], {"down": face("#strip", uv=[0, 0, 16, 4]),
                                                                "north": face("#strip", uv=[0, 1, 16, 2])}, glow=True))
            els.append(box([0, y + 3, 8.5], [16, y + 3.5, 9], {"north": face("#brass", uv=[0, 6, 16, 10]),
                                                                "up": face("#brass", uv=[0, 2, 16, 6]),
                                                                "down": face("#brass", uv=[0, 10, 16, 14])}))
        model(f"bottle_shelf_{variant}", els, t, render_type="cutout")
    variants = {}
    for facing, y in FACING_Y.items():
        variants[f"facing={facing}"] = [dict({"model": f"{NS}:block/bottle_shelf_a"}, **({"y": y} if y else {})),
                                        dict({"model": f"{NS}:block/bottle_shelf_b"}, **({"y": y} if y else {}))]
    write("blockstates/bottle_shelf.json", {"variants": variants})
    item_from_block("bottle_shelf", "bottle_shelf_a")


# =================================================================== cermin


def mirror():
    for tx in range(3):
        for ty in range(2):
            els = [box([0, 0, 15], [16, 16, 16], {"north": face("#glass"), "south": face("#wood", cull="south"),
                                                  "east": face("#wood"), "west": face("#wood"),
                                                  "up": face("#wood"), "down": face("#wood")})]
            model(f"bar_mirror_{tx}_{ty}", els, {"glass": tex(f"mirror_{tx}_{ty}"), "wood": tex("walnut_planks"),
                                                 "particle": tex(f"mirror_{tx}_{ty}")})
    # tx 0 = kiri dari depan (barat bila menghadap selatan); ty 0 = atas.
    write("blockstates/bar_mirror.json", {"variants": facing_variants(
        lambda s: f"bar_mirror_{s['col']}_{1 - int(s['row'])}", [("col", ["0", "1", "2"]), ("row", ["0", "1"])])})
    item_from_block("bar_mirror", "bar_mirror_1_1")


# =================================================================== stool


def stool():
    t = {"s": tex("stool"), "particle": tex("stool")}
    L = "#s"
    els = [
        box([4, 0, 4], [12, 1, 12], {d: face(L, uv=[10, 5, 16, 7]) for d in ("north", "south", "east", "west")}
            | {"up": face(L, uv=[10, 5, 14, 9]), "down": face(L, uv=[10, 5, 14, 9], cull="down")}),
        box([7, 1, 7], [9, 10.5, 9], {d: face(L, uv=[10, 0, 11, 5]) for d in ("north", "south", "east", "west")}),
        # Cincin pijakan.
        box([4.5, 5, 4.5], [11.5, 5.75, 5.25], {d: face(L, uv=[10, 0, 16, 0.5]) for d in all_faces(L)}),
        box([4.5, 5, 10.75], [11.5, 5.75, 11.5], {d: face(L, uv=[10, 0, 16, 0.5]) for d in all_faces(L)}),
        box([4.5, 5, 5.25], [5.25, 5.75, 10.75], {d: face(L, uv=[10, 0, 16, 0.5]) for d in all_faces(L)}),
        box([10.75, 5, 5.25], [11.5, 5.75, 10.75], {d: face(L, uv=[10, 0, 16, 0.5]) for d in all_faces(L)}),
        box([5, 5.2, 7.6], [11, 5.6, 8.4], {d: face(L, uv=[10, 0, 16, 0.5]) for d in ("up", "down", "north", "south")}),
        # Dudukan.
        box([3.5, 10.5, 3.5], [12.5, 11.5, 12.5], {d: face(L, uv=[0, 13, 16, 16]) for d in ("north", "south", "east", "west", "down")}),
        box([3, 11.5, 3], [13, 13.5, 13], {d: face(L, uv=[0, 10, 16, 13]) for d in ("north", "south", "east", "west")}
            | {"up": face(L, uv=[0, 0, 10, 10]), "down": face(L, uv=[0, 13, 16, 16])}),
        box([4, 13.5, 4], [12, 14, 12], {d: face(L, uv=[0, 10, 16, 11]) for d in ("north", "south", "east", "west")}
            | {"up": face(L, uv=[1, 1, 9, 9])}),
    ]
    model("bar_stool", els, t)
    simple_state("bar_stool")
    item_from_block("bar_stool")


# =================================================================== sconce


def sconce():
    """Sconce dinding (dinding di selatan): pelat oval, lengan, kap tulip amber menyala redup, bohlam."""
    t = {"s": tex("sconce"), "particle": tex("sconce")}
    S = "#s"
    els = [
        box([5, 3, 15], [11, 11, 16], {"north": face(S, uv=[0, 0, 6, 8]), "east": face(S, uv=[2, 0, 3, 8]),
                                       "west": face(S, uv=[2, 0, 3, 8]), "up": face(S, uv=[2, 0, 3, 1]),
                                       "down": face(S, uv=[2, 7, 3, 8])}),
        box([7.5, 5.5, 11], [8.5, 6.5, 15], {d: face(S, uv=[8, 0, 16, 2]) for d in ("east", "west", "up", "down")}),
        box([7.25, 5.25, 10.25], [8.75, 7.5, 11.75], all_faces(S, uv=[8, 0, 9, 2])),
        # Kap tulip: dua kotak bertingkat, bercahaya (kaca amber diterangi bohlam).
        box([6.25, 7.5, 9.25], [9.75, 8.5, 12.75], {d: face(S, uv=[0, 12, 8, 14]) for d in ("north", "south", "east", "west")}
            | {"down": face(S, uv=[0, 8, 8, 10])}, glow=True),
        box([5.25, 8.5, 8.25], [10.75, 11.5, 13.75], {d: face(S, uv=[0, 8, 8, 16]) for d in ("north", "south", "east", "west")}
            | {"up": face(S, uv=[12, 8, 16, 12])}, glow=True),
        box([7, 10, 10], [9, 11.25, 12], {"up": face(S, uv=[8, 8, 12, 12])}, glow=True),
    ]
    model("wall_sconce", els, t, render_type="cutout")
    write("blockstates/wall_sconce.json", {"variants": facing_variants(lambda s: "wall_sconce")})
    item_from_block("wall_sconce")


# =================================================================== kipas, pipa


def fan():
    t = {"s": tex("fan"), "particle": tex("fan")}
    S = "#s"
    els = [
        box([6, 15, 6], [10, 16, 10], {d: face(S, uv=[0, 4, 8, 6]) for d in ("north", "south", "east", "west", "down")}),
        box([7.5, 9, 7.5], [8.5, 15, 8.5], {d: face(S, uv=[8, 4, 10, 16]) for d in ("north", "south", "east", "west")}),
        box([6, 6, 6], [10, 9, 10], {d: face(S, uv=[0, 4, 8, 10]) for d in ("north", "south", "east", "west")}
            | {"down": face(S, uv=[0, 8, 8, 12]), "up": face(S, uv=[0, 4, 8, 8])}),
        box([7, 5.25, 7], [9, 6, 9], all_faces(S, uv=[0, 10, 4, 12], skip=("up",))),
    ]
    model("ceiling_fan", els, t)
    simple_state("ceiling_fan")
    item_from_block("ceiling_fan")


def pipe():
    """Pipa besi di bawah plafon, sepanjang z (axis=z); axis=x diputar 90."""
    t = {"p": tex("pipe"), "f": tex("pipe_flange"), "b": tex("iron_bracket"), "particle": tex("pipe")}
    els = [
        box([6.5, 12, 0], [9.5, 15, 16], {"north": face("#p", uv=[6, 0, 9, 3], cull="north"),
                                          "south": face("#p", uv=[6, 0, 9, 3], cull="south"),
                                          "east": face("#p", uv=[0, 1, 16, 4], rotation=90),
                                          "west": face("#p", uv=[0, 1, 16, 4], rotation=90),
                                          "up": face("#p", uv=[0, 0, 16, 3], rotation=90),
                                          "down": face("#p", uv=[0, 10, 16, 13], rotation=90)}),
        box([6, 11.5, 0], [10, 15.5, 1.25], all_faces("#f", uv=[0, 0, 4, 4])),
        # Tali gantungan ke plafon.
        box([6.25, 15, 7.25], [9.75, 16, 8.75], all_faces("#b", uv=[0, 0, 4, 2], skip=("up",))),
        box([6.25, 11.75, 7.25], [6.75, 15, 8.75], all_faces("#b", uv=[0, 0, 2, 4])),
        box([9.25, 11.75, 7.25], [9.75, 15, 8.75], all_faces("#b", uv=[0, 0, 2, 4])),
    ]
    model("iron_pipe", els, t)
    write("blockstates/iron_pipe.json", {"variants": {"axis=z": {"model": f"{NS}:block/iron_pipe"},
                                                      "axis=x": {"model": f"{NS}:block/iron_pipe", "y": 90}}})
    item_from_block("iron_pipe")


# =================================================================== poster


def poster():
    for design in ("pull", "bluff"):
        for half in ("top", "bottom"):
            y0, y1 = (0, 15) if half == "top" else (1, 16)
            v0, v1 = (0, 15) if half == "top" else (1, 16)
            els = [box([0, y0, 15.5], [16, y1, 16], {"north": face("#art", uv=[0, v0, 16, v1]),
                                                      "east": face("#art", uv=[15, v0, 16, v1]),
                                                      "west": face("#art", uv=[0, v0, 1, v1]),
                                                      "up" if half == "top" else "down": face("#art", uv=[0, 0, 16, 1])})]
            model(f"poster_{design}_{half}", els, {"art": tex(f"poster/{design}_{half}"),
                                                   "particle": tex(f"poster/{design}_{half}")})
    write("blockstates/noir_poster.json", {"variants": facing_variants(
        lambda s: f"poster_{s['design']}_{'top' if s['half'] == 'upper' else 'bottom'}",
        [("design", ["pull", "bluff"]), ("half", ["upper", "lower"])])})
    item_from_block("noir_poster", "poster_pull_bottom")


# =================================================================== jendela kota malam


def window():
    """
    Jendela dalam dinding (interior di utara, luar di selatan). Dari luar ke dalam: kaca luar (z 16), kota malam
    (z 14.5, bercahaya), hujan animasi (z 10), kusen + sash kayu (z 2..4), lalu venetian blinds (z 1.2..1.8):
    bilah krem yang tepi atasnya tertangkap cahaya cyan dari luar, kota hanya terlihat di celahnya.
    Relung berplester gelap di tepi luar jendela; ambang kayu menonjol di tile bawah.
    """
    for scene in ("0", "1", "s"):
        panes = ("single",) if scene == "s" else ("left", "right")
        for pane in panes:
            for half in ("top", "bottom"):
                city = tex(f"window/city_{scene}_{pane}_{half}")
                left_edge = pane in ("left", "single")
                right_edge = pane in ("right", "single")
                els = [
                    box([0, 0, 15.9], [16, 16, 16], {"south": face("#outside", cull="south")}),
                    box([0, 0, 14.5], [16, 16, 14.5], {"north": face("#city")}, glow=True),
                    box([0, 0, 10], [16, 16, 10], {"north": face("#rain")}, glow=True),
                ]
                # Relung (dari dalam melihat ke selatan, kiri = timur / x besar).
                if left_edge:
                    els.append(box([14.5, 0, 0], [16, 16, 14.5], {"west": face("#reveal"), "north": face("#frame")}))
                if right_edge:
                    els.append(box([0, 0, 0], [1.5, 16, 14.5], {"east": face("#reveal"), "north": face("#frame")}))
                if half == "top":
                    els.append(box([0, 14.5, 0], [16, 16, 14.5], {"down": face("#reveal"), "north": face("#frame")}))
                else:
                    els.append(box([0, 0, 0], [16, 1.5, 14.5], {"up": face("#reveal"), "north": face("#frame")}))
                    els.append(box([0, 0.5, -2], [16, 1.75, 1], {"up": face("#frame", uv=[0, 0, 16, 3]),
                                                                 "north": face("#frame", uv=[0, 0, 16, 1.25]),
                                                                 "down": face("#frame", uv=[0, 0, 16, 3]),
                                                                 "east": face("#frame", uv=[0, 0, 3, 1.25]),
                                                                 "west": face("#frame", uv=[0, 0, 3, 1.25])}))
                # Sash: bingkai tepi + mullion/transom bersama.
                sash = [([13.5, 0, 2], [15, 16, 4]) if left_edge else ([15.25, 0, 2], [16, 16, 4]),
                        ([1, 0, 2], [2.5, 16, 4]) if right_edge else ([0, 0, 2], [0.75, 16, 4])]
                if half == "top":
                    sash += [([0, 13.5, 2], [16, 15, 4]), ([0, 0, 2], [16, 0.5, 4])]
                else:
                    sash += [([0, 1.75, 2], [16, 3, 4]), ([0, 15.5, 2], [16, 16, 4])]
                for frm, to in sash:
                    els.append(box(frm, to, all_faces("#frame")))
                # Blinds: rentang di antara kusen.
                x0 = 1.5 if right_edge else 0
                x1 = 14.5 if left_edge else 16
                y_lo, y_hi = (0, 13.5) if half == "top" else (2.4, 16)
                y = y_hi - 1.9
                while y >= y_lo - 0.01:
                    els.append(box([x0, y, 1.2], [x1, y + 1.3, 1.8], {
                        "north": face("#slat", uv=[0, 0, 16, 2]), "down": face("#slat", uv=[0, 4, 16, 5]),
                        "up": face("#slat", uv=[0, 0, 16, 1])}))
                    els.append(box([x0, y + 1.3, 1.2], [x1, y + 1.45, 1.8], {
                        "north": face("#slatglow", uv=[0, 0, 16, 1]), "up": face("#slatglow", uv=[0, 0, 16, 2])},
                        glow=True))
                    y -= 1.9
                if half == "top":
                    els.append(box([x0, 13.5, 0.6], [x1, 15, 2], all_faces("#frame")))
                else:
                    els.append(box([x0, 2.4, 1.0], [x1, 3.0, 2.0], all_faces("#slat", uv=[0, 0, 16, 1])))
                model(f"noir_window_{scene}_{pane}_{half}", els, {
                    "city": city, "rain": tex("window/rain"), "outside": tex("window/outside"),
                    "reveal": tex("window/reveal"), "frame": tex("window/frame"), "slat": tex("window/slat"),
                    "slatglow": tex("window/slat_glow"), "particle": tex("window/frame")},
                    render_type="cutout", ao=False)
    variants = {}
    for facing, y in FACING_Y.items():
        for pane in ("left", "right", "single"):
            for half in ("upper", "lower"):
                for scene in ("0", "1"):
                    sc = "s" if pane == "single" else scene
                    h = "top" if half == "upper" else "bottom"
                    v = {"model": f"{NS}:block/noir_window_{sc}_{pane}_{h}"}
                    if y:
                        v["y"] = y
                    variants[f"facing={facing},half={half},pane={pane},scene={scene}"] = v
    write("blockstates/noir_window.json", {"variants": variants})
    item_from_block("noir_window", "noir_window_0_left_bottom")


# =================================================================== pintu


def door():
    for part in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open", "top_left", "top_left_open",
                 "top_right", "top_right_open"):
        write(f"models/block/frosted_door_{part}.json", {"parent": f"minecraft:block/door_{part}", "textures": {
            "bottom": tex("door_bottom"), "top": tex("door_top")}})
    # Variasi pintu disalin dari blockstate vanilla dark_oak_door (rotasi buka/tutup sudah benar di sana).
    ref = os.path.join(os.path.dirname(__file__), "..", "build", "mcref", "assets", "minecraft", "blockstates",
                       "dark_oak_door.json")
    with open(ref, encoding="utf-8") as fh:
        variants = json.load(fh)["variants"]
    for v in variants.values():
        v["model"] = v["model"].replace("minecraft:block/dark_oak_door_", f"{NS}:block/frosted_door_")
    write("blockstates/frosted_door.json", {"variants": variants})
    write("models/item/frosted_door.json", {"parent": "minecraft:item/generated",
                                           "textures": {"layer0": f"{NS}:item/frosted_door"}})


# =================================================================== papan nama, kanopi


def sign():
    """Papan nama gantung tegak lurus dinding. part=inner (menempel dinding) / outer. Dinding di selatan."""
    for part in ("inner", "outer"):
        # Dari timur ("east" face) tile kiri ada di dekat dinding? Dinding di selatan (z 16), papan sepanjang z.
        # Dilihat dari timur: utara di kanan -> tile dekat dinding (selatan) tampil di kiri.
        east_t = "left" if part == "inner" else "right"
        west_t = "right" if part == "inner" else "left"
        els = [
            box([7.5, 0, 0], [8.5, 15, 16], {"east": face(f"#{east_t}", uv=[0, 1, 16, 16]),
                                             "west": face(f"#{west_t}", uv=[0, 1, 16, 16]),
                                             "down": face("#bracket", uv=[0, 0, 1, 16]),
                                             "north" if part == "outer" else "south": face("#bracket", uv=[0, 0, 1, 15])}),
            box([8.55, 0, 0], [8.55, 15, 16], {"east": face(f"#{east_t}_glow", uv=[0, 1, 16, 16])}, glow=True),
            box([7.45, 0, 0], [7.45, 15, 16], {"west": face(f"#{west_t}_glow", uv=[0, 1, 16, 16])}, glow=True),
            # Lengan besi di atas papan + rantai pendek.
            box([7.5, 15.5, 0 if part == "outer" else 0], [8.5, 16.5, 16], all_faces("#bracket", uv=[0, 0, 16, 1])),
            box([7.75, 15, 2], [8.25, 15.5, 2.5], all_faces("#bracket", uv=[0, 0, 1, 1])),
            box([7.75, 15, 13.5], [8.25, 15.5, 14], all_faces("#bracket", uv=[0, 0, 1, 1])),
        ]
        if part == "inner":
            els.append(box([7, 12, 15], [9, 17.5, 16], all_faces("#bracket", uv=[0, 0, 2, 5])))
        model(f"bar_sign_{part}", els, {"left": tex("sign/left"), "right": tex("sign/right"),
                                        "left_glow": tex("sign/left_glow"), "right_glow": tex("sign/right_glow"),
                                        "bracket": tex("iron_bracket"), "particle": tex("sign/left")},
              render_type="cutout")
    write("blockstates/bar_sign.json", {"variants": facing_variants(lambda s: f"bar_sign_{s['part']}",
                                                                     [("part", ["inner", "outer"])])})
    item_from_block("bar_sign", "bar_sign_inner")


def awning():
    """Kanopi kanvas miring dari dinding (selatan) ke depan (utara), valance bergerigi."""
    els = [
        box([0, 15, 0], [16, 15.5, 16], {"up": face("#canvas", rotation=180), "down": face("#canvas"),
                                         "north": face("#canvas", uv=[0, 0, 16, 0.5])},
            rotation={"angle": -22.5, "axis": "x", "origin": [8, 16, 16]}),
        box([0, 3.5, 1.1], [16, 9.8, 1.1], {"north": face("#valance", uv=[0, 0, 16, 8]),
                                            "south": face("#valance", uv=[0, 0, 16, 8])}),
        box([0, 9.4, 0.9], [16, 10.2, 1.4], {"north": face("#brass", uv=[0, 4, 16, 6]), "up": face("#brass", uv=[0, 4, 16, 6]),
                                             "down": face("#brass", uv=[0, 4, 16, 6])}),
    ]
    model("bar_awning", els, {"canvas": tex("awning"), "valance": tex("awning_valance"), "brass": tex("brass"),
                              "particle": tex("awning")}, render_type="cutout")
    write("blockstates/bar_awning.json", {"variants": facing_variants(lambda s: "bar_awning")})
    item_from_block("bar_awning")


def booth():
    """Booth kulit menghadap utara (sandaran di selatan, setinggi ~1,4 blok)."""
    t = {"b": tex("booth"), "w": tex("walnut_planks"), "particle": tex("booth")}
    els = [
        box([0, 0, 2], [16, 6, 16], {"north": face("#b", uv=[0, 14, 16, 16]), "east": face("#w", cull="east"),
                                     "west": face("#w", cull="west"), "down": face("#w", cull="down"),
                                     "south": face("#w", cull="south")}),
        box([0, 6, 1], [16, 8.5, 13], {"up": face("#b", uv=[0, 10, 16, 14]), "north": face("#b", uv=[0, 10, 16, 11.25]),
                                        "east": face("#b", uv=[0, 10, 6, 11.25], cull="east"),
                                        "west": face("#b", uv=[0, 10, 6, 11.25], cull="west")}),
        box([0, 6, 13], [16, 22, 16], {"north": face("#b", uv=[0, 0, 16, 10]), "south": face("#w", cull="south"),
                                        "east": face("#w", cull="east"), "west": face("#w", cull="west"),
                                        "up": face("#w", uv=[0, 0, 16, 3])}),
        box([0, 22, 12.5], [16, 23, 16], {"up": face("#w"), "north": face("#w", uv=[0, 0, 16, 1]),
                                           "down": face("#w"), "east": face("#w"), "west": face("#w")}),
    ]
    model("bar_booth", els, t)
    write("blockstates/bar_booth.json", {"variants": facing_variants(lambda s: "bar_booth")})
    item_from_block("bar_booth")


def side_table():
    """Meja kecil walnut berkaki tunggal dengan lampu banker (kap hijau, dalam menyala)."""
    t = {"s": tex("table_lamp"), "particle": tex("table_lamp")}
    S = "#s"
    els = [
        box([4.5, 0, 4.5], [11.5, 1, 11.5], all_faces(S, uv=[0, 12, 16, 13])),
        box([7, 1, 7], [9, 11, 9], {d: face(S, uv=[8, 8, 9, 13]) for d in ("north", "south", "east", "west")}),
        box([1.5, 11, 1.5], [14.5, 12.25, 14.5], {"up": face(S, uv=[0, 0, 8, 8]), "down": face(S, uv=[0, 0, 8, 8]),
                                                  "north": face(S, uv=[0, 12, 16, 13]), "south": face(S, uv=[0, 12, 16, 13]),
                                                  "east": face(S, uv=[0, 12, 16, 13]), "west": face(S, uv=[0, 12, 16, 13])}),
        box([5.5, 12.25, 6.5], [9.5, 12.75, 9.5], all_faces(S, uv=[0, 8, 8, 10], skip=("down",))),
        box([7.25, 12.75, 7.75], [7.75, 15.5, 8.25], all_faces(S, uv=[0, 8, 1, 10])),
        box([4, 15, 6], [10, 16.75, 10], {"up": face(S, uv=[8, 0, 16, 2]), "north": face(S, uv=[8, 0, 16, 4]),
                                          "south": face(S, uv=[8, 0, 16, 4]), "east": face(S, uv=[8, 0, 16, 4]),
                                          "west": face(S, uv=[8, 0, 16, 4])}),
        box([4.25, 15.01, 6.25], [9.75, 15.01, 9.75], {"down": face(S, uv=[8, 4, 16, 8])}, glow=True),
    ]
    model("side_table", els, t)
    simple_state("side_table")
    item_from_block("side_table")


def marquee():
    """Papan marquee di dinding (dinding di selatan), 3 tile; col 0 = kiri dilihat dari depan."""
    for col in range(3):
        els = [
            box([0, 0, 14], [16, 16, 16], {"north": face("#art"), "up": face("#art", uv=[0, 0, 16, 2]),
                                           "down": face("#art", uv=[0, 14, 16, 16]),
                                           "east": face("#art", uv=[0, 0, 2, 16]), "west": face("#art", uv=[14, 0, 16, 16])}),
            box([0, 0, 13.95], [16, 16, 13.95], {"north": face("#glow")}, glow=True),
        ]
        model(f"bar_marquee_{col}", els, {"art": tex(f"marquee/{col}"), "glow": tex(f"marquee/{col}_glow"),
                                          "particle": tex(f"marquee/{col}")}, render_type="cutout")
    write("blockstates/bar_marquee.json", {"variants": facing_variants(lambda s: f"bar_marquee_{s['col']}",
                                                                        [("col", ["0", "1", "2"])])})
    item_from_block("bar_marquee", "bar_marquee_1")


def bistro():
    """Meja bistro bundar (daun walnut + bibir kuningan, kaki besi cor) dengan dua gelas whiskey dan asbak."""
    t = {"s": tex("bistro"), "particle": tex("bistro")}
    S = "#s"
    iron = {d: face(S, uv=[8, 0, 16, 4]) for d in ("north", "south", "east", "west", "up", "down")}
    els = [
        box([4, 0, 4], [12, 0.75, 12], iron),
        box([7.25, 0.75, 7.25], [8.75, 11, 8.75], dict(iron)),
        box([1, 11, 1], [15, 12, 15], {"up": face(S, uv=[0, 0, 8, 8]), "down": face(S, uv=[0, 0, 8, 8]),
                                       "north": face(S, uv=[0, 7.5, 8, 8]), "south": face(S, uv=[0, 7.5, 8, 8]),
                                       "east": face(S, uv=[0, 7.5, 8, 8]), "west": face(S, uv=[0, 7.5, 8, 8])}),
    ]

    def glass():
        g = {d: face(S, uv=[8, 8, 12, 12]) for d in ("north", "south", "east", "west")}
        g["up"] = face(S, uv=[8, 9.5, 12, 12])
        return g

    els.append(box([4, 12, 9], [5.5, 13.75, 10.5], glass()))
    els.append(box([10.5, 12, 5], [12, 13.75, 6.5], glass()))
    els.append(box([7, 12, 3.5], [9, 12.5, 5.5], {d: face(S, uv=[12, 8, 16, 12])
                                                  for d in ("north", "south", "east", "west", "up")}))
    model("bistro_table", els, t)
    simple_state("bistro_table")
    item_from_block("bistro_table")


def chair():
    """Kursi bentwood menghadap utara: dudukan rotan, sandaran melengkung di selatan."""
    t = {"s": tex("bistro"), "particle": tex("bistro")}
    S = "#s"

    def wood():
        return {d: face(S, uv=[8, 4, 16, 8]) for d in ("north", "south", "east", "west", "up", "down")}

    els = []
    for x, z in ((4, 3.5), (11, 3.5), (4, 11.5), (11, 11.5)):
        els.append(box([x, 0, z], [x + 1, 7, z + 1], wood()))
    els.append(box([3.5, 2.5, 4], [12.5, 3, 12.5], wood()))
    els.append(box([3, 7, 3], [13, 8, 13], {"up": face(S, uv=[0, 8, 8, 16]), "down": face(S, uv=[0, 8, 8, 16]),
                                             "north": face(S, uv=[8, 4, 16, 5]), "south": face(S, uv=[8, 4, 16, 5]),
                                             "east": face(S, uv=[8, 4, 16, 5]), "west": face(S, uv=[8, 4, 16, 5])}))
    els.append(box([4, 8, 11.5], [5, 18, 12.5], wood()))
    els.append(box([11, 8, 11.5], [12, 18, 12.5], wood()))
    els.append(box([4, 17, 11.5], [12, 18.5, 12.5], wood()))
    els.append(box([4, 13, 11.75], [12, 13.75, 12.25], wood()))
    model("bar_chair", els, t)
    write("blockstates/bar_chair.json", {"variants": facing_variants(lambda s: "bar_chair")})
    item_from_block("bar_chair")


def npc_items():
    """
    Gelas dan botol whiskey sebagai model 3D (bukan sprite datar yang dipegang miring seperti alat).
    Titik tengah genggaman di (8, 8, 8): rantai ItemInHandLayer memetakan +y item ke arah atas lengan bawah,
    jadi saat lengan bawah mendatar (pose minum, membawa, menuang) gelas berdiri tegak.
    """
    hand = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.55, 0.55, 0.55]}
    display = {
        "thirdperson_righthand": hand, "thirdperson_lefthand": hand,
        "firstperson_righthand": {"rotation": [0, -20, 0], "translation": [0, 2, 0], "scale": [0.7, 0.7, 0.7]},
        "firstperson_lefthand": {"rotation": [0, -20, 0], "translation": [0, 2, 0], "scale": [0.7, 0.7, 0.7]},
        "gui": {"rotation": [25, 45, 0], "translation": [0, 0, 0], "scale": [1.1, 1.1, 1.1]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
    }
    g = "#t"
    sides = ("north", "south", "east", "west")
    glass = [
        box([6, 5.25, 6], [10, 10.75, 10], {d: face(g, uv=[4, 6, 12, 15]) for d in sides}
            | {"up": face(g, uv=[5, 9, 11, 13]), "down": face(g, uv=[4, 14, 12, 15])}),
    ]
    write("models/item/whiskey_glass.json", {"parent": "minecraft:block/block", "display": display,
                                             "textures": {"t": f"{NS}:item/whiskey_glass",
                                                          "particle": f"{NS}:item/whiskey_glass"},
                                             "elements": glass})
    bottle = [
        box([6, 2.5, 6], [10, 10, 10], {d: face(g, uv=[5, 6, 11, 15]) for d in sides}
            | {"up": face(g, uv=[5, 6, 11, 8]), "down": face(g, uv=[5, 14, 11, 15])}),
        box([7.25, 10, 7.25], [8.75, 13, 8.75], {d: face(g, uv=[7, 2, 9, 6]) for d in sides}),
        box([7, 13, 7], [9, 14, 9], {d: face(g, uv=[7, 1, 9, 2]) for d in sides} | {"up": face(g, uv=[7, 1, 9, 2])}),
    ]
    write("models/item/whiskey_bottle.json", {"parent": "minecraft:block/block", "display": display,
                                              "textures": {"t": f"{NS}:item/whiskey_bottle",
                                                           "particle": f"{NS}:item/whiskey_bottle"},
                                              "elements": bottle})


def main():
    cubes()
    walls()
    counter()
    shelf()
    mirror()
    stool()
    sconce()
    fan()
    pipe()
    poster()
    window()
    door()
    sign()
    awning()
    booth()
    side_table()
    marquee()
    bistro()
    chair()
    npc_items()
    print("models written")


if __name__ == "__main__":
    main()
