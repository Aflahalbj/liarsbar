"""Animasi pemain di meja (format Emotecraft v3 untuk Player Animator) + timeline roulette.

    python tools/table_anims.py          -> tulis ke src/main/resources
    python tools/table_anims.py --dev    -> juga ke build/resources (pratinjau langsung, lihat AnimDev "reload")

Cara membangunnya sama dengan mod Backrooms (tools/match_anims.py): tiap animasi =
daftar POSE KUNCI (tick, pose mutlak, easing). Tiap bagian badan menempuh pose
kuncinya sedikit terlambat dari badan (overlapping action), lalu semua frame
dilewatkan filter pegas teredam kritis (tanpa patahan). Gerak lambat dipanggang
lewat pelengkung waktu: tiap tick nyata punya keyframe sendiri.

Konvensi rig (Player Animator, dikalibrasi di mod Backrooms):
  body pitch +  = rebah ke belakang, -  = condong ke depan; pusat putar 0,7 blok di atas kaki
  body roll  +  = miring ke kiri karakter;  body x + = ke kiri karakter; z + = ke belakang; y - = turun (blok)
  torso bend +  = membungkuk; head pitch - = mendongak; head yaw + = menoleh ke kiri karakter
  arm pitch -90 = lurus ke depan, -180 = lurus ke atas; arm yaw + = ke arah kanan karakter (kedua lengan)
  rightArm roll + / leftArm roll - = keluar; bend lengan - = siku menekuk
  leg pitch -   = paha ke depan; bend kaki + = lutut menekuk ke belakang

Satu sumber waktu: konstanta TIMING di bawah juga dipakai server (TableTiming,
dicek unit test RouletteTimelineTest) dan client (roulette.json).
"""
import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'liarsbar')
OUT_DIRS = [ASSETS]
if '--dev' in sys.argv:
    OUT_DIRS.append(os.path.join(ROOT, 'build', 'resources', 'main', 'assets', 'liarsbar'))

PARTS = ('head', 'torso', 'body', 'rightArm', 'leftArm', 'rightLeg', 'leftLeg', 'rightItem', 'leftItem')
DEFAULTS = {
    'head': (0.0, 0.0, 0.0), 'torso': (0.0, 0.0, 0.0), 'body': (0.0, 0.0, 0.0),
    'rightArm': (-5.0, 2.0, 0.0), 'leftArm': (5.0, 2.0, 0.0),
    'rightLeg': (-1.9, 12.0, 0.1), 'leftLeg': (1.9, 12.0, 0.1),
    'rightItem': (0.0, 0.0, 0.0), 'leftItem': (0.0, 0.0, 0.0),
}
AXES = {
    'body': ('pitch', 'yaw', 'roll', 'x', 'y', 'z'),
    'torso': ('pitch', 'yaw', 'roll', 'bend'),
    'head': ('pitch', 'yaw', 'roll'),
    'rightArm': ('pitch', 'yaw', 'roll', 'x', 'y', 'z', 'bend'),
    'leftArm': ('pitch', 'yaw', 'roll', 'x', 'y', 'z', 'bend'),
    'rightLeg': ('pitch', 'yaw', 'roll', 'bend'),
    'leftLeg': ('pitch', 'yaw', 'roll', 'bend'),
    'rightItem': ('pitch', 'yaw', 'roll'),
}
ANGLE_AXES = ('pitch', 'yaw', 'roll')

# ================================================================ waktu (tick)
# Art Bible shot list (2026-10-24): penuh R1 wide 0-32, R2 insert meja 32-64, R3 dada/silinder 64-108,
# R4 MCU 108-148, R5 reaksi 148-188, R6 siluet 188-212, R7 tarik 212. Pendek: angkat 0-24, kokang 24-44,
# siluet 44-72, tarik 72. KLIK 56, DOR 100.
TIMING = {
    # Versi penuh (momen penting sesudah revolver terisi): silinder TIDAK dibuka/diputar lagi (hanya sekali, saat
    # diisi); revolver ditatap di depan dada 'grab'..'raise'.
    'full': {'grab': 44, 'raise': 108, 'temple': 132, 'cock': 138,
             'hold': 140, 'inhale': 190, 'pull': 212},
    'short': {'grab': 8, 'raise': 12, 'temple': 26, 'cock': 32, 'hold': 34, 'inhale': 56, 'pull': 72},
    # Isi (tarikan pertama penembak): L1 wide 0-20; L2 insert felt 20-56: tangan kiri meraih peluru yang
    # berdiri di samping pistol (jepit 30), mengangkatnya ke depan mata (42), tangan kanan mengambil pistol (52);
    # L3 dekat silinder 56-96: crane dibuka (66), ujung peluru di mulut chamber (80), didorong masuk dari bawah
    # silinder sampai pelek duduk (88); L4 profil 96-128: diputar (100), ditutup (124);
    # sisanya sama dengan versi penuh digeser 20 tick.
    'load': {'pick': 30, 'show': 42, 'grab': 52, 'open': 66, 'insert': 80, 'seat': 88, 'flick': 100, 'close': 124,
             'raise': 128, 'temple': 152, 'cock': 158, 'hold': 160, 'inhale': 210, 'pull': 232},
    'click': {'flinch': 2, 'lower': 12, 'exhale': 12, 'table': 44, 'length': 56},
    'bang': {'fall': 30, 'length': 100},
    # LIAR! (Art Bible): L1 gebrak 0-14 (slow-mo 4-12), L2 OTS + stempel 14-38, L3 dolly zoom 38-64.
    'liar': {'slam': 9, 'stamp': 14, 'dolly': 38, 'length': 64},
    # Reveal: kartu dibalik satu per satu tiap 12 tick mulai tick 8, tahan sampai 64.
    'reveal': {'first': 8, 'step': 12, 'length': 64},
    # Main kartu (TableTiming.THROW): tangan kanan sampai di kipas 6, menjepit 8, mencabut 12, ancang-ancang 13,
    # lepas 15, kartu mendarat dan berhenti meluncur sebelum 30.
    'throw': {'reach': 6, 'pinch': 8, 'draw': 12, 'wind': 13, 'release': 15, 'follow': 19, 'length': 30},
    # Ambil paket hasil bagi (TableTiming.PICKUP): raih 6, genggam 8, angkat sampai kipas 14.
    'pickup': {'reach': 6, 'grasp': 8, 'lift': 14, 'length': 16},
}


# ================================================================ util

def clamp01(x):
    return max(0.0, min(1.0, x))


def smooth(x):
    x = clamp01(x)
    return x * x * (3 - 2 * x)


