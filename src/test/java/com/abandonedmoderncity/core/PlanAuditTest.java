package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Full-resolution plan audits (every column) around whole cities and along whole highways, on every synthetic
 * terrain, plus determinism of the plan under different query orders and threads.
 */
class PlanAuditTest {

    @Test
    void citiesAndHighwaysPassThePlanAudit() throws Exception {
        StringBuilder out = new StringBuilder();
        int audits = 0;
        int failures = 0;
        for (TestTerrain.Kind kind : TestTerrain.Kind.values()) {
            for (long seed : WorldPlanTest.SEEDS) {
                WorldPlan p = WorldPlanTest.plan(seed, kind);
                List<City> cities = WorldPlanTest.allCities(p);
                // three cities per terrain and seed, whole (plus their slopes)
                int n = 0;
                for (City c : cities) {
                    if (n++ >= 3) {
                        break;
                    }
                    int r = (int) Math.ceil(c.influence / 16) + 1;
                    int ccx = Math.floorDiv(c.cx, 16);
                    int ccz = Math.floorDiv(c.cz, 16);
                    PlanAudit.Report rep = PlanAudit.audit(p, ccx - r, ccz - r, ccx + r, ccz + r);
                    audits++;
                    if (!rep.ok()) {
                        failures++;
                    }
                    out.append(kind).append(' ').append(seed).append(' ').append(c).append(": ").append(rep.summary());
                    if (seed == WorldPlanTest.SEEDS[0] && n == 1) {
                        PlanRender.write(PlanRender.detail(p, ccx - r, ccz - r, ccx + r, ccz + r),
                                new File(WorldPlanTest.renderDir(), "city_" + kind + ".png"));
                    }
                }
                // two highways per terrain and seed, whole
                List<Highway> hs = p.highwaysOf(cities);
                hs.sort((a, b) -> Long.compare(a.key, b.key));
                int m = 0;
                for (Highway h : hs) {
                    if (m++ >= 2) {
                        break;
                    }
                    int cx0 = (int) Math.floor((h.minX - 40) / 16);
                    int cz0 = (int) Math.floor((h.minZ - 40) / 16);
                    int cx1 = (int) Math.floor((h.maxX + 40) / 16);
                    int cz1 = (int) Math.floor((h.maxZ + 40) / 16);
                    PlanAudit.Report rep = PlanAudit.audit(p, cx0, cz0, cx1, cz1);
                    audits++;
                    if (!rep.ok()) {
                        failures++;
                    }
                    out.append(kind).append(' ').append(seed).append(' ').append(h).append(": ").append(rep.summary());
                    if (seed == WorldPlanTest.SEEDS[0] && m == 1) {
                        PlanRender.write(PlanRender.profile(p, h, 2), new File(WorldPlanTest.renderDir(), "profile_" + kind + ".png"));
                        PlanRender.write(PlanRender.detail(p, cx0, cz0, cx1, cz1), new File(WorldPlanTest.renderDir(), "highway_" + kind + ".png"));
                    }
                }
            }
        }
        System.out.println("[PlanAuditTest]\n" + out);
        assertTrue(failures == 0, failures + " of " + audits + " audits failed:\n" + out);
    }

    @Test
    void planIsIndependentOfQueryOrderAndThreads() throws Exception {
        long seed = WorldPlanTest.SEEDS[1];
        TestTerrain.Kind kind = TestTerrain.Kind.MIXED;
        WorldPlan reference = WorldPlanTest.plan(seed, kind);
        List<int[]> chunks = new ArrayList<>();
        // chunks around a city and the start of its highways
        City c = WorldPlanTest.allCities(reference).get(0);
        int ccx = Math.floorDiv(c.cx, 16);
        int ccz = Math.floorDiv(c.cz, 16);
        for (int dz = -30; dz <= 30; dz += 1) {
            for (int dx = -30; dx <= 30; dx += 1) {
                chunks.add(new int[]{ccx + dx, ccz + dz});
            }
        }
        List<ChunkPlan> ref = new ArrayList<>();
        for (int[] ch : chunks) {
            ref.add(reference.chunk(ch[0], ch[1]));
        }
        // a fresh plan queried in a shuffled order from 8 threads
        WorldPlan other = WorldPlanTest.plan(seed, kind);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, new java.util.Random(7));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<ChunkPlan>> fs = new ArrayList<>();
            for (int i : order) {
                int[] ch = chunks.get(i);
                fs.add(pool.submit(() -> other.chunk(ch[0], ch[1])));
            }
            for (int q = 0; q < order.size(); q++) {
                ChunkPlan a = ref.get(order.get(q));
                ChunkPlan b = fs.get(q).get();
                assertArrayEquals(a.kind, b.kind, "kind " + a.cx + "," + a.cz);
                assertArrayEquals(a.h2, b.h2, "h2 " + a.cx + "," + a.cz);
                assertArrayEquals(a.zone, b.zone, "zone " + a.cx + "," + a.cz);
                assertArrayEquals(a.struct, b.struct, "struct " + a.cx + "," + a.cz);
                assertArrayEquals(a.flags, b.flags, "flags " + a.cx + "," + a.cz);
                assertArrayEquals(a.lo, b.lo, "lo " + a.cx + "," + a.cz);
                assertArrayEquals(a.hi, b.hi, "hi " + a.cx + "," + a.cz);
                for (int i = 0; i < 256; i++) {
                    if (a.lot[i] == null) {
                        assertSame(null, b.lot[i]);
                    } else {
                        assertTrue(b.lot[i] != null && a.lot[i].x0 == b.lot[i].x0 && a.lot[i].z0 == b.lot[i].z0
                                && a.lot[i].type == b.lot[i].type && a.lot[i].seed == b.lot[i].seed);
                    }
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
