package com.liarsbar.client.fx;

import com.liarsbar.client.ClientTable;
import com.liarsbar.client.render.FxTextures;
import com.liarsbar.client.render.Geo;
import com.liarsbar.entity.TableEntity;
import com.liarsbar.table.PublicState;
import com.liarsbar.table.SeatLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Haze noir (Art Bible): asap hanya terlihat di dalam kerucut lampu dan berkas jendela, tidak ada kabut rata.
 * Kerucut digambar sebagai dua cangkang aditif yang terangnya mengikuti sudut pandang (tepi siluet memudar),
 * jadi terbaca sebagai volume cahaya, bukan corong kaca. Debu melayang pelan di dalam kerucut yang menyala.
 */
public final class Atmosphere {

    private static final int SEGMENTS = 32;
    /** Terang asap lampu tengah (AnimDev "hazecfg"). */
    private static float coneGain = 0.32f;
    private static float dustGain = 2.5f;
    private static float shaftGain = 0.15f;

    public static void tune(float[] v) {
        coneGain = v[0];
        dustGain = v[1];
        shaftGain = v[2];
    }

    private Atmosphere() {
    }

    public static void render(TableEntity entity, PoseStack pose, MultiBufferSource buffers, PublicState state, float partial) {
        FxTextures.ensure();
        Minecraft mc = Minecraft.getInstance();
        Vec3 origin = entity.getPosition(partial);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition().subtract(origin);
        float t = ClientTable.now() + partial;
        int count = state.seats.size();
        float tableTop = SeatLayout.TABLE_HEIGHT + 0.03f;
        Quaternionf facing = mc.getEntityRenderDispatcher().cameraOrientation();
        float level = TableLights.brightness(state, partial);
        if (level >= 0.02f) {
            Vec3 pivot = TableLights.lampPos(entity.position()).subtract(entity.position()).add(0, TableLights.CORD, 0);
            Vec3 apex = TableLights.swung(pivot, TableLights.swing(partial), TableLights.CORD + 0.03f);
            float len = (float) (apex.y - tableTop);
            // Sedikit lebih sempit dari sorot supaya tepi asapnya bersih.
            float half = TableLights.spotAngle(count) - 0.08f;
            VertexConsumer haze = buffers.getBuffer(RenderType.eyes(FxTextures.HAZE));
            cone(haze, pose, cam, apex, len, half, 1.0f, coneGain * level, t * 0.004f);
            cone(haze, pose, cam, apex, len, half, 0.62f, coneGain * 0.7f * level, -t * 0.006f + 0.17f);
            dust(buffers.getBuffer(RenderType.eyes(FxTextures.DOT)), pose, facing, apex, len, half, level * dustGain, t);
        }
        if (com.liarsbar.client.render.BarRoom.active()) {
            shafts(buffers.getBuffer(RenderType.eyes(FxTextures.HAZE)), pose, cam, t);
        }
    }

