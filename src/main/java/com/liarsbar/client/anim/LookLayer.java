package com.liarsbar.client.anim;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.util.Mth;

/**
 * Lapisan di atas animasi duduk: menoleh (kepala + puntiran badan) ke arah yang
 * sedang jadi pusat perhatian, dan tersentak saat ada tembakan. Hanya
 * menambah rotasi kepala/badan atas; bagian lain diteruskan apa adanya.
 * Semua gerak mengikuti target lewat pegas teredam (tanpa patahan).
 */
final class LookLayer implements IAnimation {

    /** Batas putar kepala (rad); sisanya ditanggung puntiran badan atas. */
    private static final float HEAD_YAW = 1.15f;
    private static final float TORSO_YAW = 0.42f;

    private float yaw, pitch, torso, flinch;
    private float prevYaw, prevPitch, prevTorso, prevFlinch;
    private float vYaw, vPitch, vTorso;
    private float targetYaw, targetPitch, targetTorso;
    /** Arah sentakan: +1 = menjauh ke kiri karakter, -1 = ke kanan. */
    private float flinchSide = 1;
    private float flinchV;

    /** Sasaran: menoleh {@code yawLeft} rad ke kiri karakter, menunduk {@code pitchDown} rad, dengan bobot. */
    void target(float yawLeft, float pitchDown, float weight) {
        float headYaw = Mth.clamp(yawLeft, -HEAD_YAW, HEAD_YAW);
        float rest = yawLeft - headYaw;
        targetTorso = Mth.clamp(rest + yawLeft * 0.18f, -TORSO_YAW, TORSO_YAW) * weight;
        targetYaw = (headYaw - targetTorso * 0.5f) * weight;
        targetPitch = Mth.clamp(pitchDown, -0.5f, 0.5f) * weight;
    }

    void kick(float strength, float side) {
        flinchV += strength * 0.55f;
        flinchSide = side;
    }

    @Override
    public void tick() {
        prevYaw = yaw;
        prevPitch = pitch;
        prevTorso = torso;
        prevFlinch = flinch;
        // Pegas teredam kritis per tick (kepala lebih lincah dari badan).
        float[] y = spring(yaw, vYaw, targetYaw, 6.0f);
        yaw = y[0];
        vYaw = y[1];
        float[] p = spring(pitch, vPitch, targetPitch, 6.0f);
        pitch = p[0];
        vPitch = p[1];
        float[] t = spring(torso, vTorso, targetTorso, 4.0f);
        torso = t[0];
        vTorso = t[1];
        // Sentakan: naik cepat lalu meluruh.
        flinch += flinchV;
        flinchV *= 0.35f;
        flinch *= 0.86f;
    }

    private static float[] spring(float x, float v, float goal, float omega) {
        float dt = 0.05f;
        for (int i = 0; i < 4; i++) {
            float h = dt / 4;
            float a = omega * omega * (goal - x) - 2 * omega * v;
            v += a * h;
            x += v * h;
        }
        return new float[]{x, v};
    }

    @Override
    public boolean isActive() {
        return Math.abs(yaw) + Math.abs(pitch) + Math.abs(torso) + Math.abs(flinch)
                + Math.abs(targetYaw) + Math.abs(targetPitch) + Math.abs(targetTorso) > 1e-4f;
    }

    @Override
    public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
        if (type != TransformType.ROTATION) {
            return value0;
        }
        float f = Mth.lerp(tickDelta, prevFlinch, flinch);
        if (modelName.equals("head")) {
            float yw = Mth.lerp(tickDelta, prevYaw, yaw) + f * 0.32f * flinchSide;
            float pt = Mth.lerp(tickDelta, prevPitch, pitch) - f * 0.22f;
            return new Vec3f(value0.getX() + pt, value0.getY() + yw, value0.getZ() + f * 0.12f * flinchSide);
        }
        if (modelName.equals("torso")) {
            float tw = Mth.lerp(tickDelta, prevTorso, torso) + f * 0.12f * flinchSide;
            return new Vec3f(value0.getX() - f * 0.1f, value0.getY() + tw, value0.getZ());
        }
        return value0;
    }

    @Override
    public void setupAnim(float tickDelta) {
    }
}
