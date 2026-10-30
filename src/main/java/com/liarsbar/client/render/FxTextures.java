package com.liarsbar.client.render;

import com.liarsbar.LiarsBar;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Random;

/**
 * Tekstur efek cahaya yang dibuat saat jalan (bukan aset gambar): gradien, titik lembut (bokeh, debu,
 * pendar bohlam), garis lembut (lampu strip), garis hujan di kaca, dan kabut kerucut lampu.
 * Semuanya dipakai dengan {@code RenderType.eyes} (aditif): hitam = tidak menambah apa pun.
 */
public final class FxTextures {

    public static final ResourceLocation DOT = id("dot");
    public static final ResourceLocation HAZE = id("haze");

    private static boolean ready;

    private FxTextures() {
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation(LiarsBar.MOD_ID, "fx_runtime/" + name);
    }

    /** Daftarkan semua tekstur (sekali, di thread render, sebelum dipakai). */
    public static void ensure() {
        if (ready) {
            return;
        }
        ready = true;
        register(DOT, 32, 32, (x, y) -> {
            float dx = (x - 15.5f) / 15.5f, dy = (y - 15.5f) / 15.5f;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float v = Math.max(0, 1 - d);
            return gray(v * v * (3 - 2 * v));
        });
        // Kabut kerucut: u = sekeliling (asap bergumpal), v = dari kap (0) ke ujung kerucut (1).
        float[][] smoke = noise(64, 64, 11);
        register(HAZE, 64, 64, (x, y) -> {
            float v = y / 63f;
            float fade = (1 - v) * (1 - v) * (0.25f + 0.75f * Math.min(1, v * 6));
            return gray(fade * (0.35f + 0.65f * smoke[y][x]));
        });
    }

    private interface Pixel {
        int at(int x, int y);
    }

    private static void register(ResourceLocation id, int w, int h, Pixel pixel) {
        NativeImage image = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setPixelRGBA(x, y, pixel.at(x, y));
            }
        }
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false);
        Minecraft.getInstance().getTextureManager().register(id, texture);
    }

    /** ABGR (format NativeImage) abu-abu, alfa penuh. */
    private static int gray(float v) {
        int c = Math.max(0, Math.min(255, Math.round(v * 255)));
        return 0xFF000000 | c << 16 | c << 8 | c;
    }

    /** Noise lembut yang bisa diulang (tileable): jumlah beberapa oktaf nilai acak yang diinterpolasi. */
    private static float[][] noise(int w, int h, long seed) {
        Random rng = new Random(seed);
        float[][] out = new float[h][w];
        float amp = 1, total = 0;
        for (int cell = 16; cell >= 4; cell /= 2) {
            int gw = w / cell, gh = h / cell;
            float[][] grid = new float[gh][gw];
            for (float[] row : grid) {
                for (int i = 0; i < gw; i++) {
                    row[i] = rng.nextFloat();
                }
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float fx = (float) x / cell, fy = (float) y / cell;
                    int x0 = (int) fx, y0 = (int) fy;
                    float tx = fx - x0, ty = fy - y0;
                    tx = tx * tx * (3 - 2 * tx);
                    ty = ty * ty * (3 - 2 * ty);
                    float a = grid[y0 % gh][x0 % gw], b = grid[y0 % gh][(x0 + 1) % gw];
                    float c = grid[(y0 + 1) % gh][x0 % gw], d = grid[(y0 + 1) % gh][(x0 + 1) % gw];
                    out[y][x] += amp * ((a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty);
                }
            }
            total += amp;
            amp *= 0.5f;
        }
        for (float[] row : out) {
            for (int i = 0; i < w; i++) {
                row[i] /= total;
            }
        }
        return out;
    }
}