def smoother(x):
    x = clamp01(x)
    return x * x * x * (x * (x * 6 - 15) + 10)


def ease_out(x):
    return 1 - (1 - clamp01(x)) ** 3


def ease_in(x):
    return clamp01(x) ** 3


def fall(x):
    """Roboh seperti tongkat: pelan lalu makin cepat sampai menghantam."""
    return clamp01(x) ** 2.2


def noise(t, seed, period=23.0):
    return (0.6 * math.sin(2 * math.pi * (t / period + seed * 0.37))
            + 0.3 * math.sin(2 * math.pi * (t / (period * 0.53) + seed * 1.13))
            + 0.1 * math.sin(2 * math.pi * (t / (period * 0.29) + seed * 2.71)))


class Pose(dict):
    def copy(self):
        return Pose({k: dict(v) for k, v in self.items()})


def full(p):
    out = Pose()
    for part, axes in AXES.items():
        src = p.get(part, {})
        out[part] = {a: float(src.get(a, 0.0)) for a in axes}
    return out


def edit(base, **parts):
    """Salinan pose dengan nilai mutlak baru untuk sumbu yang disebut."""
    out = base.copy()
    for part, axes in parts.items():
        out.setdefault(part, {}).update({a: float(v) for a, v in axes.items()})
    return full(out)


def add(p, part, **values):
    for a, v in values.items():
        p[part][a] = p[part].get(a, 0.0) + v
    return p


def lerp_pose(a, b, w):
    out = Pose()
    for part in AXES:
        out[part] = {ax: a[part][ax] + (b[part][ax] - a[part][ax]) * w for ax in AXES[part]}
    return out


# Bagian menyusul badan (tick): overlapping action.
LAG = {'body': 0.0, 'torso': 0.6, 'head': 1.4, 'rightArm': 1.0, 'leftArm': 1.6,
       'rightLeg': 0.8, 'leftLeg': 1.1, 'rightItem': 1.2}


def track_keys(keys, lag=True):
    """keys: [(tick, pose, easing)] -> f(t), tiap bagian terlambat LAG tick."""
    def at(t):
        out = Pose()
        for part in AXES:
            tt = t - (LAG.get(part, 0.0) if lag else 0.0)
            if tt <= keys[0][0]:
                out[part] = dict(keys[0][1][part])
                continue
            for i in range(1, len(keys)):
                k0, k1 = keys[i - 1], keys[i]
                if tt <= k1[0]:
                    ease = k1[2] if len(k1) > 2 else smooth
                    u = (tt - k0[0]) / (k1[0] - k0[0]) if k1[0] > k0[0] else 1.0
                    w = ease(clamp01(u))
                    out[part] = {a: k0[1][part][a] + (k1[1][part][a] - k0[1][part][a]) * w for a in AXES[part]}
                    break
            else:
                out[part] = dict(keys[-1][1][part])
        return out
    return at


# Frekuensi alami pegas (rad/detik). Di meja gerakan pelan dan berat; tangan lebih lincah.
SPRING = {'body': 9.0, 'torso': 10.0, 'head': 12.0, 'rightArm': 14.0, 'leftArm': 13.0,
          'rightLeg': 9.0, 'leftLeg': 9.0, 'rightItem': 18.0}
DEATH_SPRING = {'body': 15.0, 'torso': 17.0, 'head': 20.0, 'rightArm': 16.0, 'leftArm': 15.0,
                'rightLeg': 13.0, 'leftLeg': 12.0, 'rightItem': 18.0}


def spring_filter(targets, table=SPRING, dt=0.05, substeps=8):
    """Tiap sumbu mengikuti targetnya seperti pegas teredam kritis: tanpa lonjakan atau patahan."""
    state = {}
    prev = targets[0]
    out = []
    for target in targets:
        res = Pose()
        for part, axes in target.items():
            w = table.get(part, 12.0)
            for axis, goal in axes.items():
                key = (part, axis)
                start = prev.get(part, {}).get(axis, goal)
                if key not in state:
                    state[key] = [goal, 0.0]
                x, v = state[key]
                h = dt / substeps
                for k in range(substeps):
                    g = start + (goal - start) * (k + 1) / substeps
                    acc = w * w * (g - x) - 2.0 * w * v
                    v += acc * h
                    x += v * h
                state[key] = [x, v]
                res.setdefault(part, {})[axis] = x
        out.append(res)
        prev = target
    return out


def warp(slow_from, slow_to, speed, ramp=4.0):
    """Pelengkung waktu: tick nyata -> waktu adegan, melambat ke `speed` di [slow_from, slow_to]."""
    def rate(t):
        if t < slow_from - ramp or t > slow_to + ramp:
            return 1.0
        if t < slow_from:
            return 1.0 + (speed - 1.0) * smooth((t - (slow_from - ramp)) / ramp)
        if t > slow_to:
            return speed + (1.0 - speed) * smooth((t - slow_to) / ramp)
        return speed
    table = [0.0]
    acc = 0.0
    for i in range(1, 2000):
        # Integrasi per sepersepuluh tick supaya halus.
        for k in range(10):
            acc += rate(i - 1 + (k + 0.5) / 10.0) / 10.0
        table.append(acc)

    def scene(t):
        i = int(t)
        return table[i] + (table[i + 1] - table[i]) * (t - i)
    return scene


# ================================================================ keluaran

