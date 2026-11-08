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
    void cityStreetsHaveNoDeadEndsAndLotsAreBuildable() {
        int cities = 0;
        int lots = 0;
        int[] cutHistogram = new int[City.MAX_CUTS + 1];
        for (TestTerrain.Kind kind : TestTerrain.Kind.values()) {
            WorldPlan p = plan(SEEDS[0], kind);
            City.Hit hit = new City.Hit();
            for (City c : allCities(p)) {
                cities++;
                int n = c.gridHalf();
                assertTrue(c.isCityBlock(0, 0), "center block");
                cutHistogram[c.streetCuts()]++;
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
                    for (int x = xc - 12; x <= xc + 12; x++) {
                        c.query(x + 0.5, z + 0.5, hit);
                        if (hit.road) {
                            width++;
                        }
                    }
                    assertEquals(17, width, c + " line " + i + " row " + j);
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
                    assertTrue(exitsUsed.computeIfAbsent(h.a.id, k -> new HashSet<>()).add(h.exitA.toString()),
                            "exit used twice");
                    assertTrue(exitsUsed.computeIfAbsent(h.b.id, k -> new HashSet<>()).add(h.exitB.toString()),
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
}
