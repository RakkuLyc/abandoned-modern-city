package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Cities, the highway network and the per-column plan on synthetic terrains (flat, hills, mountains, islands,
 * mixed, canyons), with fixed seeds.
 */
class WorldPlanTest {
    static final long[] SEEDS = {20261104L, -3297019724594858492L, 42L};
    static final int CELLS = 4; // cells -CELLS..CELLS

    static File renderDir() {
        return new File(System.getProperty("amcs.renderDir", "build/test-renders"));
    }

    static WorldPlan plan(long seed, TestTerrain.Kind kind) {
        return new WorldPlan(seed, Params.DEFAULT, new TestTerrain(seed, kind));
    }

    static List<City> allCities(WorldPlan p) {
        List<City> out = new ArrayList<>();
        for (int cj = -CELLS; cj <= CELLS; cj++) {
            for (int ci = -CELLS; ci <= CELLS; ci++) {
                City c = p.city(ci, cj);
                if (c != null) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    @Test
    void villageCreaturesAreRemovedInTheCityAndItsMargin() {
        WorldPlan p = plan(SEEDS[0], TestTerrain.Kind.FLAT);
        City.Hit hit = new City.Hit();
        int cities = 0;
        for (City c : allCities(p)) {
            if (++cities > 30) {
                break;
            }
            // the center is a street crossing or a lot
            assertTrue(c.within(c.cx + 0.5, c.cz + 0.5, City.VILLAGE_MARGIN, hit), c + " center");
            // walking out from the center in x: inside, then within the margin, then out; the limit is the margin
            double inside = Double.NaN;
            double out = Double.NaN;
            for (int dx = 0; dx < 700; dx++) {
                boolean in = c.within(c.cx + dx + 0.5, c.cz + 0.5, City.VILLAGE_MARGIN, hit);
                boolean city = hit.road || hit.lot;
                if (city) {
                    inside = dx;
                }
                if (!in) {
                    out = dx;
                    break;
                }
            }
            assertFalse(Double.isNaN(out), c + " never out");
            assertEquals(City.VILLAGE_MARGIN, out - inside - 1, 1.0, c + " margin beyond the last street column");
            assertFalse(c.within(c.cx + 700.5, c.cz + 0.5, City.VILLAGE_MARGIN, hit), c + " far away");
        }
    }

    @Test
    void parkBenchesSitOnTheEdgeOfThePathsFacingThem() {
        WorldPlan p = plan(SEEDS[0], TestTerrain.Kind.FLAT);
        int parks = 0;
        int benches = 0;
        int seats = 0;
        long pathLength = 0;
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (City c : allCities(p)) {
            int n = c.gridHalf();
            for (int bj = -n; bj <= n; bj++) {
                for (int bi = -n; bi <= n; bi++) {
                    for (Lot l : c.lots(bi, bj)) {
                        if (l.park == null) {
                            continue;
                        }
                        Park park = l.park;
                        parks++;
                        int count = park.benchCount();
                        int pathCells = 0;
                        for (int z = l.z0; z <= l.z1; z++) {
                            for (int x = l.x0; x <= l.x1; x++) {
                                pathCells += park.path(x, z) ? 1 : 0;
                            }
                        }
                        pathLength += pathCells / 3;
                        List<int[]> seatCells = new ArrayList<>();
                        for (var e : park.benches.entrySet()) {
                            int x = (int) (e.getKey() >> 32);
                            int z = (int) (long) e.getKey();
                            int code = e.getValue();
                            assertFalse(park.path(x, z), "bench on a path at " + x + "," + z);
                            assertEquals(code, park.code(x, 1, z, 0), "bench block");
                            // nothing grows through a bench (a crown high above may pass)
                            assertEquals(park.trees.code(x, 2, z, 0), park.code(x, 2, z, 0), "a plant through a bench");
                            if (code >= BlockCode.BENCH_SEAT && code < BlockCode.BENCH_SEAT + 4) {
                                seats++;
                                int back = code - BlockCode.BENCH_SEAT;
                                int front = (back + 2) & 3;
                                // who sits looks at the path; the back is away from it
                                assertTrue(park.path(x + dirs[front][0], z + dirs[front][1]), "seat without a path in front at " + x + "," + z);
                                assertFalse(park.path(x + dirs[back][0], z + dirs[back][1]), "path behind a seat at " + x + "," + z);
                                seatCells.add(new int[]{x, z});
                            } else {
                                // an armrest: its panel against a seat (the side opposite to its facing)
                                int f = code - BlockCode.BENCH_ARM;
                                int sx = x - dirs[f][0];
                                int sz = z - dirs[f][1];
                                Integer next = park.benches.get(((long) sx << 32) | (sz & 0xffffffffL));
                                assertTrue(next != null && next >= BlockCode.BENCH_SEAT && next < BlockCode.BENCH_SEAT + 4,
                                        "armrest not against a seat at " + x + "," + z);
                            }
                        }
                        benches += count;
                        assertTrue(count >= 1, "park without a bench");
                        // benches apart: seats of different benches 12+ away (seats of one bench are side by side)
                        for (int[] a : seatCells) {
                            for (int[] b : seatCells) {
                                int d = Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1]));
                                assertTrue(d <= 1 || d >= Park.BENCH_APART - 2, "benches too close " + d);
                            }
                        }
                    }
                }
            }
        }
        double perBench = pathLength / (double) benches;
        System.out.printf("[WorldPlanTest] park benches: %d in %d parks, %d seats, one per %.1f blocks of path%n",
                benches, parks, seats, perBench);
        assertTrue(perBench >= 12 && perBench <= 30, "one bench per " + perBench + " blocks of path");
    }

