package com.liarsbar.client.render;

import com.liarsbar.client.ClientTable;
import com.liarsbar.client.cinematic.CameraDirector;
import com.liarsbar.client.cinematic.CameraPath;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.liarsbar.client.cinematic.RouletteTimeline;
import com.liarsbar.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderHandEvent;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pandangan penembak (orang pertama): tangan vanilla diganti revolver yang
 * diangkat dari bawah, dipegang di depan dada, lalu diangkat ke pelipis kanan sampai hanya ujungnya
 * terlihat di tepi layar, gemetar selama ditahan. Versi isi: tangan kiri
 * mengangkat peluru ke depan mata dulu, lalu memasukkannya ke chamber
 * yang terbuka.
 */
public final class FirstPersonGun {

    /** Pose pistol di ruang pandangan: geser x, y, z (blok) dan rotX, rotY, rotZ (derajat). */
    private record View(float x, float y, float z, float rx, float ry, float rz) {
        View lerp(View o, float w) {
            return new View(Mth.lerp(w, x, o.x), Mth.lerp(w, y, o.y), Mth.lerp(w, z, o.z), Mth.lerp(w, rx, o.rx),
                    Mth.lerp(w, ry, o.ry), Mth.lerp(w, rz, o.rz));
        }
    }

    private static final View BELOW = new View(0.40f, -0.70f, -0.62f, 12, 40, -10);
    private static final View CHEST = new View(0.16f, -0.26f, -0.52f, 8, 64, -6);
    /** Versi isi: laras ke depan (sedikit ke kiri bawah), silinder keluar ke kiri, ke tangan kiri. */
    private static final View LOAD = new View(0.08f, -0.25f, -0.52f, -6, 14, -6);
    /** Kepalan kiri (ujung tangan) dan peluru di depan mata, relatif pandangan. */
    private static final Vector3f SHOW = new Vector3f(-0.09f, -0.13f, -0.40f);
    private static final Vector3f LEFT_OUT = new Vector3f(-0.34f, -0.62f, -0.30f);
    private static final float ARM = 0.62f;
    private static final int TINT = 0xFFFFE6C8;
    /** Pantat peluru (dan satu piksel di belakangnya) di ruang pose tangan, dari render revolver frame ini. */
    private static final Vector3f[] CHAMBER = {new Vector3f(), new Vector3f()};
    /** Di pelipis: hanya bagian belakang revolver yang terlihat di tepi kanan layar (seperti game asli). */
    private static View TEMPLE = new View(0.40f, -0.12f, -0.32f, -6, 124, 8);

    /** AnimDev: setel pose pistol di pelipis (x y z rotX rotY rotZ) saat jalan. */
    public static void tuneTemple(float[] v) {
        TEMPLE = new View(v[0], v[1], v[2], v[3], v[4], v[5]);
    }

    private static ItemStack stack;

