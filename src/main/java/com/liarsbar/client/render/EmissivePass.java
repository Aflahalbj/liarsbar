package com.liarsbar.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emissive (RenderType {@code eyes}: kaca jendela, hujan, bohlam, strip rak, asap) yang ditunda sampai semua
 * entity selesai digambar. Di Veil deferred, eyes menulis ke buffer "compatibility" yang ditumpuk di atas hasil
 * lampu, sedangkan entity opaque menulis ke albedo: kalau eyes digambar duluan, pemain yang digambar sesudahnya
 * tidak menghapusnya, jadi jendela tampak menembus pemain. Ditunda ke {@code AFTER_ENTITIES}, depth semua
 * entity sudah ada dan yang di belakang pemain dibuang depth test.
 */
public final class EmissivePass {

    /** Per vertex: x y z u v nx ny nz (float), warna, overlay, cahaya (int); posisi sudah dalam ruang pose. */
    private static final int FLOATS = 8, INTS = 3;

    private static final Map<RenderType, Boolean> EMISSIVE = new IdentityHashMap<>();
    private static final Map<RenderType, Sink> SINKS = new IdentityHashMap<>();
    private static final List<RenderType> ORDER = new ArrayList<>();
    private static final Wrapper WRAPPER = new Wrapper();

    private EmissivePass() {
    }

    /** Sumber buffer untuk satu panggilan render: eyes ditahan, sisanya langsung ke {@code buffers}. */
    public static MultiBufferSource wrap(MultiBufferSource buffers) {
        WRAPPER.delegate = buffers;
        return WRAPPER;
    }

    private static boolean emissive(RenderType type) {
        return EMISSIVE.computeIfAbsent(type, t -> t.toString().startsWith("RenderType[eyes"));
    }

    /** Dipanggil di {@code AFTER_ENTITIES}: gambar semua yang ditahan frame ini lalu kosongkan. */
    public static void flush() {
        if (ORDER.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (RenderType type : ORDER) {
            Sink s = SINKS.get(type);
            if (s.count == 0) {
                continue;
            }
            VertexConsumer vc = buffers.getBuffer(type);
            for (int v = 0; v < s.count; v++) {
                int fo = v * FLOATS, io = v * INTS;
                int c = s.i[io];
                vc.vertex(s.f[fo], s.f[fo + 1], s.f[fo + 2], ((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f,
                        (c & 0xFF) / 255f, ((c >>> 24) & 0xFF) / 255f, s.f[fo + 3], s.f[fo + 4], s.i[io + 1],
                        s.i[io + 2], s.f[fo + 5], s.f[fo + 6], s.f[fo + 7]);
            }
            buffers.endBatch(type);
            s.count = 0;
        }
        ORDER.clear();
    }

    private static final class Wrapper implements MultiBufferSource {
        MultiBufferSource delegate;

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            if (!emissive(type)) {
                return delegate.getBuffer(type);
            }
            Sink s = SINKS.computeIfAbsent(type, t -> new Sink());
            if (s.count == 0 && !ORDER.contains(type)) {
                ORDER.add(type);
            }
            return s;
        }
    }

    private static final class Sink implements VertexConsumer {
        float[] f = new float[1024 * FLOATS];
        int[] i = new int[1024 * INTS];
        int count;
        float x, y, z, u, v, nx, ny = 1, nz;
        int color = 0xFFFFFFFF, overlay, light;
        boolean fixedColor;
        int defaultColor;

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.x = (float) x;
            this.y = (float) y;
            this.z = (float) z;
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            color = (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            overlay = (u & 0xFFFF) | (v & 0xFFFF) << 16;
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            light = (u & 0xFFFF) | (v & 0xFFFF) << 16;
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            nx = x;
            ny = y;
            nz = z;
            return this;
        }

        @Override
        public void endVertex() {
            if (count * FLOATS >= f.length) {
                f = Arrays.copyOf(f, f.length * 2);
                i = Arrays.copyOf(i, i.length * 2);
            }
            int fo = count * FLOATS, io = count * INTS;
            f[fo] = x;
            f[fo + 1] = y;
            f[fo + 2] = z;
            f[fo + 3] = u;
            f[fo + 4] = v;
            f[fo + 5] = nx;
            f[fo + 6] = ny;
            f[fo + 7] = nz;
            i[io] = fixedColor ? defaultColor : color;
            i[io + 1] = overlay;
            i[io + 2] = light;
            count++;
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            fixedColor = true;
            defaultColor = (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
        }

        @Override
        public void unsetDefaultColor() {
            fixedColor = false;
        }
    }
}