    /** Satu cangkang kerucut dari puncak {@code apex} ke bawah sepanjang {@code len}, terang mengikuti sudut pandang. */
    private static void cone(VertexConsumer vc, PoseStack pose, Vec3 cam, Vec3 apex, float len, float half, float scale,
                             float gain, float scroll) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float r0 = TableLights.SHADE_RADIUS * 0.9f * scale, r1 = (TableLights.SHADE_RADIUS * 0.9f + len * (float) Math.tan(half)) * scale;
        float ax = (float) apex.x, ay = (float) apex.y, az = (float) apex.z;
        for (int k = 0; k < SEGMENTS; k++) {
            float a0 = Mth.TWO_PI * k / SEGMENTS, a1 = Mth.TWO_PI * (k + 1) / SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float u0 = (float) k / SEGMENTS * 2 + scroll, u1 = (float) (k + 1) / SEGMENTS * 2 + scroll;
            // Titik atas/bawah dua sisi segmen; terang tiap titik = seberapa tegak lurus permukaan ke mata.
            float[][] p = {
                    {ax + c0 * r0, ay, az + s0 * r0}, {ax + c1 * r0, ay, az + s1 * r0},
                    {ax + c1 * r1, ay - len, az + s1 * r1}, {ax + c0 * r1, ay - len, az + s0 * r1}};
            float[][] uv = {{u0, 0}, {u1, 0}, {u1, 1}, {u0, 1}};
            int[] col = new int[4];
            for (int j = 0; j < 4; j++) {
                float cx = j == 1 || j == 2 ? c1 : c0, sz = j == 1 || j == 2 ? s1 : s0;
                float vx = (float) (cam.x - p[j][0]), vy = (float) (cam.y - p[j][1]), vz = (float) (cam.z - p[j][2]);
                float vl = Mth.sqrt(vx * vx + vy * vy + vz * vz) + 1e-4f;
                float face = Math.abs(cx * vx + sz * vz) / vl;
                col[j] = gray(gain * face * face);
            }
            // Dua sisi (dari dalam dan luar kerucut).
            for (int j : new int[]{0, 3, 2, 1}) {
                Geo.vertex(vc, m, n, 0, 1, 0, LightTexture.FULL_BRIGHT, col[j], p[j][0], p[j][1], p[j][2], uv[j][0], uv[j][1]);
            }
            for (int j : new int[]{0, 1, 2, 3}) {
                Geo.vertex(vc, m, n, 0, 1, 0, LightTexture.FULL_BRIGHT, col[j], p[j][0], p[j][1], p[j][2], uv[j][0], uv[j][1]);
            }
        }
    }

    /** Debu yang melayang pelan di dalam kerucut, berkilau sesaat (billboard menghadap kamera). */
    private static void dust(VertexConsumer vc, PoseStack pose, Quaternionf facing, Vec3 apex, float len, float half,
                             float gain, float t) {
        if (gain <= 0.01f) {
            return;
        }
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        Vector3f right = facing.transform(new Vector3f(1, 0, 0)), up = facing.transform(new Vector3f(0, 1, 0));
        float tan = (float) Math.tan(half) * 0.85f;
        for (int k = 0; k < 120; k++) {
            int seed = k * 104729;
            float fy = rand(seed, 1), fa = rand(seed, 2) * Mth.TWO_PI, fr = Mth.sqrt(rand(seed, 3));
            float speed = 0.0012f + rand(seed, 4) * 0.0015f;
            float y = frac(fy - t * speed * 0.35f);
            float depth = 0.12f + y * (len - 0.2f);
            float r = (TableLights.SHADE_RADIUS * 0.8f + depth * tan) * fr;
            float a = fa + t * speed * 2.0f;
            float x = (float) apex.x + Mth.cos(a) * r, z = (float) apex.z + Mth.sin(a) * r;
            float py = (float) apex.y - depth + Mth.sin(t * 0.03f + k) * 0.02f;
            float twinkle = 0.5f + 0.5f * Mth.sin(t * (0.05f + 0.05f * rand(seed, 5)) + k * 1.7f);
            float near = 1 - y;
            int c = gray(gain * twinkle * (0.3f + 0.7f * near));
            float s = 0.007f + 0.006f * rand(seed, 6);
            billboard(vc, m, n, right, up, x, py, z, s, c);
        }
    }

    /** Jarak antarbilah kerai jendela (blok), sama dengan model noir_window. */
    private static final float SLAT_PITCH = 1.9f / 16f;

    /**
     * Berkas cahaya cyan dari tiap jendela barat lewat celah venetian blinds: satu pita tipis miring per celah
     * bilah (asap di dalamnya), dan garis terang di lantai tempat pita itu jatuh.
     */
    private static void shafts(VertexConsumer vc, PoseStack pose, Vec3 cam, float t) {
        if (shaftGain <= 0.001f) {
            return;
        }
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float h = com.liarsbar.client.render.BarRoom.half();
        float dx = 1.0f, dy = -0.42f;
        // Normal bidang pita (memuat arah cahaya dan sumbu z).
        float len = (float) Math.sqrt(dx * dx + dy * dy), nx = dy / len, ny = -dx / len;
        float x0 = -h + 0.05f;
        float drift = (t * 0.002f) % 1f;
        for (float[] w : com.liarsbar.client.render.BarRoom.westWindows()) {
            float za = w[0] - w[1] / 2 + 0.08f, zb = w[0] + w[1] / 2 - 0.08f;
            int count = Math.max(1, (int) ((w[3] - w[2]) / SLAT_PITCH));
            for (int k = 0; k < count; k++) {
                float gy = w[2] + (k + 0.5f) * SLAT_PITCH;
                float x1 = x0 + dx * gy / -dy;
                float g = shaftGain * 0.42f;
                int c = rgb(g * 0.36f, g * 0.78f, g);
                float u = drift + k * 0.137f;
                // Pita miring dari celah ke lantai (dua sisi), memudar menurut tekstur haze (v 0 -> 1).
                Geo.vertex(vc, m, n, nx, ny, 0, LightTexture.FULL_BRIGHT, c, x0, gy, za, u, 0.02f);
                Geo.vertex(vc, m, n, nx, ny, 0, LightTexture.FULL_BRIGHT, c, x1, 0.01f, za, u, 0.95f);
                Geo.vertex(vc, m, n, nx, ny, 0, LightTexture.FULL_BRIGHT, c, x1, 0.01f, zb, u + 0.5f, 0.95f);
                Geo.vertex(vc, m, n, nx, ny, 0, LightTexture.FULL_BRIGHT, c, x0, gy, zb, u + 0.5f, 0.02f);
                Geo.vertex(vc, m, n, -nx, -ny, 0, LightTexture.FULL_BRIGHT, c, x0, gy, zb, u + 0.5f, 0.02f);
                Geo.vertex(vc, m, n, -nx, -ny, 0, LightTexture.FULL_BRIGHT, c, x1, 0.01f, zb, u + 0.5f, 0.95f);
                Geo.vertex(vc, m, n, -nx, -ny, 0, LightTexture.FULL_BRIGHT, c, x1, 0.01f, za, u, 0.95f);
                Geo.vertex(vc, m, n, -nx, -ny, 0, LightTexture.FULL_BRIGHT, c, x0, gy, za, u, 0.02f);
                // Garis cahaya di lantai: makin jauh dari jendela makin lebar dan redup.
                float sw = 0.035f + 0.012f * (x1 - x0);
                float fl = shaftGain * 0.9f / (1 + 0.25f * (x1 - x0));
                int cf = rgb(fl * 0.36f, fl * 0.78f, fl);
                Geo.vertex(vc, m, n, 0, 1, 0, LightTexture.FULL_BRIGHT, cf, x1 - sw, 0.012f, zb, 0.5f, 0.06f);
                Geo.vertex(vc, m, n, 0, 1, 0, LightTexture.FULL_BRIGHT, cf, x1 + sw, 0.012f, zb, 0.5f, 0.06f);
                Geo.vertex(vc, m, n, 0, 1, 0, LightTexture.FULL_BRIGHT, cf, x1 + sw, 0.012f, za, 0.5f, 0.06f);
                Geo.vertex(vc, m, n, 0, 1, 0, LightTexture.FULL_BRIGHT, cf, x1 - sw, 0.012f, za, 0.5f, 0.06f);
            }
        }
    }

    private static void billboard(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f right, Vector3f up, float x,
                                  float y, float z, float s, int color) {
        int l = LightTexture.FULL_BRIGHT;
        float rx = right.x * s, ry = right.y * s, rz = right.z * s, ux = up.x * s, uy = up.y * s, uz = up.z * s;
        Geo.vertex(vc, m, n, 0, 0, 1, l, color, x - rx + ux, y - ry + uy, z - rz + uz, 0, 0);
        Geo.vertex(vc, m, n, 0, 0, 1, l, color, x - rx - ux, y - ry - uy, z - rz - uz, 0, 1);
        Geo.vertex(vc, m, n, 0, 0, 1, l, color, x + rx - ux, y + ry - uy, z + rz - uz, 1, 1);
        Geo.vertex(vc, m, n, 0, 0, 1, l, color, x + rx + ux, y + ry + uy, z + rz + uz, 1, 0);
    }

    /** Bilangan acak tetap 0..1 dari (seed, salt): hash integer. */
    private static float rand(int seed, int salt) {
        int x = seed * 0x9E3779B1 + salt * 0x85EBCA77;
        x ^= x >>> 16;
        x *= 0x7FEB352D;
        x ^= x >>> 15;
        x *= 0x846CA68B;
        x ^= x >>> 16;
        return (x >>> 8) / (float) (1 << 24);
    }

    private static float frac(float v) {
        return v - Mth.floor(v);
    }

    /** Warna hangat lampu (tungsten) dengan terang {@code k}. */
    private static int gray(float k) {
        return rgb(k, k * 0.72f, k * 0.46f);
    }

    /**
     * Warna aditif dengan alfa 0: blend ONE,ONE tetap menambah warnanya, dan di Veil (buffer compatibility
     * ditumpuk ke hasil lampu dengan alfa) alfa 0 berarti cahaya di belakangnya tidak tertutup.
     */
    private static int rgb(float r, float g, float b) {
        int ri = Mth.clamp((int) (r * 255), 0, 255), gi = Mth.clamp((int) (g * 255), 0, 255);
        int bi = Mth.clamp((int) (b * 255), 0, 255);
        return ri << 16 | gi << 8 | bi;
    }
}
