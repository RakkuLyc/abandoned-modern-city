package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The chests drawn in each copy of a building ({@link ChestDraw}, stage 3 of the procedural chests). */
class ChestDrawTest {
    static final int COPIES = 200;
    static final int WALKED = 40;
    private static final int[][] SIDE = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private static boolean building(Structures.Def d) {
        return d.type().startsWith("buildings_");
    }

    private static String cls(Structures.Def d) {
        return d.category().substring("building_".length());
    }

    private static long seed(int i) {
        return (i + 1) * 0x9E3779B97F4A7C15L;
    }

    private static List<Rooms.Spot> draw(Structures.Def d, long seed) {
        return ChestDraw.draw(d.rooms(), cls(d), d.t().sx, d.t().sz, seed);
    }

    @Test
    void countsCoverEveryRange() {
        for (String c : List.of("small", "medium", "large", "immense")) {
            int[] r = ChestDraw.range(c);
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < 4000; i++) {
                int n = ChestDraw.count(c, seed(i));
                assertTrue(n >= r[0] && n <= r[1], c + " " + n);
                seen.add(n);
            }
            assertEquals(r[1] - r[0] + 1, seen.size(), c + ": every count of the range comes up");
        }
        assertEquals(12, ChestDraw.range("small")[0]);
        assertEquals(28, ChestDraw.range("small")[1]);
        assertEquals(25, ChestDraw.range("medium")[0]);
        assertEquals(47, ChestDraw.range("medium")[1]);
        assertEquals(38, ChestDraw.range("large")[0]);
        assertEquals(65, ChestDraw.range("large")[1]);
        assertEquals(51, ChestDraw.range("immense")[0]);
        assertEquals(83, ChestDraw.range("immense")[1]);
    }

    @Test
    void everyCopyGetsItsCountSpacedOnEveryFloor() {
        for (Structures.Def d : Structures.ALL) {
            if (!building(d)) {
                assertTrue(d.chests(1).isEmpty(), d.name() + " is not a building");
                continue;
            }
            Rooms.Layout l = d.rooms();
            Set<Rooms.Spot> spots = new HashSet<>(l.spots());
            int nf = l.floors().size();
            int gy = d.oy() + d.ground();
            int street = -1;
            for (int f = 0; f < nf; f++) {
                if (l.floors().get(f).from() <= gy + 1 && l.floors().get(f).to() >= gy + 1) {
                    street = f;
                }
            }
            Set<List<Rooms.Spot>> distinct = new HashSet<>();
            long total = 0;
            long onStreetFloor = 0;
            long onTop = 0;
            long explore = 0;
            long streetSpots = 0;
            long hard = 0;
            int maxZone = 0;
            int copiesOverCap = 0;
            int roomsOverCap = 0;
            Map<Integer, Set<Integer>> zonesOfRoom = new HashMap<>();
            for (Rooms.Spot s : l.spots()) {
                zonesOfRoom.computeIfAbsent(s.room(), k -> new HashSet<>()).add(s.zone());
            }
            int minExplore = Integer.MAX_VALUE;
            for (int i = 0; i < COPIES; i++) {
                long seed = seed(i);
                List<Rooms.Spot> c = draw(d, seed);
                int n = ChestDraw.count(cls(d), seed);
                assertEquals(n, c.size(), d.name() + ": the count of the copy");
                int[] perFloor = new int[nf];
                Map<Integer, Integer> perZone = new HashMap<>();
                int e = 0;
                for (int a = 0; a < c.size(); a++) {
                    Rooms.Spot s = c.get(a);
                    assertTrue(spots.contains(s), d.name() + ": not a spot " + s);
                    for (int b = a + 1; b < c.size(); b++) {
                        Rooms.Spot o = c.get(b);
                        assertFalse(Math.abs(o.x() - s.x()) < Rooms.SPACING && Math.abs(o.z() - s.z()) < Rooms.SPACING
                                && Math.abs(o.y() - s.y()) <= 2, d.name() + ": two chests too close " + s + " " + o);
                    }
                    perFloor[s.floor()]++;
                    perZone.merge(s.zone(), 1, Integer::sum);
                    e += ChestDraw.exploration(s) ? 1 : 0;
                    streetSpots += s.is(Rooms.STREET) ? 1 : 0;
                    hard += s.is(Rooms.HARD) ? 1 : 0;
                }
                assertTrue(e >= Math.ceil(ChestDraw.EXPLORATION_MIN * n), d.name() + ": " + e + " exploration of " + n);
                // every floor whose share is one chest or more gets some (a tiny roof may get none)
                double weights = java.util.Arrays.stream(l.floorWeight()).sum();
                for (int f = 0; f < nf; f++) {
                    assertTrue(n * l.floorWeight()[f] / weights < 1 || perFloor[f] > 0, d.name() + ": no chest on floor " + f);
                }
                for (Map.Entry<Integer, Integer> z : perZone.entrySet()) {
                    roomsOverCap += z.getValue() > ChestDraw.ZONE_CAP
                            && zonesOfRoom.get(l.zoneRoom()[z.getKey()]).size() == 1 ? 1 : 0;
                }
                int mz = perZone.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                maxZone = Math.max(maxZone, mz);
                copiesOverCap += mz > ChestDraw.ZONE_CAP ? 1 : 0;
                minExplore = Math.min(minExplore, 100 * e / n);
                distinct.add(c);
                total += n;
                onStreetFloor += street >= 0 ? perFloor[street] : 0;
                onTop += perFloor[nf - 1];
                explore += e;
            }
            assertTrue(distinct.size() >= COPIES - 2, d.name() + ": copies with the same chests");
            // 1 chest per zone; the cap is raised only in the skeleton building (up to 3, also in whole rooms: accepted
            // by the user on 15 Nov 2026) and in the apartment building (pieces of halls)
            assertTrue(maxZone <= 3, d.name() + ": " + maxZone + " chests in a zone");
            if (!d.name().equals("skeleton_building_31x29")) {
                assertEquals(0, roomsOverCap, d.name() + ": a room of one zone with more than one chest");
                if (!d.name().equals("apartment_building")) {
                    assertEquals(0, copiesOverCap, d.name() + ": copies over the zone cap");
                }
            }
            System.out.printf("[ChestDrawTest] %-28s %d copies: %.1f chests on average, on the street floor %.0f%%, on"
                            + " the top floor %.0f%%, exploration %.0f%% (least %d%%), street spots %.0f%%, hard %.1f a"
                            + " copy; most in a zone %d (copies over %d: %d)%n", d.name(), COPIES, (double) total / COPIES,
                    100.0 * onStreetFloor / total, 100.0 * onTop / total, 100.0 * explore / total, minExplore,
                    100.0 * streetSpots / total, (double) hard / COPIES, maxZone, ChestDraw.ZONE_CAP, copiesOverCap);
        }
    }

    @Test
    void drawnChestsHardToGetToAreHardAndTheBarrelsKeepTheirFlags() {
        for (Structures.Def d : Structures.ALL) {
            if (!building(d)) {
                assertTrue(d.hardSpots().isEmpty(), d.name());
                continue;
            }
            Template t = d.t();
            Set<Integer> hard = new HashSet<>();
            for (Rooms.Spot s : d.rooms().spots()) {
                if (s.is(Rooms.HARD)) {
                    hard.add(s.x() + t.sx * (s.z() + t.sz * s.y()));
                }
            }
            assertEquals(hard, d.hardSpots(), d.name());
            // no spot is a container of the template nor a barrel: their flags stay those of Access
            for (int c : d.hardSpots()) {
                assertFalse(d.access().hard().contains(c), d.name());
                assertFalse(d.barrels().containsKey(c), d.name());
            }
            for (int i = 0; i < 10; i++) {
                long seed = seed(i);
                long expected = draw(d, seed).stream().filter(s -> s.is(Rooms.HARD)).count();
                for (int rot = 0; rot < 4; rot++) {
                    int hardChests = 0;
                    int drawnChests = 0;
                    for (int y = -d.ground(); y < d.above(); y++) {
                        for (int z = 0; z < d.sizeZ(rot); z++) {
                            for (int x = 0; x < d.sizeX(rot); x++) {
                                int c = d.code(x, y, z, rot, seed);
                                if (c >= BlockCode.STRUCT && d.code(x, y, z, rot) == BlockCode.AIR) {
                                    drawnChests++;
                                    hardChests += d.hard(x, y, z, rot) ? 1 : 0;
                                }
                            }
                        }
                    }
                    assertEquals(d.chests(seed).size(), drawnChests, d.name());
                    assertEquals(expected, hardChests, d.name() + " rot " + rot + ": hard drawn chests");
                }
            }
        }
    }

    @Test
    void mediumAndImmenseReachTheirCountsOnALargeBuilding() {
        // no building is medium or immense yet: their ranges, drawn on the rooms of a large one, always reach the count
        Structures.Def d = Structures.ALL.stream().filter(x -> x.name().equals("brutalist_u_building_75x58")).findFirst()
                .orElseThrow();
        for (String c : List.of("medium", "immense")) {
            int[] r = ChestDraw.range(c);
            Set<Integer> sizes = new HashSet<>();
            for (int i = 0; i < 400; i++) {
                long seed = seed(i);
                List<Rooms.Spot> l = ChestDraw.draw(d.rooms(), c, d.t().sx, d.t().sz, seed);
                assertEquals(ChestDraw.count(c, seed), l.size(), c);
                assertTrue(l.stream().filter(ChestDraw::exploration).count() >= Math.ceil(ChestDraw.EXPLORATION_MIN * l.size()), c);
                sizes.add(l.size());
            }
            assertTrue(sizes.contains(r[0]) && sizes.contains(r[1]), c + ": " + sizes);
        }
    }

    @Test
    void theSameSeedGivesTheSameChests() {
        for (Structures.Def d : Structures.ALL) {
            if (!building(d)) {
                continue;
            }
            for (int i = 0; i < 5; i++) {
                assertEquals(draw(d, seed(i)), draw(d, seed(i)), d.name());
                Map<Integer, Integer> m = d.chests(seed(i));
                assertEquals(ChestDraw.count(cls(d), seed(i)), m.size(), d.name());
                assertEquals(m, d.chests(seed(i)), d.name());
            }
        }
    }

    @Test
    void copiesWalkedAgainLoseNothing() {
        // each copy with its barrels kept (as chests) or taken out and its chests in place: every chest reached with
        // someone standing in front of it, and every container reached before still reached
        for (Structures.Def d : Structures.ALL) {
            if (!building(d)) {
                continue;
            }
            Template t = d.t();
            int layer = t.sx * t.sz;
            for (int i = 0; i < WALKED; i++) {
                long seed = seed(i);
                Map<Integer, String> base = new HashMap<>();
                for (Map.Entry<Integer, int[]> e : d.barrels().entrySet()) {
                    if (!Barrels.kept(e.getValue(), seed, layer)) {
                        base.put(e.getKey(), "minecraft:air");
                    }
                }
                Map<Integer, String> withChests = new HashMap<>(base);
                List<Rooms.Spot> c = draw(d, seed);
                for (Rooms.Spot s : c) {
                    withChests.put(s.x() + t.sx * (s.z() + t.sz * s.y()), "minecraft:chest[facing="
                            + Rooms.FACINGS[s.facing()] + ",type=single,waterlogged=false]");
                }
                Template t0 = base.isEmpty() ? t : t.withStates(base);
                Template t1 = t.withStates(withChests);
                Access.Walk w0 = walk(d, t0);
                Access.Walk w1 = walk(d, t1);
                for (Rooms.Spot s : c) {
                    assertTrue(w1.reaches(s.x(), s.y(), s.z()), d.name() + ": out of reach " + s);
                    assertTrue(w1.reachedAt(s.x() + SIDE[s.facing()][0], s.y(), s.z() + SIDE[s.facing()][1]),
                            d.name() + ": nobody stands in front of " + s);
                }
                for (int y = 0; y < t.sy; y++) {
                    for (int z = 0; z < t.sz; z++) {
                        for (int x = 0; x < t.sx; x++) {
                            int p = t0.at(x, y, z);
                            if (p >= 0 && Access.container(Access.bare(t0.palette.get(p))) && w0.reaches(x, y, z)) {
                                assertTrue(w1.reaches(x, y, z), d.name() + ": a container cut off at " + x + "," + y + "," + z);
                            }
                        }
                    }
                }
            }
        }
    }

    private static Access.Walk walk(Structures.Def d, Template t) {
        String[] placed = new String[t.palette.size()];
        for (int p = 0; p < placed.length; p++) {
            placed[p] = Structures.swap(t.palette.get(p));
        }
        return Access.walk(t, placed, d.ox(), d.oy(), d.oz(), d.w(), d.h(), d.d(), d.ground());
    }

    @Test
    void chestsFaceAnOpenCellInEveryRotation() {
        for (Structures.Def d : Structures.ALL) {
            if (!building(d)) {
                continue;
            }
            long seed = seed(7);
            for (int rot = 0; rot < 4; rot++) {
                int found = 0;
                for (int y = -d.ground(); y < d.above(); y++) {
                    for (int z = 0; z < d.sizeZ(rot); z++) {
                        for (int x = 0; x < d.sizeX(rot); x++) {
                            int c = d.code(x, y, z, rot, seed);
                            if (c < BlockCode.STRUCT || d.code(x, y, z, rot) != BlockCode.AIR) {
                                continue;
                            }
                            String s = Structures.STATES.get((c - BlockCode.STRUCT) / 4);
                            assertEquals("minecraft:chest", Structures.blockOf(s), d.name());
                            int f = List.of(Rooms.FACINGS).indexOf(s.replaceAll(".*facing=(\\w+).*", "$1"));
                            int world = (f + (c - BlockCode.STRUCT) % 4) & 3;
                            int front = d.code(x + SIDE[world][0], y, z + SIDE[world][1], rot, seed);
                            boolean open = front == BlockCode.AIR || front == BlockCode.KEEP
                                    || front >= BlockCode.STRUCT && Access.passable(Access.bare(
                                    Structures.STATES.get((front - BlockCode.STRUCT) / 4)));
                            assertTrue(open, d.name() + " rot " + rot + ": the chest at " + x + "," + y + "," + z
                                    + " faces a block");
                            found++;
                        }
                    }
                }
                assertEquals(d.chests(seed).size(), found, d.name() + " rot " + rot);
            }
        }
    }
}
