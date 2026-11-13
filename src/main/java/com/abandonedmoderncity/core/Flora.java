package com.abandonedmoderncity.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Nature taking over the city: varied trees (oak, birch, dark oak, spruce) with vines hanging from their crowns, a
 * ground of grass, moss and coarse dirt where bits of the old concrete still show, tall grass, ferns, bushes and
 * rubble. Everything is a pure function of the position (used by {@link Wild} and {@link Park}).
 */
public final class Flora {
    private Flora() {
    }

    public static final int OAK = 0;
    public static final int BIRCH = 1;
    public static final int DARK_OAK = 2;
    public static final int SPRUCE = 3;

    /** Whether a code is a log / leaves of the overgrowth. */
    public static boolean isLog(int c) {
        return c >= BlockCode.WILD_LOG && c < BlockCode.WILD_LOG + 4;
    }

    public static boolean isLeaves(int c) {
        return c >= BlockCode.WILD_LEAVES && c < BlockCode.WILD_LEAVES_END;
    }

    /** Leaves code of a species at a distance (1..6) from a log. */
    public static int leaves(int species, int distance) {
        return BlockCode.WILD_LEAVES + 8 * species + distance;
    }

    /** Vanilla leaves farther than this (through leaves) from a log decay. */
    public static final int LEAF_REACH = 6;

    /**
     * A tree: trunk at (x, z), {@code height} logs from the ground, a crown of {@code radius} (round, or a cone for
     * the spruce), vines hanging from the bottom of the crown. Its leaves are real ones: each knows its distance to
     * the trunk ({@code dist}, steps through leaves, as vanilla counts it), and leaves of the shape that would be more
     * than {@link #LEAF_REACH} away are left out (they would decay).
     */
    public record Tree(int x, int z, int height, int species, int radius, long seed, byte[] dist) {
        static Tree of(int x, int z, int height, int species, int radius, long seed) {
            Tree shape = new Tree(x, z, height, species, radius, seed, null);
            return new Tree(x, z, height, species, radius, seed, shape.distances());
        }

        private int side() {
            return 2 * radius + 1;
        }

        private int index(int dx, int dy, int dz) {
            return (dx + radius) + side() * ((dz + radius) + side() * dy);
        }

        /** Breadth-first search from the logs through the leaves of the shape. */
        private byte[] distances() {
            int n = side();
            int ny = height + 2;
            byte[] d = new byte[n * n * ny];
            ArrayDeque<int[]> q = new ArrayDeque<>();
            for (int dy = 1; dy <= height; dy++) {
                q.add(new int[]{0, dy, 0, 0});
            }
            int[][] step = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            while (!q.isEmpty()) {
                int[] c = q.poll();
                if (c[3] >= LEAF_REACH) {
                    continue;
                }
                for (int[] st : step) {
                    int dx = c[0] + st[0];
                    int dy = c[1] + st[1];
                    int dz = c[2] + st[2];
                    if (Math.abs(dx) > radius || Math.abs(dz) > radius || dy < 1 || dy >= ny || !shapeLeaf(dx, dy, dz)) {
                        continue;
                    }
                    int k = index(dx, dy, dz);
                    if (d[k] == 0) {
                        d[k] = (byte) (c[3] + 1);
                        q.add(new int[]{dx, dy, dz, c[3] + 1});
                    }
                }
            }
            return d;
        }

        /** Distance to the trunk of a leaf at (dx, dy, dz), 0 when there is no leaf there. */
        int distance(int dx, int dy, int dz) {
            if (Math.abs(dx) > radius || Math.abs(dz) > radius || dy < 1 || dy > height + 1) {
                return 0;
            }
            return dist[index(dx, dy, dz)];
        }

        /** Whether (dx, dy, dz) is a leaf that stays (close enough to the trunk). */
        boolean leaf(int dx, int dy, int dz) {
            return distance(dx, dy, dz) > 0;
        }
        /** Farthest column (from the trunk) the tree touches: crown plus its vines. */
        public int reach() {
            return radius + 1;
        }

        /** Highest y above the ground the tree reaches. */
        public int top() {
            return height + 1;
        }

        /** Lowest crown layer (above the ground). */
        int crownBottom() {
            return species == SPRUCE ? 3 : height - (radius >= 3 ? 3 : 2);
        }

