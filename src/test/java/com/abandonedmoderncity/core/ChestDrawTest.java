package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        return Structures.isBuildingType(d.type());
    }

    private static long seed(int i) {
        return (i + 1) * 0x9E3779B97F4A7C15L;
    }

    private static List<Rooms.Spot> draw(Structures.Def d, long seed) {
        return ChestDraw.draw(d.rooms(), d.t().sx, d.t().sz, seed);
    }

    /** The average count of a building: max(floors with room, round(covered / AREA × factor)), the factor even. */
    private static double expected(Structures.Def d) {
        int steps = 100_000;
        double sum = 0;
        double base = (double) ChestDraw.covered(d.rooms()) / ChestDraw.AREA;
        for (int i = 0; i < steps; i++) {
            double factor = 1 - ChestDraw.SPREAD + 2 * ChestDraw.SPREAD * (i + 0.5) / steps;
            sum += Math.max(ChestDraw.floorsWithRoom(d.rooms()), Math.round(base * factor));
        }
        return sum / steps;
    }

    @Test
    void countsFollowTheAreaUnderACeiling() {
        // the user's choices of 27 Nov 2026: one chest for every 250 cells under a ceiling, ±30%, at least one a floor
        assertEquals(250, ChestDraw.AREA);
        assertEquals(0.30, ChestDraw.SPREAD);
        double apartment = 0;
        double crash = 0;
        for (Structures.Def d : Structures.ALL) {
            if (!building(d)) {
                continue;
            }
            Rooms.Layout l = d.rooms();
            int floors = ChestDraw.floorsWithRoom(l);
            double base = (double) ChestDraw.covered(l) / ChestDraw.AREA;
            int low = Math.max(floors, (int) Math.round(base * (1 - ChestDraw.SPREAD)));
            int high = Math.max(floors, (int) Math.round(base * (1 + ChestDraw.SPREAD)));
            int min = Integer.MAX_VALUE;
            int max = 0;
            long sum = 0;
            int copies = 4000;
            for (int i = 0; i < copies; i++) {
                int n = ChestDraw.count(l, seed(i));
                assertTrue(n >= low && n <= high, d.name() + ": " + n + " out of " + low + "–" + high);
                assertTrue(n >= floors, d.name() + ": fewer chests than floors with room");
                min = Math.min(min, n);
                max = Math.max(max, n);
                sum += n;
            }
            double mean = (double) sum / copies;
            double want = expected(d);
            assertTrue(Math.abs(mean - want) <= 0.02 * want + 0.2, d.name() + ": " + mean + " against " + want);
            assertEquals(low, min, d.name() + ": the lowest count comes up");
            assertEquals(high, max, d.name() + ": the highest count comes up");
            apartment = d.name().equals("apartment_building") ? mean : apartment;
            crash = d.name().equals("abandoned_crash_building") ? mean : crash;
            System.out.printf("[ChestDrawTest] %-28s %6d cells under a ceiling, %2d floors with room: %d to %d chests,"
                    + " %.1f on average (expected %.1f)%n", d.name(), ChestDraw.covered(l), floors, min, max, mean, want);
        }
        // the wider building gets more: the crash building (~26,900 cells) ten times the apartment building (~2,600)
        assertTrue(crash > 8 * apartment, crash + " against " + apartment);
    }

    @Test
    void quotasGiveEveryFloorWithRoomOne() {
        // in proportion first (largest remainders: 6, 0, 4 and 2, 0, 1), then a floor with room left at 0 takes one
        // from the largest share
        assertArrayEquals(new int[]{5, 1, 4}, ChestDraw.quotas(new double[]{50, 1, 40}, 10));
        assertArrayEquals(new int[]{1, 1, 1, 0}, ChestDraw.quotas(new double[]{50, 1, 40, 0}, 3));
        assertArrayEquals(new int[]{30, 20}, ChestDraw.quotas(new double[]{60, 40}, 50));
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
                int n = ChestDraw.count(l, seed);
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
                // every floor with room for a chest gets one (the user's minimum of 27 Nov 2026)
                for (int f = 0; f < nf; f++) {
                    assertTrue(l.floorWeight()[f] == 0 || perFloor[f] > 0, d.name() + ": no chest on floor " + f);
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
                                if (c >= BlockCode.STRUCT && d.code(x, y, z, rot) == BlockCode.AIR
                                        && Structures.blockOf(Structures.STATES.get((c - BlockCode.STRUCT) / 4))
                                        .equals("minecraft:chest")) {
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
    void theSameSeedGivesTheSameChests() {
        for (Structures.Def d : Structures.ALL) {
            if (!building(d)) {
                continue;
            }
            for (int i = 0; i < 5; i++) {
                assertEquals(draw(d, seed(i)), draw(d, seed(i)), d.name());
                Map<Integer, Integer> m = d.chests(seed(i));
                assertEquals(ChestDraw.count(d.rooms(), seed(i)), m.size(), d.name());
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
                            if (Structures.blockOf(s).equals("minecraft:spawner")) {
                                // a zombie spawner of the copy (SpawnerDraw): no facing
                                continue;
                            }
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
