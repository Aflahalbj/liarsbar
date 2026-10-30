package com.liarsbar.network;

import com.liarsbar.table.TableManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Client -> server: aksi di giliran sendiri. Server memvalidasi semuanya lewat rules engine. */
public record TableActionC2S(Action action, List<Integer> cardIds) {

    public enum Action {
        PLAY, CALL_LIAR
    }

    /** Batas aman ukuran paket (aturan sendiri membatasi 1-3). */
    private static final int MAX_IDS = 8;

    public static void encode(TableActionC2S msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeVarInt(msg.cardIds.size());
        for (int id : msg.cardIds) {
            buf.writeVarInt(id);
        }
    }

    public static TableActionC2S decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        int count = Math.min(buf.readVarInt(), MAX_IDS);
        List<Integer> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(buf.readVarInt());
        }
        return new TableActionC2S(action, List.copyOf(ids));
    }

    public static void handle(TableActionC2S msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                TableManager.onAction(player, msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
