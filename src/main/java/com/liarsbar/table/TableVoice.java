package com.liarsbar.table;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Jembatan ke Simple Voice Chat (opsional). Satu group voice per meja, jadi
 * semua di meja saling dengar walau mejanya besar. Pemain yang sudah mati dan
 * penonton jadi "hantu": mereka mendengar meja, tapi suaranya hanya sampai ke
 * sesama hantu. Backend-nya dipasang plugin voice chat kalau mod itu ada.
 */
public final class TableVoice {

    public interface Backend {
        /** {@code living}: boleh bicara ke semua; {@code ghosts}: hanya terdengar sesama hantu. */
        void update(int tableId, Set<UUID> living, Set<UUID> ghosts);

        void remove(int tableId);
    }

    private static volatile Backend backend;

    private TableVoice() {
    }

    public static void setBackend(Backend b) {
        backend = b;
    }

    static void update(TableInstance table) {
        Backend b = backend;
        if (b == null) {
            return;
        }
        Set<UUID> living = new HashSet<>();
        Set<UUID> ghosts = new HashSet<>();
        for (UUID player : table.humanPlayers()) {
            if (!table.isSeated(player)) {
                continue;
            }
            (table.isAlive(player) ? living : ghosts).add(player);
        }
        for (UUID watcher : TableManager.watcherIds(table)) {
            if (!living.contains(watcher)) {
                ghosts.add(watcher);
            }
        }
        b.update(table.id(), living, ghosts);
    }

    static void remove(TableInstance table) {
        Backend b = backend;
        if (b != null) {
            b.remove(table.id());
        }
    }
}
