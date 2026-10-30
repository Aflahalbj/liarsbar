package com.liarsbar.client.anim;

import com.liarsbar.entity.BarNpcEntity;
import com.liarsbar.entity.BotEntity;
import com.mojang.authlib.GameProfile;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Badan NPC bar yang terlihat: pemain tiruan khusus client yang mengikuti {@link BarNpcEntity} (posisi, arah,
 * langkah kaki vanilla saat berjalan) dan dianimasikan {@link NpcPuppets}; gelas/botolnya digambar NpcPropLayer. Tidak bisa disentuh atau dibidik.
 */
public final class NpcPuppet extends RemotePlayer {

    final BarNpcEntity npc;

    NpcPuppet(ClientLevel level, BarNpcEntity npc) {
        super(level, new GameProfile(UUID.nameUUIDFromBytes(("liarsbar-npc-" + npc.getUUID()).getBytes()), "npc"));
        this.npc = npc;
        moveTo(npc.getX(), npc.getY(), npc.getZ(), npc.getYRot(), 0);
        sync();
        xo = xOld = getX();
        yo = yOld = getY();
        zo = zOld = getZ();
        yBodyRotO = yBodyRot;
        yHeadRotO = yHeadRot;
    }

    /** Ikuti NPC tanpa menyamakan posisi lama (moveTo akan menghapus langkah kaki dan interpolasinya). */
    private void sync() {
        setPos(npc.getX(), npc.getY(), npc.getZ());
        setYRot(npc.getYRot());
        yBodyRot = npc.yBodyRot;
        yHeadRot = npc.yHeadRot;
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
        // Langkah kaki vanilla dari perpindahan (lengan/kepala bisa ditimpa animasi).
        calculateEntityAnimation(false);
        tickCount++;
        PlayerAnimationAccess.getPlayerAnimLayer(this).tick();
    }

    /** Tick di npc_fetch saat tangan mencapai botol di rak (sama dengan tools/npc_anims.py). */
    public static final int GRAB = 30;

    @Override
    public void aiStep() {
    }

    @Override
    public ResourceLocation getSkinTextureLocation() {
        ResourceLocation skin = NpcSkins.get(npc).texture;
        if (skin != null) {
            return skin;
        }
        // Selama skin akun belum terunduh: skin bawaan.
        String name = BotEntity.SKIN_NAMES[Math.floorMod(npc.skin(), BotEntity.SKIN_NAMES.length)];
        return new ResourceLocation("textures/entity/player/wide/" + name + ".png");
    }

    @Override
    public String getModelName() {
        NpcSkins.Skin skin = NpcSkins.get(npc);
        return skin.texture != null && skin.slim ? "slim" : "default";
    }

    @Override
    public boolean isSkinLoaded() {
        return true;
    }

    @Override
    public Component getDisplayName() {
        return Component.empty();
    }

    @Override
    public boolean shouldShowName() {
        return false;
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