    @Test
    void streetLightsStandOnTheSidewalksWithTheirArmsOverTheLanes() {
        WorldPlan p = plan(SEEDS[0], TestTerrain.Kind.FLAT);
        int[] variants = new int[StreetLights.LAMP_VARIANTS];
        int poles = 0;
        int cities = 0;
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        City.Hit hit = new City.Hit();
        for (City c : allCities(p)) {
            if (++cities > 20) {
                break;
            }
            int r = c.radius + 40;
            Map<Long, int[]> parts = new HashMap<>();
            Map<Long, int[]> info = new HashMap<>();
            for (int z = c.cz - r; z <= c.cz + r; z++) {
                for (int x = c.cx - r; x <= c.cx + r; x++) {
                    c.query(x + 0.5, z + 0.5, hit);
                    int[] lp = StreetLights.lampParts(hit);
                    if (lp == null) {
                        continue;
                    }
                    assertTrue(hit.road && hit.count == 1 && !hit.crosswalk, c + " light off a street segment at " + x + "," + z);
                    long k = ((long) x << 32) | (z & 0xffffffffL);
                    parts.put(k, lp);
                    info.put(k, new int[]{hit.lamp, hit.lampVariant, hit.lampFace});
                }
            }
            for (var e : parts.entrySet()) {
                int x = (int) (e.getKey() >> 32);
                int z = (int) (long) e.getKey();
                int[] lp = e.getValue();
                int[] in = info.get(e.getKey());
                int offset = in[0];
                int v = in[1];
                String where = c + " " + x + "," + z + " offset " + offset + " variant " + v;
                if (offset == 0) {
                    poles++;
                    variants[v]++;
                    for (int dy = 1; dy <= StreetLights.LAMP_POST; dy++) {
                        assertTrue(lp[dy] != BlockCode.KEEP && !StreetLights.lampVine(lp[dy]), where + " post hole at " + dy);
                    }
                    assertEquals(v == StreetLights.V_OVERGROWN, lp[StreetLights.LAMP_TOP] == BlockCode.LAMP + StreetLights.L_LEAVES, where + " tuft");
                } else if (offset == -1) {
                    // a vine up the back of the post: the post right beside it at the same height
                    int px = x + dirs[in[2]][0];
                    int pz = z + dirs[in[2]][1];
                    int[] post = parts.get(((long) px << 32) | (pz & 0xffffffffL));
                    assertNotNull(post, where + " vine without its post");
                    for (int dy = 1; dy < lp.length; dy++) {
                        if (lp[dy] == BlockCode.KEEP) {
                            continue;
                        }
                        assertEquals(BlockCode.LAMP + StreetLights.L_VINE + 1 + in[2], lp[dy], where + " not a vine facing the post");
                        assertTrue(dy >= 3, where + " vine in the sidewalk's tall grass");
                        assertTrue(post[dy] != BlockCode.KEEP && !StreetLights.lampVine(post[dy]), where + " vine on nothing at " + dy);
                    }
                } else {
                    // over the lanes: nothing under the arm's height but the fallen lamp
                    for (int dy = 1; dy < StreetLights.LAMP_ARM_Y - 1; dy++) {
                        boolean fallen = v == StreetLights.V_FALLEN && offset == StreetLights.LAMP_ARM && dy == 1;
                        assertEquals(fallen ? BlockCode.LAMP + StreetLights.L_GRAY_GLASS : BlockCode.KEEP, lp[dy], where + " at " + dy);
                    }
                    if (lp[StreetLights.LAMP_ARM_Y - 1] == BlockCode.LAMP + StreetLights.L_VINE) {
                        assertTrue(lp[StreetLights.LAMP_ARM_Y] != BlockCode.KEEP, where + " hanging vine without the arm");
                    }
                    if (v == StreetLights.V_BROKEN) {
                        assertEquals(offset <= 2, lp[StreetLights.LAMP_ARM_Y] != BlockCode.KEEP, where + " broken arm");
                    }
                }
            }
        }
        double perCity = poles / 20.0;
        System.out.printf("[WorldPlanTest] street lights: %.1f posts per city, variants %s%n", perCity, Arrays.toString(variants));
        // 144 places per city, 10% gone; the 5 variants share the rest
        assertTrue(perCity > 144 * 0.8 && perCity < 144 * 0.95, "posts per city " + perCity);
        for (int n : variants) {
            assertTrue(Math.abs(n - poles / 5.0) < poles * 0.06, "variants " + Arrays.toString(variants));
        }
        // in the chunk plans: posts and fallen lamps stand on the street (not drivable), the rest of a light does not
        City c0 = allCities(p).get(0);
        int posts = 0;
        int fallen = 0;
        int lit = 0;
        for (int cz = (c0.cz - 240) >> 4; cz <= (c0.cz + 240) >> 4; cz++) {
            for (int cx = (c0.cx - 240) >> 4; cx <= (c0.cx + 240) >> 4; cx++) {
                ChunkPlan cp = p.chunk(cx, cz);
                for (int i = 0; i < 256; i++) {
                    if ((cp.flags[i] & ChunkPlan.F_LAMP) != 0) {
                        lit++;
                    }
                    if ((cp.flags[i] & ChunkPlan.F_POLE) != 0) {
                        assertTrue((cp.flags[i] & ChunkPlan.F_LAMP) != 0);
                        assertFalse(cp.drivable(i));
                        if (cp.zone[i] == ChunkPlan.SIDEWALK) {
                            posts++;
                        } else {
                            fallen++;
                        }
                    }
                }
            }
        }
        assertTrue(posts > 100 && fallen > 5 && lit > posts * 4, "posts " + posts + ", fallen lamps " + fallen + ", light columns " + lit);
    }

