# Blender 4.2: set bar konsep (layer background) + pass kedalaman. blender -b -P bl_set.py -- spec.json
import bpy, bmesh, json, sys, math, random
from mathutils import Vector, Euler

spec = json.load(open(sys.argv[sys.argv.index("--") + 1]))
random.seed(11)


def mc(v):
    return Vector((v[0], -v[2], v[1]))


bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.engine = "CYCLES"
scene.cycles.samples = spec.get("samples", 32)
scene.cycles.use_denoising = True
scene.cycles.use_adaptive_sampling = True
scene.cycles.max_bounces = 3
scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
scene.view_settings.view_transform = "Standard"
scene.world = bpy.data.worlds.new("w")
scene.world.use_nodes = True
scene.world.node_tree.nodes["Background"].inputs[0].default_value = (0.0, 0.0, 0.0, 1)


def srgb(c):
    return tuple(((x / 255) ** 2.2) for x in c)


def mat(name, col, rough=0.6, metal=0.0, emit=0.0, ecol=None, transmission=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*srgb(col), 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    b.inputs["Transmission Weight"].default_value = transmission
    if emit:
        b.inputs["Emission Color"].default_value = (*srgb(ecol or col), 1)
        b.inputs["Emission Strength"].default_value = emit
    return m


def mat_tex(name, path, rough=0.7, scale=1.0, emit=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    b = nt.nodes["Principled BSDF"]
    t = nt.nodes.new("ShaderNodeTexImage")
    t.image = bpy.data.images.load(path)
    t.interpolation = "Closest"
    tc = nt.nodes.new("ShaderNodeTexCoord")
    mp = nt.nodes.new("ShaderNodeMapping")
    mp.inputs["Scale"].default_value = (scale, scale, scale)
    nt.links.new(tc.outputs["UV"], mp.inputs["Vector"])
    nt.links.new(mp.outputs["Vector"], t.inputs["Vector"])
    nt.links.new(t.outputs["Color"], b.inputs["Base Color"])
    b.inputs["Roughness"].default_value = rough
    if emit:
        nt.links.new(t.outputs["Color"], b.inputs["Emission Color"])
        b.inputs["Emission Strength"].default_value = emit
    return m


def box(size, pos, m, rot=(0, 0, 0), name="box"):
    """size & pos dalam MC (x, y, z); pos = pusat."""
    bpy.ops.mesh.primitive_cube_add(size=1)
    o = bpy.context.active_object
    o.name = name
    o.scale = (size[0], size[2], size[1])
    o.location = mc(pos)
    o.rotation_euler = Euler([math.radians(a) for a in rot], "XYZ")
    o.data.materials.append(m)
    bpy.ops.object.transform_apply(scale=True)
    # UV kotak per blok supaya tekstur 16px tidak melar
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.cube_project(cube_size=1.0)
    bpy.ops.object.mode_set(mode="OBJECT")
    return o


R = spec.get("room", 5.0)
CEIL = 4.2
TEX = spec["tex"]
WOOD_DK = mat_tex("wood_dk", TEX + "/dark_oak_planks.png", 0.8)
WOOD = mat_tex("wood", TEX + "/spruce_planks.png", 0.75)
WALL = mat("wall", (34, 30, 30), 0.9)
PAPER = mat("paper", (52, 40, 36), 0.9)
BRASS = mat("brass", (201, 160, 74), 0.35, 1.0)
IRON = mat("iron", (40, 42, 46), 0.5, 0.8)
MIRROR = mat("mirror", (200, 200, 200), 0.05, 1.0)
LEATHER = mat("leather", (70, 22, 20), 0.6)

# lantai, dinding, plafon
box((2 * R, 0.1, 2 * R), (0, -0.05, 0), WOOD_DK, name="floor")
box((2 * R, 0.2, 2 * R), (0, CEIL + 0.1, 0), WALL, name="ceiling")
for sx, sz, w, d in ((0, -R, 2 * R, 0.2), (0, R, 2 * R, 0.2), (-R, 0, 0.2, 2 * R), (R, 0, 0.2, 2 * R)):
    box((w, CEIL, d), (sx, CEIL / 2, sz), PAPER, name="wall")
    # wainscot kayu + list
    inset = 0.12
    box((w if d < 1 else 0.06, 1.1, d if w < 1 else 0.06) if False else (w - 0.01 if w > 1 else 0.06, 1.1, d - 0.01 if d > 1 else 0.06),
        (sx + (inset if sx < 0 else -inset if sx > 0 else 0), 0.55, sz + (inset if sz < 0 else -inset if sz > 0 else 0)), WOOD_DK, name="wainscot")
    box((w - 0.01 if w > 1 else 0.1, 0.06, d - 0.01 if d > 1 else 0.1),
        (sx + (0.15 if sx < 0 else -0.15 if sx > 0 else 0), 1.12, sz + (0.15 if sz < 0 else -0.15 if sz > 0 else 0)), WOOD, name="rail")

# --- back bar (dinding utara, z = -R) ---
zb = -R + 0.2
box((6.0, 0.9, 0.7), (0, 0.45, -R + 1.6), WOOD_DK, name="counter")
box((6.2, 0.06, 0.85), (0, 0.93, -R + 1.6), WOOD, name="countertop")
box((6.2, 0.05, 0.05), (0, 0.2, -R + 1.18), BRASS, name="footrail")
for i in range(6):
    x = -2.5 + i
    box((0.06, 0.75, 0.06), (x, 0.38, -R + 2.3), IRON, name="stool_leg")
    box((0.42, 0.08, 0.42), (x, 0.78, -R + 2.3), LEATHER, name="stool")
box((2.4, 1.3, 0.04), (0, 2.25, zb + 0.02), MIRROR, name="mirror")
box((2.6, 0.08, 0.06), (0, 1.56, zb + 0.05), BRASS, name="mirrorframe_b")
box((2.6, 0.08, 0.06), (0, 2.94, zb + 0.05), BRASS, name="mirrorframe_t")
BOTTLE_COLS = [(150, 90, 20), (40, 90, 40), (120, 30, 20), (190, 170, 120), (60, 50, 30), (30, 60, 70)]
for side in (-1, 1):
    for level, y in enumerate((1.45, 2.05, 2.65)):
        box((1.6, 0.05, 0.32), (side * 2.35, y, zb + 0.16), WOOD, name="shelf")
        # lampu strip di bawah rak: hangat, kecil
        box((1.5, 0.015, 0.02), (side * 2.35, y - 0.03, zb + 0.30), mat("strip", (255, 170, 90), emit=6.0), name="strip")
        x = side * 2.35 - 0.7
        while x < side * 2.35 + 0.7:
            h = random.uniform(0.28, 0.42)
            c = random.choice(BOTTLE_COLS)
            box((0.1, h, 0.1), (x, y + 0.025 + h / 2, zb + 0.16), mat("bottle", c, 0.1, transmission=0.6, emit=0.15), name="bottle")
            box((0.04, 0.1, 0.04), (x, y + 0.025 + h + 0.05, zb + 0.16), mat("neck", c, 0.1, transmission=0.6), name="neck")
            x += random.uniform(0.13, 0.2)

# --- jendela besar di dinding barat (x = -R): hujan + neon kota (cyan) ---
WIN = mat_tex("rain", spec["rain"], rough=1.0, emit=spec.get("window_emit", 3.0))
for wz in (-1.6, 1.6):
    box((0.04, 2.2, 1.9), (-R + 0.15, 2.0, wz), WIN, name="window")
    for dz in (-0.95, 0, 0.95):
        box((0.1, 2.3, 0.08), (-R + 0.2, 2.0, wz + dz), WOOD_DK, name="mullion")
    for dy in (0.9, 2.0, 3.1):
        box((0.1, 0.08, 2.0), (-R + 0.2, dy, wz), WOOD_DK, name="transom")
# jendela kecil di dinding utara pojok kiri (POV)
box((1.2, 1.6, 0.04), (-4.0, 2.3, -R + 0.15), WIN, name="window_n")
for dx in (-0.6, 0, 0.6):
    box((0.08, 1.7, 0.1), (-4.0 + dx, 2.3, -R + 0.2), WOOD_DK, name="mullion")

# --- poster berbingkai ---
POST = spec.get("posters", [])
for i, (p, pos, size, rot) in enumerate(POST):
    box((size[0], size[1], 0.03), pos, mat_tex(f"poster{i}", p, 0.8), rot=rot, name="poster")

# --- plafon: kipas, pipa ---
box((0.06, 0.6, 0.06), (0, CEIL - 0.3, 0), IRON, name="fan_rod")
box((0.25, 0.15, 0.25), (0, CEIL - 0.65, 0), BRASS, name="fan_hub")
for k in range(4):
    a = math.radians(45 * k + spec.get("fan_angle", 12))
    box((1.5, 0.02, 0.22), (0.75 * math.cos(a) * 0 , CEIL - 0.68, 0), WOOD_DK, rot=(0, 0, 0), name="blade")
    o = bpy.context.active_object
    o.location = mc((0.8 * math.cos(a), CEIL - 0.68, 0.8 * math.sin(a)))
    o.rotation_euler = (math.radians(6), 0, -a)
for px, pz, L, rot in ((-3.2, 0, 2 * R, 0), (3.6, 0, 2 * R, 0), (0, 3.4, 2 * R, 90)):
    box((0.12, 0.12, L) if rot == 0 else (L, 0.12, 0.12), (px, CEIL - 0.2, pz), IRON, name="pipe")

# --- pintu dengan kaca buram di dinding timur ---
box((0.1, 2.4, 1.2), (R - 0.15, 1.2, 1.5), WOOD_DK, name="door")
box((0.12, 0.9, 0.7), (R - 0.18, 1.7, 1.5), mat("frost", (180, 200, 190), 0.3, emit=0.5, ecol=(120, 170, 190)), name="doorglass")

# --- sconce dinding: praktikal latar (kap kaca amber + braket kuningan) ---
AMBER = mat("amber", (255, 170, 80), 0.3, emit=2.5, ecol=(255, 160, 70))
for (sx, sy, sz, rot) in ((3.6, 2.25, -R + 0.25, 0), (-3.6, 2.25, -R + 0.25, 0), (R - 0.25, 2.25, -2.6, 90), (R - 0.25, 2.25, 3.2, 90),
                          (1.5, 2.25, R - 0.25, 0)):
    box((0.06, 0.25, 0.06), (sx, sy - 0.12, sz), BRASS, name="sconce_arm")
    box((0.2, 0.22, 0.2), (sx, sy + 0.08, sz + (0.06 if sz < 0 and rot == 0 else -0.06 if rot == 0 else 0)), AMBER, name="sconce_shade")
    d = bpy.data.lights.new("sconce", "POINT")
    d.energy = 8
    d.color = (1.0, 0.62, 0.3)
    d.shadow_soft_size = 0.1
    o = bpy.data.objects.new("sconce", d)
    bpy.context.collection.objects.link(o)
    o.location = mc((sx + (0.25 if sx < -R + 1 else -0.25 if sx > R - 1 else 0), sy + 0.1, sz + (0.25 if sz < -R + 1 else -0.25 if sz > R - 1 else 0)))

# lampu: sama dengan properti (gantung per kursi) + cahaya jendela
for L in spec.get("lamps", []):
    d = bpy.data.lights.new("lamp", "SPOT")
    d.energy = L["energy"]
    d.color = L["color"]
    d.spot_size = math.radians(L.get("size", 75))
    d.spot_blend = 0.7
    d.shadow_soft_size = 0.06
    o = bpy.data.objects.new("lamp", d)
    bpy.context.collection.objects.link(o)
    o.location = mc(L["pos"])
for A in spec.get("areas", []):
    d = bpy.data.lights.new("area", "AREA")
    d.energy = A["energy"]
    d.color = A["color"]
    d.size = A.get("size", 1.5)
    o = bpy.data.objects.new("area", d)
    bpy.context.collection.objects.link(o)
    o.location = mc(A["pos"])
    o.rotation_euler = (mc(A["target"]) - mc(A["pos"])).to_track_quat("-Z", "Y").to_euler()

# kamera
cd = bpy.data.cameras.new("cam")
cd.sensor_fit = "VERTICAL"
cd.angle = math.radians(spec["cam"]["fov"])
cd.clip_start = 0.05
cam = bpy.data.objects.new("cam", cd)
bpy.context.collection.objects.link(cam)
cam.location = mc(spec["cam"]["pos"])
cam.rotation_euler = (mc(spec["cam"]["target"]) - mc(spec["cam"]["pos"])).to_track_quat("-Z", "Y").to_euler()
scene.camera = cam

# pass kedalaman -> PNG 16-bit (0..20 m)
scene.view_layers[0].use_pass_z = True
scene.use_nodes = True
nt = scene.node_tree
for n in list(nt.nodes):
    nt.nodes.remove(n)
rl = nt.nodes.new("CompositorNodeRLayers")
comp = nt.nodes.new("CompositorNodeComposite")
nt.links.new(rl.outputs["Image"], comp.inputs["Image"])
mr = nt.nodes.new("CompositorNodeMapRange")
mr.inputs["From Min"].default_value = 0
mr.inputs["From Max"].default_value = 20
mr.inputs["To Min"].default_value = 0
mr.inputs["To Max"].default_value = 1
mr.use_clamp = True
nt.links.new(rl.outputs["Depth"], mr.inputs["Value"])
fo = nt.nodes.new("CompositorNodeOutputFile")
fo.base_path = spec["out_dir"]
fo.format.file_format = "PNG"
fo.format.color_depth = "16"
fo.format.color_mode = "BW"
fo.file_slots[0].path = spec["name"] + "_depth_"
nt.links.new(mr.outputs["Value"], fo.inputs[0])
scene.render.filepath = spec["out_dir"] + "/" + spec["name"] + "_set.png"
bpy.ops.render.render(write_still=True)
print("RENDERED SET", scene.render.filepath)
