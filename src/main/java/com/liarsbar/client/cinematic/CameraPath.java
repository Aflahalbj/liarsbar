package com.liarsbar.client.cinematic;

import net.minecraft.world.phys.Vec3;

/** Jalur kamera: Catmull-Rom pada kunci berwaktu (tidak harus berjarak sama), dan noise genggam. */
public final class CameraPath {

    private CameraPath() {
    }

    public static Vec3 sample(float[] ticks, Vec3[] points, float t) {
        if (points.length == 1 || t <= ticks[0]) {
            return points[0];
        }
        int n = points.length;
        if (t >= ticks[n - 1]) {
            return points[n - 1];
        }
        int i = 0;
        while (i < n - 2 && t > ticks[i + 1]) {
            i++;
        }
        float u = (t - ticks[i]) / Math.max(1e-4f, ticks[i + 1] - ticks[i]);
        u = u * u * (3 - 2 * u) * 0.35f + u * 0.65f;
        Vec3 p0 = points[Math.max(0, i - 1)];
        Vec3 p1 = points[i];
        Vec3 p2 = points[i + 1];
        Vec3 p3 = points[Math.min(n - 1, i + 2)];
        return catmull(p0, p1, p2, p3, u);
    }

    public static float sampleFloat(float[] ticks, float[] values, float t) {
        if (values.length == 1 || t <= ticks[0]) {
            return values[0];
        }
        int n = values.length;
        if (t >= ticks[n - 1]) {
            return values[n - 1];
        }
        int i = 0;
        while (i < n - 2 && t > ticks[i + 1]) {
            i++;
        }
        float u = (t - ticks[i]) / Math.max(1e-4f, ticks[i + 1] - ticks[i]);
        u = u * u * (3 - 2 * u);
        return values[i] + (values[i + 1] - values[i]) * u;
    }

    private static Vec3 catmull(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double u) {
        double u2 = u * u;
        double u3 = u2 * u;
        return new Vec3(
                0.5 * (2 * p1.x + (-p0.x + p2.x) * u + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * u2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * u3),
                0.5 * (2 * p1.y + (-p0.y + p2.y) * u + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * u2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * u3),
                0.5 * (2 * p1.z + (-p0.z + p2.z) * u + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * u2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * u3));
    }

    /** Gerak halus pseudo-acak, kira-kira -1..1. */
    public static double noise(double t, double seed) {
        return 0.6 * Math.sin(t * 1.7 + seed) + 0.3 * Math.sin(t * 3.1 + seed * 2.3) + 0.1 * Math.sin(t * 7.3 + seed * 4.1);
    }

    public static float yawOf(Vec3 dir) {
        return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
    }

    public static float pitchOf(Vec3 dir) {
        return (float) Math.toDegrees(-Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
    }

    /** Selisih sudut terpendek b - a (derajat), menjaga yaw kontinu di batas -180/180. */
    public static float angleDelta(float a, float b) {
        float d = (b - a) % 360f;
        if (d > 180) {
            d -= 360;
        }
        if (d < -180) {
            d += 360;
        }
        return d;
    }
}
