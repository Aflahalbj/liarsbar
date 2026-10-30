package com.liarsbar.mixin;

import com.liarsbar.client.render.BarRoom;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hujan dunia tidak turun di dalam ruang bar (hujannya ada di kaca jendela). */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void liarsbar$noRainIndoors(LightTexture light, float partialTick, double camX, double camY, double camZ,
                                        CallbackInfo ci) {
        if (BarRoom.contains(camX, camY, camZ)) {
            ci.cancel();
        }
    }
}
