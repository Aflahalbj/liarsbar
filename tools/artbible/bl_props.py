# Blender 4.2: render properti konsep untuk style frame (RGBA + bayangan di meja).
# blender -b -P bl_props.py -- spec.json
import bpy, bmesh, json, sys, math, random
from mathutils import Vector, Matrix, Euler, Quaternion

spec = json.load(open(sys.argv[sys.argv.index("--") + 1]))
ART = spec["art"]
random.seed(7)


def mc(v):
    return Vector((v[0], -v[2], v[1]))


def clear():
    bpy.ops.wm.read_factory_settings(use_empty=True)


clear()
scene = bpy.context.scene
scene.render.engine = "CYCLES"
scene.cycles.samples = spec.get("samples", 96)
scene.cycles.use_denoising = True
scene.render.film_transparent = True
scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
scene.view_settings.view_transform = "Standard"
scene.view_settings.exposure = spec.get("exposure", 0.0)
scene.world = bpy.data.worlds.new("w")
scene.world.use_nodes = True
scene.world.node_tree.nodes["Background"].inputs[0].default_value = (0.004, 0.005, 0.007, 1)
scene.cycles.max_bounces = 4
scene.cycles.transmission_bounces = 4
scene.cycles.use_adaptive_sampling = True
scene.cycles.adaptive_threshold = 0.03

# ---------- material ----------
_mats = {}


