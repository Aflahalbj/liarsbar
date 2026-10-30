package com.liarsbar.client.render;

import com.liarsbar.block.CeilingFanBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Empat bilah walnut kipas plafon, berputar pelan (Art Bible: kipas 4 bilah berputar). Fase putaran dari
 * waktu dunia + posisi supaya kipas-kipas tidak serempak.
 */
public class CeilingFanRenderer implements BlockEntityRenderer<CeilingFanBlock.Entity> {

    private static final ResourceLocation SPRITE = new ResourceLocation("liarsbar", "block/fan");
    /** Radian per tick. */
    private static final float SPEED = 0.05f;
    private static final float HUB_Y = 5.6f / 16f;

    public CeilingFanRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CeilingFanBlock.Entity fan, float partial, PoseStack pose, MultiBufferSource buffers, int light,
                       int overlay) {
        if (fan.getLevel() == null) {
            return;
        }
        TextureAtlasSprite sp = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(SPRITE);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        long seed = fan.getBlockPos().asLong();
        float spin = ((fan.getLevel().getGameTime() % 100000L) + partial) * SPEED + (seed & 0xFF) * 0.1f;
        // Bilah: u 0..16 (sepanjang), v 0..4 (lebar) di sheet fan 32x32.
        float u0 = sp.getU(0), u1 = sp.getU(16), v0 = sp.getV(0), v1 = sp.getV(4);
        for (int k = 0; k < 4; k++) {
            pose.pushPose();
            pose.translate(0.5f, HUB_Y, 0.5f);
            pose.mulPose(Axis.YP.rotation(spin + k * (float) Math.PI / 2));
            pose.mulPose(Axis.XP.rotationDegrees(8));
            Matrix4f m = pose.last().pose();
            Matrix3f n = pose.last().normal();
            float x0 = 0.12f, x1 = 0.95f, w = 0.1f, t = 0.012f;
            int top = 0xFFFFFFFF, bottom = 0xFFB0A090;
            // Atas dan bawah bilah + tepi depan tipis.
            Geo.vertex(vc, m, n, 0, 1, 0, light, top, x0, t, -w, u0, v0);
            Geo.vertex(vc, m, n, 0, 1, 0, light, top, x0, t, w, u0, v1);
            Geo.vertex(vc, m, n, 0, 1, 0, light, top, x1, t, w, u1, v1);
            Geo.vertex(vc, m, n, 0, 1, 0, light, top, x1, t, -w, u1, v0);
            Geo.vertex(vc, m, n, 0, -1, 0, light, bottom, x0, -t, -w, u0, v0);
            Geo.vertex(vc, m, n, 0, -1, 0, light, bottom, x1, -t, -w, u1, v0);
            Geo.vertex(vc, m, n, 0, -1, 0, light, bottom, x1, -t, w, u1, v1);
            Geo.vertex(vc, m, n, 0, -1, 0, light, bottom, x0, -t, w, u0, v1);
            Geo.vertex(vc, m, n, 0, 0, -1, light, bottom, x0, t, -w, u0, v0);
            Geo.vertex(vc, m, n, 0, 0, -1, light, bottom, x1, t, -w, u1, v0);
            Geo.vertex(vc, m, n, 0, 0, -1, light, bottom, x1, -t, -w, u1, v0);
            Geo.vertex(vc, m, n, 0, 0, -1, light, bottom, x0, -t, -w, u0, v0);
            pose.popPose();
        }
    }
}
