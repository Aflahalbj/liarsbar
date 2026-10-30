package com.liarsbar.client.cinematic;

import com.liarsbar.client.ClientTable;
import com.liarsbar.client.render.RevolverPose;
import com.liarsbar.entity.SeatEntity;
import com.liarsbar.entity.TableEntity;
import com.liarsbar.game.deck.GameEvent;
import com.liarsbar.network.TableEventS2C;
import com.liarsbar.sound.ModSounds;
import com.liarsbar.table.PublicState;
import com.liarsbar.table.SeatLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sutradara roulette di client: mengubah kejadian meja (RouletteStarted,
 * TriggerPulled, PlayerEliminated) jadi fase per kursi, keadaan revolver,
 * bidikan kamera, intensitas efek layar dan audio, detak jantung, dan suara.
 * Semua waktu = tick client (pecahan, integrasi waktu nyata), mulai dari saat
 * kejadiannya diterima; server menunggu {@link com.liarsbar.table.TableTiming}
 * yang sama dengan timeline ini.
 */
public final class RouletteDirector {

    public enum Phase {
        IDLE, ROULETTE, CLICK, BANG, DEAD, LIAR, DEAL, THROW
    }

    /** Apa yang sedang dilakukan satu kursi: animasi dan tick-nya sejak fase mulai. */
    public record Act(Phase phase, String animation, float tick, boolean full) {
    }

    /** Revolver satu kursi: di tangan, di meja, atau tidak terlihat; plus keadaan mekaniknya. */
    public record Gun(boolean inHand, boolean onTable, RevolverPose pose) {
    }

    /** Intensitas efek untuk pemain lokal (0..1). */
    public record Fx(float vignette, float pulse, float aberration, float desaturate, float flash, float darken,
                     float blur, float lowpass, float grain) {
        public static final Fx NONE = new Fx(0, 0, 0, 0, 0, 0, 0, 0, 0);

        public boolean active() {
            return vignette > 0.001f || aberration > 0.001f || desaturate > 0.001f || flash > 0.001f
                    || darken > 0.001f || blur > 0.001f || grain > 0.001f;
        }
    }

    private static final class Show {
        final int tableId;
        final List<Integer> shooters;
        final boolean full;
        /** Versi isi: penembak mengisi revolvernya dulu (tarikan pertamanya). */
        final boolean load;
        final long start;
        final Map<Integer, Boolean> fired = new HashMap<>();
        long outcome = -1;
        /** Putaran silinder (derajat) saat diputar; kelipatan 60 supaya chamber sejajar laras. */
        final float spinTotal;

        Show(int tableId, List<Integer> shooters, boolean full, boolean load, long start) {
            this.tableId = tableId;
            this.shooters = shooters;
            this.full = full || load;
            this.load = load;
            this.start = start;
            this.spinTotal = 360f * 3 + 60f * RandomSource.create().nextInt(6);
        }

        boolean anyFired() {
            return fired.containsValue(Boolean.TRUE);
        }
    }

    private static Show show;
    private static int tableId = -1;
    /** Pose mati tiap kursi (side / table), tetap sampai meja kembali ke lobby. */
    private static final Map<Integer, String> deathPose = new HashMap<>();
    /** Tick client saat kursi ini tewas (lampunya padam berkedip). */
    private static final Map<Integer, Long> diedAt = new HashMap<>();
    private static float lastCueTick = Float.NaN;
    private static String lastCuePhase = "";
    private static double heartPhase;
    private static long lastBeat = Long.MIN_VALUE;
    private static long bangAt = Long.MIN_VALUE;
    private static boolean localWasShooter;

    private RouletteDirector() {
    }

    // ============================================================ waktu

    public static float now(float partial) {
        return ClientTable.now() + partial;
    }

    // ========================================================== kejadian

    public static void onEvent(TableEventS2C msg) {
        GameEvent event = msg.event();
        if (msg.tableId() != tableId) {
            reset(msg.tableId());
        }
        long now = ClientTable.now();
        if (event instanceof GameEvent.RoundStarted e && e.round() == 1) {
            deathPose.clear();
            diedAt.clear();
        } else if (event instanceof GameEvent.RouletteStarted e) {
            show = new Show(msg.tableId(), e.shooters(), msg.full(), msg.load(), now);
            localWasShooter = e.shooters().contains(ClientTable.mySeat());
            lastCueTick = Float.NaN;
            heartPhase = 0;
        } else if (event instanceof GameEvent.TriggerPulled e) {
            if (show != null && show.shooters.contains(e.seat())) {
                show.fired.put(e.seat(), e.fired());
                if (show.outcome < 0) {
                    show.outcome = now;
                    lastCueTick = Float.NaN;
                }
                if (e.fired()) {
                    bangAt = now;
                }
            }
        } else if (event instanceof GameEvent.PlayerEliminated e) {
            diedAt.put(e.seat(), now);
            variant(e.seat());
        }
    }

    public static void reset(int table) {
        tableId = table;
        show = null;
        deathPose.clear();
        diedAt.clear();
        bangAt = Long.MIN_VALUE;
    }

    // ============================================================ kursi

    public static String variant(int seat) {
        return deathPose.computeIfAbsent(seat, s -> ((tableId * 13 + s * 7) & 1) == 0 ? "side" : "table");
    }

    public static long diedAt(int seat) {
        return diedAt.getOrDefault(seat, Long.MIN_VALUE);
    }

    private static boolean shooting(int seat) {
        PublicState state = ClientTable.state();
        return show != null && state != null && show.tableId == state.tableId && show.shooters.contains(seat);
    }

    /** AnimDev: tahan roulette kursi ini di tick tertentu (null = normal). */
    private static final Map<Integer, Float> FROZEN = new HashMap<>();

    public static void freeze(int seat, Float tick) {
        if (tick == null) {
            FROZEN.remove(seat);
        } else {
            FROZEN.put(seat, tick);
        }
    }

    public static Act act(int seat, float partial) {
        Float frozen = FROZEN.get(seat);
        if (frozen != null && show != null && show.shooters.contains(seat) && show.outcome < 0) {
            return new Act(Phase.ROULETTE, "roulette_" + showKind(), frozen, show.full);
        }
        PublicState state = ClientTable.state();
        if (state == null || seat < 0 || seat >= state.seats.size()) {
            return new Act(Phase.IDLE, "sit_calm", 0, false);
        }
        float now = now(partial);
        if (shooting(seat)) {
            if (show.outcome < 0) {
                return new Act(Phase.ROULETTE, "roulette_" + showKind(), now - show.start, show.full);
            }
            float d = now - show.outcome;
            if (Boolean.TRUE.equals(show.fired.get(seat))) {
                String anim = "death_" + variant(seat);
                if (d < RouletteTimeline.timing("bang", "length", 110)) {
                    return new Act(Phase.BANG, anim, d, show.full);
                }
                return new Act(Phase.DEAD, "dead_" + variant(seat), 0, show.full);
            }
            if (d < RouletteTimeline.timing("click", "length", 55)) {
                return new Act(Phase.CLICK, "after_click", d, show.full);
            }
        }
        PublicState.SeatInfo info = state.seats.get(seat);
        if (!info.alive) {
            return new Act(Phase.DEAD, "dead_" + variant(seat), 0, false);
        }
        Act liar = LiarDirector.act(seat, partial);
        if (liar != null) {
            return liar;
        }
        Act deal = DealDirector.act(seat, partial);
        if (deal != null) {
            return deal;
        }
        Act played = ThrowDirector.act(seat, partial);
        if (played != null) {
            return played;
        }
        return new Act(Phase.IDLE, info.pulls >= 3 ? "sit_tense" : "sit_calm", 0, false);
    }