def mat_color_attr(name, rough=0.55, metal=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    b = nt.nodes["Principled BSDF"]
    a = nt.nodes.new("ShaderNodeVertexColor")
    a.layer_name = "Col"
    nt.links.new(a.outputs["Color"], b.inputs["Base Color"])
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    return m


def mat_image(path, alpha=True, rough=0.7, emit=0.0):
    key = (path, emit)
    if key in _mats:
        return _mats[key]
    m = bpy.data.materials.new(path.split("/")[-1])
    m.use_nodes = True
    nt = m.node_tree
    b = nt.nodes["Principled BSDF"]
    t = nt.nodes.new("ShaderNodeTexImage")
    t.image = bpy.data.images.load(path)
    t.interpolation = "Closest"
    nt.links.new(t.outputs["Color"], b.inputs["Base Color"])
    if alpha:
        nt.links.new(t.outputs["Alpha"], b.inputs["Alpha"])
    b.inputs["Roughness"].default_value = rough
    if emit:
        nt.links.new(t.outputs["Color"], b.inputs["Emission Color"])
        b.inputs["Emission Strength"].default_value = emit
    _mats[key] = m
    return m


def mat_flat(name, col, rough=0.5, metal=0.0, emit=0.0, transmission=0.0, ior=1.45, alpha=1.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*col, 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    b.inputs["Transmission Weight"].default_value = transmission
    b.inputs["IOR"].default_value = ior
    b.inputs["Alpha"].default_value = alpha
    if emit:
        b.inputs["Emission Color"].default_value = (*col, 1)
        b.inputs["Emission Strength"].default_value = emit
    return m


def srgb(c):
    return tuple(((x / 255) ** 2.2) for x in c)


BRASS = mat_flat("brass", srgb((201, 160, 74)), rough=0.35, metal=1.0)
BRASS_DK = mat_flat("brass_dk", srgb((120, 88, 40)), rough=0.4, metal=1.0)
GLASS = mat_flat("glass", (0.9, 0.95, 1.0), rough=0.05, transmission=1.0, ior=1.45)
CHALK = mat_flat("chalk", srgb((232, 228, 215)), rough=0.95)
SAND = mat_flat("sand", srgb((214, 168, 96)), rough=0.9, emit=0.0)
WHISKY = mat_flat("whisky", srgb((176, 92, 22)), rough=0.05, transmission=0.85, ior=1.33)


def link(ob):
    bpy.context.collection.objects.link(ob)
    return ob


def place(ob, pos, rot_mc=(0, 0, 0), scale=1.0, parent=None):
    """pos dalam koordinat MC, rot = euler XYZ (derajat) di ruang Blender."""
    ob.location = mc(pos) if parent is None else Vector(pos)
    ob.rotation_euler = Euler([math.radians(a) for a in rot_mc], "XYZ")
    ob.scale = (scale, scale, scale)
    if parent:
        ob.parent = parent
    return ob


# ---------- voxel dari profil pixel ----------
def voxel(name, rows, colors, depth, px):
    bm = bmesh.new()
    col_layer = bm.loops.layers.color.new("Col")
    h = len(rows)
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c not in colors:
                continue
            t = depth.get(c, 2)
            for zi in range(t):
                z0 = zi - t / 2
                cube = bmesh.ops.create_cube(bm, size=1.0)
                vs = cube["verts"]
                bmesh.ops.translate(bm, verts=vs, vec=Vector((x + 0.5, z0 + 0.5, (h - y) - 0.5)))
                base = colors[c]
                jitter = 1.0 + random.uniform(-0.06, 0.06)
                for f in {f for v in vs for f in v.link_faces}:
                    shade = 1.0
                    for l in f.loops:
                        l[col_layer] = (*(min(1, ch * jitter * shade) for ch in srgb(base)), 1)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=0.001)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    ob.data.materials.append(mat_color_attr(name + "_m", rough=0.34, metal=0.15))
    # pusat di genggaman (kolom 3, baris 8) supaya gampang ditaruh di kepalan
    gx, gy = spec.get("grip_px", (3, 9))
    me.transform(Matrix.Translation(Vector((-gx, 0, -(h - gy)))))
    me.transform(Matrix.Scale(px, 4))
    return link(ob)


REV_ROWS = open(ART + "/revolver_profile.txt").read().split("\n")
REV_COL = {"B": (66, 74, 86), "L": (170, 182, 196), "S": (220, 226, 232), "R": (44, 50, 58), "F": (76, 84, 96), "C": (128, 138, 150),
           "K": (24, 26, 30), "H": (60, 66, 76), "T": (40, 44, 50), "G": (76, 84, 96), "W": (120, 66, 34), "M": (220, 176, 84)}
REV_DEPTH = {"B": 2, "L": 2, "S": 1, "R": 1, "F": 3, "C": 5, "K": 5, "H": 2, "T": 1, "G": 1, "W": 3, "M": 4}


def revolver(pos, rot, px=1 / 40, parent=None):
    px *= spec.get("gun_scale", 1.0)
    ob = voxel("revolver", REV_ROWS, REV_COL, REV_DEPTH, px)
    return place(ob, pos, rot, parent=parent)


# ---------- kartu ----------
def card(face, pos, rot, w=0.16, h=0.24, parent=None, emit=0.0):
    bpy.ops.mesh.primitive_plane_add(size=1)
    f = bpy.context.active_object
    f.scale = (w, h, 1)
    bpy.ops.object.transform_apply(scale=True)
    f.data.materials.append(mat_image(f"{ART}/card_{face}.png", emit=emit))
    bpy.ops.mesh.primitive_plane_add(size=1)
    b = bpy.context.active_object
    b.scale = (w, h, 1)
    b.rotation_euler = (0, math.pi, 0)
    b.location = (0, 0, -0.0015)
    bpy.ops.object.transform_apply(scale=True, rotation=True, location=True)
    b.data.materials.append(mat_image(f"{ART}/card_back.png"))
    bpy.ops.object.select_all(action="DESELECT")
    f.select_set(True)
    b.select_set(True)
    bpy.context.view_layer.objects.active = f
    bpy.ops.object.join()
    return place(f, pos, rot, parent=parent)


def card_fan(n, faces, pos, rot, spread=11, raise_idx=-1, w=0.16, h=0.24, parent=None, back_out=False):
    root = bpy.data.objects.new("fan", None)
    link(root)
    place(root, pos, rot, parent=parent)
    for i in range(n):
        a = (i - (n - 1) / 2) * spread
        e = bpy.data.objects.new("pivot", None)
        link(e)
        e.parent = root
        e.rotation_euler = (0, 0, math.radians(-a))
        e.location = (0, 0, i * 0.002)
        c = card(faces[i % len(faces)], (0, h * 0.42 + (0.05 if i == raise_idx else 0), 0), (0, 180 if back_out else 0, 0), w, h, parent=e)
        c.location = Vector((0, h * 0.42 + (0.05 if i == raise_idx else 0), i * 0.002))
    return root


# ---------- lengan MC (skin 64x64) ----------
ARM_UV = {  # (u, v) pojok kiri-atas dari box 4x12x4 di skin
    "right": (40, 16), "left": (32, 48), "right_sleeve": (40, 32), "left_sleeve": (48, 48)}


def mc_box(skin, uv0, size=(4, 12, 4), inflate=0.0, rows=None):
    w, h, d = size
    u0, v0 = uv0
    r0, rh = rows if rows else (0, h)
    sx, sy, sz = (w + inflate * 2) / 16, (h + inflate * 2) / 16, (d + inflate * 2) / 16
    me = bpy.data.meshes.new("box")
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new()
    # titik: x kiri-kanan, y depan(-)/belakang(+) ... pakai konvensi model MC: tinggi sepanjang -Z dari bahu
    X, Y, Z = sx / 2, sz / 2, sy
    V = {}
    for i in (0, 1):
        for j in (0, 1):
            for k in (0, 1):
                V[(i, j, k)] = bm.verts.new(((i * 2 - 1) * X, (j * 2 - 1) * Y, -k * Z))

    def face(keys, u, v, uw, vh, flip=False):
        f = bm.faces.new([V[k] for k in keys])
        uvs = [(u, v), (u + uw, v), (u + uw, v + vh), (u, v + vh)]
        if flip:
            uvs = [uvs[1], uvs[0], uvs[3], uvs[2]]
        for l, (a, b) in zip(f.loops, uvs):
            l[uvl].uv = (a / 64, 1 - b / 64)
        return f
    # depan (Y-), belakang (Y+), kiri (X-), kanan (X+), atas, bawah
    face([(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)], u0 + d, v0 + d + r0, w, rh)
    face([(1, 1, 0), (0, 1, 0), (0, 1, 1), (1, 1, 1)], u0 + d + w + d, v0 + d + r0, w, rh)
    face([(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)], u0, v0 + d + r0, d, rh)
    face([(1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)], u0 + d + w, v0 + d + r0, d, rh)
    face([(0, 1, 0), (1, 1, 0), (1, 0, 0), (0, 0, 0)], u0 + d, v0, w, d)
    face([(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)], u0 + d + w, v0, w, d)
    bm.normal_update()
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new("arm", me)
    ob.data.materials.append(mat_image(skin, rough=0.8))
    return link(ob)


def arm(skin, side, pos, rot, parent=None, scale=1.0):
    root = bpy.data.objects.new("arm_root", None)
    link(root)
    place(root, pos, rot, parent=parent)
    root.scale = (scale, scale, scale)
    a = mc_box(skin, ARM_UV[side])
    a.parent = root
    return root


def arm2(skin, side, S, El, Fi, slim=True):
    """Lengan MC dua segmen (bendy): bahu S -> siku El -> kepalan Fi (koordinat MC)."""
    w = 3 if slim else 4
    for (a, b, rows) in ((S, El, (0, 6)), (El, Fi, (6, 6))):
        L = (mc(b) - mc(a)).length
        for key, infl in ((side, 0.0), (side + "_sleeve", 0.25)):
            o = mc_box(skin, ARM_UV[key], size=(w, 6, 4), inflate=infl, rows=rows)
            o.scale = (1, 1, L / (6 / 16 + infl * 2 / 16))
            o.location = mc(a)
            o.rotation_euler = (mc(b) - mc(a)).to_track_quat("-Z", "Y").to_euler()


# ---------- properti meja ----------
def cylinder(r, h, mat, pos, verts=16, rot=(0, 0, 0), parent=None):
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=h)
    o = bpy.context.active_object
    o.data.materials.append(mat)
    place(o, pos, rot, parent=parent)
    return o


