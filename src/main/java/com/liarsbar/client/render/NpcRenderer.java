package com.liarsbar.client.render;

import com.liarsbar.entity.BarNpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** NPC bar sendiri tidak digambar: badannya {@link com.liarsbar.client.anim.NpcPuppet} (pemain tiruan client). */
public class NpcRenderer extends EntityRenderer<BarNpcEntity> {

    private static final ResourceLocation NONE = new ResourceLocation("textures/entity/player/wide/steve.png");

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(BarNpcEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
    }

    @Override
    protected boolean shouldShowName(BarNpcEntity entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(BarNpcEntity entity) {
        return NONE;
    }
}
