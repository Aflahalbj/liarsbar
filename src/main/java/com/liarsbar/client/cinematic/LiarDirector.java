package com.liarsbar.client.cinematic;

import com.liarsbar.client.ClientTable;
import com.liarsbar.game.deck.GameEvent;
import com.liarsbar.network.TableEventS2C;
import com.liarsbar.table.PublicState;
import com.liarsbar.table.SeatLayout;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Sekuens LIAR! dan reveal (Art Bible, shot list):
 * <ul>
 *   <li>L1 (0-14): insert rendah lintas felt, tangan penuduh menggebrak meja (gerak lambat dipanggang di animasi).</li>
 *   <li>L2 (14-38): OTS dari bahu yang dituduh ke penuduh, stempel LIAR! (TableHud).</li>
 *   <li>L3 (38-64): MCU yang dituduh dengan dolly zoom (maju sambil FOV melebar).</li>
 *   <li>Reveal (0-64 sesudah kartu dibuka): top-down di kartu yang dibuka, dibalik satu per satu.</li>
 * </ul>
 * Waktu dari roulette.json ("liar", "reveal"), sama dengan TableTiming di server.
 */
public final class LiarDirector {

    /** Jarak kartu yang dibuka dari pusat meja ke arah yang dituduh (dikali jari-jari meja). */
    public static final float REVEAL_RING = 0.52f;

    private static int tableId = -1;
    private static long calledAt = Long.MIN_VALUE;
    private static int caller = -1;
    private static int accused = -1;
    private static boolean forced;
    private static long revealedAt = Long.MIN_VALUE;
    private static int revealCount;

    private LiarDirector() {
    }

    public static void onEvent(TableEventS2C msg) {
        long now = ClientTable.now();
        if (msg.tableId() != tableId) {
            tableId = msg.tableId();
            calledAt = Long.MIN_VALUE;
            revealedAt = Long.MIN_VALUE;
        }
        if (msg.event() instanceof GameEvent.LiarCalled e) {
            calledAt = now;
            caller = e.caller();
            accused = e.accused();
            forced = e.forced();
            revealedAt = Long.MIN_VALUE;
        } else if (msg.event() instanceof GameEvent.Revealed e) {
            revealedAt = now;
            revealCount = e.cards().size();
        } else if (msg.event() instanceof GameEvent.RouletteStarted || msg.event() instanceof GameEvent.RoundStarted) {
            calledAt = Long.MIN_VALUE;
            revealedAt = Long.MIN_VALUE;
        }
    }

    private static float lastTick = -1;

