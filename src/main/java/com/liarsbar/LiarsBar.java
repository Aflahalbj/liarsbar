package com.liarsbar;

import com.liarsbar.command.LiarsBarCommand;
import com.liarsbar.entity.ModEntities;
import com.liarsbar.network.ModNetwork;
import com.liarsbar.table.TableManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Liar's Deck di meja bundar untuk 2-10 pemain. Aturan ada di
 * {@code com.liarsbar.game} (Java murni, di-unit-test), meja server di
 * {@code com.liarsbar.table}, presentasi di {@code com.liarsbar.client}.
 */
@Mod(LiarsBar.MOD_ID)
public class LiarsBar {

    public static final String MOD_ID = "liarsbar";

    public LiarsBar() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITY_TYPES.register(modBus);
        // Block didaftarkan dulu: ModBlocks ikut menambah BlockItem ke ModItems.ITEMS.
        com.liarsbar.block.ModBlocks.BLOCKS.register(modBus);
        com.liarsbar.block.ModBlocks.BLOCK_ENTITIES.register(modBus);
        com.liarsbar.block.ModBlocks.TABS.register(modBus);
        com.liarsbar.item.ModItems.ITEMS.register(modBus);
        com.liarsbar.sound.ModSounds.SOUNDS.register(modBus);
        modBus.addListener(ModEntities::onAttributes);
        ModNetwork.register();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(TableManager.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.liarsbar.client.ClientSetup.init(modBus));
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LiarsBarCommand.register(event.getDispatcher());
    }
}
