package com.liarsbar.client.cinematic;

import com.liarsbar.client.ClientTable;
import com.liarsbar.mixin.CameraAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Kamera meja, dipasang sesudah kamera vanilla (lihat {@code CameraMixin}):
 * <ul>
 *   <li>penonton versi penuh: bidikan sinematik lepas dari pemain, dibaurkan
 *       masuk/keluar dari pandangan sendiri;</li>
 *   <li>penonton versi pendek: menoleh pelan ke penembak;</li>
 *   <li>penembak (POV): pandangan ditarik menunduk ke pistol saat diambil dan
 *       diputar, goyang napas saat menahan, dan roboh saat tertembak.</li>
 * </ul>
 * Yaw selalu dibaurkan lewat selisih terpendek supaya tidak berputar liar di batas -180/180.
 */
public final class CameraDirector {

    /** Pandangan duduk: naik dan mundur (blok) dari mata vanilla. */
    private static final double SEAT_LIFT = 0.42, SEAT_BACK = 0.18;

    private static float liftWeight;
    private static float fov = -1;
    private static float fovWeight;
    private static float roll;
    /** Kamera sinematik sedang dipakai (HUD meja disembunyikan). */
    private static float cinematicWeight;

    private CameraDirector() {
    }

    public static float cinematicWeight() {
        return cinematicWeight;
    }

    public static void apply(Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        fovWeight = 0;
        roll = 0;
        cinematicWeight = 0;
        if (mc.level == null || mc.player == null || ClientTable.state() == null) {
            return;
        }
        CameraAccessor access = (CameraAccessor) camera;
        seatLift(camera, access);
        DevCamera.Override dev = DevCamera.current();
        if (dev != null) {
            Vec3 dir = dev.target().subtract(dev.pos());
            access.liarsbar$setPosition(dev.pos());
            access.liarsbar$setRotation(CameraPath.yawOf(dir), CameraPath.pitchOf(dir));
            access.liarsbar$setDetached(true);
            fov = dev.fov();
            fovWeight = 1;
            cinematicWeight = 1;
            return;
        }
        RouletteDirector.Shot shot = RouletteDirector.cinematic(partial);
        if (shot == null) {
            shot = LiarDirector.cinematic(partial);
        }
        if (shot == null) {
            shot = DealDirector.cinematic(partial);
        }
        if (shot != null) {
            float w = shot.weight();
            Vec3 base = camera.getPosition();
            Vec3 pos = base.add(shot.pos().subtract(base).scale(w));
            Vec3 dir = shot.target().subtract(shot.pos());
            float yaw = camera.getYRot() + CameraPath.angleDelta(camera.getYRot(), CameraPath.yawOf(dir)) * w;
            float pitch = Mth.lerp(w, camera.getXRot(), CameraPath.pitchOf(dir));
            access.liarsbar$setPosition(pos);
            access.liarsbar$setRotation(yaw, pitch);
            if (w > 0.05f) {
                access.liarsbar$setDetached(true);
            }
            fov = shot.fov();
            fovWeight = w;
            cinematicWeight = w;
            return;
        }
        RouletteDirector.Shot look = RouletteDirector.lookAt(partial);
        if (look != null) {
            Vec3 dir = look.target().subtract(camera.getPosition());
            float w = look.weight();
            float yaw = camera.getYRot() + CameraPath.angleDelta(camera.getYRot(), CameraPath.yawOf(dir)) * w;
            float pitch = Mth.lerp(w, camera.getXRot(), CameraPath.pitchOf(dir));
            access.liarsbar$setRotation(yaw, pitch);
            return;
        }
        float[] pov = RouletteDirector.shooterLook(partial);
        if (pov != null) {
            float t = RouletteDirector.now(partial);
            float sway = pov[1];
            float pitch = camera.getXRot() + pov[0] + (float) Math.sin(t * 0.21) * 0.7f * sway;
            float yaw = camera.getYRot() + (float) Math.sin(t * 0.13 + 1.1) * 0.5f * sway;
            access.liarsbar$setRotation(yaw, pitch);
            roll = (float) Math.sin(t * 0.17 + 0.4) * 0.6f * sway;
        }
        float[] collapse = RouletteDirector.shooterCollapse(partial);
        if (collapse != null) {
            Vec3 base = camera.getPosition();
            access.liarsbar$setPosition(base.add(0, -collapse[0], 0));
            access.liarsbar$setRotation(camera.getYRot() + collapse[2], camera.getXRot() + collapse[3]);
            roll += collapse[1];
        }
    }

    /**
     * Pandangan duduk dinaikkan dan ditarik sedikit ke belakang supaya permukaan meja (tumpukan, table card,
     * jam pasir) kelihatan di atas kipas kartu. Masuk/keluar halus saat duduk/berdiri; jadi dasar semua bidikan.
     */
    private static void seatLift(Camera camera, CameraAccessor access) {
        Minecraft mc = Minecraft.getInstance();
        boolean want = ClientTable.seated() && !camera.isDetached();
        float dt = mc.getDeltaFrameTime();
        liftWeight += ((want ? 1 : 0) - liftWeight) * (1 - (float) Math.pow(0.02, dt / 20f));
        if (liftWeight < 0.001f) {
            return;
        }
        float e = liftWeight * liftWeight * (3 - 2 * liftWeight);
        double yaw = Math.toRadians(camera.getYRot());
        Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw)).scale(SEAT_BACK * e);
        access.liarsbar$setPosition(camera.getPosition().add(back).add(0, SEAT_LIFT * e, 0));
    }

    public static void onFov(ViewportEvent.ComputeFov event) {
        if (fovWeight > 0 && fov > 0) {
            event.setFOV(Mth.lerp(fovWeight, event.getFOV(), fov));
        }
    }

    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (roll != 0) {
            event.setRoll(event.getRoll() + roll);
        }
    }
}
