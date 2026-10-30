"""Spec per style frame: kamera + lampu + properti (koordinat MC relatif pusat meja)."""
import sys, os, json, math, subprocess
import numpy as np
sys.path.insert(0, os.path.dirname(__file__))
from cam import *
SP = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ART = (SP + "/art").replace("\\", "/")
SKIN = (SP + "/mc/assets/minecraft/textures/entity/player/wide/").replace("\\", "/")
BLENDER = "C:/Program Files/Blender Foundation/Blender 4.2/blender.exe"
WARM = (1.0, 0.60, 0.28)
CYAN_ = (0.35, 0.78, 1.0)
WINDOW = [{"pos": (-4.5, 2.1, 0.0), "target": (0, 1.1, 0), "energy": 420, "color": CYAN_, "size": 2.6}]
CYAN = (0.35, 0.78, 1.0)

def lamp_pos(i, n=4):
    eye, fwd, right = seat(i, n)
    p = np.array([eye[0], 0, eye[2]]) + fwd * 0.75
    return [float(p[0]), 2.45, float(p[2])]

def lamps(active, dead=(), n=4, hi=95, lo=16):
    return [{"pos": lamp_pos(i, n), "energy": (0 if i in dead else hi if i == active else lo), "color": WARM, "size": 72, "blend": 0.5} for i in range(n)]

def seat_props(i, cards, tallies, n=4, fan=True):
    eye, fwd, right = seat(i, n)
    out = [{"type": "tally", "eye": eye.tolist(), "fwd": fwd.tolist(), "count": tallies}]
    if fan and cards:
        p = eye + fwd * 0.40 + np.array([0, -0.36, 0])
        out.append({"type": "fan", "n": cards, "faces": ["back"], "pos": p.tolist(), "face": (-fwd).tolist(),
                    "rot": (-25, 0, 0), "spread": 13, "w": 0.13, "h": 0.195, "back_out": True})
    g = np.array([eye[0], 0.8 + 0.05, eye[2]]) + fwd * 0.85 + right * 0.42
    yaw = math.degrees(math.atan2(-fwd[2], fwd[0]))  # barel ke tengah
    out.append({"type": "revolver", "pos": g.tolist(), "rot": (90, 0, yaw + 25), "px": 1 / 56})
    return out

def cam_json(c):
    return {"pos": c.pos.tolist(), "target": c.target.tolist(), "fov": c.fov}

def spec_pov():
    c = povcam(0, 0, 8)
    props = [
        {"type": "fan", "cam": True, "n": 5, "faces": ["A", "K", "J", "Q", "A"], "pos": (0.02, -0.33, -0.58), "rot": (-18, 0, 6), "spread": 13, "raise": 2},
        {"type": "arm", "cam": True, "skin": SKIN + "steve.png", "side": "left", "scale": 0.7, "pos": (-0.12, -0.80, -0.36), "aim": (0.01, -0.42, -0.58), "roll": (0, 0, 35)},
        {"type": "stand", "face": "A", "pos": (0, 0.8, 0), "yaw": 0, "size": 0.5},
        {"type": "hourglass", "pos": (0.40, 0.8, 0.30), "fill": 0.7, "scale": 1.3},
        {"type": "card", "face": "back", "pos": (-0.22, 0.803, 0.42), "rot": (0, 180, 14)},
        {"type": "card", "face": "back", "pos": (-0.12, 0.806, 0.48), "rot": (0, 180, -9)},
    ]
    props += seat_props(0, 0, 1, fan=False)
    props += seat_props(1, 4, 2) + seat_props(2, 5, 0) + seat_props(3, 2, 3)
    return {"cam": cam_json(c), "lamps": lamps(0), "areas": WINDOW, "props": props,
            "relight": {"active": 0, "rim_from": (-4.5, 2.1, 0.0), "window_shaft": WIN_SHAFT,
                        "haze_density": 0.006, "haze_cone": 36, "set_gain": 0.16, "exposure": 1.25, "dust": 0.8,
                        "ambient": (0.003, 0.0045, 0.007), "rim_gain": 1.6, "rim_w": 3.0, "black": 0.006, "bloom": 0.8}}

WIN_SHAFT = {"x": -4.85, "dir": (1.0, -0.38, 0.12), "energy": 1.8,
             "panes": [(0.95, 3.05, -2.5, -0.7), (0.95, 3.05, 0.7, 2.5)], "mullions_z": [-1.6, 1.6], "mullions_y": [2.0]}
