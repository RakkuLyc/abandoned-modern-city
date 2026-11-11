package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Abandoned roads: share taken over, kinds, plants, and where nothing changes. */
class RoadDecayTest {

    @Test
    void aboutOneColumnInTenIsTakenInPatches() {
        long taken = 0;
        long total = 0;
        long neighbours = 0;
        Map<Integer, Integer> kinds = new TreeMap<>();
        for (long seed : new long[]{20261027L, 42L, -3297019724594858492L}) {
            for (int z = -400; z < 400; z++) {
                for (int x = -400; x < 400; x++) {
                    int s = RoadDecay.surface(seed, x, z, ChunkPlan.LANE, Profile.GROUND, false);
                    assertEquals(s, RoadDecay.surface(seed, x, z, ChunkPlan.LANE, Profile.GROUND, false), "deterministic");
                    total++;
                    if (s == BlockCode.KEEP) {
                        continue;
                    }
                    taken++;
                    kinds.merge(s, 1, Integer::sum);
                    if (RoadDecay.surface(seed, x + 1, z, ChunkPlan.LANE, Profile.GROUND, false) != BlockCode.KEEP) {
                        neighbours++;
                    }
                }
            }
        }
        double share = taken / (double) total;
        System.out.println("[RoadDecayTest] taken " + String.format("%.3f", share) + ", next one taken too "
                + String.format("%.2f", neighbours / (double) taken) + ", kinds " + kinds);
        assertTrue(share > 0.07 && share < 0.13, "share " + share);
        // patches: most taken columns have a taken neighbour
        assertTrue(neighbours > 0.4 * taken, "patches");
        assertTrue(kinds.containsKey(BlockCode.LAWN) && kinds.containsKey(BlockCode.MOSS_BLOCK)
                && kinds.containsKey(BlockCode.COARSE_DIRT) && kinds.containsKey(BlockCode.GRAVEL)
                && kinds.containsKey(BlockCode.BLACKSTONE) && kinds.containsKey(BlockCode.COBBLED_DEEPSLATE));
    }

    @Test
    void bridgesTunnelsRampStepsAndWallsStayClean() {
        for (int x = 0; x < 300; x++) {
            for (int z = 0; z < 300; z++) {
                assertEquals(BlockCode.KEEP, RoadDecay.surface(1, x, z, ChunkPlan.LANE, Profile.BRIDGE, false));
                assertEquals(BlockCode.KEEP, RoadDecay.surface(1, x, z, ChunkPlan.LANE, Profile.TUNNEL, false));
                assertEquals(BlockCode.KEEP, RoadDecay.surface(1, x, z, ChunkPlan.LANE, Profile.GROUND, true));
                assertEquals(BlockCode.KEEP, RoadDecay.surface(1, x, z, ChunkPlan.PARAPET, Profile.GROUND, false));
                // sidewalks crack into cobblestone, never into asphalt
                int s = RoadDecay.surface(1, x, z, ChunkPlan.SIDEWALK, Profile.GROUND, false);
                assertTrue(s != BlockCode.BLACKSTONE && s != BlockCode.COBBLED_DEEPSLATE);
            }
        }
    }

    @Test
    void plantsOnlyOnGrassAndMossAndWithoutCollision() {
        for (int x = 0; x < 400; x++) {
            for (int z = 0; z < 400; z++) {
                int s = RoadDecay.surface(7, x, z, ChunkPlan.LANE, Profile.GROUND, false);
                if (s == BlockCode.KEEP) {
                    continue;
                }
                int p1 = RoadDecay.plant(7, x, z, 1, s);
                int p2 = RoadDecay.plant(7, x, z, 2, s);
                if (s != BlockCode.LAWN && s != BlockCode.MOSS_BLOCK) {
                    assertEquals(BlockCode.KEEP, p1);
                }
                assertTrue(p1 == BlockCode.KEEP || p1 == BlockCode.SHRUB || p1 == BlockCode.TALL_GRASS_LOWER || p1 == BlockCode.FERN,
                        "plant " + p1);
                assertEquals(p1 == BlockCode.TALL_GRASS_LOWER ? BlockCode.TALL_GRASS_UPPER : BlockCode.KEEP, p2);
            }
        }
    }
}
