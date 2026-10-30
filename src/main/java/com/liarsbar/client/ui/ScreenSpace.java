package com.liarsbar.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Proyeksi titik dunia ke koordinat GUI (untuk teks yang menempel di dunia), dari matriks frame terakhir. */
public final class ScreenSpace {

    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;

    private ScreenSpace() {
    }

    /** Dipanggil sekali per frame sebelum level digambar. */
    public static void capture(Matrix4f projection, Matrix4f view, Vec3 cameraPos) {
        PROJECTION.set(projection);
        VIEW.set(view);
        camera = cameraPos;
    }

    /** {x, y} dalam piksel GUI, atau null kalau di belakang kamera. */
    public static float[] project(Vec3 world) {
        Vector4f p = new Vector4f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z), 1);
        VIEW.transform(p);
        PROJECTION.transform(p);
        if (p.w <= 0.01f) {
            return null;
        }
        var window = Minecraft.getInstance().getWindow();
        float x = (p.x / p.w * 0.5f + 0.5f) * window.getGuiScaledWidth();
        float y = (1 - (p.y / p.w * 0.5f + 0.5f)) * window.getGuiScaledHeight();
        return new float[]{x, y};
    }
}
