package com.liarsbar.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Kamera sinematik: posisi, arah, dan "lepas dari pemain" (badan sendiri ikut digambar). */
@Mixin(Camera.class)
public interface CameraAccessor {

    @Invoker("setRotation")
    void liarsbar$setRotation(float yRot, float xRot);

    @Invoker("setPosition")
    void liarsbar$setPosition(Vec3 pos);

    @Accessor("detached")
    void liarsbar$setDetached(boolean detached);
}
