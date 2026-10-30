package com.liarsbar.client.fx;

import com.liarsbar.client.ClientTable;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.liarsbar.entity.TableEntity;
import com.liarsbar.table.PublicState;
import com.liarsbar.table.SeatLayout;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.deferred.light.AreaLight;
import foundry.veil.api.client.render.deferred.light.Light;
import foundry.veil.api.client.render.deferred.light.PointLight;
import foundry.veil.api.client.render.deferred.light.renderer.LightRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Satu lampu gantung di atas pusat meja (Veil point light + sorot kerucut) yang menerangi semua kursi. Begitu
 * ada pemain tewas lampunya berkedip sebentar lalu menyala lagi. Semua lampu yang dipasang dicatat dan dilepas
 * saat dunia diganti (lampu Veil yang basi merusak penerangan).
 */
public final class TableLights {

    /** Tinggi bohlam di atas lantai. */
    public static final float LAMP_HEIGHT = 2.45f;
    /** Lama lampu berkedip sesudah ada yang tewas (tick). */
    public static final int DIE_FLICKER = 24;
    /** Jari-jari bibir kap lampu (dipakai juga oleh kerucut asap). */
    public static final float SHADE_RADIUS = 0.36f;

    private static final Set<Light> ADDED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static PointLight lamp;
    /** Sorot kerucut dari kap ke bawah: kolam cahaya menutup seluruh meja dan kursi, sekelilingnya gelap. */
    private static AreaLight spot;
    private static final Quaternionf SPOT_Q = new Quaternionf();
    /** Cahaya pengisi redup di tengah meja (supaya wajah di sisi gelap tetap terbaca). */
    private static PointLight fill;
    private static int tableId = -1;
    private static int seatCount;

    /** Terang sorot kap, kelebihan kolam sorot di luar bibir meja, jangkauan ekstra, pendar bohlam, pengisi. */
    private static float SPOT = 1.6f;
    private static float SPOT_MARGIN = 0.75f;
    private static float SPOT_DISTANCE = 1.2f;
    private static float BULB = 0.55f;
    private static float BULB_RADIUS = 1.8f;
    private static float FILL = 0.14f;
    /** Pantulan dan rim untuk pemain yang tidak sedang jadi fokus (relatif yang fokus). */
    private static float IDLE = 0.35f;
    /**
     * Selama roulette kamera masuk dekat ke tangan dan wajah, ditambah key wajah: sorot dan pantulan diredam
     * supaya kulit dan pistol tidak terbakar putih. {@code hush} 0..1 dihaluskan per tick.
     */
    private static final float ROULETTE_SPOT_CUT = 0.35f, ROULETTE_BOUNCE_CUT = 0.55f;
    private static float hush;
    /** Pantulan felt ke wajah pemain yang sedang jadi fokus. */
    private static float BOUNCE = 0.65f;

    /** AnimDev "lightcfg": tuning saat jalan {sorot, margin kolam, jarak ekstra, bohlam, radius bohlam, pengisi[, idle, pantulan]}. */
    public static void tune(float[] v) {
        SPOT = v[0];
        SPOT_MARGIN = v[1];
        SPOT_DISTANCE = v[2];
        BULB = v[3];
        BULB_RADIUS = v[4];
        FILL = v[5];
        if (v.length > 7) {
            IDLE = v[6];
            BOUNCE = v[7];
        }
        tableId = -1;
    }

    /** Panjang tali dari langit-langit ke atas kap lampu (blok). */
    public static final float CORD = 2.55f;
    private static final float OMEGA2 = 9.8f / CORD;
    /** Sudut ayun lampu (rad): ax = simpangan ke +x dunia, az = ke +z dunia. */
    private static float ax, az, vx, vz, pax, paz;
    private static long kicked = Long.MIN_VALUE;
    private static long ticks;
    /** Lampu kunci sementara di samping penembak yang tewas (supaya badannya terbaca). */
    private static PointLight key;

    private TableLights() {
    }

    private static LightRenderer lights() {
        return VeilRenderSystem.renderer().getDeferredRenderer().getLightRenderer();
    }

    public static void addLight(Light light) {
        if (ADDED.add(light)) {
            lights().addLight(light);
        }
    }

    public static void removeLight(Light light) {
        if (light != null && ADDED.remove(light)) {
            lights().removeLight(light);
        }
    }

    public static void clearAll() {
        for (Light light : new ArrayList<>(ADDED)) {
            lights().removeLight(light);
        }
        ADDED.clear();
        lamp = null;
        spot = null;
        fill = null;
        key = null;
        face = null;
        bounce = null;
        rim = null;
        tableId = -1;
        seatCount = 0;
    }

