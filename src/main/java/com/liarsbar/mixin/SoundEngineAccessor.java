package com.liarsbar.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Semua suara yang sedang diputar beserta channel-nya (filter low-pass diterapkan ke tiap channel). */
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {

    @Accessor("instanceToChannel")
    Map<SoundInstance, ChannelAccess.ChannelHandle> liarsbar$channels();

    @Accessor("loaded")
    boolean liarsbar$loaded();
}
