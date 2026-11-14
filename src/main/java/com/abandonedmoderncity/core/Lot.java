package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A rectangle of buildable ground inside a city block (inclusive block coordinates), with what stands on it.
 * Lots never touch a street: {@link City#computeLots} only keeps rectangles whose every column is buildable.
 */
public final class Lot {
    public enum Type { PARK, BUILDING, WILD }

    public final int x0;
    public final int z0;
    public final int x1;
    public final int z1;
    public final Type type;
    /** Whole-block park of a paved city (paths, flowers, fountain), else null. */
    public final Park park;
    public final long seed;
    /** The structure standing on a BUILDING lot (its footprint is the lot), turned {@link #rot} quarter turns; else null. */
    public final Structures.Def building;
    public final int rot;
    /** The overgrown core of a block (WILD lot), else null. */
    public final Wild wild;

    private Lot(int x0, int z0, int x1, int z1, Type type, Park park, long seed, Structures.Def building, int rot,
                Wild wild) {
        this.wild = wild;
        this.building = building;
        this.rot = rot;
        this.park = park;
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
        this.type = type;
        this.seed = seed;
    }

    public boolean contains(int x, int z) {
        return x >= x0 && x <= x1 && z >= z0 && z <= z1;
    }

    /** A structure with its box corner at (x0, z0), turned {@code rot} quarter turns. */
    static Lot building(int x0, int z0, Structures.Def def, int rot, long seed) {
        return new Lot(x0, z0, x0 + def.sizeX(rot) - 1, z0 + def.sizeZ(rot) - 1, Type.BUILDING, null, seed, def, rot,
                null);
    }

    /**
     * The overgrown core of a block over its whole free rectangle; the {@code obstacles} (the structures of the block,
     * listed before it so that they win) keep their trees out.
     */
    static Lot wild(int x0, int z0, int x1, int z1, long seed, List<Lot> obstacles) {
        return new Lot(x0, z0, x1, z1, Type.WILD, null, seed, null, 0, new Wild(x0, z0, x1, z1, seed, obstacles));
    }

    /** A whole park: grass, weeds, flowers, trees, dirt paths and the fountain in the middle. */
    static Lot park(int x0, int z0, int x1, int z1, long seed) {
        Park p = new Park(x0, z0, x1, z1, seed);
        return new Lot(x0, z0, x1, z1, Type.PARK, p, seed, null, 0, null);
    }

    /**
     * Block of this lot at (x, y, z), for a lot whose ground surface block is at {@code base} (= city level - 1).
     * Returns {@link BlockCode#KEEP} above the content (the column was already cleared).
     */
    public int code(int x, int y, int z, int base) {
        if (park != null) {
            return park.code(x, y, z, base);
        }
        if (wild != null) {
            return wild.code(x, y, z, base);
        }
        // a structure
        if (y < base - building.ground()) {
            return BlockCode.KEEP;
        }
        int c = building.code(x - x0, y - base, z - z0, rot, seed);
        // the floor layer replaces the ground; where the structure has no block at it, the city ground stays,
        // except over dug-out space (the mouth of a garage ramp or stairwell stays open)
        if (y == base && (c == BlockCode.KEEP || c == BlockCode.AIR)) {
            return building.opening(x - x0, z - z0, rot) ? BlockCode.AIR : BlockCode.CITY_GROUND;
        }
        return c;
    }

    /** Banner design ({pattern, color} layers) of the structure block at (x, y, z), or null. */
    public List<String[]> bannerAt(int x, int y, int z, int base) {
        return building == null ? null : building.banner(x - x0, y - base, z - z0, rot);
    }

    private List<int[]> overhangs;

    /**
     * Leaves completing the tree crowns its template cut ({@link Structures.Def#overhangs}), out of this lot: each
     * {x, ry, z, code} in world x and z, ry over the base.
     */
    public List<int[]> overhangs() {
        if (overhangs == null) {
            List<int[]> out = new ArrayList<>();
            if (building != null) {
                for (int[] o : building.overhangs(rot)) {
                    out.add(new int[]{x0 + o[0], o[1], z0 + o[2], o[3]});
                }
            }
            overhangs = List.copyOf(out);
        }
        return overhangs;
    }

    /** Whether the structure container at (x, y, z) is hard to get to ({@link Access}): more generous loot. */
    public boolean hardAt(int x, int y, int z, int base) {
        return building != null && building.hard(x - x0, y - base, z - z0, rot);
    }

    /** Layers under its base this lot places blocks in (the buried layers of a structure). */
    public int depthBelowBase() {
        return building != null ? building.ground() : 0;
    }

    /** Highest y this lot places anything at, relative to its base. */
    public int heightAboveBase() {
        if (building != null) {
            return building.above();
        }
        if (wild != null) {
            return wild.heightAboveBase();
        }
        return Math.max(2, park.heightAboveBase());
    }
}