    /** Nama timeline roulette yang sedang diputar: load (isi dulu), full, atau short. */
    private static String showKind() {
        return show == null || !show.full ? "short" : show.load ? "load" : "full";
    }

    /** Nama timeline untuk {@code act} (fase roulette/klik): load, full, atau short. */
    public static String kind(Act act) {
        return !act.full() ? "short" : show != null && show.load ? "load" : "full";
    }

    /** AnimDev: kursi yang dipaksa memegang revolver (pose istirahat), untuk menyetel posisinya di tangan. */
    private static final java.util.Set<Integer> SHOW_GUN = new java.util.HashSet<>();

    public static void showGun(int seat, boolean show) {
        if (show) {
            SHOW_GUN.add(seat);
        } else {
            SHOW_GUN.remove(seat);
        }
    }

    /**
     * Basis arah revolver di dunia: laras menunjuk {@code barrel}, sisi atas model ke {@code up}.
     * Sumbu model: laras ke -z, atas +y, samping +x.
     */
    public static Quaternionf basis(Vec3 barrel, Vec3 up) {
        Vector3f z = new Vector3f((float) -barrel.x, (float) -barrel.y, (float) -barrel.z).normalize();
        Vector3f y = new Vector3f((float) up.x, (float) up.y, (float) up.z);
        Vector3f x = new Vector3f(y).cross(z).normalize();
        y = new Vector3f(z).cross(x).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
    }

    /** Di depan dada: laras ke kiri-depan penembak, silinder terlihat dari depan kanan. */
    public static Quaternionf chest(Frame f) {
        return basis(f.right().scale(-0.85).add(f.forward().scale(0.45)), f.up());
    }

    /**
     * Versi isi, di depan dada: laras lurus ke depan (sedikit ke kiri dan ke bawah, ke felt, tidak ke orang),
     * sisi atas ke atas. Crane keluar ke kiri revolver = kiri penembak, jadi tangan kiri mengisi dan memutar
     * silinder tanpa menyilang badan.
     */
    public static Quaternionf loadChest(Frame f) {
        return basis(f.forward().scale(0.93).add(f.right().scale(-0.22)).add(f.up().scale(-0.18)), f.up());
    }

    /** Kepalan kanan saat mengisi (relatif mata): di depan tengah dada. */
    private static final float[] LOAD_AT = {0.05f, -0.36f, 0.42f};

    /** Di pelipis kanan: laras miring naik 15 derajat ke pelipis, gagang ke bawah (moncong menempel, tidak menembus). */
    public static Quaternionf temple(Frame f) {
        return basis(f.right().scale(-1).add(f.up().scale(0.27)).add(f.forward().scale(0.10)), f.up());
    }

    /** Tergeletak menyamping (sisi kanan model ke atas), laras ke kiri. */
    public static Quaternionf lying(Frame f) {
        return basis(f.right().scale(-1), f.forward().scale(-1));
    }

    private static Quaternionf orientation(int seat, Act act) {
        Frame f = frame(seat);
        Vec3 table = tableGunPos(seat);
        if (f == null || table == null) {
            return null;
        }
        return hold(f, act, table.add(0, REACH_LIFT, 0)).q();
    }

    /** Arah dan titik genggam revolver di tangan pada satu saat. */
    private record Hold(Quaternionf q, Vec3 p) {
    }

    /**
     * Titik antara jalur dada -> pelipis (kepalan relatif mata). Jalur lurus menyapu laras dan hammer
     * menembus muka; jadi revolver didorong maju dulu (laras ke depan), lalu diangkat di samping kepala
     * dengan laras tegak, baru laras direbahkan ke pelipis. Diukur: tidak ada titik revolver yang masuk
     * kepala atau badan sepanjang jalur (terdekat: moncong yang menempel di pelipis di ujungnya).
     */
    private static final float[] LIFT_OUT = {0.34f, -0.38f, 0.46f};
    private static final float[] LIFT_UP = {0.62f, -0.14f, 0.16f};

    private static Quaternionf liftOut(Frame f) {
        return basis(f.right().scale(-0.2).add(f.up().scale(0.3)).add(f.forward()), f.up());
    }

    private static Quaternionf liftUp(Frame f) {
        return basis(f.right().scale(0.15).add(f.up()).add(f.forward().scale(0.05)), f.forward().scale(-1));
    }

    /** Dari pose awal ({@code q0}, {@code p0}) menuju pelipis, {@code s} 0..1; baurnya tumpang tindih supaya tidak berhenti di titik antara. */
    private static Hold raise(Frame f, Quaternionf q0, Vec3 p0, float s) {
        float a = smooth(s / 0.42f), b = smooth((s - 0.20f) / 0.48f), c = smooth((s - 0.52f) / 0.48f);
        Quaternionf q = new Quaternionf(q0).slerp(liftOut(f), a).slerp(liftUp(f), b).slerp(temple(f), c);
        Vec3 p = p0.lerp(f.at(RouletteTimeline.gunPath("liftOut", LIFT_OUT)), a)
                .lerp(f.at(RouletteTimeline.gunPath("liftUp", LIFT_UP)), b)
                .lerp(f.at(RouletteTimeline.gunPath("temple", TEMPLE_AT)), c);
        return new Hold(q, p);
    }

    /** Revolver di tangan selama roulette/klik (tanpa gemetar); {@code table} = titik raih di meja. */
    private static Hold hold(Frame f, Act act, Vec3 table) {
        Vec3 chest = f.at(RouletteTimeline.gunPath("chest", CHEST_AT));
        float t = act.tick();
        if (act.phase() == Phase.ROULETTE) {
            String kind = kind(act);
            int grab = RouletteTimeline.timing(kind, "grab", 12);
            int raise = RouletteTimeline.timing(kind, "raise", 18);
            int temple = RouletteTimeline.timing(kind, "temple", 40);
            float g = smooth((t - grab) / 10f);
            boolean load = kind.equals("load");
            Quaternionf q = new Quaternionf(lying(f)).slerp(load ? loadChest(f) : chest(f), g);
            Vec3 held = load ? f.at(LOAD_AT) : chest;
            return raise(f, q, table.lerp(held, g), Mth.clamp((t - raise) / Math.max(1f, temple - raise), 0, 1));
        }
        if (act.phase() == Phase.CLICK) {
            // Jalur yang sama dibalik: turun dari pelipis lewat samping kepala, lalu ke meja.
            int tableTick = RouletteTimeline.timing("click", "table", 42);
            Hold h = raise(f, chest(f), chest, 1 - Mth.clamp((t - 12) / 18f, 0, 1));
            float d = smooth((t - 30) / Math.max(1f, tableTick - 30));
            return new Hold(h.q().slerp(lying(f), d), h.p().lerp(table, d));
        }
        return new Hold(chest(f), chest);
    }

