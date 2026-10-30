package com.liarsbar.client.anim;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * IK dua ruas untuk lengan pemain (bahu -> siku -> kepalan), memakai model
 * tekuk yang sama dengan Player Animator/bendy-lib: lengan berporos di bahu,
 * siku di 0,25 (satuan model) di bawah poros, lengan bawah diputar di sumbu X
 * lokal (sudut negatif = lengan bawah ke depan). Semua hitungan di ruang
 * induk lengan (ruang model yang sudah termasuk badan/torso), jadi hasilnya
 * langsung berupa xRot/yRot/zRot bagian lengan dan sudut tekuk.
 */
public final class ArmIk {

    /** Jarak poros bahu ke siku dan siku ke tengah kepalan (satuan model). */
    private static final float UPPER = 0.25f;
    private static final float LOWER = 0.25f;
    /** Tekukan siku paling dalam. */
    private static final float MAX_BEND = 2.5f;

    private final Vector3f t = new Vector3f();
    private final Vector3f p = new Vector3f();
    private final Vector3f a1 = new Vector3f();
    private final Vector3f a2 = new Vector3f();
    private final Vector3f a3 = new Vector3f();
    private final Vector3f b2 = new Vector3f();
    private final Vector3f b3 = new Vector3f();

    /**
     * @param toLocal  dunia -> ruang induk lengan
     * @param pivot    poros bahu di ruang induk (satuan model)
     * @param fistX    geser samping tengah kepalan di ruang lengan (kanan -1/16, kiri +1/16)
     * @param target   titik dunia yang harus dicapai tengah kepalan
     * @param pole     arah dunia ke mana siku menunjuk
     * @param out      {xRot, yRot, zRot, tekuk} (radian)
     * @return seberapa jauh sasaran di luar jangkauan (0 = tercapai; satuan model)
     */
    public float solve(Matrix4f toLocal, Vector3f pivot, float fistX, Vec3 target, Vec3 pole, float[] out) {
        toLocal.transformPosition((float) target.x, (float) target.y, (float) target.z, t).sub(pivot);
        toLocal.transformDirection((float) pole.x, (float) pole.y, (float) pole.z, p);
        float d = t.length();
        if (d < 1e-4f) {
            return 0;
        }
        // |kepalan|^2 = x^2 + U^2 + L^2 + 2UL cos(tekuk) -> sudut tekuk dari jarak sasaran.
        float c = (d * d - fistX * fistX - UPPER * UPPER - LOWER * LOWER) / (2 * UPPER * LOWER);
        float reach = (float) Math.sqrt(fistX * fistX + (UPPER + LOWER) * (UPPER + LOWER));
        c = Mth.clamp(c, Mth.cos(MAX_BEND), 1);
        float bend = -(float) Math.acos(c);
        // Kepalan di ruang lengan, dan arah siku (+Z lokal, karena lengan bawah menekuk ke -Z).
        a1.set(fistX, UPPER + LOWER * Mth.cos(bend), LOWER * Mth.sin(bend)).normalize();
        a2.set(0, 0, 1).sub(a1.x * a1.z, a1.y * a1.z, a1.z * a1.z).normalize();
        a1.cross(a2, a3);
        t.normalize();
        b2.set(p).sub(t.x * t.dot(p), t.y * t.dot(p), t.z * t.dot(p));
        if (b2.lengthSquared() < 1e-6f) {
            // Sasaran tepat searah pole: pakai belakang badan sebagai cadangan.
            b2.set(0, 0, 1).sub(t.x * t.z, t.y * t.z, t.z * t.z);
        }
        b2.normalize();
        t.cross(b2, b3);
        // R = B * A^T, lalu urai ke Euler ZYX seperti ModelPart (R = Rz * Ry * Rx).
        float r00 = t.x * a1.x + b2.x * a2.x + b3.x * a3.x;
        float r10 = t.y * a1.x + b2.y * a2.x + b3.y * a3.x;
        float r20 = t.z * a1.x + b2.z * a2.x + b3.z * a3.x;
        float r21 = t.z * a1.y + b2.z * a2.y + b3.z * a3.y;
        float r22 = t.z * a1.z + b2.z * a2.z + b3.z * a3.z;
        out[0] = (float) Math.atan2(r21, r22);
        out[1] = (float) Math.asin(Mth.clamp(-r20, -1, 1));
        out[2] = (float) Math.atan2(r10, r00);
        out[3] = bend;
        return Math.max(0, d - reach);
    }

    /**
     * Tengah kepalan di ruang induk lengan untuk rotasi dan tekukan tertentu (sama dengan cara
     * Player Animator menaruh item di tangan yang ditekuk).
     */
    public static Vector3f fist(Vector3f pivot, float xRot, float yRot, float zRot, float bendAxis, float bend,
                                float fistX, Vector3f dest) {
        float ax = Mth.cos(-bendAxis), az = Mth.sin(-bendAxis);
        // Lengan bawah (dari siku) diputar di sumbu (ax, 0, az) sebesar tekukan.
        Vector3f lower = new Vector3f(fistX, LOWER, 0).rotateAxis(bend, ax, 0, az);
        dest.set(lower.x, UPPER + lower.y, lower.z);
        dest.rotateX(xRot).rotateY(yRot).rotateZ(zRot);
        return dest.add(pivot);
    }
}
