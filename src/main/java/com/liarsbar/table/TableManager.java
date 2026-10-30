package com.liarsbar.table;

import com.liarsbar.entity.SeatEntity;
import com.liarsbar.network.ModNetwork;
import com.liarsbar.network.TableActionC2S;
import com.liarsbar.network.TableStateS2C;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Semua meja yang sedang ada (banyak meja bisa jalan bersamaan). Juga
 * menentukan meja mana yang "dilihat" tiap pemain: meja tempat dia duduk,
 * atau meja terdekat dalam {@link #WATCH_RANGE} blok (penonton).
 */
public final class TableManager {

    public static final double WATCH_RANGE = 40;

    private static final Map<Integer, TableInstance> TABLES = new LinkedHashMap<>();
    /** Pemain -> id meja yang sedang dia lihat. */
    private static final Map<UUID, Integer> WATCHING = new HashMap<>();
    private static int nextId = 1;
    /** True selama kode meja sendiri menurunkan pemain dari kursi. */
    static boolean allowDismount;
    private static MinecraftServer server;

    private TableManager() {
    }

    public static TableInstance get(int id) {
        return TABLES.get(id);
    }

    public static Collection<TableInstance> all() {
        return TABLES.values();
    }

    public static TableInstance create(ServerLevel level, Vec3 center, boolean fast) {
        server = level.getServer();
        TableInstance table = new TableInstance(nextId++, level, center, fast);
        TABLES.put(table.id, table);
        refreshWatchers();
        return table;
    }

    public static void remove(TableInstance table) {
        TABLES.remove(table.id);
        table.dispose();
        refreshWatchers();
    }

    /** Meja tempat pemain ini duduk (termasuk yang sedang keluar dari kursinya). */
    public static TableInstance tableOf(UUID player) {
        for (TableInstance table : TABLES.values()) {
            if (table.member(player) != null) {
                return table;
            }
        }
        return null;
    }

    public static TableInstance nearest(ServerPlayer player, double range) {
        TableInstance best = null;
        double bestDistance = range * range;
        for (TableInstance table : TABLES.values()) {
            if (table.level != player.level()) {
                continue;
            }
            double distance = table.center.distanceToSqr(player.position());
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = table;
            }
        }
        return best;
    }

    static List<ServerPlayer> watchers(TableInstance table) {
        List<ServerPlayer> players = new ArrayList<>();
        if (server == null) {
            return players;
        }
        for (Map.Entry<UUID, Integer> entry : WATCHING.entrySet()) {
            if (entry.getValue() == table.id) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    players.add(player);
                }
            }
        }
        return players;
    }

    static List<UUID> watcherIds(TableInstance table) {
        List<UUID> ids = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : WATCHING.entrySet()) {
            if (entry.getValue() == table.id) {
                ids.add(entry.getKey());
            }
        }
        return ids;
    }

    /** Hitung ulang meja yang dilihat tiap pemain; kirim state baru ke yang berpindah meja. */
    private static void refreshWatchers() {
        if (server == null) {
            return;
        }
        List<TableInstance> changed = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TableInstance seated = tableOf(player.getUUID());
            TableInstance table = seated != null && seated.isSeated(player.getUUID())
                    ? seated : nearest(player, WATCH_RANGE);
            int id = table == null ? -1 : table.id;
            Integer previous = WATCHING.get(player.getUUID());
            if (previous != null && previous == id) {
                continue;
            }
            if (id < 0) {
                WATCHING.remove(player.getUUID());
                ModNetwork.send(player, TableStateS2C.none());
            } else {
                WATCHING.put(player.getUUID(), id);
                table.sendState(player);
                changed.add(table);
            }
            if (previous != null && TABLES.containsKey(previous)) {
                changed.add(TABLES.get(previous));
            }
        }
        for (TableInstance table : changed) {
            TableVoice.update(table);
        }
    }

    public static void onAction(ServerPlayer player, TableActionC2S action) {
        TableInstance table = tableOf(player.getUUID());
        if (table != null && table.isSeated(player.getUUID())) {
            table.onAction(player, action);
        }
    }

    // ============================================================== event

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        server = event.getServer();
        for (TableInstance table : new ArrayList<>(TABLES.values())) {
            table.tick();
        }
        if (server.getTickCount() % 10 == 0) {
            refreshWatchers();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        // Lepas dari penonton dulu, supaya pemain yang sudah offline tidak ikut dihitung di group voice.
        WATCHING.remove(id);
        TableInstance table = tableOf(id);
        if (table != null) {
            // Turunkan dulu, supaya yang tersimpan posisi berdiri (titik duduk ada di bawah lantai).
            if (event.getEntity() instanceof ServerPlayer player) {
                table.dismount(player);
            }
            table.onDisconnect(id);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TableInstance table = tableOf(player.getUUID());
            if (table != null) {
                table.rejoin(player);
            }
        }
    }

    /** Pemain dan bot tidak bisa turun dari kursi sendiri (shift) selama duduk di meja. */
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.isDismounting() && !allowDismount && !event.getLevel().isClientSide()
                && event.getEntityBeingMounted() instanceof SeatEntity seat && get(seat.tableId()) != null) {
            event.setCanceled(true);
        }
    }

    /** Yang sedang duduk di meja tidak bisa terluka (hanya pistol yang bisa membunuh). */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getVehicle() instanceof SeatEntity) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TableInstance table = tableOf(player.getUUID());
            if (table != null) {
                table.leave(player);
            }
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TableInstance table = tableOf(player.getUUID());
            if (table != null && table.level != player.level()) {
                table.leave(player);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (TableInstance table : new ArrayList<>(TABLES.values())) {
            remove(table);
        }
        WATCHING.clear();
        nextId = 1;
    }
}