def cone(r1, r2, h, mat, pos, parent=None):
    bpy.ops.mesh.primitive_cone_add(vertices=24, radius1=r1, radius2=r2, depth=h)
    o = bpy.context.active_object
    o.data.materials.append(mat)
    place(o, pos, parent=parent)
    return o


def table_card_stand(face, pos, yaw=0.0, size=1.0):
    root = bpy.data.objects.new("stand", None)
    link(root)
    place(root, pos, (0, 0, yaw))
    cylinder(0.13 * size, 0.025, BRASS, (0, 0, 0.0125), verts=24, parent=root)
    cylinder(0.012 * size, 0.30 * size, BRASS, (0, 0, 0.15 * size), verts=8, parent=root)
    cylinder(0.03 * size, 0.02, BRASS, (0, 0, 0.30 * size), verts=12, parent=root)
    for k in range(3):
        e = bpy.data.objects.new("leg", None)
        link(e)
        e.parent = root
        e.rotation_euler = (0, 0, math.radians(120 * k))
        c = card(face, (0, 0, 0), (0, 0, 0), 0.24 * size, 0.36 * size, parent=e)
        c.location = Vector((0, -0.075 * size, 0.205 * size))
        c.rotation_euler = Euler((math.radians(90 - 18), 0, 0), "XYZ")
    return root


