package com.liarsbar.client.render;

import com.liarsbar.LiarsBar;
import com.liarsbar.item.RevolverItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.liarsbar.client.fx.MuzzleFx;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.RenderUtils;

/**
 * Revolver GeckoLib. Bone-nya diatur dari {@link RevolverPose} tiap kali
 * digambar: silinder berputar di sumbunya, crane mengayun keluar, hammer
 * dikokang, pelatuk ditekan, peluru di chamber atas ditampilkan/digeser. Posisi
 * bone "muzzle" dicatat untuk api moncong, pantat peluru untuk tangan yang mengisi.
 */
public class RevolverRenderer extends GeoItemRenderer<RevolverItem> {

    /**
     * Pose dasar per konteks (derajat rotX, rotY, rotZ, geser x, y, z dalam blok, skala). Model asli
     * sekitar 15 piksel panjangnya; skala 0,38 memberi revolver sekitar 35 cm di tangan pemain.
     * Bisa dituning saat jalan (AnimDev "gun").
     */
    public static final float[] HAND = {0, 0, 0, 0, 0, 0, 0.52f};
    public static final float[] TABLE = {0, 90, 90, 0, 0, 0, 0.38f};
    public static final float[] VIEW = {0, 0, 0, 0, 0, 0, 0.42f};

    public RevolverRenderer() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(LiarsBar.MOD_ID, "revolver")));
    }

    private static void base(PoseStack pose, float[] t) {
        pose.translate(t[3], t[4], t[5]);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(t[0]));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(t[1]));
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(t[2]));
        pose.scale(t[6], t[6], t[6]);
    }

    @Override
    public void preRender(PoseStack poseStack, RevolverItem animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, red, green, blue, alpha);
        RevolverPose state = RevolverPose.current();
        if (!isReRender && state.world() != null && (renderPerspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || renderPerspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || renderPerspective == ItemDisplayContext.NONE)) {
            // Posisi dari tangan, arah dari dunia: putaran pandangan kamera dikalikan arah dunia revolver.
            Matrix4f m = poseStack.last().pose();
            Vector3f at = m.getTranslation(new Vector3f());
            if (state.at() != null) {
                // Posisi dunia penuh: ruang render = putaran pandangan x (posisi - kamera).
                Vec3 rel = state.at().subtract(MuzzleFx.cameraPos());
                at = MuzzleFx.viewRotation().transform(new Vector3f((float) rel.x, (float) rel.y, (float) rel.z));
            }
            Matrix3f rot = new Matrix3f(MuzzleFx.viewRotation()).rotate(state.world());
            m.identity().translate(at).mul(new Matrix4f().set(rot)).translate(HAND[3], HAND[4], HAND[5]);
            poseStack.last().normal().set(rot);
            poseStack.scale(HAND[6], HAND[6], HAND[6]);
        } else if (!isReRender && renderPerspective != null) {
            switch (renderPerspective) {
                case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> base(poseStack, HAND);
                case FIXED, GROUND -> base(poseStack, TABLE);
                case NONE, FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> base(poseStack, VIEW);
                default -> {
                }
            }
        }
        RevolverPose pose = state;
        model.getBone("cylinder").ifPresent(b -> b.setRotZ(pose.cylinder() * Mth.DEG_TO_RAD));
        // Crane mengayun keluar ke kiri revolver (sumbu -x render) sekitar 80 derajat.
        model.getBone("crane").ifPresent(b -> b.setRotZ(pose.open() * 80 * Mth.DEG_TO_RAD));
        model.getBone("hammer").ifPresent(b -> b.setRotX(pose.cock() * 34 * Mth.DEG_TO_RAD));
        model.getBone("trigger").ifPresent(b -> b.setRotX(-pose.trigger() * 22 * Mth.DEG_TO_RAD));
        model.getBone("bullet").ifPresent(b -> {
            b.setHidden(pose.bullet() < 0);
            // Keluar dari chamber ke belakang (+z model) sepanjang selongsong + kepala peluru.
            b.setPosZ(Math.max(0, pose.bullet()) * BULLET_LENGTH);
        });
    }

    /** Panjang peluru (piksel model), sama dengan tools/make_revolver.py BULLET_CASE + BULLET_NOSE. */
    public static final float BULLET_LENGTH = 3.6f;

    @Override
    public void renderRecursively(PoseStack poseStack, RevolverItem animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay, float red, float green,
                                  float blue, float alpha) {
        Vector3f sink = RevolverPose.muzzleSink();
        if (sink != null && "muzzle".equals(bone.getName())) {
            poseStack.pushPose();
            RenderUtils.prepMatrixForBone(poseStack, bone);
            poseStack.last().pose().transformPosition(0, 0, 0, sink);
            poseStack.popPose();
        }
        Vector3f[] chamber = RevolverPose.chamberSink();
        if (chamber != null && "bullet".equals(bone.getName())) {
            poseStack.pushPose();
            RenderUtils.prepMatrixForBone(poseStack, bone);
            // Titik asal bone = pivot (tengah lubang chamber di muka belakang), sudah termasuk geseran keluar.
            Vector3f pivot = new Vector3f(bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()).div(16);
            poseStack.last().pose().transformPosition(pivot.x, pivot.y, pivot.z, chamber[0]);
            poseStack.last().pose().transformPosition(pivot.x, pivot.y, pivot.z + 1 / 16f, chamber[1]);
            poseStack.popPose();
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }
}