        /** Whether (dx, dy, dz) from the base of the trunk is a leaf of the crown's shape (dy above the ground). */
        boolean shapeLeaf(int dx, int dy, int dz) {
            if (dy < crownBottom() || dy > height + 1 || dx == 0 && dz == 0 && dy <= height) {
                return false;
            }
            int ax = Math.abs(dx);
            int az = Math.abs(dz);
            if (species == SPRUCE) {
                // a cone of rings, wider every other layer
                int r = Math.min(radius, (height + 2 - dy) / 2);
                return ax + az <= r + (r > 1 ? 1 : 0) && ax <= r && az <= r;
            }
            int ly = dy - height;
            int r = ly == 1 ? Math.max(1, radius - 1) : dy == crownBottom() && radius >= 3 ? radius - 1 : radius;
            if (ax > r || az > r) {
                return false;
            }
            if (ax == r && az == r) {
                // corners: ragged
                return r <= 1 ? ly <= 0 : Hash.unit(Hash.of(seed, 0x4C46L, dx, dy * 31L + dz)) < 0.25;
            }
            // a few holes in the outer shell
            return ax < r && az < r || Hash.unit(Hash.of(seed, 0x484FL, dx, dy * 31L + dz)) >= 0.12;
        }

        /** Block code of this tree at (x, y, z) for ground surface {@code base}, else KEEP. */
        public int code(int x, int y, int z, int base) {
            int dx = x - this.x;
            int dz = z - this.z;
            int dy = y - base;
            if (Math.abs(dx) > reach() || Math.abs(dz) > reach() || dy < 1 || dy > top()) {
                return BlockCode.KEEP;
            }
            if (dx == 0 && dz == 0 && dy <= height) {
                return BlockCode.WILD_LOG + species;
            }
            int d = distance(dx, dy, dz);
            if (d > 0) {
                return leaves(species, d);
            }
            return vine(dx, dy, dz);
        }