def hourglass(pos, fill=0.65, scale=1.0):
    root = bpy.data.objects.new("hourglass", None)
    link(root)
    place(root, pos)
    root.scale = (scale, scale, scale)
    H = 0.2
    cylinder(0.055, 0.012, BRASS, (0, 0, 0.006), parent=root)
    cylinder(0.055, 0.012, BRASS, (0, 0, H - 0.006), parent=root)
    for k in range(3):
        a = math.radians(120 * k + 30)
        cylinder(0.005, H, BRASS_DK, (0.048 * math.cos(a), 0.048 * math.sin(a), H / 2), verts=6, parent=root)
    cone(0.04, 0.006, H / 2 - 0.012, GLASS, (0, 0, H * 0.75 - 0.003), parent=root)
    cone(0.006, 0.04, H / 2 - 0.012, GLASS, (0, 0, H * 0.25 + 0.003), parent=root)
    # pasir: atas sisa (1-fill), bawah fill
    cone(0.034 * (1 - fill) + 0.006, 0.006, (H / 2 - 0.02) * (1 - fill) + 0.002, SAND, (0, 0, H / 2 + 0.01 + (H / 2 - 0.02) * (1 - fill) / 2), parent=root)
    cone(0.034, 0.034 * (1 - fill) + 0.006, (H / 2 - 0.02) * fill, SAND, (0, 0, 0.012 + (H / 2 - 0.02) * fill / 2), parent=root)
    cylinder(0.0015, H / 2 - 0.02, SAND, (0, 0, H * 0.35), verts=6, parent=root)
    return root


def tumbler(pos, tilt=(0, 0, 0), splash=False, liquid=0.5):
    root = bpy.data.objects.new("glass", None)
    link(root)
    place(root, pos, tilt)
    cylinder(0.045, 0.1, GLASS, (0, 0, 0.05), verts=8, parent=root)  # gelas segi-8: tetap kotak-ish ala MC
    cylinder(0.04, 0.1 * liquid, WHISKY, (0, 0, 0.012 + 0.05 * liquid), verts=8, parent=root)
    if splash and False:
        for i in range(9):
            a = random.uniform(0, 6.28)
            r = random.uniform(0.02, 0.07)
            bpy.ops.mesh.primitive_cube_add(size=random.uniform(0.008, 0.016))
            d = bpy.context.active_object
            d.data.materials.append(WHISKY)
            d.parent = root
            d.location = (r * math.cos(a), r * math.sin(a), 0.11 + random.uniform(0.0, 0.09))
    return root


