package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** The zombie spawners drawn in each copy of a building ({@link SpawnerDraw}), before any is placed in the world. */
class SpawnerDrawTest {
    static final int COPIES = 300;
    static final int WALKED = 200;
    private static final int[][] SIDE = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private static boolean building(Structures.Def d) {
        return d.type().startsWith("buildings_");
    }

    private static String cls(Structures.Def d) {
        return d.type().substring("buildings_".length());
    }

    private static long seed(int i) {
        return (i + 1) * 0x9E3779B97F4A7C15L;
    }

    private static int gy(Structures.Def d) {
        return d.oy() + d.ground();
    }

    @Test
    void onlyTheBuildingsGetSpawners() {
        int buildings = 0;
        for (Structures.Def d : Structures.ALL) {
            if (!building(d)) {
                for (int i = 0; i < 5; i++) {
                    assertTrue(SpawnerDraw.of(d, seed(i)).isEmpty(), d.name() + " is not a building");
                }
                continue;
            }
            buildings++;
            assertFalse(SpawnerDraw.of(d, seed(0)).isEmpty(), d.name() + " gets spawners");
        }
        assertEquals(10, buildings);
    }

    @Test
    void floorsAndCounts() {
        for (Structures.Def d : Structures.ALL) {
            if (!building(d)) {
                continue;
            }
            Rooms.Layout l = d.rooms();
            int nf = l.floors().size();
            int gy = gy(d);
            boolean small = cls(d).equals("small");
            // the floors that get spawners: under the ground every one, over it one in two from the street floor
            boolean[] gets = new boolean[nf];
            int above = 0;
            for (int f = 0; f < nf; f++) {
                if (l.floors().get(f).y() <= gy) {
                    gets[f] = true;
                } else {
                    gets[f] = above % 2 == 0;
                    above++;
                }
            }
            long total = 0;
            long wanted = 0;
            long guarding = 0;
            long street = 0;
            int least = Integer.MAX_VALUE;
            int most = 0;
            int copiesShort = 0;
            long[] perFloor = new long[nf];
            Set<Integer> countsSeen = new HashSet<>();
            for (int i = 0; i < COPIES; i++) {
                long seed = seed(i);
                List<SpawnerDraw.Spawner> sp = SpawnerDraw.of(d, seed);
                int[] onFloor = new int[nf];
                for (SpawnerDraw.Spawner s : sp) {
                    onFloor[s.spot().floor()]++;
                    guarding += s.guards() ? 1 : 0;
                    street += s.spot().is(Rooms.STREET) ? 1 : 0;
                }
                boolean isShort = false;
                for (int f = 0; f < nf; f++) {
                    int n = SpawnerDraw.count(l, cls(d), gy, f, seed);
                    if (!gets[f]) {
                        assertEquals(0, n, d.name() + ": floor " + f + " gets no spawner");
                    } else if (small) {
                        assertEquals(1, n, d.name() + ": 1 on floor " + f);
                    } else {
                        assertTrue(n >= SpawnerDraw.LARGE_MIN && n <= SpawnerDraw.LARGE_MAX, d.name() + ": " + n);
                        countsSeen.add(n);
                    }
                    assertTrue(onFloor[f] <= n, d.name() + ": floor " + f + " has " + onFloor[f] + " of " + n);
                    // a copy is short when a floor with spots gets fewer than drawn (no room near or away from a chest)
                    final int floor = f;
                    boolean hasSpots = l.spots().stream().anyMatch(s -> s.floor() == floor);
                    isShort |= hasSpots && onFloor[f] < n;
                    wanted += hasSpots ? n : 0;
                    perFloor[f] += onFloor[f];
                }
                copiesShort += isShort ? 1 : 0;
                total += sp.size();
                least = Math.min(least, sp.size());
                most = Math.max(most, sp.size());
            }
            if (!small) {
                assertEquals(4, countsSeen.size(), d.name() + ": every count from 2 to 5 comes up " + countsSeen);
            }
            StringBuilder fl = new StringBuilder();
            for (int f = 0; f < nf; f++) {
                if (gets[f]) {
                    fl.append(' ').append(l.floors().get(f).y() - gy).append(':')
                            .append(String.format("%.1f", (double) perFloor[f] / COPIES));
                }
            }
            System.out.printf("[SpawnerDrawTest] %-28s %-6s %d copies: %.1f spawners on average (%d to %d), %.1f%% of"
                            + " those wanted, %.1f%% guarding a chest, %.1f%% in a lobby open onto the street, copies short"
                            + " %d%n[SpawnerDrawTest]    per floor (height: average):%s%n", d.name(), cls(d), COPIES,
                    (double) total / COPIES, least, most, 100.0 * total / wanted, 100.0 * guarding / total,
                    100.0 * street / total, copiesShort, fl);
        }
    }