    private FirstPersonGun() {
    }

    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!RouletteDirector.localShooting() || mc.player == null || CameraDirector.cinematicWeight() > 0) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        int seat = ClientTable.mySeat();
        float partial = event.getPartialTick();
        RouletteDirector.Gun gun = RouletteDirector.gun(seat, partial);
        View view = view(RouletteDirector.act(seat, partial), partial);
        if (view == null || !gun.inHand()) {
            // Versi isi: peluru sudah di jari sebelum revolver diambil.
            CHAMBER[0].set(Float.NaN);
            loading(event.getPoseStack(), event.getMultiBufferSource(), mc.player, RouletteDirector.act(seat, partial),
                    -1, event.getPackedLight());
            return;
        }
        if (stack == null) {
            stack = new ItemStack(ModItems.REVOLVER.get());
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        float t = RouletteDirector.now(partial);
        float shake = holding(RouletteDirector.act(seat, partial)) ? 1 : 0;
        pose.translate(view.x + CameraPath.noise(t * 0.31, 1.7) * 0.004f * shake,
                view.y + CameraPath.noise(t * 0.27, 2.9) * 0.004f * shake, view.z);
        pose.mulPose(Axis.XP.rotationDegrees(view.rx));
        pose.mulPose(Axis.YP.rotationDegrees(view.ry));
        pose.mulPose(Axis.ZP.rotationDegrees(view.rz));
        // Hanya keadaan mekaniknya: posisi dan arah di pandangan diatur di sini, bukan dari dunia.
        RevolverPose p = gun.pose();
        CHAMBER[0].set(Float.NaN);
        RevolverPose.set(p.mechanics(), null, CHAMBER);
        try {
            // Lampu Veil tidak menerangi tangan orang pertama: cahaya minimal setara lampu meja.
            int light = event.getPackedLight();
            int block = Math.max(net.minecraft.client.renderer.LightTexture.block(light), 11);
            int sky = net.minecraft.client.renderer.LightTexture.sky(light);
            mc.getItemRenderer().renderStatic(mc.player, stack, ItemDisplayContext.NONE, false, pose,
                    event.getMultiBufferSource(), mc.level, net.minecraft.client.renderer.LightTexture.pack(block, sky),
                    OverlayTexture.NO_OVERLAY, 0);
        } finally {
            RevolverPose.clear();
        }
        pose.popPose();
        loading(pose, event.getMultiBufferSource(), mc.player, RouletteDirector.act(seat, partial), p.bullet(),
                event.getPackedLight());
    }

    /**
     * Versi isi: peluru di jari kiri naik dari bawah ke depan mata, diputar pelan, lalu dibawa ke mulut chamber
     * (titik dari render revolver frame ini, jadi tepat) dan didorong masuk; tangan lalu turun keluar pandangan.
     */
    private static void loading(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player,
                                RouletteDirector.Act act, float slide, int packed) {
        if (act.phase() != RouletteDirector.Phase.ROULETTE || !RouletteDirector.kind(act).equals("load")) {
            return;
        }
        float t = act.tick();
        int pick = RouletteTimeline.timing("load", "pick", 30), show = RouletteTimeline.timing("load", "show", 42);
        int insert = RouletteTimeline.timing("load", "insert", 80), seat = RouletteTimeline.timing("load", "seat", 88);
        if (t < pick + 1 || t > seat + 10) {
            return;
        }
        boolean chamber = Float.isFinite(CHAMBER[0].x);
        Vector3f out = chamber ? new Vector3f(CHAMBER[1]).sub(CHAMBER[0]) : new Vector3f(0, 1, 0);
        float px = Math.max(1e-5f, out.length());
        out.div(px);
        float len = RevolverRenderer.BULLET_LENGTH * px;
        // Pantat chamber saat peluru duduk penuh (CHAMBER sudah termasuk geseran keluar frame ini).
        Vector3f rear0 = chamber ? new Vector3f(CHAMBER[0]).sub(new Vector3f(out).mul(Math.max(0, slide) * len)) : null;
        // Ujung tangan (kepalan): naik dari bawah, ditahan di depan mata, ke belakang mulut chamber, mendorong, turun.
        Vector3f hand;
        float rise = smooth((t - (pick + 1)) / Math.max(1f, show - pick - 1));
        hand = new Vector3f(LEFT_OUT).lerp(SHOW, rise);
        hand.add(0, 0.004f * Mth.sin((t - show) * 0.3f) * smooth((t - show) / 4f), 0);
        float aim = smooth((t - (insert - 16)) / 12f);
        Vector3f axis = slerp(new Vector3f(1, 0.25f, 0.1f).normalize(), chamber ? new Vector3f(out).negate()
                : new Vector3f(0, -1, 0), aim);
        Vector3f base = new Vector3f(hand);
        boolean drawBullet = t < insert;
        if (chamber) {
            Vector3f mouth = new Vector3f(rear0).add(new Vector3f(out).mul(len));
            if (t < insert) {
                float k = smooth((t - (insert - 16)) / 16f);
                hand.lerp(new Vector3f(mouth).add(new Vector3f(out).mul(0.012f)), k);
            } else {
                float s = Math.max(0, slide);
                Vector3f push = new Vector3f(rear0).add(new Vector3f(out).mul(s * len + 0.012f));
                float off = smooth((t - seat) / 8f);
                hand = push.lerp(LEFT_OUT, off);
            }
            base = new Vector3f(hand).sub(new Vector3f(out).mul(0.012f));
        }
        int block = Math.max(net.minecraft.client.renderer.LightTexture.block(packed), 11);
        int light = net.minecraft.client.renderer.LightTexture.pack(block,
                net.minecraft.client.renderer.LightTexture.sky(packed));
        if (drawBullet) {
            // Pantat peluru di ujung jari, kepala menjauh ke arah sumbu.
            pose.pushPose();
            pose.translate(base.x, base.y, base.z);
            pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, axis.x, axis.y, axis.z).rotateY(t * 0.05f));
            pose.translate(0, -BulletRenderer.RIM_T, 0);
            BulletRenderer.draw(pose, buffers, light);
            pose.popPose();
        }
        // Lengan kiri: dari ujung tangan ke kiri bawah layar.
        Vector3f dir = new Vector3f(-0.42f, -0.80f, 0.42f).normalize();
        boolean slim = "slim".equals(player.getModelName());
        pose.pushPose();
        pose.translate(hand.x, hand.y, hand.z);
        pose.mulPose(new Quaternionf().rotationTo(0, -1, 0, dir.x, dir.y, dir.z));
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(player.getSkinTextureLocation()));
        float w = (slim ? 3 : 4) / 16f * ARM, d = 4 / 16f * ARM, length = 12 / 16f * ARM;
        // Ujung tangan sedikit di belakang pantat peluru (jari menjepit/mendorongnya).
        SkinBox.arm(vc, pose, light, TINT, slim, -w / 2, -length - 0.02f, -d / 2, w / 2, -0.02f, d / 2);
        pose.popPose();
    }

    private static Vector3f slerp(Vector3f a, Vector3f b, float u) {
        return new Vector3f(a).lerp(b, Mth.clamp(u, 0, 1)).normalize();
    }

    private static boolean holding(RouletteDirector.Act act) {
        if (act.phase() != RouletteDirector.Phase.ROULETTE) {
            return false;
        }
        return act.tick() >= RouletteTimeline.timing(RouletteDirector.kind(act), "temple", 40);
    }

    private static View view(RouletteDirector.Act act, float partial) {
        float t = act.tick();
        switch (act.phase()) {
            case ROULETTE -> {
                String kind = RouletteDirector.kind(act);
                int grab = RouletteTimeline.timing(kind, "grab", 12);
                int raise = RouletteTimeline.timing(kind, "raise", 18);
                int temple = RouletteTimeline.timing(kind, "temple", 40);
                if (t < grab - 2) {
                    return null;
                }
                View chest = CHEST;
                if (kind.equals("load")) {
                    chest = LOAD;
                }
                if (t < raise) {
                    return BELOW.lerp(chest, smooth((t - (grab - 2)) / 12f));
                }
                return chest.lerp(TEMPLE, smooth((t - raise) / Math.max(1f, temple - raise)));
            }
            case CLICK -> {
                if (t < 12) {
                    float jolt = (float) Math.sin(Math.PI * Mth.clamp(t / 5f, 0, 1)) * 0.02f;
                    return new View(TEMPLE.x + jolt, TEMPLE.y + jolt, TEMPLE.z, TEMPLE.rx, TEMPLE.ry, TEMPLE.rz);
                }
                return TEMPLE.lerp(BELOW, smooth((t - 12) / 22f));
            }
            case BANG -> {
                if (t > 14) {
                    return null;
                }
                return TEMPLE.lerp(new View(0.7f, -0.9f, -0.2f, 40, 120, 40), smooth(t / 12f));
            }
            default -> {
                return null;
            }
        }
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
