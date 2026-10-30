package com.liarsbar.network;

import com.liarsbar.game.deck.GameEvent;
import com.liarsbar.table.PublicState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client: satu kejadian di meja, dikirim tepat di momennya (client
 * mengubahnya jadi presentasi). {@link GameEvent.TriggerPulled} baru dikirim
 * saat pelatuk ditarik.
 */
public record TableEventS2C(int tableId, GameEvent event, int flags) {

    /** {@link GameEvent.RouletteStarted}: versi sinematik penuh (bukan versi pendek). */
    public static final int FULL = 1;
    /** {@link GameEvent.RouletteStarted}: penembak mengisi revolvernya dulu (tarikan pertamanya). */
    public static final int LOAD = 2;

    public TableEventS2C(int tableId, GameEvent event) {
        this(tableId, event, 0);
    }

    public boolean full() {
        return (flags & FULL) != 0;
    }

    public boolean load() {
        return (flags & LOAD) != 0;
    }

    private static final int ROUND = 0, TURN = 1, PLAYED = 2, CALLED = 3, REVEALED = 4, ROULETTE = 5,
            TRIGGER = 6, ELIMINATED = 7, WON = 8;

    public static void encode(TableEventS2C msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.tableId);
        buf.writeVarInt(msg.flags);
        GameEvent event = msg.event;
        if (event instanceof GameEvent.RoundStarted e) {
            buf.writeByte(ROUND);
            buf.writeVarInt(e.round());
            PublicState.writeRank(buf, e.tableRank());
            buf.writeVarInt(e.starter());
            buf.writeVarInt(e.decks());
            writeInts(buf, e.handSizes());
        } else if (event instanceof GameEvent.TurnStarted e) {
            buf.writeByte(TURN);
            buf.writeVarInt(e.seat());
            buf.writeBoolean(e.canCallLiar());
        } else if (event instanceof GameEvent.CardsPlayed e) {
            buf.writeByte(PLAYED);
            buf.writeVarInt(e.seat());
            buf.writeVarInt(e.count());
            PublicState.writeRank(buf, e.claim());
            buf.writeVarInt(e.cardsLeft());
        } else if (event instanceof GameEvent.LiarCalled e) {
            buf.writeByte(CALLED);
            buf.writeVarInt(e.caller());
            buf.writeVarInt(e.accused());
            buf.writeBoolean(e.forced());
        } else if (event instanceof GameEvent.Revealed e) {
            buf.writeByte(REVEALED);
            buf.writeVarInt(e.accused());
            PublicState.writeCards(buf, e.cards());
            buf.writeBoolean(e.lie());
        } else if (event instanceof GameEvent.RouletteStarted e) {
            buf.writeByte(ROULETTE);
            writeInts(buf, e.shooters());
        } else if (event instanceof GameEvent.TriggerPulled e) {
            buf.writeByte(TRIGGER);
            buf.writeVarInt(e.seat());
            buf.writeVarInt(e.pullNumber());
            buf.writeBoolean(e.fired());
        } else if (event instanceof GameEvent.PlayerEliminated e) {
            buf.writeByte(ELIMINATED);
            buf.writeVarInt(e.seat());
        } else if (event instanceof GameEvent.GameWon e) {
            buf.writeByte(WON);
            buf.writeVarInt(e.seat());
        } else {
            throw new IllegalArgumentException("unknown event " + event);
        }
    }

    public static TableEventS2C decode(FriendlyByteBuf buf) {
        int tableId = buf.readVarInt();
        int flags = buf.readVarInt();
        int type = buf.readByte();
        GameEvent event = switch (type) {
            case ROUND -> new GameEvent.RoundStarted(buf.readVarInt(), PublicState.readRank(buf), buf.readVarInt(),
                    buf.readVarInt(), readInts(buf));
            case TURN -> new GameEvent.TurnStarted(buf.readVarInt(), buf.readBoolean());
            case PLAYED -> new GameEvent.CardsPlayed(buf.readVarInt(), buf.readVarInt(), PublicState.readRank(buf),
                    buf.readVarInt());
            case CALLED -> new GameEvent.LiarCalled(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
            case REVEALED -> new GameEvent.Revealed(buf.readVarInt(), PublicState.readCards(buf), buf.readBoolean());
            case ROULETTE -> new GameEvent.RouletteStarted(readInts(buf));
            case TRIGGER -> new GameEvent.TriggerPulled(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
            case ELIMINATED -> new GameEvent.PlayerEliminated(buf.readVarInt());
            case WON -> new GameEvent.GameWon(buf.readVarInt());
            default -> throw new IllegalArgumentException("unknown event type " + type);
        };
        return new TableEventS2C(tableId, event, flags);
    }

    private static void writeInts(FriendlyByteBuf buf, List<Integer> values) {
        buf.writeVarInt(values.size());
        for (int value : values) {
            buf.writeVarInt(value);
        }
    }

    private static List<Integer> readInts(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Integer> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(buf.readVarInt());
        }
        return List.copyOf(values);
    }

    public static void handle(TableEventS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.liarsbar.client.ClientTable.onEvent(msg)));
        ctx.get().setPacketHandled(true);
    }
}
