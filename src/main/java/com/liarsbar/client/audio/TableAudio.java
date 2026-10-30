package com.liarsbar.client.audio;

import com.liarsbar.LiarsBar;
import com.liarsbar.client.fx.PostFx;
import com.liarsbar.mixin.ChannelAccessor;
import com.liarsbar.mixin.SoundEngineAccessor;
import com.liarsbar.mixin.SoundManagerAccessor;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Map;

/**
 * Audio dunia diredam selama roulette: filter low-pass OpenAL EFX dipasang ke
 * semua suara yang sedang diputar kecuali suara mod ini (detak jantung,
 * revolver, tembakan), jadi makin tegang dunia makin tertutup sampai tinggal
 * detak jantung. Kalau EFX tidak tersedia, hanya volumenya yang turun.
 */
public final class TableAudio {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static int filter = -1;
    private static boolean efxBroken;
    private static float applied = 0;

    private TableAudio() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        float strength = PostFx.current().lowpass();
        if (strength < 0.01f && applied < 0.01f) {
            return;
        }
        SoundEngine engine = ((SoundManagerAccessor) mc.getSoundManager()).liarsbar$engine();
        SoundEngineAccessor access = (SoundEngineAccessor) engine;
        if (!access.liarsbar$loaded()) {
            return;
        }
        applied = strength;
        float gain = 1 - 0.55f * strength;
        float gainHf = (float) Math.pow(1 - 0.97f * strength, 2);
        boolean off = strength < 0.01f;
        Map<SoundInstance, ChannelAccess.ChannelHandle> channels = access.liarsbar$channels();
        for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : new ArrayList<>(channels.entrySet())) {
            if (LiarsBar.MOD_ID.equals(entry.getKey().getLocation().getNamespace())) {
                continue;
            }
            entry.getValue().execute(channel -> apply(((ChannelAccessor) channel).liarsbar$source(), off, gain, gainHf));
        }
    }

    private static void apply(int source, boolean off, float gain, float gainHf) {
        if (efxBroken) {
            return;
        }
        try {
            if (filter == -1) {
                filter = EXTEfx.alGenFilters();
                EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            }
            if (off) {
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                return;
            }
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, gain);
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, Math.max(0.001f, gainHf));
            // Parameter filter disalin saat dipasang, jadi dipasang ulang tiap tick.
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
        } catch (Throwable t) {
            efxBroken = true;
            LOGGER.warn("OpenAL EFX low-pass unavailable, world audio will not be muffled", t);
        }
    }
}
