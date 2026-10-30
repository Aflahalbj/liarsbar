package com.liarsbar.client.test;

import com.liarsbar.client.ClientTable;
import com.liarsbar.client.anim.SeatAnimator;
import com.liarsbar.client.cinematic.DevCamera;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.liarsbar.client.render.RevolverRenderer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Pratinjau dan tuning khusus development ({@code gradlew runClient -Panimtest=true}).
 * Game tetap terbuka; skrip dibaca dari {@code run/animtest/command.txt} setiap
 * kali file itu berubah, satu perintah per baris:
 * <pre>
 *   reload                       muat ulang resource (animasi, timeline roulette)
 *   force SEAT ANIM TICK         tahan kursi di animasi ANIM tick TICK;  unforce = semua kembali normal
 *   cam SEAT DIST AZ EL [FOV] [AIM]  kamera uji di sekitar mata penembak SEAT (AZ 0 = di depannya,
 *                                90 = di kanannya; EL derajat ke atas; AIM tinggi titik tatap relatif mata)
 *   camoff                       kamera kembali normal
 *   showgun SEAT [off]           paksa kursi memegang revolver (untuk menyetel "gun hand")
 *   gun hand|table|view v0..v6   pose dasar revolver (rotX rotY rotZ x y z skala)
 *   freeze SEAT TICK|off         tahan roulette kursi di tick tertentu (kamera/pistol/efek)
 *   pov x y z rx ry rz           pose pistol di pelipis dari pandangan penembak
 *   hud on|off                   sembunyikan/tampilkan GUI
 *   shot NAME                    screenshot run/screenshots/anim_NAME.png
 *   strip NAME COUNT EVERY       COUNT screenshot tiap EVERY tick (NAME_000 ...)
 *   cmd PERINTAH                 jalankan perintah (tanpa "/")
 *   wait N                       tunggu N tick
 *   log                          catat keadaan meja dan roulette
 *   quit
 * </pre>
 * Selesai menjalankan skrip, {@code run/animtest/done.txt} ditulis.
 */
public final class AnimDev {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static long lastModified;
    private static int wait;
    private static int poll;
    private static String stripName;
    private static int stripLeft;
    private static int stripEvery;
    private static int stripIndex;
    private static int stripWait;
    /** "waitturn SEAT": tunggu sampai giliran kursi SEAT (paling lama 2400 tick). */
    private static int waitTurnSeat = -1;
    /** "waitstage STAGE": tunggu sampai tahap meja STAGE (mis. CALLED), paling lama waitTurnLeft tick. */
    private static String waitStage;
    private static int waitTurnLeft;

    private AnimDev() {
    }

    public static void init() {
        if (!Boolean.getBoolean("liarsbar.animtest")) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(AnimDev::onTick);
    }

