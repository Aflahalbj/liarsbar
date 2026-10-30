package com.liarsbar.client.cinematic;

import com.liarsbar.client.ClientTable;
import com.liarsbar.client.render.HeldCards;
import com.liarsbar.client.render.TableCards;
import com.liarsbar.game.deck.GameEvent;
import com.liarsbar.network.TableEventS2C;
import com.liarsbar.sound.ModSounds;
import com.liarsbar.table.PublicState;
import com.liarsbar.table.SeatLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Main kartu: pemain menunduk ke kipas di tangan kiri, tangan kanan (IK) menjepit kartu yang dimainkan,
 * mencabutnya ke kanan atas, ancang-ancang, lalu menjentikkannya ke tengah meja. Kartu terbang berputar,
 * mendarat, meluncur, dan berhenti berserakan acak di sekitar stand table card (bukan dek rapi).
 * <p>
 * Waktu dari roulette.json ("throw"), sama dengan {@link com.liarsbar.table.TableTiming#THROW} di server.
 * Untuk pemain lokal di orang pertama, lemparannya dimulai saat klik (FirstPersonCards), kartu dunia
 * berangkat dari titik lepas di pandangan.
 */
public final class ThrowDirector {

    /** Satu kartu yang berbaring di felt (ruang lokal meja, relatif pusat meja). */
    public static final class Lying {
        public final float x, z, yaw;
        public final int layer;
        /** Kartu ini sedang dibuka (digambar di baris reveal, bukan di tumpukan). */
        public boolean revealed;

        Lying(float x, float z, float yaw, int layer) {
            this.x = x;
            this.z = z;
            this.yaw = yaw;
            this.layer = layer;
        }
    }

    private static final class Flight {
        final int seat;
        final int slot;
        final Lying target;
        final float launch;
        final float spin;
        Vec3 from;
        Quaternionf fromQ;
        float duration;

        Flight(int seat, int slot, Lying target, float launch, float spin) {
            this.seat = seat;
            this.slot = slot;
            this.target = target;
            this.launch = launch;
            this.spin = spin;
        }
    }

    private static final class Throw {
        final int seat;
        final int count;
        final float start;
        final boolean pov;
        /** Arah lempar (horizontal, ke kartu pertama). */
        final Vec3 aim;

        Throw(int seat, int count, float start, boolean pov, Vec3 aim) {
            this.seat = seat;
            this.count = count;
            this.start = start;
            this.pov = pov;
            this.aim = aim;
        }
    }

    private static int tableId = -1;
    private static final List<Lying> PILE = new ArrayList<>();
    private static final List<Flight> FLIGHTS = new ArrayList<>();
    private static final List<Throw> THROWS = new ArrayList<>();
    /** Indeks kartu pertama dari lemparan terakhir di PILE+FLIGHTS (urutan layer), dan jumlahnya. */
    private static int lastStart, lastCount;
    private static int layers;
    /** Klik lempar pemain lokal (tick client), untuk menyamakan awal lemparan dengan animasi orang pertama. */
    private static float povClick = Float.NEGATIVE_INFINITY;
    private static Vec3 povRelease;

    private ThrowDirector() {
    }

    public static int t(String key, int fallback) {
        return RouletteTimeline.timing("throw", key, fallback);
    }

    // ============================================================ kejadian

    public static void onEvent(TableEventS2C msg) {
        if (msg.tableId() != tableId) {
            tableId = msg.tableId();
            clear();
        }
        GameEvent event = msg.event();
        float now = ClientTable.now();
        if (event instanceof GameEvent.RoundStarted) {
            clear();
        } else if (event instanceof GameEvent.CardsPlayed e) {
            boolean local = e.seat() == ClientTable.mySeat();
            boolean pov = local && firstPerson();
            float start = local && now - povClick < 20 ? povClick : now;
            begin(e.seat(), e.count(), start, pov);
        } else if (event instanceof GameEvent.Revealed) {
            // Kartu lemparan terakhir pindah ke baris reveal (TableRenderer.revealed menggesernya dari sini).
            for (Flight f : FLIGHTS) {
                land(f);
            }
            FLIGHTS.clear();
            for (int i = 0; i < PILE.size(); i++) {
                Lying l = PILE.get(i);
                if (l.layer >= lastStart && l.layer < lastStart + lastCount) {
                    l.revealed = true;
                }
            }
        }
    }

    private static void clear() {
        PILE.clear();
        FLIGHTS.clear();
        THROWS.clear();
        layers = 0;
        lastStart = 0;
        lastCount = 0;
    }

    /** Pemain lokal mengklik lempar (sebelum server menjawab): animasi orang pertama mulai sekarang. */
    public static void localThrow() {
        povClick = ClientTable.now();
        povRelease = null;
    }

    public static float povClick() {
        return povClick;
    }

    /** Dari FirstPersonCards tiap frame: titik dunia kartu di jepitan tangan kanan orang pertama. */
    public static void povRelease(Vec3 at) {
        povRelease = at;
    }

    private static boolean firstPerson() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options.getCameraType().isFirstPerson() && CameraDirector.cinematicWeight() <= 0;
    }

    /** AnimDev: lemparan palsu dari kursi ini (hanya di client). */
    public static void debugThrow(int seat, int count) {
        begin(seat, count, ClientTable.now(), seat == ClientTable.mySeat() && firstPerson());
    }

    private static void begin(int seat, int count, float start, boolean pov) {
        PublicState state = ClientTable.state();
        RouletteDirector.Frame f = RouletteDirector.frame(seat);
        if (state == null || f == null || count <= 0) {
            return;
        }
        float r = SeatLayout.Size.forSeats(state.seats.size()).radius;
        RandomSource rng = RandomSource.create(tableId * 7919L + state.round * 104729L + layers * 31L + seat);
        Vec3 out = f.forward().scale(-1);
        lastStart = layers;
        lastCount = count;
        float release = t("release", 15);
        Lying first = null;
        for (int j = 0; j < count; j++) {
            Lying target = first == null ? spot(rng, out, r, null) : spot(rng, out, r, first);
            if (first == null) {
                first = target;
            }
            // Kartu di jepitan dilepas hampir bersamaan, sedikit berurutan; putaran tambahan acak arah.
            float spin = (rng.nextBoolean() ? 1 : -1) * (120 + rng.nextFloat() * 260);
            FLIGHTS.add(new Flight(seat, j, target, start + release + j * 0.6f, spin));
        }
        var table = RouletteDirector.tableEntity();
        Vec3 aim = table == null ? f.forward()
                : table.position().add(first.x, 0, first.z).subtract(f.eye()).multiply(1, 0, 1).normalize();
        THROWS.add(new Throw(seat, count, start, pov, aim));
    }

    /**
     * Titik jatuh acak di felt sekitar stand table card: condong ke sisi pelempar, menjauhi stand dan jam pasir,
     * di dalam bibir meja. Kartu kedua dan seterusnya dari lemparan yang sama jatuh berdekatan (saling tumpang).
     */
    private static Lying spot(RandomSource rng, Vec3 out, float r, Lying near) {
        double base = Math.atan2(out.z, out.x);
        float x = 0, z = 0;
        for (int tries = 0; tries < 60; tries++) {
            if (near == null) {
                double a = base + (rng.nextFloat() * 2 - 1) * 1.15;
                double d = 0.17 + rng.nextFloat() * Math.min(0.30, r * 0.22);
                x = (float) (Math.cos(a) * d);
                z = (float) (Math.sin(a) * d);
            } else {
                double a = rng.nextFloat() * Math.PI * 2;
                double d = 0.035 + rng.nextFloat() * 0.05;
                x = near.x + (float) (Math.cos(a) * d);
                z = near.z + (float) (Math.sin(a) * d);
            }
            boolean clearStand = x * x + z * z > 0.15f * 0.15f;
            float hx = x - 0.42f, hz = z - 0.12f;
            boolean clearGlass = hx * hx + hz * hz > 0.18f * 0.18f;
            boolean inside = x * x + z * z < (r - 0.36f) * (r - 0.36f);
            if (clearStand && clearGlass && inside) {
                break;
            }
        }
        float yaw = near == null ? rng.nextFloat() * 360 : near.yaw + (rng.nextFloat() * 2 - 1) * 40;
        return new Lying(x, z, yaw, layers++);
    }

    // ============================================================ tick

    public static void tick() {
        PublicState state = ClientTable.state();
        if (state == null || state.tableId != tableId) {
            if (state != null && state.tableId != tableId) {
                tableId = state.tableId;
                clear();
            }
            return;
        }
        if (state.stage == PublicState.Stage.LOBBY || state.stage == PublicState.Stage.DEALING) {
            if (!PILE.isEmpty() || !FLIGHTS.isEmpty()) {
                clear();
            }
            THROWS.clear();
            return;
        }
        float now = ClientTable.now();
        for (Flight f : FLIGHTS) {
            if (f.from == null && now >= f.launch) {
                launch(f);
            }
        }
        for (Iterator<Flight> it = FLIGHTS.iterator(); it.hasNext(); ) {
            Flight f = it.next();
            if (f.from != null && now - f.launch >= f.duration) {
                land(f);
                it.remove();
            }
        }
        THROWS.removeIf(th -> now - th.start > t("length", 30) + 2);
        // Baru melihat meja di tengah ronde: kartu yang sudah di meja langsung berserakan.
        if (THROWS.isEmpty() && FLIGHTS.isEmpty() && PILE.size() < state.pile) {
            RandomSource rng = RandomSource.create(tableId * 31L + state.round);
            float r = SeatLayout.Size.forSeats(state.seats.size()).radius;
            while (PILE.size() < state.pile) {
                double a = rng.nextFloat() * Math.PI * 2;
                PILE.add(spot(rng, new Vec3(Math.cos(a), 0, Math.sin(a)), r, null));
            }
        }
    }

    private static void launch(Flight f) {
        RouletteDirector.Frame frame = RouletteDirector.frame(f.seat);
        var table = RouletteDirector.tableEntity();
        if (frame == null || table == null) {
            f.from = Vec3.ZERO;
            f.duration = 0;
            return;
        }
        Throw th = throwOf(f.seat);
        Vec3 aim = th == null ? frame.forward() : th.aim;
        float aimYaw = TableCards.yawOf(aim.x, aim.z);
        Vec3 offset = new Vec3(aim.z, 0, -aim.x).scale((f.slot - 0.5) * 0.012);
        if (th != null && th.pov) {
            Vec3 at = povRelease;
            if (at == null) {
                Minecraft mc = Minecraft.getInstance();
                var cam = mc.gameRenderer.getMainCamera();
                Vector3f look = cam.getLookVector();
                at = cam.getPosition().add(look.x() * 0.55, look.y() * 0.55 - 0.3, look.z() * 0.55);
            }
            f.from = at.add(offset);
            f.fromQ = new Quaternionf().rotationY(aimYaw * Mth.DEG_TO_RAD).rotateX(-0.5f);
        } else {
            Vec3 fist = HeldCards.rightNow(f.seat);
            if (fist == null) {
                fist = frame.at(RELEASE_AT);
            }
            Quaternionf q = held(frame, aim, t("release", 15));
            Vector3f c = q.transform(new Vector3f(0, 0, HELD_FORWARD));
            f.from = fist.add(c.x, c.y, c.z).add(offset);
            f.fromQ = q;
        }
        Vec3 to = target(table.position(), f.target);
        double dist = to.subtract(f.from).multiply(1, 0, 1).length();
        f.duration = Mth.clamp(5 + (float) dist * 1.8f, 7, 12);
        if (f.slot == 0) {
            sound(ModSounds.CARD_SLIDE.get(), f.from, 0.55f, 0.95f + RandomSource.create().nextFloat() * 0.2f);
        }
    }

    private static void land(Flight f) {
        PILE.add(f.target);
        if (f.slot == 0 && f.from != null) {
            var table = RouletteDirector.tableEntity();
            if (table != null) {
                sound(ModSounds.CARD_PLACE.get(), target(table.position(), f.target), 0.35f, 1.05f);
            }
        }
    }

    private static Vec3 target(Vec3 tableCenter, Lying l) {
        return tableCenter.add(l.x, SeatLayout.TABLE_HEIGHT + 0.003 + l.layer * 0.0012, l.z);
    }

    private static Throw throwOf(int seat) {
        for (int i = THROWS.size() - 1; i >= 0; i--) {
            if (THROWS.get(i).seat == seat) {
                return THROWS.get(i);
            }
        }
        return null;
    }

    /** Tick sejak lemparan kursi ini mulai, atau -1. */
    public static float since(int seat, float partial) {
        Throw th = throwOf(seat);
        if (th == null) {
            return -1;
        }
        float t = ClientTable.now() + partial - th.start;
        return t <= t("length", 30) ? t : -1;
    }

    // ============================================================ badan

    public static RouletteDirector.Act act(int seat, float partial) {
        float t = since(seat, partial);
        Throw th = throwOf(seat);
        if (t < 0 || th == null) {
            return null;
        }
        return new RouletteDirector.Act(RouletteDirector.Phase.THROW, "card_throw", t, false);
    }

    /** Kartu yang masih di kipas tangan kiri (sudah dikurangi server) tapi belum dijepit tangan kanan. */
    public static int inFan(int seat, float partial) {
        float t = since(seat, partial);
        Throw th = throwOf(seat);
        return t >= 0 && th != null && t < t("pinch", 8) ? th.count : 0;
    }

    /** Jumlah kartu di jepitan tangan kanan (0 kalau tidak sedang mencabut/melempar). */
    public static int inHand(int seat, float partial) {
        float t = since(seat, partial);
        Throw th = throwOf(seat);
        return t >= 0 && th != null && t >= t("pinch", 8) && t < t("release", 15) ? th.count : 0;
    }

    /** Kepalan kanan saat lepas (relatif mata pelempar: kanan, atas, depan). */
    private static final float[] RELEASE_AT = {0.10f, -0.32f, 0.50f};
    private static final float[] FOLLOW_AT = {0.10f, -0.37f, 0.57f};
    /** Kipas di tangan kiri saat kepalan kiri belum terekam (pemain belum digambar). */
    private static final float[] FAN_FIST = {-0.13f, -0.40f, 0.33f};
    /** Pusat kartu di jepitan, di depan tengah kepalan (sepanjang sumbu panjang kartu). */
    public static final float HELD_FORWARD = 0.05f;

    /**
     * Arah kartu di jepitan pada tick {@code t}: tegak seperti di kipas (punggung ke meja), lalu direbahkan
     * ke arah lemparan selama ancang-ancang sampai lepas.
     */
    public static Quaternionf held(RouletteDirector.Frame f, Vec3 aim, float t) {
        float tilt = 24 * Mth.DEG_TO_RAD;
        Vec3 top = f.up().scale(Mth.cos(tilt)).add(f.forward().scale(-Mth.sin(tilt)));
        Vec3 facing = f.forward().scale(-Mth.cos(tilt)).add(f.up().scale(-Mth.sin(tilt)));
        Quaternionf stand = basis(top, facing.scale(-1));
        Quaternionf lie = basis(aim, new Vec3(0, 1, 0));
        float k = Mth.clamp((t - (t("draw", 12) - 1)) / (t("release", 15) - t("draw", 12) + 1f), 0, 1);
        return stand.slerp(lie, k * k * (3 - 2 * k));
    }

    /** Putaran yang membawa sumbu lokal +Z ke {@code z} dan +Y sedekat mungkin ke {@code y}. */
    public static Quaternionf basis(Vec3 z, Vec3 y) {
        Vector3f zz = new Vector3f((float) z.x, (float) z.y, (float) z.z).normalize();
        Vector3f yy = new Vector3f((float) y.x, (float) y.y, (float) y.z);
        Vector3f xx = yy.cross(zz, new Vector3f()).normalize();
        yy = zz.cross(xx, new Vector3f()).normalize();
        Matrix3f m = new Matrix3f(xx, yy, zz);
        return new Quaternionf().setFromNormalized(m);
    }

    /** Titik jepit di kipas kiri: sisi kanan kipas, setinggi tengah kartu. */
    private static Vec3 fanPinch(RouletteDirector.Frame f, int seat) {
        Vec3 left = HeldCards.leftNow(seat);
        if (left == null) {
            left = f.at(FAN_FIST);
        }
        float tilt = 24 * Mth.DEG_TO_RAD;
        Vec3 top = f.up().scale(Mth.cos(tilt)).add(f.forward().scale(-Mth.sin(tilt)));
        return left.add(0, HeldCards.GRIP_UP, 0).add(top.scale(0.11)).add(f.right().scale(0.11));
    }

    /** Sasaran IK tangan kanan: ke kipas, jepit, cabut ke kanan atas, ancang-ancang, jentik, ikuti, lepas. */
    public static RouletteDirector.Grip grip(int seat, float partial) {
        float t = since(seat, partial);
        Throw th = throwOf(seat);
        RouletteDirector.Frame f = RouletteDirector.frame(seat);
        if (t < 0 || th == null || f == null) {
            return null;
        }
        int reach = t("reach", 6), pinch = t("pinch", 8), draw = t("draw", 12), wind = t("wind", 13);
        int release = t("release", 15), follow = t("follow", 19), length = t("length", 30);
        Vec3 pp = fanPinch(f, seat);
        Vec3 pd = pp.add(f.right().scale(0.10)).add(f.up().scale(0.11)).add(f.forward().scale(0.03));
        Vec3 pw = pd.add(f.up().scale(0.02)).add(f.forward().scale(-0.03));
        Vec3 pr = f.at(RELEASE_AT).add(th.aim.scale(0.04));
        Vec3 pf = f.at(FOLLOW_AT).add(th.aim.scale(0.06));
        Vec3 at;
        if (t < pinch) {
            at = pp;
        } else if (t < draw) {
            at = pp.lerp(pd, smooth((t - pinch) / (draw - pinch)));
        } else if (t < wind) {
            at = pd.lerp(pw, smooth((t - draw) / Math.max(1f, wind - draw)));
        } else if (t < release) {
            float u = Mth.clamp((t - wind) / Math.max(1f, release - wind), 0, 1);
            at = pw.lerp(pr, u * u);
        } else {
            float u = Mth.clamp((t - release) / Math.max(1f, follow - release), 0, 1);
            at = pr.lerp(pf, 1 - (1 - u) * (1 - u));
        }
        float w = smooth(t / Math.max(1f, reach - 1)) * (1 - smooth((t - follow) / Math.max(1f, length - 4 - follow)));
        // Siku keluar ke kanan bawah, tidak menutupi kipas dari depan.
        Vec3 pole = f.right().scale(0.8).add(f.up().scale(-0.6)).add(f.forward().scale(-0.2));
        return w > 0 ? new RouletteDirector.Grip(at, pole, w, null, null, 0) : null;
    }

    /** Arah lempar kursi ini (untuk kartu di jepitan). */
    public static Vec3 aim(int seat) {
        Throw th = throwOf(seat);
        return th == null ? null : th.aim;
    }

    // ============================================================ gambar

    public static void render(PoseStack pose, MultiBufferSource buffers, Vec3 origin, float top, int light,
                              PublicState state, float partial) {
        var table = RouletteDirector.tableEntity();
        if (table == null || state.stage == PublicState.Stage.LOBBY) {
            return;
        }
        Vec3 center = table.position();
        VertexConsumer vc = TableCards.backs(buffers);
        // Meloncat sesaat saat meja digebrak (LIAR!).
        float shake = Math.max(0, LiarDirector.slamShake(LiarDirector.sinceCall(partial)));
        for (Lying l : PILE) {
            if (l.revealed) {
                continue;
            }
            pose.pushPose();
            Vec3 at = target(center, l);
            float jump = shake * (0.015f + 0.003f * (l.layer % 5));
            pose.translate(at.x - origin.x, at.y - origin.y + jump, at.z - origin.z);
            pose.mulPose(TableCards.flat(l.yaw + jump * 300 * ((l.layer & 1) == 0 ? 1 : -1)));
            TableCards.card(vc, pose, light);
            pose.popPose();
        }
        float now = ClientTable.now() + partial;
        for (Flight f : FLIGHTS) {
            if (f.from == null || f.duration <= 0) {
                continue;
            }
            float u = (now - f.launch) / f.duration;
            if (u < 0) {
                continue;
            }
            Vec3 to = target(center, f.target);
            double dist = to.subtract(f.from).multiply(1, 0, 1).length();
            Vec3 at = TableCards.slide(f.from, to, u, 0.04f + (float) dist * 0.025f);
            pose.pushPose();
            TableCards.place(pose, origin, at, TableCards.spin(f.fromQ, f.target.yaw, f.spin, u));
            TableCards.card(vc, pose, light);
            pose.popPose();
        }
    }

    // ============================================================ reveal / kumpul

    /** Kartu tumpukan yang sedang dibuka, urut sesuai lemparan (asal geser ke baris reveal). */
    public static List<Lying> revealedCards() {
        List<Lying> out = new ArrayList<>();
        for (Lying l : PILE) {
            if (l.revealed) {
                out.add(l);
            }
        }
        out.sort((a, b) -> Integer.compare(a.layer, b.layer));
        return out;
    }

    /** Semua kartu di felt (termasuk yang sedang dibuka dan yang masih terbang, di titik jatuhnya). */
    public static List<Lying> allOnTable() {
        List<Lying> out = new ArrayList<>(PILE);
        for (Flight f : FLIGHTS) {
            out.add(f.target);
        }
        return out;
    }

    static void sound(net.minecraft.sounds.SoundEvent sound, Vec3 at, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                sound, net.minecraft.sounds.SoundSource.PLAYERS, volume, Mth.clamp(pitch, 0.5f, 2f),
                RandomSource.create(), at.x, at.y, at.z));
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
