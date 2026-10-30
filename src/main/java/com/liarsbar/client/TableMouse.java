package com.liarsbar.client;

import com.liarsbar.client.cinematic.CameraDirector;
import com.liarsbar.client.cinematic.DevCamera;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.liarsbar.client.render.FirstPersonCards;
import com.liarsbar.client.ui.TableHud;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Mouse di meja: selama duduk dalam game, kursor dilepas (tidak menggerakkan pandangan) supaya kartu bisa
 * ditunjuk dan diklik. Klik kiri pada kartu menandainya, klik tombol LEMPAR/LIAR di layar menjalankannya,
 * klik kiri sambil digeser memutar pandangan, roda mouse memindah kartu yang ditunjuk. Tombol keyboard (A/D, Spasi, E, X) tetap jalan.
 * Saat sinematik, menembak, atau layar terbuka, mouse kembali seperti biasa (dikunci vanilla).
 * Dipanggil dari {@code MouseHandlerMixin}; posisi dalam piksel jendela.
 */
public final class TableMouse {

    /** Geseran (piksel) sebelum tekan-lepas dianggap seret, bukan klik. */
    private static final double DRAG_START = 6;

    private static boolean wasFree;
    private static boolean down;
    private static boolean dragging;
    private static double pressX, pressY;
    private static double lastX = Double.NaN, lastY = Double.NaN;
    private static double mouseX, mouseY;
    private static boolean moved;
    private static int hovered = -1;
    private static TableHud.Button pressedButton;

    private TableMouse() {
    }

    /** Kursor dilepas untuk meja sekarang. */
    public static boolean free() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && mc.getOverlay() == null && mc.options.getCameraType().isFirstPerson()
                && ClientTable.inGame() && CameraDirector.cinematicWeight() <= 0 && !RouletteDirector.localShooting()
                && DevCamera.current() == null;
    }

    /** Kartu yang sedang di bawah kursor (-1: tidak ada). */
    public static int hovered() {
        return hovered;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean free = free();
        if (free && mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        } else if (!free && wasFree) {
            down = false;
            dragging = false;
            if (mc.screen == null) {
                mc.mouseHandler.grabMouse();
            }
        }
        wasFree = free;
    }

    /** Dipanggil tiap frame sebelum dunia digambar: kartu di bawah kursor menjadi kartu yang ditunjuk. */
    public static void frame() {
        if (!free()) {
            hovered = -1;
            return;
        }
        int card = dragging ? -1 : FirstPersonCards.cardAt(mouseX, mouseY);
        if (moved) {
            ClientTable.mouseMoved();
        }
        if (card != hovered) {
            hovered = card;
            if (card >= 0 && moved) {
                ClientTable.pointAt(card);
            }
        }
        moved = false;
    }

    /** @return true kalau klik dipakai meja (vanilla tidak boleh menyerang/mengunci mouse). */
    public static boolean onPress(int button, int action) {
        if (!free()) {
            if (action == GLFW.GLFW_RELEASE && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                down = false;
                pressedButton = null;
                dragging = false;
            }
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW.GLFW_PRESS) {
                pressedButton = TableHud.buttonAt(guiX(mouseX), guiY(mouseY));
                // Tombol tidak bisa diseret jadi putaran kamera.
                down = pressedButton == null;
                dragging = false;
                pressX = mouseX;
                pressY = mouseY;
            } else if (action == GLFW.GLFW_RELEASE) {
                if (pressedButton != null) {
                    if (pressedButton == TableHud.buttonAt(guiX(mouseX), guiY(mouseY))) {
                        press(pressedButton);
                    }
                    pressedButton = null;
                } else if (down && !dragging) {
                    int card = FirstPersonCards.cardAt(mouseX, mouseY);
                    if (card >= 0) {
                        ClientTable.pointAt(card);
                        ClientTable.toggleSelected();
                    }
                }
                down = false;
                dragging = false;
            }
        }
        return true;
    }

    public static void onMove(double x, double y) {
        if (!Double.isNaN(lastX) && free() && down) {
            if (!dragging && Math.hypot(x - pressX, y - pressY) > DRAG_START) {
                dragging = true;
            }
            if (dragging) {
                turn(x - lastX, y - lastY);
            }
        }
        if (x != lastX || y != lastY) {
            moved = true;
        }
        lastX = x;
        lastY = y;
        mouseX = x;
        mouseY = y;
    }

    private static void press(TableHud.Button button) {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
        if (button == TableHud.Button.THROW) {
            ClientTable.throwSelected();
        } else {
            ClientTable.callLiar();
        }
    }

    private static double guiX(double x) {
        var window = Minecraft.getInstance().getWindow();
        return x * window.getGuiScaledWidth() / window.getScreenWidth();
    }

    private static double guiY(double y) {
        var window = Minecraft.getInstance().getWindow();
        return y * window.getGuiScaledHeight() / window.getScreenHeight();
    }

    /** @return true kalau roda dipakai meja. */
    public static boolean onScroll(double delta) {
        if (!free()) {
            return false;
        }
        if (delta != 0) {
            ClientTable.moveCursor(delta > 0 ? -1 : 1);
        }
        return true;
    }

    /** Putar pandangan dengan kepekaan mouse vanilla (rumus yang sama dengan {@code MouseHandler.turnPlayer}). */
    private static void turn(double dx, double dy) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        double s = mc.options.sensitivity().get() * 0.6 + 0.2;
        double k = s * s * s * 8.0;
        int invert = mc.options.invertYMouse().get() ? -1 : 1;
        mc.player.turn(dx * k, dy * k * invert);
    }
}
