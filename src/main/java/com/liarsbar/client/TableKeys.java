package com.liarsbar.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Kontrol di meja, sama dengan game asli: A/D pindah kartu, Spasi tandai,
 * E lempar kartu, X panggil LIAR. Hanya aktif selama duduk di meja (saat itu
 * gerak tidak berpengaruh dan inventory dicegah), jadi tidak bentrok dengan
 * tombol vanilla.
 */
public final class TableKeys {

    private static final String CATEGORY = "key.categories.liarsbar";

    /** Aktif selama duduk di meja dan tidak ada layar terbuka. */
    public static final IKeyConflictContext AT_TABLE = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return Minecraft.getInstance().screen == null && ClientTable.seated();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this || other == KeyConflictContext.UNIVERSAL;
        }
    };

    public static final KeyMapping PREVIOUS = key("previous", GLFW.GLFW_KEY_A);
    public static final KeyMapping NEXT = key("next", GLFW.GLFW_KEY_D);
    public static final KeyMapping SELECT = key("select", GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping THROW = key("throw", GLFW.GLFW_KEY_E);
    public static final KeyMapping LIAR = key("liar", GLFW.GLFW_KEY_X);

    private TableKeys() {
    }

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.liarsbar." + name, AT_TABLE, InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    static void register(RegisterKeyMappingsEvent event) {
        event.register(PREVIOUS);
        event.register(NEXT);
        event.register(SELECT);
        event.register(THROW);
        event.register(LIAR);
    }

    static void tick() {
        while (PREVIOUS.consumeClick()) {
            ClientTable.moveCursor(-1);
        }
        while (NEXT.consumeClick()) {
            ClientTable.moveCursor(1);
        }
        while (SELECT.consumeClick()) {
            ClientTable.toggleSelected();
        }
        while (THROW.consumeClick()) {
            ClientTable.throwSelected();
        }
        while (LIAR.consumeClick()) {
            ClientTable.callLiar();
        }
    }
}
