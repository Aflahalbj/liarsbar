package com.liarsbar.item;

import com.liarsbar.LiarsBar;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, LiarsBar.MOD_ID);

    public static final RegistryObject<RevolverItem> REVOLVER = ITEMS.register("revolver",
            () -> new RevolverItem(new Item.Properties().stacksTo(1)));

    /** Properti NPC bar (dipegang pengunjung, bartender, pelayan). */
    public static final RegistryObject<Item> WHISKEY_GLASS = ITEMS.register("whiskey_glass",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> WHISKEY_BOTTLE = ITEMS.register("whiskey_bottle",
            () -> new Item(new Item.Properties()));

    private ModItems() {
    }
}
