package com.liarsbar.client.render;

import com.liarsbar.client.anim.ArmIk;
import com.liarsbar.client.anim.GripLayer;
import com.liarsbar.client.anim.NpcPuppet;
import com.liarsbar.client.anim.NpcRig;
import com.liarsbar.client.anim.NpcPuppets;
import com.liarsbar.client.fx.MuzzleFx;
import com.liarsbar.entity.BarNpcEntity;
import com.liarsbar.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Gelas dan botol NPC bar, digambar di kepalan yang benar-benar dirender (seperti revolver di {@link GunLayer})
 * dengan arah dari {@link com.liarsbar.client.anim.NpcRig}, bukan lewat item-di-tangan vanilla. Merekam juga kerangka
 * lengan + titik mulut untuk IK frame berikutnya, dan jarak hasilnya (AnimDev "npcprobe").
 */
public class NpcPropLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private final Matrix4f world = new Matrix4f();
    private ItemStack glass;
    private ItemStack bottle;

    public NpcPropLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(player instanceof NpcPuppet puppet)) {
            return;
        }
        Vec3 cam = MuzzleFx.cameraPos();
        world.translation((float) cam.x, (float) cam.y, (float) cam.z).mul(MuzzleFx.viewInverse()).mul(pose.last().pose());
        NpcRig rig = NpcPuppets.rig(puppet);
        if (rig == null) {
            return;
        }
        rig.capture(world, getParentModel());
        if (glass == null) {
            glass = new ItemStack(ModItems.WHISKEY_GLASS.get());
            bottle = new ItemStack(ModItems.WHISKEY_BOTTLE.get());
        }
        Vec3 fwd = rig.forward(partialTick);
        Vec3 r = new Vec3(-fwd.z, 0, fwd.x);
        BarNpcEntity npc = rig.npc();
        if (npc.role() == BarNpcEntity.Role.PATRON) {
            Quaternionf rot = rig.glassRot();
            Vec3 center = NpcRig.held(fist(player, getParentModel().rightArm, "rightArm", GripLayer.RIGHT_FIST_X,
                    partialTick), up(rot), r, fwd, 1);
            draw(pose, buffers, light, glass, center, rot, cam);
            Vector3f up = rot.transform(new Vector3f(0, 1, 0));
            Vec3 rim = center.add(up.x * NpcRig.GLASS_HALF,
                    up.y * NpcRig.GLASS_HALF, up.z * NpcRig.GLASS_HALF);
            rig.probe(rig.mouth() == null ? Float.NaN : (float) rim.distanceTo(rig.mouth()), Float.NaN);
            return;
        }
        String anim = rig.anim();
        if ("npc_pour".equals(anim)) {
            Vec3 left = NpcRig.held(fist(player, getParentModel().leftArm, "leftArm", GripLayer.LEFT_FIST_X, partialTick),
                    new Vec3(0, 1, 0), r, fwd, -1);
            draw(pose, buffers, light, glass, left, new Quaternionf(), cam);
            Quaternionf rot = rig.bottleRot();
            Vec3 n = up(rot);
            Vec3 center = NpcRig.held(fist(player, getParentModel().rightArm, "rightArm", GripLayer.RIGHT_FIST_X,
                    partialTick), n, r, fwd, 1);
            draw(pose, buffers, light, bottle, center, rot, cam);
            Vec3 tip = center.add(n.scale(NpcRig.BOTTLE_TIP));
            Vec3 rim = left.add(0, NpcRig.GLASS_HALF, 0);
            rig.probe(Float.NaN, (float) tip.distanceTo(rim));
            if (rig.pourTilt() > 0.7f) {
                stream(pose, buffers, tip, rim.add(0, -0.03, 0), cam);
            }
        } else if ("npc_fetch".equals(anim) && rig.age() >= NpcPuppet.GRAB) {
            Vec3 center = NpcRig.held(fist(player, getParentModel().rightArm, "rightArm", GripLayer.RIGHT_FIST_X,
                    partialTick), new Vec3(0, 1, 0), r, fwd, 1);
            draw(pose, buffers, light, bottle, center, new Quaternionf(), cam);
        }
    }

    private static Vec3 up(Quaternionf rot) {
        Vector3f u = rot.transform(new Vector3f(0, 1, 0));
        return new Vec3(u.x, u.y, u.z);
    }

    /** Aliran whiskey dari ujung leher botol ke gelas: pita amber tipis yang menghadap kamera. */
    private static void stream(PoseStack pose, MultiBufferSource buffers, Vec3 from, Vec3 to, Vec3 cam) {
        Vec3 axis = to.subtract(from);
        Vec3 side = axis.cross(from.subtract(cam)).normalize().scale(0.012);
        pose.pushPose();
        Matrix4f view = new Matrix4f(MuzzleFx.viewInverse()).invert();
        pose.last().pose().set(view);
        pose.last().normal().set(new Matrix3f(view));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        var vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(
                new net.minecraft.resources.ResourceLocation("liarsbar", "textures/entity/white.png")));
        Vec3 a = from.subtract(cam), b = to.subtract(cam);
        int c = 0xD8B06A20;
        int l = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        Geo.vertex(vc, m, n, 0, 1, 0, l, c, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z), 0, 0);
        Geo.vertex(vc, m, n, 0, 1, 0, l, c, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z), 0, 1);
        Geo.vertex(vc, m, n, 0, 1, 0, l, c, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z), 1, 1);
        Geo.vertex(vc, m, n, 0, 1, 0, l, c, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z), 1, 0);
        pose.popPose();
    }

    /** Tengah kepalan yang dirender, di dunia (sama dengan GunLayer). */
    private Vec3 fist(AbstractClientPlayer player, ModelPart arm, String name, float fistX, float partial) {
        Vec3f bend = PlayerAnimationAccess.getPlayerAnimLayer(player).get3DTransform(name, TransformType.BEND, partial,
                Vec3f.ZERO);
        Vector3f p = ArmIk.fist(new Vector3f(arm.x / 16f, arm.y / 16f, arm.z / 16f), arm.xRot, arm.yRot, arm.zRot,
                bend.getX(), bend.getY(), fistX, new Vector3f());
        world.transformPosition(p);
        return new Vec3(p.x, p.y, p.z);
    }

    /** Model item di titik dunia {@code at} dengan arah dunia {@code rot} (model berpusat di tengahnya). */
    private static void draw(PoseStack pose, MultiBufferSource buffers, int light, ItemStack stack, Vec3 at,
                             Quaternionf rot, Vec3 cam) {
        pose.pushPose();
        Matrix4f view = new Matrix4f(MuzzleFx.viewInverse()).invert();
        pose.last().pose().set(view).translate((float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z))
                .rotate(rot).scale(NpcRig.PROP_SCALE);
        pose.last().normal().set(new Matrix3f(view).rotate(rot));
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, Minecraft.getInstance().level, 0);
        pose.popPose();
    }
}
