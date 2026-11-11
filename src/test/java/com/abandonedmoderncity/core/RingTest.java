package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The structures (rotation, boxes, block codes) and the ring of them around a block. */
class RingTest {

    @Test
    void withoutStructuresBlocksStayEmpty() {
        assumeTrue(Structures.ALL.isEmpty(), "structures present");
        assertTrue(Ring.lots(0, 0, 179, 119, 1).isEmpty());
        assertTrue(Structures.STATES.isEmpty());
    }

    @Test
    void structuresHaveSolidBoxesWithAFloorLayer() {
        assumeFalse(Structures.ALL.isEmpty(), "no structures");
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
        assumeFalse(Structures.ALL.isEmpty(), "no structures");
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
        assumeFalse(Structures.ALL.isEmpty(), "no structures");
        int[][] sizes = {{180, 120}, {120, 180}};
        long lots = 0;
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
                    for (int b = a + 1; b < all.size(); b++) {
                        Lot m = all.get(b);
                        assertFalse(l.x0 <= m.x1 && m.x0 <= l.x1 && l.z0 <= m.z1 && m.z0 <= l.z1,
                                "overlap " + l.building.name() + " / " + m.building.name());
                    }
                }
            }
        }
        // every structure shows up somewhere and every side of the ring is used
        assertEquals(Structures.ALL.size(), used.size(), "structures never drawn: " + used);
        for (long n : bySide) {
            assertTrue(n > lots / 10, "a side of the ring is almost empty");
        }
    }

    @Test
    void everyStructureFitsNextToTheShallowestInA120Block() {
        assumeFalse(Structures.ALL.isEmpty(), "no structures");
        // each structure fits a 180 x 120 block next to the shallowest structure (depth 20) and the widest forest (30)
        for (Structures.Def d : Structures.ALL) {
            assertTrue(d.depth() + Structures.MIN_DEPTH + Ring.CORE_MAX <= 120, d.name() + " is too deep for a 120 block next to the shallowest and the widest forest");
            assertTrue(d.front() <= 180, d.name());
        }
    }

    @Test
    void groundLayerAndBuriedLayers() {
        assumeFalse(Structures.ALL.isEmpty(), "no structures");
        for (Structures.Def d : Structures.ALL) {
            int buried = 0;
            int below = 0;
            for (int x = 0; x < d.w(); x++) {
                for (int z = 0; z < d.d(); z++) {
                    if (d.code(x, -1, z, 0) >= BlockCode.STRUCT) {
                        buried++;
                    }
                    if (d.code(x, -1 - d.ground(), z, 0) != BlockCode.KEEP || d.code(x, -1, z, 0) == BlockCode.AIR) {
                        below++;
                    }
                }
            }
            // a structure with a buried layer has blocks under the ground; nothing lies deeper, and no air is dug
            assertEquals(d.ground() > 0, buried > 0, d.name() + " buried blocks " + buried);
            assertEquals(0, below, d.name() + " blocks below its box or air underground");
            assertEquals(d.h(), d.above() + d.ground(), d.name());
        }
        // the four the user named are one layer lower than their template's lowest layer
        for (String n : List.of("mansao_abandonada", "torre", "predio_azulado", "predio_apartamento")) {
            assertEquals(1, Structures.ALL.stream().filter(d -> d.name().equals(n)).findFirst().orElseThrow().ground(), n);
        }
    }

    @Test
    void frontsMeetTheSidewalk() {
        assumeFalse(Structures.ALL.isEmpty(), "no structures");
        for (Structures.Def d : Structures.ALL) {
            int front = 0;
            for (int x = 0; x < d.w(); x++) {
                if (d.code(x, 0, 0, 0) >= BlockCode.STRUCT) {
                    front++;
                }
            }
            System.out.println("[RingTest] " + d.name() + " front row on the ground " + front + "/" + d.w());
            // the floor reaches the front row (vines and banners in front of the floor are kept, the floor under them
            // is filled); only the apartment has gaps there (empty cells become the city ground, level with it)
            assertTrue(front >= 0.7 * d.w(), d.name() + " front row " + front + "/" + d.w());
            if (!d.name().equals("predio_apartamento")) {
                assertEquals(d.w(), front, d.name() + " front row");
            }
        }
    }

    @Test
    void structureTreesHaveRealLeaves() {
        assumeFalse(Structures.ALL.isEmpty(), "no structures");
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
        assumeFalse(Structures.ALL.isEmpty(), "no structures");
        for (long seed = 0; seed < 300; seed++) {
            java.util.Map<String, Integer> used = new java.util.HashMap<>();
            java.util.Map<String, Integer> seen = new java.util.HashMap<>();
            // four blocks of one city, one after the other
            for (int b = 0; b < 4; b++) {
                for (Lot l : Ring.lots(0, 0, 179, 119, Hash.of(seed, 11L, b), used)) {
                    if (l.building.group() != null) {
                        seen.merge(l.building.group(), 1, Integer::sum);
                    }
                }
            }
            for (var e : seen.entrySet()) {
                assertTrue(e.getValue() <= Structures.LIMITS.get(e.getKey()), e.getKey() + " " + e.getValue());
            }
            assertEquals(seen, used);
        }
    }

    @Test
    void housesWithTheirFrontOnPlusZFaceTheStreet() {
        assumeFalse(Structures.ALL.isEmpty(), "no structures");
        // the user found the front of these two on the template's +Z side
        List<String> turned = List.of("casa_moderna_abandonada", "mansao_pos_apocaliptica");
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
    void pharmacyBannersKeepTheirLetters() {
        assumeFalse(Structures.ALL.isEmpty(), "no structures");
        for (String name : List.of("farmacia_vermelho", "farmacia_grande_amarela")) {
            Structures.Def d = Structures.ALL.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
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
                        }
                    }
                }
                // D R U G S T O R E: once on the small pharmacy, twice (front and side) on the big one
                assertEquals(name.equals("farmacia_vermelho") ? 9 : 18, banners, name + " rot " + rot);
            }
        }
    }

    @Test
    void noFloatingCobwebs() {
        assumeFalse(Structures.ALL.isEmpty(), "no structures");
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

    private static String name(int code) {
        return code < BlockCode.STRUCT ? "" : Structures.blockOf(Structures.STATES.get((code - BlockCode.STRUCT) / 4));
    }

    @Test
    void structuresNeverTouchAndLeaveTheForest() {
        assumeFalse(Structures.ALL.isEmpty(), "no structures");
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
