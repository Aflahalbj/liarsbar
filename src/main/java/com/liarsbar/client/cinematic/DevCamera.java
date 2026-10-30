package com.liarsbar.client.cinematic;

import net.minecraft.world.phys.Vec3;

/** Kamera uji (AnimDev): bidikan tetap untuk memeriksa animasi dan kamera dari sudut tertentu. */
public final class DevCamera {

    public record Override(Vec3 pos, Vec3 target, float fov) {
    }

    private static Override current;

    private DevCamera() {
    }

    public static Override current() {
        return current;
    }

    public static void set(Override o) {
        current = o;
    }
}