        /** Vines hanging from the lowest crown layer, on the outside, 1 to 4 blocks long. */
        private int vine(int dx, int dy, int dz) {
            int bottom = crownBottom();
            if (dy > bottom || leaf(dx, bottom, dz)) {
                return BlockCode.KEEP;
            }
            long h = Hash.of(seed, 0x56494EL, dx, dz);
            if (Hash.unit(h) >= 0.35) {
                return BlockCode.KEEP;
            }
            int len = Hash.range(Hash.next(h, 1), 1, 4);
            if (dy < Math.max(3, bottom - len + 1)) {
                return BlockCode.KEEP;
            }
            // attached to the leaf next to it (west, east, north, south)
            int[][] d = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int k = 0; k < 4; k++) {
                if (leaf(dx + d[k][0], bottom, dz + d[k][1])) {
                    return BlockCode.VINE + k;
                }
            }
            return BlockCode.KEEP;
        }
    }

    /** Trees of an area, found by column through buckets. */
    public static final class Trees {
        private static final int BUCKET = 8;
        public final List<Tree> list;
        private final int bx0;
        private final int bz0;
        private final int nx;
        private final int nz;
        private final List<List<Tree>> buckets;
        private final int maxTop;

        Trees(List<Tree> list, int x0, int z0, int x1, int z1) {
            this.list = List.copyOf(list);
            this.bx0 = Math.floorDiv(x0, BUCKET);
            this.bz0 = Math.floorDiv(z0, BUCKET);
            this.nx = Math.floorDiv(x1, BUCKET) - bx0 + 1;
            this.nz = Math.floorDiv(z1, BUCKET) - bz0 + 1;
            this.buckets = new ArrayList<>(nx * nz);
            for (int i = 0; i < nx * nz; i++) {
                buckets.add(new ArrayList<>(2));
            }
            int top = 0;
            for (Tree t : list) {
                top = Math.max(top, t.top());
                int r = t.reach();
                for (int bz = Math.floorDiv(t.z() - r, BUCKET); bz <= Math.floorDiv(t.z() + r, BUCKET); bz++) {
                    for (int bx = Math.floorDiv(t.x() - r, BUCKET); bx <= Math.floorDiv(t.x() + r, BUCKET); bx++) {
                        int i = bx - bx0;
                        int j = bz - bz0;
                        if (i >= 0 && j >= 0 && i < nx && j < nz) {
                            buckets.get(i + j * nx).add(t);
                        }
                    }
                }
            }
            this.maxTop = top;
        }

        public int maxTop() {
            return maxTop;
        }

        private static final int[][] SIDE = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        /**
         * Log, then leaves, then vines of the trees at (x, y, z), else KEEP. A vine is kept only if it hangs on a leaf
         * or log next to it, or under the same vine (where two crowns meet, one may cut the other's vines).
         */
        public int code(int x, int y, int z, int base) {
            int c = raw(x, y, z, base);
            if (c >= BlockCode.VINE && c < BlockCode.VINE + 4) {
                int[] d = SIDE[c - BlockCode.VINE];
                int held = raw(x + d[0], y, z + d[1], base);
                boolean onWood = isLog(held) || isLeaves(held);
                if (!onWood && code(x, y + 1, z, base) != c) {
                    return BlockCode.KEEP;
                }
            }
            return c;
        }

        private int raw(int x, int y, int z, int base) {
            int i = Math.floorDiv(x, BUCKET) - bx0;
            int j = Math.floorDiv(z, BUCKET) - bz0;
            if (i < 0 || j < 0 || i >= nx || j >= nz) {
                return BlockCode.KEEP;
            }
            int best = BlockCode.KEEP;
            for (Tree t : buckets.get(i + j * nx)) {
                int c = t.code(x, y, z, base);
                if (c == BlockCode.KEEP) {
                    continue;
                }
                if (isLog(c)) {
                    return c; // a log wins
                }
                if (best == BlockCode.KEEP || isLeaves(c) && (!isLeaves(best) || c % 8 < best % 8)) {
                    best = c; // leaves before vines, the closest to a trunk first
                }
            }
            return best;
        }

        /** Whether (x, z) is a trunk column. */
        public boolean trunk(int x, int z) {
            int i = Math.floorDiv(x, BUCKET) - bx0;
            int j = Math.floorDiv(z, BUCKET) - bz0;
            if (i < 0 || j < 0 || i >= nx || j >= nz) {
                return false;
            }
            for (Tree t : buckets.get(i + j * nx)) {
                if (t.x() == x && t.z() == z) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Trees scattered over x0..x1, z0..z1 (crowns and vines stay inside), about one per {@code areaPerTree} columns,
     * trunks at least {@code spacing} apart; {@code blocked} (x, z, reach) != 0 rejects a spot.
     */
    public static Trees scatter(int x0, int z0, int x1, int z1, long seed, int areaPerTree, int spacing,
                                Blocked blocked) {
        List<Tree> out = new ArrayList<>();
        int target = (x1 - x0 + 1) * (z1 - z0 + 1) / areaPerTree;
        for (int k = 0; k < target * 8 && out.size() < target; k++) {
            long h = Hash.next(seed, k);
            double s = Hash.unit(Hash.next(h, 3));
            int species = s < 0.38 ? OAK : s < 0.62 ? BIRCH : s < 0.80 ? DARK_OAK : SPRUCE;
            boolean big = Hash.unit(Hash.next(h, 4)) < 0.3;
            int height;
            int radius;
            switch (species) {
                case BIRCH -> {
                    height = Hash.range(Hash.next(h, 2), 5, big ? 9 : 7);
                    radius = 2;
                }
                case DARK_OAK -> {
                    height = Hash.range(Hash.next(h, 2), 4, 6);
                    radius = big ? 4 : 3;
                }
                case SPRUCE -> {
                    height = Hash.range(Hash.next(h, 2), big ? 9 : 6, big ? 12 : 8);
                    radius = big ? 3 : 2;
                }
                default -> {
                    height = big ? Hash.range(Hash.next(h, 2), 7, 9) : Hash.range(Hash.next(h, 2), 4, 6);
                    radius = big ? 3 : 2;
                }
            }
            int reach = radius + 1;
            if (x1 - x0 < 2 * reach || z1 - z0 < 2 * reach) {
                continue;
            }
            int x = Hash.range(h, x0 + reach, x1 - reach);
            int z = Hash.range(Hash.next(h, 1), z0 + reach, z1 - reach);
            if (blocked != null && blocked.test(x, z, reach)) {
                continue;
            }
            boolean free = true;
            for (Tree t : out) {
                if (Math.abs(t.x() - x) < spacing && Math.abs(t.z() - z) < spacing) {
                    free = false;
                    break;
                }
            }
            if (free) {
                out.add(Tree.of(x, z, height, species, radius, Hash.next(h, 5)));
            }
        }
        return new Trees(out, x0, z0, x1, z1);
    }

    /** A spot test for {@link #scatter}. */
    @FunctionalInterface
    public interface Blocked {
        boolean test(int x, int z, int reach);
    }

    // ------------------------------------------------------------------ ground and plants

    /** Smooth value noise in [0, 1) with features about {@code cell} blocks wide. */
    static double noise(long seed, long salt, double x, double z, double cell) {
        double fx = x / cell;
        double fz = z / cell;
        int ix = (int) Math.floor(fx);
        int iz = (int) Math.floor(fz);
        double tx = fx - ix;
        double tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = Hash.unit(Hash.of(seed, salt, ix, iz));
        double b = Hash.unit(Hash.of(seed, salt, ix + 1, iz));
        double c = Hash.unit(Hash.of(seed, salt, ix, iz + 1));
        double d = Hash.unit(Hash.of(seed, salt, ix + 1, iz + 1));
        return (a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz;
    }

    /**
     * Ground of an overgrown yard at (x, z): mostly grass, patches of moss and coarse dirt, and patches where the old
     * concrete (the city ground) still shows.
     */
    public static int ground(long seed, int x, int z) {
        if (noise(seed, 0x434FL, x, z, 9) > 0.70) {
            return BlockCode.CITY_GROUND;
        }
        if (noise(seed, 0x4D4FL, x, z, 7) > 0.72) {
            return BlockCode.MOSS_BLOCK;
        }
        if (noise(seed, 0x4344L, x, z, 6) > 0.74) {
            return BlockCode.COARSE_DIRT;
        }
        return BlockCode.LAWN;
    }

    static boolean soil(int ground) {
        return ground == BlockCode.LAWN || ground == BlockCode.MOSS_BLOCK || ground == BlockCode.COARSE_DIRT;
    }

    /**
     * Plants and rubble standing on {@code ground} at (x, z), {@code dy} above it (1 or 2); KEEP elsewhere. Tall grass
     * and large ferns take two blocks, rubble piles one or two.
     */
    public static int plant(long seed, int x, int z, int dy, int ground, double rubbleChance) {
        if (dy < 1 || dy > 2) {
            return BlockCode.KEEP;
        }
        return plantAbove(seed, x, z, dy, ground, rubbleChance);
    }

    /**
     * The ground block under {@link #plant}: the log of a bush (hidden in the ground, under its leaves), else the
     * given ground.
     */
    public static int groundUnder(long seed, int x, int z, int ground, double rubbleChance) {
        return isLeaves(plantAbove(seed, x, z, 1, ground, rubbleChance)) ? BlockCode.WILD_LOG + bushSpecies(seed, x, z) : ground;
    }

    private static int bushSpecies(long seed, int x, int z) {
        return Hash.unit(Hash.next(Hash.of(seed, 0x4255L, x, z), 1)) < 0.5 ? OAK : DARK_OAK;
    }

    private static int plantAbove(long seed, int x, int z, int dy, int ground, double rubbleChance) {
        double r = Hash.unit(Hash.of(seed, 0x504CL, x, z));
        // rubble: on the old concrete mostly, now and then in the grass
        double rubble = ground == BlockCode.CITY_GROUND ? rubbleChance * 4 : rubbleChance;
        if (r < rubble) {
            long h = Hash.of(seed, 0x5255L, x, z);
            double k = Hash.unit(h);
            boolean slab = k < 0.18;
            boolean two = !slab && Hash.unit(Hash.next(h, 1)) < 0.3;
            if (dy == 2) {
                return two ? BlockCode.RUBBLE + Hash.range(Hash.next(h, 2), 0, 1) : BlockCode.KEEP;
            }
            return slab ? BlockCode.MOSSY_SLAB : BlockCode.RUBBLE + (int) (k * BlockCode.RUBBLES) % BlockCode.RUBBLES;
        }
        if (!soil(ground)) {
            return BlockCode.KEEP; // nothing grows on the bare concrete
        }
        double p = (r - rubble) / (1 - rubble);
        if (ground == BlockCode.MOSS_BLOCK && p < 0.45) {
            return dy == 1 ? BlockCode.ROOF_MOSS : BlockCode.KEEP;
        }
        if (p < 0.36) {
            return dy == 1 ? BlockCode.SHRUB : BlockCode.KEEP;
        }
        if (p < 0.50) {
            return dy == 1 ? BlockCode.TALL_GRASS_LOWER : BlockCode.TALL_GRASS_UPPER;
        }
        if (p < 0.57) {
            return dy == 1 ? BlockCode.FERN : BlockCode.KEEP;
        }
        if (p < 0.61) {
            return dy == 1 ? BlockCode.LARGE_FERN_LOWER : BlockCode.LARGE_FERN_UPPER;
        }
        if (p < 0.645) {
            // a bush: leaves on a log hidden in the ground, so they do not decay (sometimes two blocks high)
            long h = Hash.of(seed, 0x4255L, x, z);
            boolean two = Hash.unit(h) < 0.25;
            int species = Hash.unit(Hash.next(h, 1)) < 0.5 ? OAK : DARK_OAK;
            return dy == 1 ? leaves(species, 1) : two ? leaves(species, 2) : BlockCode.KEEP;
        }
        return BlockCode.KEEP;
    }
}
