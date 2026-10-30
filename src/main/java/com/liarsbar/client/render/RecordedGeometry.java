package com.liarsbar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Geometri statis yang direkam sekali (posisi di ruang lokal) lalu diputar ulang tiap frame dengan pose
 * saat itu. Model blok/item vanilla dan ratusan kotak bertekstur jadi cukup sekali dibangun; tiap frame
 * hanya array angka yang dikirim ke buffer. Dipakai sebagai {@link MultiBufferSource} saat merekam.
 */
public final class RecordedGeometry {

    /** Per vertex: x y z u v nx ny nz (float) dan warna overlay cahaya (int). */
    private static final int FLOATS = 8, INTS = 3;

    private final List<RenderType> types = new ArrayList<>();
    private final List<float[]> floats = new ArrayList<>();
    private final List<int[]> ints = new ArrayList<>();
    private final List<Integer> counts = new ArrayList<>();

    public int vertexCount() {
        int n = 0;
        for (int c : counts) {
            n += c;
        }
        return n;
    }

    public void replay(PoseStack pose, MultiBufferSource buffers) {
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        for (int t = 0; t < types.size(); t++) {
            VertexConsumer vc = buffers.getBuffer(types.get(t));
            float[] f = floats.get(t);
            int[] in = ints.get(t);
            int count = counts.get(t);
            for (int v = 0; v < count; v++) {
                int fo = v * FLOATS, io = v * INTS;
                float x = f[fo], y = f[fo + 1], z = f[fo + 2];
                float px = m.m00() * x + m.m10() * y + m.m20() * z + m.m30();
                float py = m.m01() * x + m.m11() * y + m.m21() * z + m.m31();
                float pz = m.m02() * x + m.m12() * y + m.m22() * z + m.m32();
                float nx = f[fo + 5], ny = f[fo + 6], nz = f[fo + 7];
                float qx = nm.m00() * nx + nm.m10() * ny + nm.m20() * nz;
                float qy = nm.m01() * nx + nm.m11() * ny + nm.m21() * nz;
                float qz = nm.m02() * nx + nm.m12() * ny + nm.m22() * nz;
                int c = in[io];
                vc.vertex(px, py, pz, ((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f,
                        ((c >>> 24) & 0xFF) / 255f, f[fo + 3], f[fo + 4], in[io + 1], in[io + 2], qx, qy, qz);
            }
        }
    }

    /** Sumber buffer perekam: semua yang digambar ke sini disimpan per RenderType. */
    public static final class Recorder implements MultiBufferSource {
        private final Map<RenderType, Sink> sinks = new LinkedHashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return sinks.computeIfAbsent(type, t -> new Sink());
        }

        public RecordedGeometry finish() {
            RecordedGeometry out = new RecordedGeometry();
            for (Map.Entry<RenderType, Sink> e : sinks.entrySet()) {
                Sink s = e.getValue();
                if (s.count == 0) {
                    continue;
                }
                out.types.add(e.getKey());
                out.floats.add(Arrays.copyOf(s.f, s.count * FLOATS));
                out.ints.add(Arrays.copyOf(s.i, s.count * INTS));
                out.counts.add(s.count);
            }
            return out;
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