def table_common(tallies, fans, stand_yaw=0, piles=()):
    props = [{"type": "stand", "face": "A", "pos": (0, 0.8, 0), "yaw": stand_yaw, "size": 0.5}]
    for i in range(4):
        props += seat_props(i, fans.get(i, 0), tallies.get(i, 0))
    for (face, pos, yaw) in piles:
        props.append({"type": "card", "face": face, "pos": pos, "rot": ((0, 180, yaw) if face == "back" else (0, 0, yaw))})
    return props

def spec_liar():
    c = devcam(3, 4.4, 5, 2, 30, -0.1)
    L = lamps(3, hi=85, lo=12); L[1]["energy"] = 30
    props = table_common({0: 1, 1: 2, 2: 0, 3: 3}, {0: 3, 2: 4},
                         piles=[("back", (0.25, 0.803, 0.05), 20), ("back", (0.33, 0.806, -0.08), -14), ("back", (0.18, 0.809, -0.14), 47),
                                ("back", (-1.30, 0.803, -0.18), 8), ("back", (-1.36, 0.806, -0.05), -21)])
    props = [p for p in props if not (p["type"] == "revolver" and p["pos"][0] < -0.8)]
    SK = SKIN + "steve.png"
    props += [{"type": "arm2", "skin": SK, "side": "right", "S": [-1.72, 1.02, 0.31], "El": [-1.38, 0.96, 0.40], "Fi": [-1.05, 0.87, 0.40], "slim": False},
              {"type": "arm2", "skin": SK, "side": "left", "S": [-1.72, 1.02, -0.31], "El": [-1.42, 1.10, -0.50], "Fi": [-1.12, 1.18, -0.72], "slim": False},
              {"type": "tumbler", "pos": (-1.0, 0.88, 0.66), "tilt": (16, -10, 0), "splash": True, "liquid": 0.5},

              {"type": "revolver", "pos": (-1.25, 0.85, 0.75), "rot": (90, 0, 205), "px": 1 / 56},
              {"type": "holdout_box", "pos": [-1.72, 1.26, 0.0], "size": [0.5, 0.5, 0.5], "rot": (0, 0, 0)},
              {"type": "holdout_box", "pos": [-1.80, 0.70, 0.0], "size": [0.25, 0.62, 0.5], "rot": (0, 0, 0)}]
    return {"cam": cam_json(c), "lamps": L, "areas": WINDOW, "props": props,
            "relight": {"active": 3, "rim_from": (-4.5, 2.1, 0.0), "window_shaft": WIN_SHAFT, "haze_density": 0.010, "haze_cone": 46,
                        "set_gain": 0.15, "exposure": 1.2, "dust": 0.8, "ambient": (0.003, 0.0045, 0.007), "rim_gain": 2.2, "rim_w": 3.0,
                        "black": 0.006, "bloom": 0.9}}

def spec_rt():
    c = devcam(2, 3.0, -15, 8, 40, -0.15)
    L = lamps(2, hi=75, lo=8)
    L[2]["size"] = 50
    E = np.array([0, EYE_Y, -RING])
    S = [-0.3125, 0.995, -1.95]; El = [-0.66, 1.02, -2.20]; Fi = [-0.80, 1.10, -1.90]
    props = table_common({0: 1, 1: 2, 2: 2, 3: 3}, {}, piles=[("K", (0.1, 0.803, -0.3), 10), ("J", (-0.1, 0.806, -0.25), -12)])
    props = [p for p in props if not (p["type"] == "revolver" and abs(p["pos"][2] + 1.95 + 0) < 0.9 and p["pos"][2] < -0.8)]
    props += [{"type": "arm2", "skin": SKIN + "alex.png", "side": "right", "S": S, "El": El, "Fi": Fi, "slim": True},
              {"type": "revolver_at", "pos": [Fi[0] + 0.02, Fi[1] + 0.02, Fi[2]], "aim": [-0.24, 1.12, -1.90]},
              {"type": "holdout_box", "pos": [0, 1.27, -1.95], "size": [0.5, 0.5, 0.5], "rot": (0, 8, 0)},
              {"type": "holdout_box", "pos": [0, 0.69, -1.97], "size": [0.5, 0.62, 0.25], "rot": (4, 0, 0)}]
    kick = [{"pos": (-0.30, 1.62, -0.95), "target": (-0.56, 1.20, -1.90), "energy": 55, "color": (1.0, 0.76, 0.50), "size": 14},
            {"type": "area", "pos": (-1.5, 1.75, -2.6), "target": (-0.56, 1.2, -1.9), "energy": 45, "color": CYAN_, "size": 0.35}]
    return {"cam": cam_json(c), "lamps": L, "areas": WINDOW, "props": props, "prop_lights": kick, "gun_scale": 1.35,
            "relight": {"active": 2, "rim_from": (-4.5, 2.1, 0.0), "window_shaft": WIN_SHAFT, "haze_density": 0.012, "haze_cone": 44,
                        "set_gain": 0.13, "exposure": 1.15, "dust": 1.0, "ambient": (0.002, 0.003, 0.005), "rim_gain": 2.0, "rim_w": 3.0,
                        "black": 0.008, "bloom": 0.9, "vignette": 0.6}}

