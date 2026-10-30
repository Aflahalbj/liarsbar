package com.liarsbar.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Id sumber OpenAL sebuah channel (untuk filter low-pass EFX). */
@Mixin(Channel.class)
public interface ChannelAccessor {

    @Accessor("source")
    int liarsbar$source();
}
