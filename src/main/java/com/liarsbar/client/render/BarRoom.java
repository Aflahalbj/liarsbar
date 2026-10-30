package com.liarsbar.client.render;

import com.liarsbar.block.BarBlocks;
import com.liarsbar.block.ModBlocks;
import com.liarsbar.client.ClientTable;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.liarsbar.client.fx.TableLights;
import com.liarsbar.entity.TableEntity;
import com.liarsbar.table.PublicState;
import foundry.veil.api.client.render.deferred.light.AreaLight;
import foundry.veil.api.client.render.deferred.light.PointLight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Bar block di sekeliling meja (struktur {@code /liarsbar bar}). Aktif kalau meja berdiri di lantai
 * {@code liarsbar:bar_floor} dan dikelilingi dinding bar. Bloknya digambar dunia; dari sini dipasang cahaya
 * Veil yang diambil dari block jendela (area cyan), sconce (titik hangat), dan rak botol (strip), plus
 * kilat sesekali di jendela (selalu sesaat sesudah DOR) dan suara hujan di kaca.
 */
public final class BarRoom {

    /** Kemiringan cahaya jendela ke bawah (rad). */
    private static final float WINDOW_TILT = 0.3f;
    /** Terang cahaya jendela, sconce, strip rak, dan jangkauan cahaya jendela (AnimDev "roomcfg"). */
    private static float windowLight = 1.5f;
    /**
     * Jangkauan area light jendela (blok). Pendek: cahaya jendela jadi rim di dekat dinding barat, bukan key
     * light yang membakar wajah orang yang duduk menghadap jendela beberapa blok di depannya.
     */
    private static float windowReach = 3.0f;
    private static float sconceLight = 0.75f;
    private static float shelfLight = 1.2f;
    /** Jarak cari dinding dari pusat meja (blok). */
    private static final int REACH = 14;

    public static void tune(float[] v) {
        windowLight = v[0];
        sconceLight = v[1];
        shelfLight = v[2];
        if (v[3] > 0) {
            windowReach = v[3];
        }
        if (active) {
            createLights();
        }
    }

    private static boolean active;
    private static int tableId = -1;
    private static Vec3 center = Vec3.ZERO;
    /** Jarak pusat meja ke dinding dalam (x: timur-barat, z: utara-selatan) dan tinggi plafon. */
    private static float halfX, halfZ, height;
    private static int checkIn;

    private static final List<PointLight> LIGHTS = new ArrayList<>();
    private static final List<AreaLight> WINDOW_LIGHTS = new ArrayList<>();
    /** Pusat kaca tiap jendela (dunia), untuk asal suara hujan dan guntur. */
    private static final List<Vec3> WINDOWS = new ArrayList<>();
    /** Jendela dinding barat: {z pusat, lebar, y bawah, y atas} relatif pusat meja. */
    private static final List<float[]> WEST = new ArrayList<>();

    private static long strikeAt = Long.MIN_VALUE;
    private static long nextStrike = 200;
    private static long thunderAt = Long.MIN_VALUE;
    private static long handledBang = Long.MIN_VALUE;
    private static final RandomSource RANDOM = RandomSource.create();

    private BarRoom() {
    }

    public static boolean active() {
        return active;
    }

    /** Jarak pusat meja ke dinding barat/timur (berkas cahaya jendela barat). */
    public static float half() {
        return halfX;
    }

    /** Jendela dinding barat {z pusat, lebar, y bawah, y atas} relatif pusat meja. */
    public static List<float[]> westWindows() {
        return WEST;
    }

    /** Titik ini di dalam bar yang sedang aktif. */
    public static boolean contains(double x, double y, double z) {
        return active && Math.abs(x - center.x) <= halfX + 0.1 && Math.abs(z - center.z) <= halfZ + 0.1
                && y >= center.y - 0.6 && y <= center.y + height + 0.6;
    }

