package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A highway between two cities: a smooth curve (cubic Bezier) from an exit of city A to an exit of city B, leaving
 * each city straight along the street it continues, resampled into points one block apart. Point k is at arc length
 * ~k (exactly {@code k * step}).
 * <p>
 * The terrain-dependent part (height profile, ground/bridge/tunnel per point, pillars) is computed lazily by
 * {@link #detail}. Everything is a pure function of the two cities and their exits.
 */
public final class Highway {
    public final City a;
    public final City b;
    public final long key;
    public final City.Exit exitA;
    public final City.Exit exitB;
    /** Number of segments; points are 0..length. */
    public final int length;
    public final double step;
    final double[] px;
    final double[] pz;
    public final double minX;
    public final double minZ;
    public final double maxX;
    public final double maxZ;
    private final Map<Long, int[]> buckets;
    private volatile Detail detail;

    private Highway(City a, City b, long key, City.Exit exitA, City.Exit exitB, double[] px, double[] pz, double step) {
        this.a = a;
        this.b = b;
        this.key = key;
        this.exitA = exitA;
        this.exitB = exitB;
        this.px = px;
        this.pz = pz;
        this.length = px.length - 1;
        this.step = step;
        double x0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE;
        double z0 = Double.MAX_VALUE;
        double z1 = -Double.MAX_VALUE;
        Map<Long, List<Integer>> tmp = new HashMap<>();
        for (int k = 0; k <= length; k++) {
            x0 = Math.min(x0, px[k]);
            x1 = Math.max(x1, px[k]);
            z0 = Math.min(z0, pz[k]);
            z1 = Math.max(z1, pz[k]);
            long ck = Cache.key((int) Math.floor(px[k] / 16), (int) Math.floor(pz[k] / 16));
            tmp.computeIfAbsent(ck, q -> new ArrayList<>()).add(k);
        }
        this.minX = x0;
        this.maxX = x1;
        this.minZ = z0;
        this.maxZ = z1;
        Map<Long, int[]> bk = new HashMap<>();
        for (Map.Entry<Long, List<Integer>> e : tmp.entrySet()) {
            bk.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).toArray());
        }
        this.buckets = bk;
    }

    /** Builds the curve between two exits: P0 + d0 (leaving A), P3 + d3 (leaving B, i.e. arriving reversed). */
    static Highway create(City a, City.Exit exitA, City b, City.Exit exitB, long key) {
        double[] p0 = a.exitWorld(exitA);
        double[] d0 = a.exitDirection(exitA);
        double[] p3 = b.exitWorld(exitB);
        double[] d3 = b.exitDirection(exitB);
        List<double[]> pts = new ArrayList<>();
        bezier(pts, p0, d0, p3, d3);
        int n = pts.size() - 1;
        double[] bx = new double[n + 1];
        double[] bz = new double[n + 1];
        double[] cum = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            bx[i] = pts.get(i)[0];
            bz[i] = pts.get(i)[1];
            if (i > 0) {
                cum[i] = cum[i - 1] + Math.hypot(bx[i] - bx[i - 1], bz[i] - bz[i - 1]);
            }
        }
        double total = cum[n];
        int len = Math.max(1, (int) Math.round(total));
        double step = total / len;
        double[] xs = new double[len + 1];
        double[] zs = new double[len + 1];
        int seg = 0;
        for (int k2 = 0; k2 <= len; k2++) {
            double target = k2 == len ? total : k2 * step;
            while (seg < n - 1 && cum[seg + 1] < target) {
                seg++;
            }
            double segLen = cum[seg + 1] - cum[seg];
            double f = segLen <= 0 ? 0 : Math.max(0, Math.min(1, (target - cum[seg]) / segLen));
            xs[k2] = bx[seg] + (bx[seg + 1] - bx[seg]) * f;
            zs[k2] = bz[seg] + (bz[seg + 1] - bz[seg]) * f;
        }
        xs[0] = p0[0];
        zs[0] = p0[1];
        xs[len] = p3[0];
        zs[len] = p3[1];
        return new Highway(a, b, key, exitA, exitB, xs, zs, step);
    }

    /** Appends a dense cubic Bezier from p0 (leaving along d0) to p3 (arriving against d3). */
    private static void bezier(List<double[]> out, double[] p0, double[] d0, double[] p3, double[] d3) {
        double chord = Math.hypot(p3[0] - p0[0], p3[1] - p0[1]);
        double k = Math.max(40, Math.min(300, chord * 0.3));
        double[] p1 = {p0[0] + d0[0] * k, p0[1] + d0[1] * k};
        double[] p2 = {p3[0] + d3[0] * k, p3[1] + d3[1] * k};
        int n = Math.max(256, (int) (chord * 4));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double u = 1 - t;
            double w0 = u * u * u;
            double w1 = 3 * u * u * t;
            double w2 = 3 * u * t * t;
            double w3 = t * t * t;
            out.add(new double[]{w0 * p0[0] + w1 * p1[0] + w2 * p2[0] + w3 * p3[0],
                    w0 * p0[1] + w1 * p1[1] + w2 * p2[1] + w3 * p3[1]});
        }
    }

    public double x(int k) {
        return px[k];
    }

    public double z(int k) {
        return pz[k];
    }

    /**
     * Width of the edge strip at point k: 1 block where the highway runs along an axis, up to 1.41 on a diagonal, so the
     * edge columns always touch by a side (walls only connect sideways).
     */
    /** Width of the highway (21) and of a city street (17), which it narrows to next to cities. */
    public static final int FULL_WIDTH = (int) Math.round(2 * Params.HIGHWAY_HW);
    public static final int NARROW_WIDTH = (int) Math.round(2 * Params.STREET_HW);
    /** The highway loses 1 block of width every TAPER_STEP blocks as it nears a city ... */
    public static final int TAPER_STEP = 8;
    /**
     * ... down to NARROW_WIDTH this far from the end: just past the junction with the city street (whose edges have
     * no walls), so that no step of the walls falls on the place where they start.
     */
    public static final int TAPER_START = (int) Math.ceil(Params.HIGHWAY_HW + Params.STREET_HW + 4) + 3;

    /** Whether index k is where the highway meets a city street: open edges, no walls. */
    public boolean junction(int k) {
        double jl = Params.HIGHWAY_HW + Params.STREET_HW + 4;
        return k < jl || k > length - jl;
    }

    /** Blocks along the path from k to the nearest end of the highway. */
    int endDistance(int k) {
        return Math.max(0, Math.min(k, length - k));
    }

    /**
     * Half width of the lanes on one side of the centerline at index k (9.5 for the full 21 blocks). Near the ends
     * the width shrinks one block at a time, alternating sides, so the center marking stays in the middle.
     */
    public double laneHalf(int k, boolean positiveSide) {
        k = Math.max(0, Math.min(length, k));
        int over = endDistance(k) - TAPER_START;
        int w = over <= 0 ? NARROW_WIDTH : Math.min(FULL_WIDTH, NARROW_WIDTH + (over + TAPER_STEP - 1) / TAPER_STEP);
        int removed = FULL_WIDTH - w;
        int r = positiveSide ? (removed + 1) / 2 : removed / 2;
        return Params.HIGHWAY_LANE_HW - r;
    }

    public double edgeWidth(int k) {
        int a = Math.max(0, k - 1);
        int b = Math.min(length, k + 1);
        double tx = px[b] - px[a];
        double tz = pz[b] - pz[a];
        double l = Math.hypot(tx, tz);
        // + 0.1: exactly at the limit, rounding could still leave two edge columns touching by a corner
        return l <= 0 ? 1 : (Math.abs(tx) + Math.abs(tz)) / l + 0.1;
    }

    /** Smallest curve radius along the path, measured over 6-block windows. */
    public double minCurveRadius() {
        int w = 6;
        double min = Double.POSITIVE_INFINITY;
        for (int k = w; k + w <= length; k++) {
            double ax = px[k] - px[k - w];
            double az = pz[k] - pz[k - w];
            double bx = px[k + w] - px[k];
            double bz = pz[k + w] - pz[k];
            double cross = ax * bz - az * bx;
            double dot = ax * bx + az * bz;
            double angle = Math.abs(StrictMath.atan2(cross, dot));
            if (angle > 1e-9) {
                min = Math.min(min, w * step / angle);
            }
        }
        return min;
    }

    // ------------------------------------------------------------------ projection

    /** Segment indices that may come within reach of chunk (cx, cz): points of the chunks within {@code r} chunks. */
    public int[] segmentsNear(int cx, int cz, int r) {
        int[] tmp = new int[64];
        int n = 0;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int[] pts = buckets.get(Cache.key(cx + dx, cz + dz));
                if (pts == null) {
                    continue;
                }
                for (int k : pts) {
                    if (n + 2 > tmp.length) {
                        tmp = Arrays.copyOf(tmp, tmp.length * 2);
                    }
                    if (k > 0) {
                        tmp[n++] = k - 1;
                    }
                    if (k < length) {
                        tmp[n++] = k;
                    }
                }
            }
        }
        int[] out = Arrays.copyOf(tmp, n);
        Arrays.sort(out);
        int u = 0;
        for (int i = 0; i < n; i++) {
            if (u == 0 || out[u - 1] != out[i]) {
                out[u++] = out[i];
            }
        }
        return Arrays.copyOf(out, u);
    }

    /** Result of {@link #project}: lateral distance (signed: positive on the left), position along the path. */
    public static final class Proj {
        public double dist;
        public double d;
        public double s;

        public int index() {
            return (int) Math.round(s);
        }
    }

    /**
     * Nearest point of the path to (x, z), among the given segments. The two ends are cut flat: a point behind the
     * first point (inside city A) or past the last one is not on the highway. Returns false when no segment applies.
     */
    public boolean project(double x, double z, int[] segs, Proj out) {
        double best = Double.POSITIVE_INFINITY;
        double bestS = 0;
        double bestD = 0;
        for (int k : segs) {
            double ax = px[k];
            double az = pz[k];
            double ex = px[k + 1] - ax;
            double ez = pz[k + 1] - az;
            double len2 = ex * ex + ez * ez;
            double t = len2 <= 0 ? 0 : ((x - ax) * ex + (z - az) * ez) / len2;
            if (k == 0 && t < 0 || k == length - 1 && t > 1) {
                continue;
            }
            t = Math.max(0, Math.min(1, t));
            double qx = ax + ex * t;
            double qz = az + ez * t;
            double dd = Math.hypot(x - qx, z - qz);
            if (dd < best) {
                best = dd;
                bestS = k + t;
                double cross = ex * (z - az) - ez * (x - ax);
                bestD = cross >= 0 ? dd : -dd;
            }
        }
        if (best == Double.POSITIVE_INFINITY) {
            return false;
        }
        out.dist = best;
        out.d = bestD;
        out.s = bestS;
        return true;
    }

    // ------------------------------------------------------------------ terrain dependent detail

    public static final class Detail {
        /** Walking height in eighths of a block per point. */
        public final int[] he;
        public final byte[] type;
        public final boolean[] pillar;
        /** Natural ground per point (interpolated from samples every 4 blocks) and water flag. */
        public final double[] ground;
        public final boolean[] water;
        public final boolean valid;
        public final String reason;

        Detail(int[] he, byte[] type, boolean[] pillar, double[] ground, boolean[] water, boolean valid, String reason) {
            this.he = he;
            this.type = type;
            this.pillar = pillar;
            this.ground = ground;
            this.water = water;
            this.valid = valid;
            this.reason = reason;
        }

        /** 2 x walking height (half blocks) at point k. */
        public int h2(int k) {
            return Math.floorDiv(he[k] + 2, 4);
        }
    }

    public static final int SAMPLE = 4;
    public static final int DEADBAND = 5;
    public static final int SMOOTH = 12;

    public Detail detail(Terrain terrain) {
        Detail d = detail;
        if (d == null) {
            d = computeDetail(terrain);
            detail = d;
        }
        return d;
    }

    private Detail computeDetail(Terrain terrain) {
        int sea = terrain.seaLevel();
        int ns = (length + SAMPLE - 1) / SAMPLE + 1;
        int[] g = new int[ns];
        boolean[] w = new boolean[ns];
        int wet = 0;
        int run = 0;
        int longestWet = 0;
        for (int i = 0; i < ns; i++) {
            int k = Math.min(i * SAMPLE, length);
            int x = (int) Math.floor(px[k]);
            int z = (int) Math.floor(pz[k]);
            w[i] = terrain.water(x, z);
            g[i] = terrain.ground(x, z);
            if (w[i]) {
                wet++;
                run++;
                longestWet = Math.max(longestWet, run);
            } else {
                run = 0;
            }
        }
        // smoothed target, decks at least 3 above the sea
        double[] eff = new double[ns];
        for (int i = 0; i < ns; i++) {
            eff[i] = w[i] ? sea + 3 : Math.max(g[i], sea + 3);
        }
        double[] smooth = new double[ns];
        for (int i = 0; i < ns; i++) {
            double sum = 0;
            int c = 0;
            for (int j = Math.max(0, i - SMOOTH); j <= Math.min(ns - 1, i + SMOOTH); j++) {
                sum += eff[j];
                c++;
            }
            smooth[i] = Math.max(sea + 2, Math.min(Params.MAX_HIGHWAY_LEVEL, sum / c));
        }
        double[] target = new double[length + 1];
        double[] ground = new double[length + 1];
        boolean[] water = new boolean[length + 1];
        for (int k = 0; k <= length; k++) {
            double f = k / (double) SAMPLE;
            int i0 = Math.min(ns - 1, (int) Math.floor(f));
            int i1 = Math.min(ns - 1, i0 + 1);
            double t = f - i0;
            // the last sample sits at k = length, closer than SAMPLE to the previous one
            if (i1 == ns - 1) {
                int k0 = i0 * SAMPLE;
                int span = length - k0;
                t = span <= 0 ? 0 : (k - k0) / (double) span;
            }
            target[k] = smooth[i0] + (smooth[i1] - smooth[i0]) * t;
            ground[k] = g[i0] + (g[i1] - g[i0]) * t;
            water[k] = t < 0.5 ? w[i0] : w[i1];
        }
        int[] he = Profile.build(length, a.level, b.level, target, Params.FLAT_END, DEADBAND, sea + 2);
        byte[] type = Profile.classify(he, ground, water);
        boolean[] pillar = Profile.pillars(type);
        boolean valid = true;
        String reason = "ok";
        if (wet * 2 > ns) {
            valid = false;
            reason = "mostly water (" + wet + "/" + ns + " samples)";
        } else if (longestWet * SAMPLE > 600) {
            valid = false;
            reason = "water crossing of " + longestWet * SAMPLE + " blocks";
        }
        return new Detail(he, type, pillar, ground, water, valid, reason);
    }

    @Override
    public String toString() {
        return "Highway[" + a.ci + "," + a.cj + " -> " + b.ci + "," + b.cj + " len=" + length + "]";
    }
}
