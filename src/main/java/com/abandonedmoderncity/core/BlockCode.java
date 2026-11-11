package com.abandonedmoderncity.core;

/**
 * Abstract blocks produced by the pure planner. The game layer maps each code (plus a building style) to a block
 * state; every block the mod can place is listed there and checked to be breakable.
 */
public final class BlockCode {
    private BlockCode() {
    }

    /** Leave the world as it is. */
    public static final int KEEP = -1;
    public static final int AIR = 0;

    // buildings (material depends on the style)
    public static final int WALL = 1;
    public static final int WALL_WORN = 2;
    public static final int WINDOW = 3;
    public static final int FLOOR = 4;
    public static final int TRIM = 5;
    public static final int LADDER_EAST = 6;
    public static final int ROOF_MOSS = 7;
    public static final int PARAPET = 8;
    public static final int LADDER_WEST = 9;
    public static final int LADDER_SOUTH = 10;
    public static final int LADDER_NORTH = 11;
    /** Iron bars around the openings of a building's open stairs. */
    public static final int RAIL = 12;
    /** Floor of a balcony: a top slab of the style's floor material. */
    public static final int BALCONY_FLOOR = 13;
    /** Stone parapet of a balcony: a wall block close to the style's colors. */
    public static final int BALCONY_WALL = 14;

    // lots
    public static final int GRASS = 20;
    public static final int COARSE_DIRT = 21;
    public static final int PLAZA = 22;
    public static final int PLAZA_ALT = 23;
    public static final int LOG = 24;
    public static final int LEAVES = 25;
    public static final int SHRUB = 26;
    /** Yard of a paved city (gray concrete powder). */
    public static final int CITY_GROUND = 27;

    // town houses; the 4 directions are added to the base code: + direction 0..3 (west, east, north, south)
    public static final int PLANKS = 30;
    public static final int GLASS = 31;
    public static final int DOOR_LOWER = 32;
    public static final int DOOR_UPPER = 36;
    /** Oak stair whose high side faces the added direction. */
    public static final int STAIR = 40;

    /** Grass block whatever the biome (parks of paved cities), dirt path, smooth stone slab (fountain ring). */
    public static final int LAWN = 28;
    public static final int PATH = 29;
    public static final int RING_SLAB = 46;
    /** Flower {@code FLOWER + k}, k < FLOWERS (see the game palette); tall grass in two halves. */
    public static final int FLOWER = 90;
    public static final int FLOWERS = 9;
    public static final int TALL_GRASS_LOWER = 100;
    public static final int TALL_GRASS_UPPER = 101;
    /**
     * Double oak door of a building: {@code BUILDING_DOOR + facing + 4 * (hinge on the right) + 8 * (upper half)},
     * facing one of the directions 0..3 (west, east, north, south).
     */
    public static final int BUILDING_DOOR = 120;
    public static final int BUILDING_DOOR_END = BUILDING_DOOR + 16;
    /**
     * Step of a building stairwell, in the stairs block of the building's style: {@code BUILDING_STAIR + facing}, the
     * high side towards facing (one of the directions 0..3).
     */
    public static final int BUILDING_STAIR = 140;
    public static final int BUILDING_STAIR_END = BUILDING_STAIR + 4;
    // overgrowth ({@link Flora}): logs and leaves + species (oak, birch, dark oak, spruce), vines + the direction of
    // the block they hang on (west, east, north, south)
    public static final int WILD_LOG = 200;
    /**
     * Leaves that decay like a real tree's: {@code WILD_LEAVES + 8 * species + distance}, the distance (1..6) being the
     * number of steps through leaves to a log, as vanilla keeps it (not persistent).
     */
    public static final int WILD_LEAVES = 240;
    public static final int WILD_LEAVES_END = WILD_LEAVES + 32;
    public static final int VINE = 208;
    public static final int MOSS_BLOCK = 212;
    public static final int FERN = 213;
    public static final int LARGE_FERN_LOWER = 214;
    public static final int LARGE_FERN_UPPER = 215;
    /** Bottom slab of mossy stone bricks (rubble, the mossy parts of the fountain ring). */
    public static final int MOSSY_SLAB = 216;
    /** Bottom slab of mossy cobblestone (the fountain ring). */
    public static final int MOSSY_COBBLE_SLAB = 217;
    /** Rubble {@code RUBBLE + k}, k < RUBBLES: mossy cobblestone, cobblestone, cracked stone bricks, gray concrete. */
    public static final int RUBBLE = 220;
    public static final int RUBBLES = 4;
    /** Taken-over road surfaces ({@link RoadDecay}): gravel, and cracked asphalt (blackstone, cobbled deepslate). */
    public static final int GRAVEL = 230;
    public static final int BLACKSTONE = 231;
    public static final int COBBLED_DEEPSLATE = 232;
    /** Block {@code TEMPLATE + i}: entry i of the palette of {@link Template#WATERFALL}. */
    public static final int TEMPLATE = 1000;
    /**
     * Block of a building structure ({@link Structures}): {@code STRUCT + 4 * state + rotation}, the state being an index
     * into {@link Structures#STATES} and the rotation in quarter turns clockwise.
     */
    public static final int STRUCT = 100_000;
}