    @Test
    void cutCrownsHangOverSidewalksAndCores() {
        Map<String, Integer> why = new TreeMap<>();
        int placed = 0;
        WorldPlan p = plan(SEEDS[0], TestTerrain.Kind.FLAT);
        Set<Long> chunks = new HashSet<>();
        for (City c : allCities(p)) {
            int n = c.gridHalf();
            for (int bj = -n; bj <= n; bj++) {
                for (int bi = -n; bi <= n; bi++) {
                    for (Lot l : c.lots(bi, bj)) {
                        for (int[] o : l.overhangs()) {
                            chunks.add(((long) (o[0] >> 4) << 32) | ((o[2] >> 4) & 0xffffffffL));
                        }
                    }
                }
            }
        }
        for (long k : chunks) {
            int cx = (int) (k >> 32);
            int cz = (int) k;
            Map<Long, Integer> m = Crowns.forChunk(p, cx, cz, why);
            placed += m.size();
            for (long pos : m.keySet()) {
                // inside the chunk asked for
                int x = (int) (pos >> 38);
                int z = (int) (pos << 26 >> 38);
                assertEquals(cx, x >> 4);
                assertEquals(cz, z >> 4);
            }
        }
        System.out.println("[WorldPlanTest] crown leaves near " + chunks.size() + " chunks: " + why + ", placed " + placed);
        // the crowns hang over the core of the blocks and, high up, over the sidewalks
        assertTrue(why.getOrDefault("core", 0) > 0, "no crown over a core " + why);
        assertTrue(why.getOrDefault("street", 0) > 0, "no crown over a sidewalk " + why);
        assertEquals(0, why.getOrDefault("another structure", 0), "crown into another structure " + why);
    }