    @Test
    void everySpawnerGuardsAChestOrIsAwayFromThemAndTouchesNothing() {
        for (Structures.Def d : Structures.ALL) {
            if (!building(d)) {
                continue;
            }
            Set<Rooms.Spot> spots = new HashSet<>(d.rooms().spots());
            for (int i = 0; i < COPIES; i++) {
                long seed = seed(i);
                List<int[]> chests = SpawnerDraw.chestsOf(d, seed);
                assertEquals(d.chests(seed).size() + keptBarrels(d, seed), chests.size(), d.name());
                List<SpawnerDraw.Spawner> sp = SpawnerDraw.of(d, seed);
                for (int a = 0; a < sp.size(); a++) {
                    Rooms.Spot s = sp.get(a).spot();
                    assertTrue(spots.contains(s), d.name() + ": not a spot " + s);
                    assertFalse(s.is(Rooms.OPEN), d.name() + ": under the open sky " + s);
                    // a lobby open onto the street only once the floor found no room elsewhere: after every other
                    // spawner of its floor
                    if (s.is(Rooms.STREET)) {
                        for (int b = a + 1; b < sp.size(); b++) {
                            Rooms.Spot o = sp.get(b).spot();
                            assertFalse(o.floor() == s.floor() && !o.is(Rooms.STREET), d.name()
                                    + ": a lobby spot taken while the floor had room elsewhere " + s);
                        }
                    }
                    boolean nearAChest = false;
                    for (int[] c : chests) {
                        int dx = Math.abs(s.x() - c[0]);
                        int dy = Math.abs(s.y() - c[1]);
                        int dz = Math.abs(s.z() - c[2]);
                        assertFalse(dx <= 1 && dy <= 1 && dz <= 1, d.name() + ": touches a chest " + s);
                        nearAChest |= dx <= SpawnerDraw.GUARD && dz <= SpawnerDraw.GUARD && dy <= SpawnerDraw.GUARD_DY;
                    }
                    if (sp.get(a).guards()) {
                        assertTrue(nearAChest, d.name() + ": guards no chest " + s);
                    } else {
                        assertFalse(nearAChest, d.name() + ": away from the chests, but near one " + s);
                        assertTrue(ChestDraw.exploration(s), d.name() + ": away from the chests, not exploring " + s);
                    }
                    for (int b = a + 1; b < sp.size(); b++) {
                        Rooms.Spot o = sp.get(b).spot();
                        assertFalse(Math.abs(o.x() - s.x()) <= 1 && Math.abs(o.y() - s.y()) <= 1
                                && Math.abs(o.z() - s.z()) <= 1, d.name() + ": two spawners touch " + s + " " + o);
                    }
                }
                // in front of a drawn chest there is never a spawner (it touches the chest)
                Set<Integer> spawnerCells = new HashSet<>();
                Template t = d.t();
                for (SpawnerDraw.Spawner s : sp) {
                    spawnerCells.add(s.spot().x() + t.sx * (s.spot().z() + t.sz * s.spot().y()));
                }
                for (Map.Entry<Integer, Integer> e : d.chests(seed).entrySet()) {
                    int c = e.getKey();
                    int x = c % t.sx + SIDE[e.getValue()][0];
                    int z = (c / t.sx) % t.sz + SIDE[e.getValue()][1];
                    int y = c / (t.sx * t.sz);
                    assertFalse(spawnerCells.contains(x + t.sx * (z + t.sz * y)), d.name() + ": a spawner in front of a chest");
                }
            }
        }
    }