    /** Tengah kepalan kanan (relatif mata: kanan, atas, depan) saat revolver di depan dada / di pelipis. */
    private static final float[] CHEST_AT = {0.10f, -0.36f, 0.40f};
    private static final float[] TEMPLE_AT = {0.609f, -0.218f, 0.027f};
    /** Kepalan menyentuh revolver di meja dari atas (setengah tebal kepalan). */
    public static final double REACH_LIFT = 0.1;

    /** Titik genggam revolver yang tergeletak di meja, di depan kanan pemiliknya (dipakai TableRenderer juga). */
    public static Vec3 tableGunPos(int seat) {
        TableEntity table = tableEntity();
        PublicState state = ClientTable.state();
        if (table == null || state == null) {
            return null;
        }
        SeatLayout.Seat s = SeatLayout.seats(state.seats.size()).get(seat);
        float r = SeatLayout.Size.forSeats(state.seats.size()).radius;
        double len = Math.sqrt(s.dx() * s.dx() + s.dz() * s.dz());
        double ux = s.dx() / len, uz = s.dz() / len;
        // Dekat tepi meja, dalam jangkauan tangan kanan pemiliknya; tebal revolver rebah sekitar 0,03.
        double in = r - 0.14;
        double x = ux * in + uz * 0.2, z = uz * in - ux * 0.2;
        return table.position().add(x, SeatLayout.TABLE_HEIGHT + 0.035, z);
    }

    /** Titik genggam di dunia selama roulette/klik, sejalan dengan kunci animasinya. */
    private static Vec3 position(int seat, Act act, float now) {
        Frame f = frame(seat);
        Vec3 table = tableGunPos(seat);
        if (f == null || table == null) {
            return null;
        }
        Vec3 p = hold(f, act, table.add(0, REACH_LIFT, 0)).p();
        if (act.phase() == Phase.ROULETTE) {
            // Gemetar selama ditahan, makin kuat mendekati tarikan pelatuk.
            int temple_t = RouletteTimeline.timing(kind(act), "temple", 40);
            float shake = smooth((act.tick() - temple_t) / 40f) * 0.006f;
            return p.add(CameraPath.noise(now * 0.37, 1.1) * shake, CameraPath.noise(now * 0.31, 2.3) * shake,
                    CameraPath.noise(now * 0.29, 3.9) * shake);
        }
        return p;
    }

    /**
     * Sasaran IK lengan: tengah kepalan kanan (titik genggam revolver) dan arah siku, tangan kiri di
     * sisi silinder saat dibuka/diputar. Bobot 0 = lengan ikut animasi biasa.
     */
    public record Grip(Vec3 right, Vec3 rightPole, float rightWeight, Vec3 left, Vec3 leftPole, float leftWeight) {
    }

