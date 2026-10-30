package com.liarsbar.client.render;

import com.liarsbar.entity.BotEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Bot sendiri tidak digambar: badannya yang terlihat adalah {@link com.liarsbar.client.anim.BotPuppet}
 * (pemain tiruan client) supaya dianimasikan persis seperti pemain sungguhan.
 */
public class BotRenderer extends EntityRenderer<BotEntity> {

    private static final ResourceLocation NONE = new ResourceLocation("textures/entity/player/wide/steve.png");

    public BotRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(BotEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
    }

    @Override
    protected boolean shouldShowName(BotEntity entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(BotEntity entity) {
        return NONE;
    }
}
