package com.liarsbar.client.anim;

import com.liarsbar.entity.BotEntity;
import com.mojang.authlib.GameProfile;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Badan bot yang terlihat: pemain tiruan khusus client yang mengikuti posisi
 * {@link BotEntity} (yang ditunggangkan di kursi). Dengan begitu bot dianimasikan
 * Player Animator persis seperti pemain sungguhan. Tidak bisa disentuh atau dibidik.
 */
public final class BotPuppet extends RemotePlayer {

    final BotEntity bot;

    BotPuppet(ClientLevel level, BotEntity bot) {
        super(level, new GameProfile(UUID.nameUUIDFromBytes(("liarsbar-bot-" + bot.getUUID()).getBytes()),
                bot.getName().getString()));
        this.bot = bot;
        sync();
        xo = xOld = getX();
        yo = yOld = getY();
        zo = zOld = getZ();
        yBodyRotO = yBodyRot;
        yHeadRotO = yHeadRot;
    }

    private void sync() {
        float yaw = bot.getYRot();
        moveTo(bot.getX(), bot.getY(), bot.getZ(), yaw, 0);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    @Override
    public void tick() {
        xo = xOld = getX();
        yo = yOld = getY();
        zo = zOld = getZ();
        yRotO = getYRot();
        yBodyRotO = yBodyRot;
        yHeadRotO = yHeadRot;
        sync();
        tickCount++;
        // Tanpa logika Player vanilla: hanya animasinya yang berjalan.
        PlayerAnimationAccess.getPlayerAnimLayer(this).tick();
    }

    @Override
    public void aiStep() {
    }

    @Override
    public ResourceLocation getSkinTextureLocation() {
        String name = BotEntity.SKIN_NAMES[Math.floorMod(bot.skin(), BotEntity.SKIN_NAMES.length)];
        return new ResourceLocation("textures/entity/player/wide/" + name + ".png");
    }

    @Override
    public String getModelName() {
        return "default";
    }

    @Override
    public boolean isSkinLoaded() {
        return true;
    }

    @Override
    public Component getDisplayName() {
        return bot.getName();
    }

    @Override
    public boolean shouldShowName() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isSpectator() {
        return false;
    }
}
