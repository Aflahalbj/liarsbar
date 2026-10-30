package com.liarsbar.table;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server menunggu {@link TableTiming} sebelum menarik pelatuk dan sebelum ronde baru; client memutar
 * animasi dan kamera dari roulette.json (tools/table_anims.py). Keduanya harus cocok, kalau tidak
 * hasil tembakan datang sebelum pistolnya sampai di pelipis.
 */
class RouletteTimelineTest {

    private static JsonObject timeline() throws Exception {
        try (InputStream in = RouletteTimelineTest.class.getResourceAsStream("/assets/liarsbar/cinematic/roulette.json")) {
            assertNotNull(in, "roulette.json missing: run tools/table_anims.py");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void pullTicksMatchTheServer() throws Exception {
        JsonObject timing = timeline().getAsJsonObject("timing");
        assertEquals(TableTiming.PULL_FULL, timing.getAsJsonObject("full").get("pull").getAsInt());
        assertEquals(TableTiming.PULL_SHORT, timing.getAsJsonObject("short").get("pull").getAsInt());
        assertEquals(TableTiming.PULL_LOAD, timing.getAsJsonObject("load").get("pull").getAsInt());
        assertEquals(TableTiming.AFTER_CLICK, timing.getAsJsonObject("click").get("length").getAsInt());
        assertEquals(TableTiming.AFTER_BANG, timing.getAsJsonObject("bang").get("length").getAsInt());
    }

    @Test
    void cardTicksMatchTheServer() throws Exception {
        JsonObject timing = timeline().getAsJsonObject("timing");
        JsonObject toss = timing.getAsJsonObject("throw");
        assertEquals(TableTiming.THROW, toss.get("length").getAsInt());
        assertTrue(toss.get("release").getAsInt() + 12 <= TableTiming.THROW, "thrown cards land before the next turn");
        assertEquals(TableTiming.PICKUP, timing.getAsJsonObject("pickup").get("length").getAsInt());
    }

    @Test
    void dealFitsEveryTableSize() {
        for (boolean full : new boolean[]{true, false}) {
            for (int alive = 2; alive <= 10; alive++) {
                int deal = TableTiming.deal(full, alive);
                assertTrue(TableTiming.pickupStart(full, alive) > TableTiming.dealStart(full));
                // Kartu dibagi satu per satu: versi pendek (tiap ronde) paling lama 14 detik, penuh (ronde pertama) 16.
                assertTrue(deal <= (full ? 320 : 280), "deal full=" + full + " alive=" + alive + " = " + deal);
            }
        }
    }

    @Test
    void gunReachesTheTempleBeforeTheTrigger() throws Exception {
        JsonObject timing = timeline().getAsJsonObject("timing");
        for (String kind : new String[]{"full", "short", "load"}) {
            JsonObject t = timing.getAsJsonObject(kind);
            int temple = t.get("temple").getAsInt();
            int cock = t.get("cock").getAsInt();
            int pull = t.get("pull").getAsInt();
            assertTrue(t.get("grab").getAsInt() < temple, kind + ": grab before temple");
            assertTrue(temple <= cock && cock < pull, kind + ": cocked at the temple before the pull");
            // Tahan pistol di kepala paling sedikit 1,5 detik (game asli: 5-8 detik di versi penuh).
            assertTrue(pull - cock >= 30, kind + ": hold too short");
        }
    }

    @Test
    void bulletIsSeatedBeforeTheSpin() throws Exception {
        JsonObject t = timeline().getAsJsonObject("timing").getAsJsonObject("load");
        int pick = t.get("pick").getAsInt(), grab = t.get("grab").getAsInt(), open = t.get("open").getAsInt();
        int insert = t.get("insert").getAsInt(), seat = t.get("seat").getAsInt(), flick = t.get("flick").getAsInt();
        assertTrue(pick < grab && grab < open && open + 5 <= insert, "bullet picked, gun taken, crane open before insert");
        assertTrue(insert < seat && seat + 6 <= flick && flick < t.get("close").getAsInt(), "seated before the spin");
    }

    @Test
    void deathFallsBeforeTheNextRound() throws Exception {
        JsonObject death = timeline().getAsJsonObject("death");
        for (String name : new String[]{"death_side", "death_table"}) {
            int fall = death.getAsJsonObject(name).get("fall").getAsInt();
            assertTrue(fall > 0 && fall + 30 < TableTiming.AFTER_BANG, name + " fall=" + fall);
        }
    }
}
