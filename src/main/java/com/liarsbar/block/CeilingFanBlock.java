package com.liarsbar.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Kipas plafon: rumah motor dari model block, bilah berputar digambar CeilingFanRenderer (client). */
public class CeilingFanBlock extends Block implements EntityBlock {

    private static final VoxelShape SHAPE = Block.box(5, 5, 5, 11, 16, 11);

    public CeilingFanBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    public static class Entity extends BlockEntity {
        public Entity(BlockPos pos, BlockState state) {
            super(ModBlocks.FAN_ENTITY.get(), pos, state);
        }

        @Override
        public net.minecraft.world.phys.AABB getRenderBoundingBox() {
            // Bilah menjangkau hampir satu blok ke tiap sisi.
            return new net.minecraft.world.phys.AABB(worldPosition).inflate(1.0);
        }
    }
}
