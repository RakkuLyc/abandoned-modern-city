package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Nature taking over the structures ({@link Weathering}): what changes, what never does, and the measures reached. */
class WeatheringTest {
    private static final Set<String> SOIL = Set.of("minecraft:moss_block", "minecraft:grass_block", "minecraft:dirt",
            "minecraft:coarse_dirt", "minecraft:rooted_dirt");
    private static final Set<String> PLANTS = Set.of("minecraft:short_grass", "minecraft:fern", "minecraft:dead_bush");

    private static String block(Template t, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= t.sx || y >= t.sy || z >= t.sz) {
            return "minecraft:air";
        }
        int p = t.at(x, y, z);
        return p < 0 ? "minecraft:air" : Structures.blockOf(t.palette.get(p));
    }

    private static String state(Template t, int x, int y, int z) {
        int p = t.at(x, y, z);
        return p < 0 ? "minecraft:air" : t.palette.get(p);
    }

    @Test
    void onlyAllowedChangesAndEverythingHolds() {
        assumeFalse(Structures.ALL.isEmpty(), "no structures");
        assumeTrue(Weathering.enabled, "overgrowth off");
        Map<String, Integer> kinds = new TreeMap<>();
        for (Structures.Def d : Structures.ALL) {
            Template raw = Structures.source(d.name());
            Weathering.Result w = Weathering.apply(raw, d.ground(), d.name().hashCode());
            Weathering.Result again = Weathering.apply(Structures.source(d.name()), d.ground(), d.name().hashCode());
            Template t = w.template();
            assertEquals(raw.sx, t.sx);
            assertEquals(raw.sy, t.sy);
            assertEquals(raw.sz, t.sz);
            assertEquals(raw.banners, t.banners, d.name() + " banners");
            // the box of the structure (every block but air and structure void) does not change
            assertEquals(Arrays.toString(box(raw)), Arrays.toString(box(t)), d.name() + " box");
            int changed = 0;
            for (int y = 0; y < t.sy; y++) {
                for (int z = 0; z < t.sz; z++) {
                    for (int x = 0; x < t.sx; x++) {
                        String before = state(raw, x, y, z);
                        String after = state(t, x, y, z);
                        // the same on every load
                        assertEquals(after, state(again.template(), x, y, z), d.name() + " not deterministic");
                        if (before.equals(after)) {
                            continue;
                        }
                        changed++;
                        String b0 = Structures.blockOf(before);
                        String b1 = Structures.blockOf(after);
                        String where = d.name() + " " + x + "," + y + "," + z + " " + before + " -> " + after;
                        String kind;
                        if (b1.startsWith("minecraft:cracked_")) {
                            kind = "cracked";
                            assertEquals("minecraft:cracked_" + b0.substring(10), b1, where);
                        } else if (b1.startsWith("minecraft:mossy_")) {
                            kind = "mossy";
                            assertEquals("minecraft:mossy_" + b0.substring(10), b1, where);
                            assertEquals(before.substring(b0.length()), after.substring(b1.length()), where + " properties");
                        } else if (SOIL.contains(b1)) {
                            kind = "soil";
                            assertTrue(Weathering.mineral(b0), where);
                            assertTrue(Weathering.air(block(raw, x, y + 1, z)), where + " not open above");
                        } else if (b1.equals("minecraft:moss_carpet")) {
                            kind = "carpet";
                            assertTrue(Weathering.air(b0), where);
                            String under = block(t, x, y - 1, z);
                            assertTrue(Weathering.full(under) || SOIL.contains(under), where + " on " + under);
                        } else if (PLANTS.contains(b1)) {
                            kind = "plant";
                            assertTrue(Weathering.air(b0), where);
                            assertTrue(SOIL.contains(block(t, x, y - 1, z)), where + " on " + block(t, x, y - 1, z));
                        } else if (b1.equals("minecraft:vine")) {
                            kind = "vine";
                            assertTrue(Weathering.air(b0), where);
                            // each face holds on a full block, or on the vine above with that face; the top face on
                            // a full block
                            int faces = 0;
                            String[][] f = {{"north", "0", "-1"}, {"south", "0", "1"}, {"east", "1", "0"}, {"west", "-1", "0"}};
                            for (String[] q : f) {
                                if (after.contains(q[0] + "=true")) {
                                    faces++;
                                    String wall = block(t, x + Integer.parseInt(q[1]), y, z + Integer.parseInt(q[2]));
                                    boolean held = Weathering.full(wall) && !Weathering.soft(wall)
                                            || state(t, x, y + 1, z).startsWith("minecraft:vine")
                                            && state(t, x, y + 1, z).contains(q[0] + "=true");
                                    assertTrue(held, where + " " + q[0] + " face on " + wall);
                                }
                            }
                            if (after.contains("up=true")) {
                                faces++;
                                assertTrue(Weathering.full(block(t, x, y + 1, z)), where + " up face");
                            }
                            assertEquals(1, faces, where);
                        } else {
                            throw new AssertionError("unexpected change " + where);
                        }
                        kinds.merge(kind, 1, Integer::sum);
                    }
                }
            }
            Weathering.Report r = w.report();
            assertEquals(r.total(), changed, d.name() + " report");
        }
        System.out.println("[WeatheringTest] changes over the " + Structures.ALL.size() + " files: " + kinds);
        assertTrue(kinds.getOrDefault("vine", 0) > 0 && kinds.getOrDefault("soil", 0) > 0
                && kinds.getOrDefault("cracked", 0) > 0 && kinds.getOrDefault("carpet", 0) > 0, kinds.toString());
    }

    private static int[] box(Template t) {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1, -1};
        for (int y = 0; y < t.sy; y++) {
            for (int z = 0; z < t.sz; z++) {
                for (int x = 0; x < t.sx; x++) {
                    String n = block(t, x, y, z);
                    if (!Weathering.air(n) && !n.equals("minecraft:structure_void")) {
                        b[0] = Math.min(b[0], x);
                        b[1] = Math.min(b[1], y);
                        b[2] = Math.min(b[2], z);
                        b[3] = Math.max(b[3], x);
                        b[4] = Math.max(b[4], y);
                        b[5] = Math.max(b[5], z);
                    }
                }
            }
        }
        return b;
    }

    /** The measures of {@link Weathering} on a template: cracked share, green under a roof and open, vines per wall. */
    static double[] measures(Template t, int gy) {
        int family = 0;
        int cracked = 0;
        int coveredTops = 0;
        int coveredGreen = 0;
        int openTops = 0;
        int openGreen = 0;
        int walls = 0;
        int vines = 0;
        int[] roof = new int[t.sx * t.sz];
        Arrays.fill(roof, -1);
        for (int y = 0; y < t.sy; y++) {
            for (int z = 0; z < t.sz; z++) {
                for (int x = 0; x < t.sx; x++) {
                    if (!Weathering.soft(block(t, x, y, z))) {
                        roof[x + t.sx * z] = y;
                    }
                }
            }
        }
        for (int y = gy + 1; y < t.sy; y++) {
            for (int z = 0; z < t.sz; z++) {
                for (int x = 0; x < t.sx; x++) {
                    String b = block(t, x, y, z);
                    if (b.matches("minecraft:(stone_bricks|deepslate_bricks|deepslate_tiles|polished_blackstone_bricks|nether_bricks)")) {
                        family++;
                    } else if (b.matches("minecraft:cracked_(stone_bricks|deepslate_bricks|deepslate_tiles|polished_blackstone_bricks|nether_bricks)")) {
                        family++;
                        cracked++;
                    }
                    if (b.equals("minecraft:vine")) {
                        vines++;
                    }
                    if (Weathering.soft(b)) {
                        continue;
                    }
                    if (Weathering.soft(block(t, x + 1, y, z)) || Weathering.soft(block(t, x - 1, y, z))
                            || Weathering.soft(block(t, x, y, z + 1)) || Weathering.soft(block(t, x, y, z - 1))) {
                        walls++;
                    }
                    String above = block(t, x, y + 1, z);
                    if (y + 1 >= t.sy || !Weathering.soft(above)) {
                        continue;
                    }
                    boolean green = b.equals("minecraft:moss_block") || b.equals("minecraft:grass_block")
                            || above.matches("minecraft:(moss_carpet|short_grass|fern|dead_bush|tall_grass|large_fern)");
                    boolean candidate = green || Weathering.full(b) && Weathering.air(above);
                    if (!candidate) {
                        continue;
                    }
                    if (roof[x + t.sx * z] > y + 1) {
                        coveredTops++;
                        coveredGreen += green ? 1 : 0;
                    } else {
                        openTops++;
                        openGreen += green ? 1 : 0;
                    }
                }
            }
        }
        // vines: the share of the outer facade covered, all sides together, and the least covered side
        int[][] f = Weathering.facade(t, gy);
        int cells = 0;
        int vined = 0;
        double least = Double.NaN;
        for (int[] side : f) {
            cells += side[0];
            vined += side[1];
            if (side[0] >= 40) {
                double c = side[1] / (double) side[0];
                least = Double.isNaN(least) ? c : Math.min(least, c);
            }
        }
        return new double[]{family == 0 ? Double.NaN : cracked / (double) family,
                coveredTops == 0 ? Double.NaN : coveredGreen / (double) coveredTops,
                openTops == 0 ? Double.NaN : openGreen / (double) openTops, cells == 0 ? Double.NaN : vined / (double) cells,
                least};
    }

    @Test
    void everyStructureReachesTheMeasuresOfTheApocalypticBuilding() {
        assumeFalse(Structures.ALL.isEmpty(), "no structures");
        assumeTrue(Weathering.enabled, "overgrowth off");
        System.out.println("[WeatheringTest] structure: cracked, green under a roof, green open, facade with vines, least covered side (before -> after)");
        for (Structures.Def d : Structures.ALL) {
            Template raw = Structures.source(d.name());
            Weathering.Result w = Weathering.apply(raw, d.ground(), d.name().hashCode());
            int gy = lowest(raw) + d.ground();
            double[] a = measures(raw, gy);
            double[] b = measures(w.template(), gy);
            System.out.printf("[WeatheringTest] %-32s %s -> %s  %s%n", d.name(), fmt(a), fmt(b), w.report());
            for (int k = 0; k < 4; k++) {
                if (Double.isNaN(b[k])) {
                    continue;
                }
                // never less than before; the target reached, or as close as the candidates allow (vines: strands
                // stop at floors, so a little under)
                // (the cracked share may drop a little: brick floors that turn to moss leave the family)
                assertTrue(b[k] >= a[k] - (k == 0 || k >= 3 ? 0.03 : 1e-9), d.name() + " measure " + k + " went down");
            }
            if (!Double.isNaN(b[0])) {
                assertTrue(b[0] >= Weathering.CRACKED - 0.03 || b[0] >= a[0] - 0.02, d.name() + " cracked " + b[0]);
            }
            // vines on every side (sides of 40+ facade cells): about the target share, or more if it had more
            if (!Double.isNaN(b[4])) {
                assertTrue(b[4] >= Weathering.VINES - 0.06, d.name() + " least covered side " + b[4]);
            }
        }
        // the apocalyptic building itself
        Structures.Def ref = Structures.ALL.stream().filter(d -> d.name().equals("predio_apocaliptico_57x45")).findFirst().orElseThrow();
        Weathering.Report r = Weathering.apply(Structures.source(ref.name()), ref.ground(), ref.name().hashCode()).report();
        // it is the model of the stone and the floors (nothing there changes); the vines are the user's stronger ones
        assertEquals(0, r.cracked() + r.floors() + r.carpets() + r.plants() + r.mossy(), "predio_apocaliptico changed " + r);
        assertTrue(r.vines() > 0, "predio_apocaliptico without the stronger vines");
    }

    private static int lowest(Template t) {
        for (int y = 0; y < t.sy; y++) {
            for (int z = 0; z < t.sz; z++) {
                for (int x = 0; x < t.sx; x++) {
                    if (!Weathering.soft(block(t, x, y, z))) {
                        return y;
                    }
                }
            }
        }
        return 0;
    }

    private static String fmt(double[] m) {
        StringBuilder sb = new StringBuilder();
        for (double v : m) {
            sb.append(Double.isNaN(v) ? "   - " : String.format("%4.0f%%", 100 * v)).append(' ');
        }
        return sb.toString().trim();
    }

    @Test
    void wholeNamesAreNotTakenForParts() {
        // light_gray_concrete, sandstone and mud bricks are full stone-like blocks; glass, leaves, slabs are not
        assertTrue(Weathering.mineral("minecraft:light_gray_concrete"));
        assertTrue(Weathering.mineral("minecraft:smooth_sandstone"));
        assertTrue(Weathering.mineral("minecraft:mud_bricks"));
        assertTrue(Weathering.full("minecraft:oak_planks"));
        assertFalse(Weathering.mineral("minecraft:oak_planks"));
        assertFalse(Weathering.full("minecraft:glass"));
        assertFalse(Weathering.full("minecraft:oak_leaves"));
        assertFalse(Weathering.full("minecraft:stone_brick_slab"));
        assertFalse(Weathering.full("minecraft:chest"));
        assertFalse(Weathering.full("minecraft:white_wall_banner"));
        assertFalse(Weathering.full("minecraft:light"));
    }
}
