package com.liarsbar.client.ui;

import com.liarsbar.LiarsBar;
import com.liarsbar.client.ClientTable;
import com.liarsbar.client.TableKeys;
import com.liarsbar.client.cinematic.CameraDirector;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.liarsbar.game.deck.Rank;
import com.liarsbar.table.PublicState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.Locale;

/**
 * HUD layar meja (Art Bible). Info sebanyak mungkin ada di dunia (kartu 3D, lampu giliran, jam pasir, kapur);
 * layar hanya memegang: subtitle klaim yang menempel di pembicara, judul ronde singkat, daftar pemain selama
 * Tab ditahan, petunjuk tombol di ronde pertama, dan satu baris kecil untuk lobby/penonton. Tanpa panel
 * permanen, tanpa font piksel vanilla. Sinematik (letterbox) = HUD hilang.
 */
public final class TableHud {

    public static final ResourceLocation DISPLAY = font("display");
    public static final ResourceLocation DISPLAY_BIG = font("display_big");
    public static final ResourceLocation LABEL = font("label");
    public static final ResourceLocation SERIF = font("serif");
    public static final ResourceLocation SERIF_BIG = font("serif_big");
    public static final ResourceLocation MONO = font("mono");

    private static final int PAPER = 0xEDE3C9;
    private static final int BRASS = 0xC9A04A;
    private static final int DIM = 0x9A9284;
    private static final int RED = 0xD6202A;
    private static final int INK = 0x0A0A0C;

    /** Lama subtitle klaim dan judul ronde (tick). */
    private static final int CLAIM_TICKS = 70;
    private static final int TITLE_TICKS = 56;

    private TableHud() {
    }

    private static ResourceLocation font(String name) {
        return new ResourceLocation(LiarsBar.MOD_ID, name);
    }

    public static ResourceLocation cardTexture(Rank rank) {
        return new ResourceLocation(LiarsBar.MOD_ID, "textures/card/" + (rank == null ? "back" : rank.name().toLowerCase()) + ".png");
    }

    private static MutableComponent text(Component c, ResourceLocation font) {
        return c.copy().withStyle(s -> s.withFont(font));
    }

    private static MutableComponent text(String s, ResourceLocation font) {
        return Component.literal(s).withStyle(st -> st.withFont(font));
    }

