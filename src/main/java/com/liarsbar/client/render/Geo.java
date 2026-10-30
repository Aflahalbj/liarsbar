package com.liarsbar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Bantuan menggambar geometri sederhana (kotak, quad) langsung ke vertex consumer entity. */
public final class Geo {

    private Geo() {
    }

    public static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float nx, float ny, float nz, int light,
                              int color, float x, float y, float z, float u, float v) {
        vc.vertex(m, x, y, z)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(n, nx, ny, nz)
                .endVertex();
    }

    /** Kotak sejajar sumbu; tekstur dipetakan per sisi dengan skala {@code uvScale} per blok. */
    public static void box(VertexConsumer vc, PoseStack pose, int light, int color, float x0, float y0, float z0,
                           float x1, float y1, float z1, float uvScale) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float sx = (x1 - x0) * uvScale, sy = (y1 - y0) * uvScale, sz = (z1 - z0) * uvScale;
        // Sisi atas sedikit lebih terang, bawah lebih gelap (bentuk tetap terbaca di cahaya rata).
        int up = shade(color, 1.0f), down = shade(color, 0.55f), side = shade(color, 0.82f);
        face(vc, m, n, 0, 1, 0, light, up, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, sx, sz);
        face(vc, m, n, 0, -1, 0, light, down, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, sx, sz);
        face(vc, m, n, 0, 0, -1, light, side, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, sx, sy);
        face(vc, m, n, 0, 0, 1, light, side, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, sx, sy);
        face(vc, m, n, -1, 0, 0, light, side, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, sz, sy);
        face(vc, m, n, 1, 0, 0, light, side, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, sz, sy);
    }

    private static void face(VertexConsumer vc, Matrix4f m, Matrix3f n, float nx, float ny, float nz, int light,
                             int color, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
                             float cz, float dx, float dy, float dz, float us, float vs) {
        vertex(vc, m, n, nx, ny, nz, light, color, ax, ay, az, 0, 0);
        vertex(vc, m, n, nx, ny, nz, light, color, bx, by, bz, us, 0);
        vertex(vc, m, n, nx, ny, nz, light, color, cx, cy, cz, us, vs);
        vertex(vc, m, n, nx, ny, nz, light, color, dx, dy, dz, 0, vs);
    }

    /** Quad datar menghadap ke atas di y, dari (x0,z0) ke (x1,z1), tekstur penuh 0..1. */
    public static void flat(VertexConsumer vc, PoseStack pose, int light, int color, float x0, float z0, float x1,
                            float z1, float y) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        vertex(vc, m, n, 0, 1, 0, light, color, x0, y, z0, 0, 0);
        vertex(vc, m, n, 0, 1, 0, light, color, x0, y, z1, 0, 1);
        vertex(vc, m, n, 0, 1, 0, light, color, x1, y, z1, 1, 1);
        vertex(vc, m, n, 0, 1, 0, light, color, x1, y, z0, 1, 0);
    }

    /**
     * Quad bertekstur sprite atlas, diulang per {@code tile} blok (atlas tidak bisa di-wrap, jadi dipecah
     * per sel). Titik (s,t) = a + (b-a)s + (d-a)t; normal menghadap keluar dari urutan a,b,c,d berlawanan
     * jarum jam dilihat dari depan.
     */
    public static void tiled(VertexConsumer vc, Matrix4f m, Matrix3f n, net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
                             int light, int color, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz, float dx, float dy, float dz,
                             float tile) {
        float lu = (float) Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        float lv = (float) Math.sqrt((dx - ax) * (dx - ax) + (dy - ay) * (dy - ay) + (dz - az) * (dz - az));
        if (lu < 1e-4f || lv < 1e-4f) {
            return;
        }
        int cu = (int) Math.ceil(lu / tile - 1e-4f), cv = (int) Math.ceil(lv / tile - 1e-4f);
        for (int i = 0; i < cu; i++) {
            float s0 = i * tile, s1 = Math.min(lu, (i + 1) * tile);
            for (int j = 0; j < cv; j++) {
                float t0 = j * tile, t1 = Math.min(lv, (j + 1) * tile);
                float u0 = sprite.getU(0), u1 = sprite.getU((s1 - s0) / tile * 16);
                float v0 = sprite.getV(0), v1 = sprite.getV((t1 - t0) / tile * 16);
                float fs0 = s0 / lu, fs1 = s1 / lu, ft0 = t0 / lv, ft1 = t1 / lv;
                corner(vc, m, n, nx, ny, nz, light, color, ax, ay, az, bx, by, bz, dx, dy, dz, fs0, ft0, u0, v0);
                corner(vc, m, n, nx, ny, nz, light, color, ax, ay, az, bx, by, bz, dx, dy, dz, fs1, ft0, u1, v0);
                corner(vc, m, n, nx, ny, nz, light, color, ax, ay, az, bx, by, bz, dx, dy, dz, fs1, ft1, u1, v1);
                corner(vc, m, n, nx, ny, nz, light, color, ax, ay, az, bx, by, bz, dx, dy, dz, fs0, ft1, u0, v1);
            }
        }
    }

    private static void corner(VertexConsumer vc, Matrix4f m, Matrix3f n, float nx, float ny, float nz, int light,
                               int color, float ax, float ay, float az, float bx, float by, float bz, float dx, float dy,
                               float dz, float s, float t, float u, float v) {
        vertex(vc, m, n, nx, ny, nz, light, color, ax + (bx - ax) * s + (dx - ax) * t, ay + (by - ay) * s + (dy - ay) * t,
                az + (bz - az) * s + (dz - az) * t, u, v);
    }

    /** Kotak sejajar sumbu bertekstur sprite atlas, diulang per {@code tile} blok. Tanpa shading bawaan (lampu Veil). */
    public static void tiledBox(VertexConsumer vc, PoseStack pose, net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
                                int light, int color, float x0, float y0, float z0, float x1, float y1, float z1, float tile) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        // atas, bawah, utara(-z), selatan(+z), barat(-x), timur(+x); a->b = U, a->d = V, U x V = normal
        tiled(vc, m, n, sprite, light, color, 0, 1, 0, x0, y1, z1, x1, y1, z1, x0, y1, z0, tile);
        tiled(vc, m, n, sprite, light, color, 0, -1, 0, x0, y0, z0, x1, y0, z0, x0, y0, z1, tile);
        tiled(vc, m, n, sprite, light, color, 0, 0, -1, x0, y1, z0, x1, y1, z0, x0, y0, z0, tile);
        tiled(vc, m, n, sprite, light, color, 0, 0, 1, x1, y1, z1, x0, y1, z1, x1, y0, z1, tile);
        tiled(vc, m, n, sprite, light, color, -1, 0, 0, x0, y1, z1, x0, y1, z0, x0, y0, z1, tile);
        tiled(vc, m, n, sprite, light, color, 1, 0, 0, x1, y1, z0, x1, y1, z1, x1, y0, z0, tile);
    }

    public static int shade(int argb, float k) {
        int a = argb >>> 24;
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * k));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * k));
        int b = Math.min(255, (int) ((argb & 0xFF) * k));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
