package com.liarsbar.entity;

import com.liarsbar.LiarsBar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entity meja: {@code noSave}, hidup selama server jalan (dikelola {@link com.liarsbar.table.TableManager}),
 * tidak ikut tersimpan ke chunk. Kecuali NPC bar, yang memang bagian dari bangunannya.
 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, LiarsBar.MOD_ID);

    public static final RegistryObject<EntityType<TableEntity>> TABLE = ENTITY_TYPES.register("table",
            () -> EntityType.Builder.<TableEntity>of(TableEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f).noSave().noSummon().fireImmune()
                    .clientTrackingRange(10).updateInterval(20).build("table"));

    public static final RegistryObject<EntityType<SeatEntity>> SEAT = ENTITY_TYPES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.5f).noSave().noSummon().fireImmune()
                    .clientTrackingRange(10).updateInterval(20).build("seat"));

    public static final RegistryObject<EntityType<BotEntity>> BOT = ENTITY_TYPES.register("bot",
            () -> EntityType.Builder.<BotEntity>of(BotEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).noSave().noSummon().fireImmune()
                    .clientTrackingRange(10).updateInterval(3).build("bot"));

    /** NPC suasana bar: ikut tersimpan ke chunk (bar tetap ramai setelah dunia dibuka ulang). */
    public static final RegistryObject<EntityType<BarNpcEntity>> BAR_NPC = ENTITY_TYPES.register("bar_npc",
            () -> EntityType.Builder.<BarNpcEntity>of(BarNpcEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).noSummon().fireImmune()
                    .clientTrackingRange(10).updateInterval(2).build("bar_npc"));

    private ModEntities() {
    }

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(BOT.get(), LivingEntity.createLivingAttributes().build());
        event.put(BAR_NPC.get(), BarNpcEntity.attributes().build());
    }
}