    private static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("animtest");
    }

    private static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.pauseOnLostFocus && mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            mc.setScreen(null);
        }
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (++poll % 10 == 0) {
            Path file = dir().resolve("command.txt");
            try {
                if (Files.exists(file)) {
                    long modified = Files.getLastModifiedTime(file).toMillis();
                    if (modified != lastModified) {
                        lastModified = modified;
                        String text = Files.readString(file, StandardCharsets.UTF_8);
                        if (!text.isEmpty() && text.charAt(0) == '﻿') {
                            text = text.substring(1);
                        }
                        Files.deleteIfExists(dir().resolve("done.txt"));
                        for (String line : text.split("\\R")) {
                            if (!line.isBlank() && !line.trim().startsWith("#")) {
                                QUEUE.add(line.trim());
                            }
                        }
                        LOGGER.info("[animdev] script with {} commands", QUEUE.size());
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("[animdev] cannot read {}", file, e);
            }
        }
        if (stripLeft > 0) {
            if (--stripWait <= 0) {
                screenshot(String.format("%s_%03d", stripName, stripIndex++));
                stripLeft--;
                stripWait = stripEvery;
                if (stripLeft == 0 && QUEUE.isEmpty()) {
                    finished();
                }
            }
            return;
        }
        if (waitTurnSeat >= 0 || waitStage != null) {
            var st = ClientTable.state();
            boolean ok = waitStage != null ? st != null && st.stage.name().equals(waitStage)
                    : st != null && st.stage == com.liarsbar.table.PublicState.Stage.TURN && st.current == waitTurnSeat;
            if (ok || --waitTurnLeft <= 0) {
                LOGGER.info("[animdev] waitturn {} {}", waitTurnSeat, ok ? "reached" : "timed out");
                waitTurnSeat = -1;
                waitStage = null;
                if (QUEUE.isEmpty()) {
                    finished();
                }
            }
            return;
        }
        if (wait > 0) {
            if (--wait == 0 && QUEUE.isEmpty()) {
                finished();
            }
            return;
        }
        while (!QUEUE.isEmpty() && wait == 0 && stripLeft == 0 && waitTurnSeat < 0 && waitStage == null) {
            String line = QUEUE.poll();
            try {
                run(line);
            } catch (RuntimeException e) {
                LOGGER.error("[animdev] '{}' failed", line, e);
            }
            if (QUEUE.isEmpty() && wait == 0 && stripLeft == 0 && waitTurnSeat < 0 && waitStage == null) {
                finished();
            }
        }
    }

    /** Nomor kursi, "me" = kursi sendiri, atau "d" = dealer pembagian kartu yang sedang berjalan. */
    private static int seatArg(String arg) {
        if (arg.equals("me")) {
            return com.liarsbar.client.ClientTable.mySeat();
        }
        return arg.equals("d") ? com.liarsbar.client.cinematic.DealDirector.dealer() : Integer.parseInt(arg);
    }

    private static void finished() {
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("done.txt"), Long.toString(System.currentTimeMillis()));
        } catch (IOException e) {
            LOGGER.warn("[animdev] cannot write done.txt", e);
        }
    }

    private static void run(String line) {
        Minecraft mc = Minecraft.getInstance();
        String[] a = line.split("\\s+");
        LOGGER.info("[animdev] {}", line);
        switch (a[0]) {
            case "reload" -> {
                mc.reloadResourcePacks();
                wait = 40;
            }
            case "force" -> SeatAnimator.force(Integer.parseInt(a[1]), a[2], Integer.parseInt(a[3]));
            case "unforce" -> SeatAnimator.clearForced();
            case "cam" -> {
                int seat = seatArg(a[1]);
                double dist = Double.parseDouble(a[2]);
                double az = Math.toRadians(Double.parseDouble(a[3]));
                double el = Math.toRadians(Double.parseDouble(a[4]));
                float fov = a.length > 5 ? Float.parseFloat(a[5]) : 50;
                double aim = a.length > 6 ? Double.parseDouble(a[6]) : -0.35;
                RouletteDirector.Frame f = RouletteDirector.frame(seat);
                if (f == null) {
                    LOGGER.warn("[animdev] no frame for seat {}", seat);
                    return;
                }
                Vec3 target = f.eye().add(0, aim, 0);
                Vec3 dir = f.forward().scale(Math.cos(az)).add(f.right().scale(Math.sin(az)));
                Vec3 pos = target.add(dir.scale(dist * Math.cos(el))).add(0, dist * Math.sin(el), 0);
                DevCamera.set(new DevCamera.Override(pos, target, fov));
            }
            case "camat" -> {
                // camat x y z tx ty tz [fov]: kamera bebas, relatif pusat meja (y = lantai).
                var table = RouletteDirector.tableEntity();
                if (table == null) {
                    return;
                }
                Vec3 c = table.position();
                Vec3 pos = c.add(Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]));
                Vec3 target = c.add(Double.parseDouble(a[4]), Double.parseDouble(a[5]), Double.parseDouble(a[6]));
                DevCamera.set(new DevCamera.Override(pos, target, a.length > 7 ? Float.parseFloat(a[7]) : 50));
            }
            case "camf" -> {
                // camf SEAT r u f ar au af [fov]: kamera di kerangka penembak (sama dengan kunci kamera roulette.json).
                RouletteDirector.Frame f = RouletteDirector.frame(seatArg(a[1]));
                if (f == null) {
                    return;
                }
                float[] v = new float[6];
                for (int i = 0; i < 6; i++) {
                    v[i] = Float.parseFloat(a[i + 2]);
                }
                DevCamera.set(new DevCamera.Override(f.at(new float[]{v[0], v[1], v[2]}),
                        f.at(new float[]{v[3], v[4], v[5]}), a.length > 8 ? Float.parseFloat(a[8]) : 40));
            }
            // fakeload SEAT: putar roulette versi isi untuk kursi SEAT hanya di client (uji POV saat duduk di game).
            case "fakeload" -> {
                var st = com.liarsbar.client.ClientTable.state();
                if (st != null) {
                    RouletteDirector.onEvent(new com.liarsbar.network.TableEventS2C(st.tableId,
                            new com.liarsbar.game.deck.GameEvent.RouletteStarted(java.util.List.of(seatArg(a[1]))),
                            com.liarsbar.network.TableEventS2C.FULL | com.liarsbar.network.TableEventS2C.LOAD));
                }
            }
            case "camoff" -> DevCamera.set(null);
            case "showgun" -> {
                RouletteDirector.showGun(Integer.parseInt(a[1]), a.length < 3 || !a[2].equals("off"));
                if (a.length >= 3 && !a[2].equals("off")) {
                    RouletteDirector.showGunAs(a[2]);
                }
            }
            case "gun" -> {
                float[] target = switch (a[1]) {
                    case "hand" -> RevolverRenderer.HAND;
                    case "table" -> RevolverRenderer.TABLE;
                    default -> RevolverRenderer.VIEW;
                };
                for (int i = 0; i < 7 && i + 2 < a.length; i++) {
                    target[i] = Float.parseFloat(a[i + 2]);
                }
            }
            case "toggle" -> LOGGER.info("[animdev] {} -> {}", a[1], com.liarsbar.client.test.DevToggles.toggle(a[1]));
            case "freeze" -> RouletteDirector.freeze(Integer.parseInt(a[1]),
                    a[2].equals("off") ? null : Float.parseFloat(a[2]));
            case "roomcfg" -> {
                float[] v = new float[4];
                for (int i = 0; i < 4; i++) {
                    v[i] = Float.parseFloat(a[i + 1]);
                }
                com.liarsbar.client.render.BarRoom.tune(v);
            }
            case "hazecfg" -> {
                float[] v = new float[3];
                for (int i = 0; i < 3; i++) {
                    v[i] = Float.parseFloat(a[i + 1]);
                }
                com.liarsbar.client.fx.Atmosphere.tune(v);
            }
            case "postcfg" -> {
                float[] v = new float[a.length - 1];
                for (int i = 0; i < v.length; i++) {
                    v[i] = Float.parseFloat(a[i + 1]);
                }
                com.liarsbar.client.fx.PostFx.tune(v);
            }
            case "lightcfg" -> {
                float[] v = new float[a.length - 1];
                for (int i = 0; i < v.length; i++) {
                    v[i] = Float.parseFloat(a[i + 1]);
                }
                com.liarsbar.client.fx.TableLights.tune(v);
            }
            case "pov" -> {
                float[] v = new float[6];
                for (int i = 0; i < 6; i++) {
                    v[i] = Float.parseFloat(a[i + 1]);
                }
                com.liarsbar.client.render.FirstPersonGun.tuneTemple(v);
            }
            case "hud" -> mc.options.hideGui = a[1].equals("off");
            case "shot" -> screenshot(a[1]);
            case "strip" -> {
                stripName = a[1];
                stripLeft = Integer.parseInt(a[2]);
                stripEvery = Integer.parseInt(a[3]);
                stripIndex = 0;
                stripWait = 1;
            }
            case "cmd" -> mc.player.connection.sendCommand(line.substring(4).trim());
            case "wait" -> wait = Integer.parseInt(a[1]);
            case "waitstage" -> {
                waitStage = a[1];
                waitTurnLeft = 6000;
            }
            case "waitturn" -> {
                waitTurnSeat = Integer.parseInt(a[1]);
                waitTurnLeft = 2400;
            }
            case "probe" -> {
                int seat = Integer.parseInt(a[1]);
                RouletteDirector.Frame f = RouletteDirector.frame(seat);
                Vec3 m = com.liarsbar.client.fx.MuzzleFx.muzzle(seat);
                if (f != null && m != null) {
                    Vec3 d = m.subtract(f.eye());
                    LOGGER.info("[animdev] probe seat {} muzzle={} eye={} rel right={} up={} fwd={}", seat, m, f.eye(),
                            String.format("%.2f", d.dot(f.right())), String.format("%.2f", d.dot(f.up())),
                            String.format("%.2f", d.dot(f.forward())));
                } else {
                    LOGGER.info("[animdev] probe seat {} frame={} muzzle={}", seat, f, m);
                }
                RouletteDirector.Grip g = RouletteDirector.grip(seat, 0);
                LOGGER.info("[animdev] probe seat {} grip w={} short={}", seat, g == null ? 0 : g.rightWeight(),
                        String.format("%.3f", com.liarsbar.client.anim.SeatAnimator.gripShort(seat)));
            }
            // deal full|short: putar ulang pembagian kartu di client. throw SEAT N: kursi SEAT melempar N kartu.
            case "deal" -> com.liarsbar.client.cinematic.DealDirector.debugDeal(a.length < 2 || a[1].equals("full"));
            case "throw" -> com.liarsbar.client.cinematic.ThrowDirector.debugThrow(Integer.parseInt(a[1]),
                    a.length > 2 ? Integer.parseInt(a[2]) : 2);
            // povthrow N: lempar N kartu pertama dari tangan sendiri di orang pertama (hanya di client).
            case "povthrow" -> {
                java.util.Set<Integer> ids = new java.util.LinkedHashSet<>();
                for (var card : com.liarsbar.client.ClientTable.hand()) {
                    if (ids.size() < Integer.parseInt(a[1])) {
                        ids.add(card.id());
                    }
                }
                com.liarsbar.client.render.FirstPersonCards.beginThrow(ids);
                com.liarsbar.client.cinematic.ThrowDirector.localThrow();
                com.liarsbar.client.cinematic.ThrowDirector.debugThrow(com.liarsbar.client.ClientTable.mySeat(), ids.size());
            }
            // cardprobe SEAT: jarak kepalan yang dirender ke sasaran IK kartu (0 = tangan sampai).
            case "cardprobe" -> {
                int seat = seatArg(a[1]);
                RouletteDirector.Grip g = RouletteDirector.grip(seat, 0);
                Vec3 l = com.liarsbar.client.render.HeldCards.leftNow(seat);
                Vec3 r = com.liarsbar.client.render.HeldCards.rightNow(seat);
                LOGGER.info("[animdev] cardprobe seat {} act={} right w={} miss={} left w={} miss={}", seat,
                        RouletteDirector.act(seat, 0).animation(),
                        g == null ? 0 : String.format("%.2f", g.rightWeight()),
                        g == null || g.right() == null || r == null ? "-" : String.format("%.3f", r.distanceTo(g.right())),
                        g == null ? 0 : String.format("%.2f", g.leftWeight()),
                        g == null || g.left() == null || l == null ? "-" : String.format("%.3f", l.distanceTo(g.left())));
                RouletteDirector.HeldBullet hb = RouletteDirector.heldBullet(seat, l, 0);
                LOGGER.info("[animdev] cardprobe seat {} bullet={} fist={}", seat,
                        hb == null ? "-" : hb.center() + " axis=" + hb.axis() + " k=" + hb.chamber(), l);
            }
            case "perf" -> LOGGER.info("[animdev] perf fps={}", mc.getFps());
            case "npcprobe" -> com.liarsbar.client.anim.NpcPuppets.probe(LOGGER);
            // npcanim NAME TICK | npcanim off: semua NPC bar beku di animasi NAME tick TICK.
            case "npcanim" -> com.liarsbar.client.anim.NpcPuppets.force(a[1].equals("off") ? null : a[1],
                    a.length > 2 ? Integer.parseInt(a[2]) : 0);
            // npccam SKIN DIST AZ EL [FOV] [AIM] | npccam off: kamera mengikuti NPC berkulit SKIN.
            case "npccam" -> {
                if (a[1].equals("off")) {
                    com.liarsbar.client.anim.NpcPuppets.follow(-1, null);
                    DevCamera.set(null);
                } else {
                    com.liarsbar.client.anim.NpcPuppets.follow(Integer.parseInt(a[1]), new double[]{
                            Double.parseDouble(a[2]), Double.parseDouble(a[3]), Double.parseDouble(a[4]),
                            a.length > 5 ? Double.parseDouble(a[5]) : 40, a.length > 6 ? Double.parseDouble(a[6]) : -0.2});
                }
            }
            case "log" -> LOGGER.info("[animdev] state={} me={} fps={} roulette={}",
                    ClientTable.state() == null ? null : ClientTable.state().stage, ClientTable.mySeat(), mc.getFps(),
                    RouletteDirector.describe());
            case "camlog" -> {
                var cam = mc.gameRenderer.getMainCamera();
                LOGGER.info("[animdev] camlog player={} vehicle={} seated={} cam={} detached={} type={} cine={}",
                        mc.player.position(), mc.player.getVehicle(), ClientTable.seated(), cam.getPosition(),
                        cam.isDetached(), mc.options.getCameraType(),
                        com.liarsbar.client.cinematic.CameraDirector.cinematicWeight());
            }
            case "quit" -> mc.stop();
            default -> LOGGER.warn("[animdev] unknown command {}", a[0]);
        }
    }

    private static void screenshot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "anim_" + name + ".png", mc.getMainRenderTarget(),
                message -> LOGGER.info("[animdev] screenshot {}", name));
    }
}
