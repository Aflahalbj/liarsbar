package com.liarsbar.sound;

import com.liarsbar.LiarsBar;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Suara meja: roulette disintesis sendiri (tools/make_sounds.py), kartu rekaman CC0. */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LiarsBar.MOD_ID);

    public static final RegistryObject<SoundEvent> HEARTBEAT = register("heartbeat");
    public static final RegistryObject<SoundEvent> REVOLVER_SPIN = register("revolver_spin");
    public static final RegistryObject<SoundEvent> REVOLVER_OPEN = register("revolver_open");
    public static final RegistryObject<SoundEvent> REVOLVER_CLOSE = register("revolver_close");
    public static final RegistryObject<SoundEvent> HAMMER_COCK = register("hammer_cock");
    public static final RegistryObject<SoundEvent> DRY_FIRE = register("dry_fire");
    public static final RegistryObject<SoundEvent> GUNSHOT = register("gunshot");
    public static final RegistryObject<SoundEvent> TINNITUS = register("tinnitus");
    public static final RegistryObject<SoundEvent> EXHALE_RELIEF = register("exhale_relief");
    public static final RegistryObject<SoundEvent> INHALE_TENSE = register("inhale_tense");
    public static final RegistryObject<SoundEvent> BODY_FALL = register("body_fall");
    public static final RegistryObject<SoundEvent> GUN_TABLE = register("gun_table");
    public static final RegistryObject<SoundEvent> GUN_PICKUP = register("gun_pickup");
    /** Kartu: rekaman Kenney "Casino Audio" (CC0), lihat CREDITS.txt. */
    public static final RegistryObject<SoundEvent> CARD_RIFFLE = register("card_riffle");
    public static final RegistryObject<SoundEvent> CARD_SLIDE = register("card_slide");
    public static final RegistryObject<SoundEvent> CARD_PLACE = register("card_place");
    public static final RegistryObject<SoundEvent> CARD_SHOVE = register("card_shove");
    public static final RegistryObject<SoundEvent> CARD_FAN = register("card_fan");
    /** Peluru: dijepit dari felt, meluncur masuk chamber, pelek duduk di muka silinder. */
    public static final RegistryObject<SoundEvent> BULLET_PICK = register("bullet_pick");
    public static final RegistryObject<SoundEvent> BULLET_SLIDE = register("bullet_slide");
    public static final RegistryObject<SoundEvent> BULLET_SEAT = register("bullet_seat");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(LiarsBar.MOD_ID, name)));
    }
}
