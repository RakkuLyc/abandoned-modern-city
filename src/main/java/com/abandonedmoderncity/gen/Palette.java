package com.abandonedmoderncity.gen;

import com.abandonedmoderncity.AMCSimple;
import com.abandonedmoderncity.core.BlockCode;
import com.abandonedmoderncity.core.ChunkPlan;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import com.abandonedmoderncity.core.Template;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Every block the generator places. Any hardness is allowed (the user lifted the old limit), but nothing may be
 * unbreakable: {@link #validate} checks the whole list at startup and refuses to run otherwise. The only container is
 * the empty chest of some park fountains, so there is no loot.
 */
public final class Palette {
    private Palette() {
    }

    public static final BlockState AIR = Blocks.AIR.defaultBlockState();

    // ------------------------------------------------------------------ roads
    /** Lanes: polished blackstone (a slab on highway ramps). */
    public static final BlockState LANE = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    public static final BlockState LANE_SLAB = Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState();
    /** Sidewalks, highway edges and the 2 x 2 squares at the crossings: smooth stone. */
    public static final BlockState SIDEWALK = Blocks.SMOOTH_STONE.defaultBlockState();
    public static final BlockState SIDEWALK_SLAB = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
    public static final BlockState CITY_MARK = Blocks.WHITE_CONCRETE.defaultBlockState();
    public static final BlockState HIGHWAY_MARK = Blocks.YELLOW_TERRACOTTA.defaultBlockState();
    /** Wall on the highway edges. */
    public static final BlockState WALL = Blocks.STONE_BRICK_WALL.defaultBlockState();
    /** Fill, tunnel lining and bridge pillars. */
    public static final BlockState STONE = Blocks.STONE.defaultBlockState();
    /** Tunnel ceiling lights. */
    public static final BlockState LIGHT = Blocks.GLOWSTONE.defaultBlockState();

    // ------------------------------------------------------------------ terrain fill
    public static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    public static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    public static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
    public static final BlockState RED_SANDSTONE = Blocks.RED_SANDSTONE.defaultBlockState();

    // ------------------------------------------------------------------ lots
    public static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
    public static final BlockState PLAZA = Blocks.POLISHED_ANDESITE.defaultBlockState();
    public static final BlockState PLAZA_ALT = Blocks.STONE_BRICKS.defaultBlockState();
    public static final BlockState LOG = Blocks.OAK_LOG.defaultBlockState();
    public static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    public static final BlockState SHRUB = Blocks.SHORT_GRASS.defaultBlockState();
    // ------------------------------------------------------------------ town houses and fields
    public static final BlockState PLANKS = Blocks.OAK_PLANKS.defaultBlockState();
    public static final BlockState GLASS = Blocks.GLASS_PANE.defaultBlockState();
    /** Rails of the open stairs in buildings. */
    public static final BlockState RAIL = Blocks.IRON_BARS.defaultBlockState();
    /** Indexed by direction: west, east, north, south (0..3). */
    private static final Direction[] DIRS = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
    public static final BlockState[] DOOR_LOWER = new BlockState[4];
    public static final BlockState[] DOOR_UPPER = new BlockState[4];
    public static final BlockState[] STAIR = new BlockState[4];

    /** Double doors of the buildings: index facing + 4 * (hinge right) + 8 * (upper half). */
    public static final BlockState[] BUILDING_DOOR = new BlockState[16];

    static {
        for (int k = 0; k < 16; k++) {
            BUILDING_DOOR[k] = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, DIRS[k & 3])
                    .setValue(DoorBlock.HINGE, (k & 4) != 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT)
                    .setValue(DoorBlock.HALF, (k & 8) != 0 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
        }
        for (int d = 0; d < 4; d++) {
            BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, DIRS[d]);
            DOOR_LOWER[d] = door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            DOOR_UPPER[d] = door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
            STAIR[d] = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, DIRS[d]);
        }
    }

    // ------------------------------------------------------------------ parks
    public static final BlockState PATH = Blocks.DIRT_PATH.defaultBlockState();
    public static final BlockState RING_SLAB = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
    public static final BlockState[] FLOWERS = {b(Blocks.POPPY), b(Blocks.DANDELION), b(Blocks.AZURE_BLUET),
            b(Blocks.RED_TULIP), b(Blocks.ORANGE_TULIP), b(Blocks.WHITE_TULIP), b(Blocks.PINK_TULIP), b(Blocks.OXEYE_DAISY),
            b(Blocks.CORNFLOWER)};
    public static final BlockState TALL_GRASS_LOWER = Blocks.TALL_GRASS.defaultBlockState()
            .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
    public static final BlockState TALL_GRASS_UPPER = Blocks.TALL_GRASS.defaultBlockState()
            .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
    // ------------------------------------------------------------------ overgrowth (core/Flora)
    /** Logs of oak, birch, dark oak and spruce. */
    public static final BlockState[] WILD_LOGS = {b(Blocks.OAK_LOG), b(Blocks.BIRCH_LOG), b(Blocks.DARK_OAK_LOG), b(Blocks.SPRUCE_LOG)};
    /**
     * Real leaves of the same species, [8 * species + distance]: not persistent, with their distance to a log, so they
     * stay while the tree stands and decay once cut off from the wood.
     */
    public static final BlockState[] WILD_LEAVES = new BlockState[32];

    static {
        Block[] kinds = {Blocks.OAK_LEAVES, Blocks.BIRCH_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.SPRUCE_LEAVES};
        for (int sp = 0; sp < 4; sp++) {
            for (int d = 1; d <= 7; d++) {
                WILD_LEAVES[8 * sp + d] = kinds[sp].defaultBlockState().setValue(LeavesBlock.PERSISTENT, false)
                        .setValue(LeavesBlock.DISTANCE, d);
            }
        }
    }
    /** Vines hanging on the block to the west, east, north, south. */
    public static final BlockState[] VINES = {
            Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.WEST, true),
            Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.EAST, true),
            Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.NORTH, true),
            Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.SOUTH, true)};
    public static final BlockState MOSS_BLOCK = Blocks.MOSS_BLOCK.defaultBlockState();
    public static final BlockState FERN = Blocks.FERN.defaultBlockState();
    public static final BlockState LARGE_FERN_LOWER = Blocks.LARGE_FERN.defaultBlockState()
            .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
    public static final BlockState LARGE_FERN_UPPER = Blocks.LARGE_FERN.defaultBlockState()
            .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
    public static final BlockState MOSSY_SLAB = Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState();
    public static final BlockState MOSSY_COBBLE_SLAB = Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState();
    public static final BlockState[] RUBBLE = {b(Blocks.MOSSY_COBBLESTONE), b(Blocks.COBBLESTONE),
            b(Blocks.CRACKED_STONE_BRICKS), b(Blocks.GRAY_CONCRETE)};

    /** Palette of the fountain template, as block states. */
    private static BlockState[] fountain;

    static BlockState[] fountain() {
        if (fountain == null) {
            List<String> names = Template.WATERFALL.palette;
            BlockState[] out = new BlockState[names.size()];
            for (int i = 0; i < out.length; i++) {
                try {
                    out[i] = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), names.get(i), false).blockState();
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    throw new IllegalStateException("bad block state in waterfall.nbt: " + names.get(i), e);
                }
            }
            fountain = out;
        }
        return fountain;
    }

    /** Block states of the building structures turned 0..3 quarter turns clockwise: [state * 4 + rotation]. */
    private static volatile BlockState[] structures;

    /** The block state of a structure block code (minus {@link BlockCode#STRUCT}): state index * 4 + rotation. */
    static BlockState structure(int k) {
        BlockState[] all = structures;
        if (all == null) {
            synchronized (Palette.class) {
                if (structures == null) {
                    List<String> names = com.abandonedmoderncity.core.Structures.STATES;
                    BlockState[] out = new BlockState[names.size() * 4];
                    for (int i = 0; i < names.size(); i++) {
                        BlockState s;
                        try {
                            s = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), names.get(i), false).blockState();
                        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                            throw new IllegalStateException("bad block state in a structure: " + names.get(i), e);
                        }
                        out[4 * i] = s;
                        out[4 * i + 1] = s.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
                        out[4 * i + 2] = s.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_180);
                        out[4 * i + 3] = s.rotate(net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90);
                    }
                    structures = out;
                }
                all = structures;
            }
        }
        return all[k];
    }

    /** Yards of paved cities. */
    public static final BlockState CITY_GROUND = Blocks.GRAY_CONCRETE_POWDER.defaultBlockState();
    public static final BlockState ROOF_MOSS = Blocks.MOSS_CARPET.defaultBlockState();
    public static final BlockState LADDER_EAST = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
    public static final BlockState LADDER_WEST = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);
    public static final BlockState LADDER_SOUTH = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
    public static final BlockState LADDER_NORTH = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);

    // ------------------------------------------------------------------ building styles
    // wall, worn wall, window, floor, trim, parapet
    private static final BlockState[][] STYLES = {
            {b(Blocks.WHITE_CONCRETE), b(Blocks.LIGHT_GRAY_CONCRETE), b(Blocks.GLASS_PANE), b(Blocks.SMOOTH_STONE), b(Blocks.LIGHT_GRAY_CONCRETE), b(Blocks.WHITE_CONCRETE)},
            {b(Blocks.LIGHT_GRAY_CONCRETE), b(Blocks.GRAY_CONCRETE), b(Blocks.GRAY_STAINED_GLASS_PANE), b(Blocks.POLISHED_ANDESITE), b(Blocks.STONE_BRICKS), b(Blocks.LIGHT_GRAY_CONCRETE)},
            {b(Blocks.BRICKS), b(Blocks.POLISHED_GRANITE), b(Blocks.GLASS_PANE), b(Blocks.SPRUCE_PLANKS), b(Blocks.STONE_BRICKS), b(Blocks.BRICKS)},
            {b(Blocks.STONE_BRICKS), b(Blocks.CRACKED_STONE_BRICKS), b(Blocks.BLACK_STAINED_GLASS_PANE), b(Blocks.SMOOTH_STONE), b(Blocks.POLISHED_ANDESITE), b(Blocks.MOSSY_STONE_BRICKS)},
            {b(Blocks.TERRACOTTA), b(Blocks.BROWN_TERRACOTTA), b(Blocks.GLASS_PANE), b(Blocks.SMOOTH_SANDSTONE), b(Blocks.SMOOTH_SANDSTONE), b(Blocks.TERRACOTTA)},
            {b(Blocks.GRAY_CONCRETE), b(Blocks.CYAN_TERRACOTTA), b(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE), b(Blocks.POLISHED_ANDESITE), b(Blocks.WHITE_CONCRETE), b(Blocks.GRAY_CONCRETE)},
            {b(Blocks.QUARTZ_BLOCK), b(Blocks.CALCITE), b(Blocks.BLACK_STAINED_GLASS_PANE), b(Blocks.SMOOTH_STONE), b(Blocks.SMOOTH_QUARTZ), b(Blocks.QUARTZ_BLOCK)},
            {b(Blocks.MUD_BRICKS), b(Blocks.PACKED_MUD), b(Blocks.GLASS_PANE), b(Blocks.OAK_PLANKS), b(Blocks.DARK_OAK_PLANKS), b(Blocks.MUD_BRICKS)},
    };

    /** Stairwell steps of each style, close in color to its floor; [style][facing]. */
    private static final Block[] STYLE_STAIRS = {Blocks.STONE_STAIRS, Blocks.POLISHED_ANDESITE_STAIRS, Blocks.SPRUCE_STAIRS,
            Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.POLISHED_ANDESITE_STAIRS, Blocks.QUARTZ_STAIRS,
            Blocks.OAK_STAIRS};
    private static final BlockState[][] BUILDING_STAIR = new BlockState[STYLE_STAIRS.length][4];
    /** Balcony floors of each style: a top slab of (or close to) its floor material. */
    private static final BlockState[] BALCONY_FLOOR = {top(Blocks.SMOOTH_STONE_SLAB), top(Blocks.POLISHED_ANDESITE_SLAB),
            top(Blocks.SPRUCE_SLAB), top(Blocks.SMOOTH_STONE_SLAB), top(Blocks.SMOOTH_SANDSTONE_SLAB),
            top(Blocks.POLISHED_ANDESITE_SLAB), top(Blocks.SMOOTH_STONE_SLAB), top(Blocks.OAK_SLAB)};
    /** Stone balcony parapets of each style: a wall block close to its wall colors. */
    private static final BlockState[] BALCONY_WALL = {b(Blocks.DIORITE_WALL), b(Blocks.ANDESITE_WALL), b(Blocks.BRICK_WALL),
            b(Blocks.STONE_BRICK_WALL), b(Blocks.GRANITE_WALL), b(Blocks.POLISHED_DEEPSLATE_WALL), b(Blocks.DIORITE_WALL),
            b(Blocks.MUD_BRICK_WALL)};

    static {
        for (int s = 0; s < STYLE_STAIRS.length; s++) {
            for (int d = 0; d < 4; d++) {
                BUILDING_STAIR[s][d] = STYLE_STAIRS[s].defaultBlockState().setValue(StairBlock.FACING, DIRS[d]);
            }
        }
        if (STYLE_STAIRS.length != STYLES.length || BALCONY_FLOOR.length != STYLES.length
                || BALCONY_WALL.length != STYLES.length) {
            throw new IllegalStateException("Palette styles of different lengths");
        }
    }

    private static BlockState b(Block block) {
        return block.defaultBlockState();
    }

    private static BlockState top(Block slab) {
        return slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    /** Block for a building/lot code, or null for {@link BlockCode#KEEP}. */
    public static BlockState of(int code, int style, BlockState naturalTop) {
        BlockState[] s = STYLES[Math.floorMod(style, STYLES.length)];
        if (code >= BlockCode.STRUCT) {
            return structure(code - BlockCode.STRUCT);
        }
        if (code >= BlockCode.TEMPLATE) {
            return fountain()[code - BlockCode.TEMPLATE];
        }
        if (code >= BlockCode.FLOWER && code < BlockCode.FLOWER + BlockCode.FLOWERS) {
            return FLOWERS[code - BlockCode.FLOWER];
        }
        if (code >= BlockCode.WILD_LOG && code < BlockCode.WILD_LOG + 4) {
            return WILD_LOGS[code - BlockCode.WILD_LOG];
        }
        if (code >= BlockCode.WILD_LEAVES && code < BlockCode.WILD_LEAVES_END) {
            return WILD_LEAVES[code - BlockCode.WILD_LEAVES];
        }
        if (code >= BlockCode.VINE && code < BlockCode.VINE + 4) {
            return VINES[code - BlockCode.VINE];
        }
        if (code >= BlockCode.RUBBLE && code < BlockCode.RUBBLE + BlockCode.RUBBLES) {
            return RUBBLE[code - BlockCode.RUBBLE];
        }
        switch (code) {
            case BlockCode.MOSS_BLOCK:
                return MOSS_BLOCK;
            case BlockCode.FERN:
                return FERN;
            case BlockCode.LARGE_FERN_LOWER:
                return LARGE_FERN_LOWER;
            case BlockCode.LARGE_FERN_UPPER:
                return LARGE_FERN_UPPER;
            case BlockCode.MOSSY_SLAB:
                return MOSSY_SLAB;
            case BlockCode.MOSSY_COBBLE_SLAB:
                return MOSSY_COBBLE_SLAB;
            default:
                break;
        }
        if (code >= BlockCode.BUILDING_DOOR && code < BlockCode.BUILDING_DOOR_END) {
            return BUILDING_DOOR[code - BlockCode.BUILDING_DOOR];
        }
        if (code >= BlockCode.BUILDING_STAIR && code < BlockCode.BUILDING_STAIR_END) {
            return BUILDING_STAIR[Math.floorMod(style, STYLES.length)][code - BlockCode.BUILDING_STAIR];
        }
        if (code >= BlockCode.DOOR_LOWER && code < BlockCode.DOOR_LOWER + 4) {
            return DOOR_LOWER[code - BlockCode.DOOR_LOWER];
        }
        if (code >= BlockCode.DOOR_UPPER && code < BlockCode.DOOR_UPPER + 4) {
            return DOOR_UPPER[code - BlockCode.DOOR_UPPER];
        }
        if (code >= BlockCode.STAIR && code < BlockCode.STAIR + 4) {
            return STAIR[code - BlockCode.STAIR];
        }
        return switch (code) {
            case BlockCode.KEEP -> null;
            case BlockCode.AIR -> AIR;
            case BlockCode.WALL -> s[0];
            case BlockCode.WALL_WORN -> s[1];
            case BlockCode.WINDOW -> s[2];
            case BlockCode.FLOOR -> s[3];
            case BlockCode.TRIM -> s[4];
            case BlockCode.PARAPET -> s[5];
            case BlockCode.LADDER_EAST -> LADDER_EAST;
            case BlockCode.LADDER_WEST -> LADDER_WEST;
            case BlockCode.LADDER_SOUTH -> LADDER_SOUTH;
            case BlockCode.LADDER_NORTH -> LADDER_NORTH;
            case BlockCode.ROOF_MOSS -> ROOF_MOSS;
            case BlockCode.RAIL -> RAIL;
            case BlockCode.BALCONY_FLOOR -> BALCONY_FLOOR[Math.floorMod(style, STYLES.length)];
            case BlockCode.BALCONY_WALL -> BALCONY_WALL[Math.floorMod(style, STYLES.length)];
            case BlockCode.GRASS -> lotGround(naturalTop);
            case BlockCode.COARSE_DIRT -> COARSE_DIRT;
            case BlockCode.PLAZA -> PLAZA;
            case BlockCode.PLAZA_ALT -> PLAZA_ALT;
            case BlockCode.LOG -> LOG;
            case BlockCode.LEAVES -> LEAVES;
            case BlockCode.SHRUB -> SHRUB;
            case BlockCode.CITY_GROUND -> CITY_GROUND;
            case BlockCode.PLANKS -> PLANKS;
            case BlockCode.GLASS -> GLASS;
            case BlockCode.LAWN -> GRASS;
            case BlockCode.PATH -> PATH;
            case BlockCode.RING_SLAB -> RING_SLAB;
            case BlockCode.TALL_GRASS_LOWER -> TALL_GRASS_LOWER;
            case BlockCode.TALL_GRASS_UPPER -> TALL_GRASS_UPPER;
            default -> throw new IllegalArgumentException("unknown block code " + code);
        };
    }

    /** Yards keep sand in deserts and on beaches; elsewhere they are grass. */
    static BlockState lotGround(BlockState naturalTop) {
        if (naturalTop != null && (naturalTop.is(Blocks.SAND) || naturalTop.is(Blocks.RED_SAND))) {
            return naturalTop.getBlock().defaultBlockState();
        }
        return GRASS;
    }

    /** Surface block of a road column (markings have no slab: on a ramp they are plain lane). */
    public static BlockState roadSurface(byte zone, boolean slab) {
        return switch (zone) {
            case ChunkPlan.PARAPET -> SIDEWALK;
            case ChunkPlan.MARK -> slab ? LANE_SLAB : CITY_MARK;
            case ChunkPlan.CROSSWALK -> slab ? LANE_SLAB : CITY_MARK;
            case ChunkPlan.MARK_HIGHWAY -> slab ? LANE_SLAB : HIGHWAY_MARK;
            case ChunkPlan.LANE -> slab ? LANE_SLAB : LANE;
            default -> slab ? SIDEWALK_SLAB : SIDEWALK;
        };
    }

    /** Full block under a slab, or the second deck layer. */
    public static BlockState roadBase(byte zone) {
        return zone == ChunkPlan.LANE || zone == ChunkPlan.MARK || zone == ChunkPlan.MARK_HIGHWAY || zone == ChunkPlan.CROSSWALK ? LANE : SIDEWALK;
    }

    /** Whether the generator may place this block (breakable, no block entity but the fountain chest). */
    public static boolean allowed(BlockState s) {
        float h = s.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return h >= 0 && (!s.hasBlockEntity() || exempt(s));
    }

    /**
     * The block entities allowed (asked for by the user): the empty chest of the park fountain and what the building
     * structures bring, always empty and without loot: chests, barrels, brewing stands, decorated pots, lecterns,
     * furnaces (blast furnace, smoker), beds, campfires, daylight detectors and jukeboxes.
     */
    public static boolean exempt(BlockState s) {
        Block b = s.getBlock();
        return b instanceof ChestBlock || b instanceof net.minecraft.world.level.block.BarrelBlock
                || b instanceof net.minecraft.world.level.block.BrewingStandBlock
                || b instanceof net.minecraft.world.level.block.DecoratedPotBlock
                || b instanceof net.minecraft.world.level.block.LecternBlock
                || b instanceof net.minecraft.world.level.block.AbstractFurnaceBlock
                || b instanceof net.minecraft.world.level.block.BedBlock
                || b instanceof net.minecraft.world.level.block.CampfireBlock
                || b instanceof net.minecraft.world.level.block.DaylightDetectorBlock
                || b instanceof net.minecraft.world.level.block.JukeboxBlock;
    }

    /** All fixed states the generator places (natural surface blocks reused for slopes are checked when used). */
    public static List<BlockState> all() {
        Set<BlockState> set = new LinkedHashSet<>(List.of(LANE, LANE_SLAB, SIDEWALK, SIDEWALK_SLAB, CITY_MARK, HIGHWAY_MARK, WALL, STONE, LIGHT, DIRT, GRASS, SANDSTONE,
                RED_SANDSTONE, COARSE_DIRT, CITY_GROUND, PLANKS, GLASS, RAIL, PLAZA, PLAZA_ALT, LOG, LEAVES, SHRUB, ROOF_MOSS, LADDER_EAST, LADDER_WEST, LADDER_SOUTH, LADDER_NORTH,
                Blocks.SAND.defaultBlockState(), Blocks.RED_SAND.defaultBlockState()));
        for (BlockState[] style : STYLES) {
            set.addAll(List.of(style));
        }
        set.addAll(List.of(DOOR_LOWER));
        set.addAll(List.of(DOOR_UPPER));
        set.addAll(List.of(STAIR));
        set.addAll(List.of(BUILDING_DOOR));
        for (BlockState[] stairs : BUILDING_STAIR) {
            set.addAll(List.of(stairs));
        }
        set.addAll(List.of(BALCONY_FLOOR));
        set.addAll(List.of(BALCONY_WALL));
        set.addAll(List.of(PATH, RING_SLAB, TALL_GRASS_LOWER, TALL_GRASS_UPPER));
        set.addAll(List.of(FLOWERS));
        set.addAll(List.of(WILD_LOGS));
        for (BlockState l : WILD_LEAVES) {
            if (l != null) {
                set.add(l);
            }
        }
        set.addAll(List.of(VINES));
        set.addAll(List.of(RUBBLE));
        set.addAll(List.of(MOSS_BLOCK, FERN, LARGE_FERN_LOWER, LARGE_FERN_UPPER, MOSSY_SLAB, MOSSY_COBBLE_SLAB));
        for (BlockState f : fountain()) {
            // flowing water of the fountain is a fluid, not a block to break
            if (!(f.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)) {
                set.add(f);
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * Throws if any block of the palette is unbreakable or a block entity (container), or if a building or
     * lot block is one that ore blobs replace (stone, granite, diorite, andesite, tuff, deepslate): a chunk decorated
     * later would eat it. Road stone is the one exception, restored by {@code ChunkBuilder.repairNeighbours}.
     */
    public static void validate() {
        List<String> bad = new ArrayList<>();
        for (BlockState s : all()) {
            if (!allowed(s)) {
                bad.add(BuiltInRegistries.BLOCK.getKey(s.getBlock()) + " (hardness "
                        + s.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) + ")");
            }
        }
        List<BlockState> structures = new ArrayList<>(List.of(PLAZA, PLAZA_ALT, COARSE_DIRT, LOG, ROOF_MOSS, PLANKS, GLASS,
                CITY_GROUND));
        for (BlockState[] style : STYLES) {
            structures.addAll(List.of(style));
        }
        for (BlockState[] stairs : BUILDING_STAIR) {
            structures.addAll(List.of(stairs));
        }
        structures.addAll(List.of(BALCONY_FLOOR));
        structures.addAll(List.of(BALCONY_WALL));
        for (BlockState s : structures) {
            if (s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || s.is(net.minecraft.tags.BlockTags.STONE_ORE_REPLACEABLES)
                    || s.is(net.minecraft.tags.BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
                bad.add(BuiltInRegistries.BLOCK.getKey(s.getBlock()) + " (replaced by ore blobs)");
            }
        }
        // the building structures: breakable, and no block entity but the empty containers
        int nStructure = com.abandonedmoderncity.core.Structures.STATES.size();
        for (int i = 0; i < nStructure; i++) {
            BlockState s = structure(4 * i);
            if (!allowed(s)) {
                bad.add(BuiltInRegistries.BLOCK.getKey(s.getBlock()) + " (structure, hardness "
                        + s.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) + ")");
            }
        }
        if (!bad.isEmpty()) {
            throw new IllegalStateException("[AMCS] Palette contains forbidden blocks: " + bad);
        }
        AMCSimple.LOGGER.info("[AMCS] Palette validated: {} block states and {} structure states, all breakable, no block entities but empty containers",
                all().size(), nStructure);
    }

    /** Natural surface block reused on slopes, if soft enough; otherwise stone. */
    static BlockState surfaceFor(BlockState natural) {
        if (natural == null || natural.isAir() || !allowed(natural)) {
            return GRASS;
        }
        if (natural.is(net.minecraft.tags.BlockTags.ICE)) {
            // frozen water is not a slope surface
            return Blocks.SNOW_BLOCK.defaultBlockState();
        }
        return natural;
    }

    /** What goes under a reused surface block on a fill. */
    static BlockState subsurfaceFor(BlockState natural) {
        if (natural == null) {
            return DIRT;
        }
        if (natural.is(Blocks.SAND)) {
            return SANDSTONE;
        }
        if (natural.is(Blocks.RED_SAND)) {
            return RED_SANDSTONE;
        }
        if (natural.is(Blocks.GRASS_BLOCK) || natural.is(Blocks.PODZOL) || natural.is(Blocks.MYCELIUM)
                || natural.is(Blocks.MOSS_BLOCK) || natural.is(Blocks.SNOW_BLOCK) || natural.is(Blocks.DIRT)
                || natural.is(Blocks.COARSE_DIRT) || natural.is(Blocks.ROOTED_DIRT) || natural.is(Blocks.MUD)
                || natural.is(Blocks.DIRT_PATH)) {
            return DIRT;
        }
        return allowed(natural) && !natural.is(Blocks.GRAVEL) ? natural : STONE;
    }
}
