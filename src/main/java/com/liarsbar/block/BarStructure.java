package com.liarsbar.block;

import com.liarsbar.LiarsBar;
import com.liarsbar.entity.BarNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * Struktur bar ({@code data/liarsbar/structures/bar.nbt}, dibuat oleh tools/bar_structure.mjs). Titik acuannya
 * pusat meja: blok tempat meja berdiri. Struktur hanya mengubah blok yang ia isi (interior dikosongkan,
 * sekitarnya dibiarkan); di bawah lantai diberi pondasi batu sampai menyentuh tanah.
 */
public final class BarStructure {

    public static final ResourceLocation ID = new ResourceLocation(LiarsBar.MOD_ID, "bar");
    /** Posisi pusat meja di dalam struktur (= -MIN di bar_structure.mjs). */
    private static final BlockPos TABLE_OFFSET = new BlockPos(10, 1, 9);
    private static final int MAX_FOUNDATION = 16;
    private static final int CLEAR_HEIGHT = 24;

    private BarStructure() {
    }

    /** Pasang bar dengan pusat meja di {@code table}; false kalau berkas struktur tidak ada. */
    public static boolean place(ServerLevel level, BlockPos table) {
        Optional<StructureTemplate> template = level.getStructureManager().get(ID);
        if (template.isEmpty()) {
            return false;
        }
        BlockPos origin = table.subtract(TABLE_OFFSET);
        var size = template.get().getSize();
        // Bersihkan seluruh tapak struktur (pohon, daun, bukit) dari lantai ke atas supaya fasad terlihat.
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(0, TABLE_OFFSET.getY(), 0),
                origin.offset(size.getX() - 1, CLEAR_HEIGHT, size.getZ() - 1))) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, air, Block.UPDATE_CLIENTS);
            }
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true);
        template.get().placeInWorld(level, origin, origin, settings, level.random, Block.UPDATE_CLIENTS);
        // Pondasi di bawah seluruh tapak sampai tanah padat (udara, air, rumput tinggi diisi).
        BlockState stone = ModBlocks.STONE.get().defaultBlockState();
        for (int x = 0; x < size.getX(); x++) {
            for (int z = 0; z < size.getZ(); z++) {
                for (int dy = 1; dy <= MAX_FOUNDATION; dy++) {
                    BlockPos pos = origin.offset(x, -dy, z);
                    if (!level.getBlockState(pos).canBeReplaced()) {
                        break;
                    }
                    level.setBlock(pos, stone, Block.UPDATE_CLIENTS);
                }
            }
        }
        spawnNpcs(level, table, origin, size);
        return true;
    }

    /**
     * NPC suasana: {peran, x, y kaki, z, yaw} relatif pusat meja. Pengunjung duduk dengan pinggul (0,75 blok di atas
     * kaki boneka) di atas dudukan: stool 0,875, booth 0,53, kursi bistro 0,5.
     */
    private static final double[][] NPCS = {
            {0, -3, 0.145, -4, 180}, {0, 4, 0.145, -4, 180},
            {0, -6, -0.2, 7.2, 180}, {0, 5, -0.2, 7.2, 180},
            {0, -7.05, -0.23, -3, -90}, {0, -4.95, -0.23, -3, 90},
            {1, 0, 0, -6, 0},
    };

    private static void spawnNpcs(ServerLevel level, BlockPos table, BlockPos origin, net.minecraft.core.Vec3i size) {
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(origin, origin.offset(size));
        for (BarNpcEntity old : level.getEntitiesOfClass(BarNpcEntity.class, area.inflate(2))) {
            old.discard();
        }
        net.minecraft.world.phys.Vec3 center = net.minecraft.world.phys.Vec3.atBottomCenterOf(table);
        for (int i = 0; i < NPCS.length; i++) {
            double[] n = NPCS[i];
            BarNpcEntity npc = com.liarsbar.entity.ModEntities.BAR_NPC.get().create(level);
            if (npc == null) {
                continue;
            }
            BarNpcEntity.Role role = BarNpcEntity.Role.values()[(int) n[0]];
            // Kulit beda-beda, tidak bentrok dengan bot meja (yang mulai dari indeks awal).
            npc.setup(role, 8 - i, center.add(n[1], n[2], n[3]), (float) n[4], center);
            level.addFreshEntity(npc);
        }
    }
}
