package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * {@link #BASE_SIZE} wide; larger parks scale them up (the 180 x 120 blocks: x 1.76).
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
    /** About one tree per this many columns. */
    static final int TREE_AREA = 60;
    /** Share of path columns taken over by grass, moss or coarse dirt. */
    static final double PATH_GAPS = 0.30;
    /** Share of the ring slabs that are missing (the others: half mossy stone bricks, half mossy cobblestone). */
    static final double RING_GAPS = 0.10;
    /**
     * Share of the fountain's columns with a place for a vine (air next to its leaves or stone) that get a strand,
     * hanging from the highest such place, {@link #VINE_MIN} to {@link #VINE_MAX} blocks long.
     */
    static final double FOUNTAIN_VINES = 0.55;
    static final int VINE_MIN = 2;
    static final int VINE_MAX = 6;
    static final double RUBBLE = 0.006;

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
    final Flora.Trees trees;
    /** Benches: column (x, z) -> its block code one above the ground ({@link #benches}). */
    final Map<Long, Integer> benches;
    private int benchSites;

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
        // overgrown: trees of several species with vines, no trunk on a path, no crown over the fountain ring
        this.trees = Flora.scatter(x0, z0, x1, z1, Hash.next(seed, 5), TREE_AREA, 5,
                (x, z, reach) -> radius(x, z) - reach < RING_OUT + 1 || nearPath(x, z, 1));
        this.benches = benches();
    }

    /** Blocks of path per bench, and least distance (blocks, along x or z) between two benches. */
    static final int BENCH_EVERY = 15;
    static final int BENCH_APART = 8;
    /** Share of the benches broken: a seat and its armrest gone. */
    static final double BENCH_BROKEN = 0.20;
    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // north, east, south, west

    /**
     * Benches along the paths: two spruce stairs side by side on the edge of a path,
     * their backs away from it (who sits looks at the path), an open spruce trapdoor at each end as armrests; about one
     * per {@link #BENCH_EVERY} blocks of path, {@link #BENCH_APART}+ apart, never on a path, the fountain's ring, a
     * trunk or under a crown; {@link #BENCH_BROKEN} of them broken.
     */
    private Map<Long, Integer> benches() {
        Map<Long, Integer> out = new HashMap<>();
        int pathCells = 0;
        List<int[]> sites = new ArrayList<>();
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                if (path(x, z)) {
                    pathCells++;
                    continue;
                }
                for (int k = 0; k < 4; k++) {
                    // the path in direction k; the bench runs along a = k turned 90 degrees: c - a, c, c + a, c + 2a
                    int ax = -DIRS[k][1];
                    int az = DIRS[k][0];
                    boolean ok = true;
                    for (int s = -1; s <= 2 && ok; s++) {
                        int bx = x + s * ax;
                        int bz = z + s * az;
                        ok = bx >= x0 && bx <= x1 && bz >= z0 && bz <= z1 && !path(bx, bz)
                                && path(bx + DIRS[k][0], bz + DIRS[k][1]) && !path(bx - DIRS[k][0], bz - DIRS[k][1])
                                && radius(bx, bz) >= RING_OUT + 1 && !trees.trunk(bx, bz)
                                && trees.code(bx, 1, bz, 0) == BlockCode.KEEP && trees.code(bx, 2, bz, 0) == BlockCode.KEEP;
                    }
                    if (ok) {
                        sites.add(new int[]{x, z, k});
                    }
                }
            }
        }
        // the path is 3 wide: its length is about a third of its columns
        int want = Math.max(1, (int) Math.round(pathCells / 3.0 / BENCH_EVERY));
        sites.sort((p, q) -> Long.compare(Hash.of(seed, 0x42454EL, p[0], p[1] * 4L + p[2]),
                Hash.of(seed, 0x42454EL, q[0], q[1] * 4L + q[2])));
        List<int[]> chosen = new ArrayList<>();
        for (int[] s : sites) {
            if (chosen.size() >= want) {
                break;
            }
            boolean far = true;
            for (int[] c : chosen) {
                far &= Math.max(Math.abs(c[0] - s[0]), Math.abs(c[1] - s[1])) >= BENCH_APART;
            }
            if (!far) {
                continue;
            }
            chosen.add(s);
            benchSites++;
            int k = s[2];
            int ax = -DIRS[k][1];
            int az = DIRS[k][0];
            // the seat's back away from the path: facing the opposite of k
            int back = (k + 2) & 3;
            // which way along a: a = DIRS[(k + 1) & 3] (k turned 90 degrees clockwise)
            int along = (k + 1) & 3;
            boolean broken = Hash.unit(Hash.of(seed, 0x42524BL, s[0], s[1])) < BENCH_BROKEN;
            out.put(key(s[0] - ax, s[1] - az), BlockCode.BENCH_ARM + ((along + 2) & 3));
            out.put(key(s[0], s[1]), BlockCode.BENCH_SEAT + back);
            if (!broken) {
                out.put(key(s[0] + ax, s[1] + az), BlockCode.BENCH_SEAT + back);
                out.put(key(s[0] + 2 * ax, s[1] + 2 * az), BlockCode.BENCH_ARM + along);
            }
        }
        return out;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /** Number of benches of this park (for the tests). */
    int benchCount() {
        return benchSites;
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

    public int heightAboveBase() {
        return Math.max(Template.WATERFALL.sy, trees.maxTop());
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
            // the ring and everything between it and the fountain's hedge: smooth stone slabs, some mossy, some gone
            // (no grass where a slab is missing over the fountain's own floor)
            int slab = ringSlab(x, z);
            return slab == BlockCode.SHRUB && inFountain(x, z) ? BlockCode.KEEP : slab;
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
            int col = (x - ox) + (z - oz) * t.sx;
            if (dy > 0 && dy <= VINE_TOP[col] && dy >= VINE_LOW[col]) {
                long hv = Hash.of(seed, 0x46564EL, x, z);
                if (Hash.unit(hv) < FOUNTAIN_VINES && dy > VINE_TOP[col] - Hash.range(Hash.next(hv, 1), VINE_MIN, VINE_MAX)) {
                    return BlockCode.VINE + VINE_TOP_FACE[col];
                }
            }
            if (idx < 0) {
                return dy == 0 ? BlockCode.LAWN : BlockCode.KEEP;
            }
            if (!chest && idx == CHEST) {
                idx = MOSSY;
            }
            if (dy == 0) {
                // the stone floor around the basin turns mossy (the fountain's own blocks stay)
                idx = FLOOR_SWAP[idx];
            }
            return BlockCode.TEMPLATE + idx;
        }
        Integer bench = benches.get(key(x, z));
        if (bench != null && dy >= 1) {
            // a bench: its block; above it a crown of a tree stays, no plant grows through it
            return dy == 1 ? bench : trees.code(x, y, z, base);
        }
        boolean path = path(x, z);
        // many path columns are taken over: grass, moss or coarse dirt, with weeds
        boolean gap = path && rnd(x, z, 0x50L) < PATH_GAPS;
        int ground = path && !gap ? BlockCode.PATH : gapGround(x, z, gap);
        if (dy == 0) {
            if (trees.trunk(x, z)) {
                return BlockCode.LAWN;
            }
            // the log hidden under a bush (not under a bench)
            boolean open = !(path && !gap) && !onRing(x, z) && bench == null;
            return open && Flora.isLeaves(plantAt(x, z, 1, ground, gap)) ? Flora.groundUnder(seed, x, z, ground, RUBBLE) : ground;
        }
        if (onRing(x, z)) {
            return dy == 1 ? ringSlab(x, z) : BlockCode.KEEP;
        }
        int tree = trees.code(x, y, z, base);
        if (tree != BlockCode.KEEP) {
            return tree;
        }
        if (path && !gap || dy > 2 || trees.trunk(x, z)) {
            return BlockCode.KEEP;
        }
        int plant = plantAt(x, z, dy, ground, gap);
        if (plant != BlockCode.KEEP && dy == 2 && trees.code(x, y - 1, z, base) != BlockCode.KEEP) {
            return BlockCode.KEEP;
        }
        if (dy == 1 && (plant == BlockCode.TALL_GRASS_LOWER || plant == BlockCode.LARGE_FERN_LOWER)
                && trees.code(x, y + 1, z, base) != BlockCode.KEEP) {
            return BlockCode.SHRUB;
        }
        return plant;
    }

    /** Plant (or flower, or rubble) dy above the ground of an open park column. */
    private int plantAt(int x, int z, int dy, int ground, boolean gap) {
        // a few flowers left from the old park
        if (!gap && ground == BlockCode.LAWN && rnd(x, z, 0x53L) < 0.05) {
            return dy == 1 ? BlockCode.FLOWER + Hash.range(Hash.of(seed, 0x54L, x, z), 0, BlockCode.FLOWERS - 1) : BlockCode.KEEP;
        }
        return Flora.plant(seed, x, z, dy, ground, RUBBLE);
    }

    /** Ground of a park column off the paths (or of a path column taken over). */
    private int gapGround(int x, int z, boolean gap) {
        int g = Flora.ground(seed, x, z);
        // no bare concrete in a park: it was lawn
        if (g == BlockCode.CITY_GROUND) {
            g = BlockCode.LAWN;
        }
        if (gap && g == BlockCode.LAWN && rnd(x, z, 0x51L) < 0.4) {
            g = BlockCode.COARSE_DIRT;
        }
        return g;
    }

    /** A slab of the ring around the fountain: mossy stone bricks or mossy cobblestone, or missing (over the lawn). */
    private int ringSlab(int x, int z) {
        double r = rnd(x, z, 0x52534CL);
        if (r < RING_GAPS) {
            return rnd(x, z, 0x52534DL) < 0.5 ? BlockCode.SHRUB : BlockCode.KEEP;
        }
        return r < RING_GAPS + (1 - RING_GAPS) / 2 ? BlockCode.MOSSY_SLAB : BlockCode.MOSSY_COBBLE_SLAB;
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

    /**
     * For every air position of the fountain template (x + sx * (z + sz * y)): the direction (west, east, north,
     * south) of a leaf or stone block next to it that a vine can hang on, or -1.
     */
    static final int[] VINE_FACE = vineFaces();

    private static int[] vineFaces() {
        Template t = Template.WATERFALL;
        int[] out = new int[t.sx * t.sy * t.sz];
        Arrays.fill(out, -1);
        int[][] d = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int y = 1; y < t.sy; y++) {
            for (int z = 0; z < t.sz; z++) {
                for (int x = 0; x < t.sx; x++) {
                    int idx = t.at(x, y, z);
                    if (idx >= 0 && !t.palette.get(idx).equals("minecraft:air")) {
                        continue;
                    }
                    for (int k = 0; k < 4; k++) {
                        int n = t.at(x + d[k][0], y, z + d[k][1]);
                        if (n >= 0 && holdsVine(t.palette.get(n))) {
                            out[x + t.sx * (z + t.sz * y)] = k;
                            break;
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Full blocks of the fountain a vine can hang on. */
    private static boolean holdsVine(String state) {
        String b = state.contains("[") ? state.substring(0, state.indexOf('[')) : state;
        return b.endsWith("_leaves") || b.endsWith("cobblestone") || b.endsWith("stone_bricks") || b.equals("minecraft:stone")
                || b.endsWith("_planks") || b.endsWith("_log");
    }

    /**
     * Per template column: the highest place for a vine ({@link #VINE_FACE} set; 0 if none), its direction, and the
     * lowest y (at least 1) down to which the column is air below it, so a strand hangs freely.
     */
    static final int[] VINE_TOP = new int[Template.WATERFALL.sx * Template.WATERFALL.sz];
    static final int[] VINE_TOP_FACE = new int[VINE_TOP.length];
    static final int[] VINE_LOW = new int[VINE_TOP.length];

    static {
        Template t = Template.WATERFALL;
        for (int z = 0; z < t.sz; z++) {
            for (int x = 0; x < t.sx; x++) {
                int col = x + z * t.sx;
                VINE_LOW[col] = Integer.MAX_VALUE;
                for (int y = t.sy - 1; y >= 1; y--) {
                    int f = VINE_FACE[x + t.sx * (z + t.sz * y)];
                    if (f >= 0) {
                        VINE_TOP[col] = y;
                        VINE_TOP_FACE[col] = f;
                        int low = y;
                        while (low - 1 >= 1 && air(t, x, low - 1, z)) {
                            low--;
                        }
                        VINE_LOW[col] = low;
                        break;
                    }
                }
            }
        }
    }

    private static boolean air(Template t, int x, int y, int z) {
        int idx = t.at(x, y, z);
        return idx < 0 || t.palette.get(idx).equals("minecraft:air");
    }

    static final int CHEST = indexOf("minecraft:chest");
    static final int MOSSY = Template.WATERFALL.indexOf("minecraft:mossy_cobblestone");
    static final int MOSSY_BRICKS = indexOf("minecraft:mossy_stone_bricks");

    /**
     * Palette index that replaces each entry on the fountain's floor layer (it looks as overgrown as the park): stone
     * and cobblestone become mossy cobblestone, stone bricks and cracked stone bricks mossy stone bricks; the rest
     * stays.
     * These four only lie on the floor outside the basin.
     */
    static final int[] FLOOR_SWAP = floorSwap();

    private static int[] floorSwap() {
        List<String> p = Template.WATERFALL.palette;
        int[] out = new int[p.size()];
        for (int i = 0; i < out.length; i++) {
            String b = p.get(i);
            out[i] = b.equals("minecraft:stone") || b.equals("minecraft:cobblestone") ? MOSSY
                    : b.equals("minecraft:stone_bricks") || b.equals("minecraft:cracked_stone_bricks") ? MOSSY_BRICKS : i;
        }
        return out;
    }

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
