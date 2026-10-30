package com.liarsbar.client.anim;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Tangan NPC yang memegang properti, lewat IK (seperti {@link GripLayer} untuk revolver): sasaran dihitung di dunia
 * dari geometri yang benar-benar dirender, bukan dari sudut tebakan.
 * <ul>
 *   <li>npc_drink: gelas diangkat tegak ke depan dagu, lalu bibir gelas ditempelkan ke mulut (titik mulut dari kepala
 *   model yang dirender) dengan gelas dimiringkan ke wajah.</li>
 *   <li>npc_pour: gelas di tangan kiri depan dada; ujung leher botol di tangan kanan dibawa tepat di atas bibir gelas
 *   itu lalu dimiringkan sampai leher menghadap ke bawah.</li>
 * </ul>
 * Kurva waktunya sama dengan tools/npc_anims.py (window), jadi IK masuk dan keluar bersama animasi di bawahnya.
 * Properti digambar {@code NpcPropLayer} di kepalan yang dirender, dengan arah dari sini.
 */
public final class NpcRig implements IAnimation {

    /** Setengah tinggi gelas, jarak tengah kepalan ke tengah gelas/botol, tengah botol ke ujung lehernya (blok). */
    /** Skala model item gelas/botol (lihat make_block_models.npc_items). */
    public static final float PROP_SCALE = 0.62f;
    public static final float GLASS_HALF = 5.5f / 32f * PROP_SCALE, BOTTLE_TIP = 6f / 16f * PROP_SCALE;
    /**
     * Kepalan memegang dari samping-bawah-belakang (tidak menutup gelas/botol dari depan): tengah kepalan digeser
     * dari tengah properti ke samping luar, ke arah dasar properti, dan sedikit ke belakang.
     */
    public static final float GRIP_SIDE = 0.06f, GRIP_LOW = 0.09f, GRIP_BACK = 0.02f;
    /** Mulut di ruang kepala model (piksel): tengah, 2 px di atas dagu, sedikit di depan muka. */
    private static final float MOUTH_Y = -2f / 16f, MOUTH_Z = -4.2f / 16f;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    final NpcPuppet body;
    /** Animasi NPC saat ini dan tick dunia saat dimulai (diisi NpcPuppets). */
    String anim;
    long start;
    /** Panjang loop animasi (tick), 0 = tidak berulang. */
    int loop;

    private boolean framed;
    private final Matrix4f toLocal = new Matrix4f();
    private final Vector3f rightPivot = new Vector3f(-5 / 16f, 2 / 16f, 0);
    private final Vector3f leftPivot = new Vector3f(5 / 16f, 2 / 16f, 0);
    private final ArmIk ik = new ArmIk();
    private final float[] right = new float[4];
    private final float[] left = new float[4];
    private float rightW;
    private float leftW;
    private final Quaternionf qa = new Quaternionf();
    private final Quaternionf qb = new Quaternionf();
    private final Matrix3f m = new Matrix3f();

    /** Mulut di dunia (render terakhir). */
    Vec3 mouth;
    /** Arah gelas (tangan kanan pengunjung) dan botol (tangan kanan bartender) di dunia. */
    final Quaternionf glassRot = new Quaternionf();
    final Quaternionf bottleRot = new Quaternionf();
    /** Ukuran hasil render terakhir untuk AnimDev "npcprobe": jarak bibir gelas-mulut, ujung botol-bibir gelas. */
    float probeMouth = Float.NaN, probePour = Float.NaN;

    NpcRig(NpcPuppet body) {
        this.body = body;
    }

    public com.liarsbar.entity.BarNpcEntity npc() {
        return body.npc;
    }

    public String anim() {
        return anim;
    }

    /** Tick sejak animasi saat ini dimulai. */
    public float age() {
        return tick(0);
    }

    public Vec3 mouth() {
        return mouth;
    }

    public Quaternionf glassRot() {
        return glassRot;
    }

    public Quaternionf bottleRot() {
        return bottleRot;
    }

    public void probe(float mouthDist, float pourDist) {
        probeMouth = mouthDist;
        probePour = pourDist;
    }

    /** Dari NpcPropLayer: ruang induk lengan -> dunia, poros bahu, dan titik mulut dari kepala yang dirender. */
    public void capture(Matrix4f parentToWorld, PlayerModel<?> model) {
        toLocal.set(parentToWorld).invert();
        rightPivot.set(model.rightArm.x / 16f, model.rightArm.y / 16f, model.rightArm.z / 16f);
        leftPivot.set(model.leftArm.x / 16f, model.leftArm.y / 16f, model.leftArm.z / 16f);
        Vector3f p = new Matrix4f(parentToWorld)
                .translate(model.head.x / 16f, model.head.y / 16f, model.head.z / 16f)
                .rotate(new Quaternionf().rotationZYX(model.head.zRot, model.head.yRot, model.head.xRot))
                .transformPosition(0, MOUTH_Y, MOUTH_Z, new Vector3f());
        mouth = new Vec3(p.x, p.y, p.z);
        framed = true;
    }

    void play(String name, long startTick, int loopLength) {
        anim = name;
        start = startTick;
        loop = loopLength;
    }