    private static int keptBarrels(Structures.Def d, long seed) {
        int layer = d.t().sx * d.t().sz;
        int n = 0;
        for (int[] b : d.barrels().values()) {
            n += b[Barrels.FORCED] == 0 && Barrels.kept(b, seed, layer) ? 1 : 0;
        }
        return n;
    }

    @Test
    void theSameSeedGivesTheSameSpawnersAndTheChestsDoNotChange() {
        for (Structures.Def d : Structures.ALL) {
            if (!building(d)) {
                continue;
            }
            for (int i = 0; i < 5; i++) {
                Map<Integer, Integer> before = d.chests(seed(i));
                assertEquals(SpawnerDraw.of(d, seed(i)), SpawnerDraw.of(d, seed(i)), d.name());
                assertEquals(before, d.chests(seed(i)), d.name());
                assertEquals(ChestDraw.count(cls(d), seed(i)), before.size(), d.name());
            }
            Set<List<SpawnerDraw.Spawner>> distinct = new HashSet<>();
            for (int i = 0; i < 50; i++) {
                distinct.add(SpawnerDraw.of(d, seed(i)));
            }
            assertTrue(distinct.size() >= 48, d.name() + ": copies with the same spawners");
        }
    }

    @Test
    void theCodeOfACopyPlacesItsSpawnersInEveryRotation() {
        assertTrue(Structures.SPAWNER_STATE >= 0, "zombieSpawners on in the tests");
        assertEquals("minecraft:spawner", Structures.STATES.get(Structures.SPAWNER_STATE));
        assertEquals(Structures.STATES.size() - 1, Structures.SPAWNER_STATE, "the spawner's state id comes last");
        for (Structures.Def d : Structures.ALL) {
            long seed = seed(3);
            Set<Integer> cells = new HashSet<>();
            Template t = d.t();
            for (SpawnerDraw.Spawner s : SpawnerDraw.of(d, seed)) {
                cells.add(s.spot().x() + t.sx * (s.spot().z() + t.sz * s.spot().y()));
            }
            assertEquals(cells, d.spawners(seed), d.name());
            for (int rot = 0; rot < 4; rot++) {
                int found = 0;
                for (int y = -d.ground(); y < d.above(); y++) {
                    for (int z = 0; z < d.sizeZ(rot); z++) {
                        for (int x = 0; x < d.sizeX(rot); x++) {
                            int c = d.code(x, y, z, rot, seed);
                            if (c >= BlockCode.STRUCT && (c - BlockCode.STRUCT) / 4 == Structures.SPAWNER_STATE) {
                                assertEquals(BlockCode.AIR, d.code(x, y, z, rot), d.name() + ": a spawner on air");
                                found++;
                            }
                        }
                    }
                }
                assertEquals(cells.size(), found, d.name() + " rot " + rot);
            }
        }
    }

