package com.liarsbar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Kotak bertekstur skin pemain (64x64) dengan tata letak UV model Minecraft. */
public final class SkinBox {

    private SkinBox() {
    }

    /**
     * Lengan kiri (UV 32,48; slim 3 piksel) dari y0 (siku, bawah) ke y1 (tangan, atas): tekstur dibalik
     * vertikal supaya ujung tangan (bagian bawah tekstur) ada di atas.
     */
    public static void arm(VertexConsumer vc, PoseStack pose, int light, int color, boolean slim, float x0, float y0,
                           float z0, float x1, float y1, float z1) {
        arm(vc, pose, light, color, slim, x0, y0, z0, x1, y1, z1, 12);
    }

    /** Sama dengan {@link #arm}, tapi sisinya hanya memakai {@code rows} baris tekstur dari ujung tangan (jempol, jari). */
    public static void arm(VertexConsumer vc, PoseStack pose, int light, int color, boolean slim, float x0, float y0,
                           float z0, float x1, float y1, float z1, float rows) {
        float w = slim ? 3 : 4, d = 4, u = 32;
        // Sisi memetakan v+d .. v+d+h; dengan rows < 12 pangkalnya digeser ke ujung tangan.
        float h = rows, v = 48 + 12 - rows;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        // depan (+z): u+d .. u+d+w ; belakang (-z): u+d+w+d .. ; kanan (+x): u+d+w ; kiri (-x): u
        face(vc, m, n, 0, 0, 1, light, color, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1,
                u + d, v + d + h, u + d + w, v + d);
        face(vc, m, n, 0, 0, -1, light, color, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0,
                u + d + w + d, v + d + h, u + d + w + d + w, v + d);
        face(vc, m, n, 1, 0, 0, light, color, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1,
                u + d + w, v + d + h, u + d + w + d, v + d);
        face(vc, m, n, -1, 0, 0, light, color, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0,
                u, v + d + h, u + d, v + d);
        // Tutup ujung tangan: sel bawah lengan (baris 48..52), tidak ikut digeser.
        face(vc, m, n, 0, 1, 0, light, color, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1,
                u + d + w, 48, u + d + w + w, 48 + d);
    }

    /** Quad a,b,c,d (berlawanan jarum jam dari depan); UV (ua,va) di a..b atas, (ub,vb) di bawah. */
    private static void face(VertexConsumer vc, Matrix4f m, Matrix3f n, float nx, float ny, float nz, int light,
                             int color, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
                             float cz, float dx, float dy, float dz, float ua, float va, float ub, float vb) {
        Geo.vertex(vc, m, n, nx, ny, nz, light, color, ax, ay, az, ua / 64f, va / 64f);
        Geo.vertex(vc, m, n, nx, ny, nz, light, color, dx, dy, dz, ua / 64f, vb / 64f);
        Geo.vertex(vc, m, n, nx, ny, nz, light, color, cx, cy, cz, ub / 64f, vb / 64f);
        Geo.vertex(vc, m, n, nx, ny, nz, light, color, bx, by, bz, ub / 64f, va / 64f);
    }
}