    public static Grip grip(int seat, float partial) {
        Frame f = frame(seat);
        if (f == null) {
            return null;
        }
        Vec3 poleTable = f.right().scale(0.8).add(f.up().scale(0.2)).add(f.forward().scale(-0.5));
        Vec3 poleChest = f.right().scale(0.7).add(f.up().scale(-1));
        // Siku keluar ke samping dan sedikit ke belakang (The Deer Hunter): lengan tidak menutupi pistol dari depan.
        Vec3 poleTemple = f.right().add(f.up().scale(0.1)).add(f.forward().scale(-0.45));
        if (SHOW_GUN.contains(seat)) {
            boolean chest = showGunAs.equals("chest");
            Vec3 at = f.at(RouletteTimeline.gunPath(chest ? "chest" : "temple", chest ? CHEST_AT : TEMPLE_AT));
            return new Grip(at, chest ? poleChest : poleTemple, 1, null, null, 0);
        }
        Act act = act(seat, partial);
        float t = act.tick();
        float now = now(partial);
        switch (act.phase()) {
            case ROULETTE -> {
                String kind = kind(act);
                int grab = RouletteTimeline.timing(kind, "grab", 12);
                int raise = RouletteTimeline.timing(kind, "raise", 18);
                int temple = RouletteTimeline.timing(kind, "temple", 40);
                Vec3 at = position(seat, act, now);
                if (at == null) {
                    return null;
                }
                Vec3 pole = poleTable.lerp(poleChest, smooth((t - grab) / 10f))
                        .lerp(poleTemple, smooth((t - raise) / Math.max(1f, temple - raise)));
                // Tangan meraih revolver di meja dulu, baru menggenggamnya.
                float w = smooth((t - (grab - 13)) / 11f);
                if (!kind.equals("load")) {
                    // Silinder hanya dibuka dan diputar sekali, saat revolver diisi.
                    return new Grip(at, pole, w, null, null, 0);
                }
                // Tangan kiri: mengisi peluru, menyapu silinder, menutup crane, lalu lepas.
                int flick = RouletteTimeline.timing(kind, "flick", 100);
                int close = RouletteTimeline.timing(kind, "close", 124);
                int pick = RouletteTimeline.timing(kind, "pick", 30);
                float lw = smooth((t - (pick - 11)) / 10f) * (1 - smooth((t - close) / 10f));
                if (lw <= 0) {
                    return new Grip(at, pole, w, null, null, 0);
                }
                Quaternionf q = orientation(seat, act);
                RouletteTimeline.Gun g = RouletteTimeline.gun(kind);
                float opened = g == null ? 0 : g.open().at(t);
                // Pusat silinder relatif tengah kepalan kanan (revolver skala 0,52 duduk di atas kepalan).
                Vector3f cyl = q.transform(new Vector3f(0, 0.14f, -0.137f));
                Vector3f side = q.transform(new Vector3f(-1, 0, 0));
                Vector3f up = q.transform(new Vector3f(0, 1, 0));
                // Silinder ikut crane keluar ke kiri revolver (= kiri penembak) saat dibuka.
                cyl.add(side.x * 0.07f * opened, side.y * 0.07f * opened, side.z * 0.07f * opened);
                // Silinder keluar ke kiri; dari mata penembak ia berputar ke kiri (sisi atas ke kiri, sisi luar
                // ke bawah; RevolverPose.cylinder positif). Telapak kiri mengusap sisi luar silinder dari atas ke
                // bawah, searah putarannya, lalu menyingkir ke kiri bawah selama berputar, dan mendorong silinder
                // dari sisi luarnya untuk menutup crane.
                float u = smooth((t - (flick - 3)) / 6f);
                float drop = smooth((t - (flick + 3)) / 6f);
                float sx = 0.15f + 0.02f * Mth.sin(Mth.PI * u);
                float lift = Mth.lerp(u, 0.05f, -0.12f);
                sx = Mth.lerp(drop, sx, 0.20f);
                lift = Mth.lerp(drop, lift, -0.18f);
                float back = smooth((t - (close - 11)) / 9f);
                sx = Mth.lerp(back, sx, 0.15f);
                lift = Mth.lerp(back, lift, -0.02f);
                Vec3 left = at.add(cyl.x + side.x * sx + up.x * lift, cyl.y + side.y * sx + up.y * lift,
                        cyl.z + side.z * sx + up.z * lift);
                Vec3 leftPole = f.up().scale(-1).add(f.right().scale(-0.7)).add(f.forward().scale(-0.2));
                int seatT = RouletteTimeline.timing(kind, "seat", 88);
                Vec3 loading = loadingFist(seat, f, t, new Vec3(up.x, up.y, up.z), new Vec3(side.x, side.y, side.z));
                if (loading != null) {
                    // Sesudah peluru duduk: tangan kiri lepas dari pantat chamber lalu masuk jalur sapuan.
                    left = loading.lerp(left, smooth((t - (seatT + 2)) / Math.max(1f, flick - 3 - seatT - 2)));
                }
                Vec3 bulletPole = f.right().scale(-0.9).add(f.up().scale(-0.5)).add(f.forward().scale(-0.3));
                leftPole = bulletPole.lerp(leftPole, smooth((t - seatT) / 8f));
                return new Grip(at, pole, w, left, leftPole, lw);
            }
            case CLICK -> {
                int table = RouletteTimeline.timing("click", "table", 42);
                Vec3 at = position(seat, act, now);
                if (at == null) {
                    return null;
                }
                Vec3 pole = poleTemple.lerp(poleChest, smooth((t - 12) / 18f))
                        .lerp(poleTable, smooth((t - 30) / Math.max(1f, table - 30)));
                return new Grip(at, pole, 1 - smooth((t - table) / 12f), null, null, 0);
            }
            case BANG -> {
                // Lengan lepas dari genggaman saat tubuh terhempas.
                Vec3 at = f.at(RouletteTimeline.gunPath("temple", TEMPLE_AT));
                float w = 1 - smooth(t / 6f);
                return w > 0 ? new Grip(at, poleTemple, w, null, null, 0) : null;
            }
            case DEAL -> {
                return DealDirector.grip(seat, partial);
            }
            case THROW -> {
                return ThrowDirector.grip(seat, partial);
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * Seberapa mantap revolver digenggam (0 = baru diangkat dari meja, kepalan di atasnya; 1 = gagang
     * di dalam kepalan, frame dan silinder di atas jari). Berubah halus supaya tidak meloncat.
     */
    public static float gripSettle(int seat, float partial) {
        if (SHOW_GUN.contains(seat)) {
            return 1;
        }
        Act act = act(seat, partial);
        float t = act.tick();
        return switch (act.phase()) {
            case ROULETTE -> smooth((t - RouletteTimeline.timing(kind(act), "grab", 12)) / 8f);
            case CLICK -> 1 - smooth((t - (RouletteTimeline.timing("click", "table", 42) - 8)) / 8f);
            default -> 1;
        };
    }

    public static Gun gun(int seat, float partial) {
        Gun g = gunState(seat, partial);
        if (g.inHand() && g.pose().world() == null) {
            if (SHOW_GUN.contains(seat)) {
                Quaternionf q = showGunOrientation(seat);
                Frame f = frame(seat);
                if (q != null && f != null) {
                    Vec3 at = f.at(RouletteTimeline.gunPath(showGunAs.equals("chest") ? "chest" : "temple",
                            showGunAs.equals("chest") ? CHEST_AT : TEMPLE_AT));
                    return new Gun(true, false, g.pose().placed(q, at));
                }
                return g;
            }
            Act act = act(seat, partial);
            Quaternionf q = orientation(seat, act);
            Vec3 at = position(seat, act, now(partial));
            if (q != null && at != null) {
                return new Gun(true, g.onTable(), g.pose().placed(q, at));
            }
        }
        return g;
    }

    /** AnimDev: arah revolver yang dipaksa (chest/temple/lying). */
    private static String showGunAs = "temple";

    public static void showGunAs(String name) {
        showGunAs = name;
    }

    private static Quaternionf showGunOrientation(int seat) {
        Frame f = frame(seat);
        if (f == null) {
            return null;
        }
        return switch (showGunAs) {
            case "chest" -> chest(f);
            case "lying" -> lying(f);
            default -> temple(f);
        };
    }

    private static Gun gunState(int seat, float partial) {
        if (SHOW_GUN.contains(seat)) {
            return new Gun(true, false, RevolverPose.REST);
        }
        PublicState state = ClientTable.state();
        float base = state != null && seat >= 0 && seat < state.seats.size() ? state.seats.get(seat).pulls * 60f : 0;
        Act act = act(seat, partial);
        switch (act.phase()) {
            case ROULETTE -> {
                String kind = kind(act);
                RouletteTimeline.Gun g = RouletteTimeline.gun(kind);
                if (g == null) {
                    return new Gun(true, false, RevolverPose.REST);
                }
                float t = act.tick();
                float spin = 0;
                if (g.spinFrom() >= 0 && t > g.spinFrom()) {
                    float u = Mth.clamp((t - g.spinFrom()) / Math.max(1, g.spinTo() - g.spinFrom()), 0, 1);
                    // Positif: dari mata penembak sisi atas silinder ke kiri, searah usapan tangan kiri.
                    spin = show.spinTotal * (1 - (1 - u) * (1 - u) * (1 - u));
                }
                float open = g.open().at(t);
                return new Gun(t >= g.handFrom(), t < g.handFrom(),
                        new RevolverPose(base + spin, open, g.cock().at(t), g.trigger().at(t), chamberBullet(kind, t, open)));
            }
            case CLICK -> {
                RouletteTimeline.Gun g = RouletteTimeline.gun("click");
                float t = act.tick();
                boolean hand = g == null || t < g.handTo();
                float trigger = Math.max(0, 1 - t / 8f);
                float spin = show.load ? show.spinTotal : 0;
                return new Gun(hand, !hand, new RevolverPose(base + spin, 0, 0, trigger));
            }
            case BANG, DEAD -> {
                // Terlepas dari tangan saat tertembak; MuzzleFx menjatuhkannya ke lantai/meja.
                return new Gun(false, false, new RevolverPose(base, 0, 0, 0));
            }
            default -> {
                return new Gun(false, true, new RevolverPose(base, 0, 0, 0));
            }
        }
    }

    // ============================================================ peluru

    /**
     * Peluru dijepit di ujung jari: tengahnya sejauh ini dari tengah kepalan, searah bahu kiri -> kepalan
     * (perpanjangan lengan bawah). Di bawah kepalan (searah dunia) peluru tenggelam di lengan saat tangan diangkat.
     */
    private static final double TIP = 0.17;
    /** Bahu kiri relatif mata (kanan, atas, depan). */
    private static final float[] LEFT_SHOULDER = {-0.33f, -0.25f, 0.0f};
    /** Kepalan saat mendorong peluru: sejauh ini di depan pantatnya (sepanjang sumbu), di bawah sumbu, ke sisi crane. */
    private static final double FIST_BEHIND = 0.03, FIST_BELOW = 0.16, FIST_SIDE = 0.03;
    /** Peluru di depan mata saat ditatap (relatif mata: kanan, atas, depan): titik tengah kepalan kiri. */
    private static final float[] SHOW_AT = {-0.08f, -0.14f, 0.40f};

    /** Pantat chamber atas di dunia (duduk penuh), arah keluar chamber, dan panjang satu piksel model, per kursi. */
    private record Chamber(Vec3 rear, Vec3 out, double px, long at) {
    }

    private static final Map<Integer, Chamber> CHAMBER = new HashMap<>();

    /**
     * Dari GunLayer: pantat peluru (sudah termasuk geseran keluar {@code slide}) dan titik satu piksel di
     * belakangnya, di dunia. Disimpan sebagai posisi duduk penuh supaya sasaran tangan tidak ikut tertinggal satu frame.
     */
    public static void recordChamber(int seat, Vec3 rear, Vec3 behind, float slide) {
        Vec3 d = behind.subtract(rear);
        double px = d.length();
        if (px < 1e-6) {
            return;
        }
        Vec3 out = d.scale(1 / px);
        float bullet = Math.max(0, slide) * com.liarsbar.client.render.RevolverRenderer.BULLET_LENGTH;
        CHAMBER.put(seat, new Chamber(rear.subtract(out.scale(px * bullet)), out, px, ClientTable.now()));
    }

    private static Chamber chamber(int seat) {
        Chamber c = CHAMBER.get(seat);
        return c == null || ClientTable.now() - c.at > 4 ? null : c;
    }

    /** Peluru di chamber (RevolverPose.bullet): versi isi masuk dari {@code insert} sampai {@code seat}. */
    private static float chamberBullet(String kind, float t, float open) {
        if (open < 0.15f || kind.equals("short")) {
            return -1;
        }
        if (!kind.equals("load")) {
            return 0;
        }
        int insert = RouletteTimeline.timing(kind, "insert", 80), seat = RouletteTimeline.timing(kind, "seat", 88);
        if (t < insert) {
            return -1;
        }
        // Didorong pelan, lalu sentakan kecil di akhir saat pelek menyentuh muka silinder.
        float u = Mth.clamp((t - insert) / Math.max(1f, seat - insert), 0, 1);
        return 1 - (u < 0.75f ? 0.8f * smooth(u / 0.75f) : 0.8f + 0.2f * smooth((u - 0.75f) / 0.25f));
    }

    /** Titik peluru berdiri di felt (tengah dasar), di samping kiri dalam revolver pemiliknya. */
    public static Vec3 tableBulletPos(int seat) {
        TableEntity table = tableEntity();
        PublicState state = ClientTable.state();
        if (table == null || state == null || seat < 0 || seat >= state.seats.size()) {
            return null;
        }
        SeatLayout.Seat s = SeatLayout.seats(state.seats.size()).get(seat);
        float r = SeatLayout.Size.forSeats(state.seats.size()).radius;
        double len = Math.sqrt(s.dx() * s.dx() + s.dz() * s.dz());
        double ux = s.dx() / len, uz = s.dz() / len;
        // Di kiri depan pemiliknya (dalam jangkauan tangan kiri), di kiri tempat paket kartu hasil bagi mendarat.
        double in = r - 0.15;
        double x = ux * in - uz * 0.22, z = uz * in + ux * 0.22;
        return table.position().add(x, SeatLayout.TABLE_HEIGHT + 0.003, z);
    }

    /**
     * Tangan kiri sedang dipakai mengisi (versi isi, sejak meraih peluru): kipas kartunya tidak digambar.
     * Mulai tepat di potongan ke insert felt, jadi hilangnya kipas tidak terlihat.
     */
    public static boolean leftHandBusy(int seat, float partial) {
        Act act = act(seat, partial);
        return act.phase() == Phase.ROULETTE && kind(act).equals("load")
                && act.tick() >= RouletteTimeline.timing("load", "pick", 30) - 10;
    }

    /** Peluru masih berdiri di felt: revolvernya belum pernah diisi (belum pernah menarik pelatuk). */
    public static boolean bulletOnTable(int seat, float partial) {
        PublicState state = ClientTable.state();
        if (state == null || seat < 0 || seat >= state.seats.size()) {
            return false;
        }
        PublicState.SeatInfo info = state.seats.get(seat);
        if (!info.alive || info.pulls > 0) {
            return false;
        }
        Act act = act(seat, partial);
        if (act.phase() == Phase.ROULETTE && kind(act).equals("load")) {
            return act.tick() < RouletteTimeline.timing("load", "pick", 30) + 1;
        }
        return act.phase() != Phase.CLICK && act.phase() != Phase.BANG;
    }

    /**
     * Peluru di tangan kiri (versi isi, sejak dijepit sampai ujungnya di mulut chamber): {@code null} kalau
     * tidak dipegang. Arah = sumbu peluru (dasar ke kepala); {@code chamber} 0..1 = seberapa jauh sudah
     * dipindah dari kepalan ke mulut chamber (supaya bertemu tepat dengan peluru di bone tanpa meloncat).
     */
    public record HeldBullet(Vec3 center, Vec3 axis, float chamber, Vec3 chamberCenter) {
    }

    public static HeldBullet heldBullet(int seat, Vec3 fist, float partial) {
        Act act = act(seat, partial);
        if (act.phase() != Phase.ROULETTE || !kind(act).equals("load") || fist == null) {
            return null;
        }
        float t = act.tick();
        int pick = RouletteTimeline.timing("load", "pick", 30), show = RouletteTimeline.timing("load", "show", 42);
        int insert = RouletteTimeline.timing("load", "insert", 80);
        if (t < pick + 1 || t >= insert) {
            return null;
        }
        Frame f = frame(seat);
        if (f == null) {
            return null;
        }
        // Dijepit di kepala peluru (tegak), diputar jadi melintang di depan mata, lalu kepala ke mulut chamber.
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 across = f.right().scale(-1).add(f.up().scale(0.25)).normalize();
        Chamber c = chamber(seat);
        Vec3 in = c == null ? f.forward().scale(0.7).add(0, -0.7, 0).normalize() : c.out.scale(-1);
        float turn = smooth((t - (pick + 1)) / Math.max(1f, show - pick - 1));
        float aim = smooth((t - (insert - 16)) / 12f);
        Vec3 axis = slerpDir(slerpDir(up, across, turn), in, aim);
        double half = com.liarsbar.client.render.RevolverRenderer.BULLET_LENGTH / 2.0
                * (c == null ? com.liarsbar.client.render.BulletRenderer.PX : c.px);
        Vec3 reach = fist.subtract(f.at(LEFT_SHOULDER)).normalize();
        Vec3 inHand = fist.add(reach.scale(TIP)).add(axis.scale(-half * 0.6 * aim));
        if (c == null) {
            return new HeldBullet(inHand, axis, 0, null);
        }
        // Peluru di bone saat slide = 1: pantat di rear + out * panjang, tengah setengahnya lebih dalam.
        Vec3 mouth = c.rear.add(c.out.scale(c.px * com.liarsbar.client.render.RevolverRenderer.BULLET_LENGTH))
                .subtract(c.out.scale(half));
        float k = smooth((t - (insert - 6)) / 6f);
        return new HeldBullet(inHand.lerp(mouth, k), axis, k, mouth);
    }

    private static Vec3 slerpDir(Vec3 a, Vec3 b, float u) {
        if (u <= 0) {
            return a;
        }
        if (u >= 1) {
            return b;
        }
        return a.scale(1 - u).add(b.scale(u)).normalize();
    }

    /**
     * Sasaran kepalan kiri versi isi sampai peluru duduk: turun ke peluru di felt, mengangkatnya ke depan
     * mata, lalu ke atas mulut chamber dan mendorongnya masuk. Null kalau belum ada posisi chamber.
     */
    private static Vec3 loadingFist(int seat, Frame f, float t, Vec3 gunUp, Vec3 gunSide) {
        int pick = RouletteTimeline.timing("load", "pick", 30), show = RouletteTimeline.timing("load", "show", 42);
        int insert = RouletteTimeline.timing("load", "insert", 80);
        Vec3 felt = tableBulletPos(seat);
        if (felt == null) {
            return null;
        }
        double half = com.liarsbar.client.render.RevolverRenderer.BULLET_LENGTH / 2.0 * com.liarsbar.client.render.BulletRenderer.PX;
        // Kepalan di belakang peluru searah lengan (dari bahu), sedikit di atasnya: jari menjepit badan peluru.
        Vec3 center = felt.add(0, half + 0.02, 0);
        Vec3 pinch = center.subtract(center.subtract(f.at(LEFT_SHOULDER)).normalize().scale(TIP));
        Vec3 shown = f.at(SHOW_AT);
        Vec3 at;
        if (t < pick) {
            at = pinch.add(0, 0.05 * (1 - smooth((t - (pick - 11)) / 11f)), 0);
        } else {
            // Diangkat lurus dulu dari felt, baru ke depan mata.
            Vec3 lifted = pinch.add(0, 0.06, 0);
            float a = smooth((t - pick) / 4f), b = smooth((t - (pick + 2)) / Math.max(1f, show - pick - 2));
            at = pinch.lerp(lifted, a).lerp(shown, b);
            // Ditatap sebentar sambil sedikit diputar di jari.
            at = at.add(f.right().scale(0.006 * Mth.sin((t - show) * 0.35f) * smooth((t - show) / 4f)));
        }
        Chamber c = chamber(seat);
        if (c == null) {
            return at;
        }
        // Kepalan Minecraft sebesar silinder dan pantat chamber menghadap dada: jari menjepit pantat peluru dari
        // bawah (kepalan di bawah sumbu chamber, di sisi crane, seperti saat menyentil silinder), ikut bergeser
        // sepanjang sumbunya saat peluru didorong masuk. Diukur dengan cardprobe: dari belakang tidak terjangkau.
        double len = c.px * com.liarsbar.client.render.RevolverRenderer.BULLET_LENGTH;
        float slide = chamberBullet("load", t, 1);
        Vec3 below = gunUp.scale(-FIST_BELOW).add(gunSide.scale(FIST_SIDE));
        // Kepalan bertahan di mulut chamber, jari yang mendorong peluru masuk: lebih dekat ke dada lengan tidak
        // sampai (cardprobe: meleset 26 cm dan kepalan menyapu ke depan kamera).
        Vec3 push = c.rear.add(c.out.scale(len * (0.85f + 0.15f * Math.max(0, slide)) - FIST_BEHIND)).add(below);
        if (t >= insert) {
            return push;
        }
        Vec3 above = c.rear.add(c.out.scale(len - FIST_BEHIND + 0.04)).add(below);
        float approach = smooth((t - (insert - 22)) / 16f);
        float settle = smooth((t - (insert - 6)) / 6f);
        return at.lerp(above, approach).lerp(push, settle);
    }

    // ============================================================ kerangka

    /** Mata, kanan, atas, depan (ke tengah meja) penembak di kursi {@code seat}. */
    public record Frame(Vec3 eye, Vec3 right, Vec3 up, Vec3 forward) {
        public Vec3 at(float[] ruf) {
            return eye.add(right.scale(ruf[0])).add(up.scale(ruf[1])).add(forward.scale(ruf[2]));
        }
    }

    public static TableEntity tableEntity() {
        Minecraft mc = Minecraft.getInstance();
        PublicState state = ClientTable.state();
        if (mc.level == null || state == null) {
            return null;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof TableEntity t && t.tableId() == state.tableId) {
                return t;
            }
        }
        return null;
    }

    public static Frame frame(int seat) {
        TableEntity table = tableEntity();
        PublicState state = ClientTable.state();
        if (table == null || state == null || seat < 0 || seat >= state.seats.size()) {
            return null;
        }
        SeatLayout.Seat s = SeatLayout.seats(state.seats.size()).get(seat);
        Vec3 center = table.position();
        Vec3 seatPos = center.add(s.dx(), 0, s.dz());
        Vec3 eye = seatPos.add(0, SeatEntity.RIDE_OFFSET - 0.35 + 1.62, 0);
        Vec3 forward = new Vec3(-s.dx(), 0, -s.dz()).normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        return new Frame(eye, right, new Vec3(0, 1, 0), forward);
    }

    // ============================================================ kamera

    /** Bidikan untuk pemain lokal: posisi, titik tatap, FOV, dan bobot (0 = kamera biasa). */
    public record Shot(Vec3 pos, Vec3 target, float fov, float weight) {
    }

    /** Sedang roulette di meja yang dilihat dan pemain lokal bukan penembaknya. */
    public static boolean watchingShow() {
        PublicState state = ClientTable.state();
        return show != null && state != null && show.tableId == state.tableId
                && !show.shooters.contains(ClientTable.mySeat());
    }

    public static boolean localShooting() {
        return shooting(ClientTable.mySeat()) && ClientTable.mySeat() >= 0;
    }

    /** Bidikan sinematik penonton (Art Bible: semua roulette sinematik; versi pendek lebih singkat). */
    public static Shot cinematic(float partial) {
        if (!watchingShow() || show.shooters.size() != 1) {
            return null;
        }
        int shooter = show.shooters.get(0);
        Frame f = frame(shooter);
        if (f == null) {
            return null;
        }
        float now = now(partial);
        List<RouletteTimeline.CamKey> keys;
        float t;
        float weight;
        if (show.outcome < 0) {
            Float frozen = FROZEN.get(shooter);
            t = frozen != null ? frozen : now - show.start;
            keys = RouletteTimeline.camera(showKind());
            weight = frozen != null ? 1 : smooth(t / 6f);
        } else {
            t = now - show.outcome;
            boolean bang = show.anyFired();
            float len = RouletteTimeline.timing(bang ? "bang" : "click", "length", bang ? 110 : 55);
            keys = RouletteTimeline.camera(bang ? "bang" : "click");
            if (bang) {
                if (t >= len - BANG_CUT_BEFORE_END) {
                    return null;
                }
                weight = 1;
            } else {
                weight = 1 - smooth((t - (len - 16)) / 16f);
                if (t >= len) {
                    return null;
                }
            }
        }
        if (keys.isEmpty() || weight <= 0) {
            return null;
        }
        // Potongan keras: hanya kunci di segmen yang memuat t (segmen dimulai di kunci bertanda cut).
        int from = 0;
        for (int i = 1; i < keys.size(); i++) {
            if (keys.get(i).cut() && keys.get(i).t() <= t) {
                from = i;
            }
        }
        int to = keys.size();
        for (int i = from + 1; i < keys.size(); i++) {
            if (keys.get(i).cut()) {
                to = i;
                break;
            }
        }
        keys = keys.subList(from, to);
        Vec3[] pos = new Vec3[keys.size()];
        Vec3[] aim = new Vec3[keys.size()];
        float[] fov = new float[keys.size()];
        float[] ticks = new float[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            RouletteTimeline.CamKey k = keys.get(i);
            pos[i] = f.at(k.pos());
            aim[i] = f.at(k.aim());
            fov[i] = k.fov();
            ticks[i] = k.t();
        }
        Vec3 p = CameraPath.sample(ticks, pos, t);
        Vec3 a = CameraPath.sample(ticks, aim, t);
        float fv = CameraPath.sampleFloat(ticks, fov, t);
        // Goyang kamera genggam yang sangat halus.
        double n = now * 0.05;
        p = p.add(CameraPath.noise(n, 1.3) * 0.006, CameraPath.noise(n, 2.1) * 0.005, CameraPath.noise(n, 3.7) * 0.006);
        float beat = pulse(now) * tension(now);
        if (beat > 0.001f) {
            p = p.add(a.subtract(p).normalize().scale(0.022 * beat));
            fv -= 1.2f * beat;
        }
        if (show.anyFired() && show.outcome >= 0) {
            float d = now - show.outcome;
            float shake = (float) Math.exp(-d / 6.0) * 0.06f;
            p = p.add(CameraPath.noise(now * 0.9, 5.1) * shake, CameraPath.noise(now * 0.8, 6.3) * shake, 0);
        }
        return new Shot(p, a, fv, weight);
    }

    /** Sesudah DOR, kamera sinematik dipotong kembali ke POV sekian tick sebelum akhir (tertutup layar hitam). */
    private static final int BANG_CUT_BEFORE_END = 18;

    /** Layar gelap singkat untuk menutup potongan kamera sesudah DOR (penonton versi penuh). */
    private static float cutDip(float now) {
        if (show == null || !show.full || show.outcome < 0 || !show.anyFired() || localWasShooter) {
            return 0;
        }
        float len = RouletteTimeline.timing("bang", "length", 110);
        float d = now - show.outcome - (len - BANG_CUT_BEFORE_END);
        return d < -8 || d > 10 ? 0 : d < 0 ? smooth((d + 8) / 8f) : 1 - smooth(d / 10f);
    }

    /** Versi pendek: penonton menoleh pelan ke penembak (seperti game asli), tanpa kamera lepas. */
    public static Shot lookAt(float partial) {
        if (!watchingShow() || show.shooters.size() == 1 || show.shooters.isEmpty() || ClientTable.mySeat() < 0) {
            return null;
        }
        Frame f = frame(show.shooters.get(0));
        if (f == null) {
            return null;
        }
        float now = now(partial);
        float weight;
        if (show.outcome < 0) {
            weight = smooth((now - show.start - 4) / 18f) * 0.95f;
        } else {
            float d = now - show.outcome;
            float len = show.anyFired() ? 70 : 40;
            weight = (1 - smooth((d - (len - 20)) / 20f)) * 0.95f;
        }
        if (weight <= 0) {
            return null;
        }
        return new Shot(null, f.eye().add(0, -0.12, 0), 0, weight);
    }

    /** Penembak lokal (POV): seberapa jauh pandangan ditarik menunduk ke pistol (derajat) dan goyang napas. */
    public static float[] shooterLook(float partial) {
        if (!localShooting()) {
            return null;
        }
        Act act = act(ClientTable.mySeat(), partial);
        float t = act.tick();
        float down = 0;
        float sway = 0;
        if (act.phase() == Phase.ROULETTE) {
            String kind = kind(act);
            int grab = RouletteTimeline.timing(kind, "grab", 12);
            int raise = RouletteTimeline.timing(kind, "raise", 18);
            int temple = RouletteTimeline.timing(kind, "temple", 40);
            float in = smooth((t - (grab - 10)) / 10f);
            float out = smooth((t - raise) / Math.max(1f, temple - raise));
            down = (act.full() ? 34f : 18f) * in * (1 - out);
            sway = smooth((t - temple) / 30f);
        } else if (act.phase() == Phase.CLICK) {
            float e = (float) Math.sin(Math.PI * Mth.clamp((t - 10) / 40f, 0, 1));
            down = 14f * e;
            sway = 1 - smooth(t / 20f);
        }
        float beat = pulse(now(partial)) * tension(now(partial));
        return new float[]{down + 0.7f * beat, sway};
    }

    /**
     * Penembak lokal yang tewas: pandangan ambruk (turun, miring, menunduk ke meja) lalu diam, sampai
     * jadi hantu. {turun (blok), roll, yaw, pitch} atau null.
     */
    public static float[] shooterCollapse(float partial) {
        if (show == null || !localWasShooter || show.outcome < 0 || !show.anyFired()) {
            return null;
        }
        float d = now(partial) - show.outcome;
        float len = RouletteTimeline.timing("bang", "length", 110);
        if (d > len) {
            return null;
        }
        // Tersentak ke kiri sesaat, lalu roboh (pelan lalu makin cepat) dan diam.
        float snap = (float) (Math.sin(Math.PI * Mth.clamp(d / 6f, 0, 1)) * Math.exp(-d / 8.0));
        float drop = (float) Math.pow(Mth.clamp((d - 4) / 22f, 0, 1), 2.2);
        float back = 1 - smooth((d - (len - 25)) / 25f);
        return new float[]{0.55f * drop * back, (-14 * snap + 38 * drop) * back, -18 * snap * back, (22 * drop) * back};
    }

    // ============================================================== fx

    public static Fx fx(float partial) {
        PublicState state = ClientTable.state();
        if (state == null) {
            return Fx.NONE;
        }
        float now = now(partial);
        int me = ClientTable.mySeat();
        boolean ghost = me >= 0 && me < state.seats.size() && !state.seats.get(me).alive;
        float vignette = ghost ? 0.35f : 0;
        float desat = ghost ? 0.7f : 0;
        float lowpass = ghost ? 0.25f : 0;
        float aberration = 0;
        float flash = 0;
        float darken = 0;
        float blur = 0;
        float grain = ghost ? 0.03f : 0;
        float pulse = pulse(now);
        if (show != null && show.tableId == state.tableId) {
            boolean shooter = show.shooters.contains(me) && me >= 0;
            float role = shooter ? 1f : show.full ? 0.85f : 0.55f;
            float k = tension(now);
            vignette = Math.max(vignette, (0.18f + 0.5f * k) * role + 0.16f * k * pulse * role);
            aberration = (0.0012f + 0.0035f * pulse) * k * role;
            desat = Math.max(desat, 0.3f * k * role);
            lowpass = Math.max(lowpass, 0.92f * k * role);
            grain = Math.max(grain, 0.035f * k * role);
            // Tanpa depth of field (Art Bible): blur tidak boleh menutupi kesalahan.
            if (show.outcome >= 0 && show.anyFired()) {
                float d = now - show.outcome;
                if (localWasShooter) {
                    // Penembak yang tewas: putih, lalu merah gelap memudar ke hitam, baru jadi hantu.
                    flash = d < 6 ? 1 : (float) Math.max(0, 1 - (d - 6) / 18.0);
                    darken = smooth((d - 14) / 26f) * (1 - smooth((d - 70) / 30f));
                    desat = Math.max(desat, smooth(d / 40f) * 0.8f);
                    lowpass = Math.max(lowpass, 1 - smooth((d - 70) / 30f) * 0.75f);
                } else {
                    flash = (float) Math.pow(Math.max(0, 1 - d / 14.0), 1.5) * 0.9f;
                    lowpass = Math.max(lowpass, (float) Math.exp(-d / 30.0) * 0.85f);
                    aberration += (float) Math.exp(-d / 8.0) * 0.006f;
                }
            }
        }
        darken = Math.max(darken, cutDip(now));
        return new Fx(Math.min(vignette, 0.95f), pulse, aberration, Math.min(desat, 0.95f), flash, darken, blur,
                Math.min(lowpass, 1f), grain);
    }

    /** Titik fokus depth of field: pistol saat diambil dan diputar, wajah penembak saat ditahan. */
    public static Vec3 focus(float partial) {
        if (show == null || show.shooters.isEmpty()) {
            return null;
        }
        int seat = show.shooters.get(0);
        Frame f = frame(seat);
        if (f == null) {
            return null;
        }
        Vec3 face = f.eye().add(f.right().scale(0.08)).add(0, -0.08, 0);
        Act act = act(seat, partial);
        if (act.phase() != Phase.ROULETTE) {
            return face;
        }
        Gun g = gun(seat, partial);
        if (g.pose().at() == null) {
            return face;
        }
        String kind = kind(act);
        int raise = RouletteTimeline.timing(kind, "raise", 18);
        int temple = RouletteTimeline.timing(kind, "temple", 40);
        float w = smooth((act.tick() - raise) / Math.max(1f, temple - raise));
        return g.pose().at().lerp(face, w);
    }

    /** 0..1: seberapa "siluet" bidikan penembak sekarang (R6/S3: wajah gelap, tepi disinari). */
    public static float silhouette(float partial) {
        if (show == null || show.outcome >= 0) {
            return 0;
        }
        float t = now(partial) - show.start;
        String kind = showKind();
        int start = RouletteTimeline.timing(kind, "inhale", show.full ? 190 : 56) - 2;
        int pull = RouletteTimeline.timing(kind, "pull", show.full ? 212 : 72);
        return t >= start && t < pull ? 1 : 0;
    }

    /** Ketegangan 0..1: naik selama pistol di pelipis, turun sesudah hasilnya. */
    private static float tension(float now) {
        if (show == null) {
            return 0;
        }
        String kind = showKind();
        if (show.outcome < 0) {
            float t = now - show.start;
            int cock = RouletteTimeline.timing(kind, "cock", 40);
            int pull = RouletteTimeline.timing(kind, "pull", 80);
            float pre = 0.15f * smooth(t / 30f);
            return Math.max(pre, smooth((t - (cock - 20)) / Math.max(1f, pull - cock + 20)));
        }
        float d = now - show.outcome;
        if (show.anyFired()) {
            return 0;
        }
        return 1 - smooth(d / 26f);
    }

    private static float pulse(float now) {
        if (lastBeat == Long.MIN_VALUE) {
            return 0;
        }
        return (float) Math.exp(-(now - lastBeat) / 2.5);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    // ============================================================= tick

    public static void tick() {
        PublicState state = ClientTable.state();
        if (state == null) {
            if (show != null || tableId != -1) {
                reset(-1);
            }
            return;
        }
        if (state.tableId != tableId) {
            reset(state.tableId);
        }
        if (state.stage == PublicState.Stage.LOBBY) {
            show = null;
            deathPose.clear();
            diedAt.clear();
        }
        long now = ClientTable.now();
        heartbeat(now);
        cues(now);
        if (show != null && show.outcome >= 0) {
            float d = now - show.outcome;
            float len = show.anyFired() ? RouletteTimeline.timing("bang", "length", 110)
                    : RouletteTimeline.timing("click", "length", 55);
            if (d > len + 2) {
                show = null;
            }
        }
    }

    /** Detak jantung: makin cepat dan keras selama ketegangan naik; tiap detak menggerakkan vignette. */
    private static void heartbeat(long now) {
        float k = tension(now);
        if (k < 0.05f) {
            heartPhase = 0;
            return;
        }
        boolean shooter = show != null && show.shooters.contains(ClientTable.mySeat()) && ClientTable.mySeat() >= 0;
        float role = shooter ? 1f : show != null && show.full ? 0.8f : 0.55f;
        double bpm = 66 + 84 * k;
        heartPhase += bpm / 1200.0;
        if (heartPhase >= 1) {
            heartPhase -= 1;
            lastBeat = now;
            float volume = (0.2f + 0.8f * k) * role;
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(ModSounds.HEARTBEAT.get(), 1.0f, volume));
        }
    }

    /** Suara dari timeline (posisional di penembak), dipicu saat tick-nya terlewati. */
    private static void cues(long now) {
        if (show == null) {
            return;
        }
        String phase;
        float t;
        if (show.outcome < 0) {
            phase = showKind();
            t = now - show.start;
        } else {
            phase = show.anyFired() ? "bang" : "click";
            t = now - show.outcome;
        }
        if (!phase.equals(lastCuePhase)) {
            lastCuePhase = phase;
            lastCueTick = Float.NaN;
        }
        float prev = Float.isNaN(lastCueTick) ? -1 : lastCueTick;
        lastCueTick = t;
        for (int shooter : show.shooters) {
            Frame f = frame(shooter);
            if (f == null) {
                continue;
            }
            Vec3 at = f.eye().add(f.right().scale(0.2)).add(0, -0.25, 0);
            for (RouletteTimeline.Cue cue : RouletteTimeline.sounds(phase)) {
                if (cue.tick() > prev && cue.tick() <= t) {
                    play(cue.sound(), at, 1f);
                }
            }
            if (phase.equals("bang") && Boolean.TRUE.equals(show.fired.get(shooter))) {
                int fall = RouletteTimeline.deathFall("death_" + variant(shooter));
                if (fall > prev && fall <= t) {
                    play("body_fall", f.eye().add(0, -1.1, 0), 1f);
                }
                if (0 > prev && 0 <= t) {
                    tinnitus(shooter);
                }
            }
        }
    }

    private static void tinnitus(int shooter) {
        float volume = localWasShooter ? 0.8f : 0.32f;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.TINNITUS.get(), 1.0f, volume));
    }

