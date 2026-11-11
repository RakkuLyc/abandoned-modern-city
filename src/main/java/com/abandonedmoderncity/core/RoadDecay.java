package com.abandonedmoderncity.core;

/**
 * Abandoned roads (asked by the user, 12 nov 2026): about 30% of the road columns are taken over, in patches and
 * cracks that are a pure function of the position (and the world seed), on city streets, sidewalks, highways and
 * bridges (not in tunnels, on the half-slab steps of ramps or under the highway walls); the other columns get a worn
 * texture ({@link #texture}). A taken column
 * keeps its height (no step for vehicles); only plants without collision grow on it:
 * <ul>
 *   <li>grass ({@code grass_block}) with short grass, tall grass or a fern;</li>
 *   <li>moss ({@code moss_block}), sometimes with short grass (no moss carpet: it has a collision box);</li>
 *   <li>coarse dirt or gravel;</li>
 *   <li>cracked asphalt: blackstone or cobbled deepslate on the lanes, cobblestone on the sidewalks.</li>
 * </ul>
 */
public final class RoadDecay {
    private RoadDecay() {
    }

    /** Patches: value noise over cells of this size above {@link #PATCH}; cracks: single columns at {@link #CRACK}. */
    static final double PATCH_CELL = 5;
    static final double PATCH = 0.66;
    static final double CRACK = 0.06;
    private static final long SALT = 0x524F4144L;

    /** Whether a road column with this zone and structure can be taken over (bridges too, since 12 nov 2026). */
    public static boolean eligible(byte zone, byte struct, boolean slab) {
        return !slab && struct != Profile.TUNNEL && zone != ChunkPlan.PARAPET;
    }

    /** Worn variants of the road surface: lanes (polished deepslate first), sidewalks (smooth stone first). */
    public static final int LANE_VARIANTS = 7;
    public static final int SIDEWALK_VARIANTS = 5;
    public static final int SLAB_VARIANTS = 4;
    /** Share of the columns of a patch of worn texture, and of single worn columns; share of worn markings. */
    static final double WORN_PATCH = 0.55;
    static final double WORN_SINGLE = 0.08;
    static final double WORN_MARK = 0.20;

    /**
     * Worn texture of a road column that is not taken over: patches and single columns of other dark stones in the
     * lanes ({@link BlockCode#LANE_TEX} + variant: polished deepslate, polished blackstone, cobbled deepslate,
     * deepslate tiles, cracked deepslate tiles, blackstone, smooth basalt), of other light stones on the sidewalks
     * ({@link BlockCode#SIDEWALK_TEX} + variant: smooth stone, andesite, cracked stone bricks, stone bricks,
     * cobblestone), the same in slabs on the ramps; a fifth of the markings worn off. KEEP: the plain marking or the
     * wall column.
     */
    public static int texture(long seed, int x, int z, byte zone) {
        return texture(seed, x, z, zone, false);
    }

    public static int texture(long seed, int x, int z, byte zone, boolean slab) {
        boolean mark = zone == ChunkPlan.MARK || zone == ChunkPlan.MARK_HIGHWAY || zone == ChunkPlan.CROSSWALK;
        if (zone == ChunkPlan.PARAPET || mark && Hash.unit(Hash.of(seed, SALT + 6, x, z)) >= WORN_MARK) {
            return BlockCode.KEEP;
        }
        boolean lane = mark || zone == ChunkPlan.LANE;
        int n = slab ? SLAB_VARIANTS : lane ? LANE_VARIANTS : SIDEWALK_VARIANTS;
        int variant = 0;
        boolean patch = Flora.noise(seed, SALT + 7, x, z, 4) > WORN_PATCH;
        if (patch || Hash.unit(Hash.of(seed, SALT + 8, x, z)) < WORN_SINGLE) {
            // a patch mostly of one stone, a single column of any
            double k = patch ? Flora.noise(seed, SALT + 9, x, z, 7) : Hash.unit(Hash.of(seed, SALT + 10, x, z));
            variant = 1 + Math.min(n - 2, (int) (k * (n - 1)));
        }
        int base = slab ? (lane ? BlockCode.LANE_SLAB_TEX : BlockCode.SIDEWALK_SLAB_TEX)
                : lane ? BlockCode.LANE_TEX : BlockCode.SIDEWALK_TEX;
        return base + variant;
    }