def spec_win():
    c = devcam(2, 3.4, -28, 26, 42, -0.3)
    L = lamps(2, dead=(0, 1, 3), hi=55, lo=0)
    L[2]["size"] = 50
    L[2]["pos"] = [0.0, 2.6, -1.45]
    props = table_common({0: 4, 1: 5, 2: 3, 3: 2}, {},
                         piles=[("A", (0.3, 0.803, 0.2), 30), ("Q", (-0.2, 0.803, 0.35), -20), ("back", (0.1, 0.806, 0.45), 70),
                                ("J", (-0.35, 0.803, -0.1), 12), ("K", (0.45, 0.806, -0.25), -35), ("back", (-0.05, 0.809, 0.15), 5)])
    return {"cam": cam_json(c), "lamps": L, "areas": WINDOW, "props": props,
            "relight": {"active": 2, "dead": [0, 1, 3], "rim_from": (-4.5, 2.1, 0.0), "window_shaft": WIN_SHAFT, "haze_density": 0.014,
                        "haze_cone": 40, "set_gain": 0.14, "exposure": 1.2, "dust": 1.4, "ambient": (0.002, 0.003, 0.005), "rim_gain": 2.0,
                        "rim_w": 3.0, "black": 0.008, "bloom": 1.0, "vignette": 0.55}}

SPECS = {"pov": spec_pov, "liar": spec_liar, "rt": spec_rt, "win": spec_win}
TEXD = (SP + "/mc/assets/minecraft/textures/block").replace("\\", "/")
POSTERS = [(ART + "/poster_fight.png", (4.75, 2.3, -2.6), (0.7, 0.95), (0, 0, 90)),
           (ART + "/poster_ship.png", (4.75, 2.2, -0.6), (0.6, 0.8), (0, 0, 90)),
           (ART + "/poster_eye.png", (-3.0, 2.3, 4.75), (0.7, 0.95), (0, 0, 0))]

def set_spec(s, name, samples):
    return {"cam": s["cam"], "lamps": s["lamps"], "areas": s["areas"] + [
                {"pos": (-4.4, 2.2, 0), "target": (-4.4, 0.0, 0.5), "energy": 120, "color": CYAN, "size": 3.0}],
            "tex": TEXD, "rain": ART + "/rain.png", "posters": POSTERS, "out_dir": (SP + "/render").replace("\\", "/"),
            "name": name, "samples": samples}

if __name__ == "__main__":
    name = sys.argv[1]
    s = SPECS[name](); s["art"] = ART; s["out"] = f"{SP}/render/{name}_props.png".replace("\\", "/")
    s["samples"] = int(sys.argv[2]) if len(sys.argv) > 2 else 64
    os.makedirs(SP + "/render", exist_ok=True)
    rl = dict(s.get("relight", {})); rl.update({"cam": s["cam"], "lamps": [L for L in s["lamps"] if L["energy"] > 0], "areas": s["areas"]})
    json.dump(rl, open(f"{SP}/render/{name}_relight.json", "w"), indent=1)
    if "relightonly" in sys.argv: sys.exit()
    if "set" in sys.argv:
        ss = set_spec(s, name, s["samples"]); jss = f"{SP}/render/{name}_set.json"; json.dump(ss, open(jss, "w"), indent=1)
        r = subprocess.run([BLENDER, "-b", "-P", os.path.join(os.path.dirname(__file__), "bl_set.py"), "--", jss], capture_output=True, text=True)
        print(chr(10).join(l for l in r.stdout.splitlines() if "RENDERED" in l or "Error" in l or "rror" in l) or r.stdout[-1500:], r.stderr[-800:])
        if "props" not in sys.argv: sys.exit()
    js = f"{SP}/render/{name}.json"; json.dump(s, open(js, "w"), indent=1)
    r = subprocess.run([BLENDER, "-b", "-P", os.path.join(os.path.dirname(__file__), "bl_props.py"), "--", js], capture_output=True, text=True)
    tail = [l for l in r.stdout.splitlines() if "RENDERED" in l or "Error" in l or "Traceback" in l]
    print("\n".join(tail) or r.stdout[-2000:], r.stderr[-1500:])
