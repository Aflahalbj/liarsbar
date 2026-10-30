package com.liarsbar.client.test;

import com.liarsbar.client.ClientTable;
import com.liarsbar.table.PublicState;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Uji client otomatis khusus development ({@code gradlew runClient -Pclienttest=true}):
 * masuk world uji, membuat meja, mengisi 10 kursi dengan bot, memainkan satu
 * game penuh (kursi sendiri dimainkan bot), mengambil screenshot di tiap
 * momen penting ({@code run/screenshots/clienttest_*.png}), lalu menutup game.
 */
public final class ClientSelfTest {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Step> STEPS = new ArrayList<>();
    private static int index;
    private static int wait;

    /** Jalankan {@code action} setelah {@code delayTicks} dan {@code ready} terpenuhi (paling lama {@code timeoutTicks}). */
    private record Step(int delayTicks, BooleanSupplier ready, int timeoutTicks, String name, Runnable action) {
    }

    private ClientSelfTest() {
    }

    public static void init() {
        String scenario = System.getProperty("liarsbar.clienttest");
        if (scenario == null) {
            return;
        }
        if ("multi".equals(scenario)) {
            multi();
        } else {
            fullTable();
        }
        MinecraftForge.EVENT_BUS.addListener(ClientSelfTest::onTick);
    }

    private static final String[] FAKES = {"anna", "budi", "citra"};
    /** Fake player yang dicabut di tengah game (dipilih saat itu: yang masih hidup). */
    private static String dropped;