    /** Whether (x, z) is taken over (the column being eligible). */
    static boolean taken(long seed, int x, int z) {
        return Flora.noise(seed, SALT, x, z, PATCH_CELL) > PATCH || Hash.unit(Hash.of(seed, SALT + 1, x, z)) < CRACK;
    }

    /** Surface block code of a taken column, or KEEP for the normal road surface. */
    public static int surface(long seed, int x, int z, byte zone, byte struct, boolean slab) {
        if (!eligible(zone, struct, slab) || !taken(seed, x, z)) {
            return BlockCode.KEEP;
        }
        // the kind changes over a larger scale, so a patch is mostly of one kind
        double k = Flora.noise(seed, SALT + 2, x, z, 9);
        long h = Hash.of(seed, SALT + 3, x, z);
        if (k < 0.36) {
            return BlockCode.LAWN;
        }
        if (k < 0.52) {
            return BlockCode.MOSS_BLOCK;
        }
        if (k < 0.70) {
            return Hash.unit(h) < 0.6 ? BlockCode.COARSE_DIRT : BlockCode.GRAVEL;
        }
        boolean sidewalk = zone == ChunkPlan.SIDEWALK || zone == ChunkPlan.SHOULDER;
        if (sidewalk) {
            return BlockCode.RUBBLE + 1; // cobblestone
        }
        return Hash.unit(h) < 0.5 ? BlockCode.BLACKSTONE : BlockCode.COBBLED_DEEPSLATE;
    }

    /** Plant {@code dy} (1 or 2) above a taken column whose surface is {@code surface}, else KEEP. */
    public static int plant(long seed, int x, int z, int dy, int surface) {
        if (dy < 1 || dy > 2) {
            return BlockCode.KEEP;
        }
        double r = Hash.unit(Hash.of(seed, SALT + 4, x, z));
        if (surface == BlockCode.MOSS_BLOCK) {
            // no moss carpet: it has a collision box (1/16 block), a bump for vehicles
            return dy == 1 && r < 0.30 ? BlockCode.SHRUB : BlockCode.KEEP;
        }
        if (surface != BlockCode.LAWN) {
            return BlockCode.KEEP;
        }
        if (r < 0.42) {
            return dy == 1 ? BlockCode.SHRUB : BlockCode.KEEP;
        }
        if (r < 0.60) {
            return dy == 1 ? BlockCode.TALL_GRASS_LOWER : BlockCode.TALL_GRASS_UPPER;
        }
        if (r < 0.70) {
            return dy == 1 ? BlockCode.FERN : BlockCode.KEEP;
        }
        return BlockCode.KEEP;
    }

    /** Gaps 1 to 3 blocks long in the walls of the bridges (value noise over cells of 2.5 above this). */
    static final double WALL_GAP = 0.80;

    /** Whether the wall of a bridge edge column at (x, z) has fallen. */
    public static boolean wallGap(long seed, int x, int z) {
        return Flora.noise(seed, SALT + 11, x, z, 2.5) > WALL_GAP;
    }

    /** Whether the wall of a bridge edge column at (x, z) is mossy (in stretches, about half of them). */
    public static boolean mossyWall(long seed, int x, int z) {
        return Flora.noise(seed, SALT + 12, x, z, 6) > 0.5 || Hash.unit(Hash.of(seed, SALT + 13, x, z)) < 0.15;
    }

    /** Stones of the bridges' slab and pillars: mossy stone bricks, cracked stone bricks, mossy cobblestone, stone bricks. */
    public static final int BRIDGE_STONES = 4;

    /** Stone (0..3) of the bridge slab or pillar block at (x, y, z), in patches. */
    public static int bridgeStone(long seed, int x, int y, int z) {
        double k = Flora.noise(seed, SALT + 14, x + 0.37 * y, z - 0.29 * y, 3);
        if (Hash.unit(Hash.of(seed, SALT + 15, x, y * 31L + z)) < 0.2) {
            k = Hash.unit(Hash.of(seed, SALT + 16, x, y * 31L + z));
        }
        return Math.min(BRIDGE_STONES - 1, (int) (k * BRIDGE_STONES));
    }

    /** Share of the bridge edge columns from which a vine hangs down to the ground (asked by the user). */
    public static final double BRIDGE_VINES = 0.35;

    /** Whether a vine strand hangs at (x, z), just outside a bridge edge. */
    public static boolean bridgeVine(long seed, int x, int z) {
        return Hash.unit(Hash.of(seed, SALT + 5, x, z)) < BRIDGE_VINES;
    }
}
