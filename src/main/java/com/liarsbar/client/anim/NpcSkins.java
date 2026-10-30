package com.liarsbar.client.anim;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.liarsbar.entity.BarNpcEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skin NPC bar diambil dari akun Minecraft asli (lewat nama): bartender selalu {@link #BARTENDER}, pengunjung
 * bergiliran dari {@link #PATRONS}. Diunduh sekali di latar belakang; selama belum ada (atau offline) NPC
 * memakai skin bawaan.
 */
public final class NpcSkins {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String BARTENDER = "itslyricss";
    /** Akun acak yang tidak terkenal (skinnya sudah dicek satu per satu: pantas, bukan Steve/skin terkenal). */
    private static final String[] PATRONS = {"oldwhiskey", "velvetcrow", "dustyhat", "silvercane", "tomgreer", "nightowl_"};

    private static final Map<String, Skin> CACHE = new ConcurrentHashMap<>();

    /** Tekstur dan model skin yang sudah terdaftar; null selama masih diunduh atau gagal. */
    public static final class Skin {
        volatile ResourceLocation texture;
        volatile boolean slim;
    }

    private NpcSkins() {
    }

    /** Nama akun yang skinnya dipakai NPC ini. */
    public static String name(BarNpcEntity npc) {
        return npc.role() == BarNpcEntity.Role.BARTENDER ? BARTENDER : PATRONS[Math.floorMod(npc.skin(), PATRONS.length)];
    }

    /** Skin NPC ini; unduhan dimulai saat pertama diminta. */
    public static Skin get(BarNpcEntity npc) {
        return CACHE.computeIfAbsent(name(npc).toLowerCase(Locale.ROOT), NpcSkins::fetch);
    }

    private static Skin fetch(String name) {
        Skin skin = new Skin();
        CompletableFuture.supplyAsync(() -> lookup(name), Util.backgroundExecutor()).thenAccept(profile -> {
            if (profile == null) {
                return;
            }
            // SkinManager mengisi properti profil dari session server lalu mendaftarkan teksturnya di render thread.
            Minecraft.getInstance().getSkinManager().registerSkins(profile, (type, location, texture) -> {
                if (type == MinecraftProfileTexture.Type.SKIN) {
                    skin.slim = "slim".equals(texture.getMetadata("model"));
                    skin.texture = location;
                }
            }, false);
        });
        return skin;
    }

    /** UUID akun dari nama (Mojang API); null kalau tidak ada atau offline. */
    private static GameProfile lookup(String name) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://api.mojang.com/users/profiles/minecraft/" + name).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            if (c.getResponseCode() != 200) {
                LOGGER.warn("Skin NPC '{}' tidak ditemukan (HTTP {})", name, c.getResponseCode());
                return null;
            }
            try (InputStreamReader in = new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(in).getAsJsonObject();
                String id = json.get("id").getAsString();
                UUID uuid = UUID.fromString(id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                return new GameProfile(uuid, json.get("name").getAsString());
            }
        } catch (Exception e) {
            LOGGER.warn("Gagal mengambil skin NPC '{}': {}", name, e.toString());
            return null;
        }
    }
}
