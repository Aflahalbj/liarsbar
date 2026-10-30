package com.liarsbar.mixin;

import com.liarsbar.client.TableMouse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Di meja kursor dilepas: klik, seret, dan roda mouse dipakai {@link TableMouse}, bukan untuk menyerang/menoleh. */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void liarsbar$keepFree(CallbackInfo ci) {
        if (TableMouse.free()) {
            ci.cancel();
        }
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void liarsbar$press(long window, int button, int action, int mods, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow() && TableMouse.onPress(button, action)) {
            ci.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    private void liarsbar$move(long window, double x, double y, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow()) {
            TableMouse.onMove(x, y);
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void liarsbar$scroll(long window, double dx, double dy, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow() && TableMouse.onScroll(dy)) {
            ci.cancel();
        }
    }
}
