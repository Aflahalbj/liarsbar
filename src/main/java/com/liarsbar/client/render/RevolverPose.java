package com.liarsbar.client.render;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Keadaan mekanik revolver yang sedang digambar (diisi sebelum render, dibaca
 * {@link RevolverRenderer}). Satu objek per pemanggil; render terjadi di satu
 * thread jadi cukup disimpan statis selama satu panggilan render.
 *
 * @param cylinder sudut silinder (derajat, kelipatan 60 = chamber sejajar laras)
 * @param open     crane terbuka 0..1
 * @param cock     hammer dikokang 0..1
 * @param trigger  pelatuk ditekan 0..1
 * @param bullet   peluru di chamber atas: negatif = tidak terlihat, 0 = duduk penuh, 1 = baru menyentuh mulut
 *                 chamber (seluruh panjangnya masih di luar)
 * @param world    arah revolver di ruang dunia (null = ikut tangan/konteks)
 */
public record RevolverPose(float cylinder, float open, float cock, float trigger, float bullet, Quaternionf world,
                           Vec3 at) {

    public static final RevolverPose REST = new RevolverPose(0, 0, 0, 0);

    public RevolverPose(float cylinder, float open, float cock, float trigger) {
        this(cylinder, open, cock, trigger, -1, null, null);
    }

    public RevolverPose(float cylinder, float open, float cock, float trigger, float bullet) {
        this(cylinder, open, cock, trigger, bullet, null, null);
    }

    /** Sama, dengan arah hadap di ruang dunia (posisi tetap ikut tangan). */
    public RevolverPose facing(Quaternionf orientation) {
        return new RevolverPose(cylinder, open, cock, trigger, bullet, orientation, at);
    }

    /** Sama, dengan posisi dan arah penuh di ruang dunia (tidak bergantung pada transform tangan). */
    public RevolverPose placed(Quaternionf orientation, Vec3 position) {
        return new RevolverPose(cylinder, open, cock, trigger, bullet, orientation, position);
    }

    /** Hanya keadaan mekaniknya (tanpa arah/posisi dunia). */
    public RevolverPose mechanics() {
        return new RevolverPose(cylinder, open, cock, trigger, bullet);
    }

    private static RevolverPose current = REST;
    /** Kalau tidak null, posisi moncong (ruang pose saat render) ditulis ke sini. */
    private static Vector3f muzzleSink;
    /**
     * Kalau tidak null, pantat peluru di chamber atas (ruang pose saat render) ditulis ke [0] dan titik satu
     * piksel model lebih jauh ke belakang sepanjang sumbu chamber ke [1] (arah keluar chamber).
     */
    private static Vector3f[] chamberSink;

    public static void set(RevolverPose pose, Vector3f muzzle) {
        set(pose, muzzle, null);
    }

    public static void set(RevolverPose pose, Vector3f muzzle, Vector3f[] chamber) {
        current = pose == null ? REST : pose;
        muzzleSink = muzzle;
        chamberSink = chamber;
    }

    public static void clear() {
        current = REST;
        muzzleSink = null;
        chamberSink = null;
    }

    static RevolverPose current() {
        return current;
    }

    static Vector3f muzzleSink() {
        return muzzleSink;
    }

    static Vector3f[] chamberSink() {
        return chamberSink;
    }
}
