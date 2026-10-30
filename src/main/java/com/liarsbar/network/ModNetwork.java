package com.liarsbar.network;

import com.liarsbar.LiarsBar;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * Paket meja. Server -> client hanya membawa info publik ({@link TableStateS2C},
 * {@link TableEventS2C}), kecuali {@link HandS2C} yang dikirim hanya ke
 * pemilik kartunya. Posisi peluru tidak pernah dikirim; hasil tembakan baru
 * dikirim di momen tembakan.
 */
public final class ModNetwork {

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LiarsBar.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, TableStateS2C.class, TableStateS2C::encode, TableStateS2C::decode,
                TableStateS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, HandS2C.class, HandS2C::encode, HandS2C::decode,
                HandS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, TableEventS2C.class, TableEventS2C::encode, TableEventS2C::decode,
                TableEventS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, TableActionC2S.class, TableActionC2S::encode, TableActionC2S::decode,
                TableActionC2S::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void send(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void toServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}
