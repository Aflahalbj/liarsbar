package com.liarsbar.block;

import com.liarsbar.LiarsBar;
import com.liarsbar.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** Block set bar noir (Art Bible: set bar) + tab creative mod. */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, LiarsBar.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, LiarsBar.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB,
            LiarsBar.MOD_ID);

    private static final List<RegistryObject<? extends Item>> TAB_ITEMS = new ArrayList<>();

    /** Mob tidak pernah muncul di dalam bar (lantai gelap tanpa cahaya blok). */
    private static BlockBehaviour.Properties props(MapColor color, SoundType sound, float strength) {
        return BlockBehaviour.Properties.of().mapColor(color).sound(sound).strength(strength)
                .isValidSpawn((state, level, pos, type) -> false);
    }

    private static BlockBehaviour.Properties wood() {
        return props(MapColor.COLOR_BROWN, SoundType.WOOD, 2.0f);
    }

    private static BlockBehaviour.Properties metal() {
        return props(MapColor.METAL, SoundType.METAL, 1.5f).noOcclusion();
    }

    private static <B extends Block> RegistryObject<B> block(String name, Supplier<B> block) {
        RegistryObject<B> reg = BLOCKS.register(name, block);
        TAB_ITEMS.add(ModItems.ITEMS.register(name, () -> new BlockItem(reg.get(), new Item.Properties())));
        return reg;
    }

    private static <B extends Block> RegistryObject<B> block(String name, Supplier<B> block,
                                                             Function<B, Item> item) {
        RegistryObject<B> reg = BLOCKS.register(name, block);
        TAB_ITEMS.add(ModItems.ITEMS.register(name, () -> item.apply(reg.get())));
        return reg;
    }

    // ------------------------------------------------------------ arsitektur
    public static final RegistryObject<Block> FLOOR = block("bar_floor", () -> new Block(wood()));
    public static final RegistryObject<BarBlocks.Wall> WALL = block("bar_wall",
            () -> new BarBlocks.Wall(props(MapColor.COLOR_GREEN, SoundType.WOOD, 2.5f)));
    public static final RegistryObject<Block> CEILING = block("bar_ceiling",
            () -> new Block(props(MapColor.COLOR_BLACK, SoundType.METAL, 2.0f)));
    public static final RegistryObject<Block> BRICK = block("bar_brick",
            () -> new Block(props(MapColor.COLOR_BROWN, SoundType.STONE, 2.5f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> STONE = block("bar_stone",
            () -> new Block(props(MapColor.COLOR_GRAY, SoundType.STONE, 2.5f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> WALNUT = block("bar_walnut", () -> new Block(wood()));

    // ------------------------------------------------------------ back bar
    public static final RegistryObject<BarBlocks.Facing> COUNTER = block("bar_counter",
            () -> new BarBlocks.Facing(wood().noOcclusion(), null, true));
    public static final RegistryObject<BarBlocks.Facing> CABINET = block("back_cabinet",
            () -> new BarBlocks.Facing(wood().noOcclusion(), null, true));
    public static final RegistryObject<BarBlocks.Facing> SHELF = block("bottle_shelf",
            () -> new BarBlocks.Facing(props(MapColor.COLOR_BROWN, SoundType.GLASS, 1.0f).noOcclusion().lightLevel(s -> 7),
                    BarBlocks.rotated(new double[]{0, 0, 9, 16, 16, 16}), true));
    public static final RegistryObject<BarBlocks.MirrorTile> MIRROR = block("bar_mirror",
            () -> new BarBlocks.MirrorTile(props(MapColor.COLOR_BLACK, SoundType.GLASS, 1.0f).noOcclusion()));
    public static final RegistryObject<BarBlocks.Stool> STOOL = block("bar_stool",
            () -> new BarBlocks.Stool(props(MapColor.COLOR_BROWN, SoundType.WOOL, 1.0f).noOcclusion()));

    // ------------------------------------------------------------ lampu, plafon, dinding
    public static final RegistryObject<BarBlocks.Facing> SCONCE = block("wall_sconce",
            () -> new BarBlocks.Facing(metal().noCollission().lightLevel(s -> 8),
                    BarBlocks.rotated(new double[]{5, 3, 8, 11, 12, 16}), false));
    public static final RegistryObject<CeilingFanBlock> FAN = block("ceiling_fan",
            () -> new CeilingFanBlock(metal().noCollission()));
    public static final RegistryObject<BarBlocks.Pipe> PIPE = block("iron_pipe",
            () -> new BarBlocks.Pipe(metal().noCollission()));
    public static final RegistryObject<BarBlocks.Poster> POSTER = block("noir_poster",
            () -> new BarBlocks.Poster(props(MapColor.COLOR_YELLOW, SoundType.WOOL, 0.5f).noOcclusion().noCollission()));
    public static final RegistryObject<BarBlocks.NoirWindow> WINDOW = block("noir_window",
            () -> new BarBlocks.NoirWindow(props(MapColor.COLOR_BLACK, SoundType.GLASS, 1.5f).lightLevel(s -> 6)));
    public static final RegistryObject<BarBlocks.FrostedDoor> DOOR = block("frosted_door",
            () -> new BarBlocks.FrostedDoor(wood().noOcclusion()), b -> new DoubleHighBlockItem(b, new Item.Properties()));

    public static final RegistryObject<BarBlocks.Facing> BOOTH = block("bar_booth",
            () -> new BarBlocks.Facing(props(MapColor.COLOR_BROWN, SoundType.WOOL, 1.5f).noOcclusion(),
                    BarBlocks.rotated(new double[]{0, 0, 1, 16, 8.5, 16}, new double[]{0, 8.5, 13, 16, 16, 16}), true));
    public static final RegistryObject<BarBlocks.SideTable> SIDE_TABLE = block("side_table",
            () -> new BarBlocks.SideTable(wood().noOcclusion().lightLevel(s -> 10)));

    public static final RegistryObject<Block> BISTRO_TABLE = block("bistro_table",
            () -> new BarBlocks.Shaped(wood().noOcclusion(), new double[]{1, 11, 1, 15, 12, 15}, new double[]{6.5, 0, 6.5, 9.5, 11, 9.5}));
    public static final RegistryObject<BarBlocks.Facing> CHAIR = block("bar_chair",
            () -> new BarBlocks.Facing(wood().noOcclusion(),
                    BarBlocks.rotated(new double[]{3, 0, 3, 13, 8, 13}, new double[]{4, 8, 11.5, 12, 18.5, 12.5}), true));

    // ------------------------------------------------------------ fasad
    public static final RegistryObject<BarBlocks.Marquee> MARQUEE = block("bar_marquee",
            () -> new BarBlocks.Marquee(metal().noCollission().lightLevel(s -> 12)));
    public static final RegistryObject<BarBlocks.HangingSign> SIGN = block("bar_sign",
            () -> new BarBlocks.HangingSign(metal().noCollission().lightLevel(s -> 9)));
    public static final RegistryObject<BarBlocks.Facing> AWNING = block("bar_awning",
            () -> new BarBlocks.Facing(props(MapColor.COLOR_GREEN, SoundType.WOOL, 0.5f).noOcclusion().noCollission(),
                    BarBlocks.rotated(new double[]{0, 4, 0, 16, 16, 16}), false));

    public static final RegistryObject<BlockEntityType<CeilingFanBlock.Entity>> FAN_ENTITY = BLOCK_ENTITIES.register(
            "ceiling_fan", () -> BlockEntityType.Builder.of(CeilingFanBlock.Entity::new, FAN.get()).build(null));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.liarsbar"))
            .icon(() -> new ItemStack(SIGN.get()))
            .displayItems((params, out) -> {
                out.accept(ModItems.REVOLVER.get());
                for (RegistryObject<? extends Item> item : TAB_ITEMS) {
                    out.accept(item.get());
                }
            })
            .build());

    private ModBlocks() {
    }
}