def tally(seat_eye, seat_fwd, count, along=0.0):
    """Goresan kapur di bibir meja depan kursi: tiap tarikan pelatuk satu goresan."""
    right = Vector((-seat_fwd[2], 0, seat_fwd[0]))
    base = Vector((seat_eye[0], 0.802, seat_eye[2])) + Vector(seat_fwd) * 0.62 + right * along
    for i in range(count):
        bpy.ops.mesh.primitive_plane_add(size=1)
        s = bpy.context.active_object
        s.scale = (0.024, 0.19, 1)
        bpy.ops.object.transform_apply(scale=True)
        s.data.materials.append(CHALK)
        p = base + right * (i * 0.06 - 0.12)
        yaw = math.atan2(seat_fwd[0], seat_fwd[2])
        if i == 4:
            p = base + right * 0.0
            s.scale = (1, 1.5, 1)
        s.location = mc(p)
        s.rotation_euler = (0, 0, -yaw + (math.radians(62) if i == 4 else math.radians(random.uniform(-6, 6))))


def lamp_light(pos, energy, color, size_deg=75, blend=0.7, radius=0.06):
    d = bpy.data.lights.new("lamp", "SPOT")
    d.energy = energy
    d.color = color
    d.spot_size = math.radians(size_deg)
    d.spot_blend = blend
    d.shadow_soft_size = radius
    o = bpy.data.objects.new("lamp", d)
    link(o)
    o.location = mc(pos)
    o.rotation_euler = (0, 0, 0)  # menunjuk ke -Z (ke bawah)
    return o


def area_light(pos, target, energy, color, size=1.5):
    d = bpy.data.lights.new("area", "AREA")
    d.energy = energy
    d.color = color
    d.size = size
    o = bpy.data.objects.new("area", d)
    link(o)
    o.location = mc(pos)
    direction = mc(target) - mc(pos)
    o.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()
    return o


# ---------- kamera ----------
cam_d = bpy.data.cameras.new("cam")
cam_d.sensor_fit = "VERTICAL"
cam_d.angle = math.radians(spec["cam"]["fov"])
cam_d.clip_start = 0.02
cam = bpy.data.objects.new("cam", cam_d)
link(cam)
cam.location = mc(spec["cam"]["pos"])
cam.rotation_euler = (mc(spec["cam"]["target"]) - mc(spec["cam"]["pos"])).to_track_quat("-Z", "Y").to_euler()
scene.camera = cam

# penangkap bayangan: daun meja
bpy.ops.mesh.primitive_cylinder_add(vertices=64, radius=1.5, depth=0.05, location=mc((0, 0.775, 0)))
tbl = bpy.context.active_object
tbl.is_shadow_catcher = True
tbl.data.materials.append(mat_flat("tbl", (0.3, 0.12, 0.05)))

SHADE = mat_flat("shade", srgb((26, 38, 30)), rough=0.35)
SHADE_IN = mat_flat("shade_in", srgb((235, 225, 200)), rough=0.6)
CORD = mat_flat("cord", (0.01, 0.01, 0.01), rough=0.8)
for L in spec.get("lamps", []):
    on = L["energy"] > 0
    if on:
        lamp_light(L["pos"], L["energy"], L.get("color", (1.0, 0.62, 0.30)), L.get("size", 75), L.get("blend", 0.7))
    if spec.get("lamp_geo", True):
        p = L["pos"]
        bpy.ops.mesh.primitive_cone_add(vertices=12, radius1=0.21, radius2=0.07, depth=0.17, end_fill_type="NOTHING")
        o = bpy.context.active_object
        o.data.materials.append(SHADE)
        o.modifiers.new("s", "SOLIDIFY").thickness = 0.012
        o.location = mc((p[0], p[1] + 0.07, p[2]))
        cylinder(0.008, 4.2 - p[1] - 0.15, CORD, (p[0], (4.2 + p[1] + 0.15) / 2, p[2]), verts=6)
        bpy.ops.mesh.primitive_uv_sphere_add(radius=0.04, segments=12, ring_count=8)
        b = bpy.context.active_object
        b.location = mc((p[0], p[1] + 0.02, p[2]))
        b.data.materials.append(mat_flat("bulb", (1.0, 0.75, 0.45), emit=(60.0 if on else 0.0)) if on else mat_flat("bulb_off", (0.05, 0.04, 0.03), rough=0.2))