    /** Posisi lampu saat diam (tanpa ayunan) untuk meja berpusat {@code center}; gantungannya CORD di atasnya. */
    public static Vec3 lampPos(Vec3 center) {
        return center.add(0, LAMP_HEIGHT, 0);
    }

    /** Setengah sudut sorot (rad): kolamnya menutup meja {@code count} kursi sampai ke badan pemain. */
    public static float spotAngle(int count) {
        float r = SeatLayout.Size.forSeats(count).radius + SPOT_MARGIN;
        return (float) Math.atan2(r, LAMP_HEIGHT - 0.12f - SeatLayout.TABLE_HEIGHT);
    }

    /** Sudut ayun lampu {ax, az} (rad), diinterpolasi antar tick. */
    public static float[] swing(float partial) {
        return new float[]{Mth.lerp(partial, pax, ax), Mth.lerp(partial, paz, az)};
    }

    /** Titik pada lampu sedalam {@code depth} di bawah gantungannya, sesudah diayun. */
    public static Vec3 swung(Vec3 pivot, float[] a, float depth) {
        // Sama dengan renderer: Rz(ax) * Rx(-az) * (0, -depth, 0).
        double sx = Math.sin(a[0]), cx = Math.cos(a[0]), sz = Math.sin(a[1]), cz = Math.cos(a[1]);
        return pivot.add(depth * cz * sx, -depth * cz * cx, depth * sz);
    }

    private static void simulate(Vec3 center) {
        ticks++;
        pax = ax;
        paz = az;
        long bang = RouletteDirector.bangAt();
        if (bang != Long.MIN_VALUE && bang != kicked && ClientTable.now() - bang < 4) {
            kicked = bang;
            int seat = RouletteDirector.bangSeat();
            RouletteDirector.Frame f = seat >= 0 ? RouletteDirector.frame(seat) : null;
            if (f != null) {
                // Hentakan tembakan mendorong lampu menjauh dari penembak.
                Vec3 at = lampPos(center);
                double dx = at.x - f.eye().x, dz = at.z - f.eye().z;
                double d = Math.max(0.3, Math.hypot(dx, dz));
                float impulse = (float) (0.55 / (1 + d * 0.9));
                vx += (float) (dx / d) * impulse;
                vz += (float) (dz / d) * impulse;
            }
        }
        // Angin halus di ruangan supaya lampu tidak pernah benar-benar diam.
        float drive = 0.006f;
        float fx = (float) Math.sin(ticks * 0.031) * drive;
        float fz = (float) Math.sin(ticks * 0.027 + 0.8) * drive;
        for (int k = 0; k < 4; k++) {
            float h = 0.0125f;
            vx += (-OMEGA2 * (float) Math.sin(ax) - 0.45f * vx + fx) * h;
            vz += (-OMEGA2 * (float) Math.sin(az) - 0.45f * vz + fz) * h;
            ax += vx * h;
            az += vz * h;
        }
    }

    private static float[] focus = new float[0];
    private static PointLight bounce;
    private static int bounceSeat = -1;

    /** Kursi yang sedang jadi pusat perhatian: giliran, yang dituduh saat reveal, penembak saat roulette. -1 = semua. */
    public static int focusSeat(PublicState state) {
        return switch (state.stage) {
            // Sesudah kartu dimainkan (sebelum giliran berikutnya) lampu tetap di pelempar.
            case TURN -> state.current >= 0 ? state.current : state.claimSeat;
            case DEALING -> com.liarsbar.client.cinematic.DealDirector.focus();
            case CALLED, REVEAL -> state.accused >= 0 ? state.accused : state.current;
            case ROULETTE, SHOT -> !state.shooters.isEmpty() ? state.shooters.get(0) : state.shotSeat;
            case FINISHED -> state.winner;
            default -> -1;
        };
    }

    /** Level fokus kursi 0..1 (IDLE..1) untuk pantulan dan rim, dihaluskan per tick. */
    public static float focus(int seat) {
        return seat >= 0 && seat < focus.length ? focus[seat] : 1;
    }

    private static void updateFocus(PublicState state) {
        int n = state.seats.size();
        if (focus.length != n) {
            focus = new float[n];
            java.util.Arrays.fill(focus, 1);
        }
        int f = focusSeat(state);
        for (int i = 0; i < n; i++) {
            float want = f < 0 ? 0.6f : i == f ? 1 : IDLE;
            focus[i] += (want - focus[i]) * 0.12f;
        }
    }

