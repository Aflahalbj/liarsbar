"""Animasi NPC bar (format Emotecraft v3 untuk Player Animator), memakai util tools/table_anims.py.

    python tools/npc_anims.py          -> tulis ke src/main/resources/assets/liarsbar/player_animation/npc_*.json

Pengunjung duduk (seluruh badan): npc_drink, npc_talk, npc_laugh. Berdiri (badan atas saja, kaki vanilla):
npc_wipe, npc_fetch (ambil botol di rak), npc_pour (bartender). Semua loop mulus, dilewatkan filter pegas (tanpa patahan).
Konvensi rig sama dengan table_anims.py.
"""
import math
import os

from table_anims import (ANGLE_AXES, AXES, DEFAULTS, PARTS, Pose, add, bake, breathe, clamp01, edit, full, noise,
                         seamless, sit, smooth, write_json)

UPPER = ('head', 'torso', 'rightArm', 'leftArm', 'rightItem')


def compile_parts(name, frames, parts=PARTS, loop_from=0):
    """Seperti table_anims.compile_anim, tapi hanya bagian {@code parts} yang dinyalakan (sisanya vanilla)."""
    total = len(frames) - 1
    moves = []
    for t, pose in enumerate(frames):
        for part in parts:
            axes = AXES.get(part)
            if not axes:
                continue
            values = {}
            for axis in axes:
                v = pose[part][axis]
                if axis in ('x', 'y', 'z'):
                    v = DEFAULTS[part]['xyz'.index(axis)] + v
                values[axis] = round(v, 3)
            moves.append({'tick': t, 'easing': 'LINEAR', 'turn': 0, part: values})
    data = {
        'version': 3, 'name': name, 'author': "Liar's Table", 'sounds': '',
        'emote': {'isLoop': True, 'returnTick': loop_from, 'beginTick': 0, 'endTick': total, 'stopTick': total + 1,
                  'degrees': True, 'easeBeforeKeyframe': True, 'moves': moves},
    }
    text = write_json(os.path.join('player_animation', name + '.json'), data)
    print('%-12s %4d tick  %-38s %6.1f KB' % (name, total, ','.join(parts), len(text) / 1024))


def window(t, a, b, ramp=10.0):
    """0 -> 1 -> 0: naik di [a, a+ramp], tahan, turun di [b-ramp, b]."""
    return smooth(clamp01((t - a) / ramp)) * smooth(clamp01((b - t) / ramp))


# ================================================================ pengunjung duduk

def patron():
    """Duduk santai: punggung sedikit membungkuk, lengan bertumpu di meja/counter, kepala lurus ke depan."""
    return edit(sit(), torso=dict(bend=7), head=dict(pitch=2),
                rightArm=dict(pitch=-44, yaw=-14, roll=4, bend=-62, z=-0.4),
                leftArm=dict(pitch=-42, yaw=16, roll=-4, bend=-52, z=-0.4))


def drink():
    """Napas pelan, angkat gelas ke mulut, teguk (kepala mendongak), turunkan, ketuk gelas di meja."""
    base = patron()
    length = 240

    def raw(t):
        p = base.copy()
        breathe(p, t, 80.0, 1.0)
        # Hasil kalibrasi in-game (AnimDev npcanim/npccam, dicek depan + samping): angkat = lengan atas ~50 derajat
        # ke depan, siku 90, gelas tegak di depan dagu; teguk = lengan condong lebih ke tengah, siku 100, gelas
        # dimiringkan ke mulut. Kepala hanya sedikit mendongak (mendongak jauh menjauhkan mulut dari gelas).
        lift = window(t, 70, 160, 22)
        add(p, 'rightArm', pitch=-6 * lift, yaw=-31 * lift, roll=-4 * lift, bend=-28 * lift)
        add(p, 'rightItem', pitch=40 * lift)
        sip = window(t, 98, 138, 12)
        add(p, 'rightArm', pitch=-2 * sip, yaw=-25 * sip, bend=-10 * sip)
        add(p, 'rightItem', pitch=-30 * sip)
        add(p, 'head', pitch=-4 * sip)
        # Melirik ke meja judi sebentar.
        glance = window(t, 190, 226, 10)
        add(p, 'head', yaw=-28 * glance, pitch=6 * glance)
        return p
    compile_parts('npc_drink', seamless(bake(raw, length), 0, 14))


def talk():
    """Bercerita: tangan kiri menggambar di udara, kepala mengangguk dan menoleh, badan condong ke lawan bicara."""
    base = patron()
    length = 240

    def raw(t):
        p = base.copy()
        breathe(p, t, 64.0, 1.1)
        g = window(t, 20, 110, 14)
        wave = math.sin(2 * math.pi * t / 16.0)
        add(p, 'leftArm', pitch=-26 * g + 7 * wave * g, yaw=10 * g + 6 * math.sin(2 * math.pi * t / 23.0) * g,
            bend=-28 * g)
        add(p, 'head', pitch=3 * math.sin(2 * math.pi * t / 28.0), yaw=14 * g)
        add(p, 'torso', pitch=-3 * g)
        # Mendengarkan: mengangguk pelan, tangan kanan memutar gelas.
        listen = window(t, 120, 230, 16)
        add(p, 'head', pitch=4 * math.sin(2 * math.pi * t / 34.0) * listen, yaw=-10 * listen)
        add(p, 'rightArm', yaw=3 * math.sin(2 * math.pi * t / 12.0) * listen)
        return p
    compile_parts('npc_talk', seamless(bake(raw, length), 0, 14))


