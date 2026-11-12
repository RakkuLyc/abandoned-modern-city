package com.abandonedmoderncity.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Where the leaves completing the tree crowns cut by the edge of a structure's template go (asked by the user,
 * 13 nov 2026; {@link Structures.Def#overhangs}): they hang out of the structure's lot. Over a street or sidewalk
 * only from {@link Params#CLEARANCE} + 1 blocks over its surface up (above the room kept free for vehicles), never
 * over a highway wall; over the core of the block (or a park) where that lot plans nothing; never into another
 * structure. The world places them only where there is air.
 */
public final class Crowns {
    private Crowns() {
    }

    /** How far out of its structure a completed crown can reach (twice the crown radius). */
    public static final int REACH = 2 * Structures.CROWN_RADIUS;

    /** Packs (x, y, z) like Minecraft's {@code BlockPos.asLong}. */
    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    /** The planned crown leaves whose column is in chunk (cx, cz): packed position -> block code. */
    public static Map<Long, Integer> forChunk(WorldPlan plan, int cx, int cz) {
        return forChunk(plan, cx, cz, null);
    }

    /**
     * The same; {@code why}, when given, counts every crown leaf near the chunk by what decided it ("street", "core",
     * "too low over a street", "another structure", "planned by the core", ...).
     */
    public static Map<Long, Integer> forChunk(WorldPlan plan, int cx, int cz, Map<String, Integer> why) {
        Map<Long, Integer> out = new HashMap<>();
        int bx = cx << 4;
        int bz = cz << 4;
        ChunkPlan cp = plan.chunk(cx, cz);
        if (!cp.any) {
            return out;
        }
        // the city whose area covers the chunk (citiesIn wants the city's center inside the rectangle)
        City near = plan.cityNear(bx + 8, bz + 8, 16 + REACH);
        for (City city : near == null ? java.util.List.<City>of() : java.util.List.of(near)) {
            int n = city.gridHalf();
            int base = city.level - 1;
            for (int bj = -n; bj <= n; bj++) {
                for (int bi = -n; bi <= n; bi++) {
                    for (Lot lot : city.lots(bi, bj)) {
                        if (lot.building == null || lot.x1 + REACH < bx || lot.x0 - REACH > bx + 15
                                || lot.z1 + REACH < bz || lot.z0 - REACH > bz + 15) {
                            continue;
                        }
                        for (int[] o : lot.overhangs()) {
                            int x = o[0];
                            int z = o[2];
                            if (x >> 4 != cx || z >> 4 != cz) {
                                continue;
                            }
                            int y = base + o[1];
                            int i = ChunkPlan.idx(x & 15, z & 15);
                            String r;
                            if (cp.kind[i] == ChunkPlan.ROAD) {
                                r = ChunkPlan.walled(cp.zone[i]) ? "highway wall"
                                        : y > ChunkPlan.surfaceY(cp.h2[i]) + Params.CLEARANCE ? "street" : "too low over a street";
                            } else if (cp.kind[i] == ChunkPlan.LOT) {
                                Lot at = cp.lot[i];
                                r = at == null ? "yard" : at.building != null ? "another structure"
                                        : at.code(x, y, z, cp.h2[i] / 2 - 1) == BlockCode.KEEP ? "core" : "planned by the core";
                            } else {
                                r = "kind " + cp.kind[i];
                            }
                            if (why != null) {
                                why.merge(r, 1, Integer::sum);
                            }
                            if (r.equals("street") || r.equals("core")) {
                                out.putIfAbsent(pack(x, y, z), o[3]);
                            }
                        }
                    }
                }
            }
        }
        return out;
    }
}