    @Test
    void copiesWithSpawnersWalkedAgainLoseNothing() {
        // each copy with its barrels kept (as chests) or taken out, its chests and its spawners in place: every chest
        // reached with someone standing in front of it, every container reached before still reached, and every place
        // reached with the chests alone still reached with the spawners
        List<String> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        for (Structures.Def d : Structures.ALL) {
            if (!building(d)) {
                continue;
            }
            Template t = d.t();
            int layer = t.sx * t.sz;
            IntStream.range(0, WALKED).parallel().forEach(i -> {
                long seed = seed(i);
                Map<Integer, String> base = new HashMap<>();
                for (Map.Entry<Integer, int[]> e : d.barrels().entrySet()) {
                    if (!Barrels.kept(e.getValue(), seed, layer)) {
                        base.put(e.getKey(), "minecraft:air");
                    }
                }
                Map<Integer, String> placed = new HashMap<>(base);
                Map<Integer, Integer> chests = d.chests(seed);
                for (Map.Entry<Integer, Integer> e : chests.entrySet()) {
                    placed.put(e.getKey(), "minecraft:chest[facing=" + Rooms.FACINGS[e.getValue()]
                            + ",type=single,waterlogged=false]");
                }
                Access.Walk wc = walk(d, t.withStates(placed));
                Set<Integer> spawnerCells = new HashSet<>();
                for (SpawnerDraw.Spawner s : SpawnerDraw.of(d, seed)) {
                    int c = s.spot().x() + t.sx * (s.spot().z() + t.sz * s.spot().y());
                    placed.put(c, "minecraft:spawner");
                    spawnerCells.add(c);
                }
                Template t0 = base.isEmpty() ? t : t.withStates(base);
                Access.Walk w0 = walk(d, t0);
                Access.Walk w1 = walk(d, t.withStates(placed));
                // the spawners close no way: every place where the feet stood with the chests alone is still reached,
                // but the spawner's own cell and the one under it (the head would be in the spawner)
                for (int y = 0; y < wc.fh; y++) {
                    for (int z = 0; z < wc.fd; z++) {
                        for (int x = 0; x < wc.fw; x++) {
                            if (wc.dist[wc.i(x, y, z)] < 0) {
                                continue;
                            }
                            int tx = x + wc.fx0;
                            int ty = y + wc.fy0;
                            int tz = z + wc.fz0;
                            int c = tx + t.sx * (tz + t.sz * ty);
                            if (spawnerCells.contains(c) || spawnerCells.contains(c + layer)) {
                                continue;
                            }
                            if (!w1.reachedAt(tx, ty, tz)) {
                                failures.add(d.name() + " seed " + i + ": a spawner cuts off the feet at " + tx + ","
                                        + ty + "," + tz);
                            }
                        }
                    }
                }
                for (Map.Entry<Integer, Integer> e : chests.entrySet()) {
                    int c = e.getKey();
                    int x = c % t.sx;
                    int z = (c / t.sx) % t.sz;
                    int y = c / layer;
                    if (!w1.reaches(x, y, z)) {
                        failures.add(d.name() + " seed " + i + ": chest out of reach " + x + "," + y + "," + z);
                    }
                    if (!w1.reachedAt(x + SIDE[e.getValue()][0], y, z + SIDE[e.getValue()][1])) {
                        failures.add(d.name() + " seed " + i + ": nobody stands in front of " + x + "," + y + "," + z);
                    }
                }
                for (int y = 0; y < t.sy; y++) {
                    for (int z = 0; z < t.sz; z++) {
                        for (int x = 0; x < t.sx; x++) {
                            int p = t0.at(x, y, z);
                            if (p >= 0 && Access.container(Access.bare(t0.palette.get(p))) && w0.reaches(x, y, z)
                                    && !w1.reaches(x, y, z)) {
                                failures.add(d.name() + " seed " + i + ": a container cut off at " + x + "," + y + "," + z);
                            }
                        }
                    }
                }
            });
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures: "
                + String.join("; ", failures.subList(0, Math.min(20, failures.size()))));
    }

    private static Access.Walk walk(Structures.Def d, Template t) {
        String[] placed = new String[t.palette.size()];
        for (int p = 0; p < placed.length; p++) {
            placed[p] = Structures.swap(t.palette.get(p));
        }
        return Access.walk(t, placed, d.ox(), d.oy(), d.oz(), d.w(), d.h(), d.d(), d.ground());
    }
}