    /** Bunyi gebrakan tepat saat telapak menghantam meja. */
    public static void tick() {
        float t = sinceCall(0);
        float hit = liar("slam", 9) + 4;
        if (t >= hit && lastTick < hit && !forced) {
            RouletteDirector.Frame f = RouletteDirector.frame(caller);
            if (f != null) {
                Vec3 at = f.at(new float[]{0.18f, -0.50f, 0.55f});
                net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                        new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                                net.minecraft.sounds.SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
                                net.minecraft.sounds.SoundSource.PLAYERS, 0.9f, 0.75f,
                                net.minecraft.util.RandomSource.create(), at.x, at.y, at.z));
            }
        }
        lastTick = t;
    }

    private static int liar(String key, int fallback) {
        return RouletteTimeline.timing("liar", key, fallback);
    }

    private static int reveal(String key, int fallback) {
        return RouletteTimeline.timing("reveal", key, fallback);
    }

    /** Tick sejak LIAR! dipanggil (selama sekuensnya), atau -1. */
    public static float sinceCall(float partial) {
        if (calledAt == Long.MIN_VALUE) {
            return -1;
        }
        float t = ClientTable.now() + partial - calledAt;
        return t <= liar("length", 64) + 2 ? t : -1;
    }

    /** Tick sejak kartu dibuka (selama reveal), atau -1. */
    public static float sinceReveal(float partial) {
        if (revealedAt == Long.MIN_VALUE) {
            return -1;
        }
        float t = ClientTable.now() + partial - revealedAt;
        return t <= reveal("length", 64) + 2 ? t : -1;
    }

    /** Berapa kartu yang sudah dibalik (0..jumlah), dengan pecahan untuk animasi balik kartu yang sedang jalan. */
    public static float flipped(float partial) {
        float t = sinceReveal(partial);
        PublicState state = ClientTable.state();
        if (t < 0) {
            // Sesudah reveal (roulette), semua tetap terbuka.
            return state != null && (state.stage == PublicState.Stage.ROULETTE || state.stage == PublicState.Stage.SHOT
                    || state.stage == PublicState.Stage.REVEAL) ? 99 : 0;
        }
        return Math.max(0, (t - reveal("first", 8)) / reveal("step", 12) + 1);
    }

    /** Waktu sejak kartu ke-{@code i} terbalik (tick), negatif kalau belum. */
    public static float cardAge(int i, float partial) {
        float t = sinceReveal(partial);
        if (t < 0) {
            return 999;
        }
        return t - (reveal("first", 8) + i * reveal("step", 12));
    }

    public static int caller() {
        return caller;
    }

    public static int accused() {
        return accused;
    }

    public static boolean forced() {
        return forced;
    }

    /** Animasi kursi selama LIAR!: penuduh menggebrak, yang dituduh tersentak. Null = biarkan animasi lain. */
    public static RouletteDirector.Act act(int seat, float partial) {
        float t = sinceCall(partial);
        PublicState state = ClientTable.state();
        if (t < 0 || state == null || state.stage == PublicState.Stage.ROULETTE || state.stage == PublicState.Stage.SHOT) {
            return null;
        }
        if (seat == caller && !forced) {
            return new RouletteDirector.Act(RouletteDirector.Phase.LIAR, "liar_call", t, true);
        }
        if (seat == accused) {
            return new RouletteDirector.Act(RouletteDirector.Phase.LIAR, "liar_flinch", t, true);
        }
        return null;
    }

    /** 0..1 kekuatan stempel LIAR! (muncul di L2, 0,8 dtk penuh, lalu memudar). */
    public static float stamp(float partial) {
        float t = sinceCall(partial);
        int at = liar("stamp", 14);
        if (t < at) {
            return 0;
        }
        float d = t - at;
        return d < 3 ? d / 3f : d < 19 ? 1 : Math.max(0, 1 - (d - 19) / 6f);
    }

    /** Bidikan sinematik LIAR! / reveal untuk semua yang melihat meja ini. */
    public static RouletteDirector.Shot cinematic(float partial) {
        float t = sinceCall(partial);
        if (t >= 0 && sinceReveal(partial) < 0) {
            return liarShot(t);
        }
        float r = sinceReveal(partial);
        if (r >= 0) {
            return revealShot(r);
        }
        return null;
    }

    private static RouletteDirector.Shot liarShot(float t) {
        RouletteDirector.Frame a = RouletteDirector.frame(caller);
        RouletteDirector.Frame b = RouletteDirector.frame(accused);
        if (a == null || b == null) {
            return null;
        }
        int stampAt = liar("stamp", 14), dollyAt = liar("dolly", 38), len = liar("length", 64);
        float in = smooth(t / 3f);
        // Semua bidikan LIAR! lurus dari depan (dari tengah meja), tidak dari samping.
        if (t < stampAt && !forced) {
            // L1: medium shot rendah penuduh (muka sampai daun meja): seluruh gerak angkat tangan lalu gebrak
            // terbaca. Bidikan di dada, bukan di meja (di sana ada revolvernya).
            Vec3 target = a.at(new float[]{0.0f, -0.28f, 0.25f});
            Vec3 pos = a.at(new float[]{0.0f, -0.20f, 1.95f});
            float shake = slamShake(t);
            return new RouletteDirector.Shot(pos.add(0, shake * 0.02, 0), target, 40, in);
        }
        if (t < dollyAt) {
            // L2: penuduh dari depan setinggi mata (kepala sampai dada), push-in pelan, stempel LIAR!.
            Vec3 target = a.eye().add(0, -0.22, 0);
            float drift = smooth((t - stampAt) / Math.max(1, dollyAt - stampAt));
            Vec3 pos = a.at(new float[]{0.0f, 0.02f, (float) Mth.lerp(drift, 2.45, 2.30)});
            return new RouletteDirector.Shot(pos, target, 34, forced ? in : 1);
        }
        // L3: MCU yang dituduh dari depan, dolly zoom (kamera maju, FOV melebar: latar seperti menjauh).
        float u = smooth((t - dollyAt) / Math.max(1, len - dollyAt));
        Vec3 face = b.eye().add(0, -0.12, 0);
        Vec3 dir = b.forward();
        double dist = Mth.lerp(u, 2.25, 1.6);
        float fov = Mth.lerp(u, 32, 46);
        float out = 1 - smooth((t - len + 4) / 4f);
        return new RouletteDirector.Shot(face.add(dir.scale(dist)).add(0, 0.05, 0), face, fov, out);
    }

    private static RouletteDirector.Shot revealShot(float t) {
        Vec3 cards = revealCenter();
        if (cards == null) {
            return null;
        }
        float in = smooth(t / 4f);
        int len = reveal("length", 64);
        float out = 1 - smooth((t - len + 6) / 6f);
        // Hampir top-down dari sisi tengah meja (sedikit miring supaya terbaca sebagai meja), turun pelan.
        var table = RouletteDirector.tableEntity();
        Vec3 toCenter = table == null ? Vec3.ZERO : table.position().subtract(cards).multiply(1, 0, 1).normalize();
        Vec3 pos = cards.add(toCenter.scale(0.32)).add(0, Mth.lerp(smooth(t / len), 1.05, 0.85), 0);
        return new RouletteDirector.Shot(pos, cards, 38, in * out);
    }

    /** Titik tengah kartu yang dibuka (sama dengan TableRenderer.revealed). */
    public static Vec3 revealCenter() {
        var table = RouletteDirector.tableEntity();
        PublicState state = ClientTable.state();
        int seat = accused >= 0 ? accused : state == null ? -1 : state.accused;
        if (table == null || state == null || seat < 0 || seat >= state.seats.size()) {
            return null;
        }
        SeatLayout.Seat s = SeatLayout.seats(state.seats.size()).get(seat);
        float r = SeatLayout.Size.forSeats(state.seats.size()).radius;
        double len = Math.sqrt(s.dx() * s.dx() + s.dz() * s.dz());
        return table.position().add(s.dx() / len * r * REVEAL_RING, SeatLayout.TABLE_HEIGHT + 0.06, s.dz() / len * r * REVEAL_RING);
    }

    /** Hentakan saat telapak menghantam meja (untuk kamera dan kartu yang meloncat). */
    public static float slamShake(float t) {
        int slam = liar("slam", 9);
        // Animasi gebrak memakai gerak lambat 0,5x di 4..12 tick adegan: tick nyata hantaman sedikit lebih lambat.
        float hit = slam + 4;
        float d = t - hit;
        return d < 0 || forced ? 0 : (float) (Math.exp(-d / 3.0) * Math.sin(d * 2.4));
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
