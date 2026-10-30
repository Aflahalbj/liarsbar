package com.liarsbar.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Block set bar. Model dasar menghadap utara ({@code facing=north}: sisi depan di -z, dinding di +z);
 * bentuk tumbukan diputar mengikuti {@code facing} sama seperti blockstate memutar modelnya.
 */
public final class BarBlocks {

    private BarBlocks() {
    }

    /** Kotak dalam 1/16 blok (seperti model JSON), diputar dari utara ke tiap arah horizontal. */
    static Map<Direction, VoxelShape> rotated(double[]... boxes) {
        Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                double[] r = rotate(b, dir);
                shape = Shapes.or(shape, Block.box(r[0], r[1], r[2], r[3], r[4], r[5]));
            }
            out.put(dir, shape.optimize());
        }
        return out;
    }

    /** Putar kotak (x0 y0 z0 x1 y1 z1) dari arah utara ke {@code dir} searah jarum jam dilihat dari atas. */
    private static double[] rotate(double[] b, Direction dir) {
        double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        return switch (dir) {
            case EAST -> new double[]{16 - z1, b[1], x0, 16 - z0, b[4], x1};
            case SOUTH -> new double[]{16 - x1, b[1], 16 - z1, 16 - x0, b[4], 16 - z0};
            case WEST -> new double[]{z0, b[1], 16 - x1, z1, b[4], 16 - x0};
            default -> b;
        };
    }

    /** Block berarah horizontal dengan bentuk opsional; dipasang menghadap pemain. */
    public static class Facing extends HorizontalDirectionalBlock {
        private final Map<Direction, VoxelShape> shapes;
        private final boolean solid;

        public Facing(Properties properties, Map<Direction, VoxelShape> shapes, boolean solid) {
            super(properties);
            this.shapes = shapes;
            this.solid = solid;
            registerDefaultState(defaultState().setValue(FACING, Direction.NORTH));
        }

        protected BlockState defaultState() {
            return stateDefinition.any();
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return shapes == null ? Shapes.block() : shapes.get(state.getValue(FACING));
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return solid ? getShape(state, level, pos, ctx) : Shapes.empty();
        }

        @Override
        public BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        public BlockState mirror(BlockState state, Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(FACING)));
        }
    }

    // ------------------------------------------------------------ dinding

    public enum WallLevel implements StringRepresentable {
        WAINSCOT, PAPER, CROWN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Dinding bar: satu sisi interior (wainscot / wallpaper / list mahkota), sisi lain bata. */
    public static class Wall extends Facing {
        public static final EnumProperty<WallLevel> LEVEL = EnumProperty.create("level", WallLevel.class);

        public Wall(Properties properties) {
            super(properties, null, true);
            registerDefaultState(defaultBlockState().setValue(LEVEL, WallLevel.PAPER));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, LEVEL);
        }
    }

    // ------------------------------------------------------------ tile bergambar (cermin, poster, jendela, papan)

    /** Cermin 3x2: col 0..2 dari kiri (dilihat dari depan), row 0 = bawah. */
    public static class MirrorTile extends Facing {
        public static final IntegerProperty COL = IntegerProperty.create("col", 0, 2);
        public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 1);

        public MirrorTile(Properties properties) {
            super(properties, rotated(new double[]{0, 0, 15, 16, 16, 16}), true);
            registerDefaultState(defaultBlockState().setValue(COL, 1).setValue(ROW, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, COL, ROW);
        }
    }

    public enum PosterDesign implements StringRepresentable {
        PULL, BLUFF;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Poster potongan kertas 1x2. */
    public static class Poster extends Facing {
        public static final EnumProperty<PosterDesign> DESIGN = EnumProperty.create("design", PosterDesign.class);

        public Poster(Properties properties) {
            super(properties, rotated(new double[]{0, 0, 15.5, 16, 16, 16}), false);
            registerDefaultState(defaultBlockState().setValue(DESIGN, PosterDesign.PULL)
                    .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, DESIGN, BlockStateProperties.DOUBLE_BLOCK_HALF);
        }
    }

    public enum Pane implements StringRepresentable {
        LEFT, RIGHT, SINGLE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Jendela kota malam (FACING = arah interior). Kotanya dilukis bercahaya di dalam relung, jadi suasana malam
     * hujan tetap ada walau di luar siang; block ini menahan cahaya langit sepenuhnya supaya bar tetap gelap.
     */
    public static class NoirWindow extends Facing {
        public static final EnumProperty<Pane> PANE = EnumProperty.create("pane", Pane.class);
        public static final IntegerProperty SCENE = IntegerProperty.create("scene", 0, 1);

        public NoirWindow(Properties properties) {
            super(properties, null, true);
            registerDefaultState(defaultBlockState().setValue(PANE, Pane.LEFT).setValue(SCENE, 0)
                    .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, PANE, SCENE, BlockStateProperties.DOUBLE_BLOCK_HALF);
        }

        @Override
        public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
            return 15;
        }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
            return false;
        }
    }

    public enum SignPart implements StringRepresentable {
        INNER, OUTER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Papan nama gantung (FACING = arah keluar dari dinding); INNER menempel dinding. */
    public static class HangingSign extends Facing {
        public static final EnumProperty<SignPart> PART = EnumProperty.create("part", SignPart.class);

        public HangingSign(Properties properties) {
            super(properties, rotated(new double[]{7.5, 0, 0, 8.5, 16, 16}), false);
            registerDefaultState(defaultBlockState().setValue(PART, SignPart.INNER));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, PART);
        }
    }

    /** Papan marquee 3 tile di dinding fasad; col 0 = kiri dilihat dari depan. */
    public static class Marquee extends Facing {
        public static final IntegerProperty COL = IntegerProperty.create("col", 0, 2);

        public Marquee(Properties properties) {
            super(properties, rotated(new double[]{0, 0, 14, 16, 16, 16}), false);
            registerDefaultState(defaultBlockState().setValue(COL, 1));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, COL);
        }
    }

    /** Block tanpa arah dengan bentuk dari kotak-kotak 1/16 blok. */
    public static class Shaped extends Block {
        private final VoxelShape shape;

        public Shaped(Properties properties, double[]... boxes) {
            super(properties);
            VoxelShape out = Shapes.empty();
            for (double[] b : boxes) {
                out = Shapes.or(out, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
            }
            this.shape = out.optimize();
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return shape;
        }
    }

    /** Meja kecil berkaki tunggal dengan lampu banker. */
    public static class SideTable extends Block {
        private static final VoxelShape SHAPE = Shapes.or(Block.box(1.5, 11, 1.5, 14.5, 12.25, 14.5),
                Block.box(6.5, 0, 6.5, 9.5, 11, 9.5), Block.box(4, 12, 6, 10, 16.75, 10));

        public SideTable(Properties properties) {
            super(properties);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }
    }

    // ------------------------------------------------------------ pipa, pintu

    /** Pipa besi di bawah plafon, sepanjang sumbu horizontal. */
    public static class Pipe extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        private static final VoxelShape Z = Block.box(6, 11.5, 0, 10, 16, 16);
        private static final VoxelShape X = Block.box(0, 11.5, 6, 16, 16, 10);

        public Pipe(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(AXIS);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(AXIS, ctx.getHorizontalDirection().getAxis());
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return state.getValue(AXIS) == Direction.Axis.X ? X : Z;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return Shapes.empty();
        }

        @Override
        public BlockState rotate(BlockState state, Rotation rotation) {
            if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
                return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
            }
            return state;
        }
    }

    /** Pintu kaca buram: saat tertutup menahan cahaya seperti dinding (bar tidak kebanjiran cahaya siang). */
    public static class FrostedDoor extends net.minecraft.world.level.block.DoorBlock {
        public FrostedDoor(Properties properties) {
            super(properties, net.minecraft.world.level.block.state.properties.BlockSetType.DARK_OAK);
        }

        @Override
        public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
            return state.getValue(OPEN) ? 0 : 15;
        }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
            return false;
        }
    }

    /** Stool bar: tumbukan setinggi dudukan. */
    public static class Stool extends Block {
        private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 10.5, 3, 13, 14, 13), Block.box(6.5, 0, 6.5, 9.5, 10.5, 9.5),
                Block.box(4, 0, 4, 12, 1, 12));

        public Stool(Properties properties) {
            super(properties);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }
    }
}
