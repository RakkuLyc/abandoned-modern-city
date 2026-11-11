package com.abandonedmoderncity.core;

/**
 * Abandoned roads (asked by the user, 12 nov 2026): about one road column in ten is taken over, in patches and cracks
 * that are a pure function of the position (and the world seed), on city streets, sidewalks and highways on the
 * ground (not on bridges, in tunnels, on the half-slab steps of ramps or under the highway walls). A taken column
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
    static final double PATCH = 0.82;
    static final double CRACK = 0.03;
    private static final long SALT = 0x524F4144L;

    /** Whether a road column with this zone and structure can be taken over. */
    public static boolean eligible(byte zone, byte struct, boolean slab) {
        return !slab && struct == Profile.GROUND && zone != ChunkPlan.PARAPET;
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

    /** Share of the bridge edge columns from which a vine hangs down to the ground (asked by the user). */
    public static final double BRIDGE_VINES = 0.35;

    /** Whether a vine strand hangs at (x, z), just outside a bridge edge. */
    public static boolean bridgeVine(long seed, int x, int z) {
        return Hash.unit(Hash.of(seed, SALT + 5, x, z)) < BRIDGE_VINES;
    }
}
