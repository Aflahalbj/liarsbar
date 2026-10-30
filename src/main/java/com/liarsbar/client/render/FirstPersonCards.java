package com.liarsbar.client.render;

import com.liarsbar.client.ClientTable;
import com.liarsbar.client.cinematic.CameraDirector;
import com.liarsbar.client.cinematic.DealDirector;
import com.liarsbar.client.cinematic.ThrowDirector;
import com.liarsbar.client.fx.MuzzleFx;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.liarsbar.client.ui.TableHud;
import com.liarsbar.game.deck.Card;
import com.liarsbar.table.PublicState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.event.RenderHandEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * Kartu di tangan (Art Bible, HUD diegetik): kipas 5 kartu 3D digenggam tangan kiri di bawah tengah pandangan.
 * Telapak dan jari di belakang pangkal kipas, jempol menjepit pangkalnya dari depan, lengan bawah masuk dari
 * kiri bawah layar. Kartu yang ditunjuk naik, kartu yang ditandai miring dengan tepi kuningan. Saat bukan
 * giliran sendiri kipas diturunkan sedikit supaya meja terlihat.
 * <p>
 * Tiap frame sudut tiap kartu diproyeksikan ke piksel jendela, jadi kursor mouse bisa menunjuk kartu
 * ({@link #cardAt}). Lampu Veil tidak menerangi tangan orang pertama, jadi kartu diberi cahaya hangat setara
 * lampu kursi sendiri.
 */
public final class FirstPersonCards {

    /** Ukuran kartu di ruang pandangan (rasio 2:3 seperti tekstur 32x48). */
    private static final float W = 0.13f, H = 0.195f;
    /** Pangkal kipas (di genggaman) di ruang pandangan: x kanan, y atas, z ke depan negatif. */
    private static final float PX = 0.0f, PY = -0.36f, PZ = -0.56f;
    private static final float SPREAD = 15f;
    /** Geser samping per kartu supaya indeks pojok tiap kartu terbaca. */
    private static final float STEP = 0.028f;
    /** Bagian bawah kartu yang masuk ke genggaman. */
    private static final float SINK = 0.02f;
    /** Kipas turun sebanyak ini saat bukan giliran sendiri. */
    private static final float IDLE_DROP = 0.06f;
    /** Skala lengan orang pertama terhadap ukuran model (1 piksel skin = 1/16 * skala). */
    private static final float ARM = 0.62f;
    private static final int TINT = 0xFFFFE6C8;
    private static final int BRASS = 0xFFD2A866;

    /** Posisi naik kartu per indeks (dihaluskan per frame). */
    private static float[] lift = new float[0];
    private static float[] mark = new float[0];
    private static float show;
    private static float raised;

    /** Sudut tiap kartu di piksel jendela, {x0,y0,..,x3,y3} per kartu, untuk penunjuk mouse. */
    private static float[][] quads = new float[0][];
    private static int quadCount;
    private static final Matrix4f CLIP = new Matrix4f();
    private static final Matrix4f TO_CLIP = new Matrix4f();
    private static final Vector4f CORNER = new Vector4f();

    private FirstPersonCards() {
    }

    public static boolean wants() {
        PublicState state = ClientTable.state();
        int seat = ClientTable.mySeat();
        return state != null && seat >= 0 && seat < state.seats.size() && state.seats.get(seat).alive
                && (!ClientTable.hand().isEmpty() || throwing()) && state.stage != PublicState.Stage.LOBBY
                && !RouletteDirector.localShooting() && CameraDirector.cinematicWeight() <= 0
                && DealDirector.visibleCards(seat, 1, Minecraft.getInstance().getFrameTime()) > 0;
    }

    // ------------------------------------------------------------ lempar (orang pertama)

    /** Kartu yang sedang dicabut dan dilempar, indeksnya di kipas lama, dan ukuran kipas lama. */
    private static List<Card> thrown = List.of();
    private static int[] thrownAt = new int[0];
    private static int thrownOf;
    private static final Set<Integer> THROWN_IDS = new java.util.HashSet<>();

    /** Dari ClientTable saat klik lempar (sebelum pilihan dikosongkan). */
    public static void beginThrow(Set<Integer> ids) {
        List<Card> hand = ClientTable.hand();
        List<Card> cards = new java.util.ArrayList<>();
        int[] at = new int[ids.size()];
        for (int i = 0; i < hand.size(); i++) {
            if (ids.contains(hand.get(i).id()) && cards.size() < at.length) {
                at[cards.size()] = i;
                cards.add(hand.get(i));
            }
        }
        thrown = cards;
        thrownAt = at;
        thrownOf = hand.size();
        THROWN_IDS.clear();
        THROWN_IDS.addAll(ids);
    }

    private static float throwAge(float partial) {
        return ClientTable.now() + partial - ThrowDirector.povClick();
    }

    private static boolean throwing() {
        return !thrown.isEmpty() && throwAge(0) < ThrowDirector.t("length", 30);
    }

    /** Kartu teratas di bawah titik jendela (piksel layar, seperti posisi mouse), atau -1. */
    public static int cardAt(double x, double y) {
        if (!wants()) {
            return -1;
        }
        for (int i = quadCount - 1; i >= 0; i--) {
            if (inside(quads[i], (float) x, (float) y)) {
                return i;
            }
        }
        return -1;
    }

    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !wants()) {
            show = 0;
            quadCount = 0;
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        float partial = mc.getFrameTime();
        Set<Integer> selected = ClientTable.selected();
        float age = throwAge(partial);
        boolean flying = throwing();
        // Kartu yang sedang dicabut tidak ikut kipas (walau server belum mengirim tangan baru).
        List<Card> all = ClientTable.hand();
        List<Card> hand = all;
        if (flying) {
            hand = new java.util.ArrayList<>(all.size());
            for (Card c : all) {
                if (!THROWN_IDS.contains(c.id())) {
                    hand.add(c);
                }
            }
        }
        int n = hand.size();
        if (lift.length != n) {
            lift = new float[n];
            mark = new float[n];
        }
        if (quads.length < n) {
            quads = new float[n][8];
        }
        float dt = mc.getDeltaFrameTime();
        show = Math.min(1, show + dt * 0.08f);
        boolean myTurn = ClientTable.myTurn();
        float k = 1 - (float) Math.pow(0.001, dt / 20f * 6);
        raised += ((myTurn ? 1 : 0) - raised) * k * 0.5f;
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = LightTexture.pack(13, 0);
        float in = 1 - (1 - show) * (1 - show);
        TO_CLIP.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());
        boolean slim = "slim".equals(mc.player.getModelName());
        // Baru diambil dari felt: paket naik dari tempatnya di meja ke genggaman sambil kipasnya dibuka.
        float[] pickup = DealDirector.pickup(ClientTable.mySeat(), partial);
        float spread = pickup == null ? 1 : pickup[1];
        float up = pickup == null ? 1 : pickup[0];
        if (pickup != null) {
            in = 1;
            show = 1;
        }
        pose.pushPose();
        FAN.identity().translate(PX, PY - 0.25f * (1 - in) - IDLE_DROP * (1 - raised), PZ)
                .rotateX(-14 * Mth.DEG_TO_RAD).rotateZ(5 * Mth.DEG_TO_RAD);
        if (pickup != null && up < 1 && packetView(pickup[3])) {
            FAN.getTranslation(T_FAN);
            FAN.getUnnormalizedRotation(Q_FAN);
            PACKET.getTranslation(T_PK);
            PACKET.getUnnormalizedRotation(Q_PK);
            T_PK.lerp(T_FAN, up);
            Q_PK.slerp(Q_FAN, up);
            FAN.translationRotate(T_PK.x, T_PK.y, T_PK.z, Q_PK);
        }
        pose.last().pose().mul(FAN);
        pose.last().normal().rotate(FAN.getUnnormalizedRotation(Q_TMP));
        if (up >= 1) {
            palm(pose, buffers, mc.player, light, slim);
        }
        for (int i = 0; i < n; i++) {
            Card card = hand.get(i);
            // Naik hanya selama di-hover mouse (atau ditunjuk keyboard/roda); lepas hover = turun lagi.
            boolean cursor = myTurn && i == ClientTable.cursor()
                    && (com.liarsbar.client.TableMouse.hovered() == i || ClientTable.keyCursor());
            boolean picked = selected.contains(card.id());
            lift[i] += ((cursor ? 0.05f : 0) - lift[i]) * k;
            mark[i] += ((picked ? 1 : 0) - mark[i]) * k;
            // Sudut dan geser tiap kartu dihaluskan per kartu: sisa kipas merapat pelan sesudah kartu dicabut.
            float wantAngle = ((n - 1) / 2f - i) * SPREAD * spread, wantX = (i - (n - 1) / 2f) * STEP * spread;
            float[] sl = SLOTS.computeIfAbsent(card.id(), id -> new float[]{wantAngle, wantX});
            sl[0] += (wantAngle - sl[0]) * k * 0.6f;
            sl[1] += (wantX - sl[1]) * k * 0.6f;
            if (pickup != null) {
                sl[0] = wantAngle;
                sl[1] = wantX;
            }
            pose.pushPose();
            pose.translate(sl[1], 0, 0.003f * i);
            pose.mulPose(Axis.ZP.rotationDegrees(sl[0]));
            pose.translate(0, -SINK + lift[i], 0);
            pose.mulPose(Axis.XP.rotationDegrees(-8 * mark[i]));
            pose.mulPose(Axis.ZP.rotationDegrees(-6 * mark[i]));
            if (mark[i] > 0.02f) {
                // Tepi kuningan di belakang kartu yang ditandai.
                VertexConsumer edge = buffers.getBuffer(RenderType.entityCutout(new net.minecraft.resources.ResourceLocation(
                        "minecraft", "textures/block/gold_block.png")));
                card(edge, pose, light, (Math.round(mark[i] * 255) << 24) | (BRASS & 0xFFFFFF), 0.012f, -0.0015f);
            }
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(TableHud.cardTexture(card.rank())));
            card(vc, pose, light, TINT, 0, 0);
            project(pose.last().pose(), quads[i]);
            pose.popPose();
        }
        if (flying) {
            pinch(pose, buffers, mc.player, light, slim, age);
        }
        quadCount = n;
        if (up >= 1) {
            thumb(pose, buffers, mc.player, light, slim);
        }
        pose.popPose();
        if (up < 1) {
            // Tangan kiri naik masuk pandangan menyambut paket.
            pose.pushPose();
            pose.translate(PX, PY - 0.35f * (1 - up), PZ);
            pose.mulPose(Axis.XP.rotationDegrees(-14));
            pose.mulPose(Axis.ZP.rotationDegrees(5));
            palm(pose, buffers, mc.player, light, slim);
            pose.popPose();
        }
        if (SLOTS.size() > 32) {
            SLOTS.clear();
        }
    }

    private static final java.util.Map<Integer, float[]> SLOTS = new java.util.HashMap<>();
    private static final Matrix4f FAN = new Matrix4f();
    private static final Matrix4f PACKET = new Matrix4f();
    private static final Vector3f T_FAN = new Vector3f(), T_PK = new Vector3f();
    private static final Quaternionf Q_FAN = new Quaternionf(), Q_PK = new Quaternionf(), Q_TMP = new Quaternionf();

    /**
     * Pose paket di felt dalam ruang pandangan (pangkal kartu di titik asal, tinggi +y, muka +z), di PACKET.
     * Tangan orang pertama memakai FOV 70 tetap; dengan FOV bawaan 70 posisinya cocok dengan dunia.
     */
    private static boolean packetView(float yaw) {
        Vec3 packet = DealDirector.packetAt(ClientTable.mySeat());
        if (packet == null) {
            return false;
        }
        Quaternionf view = new Quaternionf().setFromNormalized(MuzzleFx.viewRotation());
        Quaternionf world = TableCards.flat(yaw).mul(new Quaternionf().setFromNormalized(
                new org.joml.Matrix3f(-1, 0, 0, 0, 0, 1, 0, 1, 0)));
        Vector3f half = world.transform(new Vector3f(0, H / 2, 0));
        Vec3 cam = MuzzleFx.cameraPos();
        Vector3f p = new Vector3f((float) (packet.x - cam.x - half.x), (float) (packet.y - cam.y - half.y),
                (float) (packet.z - cam.z - half.z));
        MuzzleFx.viewRotation().transform(p);
        PACKET.translationRotate(p.x, p.y, p.z, view.mul(world));
        return true;
    }

    /**
     * Tangan kanan mencabut kartu yang dimainkan dari sisi kanan kipas, menariknya ke kanan atas, lalu
     * menjentikkannya ke depan bawah (ke meja). Di titik lepas kartu dunia mengambil alih (ThrowDirector).
     */
    private static void pinch(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, int light,
                              boolean slim, float t) {
        int pinchT = ThrowDirector.t("pinch", 8), draw = ThrowDirector.t("draw", 12);
        int release = ThrowDirector.t("release", 15);
        int count = thrown.size();
        Vector3f fistAt = new Vector3f();
        Matrix4f group = new Matrix4f(pose.last().pose());
        if (t < release) {
            float pull = smooth((t - pinchT) / Math.max(1f, draw - pinchT));
            float flick = Mth.clamp((t - draw) / Math.max(1f, release - draw), 0, 1);
            flick = flick * flick;
            for (int j = 0; j < count; j++) {
                int at = thrownAt[j];
                float angle = ((thrownOf - 1) / 2f - at) * SPREAD;
                float x = (at - (thrownOf - 1) / 2f) * STEP;
                pose.pushPose();
                // Dari tempatnya di kipas: naik keluar dari kipas ke kanan, lalu dijentik menjauh dan direbahkan.
                pose.translate(Mth.lerp(pull, x, 0.10f + j * 0.012f), pull * 0.10f - flick * 0.20f,
                        0.004f * at + 0.02f * pull - flick * 0.30f);
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pull, angle, -12 + j * 5)));
                pose.translate(0, -SINK, 0);
                pose.mulPose(Axis.XP.rotationDegrees(-75 * flick));
                VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(TableHud.cardTexture(thrown.get(j).rank())));
                card(vc, pose, light, TINT, 0, 0);
                if (j == 0) {
                    pose.last().pose().transformPosition(W * 0.3f, H * 0.72f, 0, fistAt);
                    Vector3f world = new Vector3f(fistAt);
                    MuzzleFx.viewInverse().transformPosition(world);
                    Vec3 cam = MuzzleFx.cameraPos();
                    ThrowDirector.povRelease(cam.add(world.x, world.y, world.z));
                }
                pose.popPose();
            }
        }
        // Lengan kanan: masuk dari kanan bawah ke jepitan, ikut kartu, lalu turun keluar pandangan.
        Vector3f off = new Vector3f(0.42f, -0.62f, -0.20f);
        Vector3f pinchAt = group.transformPosition(0.10f, 0.10f + H * 0.72f - SINK, 0.02f, new Vector3f());
        Vector3f p;
        if (t < pinchT) {
            p = new Vector3f(off).lerp(pinchAt, smooth(t / Math.max(1f, pinchT - 1)));
        } else if (t < release && count > 0) {
            p = fistAt;
        } else {
            float u = smooth((t - release) / 8f);
            p = new Vector3f(0.12f, -0.32f, -0.62f).lerp(off, u);
        }
        if (t > release + 8) {
            return;
        }
        Vector3f dir = new Vector3f(0.50f, -0.75f, 0.35f).normalize();
        pose.pushPose();
        pose.last().pose().identity();
        pose.last().normal().identity();
        pose.translate(p.x, p.y, p.z);
        pose.mulPose(new Quaternionf().rotationTo(0, -1, 0, dir.x, dir.y, dir.z));
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(player.getSkinTextureLocation()));
        float w = (slim ? 3 : 4) / 16f * ARM, d = 4 / 16f * ARM, len = 12 / 16f * ARM;
        SkinBox.arm(vc, pose, light, TINT, slim, -w / 2, -len, -d / 2, w / 2, 0.02f, d / 2);
        pose.popPose();
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** Satu kartu berdiri di bidang XY, pangkal bawah-tengah di titik asal, menghadap +z (ke mata). */
    private static void card(VertexConsumer vc, PoseStack pose, int light, int color, float grow, float z) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float hw = W / 2 + grow, y0 = -grow, y1 = H + grow;
        Geo.vertex(vc, m, n, 0, 0, 1, light, color, -hw, y1, z, 0, 0);
        Geo.vertex(vc, m, n, 0, 0, 1, light, color, -hw, y0, z, 0, 1);
        Geo.vertex(vc, m, n, 0, 0, 1, light, color, hw, y0, z, 1, 1);
        Geo.vertex(vc, m, n, 0, 0, 1, light, color, hw, y1, z, 1, 0);
    }

    /** Sudut kartu (ruang pose sekarang) -> piksel jendela. */
    private static void project(Matrix4f pose, float[] out) {
        var window = Minecraft.getInstance().getWindow();
        float hw = W / 2;
        CLIP.set(TO_CLIP).mul(pose);
        float[] xs = {-hw, -hw, hw, hw};
        float[] ys = {H, 0, 0, H};
        for (int c = 0; c < 4; c++) {
            CLIP.transform(CORNER.set(xs[c], ys[c], 0, 1));
            float w = Math.max(1e-4f, CORNER.w);
            out[c * 2] = (CORNER.x / w * 0.5f + 0.5f) * window.getScreenWidth();
            out[c * 2 + 1] = (0.5f - CORNER.y / w * 0.5f) * window.getScreenHeight();
        }
    }

    /** Titik di dalam segi empat cembung (urutan sudut keliling, arah mana pun). */
    private static boolean inside(float[] q, float x, float y) {
        int sign = 0;
        for (int c = 0; c < 4; c++) {
            float ax = q[c * 2], ay = q[c * 2 + 1];
            float bx = q[(c + 1) % 4 * 2], by = q[(c + 1) % 4 * 2 + 1];
            float cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax);
            int s = cross > 0 ? 1 : cross < 0 ? -1 : 0;
            if (s != 0) {
                if (sign != 0 && s != sign) {
                    return false;
                }
                sign = s;
            }
        }
        return true;
    }

    /**
     * Telapak + lengan bawah kiri: ujung tangan tepat di belakang pangkal kipas (jari menahan kartu dari
     * belakang), lengan turun ke arah mata dan ke kiri bawah layar.
     */
    private static void palm(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, int light,
                             boolean slim) {
        pose.pushPose();
        pose.translate(-0.012f, 0.004f, -0.042f);
        pose.mulPose(Axis.ZP.rotationDegrees(-24));
        pose.mulPose(Axis.XP.rotationDegrees(-58));
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(player.getSkinTextureLocation()));
        float w = (slim ? 3 : 4) / 16f * ARM, d = 4 / 16f * ARM, len = 12 / 16f * ARM;
        SkinBox.arm(vc, pose, light, TINT, slim, -w / 2, -len, -d / 2, w / 2, 0, d / 2);
        pose.popPose();
    }

    /** Jempol kiri menjepit pangkal kipas dari depan, condong ke kanan atas. */
    private static void thumb(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, int light,
                              boolean slim) {
        pose.pushPose();
        // Di depan kartu terdepan (z 0,012) plus setengah tebal jempol, supaya tidak menembus kartu.
        pose.translate(-0.03f, -0.035f, 0.052f);
        pose.mulPose(Axis.ZP.rotationDegrees(-38));
        pose.mulPose(Axis.XP.rotationDegrees(-12));
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(player.getSkinTextureLocation()));
        float s = 1.8f / 16f * ARM, len = 2.9f / 16f * ARM;
        SkinBox.arm(vc, pose, light, TINT, slim, -s / 2, 0, -s / 2, s / 2, len, s / 2, 3);
        pose.popPose();
    }
}