    @Test
    void cityStreetsHaveNoDeadEndsAndLotsAreBuildable() throws IOException {
        int cities = 0;
        int lots = 0;
        int parks = 0;
        int parkStructures = 0;
        int buildingsPerCity = 0;
        int blocks = 0;
        int structures = 0;
        int houses = 0;
        for (TestTerrain.Kind kind : TestTerrain.Kind.values()) {
            WorldPlan p = plan(SEEDS[0], kind);
            City.Hit hit = new City.Hit();
            for (City c : allCities(p)) {
                cities++;
                int n = c.gridHalf();
                assertTrue(c.isCityBlock(0, 0), "center block");
                // spawn limits per city (pharmacies 3, workshops 2, gas stations 2)
                Map<String, Integer> groups = new HashMap<>();
                Map<String, Integer> buildings = new HashMap<>();
                for (int bj = -n; bj <= n; bj++) {
                    for (int bi = -n; bi <= n; bi++) {
                        if (!c.lots(bi, bj).isEmpty()) {
                            blocks++;
                        }
                        // and never two of the same structure in one block
                        Set<String> inBlock = new HashSet<>();
                        for (Lot l : c.lots(bi, bj)) {
                            if (l.building != null) {
                                assertTrue(inBlock.add(l.building.name()), c + " block " + bi + "," + bj + " has two "
                                        + l.building.name());
                                structures++;
                                if (l.building.type().startsWith("houses")) {
                                    houses++;
                                }
                            }
                            if (l.building != null && l.building.group() != null) {
                                groups.merge(l.building.group(), 1, Integer::sum);
                            }
                            if (l.building != null && l.building.onePerCity()) {
                                buildings.merge(l.building.model(), 1, Integer::sum);
                            }
                        }
                    }
                }
                for (var e : groups.entrySet()) {
                    assertTrue(e.getValue() <= Structures.LIMITS.get(e.getKey()), c + " " + e);
                }
                // never the same building twice in a city
                for (var e : buildings.entrySet()) {
                    assertEquals(1, e.getValue(), c + " has " + e.getValue() + " " + e.getKey());
                }
                buildingsPerCity += buildings.size();
                // park blocks: a park of 110 x 64 in a corner, structures 4+ blocks away from it
                for (int bj = -n; bj <= n; bj++) {
                    for (int bi = -n; bi <= n; bi++) {
                        List<Lot> ls = c.lots(bi, bj);
                        Lot park = ls.stream().filter(l -> l.type == Lot.Type.PARK).findFirst().orElse(null);
                        if (park == null) {
                            continue;
                        }
                        parks++;
                        if (parks == 1) {
                            // a picture of a park block (detail of its chunks)
                            PlanRender.write(PlanRender.detail(p, (wildX0(ls) >> 4) - 1, (wildZ0(ls) >> 4) - 1,
                                    (ls.get(ls.size() - 1).x1 >> 4) + 1, (ls.get(ls.size() - 1).z1 >> 4) + 1),
                                    new File(renderDir(), "park_block.png"));
                        }
                        int pw = park.x1 - park.x0 + 1;
                        int ph = park.z1 - park.z0 + 1;
                        assertTrue(pw == City.PARK_LONG && ph == City.PARK_SHORT || pw == City.PARK_SHORT && ph == City.PARK_LONG,
                                c + " park " + pw + " x " + ph);
                        Lot wild = ls.get(ls.size() - 1);
                        assertEquals(Lot.Type.WILD, wild.type);
                        boolean corner = (park.x0 == wild.x0 || park.x1 == wild.x1) && (park.z0 == wild.z0 || park.z1 == wild.z1);
                        assertTrue(corner, c + " park not in a corner");
                        for (Lot l : ls) {
                            if (l.building == null) {
                                continue;
                            }
                            parkStructures++;
                            int gx = Math.max(l.x0, park.x0) - Math.min(l.x1, park.x1) - 1;
                            int gz = Math.max(l.z0, park.z0) - Math.min(l.z1, park.z1) - 1;
                            assertTrue(Math.max(gx, gz) >= Ring.GAP_MIN, c + " structure " + l.building.name() + " touches the park");
                        }
                    }
                }
                for (int j = -n - 1; j <= n + 2; j++) {
                    for (int i = -n - 1; i <= n + 2; i++) {
                        int deg = (c.xSegment(i, j) ? 1 : 0) + (c.xSegment(i, j - 1) ? 1 : 0)
                                + (c.zSegment(i, j) ? 1 : 0) + (c.zSegment(i - 1, j) ? 1 : 0);
                        assertTrue(deg != 1, c + " dead end at node " + i + "," + j);
                        if (deg == 2) {
                            boolean straight = c.xSegment(i, j) && c.xSegment(i, j - 1)
                                    || c.zSegment(i, j) && c.zSegment(i - 1, j);
                            assertFalse(straight, "degree-2 nodes are corners");
                            assertTrue(c.cornerAt(i, j) != 0, "corner arc at " + i + "," + j);
                        }
                    }
                }
                for (int j = -n; j <= n; j++) {
                    for (int i = -n; i <= n; i++) {
                        for (Lot lot : c.lots(i, j)) {
                            lots++;
                            for (int z = lot.z0; z <= lot.z1; z++) {
                                for (int x = lot.x0; x <= lot.x1; x++) {
                                    c.query(x + 0.5, z + 0.5, hit);
                                    assertTrue(hit.lot && !hit.road, c + " lot column " + x + "," + z + " is not buildable: road=" + hit.road + " lot=" + hit.lot + " lotrect " + lot.x0 + "," + lot.z0 + ".." + lot.x1 + "," + lot.z1 + " " + lot.type + " block " + i + "," + j + " U=" + c.U(i) + " V=" + c.V(j));
                                    assertEquals(i, hit.bi);
                                    assertEquals(j, hit.bj);
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println("[WorldPlanTest] parks " + parks + " with " + parkStructures + " structures");
        System.out.printf("[WorldPlanTest] buildings per city %.2f (each model at most once)%n", buildingsPerCity / (double) cities);
        System.out.println("[WorldPlanTest] cities " + cities + ", lots " + lots);
        System.out.printf("[WorldPlanTest] %d structures in %d blocks: %.2f per block, %.2f in the blocks without a park, "
                        + "%.2f in those with one; %.1f%% houses%n", structures, blocks, structures / (double) blocks,
                (structures - parkStructures) / (double) (blocks - parks), parkStructures / (double) parks,
                100.0 * houses / structures);
        assertTrue(cities > 40, "too few cities: " + cities);
    }

    @Test
    void streetWidthIsConstantOnStraightStreets() {
        // on a city without waves the street bands are exactly 11 (street) or 15 (avenue) columns wide
        WorldPlan p = plan(SEEDS[0], TestTerrain.Kind.FLAT);
        City.Hit hit = new City.Hit();
        int checked = 0;
        for (City c : allCities(p)) {
            if (c.warp.maxSlope() != 0) {
                continue;
            }
            int n = c.gridHalf();
            for (int i = -n; i <= n + 1; i++) {
                for (int j = -n; j <= n; j++) {
                    if (!c.xSegment(i, j) || c.cornerAt(i, j) != 0 || c.cornerAt(i, j + 1) != 0) {
                        continue;
                    }
                    int z = (int) Math.floor(c.V(j) + c.SZ / 2.0);
                    int xc = (int) Math.floor(c.U(i));
                    int width = 0;
                    for (int x = xc - 24; x <= xc + 24; x++) {
                        c.query(x + 0.5, z + 0.5, hit);
                        if (hit.road) {
                            width++;
                        }
                    }
                    assertEquals(33, width, c + " line " + i + " row " + j);
                    // zebra crossing: 3 rows starting 2 rows from the crossing, 14 white stripes across the 27 lanes
                    for (int t = 0; t <= 6; t++) {
                        int zt = (int) Math.round(c.V(j) - 0.5) + (int) Params.STREET_HW + 1 + t;
                        int stripes = 0;
                        for (int x = xc - 24; x <= xc + 24; x++) {
                            c.query(x + 0.5, zt + 0.5, hit);
                            if (hit.crosswalk) {
                                stripes++;
                            }
                        }
                        boolean row = t >= City.CROSSWALK_GAP && t < City.CROSSWALK_GAP + City.CROSSWALK_DEPTH;
                        assertEquals(row ? 14 : 0, stripes, c + " crosswalk row " + t + " line " + i + " row " + j);
                    }
                    checked++;
                }
            }
        }
        assertTrue(checked > 20, "checked " + checked);
    }

    @Test
    void highwayNetworkIsConsistentAndWellConnected() {
        StringBuilder report = new StringBuilder();
        for (TestTerrain.Kind kind : TestTerrain.Kind.values()) {
            for (long seed : SEEDS) {
                WorldPlan p = plan(seed, kind);
                for (City a : allCities(p)) {
                    for (City b : p.neighbors(a)) {
                        assertTrue(p.neighbors(b).contains(a), "Gabriel graph is symmetric");
                    }
                }
                NetworkStats st = NetworkStats.of(p, -CELLS, -CELLS, CELLS, CELLS);
                List<Highway> finals = st.highways;
                // geometric guarantees of every kept highway
                Map<Long, Set<String>> exitsUsed = new HashMap<>();
                for (Highway h : finals) {
                    double[] pa = h.a.exitWorld(h.exitA);
                    double[] pb = h.b.exitWorld(h.exitB);
                    assertEquals(pa[0], h.x(0), 1e-9);
                    assertEquals(pa[1], h.z(0), 1e-9);
                    assertEquals(pb[0], h.x(h.length), 1e-9);
                    assertEquals(pb[1], h.z(h.length), 1e-9);
                    assertTrue(h.minCurveRadius() >= Params.MIN_CURVE_RADIUS, h + " curve radius " + h.minCurveRadius());
                    assertTrue(h.step > 0.9 && h.step < 1.1, "point spacing " + h.step);
                    assertTrue(h.length <= Params.DEFAULT.highwayMaxLength(), "too long " + h);
                    // one highway per crossing (a corner has two exits, one per direction)
                    assertTrue(exitsUsed.computeIfAbsent(h.a.id, k -> new HashSet<>()).add(h.exitA.i() + "," + h.exitA.j()),
                            "exit used twice");
                    assertTrue(exitsUsed.computeIfAbsent(h.b.id, k -> new HashSet<>()).add(h.exitB.i() + "," + h.exitB.j()),
                            "exit used twice");
                    for (Highway f : finals) {
                        if (f != h) {
                            assertFalse(WorldPlan.tooClose(h, f), h + " too close to " + f);
                        }
                    }
                }
                String line = String.format("%-9s seed %-20d %s", kind, seed, st);
                report.append(line).append('\n');
                if (seed == SEEDS[0]) {
                    try {
                        int r = (CELLS + 1) * Params.DEFAULT.cellSize();
                        PlanRender.write(PlanRender.network(p, -r, -r, r, r, 8), new File(renderDir(), "network_" + kind + ".png"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                for (City c : allCities(p)) {
                    assertTrue(p.highways(c).size() <= City.EXITS, c + " has more than " + City.EXITS + " highways");
                }
                switch (kind) {
                    case FLAT, HILLS -> {
                        assertTrue(st.keptRatio() >= 0.85, kind + " keeps too few links: " + line);
                        assertTrue(st.isolatedRatio() <= 0.05, kind + " leaves too many cities alone: " + line);
                    }
                    case MOUNTAINS, RIVERS, CANYONS -> assertTrue(st.isolatedRatio() <= 0.15,
                            kind + " leaves too many cities alone: " + line);
                    default -> {
                        // islands and mixed terrain: cities across the sea stay apart, which is expected
                    }
                }
                if (kind != TestTerrain.Kind.ISLANDS) {
                    assertTrue(st.cities >= st.cells / 2, kind + " has too few cities: " + line);
                }
            }
        }
        System.out.println("[WorldPlanTest] network\n" + report);
    }

    private static long find(Map<Long, Long> p, long x) {
        while (p.get(x) != x) {
            p.put(x, p.get(p.get(x)));
            x = p.get(x);
        }
        return x;
    }

    private static void union(Map<Long, Long> p, long a, long b) {
        p.put(find(p, a), find(p, b));
    }

    private static int wildX0(List<Lot> ls) {
        return ls.get(ls.size() - 1).x0;
    }

    private static int wildZ0(List<Lot> ls) {
        return ls.get(ls.size() - 1).z0;
    }
}
