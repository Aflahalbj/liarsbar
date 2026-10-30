package com.liarsbar.client.render;

import com.liarsbar.LiarsBar;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Satu peluru .38 di luar revolver (berdiri di felt, di jari saat diisi), ukuran dan tekstur sama dengan bone
 * "bullet" di model revolver (tools/make_revolver.py): selongsong kuningan, kepala timah bulat, pelek dengan
 * primer. Prisma segi delapan supaya siluetnya bulat di insert dekat.
 * <p>
 * Ruang lokal: sumbu peluru +Y (dasar di y = 0, kepala ke atas).
 */
public final class BulletRenderer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(LiarsBar.MOD_ID, "textures/item/revolver.png");
    /** Satu piksel model revolver di tangan (RevolverRenderer.HAND skala 0,52). */
    public static final float PX = 0.52f / 16f;
    public static final float CASE = 3.0f * PX, NOSE = 0.6f * PX, RADIUS = 0.33f * PX;
    public static final float RIM = 0.4f * PX, RIM_T = 0.12f * PX;
    public static final float LENGTH = CASE + NOSE + RIM_T;
    private static final int SIDES = 8;
    // Daerah tekstur (piksel 64x64), sama dengan MAT di make_revolver.py.
    private static final float[] CASE_UV = {48, 32, 8, 8}, LEAD_UV = {56, 32, 8, 8}, HEAD_UV = {48, 40, 8, 8};

    private BulletRenderer() {
    }

    /** Peluru dengan dasar di {@code base} (dunia), sumbu ke {@code axis}, di ruang render relatif {@code cam}. */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, Vec3 cam, Vec3 base, Vec3 axis,
                              float spin) {
        pose.pushPose();
        pose.translate(base.x - cam.x, base.y - cam.y, base.z - cam.z);
        Vector3f a = new Vector3f((float) axis.x, (float) axis.y, (float) axis.z).normalize();
        pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, a.x, a.y, a.z).rotateY(spin));
        draw(pose, buffers, light);
        pose.popPose();
    }

    /** Peluru di titik asal pose sekarang (lokal: dasar di y = 0, kepala +Y). */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        // Pelek: lebih lebar, setipis kertas; pantatnya bergambar primer.
        prism(vc, m, n, light, 0, RIM_T, CASE_UV, RIM, RIM);
        cap(vc, m, n, light, RIM, 0, -1, HEAD_UV);
        cap(vc, m, n, light, RIM, RIM_T, 1, CASE_UV);
        // Selongsong.
        prism(vc, m, n, light, RIM_T, RIM_T + CASE, CASE_UV, RADIUS, RADIUS);
        // Kepala timah: membulat (dua ruas mengecil) sampai ujung tumpul.
        float y0 = RIM_T + CASE;
        prism(vc, m, n, light, y0, y0 + NOSE * 0.55f, LEAD_UV, RADIUS * 0.95f, RADIUS * 0.82f);
        prism(vc, m, n, light, y0 + NOSE * 0.55f, y0 + NOSE, LEAD_UV, RADIUS * 0.82f, RADIUS * 0.45f);
        cap(vc, m, n, light, RADIUS * 0.45f, y0 + NOSE, 1, LEAD_UV);
    }

    /** Sisi prisma dari jari-jari {@code r0} di y0 ke {@code r1} di y1 (kerucut terpotong). */
    private static void prism(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, float y0, float y1,
                              float[] uv, float r0, float r1) {
        for (int i = 0; i < SIDES; i++) {
            float a0 = Mth.TWO_PI * i / SIDES, a1 = Mth.TWO_PI * (i + 1) / SIDES, am = (a0 + a1) / 2;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float nx = Mth.cos(am), nz = Mth.sin(am);
            // Kolom tekstur: pantulan memanjang ikut sisi (sisi 0..7 -> u 0..1 dari daerah bahan).
            float u0 = uv[0] + uv[2] * i / SIDES, u1 = uv[0] + uv[2] * (i + 1) / SIDES;
            float v0 = uv[1], v1 = uv[1] + uv[3];
            Geo.vertex(vc, m, n, nx, 0, nz, light, 0xFFFFFFFF, c0 * r1, y1, s0 * r1, u0 / 64, v0 / 64);
            Geo.vertex(vc, m, n, nx, 0, nz, light, 0xFFFFFFFF, c0 * r0, y0, s0 * r0, u0 / 64, v1 / 64);
            Geo.vertex(vc, m, n, nx, 0, nz, light, 0xFFFFFFFF, c1 * r0, y0, s1 * r0, u1 / 64, v1 / 64);
            Geo.vertex(vc, m, n, nx, 0, nz, light, 0xFFFFFFFF, c1 * r1, y1, s1 * r1, u1 / 64, v0 / 64);
        }
    }

    /** Tutup segi delapan di y (normal ke {@code dir} +1/-1), sebagai kipas quad dari pusat. */
    private static void cap(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, float r, float y, int dir, float[] uv) {
        float cu = uv[0] + uv[2] / 2, cv = uv[1] + uv[3] / 2;
        for (int i = 0; i < SIDES; i += 2) {
            float[] xs = new float[3], zs = new float[3];
            for (int k = 0; k < 3; k++) {
                float a = Mth.TWO_PI * (i + k) / SIDES;
                xs[k] = Mth.cos(a) * r;
                zs[k] = Mth.sin(a) * r;
            }
            // Quad: pusat, tepi i, i+1, i+2 (urutan dibalik untuk tutup bawah supaya menghadap keluar).
            float[][] q = {{0, 0}, {xs[0], zs[0]}, {xs[1], zs[1]}, {xs[2], zs[2]}};
            for (int k = 0; k < 4; k++) {
                float[] c = q[dir > 0 ? 3 - k : k];
                float u = cu + c[0] / r * uv[2] / 2, v = cv + c[1] / r * uv[3] / 2;
                Geo.vertex(vc, m, n, 0, dir, 0, light, 0xFFFFFFFF, c[0], y, c[1], u / 64, v / 64);
            }
        }
    }
}
