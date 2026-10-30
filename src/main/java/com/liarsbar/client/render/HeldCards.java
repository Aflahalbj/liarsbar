package com.liarsbar.client.render;

import com.liarsbar.client.ClientTable;
import com.liarsbar.client.cinematic.DealDirector;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.liarsbar.client.cinematic.ThrowDirector;
import com.liarsbar.game.deck.LiarsDeckRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Kartu yang dipegang, digambar di kepalan yang benar-benar dirender (bukan di sasaran IK), jadi selalu
 * menempel di tangan walau lengan tidak sampai: kartu di jepitan tangan kanan saat melempar, dek di tangan
 * kiri dealer, paket yang sedang dilempar dealer, dan peluru di jari kiri saat revolver diisi. Posisi kepalan tiap kursi juga direkam di sini untuk
 * titik berangkat kartu terbang dan sasaran IK (kipas kiri).
 */
public final class HeldCards {

    /** Pangkal kipas di atas tengah kepalan (blok): sebagian bawah kartu tenggelam di genggaman. */
    public static final float GRIP_UP = 0.085f;
    /** Rekaman lebih tua dari ini (tick) dianggap tidak ada (pemain tidak digambar). */
    private static final int STALE = 6;

    private static final Vec3[] LEFT = new Vec3[LiarsDeckRules.MAX_PLAYERS];
    private static final Vec3[] RIGHT = new Vec3[LiarsDeckRules.MAX_PLAYERS];
    private static final Vec3[] REST = new Vec3[LiarsDeckRules.MAX_PLAYERS];
    private static final long[] AT = new long[LiarsDeckRules.MAX_PLAYERS];

    private HeldCards() {
    }

    /** Dari HandCardsLayer tiap frame. {@code leftFree}: lengan kiri tidak sedang dikunci IK (pose istirahat). */
    static void record(int seat, Vec3 left, Vec3 right, boolean leftFree) {
        if (seat < 0 || seat >= LEFT.length) {
            return;
        }
        LEFT[seat] = left;
        RIGHT[seat] = right;
        if (leftFree) {
            REST[seat] = left;
        }
        AT[seat] = ClientTable.now();
    }

    private static Vec3 fresh(Vec3[] values, int seat) {
        if (seat < 0 || seat >= values.length || ClientTable.now() - AT[seat] > STALE) {
            return null;
        }
        return values[seat];
    }

    public static Vec3 leftNow(int seat) {
        return fresh(LEFT, seat);
    }

    public static Vec3 rightNow(int seat) {
        return fresh(RIGHT, seat);
    }

    /** Kepalan kiri terakhir saat tidak dikunci IK (tempat kipas kartu): sasaran akhir ambil paket. */
    public static Vec3 restLeft(int seat) {
        return seat < 0 || seat >= REST.length ? null : REST[seat];
    }

    /**
     * Gambar kartu di tangan kursi ini. Pose sudah di ruang dunia relatif kamera (titik asal = kamera, tanpa
     * putaran pandangan dibatalkan; lihat HandCardsLayer).
     */
    static void render(int seat, PoseStack pose, MultiBufferSource buffers, int light, Vec3 cam, Vec3 left, Vec3 right,
                       float partial) {
        RouletteDirector.Frame f = RouletteDirector.frame(seat);
        if (f == null) {
            return;
        }
        RouletteDirector.HeldBullet bullet = RouletteDirector.heldBullet(seat, left, partial);
        if (bullet != null) {
            Vec3 base = bullet.center().subtract(bullet.axis().scale(BulletRenderer.LENGTH / 2));
            BulletRenderer.render(pose, buffers, light, cam, base, bullet.axis(), 0);
        }
        VertexConsumer vc = TableCards.backs(buffers);
        int pinched = ThrowDirector.inHand(seat, partial);
        Vec3 aim = ThrowDirector.aim(seat);
        if (pinched > 0 && aim != null) {
            Quaternionf q = ThrowDirector.held(f, aim, ThrowDirector.since(seat, partial));
            fan(vc, pose, light, cam, right, q, pinched, 0.012f, 6);
        }
        float[] toss = DealDirector.rightHeld(seat, partial);
        if (toss != null) {
            Quaternionf q = TableCards.flat(toss[1]);
            Vector3f c = q.transform(new Vector3f(0, 0, ThrowDirector.HELD_FORWARD));
            Vec3 at = right.add(c.x, c.y - 0.06, c.z);
            for (int i = 0; i < (int) toss[0]; i++) {
                pose.pushPose();
                TableCards.place(pose, cam, at, q);
                pose.translate(0.006f * i, i * TableCards.THICK, 0.004f * i);
                TableCards.card(vc, pose, light);
                pose.popPose();
            }
        }
        int deck = DealDirector.deckInLeft(seat, partial);
        if (deck > 0) {
            // Dek di telapak kiri, sumbu panjang ke depan agak serong ke kanan, sedikit dimiringkan ke tangan kanan.
            Vec3 dir = f.forward().scale(Mth.cos(0.26f)).add(f.right().scale(Mth.sin(0.26f)));
            Quaternionf q = TableCards.flat(TableCards.yawOf(dir.x, dir.z)).rotateZ(-0.17f);
            Vec3 at = left.add(0, DealDirector.DECK_UP, 0).add(f.forward().scale(0.04));
            float lift = DealDirector.deckLift(partial);
            Vec3 felt = DealDirector.deckOnFelt();
            if (lift < 1 && felt != null) {
                at = felt.lerp(at, lift);
                q = TableCards.flat(DealDirector.deckYaw()).slerp(q, lift);
            }
            pose.pushPose();
            TableCards.place(pose, cam, at, q);
            TableCards.deck(buffers, pose, light, deck);
            pose.popPose();
        }
    }

    /** Beberapa kartu di jepitan, sedikit dikipas. */
    private static void fan(VertexConsumer vc, PoseStack pose, int light, Vec3 cam, Vec3 fist, Quaternionf q, int n,
                            float step, float degrees) {
        Vector3f c = q.transform(new Vector3f(0, 0, ThrowDirector.HELD_FORWARD));
        for (int i = 0; i < n; i++) {
            pose.pushPose();
            TableCards.place(pose, cam, fist.add(c.x, c.y, c.z), q);
            pose.translate((i - (n - 1) / 2f) * step, i * 0.002f, 0);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees((i - (n - 1) / 2f) * degrees));
            TableCards.card(vc, pose, light);
            pose.popPose();
        }
    }
}
