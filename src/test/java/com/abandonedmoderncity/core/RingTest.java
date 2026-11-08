package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The structures (rotation, boxes, block codes) and the ring of them around a block. */
class RingTest {

    @Test
    void allStructuresAreLoadedWithTheirMeasuredBoxes() {
        assertEquals(33, Structures.ALL.size());
        Structures.Def posto = Structures.ALL.stream().filter(d -> d.name().equals("posto_azul")).findFirst().orElseThrow();
        assertEquals(45, posto.w());
        assertEquals(37, posto.d());
        // the solid box drops the empty first row of the pharmacy (20 x 29 template, 19 x 28 solid)
        Structures.Def farmacia = Structures.ALL.stream().filter(d -> d.name().equals("farmacia_amarelo")).findFirst().orElseThrow();
        assertEquals(19, farmacia.w());
        assertEquals(28, farmacia.d());
        assertEquals(20, Structures.MIN_DEPTH);
        // the floor layer of every structure has blocks (nothing floats: the box starts at the first solid layer),
        for (Structures.Def d : Structures.ALL) {
            int solid = 0;
            for (int x = 0; x < d.w(); x++) {
                for (int z = 0; z < d.d(); z++) {
                    int c = d.code(x, 0, z, 0);
                    if (c >= BlockCode.STRUCT) {
                        solid++;
                    }
                }
            }
            assertTrue(solid > 0, d.name() + " has nothing on its first layer");
        }
    }

    @Test
    void rotationKeepsEveryBlockAndTurnsTheFrontToTheStreet() {
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
        int[][] sizes = {{140, 90}, {90, 140}};
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
    void everyStructureFitsNextToTheShallowestInA90Block() {
        // each structure fits a 140 x 90 block next to the shallowest structure (depth 20): 90 - 20 = 70 >= depth
        for (Structures.Def d : Structures.ALL) {
            assertTrue(d.d() + Structures.MIN_DEPTH <= 90, d.name() + " is too deep for a 90 block next to the shallowest");
            assertTrue(d.w() <= 140, d.name());
        }
    }
}