    private static int argb(int rgb, float alpha) {
        return (Mth.clamp(Math.round(alpha * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        PublicState state = ClientTable.state();
        Minecraft mc = Minecraft.getInstance();
        if (state == null || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        if (CameraDirector.cinematicWeight() > 0.05f) {
            liarStamp(g, font, state, width, height, partialTick);
            banner(g, font, width, height);
            g.pose().popPose();
            return;
        }
        if (state.stage == PublicState.Stage.LOBBY) {
            footLine(g, font, width, height, Component.translatable(ClientTable.mySeat() >= 0 ? "liarsbar.hud2.lobby"
                    : "liarsbar.hud2.lobby.join", state.tableId, state.seats.size()));
        } else {
            roundTitle(g, font, width, partialTick);
            claim(g, font, state, width, height, partialTick);
            hints(g, font, state, width, height);
            buttons(g, font, state, width, height);
            if (ClientTable.mySeat() < 0) {
                footLine(g, font, width, height, Component.translatable("liarsbar.hud2.spectator"));
            } else if (!state.seats.get(ClientTable.mySeat()).alive) {
                footLine(g, font, width, height, Component.translatable("liarsbar.hud2.dead"));
            }
        }
        if (mc.options.keyPlayerList.isDown() || !com.liarsbar.client.test.DevToggles.on("forcetab")) {
            tab(g, font, state);
        }
        banner(g, font, width, height);
        g.pose().popPose();
    }

    // ------------------------------------------------------------ stempel LIAR!

    /** Stempel LIAR! gaya cetak (Saul Bass): huruf display merah miring, nama penuduh di bawahnya. */
    private static void liarStamp(GuiGraphics g, Font font, PublicState state, int width, int height, float partial) {
        float k = com.liarsbar.client.cinematic.LiarDirector.stamp(partial);
        int caller = com.liarsbar.client.cinematic.LiarDirector.caller();
        if (k <= 0.01f || caller < 0 || caller >= state.seats.size()) {
            return;
        }
        Component word = text(Component.translatable("liarsbar.hud2.liar"), DISPLAY_BIG);
        float pop = 1 + 0.25f * (1 - Math.min(1, k * 1.4f));
        g.pose().pushPose();
        g.pose().translate(width * 0.08f, height * 0.33f, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-6));
        g.pose().scale(1.5f * pop, 1.5f * pop, 1);
        // Salah register cetak: bayangan merah gelap sedikit bergeser.
        g.drawString(font, word, 2, 1, argb(0x5A060A, k * 0.6f), false);
        g.drawString(font, word, 0, 0, argb(RED, k), false);
        g.pose().popPose();
        Component name = text("— " + state.seats.get(caller).name.toUpperCase(Locale.ROOT), LABEL);
        g.drawString(font, name, Math.round(width * 0.08f + 8), Math.round(height * 0.33f + 52), argb(BRASS, k), true);
    }

    // ------------------------------------------------------------ judul ronde

    private static void roundTitle(GuiGraphics g, Font font, int width, float partial) {
        ClientTable.RoundTitle t = ClientTable.roundTitle();
        if (t == null || t.rank() == null) {
            return;
        }
        float age = ClientTable.now() + partial - t.at();
        float alpha = Mth.clamp(Math.min(age / 6f, (TITLE_TICKS - age) / 10f), 0, 1);
        if (alpha <= 0.01f) {
            return;
        }
        Component round = text(Component.translatable("liarsbar.hud2.round", t.round()), DISPLAY_BIG);
        Component table = text(Component.translatable("liarsbar.hud2.table." + t.rank().name().toLowerCase()), SERIF);
        int cx = width / 2;
        float s = 0.55f;
        g.pose().pushPose();
        g.pose().translate(cx, 18, 0);
        g.pose().scale(s, s, 1);
        int w = font.width(round);
        g.drawString(font, round, -w / 2, 0, argb(PAPER, alpha), false);
        g.pose().popPose();
        int tw = font.width(table);
        g.drawString(font, table, cx - tw / 2, 18 + Math.round(30 * s) + 2, argb(BRASS, alpha), false);
    }

    // ------------------------------------------------------------ subtitle klaim

    private static void claim(GuiGraphics g, Font font, PublicState state, int width, int height, float partial) {
        ClientTable.Claim c = ClientTable.claim();
        if (c == null || c.rank() == null || c.seat() == ClientTable.mySeat() || state.stage != PublicState.Stage.TURN) {
            return;
        }
        float age = ClientTable.now() + partial - c.at();
        float alpha = Mth.clamp(Math.min(age / 5f, (CLAIM_TICKS - age) / 12f), 0, 1);
        if (alpha <= 0.01f) {
            return;
        }
        RouletteDirector.Frame f = RouletteDirector.frame(c.seat());
        float[] at = f == null ? null : ScreenSpace.project(f.eye().add(0, 0.42, 0));
        Component name = text(state.seats.get(c.seat()).name.toUpperCase(Locale.ROOT), LABEL);
        int count = Mth.clamp(c.count(), 1, 3);
        Component quote = text(Component.literal("“").append(Component.translatable("liarsbar.quote."
                + c.rank().name().toLowerCase() + "." + count)).append("”"), SERIF);
        int w = Math.max(font.width(name), font.width(quote));
        float x, y;
        if (at == null) {
            x = width / 2f - w / 2f;
            y = height * 0.18f;
        } else {
            // Di samping kepala pembicara, dijaga tetap di layar.
            x = Mth.clamp(at[0] + 14, 8, width - w - 8);
            y = Mth.clamp(at[1] - 18, 8, height * 0.6f);
        }
        int ix = Math.round(x), iy = Math.round(y);
        g.drawString(font, name, ix + 1, iy, argb(BRASS, alpha), true);
        g.drawString(font, quote, ix, iy + 9, argb(PAPER, alpha), true);
        g.fill(ix + 1, iy + 22, ix + 13, iy + 23, argb(BRASS, alpha * 0.8f));
    }

    // ------------------------------------------------------------ petunjuk tombol

    private static void hints(GuiGraphics g, Font font, PublicState state, int width, int height) {
        if (!ClientTable.myTurn() || state.round > 1) {
            return;
        }
        String[][] items = state.canCallLiar
                ? new String[][]{{"liarsbar.hud2.cap.mouse", "liarsbar.hud2.key.mouse"},
                {key(TableKeys.PREVIOUS), key(TableKeys.NEXT), "liarsbar.hud2.key.select"},
                {key(TableKeys.SELECT), "liarsbar.hud2.key.mark"}, {key(TableKeys.THROW), "liarsbar.hud2.key.throw"},
                {key(TableKeys.LIAR), "liarsbar.hud2.key.liar"}}
                : new String[][]{{"liarsbar.hud2.cap.mouse", "liarsbar.hud2.key.mouse"},
                {key(TableKeys.PREVIOUS), key(TableKeys.NEXT), "liarsbar.hud2.key.select"},
                {key(TableKeys.SELECT), "liarsbar.hud2.key.mark"}, {key(TableKeys.THROW), "liarsbar.hud2.key.throw"}};
        int x = width - 10, y = height - 16;
        for (int i = items.length - 1; i >= 0; i--) {
            String[] item = items[i];
            Component label = text(Component.translatable(item[item.length - 1]), LABEL);
            x -= font.width(label);
            g.drawString(font, label, x, y, argb(PAPER, 0.8f), false);
            x -= 5;
            for (int k = item.length - 2; k >= 0; k--) {
                Component cap = item[k].startsWith("liarsbar.") ? text(Component.translatable(item[k]), LABEL)
                        : text(item[k], LABEL);
                int cw = Math.max(10, font.width(cap) + 6);
                x -= cw;
                g.fill(x, y - 3, x + cw, y + 9, argb(INK, 0.45f));
                g.renderOutline(x, y - 3, cw, 12, argb(PAPER, 0.6f));
                g.drawString(font, cap, x + (cw - font.width(cap) + 1) / 2, y - 1, argb(PAPER, 0.9f), false);
                x -= 2;
            }
            x -= 12;
        }
    }

    // ------------------------------------------------------------ tombol aksi

    /** Tombol yang bisa diklik saat kursor dilepas di meja. */
    public enum Button { THROW, LIAR }

    private static final int BUTTON_H = 18;
    /** Kotak tombol frame terakhir dalam piksel GUI: {x, y, w, h}; w = 0 berarti tidak tampil. */
    private static final int[][] RECTS = new int[Button.values().length][4];

    /** Tombol di titik GUI ini, hanya yang aktif (bisa dipencet). */
    public static Button buttonAt(double x, double y) {
        for (Button b : Button.values()) {
            int[] r = RECTS[b.ordinal()];
            if (r[2] > 0 && enabled(b) && x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3]) {
                return b;
            }
        }
        return null;
    }

    private static boolean enabled(Button b) {
        PublicState state = ClientTable.state();
        if (!ClientTable.myTurn() || state == null) {
            return false;
        }
        return b == Button.THROW ? !ClientTable.selected().isEmpty() : state.canCallLiar;
    }

    /**
     * Tombol LEMPAR dan LIAR di kanan bawah, hanya saat giliran sendiri dan kursor bebas. Gaya sama dengan
     * tutup tombol petunjuk: kertas tipis di atas tinta, tepi kuningan saat ditunjuk.
     */
    private static void buttons(GuiGraphics g, Font font, PublicState state, int width, int height) {
        for (int[] r : RECTS) {
            r[2] = 0;
        }
        if (!ClientTable.myTurn() || !com.liarsbar.client.TableMouse.free()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        double mx = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double my = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        int picked = ClientTable.selected().size();
        // Baris petunjuk ronde pertama ada di height - 16; tombol di atasnya.
        int y = height - (state.round > 1 ? 14 : 30) - BUTTON_H;
        int x = width - 10;
        Button[] order = state.canCallLiar ? new Button[]{Button.LIAR, Button.THROW} : new Button[]{Button.THROW};
        for (Button b : order) {
            Component label = text(b == Button.THROW
                    ? (picked > 0 ? Component.translatable("liarsbar.hud2.button.throw", picked)
                    : Component.translatable("liarsbar.hud2.key.throw"))
                    : Component.translatable("liarsbar.hud2.key.liar"), LABEL);
            Component cap = text(key(b == Button.THROW ? TableKeys.THROW : TableKeys.LIAR), LABEL);
            int capW = Math.max(10, font.width(cap) + 6);
            int w = Math.max(70, font.width(label) + capW + 18);
            x -= w;
            int[] r = RECTS[b.ordinal()];
            r[0] = x;
            r[1] = y;
            r[2] = w;
            r[3] = BUTTON_H;
            boolean on = enabled(b);
            boolean hover = on && mx >= x && mx < x + w && my >= y && my < y + BUTTON_H;
            float a = on ? 1f : 0.4f;
            g.fill(x, y, x + w, y + BUTTON_H, argb(b == Button.LIAR && hover ? RED : INK, hover ? 0.8f : 0.6f * a));
            g.renderOutline(x, y, w, BUTTON_H, argb(hover ? BRASS : PAPER, (hover ? 1f : 0.55f) * a));
            int cx = x + 6, cy = y + (BUTTON_H - 12) / 2;
            g.renderOutline(cx, cy, capW, 12, argb(PAPER, 0.5f * a));
            g.drawString(font, cap, cx + (capW - font.width(cap) + 1) / 2, cy + 2, argb(PAPER, 0.8f * a), false);
            g.drawString(font, label, cx + capW + 6, y + (BUTTON_H - 8) / 2, argb(hover ? BRASS : PAPER, a), false);
            x -= 6;
        }
    }

    private static String key(net.minecraft.client.KeyMapping mapping) {
        String s = mapping.getTranslatedKeyMessage().getString();
        return s.length() > 6 ? s.substring(0, 6).toUpperCase(Locale.ROOT) : s.toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------ daftar Tab

    private static void tab(GuiGraphics g, Font font, PublicState state) {
        int rows = state.seats.size();
        int x = 10, y = 10, w = 180, rowH = 16;
        int h = 22 + rows * rowH + 14;
        g.fill(x, y, x + w, y + h, argb(INK, 0.84f));
        g.renderOutline(x, y, w, h, argb(0x46403A, 1));
        g.drawString(font, text(Component.translatable("liarsbar.hud2.tab.title", state.tableId, Math.max(1, state.round)),
                LABEL), x + 8, y + 7, argb(BRASS, 1), false);
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < rows; i++) {
            PublicState.SeatInfo seat = state.seats.get(i);
            int ry = y + 22 + i * rowH;
            ResourceLocation skin = skin(mc, seat);
            int tint = seat.alive ? 0xFFFFFFFF : 0xFF505050;
            g.setColor(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f, 1);
            g.blit(skin, x + 8, ry, 10, 10, 8, 8, 8, 8, 64, 64);
            g.blit(skin, x + 8, ry, 10, 10, 40, 8, 8, 8, 64, 64);
            g.setColor(1, 1, 1, 1);
            boolean turn = state.stage == PublicState.Stage.TURN && state.current == i;
            Component name = text(seat.name, SERIF);
            int nameColor = !seat.alive ? DIM : turn ? BRASS : PAPER;
            g.drawString(font, name, x + 24, ry, argb(nameColor, 1), false);
            if (!seat.alive) {
                g.fill(x + 24, ry + 5, x + 24 + font.width(name), ry + 6, argb(DIM, 1));
            }
            if (i == ClientTable.mySeat()) {
                g.drawString(font, text(Component.translatable("liarsbar.hud2.tab.you"), MONO), x + 27 + font.width(name),
                        ry + 1, argb(DIM, 1), false);
            }
            for (int k = 0; k < Math.min(seat.cards, 7) && seat.alive; k++) {
                int cx = x + 100 + k * 5;
                g.fill(cx, ry + 1, cx + 4, ry + 9, argb(BRASS, 1));
                g.fill(cx + 1, ry + 2, cx + 3, ry + 8, argb(0x1E1814, 1));
            }
            for (int k = 0; k < state.chambers; k++) {
                int dx = x + 138 + k * 6;
                boolean fatal = !seat.alive && k == seat.pulls - 1;
                int col = fatal ? RED : k < seat.pulls ? PAPER : 0x3C3832;
                g.fill(dx, ry + 3, dx + 4, ry + 7, argb(col, 1));
            }
        }
        g.drawString(font, text(Component.translatable("liarsbar.hud2.tab.legend"), MONO), x + 8, y + h - 11,
                argb(DIM, 1), false);
    }

    private static ResourceLocation skin(Minecraft mc, PublicState.SeatInfo seat) {
        if (seat.player != null && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(seat.player);
            if (info != null) {
                return info.getSkinLocation();
            }
            return DefaultPlayerSkin.getDefaultSkin(seat.player);
        }
        return DefaultPlayerSkin.getDefaultSkin();
    }

    // ------------------------------------------------------------ baris kecil + banner

    private static void footLine(GuiGraphics g, Font font, int width, int height, Component line) {
        Component c = text(line, MONO);
        int w = font.width(c);
        g.drawString(font, c, (width - w) / 2, height - 22, argb(DIM, 0.95f), true);
    }

    /** Kejadian (LIAR, reveal, DOR, menang) sementara: huruf display tanpa kotak, di sepertiga atas. */
    private static void banner(GuiGraphics g, Font font, int width, int height) {
        ClientTable.Banner banner = ClientTable.banner();
        if (banner == null) {
            return;
        }
        long age = ClientTable.now() - banner.start();
        float alpha = Mth.clamp(Math.min(age / 4f, (banner.duration() - age) / 8f), 0, 1);
        if (alpha <= 0.02f) {
            return;
        }
        Component c = text(banner.text(), DISPLAY);
        g.pose().pushPose();
        g.pose().translate(width / 2f, height * 0.26f, 0);
        g.pose().scale(1.6f, 1.6f, 1f);
        int w = font.width(c);
        g.drawString(font, c, -w / 2, 0, argb(banner.color(), alpha), true);
        g.pose().popPose();
    }

    /** Untuk penonton di luar kursi: titik dunia ke layar (dipakai subtitle). */
    static float[] project(Vec3 world) {
        return ScreenSpace.project(world);
    }
}
