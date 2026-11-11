package com.abandonedmoderncity.core;

import java.util.List;

/**
 * The core of a block left to nature: the old concrete broken up by grass, moss and coarse dirt, trees of several
 * species with vines, tall grass, ferns, bushes and rubble ({@link Flora}). It covers the whole free rectangle of the
 * block; the structures standing on it ({@code obstacles}) win over it, and no tree grows into them.
 */
public final class Wild {
    /** About one tree per this many columns, trunks at least {@link #SPACING} apart. */
    static final int AREA_PER_TREE = 55;
    static final int SPACING = 5;
    /** Share of columns with rubble on grass (4 x that on the bare concrete). */
    static final double RUBBLE = 0.012;

    public final int x0;
    public final int z0;
    public final int x1;
    public final int z1;
    private final long seed;
    public final Flora.Trees trees;

    Wild(int x0, int z0, int x1, int z1, long seed, List<Lot> obstacles) {
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
        this.seed = seed;
        this.trees = Flora.scatter(x0, z0, x1, z1, Hash.next(seed, 1), AREA_PER_TREE, SPACING, (x, z, reach) -> {
            for (Lot o : obstacles) {
                if (x + reach >= o.x0 - 1 && x - reach <= o.x1 + 1 && z + reach >= o.z0 - 1 && z - reach <= o.z1 + 1) {
                    return true;
                }
            }
            return false;
        });
    }

    public int heightAboveBase() {
        return Math.max(2, trees.maxTop());
    }

    /** Block at (x, y, z) for ground surface {@code base}. */
    public int code(int x, int y, int z, int base) {
        int dy = y - base;
        if (dy < 0) {
            return BlockCode.KEEP;
        }
        if (dy == 0) {
            return trees.trunk(x, z) ? BlockCode.LAWN : Flora.ground(seed, x, z);
        }
        int t = trees.code(x, y, z, base);
        if (t != BlockCode.KEEP) {
            return t;
        }
        if (dy > 2 || trees.trunk(x, z)) {
            return BlockCode.KEEP;
        }
        int plant = Flora.plant(seed, x, z, dy, Flora.ground(seed, x, z), RUBBLE);
        if (plant != BlockCode.KEEP && dy == 2 && trees.code(x, y - 1, z, base) != BlockCode.KEEP) {
            return BlockCode.KEEP; // the lower half is a leaf or a vine: no upper half
        }
        if (plant != BlockCode.KEEP && dy == 1 && trees.code(x, y + 1, z, base) != BlockCode.KEEP
                && (plant == BlockCode.TALL_GRASS_LOWER || plant == BlockCode.LARGE_FERN_LOWER)) {
            return BlockCode.SHRUB; // no room for the upper half
        }
        return plant;
    }
}
