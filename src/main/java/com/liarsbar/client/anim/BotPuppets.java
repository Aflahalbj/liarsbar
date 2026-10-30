package com.liarsbar.client.anim;

import com.liarsbar.entity.BotEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Satu {@link BotPuppet} untuk tiap {@link BotEntity} di dunia client; dibuang bersama bot-nya. */
public final class BotPuppets {

    private static final Map<BotEntity, BotPuppet> PUPPETS = new HashMap<>();
    private static ClientLevel level;
    /** Id entity client-only: negatif, supaya tidak bentrok dengan id dari server. */
    private static int nextId = -10_000;

    private BotPuppets() {
    }

    public static BotPuppet of(BotEntity bot) {
        return PUPPETS.get(bot);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            PUPPETS.clear();
            level = mc.level;
        }
        if (level == null) {
            return;
        }
        List<BotEntity> bots = new ArrayList<>();
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof BotEntity bot && !bot.isRemoved()) {
                bots.add(bot);
            }
        }
        for (BotEntity bot : bots) {
            if (!PUPPETS.containsKey(bot)) {
                BotPuppet puppet = new BotPuppet(level, bot);
                level.addPlayer(nextId--, puppet);
                PUPPETS.put(bot, puppet);
            }
        }
        PUPPETS.entrySet().removeIf(entry -> {
            if (entry.getKey().isRemoved() || !bots.contains(entry.getKey())) {
                level.removeEntity(entry.getValue().getId(), Entity.RemovalReason.DISCARDED);
                return true;
            }
            return false;
        });
    }
}
