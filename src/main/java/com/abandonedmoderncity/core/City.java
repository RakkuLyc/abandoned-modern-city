package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * One city: a set of square blocks on a (gently waving) grid, all at a single walking height {@link #level}.
 *
 * <h2>Streets</h2>
 * Every block side that touches at least one city block is a street segment. So every street node has degree 2, 3
 * or 4: there are no dead ends anywhere, and the outer streets form closed rings around the city. Where only one of the
 * four blocks around a node is a city block (a convex corner of the city outline) the two outer streets meet at a right
 * angle. Every segment is a street of the same width.
 *
 * <h2>Stages</h2>
 * {@link #create} only samples a handful of terrain points (does the cell have a city, where, how big, at what height);
 * the block layout (more samples) is computed lazily the first time the geometry is needed.
 * <p>
 * Grid space: {@code u = x - wx(z)}, {@code v = z - wz(x)} (see {@link Warp}). North-south grid line {@code i} is
 * {@code u = U(i)}, east-west line {@code j} is {@code v = V(j)}; block (i, j) spans {@code [U(i), U(i+1)] x [V(j), V(j+1)]}.
 */
public final class City {
    /**
     * Distance between parallel street centerlines: SX between the north-south streets (along x), SZ between the
     * east-west ones. One of them is {@link Params#BLOCK_LONG}, the other {@link Params#BLOCK_SHORT}, rolled per city.
     */
    public final int SX;
    public final int SZ;
    /** Whether the long side of the blocks runs east-west (along x). */
    public final boolean longX;
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
        this.longX = Hash.unit(Hash.next(seed, 21)) < 0.5;
        this.SX = longX ? Params.BLOCK_LONG : Params.BLOCK_SHORT;
        this.SZ = longX ? Params.BLOCK_SHORT : Params.BLOCK_LONG;
        this.cx = cx;
        this.cz = cz;
        this.radius = radius;
        this.level = level;
        this.influence = Params.influence(radius);
        this.warp = warp;
        // street centerlines on block centers (x + 0.5), so a 33-wide street covers exactly 33 columns
        // the grid (blocks FIRST..LAST) is centered on the city center
        this.u0 = cx - (int) Math.floor((FIRST + LAST + 1) / 2.0 * SX) + 0.5;
        this.v0 = cz - (int) Math.floor((FIRST + LAST + 1) / 2.0 * SZ) + 0.5;
        this.shapeA2 = a2;
        this.shapeP2 = p2;
        this.shapeA3 = a3;
        this.shapeP3 = p3;
        this.terrain = terrain;
    }

    // ------------------------------------------------------------------ stage 1

    /**
     * Entrances (highways) of a city: one per side, rolled per city among the crossings that can leave that way (the
     * border T crossing of the side or one of its two corners; one highway per crossing). A fully random choice of 4 of
     * the 8 left many cities without an entrance toward their neighbour (23% of the highways lost).
     */
    public static final int EXITS = 4;
    /** Grid blocks of a city: FIRST..LAST in both directions (-1..0 for the 2 x 2 grid, -2..1 for 4 x 4). */
    public static final int FIRST = -(Params.GRID / 2);
    public static final int LAST = (Params.GRID + 1) / 2 - 1;

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
     * Rolls whether the cell has a city, which then stands in the middle of the cell if that place is dry enough and not
     * too high (the cities form a regular grid, so the highways between neighbours are ~270 to ~400 blocks long).
     */
    private static Object site(long worldSeed, Params p, Terrain t, int ci, int cj) {
        long h = Hash.of(worldSeed, SALT, ci, cj);
        if (Hash.unit(h) >= p.cityChance()) {
            return "no city rolled";
        }
        // the city stands in the middle of its cell (highways of a regular length between neighbours); a cell whose
        // middle is mostly water or too high has no city
        int cx = ci * p.cellSize() + p.cellSize() / 2;
        int cz = cj * p.cellSize() + p.cellSize() / 2;
        int radius = Params.GRID_RADIUS;
        Object r = trySite(p, t, cx, cz, radius);
        if (r instanceof String s) {
            return s;
        }
        return build(h, ci, cj, cx, cz, radius, (Integer) r, t);
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
        return u0 + (double) i * SX;
    }

    public double V(int j) {
        return v0 + (double) j * SZ;
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

        Layout(int n, boolean[] city, long seed) {
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
                        // a corner of the city: a highway may continue either of its two border streets outwards
                        ex.add(new Exit(i, j, seB || neB ? Exit.WEST : Exit.EAST, false));
                        ex.add(new Exit(i, j, seB || swB ? Exit.NORTH : Exit.SOUTH, false));
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
            // the border T crossings and the corners (two directions each) are the possible places; each city rolls
            // EXITS of them, and WorldPlan picks the entrances among those
            // one per side: for each direction, one of the crossings that can leave that way (the middle T or one of
            // the two corners), never the same crossing twice
            List<Exit> picked = new ArrayList<>();
            List<Integer> dirs = new ArrayList<>(List.of(Exit.WEST, Exit.EAST, Exit.NORTH, Exit.SOUTH));
            dirs.sort(Comparator.comparingLong(d -> Hash.of(seed, 0x45584FL, d)));
            for (int dir : dirs) {
                List<Exit> c = new ArrayList<>();
                for (Exit e : ex) {
                    boolean used = false;
                    for (Exit q : picked) {
                        used |= q.i() == e.i() && q.j() == e.j();
                    }
                    if (e.dir() == dir && !used) {
                        c.add(e);
                    }
                }
                if (!c.isEmpty()) {
                    c.sort(Comparator.comparingLong(e -> Hash.of(seed, 0x455854L, Cache.key(e.i(), e.j()))));
                    picked.add(c.get(0));
                }
            }
            ex = picked;
            this.exits = List.copyOf(ex);
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
            return isCity(i - 1, j) || isCity(i, j);
        }

        /** East-west line j, block column i. */
        boolean zDrawn(int i, int j) {
            return isCity(i, j - 1) || isCity(i, j);
        }
    }

    /**
     * A possible highway exit, leaving the city in direction {@code dir}: either an outline node (i, j) where an inner
     * street meets the outer street, or a corner of the city ({@code mid = false}; the highway continues the street that
     * runs in direction {@code dir}; a corner has one exit per direction, a single highway uses it), or the middle of the
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
        return new Layout(n, city, seed);
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
        return eastWest ? warp.toWorld(U(e.i()), V(e.j()) + SZ / 2.0) : warp.toWorld(U(e.i()) + SX / 2.0, V(e.j()));
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

    // ------------------------------------------------------------------ column query

    /** Result of {@link #query}. Reused between calls (not thread safe: one per thread / per chunk job). */
    public static final class Hit {
        /** Inside the band of at least one street. */
        public boolean road;
        /** Inside the lanes of at least one street. */
        public boolean lane;
        /** On a dash of the center marking (only outside intersections). */
        public boolean mark;
        /** On a white stripe of a crosswalk (only outside intersections). */
        public boolean crosswalk;
        /**
         * Street light at the column ({@link StreetLights}): its offset from the post ({@link StreetLights#NO_LAMP} for
         * none, -1 the sidewalk row behind the post, 0 the post, 1 to {@link StreetLights#LAMP_ARM} the lanes under the
         * arm), the variant of that light (0 to {@link StreetLights#LAMP_VARIANTS} - 1), the side its post is on from the row behind it (0 north, 1
         * east, 2 south, 3 west) and a seed for its blocks.
         */
        public int lamp;
        public int lampVariant;
        public int lampFace;
        public long lampSeed;
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
            crosswalk = false;
            lamp = StreetLights.NO_LAMP;
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
        int bi = (int) Math.floor((u - u0) / SX);
        int bj = (int) Math.floor((v - v0) / SZ);
        h.bi = bi;
        h.bj = bj;
        double hw = Params.STREET_HW;
        double lane = Params.STREET_LANE_HW;
        int iLo = (int) Math.ceil((u - SEARCH - u0) / SX);
        int iHi = (int) Math.floor((u + SEARCH - u0) / SX);
        int jLo = (int) Math.ceil((v - SEARCH - v0) / SZ);
        int jHi = (int) Math.floor((v + SEARCH - v0) / SZ);
        // crossings: a 33 x 33 square of lanes, smooth stone 3 x 3 in the corners, and sidewalk along the sides
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
        boolean crossCandidate = false;
        double markAlong = 0;
        int markRun = 0;
        // north-south streets (only the segment of block row bj can contain v)
        for (int i = iLo; i <= iHi; i++) {
            if (L.xDrawn(i, bj) && v >= V(bj) && v <= V(bj + 1)) {
                if (consider(h, Math.abs(u - U(i)), false)) {
                    markCandidate = true;
                    markAlong = v - V(bj) - hw;
                    markRun = SZ - (int) (2 * hw);
                }
                crossCandidate |= crosswalk(u - U(i), v - V(bj) - hw, SZ - (int) (2 * hw));
                StreetLights.lampCandidate(h, seed, u - U(i), v - V(bj) - hw, SZ - (int) (2 * hw), 0, i, bj);
            }
        }
        // east-west streets
        for (int j = jLo; j <= jHi; j++) {
            if (L.zDrawn(bi, j) && u >= U(bi) && u <= U(bi + 1)) {
                if (consider(h, Math.abs(v - V(j)), false)) {
                    markCandidate = true;
                    markAlong = u - U(bi) - hw;
                    markRun = SX - (int) (2 * hw);
                }
                crossCandidate |= crosswalk(v - V(j), u - U(bi) - hw, SX - (int) (2 * hw));
                StreetLights.lampCandidate(h, seed, v - V(j), u - U(bi) - hw, SX - (int) (2 * hw), 1, bi, j);
            }
        }
        h.mark = h.count == 1 && markCandidate && dash((int) Math.floor(markAlong), markRun);
        if (h.count != 1) {
            h.lamp = StreetLights.NO_LAMP;
        }
        h.crosswalk = h.count == 1 && crossCandidate && !h.mark;
        if (!h.road && L.isCity(bi, bj)) {
            h.lot = true;
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

    /** The blocks of the street light at the column (x, z): index dy over the street surface, or null for none. */
    public int[] lampParts(double x, double z) {
        Hit h = new Hit();
        query(x, z, h);
        return StreetLights.lampParts(h);
    }

    /**
     * Margin around a city where the creatures of a village built over by it are removed (the villagers and iron
     * golems of the houses the city replaced would be left standing in its streets or buried in its fill).
     */
    public static final double VILLAGE_MARGIN = 32;

    /** Whether (x, z) is in the city (a street or a lot) or {@code margin} or less beyond its streets. */
    public boolean within(double x, double z, double margin, Hit h) {
        query(x, z, h);
        return h.road || h.lot || h.edge <= margin;
    }

    /** Rows of a crosswalk along the street, and lane rows between it and the crossing. */
    public static final int CROSSWALK_DEPTH = 3;
    public static final int CROSSWALK_GAP = 2;

    /**
     * Whether a column at signed distance {@code across} from a street's centerline, {@code along} blocks into a
     * segment {@code run} long (from the edge of the crossing), is a white stripe of the zebra crossing that each end
     * of the segment has: {@link #CROSSWALK_DEPTH} rows starting {@link #CROSSWALK_GAP} rows from the crossing,
     * stripes of 1 block with gaps of 1 across the 27 lanes (both outer lanes white).
     */
    static boolean crosswalk(double across, double along, int run) {
        if (Math.abs(across) > Params.STREET_LANE_HW) {
            return false;
        }
        int t = (int) Math.floor(along);
        boolean row = t >= CROSSWALK_GAP && t < CROSSWALK_GAP + CROSSWALK_DEPTH
                || t >= run - CROSSWALK_GAP - CROSSWALK_DEPTH && t < run - CROSSWALK_GAP;
        return row && Math.floorMod((int) Math.round(across), 2) == 1;
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

    /** Free ground of the city: gray concrete powder. */
    public int yardCode() {
        return BlockCode.CITY_GROUND;
    }

    private List<Lot> computeLots(int bi, int bj) {
        int[] r = freeRect(bi, bj);
        return blockLots(bi, bj, r[0], r[1], r[2], r[3]);
    }

    /** Free rectangle {x0, z0, x1, z1} of block (bi, bj): the ground between its four sidewalks. */
    private int[] freeRect(int bi, int bj) {
        double hw = Params.STREET_HW;
        int x0 = (int) Math.round(U(bi) + hw);
        int x1 = (int) Math.round(U(bi + 1) - hw - 1);
        int z0 = (int) Math.round(V(bj) + hw);
        int z1 = (int) Math.round(V(bj + 1) - hw - 1);
        return new int[]{x0, z0, x1, z1};
    }

    /** Size of the park of a park block, along the block's long and short sides. */
    public static final int PARK_LONG = 110;
    public static final int PARK_SHORT = 64;

    /** The park of a park block, in a corner of its free rectangle f drawn from rs: {x0, z0, x1, z1}. */
    static int[] parkRect(int[] f, long rs) {
        int w = f[2] - f[0] + 1;
        int h = f[3] - f[1] + 1;
        int pw = Math.min(w, w >= h ? PARK_LONG : PARK_SHORT);
        int ph = Math.min(h, w >= h ? PARK_SHORT : PARK_LONG);
        int corner = Hash.range(Hash.next(rs, 4), 0, 3);
        int x0 = (corner & 1) == 0 ? f[0] : f[2] - pw + 1;
        int z0 = (corner & 2) == 0 ? f[1] : f[3] - ph + 1;
        return new int[]{x0, z0, x0 + pw - 1, z0 + ph - 1};
    }

    /** Whether block (bi, bj) has a park (in one of its corners). */
    private boolean parkBlock(int bi, int bj) {
        return Hash.unit(Hash.of(seed, 0x52494E47L, bi, bj)) < PARK_BLOCK_CHANCE;
    }

    /** The rings of structures of all the blocks of the city, keyed by {@link Cache#key} of the block (lazy). */
    private volatile Map<Long, List<Lot>> rings;

    /**
     * Rings of the whole city at once, block by block in a fixed order (north to south, west to east), so that the
     * spawn limits per city ({@link Structures#LIMITS}) hold whatever block is asked for first.
     */
    private Map<Long, List<Lot>> rings() {
        Map<Long, List<Lot>> r = rings;
        if (r != null) {
            return r;
        }
        synchronized (this) {
            if (rings == null) {
                Layout L = layout();
                Map<String, Integer> used = new HashMap<>();
                Map<Long, List<Lot>> out = new HashMap<>();
                for (int bj = -L.n; bj <= L.n; bj++) {
                    for (int bi = -L.n; bi <= L.n; bi++) {
                        if (!L.isCity(bi, bj)) {
                            continue;
                        }
                        int[] f = freeRect(bi, bj);
                        long rs = Hash.of(seed, 0x52494E47L, bi, bj);
                        int[] park = parkBlock(bi, bj) ? parkRect(f, rs) : null;
                        out.put(Cache.key(bi, bj), Ring.lots(f[0], f[1], f[2], f[3], Hash.next(rs, 2), used, park));
                    }
                }
                rings = Map.copyOf(out);
            }
            return rings;
        }
    }

    /** Share of the city blocks with a park in a corner (grass, trees, paths, the fountain). */
    public static final double PARK_BLOCK_CHANCE = 0.125;
    /**
     * The lots of block (bi, bj), inside its free rectangle x0..x1, z0..z1 (whose outline touches the sidewalks): a ring of structures against the sidewalks ({@link Ring}, drawn at random
     * under the per-city limits of {@link Structures#LIMITS}); some blocks have a park of {@link #PARK_LONG} x
     * {@link #PARK_SHORT} in a corner, the structures then only along the two other streets; the rest is left to
     * nature ({@link Wild}).
     */
    private List<Lot> blockLots(int bi, int bj, int x0, int z0, int x1, int z1) {
        long rs = Hash.of(seed, 0x52494E47L, bi, bj);
        List<Lot> ring = rings().getOrDefault(Cache.key(bi, bj), List.of());
        List<Lot> out = new ArrayList<>(ring);
        if (parkBlock(bi, bj)) {
            // the park in a corner of the block, the structures along the two other streets
            int[] p = parkRect(new int[]{x0, z0, x1, z1}, rs);
            out.add(Lot.park(p[0], p[1], p[2], p[3], Hash.next(rs, 1)));
        }
        // the rest of the block is left to nature (listed last: the structures and the park win where they stand)
        List<Lot> obstacles = List.copyOf(out);
        out.add(Lot.wild(x0, z0, x1, z1, Hash.next(rs, 3), obstacles));
        return List.copyOf(out);
    }

    @Override
    public String toString() {
        return "City[" + ci + "," + cj + " @ " + cx + "," + cz + " r=" + radius + " level=" + level + "]";
    }
}
