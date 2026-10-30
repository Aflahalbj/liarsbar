package com.liarsbar.client.render;

import com.liarsbar.client.cinematic.DealDirector;
import com.liarsbar.client.cinematic.ThrowDirector;
import com.liarsbar.client.ui.TableHud;
import com.liarsbar.table.PublicState;
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

/**
 * Kartu fisik di meja: tumpukan kartu yang dimainkan (berserakan, bukan dek rapi), kartu yang sedang
 * terbang, dek dealer, dan paket hasil bagi. Satu kartu = dua segmen sepanjang sumbu panjangnya, jadi
 * bisa ditekuk (ujung dalam diangkat jempol saat riffle, melengkung saat bridge).
 * <p>
 * Ruang lokal kartu: berbaring, pusat di titik asal, sumbu panjang lokal +Z (ujung atas gambar), lebar
 * lokal X, punggung menghadap +Y. Kedua sisi bertekstur punggung (nilai kartu orang lain tidak diketahui).
 */
public final class TableCards {

    /** Ukuran kartu fisik (blok), sama dengan kipas di tangan. */
    public static final float W = 0.14f, H = 0.21f;
    /** Tebal satu kartu di tumpukan. */
    public static final float THICK = 0.0016f;
    private static final ResourceLocation EDGE = new ResourceLocation("minecraft", "textures/block/white_concrete.png");
    private static final int EDGE_TINT = 0xFF8C8478;

    private TableCards() {
    }

    public static VertexConsumer backs(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entityCutoutNoCull(TableHud.cardTexture(null)));
    }

    /**
     * Satu kartu di titik asal pose. {@code bendNear}/{@code bendFar}: sudut (rad) segmen ujung -Z / +Z;
     * positif = ujungnya terangkat, negatif = turun (dua-duanya negatif = melengkung seperti bridge).
     */
    public static void card(VertexConsumer vc, PoseStack pose, int light, int color, float w, float h,
                            float bendNear, float bendFar) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float hw = w / 2, hh = h / 2;
        float fy = Mth.sin(bendFar) * hh, fz = Mth.cos(bendFar) * hh;
        float ny = Mth.sin(bendNear) * hh, nz = -Mth.cos(bendNear) * hh;
        Geo.vertex(vc, m, n, 0, 1, 0, light, color, -hw, fy, fz, 0, 0);
        Geo.vertex(vc, m, n, 0, 1, 0, light, color, -hw, 0, 0, 0, 0.5f);
        Geo.vertex(vc, m, n, 0, 1, 0, light, color, hw, 0, 0, 1, 0.5f);
        Geo.vertex(vc, m, n, 0, 1, 0, light, color, hw, fy, fz, 1, 0);
        Geo.vertex(vc, m, n, 0, 1, 0, light, color, -hw, 0, 0, 0, 0.5f);
        Geo.vertex(vc, m, n, 0, 1, 0, light, color, -hw, ny, nz, 0, 1);
        Geo.vertex(vc, m, n, 0, 1, 0, light, color, hw, ny, nz, 1, 1);
        Geo.vertex(vc, m, n, 0, 1, 0, light, color, hw, 0, 0, 1, 0.5f);
    }

    public static void card(VertexConsumer vc, PoseStack pose, int light) {
        card(vc, pose, light, 0xFFFFFFFF, W, H, 0, 0);
    }

    /**
     * Dek rapi {@code count} kartu, dasar di titik asal: sisi tumpukan putih gading, kartu teratas dan
     * terbawah bertekstur punggung.
     */
    public static void deck(MultiBufferSource buffers, PoseStack pose, int light, int count) {
        if (count <= 0) {
            return;
        }
        float h = count * THICK;
        if (count > 1) {
            Geo.box(buffers.getBuffer(RenderType.entitySolid(EDGE)), pose, light, EDGE_TINT, -W / 2 + 0.002f, 0.0005f,
                    -H / 2 + 0.002f, W / 2 - 0.002f, h - 0.0005f, H / 2 - 0.002f, 1f);
        }
        VertexConsumer vc = backs(buffers);
        pose.pushPose();
        card(vc, pose, light);
        pose.translate(0, h, 0);
        card(vc, pose, light);
        pose.popPose();
    }

    /** Pindahkan pose (ruang lokal meja) ke titik dunia {@code at} dengan putaran {@code q}. */
    public static void place(PoseStack pose, Vec3 origin, Vec3 at, Quaternionf q) {
        pose.translate(at.x - origin.x, at.y - origin.y, at.z - origin.z);
        pose.mulPose(q);
    }

    /** Putaran kartu berbaring dengan sumbu panjang ke arah yaw (derajat, putaran Y). */
    public static Quaternionf flat(float yawDeg) {
        return new Quaternionf().rotationY(yawDeg * Mth.DEG_TO_RAD);
    }

    /** Yaw (derajat) yang mengarahkan sumbu panjang lokal +Z ke arah horizontal (dx, dz). */
    public static float yawOf(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(dx, dz));
    }

    /**
     * Kartu dilempar ke felt: melayang lurus dengan lengkung rendah selama separuh pertama, mendarat, lalu
     * meluncur dan melambat karena gesekan felt. {@code u} 0..1.
     *
     * @return posisi dunia
     */
    public static Vec3 slide(Vec3 from, Vec3 to, float u, float arc) {
        u = Mth.clamp(u, 0, 1);
        final float air = 0.5f;
        float s, y;
        if (u < air) {
            float a = u / air;
            s = 0.74f * a;
            y = (float) Mth.lerp(a * a, from.y, to.y) + arc * 4 * a * (1 - a);
            return new Vec3(Mth.lerp(s, from.x, to.x), y, Mth.lerp(s, from.z, to.z));
        }
        float b = (u - air) / (1 - air);
        s = 0.74f + 0.26f * (1 - (1 - b) * (1 - b));
        return new Vec3(Mth.lerp(s, from.x, to.x), to.y, Mth.lerp(s, from.z, to.z));
    }

    /** Sudah mendarat (fase meluncur) di {@code u}. */
    public static boolean landed(float u) {
        return u >= 0.5f;
    }

    /** Putaran kartu terbang: dari {@code from} ke berbaring di yaw akhir, plus putaran tambahan di sumbu Y. */
    public static Quaternionf spin(Quaternionf from, float toYaw, float extraDeg, float u) {
        u = Mth.clamp(u, 0, 1);
        float e = 1 - (1 - u) * (1 - u) * (1 - u);
        float settle = Mth.clamp(u / 0.5f, 0, 1);
        settle = settle * settle * (3 - 2 * settle);
        Quaternionf q = new Quaternionf(from).slerp(flat(toYaw), settle);
        return new Quaternionf().rotationY(extraDeg * (1 - e) * Mth.DEG_TO_RAD).mul(q);
    }

    // ================================================================ dari TableRenderer

    /** Semua kartu fisik meja ini (ruang render meja: titik asal = posisi entity meja). */
    public static void render(PoseStack pose, MultiBufferSource buffers, Vec3 origin, float top, int light,
                              PublicState state, float partial) {
        ThrowDirector.render(pose, buffers, origin, top, light, state, partial);
        DealDirector.render(pose, buffers, origin, top, light, state, partial);
    }
}
