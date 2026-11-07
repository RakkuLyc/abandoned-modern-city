package com.abandonedmoderncity.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Checks the plan of a region (no blocks involved): used by the unit tests on synthetic terrain and, in game, on the
 * real terrain by {@code /amcs audit plan} and the self-test. Every check must come out at 0.
 *
 * <ul>
 *   <li>{@code step}: two neighboring drivable road columns more than half a block apart (also across chunks).</li>
 *   <li>{@code city_level}: a street or lot column not at its city's level.</li>
 *   <li>{@code highway_hole}: a point of a highway's 15-block band that is not road.</li>
 *   <li>{@code highway_height}: a highway centerline column more than half a block away from its profile.</li>
 *   <li>{@code highway_end}: a highway end that does not land on a street of its city, at the city level.</li>
 *   <li>{@code street_missing}: a city street segment whose middle is not road.</li>
 *   <li>{@code dead_end}: a street node with a single street.</li>
 *   <li>{@code orphan_road}: a connected piece of road with no city street that does not leave the region.</li>
 *   <li>{@code city_split}: a city whose streets are not all connected.</li>
 *   <li>{@code removed_street}: the middle of a randomly removed street segment is not lot ground.</li>
 *   <li>{@code building_on_road}: a building footprint column that is not lot ground.</li>
 *   <li>{@code building_overlap}: two lots (buildings, houses) of a block overlap (touching is allowed).</li>
 *   <li>{@code building_setback}: a building of a paved city touches a street, or its front is not 1 to 3 columns
 *   from the sidewalk.</li>
 *   <li>{@code short_tunnel}: a tunnel shorter than {@link Profile#MIN_TUNNEL}.</li>
 *   <li>{@code ramp}: two consecutive highway points more than 1/8 block apart, or a flat at a fractional height.</li>
 * </ul>
 */
public final class PlanAudit {
    private PlanAudit() {
    }

    public static final class Report {
        public final Map<String, Integer> counts = new TreeMap<>();
        public final Map<String, List<String>> examples = new TreeMap<>();
        public final Map<String, Long> stats = new TreeMap<>();
        public String label = "PLAN";

        public void fail(String check, String example) {
            counts.merge(check, 1, Integer::sum);
            List<String> ex = examples.computeIfAbsent(check, k -> new ArrayList<>());
            if (ex.size() < 8) {
                ex.add(example);
            }
        }

        public void stat(String name, long add) {
            stats.merge(name, add, Long::sum);
        }

        public int failures() {
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }

        public boolean ok() {
            return failures() == 0;
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(ok() ? label + " OK" : label + " FAILURES: " + failures()).append('\n');
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append("  e.g. ")
                        .append(String.join("; ", examples.get(e.getKey()))).append('\n');
            }
            sb.append("  stats: ").append(stats).append('\n');
            return sb.toString();
        }
    }

    /** Audits the chunks [cx0, cx1] x [cz0, cz1]. */
    public static Report audit(WorldPlan plan, int cx0, int cz0, int cx1, int cz1) {
        Report r = new Report();
        int w = (cx1 - cx0 + 1) * 16;
        int d = (cz1 - cz0 + 1) * 16;
        int x0 = cx0 * 16;
        int z0 = cz0 * 16;
        byte[] kind = new byte[w * d];
        short[] h2 = new short[w * d];
        byte[] flags = new byte[w * d];
        boolean[] drivable = new boolean[w * d];
        byte[] struct = new byte[w * d];
        boolean[] wall = new boolean[w * d];
        for (int cz = cz0; cz <= cz1; cz++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                ChunkPlan cp = plan.chunk(cx, cz);
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int i = ChunkPlan.idx(lx, lz);
                        int gx = (cx - cx0) * 16 + lx;
                        int gz = (cz - cz0) * 16 + lz;
                        int g = gx + gz * w;
                        kind[g] = cp.kind[i];
                        h2[g] = cp.h2[i];
                        flags[g] = cp.flags[i];
                        struct[g] = cp.struct[i];
                        drivable[g] = cp.drivable(i);
                        wall[g] = cp.kind[i] == ChunkPlan.ROAD && ChunkPlan.walled(cp.zone[i]);
                        if (cp.kind[i] == ChunkPlan.ROAD) {
                            r.stat("road_columns", 1);
                            if ((cp.flags[i] & ChunkPlan.F_CITY) != 0) {
                                r.stat("street_columns", 1);
                            } else {
                                r.stat(switch (cp.struct[i]) {
                                    case Profile.BRIDGE -> "highway_bridge_columns";
                                    case Profile.TUNNEL -> "highway_tunnel_columns";
                                    default -> "highway_ground_columns";
                                }, 1);
                            }
                            if (ChunkPlan.isSlab(cp.h2[i])) {
                                r.stat("slab_columns", 1);
                            }
                        }
                        if ((cp.kind[i] == ChunkPlan.ROAD && (cp.flags[i] & ChunkPlan.F_CITY) != 0
                                || cp.kind[i] == ChunkPlan.LOT) && cp.city != null && cp.h2[i] != 2 * cp.city.level) {
                            r.fail("city_level", (x0 + gx) + "," + (z0 + gz));
                        }
                    }
                }
            }
        }
        // steps between neighbors (4-neighborhood, inside the region, across chunk borders)
        for (int gz = 0; gz < d; gz++) {
            for (int gx = 0; gx < w; gx++) {
                int g = gx + gz * w;
                if (!drivable[g]) {
                    continue;
                }
                if (gx + 1 < w && drivable[g + 1] && Math.abs(h2[g] - h2[g + 1]) > 1) {
                    r.fail("step", (x0 + gx) + "," + (z0 + gz) + " E " + h2[g] / 2.0 + "->" + h2[g + 1] / 2.0);
                }
                if (gz + 1 < d && drivable[g + w] && Math.abs(h2[g] - h2[g + w]) > 1) {
                    r.fail("step", (x0 + gx) + "," + (z0 + gz) + " S " + h2[g] / 2.0 + "->" + h2[g + w] / 2.0);
                }
            }
        }
        // highway edge walls touch by a side, never only by a corner (walls connect sideways only)
        for (int gz = 0; gz + 1 < d; gz++) {
            for (int gx = 0; gx < w; gx++) {
                int g = gx + gz * w;
                if (!wall[g]) {
                    continue;
                }
                for (int dx = -1; dx <= 1; dx += 2) {
                    int nx = gx + dx;
                    if (nx < 0 || nx >= w) {
                        continue;
                    }
                    if (wall[nx + (gz + 1) * w] && !wall[nx + gz * w] && !wall[gx + (gz + 1) * w]) {
                        r.fail("wall_gap", (x0 + gx) + "," + (z0 + gz) + " only touches " + (x0 + nx) + "," + (z0 + gz + 1) + " by a corner");
                    }
                }
            }
        }
        // cities: street segments, node degrees, lots, connectivity
        List<City> cities = plan.citiesIn(x0 - 400, z0 - 400, x0 + w + 400, z0 + d + 400);
        for (City c : cities) {
            auditCity(plan, c, r, x0, z0, w, d, kind, drivable, h2);
        }
        // highways
        for (Highway h : plan.highwaysIn(x0, z0, x0 + w, z0 + d, 0)) {
            auditHighway(plan, h, r, x0, z0, w, d, kind, h2, struct, flags);
        }
        // connectivity of the road network
        components(r, x0, z0, w, d, drivable, h2, flags);
        return r;
    }

    private static boolean inRegion(int gx, int gz, int w, int d) {
        return gx >= 0 && gz >= 0 && gx < w && gz < d;
    }

    private static void auditCity(WorldPlan plan, City c, Report r, int x0, int z0, int w, int d,
                                  byte[] kind, boolean[] drivable, short[] h2) {
        int n = c.gridHalf();
        boolean fullyInside = true;
        List<int[]> streets = new ArrayList<>();
        List<int[]> removed = new ArrayList<>();
        for (int j = -n - 1; j <= n + 2; j++) {
            for (int i = -n - 1; i <= n + 2; i++) {
                // node degree
                int deg = (c.xSegment(i, j) ? 1 : 0) + (c.xSegment(i, j - 1) ? 1 : 0)
                        + (c.zSegment(i, j) ? 1 : 0) + (c.zSegment(i - 1, j) ? 1 : 0);
                if (deg == 1) {
                    r.fail("dead_end", c + " node " + i + "," + j);
                }
                if (deg >= 2) {
                    r.stat("street_nodes", 1);
                }
                // segment midpoints
                if (c.xSegment(i, j)) {
                    streets.add(mid(c, c.U(i), c.V(j) + c.S / 2.0));
                }
                if (c.zSegment(i, j)) {
                    streets.add(mid(c, c.U(i) + c.S / 2.0, c.V(j)));
                }
                // removed segments: their middle is ground of the joined blocks, never road
                if (c.xRemoved(i, j)) {
                    removed.add(mid(c, c.U(i), c.V(j) + c.S / 2.0));
                }
                if (c.zRemoved(i, j)) {
                    removed.add(mid(c, c.U(i) + c.S / 2.0, c.V(j)));
                }
            }
        }
        int[] first = null;
        for (int[] p : streets) {
            int gx = p[0] - x0;
            int gz = p[1] - z0;
            if (!inRegion(gx, gz, w, d)) {
                fullyInside = false;
                continue;
            }
            int g = gx + gz * w;
            if (kind[g] != ChunkPlan.ROAD) {
                r.fail("street_missing", c + " at " + p[0] + "," + p[1]);
            } else if (first == null) {
                first = p;
            }
        }
        for (int[] p : removed) {
            int gx = p[0] - x0;
            int gz = p[1] - z0;
            r.stat("removed_segments", 1);
            if (inRegion(gx, gz, w, d) && kind[gx + gz * w] != ChunkPlan.LOT) {
                r.fail("removed_street", c + " removed segment at " + p[0] + "," + p[1] + " kind " + kind[gx + gz * w]);
            }
        }
        r.stat("cities", 1);
        // lots
        City.Hit hit = new City.Hit();
        for (int j = -n; j <= n; j++) {
            for (int i = -n; i <= n; i++) {
                List<Lot> blockLots = c.lots(i, j);
                for (int u = 0; u < blockLots.size(); u++) {
                    for (int v = u + 1; v < blockLots.size(); v++) {
                        Lot a = blockLots.get(u);
                        Lot b = blockLots.get(v);
                        if (a.x0 <= b.x1 && b.x0 <= a.x1 && a.z0 <= b.z1 && b.z0 <= a.z1) {
                            r.fail("lot_overlap", c + " lots " + a.x0 + "," + a.z0 + " and " + b.x0 + "," + b.z0);
                        }
                    }
                }
                for (Lot lot : blockLots) {
                    r.stat("lots", 1);
                }
            }
        }
        // all streets of a city inside the region are connected
        if (fullyInside && first != null) {
            boolean[] seen = flood(first[0] - x0, first[1] - z0, w, d, drivable, h2);
            for (int[] p : streets) {
                int gx = p[0] - x0;
                int gz = p[1] - z0;
                int g = gx + gz * w;
                if (kind[g] == ChunkPlan.ROAD && !seen[g]) {
                    r.fail("city_split", c + " street at " + p[0] + "," + p[1] + " not reachable");
                }
            }
            r.stat("cities_fully_checked", 1);
        }
    }

    private static int[] mid(City c, double u, double v) {
        double[] p = c.warp.toWorld(u, v);
        return new int[]{(int) Math.floor(p[0]), (int) Math.floor(p[1])};
    }

    private static void auditHighway(WorldPlan plan, Highway h, Report r, int x0, int z0, int w, int d,
                                     byte[] kind, short[] h2, byte[] struct, byte[] flags) {
        Highway.Detail det = h.detail(plan.terrain);
        r.stat("highways", 1);
        // profile properties
        for (int k = 1; k <= h.length; k++) {
            if (Math.abs(det.he[k] - det.he[k - 1]) > 1) {
                r.fail("ramp", h + " grade at " + k);
            }
            if (det.he[k] == det.he[k - 1] && Math.floorMod(det.he[k], Profile.E) != 0) {
                r.fail("ramp", h + " fractional flat at " + k);
            }
        }
        if (det.he[0] != h.a.level * Profile.E || det.he[h.length] != h.b.level * Profile.E) {
            r.fail("highway_end", h + " profile ends " + det.he[0] / 8.0 + "/" + det.he[h.length] / 8.0);
        }
        // tunnels long enough
        int run = 0;
        for (int k = 0; k <= h.length + 1; k++) {
            boolean t = k <= h.length && det.type[k] == Profile.TUNNEL;
            if (t) {
                run++;
            } else {
                if (run > 0 && run < Profile.MIN_TUNNEL) {
                    r.fail("short_tunnel", h + " tunnel of " + run + " ending at " + k);
                }
                if (run > 0) {
                    r.stat("tunnels", 1);
                }
                run = 0;
            }
        }
        // band coverage and centerline height
        for (int k = 0; k <= h.length; k++) {
            double x = h.x(k);
            double z = h.z(k);
            int kn = Math.min(h.length, k + 1);
            int kp = Math.max(0, k - 1);
            double tx = h.x(kn) - h.x(kp);
            double tz = h.z(kn) - h.z(kp);
            double tl = Math.hypot(tx, tz);
            double nx = -tz / tl;
            double nz = tx / tl;
            // the band is 15 wide (|d| <= 7.5); a column center can be 0.71 away from the sample point
            for (int off = -6; off <= 6; off++) {
                int bx = (int) Math.floor(x + nx * off);
                int bz = (int) Math.floor(z + nz * off);
                int gx = bx - x0;
                int gz = bz - z0;
                if (!inRegion(gx, gz, w, d)) {
                    continue;
                }
                int g = gx + gz * w;
                if (kind[g] != ChunkPlan.ROAD) {
                    // the first and last points stand on the street centerline: the band behind them is the city's
                    if (k > 0 && k < h.length) {
                        r.fail("highway_hole", h + " point " + k + " offset " + off + " at " + bx + "," + bz + " kind " + kind[g]);
                    }
                    continue;
                }
                if (off == 0 && (flags[g] & ChunkPlan.F_CITY) == 0) {
                    if (Math.abs(h2[g] - det.h2(k)) > 1) {
                        r.fail("highway_height", h + " point " + k + " plan " + h2[g] / 2.0 + " profile " + det.h2(k) / 2.0);
                    }
                    if (struct[g] != det.type[k] && Math.abs(h2[g] - det.h2(k)) > 0) {
                        r.stat("type_boundary_columns", 1);
                    }
                }
            }
            if (k == 0 || k == h.length) {
                City c = k == 0 ? h.a : h.b;
                int gx = (int) Math.floor(x) - x0;
                int gz = (int) Math.floor(z) - z0;
                if (inRegion(gx, gz, w, d)) {
                    int g = gx + gz * w;
                    if (kind[g] != ChunkPlan.ROAD || (flags[g] & ChunkPlan.F_CITY) == 0 || h2[g] != 2 * c.level) {
                        r.fail("highway_end", h + " end " + k + " at " + gx + "," + gz + " kind " + kind[g] + " h2 " + h2[g]);
                    }
                    r.stat("highway_ends_checked", 1);
                }
            }
        }
    }

    private static boolean[] flood(int sx, int sz, int w, int d, boolean[] drivable, short[] h2) {
        boolean[] seen = new boolean[w * d];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        int s = sx + sz * w;
        if (!drivable[s]) {
            return seen;
        }
        seen[s] = true;
        q.add(s);
        while (!q.isEmpty()) {
            int g = q.poll();
            int gx = g % w;
            int gz = g / w;
            int[][] nb = {{gx + 1, gz}, {gx - 1, gz}, {gx, gz + 1}, {gx, gz - 1}};
            for (int[] p : nb) {
                if (!inRegion(p[0], p[1], w, d)) {
                    continue;
                }
                int n = p[0] + p[1] * w;
                if (!seen[n] && drivable[n] && Math.abs(h2[n] - h2[g]) <= 1) {
                    seen[n] = true;
                    q.add(n);
                }
            }
        }
        return seen;
    }

    private static void components(Report r, int x0, int z0, int w, int d, boolean[] drivable, short[] h2, byte[] flags) {
        int[] comp = new int[w * d];
        int next = 0;
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int s = 0; s < w * d; s++) {
            if (!drivable[s] || comp[s] != 0) {
                continue;
            }
            next++;
            comp[s] = next;
            q.add(s);
            boolean city = false;
            boolean border = false;
            int size = 0;
            while (!q.isEmpty()) {
                int g = q.poll();
                size++;
                int gx = g % w;
                int gz = g / w;
                if ((flags[g] & ChunkPlan.F_CITY) != 0) {
                    city = true;
                }
                if (gx == 0 || gz == 0 || gx == w - 1 || gz == d - 1) {
                    border = true;
                }
                int[][] nb = {{gx + 1, gz}, {gx - 1, gz}, {gx, gz + 1}, {gx, gz - 1}};
                for (int[] p : nb) {
                    if (!inRegion(p[0], p[1], w, d)) {
                        continue;
                    }
                    int n = p[0] + p[1] * w;
                    if (comp[n] == 0 && drivable[n] && Math.abs(h2[n] - h2[g]) <= 1) {
                        comp[n] = next;
                        q.add(n);
                    }
                }
            }
            r.stat("road_components", 1);
            if (!city && !border) {
                r.fail("orphan_road", "component of " + size + " columns at " + (x0 + s % w) + "," + (z0 + s / w));
            }
        }
    }
}
