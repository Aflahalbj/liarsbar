"""Relight 2D style frame: base in-game (albedo + mask) + set & properti Blender -> frame noir.

Lampu dihitung dengan rumus yang sama dengan Cycles (spot: I = P/4pi W/sr, irradiance cos/d^2, difus /pi),
jadi kolam cahaya di meja cocok dengan bayangan properti dari Blender."""
import sys, os, json, math
import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage
import cv2
sys.path.insert(0, os.path.dirname(__file__))
from cam import *

SP = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def load(p, mode="RGB"):
    return np.asarray(Image.open(p).convert(mode), np.float32) / 255


def lin(x):
    return np.clip(x, 0, None) ** 2.2


def to_srgb(x):
    return np.clip(x, 0, 1) ** (1 / 2.2)


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def fbm(h, w, scale, seed, octaves=4):
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        s = max(2, int(scale / (2 ** o)))
        n = rng.random((h // s + 2, w // s + 2)).astype(np.float32)
        n = cv2.resize(n, (w + 2 * s, h + 2 * s), interpolation=cv2.INTER_CUBIC)[s:s + h, s:s + w]
        out += amp * n
        tot += amp
        amp *= 0.5
    return out / tot


class Relight:
    def __init__(self, name, cam, spec):
        self.name, self.cam, self.spec = name, cam, spec
        B = SP + "/base/" + name
        self.alb, self.nobar, self.bare = load(B + "_alb.png"), load(B + "_nobar.png"), load(B + "_bare.png")
        R = SP + "/render/" + spec.get("render", name)
        self.props = load(R + "_props.png", "RGBA")
        self.set = load(R + "_set.png")
        d = np.asarray(Image.open(R + "_depth_0001.png"), np.float32)
        self.set_depth = d / (65535.0 if d.max() > 255 else 255.0) * 20.0
        self.H, self.W = self.alb.shape[:2]
        self.rays()
        self.masks()

    # ---------- geometri ----------
    def rays(self):
        c = self.cam
        ys, xs = np.mgrid[0:self.H, 0:self.W].astype(np.float32)
        x = (xs + 0.5 - W / 2) / c.focal
        y = -(ys + 0.5 - H / 2) / c.focal
        d = c.f[None, None, :] + x[..., None] * c.r[None, None, :] + y[..., None] * c.u[None, None, :]
        self.zscale = np.linalg.norm(d, axis=-1)  # jarak sepanjang sinar per satuan kedalaman planar
        self.dir = d / self.zscale[..., None]

    def plane_y(self, y0):
        t = (y0 - self.cam.pos[1]) / np.where(np.abs(self.dir[..., 1]) < 1e-6, -1e-6, self.dir[..., 1])
        P = self.cam.pos + self.dir * t[..., None]
        return t, P

    def masks(self):
        diff = lambda a, b: np.abs(a - b).sum(-1) > 0.07
        bg = diff(self.alb, self.nobar)
        bg = ndimage.binary_opening(bg, iterations=1)
        prop = diff(self.nobar, self.bare) & ~bg
        prop = ndimage.binary_opening(prop, iterations=1)
        t, P = self.plane_y(TABLE_Y)
        r = np.hypot(P[..., 0], P[..., 2])
        top = (t > 0) & (r < TABLE_R + 0.02)
        a = self.alb
        wood = (a[..., 0] > a[..., 1] * 1.18) & (a[..., 0] > a[..., 2] * 1.45) & (a[..., 0] > 0.25)
        ring = top & (a.min(-1) > 0.62)  # cincin putih lama -> dihapus
        self.table = ~bg & ~prop & ((top & wood) | ring)
        self.table = ndimage.binary_closing(self.table, iterations=2) & ~bg
        self.tableP = P
        # apron (sisi meja): sinar kena silinder r=TABLE_R antara y 0.62..0.8, sebelum kena daun meja
        o, dv = self.cam.pos, self.dir
        a2 = dv[..., 0] ** 2 + dv[..., 2] ** 2
        b2 = 2 * (o[0] * dv[..., 0] + o[2] * dv[..., 2])
        c2 = o[0] ** 2 + o[2] ** 2 - TABLE_R ** 2
        disc = b2 * b2 - 4 * a2 * c2
        tc = (-b2 - np.sqrt(np.maximum(disc, 0))) / (2 * a2 + 1e-9)
        yc = o[1] + dv[..., 1] * tc
        side = (disc > 0) & (tc > 0) & (yc > 0.6) & (yc < TABLE_Y)
        self.apron = ~bg & ~prop & ~self.table & side
        self.bg, self.prop = bg, prop
        self.players = ~bg & ~prop & ~self.table & ~self.apron
        # posisi dunia kira-kira untuk pemain/kursi: bidang vertikal melalui kursi terdekat
        n = self.spec.get("seats", 4)
        heads = [self.cam.project(seat(i, n)[0] + np.array([0, -0.3, 0]))[0] for i in range(n)]
        ys, xs = np.mgrid[0:self.H, 0:self.W]
        best = np.full((self.H, self.W), 1e9, np.float32)
        Pp = np.zeros((self.H, self.W, 3), np.float32)
        self.seat_id = np.full((self.H, self.W), -1, np.int8)
        fh = self.cam.f.copy(); fh[1] = 0; fh /= np.linalg.norm(fh)
        for i, h in enumerate(heads):
            if h[2] <= 0:
                continue
            d2 = (xs - h[0]) ** 2 + (ys - h[1]) ** 2
            e = seat(i, n)[0]
            # sinar ketemu bidang vertikal tegak lurus arah kamera, melalui mata kursi i
            denom = (self.dir * fh).sum(-1)
            t = ((e - self.cam.pos) @ fh) / np.where(np.abs(denom) < 1e-6, 1e-6, denom)
            Pi = self.cam.pos + self.dir * t[..., None]
            m = d2 < best
            best[m] = d2[m]
            Pp[m] = Pi[m]
            self.seat_id[m] = i
        self.playerP = Pp
        # lampu lama (di atas 1,9 blok) dibuang -> diganti lampu baru dari Blender
        lampold = self.prop & (Pp[..., 1] > 1.9)
        self.bg |= lampold
        self.prop &= ~lampold
        # hapus bagian base yang diganti paintover (poligon), dan pemain yang sudah mati (kursi kosong)
        from PIL import ImageDraw
        if self.spec.get("erase_poly"):
            im = Image.new("L", (self.W, self.H), 0)
            dr = ImageDraw.Draw(im)
            for poly in self.spec["erase_poly"]:
                dr.polygon([tuple(p) for p in poly], fill=255)
            er = (np.asarray(im) > 0) & self.players
            self.players &= ~er
            self.bg |= er
        dead = self.spec.get("dead", [])
        if dead:
            dm = self.players & np.isin(self.seat_id, dead)
            dm = cv2.dilate(dm.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(bool) & ~self.prop & ~self.table
            self.players &= ~dm
            self.table |= dm & top
            self.apron |= dm & side & ~top
            self.bg |= dm & ~top & ~side
        # inpaint cincin putih di albedo
        ringm = (ring & ~bg).astype(np.uint8) * 255
        ringm = cv2.dilate(ringm, np.ones((5, 5), np.uint8))
        a8 = (self.alb * 255).astype(np.uint8)
        self.alb = cv2.inpaint(a8, ringm, 9, cv2.INPAINT_TELEA).astype(np.float32) / 255

    # ---------- cahaya ----------
    def spot(self, P, L, N=None, size=90, blend=0.6):
        Lp = np.array(L["pos"], np.float32)
        v = Lp - P
        d2 = np.maximum((v * v).sum(-1), 1e-4)
        d = np.sqrt(d2)
        cosang = v[..., 1] / d  # sumbu spot ke bawah
        half = math.radians(L.get("size", size) / 2)
        cone = smoothstep(math.cos(half), math.cos(half * (1 - L.get("blend", blend))), cosang)
        inten = L["energy"] / (4 * math.pi)
        cosi = np.clip((v / d[..., None] * N).sum(-1), 0, 1) if N is not None else 0.75
        return inten * cone * cosi / d2 / math.pi

    def area(self, P, A, N=None):
        Ap = np.array(A["pos"], np.float32)
        At = np.array(A["target"], np.float32)
        nrm = (At - Ap) / np.linalg.norm(At - Ap)
        v = Ap - P
        d2 = np.maximum((v * v).sum(-1), 0.05)
        d = np.sqrt(d2)
        cose = np.clip(-(v / d[..., None] * nrm).sum(-1), 0, 1)
        cosi = np.clip((v / d[..., None] * N).sum(-1), 0, 1) if N is not None else 0.6
        return A["energy"] / math.pi * cose * cosi / d2 / math.pi * 0.25

    def light_surface(self, P, N):
        warm = np.zeros(P.shape[:2], np.float32)
        for L in self.spec["lamps"]:
            warm += self.spot(P, L, N)
        cyan = np.zeros(P.shape[:2], np.float32)
        for A in self.spec["areas"]:
            cyan += self.area(P, A, N)
        return warm, cyan

    def run(self):
        S = self.spec
        WARM = np.array(S.get("warm", (1.0, 0.60, 0.28)), np.float32)
        CYAN = np.array(S.get("cyan", (0.35, 0.78, 1.0)), np.float32)
        AMB = np.array(S.get("ambient", (0.010, 0.014, 0.020)), np.float32)
        alb = lin(self.alb)
        H_, W_ = self.H, self.W
        # daun meja baru: felt hijau tua + bibir kayu walnut (tekstur asli dipakai sebagai variasi halus)
        r = np.hypot(self.tableP[..., 0], self.tableP[..., 2])
        base_l = (alb * [0.3, 0.6, 0.1]).sum(-1)
        var = base_l / (np.median(base_l[self.table]) + 1e-4)
        P = self.tableP
        nz = fbm(H_, W_, 3, 77, 2)
        felt = np.array(S.get("felt", (0.018, 0.052, 0.040)), np.float32)[None, None, :] * (0.85 + 0.3 * nz[..., None])
        walnut = np.array((0.060, 0.030, 0.016), np.float32)[None, None, :] * np.clip(var, 0.6, 1.4)[..., None]
        top = self.table & (r < TABLE_R - 0.17)
        rimw = self.table & ~top
        alb = np.where(top[..., None], felt, alb)
        alb = np.where(rimw[..., None], walnut, alb)
        alb = np.where(self.apron[..., None], walnut * 0.7, alb)
        light = np.zeros((H_, W_, 3), np.float32)
        # meja: normal ke atas
        up = np.array([0, 1, 0], np.float32)
        w, c = self.light_surface(self.tableP, up)
        m = self.table
        light[m] = (w[..., None] * WARM + c[..., None] * CYAN)[m]
        # sisi meja: gelap, hanya pantulan
        light[self.apron] = (0.25 * w[..., None] * WARM)[self.apron] * 0.3
        # pemain & kursi: shading MC sudah ada di albedo; normal ~ ke kamera + atas
        nview = -self.dir * 0.5 + up * 0.8
        nview /= np.linalg.norm(nview, axis=-1, keepdims=True)
        w2, c2 = self.light_surface(self.playerP, nview)
        pm = self.players | self.prop
        light[pm] = (w2[..., None] * WARM * 1.15 + c2[..., None] * CYAN * 0.5)[pm]
        # isi pantulan meja ke wajah pemain aktif (wajah selalu kebaca)
        act = S.get("active")
        if act is not None:
            e = seat(act, S.get("seats", 4))[0]
            near = np.exp(-((self.playerP - e) ** 2).sum(-1) / 0.35)
            light[self.players] += (near[..., None] * WARM * S.get("bounce", 0.22))[self.players]
        if act is not None and S.get("active_gain"):
            am = self.players & (self.seat_id == act)
            light[am] *= S["active_gain"]
        for sid in S.get("silhouette", []):
            sm = (self.players | self.prop) & (self.seat_id == sid)
            light[sm] *= S.get("silhouette_gain", 0.1)
        light += AMB
        fg = alb * light
        # rim cyan dari jendela (screen-space): tepi siluet yang menghadap jendela
        rim = self.rim(self.players, CYAN)
        fg += rim * (alb * 2.2 + 0.02) * S.get("rim", 1.0)
        # background: set Blender, digelapkan ala noir
        setl = lin(self.set) * S.get("set_gain", 0.55)
        out = np.where(self.bg[..., None], setl, fg)
        # properti Blender (RGBA, sRGB)
        pa = self.props[..., 3:4]
        pc = lin(self.props[..., :3]) * S.get("props_gain", 1.0)
        if S.get("props_clip"):
            pc = np.minimum(pc, S["props_clip"])
        out = out * (1 - pa) + pc * pa
        if S.get("rim_props"):
            pm2 = (pa[..., 0] > 0.5)
            out += self.rim(pm2, CYAN) * (pc * 2.5 + 0.03) * S["rim_props"]
        depth = self.depth_map(pa[..., 0])
        out += self.volumetric(depth) * S.get("haze", 1.0)
        out += self.dust(depth)
        out = self.post(out)
        return out

    def rim(self, pm, CYAN):
        S = self.spec
        if S.get("no_rim_poly"):
            from PIL import ImageDraw
            im = Image.new("L", (self.W, self.H), 0)
            dr = ImageDraw.Draw(im)
            for poly in S["no_rim_poly"]:
                dr.polygon([tuple(p) for p in poly], fill=255)
            pm = pm & ~(np.asarray(im) > 0)
        win = np.array(S.get("rim_from", (-4.5, 2.1, 0.0)), np.float32)
        q = self.cam.project(win)[0]
        m = ndimage.binary_opening(pm, iterations=1).astype(np.float32)
        sm = cv2.GaussianBlur(m, (0, 0), 3)
        gy, gx = np.gradient(sm)
        ys, xs = np.mgrid[0:self.H, 0:self.W].astype(np.float32)
        tx, ty = q[0] - xs, q[1] - ys
        if q[2] < 0:
            tx, ty = -tx, -ty
        tn = np.sqrt(tx * tx + ty * ty) + 1e-3
        facing = np.clip(-(gx * tx + gy * ty) / (np.sqrt(gx * gx + gy * gy) + 1e-6) / tn, 0, 1)
        edge = m * (1 - cv2.GaussianBlur(m, (0, 0), S.get("rim_w", 2.2)))
        edge = np.clip(edge * 2.5, 0, 1)
        # rim cuma di tepi yang berbatasan dengan latar jauh (bukan dengan meja/kursi)
        far = cv2.dilate(self.bg.astype(np.uint8), np.ones((7, 7), np.uint8)).astype(np.float32)
        edge *= far
        r = edge * (S.get("rim_base", 0.35) + (1 - S.get("rim_base", 0.35)) * facing ** 1.5)
        return r[..., None] * CYAN * S.get("rim_gain", 0.55)

    def depth_map(self, pa):
        d = np.full((self.H, self.W), 9.0, np.float32)
        t, _ = self.plane_y(TABLE_Y)
        d = np.where(self.table | self.apron, t, d)
        tp = ((self.playerP - self.cam.pos) * self.dir).sum(-1)
        d = np.where(self.players | self.prop, tp, d)
        sd = self.set_depth * self.zscale
        d = np.where(self.bg, sd, d)
        d = np.where(pa > 0.5, np.minimum(d, np.where(self.table, t, 2.0)), d)
        return np.clip(d, 0.05, 12)

    def volumetric(self, depth, steps=56, scale=4):
        S = self.spec
        h, w = self.H // scale, self.W // scale
        dirs = cv2.resize(self.dir, (w, h), interpolation=cv2.INTER_AREA)
        dep = cv2.resize(depth, (w, h), interpolation=cv2.INTER_NEAREST)
        acc_w = np.zeros((h, w), np.float32)
        acc_c = np.zeros((h, w), np.float32)
        smoke = fbm(h, w, 40, 5) * 0.7 + fbm(h, w, 12, 9) * 0.3
        smoke = 0.02 + 1.9 * smoke ** 2.4
        win = S.get("window_shaft")
        for k in range(steps):
            tk = (k + 0.5) / steps * np.minimum(dep, 9.0)
            dt = np.minimum(dep, 9.0) / steps
            P = self.cam.pos + dirs * tk[..., None]
            for L in S["lamps"]:
                Lp = np.array(L["pos"], np.float32)
                v = Lp - P
                d2 = np.maximum((v * v).sum(-1), 0.02)
                cosang = v[..., 1] / np.sqrt(d2)
                half = math.radians(L.get("size", 90) / 2)
                half = math.radians(S.get("haze_cone", 50) / 2)
                cone = smoothstep(math.cos(half), math.cos(half * 0.6), cosang)
                acc_w += L["energy"] / (4 * math.pi) * cone / d2 * dt
            if win:
                D = np.array(win["dir"], np.float32)
                D /= np.linalg.norm(D)
                s = (P[..., 0] - win["x"]) / -D[0]
                Q = P - D * s[..., None]
                ok = s > 0
                yy, zz = Q[..., 1], Q[..., 2]
                hit = np.zeros_like(ok)
                for (y0, y1, z0, z1) in win["panes"]:
                    hit |= (yy > y0) & (yy < y1) & (zz > z0) & (zz < z1)
                for bz in win.get("mullions_z", []):
                    hit &= np.abs(zz - bz) > 0.05
                for by in win.get("mullions_y", []):
                    hit &= np.abs(yy - by) > 0.05
                acc_c += (ok & hit).astype(np.float32) * win["energy"] * dt
        g = S.get("haze_density", 0.022)
        vw = acc_w * smoke * g
        vc = acc_c * (0.5 + 0.8 * smoke) * g
        vol = vw[..., None] * np.array(S.get("warm", (1.0, 0.60, 0.28))) + vc[..., None] * np.array(S.get("cyan", (0.35, 0.78, 1.0)))
        return cv2.resize(vol.astype(np.float32), (self.W, self.H), interpolation=cv2.INTER_CUBIC)

    def dust(self, depth, n=160):
        S = self.spec
        rng = np.random.default_rng(21)
        img = np.zeros((self.H, self.W), np.float32)
        for L in S["lamps"]:
            Lp = np.array(L["pos"], np.float32)
            for _ in range(int(n * L["energy"] / 120)):
                r = rng.uniform(0, 1.0)
                a = rng.uniform(0, 6.283)
                y = rng.uniform(0.95, Lp[1] - 0.25)
                rad = (Lp[1] - y) * math.tan(math.radians(L.get("size", 90) / 2)) * 0.8
                P = Lp + np.array([rad * r * math.cos(a), y - Lp[1], rad * r * math.sin(a)])
                q = self.cam.project(P)[0]
                x, yy, z = q
                if z < 0.3 or not (0 <= x < self.W and 0 <= yy < self.H):
                    continue
                if depth[int(yy), int(x)] < z * 0.98:
                    continue
                d2 = ((Lp - P) ** 2).sum()
                img[int(yy), int(x)] += L["energy"] / d2 * 0.0025 * rng.uniform(0.2, 1.0) * S.get("dust", 1.0)
        img = cv2.GaussianBlur(img, (0, 0), 0.7) * 2.2
        return img[..., None] * np.array([1.0, 0.85, 0.6])

    def post(self, x):
        S = self.spec
        # bloom
        hi = np.clip(x - S.get("bloom_th", 0.7), 0, None)
        bloom = sum(cv2.GaussianBlur(hi, (0, 0), s) * wgt for s, wgt in ((6, 0.5), (18, 0.35), (48, 0.3)))
        x = x + bloom * S.get("bloom", 0.6)
        # tonemap halus (bahu filmik) + exposure
        x = x * S.get("exposure", 1.0)
        x = x / (1 + x * 0.35) * 1.18
        # black point + split tone: bayangan sedikit cyan, highlight hangat
        lum = (x * [0.2126, 0.7152, 0.0722]).sum(-1, keepdims=True)
        shadow = np.clip(1 - lum * 6, 0, 1)
        x = x + shadow * np.array([-0.002, 0.001, 0.004])
        x = np.clip(x - S.get("black", 0.004), 0, None)
        out = to_srgb(x)
        # aberasi kromatik radial (denyut jantung roulette)
        ca = S.get("ca", 0.0)
        if ca:
            ys, xs = np.mgrid[0:self.H, 0:self.W].astype(np.float32)
            cx, cy = self.W / 2, self.H / 2
            for ch, k in ((0, ca), (2, -ca)):
                sx = (cx + (xs - cx) * (1 + k)).astype(np.float32)
                sy = (cy + (ys - cy) * (1 + k)).astype(np.float32)
                out[..., ch] = cv2.remap(out[..., ch].astype(np.float32), sx, sy, cv2.INTER_LINEAR)
        # S-curve: hitam makin pekat, highlight terbuka
        k = S.get("contrast", 1.35)
        sc = out ** k / (out ** k + (1 - out) ** k + 1e-6)
        out = out * (1 - S.get("scurve", 0.6)) + sc * S.get("scurve", 0.6)
        # vignette
        ys, xs = np.mgrid[0:self.H, 0:self.W].astype(np.float32)
        r = np.sqrt(((xs - self.W / 2) / (self.W / 2)) ** 2 + ((ys - self.H / 2) / (self.H / 2)) ** 2)
        out *= (1 - S.get("vignette", 0.35) * smoothstep(0.55, 1.45, r))[..., None]
        # grain (luma)
        rng = np.random.default_rng(1)
        g = rng.normal(0, 1, (self.H, self.W)).astype(np.float32)
        g = cv2.GaussianBlur(g, (0, 0), 0.7)
        out += (g * S.get("grain", 0.022))[..., None] * (0.4 + 0.6 * (1 - out))
        lb = S.get("letterbox")
        if lb:
            bar = int((self.H - self.W / lb) / 2)
            out[:bar] = 0
            out[self.H - bar:] = 0
        return np.clip(out, 0, 1)


def debug_masks(r, path):
    v = np.zeros((r.H, r.W, 3), np.float32)
    v[r.bg] = (0.1, 0.1, 0.4)
    v[r.table] = (0.6, 0.3, 0.1)
    v[r.apron] = (0.3, 0.15, 0.05)
    v[r.players] = (0.2, 0.7, 0.3)
    v[r.prop] = (0.7, 0.7, 0.2)
    Image.fromarray((v * 255).astype(np.uint8)).save(path)


if __name__ == "__main__":
    name = sys.argv[1]
    spec = json.load(open(SP + f"/render/{name}_relight.json"))
    extra = SP + f"/render/{name}_extra.json"
    if os.path.exists(extra):
        spec.update(json.load(open(extra)))
    c = Cam(spec["cam"]["pos"], spec["cam"]["target"], spec["cam"]["fov"])
    r = Relight(name, c, spec)
    if "masks" in sys.argv:
        debug_masks(r, SP + f"/render/{name}_masks.png")
    out = r.run()
    Image.fromarray((out * 255).astype(np.uint8)).save(SP + f"/render/{name}_lit.png")
    print("ok", name)
