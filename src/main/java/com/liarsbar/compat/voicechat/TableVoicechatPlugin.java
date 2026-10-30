package com.liarsbar.compat.voicechat;

import com.liarsbar.LiarsBar;
import com.liarsbar.table.TableVoice;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Group voice per meja (hanya dimuat kalau Simple Voice Chat terpasang).
 * Semua yang terkait meja ada di satu group terisolasi, jadi 10 pemain saling
 * dengar di meja besar dan orang di luar meja tidak ikut dengar. Suara hantu
 * (pemain mati, penonton) dicegat lalu diteruskan hanya ke sesama hantu.
 */
@ForgeVoicechatPlugin
public class TableVoicechatPlugin implements VoicechatPlugin, TableVoice.Backend {

    private static final Logger LOGGER = LogUtils.getLogger();

    private volatile VoicechatServerApi api;
    /** Id meja -> group voice-nya. */
    private final Map<Integer, Group> groups = new ConcurrentHashMap<>();
    /** Id meja -> semua anggota group (hidup + hantu). */
    private final Map<Integer, Set<UUID>> members = new ConcurrentHashMap<>();
    /** Hantu -> id mejanya. */
    private final Map<UUID, Integer> ghostTable = new ConcurrentHashMap<>();
    /** Id meja -> hantunya. */
    private final Map<Integer, Set<UUID>> ghosts = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return LiarsBar.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        TableVoice.setBackend(this);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, e -> api = e.getVoicechat());
        registration.registerEvent(VoicechatServerStoppedEvent.class, e -> {
            api = null;
            groups.clear();
            members.clear();
            ghostTable.clear();
            ghosts.clear();
        });
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        VoicechatServerApi server = api;
        VoicechatConnection sender = event.getSenderConnection();
        if (server == null || sender == null) {
            return;
        }
        UUID id = sender.getPlayer().getUuid();
        Integer table = ghostTable.get(id);
        if (table == null) {
            return;
        }
        // Hantu tidak terdengar oleh yang masih hidup: kirim manual ke sesama hantu saja.
        event.cancel();
        StaticSoundPacket packet = event.getPacket().toStaticSoundPacket();
        for (UUID ghost : ghosts.getOrDefault(table, Set.of())) {
            if (!ghost.equals(id)) {
                VoicechatConnection connection = server.getConnectionOf(ghost);
                if (connection != null) {
                    server.sendStaticSoundPacketTo(connection, packet);
                }
            }
        }
    }

    @Override
    public void update(int tableId, Set<UUID> living, Set<UUID> ghostSet) {
        VoicechatServerApi server = api;
        if (server == null) {
            return;
        }
        Group group = groups.computeIfAbsent(tableId, id -> {
            LOGGER.info("[voice] group for table {} created", id);
            return server.groupBuilder()
                    .setName("Liar's Table #" + id)
                    .setType(Group.Type.ISOLATED)
                    .setHidden(true)
                    .setPersistent(false)
                    .build();
        });
        Set<UUID> all = new HashSet<>(living);
        all.addAll(ghostSet);
        Set<UUID> previous = members.getOrDefault(tableId, Set.of());
        for (UUID player : previous) {
            if (!all.contains(player)) {
                leave(server, player, group);
            }
        }
        for (UUID player : all) {
            VoicechatConnection connection = server.getConnectionOf(player);
            if (connection != null && (connection.getGroup() == null
                    || !connection.getGroup().getId().equals(group.getId()))) {
                connection.setGroup(group);
            }
        }
        if (!all.equals(members.getOrDefault(tableId, Set.of())) || !ghostSet.equals(ghosts.getOrDefault(tableId, Set.of()))) {
            LOGGER.info("[voice] table {}: {} living, {} ghosts", tableId, living.size(), ghostSet.size());
        }
        members.put(tableId, Set.copyOf(all));
        Set<UUID> oldGhosts = ghosts.getOrDefault(tableId, Set.of());
        for (UUID ghost : oldGhosts) {
            ghostTable.remove(ghost, tableId);
        }
        for (UUID ghost : ghostSet) {
            ghostTable.put(ghost, tableId);
        }
        ghosts.put(tableId, Set.copyOf(ghostSet));
    }

    @Override
    public void remove(int tableId) {
        VoicechatServerApi server = api;
        Group group = groups.remove(tableId);
        Set<UUID> old = members.remove(tableId);
        Set<UUID> oldGhosts = ghosts.remove(tableId);
        if (oldGhosts != null) {
            for (UUID ghost : oldGhosts) {
                ghostTable.remove(ghost, tableId);
            }
        }
        if (server == null || group == null) {
            return;
        }
        if (old != null) {
            for (UUID player : old) {
                leave(server, player, group);
            }
        }
        server.removeGroup(group.getId());
    }

    private static void leave(VoicechatServerApi server, UUID player, Group group) {
        VoicechatConnection connection = server.getConnectionOf(player);
        if (connection != null && connection.getGroup() != null && connection.getGroup().getId().equals(group.getId())) {
            connection.setGroup(null);
        }
    }
}
