package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    void streetLightsStandOnTheSidewalksWithTheirArmsOverTheLanes() {
        WorldPlan p = plan(SEEDS[0], TestTerrain.Kind.FLAT);
        int poles = 0;
        int arms = 0;
        int armless = 0;
        int cities = 0;
        City.Hit hit = new City.Hit();
        for (City c : allCities(p)) {
            if (++cities > 20) {
                break;
            }
            int r = c.radius + 40;
            java.util.Map<Long, Byte> lamp = new java.util.HashMap<>();
            for (int z = (int) c.cz - r; z <= (int) c.cz + r; z++) {
                for (int x = (int) c.cx - r; x <= (int) c.cx + r; x++) {
                    c.query(x + 0.5, z + 0.5, hit);
                    if (hit.lamp != City.LAMP_NONE) {
                        assertTrue(hit.road && hit.count == 1, c + " light outside a street segment at " + x + "," + z);
                        assertFalse(hit.crosswalk, c + " light on a crosswalk at " + x + "," + z);
                        lamp.put(((long) x << 32) | (z & 0xffffffffL), hit.lamp);
                    }
                }
            }
            java.util.Set<Long> armOfAPole = new java.util.HashSet<>();
            for (var e : lamp.entrySet()) {
                if (e.getValue() != City.LAMP_POLE) {
                    continue;
                }
                poles++;
                int x = (int) (e.getKey() >> 32);
                int z = (int) (long) e.getKey();
                int found = 0;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    boolean whole = true;
                    for (int k = 1; k <= City.LAMP_ARM; k++) {
                        Byte b = lamp.get(((long) (x + k * d[0]) << 32) | ((z + k * d[1]) & 0xffffffffL));
                        byte want = k == City.LAMP_ARM ? City.LAMP_GLASS : City.LAMP_ARM_PART;
                        whole &= b != null && b == want;
                    }
                    if (whole) {
                        found++;
                        for (int k = 1; k <= City.LAMP_ARM; k++) {
                            armOfAPole.add(((long) (x + k * d[0]) << 32) | ((z + k * d[1]) & 0xffffffffL));
                        }
                    }
                }
                // one arm toward the lanes, or none (the abandoned ones that lost it)
                assertTrue(found <= 1, c + " pole with two arms at " + x + "," + z);
                if (found == 1) {
                    arms++;
                } else {
                    armless++;
                }
            }
            // no arm without its post
            for (var e : lamp.entrySet()) {
                if (e.getValue() != City.LAMP_POLE) {
                    assertTrue(armOfAPole.contains(e.getKey()), c + " arm without a post at " + (e.getKey() >> 32) + "," + (int) (long) e.getKey());
                }
            }
        }
        // a 2 x 2 city has 12 segments (6 of 180, 6 of 120) with 2 x 7 or 2 x 5 places: 144; ~10% gone, ~5% armless
        double perCity = poles / 20.0;
        System.out.printf("[WorldPlanTest] street lights: %.1f posts per city, %d with arm, %d without%n", perCity, arms, armless);
        assertTrue(perCity > 144 * 0.8 && perCity < 144 * 0.95, "posts per city " + perCity);
        assertTrue(armless > 0 && armless < poles * 0.12, "armless " + armless);
        // in the chunk plans: the post on the sidewalk (not drivable), the arm over the lanes
        City c0 = allCities(p).get(0);
        int checked = 0;
        for (int cz = ((int) c0.cz - 240) >> 4; cz <= ((int) c0.cz + 240) >> 4; cz++) {
            for (int cx = ((int) c0.cx - 240) >> 4; cx <= ((int) c0.cx + 240) >> 4; cx++) {
                ChunkPlan cp = p.chunk(cx, cz);
                for (int i = 0; i < 256; i++) {
                    if ((cp.flags[i] & ChunkPlan.F_POLE) != 0) {
                        checked++;
                        assertEquals(ChunkPlan.SIDEWALK, cp.zone[i]);
                        assertFalse(cp.drivable(i));
                    }
                    if ((cp.flags[i] & ChunkPlan.F_ARM) != 0) {
                        assertTrue(cp.zone[i] == ChunkPlan.LANE || cp.zone[i] == ChunkPlan.MARK, "arm zone " + cp.zone[i]);
                        assertTrue(cp.drivable(i));
                    }
                }
            }
        }
        assertTrue(checked > 100, "posts in the chunk plans " + checked);
    }

    @Test
    void cutCrownsHangOverSidewalksAndCores() {
        java.util.Map<String, Integer> why = new java.util.TreeMap<>();
        int placed = 0;
        WorldPlan p = plan(SEEDS[0], TestTerrain.Kind.FLAT);
        java.util.Set<Long> chunks = new java.util.HashSet<>();
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
            java.util.Map<Long, Integer> m = Crowns.forChunk(p, cx, cz, why);
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
    void cityStreetsHaveNoDeadEndsAndLotsAreBuildable() throws java.io.IOException {
        int cities = 0;
        int lots = 0;
        int parks = 0;
        int parkStructures = 0;
        int buildingsPerCity = 0;
        int[] cutHistogram = new int[City.MAX_CUTS + 1];
        for (TestTerrain.Kind kind : TestTerrain.Kind.values()) {
            WorldPlan p = plan(SEEDS[0], kind);
            City.Hit hit = new City.Hit();
            for (City c : allCities(p)) {
                cities++;
                int n = c.gridHalf();
                assertTrue(c.isCityBlock(0, 0), "center block");
                cutHistogram[c.streetCuts()]++;
                // spawn limits per city (pharmacies 3, workshops 2, gas stations 2)
                java.util.Map<String, Integer> groups = new java.util.HashMap<>();
                java.util.Map<String, Integer> buildings = new java.util.HashMap<>();
                for (int bj = -n; bj <= n; bj++) {
                    for (int bi = -n; bi <= n; bi++) {
                        // and never two of the same structure in one block
                        java.util.Set<String> inBlock = new java.util.HashSet<>();
                        for (Lot l : c.lots(bi, bj)) {
                            if (l.building != null) {
                                assertTrue(inBlock.add(l.building.name()), c + " block " + bi + "," + bj + " has two "
                                        + l.building.name());
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
                // never the same building twice in a city (asked by the user)
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
                assertEquals(c.streetCuts(), countRemoved(c), c + " removed segments");
                for (int j = -n - 1; j <= n + 2; j++) {
                    for (int i = -n - 1; i <= n + 2; i++) {
                        int deg = (c.xSegment(i, j) ? 1 : 0) + (c.xSegment(i, j - 1) ? 1 : 0)
                                + (c.zSegment(i, j) ? 1 : 0) + (c.zSegment(i - 1, j) ? 1 : 0);
                        assertTrue(deg != 1, c + " dead end at node " + i + "," + j);
                        if (deg == 2 && c.streetCuts() == 0) {
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
        System.out.println("[WorldPlanTest] cities " + cities + ", lots " + lots + ", removed segments per city "
                + java.util.Arrays.toString(cutHistogram));
        int withCuts = 0;
        for (int k = 1; k <= City.MAX_CUTS; k++) {
            withCuts += cutHistogram[k];
        }
        if (City.MAX_CUTS == 0) {
            assertEquals(0, withCuts, "street cuts are switched off");
        } else {
            assertTrue(withCuts > cities / 2, "too few cities with removed streets");
            assertTrue(cutHistogram[City.MAX_CUTS] > 0, "no city with " + City.MAX_CUTS + " removed streets");
        }
        assertTrue(cities > 40, "too few cities: " + cities);
    }

    @Test
    void removedStreetsAreRenderedAndAudited() throws Exception {
        // a grid city per number of removed segments (when one exists), audited and rendered
        WorldPlan p = plan(SEEDS[1], TestTerrain.Kind.FLAT);
        Set<Integer> done = new HashSet<>();
        for (City c : allCities(p)) {
            if (!done.add(c.streetCuts())) {
                continue;
            }
            int r = (int) Math.ceil(c.influence / 16) + 1;
            int ccx = Math.floorDiv(c.cx, 16);
            int ccz = Math.floorDiv(c.cz, 16);
            PlanAudit.Report rep = PlanAudit.audit(p, ccx - r, ccz - r, ccx + r, ccz + r);
            assertTrue(rep.ok(), c + ": " + rep.summary());
            PlanRender.write(PlanRender.detail(p, ccx - r, ccz - r, ccx + r, ccz + r),
                    new File(renderDir(), "cuts_" + c.streetCuts() + ".png"));
        }
        System.out.println("[WorldPlanTest] rendered cities with removed streets " + done);
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
                    boolean boundary = c.isCityBlock(i - 1, j) != c.isCityBlock(i, j);
                    boolean avenue = !boundary && Math.floorMod(i, Params.AVENUE_EVERY) == 0;
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

    private static int countRemoved(City c) {
        int n = c.gridHalf();
        int k = 0;
        for (int j = -n - 1; j <= n + 2; j++) {
            for (int i = -n - 1; i <= n + 2; i++) {
                k += (c.xRemoved(i, j) ? 1 : 0) + (c.zRemoved(i, j) ? 1 : 0);
            }
        }
        return k;
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
