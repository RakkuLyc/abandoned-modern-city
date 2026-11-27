package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The structures (rotation, boxes, block codes) and the ring of them around a block. */
class RingTest {

    /**
     * Every row of {@code index.txt} gives a structure: the structure tests below fail (instead of being skipped) when
     * the templates do not load.
     */
    @Test
    void everyRowOfTheIndexIsLoaded() throws IOException {
        int rows = 0;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                RingTest.class.getResourceAsStream("/amcs/buildings/index.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                rows += !line.isBlank() && !line.startsWith("#") ? 1 : 0;
            }
        }
        assertTrue(rows > 0, "empty index.txt");
        assertEquals(rows, Structures.ALL.size(), "structures loaded from index.txt");
        assertFalse(Structures.STATES.isEmpty(), "no block states");
    }

    @Test
    void structuresHaveSolidBoxesWithAFloorLayer() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        // the floor layer of every structure has blocks (nothing floats: the box starts at the first solid layer)
        for (Structures.Def d : Structures.ALL) {
            assertTrue(d.w() > 0 && d.h() > 0 && d.d() > 0, d.name());
            int solid = 0;
            for (int x = 0; x < d.w(); x++) {
                for (int z = 0; z < d.d(); z++) {
                    if (d.code(x, 0, z, 0) >= BlockCode.STRUCT) {
                        solid++;
                    }
                }
            }
            assertTrue(solid > 0, d.name() + " has nothing on its first layer");
        }
    }

    @Test
    void rotationKeepsEveryBlockAndTurnsTheFrontToTheStreet() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        for (Structures.Def d : Structures.ALL) {
            long[] count = new long[4];
            for (int rot = 0; rot < 4; rot++) {
                for (int y = 0; y < d.h(); y++) {
                    for (int z = 0; z < d.sizeZ(rot); z++) {
                        for (int x = 0; x < d.sizeX(rot); x++) {
                            if (d.code(x, y, z, rot) >= BlockCode.STRUCT) {
                                count[rot]++;
                            }
                        }
                    }
                }
            }
            assertEquals(count[0], count[1], d.name());
            assertEquals(count[0], count[2], d.name());
            assertEquals(count[0], count[3], d.name());
        }
        // a block at the front-left corner of the unrotated box (x = 0, z = 0) goes to the north-east corner when
        // the structure is turned a quarter clockwise; checked cell by cell on the first structure
        Structures.Def d = Structures.ALL.get(0);
        for (int y = 0; y < d.h(); y++) {
            for (int z = 0; z < d.d(); z++) {
                for (int x = 0; x < d.w(); x++) {
                    int c0 = d.code(x, y, z, 0);
                    int c1 = d.code(d.d() - 1 - z, y, x, 1);
                    assertEquals(c0 >= BlockCode.STRUCT ? c0 + 1 : c0, c1);
                    int c2 = d.code(d.w() - 1 - x, y, d.d() - 1 - z, 2);
                    assertEquals(c0 >= BlockCode.STRUCT ? c0 + 2 : c0, c2);
                    int c3 = d.code(z, y, d.w() - 1 - x, 3);
                    assertEquals(c0 >= BlockCode.STRUCT ? c0 + 3 : c0, c3);
                }
            }
        }
    }

    @Test
    void ringsFitTheirBlockTouchTheSidewalksAndDoNotOverlap() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        int[][] sizes = {{180, 120}, {120, 180}};
        long lots = 0;
        long houses = 0;
        long area = 0;
        long[] bySide = new long[4];
        Set<String> used = new HashSet<>();
        for (int[] size : sizes) {
            int x0 = 1000;
            int z0 = -500;
            int x1 = x0 + size[0] - 1;
            int z1 = z0 + size[1] - 1;
            for (long seed = 0; seed < 3000; seed++) {
                List<Lot> ring = Ring.lots(x0, z0, x1, z1, Hash.of(seed, 7L, 0L));
                List<Lot> again = Ring.lots(x0, z0, x1, z1, Hash.of(seed, 7L, 0L));
                assertEquals(ring.size(), again.size());
                for (int a = 0; a < ring.size(); a++) {
                    assertEquals(ring.get(a).x0, again.get(a).x0);
                    assertEquals(ring.get(a).z0, again.get(a).z0);
                    assertEquals(ring.get(a).building.name(), again.get(a).building.name());
                }
                List<Lot> all = new ArrayList<>(ring);
                // never two of the same structure in one block
                Set<String> inBlock = new HashSet<>();
                for (Lot l : all) {
                    assertTrue(inBlock.add(l.building.name()), "twice in one block: " + l.building.name());
                }
                for (int a = 0; a < all.size(); a++) {
                    Lot l = all.get(a);
                    lots++;
                    used.add(l.building.name());
                    assertEquals(Lot.Type.BUILDING, l.type);
                    assertTrue(l.x0 >= x0 && l.x1 <= x1 && l.z0 >= z0 && l.z1 <= z1, "inside the block");
                    assertEquals(l.building.sizeX(l.rot), l.x1 - l.x0 + 1);
                    assertEquals(l.building.sizeZ(l.rot), l.z1 - l.z0 + 1);
                    // touches the sidewalk its front faces
                    boolean touches = switch (l.rot) {
                        case Structures.NORTH -> l.z0 == z0;
                        case Structures.EAST -> l.x1 == x1;
                        case Structures.SOUTH -> l.z1 == z1;
                        default -> l.x0 == x0;
                    };
                    assertTrue(touches, "touches the sidewalk it faces");
                    bySide[l.rot]++;
                    area += (long) (l.x1 - l.x0 + 1) * (l.z1 - l.z0 + 1);
                    if (l.building.type().startsWith("houses")) {
                        houses++;
                    }
                    for (int b = a + 1; b < all.size(); b++) {
                        Lot m = all.get(b);
                        assertFalse(l.x0 <= m.x1 && m.x0 <= l.x1 && l.z0 <= m.z1 && m.z0 <= l.z1,
                                "overlap " + l.building.name() + " / " + m.building.name());
                    }
                }
            }
        }
        int rings = 2 * 3000;
        System.out.printf("[RingTest] %d rings: %.2f structures per block, sides %.1f%% / %.1f%% / %.1f%% / %.1f%%, "
                        + "%.1f%% of the block covered, %.1f%% houses%n", rings, lots / (double) rings,
                100.0 * bySide[0] / lots, 100.0 * bySide[1] / lots, 100.0 * bySide[2] / lots, 100.0 * bySide[3] / lots,
                100.0 * area / (rings * 180.0 * 120.0), 100.0 * houses / lots);
        // every structure shows up somewhere and every side of the ring is used
        assertEquals(Structures.ALL.size(), used.size(), "structures never drawn: " + used);
        for (long n : bySide) {
            assertTrue(n > lots / 10, "a side of the ring is almost empty");
        }
    }

    @Test
    void everyStructureFitsNextToTheShallowestInA120Block() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        // each structure fits a 180 x 120 block next to the shallowest structure (depth 20) and the widest forest (30):
        // facing a long side (depth across the 120), or facing a short side (front within the 120, depth across the
        // 180), as the ramp building (102 x 72) does
        for (Structures.Def d : Structures.ALL) {
            boolean longSide = d.depth() + Structures.MIN_DEPTH + Ring.CORE_MAX <= 120 && d.front() <= 180;
            boolean shortSide = d.depth() + Structures.MIN_DEPTH + Ring.CORE_MAX <= 180 && d.front() <= 120;
            assertTrue(longSide || shortSide, d.name() + " fits no side of a 180 x 120 block");
        }
    }

    @Test
    void groundLayerAndBuriedLayers() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        for (Structures.Def d : Structures.ALL) {
            int buried = 0;
            int below = 0;
            for (int x = 0; x < d.w(); x++) {
                for (int z = 0; z < d.d(); z++) {
                    if (d.code(x, -1, z, 0) >= BlockCode.STRUCT) {
                        buried++;
                    }
                    if (d.code(x, -1 - d.ground(), z, 0) != BlockCode.KEEP) {
                        below++;
                    }
                    // air under the ground only where the template has cave_air (the underground garage)
                    if (d.code(x, -1, z, 0) == BlockCode.AIR && !d.name().equals("abandoned_wide_building")
                            && !d.name().equals("abandoned_police_station")) {
                        below++;
                    }
                }
            }
            // a structure with a buried layer has blocks under the ground; nothing lies deeper, and no air is dug
            assertEquals(d.ground() > 0, buried > 0, d.name() + " buried blocks " + buried);
            assertEquals(0, below, d.name() + " blocks below its box or air underground");
            assertEquals(d.h(), d.above() + d.ground(), d.name());
        }
        // these four (and the big house, whose lowest layer is all dirt) stand one layer lower than their template's
        // lowest layer
        for (String n : List.of("abandoned_mansion", "abandoned_large_house", "tower", "bluish_building", "apartment_building")) {
            assertEquals(1, Structures.ALL.stream().filter(d -> d.name().equals(n)).findFirst().orElseThrow().ground(), n);
        }
    }

    @Test
    void frontsMeetTheSidewalk() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        for (Structures.Def d : Structures.ALL) {
            int front = 0;
            for (int x = 0; x < d.w(); x++) {
                if (d.code(x, 0, 0, 0) >= BlockCode.STRUCT) {
                    front++;
                }
            }
            System.out.println("[RingTest] " + d.name() + " front row on the ground " + front + "/" + d.w() + " (box "
                    + d.front() + " x " + d.depth() + " x " + d.h() + ", ground layer " + d.ground() + ")");
            // the floor reaches the front row (vines and banners in front of the floor are kept, the floor under them
            // is filled); only the apartment has gaps there (empty cells become the city ground, level with it)
            assertTrue(front >= 0.7 * d.w(), d.name() + " front row " + front + "/" + d.w());
            if (!d.name().equals("apartment_building")) {
                assertEquals(d.w(), front, d.name() + " front row");
            }
        }
    }

    @Test
    void structureTreesHaveRealLeaves() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        int[][] six = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        long real = 0;
        for (Structures.Def d : Structures.ALL) {
            for (int y = -d.ground(); y < d.above(); y++) {
                for (int z = 0; z < d.d(); z++) {
                    for (int x = 0; x < d.w(); x++) {
                        int dist = leafDistance(d.code(x, y, z, 0));
                        if (dist <= 0) {
                            continue;
                        }
                        real++;
                        assertTrue(dist <= Flora.LEAF_REACH, d.name() + " distance " + dist);
                        boolean ok = false;
                        for (int[] q : six) {
                            int n = d.code(x + q[0], y + q[1], z + q[2], 0);
                            ok |= isLog(n) || leafDistance(n) > 0 && leafDistance(n) < dist;
                        }
                        assertTrue(ok, d.name() + " leaf cut off from the wood at " + x + "," + y + "," + z);
                    }
                }
            }
        }
        assertTrue(real > 1000, "real leaves " + real);
    }

    /** Distance of a non-persistent leaf of a structure code, else 0. */
    private static int leafDistance(int c) {
        if (c < BlockCode.STRUCT) {
            return 0;
        }
        String s = Structures.STATES.get((c - BlockCode.STRUCT) / 4);
        if (!s.contains("_leaves") || !s.contains("persistent=false")) {
            return 0;
        }
        int i = s.indexOf("distance=") + 9;
        return s.charAt(i) - '0';
    }

    private static boolean isLog(int c) {
        if (c < BlockCode.STRUCT) {
            return false;
        }
        String b = Structures.blockOf(Structures.STATES.get((c - BlockCode.STRUCT) / 4));
        return Structures.isLogName(b);
    }

    @Test
    void spawnLimitsHoldOverACity() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        for (long seed = 0; seed < 300; seed++) {
            Map<String, Integer> used = new HashMap<>();
            Map<String, Integer> seen = new HashMap<>();
            // four blocks of one city, one after the other
            for (int b = 0; b < 4; b++) {
                for (Lot l : Ring.lots(0, 0, 179, 119, Hash.of(seed, 11L, b), used)) {
                    if (l.building.group() != null) {
                        seen.merge(l.building.group(), 1, Integer::sum);
                    }
                    // the buildings are counted by model: at most one of each in the city
                    if (l.building.onePerCity()) {
                        seen.merge("model:" + l.building.model(), 1, Integer::sum);
                    }
                }
            }
            for (var e : seen.entrySet()) {
                int limit = e.getKey().startsWith("model:") ? 1 : Structures.LIMITS.get(e.getKey());
                assertTrue(e.getValue() <= limit, e.getKey() + " " + e.getValue());
            }
            assertEquals(seen, used);
        }
    }

    @Test
    void housesWithTheirFrontOnPlusZFaceTheStreet() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        // the front of these two is on the template's +Z side
        List<String> turned = List.of("abandoned_modern_house", "post_apocalyptic_mansion");
        for (Structures.Def c : Structures.ALL) {
            assertEquals(turned.contains(c.name()) ? 2 : 0, c.turn(), c.name());
            if (c.turn() != 2) {
                continue;
            }
            // at rotation 0 (street to the north) the row touching the street is the template's last z row
            Template t = c.t();
            for (int x = 0; x < c.front(); x++) {
                for (int y = 1; y < c.above(); y++) {
                    int p = t.at(c.ox() + c.w() - 1 - x, c.oy() + c.ground() + y, c.oz() + c.d() - 1);
                    int code = c.code(x, y, 0, 0);
                    if (p < 0 || code < BlockCode.STRUCT) {
                        continue;
                    }
                    String placed = Structures.blockOf(Structures.STATES.get((code - BlockCode.STRUCT) / 4));
                    assertEquals(Structures.blockOf(t.palette.get(p)), placed, c.name() + " front row at x " + x + " y " + y);
                }
            }
        }
    }

    @Test
    void undergroundGarageKeepsItsRampAndStairsOpen() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        for (Structures.Def d : Structures.ALL) {
            int openings = 0;
            for (int rot = 0; rot < 4; rot++) {
                int n = 0;
                for (int rz = 0; rz < d.sizeZ(rot); rz++) {
                    for (int rx = 0; rx < d.sizeX(rot); rx++) {
                        if (d.opening(rx, rz, rot)) {
                            n++;
                            // under it dug-out space or a block of the ramp itself, never terrain
                            int below = d.code(rx, -1, rz, rot);
                            assertTrue(below == BlockCode.AIR || below >= BlockCode.STRUCT, d.name() + " terrain under an opening");
                            assertEquals(BlockCode.AIR, d.code(rx, 0, rz, rot), d.name() + " opening");
                        }
                    }
                }
                if (rot == 0) {
                    openings = n;
                } else {
                    assertEquals(openings, n, d.name() + " rot " + rot);
                }
            }
            if (d.name().equals("abandoned_wide_building")) {
                // every air cell of its street level is open: the car ramp (5 x 15, its top too) and the stairwell
                assertEquals(99, openings, "abandoned_wide_building openings");
                assertEquals(8, d.ground());
                System.out.println("[RingTest] abandoned_wide_building: " + openings + " open cells at street level, "
                        + d.access().containers() + " containers, " + d.access().closed() + " out of reach");
            } else if (d.name().equals("abandoned_police_station")) {
                // the police station's half-buried basement: its rooms cross the street level, dug out
                assertTrue(openings > 300, "police_station openings " + openings);
                assertEquals(3, d.ground());
                System.out.println("[RingTest] abandoned_police_station: " + openings + " open cells at street level");
            } else {
                // no other structure has dug-out space under its ground layer
                assertEquals(0, openings, d.name());
            }
        }
    }

    @Test
    void pharmacyVariantsMatchTheirOriginalButTheColor() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        int variants = 0;
        for (Structures.Def d : Structures.ALL) {
            if (!d.type().startsWith("pharmacy") || d.name().equals("pharmacy_red") || d.name().equals("pharmacy_large_yellow")) {
                continue;
            }
            variants++;
            String original = d.model().equals("pharmacy") ? "pharmacy_red" : "pharmacy_large_yellow";
            Template o = Structures.source(original);
            Template v = Structures.source(d.name());
            // the F A R M Á C I A letters (colored blocks on the white wall) are gone: 76 blocks on the small one (its
            // side), 152 on the big one (front and side)
            assertEquals(d.model().equals("pharmacy") ? 76 : 152, Structures.LETTERS_REMOVED.get(d.name()), d.name());
            for (int y = 0; y < o.sy; y++) {
                for (int z = 0; z < o.sz; z++) {
                    for (int x = 0; x < o.sx; x++) {
                        String os = o.at(x, y, z) < 0 ? "minecraft:air" : o.palette.get(o.at(x, y, z));
                        String vs = v.at(x, y, z) < 0 ? "minecraft:air" : v.palette.get(v.at(x, y, z));
                        String where = d.name() + " " + x + "," + y + "," + z + " " + os + " / " + vs;
                        // the same blocks in the same places (the color aside)
                        assertEquals(Structures.blockOf(os).equals("minecraft:air"), Structures.blockOf(vs).equals("minecraft:air"), where);
                        if (Template.NEUTRAL.contains(Structures.blockOf(os))) {
                            assertEquals(os, vs, where + ": a neutral block painted");
                        }
                        // the banners where the original has them
                        assertEquals(os.contains("_wall_banner"), vs.contains("_wall_banner"), where + ": banner");
                    }
                }
            }
        }
        assertEquals(8, variants);
    }

    @Test
    void pharmacyBannersKeepTheirLetters() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        // the originals and their color variants (whose letters are copied from the originals, in their color)
        List<Structures.Def> pharmacies = Structures.ALL.stream().filter(s -> s.type().startsWith("pharmacy")).toList();
        assertEquals(10, pharmacies.size());
        for (Structures.Def d : pharmacies) {
            String name = d.name();
            boolean small = d.model().equals("pharmacy");
            String color = small ? "red" : "yellow";
            for (String[] c : new String[][]{{"pharmacy_yellow", "yellow"}, {"pharmacy_blue", "blue"},
                    {"pharmacy_purple", "purple"}, {"pharmacy_green", "green"}, {"pharmacy_large_blue", "light_blue"},
                    {"pharmacy_large_purple", "purple"}, {"pharmacy_large_green", "lime"}, {"pharmacy_large_red", "red"}}) {
                if (name.equals(c[0])) {
                    color = c[1];
                }
            }
            final String want = color;
            for (int rot = 0; rot < 4; rot++) {
                int banners = 0;
                for (int y = -d.ground(); y < d.above(); y++) {
                    for (int z = 0; z < d.sizeZ(rot); z++) {
                        for (int x = 0; x < d.sizeX(rot); x++) {
                            List<String[]> design = d.banner(x, y, z, rot);
                            if (design == null) {
                                continue;
                            }
                            banners++;
                            int code = d.code(x, y, z, rot);
                            assertTrue(code >= BlockCode.STRUCT && Structures.STATES.get((code - BlockCode.STRUCT) / 4).contains("_wall_banner"),
                                    name + " design on a block that is not a banner");
                            // every letter is drawn with stripes and framed by a border
                            assertTrue(design.stream().anyMatch(l -> l[0].contains("stripe")), name);
                            assertTrue(design.stream().anyMatch(l -> l[0].equals("minecraft:border")), name);
                            // the color of the variant: the letters on the small one, the field on the big one
                            String st = Structures.STATES.get((code - BlockCode.STRUCT) / 4);
                            if (small) {
                                assertTrue(st.contains("minecraft:white_wall_banner"), name + " " + st);
                                assertTrue(design.stream().anyMatch(l -> l[0].contains("stripe") && l[1].equals(want)), name);
                            } else {
                                assertTrue(st.contains("minecraft:" + want + "_wall_banner"), name + " " + st);
                                assertTrue(design.stream().allMatch(l -> !l[0].contains("stripe") || l[1].equals("black")), name);
                            }
                        }
                    }
                }
                // D R U G S T O R E: once on the small pharmacy, twice (front and side) on the big one
                assertEquals(small ? 9 : 18, banners, name + " rot " + rot);
            }
        }
    }

    @Test
    void noFloatingCobwebs() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        int[][] six = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        long webs = 0;
        for (Structures.Def d : Structures.ALL) {
            for (int y = -d.ground(); y < d.above(); y++) {
                for (int z = 0; z < d.d(); z++) {
                    for (int x = 0; x < d.w(); x++) {
                        if (!name(d.code(x, y, z, 0)).equals("minecraft:cobweb")) {
                            continue;
                        }
                        webs++;
                        boolean held = false;
                        for (int[] q : six) {
                            String n = name(d.code(x + q[0], y + q[1], z + q[2], 0));
                            held |= !n.isEmpty() && !n.equals("minecraft:cobweb") && !n.equals("minecraft:vine")
                                    && !n.endsWith("grass") && !n.endsWith("fern") && !n.endsWith("_carpet")
                                    && !n.equals("minecraft:glow_lichen");
                        }
                        // the ground under the first layer counts as something to hang on
                        held |= y == 0;
                        assertTrue(held, d.name() + " floating cobweb at " + x + "," + y + "," + z);
                    }
                }
            }
        }
        assertTrue(webs > 2000, "cobwebs " + webs);
    }

    @Test
    void everyStructureHasASpawnCategory() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        Map<String, Integer> n = new TreeMap<>();
        for (Structures.Def d : Structures.ALL) {
            assertTrue(Arrays.asList(Structures.CATEGORIES).contains(d.category()), d.name() + " category " + d.category());
            n.merge(d.category(), 1, Integer::sum);
        }
        System.out.println("[RingTest] spawn categories: " + n);
        assertEquals("building_large", Structures.ALL.stream().filter(d -> d.name().equals("abandoned_wide_building")).findFirst().orElseThrow().category());
        assertEquals("gas_station", Structures.ALL.stream().filter(d -> d.name().equals("gas_station_small_blue")).findFirst().orElseThrow().category());
        assertEquals(Map.of("house", 12, "police_station", 1, "special", 2, "pharmacy", 10, "workshop", 8, "gas_station", 10,
                "building_small", 3, "building_large", 6), n);
    }

    /**
     * The buildings are classified by the height of their box (buried layers included): small up to 40, medium up to 75,
     * large up to 140, immense above 140, each limit with a margin of 3 blocks; the type of index.txt and the category of the commands follow it.
     */
    @Test
    void buildingsAreClassifiedByHeight() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        assertEquals("small", Structures.heightClass(43));
        assertEquals("medium", Structures.heightClass(44));
        assertEquals("medium", Structures.heightClass(78));
        assertEquals("large", Structures.heightClass(79));
        assertEquals("large", Structures.heightClass(143));
        assertEquals("immense", Structures.heightClass(144));
        Map<String, String> byName = new TreeMap<>();
        int specials = 0;
        for (Structures.Def d : Structures.ALL) {
            if (Structures.isBuildingType(d.type())) {
                // a special building (the crash building) keeps its class in its type, its category is special
                boolean special = d.type().startsWith("special_");
                assertEquals((special ? "special_" : "") + "buildings_" + Structures.heightClass(d.h()), d.type(),
                        d.name() + " height " + d.h());
                assertEquals(Structures.heightClass(d.h()), Structures.buildingClass(d.type()), d.name());
                assertEquals(special ? "special" : "building_" + Structures.heightClass(d.h()), d.category(), d.name());
                byName.put(d.name(), d.category() + " " + d.h());
                specials += special ? 1 : 0;
            }
        }
        assertEquals(1, specials, "the crash building is the one special building");
        System.out.println("[RingTest] building height classes: " + byName);
    }

    @Test
    void lightningRodsBecomeRedstoneTorches() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        assertTrue(Structures.STATES.stream().noneMatch(st -> st.startsWith("minecraft:lightning_rod")), "lightning rod state left");
        int torches = 0;
        for (Structures.Def d : Structures.ALL) {
            for (int y = -d.ground(); y < d.above(); y++) {
                for (int z = 0; z < d.d(); z++) {
                    for (int x = 0; x < d.w(); x++) {
                        String n = name(d.code(x, y, z, 0));
                        assertFalse(n.equals("minecraft:lightning_rod"), d.name());
                        torches += n.equals("minecraft:redstone_torch") ? 1 : 0;
                    }
                }
            }
        }
        // one from a rod on top of each: abandoned_tower_house, post_apocalyptic_mansion, apartment_building,
        // apocalyptic_building_57x45, tower; 12 more were saved as torches (brutalist_u_building_75x58 4,
        // abandoned_ramp_building 4, abandoned_green_tower 2, abandoned_wide_building 1, abandoned_small_building 1),
        // and 3 in the police station
        assertEquals(5 + 12 + 3, torches, "redstone torches");
        assertEquals("minecraft:redstone_wall_torch[facing=east,lit=true]",
                Structures.swap("minecraft:lightning_rod[facing=east,powered=false,waterlogged=false]"));
        assertEquals("minecraft:stone", Structures.swap("minecraft:stone"));
    }

    @Test
    void cutCrownsAreCompletedOutsideTheBox() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        int total = 0;
        Set<String> with = new HashSet<>();
        for (Structures.Def d : Structures.ALL) {
            for (int rot = 0; rot < 4; rot++) {
                List<int[]> o = d.overhangs(rot);
                assertEquals(d.overhangs(0).size(), o.size(), d.name() + " rot " + rot);
                Set<String> at = new HashSet<>();
                for (int[] q : o) {
                    at.add(q[0] + "," + q[1] + "," + q[2]);
                }
                for (int[] q : o) {
                    // out of the box, never more than the crown radius beyond it, and real leaves (leaf decay)
                    boolean out = q[0] < 0 || q[2] < 0 || q[0] >= d.sizeX(rot) || q[2] >= d.sizeZ(rot);
                    assertTrue(out, d.name() + " overhang inside the box");
                    assertTrue(q[0] >= -Structures.CROWN_RADIUS && q[2] >= -Structures.CROWN_RADIUS
                            && q[0] < d.sizeX(rot) + Structures.CROWN_RADIUS && q[2] < d.sizeZ(rot) + Structures.CROWN_RADIUS,
                            d.name() + " overhang too far");
                    String st = Structures.STATES.get((q[3] - BlockCode.STRUCT) / 4);
                    assertTrue(st.contains("_leaves") && st.contains("persistent=false"), d.name() + " " + st);
                    int dist = Integer.parseInt(st.replaceAll(".*distance=(\\d+).*", "$1"));
                    assertTrue(dist >= 1 && dist <= Flora.LEAF_REACH, d.name() + " " + st);
                    // a neighbour one step nearer to the trunk: a log, a leaf of the structure or another overhang
                    boolean held = false;
                    int[][] six = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                    for (int[] s6 : six) {
                        int nx = q[0] + s6[0];
                        int ny = q[1] + s6[1];
                        int nz = q[2] + s6[2];
                        String n = name(d.code(nx, ny, nz, rot));
                        String full = d.code(nx, ny, nz, rot) >= BlockCode.STRUCT
                                ? Structures.STATES.get((d.code(nx, ny, nz, rot) - BlockCode.STRUCT) / 4) : "";
                        if (dist == 1 && Structures.isLogName(n)) {
                            held = true;
                        }
                        if (full.contains("distance=" + (dist - 1) + ",") || full.contains("distance=" + (dist - 1) + "]")) {
                            held = true;
                        }
                        for (int[] q2 : o) {
                            if (q2[0] == nx && q2[1] == ny && q2[2] == nz && Structures.STATES.get((q2[3] - BlockCode.STRUCT) / 4)
                                    .contains("distance=" + (dist - 1))) {
                                held = true;
                            }
                        }
                    }
                    assertTrue(held, d.name() + " overhang leaf with nothing nearer to its trunk");
                }
                assertEquals(o.size(), at.size(), d.name() + " overhang twice at one place");
            }
            if (!d.overhangs(0).isEmpty()) {
                with.add(d.name());
                total += d.overhangs(0).size();
            }
        }
        System.out.println("[RingTest] crowns completed outside the box: " + total + " leaves in " + with);
        // the tree in front of the house with the pool: its crown hangs over the sidewalk, high
        Structures.Def pool = Structures.ALL.stream().filter(d -> d.name().equals("abandoned_pool_house")).findFirst()
                .orElseThrow();
        assertFalse(pool.overhangs(0).isEmpty(), "pool house crown not completed");
        for (int[] q : pool.overhangs(0)) {
            assertTrue(q[2] < 0 && q[1] > Params.CLEARANCE, "pool house crown " + Arrays.toString(q));
        }
        assertEquals(Set.of("abandoned_pool_house", "abandoned_garage_house", "abandoned_suburban_house",
                "abandoned_tower_house", "skeleton_building_31x29", "abandoned_glass_house", "abandoned_ramp_building"), with);
    }

    private static String name(int code) {
        return code < BlockCode.STRUCT ? "" : Structures.blockOf(Structures.STATES.get((code - BlockCode.STRUCT) / 4));
    }

    @Test
    void structuresNeverTouchAndLeaveTheForest() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        long pairs = 0;
        int minGap = Integer.MAX_VALUE;
        int minCore = Integer.MAX_VALUE;
        for (int[] size : new int[][]{{180, 120}, {120, 180}}) {
            for (long seed = 0; seed < 3000; seed++) {
                List<Lot> ring = Ring.lots(0, 0, size[0] - 1, size[1] - 1, Hash.of(seed, 13L, 0L));
                for (int a = 0; a < ring.size(); a++) {
                    for (int b = a + 1; b < ring.size(); b++) {
                        Lot p = ring.get(a);
                        Lot q = ring.get(b);
                        int gx = Math.max(p.x0, q.x0) - Math.min(p.x1, q.x1) - 1;
                        int gz = Math.max(p.z0, q.z0) - Math.min(p.z1, q.z1) - 1;
                        int gap = Math.max(gx, gz);
                        boolean opposite = (p.rot ^ q.rot) == 2;
                        if (opposite) {
                            // backs of opposite rows: the forest of the core in between
                            assertTrue(gap >= Ring.CORE_MIN, "forest " + gap + " between " + p.building.name() + " and " + q.building.name());
                            minCore = Math.min(minCore, gap);
                        } else {
                            assertTrue(gap >= Ring.GAP_MIN, "gap " + gap + " between " + p.building.name() + " and " + q.building.name());
                            minGap = Math.min(minGap, gap);
                        }
                        pairs++;
                    }
                }
            }
        }
        System.out.println("[RingTest] pairs " + pairs + ", smallest gap " + minGap + ", smallest forest " + minCore);
    }
}
