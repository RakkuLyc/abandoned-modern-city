package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * One city: a set of square blocks on a (gently waving) grid, all at a single walking height {@link #level}.
 *
 * <h2>Streets</h2>
 * Every block side that touches at least one city block is a street segment. So every street node has degree 2, 3
 * or 4: there are no dead ends anywhere, and the outer streets form closed rings around the city. Where only one of the
 * four blocks around a node is a city block (a convex corner of the city outline) the corner is rounded with an arc of
 * {@link Params#ARC_RADIUS}. Segments inside the city follow the class of their grid line (every third line is an
 * avenue); segments on the outline are always streets.
 *
 * <h2>Stages</h2>
 * {@link #create} only samples a handful of terrain points (does the cell have a city, where, how big, at what height);
 * the block layout (more samples) is computed lazily the first time the geometry is needed.
 * <p>
 * Grid space: {@code u = x - wx(z)}, {@code v = z - wz(x)} (see {@link Warp}). North-south grid line {@code i} is
 * {@code u = U(i)}, east-west line {@code j} is {@code v = V(j)}; block (i, j) spans {@code [U(i), U(i+1)] x [V(j), V(j+1)]}.
 */
public final class City {
    /** Distance between parallel street centerlines. */
    public final int S = Params.BLOCK;
    static final double R_ARC = Params.ARC_RADIUS;
    /** Grid-space search radius for street primitives: the widest street plus its slopes, with slack for the waves. */
    static final double SEARCH = (Params.AVENUE_HW + Params.CITY_SKIRT) * 1.05 + 2;
    private static final long SALT = 0x43495459L;

    public final int ci;
    public final int cj;
    public final long id;
    public final long seed;
    public final int cx;
    public final int cz;
    public final int radius;
    /** Walking height of every street and lot of the city. */
    public final int level;
    public final double influence;
    public final Warp warp;
    /** u of north-south line 0 and v of east-west line 0. */
    public final double u0;
    public final double v0;
    private final double shapeA2;
    private final double shapeP2;
    private final double shapeA3;
    private final double shapeP3;
    private final Terrain terrain;
    private volatile Layout layout;

    private City(int ci, int cj, long seed, int cx, int cz, int radius, int level, Warp warp,
                 double a2, double p2, double a3, double p3, Terrain terrain) {
        this.ci = ci;
        this.cj = cj;
        this.id = Cache.key(ci, cj);
        this.seed = seed;
        this.cx = cx;
        this.cz = cz;
        this.radius = radius;
        this.level = level;
        this.influence = Params.influence(radius);
        this.warp = warp;
        // street centerlines on block centers (x + 0.5), so a 17-wide street covers exactly 17 columns
        // the grid (blocks FIRST..LAST) is centered on the city center
        this.u0 = cx - (int) Math.floor((FIRST + LAST + 1) / 2.0 * S) + 0.5;
        this.v0 = cz - (int) Math.floor((FIRST + LAST + 1) / 2.0 * S) + 0.5;
        this.shapeA2 = a2;
        this.shapeP2 = p2;
        this.shapeA3 = a3;
        this.shapeP3 = p3;
        this.terrain = terrain;
    }

    // ------------------------------------------------------------------ stage 1

    /** Entrances (highways) of every city, at border T crossings facing the linked cities. */
    public static final int EXITS = 4;
    /**
     * Most street segments removed from a grid city (the count is uniform in 0..MAX_CUTS). 0 = switched off (asked by
     * the user for the 2 x 2 grid, where only the inner cross could be cut); the cutting code stays for later.
     */
    public static final int MAX_CUTS = 0;
    static final long CUT_SALT = 0x43555453L;
    /** Grid blocks of a city: FIRST..LAST in both directions (-1..0 for the 2 x 2 grid, -2..1 for 4 x 4). */
    public static final int FIRST = -(Params.GRID / 2);
    public static final int LAST = (Params.GRID + 1) / 2 - 1;

    /** Positions tried per cell before giving up (the later ones with a smaller radius). */
    static final int ATTEMPTS = 10;

    /** The city of a world cell, or null. Pure function of (seed, cell). */
    public static City create(long worldSeed, Params p, Terrain t, int ci, int cj) {
        return site(worldSeed, p, t, ci, cj) instanceof City c ? c : null;
    }

    /** "city", or why the cell has none (for diagnostics). */
    public static String diagnose(long worldSeed, Params p, Terrain t, int ci, int cj) {
        Object r = site(worldSeed, p, t, ci, cj);
        return r instanceof City ? "city" : (String) r;
    }

    /**
     * Rolls whether the cell has a city, then looks for a place for it: up to {@link #ATTEMPTS} positions inside the
     * cell (the last ones with a smaller radius), the first one that is dry, not too high and flat enough wins. A single
     * random spot per cell, rejected when it fell on a lake or a hill, left most of a real world without cities.
     */
    private static Object site(long worldSeed, Params p, Terrain t, int ci, int cj) {
        long h = Hash.of(worldSeed, SALT, ci, cj);
        if (Hash.unit(h) >= p.cityChance()) {
            return "no city rolled";
        }
        int margin = p.margin();
        int span = p.cellSize() - 2 * margin;
        int baseRadius = Hash.range(Hash.next(h, 3), p.minRadius(), p.maxRadius());
        String reason = "no attempt";
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            long ha = Hash.next(h, 100 + attempt);
            int cx = ci * p.cellSize() + margin + Hash.range(ha, 0, span);
            int cz = cj * p.cellSize() + margin + Hash.range(Hash.next(ha, 1), 0, span);
            // every city is the same 2 x 2 grid: the radius only bounds its area of influence
            int radius = Params.GRID_RADIUS;
            Object r = trySite(p, t, cx, cz, radius);
            if (r instanceof String s) {
                reason = s;
                continue;
            }
            int level = (Integer) r;
            return build(h, ci, cj, cx, cz, radius, level, t);
        }
        return reason;
    }

    /** The level of a city at (cx, cz), or why it cannot stand there. */
    private static Object trySite(Params p, Terrain t, int cx, int cz, int radius) {
        int sea = t.seaLevel();
        int[] g = new int[9];
        int n = 0;
        int wet = 0;
        g[n++] = t.ground(cx, cz);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int x = cx + (int) Math.round(StrictMath.cos(a) * radius * 0.7);
            int z = cz + (int) Math.round(StrictMath.sin(a) * radius * 0.7);
            if (t.water(x, z)) {
                wet++;
            } else {
                g[n++] = t.ground(x, z);
            }
        }
        // lakes and rivers are filled, but a city does not stand in the sea
        if (wet > 4 || n == 0) {
            return "mostly water";
        }
        int[] land = Arrays.copyOf(g, n);
        Arrays.sort(land);
        int level = Math.max(land[n / 2], sea + 2);
        if (level > p.maxCityLevel()) {
            return "too high";
        }
        // hills and mountains are cut down to the level, valleys filled up
        return level;
    }

    private static City build(long h, int ci, int cj, int cx, int cz, int radius, int level, Terrain t) {
        // a rigid grid: straight streets, right angles
        Warp warp = Warp.NONE;
        return new City(ci, cj, h, cx, cz, radius, level, warp,
                Hash.range(Hash.next(h, 12), 0.0, 0.12), Hash.range(Hash.next(h, 13), 0.0, 2 * Math.PI),
                Hash.range(Hash.next(h, 14), 0.0, 0.08), Hash.range(Hash.next(h, 15), 0.0, 2 * Math.PI), t);
    }

    public double U(int i) {
        return u0 + (double) i * S;
    }

    public double V(int j) {
        return v0 + (double) j * S;
    }

    /** Organic outline: the city reaches radius * shape(angle), at most 1.2 radius. */
    double shape(double angle) {
        return 1 + shapeA2 * StrictMath.sin(2 * angle + shapeP2) + shapeA3 * StrictMath.sin(3 * angle + shapeP3);
    }

    // ------------------------------------------------------------------ stage 2: block layout

    static final class Layout {
        final int n;
        final int w;
        final boolean[] city;
        final int blocks;
        /** Convex corner quadrant per node (0 = none), nodes i, j in [-n, n + 1]. */
        final byte[] corner;
        final int nw;
        final List<Exit> exits;
        final AtomicReferenceArray<List<Lot>> lots;
        /** Removed street segments (same indexing as {@link #corner}); null while the layout is being built. */
        boolean[] xCut;
        boolean[] zCut;
        final int cuts;

        Layout(int n, boolean[] city, long seed, int maxCuts) {
            this.n = n;
            this.w = 2 * n + 1;
            this.city = city;
            int count = 0;
            for (boolean b : city) {
                if (b) {
                    count++;
                }
            }
            this.blocks = count;
            this.nw = 2 * n + 2;
            this.corner = new byte[nw * nw];
            this.lots = new AtomicReferenceArray<>(w * w);
            List<Exit> ex = new ArrayList<>();
            for (int j = -n; j <= n + 1; j++) {
                for (int i = -n; i <= n + 1; i++) {
                    boolean nwB = isCity(i - 1, j - 1);
                    boolean neB = isCity(i, j - 1);
                    boolean swB = isCity(i - 1, j);
                    boolean seB = isCity(i, j);
                    int c = (nwB ? 1 : 0) + (neB ? 1 : 0) + (swB ? 1 : 0) + (seB ? 1 : 0);
                    if (c == 1) {
                        corner[(i + n) + (j + n) * nw] = (byte) (seB ? 1 : swB ? 2 : neB ? 3 : 4);
                    } else if (c == 2) {
                        // two edge-adjacent city blocks: a T junction on the outline, a possible highway exit
                        if (neB && seB) {
                            ex.add(new Exit(i, j, Exit.WEST, false));
                        } else if (nwB && swB) {
                            ex.add(new Exit(i, j, Exit.EAST, false));
                        } else if (swB && seB) {
                            ex.add(new Exit(i, j, Exit.NORTH, false));
                        } else if (nwB && neB) {
                            ex.add(new Exit(i, j, Exit.SOUTH, false));
                        }
                    }
                }
            }
            // the border T crossings: WorldPlan picks the city's EXITS entrances among them
            this.exits = List.copyOf(ex);
            this.xCut = new boolean[nw * nw];
            this.zCut = new boolean[nw * nw];
            this.cuts = cutStreets(seed, maxCuts);
        }

        /**
         * Removes 0..{@link #MAX_CUTS} street segments (uniform), picked in a random order; a segment is only removed
         * if no node becomes a dead end and all the streets stay connected. The ground of a removed segment joins the
         * two blocks beside it.
         */
        private int cutStreets(long seed, int maxCuts) {
            int target = Hash.range(Hash.of(seed, CUT_SALT, 0), 0, maxCuts);
            if (target == 0) {
                return 0;
            }
            List<int[]> segs = new ArrayList<>();
            for (int j = -n; j <= n + 1; j++) {
                for (int i = -n; i <= n + 1; i++) {
                    if (xDrawn(i, j)) {
                        segs.add(new int[]{0, i, j});
                    }
                    if (zDrawn(i, j)) {
                        segs.add(new int[]{1, i, j});
                    }
                }
            }
            for (int k = segs.size() - 1; k > 0; k--) {
                int r = Hash.range(Hash.of(seed, CUT_SALT, 1 + k), 0, k);
                int[] t = segs.get(k);
                segs.set(k, segs.get(r));
                segs.set(r, t);
            }
            int done = 0;
            for (int[] sg : segs) {
                if (done >= target) {
                    break;
                }
                boolean[] arr = sg[0] == 0 ? xCut : zCut;
                int idx = (sg[1] + n) + (sg[2] + n) * nw;
                arr[idx] = true;
                int bi = sg[0] == 0 ? sg[1] : sg[1] + 1;
                int bj = sg[0] == 0 ? sg[2] + 1 : sg[2];
                if (degree(sg[1], sg[2]) == 1 || degree(bi, bj) == 1 || !connected()) {
                    arr[idx] = false;
                } else {
                    done++;
                }
            }
            return done;
        }

        int degree(int i, int j) {
            return (xDrawn(i, j) ? 1 : 0) + (xDrawn(i, j - 1) ? 1 : 0) + (zDrawn(i, j) ? 1 : 0) + (zDrawn(i - 1, j) ? 1 : 0);
        }

        /** Whether every street node is reachable from every other one. */
        private boolean connected() {
            boolean[] seen = new boolean[nw * nw];
            int total = 0;
            int start = -1;
            for (int j = -n; j <= n + 1; j++) {
                for (int i = -n; i <= n + 1; i++) {
                    if (degree(i, j) > 0) {
                        total++;
                        start = (i + n) + (j + n) * nw;
                    }
                }
            }
            if (start < 0) {
                return true;
            }
            int[] stack = new int[nw * nw];
            int sp = 0;
            stack[sp++] = start;
            seen[start] = true;
            int reached = 0;
            while (sp > 0) {
                int g = stack[--sp];
                reached++;
                int i = g % nw - n;
                int j = g / nw - n;
                int[][] nb = {
                        {i, j + 1, xDrawn(i, j) ? 1 : 0}, {i, j - 1, xDrawn(i, j - 1) ? 1 : 0},
                        {i + 1, j, zDrawn(i, j) ? 1 : 0}, {i - 1, j, zDrawn(i - 1, j) ? 1 : 0}};
                for (int[] q : nb) {
                    if (q[2] == 1) {
                        int h = (q[0] + n) + (q[1] + n) * nw;
                        if (!seen[h]) {
                            seen[h] = true;
                            stack[sp++] = h;
                        }
                    }
                }
            }
            return reached == total;
        }

        private boolean cut(boolean[] arr, int i, int j) {
            if (arr == null || i < -n || i > n + 1 || j < -n || j > n + 1) {
                return false;
            }
            return arr[(i + n) + (j + n) * nw];
        }

        /** Whether the street segment was in the grid but has been removed. */
        boolean xRemoved(int i, int j) {
            return (isCity(i - 1, j) || isCity(i, j)) && cut(xCut, i, j);
        }

        boolean zRemoved(int i, int j) {
            return (isCity(i, j - 1) || isCity(i, j)) && cut(zCut, i, j);
        }

        boolean isCity(int i, int j) {
            return i >= -n && i <= n && j >= -n && j <= n && city[(i + n) + (j + n) * w];
        }

        int corner(int i, int j) {
            if (i < -n || i > n + 1 || j < -n || j > n + 1) {
                return 0;
            }
            return corner[(i + n) + (j + n) * nw];
        }

        /** North-south line i, block row j. */
        boolean xDrawn(int i, int j) {
            return (isCity(i - 1, j) || isCity(i, j)) && !cut(xCut, i, j);
        }

        /** East-west line j, block column i. */
        boolean zDrawn(int i, int j) {
            return (isCity(i, j - 1) || isCity(i, j)) && !cut(zCut, i, j);
        }
    }

    /**
     * A possible highway exit, leaving the city in direction {@code dir}: either an outline node (i, j) where an inner
     * street meets the outer street ({@code mid = false}; the highway continues that street), or the middle of the
     * outline segment starting at node (i, j) ({@code mid = true}; along north-south line i for EAST/WEST, along
     * east-west line j for NORTH/SOUTH).
     */
    public record Exit(int i, int j, int dir, boolean mid) {
        public static final int EAST = 0;
        public static final int WEST = 1;
        public static final int SOUTH = 2;
        public static final int NORTH = 3;
    }

    Layout layout() {
        Layout l = layout;
        if (l == null) {
            l = computeLayout();
            layout = l;
        }
        return l;
    }

    /**
     * The fixed grid: GRID x GRID blocks (blocks -1..0 for GRID = 2), 3 x 3 streets, 9 crossings (1 of 4 ways in the
     * middle, 4 of 3 ways on the border, 4 of 2 ways at the corners). The terrain under it is filled or cut
     * to the city level.
     */
    private Layout computeLayout() {
        int n = Math.max(-FIRST, LAST);
        int w = 2 * n + 1;
        boolean[] city = new boolean[w * w];
        for (int j = FIRST; j <= LAST; j++) {
            for (int i = FIRST; i <= LAST; i++) {
                city[(i + n) + (j + n) * w] = true;
            }
        }
        return new Layout(n, city, seed, MAX_CUTS);
    }

    public int blockCount() {
        return layout().blocks;
    }

    public int gridHalf() {
        return layout().n;
    }

    public boolean isCityBlock(int i, int j) {
        return layout().isCity(i, j);
    }

    public boolean xSegment(int i, int j) {
        return layout().xDrawn(i, j);
    }

    public boolean zSegment(int i, int j) {
        return layout().zDrawn(i, j);
    }

    /** Whether the north-south segment (line i, row j) was removed by the random street cuts. */
    public boolean xRemoved(int i, int j) {
        return layout().xRemoved(i, j);
    }

    public boolean zRemoved(int i, int j) {
        return layout().zRemoved(i, j);
    }

    /** Whether column (x, z) lies on the band of a removed street segment. */
    public boolean onRemovedStreet(double x, double z) {
        Layout L = layout();
        int i = (int) Math.round((x - u0) / S);
        int j = (int) Math.round((z - v0) / S);
        int bi = (int) Math.floor((x - u0) / S);
        int bj = (int) Math.floor((z - v0) / S);
        return Math.abs(x - U(i)) <= Params.STREET_HW && L.xRemoved(i, bj)
                || Math.abs(z - V(j)) <= Params.STREET_HW && L.zRemoved(bi, j);
    }

    /** Number of street segments removed from the grid (0..{@link #MAX_CUTS}). */
    public int streetCuts() {
        return layout().cuts;
    }

    public int cornerAt(int i, int j) {
        return layout().corner(i, j);
    }

    public List<Exit> exits() {
        return layout().exits;
    }

    /** World position of a grid node. */
    public double[] nodeWorld(int i, int j) {
        return warp.toWorld(U(i), V(j));
    }

    /** World position where a highway leaving through this exit starts (on the outer street's centerline). */
    public double[] exitWorld(Exit e) {
        if (!e.mid()) {
            return nodeWorld(e.i(), e.j());
        }
        boolean eastWest = e.dir() == Exit.EAST || e.dir() == Exit.WEST;
        return eastWest ? warp.toWorld(U(e.i()), V(e.j()) + S / 2.0) : warp.toWorld(U(e.i()) + S / 2.0, V(e.j()));
    }

    /** Unit world direction of a highway leaving through this exit (perpendicular to the outer street). */
    public double[] exitDirection(Exit e) {
        double[] p = exitWorld(e);
        double dx;
        double dz;
        switch (e.dir()) {
            case Exit.EAST -> {
                dx = 1;
                dz = warp.dwz(p[0]);
            }
            case Exit.WEST -> {
                dx = -1;
                dz = -warp.dwz(p[0]);
            }
            case Exit.SOUTH -> {
                dx = warp.dwx(p[1]);
                dz = 1;
            }
            default -> {
                dx = -warp.dwx(p[1]);
                dz = -1;
            }
        }
        double len = Math.sqrt(dx * dx + dz * dz);
        return new double[]{dx / len, dz / len};
    }

    /** Whether the street that continues into the city from this exit is an avenue. */
    public boolean exitOnAvenue(Exit e) {
        if (e.mid()) {
            return false;
        }
        int line = e.dir() == Exit.EAST || e.dir() == Exit.WEST ? e.j() : e.i();
        return Math.floorMod(line, Params.AVENUE_EVERY) == 0;
    }

    // ------------------------------------------------------------------ column query

    /** Result of {@link #query}. Reused between calls (not thread safe: one per thread / per chunk job). */
    public static final class Hit {
        /** Inside the band of at least one street. */
        public boolean road;
        /** Inside the lanes of at least one street. */
        public boolean lane;
        /** On a dash of the center marking (only outside intersections). */
        public boolean mark;
        /** Number of street bands covering the column (2+ = intersection). */
        public int count;
        /** Buildable ground of a city block (not street, inside the rounded corners). */
        public boolean lot;
        /** Distance beyond the outer edge of the nearest street; negative inside a street, +inf when far. */
        public double edge;
        /** Grid block containing the column. */
        public int bi;
        public int bj;

        void reset() {
            road = false;
            lane = false;
            mark = false;
            count = 0;
            lot = false;
            edge = Double.POSITIVE_INFINITY;
        }

        /** Whether the column belongs to the built city (a street or a lot). */
        public boolean inside() {
            return road || lot;
        }
    }

    /**
     * Classifies the column whose center is (x, z). Pure function of the position: every chunk, and the planner,
     * get exactly the same answer for a column.
     */
    public void query(double x, double z, Hit h) {
        Layout L = layout();
        h.reset();
        double u = x;
        double v = z;
        int bi = (int) Math.floor((u - u0) / S);
        int bj = (int) Math.floor((v - v0) / S);
        h.bi = bi;
        h.bj = bj;
        double hw = Params.STREET_HW;
        double lane = Params.STREET_LANE_HW;
        int iLo = (int) Math.ceil((u - SEARCH - u0) / S);
        int iHi = (int) Math.floor((u + SEARCH - u0) / S);
        int jLo = (int) Math.ceil((v - SEARCH - v0) / S);
        int jHi = (int) Math.floor((v + SEARCH - v0) / S);
        // crossings: a 17 x 17 square of lanes, smooth stone 2 x 2 in the corners, and sidewalk along the sides
        // that have no street (the border of the city)
        for (int j = jLo; j <= jHi; j++) {
            for (int i = iLo; i <= iHi; i++) {
                boolean north = L.xDrawn(i, j - 1);
                boolean south = L.xDrawn(i, j);
                boolean west = L.zDrawn(i - 1, j);
                boolean east = L.zDrawn(i, j);
                if ((north ? 1 : 0) + (south ? 1 : 0) + (west ? 1 : 0) + (east ? 1 : 0) < 2) {
                    continue;
                }
                double a = u - U(i);
                double b = v - V(j);
                double ex = Math.max(Math.abs(a) - hw, 0);
                double ez = Math.max(Math.abs(b) - hw, 0);
                double e = Math.sqrt(ex * ex + ez * ez);
                if (e > 0) {
                    h.edge = Math.min(h.edge, e);
                    continue;
                }
                boolean side = Math.abs(a) > lane && Math.abs(b) > lane
                        || b < -lane && !north || b > lane && !south || a < -lane && !west || a > lane && !east;
                h.road = true;
                h.count = 2;
                h.lane = !side;
                h.mark = false;
                h.edge = -1;
                return;
            }
        }
        boolean markCandidate = false;
        double markAlong = 0;
        // north-south streets (only the segment of block row bj can contain v)
        for (int i = iLo; i <= iHi; i++) {
            if (L.xDrawn(i, bj) && v >= V(bj) && v <= V(bj + 1)) {
                if (consider(h, Math.abs(u - U(i)), false)) {
                    markCandidate = true;
                    markAlong = v - V(bj) - hw;
                }
            }
        }
        // east-west streets
        for (int j = jLo; j <= jHi; j++) {
            if (L.zDrawn(bi, j) && u >= U(bi) && u <= U(bi + 1)) {
                if (consider(h, Math.abs(v - V(j)), false)) {
                    markCandidate = true;
                    markAlong = u - U(bi) - hw;
                }
            }
        }
        h.mark = h.count == 1 && markCandidate && dash((int) Math.floor(markAlong), S - (int) (2 * hw));
        if (!h.road && L.isCity(bi, bj)) {
            h.lot = true;
        } else if (!h.road) {
            // outside the blocks: the band of a removed border street is still city ground (joined to its block)
            for (int i = iLo; i <= iHi; i++) {
                if (L.xRemoved(i, bj) && v >= V(bj) && v <= V(bj + 1)) {
                    double e = Math.abs(u - U(i)) - hw;
                    if (e > 0) {
                        h.edge = Math.min(h.edge, e);
                    } else {
                        h.lot = true;
                        h.bi = L.isCity(i - 1, bj) ? i - 1 : i;
                    }
                }
            }
            for (int j = jLo; j <= jHi; j++) {
                if (L.zRemoved(bi, j) && u >= U(bi) && u <= U(bi + 1)) {
                    double e = Math.abs(v - V(j)) - hw;
                    if (e > 0) {
                        h.edge = Math.min(h.edge, e);
                    } else {
                        h.lot = true;
                        h.bj = L.isCity(bi, j - 1) ? j - 1 : j;
                    }
                }
            }
        }
    }

    /**
     * Center marking along a street {@code run} long: as many dashes of 11 with gaps of 6 as fit, centered (68: 3 + 11
     * + 6 + 11 + 6 + 11 + 6 + 11 + 3), so the street looks the same from both crossings.
     */
    static boolean dash(int t, int run) {
        if (t < 0 || t >= run) {
            return false;
        }
        int n = (run + Params.DASH_GAP) / (Params.DASH + Params.DASH_GAP);
        int len = n * Params.DASH + (n - 1) * Params.DASH_GAP;
        int k = t - (run - len) / 2;
        return k >= 0 && k < len && k % (Params.DASH + Params.DASH_GAP) < Params.DASH;
    }

    /** Registers one street primitive at world distance d; returns true if the column is on its center marking. */
    private static boolean consider(Hit h, double d, boolean avenue) {
        double hw = avenue ? Params.AVENUE_HW : Params.STREET_HW;
        double lane = avenue ? Params.AVENUE_LANE_HW : Params.STREET_LANE_HW;
        double e = d - hw;
        if (e < h.edge) {
            h.edge = e;
        }
        if (d <= hw) {
            h.count++;
            h.road = true;
            if (d <= lane) {
                h.lane = true;
            }
            return d <= Params.MARK_HW;
        }
        return false;
    }

    // ------------------------------------------------------------------ lots

    /** Lots of a city block (cached). */
    public List<Lot> lots(int bi, int bj) {
        Layout L = layout();
        if (!L.isCity(bi, bj)) {
            return List.of();
        }
        int k = (bi + L.n) + (bj + L.n) * L.w;
        List<Lot> lots = L.lots.get(k);
        if (lots == null) {
            lots = computeLots(bi, bj);
            L.lots.compareAndSet(k, null, lots);
            lots = L.lots.get(k);
        }
        return lots;
    }

    /** The lot containing column (x, z) of block (bi, bj), or null (a yard between lots). */
    public Lot lotAt(int bi, int bj, int x, int z) {
        for (Lot lot : lots(bi, bj)) {
            if (lot.contains(x, z)) {
                return lot;
            }
        }
        return null;
    }

    private boolean buildable(Hit h, int bi, int bj, int x, int z, boolean setback) {
        query(x + 0.5, z + 0.5, h);
        return h.lot && !h.road && h.bi == bi && h.bj == bj && (!setback || h.edge >= Params.LOT_SETBACK);
    }

    /** Free ground of the city: gray concrete powder. */
    public int yardCode() {
        return BlockCode.CITY_GROUND;
    }

    private List<Lot> computeLots(int bi, int bj) {
        // the free rectangle of a grid block: from the sidewalks; across a removed street up to its middle (the
        // two blocks join), or over the whole band of a removed street on the border of the city
        Layout L = layout();
        double hw = Params.STREET_HW;
        boolean[] street = {L.xDrawn(bi, bj), L.xDrawn(bi + 1, bj), L.zDrawn(bi, bj), L.zDrawn(bi, bj + 1)};
        int x0 = (int) Math.round(street[0] ? U(bi) + hw : L.isCity(bi - 1, bj) ? U(bi) - 0.5 : U(bi) - hw);
        int x1 = (int) Math.round(street[1] ? U(bi + 1) - hw - 1 : L.isCity(bi + 1, bj) ? U(bi + 1) - 1.5 : U(bi + 1) + hw - 1);
        int z0 = (int) Math.round(street[2] ? V(bj) + hw : L.isCity(bi, bj - 1) ? V(bj) - 0.5 : V(bj) - hw);
        int z1 = (int) Math.round(street[3] ? V(bj + 1) - hw - 1 : L.isCity(bi, bj + 1) ? V(bj + 1) - 1.5 : V(bj + 1) + hw - 1);
        // a corner over two removed streets can still be a crossing of the other two streets (or lie outside the
        // city when both are border streets): that side then stops at the street line
        Hit h = new Hit();
        if (!buildable(h, bi, bj, x0, z0, false) || !buildable(h, bi, bj, x1, z0, false)) {
            z0 = (int) Math.round(V(bj) + hw);
        }
        if (!buildable(h, bi, bj, x0, z1, false) || !buildable(h, bi, bj, x1, z1, false)) {
            z1 = (int) Math.round(V(bj + 1) - hw - 1);
        }
        return blockLots(bi, bj, x0, z0, x1, z1);
    }

    /** Share of the city blocks that are one whole park (grass and trees, no buildings). */
    public static final double PARK_BLOCK_CHANCE = 0.125;
    /**
     * The lots of block (bi, bj), inside its free rectangle x0..x1, z0..z1 (whose outline touches the sidewalks, and
     * reaches across removed streets): some blocks are a single park; the others have no lots yet, all their ground is
     * the city's free ground (gray concrete powder). No buildings: they will be designed anew.
     */
    private List<Lot> blockLots(int bi, int bj, int x0, int z0, int x1, int z1) {
        long rs = Hash.of(seed, 0x52494E47L, bi, bj);
        if (Hash.unit(rs) < PARK_BLOCK_CHANCE) {
            return List.of(Lot.park(x0, z0, x1, z1, Hash.next(rs, 1)));
        }
        return List.of();
    }

    /** 0 at the outskirts, 1 at the center. */
    public double centrality(double x, double z) {
        double dx = x - cx;
        double dz = z - cz;
        return Math.max(0, Math.min(1, 1 - Math.sqrt(dx * dx + dz * dz) / (radius * 1.15)));
    }

    @Override
    public String toString() {
        return "City[" + ci + "," + cj + " @ " + cx + "," + cz + " r=" + radius + " level=" + level + "]";
    }
}