    /** Terang lampu tengah 0..1 (1 = menyala): berkedip sebentar sesudah ada pemain yang tewas. */
    public static float brightness(PublicState state, float partial) {
        long died = Long.MIN_VALUE;
        for (int i = 0; i < state.seats.size(); i++) {
            died = Math.max(died, RouletteDirector.diedAt(i));
        }
        if (died == Long.MIN_VALUE) {
            return 1;
        }
        float age = ClientTable.now() + partial - died;
        if (age < 0 || age > DIE_FLICKER) {
            return 1;
        }
        // Kedip tak teratur yang makin jarang, lalu stabil lagi.
        double f = Math.sin(age * 2.9) + Math.sin(age * 5.3 + 1.0) * 0.6;
        return f > 0.4 + (age / DIE_FLICKER) * 1.2 ? 0.12f : 1;
    }

    /** Lampu kunci sesudah DOR: hangat redup di depan-kiri penembak, menyala pelan lalu memudar. */
    private static void keyLight() {
        int seat = RouletteDirector.bangSeat();
        float d = RouletteDirector.sinceOutcome(0);
        if (seat < 0 || d < 0) {
            removeLight(key);
            key = null;
            return;
        }
        RouletteDirector.Frame f = RouletteDirector.frame(seat);
        if (f == null) {
            return;
        }
        float len = com.liarsbar.client.cinematic.RouletteTimeline.timing("bang", "length", 110);
        float w = Mth.clamp((d - 8) / 20f, 0, 1) * (1 - Mth.clamp((d - (len - 20)) / 20f, 0, 1));
        Vec3 at = f.eye().add(f.right().scale(-0.9)).add(f.forward().scale(0.5)).add(0, -0.6, 0);
        if (key == null) {
            key = new PointLight();
            addLight(key);
        }
        key.setPosition(at.x, at.y, at.z).setColor(1.0f, 0.62f, 0.45f).setRadius(3.2f).setBrightness(1.8f * w);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        PublicState state = ClientTable.state();
        TableEntity table = state == null ? null : RouletteDirector.tableEntity();
        if (table == null || mc.level == null || !com.liarsbar.client.test.DevToggles.on("lights")) {
            removeLight(lamp);
            lamp = null;
            removeLight(spot);
            spot = null;
            tableId = -1;
            removeLight(fill);
            fill = null;
            removeLight(bounce);
            bounce = null;
            removeLight(rim);
            rim = null;
            removeLight(key);
            key = null;
            removeLight(face);
            face = null;
            return;
        }
        Vec3 center = table.position();
        simulate(center);
        hush += ((RouletteDirector.active() ? 1 : 0) - hush) * 0.1f;
        updateFocus(state);
        int count = state.seats.size();
        if (state.tableId != tableId || count != seatCount || lamp == null) {
            tableId = state.tableId;
            seatCount = count;
            removeLight(lamp);
            removeLight(spot);
            // Pendar di sekitar bohlam (kap, tali, kepala yang dekat); terang utamanya dari sorot.
            lamp = new PointLight();
            lamp.setColor(1.0f, 0.74f, 0.42f);
            addLight(lamp);
            spot = new AreaLight();
            spot.setColor(1.0f, 0.76f, 0.46f).setSize(0.12, 0.12);
            addLight(spot);
            removeLight(fill);
            fill = null;
            if (FILL > 0) {
                // Lampu kosong tetap mahal (digambar per piksel): hanya dipasang kalau memang menyala.
                Vec3 c = center.add(0, 1.6, 0);
                fill = new PointLight();
                fill.setPosition(c.x, c.y, c.z).setColor(1.0f, 0.8f, 0.6f).setRadius(7.0f).setBrightness(FILL);
                addLight(fill);
            }
        }
        float b = brightness(state, 0);
        float[] a = swing(1);
        Vec3 at = swung(lampPos(center).add(0, CORD, 0), a, CORD + 0.12f);
        lamp.setPosition(at.x, at.y, at.z).setRadius(BULB_RADIUS).setBrightness(BULB * b);
        boolean bulbs = com.liarsbar.client.test.DevToggles.on("bulbs");
        if (bulbs != ADDED.contains(lamp)) {
            if (bulbs) {
                addLight(lamp);
            } else {
                removeLight(lamp);
            }
        }
        boolean spots = com.liarsbar.client.test.DevToggles.on("spots");
        if (spots != ADDED.contains(spot)) {
            if (spots) {
                addLight(spot);
            } else {
                removeLight(spot);
            }
        }
        // Arah sorot = sumbu +Z lokal Veil; dunia = q^-1 * +Z. Bawah yang ikut ayunan: S * Rx(90) * +Z.
        SPOT_Q.rotationZ(a[0]).rotateX(-a[1]).rotateX(Mth.HALF_PI);
        if (com.liarsbar.client.test.DevToggles.on("spotq")) {
            SPOT_Q.conjugate();
        }
        float reach = (float) Math.hypot(SeatLayout.Size.forSeats(count).radius + SPOT_MARGIN, LAMP_HEIGHT - SeatLayout.TABLE_HEIGHT);
        spot.setOrientation(SPOT_Q).setPosition(at.x, at.y, at.z).setAngle(spotAngle(count))
                .setDistance(reach + SPOT_DISTANCE).setBrightness(SPOT * b * (1 - ROULETTE_SPOT_CUT * hush));
        bounceLight(state, center);
        rimLight(state);
        keyLight();
        faceLight();
    }

