package com.liarsbar.network;

import com.liarsbar.table.PublicState;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: status publik meja yang sedang dilihat pemain, plus
 * kursinya sendiri (-1 = penonton). {@code data} kosong = tidak sedang di
 * dekat meja mana pun. Isinya di-snapshot saat dibuat, karena state server
 * terus berubah sementara paket dikodekan di thread jaringan.
 */
public record TableStateS2C(byte[] data, int mySeat) {

    public static TableStateS2C of(PublicState state, long now, int mySeat) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        state.write(buf, now);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return new TableStateS2C(bytes, mySeat);
    }

    public static TableStateS2C none() {
        return new TableStateS2C(new byte[0], -1);
    }

    public static void encode(TableStateS2C msg, FriendlyByteBuf buf) {
        buf.writeByteArray(msg.data);
        buf.writeVarInt(msg.mySeat + 1);
    }

    public static TableStateS2C decode(FriendlyByteBuf buf) {
        return new TableStateS2C(buf.readByteArray(), buf.readVarInt() - 1);
    }

    /** Dipanggil di client: {@code now} = tick lokal. */
    public PublicState state(long now) {
        if (data.length == 0) {
            return null;
        }
        return PublicState.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(data)), now);
    }

    public static void handle(TableStateS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.liarsbar.client.ClientTable.onState(msg)));
        ctx.get().setPacketHandled(true);
    }
}
