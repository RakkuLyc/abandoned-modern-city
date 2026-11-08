package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A park filling a whole block: grass with weeds and flowers, trees, dirt paths 3 wide (with gaps) in one of two
 * layouts, and a free center holding the fountain ({@link Template#WATERFALL}) inside a ring of smooth stone slabs.
 * <ul>
 *   <li>{@link #GRID}: two north-south and two east-west paths {@link #PATH_OFFSET} from the center, through the
 *   whole park (the center square is left free).</li>
 *   <li>{@link #PINWHEEL}: a circular path of radius {@link #PATH_OFFSET} around the center and four spiral arms
 *   leaving it to the edges of the park.</li>
 * </ul>
 * {@link #code} is a pure function of the position.
 */
public final class Park {
    public static final int GRID = 0;
    public static final int PINWHEEL = 1;
    /**
     * Distance from the center to the middle of the grid paths, and radius of the circular path, for a park
     * {@link #BASE_SIZE} wide; larger parks scale them up (the 105 x 60 blocks are not: x 1).
     */
    static final double PATH_OFFSET = 16;
    static final int BASE_SIZE = 68;
    static final double PATH_HW = 1.0;
    /** Ring of smooth stone slabs around the fountain: radii (column centers) between these. */
    static final double RING_IN = 12.5;
    static final double RING_OUT = 13.5;
    /** Spiral arms: radius grows by ARM_GROWTH per radian, for ARM_TURN radians. */
    static final double ARM_GROWTH = 48 / Math.PI;
    static final double ARM_TURN = Math.PI * 0.8;
    /** No trees closer than this to the center. */
    static final double FREE_RADIUS = 19;

    public final int x0;
    public final int z0;
    public final int x1;
    public final int z1;
    public final int variant;
    /** Whether the fountain keeps its (empty) chest; otherwise mossy cobblestone takes its place. */
    public final boolean chest;
    private final long seed;
    /** Template origin (its corner column) and the center of the park (world coordinates). */
    private final int ox;
    private final int oz;
    private final double cx;
    private final double cz;
    private final double armPhase;
    /** Path distance from the center and arm growth, scaled with the park (68 wide: PATH_OFFSET, ARM_GROWTH). */
    private final double offset;
    private final double growth;
    private final int armSense;
    final List<Lot.Tree> trees;

    Park(int x0, int z0, int x1, int z1, long seed) {
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
        this.seed = seed;
        this.variant = Hash.unit(Hash.next(seed, 1)) < 0.5 ? GRID : PINWHEEL;
        this.chest = Hash.unit(Hash.next(seed, 2)) < 0.5;
        Template t = Template.WATERFALL;
        this.ox = (x0 + x1 + 1) / 2 - t.sx / 2;
        this.oz = (z0 + z1 + 1) / 2 - t.sz / 2;
        this.cx = ox + t.sx / 2.0;
        this.cz = oz + t.sz / 2.0;
        double scale = Math.max(1, Math.min(x1 - x0 + 1, z1 - z0 + 1) / (double) BASE_SIZE);
        this.offset = PATH_OFFSET * scale;
        this.growth = ARM_GROWTH * scale;
        this.armPhase = Hash.range(Hash.next(seed, 3), 0.0, Math.PI / 2);
        this.armSense = Hash.unit(Hash.next(seed, 4)) < 0.5 ? 1 : -1;
        this.trees = trees(Hash.next(seed, 5));
    }

    private double radius(int x, int z) {
        return Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
    }

    /** Whether column (x, z) is on a path. */
    boolean path(int x, int z) {
        if (x < x0 || x > x1 || z < z0 || z > z1) {
            return false;
        }
        double dx = x + 0.5 - cx;
        double dz = z + 0.5 - cz;
        if (variant == GRID) {
            return Math.abs(Math.abs(dx) - offset) <= PATH_HW || Math.abs(Math.abs(dz) - offset) <= PATH_HW;
        }
        double r = Math.hypot(dx, dz);
        if (Math.abs(r - offset) <= PATH_HW + 0.5) {
            return true;
        }
        double phi = Math.atan2(dz, dx) * armSense;
        double tol = (PATH_HW + 0.5) * Math.sqrt(1 + (growth / Math.max(r, 1)) * (growth / Math.max(r, 1)));
        for (int k = 0; k < 4; k++) {
            double start = armPhase + k * Math.PI / 2;
            double t = Math.floorMod(Math.round((phi - start) * 1e6), Math.round(2 * Math.PI * 1e6)) / 1e6;
            if (t <= ARM_TURN && Math.abs(r - (offset + growth * t)) <= tol) {
                return true;
            }
        }
        return false;
    }

    private boolean inFountain(int x, int z) {
        Template t = Template.WATERFALL;
        return radius(x, z) < RING_IN && x >= ox && x < ox + t.sx && z >= oz && z < oz + t.sz;
    }

    private boolean onRing(int x, int z) {
        double r = radius(x, z);
        return r >= RING_IN && r < RING_OUT;
    }

    private List<Lot.Tree> trees(long h0) {
        int r = Lot.Tree.RADIUS;
        int ax0 = x0 + r + 1;
        int ax1 = x1 - r - 1;
        int az0 = z0 + r + 1;
        int az1 = z1 - r - 1;
        List<Lot.Tree> out = new ArrayList<>();
        if (ax1 < ax0 || az1 < az0) {
            return out;
        }
        int target = (x1 - x0 + 1) * (z1 - z0 + 1) / 70;
        for (int k = 0; k < target * 6 && out.size() < target; k++) {
            long h = Hash.next(h0, k);
            int x = Hash.range(h, ax0, ax1);
            int z = Hash.range(Hash.next(h, 1), az0, az1);
            if (radius(x, z) < FREE_RADIUS || nearPath(x, z, r + 1)) {
                continue;
            }
            boolean free = true;
            for (Lot.Tree t : out) {
                if (Math.abs(t.x() - x) < 6 && Math.abs(t.z() - z) < 6) {
                    free = false;
                    break;
                }
            }
            if (free) {
                out.add(new Lot.Tree(x, z, Hash.range(Hash.next(h, 2), 4, 6)));
            }
        }
        return List.copyOf(out);
    }

    private boolean nearPath(int x, int z, int reach) {
        for (int dz = -reach; dz <= reach; dz++) {
            for (int dx = -reach; dx <= reach; dx++) {
                if (path(x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean nearTree(int x, int z) {
        for (Lot.Tree t : trees) {
            if (Math.abs(t.x() - x) <= Lot.Tree.RADIUS && Math.abs(t.z() - z) <= Lot.Tree.RADIUS) {
                return true;
            }
        }
        return false;
    }

    public int heightAboveBase() {
        int h = Template.WATERFALL.sy;
        for (Lot.Tree t : trees) {
            h = Math.max(h, t.height() + 2);
        }
        return h;
    }

    private double rnd(int x, int z, long salt) {
        return Hash.unit(Hash.of(seed, salt, x, z));
    }

    public int code(int x, int y, int z, int base) {
        int dy = y - base;
        if (dy < 0) {
            return BlockCode.KEEP;
        }
        boolean inside = radius(x, z) < RING_OUT;
        if (inside && dy == 1 && (!inFountain(x, z) || OUTSIDE[(x - ox) + (z - oz) * Template.WATERFALL.sx])) {
            // the ring and everything between it and the fountain's hedge: smooth stone slabs
            return BlockCode.RING_SLAB;
        }
        if (inside && dy == 0 && !inFountain(x, z)) {
            return BlockCode.LAWN;
        }
        if (inside && !inFountain(x, z)) {
            return BlockCode.KEEP;
        }
        if (inFountain(x, z)) {
            Template t = Template.WATERFALL;
            int idx = t.at(x - ox, dy, z - oz);
            if (idx < 0) {
                return dy == 0 ? BlockCode.LAWN : BlockCode.KEEP;
            }
            if (!chest && idx == CHEST) {
                idx = MOSSY;
            }
            return BlockCode.TEMPLATE + idx;
        }
        boolean path = path(x, z);
        // a few path columns are bare: grass or coarse dirt, sometimes with weeds
        boolean gap = path && rnd(x, z, 0x50L) < 0.08;
        if (dy == 0) {
            if (path && !gap) {
                return BlockCode.PATH;
            }
            if (gap) {
                return rnd(x, z, 0x51L) < 0.5 ? BlockCode.LAWN : BlockCode.COARSE_DIRT;
            }
            return BlockCode.LAWN;
        }
        if (onRing(x, z)) {
            return dy == 1 ? BlockCode.RING_SLAB : BlockCode.KEEP;
        }
        int tree = Lot.treeCode(trees, x, y, z, base);
        if (tree != BlockCode.KEEP) {
            return tree;
        }
        if (path && !gap) {
            return BlockCode.KEEP;
        }
        if (gap) {
            return dy == 1 && rnd(x, z, 0x52L) < 0.3 ? BlockCode.SHRUB : BlockCode.KEEP;
        }
        double r = rnd(x, z, 0x53L);
        boolean tall = r >= 0.28 && r < 0.34 && !nearTree(x, z);
        if (dy == 2) {
            return tall ? BlockCode.TALL_GRASS_UPPER : BlockCode.KEEP;
        }
        if (dy != 1) {
            return BlockCode.KEEP;
        }
        if (r < 0.28) {
            return BlockCode.SHRUB;
        }
        if (tall) {
            return BlockCode.TALL_GRASS_LOWER;
        }
        if (r >= 0.34 && r < 0.41) {
            return BlockCode.FLOWER + Hash.range(Hash.of(seed, 0x54L, x, z), 0, BlockCode.FLOWERS - 1);
        }
        return BlockCode.KEEP;
    }

    /** Radius of the fountain's hedge (its leaves stand 10.8 to 11.4 from the center; it has 4 openings). */
    static final double HEDGE_RADIUS = 11;

    /** Template columns outside the fountain's hedge: nothing on the first layer above the ground, out from it. */
    static final boolean[] OUTSIDE = outside();

    private static boolean[] outside() {
        Template t = Template.WATERFALL;
        boolean[] out = new boolean[t.sx * t.sz];
        for (int z = 0; z < t.sz; z++) {
            for (int x = 0; x < t.sx; x++) {
                double r = Math.hypot(x + 0.5 - t.sx / 2.0, z + 0.5 - t.sz / 2.0);
                out[x + z * t.sx] = r >= HEDGE_RADIUS && empty(t, x, z);
            }
        }
        return out;
    }

    private static boolean empty(Template t, int x, int z) {
        int idx = t.at(x, 1, z);
        return idx < 0 || t.palette.get(idx).equals("minecraft:air");
    }

    static final int CHEST = indexOf("minecraft:chest");
    static final int MOSSY = Template.WATERFALL.indexOf("minecraft:mossy_cobblestone");

    private static int indexOf(String prefix) {
        List<String> p = Template.WATERFALL.palette;
        for (int i = 0; i < p.size(); i++) {
            if (p.get(i).equals(prefix) || p.get(i).startsWith(prefix + "[")) {
                return i;
            }
        }
        return -1;
    }
}
