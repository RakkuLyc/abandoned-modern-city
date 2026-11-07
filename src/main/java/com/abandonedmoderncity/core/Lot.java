package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A rectangle of buildable ground inside a city block (inclusive block coordinates), with what stands on it.
 * Lots never touch a street: {@link City#computeLots} only keeps rectangles whose every column is buildable.
 */
public final class Lot {
    public enum Type { PARK, PLAZA, EMPTY, FARM }

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

    private Lot(int x0, int z0, int x1, int z1, Type type, List<Tree> trees, long seed) {
        this(x0, z0, x1, z1, type, null, trees, seed);
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

    /**
     * Lot contents of the cities that are not paved yet (towns, the octagon): switched off, their lots are plain lawn.
     * Paved cities build their own lots ({@link City#paved}).
     */
    public static final boolean BUILDINGS = false;

    /**
     * A whole abandoned field: farmland with water channels every {@link #CHANNEL} columns (one column in from the
     * edge), one crop per strip between channels at random ages, a few bare patches and weeds.
     */
    static Lot farm(int x0, int z0, int x1, int z1, long seed) {
        return new Lot(x0, z0, x1, z1, Type.FARM, List.of(), seed);
    }

    static final int CHANNEL = 9;

    private int farmCode(int x, int y, int z, int base) {
        boolean alongX = x1 - x0 >= z1 - z0;
        // channels run along the longer side
        int across = alongX ? z - z0 : x - x0;
        int len = alongX ? z1 - z0 : x1 - x0;
        boolean inner = x > x0 && x < x1 && z > z0 && z < z1;
        boolean channel = inner && Math.floorMod(across - 4, CHANNEL) == 0 && across < len - 1;
        int strip = Math.floorDiv(across - 4, CHANNEL);
        if (y == base) {
            if (channel) {
                return BlockCode.WATER;
            }
            return Hash.unit(Hash.of(seed, 0x46L, x, z)) < 0.06 ? BlockCode.COARSE_DIRT : BlockCode.FARMLAND;
        }
        if (y != base + 1 || channel) {
            return BlockCode.KEEP;
        }
        if (Hash.unit(Hash.of(seed, 0x46L, x, z)) < 0.06) {
            return Hash.unit(Hash.of(seed, 0x48L, x, z)) < 0.5 ? BlockCode.SHRUB : BlockCode.KEEP;
        }
        double r = Hash.unit(Hash.of(seed, 0x47L, x, z));
        if (r < 0.12) {
            return BlockCode.KEEP;
        }
        if (r < 0.17) {
            return BlockCode.SHRUB;
        }
        int kind = Hash.range(Hash.of(seed, 0x43L, strip), 0, 3);
        int ages = kind == 3 ? 4 : 8;
        int age = Hash.range(Hash.of(seed, 0x41L, x, z), 0, ages - 1);
        return BlockCode.CROP + 8 * kind + age;
    }

    /** A whole park: grass, weeds, flowers, trees, dirt paths and the fountain in the middle. */
    static Lot park(int x0, int z0, int x1, int z1, long seed) {
        Park p = new Park(x0, z0, x1, z1, seed);
        return new Lot(x0, z0, x1, z1, Type.PARK, p, p.trees, seed);
    }

    static Lot create(City city, int x0, int z0, int x1, int z1, long seed, City.Hit h) {
        if (!BUILDINGS) {
            return new Lot(x0, z0, x1, z1, Type.PARK, List.of(), seed);
        }
        double rest = Hash.unit(Hash.next(seed, 2));
        Type type = rest < 0.5 ? Type.PARK : rest < 0.75 ? Type.PLAZA : Type.EMPTY;
        List<Tree> trees = type == Type.PARK ? trees(x0, z0, x1, z1, Hash.next(seed, 3)) : List.of();
        return new Lot(x0, z0, x1, z1, type, trees, seed);
    }

    private static List<Tree> trees(int x0, int z0, int x1, int z1, long seed) {
        int ax0 = x0 + Tree.RADIUS + 1;
        int ax1 = x1 - Tree.RADIUS - 1;
        int az0 = z0 + Tree.RADIUS + 1;
        int az1 = z1 - Tree.RADIUS - 1;
        if (ax1 < ax0 || az1 < az0) {
            return List.of();
        }
        int target = Math.max(1, (x1 - x0 + 1) * (z1 - z0 + 1) / 60);
        List<Tree> out = new ArrayList<>();
        for (int k = 0; k < target * 4 && out.size() < target; k++) {
            long h = Hash.next(seed, k);
            int x = Hash.range(h, ax0, ax1);
            int z = Hash.range(Hash.next(h, 1), az0, az1);
            boolean free = true;
            for (Tree t : out) {
                if (Math.abs(t.x() - x) < 6 && Math.abs(t.z() - z) < 6) {
                    free = false;
                    break;
                }
            }
            if (free) {
                out.add(new Tree(x, z, Hash.range(Hash.next(h, 2), 4, 6)));
            }
        }
        return List.copyOf(out);
    }

    /**
     * Block of this lot at (x, y, z), for a lot whose ground surface block is at {@code base} (= city level - 1).
     * Returns {@link BlockCode#KEEP} above the content (the column was already cleared).
     */
    public int code(int x, int y, int z, int base) {
        if (park != null) {
            return park.code(x, y, z, base);
        }
        if (type == Type.FARM) {
            return farmCode(x, y, z, base);
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
