package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The overgrowth of the blocks and of the parks: plants on soil, vines hanging on something, fountain untouched. */
class FloraTest {
    static final int BASE = 64;

    private static boolean leafOrLog(int c) {
        return Flora.isLog(c) || Flora.isLeaves(c) || c >= BlockCode.TEMPLATE && c < BlockCode.STRUCT;
    }

    private static boolean soil(int c) {
        return c == BlockCode.LAWN || c == BlockCode.MOSS_BLOCK || c == BlockCode.COARSE_DIRT || c == BlockCode.GRASS;
    }

    /** Checks every column of a lot; returns counts of {trees' logs, vines, plants, rubble}. */
    private static long[] check(Lot lot) {
        long[] n = new long[4];
        int top = BASE + lot.heightAboveBase() + 1;
        int[][] d = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int z = lot.z0; z <= lot.z1; z++) {
            for (int x = lot.x0; x <= lot.x1; x++) {
                int ground = lot.code(x, BASE, z, BASE);
                for (int y = BASE + 1; y <= top; y++) {
                    int c = lot.code(x, y, z, BASE);
                    int below = lot.code(x, y - 1, z, BASE);
                    String at = x + "," + y + "," + z;
                    if (c >= BlockCode.VINE && c < BlockCode.VINE + 4) {
                        int k = c - BlockCode.VINE;
                        int held = lot.code(x + d[k][0], y, z + d[k][1], BASE);
                        int above = lot.code(x, y + 1, z, BASE);
                        assertTrue(leafOrLog(held) || above == c, "vine hanging on nothing at " + at);
                        n[1]++;
                    } else if (c == BlockCode.TALL_GRASS_LOWER || c == BlockCode.LARGE_FERN_LOWER || c == BlockCode.SHRUB
                            || c == BlockCode.FERN || c == BlockCode.ROOF_MOSS
                            || c >= BlockCode.FLOWER && c < BlockCode.FLOWER + BlockCode.FLOWERS) {
                        if (c == BlockCode.SHRUB && below == BlockCode.KEEP) {
                            continue; // a missing ring slab: grass on the lawn below
                        }
                        assertEquals(BASE + 1, y, "plant off the ground at " + at);
                        assertTrue(soil(ground), "plant on " + ground + " at " + at);
                        if (c == BlockCode.TALL_GRASS_LOWER) {
                            assertEquals(BlockCode.TALL_GRASS_UPPER, lot.code(x, y + 1, z, BASE), at);
                        }
                        if (c == BlockCode.LARGE_FERN_LOWER) {
                            assertEquals(BlockCode.LARGE_FERN_UPPER, lot.code(x, y + 1, z, BASE), at);
                        }
                        n[2]++;
                    } else if (c == BlockCode.TALL_GRASS_UPPER) {
                        assertEquals(BlockCode.TALL_GRASS_LOWER, below, at);
                    } else if (c == BlockCode.LARGE_FERN_UPPER) {
                        assertEquals(BlockCode.LARGE_FERN_LOWER, below, at);
                    } else if (c >= BlockCode.RUBBLE && c < BlockCode.RUBBLE + BlockCode.RUBBLES) {
                        assertTrue(y == BASE + 1 || below >= BlockCode.RUBBLE && below < BlockCode.RUBBLE + BlockCode.RUBBLES,
                                "floating rubble at " + at);
                        n[3]++;
                    } else if (c >= BlockCode.WILD_LOG && c < BlockCode.WILD_LOG + 4) {
                        n[0]++;
                    } else if (Flora.isLeaves(c)) {
                        // real leaves: distance 1..6, and a neighbour one step closer to a log (as vanilla checks)
                        int dist = (c - BlockCode.WILD_LEAVES) % 8;
                        assertTrue(dist >= 1 && dist <= Flora.LEAF_REACH, "leaf distance " + dist + " at " + at);
                        boolean ok = false;
                        int[][] six = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                        for (int[] q : six) {
                            int nc = lot.code(x + q[0], y + q[1], z + q[2], BASE);
                            ok |= Flora.isLog(nc) || Flora.isLeaves(nc) && (nc - BlockCode.WILD_LEAVES) % 8 <= dist - 1;
                        }
                        assertTrue(ok, "leaf cut off from the wood at " + at);
                    }
                }
            }
        }
        return n;
    }

    @Test
    void overgrownBlocksAreConsistent() {
        long[] total = new long[4];
        for (long seed = 0; seed < 6; seed++) {
            Lot wild = Lot.wild(0, 0, 139, 89, Hash.of(seed, 3L, 0L), List.of());
            assertTrue(wild.wild.trees.list.size() > 120, "few trees: " + wild.wild.trees.list.size());
            for (Flora.Tree t : wild.wild.trees.list) {
                assertTrue(t.x() - t.reach() >= 0 && t.x() + t.reach() <= 139 && t.z() - t.reach() >= 0
                        && t.z() + t.reach() <= 89, "crown out of the block");
            }
            long[] n = check(wild);
            for (int k = 0; k < 4; k++) {
                total[k] += n[k];
            }
            if (seed == 0) {
                render(wild, "wild_block.png");
            }
        }
        assertTrue(total[1] > 0 && total[2] > 0 && total[3] > 0, "vines, plants, rubble: " + java.util.Arrays.toString(total));
        // structures keep the trees out
        Lot building = null;
        Lot wild = Lot.wild(0, 0, 139, 89, 7, List.of(Lot.park(10, 10, 50, 40, 1)));
        for (Flora.Tree t : wild.wild.trees.list) {
            assertTrue(t.x() + t.reach() < 9 || t.x() - t.reach() > 51 || t.z() + t.reach() < 9 || t.z() - t.reach() > 41,
                    "tree into a structure at " + t.x() + "," + t.z());
        }
        assertEquals(null, building);
    }

    @Test
    void parksAreOvergrownAndKeepTheirFountain() {
        Template t = Template.WATERFALL;
        long vines = 0;
        for (long seed = 0; seed < 6; seed++) {
            Lot park = Lot.park(0, 0, 139, 89, Hash.of(seed, 5L, 0L));
            check(park);
            Park p = park.park;
            // every fountain block is there; only air positions may hold a vine
            int ox = 70 - t.sx / 2;
            int oz = 45 - t.sz / 2;
            for (int y = 0; y < t.sy; y++) {
                for (int z = 0; z < t.sz; z++) {
                    for (int x = 0; x < t.sx; x++) {
                        int idx = t.at(x, y, z);
                        boolean inside = Math.hypot(x + 0.5 - t.sx / 2.0, z + 0.5 - t.sz / 2.0) < Park.RING_IN;
                        if (!inside || idx < 0 || t.palette.get(idx).equals("minecraft:air")) {
                            continue; // the template corners outside the ring were never placed
                        }
                        int c = park.code(ox + x, BASE + y, oz + z, BASE);
                        boolean chestSwap = idx == Park.CHEST && !p.chest && c == BlockCode.TEMPLATE + Park.MOSSY;
                        boolean ringSlab = y == 1 && (c == BlockCode.MOSSY_SLAB || c == BlockCode.MOSSY_COBBLE_SLAB);
                        assertTrue(c == BlockCode.TEMPLATE + idx || chestSwap || ringSlab,
                                "fountain block changed at " + x + "," + y + "," + z + ": " + c);
                    }
                }
            }
            for (int y = 1; y < t.sy; y++) {
                for (int z = 0; z < t.sz; z++) {
                    for (int x = 0; x < t.sx; x++) {
                        int c = park.code(ox + x, BASE + y, oz + z, BASE);
                        if (c >= BlockCode.VINE && c < BlockCode.VINE + 4) {
                            vines++;
                        }
                    }
                }
            }
            if (seed == 0) {
                render(park, "wild_park.png");
            }
        }
        assertTrue(vines > 0, "no vines on the fountains");
    }

    /** Top view, written to build/test-renders. */
    private static void render(Lot lot, String name) {
        int w = lot.x1 - lot.x0 + 1;
        int h = lot.z1 - lot.z0 + 1;
        int s = 4;
        BufferedImage img = new BufferedImage(w * s, h * s, BufferedImage.TYPE_INT_RGB);
        for (int z = lot.z0; z <= lot.z1; z++) {
            for (int x = lot.x0; x <= lot.x1; x++) {
                int rgb = color(lot, x, z);
                for (int a = 0; a < s; a++) {
                    for (int b = 0; b < s; b++) {
                        img.setRGB((x - lot.x0) * s + a, (z - lot.z0) * s + b, rgb);
                    }
                }
            }
        }
        try {
            File dir = new File("build/test-renders");
            dir.mkdirs();
            ImageIO.write(img, "png", new File(dir, name));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static int color(Lot lot, int x, int z) {
        for (int y = BASE + lot.heightAboveBase(); y > BASE; y--) {
            int c = lot.code(x, y, z, BASE);
            if (Flora.isLeaves(c)) {
                int k = (c - BlockCode.WILD_LEAVES) / 8;
                return k == 1 ? 0x5F963C : k == 3 ? 0x235537 : k == 2 ? 0x1E5A1E : 0x327D2D;
            }
            if (c >= BlockCode.WILD_LOG && c < BlockCode.WILD_LOG + 4) {
                return 0x644B2D;
            }
            if (c >= BlockCode.VINE && c < BlockCode.VINE + 4) {
                return 0x2E8B22;
            }
            if (c >= BlockCode.RUBBLE && c < BlockCode.RUBBLE + BlockCode.RUBBLES) {
                return 0x787873;
            }
            if (c == BlockCode.MOSSY_SLAB || c == BlockCode.MOSSY_COBBLE_SLAB) {
                return 0x96A58C;
            }
            if (c == BlockCode.RING_SLAB) {
                return 0xD7D7D7;
            }
            if (c >= BlockCode.TEMPLATE && c < BlockCode.STRUCT) {
                String st = Template.WATERFALL.palette.get(c - BlockCode.TEMPLATE);
                if (!st.contains("air")) {
                    return st.contains("water") ? 0x466EDC : st.contains("leaves") ? 0x287832 : 0x787D73;
                }
            }
            if (c >= BlockCode.FLOWER && c < BlockCode.FLOWER + BlockCode.FLOWERS) {
                return 0xE65A78;
            }
            if (c == BlockCode.TALL_GRASS_UPPER || c == BlockCode.LARGE_FERN_UPPER) {
                return 0x78B446;
            }
        }
        int g = lot.code(x, BASE, z, BASE);
        if (Flora.isLog(g)) {
            return 0x327D2D;
        }
        return switch (g) {
            case BlockCode.CITY_GROUND -> 0x9B9B96;
            case BlockCode.MOSS_BLOCK -> 0x557D2D;
            case BlockCode.COARSE_DIRT -> 0x7D644B;
            case BlockCode.PATH -> 0x967346;
            default -> 0x69A54B;
        };
    }
}
