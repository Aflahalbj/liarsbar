"""Rekonstruksi kamera shot AnimDev (koordinat MC relatif pusat meja, lantai = 0)."""
import numpy as np, math
RING, TABLE_R, TABLE_Y, EYE_Y = 1.95, 1.5, 0.8, 1.29
W, H = 1920, 1080

def seat(i, n=4):
    th = math.radians(-90 + 360.0 * i / n)
    dx, dz = RING * math.cos(th), -RING * math.sin(th)
    eye = np.array([dx, EYE_Y, dz])
    fwd = np.array([-dx, 0, -dz]); fwd /= np.linalg.norm(fwd)
    right = np.array([-fwd[2], 0, fwd[0]])
    return eye, fwd, right

def devcam(s, dist, az, el, fov=50, aim=-0.35):
    eye, fwd, right = seat(s)
    az, el = math.radians(az), math.radians(el)
    target = eye + np.array([0, aim, 0])
    d = fwd * math.cos(az) + right * math.sin(az)
    pos = target + d * dist * math.cos(el) + np.array([0, dist * math.sin(el), 0])
    return Cam(pos, target, fov)

def povcam(s, yaw_off=0.0, pitch=0.0, fov=70):
    eye, fwd, right = seat(s)
    a = math.radians(yaw_off)
    f = fwd * math.cos(a) + right * math.sin(a)
    p = math.radians(pitch)
    f = f * math.cos(p) + np.array([0, -math.sin(p), 0])
    return Cam(eye, eye + f, fov)

class Cam:
    def __init__(self, pos, target, fov):
        self.pos, self.target, self.fov = np.asarray(pos, float), np.asarray(target, float), fov
        f = self.target - self.pos; f /= np.linalg.norm(f)
        r = np.cross(f, [0, 1, 0]); r /= np.linalg.norm(r)
        u = np.cross(r, f)
        self.f, self.r, self.u = f, r, u
        self.focal = (H / 2) / math.tan(math.radians(fov) / 2)
    def project(self, P):
        P = np.atleast_2d(P) - self.pos
        x, y, z = P @ self.r, P @ self.u, P @ self.f
        return np.stack([W / 2 + self.focal * x / z, H / 2 - self.focal * y / z, z], 1)