def laugh():
    """Tertawa: rebah ke belakang, bahu berguncang, lalu menepuk meja dan kembali."""
    base = patron()
    length = 240

    def raw(t):
        p = base.copy()
        breathe(p, t, 72.0, 1.0)
        l = window(t, 50, 130, 14)
        shake = math.sin(2 * math.pi * t / 7.0) * l
        add(p, 'body', pitch=7 * l)
        add(p, 'torso', bend=-5 * l, pitch=1.5 * shake)
        add(p, 'head', pitch=-16 * l + 2.5 * shake)
        add(p, 'rightArm', pitch=4 * shake)
        add(p, 'leftArm', pitch=-14 * l + 4 * shake)
        slap = window(t, 132, 150, 4)
        add(p, 'leftArm', pitch=-12 * slap, bend=-10 * slap)
        add(p, 'torso', pitch=-4 * slap)
        return p
    compile_parts('npc_laugh', seamless(bake(raw, length), 0, 14))


# ================================================================ berdiri

def stand():
    return full(Pose({}))


def wipe():
    """Bartender mengelap counter: condong, tangan kanan memutar lap, tangan kiri bertumpu; sesekali melirik."""
    base = edit(stand(), torso=dict(bend=20), head=dict(pitch=24),
                rightArm=dict(pitch=-58, yaw=-6, bend=-30), leftArm=dict(pitch=-44, yaw=18, roll=-4, bend=-34))
    length = 192

    def raw(t):
        p = base.copy()
        breathe(p, t, 64.0, 0.8)
        w = 2 * math.pi * t / 24.0
        add(p, 'rightArm', yaw=14 * math.sin(w), pitch=7 * math.cos(w), bend=4 * math.cos(w))
        add(p, 'torso', yaw=2 * math.sin(w))
        look = window(t, 120, 170, 12)
        add(p, 'head', pitch=-22 * look, yaw=18 * look)
        return p
    compile_parts('npc_wipe', seamless(bake(raw, length), 0, 16), parts=UPPER)


# Lengan kanan memegang botol di depan dada (akhir npc_fetch, awal/akhir npc_pour).
BOTTLE_CHEST = dict(pitch=-48, yaw=-10, roll=0, bend=-62)
# Tick tangan menyentuh botol di rak (NpcPuppet.GRAB): botol muncul di tangan.
GRAB = 30


def fetch():
    """Bartender berbalik ke rak (badan diputar server), meraih botol di rak tengah, membawanya ke dada."""
    base = edit(stand(), torso=dict(bend=4), head=dict(pitch=6), rightArm=dict(pitch=-8, yaw=-4, bend=-12),
                leftArm=dict(pitch=-6, yaw=4, bend=-10))
    length = 64

    def raw(t):
        p = base.copy()
        breathe(p, t, 60.0, 0.8)
        reach = smooth(clamp01((t - 12) / (GRAB - 12)))
        back = smooth(clamp01((t - (GRAB + 6)) / 18.0))
        # Raih: lengan lurus ke depan-atas ke rak setinggi bahu-kepala, badan sedikit tegak, kepala mendongak.
        add(p, 'rightArm', pitch=-112 * reach * (1 - back), yaw=-2 * reach, bend=-6 * reach * (1 - back))
        add(p, 'torso', bend=-6 * reach * (1 - back))
        add(p, 'head', pitch=-18 * reach * (1 - back))
        # Turunkan botol ke depan dada.
        for axis, v in BOTTLE_CHEST.items():
            p['rightArm'][axis] += (v - base['rightArm'][axis]) * back
        add(p, 'head', pitch=14 * back)
        return p
    compile_parts('npc_fetch', bake(raw, length), parts=UPPER, loop_from=length)


def pour():
    """Bartender menuang: gelas di tangan kiri depan perut, botol diangkat ke atas gelas, dimiringkan, ditegakkan."""
    base = edit(stand(), torso=dict(bend=10), head=dict(pitch=30),
                leftArm=dict(pitch=-40, yaw=24, roll=-2, bend=-52), rightArm=BOTTLE_CHEST)
    length = 180

    def raw(t):
        p = base.copy()
        breathe(p, t, 70.0, 0.8)
        # Hasil kalibrasi in-game: lengan kanan menyilang ke gelas di tangan kiri; botol dimiringkan dengan pitch
        # item (+ = leher botol turun ke arah gelas; yaw hanya memutar botol di tempat).
        over = window(t, 10, 150, 22)
        add(p, 'rightArm', pitch=-27 * over, yaw=-35 * over, roll=-6 * over, bend=32 * over)
        tilt = window(t, 44, 124, 16)
        add(p, 'rightItem', pitch=70 * tilt)
        add(p, 'leftArm', pitch=-3 * tilt)
        return p
    compile_parts('npc_pour', seamless(bake(raw, length), 0, 16), parts=UPPER)


if __name__ == '__main__':
    drink()
    talk()
    laugh()
    wipe()
    fetch()
    pour()