    /**
     * Multipemain ({@code -Pclienttest=multi}, butuh mod fake player dev): 1 pemain + 3 fake player
     * + 2 bot. anna dan budi dimainkan bot (autoplay), citra dibiarkan diam supaya timer gilirannya
     * habis. Di tengah game satu fake yang masih hidup terputus: kursinya ditunggu, lalu dimainkan
     * bot, lalu dia login lagi dan kembali ke kursinya sendiri.
     */
    private static void multi() {
        step(1, () -> Minecraft.getInstance().options.pauseOnLostFocus = false);
        until("world ready", () -> Minecraft.getInstance().player.tickCount > 60, 2400);
        command(5, "time set noon");
        command(5, "liarsbar create true");
        until("table visible", () -> ClientTable.state() != null, 200);
        command(5, "liarsbar join");
        until("seated", ClientTable::seated, 200);
        for (String fake : FAKES) {
            command(5, "fakeplayer spawn " + fake);
        }
        until("fakes online", () -> Minecraft.getInstance().getConnection().getOnlinePlayers().size() >= 4, 400);
        for (String fake : FAKES) {
            command(5, "execute as " + fake + " run liarsbar join");
        }
        command(5, "liarsbar bot add 2");
        until("6 seats", () -> state() != null && state().seats.size() == 6, 200);
        command(5, "execute as anna run liarsbar dev autoplay");
        command(5, "execute as budi run liarsbar dev autoplay");
        command(5, "liarsbar dev autoplay");
        command(5, "liarsbar dev timescale 0.3");
        camera(CameraType.THIRD_PERSON_BACK);
        shot(20, "multi_lobby");
        step(1, ClientSelfTest::diagnose);
        command(5, "liarsbar start");
        until("round 2", () -> state() != null && state().round >= 2, 6000);
        step(5, () -> {
            dropped = firstAliveFake();
            LOGGER.info("[clienttest] dropping {}", dropped);
            Minecraft.getInstance().player.connection.sendCommand("fakeplayer remove " + dropped);
        });
        until("dropped is away", () -> seatOf(dropped) != null && seatOf(dropped).away, 400);
        step(1, () -> awayAt = ClientTable.now());
        shot(5, "multi_away");
        // Masa tunggu 30 dtk x 0,3 = 180 tick. Sesudahnya kartu di kursinya harus berkurang (dimainkan bot),
        // atau kursinya mati: berarti giliran kursi itu memang berjalan tanpa pemiliknya.
        step(1, () -> actionsWhenAway = ClientTable.actionsOf(seatIndex(dropped)));
        until("bot played the dropped seat", () -> {
            PublicState.SeatInfo seat = seatOf(dropped);
            // Selama masa tunggu, timer gilirannya tetap berjalan (timeout = 1 kartu acak). Yang dicek di sini
            // aksi sesudah masa tunggu: itu dimainkan bot pengganti.
            if (seat == null || !seat.away) {
                return false;
            }
            if (ClientTable.now() - awayAt <= 185) {
                actionsWhenAway = ClientTable.actionsOf(seatIndex(dropped));
                return false;
            }
            return ClientTable.actionsOf(seatIndex(dropped)) > actionsWhenAway;
        }, 6000);
        step(1, () -> LOGGER.info("[clienttest] {} seat acted while away: {} -> {} actions, {} ticks after leaving",
                dropped, actionsWhenAway, ClientTable.actionsOf(seatIndex(dropped)), ClientTable.now() - awayAt));
        shot(1, "multi_bot_plays");
        step(1, ClientSelfTest::diagnose);
        step(5, () -> Minecraft.getInstance().player.connection.sendCommand("fakeplayer spawn " + dropped));
        until("dropped is back", () -> seatOf(dropped) != null && !seatOf(dropped).away, 600);
        step(40, () -> {
            var level = Minecraft.getInstance().level;
            boolean client = level.players().stream().anyMatch(p -> p.getGameProfile().getName().equals(dropped)
                    && p.getVehicle() instanceof com.liarsbar.entity.SeatEntity);
            String name = dropped;
            var server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayerByName(name);
                boolean seated = player != null && player.getVehicle() instanceof com.liarsbar.entity.SeatEntity;
                LOGGER.info("[clienttest] {} back on a seat: server={} client={} pos={}", name, seated, client,
                        player == null ? "-" : player.position());
            });
        });
        shot(1, "multi_back");
        command(5, "liarsbar dev timescale 0.15");
        until("finished", () -> stage() == PublicState.Stage.FINISHED, 30000);
        shot(10, "multi_winner");
        step(1, ClientSelfTest::diagnose);
        until("lobby again", () -> stage() == PublicState.Stage.LOBBY, 2000);
        step(20, () -> {
            LOGGER.info("[clienttest] done");
            Minecraft.getInstance().stop();
        });
    }

    private static long awayAt;
    private static int actionsWhenAway;

    private static int seatIndex(String name) {
        PublicState.SeatInfo seat = seatOf(name);
        return seat == null ? -2 : state().seats.indexOf(seat);
    }

    /** Yang dicabut: utamakan citra (tanpa autoplay), supaya yang memainkan kursinya pasti bot pengganti. */
    private static String firstAliveFake() {
        for (String fake : new String[]{"citra", "anna", "budi"}) {
            PublicState.SeatInfo seat = seatOf(fake);
            if (seat != null && seat.alive) {
                return fake;
            }
        }
        return FAKES[0];
    }

    private static PublicState.SeatInfo seatOf(String name) {
        if (state() == null || name == null) {
            return null;
        }
        for (PublicState.SeatInfo seat : state().seats) {
            if (seat.name.equals(name)) {
                return seat;
            }
        }
        return null;
    }

    /** Meja 10 kursi (1 pemain + 9 bot), mode cepat, dari lobby sampai pemenang. */
    private static void fullTable() {
        step(1, () -> Minecraft.getInstance().options.pauseOnLostFocus = false);
        until("world ready", () -> Minecraft.getInstance().player.tickCount > 60, 2400);
        command(5, "time set noon");
        command(5, "weather clear");
        command(5, "liarsbar create");
        until("table visible", () -> ClientTable.state() != null, 200);
        command(5, "liarsbar join");
        until("seated", ClientTable::seated, 200);
        command(5, "liarsbar bot fill");
        until("10 seats", () -> state() != null && state().seats.size() == 10, 200);
        step(30, () -> Minecraft.getInstance().player.setXRot(28));
        shot(20, "lobby_first_person");
        camera(CameraType.THIRD_PERSON_BACK);
        shot(10, "lobby_third_person");
        camera(CameraType.FIRST_PERSON);
        command(5, "liarsbar dev autoplay");
        command(5, "liarsbar start");
        until("dealing", () -> stage() == PublicState.Stage.DEALING, 200);
        shot(10, "deal");
        until("first turn", () -> stage() == PublicState.Stage.TURN, 400);
        shot(5, "turn");
        until("my turn", ClientTable::myTurn, 4000);
        shot(5, "my_turn_hand");
        until("reveal", () -> stage() == PublicState.Stage.REVEAL, 4000);
        shot(8, "reveal");
        until("roulette", () -> stage() == PublicState.Stage.ROULETTE, 400);
        shot(40, "roulette_spin");
        step(1, ClientSelfTest::diagnose);
        shot(90, "roulette_hold");
        step(1, ClientSelfTest::diagnose);
        until("shot", () -> stage() == PublicState.Stage.SHOT, 400);
        shot(1, "trigger");
        step(1, ClientSelfTest::diagnose);
        // Sisa game dipercepat supaya uji selesai dalam beberapa menit.
        command(5, "liarsbar dev timescale 0.15");
        until("someone died", () -> state() != null && state().seats.stream().anyMatch(s -> !s.alive), 12000);
        shot(10, "after_death");
        camera(CameraType.THIRD_PERSON_BACK);
        shot(10, "after_death_third_person");
        camera(CameraType.FIRST_PERSON);
        until("five dead", () -> state() != null && state().seats.stream().filter(s -> !s.alive).count() >= 5, 20000);
        shot(5, "half_dead");
        step(1, ClientSelfTest::diagnose);
        until("finished", () -> stage() == PublicState.Stage.FINISHED, 30000);
        shot(10, "winner");
        step(1, ClientSelfTest::diagnose);
        until("lobby again", () -> stage() == PublicState.Stage.LOBBY, 2000);
        shot(10, "lobby_after");
        step(20, () -> {
            LOGGER.info("[clienttest] done");
            Minecraft.getInstance().stop();
        });
    }

    private static PublicState state() {
        return ClientTable.state();
    }

    private static PublicState.Stage stage() {
        return ClientTable.state() == null ? null : ClientTable.state().stage;
    }

    private static void diagnose() {
        Minecraft mc = Minecraft.getInstance();
        PublicState s = state();
        if (s == null) {
            LOGGER.info("[clienttest] no table, fps={}", mc.getFps());
            return;
        }
        StringBuilder seats = new StringBuilder();
        for (PublicState.SeatInfo seat : s.seats) {
            seats.append(seat.name).append(seat.alive ? "" : "+").append('(').append(seat.pulls).append(") ");
        }
        StringBuilder riders = new StringBuilder();
        for (var p : mc.level.players()) {
            riders.append(p.getGameProfile().getName()).append('=')
                    .append(p.getVehicle() instanceof com.liarsbar.entity.SeatEntity ? "seat" : "-").append(' ');
        }
        var fx = com.liarsbar.client.fx.PostFx.current();
        int lights = foundry.veil.api.client.render.VeilRenderSystem.renderer().getDeferredRenderer().getLightRenderer()
                .getLights(foundry.veil.api.client.registry.LightTypeRegistry.POINT.get()).size();
        LOGGER.info("[clienttest] fps={} stage={} round={} decks={} me={} hand={} seats={} riders(client)={} lights={} "
                        + "fx(vig={} lowpass={} blur={} flash={}) roulette={}",
                mc.getFps(), s.stage, s.round, s.decks, ClientTable.mySeat(), ClientTable.hand().size(), seats, riders,
                lights, fx.vignette(), fx.lowpass(), fx.blur(), fx.flash(),
                com.liarsbar.client.cinematic.RouletteDirector.describe());
    }

    private static void camera(CameraType type) {
        step(2, () -> Minecraft.getInstance().options.setCameraType(type));
    }

    private static void step(int delay, Runnable action) {
        STEPS.add(new Step(delay, () -> true, 0, "", action));
    }

    private static void until(String name, BooleanSupplier ready, int timeoutTicks) {
        STEPS.add(new Step(1, ready, timeoutTicks, name, () -> LOGGER.info("[clienttest] {}", name)));
    }

    private static void command(int delay, String command) {
        step(delay, () -> {
            LOGGER.info("[clienttest] /{}", command);
            Minecraft.getInstance().player.connection.sendCommand(command);
        });
    }

    private static void shot(int delay, String name) {
        step(delay, () -> {
            Minecraft client = Minecraft.getInstance();
            Screenshot.grab(client.gameDirectory, "clienttest_" + name + ".png", client.getMainRenderTarget(),
                    message -> LOGGER.info("[clienttest] screenshot {}: {}", name, message.getString()));
        });
    }

    private static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (!client.options.pauseOnLostFocus && client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            client.setScreen(null);
        }
        if (client.player == null || client.level == null || index >= STEPS.size()) {
            return;
        }
        Step step = STEPS.get(index);
        if (++wait < step.delayTicks()) {
            return;
        }
        boolean ready;
        try {
            ready = step.ready().getAsBoolean();
        } catch (RuntimeException e) {
            ready = false;
        }
        if (!ready) {
            if (wait < step.delayTicks() + step.timeoutTicks()) {
                return;
            }
            LOGGER.warn("[clienttest] waiting for \"{}\" timed out, continuing", step.name());
        }
        wait = 0;
        index++;
        try {
            step.action().run();
        } catch (RuntimeException e) {
            LOGGER.error("[clienttest] step {} failed", index, e);
        }
    }
}
