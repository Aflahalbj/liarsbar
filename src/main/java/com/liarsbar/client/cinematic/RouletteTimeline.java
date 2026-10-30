package com.liarsbar.client.cinematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.liarsbar.LiarsBar;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Timeline roulette yang data-driven ({@code assets/liarsbar/cinematic/roulette.json},
 * dibuat tools/table_anims.py bersama animasinya): waktu tiap fase, kunci
 * kamera relatif penembak, keadaan revolver, dan suara. Dimuat ulang bersama
 * resource (F3+T), jadi bisa dituning tanpa compile ulang.
 */
public final class RouletteTimeline implements ResourceManagerReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation FILE = new ResourceLocation(LiarsBar.MOD_ID, "cinematic/roulette.json");

    /** Satu kunci kamera: posisi dan titik tatap di kerangka penembak (kanan, atas, depan), FOV. */
    public record CamKey(float t, float[] pos, float[] aim, float fov, boolean cut) {
    }

    /** Nilai berkunci (tick, nilai) yang diinterpolasi linear. */
    public record Curve(float[] ticks, float[] values) {
        static final Curve ZERO = new Curve(new float[]{0}, new float[]{0});

        public float at(float t) {
            if (t <= ticks[0]) {
                return values[0];
            }
            for (int i = 1; i < ticks.length; i++) {
                if (t <= ticks[i]) {
                    float u = (t - ticks[i - 1]) / Math.max(1e-4f, ticks[i] - ticks[i - 1]);
                    return values[i - 1] + (values[i] - values[i - 1]) * u;
                }
            }
            return values[values.length - 1];
        }
    }

    /** Revolver per fase: rentang di tangan, buka crane, putar silinder, kokang, pelatuk. */
    public record Gun(float handFrom, float handTo, Curve open, float spinFrom, float spinTo, Curve cock, Curve trigger) {
    }

    public record Cue(int tick, String sound) {
    }

    private static Map<String, Map<String, Integer>> timing = Map.of();
    private static Map<String, Integer> deathFall = Map.of();
    private static Map<String, List<CamKey>> camera = Map.of();
    private static Map<String, Gun> guns = Map.of();
    private static Map<String, List<Cue>> sounds = Map.of();
    private static Map<String, float[]> gunPath = Map.of();

    /** Titik genggam revolver (kanan, atas, depan dari mata penembak) untuk pose bernama. */
    public static float[] gunPath(String name, float[] fallback) {
        return gunPath.getOrDefault(name, fallback);
    }

    public static int timing(String phase, String key, int fallback) {
        return Optional.ofNullable(timing.get(phase)).map(m -> m.get(key)).orElse(fallback);
    }

    public static int deathFall(String animation) {
        return deathFall.getOrDefault(animation, 30);
    }

    public static List<CamKey> camera(String phase) {
        return camera.getOrDefault(phase, List.of());
    }

    public static Gun gun(String phase) {
        return guns.get(phase);
    }

    public static List<Cue> sounds(String phase) {
        return sounds.getOrDefault(phase, List.of());
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Optional<Resource> resource = manager.getResource(FILE);
        if (resource.isEmpty()) {
            LOGGER.error("Missing {}", FILE);
            return;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, Map<String, Integer>> t = new HashMap<>();
            for (Map.Entry<String, JsonElement> phase : root.getAsJsonObject("timing").entrySet()) {
                Map<String, Integer> keys = new HashMap<>();
                for (Map.Entry<String, JsonElement> k : phase.getValue().getAsJsonObject().entrySet()) {
                    keys.put(k.getKey(), k.getValue().getAsInt());
                }
                t.put(phase.getKey(), keys);
            }
            Map<String, Integer> d = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("death").entrySet()) {
                d.put(e.getKey(), e.getValue().getAsJsonObject().get("fall").getAsInt());
            }
            Map<String, List<CamKey>> cam = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("camera").entrySet()) {
                List<CamKey> keys = new ArrayList<>();
                for (JsonElement k : e.getValue().getAsJsonArray()) {
                    JsonObject o = k.getAsJsonObject();
                    keys.add(new CamKey(o.get("t").getAsFloat(), floats(o.getAsJsonArray("pos")),
                            floats(o.getAsJsonArray("aim")), o.get("fov").getAsFloat(),
                            o.has("cut") && o.get("cut").getAsBoolean()));
                }
                cam.put(e.getKey(), List.copyOf(keys));
            }
            Map<String, Gun> g = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("gun").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                float[] hand = o.has("hand") ? floats(o.getAsJsonArray("hand")) : new float[]{-1, -1};
                float[] spin = o.has("spin") ? floats(o.getAsJsonArray("spin")) : new float[]{-1, -1};
                g.put(e.getKey(), new Gun(hand[0], hand[1], curve(o, "open"), spin[0], spin[1], curve(o, "cock"),
                        curve(o, "trigger")));
            }
            Map<String, List<Cue>> s = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("sounds").entrySet()) {
                List<Cue> cues = new ArrayList<>();
                for (JsonElement c : e.getValue().getAsJsonArray()) {
                    JsonArray a = c.getAsJsonArray();
                    cues.add(new Cue(a.get(0).getAsInt(), a.get(1).getAsString()));
                }
                s.put(e.getKey(), List.copyOf(cues));
            }
            Map<String, float[]> gp = new HashMap<>();
            if (root.has("gunpath")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("gunpath").entrySet()) {
                    gp.put(e.getKey(), floats(e.getValue().getAsJsonArray()));
                }
            }
            gunPath = gp;
            timing = t;
            deathFall = d;
            camera = cam;
            guns = g;
            sounds = s;
            LOGGER.info("Loaded roulette timeline: pull full={} short={}", timing("full", "pull", -1),
                    timing("short", "pull", -1));
        } catch (Exception e) {
            LOGGER.error("Could not read {}", FILE, e);
        }
    }

    private static float[] floats(JsonArray array) {
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsFloat();
        }
        return out;
    }

    private static Curve curve(JsonObject o, String key) {
        if (!o.has(key)) {
            return Curve.ZERO;
        }
        JsonArray a = o.getAsJsonArray(key);
        float[] ticks = new float[a.size()];
        float[] values = new float[a.size()];
        for (int i = 0; i < a.size(); i++) {
            ticks[i] = a.get(i).getAsJsonArray().get(0).getAsFloat();
            values[i] = a.get(i).getAsJsonArray().get(1).getAsFloat();
        }
        return new Curve(ticks, values);
    }
}
