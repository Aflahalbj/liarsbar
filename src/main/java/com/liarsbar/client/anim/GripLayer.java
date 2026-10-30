package com.liarsbar.client.anim;

import com.liarsbar.client.cinematic.RouletteDirector;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Lapisan teratas: lengan yang memegang revolver diarahkan lewat IK ke titik
 * genggam dari {@link RouletteDirector#grip} (meja -> dada -> pelipis), tangan
 * kiri ke silinder saat dibuka dan diputar. Dibaurkan dengan animasi di
 * bawahnya lewat bobot dari sutradara, jadi masuk/keluarnya halus.
 * <p>
 * Kerangka induk lengan (ruang model sesudah badan/torso) direkam tiap frame
 * oleh {@code GunLayer} saat pemain digambar dan dipakai frame berikutnya;
 * pemain duduk diam, jadi selisih satu frame tidak terlihat. Revolver sendiri
 * selalu digambar di kepalan yang benar-benar dirender.
 */
public final class GripLayer implements IAnimation {

    public static final float RIGHT_FIST_X = -1 / 16f;
    public static final float LEFT_FIST_X = 1 / 16f;

    int seat = -1;
    private boolean framed;
    private final Matrix4f toLocal = new Matrix4f();
    private final Vector3f rightPivot = new Vector3f(-5 / 16f, 2 / 16f, 0);
    private final Vector3f leftPivot = new Vector3f(5 / 16f, 2 / 16f, 0);
    private final ArmIk ik = new ArmIk();
    private final float[] right = new float[4];
    private final float[] left = new float[4];
    private float rightW;
    private float leftW;
    /** Seberapa jauh sasaran tangan kanan di luar jangkauan (satuan model), untuk AnimDev. */
    private float rightShort;

    private final Quaternionf qa = new Quaternionf();
    private final Quaternionf qb = new Quaternionf();
    private final Matrix3f m = new Matrix3f();

    /** Dari GunLayer: ruang induk lengan -> dunia, dan poros bahu saat ini. */
    void capture(Matrix4f parentToWorld, ModelPart rightArm, ModelPart leftArm) {
        toLocal.set(parentToWorld).invert();
        rightPivot.set(rightArm.x / 16f, rightArm.y / 16f, rightArm.z / 16f);
        leftPivot.set(leftArm.x / 16f, leftArm.y / 16f, leftArm.z / 16f);
        framed = true;
    }

    float rightShort() {
        return rightShort;
    }

    @Override
    public boolean isActive() {
        return seat >= 0 && framed;
    }

    @Override
    public void setupAnim(float tickDelta) {
        rightW = 0;
        leftW = 0;
        RouletteDirector.Grip g = seat >= 0 && framed ? RouletteDirector.grip(seat, tickDelta) : null;
        if (g == null) {
            return;
        }
        if (g.rightWeight() > 0) {
            rightShort = ik.solve(toLocal, rightPivot, RIGHT_FIST_X, g.right(), g.rightPole(), right);
            rightW = g.rightWeight();
        }
        if (g.leftWeight() > 0) {
            ik.solve(toLocal, leftPivot, LEFT_FIST_X, g.left(), g.leftPole(), left);
            leftW = g.leftWeight();
        }
    }

    @Override
    public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
        float[] arm;
        float w;
        if (modelName.equals("rightArm")) {
            arm = right;
            w = rightW;
        } else if (modelName.equals("leftArm")) {
            arm = left;
            w = leftW;
        } else {
            return value0;
        }
        if (w <= 0) {
            return value0;
        }
        if (type == TransformType.BEND) {
            return new Vec3f(Mth.lerp(w, value0.getX(), 0), Mth.lerp(w, value0.getY(), arm[3]), value0.getZ());
        }
        if (type != TransformType.ROTATION) {
            return value0;
        }
        if (w >= 1) {
            return new Vec3f(arm[0], arm[1], arm[2]);
        }
        // Baur lewat quaternion supaya tidak berputar lewat jalur Euler yang aneh.
        qa.rotationZYX(value0.getZ(), value0.getY(), value0.getX());
        qb.rotationZYX(arm[2], arm[1], arm[0]);
        qa.slerp(qb, w);
        m.set(qa);
        return new Vec3f((float) Math.atan2(m.m12, m.m22), (float) Math.asin(Mth.clamp(-m.m02, -1, 1)),
                (float) Math.atan2(m.m01, m.m00));
    }
}
