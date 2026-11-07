package com.abandonedmoderncity.core;

import java.util.List;

/**
 * A rectangle of buildable ground inside a city block (inclusive block coordinates), with what stands on it.
 * Lots never touch a street: {@link City#computeLots} only keeps rectangles whose every column is buildable.
 */
public final class Lot {
    public enum Type { PARK, PLAZA, EMPTY }

    public final int x0;
    public final int z0;
    public final int x1;
    public final int z1;
    public final Type type;
    /** Whole-block park of a paved city (paths, flowers, fountain), else null. */
    public final Park park;
    public final List<Tree> trees;
    public final long seed;

    /** A small tree: trunk at (x, z), {@code height} logs from the ground, leaves around the top. */
    public record Tree(int x, int z, int height) {
        public static final int RADIUS = 2;
    }

    private Lot(int x0, int z0, int x1, int z1, Type type, Park park, List<Tree> trees, long seed) {
        this.park = park;
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
        this.type = type;
        this.trees = trees;
        this.seed = seed;
    }

    public boolean contains(int x, int z) {
        return x >= x0 && x <= x1 && z >= z0 && z <= z1;
    }

    /** A whole park: grass, weeds, flowers, trees, dirt paths and the fountain in the middle. */
    static Lot park(int x0, int z0, int x1, int z1, long seed) {
        Park p = new Park(x0, z0, x1, z1, seed);
        return new Lot(x0, z0, x1, z1, Type.PARK, p, p.trees, seed);
    }

    /**
     * Block of this lot at (x, y, z), for a lot whose ground surface block is at {@code base} (= city level - 1).
     * Returns {@link BlockCode#KEEP} above the content (the column was already cleared).
     */
    public int code(int x, int y, int z, int base) {
        if (park != null) {
            return park.code(x, y, z, base);
        }
        if (y == base) {
            return switch (type) {
                case PLAZA -> ((x + z) & 3) == 0 || ((x - z) & 3) == 0 ? BlockCode.PLAZA_ALT : BlockCode.PLAZA;
                case EMPTY -> Hash.unit(Hash.of(seed, 0x45L, x, z)) < 0.45 ? BlockCode.COARSE_DIRT : BlockCode.GRASS;
                default -> BlockCode.GRASS;
            };
        }
        if (y < base) {
            return BlockCode.KEEP;
        }
        int tree = treeCode(trees, x, y, z, base);
        if (tree != BlockCode.KEEP) {
            return tree;
        }
        if (y == base + 1 && type == Type.EMPTY && Hash.unit(Hash.of(seed, 0x53L, x, z)) < 0.08) {
            return BlockCode.SHRUB;
        }
        return BlockCode.KEEP;
    }

    /** Log or leaves of one of the trees at (x, y, z), else KEEP. */
    static int treeCode(List<Tree> trees, int x, int y, int z, int base) {
        for (Tree t : trees) {
            int dx = x - t.x();
            int dz = z - t.z();
            int top = base + t.height();
            if (dx == 0 && dz == 0 && y > base && y <= top) {
                return BlockCode.LOG;
            }
            int ady = y - top;
            if (ady >= -2 && ady <= 1) {
                int r = ady == 1 ? 1 : Tree.RADIUS;
                if (Math.abs(dx) <= r && Math.abs(dz) <= r && !(Math.abs(dx) == r && Math.abs(dz) == r && r > 1)) {
                    return BlockCode.LEAVES;
                }
            }
        }
        return BlockCode.KEEP;
    }

    /** Highest y this lot places anything at, relative to its base. */
    public int heightAboveBase() {
        int h = 2;
        if (park != null) {
            h = Math.max(h, park.heightAboveBase());
        }
        for (Tree t : trees) {
            h = Math.max(h, t.height() + 1);
        }
        return h;
    }
}
