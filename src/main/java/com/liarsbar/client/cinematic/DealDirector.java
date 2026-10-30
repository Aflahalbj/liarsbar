package com.liarsbar.client.cinematic;

import com.liarsbar.client.ClientTable;
import com.liarsbar.client.render.HeldCards;
import com.liarsbar.client.render.TableCards;
import com.liarsbar.game.deck.GameEvent;
import com.liarsbar.game.deck.LiarsDeckRules;
import com.liarsbar.network.TableEventS2C;
import com.liarsbar.sound.ModSounds;
import com.liarsbar.table.PublicState;
import com.liarsbar.table.SeatLayout;
import com.liarsbar.table.TableTiming;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Pembagian kartu tiap awal ronde. Dealer = kursi hidup sebelum yang memulai ronde (jadi yang memulai
 * menerima lemparan pertama, seperti di meja kartu sungguhan). Urutannya, dalam tick sejak RoundStarted
 * (sama dengan {@link TableTiming#deal} di server):
 * <ol>
 *   <li>Kumpul (0-16): kartu ronde lalu (tumpukan, baris reveal, kipas di tangan, sisa dek) meluncur ke depan
 *       dealer jadi satu dek; kedua tangan meraihnya dan merapikannya.</li>
 *   <li>Kocok: riffle di felt (dek dibelah, ujung dalam tiap belahan diangkat jempol, kartu jatuh
 *       berselang-seling, didorong menyatu), diselingi bridge (dek melengkung di antara kedua tangan lalu
 *       berjatuhan). Penuh: tiga riffle; pendek: dua. Lalu dirapikan dan diangkat ke tangan kiri.</li>
 *   <li>Bagi: tangan kanan menarik satu kartu dari dek dan melemparnya meluncur di felt ke depan kursi
 *       berikutnya, berkeliling meja lima putaran. Sisa dek ditaruh di depan dealer.</li>
 *   <li>Ambil: semua meraih paketnya dengan tangan kiri, mengangkatnya, dan membuka kipas.</li>
 * </ol>
 * Versi penuh (ronde pertama) disajikan sebagai sinematik untuk semua yang melihat meja; versi pendek dari
 * pandangan sendiri (dealer lokal mendapat satu bidikan dari belakang bahunya).
 */
public final class DealDirector {

    private static final float FELT = SeatLayout.TABLE_HEIGHT + 0.003f;
    /** Dek dan paket berbaring melintang (sumbu panjang ke samping) di felt, sejauh ini dari tepi meja. */
    private static final float INWARD = 0.25f;
    /** Paket hasil bagi di kiri tengah kursi (diambil tangan kiri). */
    private static final float PACKET_LATERAL = -0.10f;
    /** Kepalan di atas felt saat menyentuh kartu (setengah kepalan + tebal dek). */
    private static final double FIST_UP = 0.11;
    /** Dek di tangan kiri saat membagi (relatif mata dealer). */
    private static final float[] LEFT_HOLD = {-0.10f, -0.34f, 0.50f};
    private static final float[] FAN_FIST = {-0.13f, -0.40f, 0.33f};
    public static final float DECK_UP = 0.085f;

    private record Source(Vec3 from, float yaw, float scale, int count, float delay, boolean air) {
    }

    private static final class Toss {
        final int seat, count, packet;
        final float release;
        final float spin;
        final Vec3 to;
        final float yaw;
        Vec3 from;
        Quaternionf fromQ;
        float duration;

        Toss(int seat, int count, int packet, float release, float spin, Vec3 to, float yaw) {
            this.seat = seat;
            this.count = count;
            this.packet = packet;
            this.release = release;
            this.spin = spin;
            this.to = to;
            this.yaw = yaw;
        }
    }

    /** Satu riffle: belahan tiap kartu, waktu lepas, dan layer akhirnya. */
    private static final class Riffle {
        final float start, split, riffle, push;
        final int n;
        final int[] side;
        final float[] release;
        final int[] layer;
        final int[] before;

        Riffle(RandomSource rng, int n, float start, float split, float riffle, float push) {
            this.start = start;
            this.split = split;
            this.riffle = riffle;
            this.push = push;
            this.n = n;
            side = new int[n];
            release = new float[n];
            layer = new int[n];
            before = new int[n];
            int cut = n / 2 + rng.nextInt(3) - 1;
            for (int k = 0; k < n; k++) {
                side[k] = k < cut ? -1 : 1;
                before[k] = k < cut ? k : k - cut;
            }
            // Jatuh berselang-seling dari bawah tiap belahan, kadang dua kartu dari sisi yang sama.
            int a = 0, b = cut, order = 0;
            boolean left = rng.nextBoolean();
            while (a < cut || b < n) {
                int run = rng.nextFloat() < 0.3f ? 2 : 1;
                for (int i = 0; i < run; i++) {
                    if (left && a < cut) {
                        layer[a] = order;
                        release[a] = start + split + riffle * (order + 0.5f) / n;
                        a++;
                        order++;
                    } else if (!left && b < n) {
                        layer[b] = order;
                        release[b] = start + split + riffle * (order + 0.5f) / n;
                        b++;
                        order++;
                    }
                }
                left = !left;
            }
        }

        float end() {
            return start + split + riffle + push;
        }
    }

    private static final class Deal {
        final int tableId;
        final boolean full;
        final float start;
        final int dealer;
        final int[] order;
        final int total, leftover;
        final int dealStart, pickupStart, length, interval;
        final List<Toss> tosses = new ArrayList<>();
        final List<Source> sources = new ArrayList<>();
        int sourceCards;
        final List<Riffle> riffles = new ArrayList<>();
        final List<Float> bridges = new ArrayList<>();
        float square;
        final Vec3 deck;
        final float deckYaw;
        final Vec3 rest;
        final float restYaw;
        final List<float[]> cues = new ArrayList<>();
        final List<SoundEvent> cueSounds = new ArrayList<>();
        final List<Vec3> cuePos = new ArrayList<>();

        Deal(int tableId, boolean full, float start, int dealer, int[] order, int total, Vec3 deck, float deckYaw,
             Vec3 rest, float restYaw) {
            this.tableId = tableId;
            this.full = full;
            this.start = start;
            this.dealer = dealer;
            this.order = order;
            this.total = total;
            this.leftover = Math.max(0, total - order.length * LiarsDeckRules.STANDARD.handSize());
            this.dealStart = TableTiming.dealStart(full);
            this.pickupStart = TableTiming.pickupStart(full, order.length);
            this.length = TableTiming.deal(full, order.length);
            this.interval = TableTiming.tossInterval(order.length);
            this.deck = deck;
            this.deckYaw = deckYaw;
            this.rest = rest;
            this.restYaw = restYaw;
        }

        void cue(float t, SoundEvent sound, Vec3 at, float volume, float pitch) {
            cues.add(new float[]{t, volume, pitch});
            cueSounds.add(sound);
            cuePos.add(at);
        }
    }

    private static Deal deal;
    /** Sisa dek dari pembagian terakhir, tergeletak di felt sampai dikumpulkan lagi. */
    private static Vec3 restDeck;
    private static float restYaw;
    private static int restCount;
    private static int restTable = -1;
    private static float lastTick = -1;

    private DealDirector() {
    }

    // ============================================================ kejadian

    /** Dipanggil sebelum ThrowDirector (yang mengosongkan tumpukan) dan sebelum state ronde baru tiba. */
    public static void onEvent(TableEventS2C msg) {
        if (msg.event() instanceof GameEvent.RoundStarted e) {
            begin(msg.tableId(), msg.full(), e.starter(), e.decks(), e.handSizes());
        }
    }

    /** AnimDev: pembagian palsu (hanya di client) dari state sekarang. */
    public static void debugDeal(boolean full) {
        PublicState state = ClientTable.state();
        if (state == null) {
            return;
        }
        List<Integer> sizes = new ArrayList<>();
        int starter = -1;
        for (int i = 0; i < state.seats.size(); i++) {
            boolean alive = state.seats.get(i).alive;
            sizes.add(alive ? 5 : 0);
            if (alive && starter < 0) {
                starter = i;
            }
        }
        if (starter >= 0) {
            begin(state.tableId, full, state.current >= 0 ? state.current : starter, Math.max(1, state.decks), sizes);
        }
    }

    private static void begin(int tableId, boolean full, int starter, int decks, List<Integer> handSizes) {
        PublicState state = ClientTable.state();
        var table = RouletteDirector.tableEntity();
        if (state == null || table == null) {
            deal = null;
            return;
        }
        int n = handSizes.size();
        List<Integer> alive = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int seat = (starter + i) % n;
            if (handSizes.get(seat) > 0) {
                alive.add(seat);
            }
        }
        if (alive.isEmpty()) {
            deal = null;
            return;
        }
        int[] order = alive.stream().mapToInt(Integer::intValue).toArray();
        int dealer = order[order.length - 1];
        float now = ClientTable.now();
        RandomSource rng = RandomSource.create(tableId * 6151L + state.round * 92821L + starter);
        Vec3 deckAt = spot(dealer, 0, INWARD);
        float deckYaw = TableCards.yawOf(right(dealer).x, right(dealer).z);
        Vec3 rest = spot(dealer, -0.04f, 0.45f);
        Deal d = new Deal(tableId, full, now, dealer, order, decks * com.liarsbar.game.deck.DeckBuilder.DECK_SIZE, deckAt,
                deckYaw + (rng.nextFloat() * 2 - 1) * 6, rest, deckYaw + 8 + rng.nextFloat() * 10);
        gatherSources(d, state, table.position(), rng);
        shuffle(d, rng);
        tosses(d, rng);
        cues(d, rng);
        deal = d;
        lastTick = -1;
    }

    /** Kartu ronde lalu, dari tempatnya masing-masing (state lama: event datang sebelum state ronde baru). */
    private static void gatherSources(Deal d, PublicState state, Vec3 center, RandomSource rng) {
        List<Source> raw = new ArrayList<>();
        if (state.tableId == d.tableId && state.stage != PublicState.Stage.LOBBY) {
            List<ThrowDirector.Lying> on = ThrowDirector.allOnTable();
            List<ThrowDirector.Lying> revealed = ThrowDirector.revealedCards();
            for (ThrowDirector.Lying l : on) {
                if (l.revealed) {
                    int i = revealed.indexOf(l);
                    float[] slot = revealSlot(i, revealed.size(), state);
                    if (slot != null) {
                        raw.add(new Source(center.add(slot[0], FELT, slot[1]), slot[2], REVEAL_SCALE, 1, 0, false));
                        continue;
                    }
                }
                raw.add(new Source(center.add(l.x, FELT + l.layer * 0.0012, l.z), l.yaw, 1, 1, 0, false));
            }
            for (int i = 0; i < state.seats.size(); i++) {
                PublicState.SeatInfo info = state.seats.get(i);
                if (!info.alive || info.cards <= 0) {
                    continue;
                }
                Vec3 fist = HeldCards.leftNow(i);
                RouletteDirector.Frame f = RouletteDirector.frame(i);
                if (fist == null && f != null) {
                    fist = f.at(FAN_FIST);
                }
                if (fist != null) {
                    float yaw = f == null ? 0 : TableCards.yawOf(f.forward().x, f.forward().z);
                    raw.add(new Source(fist.add(0, HeldCards.GRIP_UP + 0.08, 0), yaw, 1, info.cards, 0, true));
                }
            }
        }
        if (restCount > 0 && restDeck != null && restTable == d.tableId) {
            raw.add(new Source(restDeck, restYaw, 1, restCount, 0, false));
        }
        restCount = 0;
        // Yang terjauh berangkat duluan; semuanya tiba sebelum tangan merapikan dek.
        raw.sort((a, b) -> Double.compare(b.from.distanceToSqr(d.deck), a.from.distanceToSqr(d.deck)));
        for (int i = 0; i < raw.size(); i++) {
            Source s = raw.get(i);
            float delay = Math.min(5f, i * 0.45f) + rng.nextFloat() * 0.4f;
            d.sources.add(new Source(s.from, s.yaw, s.scale, s.count, delay, s.air));
            d.sourceCards += s.count;
        }
    }

    private static void shuffle(Deal d, RandomSource rng) {
        int n = Math.min(d.total, 26);
        float t = TableTiming.GATHER;
        for (int i = 0; i < TableTiming.riffles(d.full); i++) {
            if (i > 0) {
                d.bridges.add(t);
                t += TableTiming.BRIDGE;
            }
            // Belah 5, berjatuhan 13, dorong 6, jeda 2 (TableTiming.RIFFLE).
            d.riffles.add(new Riffle(rng, n, t, 5, 13, 6));
            t += TableTiming.RIFFLE;
        }
        d.square = t;
    }

    private static void tosses(Deal d, RandomSource rng) {
        int alive = d.order.length;
        int count = TableTiming.tosses(alive);
        Vec3[] packets = new Vec3[LiarsDeckRules.MAX_PLAYERS];
        float[] yaws = new float[LiarsDeckRules.MAX_PLAYERS];
        for (int k = 0; k < count; k++) {
            int seat = d.order[k % alive];
            int layer = k / alive;
            if (packets[seat] == null) {
                Vec3 r = right(seat);
                packets[seat] = spot(seat, PACKET_LATERAL, INWARD);
                yaws[seat] = TableCards.yawOf(r.x, r.z) + (rng.nextFloat() * 2 - 1) * 10;
            }
            Vec3 to = packets[seat];
            float yaw = yaws[seat];
            if (layer > 0) {
                // Kartu berikutnya mendarat di atas yang sebelumnya, tidak pernah tepat menumpuk.
                Vec3 r = right(seat);
                Vec3 o = out(seat);
                to = to.add(r.scale((rng.nextFloat() * 2 - 1) * 0.022f)).add(o.scale((rng.nextFloat() * 2 - 1) * 0.014f))
                        .add(0, layer * TableCards.THICK, 0);
                yaw += (rng.nextFloat() * 2 - 1) * 9;
            }
            float release = d.start + d.dealStart + k * d.interval + 0.45f * d.interval;
            float spin = (rng.nextBoolean() ? 1 : -1) * (40 + rng.nextFloat() * 120);
            d.tosses.add(new Toss(seat, 1, layer, release, spin, to, yaw));
        }
    }

    private static void cues(Deal d, RandomSource rng) {
        float s = d.start;
        d.cue(s + 5, ModSounds.CARD_SHOVE.get(), d.deck, 0.5f, 1f);
        d.cue(s + 12, ModSounds.CARD_PLACE.get(), d.deck, 0.35f, 1.1f);
        for (Riffle r : d.riffles) {
            d.cue(s + r.start + r.split, ModSounds.CARD_RIFFLE.get(), d.deck, 0.7f, 0.95f + rng.nextFloat() * 0.1f);
            d.cue(s + r.start + r.split + r.riffle, ModSounds.CARD_SHOVE.get(), d.deck, 0.4f, 1.15f);
        }
        for (float bridge : d.bridges) {
            d.cue(s + bridge + 7, ModSounds.CARD_FAN.get(), d.deck, 0.5f, 1.05f + rng.nextFloat() * 0.1f);
        }
        d.cue(s + d.square, ModSounds.CARD_PLACE.get(), d.deck, 0.35f, 0.9f);
        d.cue(s + d.square + 2, ModSounds.CARD_PLACE.get(), d.deck, 0.3f, 1.0f);
        for (Toss t : d.tosses) {
            d.cue(t.release, ModSounds.CARD_SLIDE.get(), d.deck, 0.32f, 0.95f + rng.nextFloat() * 0.25f);
            d.cue(t.release + 4, ModSounds.CARD_PLACE.get(), t.to, 0.16f, 1.05f + rng.nextFloat() * 0.2f);
        }
        for (int seat : d.order) {
            d.cue(s + d.pickupStart + 9, ModSounds.CARD_FAN.get(), spot(seat, PACKET_LATERAL, INWARD), 0.35f,
                    0.95f + rng.nextFloat() * 0.15f);
        }
    }

    // ============================================================ geometri

    /** Arah luar kursi (dari pusat meja), horizontal satuan. */
    private static Vec3 out(int seat) {
        PublicState state = ClientTable.state();
        SeatLayout.Seat s = SeatLayout.seats(state.seats.size()).get(seat);
        return new Vec3(s.dx(), 0, s.dz()).normalize();
    }

    /** Kanan pemilik kursi (menghadap pusat). */
    private static Vec3 right(int seat) {
        Vec3 o = out(seat);
        return new Vec3(o.z, 0, -o.x);
    }

    /** Titik di felt di depan kursi: {@code inward} dari tepi meja, {@code lateral} ke kanan pemiliknya. */
    private static Vec3 spot(int seat, float lateral, float inward) {
        var table = RouletteDirector.tableEntity();
        PublicState state = ClientTable.state();
        float r = SeatLayout.Size.forSeats(state.seats.size()).radius;
        return table.position().add(out(seat).scale(r - inward)).add(right(seat).scale(lateral)).add(0, FELT, 0);
    }

    /** Skala kartu baris reveal (TableRenderer.card 0,22 x 0,33) terhadap kartu fisik. */
    public static final float REVEAL_SCALE = 0.22f / TableCards.W;

    /** Pose kartu ke-{@code i} dari {@code n} di baris reveal: {x, z, yaw} lokal meja (sama dengan TableRenderer). */
    public static float[] revealSlot(int i, int n, PublicState state) {
        if (state.accused < 0 || state.accused >= state.seats.size() || i < 0) {
            return null;
        }
        SeatLayout.Seat seat = SeatLayout.seats(state.seats.size()).get(state.accused);
        float r = SeatLayout.Size.forSeats(state.seats.size()).radius;
        float len = (float) Math.sqrt(seat.dx() * seat.dx() + seat.dz() * seat.dz());
        float dx = (float) seat.dx() / len, dz = (float) seat.dz() / len;
        float offset = (i - (n - 1) / 2f) * 0.3f;
        float ring = LiarDirector.REVEAL_RING;
        return new float[]{dx * r * ring - dz * offset, dz * r * ring + dx * offset, -seat.yaw()};
    }

    // ============================================================ waktu

    private static Deal current() {
        PublicState state = ClientTable.state();
        if (deal == null || state == null || state.tableId != deal.tableId) {
            return null;
        }
        return deal;
    }

    /** Tick sejak pembagian mulai (selama berlangsung), atau -1. */
    public static float since(float partial) {
        Deal d = current();
        if (d == null) {
            return -1;
        }
        float t = ClientTable.now() + partial - d.start;
        return t <= d.length + 10 ? t : -1;
    }

    public static boolean active() {
        Deal d = current();
        return d != null && since(0) >= 0 && since(0) < d.length;
    }

    public static int dealer() {
        Deal d = current();
        return d == null ? -1 : d.dealer;
    }

    /** Kursi yang lampunya jadi pusat: dealer selama mengocok dan membagi. */
    public static int focus() {
        Deal d = current();
        float t = since(0);
        return d != null && t >= 0 && t < d.pickupStart ? d.dealer : -1;
    }

    private static boolean receives(Deal d, int seat) {
        for (int s : d.order) {
            if (s == seat) {
                return true;
            }
        }
        return false;
    }

    /**
     * Kartu di kipas kursi ini selama pembagian: 0 sampai paketnya digenggam, sesudah itu {@code cards}.
     * Di luar pembagian: {@code cards}.
     */
    public static int visibleCards(int seat, int cards, float partial) {
        Deal d = current();
        float t = since(partial);
        if (d == null || t < 0 || t >= d.length || !receives(d, seat)) {
            return cards;
        }
        return t < d.pickupStart + PickupT.GRASP ? 0 : cards;
    }

    /** Waktu ambil paket (dari roulette.json "pickup", sama dengan TableTiming.PICKUP). */
    private static final class PickupT {
        static final int REACH = 6, GRASP = 8, LIFT = 14;
    }

    private static int pk(String key, int fallback) {
        return RouletteTimeline.timing("pickup", key, fallback);
    }

    // ============================================================ tick

    public static void tick() {
        Deal d = current();
        if (d == null) {
            PublicState state = ClientTable.state();
            if (state == null || state.stage == PublicState.Stage.LOBBY) {
                restCount = 0;
            }
            return;
        }
        float now = ClientTable.now();
        float t = now - d.start;
        for (Toss toss : d.tosses) {
            if (toss.from == null && now >= toss.release) {
                launch(d, toss);
            }
        }
        for (int i = 0; i < d.cues.size(); i++) {
            float at = d.cues.get(i)[0] - d.start;
            if (t >= at && lastTick < at) {
                float[] c = d.cues.get(i);
                ThrowDirector.sound(d.cueSounds.get(i), d.cuePos.get(i), c[1], c[2]);
            }
        }
        if (t >= putDownEnd(d) && lastTick < putDownEnd(d) && d.leftover > 0) {
            restDeck = d.rest;
            restYaw = d.restYaw;
            restCount = d.leftover;
            restTable = d.tableId;
            ThrowDirector.sound(ModSounds.CARD_PLACE.get(), d.rest, 0.35f, 0.85f);
        }
        lastTick = t;
    }

    private static void launch(Deal d, Toss toss) {
        RouletteDirector.Frame f = RouletteDirector.frame(d.dealer);
        Vec3 dir = toss.to.subtract(d.deck).multiply(1, 0, 1).normalize();
        Quaternionf q = TableCards.flat(TableCards.yawOf(dir.x, dir.z));
        Vec3 fist = HeldCards.rightNow(d.dealer);
        if (fist == null) {
            fist = f == null ? d.deck : tossPoint(d, f, toss, 0.45f);
        }
        Vector3f c = q.transform(new Vector3f(0, 0, ThrowDirector.HELD_FORWARD));
        toss.from = fist.add(c.x, c.y - 0.06, c.z);
        toss.fromQ = q;
        double dist = toss.to.subtract(toss.from).multiply(1, 0, 1).length();
        toss.duration = Mth.clamp(5 + (float) dist * 1.3f, 6, 10);
    }

    // ============================================================ badan

    public static RouletteDirector.Act act(int seat, float partial) {
        Deal d = current();
        float t = since(partial);
        if (d == null || t < 0) {
            return null;
        }
        if (receives(d, seat) && t >= d.pickupStart && t < d.pickupStart + pk("length", 16) + 8) {
            return new RouletteDirector.Act(RouletteDirector.Phase.DEAL, "deal_pickup", t - d.pickupStart, false);
        }
        if (seat != d.dealer || t >= d.pickupStart) {
            return null;
        }
        if (t < d.dealStart) {
            return new RouletteDirector.Act(RouletteDirector.Phase.DEAL, "deal_shuffle", t, d.full);
        }
        return new RouletteDirector.Act(RouletteDirector.Phase.DEAL, "deal_toss", t - d.dealStart, d.full);
    }

    /**
     * Arah pandang (LookLayer) selama pembagian: dealer menoleh ke kursi tujuan tiap lemparan; yang lain
     * melirik tangan dealer. {yawKiri, menunduk, bobot}, atau null.
     */
    public static float[] look(int seat, float partial) {
        Deal d = current();
        float t = since(partial);
        if (d == null || t < 0 || t >= d.pickupStart) {
            return null;
        }
        RouletteDirector.Frame me = RouletteDirector.frame(seat);
        if (me == null) {
            return null;
        }
        Vec3 target;
        float weight;
        boolean dealer = seat == d.dealer;
        if (dealer) {
            if (t < d.dealStart) {
                return null;
            }
            Toss next = nextToss(d, t);
            if (next == null) {
                return null;
            }
            target = next.to;
            weight = 0.85f;
        } else {
            target = d.deck;
            weight = 0.5f;
        }
        Vec3 dv = target.subtract(me.eye());
        double r = dv.dot(me.right()), f = dv.dot(me.forward());
        float yawLeft = (float) Math.atan2(-r, f);
        float pitch = dealer ? 0 : (float) Math.atan2(-dv.y, Math.hypot(r, f)) - 0.18f;
        return new float[]{yawLeft, pitch, weight};
    }

    private static Toss nextToss(Deal d, float t) {
        for (Toss toss : d.tosses) {
            if (toss.release - d.start + 1.5f >= t) {
                return toss;
            }
        }
        return d.tosses.isEmpty() ? null : d.tosses.get(d.tosses.size() - 1);
    }

    private static float lastToss(Deal d) {
        return d.dealStart + (d.tosses.size() - 1) * d.interval;
    }

    private static float putDownEnd(Deal d) {
        return lastToss(d) + d.interval + 6;
    }

    /** Titik kepalan kanan di siklus lemparan {@code toss} saat fase {@code u} 0..1. */
    private static Vec3 tossPoint(Deal d, RouletteDirector.Frame f, Toss toss, float u) {
        Vec3 deck = f.at(LEFT_HOLD).add(0, DECK_UP, 0).add(f.forward().scale(0.04));
        Vec3 p0 = deck.add(0, 0.09, 0).add(f.right().scale(0.04));
        Vec3 p1 = p0.add(f.right().scale(0.08)).add(0, 0.02, 0);
        Vec3 dir = toss.to.subtract(deck).multiply(1, 0, 1).normalize();
        Vec3 p2 = deck.add(dir.scale(0.20)).add(0, 0.06, 0).add(f.right().scale(0.05));
        if (u < 0.35f) {
            return p0.lerp(p1, smooth(u / 0.35f));
        }
        if (u < 0.5f) {
            float a = (u - 0.35f) / 0.15f;
            return p1.lerp(p2, a * a * (3 - 2 * a));
        }
        return p2.lerp(p0, smooth((u - 0.5f) / 0.5f));
    }

    /** Sasaran IK kedua tangan sepanjang pembagian. */
    public static RouletteDirector.Grip grip(int seat, float partial) {
        Deal d = current();
        float t = since(partial);
        RouletteDirector.Frame f = RouletteDirector.frame(seat);
        if (d == null || t < 0 || f == null) {
            return null;
        }
        Vec3 leftPole = f.right().scale(-0.8).add(f.up().scale(-0.6)).add(f.forward().scale(-0.2));
        Vec3 rightPole = f.right().scale(0.8).add(f.up().scale(-0.6)).add(f.forward().scale(-0.2));
        if (receives(d, seat) && t >= d.pickupStart) {
            float u = t - d.pickupStart;
            int reach = pk("reach", PickupT.REACH), grasp = pk("grasp", PickupT.GRASP), lift = pk("lift", PickupT.LIFT);
            int len = pk("length", 16);
            if (u >= len) {
                return null;
            }
            Vec3 packet = packetAt(d, seat).add(0, FIST_UP, 0);
            Vec3 rest = HeldCards.restLeft(seat);
            if (rest == null) {
                rest = f.at(FAN_FIST);
            }
            Vec3 at = u < grasp ? packet : packet.lerp(rest, smooth((u - grasp) / Math.max(1f, lift - grasp)));
            float w = smooth(u / Math.max(1f, reach - 1)) * (1 - smooth((u - (lift - 2)) / Math.max(1f, len - lift + 2)));
            return w > 0 ? new RouletteDirector.Grip(null, rightPole, 0, at, leftPole, w) : null;
        }
        if (seat != d.dealer || t >= d.pickupStart) {
            return null;
        }
        Vec3 r = right(seat);
        Vec3 deck = d.deck;
        float wIn = smooth((t - 3) / 7f);
        if (t < d.square + 4) {
            // Kedua tangan di ujung dek / belahan di felt.
            float[] ends = handsOnFelt(d, t);
            Vec3 lh = deck.add(r.scale(ends[0])).add(0, FIST_UP + ends[2], 0);
            Vec3 rh = deck.add(r.scale(ends[1])).add(0, FIST_UP + ends[2], 0);
            return new RouletteDirector.Grip(rh, rightPole, wIn, lh, leftPole, wIn);
        }
        Vec3 hold = f.at(LEFT_HOLD);
        float pick = smooth((t - (d.square + 4)) / 4f);
        float[] ends = handsOnFelt(d, d.square + 4);
        Vec3 lFelt = deck.add(r.scale(ends[0])).add(0, FIST_UP, 0);
        Vec3 rFelt = deck.add(r.scale(ends[1])).add(0, FIST_UP, 0);
        Vec3 deckInHand = hold.add(0, DECK_UP, 0).add(f.forward().scale(0.04));
        Vec3 rIdle = deckInHand.add(0, 0.09, 0).add(f.right().scale(0.04));
        Vec3 lh = lFelt.lerp(hold, pick);
        Vec3 rh = rFelt.lerp(rIdle, pick);
        if (t >= d.dealStart) {
            float k = (t - d.dealStart) / d.interval;
            int index = Mth.clamp((int) Math.floor(k), 0, d.tosses.size() - 1);
            float u = k - (float) Math.floor(k);
            if (k < d.tosses.size()) {
                rh = tossPoint(d, f, d.tosses.get(index), u);
            }
            if (d.leftover > 0) {
                float down = smooth((t - (lastToss(d) + d.interval)) / 6f);
                lh = hold.lerp(d.rest.add(0, FIST_UP, 0).lerp(hold, 0.45), down);
            }
        }
        float wOut = 1 - smooth((t - (d.pickupStart - 7)) / 6f);
        float rOut = 1 - smooth((t - (lastToss(d) + d.interval)) / 6f);
        return new RouletteDirector.Grip(rh, rightPole, wIn * Math.min(wOut, rOut), lh, leftPole, wIn * wOut);
    }

    /** {kiri, kanan, naik}: geser samping kepalan kiri/kanan dari pusat dek di felt, dan tinggi tambahan. */
    private static float[] handsOnFelt(Deal d, float t) {
        // Kepalan Minecraft lebih besar dari kartu: dipegang di ujung luar supaya riffle di tengah tetap terlihat.
        float end = TableCards.H / 2 + 0.13f;
        for (Riffle rf : d.riffles) {
            if (t >= rf.start && t < rf.end()) {
                float sep = riffleSep(rf, t);
                float outer = sep + TableCards.H / 2 + 0.07f;
                return new float[]{-outer, outer, 0};
            }
        }
        float bridge = bridgeAt(d, t);
        if (bridge >= 0) {
            return new float[]{-end, end, bridgeLift(t - bridge)};
        }
        if (t >= d.square && t < d.square + 4) {
            float tap = Mth.sin(Mth.clamp((t - d.square) / 4f, 0, 1) * Mth.PI) * 0.015f;
            return new float[]{-end + tap, end - tap, 0};
        }
        if (t < TableTiming.GATHER && t >= 11) {
            float tap = Mth.sin(Mth.clamp((t - 11) / 4f, 0, 1) * Mth.PI) * 0.015f;
            return new float[]{-end + tap, end - tap, 0};
        }
        return new float[]{-end, end, 0};
    }

    /** Jarak pusat tiap belahan dari pusat dek. */
    private static float riffleSep(Riffle rf, float t) {
        float sep = TableCards.H / 2 - 0.015f;
        float open = smooth((t - rf.start) / rf.split);
        float close = smooth((t - (rf.start + rf.split + rf.riffle)) / rf.push);
        return sep * open * (1 - close);
    }

    /** Awal bridge yang sedang berlangsung di {@code t}, atau -1. */
    private static float bridgeAt(Deal d, float t) {
        for (float b : d.bridges) {
            if (t >= b && t < b + TableTiming.BRIDGE) {
                return b;
            }
        }
        return -1;
    }

    /** Dek terangkat dari felt selama bridge ({@code u} tick sejak mulai): naik, ditahan melengkung, turun. */
    private static float bridgeLift(float u) {
        return 0.05f * smooth(u / 3f) * (1 - smooth((u - 7.5f) / 4f));
    }

    // ============================================================ di tangan (HeldCards)

    /** Paket kursi ini di felt (pusat, dunia). */
    public static Vec3 packetAt(int seat) {
        Deal d = current();
        return d == null ? null : packetAt(d, seat);
    }

    private static Vec3 packetAt(Deal d, int seat) {
        for (Toss toss : d.tosses) {
            if (toss.seat == seat && toss.packet == 0) {
                return toss.to;
            }
        }
        return spot(seat, PACKET_LATERAL, INWARD);
    }

    /** Ambil paket: {angkat 0..1, kipas terbuka 0..1, baur dari felt 0..1, yaw paket di felt}, atau null. */
    public static float[] pickup(int seat, float partial) {
        Deal d = current();
        float t = since(partial);
        if (d == null || t < 0 || !receives(d, seat)) {
            return null;
        }
        float u = t - d.pickupStart;
        int grasp = pk("grasp", PickupT.GRASP), lift = pk("lift", PickupT.LIFT), len = pk("length", 16);
        if (u < grasp || u > len + 8) {
            return null;
        }
        float yaw = 0;
        for (Toss toss : d.tosses) {
            if (toss.seat == seat && toss.packet == 0) {
                yaw = toss.yaw;
            }
        }
        return new float[]{smooth((u - grasp) / Math.max(1f, lift - grasp)), smooth((u - grasp - 2) / 6f),
                smooth((u - grasp) / 3f), yaw};
    }

    /** Dek di tangan kiri dealer: jumlah kartu (0 = tidak memegang dek). */
    public static int deckInLeft(int seat, float partial) {
        Deal d = current();
        float t = since(partial);
        if (d == null || t < 0 || seat != d.dealer || t < d.square + 4 || t >= d.pickupStart) {
            return 0;
        }
        if (d.leftover > 0 && t >= lastToss(d) + d.interval) {
            return 0;
        }
        return deckCount(d, t);
    }

    /** Baur dek dari felt ke tangan kiri saat diangkat (0 = di felt, 1 = di tangan). */
    public static float deckLift(float partial) {
        Deal d = current();
        float t = since(partial);
        return d == null ? 0 : smooth((t - (d.square + 4)) / 4f);
    }

    public static Vec3 deckOnFelt() {
        Deal d = current();
        return d == null ? null : d.deck;
    }

    public static float deckYaw() {
        Deal d = current();
        return d == null ? 0 : d.deckYaw;
    }

    /** Kartu di tangan kanan dealer yang akan dilempar: {jumlah, yaw arah lempar}, atau null. */
    public static float[] rightHeld(int seat, float partial) {
        Deal d = current();
        float t = since(partial);
        if (d == null || t < 0 || seat != d.dealer || t < d.dealStart) {
            return null;
        }
        float k = (t - d.dealStart) / d.interval;
        int index = (int) Math.floor(k);
        float u = k - index;
        if (index >= d.tosses.size() || u < 0.15f || u >= 0.45f) {
            return null;
        }
        Toss toss = d.tosses.get(index);
        Vec3 dir = toss.to.subtract(d.deck).multiply(1, 0, 1);
        return new float[]{toss.count, TableCards.yawOf(dir.x, dir.z)};
    }

    private static int deckCount(Deal d, float t) {
        if (t < TableTiming.GATHER) {
            if (d.sourceCards <= 0) {
                return d.total;
            }
            int arrived = 0;
            for (Source s : d.sources) {
                if (t - s.delay >= GATHER_SLIDE) {
                    arrived += s.count;
                }
            }
            return Math.round(d.total * (float) arrived / d.sourceCards);
        }
        int count = d.total;
        float now = d.start + t;
        for (Toss toss : d.tosses) {
            if (now >= toss.release) {
                count -= toss.count;
            }
        }
        return Math.max(0, count);
    }

    private static final float GATHER_SLIDE = 8;

    // ============================================================ gambar

    public static void render(PoseStack pose, MultiBufferSource buffers, Vec3 origin, float top, int light,
                              PublicState state, float partial) {
        Deal d = current();
        float t = since(partial);
        if (restCount > 0 && restDeck != null && restTable == state.tableId
                && (d == null || t < 0 || t >= putDownEnd(d))) {
            pose.pushPose();
            TableCards.place(pose, origin, restDeck, TableCards.flat(restYaw));
            TableCards.deck(buffers, pose, light, restCount);
            pose.popPose();
        }
        if (d == null || t < 0 || t >= d.length) {
            return;
        }
        VertexConsumer vc = TableCards.backs(buffers);
        if (t < TableTiming.GATHER) {
            gather(pose, buffers, vc, origin, light, d, t);
        } else if (t < d.square + 4) {
            shuffleCards(pose, buffers, vc, origin, light, d, t);
        }
        if (d.leftover > 0 && t >= lastToss(d) + d.interval && t < putDownEnd(d)) {
            // Sisa dek didorong dari tangan kiri ke depan dealer.
            RouletteDirector.Frame f = RouletteDirector.frame(d.dealer);
            Vec3 from = HeldCards.leftNow(d.dealer);
            if (from == null && f != null) {
                from = f.at(LEFT_HOLD);
            }
            if (from != null) {
                float u = smooth((t - (lastToss(d) + d.interval)) / 6f);
                Vec3 at = from.add(0, DECK_UP, 0).lerp(d.rest, u).add(0, Mth.sin(u * Mth.PI) * 0.02, 0);
                pose.pushPose();
                TableCards.place(pose, origin, at, TableCards.flat(Mth.rotLerp(u, d.deckYaw, d.restYaw)));
                TableCards.deck(buffers, pose, light, d.leftover);
                pose.popPose();
            }
        }
        float now = d.start + t;
        for (Toss toss : d.tosses) {
            if (toss.from == null || toss.duration <= 0) {
                continue;
            }
            float u = (now - toss.release) / toss.duration;
            // Paket di felt sampai digenggam (sesudahnya di tangan kiri pemiliknya).
            if (u < 0 || t >= d.pickupStart + PickupT.GRASP) {
                continue;
            }
            double dist = toss.to.subtract(toss.from).multiply(1, 0, 1).length();
            Vec3 at = TableCards.slide(toss.from, toss.to, u, 0.03f + (float) dist * 0.015f);
            Quaternionf q = TableCards.spin(toss.fromQ, toss.yaw, toss.spin, u);
            for (int i = 0; i < toss.count; i++) {
                pose.pushPose();
                TableCards.place(pose, origin, at, q);
                pose.translate(0.006f * i, i * TableCards.THICK, 0.004f * i);
                pose.mulPose(Axis.YP.rotationDegrees(2.5f * i));
                TableCards.card(vc, pose, light);
                pose.popPose();
            }
        }
    }

    private static void gather(PoseStack pose, MultiBufferSource buffers, VertexConsumer vc, Vec3 origin, int light,
                               Deal d, float t) {
        int count = deckCount(d, t);
        pose.pushPose();
        TableCards.place(pose, origin, d.deck, TableCards.flat(d.deckYaw));
        TableCards.deck(buffers, pose, light, count);
        pose.popPose();
        for (Source s : d.sources) {
            float u = (t - s.delay) / GATHER_SLIDE;
            if (u >= 1) {
                continue;
            }
            u = Math.max(0, u);
            float e = u * u * (3 - 2 * u);
            Vec3 to = d.deck.add(0, count * TableCards.THICK, 0);
            Vec3 at = s.from.lerp(to, e);
            if (s.air) {
                at = at.add(0, Mth.sin(e * Mth.PI) * 0.08, 0);
            }
            float scale = Mth.lerp(e, s.scale, 1);
            pose.pushPose();
            TableCards.place(pose, origin, at, TableCards.flat(Mth.rotLerp(e, s.yaw, d.deckYaw)));
            pose.scale(scale, 1, scale);
            if (s.count > 1) {
                TableCards.deck(buffers, pose, light, Math.min(s.count, 8));
            } else {
                TableCards.card(vc, pose, light);
            }
            pose.popPose();
        }
    }

    /** Riffle dan bridge di felt depan dealer; di luar keduanya dek rapi. */
    private static void shuffleCards(PoseStack pose, MultiBufferSource buffers, VertexConsumer vc, Vec3 origin,
                                     int light, Deal d, float t) {
        Riffle rf = null;
        for (Riffle r : d.riffles) {
            if (t >= r.start && t < r.end()) {
                rf = r;
            }
        }
        float thick = TableCards.THICK * d.total / Math.max(1, Math.min(d.total, 26));
        pose.pushPose();
        TableCards.place(pose, origin, d.deck, TableCards.flat(d.deckYaw));
        // Lokal dek: +Z = kanan dealer (sumbu panjang), +X = ke dealer/menjauh.
        if (rf != null) {
            float sep = riffleSep(rf, t);
            float splitU = smooth((t - rf.start) / rf.split);
            float lift = smooth((t - (rf.start + rf.split - 1)) / 2f);
            for (int k = 0; k < rf.n; k++) {
                boolean held = t < rf.release[k];
                float fall = held ? 0 : smooth((t - rf.release[k]) / 1.2f);
                // Tinggi: dari urutan dek lama, ke urutan dalam belahan saat dibelah, ke urutan akhir saat riffle.
                float yOld = k * thick, yHalf = rf.before[k] * thick, yNew = rf.layer[k] * thick;
                float y = splitU < 1 ? Mth.lerp(splitU, yOld, yHalf) : Mth.lerp(lift, yHalf, yNew);
                if (rf.side[k] > 0 && splitU > 0 && splitU < 1) {
                    // Belahan atas diangkat melewati belahan bawah saat dipindah ke kanan.
                    y += Mth.sin(splitU * Mth.PI) * 0.03f;
                }
                float bend = (held ? lift : 1 - fall) * 0.42f;
                if (!held && fall >= 1) {
                    bend = 0;
                }
                pose.pushPose();
                pose.translate(0, y, rf.side[k] * sep);
                // Ujung dalam (ke pusat dek) diangkat jempol: belahan kanan ujung -Z, belahan kiri ujung +Z.
                if (rf.side[k] > 0) {
                    TableCards.card(vc, pose, light, 0xFFFFFFFF, TableCards.W, TableCards.H, bend, 0);
                } else {
                    TableCards.card(vc, pose, light, 0xFFFFFFFF, TableCards.W, TableCards.H, 0, bend);
                }
                pose.popPose();
            }
        } else if (bridgeAt(d, t) >= 0) {
            float u = t - bridgeAt(d, t);
            float lift = bridgeLift(u);
            float arch = smooth((u - 3) / 3.5f);
            int n = Math.min(d.total, 26);
            for (int k = 0; k < n; k++) {
                float drop = smooth((u - (7 + 4f * k / n)) / 1.2f);
                float bend = -0.38f * arch * (1 - drop);
                float y = k * thick + lift * (1 - drop) + (1 - drop) * arch * 0.02f;
                pose.pushPose();
                pose.translate(0, y, 0);
                TableCards.card(vc, pose, light, 0xFFFFFFFF, TableCards.W, TableCards.H, bend, bend);
                pose.popPose();
            }
        } else {
            TableCards.deck(buffers, pose, light, d.total);
        }
        pose.popPose();
    }

    // ============================================================ kamera

    /** Bidikan sinematik: penuh untuk semua; pendek hanya untuk dealer lokal (dari belakang bahunya). */
    public static RouletteDirector.Shot cinematic(float partial) {
        Deal d = current();
        float t = since(partial);
        if (d == null || t < 0 || t > d.pickupStart + 6) {
            return null;
        }
        boolean localDealer = ClientTable.mySeat() == d.dealer && ClientTable.seated();
        if (!d.full && !localDealer) {
            return null;
        }
        RouletteDirector.Frame f = RouletteDirector.frame(d.dealer);
        var table = RouletteDirector.tableEntity();
        if (f == null || table == null) {
            return null;
        }
        Vec3 center = table.position().add(0, SeatLayout.TABLE_HEIGHT, 0);
        float in = smooth(t / 6f);
        float out = 1 - smooth((t - (d.pickupStart - 4)) / 8f);
        float w = Math.min(in, out);
        if (!d.full) {
            return wide(f, center, (t) / Math.max(1f, d.pickupStart), w);
        }
        // Lengan Minecraft jauh lebih besar dari kartu: insert terlalu dekat tertutup lengan. Semua bidikan
        // setengah badan dealer + tangan + dek, kamera di luar jangkauan lengan dan agak tinggi.
        Vec3 deck = f.at(new float[]{0, -0.45f, 0.62f});
        if (t < TableTiming.GATHER) {
            // D1: rendah dari depan kiri, kartu ronde lalu meluncur ke depan dealer.
            float u = smooth(t / TableTiming.GATHER);
            Vec3 pos = f.at(new float[]{-1.15f, Mth.lerp(u, -0.12f, -0.08f), Mth.lerp(u, 2.25f, 2.10f)});
            return new RouletteDirector.Shot(pos, deck, 40, w);
        }
        // Shot kocok mengikuti langkahnya: riffle 1 + bridge 1 dari depan, riffle 2 + bridge 2 dari kanan depan,
        // riffle 3 dari atas (kartu berjatuhan berselang-seling paling terbaca dari atas).
        float second = d.riffles.size() > 1 ? d.riffles.get(1).start : d.square;
        float third = d.riffles.size() > 2 ? d.riffles.get(2).start : d.square;
        if (t < second) {
            // D2: dari depan, sedikit di atas tangan: riffle terbaca, wajah di tepi atas bingkai. Dorong masuk pelan.
            float u = smooth((t - TableTiming.GATHER) / Math.max(1f, second - TableTiming.GATHER));
            Vec3 pos = f.at(new float[]{Mth.lerp(u, 0.30f, 0.20f), Mth.lerp(u, -0.10f, -0.14f), Mth.lerp(u, 2.05f, 1.86f)});
            return new RouletteDirector.Shot(pos, deck, 34, w);
        }
        if (t < third) {
            // D3: 30 derajat dari kanan depan dealer (lebih ke depan dari profil, supaya lengan kanan tidak
            // menutupi dek), orbit pelan ke depan.
            float u = smooth((t - second) / Math.max(1f, third - second));
            Vec3 pos = f.at(new float[]{Mth.lerp(u, 1.05f, 0.90f), -0.16f, Mth.lerp(u, 1.95f, 2.05f)});
            return new RouletteDirector.Shot(pos, deck, 34, w);
        }
        if (t < d.square) {
            // D3b: dari atas depan dek, menukik: riffle terakhir, wajah dealer di tepi atas bingkai.
            float u = smooth((t - third) / Math.max(1f, d.square - third));
            Vec3 pos = f.at(new float[]{Mth.lerp(u, -0.10f, -0.04f), Mth.lerp(u, 0.42f, 0.36f), Mth.lerp(u, 1.30f, 1.24f)});
            return new RouletteDirector.Shot(pos, deck.add(f.forward().scale(-0.04)), 40, w);
        }
        if (t < d.dealStart) {
            // D4: MCU dealer dari depan kanan saat merapikan dan mengangkat dek ke tangan kiri.
            float u = smooth((t - d.square) / Math.max(1f, d.dealStart - d.square));
            Vec3 pos = f.at(new float[]{0.70f, 0.10f, Mth.lerp(u, 2.00f, 1.90f)});
            return new RouletteDirector.Shot(pos, f.at(new float[]{0, -0.30f, 0.40f}), 40, w);
        }
        float half = d.dealStart + (d.pickupStart - d.dealStart) * 0.5f;
        if (t < half) {
            // D5: lebar tinggi dari belakang kanan dealer, kartu meluncur satu per satu ke semua kursi.
            return wide(f, center, (t - d.dealStart) / Math.max(1f, half - d.dealStart), w);
        }
        // D6: rendah dari depan kiri dealer: tangan kanan menjentikkan kartu keluar bingkai, dek menipis.
        float u = smooth((t - half) / Math.max(1f, d.pickupStart - half));
        Vec3 pos = f.at(new float[]{Mth.lerp(u, -0.95f, -0.85f), -0.10f, Mth.lerp(u, 1.75f, 1.65f)});
        return new RouletteDirector.Shot(pos, f.at(new float[]{0.05f, -0.36f, 0.45f}), 42, w);
    }

    private static RouletteDirector.Shot wide(RouletteDirector.Frame f, Vec3 center, float u, float w) {
        u = smooth(u);
        // Di atas bahu kanan, cukup tinggi dan ke samping supaya kepala dealer tidak menutupi tengah bingkai.
        Vec3 pos = f.at(new float[]{Mth.lerp(u, 0.95f, 0.85f), Mth.lerp(u, 1.30f, 1.40f), Mth.lerp(u, -0.20f, -0.30f)});
        return new RouletteDirector.Shot(pos, center.add(f.forward().scale(-0.35)), 54, w);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