    private static void play(String name, Vec3 at, float volume) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(
                new net.minecraft.resources.ResourceLocation(com.liarsbar.LiarsBar.MOD_ID, name));
        if (sound == null) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.PLAYERS, volume,
                0.96f + RandomSource.create().nextFloat() * 0.08f, RandomSource.create(), at.x, at.y, at.z));
    }

    /** Kursi yang barusan tewas tertembak (selama sekuens DOR), atau -1. */
    public static int bangSeat() {
        if (show == null || show.outcome < 0) {
            return -1;
        }
        for (Map.Entry<Integer, Boolean> e : show.fired.entrySet()) {
            if (e.getValue()) {
                return e.getKey();
            }
        }
        return -1;
    }

    /** Tick sejak hasil tembakan sekuens ini (-1 sebelum hasil atau tanpa sekuens). */
    public static float sinceOutcome(float partial) {
        return show == null || show.outcome < 0 ? -1 : now(partial) - show.outcome;
    }

    /** Tick client tembakan terakhir (untuk api moncong dan asap). */
    public static long bangAt() {
        return bangAt;
    }

    public static boolean active() {
        return show != null;
    }

    /** Uji: fase sekarang pemain lokal / penembak pertama. */
    public static String describe() {
        if (show == null) {
            return "none";
        }
        float now = now(0);
        return showKind() + " shooters=" + show.shooters + " t=" + (now - show.start)
                + (show.outcome >= 0 ? " outcome+" + (now - show.outcome) + " fired=" + show.fired : "");
    }
}
