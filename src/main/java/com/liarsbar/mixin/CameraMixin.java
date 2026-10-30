package com.liarsbar.mixin;

import com.liarsbar.client.cinematic.CameraDirector;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sesudah kamera vanilla dipasang, sutradara meja boleh mengambil alih (bidikan sinematik). */
@Mixin(Camera.class)
public class CameraMixin {

    @Inject(method = "setup", at = @At("TAIL"))
    private void liarsbar$direct(BlockGetter area, Entity focused, boolean thirdPerson, boolean inverseView,
                                 float partialTick, CallbackInfo ci) {
        CameraDirector.apply((Camera) (Object) this, partialTick);
    }
}