    private static PointLight rim;
    /** Rim cyan di belakang pemain fokus, dari sisi jendela (siluet terbaca lawan latar gelap). */
    private static final float RIM_LIGHT = 0.6f;

    private static void rimLight(PublicState state) {
        int seat = focusSeat(state);
        RouletteDirector.Frame f = seat >= 0 && seat < state.seats.size() && state.seats.get(seat).alive
                ? RouletteDirector.frame(seat) : null;
        if (f == null || !com.liarsbar.client.render.BarRoom.active()) {
            removeLight(rim);
            rim = null;
            return;
        }
        // Di belakang kepala, condong ke barat (jendela).
        Vec3 at = f.eye().add(f.forward().scale(-0.75)).add(-0.55, 0.25, 0);
        if (rim == null) {
            rim = new PointLight();
            addLight(rim);
        }
        float sil = RouletteDirector.silhouette(0);
        rim.setPosition(at.x, at.y, at.z).setColor(0.36f, 0.78f, 1.0f).setRadius(1.3f + 0.4f * sil)
                .setBrightness(RIM_LIGHT * focus(seat) * (1 + 2.5f * sil));
    }

    /** Pantulan hangat felt ke wajah pemain yang jadi fokus (dari bawah-depan, lembut). */
    private static void bounceLight(PublicState state, Vec3 center) {
        int seat = focusSeat(state);
        boolean alive = seat >= 0 && seat < state.seats.size() && state.seats.get(seat).alive;
        if (!alive) {
            removeLight(bounce);
            bounce = null;
            return;
        }
        RouletteDirector.Frame f = RouletteDirector.frame(seat);
        if (f == null) {
            return;
        }
        Vec3 at = f.eye().add(f.forward().scale(0.55)).add(0, -0.45, 0);
        if (bounce == null) {
            bounce = new PointLight();
            addLight(bounce);
        }
        bounce.setPosition(at.x, at.y, at.z).setColor(1.0f, 0.7f, 0.45f).setRadius(1.25f)
                .setBrightness(BOUNCE * focus(seat) * (1 - ROULETTE_BOUNCE_CUT * hush));
    }

    private static PointLight face;
    private static float faceW;

    /**
     * Key light sinematik ke wajah penembak selama roulette: dari depan-kiri (sisi tanpa pistol),
     * jadi separuh wajah terang dan separuhnya tenggelam. Masuk pelan, padam begitu DOR.
     */
    private static void faceLight() {
        PublicState state = ClientTable.state();
        int seat = RouletteDirector.active() && state != null && !state.shooters.isEmpty() ? state.shooters.get(0) : -1;
        RouletteDirector.Frame f = seat >= 0 ? RouletteDirector.frame(seat) : null;
        float want = 0;
        if (f != null) {
            RouletteDirector.Act act = RouletteDirector.act(seat, 0);
            float t = act.tick();
            want = switch (act.phase()) {
                case ROULETTE -> Mth.clamp(t / 20f, 0, 1);
                case CLICK -> 1 - Mth.clamp((t - 30) / 25f, 0, 1);
                default -> 0;
            };
            if (act.phase() == RouletteDirector.Phase.BANG) {
                faceW = 0;
            }
        }
        want *= 1 - RouletteDirector.silhouette(0) * 0.85f;
        faceW += (want - faceW) * 0.25f;
        if (f == null || faceW < 0.01f) {
            removeLight(face);
            face = null;
            return;
        }
        Vec3 at = f.eye().add(f.forward().scale(0.7)).add(f.right().scale(-0.4)).add(0, 0.15, 0);
        if (face == null) {
            face = new PointLight();
            addLight(face);
        }
        face.setPosition(at.x, at.y, at.z).setColor(1.0f, 0.84f, 0.66f).setRadius(1.5f).setBrightness(0.28f * faceW);
    }
}
