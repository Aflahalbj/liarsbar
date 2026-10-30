package com.liarsbar.network;

import com.liarsbar.game.deck.Card;
import com.liarsbar.table.PublicState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Server -> satu client: kartu di tangan pemain itu sendiri. Tidak pernah dikirim ke orang lain. */
public record HandS2C(int tableId, List<Card> cards) {

    public static void encode(HandS2C msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.tableId);
        PublicState.writeCards(buf, msg.cards);
    }

    public static HandS2C decode(FriendlyByteBuf buf) {
        return new HandS2C(buf.readVarInt(), PublicState.readCards(buf));
    }

    public static void handle(HandS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.liarsbar.client.ClientTable.onHand(msg)));
        ctx.get().setPacketHandled(true);
    }
}