def write_json(rel, data):
    text = json.dumps(data, separators=(',', ':'))
    for d in OUT_DIRS:
        path = os.path.join(d, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf8') as f:
            f.write(text)
    return text


def compile_anim(name, frames, loop_from=None, sounds=()):
    """frames: pose tiap tick nyata (0..n). loop_from: tick awal bagian yang diulang (None = tahan frame akhir)."""
    total = len(frames) - 1
    moves = []
    for t, pose in enumerate(frames):
        nxt = frames[t + 1] if t + 1 < len(frames) else None
        for part in PARTS:
            axes = AXES.get(part)
            if not axes:
                continue
            values = {}
            turns = {}
            for axis in axes:
                v = pose[part][axis]
                if nxt is not None and axis in ANGLE_AXES:
                    jump = nxt[part][axis] - v
                    if abs(jump) > 180:
                        turns[axis] = int(round(jump / 360.0))
                if axis in ('x', 'y', 'z'):
                    v = DEFAULTS[part]['xyz'.index(axis)] + v
                values[axis] = round(v, 3)
            plain = {a: v for a, v in values.items() if a not in turns}
            if plain:
                moves.append({'tick': t, 'easing': 'LINEAR', 'turn': 0, part: plain})
            for axis, turn in turns.items():
                moves.append({'tick': t, 'easing': 'LINEAR', 'turn': turn, part: {axis: values[axis]}})
    loop = loop_from is not None
    data = {
        'version': 3, 'name': name, 'author': "Liar's Table",
        'sounds': ','.join('%s:%d' % (k, x) for k, x in sounds),
        'emote': {'isLoop': loop, 'returnTick': loop_from if loop else total, 'beginTick': 0, 'endTick': total,
                  'stopTick': total + 1, 'degrees': True, 'easeBeforeKeyframe': True, 'moves': moves},
    }
    text = write_json(os.path.join('player_animation', name + '.json'), data)
    print('%-16s %4d tick  loop@%-4s %6d moves  %6.1f KB' % (name, total, loop_from, len(moves), len(text) / 1024))


def hold_end(frames):
    """Animasi yang harus menahan pose akhirnya (tewas): frame terakhir digandakan dan diulang. Tanpa ini
    Player Animator melepas animasi di endTick dan badan kembali ke pose duduk vanilla (terlihat hidup
    lagi) sampai fase berikutnya mulai."""
    return frames + [frames[-1]]


def bake(raw_fn, length, table=SPRING, scene=None):
    frames = [full(raw_fn(scene(t) if scene else t)) for t in range(length + 1)]
    return spring_filter(frames, table)


def seamless(frames, loop_from, blend=10):
    """Ujung bagian loop dibaurkan ke awalnya supaya pengulangan tidak melompat."""
    n = len(frames) - 1
    for i in range(blend):
        t = n - blend + 1 + i
        frames[t] = lerp_pose(frames[t], frames[loop_from], smooth((i + 1) / blend))
    return frames


# ================================================================ pose dasar duduk

def sit():
    """Duduk tegak di kursi, lengan bawah bertumpu di meja, menatap tengah meja."""
    return full(Pose({
        'body': {},
        'torso': {'bend': 4},
        'head': {'pitch': 10},
        'rightArm': {'pitch': -46, 'yaw': -12, 'roll': 4, 'bend': -56, 'z': -0.5},
        'leftArm': {'pitch': -46, 'yaw': 12, 'roll': -4, 'bend': -56, 'z': -0.5},
        'rightLeg': {'pitch': -86, 'yaw': 4, 'roll': 3, 'bend': 86},
        'leftLeg': {'pitch': -86, 'yaw': -4, 'roll': -3, 'bend': 86},
    }))


def breathe(p, t, period=80.0, depth=1.0):
    b = math.sin(2 * math.pi * t / period) * depth
    add(p, 'torso', pitch=-1.2 * b)
    add(p, 'head', pitch=0.8 * b)
    add(p, 'rightArm', pitch=-0.6 * b)
    add(p, 'leftArm', pitch=-0.6 * b)
    return p


def tremor(p, t, amount):
    """Gemetar halus (tangan dan kepala), untuk tegang."""
    add(p, 'rightArm', pitch=noise(t * 3.1, 1) * 1.2 * amount, roll=noise(t * 2.7, 2) * 0.9 * amount)
    add(p, 'leftArm', pitch=noise(t * 2.9, 3) * 0.8 * amount)
    add(p, 'head', pitch=noise(t * 1.7, 4) * 0.7 * amount, yaw=noise(t * 1.3, 5) * 0.9 * amount)
    return p


# Pose pistol di pelipis kanan: lengan atas terangkat ke samping, siku menekuk tajam.
# Lengan atas terangkat ke samping (siku terbuka lebar), lengan bawah tegak: tangan di samping pelipis
# cukup jauh untuk revolver yang larasnya menempel di kepala (dikalibrasi di AnimDev).
TEMPLE = dict(pitch=-15, yaw=90, roll=100, bend=-95, z=0.0, x=0.0, y=0.0)
TEMPLE_ITEM = dict(pitch=-35, yaw=0, roll=0)
# Pistol di depan dada (memutar silinder).
CHEST = dict(pitch=-40, yaw=-24, roll=0, bend=-62, z=-0.5)
CHEST_LEFT = dict(pitch=-42, yaw=30, roll=-6, bend=-62, z=-0.5)


def idle_calm():
    """Diam yang hidup: napas, jari mengetuk meja, sesekali melirik pistol di meja."""
    base = sit()
    length = 120

    def raw(t):
        p = base.copy()
        breathe(p, t, 60.0, 1.0)
        # Jari kanan mengetuk (lengan bawah naik turun sedikit) di tick 30..54.
        if 30 <= t <= 54:
            tap = max(0.0, math.sin(2 * math.pi * (t - 30) / 6.0)) * math.sin(math.pi * (t - 30) / 24.0)
            add(p, 'rightArm', bend=-3.0 * tap, pitch=-1.0 * tap)
        # Melirik pistol (di depan kanan bawah) di 80..104.
        g = math.sin(math.pi * clamp01((t - 80) / 24.0)) ** 2
        add(p, 'head', pitch=12 * g, yaw=-10 * g)
        return p
    frames = bake(raw, length)
    compile_anim('sit_calm', seamless(frames, 0, 12), loop_from=0)


def idle_tense():
    """Gelisah: napas lebih cepat, bahu naik, kaki bergoyang, sering menatap pistol."""
    base = edit(sit(), torso=dict(bend=9), head=dict(pitch=14))
    length = 100

    def raw(t):
        p = base.copy()
        breathe(p, t, 40.0, 1.3)
        tremor(p, t, 0.4)
        bounce = math.sin(2 * math.pi * t / 7.0)
        add(p, 'rightLeg', pitch=2.0 * bounce)
        g = math.sin(math.pi * clamp01((t - 20) / 30.0)) ** 2 + math.sin(math.pi * clamp01((t - 68) / 22.0)) ** 2
        add(p, 'head', pitch=10 * g, yaw=-12 * g)
        return p
    frames = bake(raw, length)
    compile_anim('sit_tense', seamless(frames, 0, 12), loop_from=0)


def hold_pose():
    """Pistol di pelipis kanan, kepala sedikit menjauh dari laras."""
    return edit(sit(), rightArm=TEMPLE, rightItem=TEMPLE_ITEM, head=dict(pitch=2, roll=-5, yaw=6),
                torso=dict(bend=6, roll=0), leftArm=dict(pitch=-40, yaw=16, roll=-6, bend=-50, z=-0.5))


def hold_life(p, t, start, intensity):
    """Selama menahan: napas berat, gemetar makin kuat, tarikan napas panjang sebelum menekan."""
    k = clamp01((t - start) / 40.0) * intensity
    breathe(p, t, 34.0, 1.6)
    tremor(p, t, 0.6 + 0.9 * k)
    return p


def roulette(kind):
    """Mengambil revolver, (versi penuh: membuka dan memutar silinder), mengangkat ke pelipis, mengokang, menahan."""
    T = TIMING[kind]
    s = sit()
    look = edit(s, head=dict(pitch=28, yaw=-8), torso=dict(bend=8))
    reach = edit(look, rightArm=dict(pitch=-38, yaw=-18, roll=2, bend=-30, z=-0.5), torso=dict(bend=12),
                 body=dict(pitch=-4))
    chest = edit(look, rightArm=CHEST, rightItem=dict(pitch=-70), head=dict(pitch=34, yaw=-12), body=dict(pitch=-2))
    hold = hold_pose()
    grab = T['grab']
    # R1: menatap pistol lama (antisipasi), lalu meraih: badan turun sedikit dulu sebelum mengangkat.
    dip = edit(reach, body=dict(pitch=-7), torso=dict(bend=15))
    keys = [(0, s), (min(12, grab - 6), look), (max(grab - 12, min(12, grab - 6)), look), (grab - 2, dip),
            (grab, reach, ease_out)]
    if kind == 'load':
        # Meraih peluru: badan turun ke felt, kepala menunduk ke peluru; lalu peluru diangkat ke depan mata dan
        # ditatap sebentar (antisipasi), baru tangan kanan meraih pistol.
        pick = edit(look, body=dict(pitch=-15), torso=dict(bend=20), head=dict(pitch=34, yaw=10, roll=4),
                    leftArm=dict(pitch=-58, yaw=-6, roll=-4, bend=-30, z=-0.5))
        shown = edit(s, head=dict(pitch=14, yaw=6, roll=3), torso=dict(bend=5), body=dict(pitch=-1),
                     leftArm=dict(pitch=-100, yaw=24, roll=-8, bend=-90, z=-0.5))
        dip_l = edit(dip, leftArm=shown['leftArm'], head=dict(pitch=30, yaw=-6))
        reach_l = edit(reach, leftArm=shown['leftArm'])
        keys = [(0, s), (12, look), (T['pick'] - 10, look), (T['pick'] - 1, pick, ease_out), (T['pick'] + 2, pick),
                (T['show'], shown, ease_out), (T['grab'] - 10, shown), (T['grab'] - 2, dip_l), (T['grab'], reach_l, ease_out)]
        chest_l = edit(chest, leftArm=shown['leftArm'])
        # Mengisi: menunduk ke silinder yang terbuka, bahu kiri maju (tangan kiri di atas silinder).
        loading = edit(chest, head=dict(pitch=24, yaw=-10), torso=dict(bend=5, yaw=-4), body=dict(pitch=-1),
                       leftArm=dict(pitch=-62, yaw=26, roll=-4, bend=-70, z=-0.5))
        pushed = edit(loading, head=dict(pitch=26), torso=dict(bend=6))
        spin_hand = edit(chest, leftArm=CHEST_LEFT)
        flick = edit(spin_hand, leftArm=dict(pitch=-58, yaw=10, roll=-10, bend=-60))
        closed = edit(chest, rightItem=dict(pitch=-62), rightArm=dict(pitch=-66))
        keys += [(T['open'] - 4, chest_l), (T['insert'] - 4, loading), (T['seat'], pushed, ease_out),
                 (T['seat'] + 4, loading), (T['flick'] - 4, spin_hand), (T['flick'], flick, ease_out),
                 (T['flick'] + 10, spin_hand), (T['close'] - 4, spin_hand), (T['close'], closed, ease_out),
                 (T['raise'], chest)]
    elif kind == 'full':
        # Menatap revolver di tangan (sudah terisi): kepala menunduk ke silinder, napas tertahan, sedikit dimiringkan.
        stare = edit(chest, head=dict(pitch=38, yaw=-14, roll=-3), torso=dict(bend=10), rightItem=dict(pitch=-64))
        keys += [(T['grab'] + 14, chest), (T['grab'] + 30, stare), (T['raise'] - 10, stare), (T['raise'], chest)]
    else:
        keys += [(T['raise'], chest)]
    mid = lerp_pose(chest, hold, 0.55)
    mid = edit(mid, head=dict(pitch=6, yaw=2))
    keys += [((T['raise'] + T['temple']) // 2, mid), (T['temple'], hold, ease_out)]
    cocked = edit(hold, rightArm=dict(bend=TEMPLE['bend'] - 4, pitch=TEMPLE['pitch'] + 2))
    keys += [(T['cock'], cocked), (T['cock'] + 3, hold), (T['pull'], hold)]
    f = track_keys(keys)
    length = T['pull'] + 30

    def raw(t):
        p = f(t)
        if t >= T['temple']:
            hold_life(p, t, T['hold'], 1.0)
        # Tarikan napas panjang lalu menahan: bahu naik, kepala sedikit mendongak.
        b = smooth((t - T['inhale']) / 12.0) * (1 - smooth((t - T['pull'] - 4) / 10.0))
        add(p, 'torso', pitch=-3.0 * b, bend=-3.0 * b)
        add(p, 'head', pitch=-3.0 * b)
        return p
    frames = bake(raw, length)
    # Sesudah tick pelatuk: tahan (gemetar) berulang sampai hasilnya datang dari server.
    compile_anim('roulette_' + kind, seamless(frames, T['pull'], 12), loop_from=T['pull'],
                 sounds=[('inhale', T['inhale'])])


def after_click():
    """Selamat: tersentak kecil, membeku, lalu menurunkan pistol pelan sambil menghembus napas lega,
    meletakkan pistol di meja, kembali duduk."""
    T = TIMING['click']
    hold = hold_pose()
    flinch = edit(hold, head=dict(pitch=-2, roll=-9, yaw=10), torso=dict(bend=2),
                  rightArm=dict(bend=TEMPLE['bend'] + 6))
    frozen = edit(hold, head=dict(pitch=4, roll=-6, yaw=8))
    lowered = edit(sit(), rightArm=dict(pitch=-70, yaw=-22, bend=-64, z=-0.5), rightItem=dict(pitch=-70),
                   head=dict(pitch=24), torso=dict(bend=16), body=dict(pitch=-3))
    on_table = edit(sit(), rightArm=dict(pitch=-40, yaw=-18, bend=-34, z=-0.5), head=dict(pitch=22),
                    torso=dict(bend=12), body=dict(pitch=-4))
    keys = [(0, hold), (T['flinch'], flinch, ease_out), (8, frozen), (T['lower'], frozen),
            (30, lowered), (T['table'], on_table), (T['length'], sit())]
    f = track_keys(keys)

    def raw(t):
        p = f(t)
        # Napas lega: dada turun panjang.
        e = math.sin(math.pi * clamp01((t - T['exhale']) / 26.0))
        add(p, 'torso', bend=6 * e, pitch=2 * e)
        add(p, 'head', pitch=6 * e)
        breathe(p, t, 40.0, 0.8)
        return p
    frames = bake(raw, T['length'])
    compile_anim('after_click', frames, sounds=[('exhale', T['exhale']), ('table', T['table'])])


def death_side():
    """Tertembak di pelipis kanan: kepala tersentak ke kiri, badan lemas miring ke kiri, jatuh dari kursi
    ke lantai, pantulan kecil, lalu diam. Gerak lambat dipanggang di detik-detik awal."""
    T = TIMING['bang']
    hold = hold_pose()
    snap = edit(hold, head=dict(pitch=-6, roll=38, yaw=22), torso=dict(roll=8, bend=2),
                rightArm=dict(pitch=-120, roll=40, bend=-90))
    limp = edit(sit(), body=dict(roll=14, x=0.06), head=dict(pitch=8, roll=46, yaw=18), torso=dict(roll=10, bend=10),
                rightArm=dict(pitch=-70, yaw=0, roll=34, bend=-40, z=0), leftArm=dict(pitch=-18, yaw=4, roll=-20, bend=-16, z=0),
                rightItem=dict(pitch=-40))
    slide = edit(limp, body=dict(roll=46, x=0.32, y=-0.18), head=dict(pitch=4, roll=30, yaw=10),
                 rightArm=dict(pitch=-40, roll=60, bend=-20), leftArm=dict(pitch=-10, roll=-50, bend=-10),
                 rightLeg=dict(pitch=-70, bend=60, roll=-8), leftLeg=dict(pitch=-76, bend=70, roll=-14))
    land = edit(slide, body=dict(roll=86, x=0.72, y=-0.52, pitch=4), head=dict(pitch=-4, roll=-12, yaw=0),
                torso=dict(roll=0, bend=4),
                rightArm=dict(pitch=-24, roll=70, bend=-14), leftArm=dict(pitch=-96, roll=0, yaw=0, bend=-12),
                rightLeg=dict(pitch=-58, bend=44, roll=2), leftLeg=dict(pitch=-44, bend=60, roll=-6))
    bounce = edit(land, body=dict(roll=81, y=-0.49))
    rest = edit(land, head=dict(pitch=6, roll=-16, yaw=8), rightArm=dict(pitch=-16, roll=76, bend=-10),
                leftArm=dict(pitch=-100, bend=-20), rightLeg=dict(pitch=-62, bend=50), leftLeg=dict(pitch=-40, bend=66))
    keys = [(0, hold), (2, snap, ease_out), (9, limp), (20, slide, ease_in), (T['fall'], land, fall),
            (T['fall'] + 4, bounce, ease_out), (T['fall'] + 9, land), (T['fall'] + 30, rest), (90, rest)]
    f = track_keys(keys)

    def raw(t):
        p = f(t)
        b = math.sin(math.pi * clamp01((t - 58) / 12.0))
        add(p, 'torso', pitch=-2.0 * b)
        return p
    # Gerak lambat: 3..18 tick nyata berjalan 0,45x (hentakan pertama tetap cepat).
    scene = warp(3, 18, 0.45)
    frames = bake(raw, 90, DEATH_SPRING, scene)
    fall_tick = next(t for t in range(91) if scene(t) >= T['fall'])
    compile_anim('death_side', hold_end(frames), loop_from=len(frames) - 1, sounds=[('fall', fall_tick)])
    compile_anim('dead_side', [frames[-1], frames[-1]], loop_from=0)
    return fall_tick


def death_table():
    """Tertembak lalu ambruk ke depan: kepala tersentak, badan roboh ke meja, pipi di daun meja."""
    hold = hold_pose()
    snap = edit(hold, head=dict(pitch=-4, roll=36, yaw=20), torso=dict(roll=6),
                rightArm=dict(pitch=-118, roll=38, bend=-88))
    limp = edit(sit(), body=dict(pitch=-6, roll=6), head=dict(pitch=18, roll=34, yaw=12), torso=dict(bend=14, roll=6),
                rightArm=dict(pitch=-64, yaw=-6, roll=24, bend=-34, z=0), leftArm=dict(pitch=-40, roll=-10, bend=-30, z=0),
                rightItem=dict(pitch=-50))
    slump = edit(limp, body=dict(pitch=-34, roll=4, z=-0.08), head=dict(pitch=30, roll=48, yaw=4),
                 torso=dict(bend=30, roll=2), rightArm=dict(pitch=-58, yaw=-14, roll=10, bend=-24),
                 leftArm=dict(pitch=-62, yaw=16, roll=-8, bend=-20))
    bounce = edit(slump, body=dict(pitch=-31), head=dict(pitch=26))
    rest = edit(slump, head=dict(pitch=32, roll=52, yaw=6), rightArm=dict(pitch=-54, bend=-18),
                leftArm=dict(pitch=-20, yaw=4, roll=-14, bend=-6))
    keys = [(0, hold), (2, snap, ease_out), (9, limp), (24, slump, fall), (28, bounce, ease_out), (32, slump),
            (60, rest), (80, rest)]
    scene = warp(3, 16, 0.45)
    frames = bake(track_keys(keys), 80, DEATH_SPRING, scene)
    hit = next(t for t in range(81) if scene(t) >= 24)
    compile_anim('death_table', hold_end(frames), loop_from=len(frames) - 1, sounds=[('slump', hit)])
    compile_anim('dead_table', [frames[-1], frames[-1]], loop_from=0)
    return hit


# ================================================================ LIAR!

def liar_call():
    """Penuduh: tarik tangan ke atas (antisipasi), gebrak meja (gerak lambat dipanggang), setengah berdiri
    sambil menunjuk ke tengah meja, tahan."""
    T = TIMING['liar']
    s = sit()
    wind = edit(s, rightArm=dict(pitch=-150, yaw=-8, roll=12, bend=-55, z=-0.5), body=dict(pitch=5),
                torso=dict(bend=-2), head=dict(pitch=-4, yaw=-4))
    slam = edit(s, rightArm=dict(pitch=-64, yaw=-6, roll=0, bend=-8, z=-0.5), body=dict(pitch=-15),
                torso=dict(bend=14), head=dict(pitch=-4))
    recoil = edit(slam, rightArm=dict(pitch=-70, bend=-14), body=dict(pitch=-12))
    rise = edit(slam, body=dict(pitch=-20, y=0.10, z=-0.06), rightArm=dict(pitch=-62, yaw=-4, bend=-10),
                leftArm=dict(pitch=-98, yaw=24, roll=0, bend=0, z=-0.5), head=dict(pitch=-14, yaw=6),
                torso=dict(bend=8))
    keys = [(0, s), (6, wind, ease_out), (T['slam'], slam, ease_in), (T['slam'] + 3, recoil, ease_out),
            (T['stamp'] + 6, rise, ease_out), (T['length'], rise)]
    f = track_keys(keys)

    def raw(t):
        p = f(t)
        if t > T['stamp']:
            breathe(p, t, 30.0, 1.4)
        return p
    scene = warp(4, 12, 0.5)
    frames = bake(raw, T['length'] + 20, SPRING, scene)
    compile_anim('liar_call', seamless(frames, T['length'], 12), loop_from=T['length'], sounds=[('slam', T['slam'])])


def liar_flinch():
    """Yang dituduh: tersentak mundur saat meja digebrak, lalu bersandar waspada menatap penuduh."""
    T = TIMING['liar']
    s = sit()
    jolt = edit(s, body=dict(pitch=9, z=0.04), head=dict(pitch=-10, roll=4), torso=dict(bend=-4),
                rightArm=dict(pitch=-30, bend=-70), leftArm=dict(pitch=-30, bend=-70))
    wary = edit(s, body=dict(pitch=5), head=dict(pitch=2, yaw=-4), torso=dict(bend=2),
                rightArm=dict(pitch=-40, bend=-60), leftArm=dict(pitch=-38, bend=-62))
    keys = [(0, s), (T['slam'], s), (T['slam'] + 3, jolt, ease_out), (T['slam'] + 16, wary), (T['length'], wary)]
    f = track_keys(keys)

    def raw(t):
        p = f(t)
        breathe(p, t, 34.0, 1.2)
        tremor(p, t, 0.3 * smooth((t - T['dolly']) / 20.0))
        return p
    scene = warp(4, 12, 0.5)
    frames = bake(raw, T['length'] + 20, SPRING, scene)
    compile_anim('liar_flinch', seamless(frames, T['length'], 12), loop_from=T['length'])


# ================================================================ kartu

def card_throw():
    """Main kartu: menunduk ke kipas di tangan kiri, kipas ditarik ke depan dada, tangan kanan mencabut kartu
    ke kanan atas, ancang-ancang kecil, lalu menjentikkannya ke tengah meja dengan badan ikut condong. Tangan
    kanan dikunci IK ke kipas dan jalur lempar (ThrowDirector); kunci di sini menjadi pose dasarnya."""
    T = TIMING['throw']
    s = sit()
    fan = dict(pitch=-60, yaw=24, roll=-2, bend=-60, z=-0.5)
    look = edit(s, head=dict(pitch=24, yaw=8), torso=dict(bend=8), leftArm=fan,
                rightArm=dict(pitch=-72, yaw=-38, roll=0, bend=-72, z=-0.5))
    draw = edit(look, head=dict(pitch=18, yaw=2), rightArm=dict(pitch=-92, yaw=-14, bend=-82))
    wind = edit(draw, body=dict(pitch=2), head=dict(pitch=12, yaw=0), rightArm=dict(pitch=-98, yaw=-6, bend=-96))
    flick = edit(s, body=dict(pitch=-8), torso=dict(bend=10), head=dict(pitch=8),
                 leftArm=dict(pitch=-54, yaw=18, roll=-3, bend=-58, z=-0.5),
                 rightArm=dict(pitch=-78, yaw=-6, roll=2, bend=-20, z=-0.5))
    follow = edit(flick, body=dict(pitch=-9), rightArm=dict(pitch=-70, bend=-14))
    keys = [(0, s), (T['reach'], look, ease_out), (T['pinch'], look), (T['draw'], draw), (T['wind'], wind, ease_out),
            (T['release'], flick, ease_in), (T['follow'], follow, ease_out), (T['length'] - 2, s), (T['length'], s)]
    f = track_keys(keys)

    def raw(t):
        p = f(t)
        breathe(p, t, 40.0, 0.8)
        return p
    frames = bake(raw, T['length'])
    compile_anim('card_throw', hold_end(frames), loop_from=len(frames) - 1)


def deal_shuffle():
    """Dealer mengumpulkan dan mengocok di felt: condong jauh ke depan (tangan sampai ke dek di felt),
    menunduk ke tangan. Lengan dikunci IK ke dek (DealDirector); di sini badan, kepala, dan napas."""
    s = sit()
    # Cukup condong supaya tangan sampai ke dek, tapi kepala tetap di atas tangan (wajah terbaca dari depan).
    lean = edit(s, body=dict(pitch=-13), torso=dict(bend=7), head=dict(pitch=14),
                rightArm=dict(pitch=-66, yaw=4, roll=12, bend=-24, z=-0.5),
                leftArm=dict(pitch=-66, yaw=-4, roll=-12, bend=-24, z=-0.5))
    keys = [(0, s), (12, lean, ease_out), (80, lean)]
    f = track_keys(keys)

    def raw(t):
        p = f(t)
        breathe(p, t, 36.0, 0.9)
        add(p, 'head', yaw=noise(t, 7, 47.0) * 2.0)
        return p
    frames = bake(raw, 80)
    compile_anim('deal_shuffle', seamless(frames, 16, 14), loop_from=16)


def deal_toss():
    """Dealer membagi: duduk lebih tegak, dek di tangan kiri, tangan kanan melempar (IK). Kepala mengikuti
    kursi tujuan lewat LookLayer."""
    s = sit()
    deal = edit(s, body=dict(pitch=-11), torso=dict(bend=7), head=dict(pitch=14),
                leftArm=dict(pitch=-64, yaw=18, roll=-3, bend=-56, z=-0.5),
                rightArm=dict(pitch=-70, yaw=-8, roll=3, bend=-40, z=-0.5))
    keys = [(0, deal), (60, deal)]
    f = track_keys(keys)

    def raw(t):
        p = f(t)
        breathe(p, t, 30.0, 0.8)
        return p
    frames = bake(raw, 60)
    compile_anim('deal_toss', seamless(frames, 0, 12), loop_from=0)


def deal_pickup():
    """Mengambil paket di felt: condong dan menunduk ke paket, tangan kiri meraih (IK), lalu tegak lagi sambil
    membuka kipas."""
    T = TIMING['pickup']
    s = sit()
    reach = edit(s, body=dict(pitch=-13), torso=dict(bend=9), head=dict(pitch=24, yaw=6),
                 leftArm=dict(pitch=-72, yaw=4, roll=-3, bend=-28, z=-0.5))
    look = edit(s, head=dict(pitch=18, yaw=4), torso=dict(bend=6))
    keys = [(0, s), (T['reach'], reach, ease_out), (T['grasp'], reach), (T['lift'], look), (T['length'], look),
            (T['length'] + 8, s)]
    f = track_keys(keys)
    frames = bake(lambda t: breathe(f(t), t, 40.0, 0.8), T['length'] + 8)
    compile_anim('deal_pickup', hold_end(frames), loop_from=len(frames) - 1)


# ================================================================ timeline sinematik

def timeline_full_camera():
    """Kunci kamera roulette versi penuh (Art Bible R1-R7)."""
    return [
        # R1 WIDE dari belakang kursi seberang, push-in pelan.
        {'t': 0, 'pos': [1.35, 0.55, 4.70], 'aim': [0.00, -0.35, 0.00], 'fov': 38},
        {'t': 31, 'pos': [1.25, 0.50, 4.40], 'aim': [0.00, -0.35, 0.00], 'fov': 38},
        # R2 INSERT setinggi felt: tangan meraih revolver di meja.
        {'t': 32, 'cut': True, 'pos': [-0.35, -0.38, 1.35], 'aim': [0.18, -0.44, 0.55], 'fov': 34},
        {'t': 63, 'pos': [-0.30, -0.39, 1.25], 'aim': [0.18, -0.44, 0.55], 'fov': 33},
        # R3 INSERT profil revolver di dada (ditatap, tidak diputar), orbit 10 derajat.
        {'t': 64, 'cut': True, 'pos': [-0.57, -0.18, 1.35], 'aim': [0.08, -0.36, 0.40], 'fov': 30},
        {'t': 107, 'pos': [-0.44, -0.16, 1.42], 'aim': [0.08, -0.36, 0.40], 'fov': 30},
        # R4 MCU depan-kiri 15 derajat (style frame), dolly in sangat pelan, hammer dikokang.
        {'t': 108, 'cut': True, 'pos': [-0.77, 0.27, 2.87], 'aim': [0.00, -0.15, 0.00], 'fov': 40},
        {'t': 147, 'pos': [-0.70, 0.25, 2.62], 'aim': [0.00, -0.15, 0.00], 'fov': 39},
        # R5 OTS dari belakang bahu penembak: reaksi pemain lain.
        {'t': 148, 'cut': True, 'pos': [-0.80, 0.30, -0.45], 'aim': [0.35, -0.30, 3.90], 'fov': 40},
        {'t': 187, 'pos': [-0.78, 0.30, -0.40], 'aim': [0.35, -0.30, 3.90], 'fov': 39},
        # R6 SILUET: depan-kanan, wajah gelap, tepi disinari (TableLights).
        {'t': 188, 'cut': True, 'pos': [1.05, 0.00, 1.20], 'aim': [0.05, -0.05, 0.00], 'fov': 32},
        {'t': 211, 'pos': [1.03, 0.00, 1.16], 'aim': [0.05, -0.05, 0.00], 'fov': 32},
        # R7 potong balik ke MCU: tarik.
        {'t': 212, 'cut': True, 'pos': [-0.70, 0.25, 2.62], 'aim': [0.00, -0.15, 0.00], 'fov': 38},
        {'t': 240, 'pos': [-0.69, 0.25, 2.58], 'aim': [0.00, -0.15, 0.00], 'fov': 38},
    ]


def timeline(side_fall, table_hit):
    """Data untuk RouletteDirector: event per fase dan bidikan kamera relatif penembak.
    Kamera: kerangka penembak (r = kanan, u = atas, f = ke tengah meja), titik asal = mata penembak."""
    full_t = TIMING['full']
    short_t = TIMING['short']
    load_t = TIMING['load']
    shift = load_t['raise'] - full_t['raise']
    # Versi isi: shot pembuka sendiri, sesudah silinder ditutup sama dengan versi penuh (digeser).
    full_cam = timeline_full_camera()
    load_cam = [
        # L1 WIDE dari belakang kursi seberang: peluru berdiri di samping pistol, penembak menatapnya.
        {'t': 0, 'pos': [1.35, 0.55, 4.70], 'aim': [0.00, -0.35, 0.00], 'fov': 38},
        {'t': 19, 'pos': [1.30, 0.52, 4.52], 'aim': [0.00, -0.35, 0.00], 'fov': 38},
        # L2 dari depan kiri, agak rendah: peluru berdiri di kiri depan penembak, tangan kiri turun menjepitnya,
        # lalu kamera mendongak mengikuti peluru yang diangkat ke depan mata.
        {'t': 20, 'cut': True, 'pos': [-0.95, -0.18, 1.75], 'aim': [-0.18, -0.44, 0.45], 'fov': 34},
        {'t': 33, 'pos': [-0.92, -0.18, 1.70], 'aim': [-0.16, -0.42, 0.45], 'fov': 33},
        {'t': 44, 'pos': [-0.90, -0.12, 1.68], 'aim': [-0.06, -0.20, 0.38], 'fov': 33},
        {'t': 55, 'pos': [-0.89, -0.11, 1.68], 'aim': [-0.04, -0.22, 0.38], 'fov': 33},
        # L3 dari kiri atas penembak: laras ke depan, silinder keluar ke kiri, peluru terlihat di belakang silinder
        # saat didorong masuk (kepalan di bawahnya; dari samping/depan peluru tertutup kepalan atau silinder).
        {'t': 56, 'cut': True, 'pos': [-0.64, 0.15, 1.00], 'aim': [-0.05, -0.33, 0.38], 'fov': 27},
        {'t': 95, 'pos': [-0.58, 0.13, 0.92], 'aim': [-0.05, -0.33, 0.38], 'fov': 26},
        # L4 dari depan atas: wajah menunduk ke revolver, tangan kiri menyapu silinder searah putarannya, crane ditutup.
        {'t': 96, 'cut': True, 'pos': [-0.42, 0.34, 1.42], 'aim': [-0.05, -0.30, 0.40], 'fov': 34},
        {'t': 127, 'pos': [-0.36, 0.32, 1.48], 'aim': [-0.05, -0.30, 0.40], 'fov': 34},
    ] + [dict(k, t=k['t'] + shift) for k in full_cam if k['t'] >= full_t['raise']]
    data = {
        'timing': TIMING,
        'death': {'death_side': {'fall': side_fall}, 'death_table': {'fall': table_hit}},
        # Kunci kamera: t (tick), pos [r,u,f], aim [r,u,f], fov. Diinterpolasi Catmull-Rom.
        'camera': {
            # Shot list Art Bible. "cut": true = potongan keras (segmen baru, tidak diinterpolasi dari kunci sebelumnya).
            'full': full_cam,
            'load': load_cam,
            'short': [
                {'t': 0, 'pos': [-0.77, 0.27, 2.87], 'aim': [0.00, -0.15, 0.00], 'fov': 40},
                {'t': 43, 'pos': [-0.68, 0.25, 2.58], 'aim': [0.00, -0.15, 0.00], 'fov': 39},
                {'t': 44, 'cut': True, 'pos': [1.05, 0.00, 1.20], 'aim': [0.05, -0.05, 0.00], 'fov': 32},
                {'t': 71, 'pos': [1.03, 0.00, 1.16], 'aim': [0.05, -0.05, 0.00], 'fov': 32},
                {'t': 72, 'cut': True, 'pos': [-0.70, 0.25, 2.62], 'aim': [0.00, -0.15, 0.00], 'fov': 38},
                {'t': 100, 'pos': [-0.69, 0.25, 2.58], 'aim': [0.00, -0.15, 0.00], 'fov': 38},
            ],
            # KLIK: tahan MCU 0,6 dtk, lalu ease mundur ke wide.
            'click': [
                {'t': 0, 'pos': [-0.70, 0.25, 2.62], 'aim': [0.00, -0.15, 0.00], 'fov': 38},
                {'t': 12, 'pos': [-0.70, 0.25, 2.62], 'aim': [0.00, -0.15, 0.00], 'fov': 38},
                # Mundur ke wide dari sisi depan-kanan (bukan lewat kursi penonton di seberang).
                {'t': 34, 'pos': [1.35, 0.55, 4.20], 'aim': [0.00, -0.40, 0.00], 'fov': 40},
                # Insert kapur: goresan baru di bibir meja depan penembak.
                {'t': 36, 'cut': True, 'pos': [0.30, -0.22, 1.05], 'aim': [0.00, -0.47, 0.55], 'fov': 30},
                {'t': 56, 'pos': [0.26, -0.22, 1.00], 'aim': [0.00, -0.47, 0.55], 'fov': 29},
            ],
            # DOR: putih 2 frame (PostFx), potong ke wide tinggi di depan kiri, turun pelan melihat badan jatuh.
            'bang': [
                {'t': 0, 'pos': [-1.10, 1.05, 2.30], 'aim': [-0.45, -0.95, 0.20], 'fov': 50},
                {'t': 40, 'pos': [-1.20, 0.95, 2.00], 'aim': [-0.60, -1.05, 0.05], 'fov': 50},
                {'t': 100, 'pos': [-1.25, 0.90, 1.85], 'aim': [-0.65, -1.10, 0.00], 'fov': 48},
            ],
        },
        # Titik genggam revolver di tangan (kerangka penembak: kanan, atas, depan dari mata), sejalan
        # dengan pose kunci animasi. Dada: cukup jauh di depan supaya tangan kiri yang memutar silinder
        # tetap di luar badan. Pelipis: laras miring naik 15 derajat, moncong tepat menyentuh pelipis
        # (r 0,28; kepala sampai r 0,25), tidak menembus kepala.
        # liftOut/liftUp: titik antara dada -> pelipis (revolver didorong maju, lalu diangkat tegak di samping
        # kepala) supaya revolver tidak menyapu muka (RouletteDirector.raise).
        'gunpath': {'chest': [0.10, -0.36, 0.40], 'temple': [0.609, -0.218, 0.027],
                    'liftOut': [0.34, -0.38, 0.46], 'liftUp': [0.62, -0.14, 0.16]},
        # Revolver: kapan di tangan, sudut silinder, crane, hammer, pelatuk (diinterpolasi linear).
        'gun': {
            'full': {'hand': [full_t['grab'], 10000], 'cock': [[full_t['cock'] - 1, 0], [full_t['cock'] + 2, 1]],
                     'trigger': [[full_t['pull'] - 3, 0], [full_t['pull'], 1]]},
            'load': {'hand': [load_t['grab'], 10000], 'open': [[load_t['open'], 0], [load_t['open'] + 5, 1],
                                                                [load_t['close'] - 2, 1], [load_t['close'], 0]],
                     'spin': [load_t['flick'], load_t['close'] - 4], 'cock': [[load_t['cock'] - 1, 0], [load_t['cock'] + 2, 1]],
                     'trigger': [[load_t['pull'] - 3, 0], [load_t['pull'], 1]]},
            'short': {'hand': [short_t['grab'], 10000], 'cock': [[short_t['cock'] - 1, 0], [short_t['cock'] + 2, 1]],
                      'trigger': [[short_t['pull'] - 3, 0], [short_t['pull'], 1]]},
            'click': {'hand': [-1, TIMING['click']['table']]},
        },
        # Suara posisional dari penembak (tick: nama).
        'sounds': {
            'full': [[full_t['grab'], 'gun_pickup'], [full_t['cock'], 'hammer_cock'], [full_t['inhale'], 'inhale_tense']],
            'load': [[load_t['pick'], 'bullet_pick'], [load_t['grab'], 'gun_pickup'], [load_t['open'], 'revolver_open'],
                     [load_t['insert'] + 1, 'bullet_slide'], [load_t['seat'], 'bullet_seat'],
                     [load_t['flick'], 'revolver_spin'], [load_t['close'], 'revolver_close'],
                     [load_t['cock'], 'hammer_cock'], [load_t['inhale'], 'inhale_tense']],
            'short': [[short_t['grab'], 'gun_pickup'], [short_t['cock'], 'hammer_cock'], [short_t['inhale'], 'inhale_tense']],
            'click': [[0, 'dry_fire'], [TIMING['click']['exhale'], 'exhale_relief'], [TIMING['click']['table'], 'gun_table']],
            'bang': [[0, 'gunshot']],
        },
    }
    write_json(os.path.join('cinematic', 'roulette.json'), data)
    print('roulette.json   pull load=%d full=%d short=%d, fall side=%d table=%d' % (
        load_t['pull'], full_t['pull'], short_t['pull'], side_fall, table_hit))


if __name__ == '__main__':
    idle_calm()
    idle_tense()
    roulette('load')
    roulette('full')
    roulette('short')
    after_click()
    liar_call()
    liar_flinch()
    card_throw()
    deal_shuffle()
    deal_toss()
    deal_pickup()
    side = death_side()
    table = death_table()
    timeline(side, table)
