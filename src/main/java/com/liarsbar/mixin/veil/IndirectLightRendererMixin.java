package com.liarsbar.mixin.veil;

import foundry.veil.api.client.render.CullFrustum;
import foundry.veil.api.client.render.deferred.light.IndirectLight;
import foundry.veil.api.client.render.deferred.light.Light;
import foundry.veil.api.client.render.deferred.light.renderer.IndirectLightRenderer;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Visibilitas lampu Veil dihitung di CPU, bukan compute shader. Di sebagian GPU
 * compute shader Veil ({@code veil:light/indirect_sphere}) gagal dikompilasi,
 * dan tanpa ini semua point light tidak tergambar. Diambil dari mod Backrooms
 * milik pengguna (yang mem-port-nya dari SP-Backrooms Revamped).
 */
@Mixin(value = IndirectLightRenderer.class, remap = false)
public abstract class IndirectLightRendererMixin<T extends Light & IndirectLight<T>> {

    @Shadow
    @Final
    protected int highResSize;

    @Shadow
    @Final
    protected int lowResSize;

    @Shadow
    @Final
    private int indirectVbo;

    @Shadow
    protected abstract boolean isVisible(T light, CullFrustum frustum);

    @Shadow
    protected abstract boolean shouldDrawHighResolution(T light, CullFrustum frustum);

    /**
     * @author Liar's Table
     * @reason compute shader visibilitas Veil tidak bisa dikompilasi di semua GPU
     */
    @Overwrite
    private int updateVisibility(List<T> lights, CullFrustum frustum) {
        int count = 0;
        GL15C.glBindBuffer(36671, this.indirectVbo);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buffer = stack.malloc(this.lowResSize > 0 ? 20 : 4);
            int index = 0;
            for (T light : lights) {
                if (this.isVisible(light, frustum)) {
                    if (this.lowResSize > 0) {
                        boolean highRes = this.shouldDrawHighResolution(light, frustum);
                        buffer.putInt(0, highRes ? this.highResSize : this.lowResSize);
                        buffer.putInt(4, 1);
                        buffer.putInt(8, !highRes ? this.highResSize : 0);
                        buffer.putInt(12, 0);
                        buffer.putInt(16, index);
                        GL15C.glBufferSubData(36671, (long) (count * 4) * 5L, buffer);
                    } else {
                        buffer.putInt(0, index);
                        GL15C.glBufferSubData(36671, (long) (count * 4) * 5L + 16L, buffer);
                    }
                    ++count;
                }
                ++index;
            }
        }
        return count;
    }
}
