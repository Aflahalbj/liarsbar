package com.liarsbar.client.render;

import com.liarsbar.client.anim.ArmIk;
import com.liarsbar.client.anim.GripLayer;
import com.liarsbar.client.anim.SeatAnimator;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.liarsbar.client.fx.MuzzleFx;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Revolver di tangan kanan penembak (orang ketiga). Tiap frame merekam
 * kerangka induk lengan (untuk IK genggaman di {@link GripLayer}), lalu
 * menaruh titik genggam revolver tepat di tengah kepalan kanan yang
 * benar-benar dirender, termasuk tekukan siku, jadi revolver tidak pernah
 * lepas dari tangan. Arahnya dari sutradara (ruang dunia). Posisi moncong
 * dicatat untuk api dan asap.
 */
public class GunLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Titik genggam revolver relatif tengah kepalan saat sudah mantap (sumbu revolver, blok). */
    private static final float GRIP_RAISE = 0.08f;
    private static final float GRIP_FORWARD = 0.02f;

    private ItemStack stack;
    private final Vector3f muzzle = new Vector3f();
    private final Vector3f[] chamber = {new Vector3f(), new Vector3f()};
    private final Matrix4f world = new Matrix4f();
    private final Vector3f fist = new Vector3f();
    private final Vector3f pivot = new Vector3f();

    public GunLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        int seat = SeatAnimator.seatOf(player);
        if (seat < 0) {
            return;
        }
        // Ruang render -> dunia: kamera + kebalikan putaran pandangan.
        Vec3 cam = MuzzleFx.cameraPos();
        world.translation((float) cam.x, (float) cam.y, (float) cam.z).mul(MuzzleFx.viewInverse())
                .mul(pose.last().pose());
        SeatAnimator.capture(player, world, getParentModel());
        RouletteDirector.Gun gun = RouletteDirector.gun(seat, partialTick);
        if (!gun.inHand() || gun.pose().world() == null) {
            return;
        }
        if (stack == null) {
            stack = new ItemStack(ModItems.REVOLVER.get());
        }
        ModelPart arm = getParentModel().rightArm;
        Vec3f bend = PlayerAnimationAccess.getPlayerAnimLayer(player)
                .get3DTransform("rightArm", TransformType.BEND, partialTick, Vec3f.ZERO);
        pivot.set(arm.x / 16f, arm.y / 16f, arm.z / 16f);
        ArmIk.fist(pivot, arm.xRot, arm.yRot, arm.zRot, bend.getX(), bend.getY(), GripLayer.RIGHT_FIST_X, fist);
        world.transformPosition(fist);
        // Kepalan memegang bagian bawah gagang supaya frame dan silinder terlihat di atas jari; saat baru
        // diangkat dari meja kepalan masih di atasnya (sama dengan sasaran raihan).
        float k = RouletteDirector.gripSettle(seat, partialTick);
        org.joml.Quaternionf q = gun.pose().world();
        Vector3f off = q.transform(new Vector3f(0, GRIP_RAISE * k, -GRIP_FORWARD * k));
        off.y -= (float) (RouletteDirector.REACH_LIFT * (1 - k));
        fist.add(off);
        Vec3 grip = new Vec3(fist.x, fist.y, fist.z);
        MuzzleFx.recordGrip(seat, grip);
        muzzle.set(Float.NaN);
        chamber[0].set(Float.NaN);
        RevolverPose.set(gun.pose().placed(gun.pose().world(), grip), muzzle, chamber);
        try {
            Minecraft.getInstance().getItemRenderer().renderStatic(player, stack, ItemDisplayContext.NONE, false, pose,
                    buffers, player.level(), light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    player.getId());
        } finally {
            RevolverPose.clear();
        }
        if (Float.isFinite(muzzle.x)) {
            MuzzleFx.recordMuzzle(seat, muzzle);
        }
        if (Float.isFinite(chamber[0].x)) {
            RouletteDirector.recordChamber(seat, toWorld(chamber[0], cam), toWorld(chamber[1], cam), gun.pose().bullet());
        }
    }

    /** Ruang render -> dunia: kamera + kebalikan putaran pandangan. */
    private static Vec3 toWorld(Vector3f p, Vec3 cam) {
        Vector3f w = MuzzleFx.viewInverse().transformPosition(new Vector3f(p));
        return cam.add(w.x, w.y, w.z);
    }
}