for A in spec.get("areas", []):
    area_light(A["pos"], A["target"], A["energy"] * spec.get("area_scale", 0.3), A["color"], A.get("size", 1.5))
for L in spec.get("prop_lights", []):
    if L.get("type") == "area":
        area_light(L["pos"], L["target"], L["energy"], L["color"], L.get("size", 0.5))
    else:
        o = lamp_light(L["pos"], L["energy"], L["color"], L.get("size", 40), 0.5, 0.1)
        o.rotation_euler = (mc(L["target"]) - mc(L["pos"])).to_track_quat("-Z", "Y").to_euler()

for p in spec["props"]:
    t = p["type"]
    if t == "revolver":
        revolver(p["pos"], p.get("rot", (0, 0, 0)), p.get("px", 1 / 40))
    elif t == "revolver_cam":
        revolver(p["pos"], p.get("rot", (0, 0, 0)), p.get("px", 1 / 40), parent=cam)
    elif t == "card":
        card(p["face"], p["pos"], p.get("rot", (0, 0, 0)), p.get("w", 0.16), p.get("h", 0.24))
    elif t == "fan":
        r = card_fan(p["n"], p["faces"], p["pos"], p.get("rot", (0, 0, 0)), p.get("spread", 11), p.get("raise", -1),
                 p.get("w", 0.16), p.get("h", 0.24), parent=cam if p.get("cam") else None, back_out=p.get("back_out", False))
        if "face" in p:
            q = mc(p["face"]).to_track_quat("Z", "Y")
            r.rotation_euler = (q @ Euler([math.radians(a) for a in p.get("rot", (0, 0, 0))], "XYZ").to_quaternion()).to_euler()
    elif t == "arm":
        r = arm(p["skin"], p["side"], p["pos"], p.get("rot", (0, 0, 0)), parent=cam if p.get("cam") else None, scale=p.get("scale", 1.0))
        if "aim" in p:
            q = (Vector(p["aim"]) - Vector(p["pos"])).to_track_quat("-Z", "Y") if p.get("cam") else (mc(p["aim"]) - mc(p["pos"])).to_track_quat("-Z", "Y")
            r.rotation_euler = (q @ Euler([math.radians(a) for a in p.get("roll", (0, 0, 0))], "XYZ").to_quaternion()).to_euler()
    elif t == "stand":
        table_card_stand(p["face"], p["pos"], p.get("yaw", 0), p.get("size", 1.0))
    elif t == "hourglass":
        hourglass(p["pos"], p.get("fill", 0.65), p.get("scale", 1.0))
    elif t == "tumbler":
        tumbler(p["pos"], p.get("tilt", (0, 0, 0)), p.get("splash", False), p.get("liquid", 0.5))
    elif t == "tally":
        tally(p["eye"], p["fwd"], p["count"], p.get("along", 0.0))
    elif t == "arm2":
        arm2(p["skin"], p["side"], p["S"], p["El"], p["Fi"], p.get("slim", True))
    elif t == "revolver_at":
        o = revolver(p["pos"], (0, 0, 0), p.get("px", 1 / 40))
        q = (mc(p["aim"]) - mc(p["pos"])).to_track_quat("X", "Z")
        o.rotation_euler = q.to_euler()
    elif t == "holdout_box":
        bpy.ops.mesh.primitive_cube_add(size=1)
        o = bpy.context.active_object
        o.scale = [s for s in (p["size"][0], p["size"][2], p["size"][1])]
        place(o, p["pos"], p.get("rot", (0, 0, 0)))
        o.scale = (p["size"][0], p["size"][2], p["size"][1])
        o.is_holdout = True

scene.render.filepath = spec["out"]
bpy.ops.render.render(write_still=True)
print("RENDERED", spec["out"])