    float tick(float partial) {
        float t = body.level().getGameTime() - start + partial;
        return loop > 0 ? t % loop : t;
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** 0 -> 1 -> 0: naik di [a, a+ramp], tahan, turun di [b-ramp, b] (sama dengan npc_anims.window). */
    static float window(float t, float a, float b, float ramp) {
        return smooth((t - a) / ramp) * smooth((b - t) / ramp);
    }

    /** Depan dan kanan badan (datar) di dunia. */
    public Vec3 forward(float partial) {
        return Vec3.directionFromRotation(0, Mth.rotLerp(partial, body.yBodyRotO, body.yBodyRot));
    }

    static Vec3 rightOf(Vec3 fwd) {
        return new Vec3(-fwd.z, 0, fwd.x);
    }

    /** Ujung leher botol saat dituang: arah leher dari sudut miring (0 = tegak, ke arah tangan kiri). */
    static Vec3 neck(Vec3 right, float degrees) {
        float a = degrees * Mth.DEG_TO_RAD;
        return UP.scale(Mth.cos(a)).add(right.scale(-Mth.sin(a)));
    }

    /** Titik tengah gelas di tangan kiri bartender saat menuang (depan dada, sedikit ke kiri). */
    Vec3 pourGlass(float partial) {
        Vec3 fwd = forward(partial);
        return body.getPosition(partial).add(0, 0.98, 0).add(fwd.scale(0.42)).subtract(rightOf(fwd).scale(0.04));
    }

    /**
     * Titik tengah kepalan yang memegang properti bertengah {@code center} dengan sumbu tegak {@code up}; side +1 =
     * tangan kanan (kepalan di kanan properti), -1 = kiri.
     */
    public static Vec3 grip(Vec3 center, Vec3 up, Vec3 right, Vec3 fwd, int side) {
        return center.add(right.scale(GRIP_SIDE * side)).subtract(up.scale(GRIP_LOW)).subtract(fwd.scale(GRIP_BACK));
    }

    /** Kebalikan {@link #grip}: tengah properti dari tengah kepalan yang dirender. */
    public static Vec3 held(Vec3 fist, Vec3 up, Vec3 right, Vec3 fwd, int side) {
        return fist.subtract(right.scale(GRIP_SIDE * side)).add(up.scale(GRIP_LOW)).add(fwd.scale(GRIP_BACK));
    }

    /** Seberapa miring botol saat ini (0..1, 1 = leher ke bawah, minuman mengalir). */
    public float pourTilt() {
        return "npc_pour".equals(anim) ? window(tick(0), 44, 124, 16) : 0;
    }

    @Override
    public boolean isActive() {
        return framed && ("npc_drink".equals(anim) || "npc_pour".equals(anim));
    }

    @Override
    public void setupAnim(float partial) {
        rightW = 0;
        leftW = 0;
        glassRot.identity();
        bottleRot.identity();
        if (!framed) {
            return;
        }
        float t = tick(partial);
        Vec3 fwd = forward(partial);
        Vec3 r = rightOf(fwd);
        Vec3 down = new Vec3(0, -1, 0);
        if ("npc_drink".equals(anim) && mouth != null) {
            float lift = window(t, 70, 160, 22), sip = window(t, 98, 138, 12);
            if (lift <= 0) {
                return;
            }
            // Gelas tegak di depan dagu, lalu dimiringkan 55 derajat ke wajah dengan bibirnya di mulut.
            float a = 55 * Mth.DEG_TO_RAD * sip;
            Vec3 up = UP.scale(Mth.cos(a)).subtract(fwd.scale(Mth.sin(a)));
            Vec3 chin = mouth.add(fwd.scale(0.17)).add(0, -0.17, 0);
            Vec3 sipCenter = mouth.subtract(up.scale(GLASS_HALF));
            Vec3 center = chin.lerp(sipCenter, sip);
            glassRot.rotationTo(0, 1, 0, (float) up.x, (float) up.y, (float) up.z);
            Vec3 fist = grip(center, up, r, fwd, 1);
            ik.solve(toLocal, rightPivot, GripLayer.RIGHT_FIST_X, fist, down.add(r.scale(0.7)).subtract(fwd.scale(0.3)), right);
            rightW = lift;
        } else if ("npc_pour".equals(anim)) {
            float over = window(t, 10, 150, 22), tilt = window(t, 44, 124, 16);
            if (over <= 0) {
                return;
            }
            Vec3 glass = pourGlass(partial);
            Vec3 rim = glass.add(0, GLASS_HALF, 0);
            ik.solve(toLocal, leftPivot, GripLayer.LEFT_FIST_X, grip(glass, UP, r, fwd, -1),
                    down.subtract(r.scale(0.7)).subtract(fwd.scale(0.3)), left);
            leftW = over;
            // Botol: mula-mula agak tegak di atas-kanan gelas, lalu ujung leher tepat di atas bibir gelas, leher ke bawah.
            Vec3 n = neck(r, 25 + 100 * tilt);
            Vec3 tip = rim.add(0, 0.04 + 0.16 * (1 - tilt), 0).add(r.scale(0.14 * (1 - tilt)));
            Vec3 center = tip.subtract(n.scale(BOTTLE_TIP));
            bottleRot.rotationTo(0, 1, 0, (float) n.x, (float) n.y, (float) n.z);
            ik.solve(toLocal, rightPivot, GripLayer.RIGHT_FIST_X, grip(center, n, r, fwd, 1),
                    down.add(r.scale(0.7)).subtract(fwd.scale(0.3)), right);
            rightW = over;
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
        qa.rotationZYX(value0.getZ(), value0.getY(), value0.getX());
        qb.rotationZYX(arm[2], arm[1], arm[0]);
        qa.slerp(qb, w);
        m.set(qa);
        return new Vec3f((float) Math.atan2(m.m12, m.m22), (float) Math.asin(Mth.clamp(-m.m02, -1, 1)),
                (float) Math.atan2(m.m01, m.m00));
    }
}
