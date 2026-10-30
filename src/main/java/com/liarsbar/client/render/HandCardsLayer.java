package com.liarsbar.client.render;

import com.liarsbar.client.ClientTable;
import com.liarsbar.client.anim.ArmIk;
import com.liarsbar.client.anim.GripLayer;
import com.liarsbar.client.anim.SeatAnimator;
import com.liarsbar.client.cinematic.DealDirector;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.liarsbar.client.cinematic.ThrowDirector;
import com.liarsbar.client.fx.MuzzleFx;
import com.liarsbar.client.ui.TableHud;
import com.liarsbar.table.PublicState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Kipas kartu tertutup di tangan kiri tiap orang di meja (HUD diegetik: jumlah = kartu di tangan). Pangkal
 * kipas ditaruh di tengah kepalan kiri yang benar-benar dirender (termasuk tekukan siku, sama seperti
 * revolver di {@link GunLayer}), jadi kartunya ikut tangan dan tidak melayang. Muka kartu menghadap
 * pemegangnya, punggungnya ke meja.
 */
public class HandCardsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final float CARD_W = TableCards.W, CARD_H = TableCards.H;
    private static final float GRIP_UP = HeldCards.GRIP_UP;

    private final Matrix4f world = new Matrix4f();
    private final Matrix4f toRender = new Matrix4f();
    private final Vector3f fist = new Vector3f();
    private final Vector3f pivot = new Vector3f();

    public HandCardsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** Tengah kepalan yang dirender (dunia), sama dengan cara GunLayer menaruh revolver. */
    private Vec3 fist(AbstractClientPlayer player, ModelPart arm, String name, float fistX, float partialTick) {
        Vec3f bend = PlayerAnimationAccess.getPlayerAnimLayer(player)
                .get3DTransform(name, TransformType.BEND, partialTick, Vec3f.ZERO);
        pivot.set(arm.x / 16f, arm.y / 16f, arm.z / 16f);
        ArmIk.fist(pivot, arm.xRot, arm.yRot, arm.zRot, bend.getX(), bend.getY(), fistX, fist);
        world.transformPosition(fist);
        return new Vec3(fist.x, fist.y, fist.z);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        int seat = SeatAnimator.seatOf(player);
        PublicState state = ClientTable.state();
        // Di lobby hanya selama sekuens roulette dev (peluru di tangan saat mengisi).
        if (seat < 0 || state == null || seat >= state.seats.size()
                || state.stage == PublicState.Stage.LOBBY && !RouletteDirector.active()) {
            return;
        }
        PublicState.SeatInfo info = state.seats.get(seat);
        RouletteDirector.Frame f = RouletteDirector.frame(seat);
        if (f == null) {
            return;
        }
        // Ruang render -> dunia, sama dengan GunLayer.
        Vec3 cam = MuzzleFx.cameraPos();
        world.translation((float) cam.x, (float) cam.y, (float) cam.z).mul(MuzzleFx.viewInverse())
                .mul(pose.last().pose());
        Vec3 left = fist(player, getParentModel().leftArm, "leftArm", GripLayer.LEFT_FIST_X, partialTick);
        Vec3 right = fist(player, getParentModel().rightArm, "rightArm", GripLayer.RIGHT_FIST_X, partialTick);
        RouletteDirector.Grip grip = RouletteDirector.grip(seat, partialTick);
        HeldCards.record(seat, left, right, grip == null || grip.leftWeight() <= 0);
        if (!info.alive) {
            return;
        }
        pose.pushPose();
        // Gambar langsung di koordinat dunia relatif kamera: dunia -> render = putaran pandangan * (p - kamera).
        toRender.set(MuzzleFx.viewInverse()).invert();
        pose.last().pose().set(toRender);
        pose.last().normal().set(MuzzleFx.viewRotation());
        HeldCards.render(seat, pose, buffers, light, cam, left, right, partialTick);
        int n = Math.min(DealDirector.visibleCards(seat, info.cards, partialTick)
                + ThrowDirector.inFan(seat, partialTick), 7);
        if (n <= 0 || RouletteDirector.gun(seat, partialTick).inHand() || RouletteDirector.leftHandBusy(seat, partialTick)) {
            pose.popPose();
            return;
        }
        // Baru diambil dari felt: paket berbaring di felt -> diangkat ke genggaman, lalu kipas dibuka.
        float[] pickup = DealDirector.pickup(seat, partialTick);
        float lift = pickup == null ? 1 : pickup[0];
        float spread = pickup == null ? 1 : pickup[1];
        float attach = pickup == null ? 1 : pickup[2];
        // Lokal +z = ke arah pemegang (wajah kartu menghadapnya); punggung kartu menghadap meja.
        Quaternionf stand = new Quaternionf().rotationY((float) Math.atan2(-f.forward().x, -f.forward().z))
                .rotateX(24 * Mth.DEG_TO_RAD);
        Quaternionf q = stand;
        Vec3 base = left.add(0, GRIP_UP, 0);
        if (lift < 1) {
            // Kartu kipas (tinggi +y, muka +z) berbaring seperti kartu meja (panjang +z, punggung +y).
            Quaternionf lying = TableCards.flat(pickup[3]).mul(new Quaternionf().setFromNormalized(
                    new org.joml.Matrix3f(-1, 0, 0, 0, 0, 1, 0, 1, 0)));
            q = new Quaternionf(lying).slerp(stand, lift);
            Vec3 packet = DealDirector.packetAt(seat);
            if (packet != null && attach < 1) {
                Vector3f half = q.transform(new Vector3f(0, CARD_H / 2, 0));
                Vec3 felt = packet.subtract(half.x, half.y, half.z);
                base = felt.lerp(base, attach);
            }
        }
        pose.translate(base.x - cam.x, base.y - cam.y, base.z - cam.z);
        pose.mulPose(q);
        VertexConsumer back = buffers.getBuffer(RenderType.entityCutout(TableHud.cardTexture(null)));
        for (int k = 0; k < n; k++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(((n - 1) / 2f - k) * 11f * spread));
            pose.translate(0, -CARD_H * 0.15f * spread, -0.002f * k);
            card(back, pose, light);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Kartu berdiri di bidang XY (pangkal bawah-tengah di titik asal), dua sisi bertekstur punggung. */
    private static void card(VertexConsumer vc, PoseStack pose, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float hw = CARD_W / 2, h = CARD_H;
        int c = 0xFFFFFFFF;
        Geo.vertex(vc, m, n, 0, 0, 1, light, c, -hw, h, 0, 0, 0);
        Geo.vertex(vc, m, n, 0, 0, 1, light, c, -hw, 0, 0, 0, 1);
        Geo.vertex(vc, m, n, 0, 0, 1, light, c, hw, 0, 0, 1, 1);
        Geo.vertex(vc, m, n, 0, 0, 1, light, c, hw, h, 0, 1, 0);
        Geo.vertex(vc, m, n, 0, 0, -1, light, c, hw, h, -0.001f, 0, 0);
        Geo.vertex(vc, m, n, 0, 0, -1, light, c, hw, 0, -0.001f, 0, 1);
        Geo.vertex(vc, m, n, 0, 0, -1, light, c, -hw, 0, -0.001f, 1, 1);
        Geo.vertex(vc, m, n, 0, 0, -1, light, c, -hw, h, -0.001f, 1, 0);
    }
}