    public static boolean contains(Entity entity) {
        return contains(entity.getX(), entity.getY() + 0.1, entity.getZ());
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        PublicState state = ClientTable.state();
        TableEntity table = state == null ? null : RouletteDirector.tableEntity();
        if (table == null || mc.level == null || !com.liarsbar.client.test.DevToggles.on("barroom")) {
            if (active) {
                active = false;
                clearLights();
            }
            return;
        }
        if (state.tableId != tableId || --checkIn <= 0) {
            checkIn = 40;
            tableId = state.tableId;
            float[] before = {halfX, halfZ, height};
            Vec3 at = table.position();
            boolean found = detect(table);
            boolean moved = !at.equals(center);
            center = at;
            // Lampu dipasang ulang hanya bila bar baru ditemukan atau ukurannya berubah.
            if (found && (!active || moved || before[0] != halfX || before[1] != halfZ || before[2] != height)) {
                active = true;
                createLights();
            } else if (!found && active) {
                active = false;
                clearLights();
            }
        }
        if (!active) {
            return;
        }
        long now = ClientTable.now();
        long bang = RouletteDirector.bangAt();
        if (bang != Long.MIN_VALUE && bang != handledBang && now - bang < 5) {
            handledBang = bang;
            strike(bang + 5);
        }
        if (now >= nextStrike && strikeAt < now - 60) {
            strike(now);
        }
        if (thunderAt != Long.MIN_VALUE && now >= thunderAt) {
            thunderAt = Long.MIN_VALUE;
            Vec3 w = WINDOWS.isEmpty() ? center : WINDOWS.get(RANDOM.nextInt(WINDOWS.size()));
            mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.AMBIENT,
                    0.45f, 0.55f + RANDOM.nextFloat() * 0.2f, RANDOM, w.x, w.y, w.z));
        }
        if (now % 22 == 0 && !WINDOWS.isEmpty()) {
            // Hujan di kaca: suara hujan vanilla diredam (pitch rendah) dari salah satu jendela.
            Vec3 w = WINDOWS.get(RANDOM.nextInt(WINDOWS.size()));
            mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.WEATHER_RAIN, SoundSource.AMBIENT, 0.09f,
                    0.5f + RANDOM.nextFloat() * 0.1f, RANDOM, w.x, w.y, w.z));
        }
        float flash = lightning(0);
        for (AreaLight light : WINDOW_LIGHTS) {
            light.setBrightness(windowLight * (1 + 4.0f * flash));
        }
    }

    private static void strike(long at) {
        strikeAt = at;
        nextStrike = at + 500 + RANDOM.nextInt(900);
        thunderAt = at + 18 + RANDOM.nextInt(30);
    }

    /** Terang kilat 0..1 (dua sambaran beruntun). */
    public static float lightning(float partial) {
        if (!active || strikeAt == Long.MIN_VALUE) {
            return 0;
        }
        float a = ClientTable.now() + partial - strikeAt;
        if (a < 0) {
            return 0;
        }
        float f = (float) Math.exp(-a / 2.0);
        if (a > 5) {
            f += 0.75f * (float) Math.exp(-(a - 5) / 2.6);
        }
        return Mth.clamp(f, 0, 1);
    }

    // ============================================================ deteksi

    private static boolean isRoomEdge(BlockState state) {
        return state.is(ModBlocks.WALL.get()) || state.is(ModBlocks.WINDOW.get()) || state.is(ModBlocks.DOOR.get())
                || state.is(ModBlocks.BRICK.get());
    }

    /** Jarak (blok) ke dinding bar pertama dari {@code from} ke arah {@code d}; -1 kalau tidak ada. */
    private static int wallDistance(Level level, BlockPos from, Direction d) {
        for (int k = 1; k <= REACH; k++) {
            if (isRoomEdge(level.getBlockState(from.relative(d, k)))) {
                return k;
            }
        }
        return -1;
    }

    /** Meja di lantai bar, dinding bar di keempat sisi, plafon di atas: ukur ruangnya. */
    private static boolean detect(TableEntity table) {
        Level level = table.level();
        BlockPos c = table.blockPosition();
        if (!level.getBlockState(c.below()).is(ModBlocks.FLOOR.get())) {
            return false;
        }
        BlockPos head = c.above();
        int e = wallDistance(level, head, Direction.EAST), w = wallDistance(level, head, Direction.WEST);
        int n = wallDistance(level, head, Direction.NORTH), s = wallDistance(level, head, Direction.SOUTH);
        if (e < 0 || w < 0 || n < 0 || s < 0) {
            return false;
        }
        int up = 1;
        while (up < 12 && level.getBlockState(c.above(up)).isAir()) {
            up++;
        }
        halfX = Math.min(e, w) - 0.5f;
        halfZ = Math.min(n, s) - 0.5f;
        height = up;
        return true;
    }

    // ============================================================ lampu

    private static void addPoint(Vec3 at, float r, float g, float b, float radius, float brightness) {
        PointLight light = new PointLight();
        light.setPosition(at.x, at.y, at.z).setColor(r, g, b).setRadius(radius).setBrightness(brightness);
        LIGHTS.add(light);
        TableLights.addLight(light);
    }

    /** Cahaya dari fixture di dalam ruang: jendela (area cyan), sconce (titik hangat), rak botol (strip). */
    private static void createLights() {
        clearLights();
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockPos c = BlockPos.containing(center);
        int rx = Mth.ceil(halfX) + 1, rz = Mth.ceil(halfZ) + 1;
        List<BlockPos> shelves = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-rx, 0, -rz), c.offset(rx, (int) height, rz))) {
            BlockState st = level.getBlockState(p);
            double lx = p.getX() + 0.5 - center.x, ly = p.getY() - center.y, lz = p.getZ() + 0.5 - center.z;
            boolean inside = Math.abs(lx) < halfX && Math.abs(lz) < halfZ;
            if (st.is(ModBlocks.WINDOW.get())) {
                if (st.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER
                        || st.getValue(BarBlocks.NoirWindow.PANE) == BarBlocks.Pane.LEFT) {
                    continue;
                }
                window(st, lx, ly, lz);
            } else if (st.is(ModBlocks.SCONCE.get()) && inside) {
                // Di atas kap yang terbuka ke atas dan dekat dinding: menyiram wallpaper, bukan kepala orang yang
                // duduk di bawahnya (titik Veil sedekat 1,5 blok membakar wajah jadi putih).
                Direction f = st.getValue(BarBlocks.Facing.FACING);
                addPoint(center.add(lx + f.getStepX() * 0.08, ly + 0.98, lz + f.getStepZ() * 0.08), 1.0f, 0.62f, 0.32f,
                        2.8f, sconceLight);
            } else if (st.is(ModBlocks.SHELF.get()) && inside) {
                shelves.add(p.immutable());
            }
        }
        shelfLights(level, shelves);
    }

    /** Jendela 2 lebar (tile RIGHT + LEFT di sebelah kirinya) atau tunggal: area light cyan condong ke lantai. */
    private static void window(BlockState st, double lx, double ly, double lz) {
        Direction in = st.getValue(BarBlocks.Facing.FACING);
        boolean single = st.getValue(BarBlocks.NoirWindow.PANE) == BarBlocks.Pane.SINGLE;
        Direction left = in.getClockWise();
        float w = (single ? 1 : 2) * 0.9f;
        double gx = lx + (single ? 0 : left.getStepX() * 0.5) + in.getStepX() * 0.45;
        double gz = lz + (single ? 0 : left.getStepZ() * 0.5) + in.getStepZ() * 0.45;
        Vec3 at = center.add(gx, ly + 1.0, gz);
        WINDOWS.add(at);
        if (in == Direction.EAST) {
            WEST.add(new float[]{(float) gz, w, (float) ly + 0.12f, (float) ly + 1.9f});
        }
        Quaternionf inv = new Quaternionf().rotationY((float) Math.atan2(in.getStepX(), in.getStepZ())).rotateX(WINDOW_TILT);
        AreaLight light = new AreaLight();
        light.setPosition(at.x, at.y, at.z).setColor(0.36f, 0.78f, 1.0f).setSize(w * 0.45f, 0.9f)
                .setAngle(0.75f).setDistance(windowReach).setOrientation(inv.conjugate()).setBrightness(windowLight);
        WINDOW_LIGHTS.add(light);
        TableLights.addLight(light);
    }

    /** Satu lampu hangat per gugus rak botol yang bersambung, di depan pusat gugusnya. */
    private static void shelfLights(Level level, List<BlockPos> shelves) {
        while (!shelves.isEmpty()) {
            List<BlockPos> group = new ArrayList<>();
            ArrayDeque<BlockPos> open = new ArrayDeque<>();
            open.add(shelves.remove(0));
            while (!open.isEmpty()) {
                BlockPos p = open.poll();
                group.add(p);
                for (Direction d : Direction.values()) {
                    if (shelves.remove(p.relative(d))) {
                        open.add(p.relative(d));
                    }
                }
            }
            double sx = 0, sy = 0, sz = 0;
            for (BlockPos p : group) {
                sx += p.getX() + 0.5;
                sy += p.getY() + 0.5;
                sz += p.getZ() + 0.5;
            }
            Direction f = level.getBlockState(group.get(0)).getValue(BarBlocks.Facing.FACING);
            int k = group.size();
            addPoint(new Vec3(sx / k + f.getStepX() * 0.5, sy / k, sz / k + f.getStepZ() * 0.5), 1.0f, 0.66f, 0.36f,
                    2.2f, shelfLight);
        }
    }

    private static void clearLights() {
        for (PointLight light : LIGHTS) {
            TableLights.removeLight(light);
        }
        for (AreaLight light : WINDOW_LIGHTS) {
            TableLights.removeLight(light);
        }
        LIGHTS.clear();
        WINDOW_LIGHTS.clear();
        WINDOWS.clear();
        WEST.clear();
    }

    /** Bersihkan saat dunia ditutup. */
    public static void clear() {
        clearLights();
        active = false;
        tableId = -1;
    }
}
