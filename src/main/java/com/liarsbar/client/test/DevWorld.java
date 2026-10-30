package com.liarsbar.client.test;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

/**
 * Mode uji development ({@code -Pclienttest}): begitu title screen muncul,
 * langsung masuk world {@value #NAME} (flat, creative, damai, cheat aktif).
 * Kalau belum ada, dibuat dulu. Dengan {@code -Pworld=open}: world baru bertopografi normal.
 */
public final class DevWorld {

    public static final String NAME = "LiarsTest";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean opened;

    private DevWorld() {
    }

    public static void init() {
        if (System.getProperty("liarsbar.clienttest") == null && !Boolean.getBoolean("liarsbar.animtest")) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(DevWorld::onScreen);
    }

    private static void onScreen(ScreenEvent.Init.Post event) {
        if (opened || !(event.getScreen() instanceof TitleScreen title)) {
            return;
        }
        opened = true;
        Minecraft client = Minecraft.getInstance();
        if ("open".equals(System.getProperty("liarsbar.world"))) {
            openWorld(client);
            return;
        }
        if (client.getLevelSource().levelExists(NAME)) {
            LOGGER.info("[devworld] opening world {}", NAME);
            client.createWorldOpenFlows().loadLevel(title, NAME);
            return;
        }
        LOGGER.info("[devworld] creating world {}", NAME);
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        LevelSettings settings = new LevelSettings(NAME, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(WorldOptions.randomSeed(), false, false);
        client.createWorldOpenFlows().createFreshLevel(NAME, settings, options,
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions());
    }

    /**
     * Kondisi pemain biasa ({@code -Pworld=open}): world baru tiap launch, terrain normal, siklus siang aktif
     * (dimulai siang), cheat aktif supaya perintah /liarsbar bisa dipakai.
     */
    private static void openWorld(Minecraft client) {
        String name = "LiarsOpen-" + System.currentTimeMillis() / 1000;
        LOGGER.info("[devworld] creating open world {}", name);
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(WorldOptions.randomSeed(), true, false);
        client.createWorldOpenFlows().createFreshLevel(name, settings, options,
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value()
                        .createWorldDimensions());
    }
}
