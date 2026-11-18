package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The whole plan of a world: cities, the highway network and, per chunk, what each column should become.
 * Pure function of (seed, params, terrain); thread safe; every answer is cached.
 *
 * <h2>Highway network</h2>
 * <ol>
 *   <li>Candidate links: the Gabriel graph of the city centers (A-B is a link when no other city lies inside the
 *   circle of diameter AB), limited to {@link Params#MAX_LINK}. It is planar and connected, so it links every city
 *   to its natural neighbors without crossings of the straight links.</li>
 *   <li>Each city assigns one exit (an outline T junction) per neighbor, nearest neighbors first, choosing the exit
 *   that faces the neighbor (avenues preferred). The highway leaves straight along the street it continues.</li>
 *   <li>A link is kept when its curve is long enough to ramp between the two levels, has no curve tighter than
 *   {@link Params#MIN_CURVE_RADIUS}, stays clear of every city it does not serve (and of its own cities after the
 *   junction), is not mostly over water, and does not come closer than {@link Params#HIGHWAY_SEPARATION} to a
 *   shorter kept link (so highways never cross or overlap).</li>
 * </ol>
 */
public final class WorldPlan {
    public final long seed;
    public final Params params;
    public final Terrain terrain;
    public final int sea;

    private final ConcurrentHashMap<Long, Optional<City>> cities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<City>> neighbors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Map<Long, City.Exit>> exits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Optional<Highway>> candidates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> finals = new ConcurrentHashMap<>();
    private final Cache<List<Highway>> tiles = new Cache<>(4096);
    private final Cache<ChunkPlan> chunks = new Cache<>(8192);

    public static final int TILE = 512;
    /** How far from its centerline a highway changes anything (band + slopes). */
    public static final double HIGHWAY_REACH = Params.HIGHWAY_HW + Params.HIGHWAY_SKIRT + 1;

    public WorldPlan(long seed, Params params, Terrain terrain) {
        this.seed = seed;
        this.params = params;
        this.terrain = terrain;
        this.sea = terrain.seaLevel();
    }

    private static <V> V memo(ConcurrentHashMap<Long, V> map, long key, Supplier<V> f) {
        V v = map.get(key);
        if (v != null) {
            return v;
        }
        v = f.get();
        V prev = map.putIfAbsent(key, v);
        return prev != null ? prev : v;
    }

    // ------------------------------------------------------------------ cities

    public City city(int ci, int cj) {
        return memo(cities, Cache.key(ci, cj),
                () -> Optional.ofNullable(City.create(seed, params, terrain, ci, cj))).orElse(null);
    }

    /** Cities whose center lies in the rectangle. */
    public List<City> citiesIn(double x0, double z0, double x1, double z1) {
        int cell = params.cellSize();
        // the cells not decided yet are decided at once, on several cores (each costs 9 terrain samples, ~26 ms in the
        // game, and the first chunks of a world ask for ~400 of them): City.create is a pure function of the seed and
        // the terrain, so the cities are the same whatever the order (18 Nov 2026, asked by the user)
        List<long[]> missing = new ArrayList<>();
        for (int cj = Math.floorDiv((int) Math.floor(z0), cell); cj <= Math.floorDiv((int) Math.floor(z1), cell); cj++) {
            for (int ci = Math.floorDiv((int) Math.floor(x0), cell); ci <= Math.floorDiv((int) Math.floor(x1), cell); ci++) {
                if (!cities.containsKey(Cache.key(ci, cj))) {
                    missing.add(new long[]{ci, cj});
                }
            }
        }
        if (missing.size() > 1) {
            missing.parallelStream().forEach(c -> city((int) c[0], (int) c[1]));
        }
        List<City> out = new ArrayList<>();
        for (int cj = Math.floorDiv((int) Math.floor(z0), cell); cj <= Math.floorDiv((int) Math.floor(z1), cell); cj++) {
            for (int ci = Math.floorDiv((int) Math.floor(x0), cell); ci <= Math.floorDiv((int) Math.floor(x1), cell); ci++) {
                City c = city(ci, cj);
                if (c != null && c.cx >= x0 && c.cx <= x1 && c.cz >= z0 && c.cz <= z1) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    /** The city whose area of influence comes within {@code slack} of (x, z) (at most one, see {@link Params#margin}). */
    public City cityNear(double x, double z, double slack) {
        int cell = params.cellSize();
        int ci = Math.floorDiv((int) Math.floor(x), cell);
        int cj = Math.floorDiv((int) Math.floor(z), cell);
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                City c = city(ci + di, cj + dj);
                if (c == null) {
                    continue;
                }
                double dx = x - c.cx;
                double dz = z - c.cz;
                double r = c.influence + slack;
                if (dx * dx + dz * dz <= r * r) {
                    return c;
                }
            }
        }
        return null;
    }

    public City cityAt(double x, double z) {
        return cityNear(x, z, 0);
    }

    /** Gabriel neighbors of a city, nearest first. */
    public List<City> neighbors(City a) {
        return memo(neighbors, a.id, () -> computeNeighbors(a));
    }

    private List<City> computeNeighbors(City a) {
        double max = Params.MAX_LINK;
        List<City> near = citiesIn(a.cx - max, a.cz - max, a.cx + max, a.cz + max);
        List<City> out = new ArrayList<>();
        for (City b : near) {
            if (b == a) {
                continue;
            }
            double d2 = dist2(a, b);
            if (d2 > max * max) {
                continue;
            }
            double mx = (a.cx + b.cx) / 2.0;
            double mz = (a.cz + b.cz) / 2.0;
            double r2 = d2 / 4;
            double r = Math.sqrt(r2);
            boolean gabriel = true;
            for (City c : citiesIn(mx - r, mz - r, mx + r, mz + r)) {
                if (c == a || c == b) {
                    continue;
                }
                double dx = c.cx - mx;
                double dz = c.cz - mz;
                if (dx * dx + dz * dz < r2) {
                    gabriel = false;
                    break;
                }
            }
            if (gabriel) {
                out.add(b);
            }
        }
        out.sort(Comparator.<City>comparingDouble(b -> dist2(a, b)).thenComparingLong(b -> b.id));
        return List.copyOf(out);
    }

    private static double dist2(City a, City b) {
        double dx = a.cx - b.cx;
        double dz = a.cz - b.cz;
        return dx * dx + dz * dz;
    }

    /** The exit of city a reserved for its link to b (null if a has no suitable exit). */
    public City.Exit exit(City a, City b) {
        return memo(exits, a.id, () -> computeExits(a)).get(b.id);
    }

    // ------------------------------------------------------------------ which cities are linked

    private final ConcurrentHashMap<Long, List<City>> links = new ConcurrentHashMap<>();

    /** The (at most {@link City#EXITS}) nearest Gabriel neighbors. */
    private List<City> nearest(City a) {
        List<City> nb = neighbors(a);
        return nb.subList(0, Math.min(City.EXITS, nb.size()));
    }

    /** Links of the first round: a and b are linked when each is among the other's EXITS nearest neighbors. */
    private List<City> mutual(City a) {
        List<City> out = new ArrayList<>();
        for (City b : nearest(a)) {
            if (nearest(b).contains(a)) {
                out.add(b);
            }
        }
        return out;
    }

    /** Second round: the nearest neighbor, not linked yet, that still has a free entrance (null if none). */
    private City spare(City a) {
        List<City> first = mutual(a);
        if (first.size() >= City.EXITS) {
            return null;
        }
        for (City b : neighbors(a)) {
            if (!first.contains(b) && mutual(b).size() < City.EXITS) {
                return b;
            }
        }
        return null;
    }

    /**
     * The cities linked to a by a highway: every city has at most {@link City#EXITS} entrances. Links are always
     * agreed by both cities: first each pair of mutual nearest neighbors, then each pair of cities with an entrance left
     * that pick each other. Depends only on the cities within two links, so every chunk gets the same answer.
     */
    public List<City> links(City a) {
        return memo(links, a.id, () -> {
            List<City> out = new ArrayList<>(mutual(a));
            City s = spare(a);
            if (s != null && spare(s) == a) {
                out.add(s);
            }
            return List.copyOf(out);
        });
    }

    /**
     * Entrance of a for each linked city: a border T crossing or a corner on the side of the city that faces it (east,
     * west, north or south, whichever dominates); among those, the one that lies farthest toward the other city (a city
     * to the north-east leaves by the north-east corner), and the seed picks among ties. A crossing serves one highway.
     */
    private Map<Long, City.Exit> computeExits(City a) {
        Map<Long, City.Exit> out = new HashMap<>();
        List<City.Exit> free = new ArrayList<>(a.exits());
        for (City b : links(a)) {
            double dx = b.cx - a.cx;
            double dz = b.cz - a.cz;
            double l = Math.sqrt(dx * dx + dz * dz);
            // the free exits whose direction faces the other city best (all the border crossings of one side of the
            // grid share a direction)
            double best = -2;
            for (City.Exit e : free) {
                double[] d = a.exitDirection(e);
                best = Math.max(best, (d[0] * dx + d[1] * dz) / l);
            }
            List<City.Exit> facing = new ArrayList<>();
            for (City.Exit e : free) {
                double[] d = a.exitDirection(e);
                if ((d[0] * dx + d[1] * dz) / l >= best - 1e-6) {
                    facing.add(e);
                }
            }
            if (facing.isEmpty()) {
                continue;
            }
            // the crossings of that side lying farthest toward the other city
            double far = -Double.MAX_VALUE;
            for (City.Exit e : facing) {
                far = Math.max(far, toward(a, e, dx / l, dz / l));
            }
            final double top = far;
            facing.removeIf(e -> toward(a, e, dx / l, dz / l) < top - 0.5);
            facing.sort(Comparator.comparingLong(e -> Hash.of(a.seed, b.id, e.i(), e.j())));
            City.Exit pick = facing.get(0);
            out.put(b.id, pick);
            free.removeIf(e -> e.i() == pick.i() && e.j() == pick.j());
        }
        return out;
    }

    /** How far exit e lies from the center of a along the unit direction (ux, uz). */
    private static double toward(City a, City.Exit e, double ux, double uz) {
        double[] p = a.exitWorld(e);
        return (p[0] - a.cx) * ux + (p[1] - a.cz) * uz;
    }

    // ------------------------------------------------------------------ highways

    private static boolean before(City a, City b) {
        return a.ci < b.ci || a.ci == b.ci && a.cj < b.cj;
    }

    private static long pairKey(City a, City b) {
        return ((long) (a.ci & 0xFFFF) << 48) | ((long) (a.cj & 0xFFFF) << 32) | ((long) (b.ci & 0xFFFF) << 16) | (b.cj & 0xFFFFL);
    }

    /** The geometric highway candidate between two neighbor cities (null when it cannot be built). */
    public Highway candidate(City a, City b) {
        if (!params.highways()) {
            return null;
        }
        City p = before(a, b) ? a : b;
        City q = p == a ? b : a;
        long key = pairKey(p, q);
        return memo(candidates, key, () -> Optional.ofNullable(buildCandidate(p, q, key))).orElse(null);
    }

    /** Why a candidate between two cities was rejected (debugging). */
    public String rejection(City a, City b) {
        City p = before(a, b) ? a : b;
        City q = p == a ? b : a;
        City.Exit ep = exit(p, q);
        City.Exit eq = exit(q, p);
        if (ep == null || eq == null) {
            return "no exit facing the other city";
        }
        Highway h = Highway.create(p, ep, q, eq, pairKey(p, q));
        if (h.length > params.highwayMaxLength()) {
            return "too long (" + h.length + ")";
        }
        if (h.length < 2 * Params.FLAT_END + 32) {
            return "too short (" + h.length + ")";
        }
        if (!Profile.feasible(h.length, p.level, q.level, Params.FLAT_END)) {
            return "levels too far apart for the length (" + p.level + " / " + q.level + ")";
        }
        if (h.minCurveRadius() < Params.MIN_CURVE_RADIUS) {
            return "curve too tight (" + Math.round(h.minCurveRadius()) + ")";
        }
        String c = clearOfCities(h);
        if (c != null) {
            return c;
        }
        Highway.Detail d = h.detail(terrain);
        if (!d.valid) {
            return d.reason;
        }
        Highway real = candidate(p, q);
        if (real != null && !isFinal(real)) {
            return "conflicts with a shorter highway";
        }
        return "kept";
    }

    private Highway buildCandidate(City p, City q, long key) {
        City.Exit ep = exit(p, q);
        City.Exit eq = exit(q, p);
        if (ep == null || eq == null) {
            return null;
        }
        Highway h = Highway.create(p, ep, q, eq, key);
        if (h.length > params.highwayMaxLength() || h.length < 2 * Params.FLAT_END + 32) {
            return null;
        }
        if (!Profile.feasible(h.length, p.level, q.level, Params.FLAT_END)) {
            return null;
        }
        if (h.minCurveRadius() < Params.MIN_CURVE_RADIUS) {
            return null;
        }
        if (clearOfCities(h) != null) {
            return null;
        }
        return h;
    }

    /** Whether two kept highways would collide (too close). */
    static boolean conflict(Highway e, Highway f) {
        return tooClose(e, f);
    }

    /** Null when the path keeps its distance from every city, else the reason. */
    private String clearOfCities(Highway h) {
        City.Hit hit = new City.Hit();
        int junction = (int) Math.ceil(Params.AVENUE_HW + Params.HIGHWAY_HW + 4);
        for (int k = 0; k <= h.length; k++) {
            double x = h.x(k);
            double z = h.z(k);
            City c = cityAt(x, z);
            if (c == null) {
                continue;
            }
            boolean own = c == h.a || c == h.b;
            if (c == h.a && k <= junction || c == h.b && k >= h.length - junction) {
                continue;
            }
            c.query(x, z, hit);
            double need = Params.HIGHWAY_HW + (own ? 3 : Params.FOREIGN_CITY_CLEARANCE);
            if (hit.inside() || hit.edge < need) {
                return "passes " + (own ? "too close to its own city" : "through/near city " + c.ci + "," + c.cj)
                        + " at point " + k;
            }
        }
        return null;
    }

    /** Whether a candidate is part of the final network (terrain valid and not in conflict with a shorter one). */
    public boolean isFinal(Highway h) {
        return memo(finals, h.key, () -> computeFinal(h));
    }

    private static boolean shorter(Highway f, Highway e) {
        return f.length < e.length || f.length == e.length && f.key < e.key;
    }

    private boolean computeFinal(Highway e) {
        if (!e.detail(terrain).valid) {
            return false;
        }
        for (Highway f : candidatesNear(e.minX, e.minZ, e.maxX, e.maxZ, Params.HIGHWAY_SEPARATION)) {
            if (f != e && shorter(f, e) && conflict(e, f) && f.detail(terrain).valid) {
                return false;
            }
        }
        return true;
    }

    /** Whether two highways come closer than {@link Params#HIGHWAY_SEPARATION} anywhere. */
    static boolean tooClose(Highway e, Highway f) {
        double sep = Params.HIGHWAY_SEPARATION;
        if (e.maxX + sep < f.minX || f.maxX + sep < e.minX || e.maxZ + sep < f.minZ || f.maxZ + sep < e.minZ) {
            return false;
        }
        double sep2 = sep * sep;
        // grid of f's points (cells of `sep`, only where e can reach): each point of e looks at 3 x 3 cells
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int k = 0; k <= f.length; k++) {
            double x = f.x(k);
            double z = f.z(k);
            if (x < e.minX - sep || x > e.maxX + sep || z < e.minZ - sep || z > e.maxZ + sep) {
                continue;
            }
            grid.computeIfAbsent(Cache.key((int) Math.floor(x / sep), (int) Math.floor(z / sep)), q -> new ArrayList<>()).add(k);
        }
        if (grid.isEmpty()) {
            return false;
        }
        for (int k = 0; k <= e.length; k++) {
            double x = e.x(k);
            double z = e.z(k);
            int gx = (int) Math.floor(x / sep);
            int gz = (int) Math.floor(z / sep);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    List<Integer> pts = grid.get(Cache.key(gx + dx, gz + dz));
                    if (pts == null) {
                        continue;
                    }
                    for (int p : pts) {
                        double ddx = f.x(p) - x;
                        double ddz = f.z(p) - z;
                        if (ddx * ddx + ddz * ddz < sep2) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Geometric candidates whose bounding box comes within {@code pad} of the rectangle. A highway lies within
     * ~700 blocks of the straight line between its city centers (exit offset + Bezier handles), and links are at most
     * {@link Params#MAX_LINK} long, which bounds the search.
     */
    private List<Highway> candidatesNear(double x0, double z0, double x1, double z1, double pad) {
        double bulge = 750;
        double far = Params.MAX_LINK + bulge + pad;
        List<Highway> out = new ArrayList<>();
        for (City a : citiesIn(x0 - far, z0 - far, x1 + far, z1 + far)) {
            for (City b : neighbors(a)) {
                if (!before(a, b)) {
                    continue;
                }
                double cx0 = Math.min(a.cx, b.cx) - bulge;
                double cx1 = Math.max(a.cx, b.cx) + bulge;
                double cz0 = Math.min(a.cz, b.cz) - bulge;
                double cz1 = Math.max(a.cz, b.cz) + bulge;
                if (cx1 < x0 - pad || cx0 > x1 + pad || cz1 < z0 - pad || cz0 > z1 + pad) {
                    continue;
                }
                Highway h = candidate(a, b);
                if (h != null && h.maxX >= x0 - pad && h.minX <= x1 + pad && h.maxZ >= z0 - pad && h.minZ <= z1 + pad) {
                    out.add(h);
                }
            }
        }
        return out;
    }

    /** Final highways of a city. */
    public List<Highway> highways(City a) {
        List<Highway> out = new ArrayList<>();
        for (City b : neighbors(a)) {
            Highway h = candidate(a, b);
            if (h != null && isFinal(h)) {
                out.add(h);
            }
        }
        return out;
    }

    /** Final highways whose bounding box comes within {@code pad} of the rectangle. */
    public List<Highway> highwaysIn(double x0, double z0, double x1, double z1, double pad) {
        if (!params.highways()) {
            return List.of();
        }
        int t0x = Math.floorDiv((int) Math.floor(x0 - pad), TILE);
        int t1x = Math.floorDiv((int) Math.floor(x1 + pad), TILE);
        int t0z = Math.floorDiv((int) Math.floor(z0 - pad), TILE);
        int t1z = Math.floorDiv((int) Math.floor(z1 + pad), TILE);
        Map<Highway, Boolean> seen = new IdentityHashMap<>();
        List<Highway> out = new ArrayList<>();
        for (int tz = t0z; tz <= t1z; tz++) {
            for (int tx = t0x; tx <= t1x; tx++) {
                for (Highway h : tile(tx, tz)) {
                    if (seen.put(h, Boolean.TRUE) == null && h.maxX >= x0 - pad && h.minX <= x1 + pad
                            && h.maxZ >= z0 - pad && h.minZ <= z1 + pad) {
                        out.add(h);
                    }
                }
            }
        }
        return out;
    }

    private List<Highway> tile(int tx, int tz) {
        return tiles.get(Cache.key(tx, tz), k -> {
            double x0 = (double) tx * TILE;
            double z0 = (double) tz * TILE;
            List<Highway> out = new ArrayList<>();
            for (Highway h : candidatesNear(x0, z0, x0 + TILE, z0 + TILE, HIGHWAY_REACH + 2)) {
                if (isFinal(h)) {
                    out.add(h);
                }
            }
            return List.copyOf(out);
        });
    }

    // ------------------------------------------------------------------ chunk plans

    public ChunkPlan chunk(int cx, int cz) {
        return chunks.get(Cache.key(cx, cz), k -> computeChunk(cx, cz));
    }

    private static final class HighwayRef {
        final Highway h;
        final Highway.Detail d;
        final int[] segs;

        HighwayRef(Highway h, Highway.Detail d, int[] segs) {
            this.h = h;
            this.d = d;
            this.segs = segs;
        }
    }

    private ChunkPlan computeChunk(int cx, int cz) {
        ChunkPlan cp = new ChunkPlan(cx, cz);
        int bx = cx << 4;
        int bz = cz << 4;
        City city = cityNear(bx + 8, bz + 8, 12);
        cp.city = city;
        List<HighwayRef> refs = new ArrayList<>();
        for (Highway h : highwaysIn(bx, bz, bx + 16, bz + 16, HIGHWAY_REACH + 2)) {
            int[] segs = h.segmentsNear(cx, cz, 2);
            if (segs.length > 0) {
                refs.add(new HighwayRef(h, h.detail(terrain), segs));
            }
        }
        if (city == null && refs.isEmpty()) {
            return cp;
        }
        City.Hit hit = new City.Hit();
        Highway.Proj pr = new Highway.Proj();
        int[] los = new int[refs.size() + 1];
        int[] his = new int[refs.size() + 1];
        double[] es = new double[refs.size() + 1];
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = ChunkPlan.idx(lx, lz);
                double x = bx + lx + 0.5;
                double z = bz + lz + 0.5;
                boolean cRoad = false;
                boolean cLot = false;
                boolean cLane = false;
                boolean cMark = false;
                double cEdge = Double.POSITIVE_INFINITY;
                if (city != null) {
                    city.query(x, z, hit);
                    cRoad = hit.road;
                    cLot = hit.lot;
                    cLane = hit.lane;
                    cMark = hit.mark;
                    cEdge = hit.edge;
                }
                HighwayRef band = null;
                double bandAbs = Double.POSITIVE_INFINITY;
                double bandLane = Params.HIGHWAY_LANE_HW;
                double bandS = 0;
                double bandD = 0;
                HighwayRef wall = null;
                double wallS = 0;
                int n = 0;
                for (HighwayRef r : refs) {
                    if (!r.h.project(x, z, r.segs, pr)) {
                        continue;
                    }
                    double ad = Math.abs(pr.d);
                    if (ad > HIGHWAY_REACH) {
                        continue;
                    }
                    int k = Math.max(0, Math.min(r.h.length, pr.index()));
                    double edge = r.h.edgeWidth(k);
                    boolean side = pr.d > 0;
                    double lane = r.h.laneHalf(k, side);
                    // lanes, then the smooth stone shoulder, then the edge strip with the wall
                    double bandLimit = lane + Params.HIGHWAY_SHOULDER + edge;
                    if (ad <= bandLimit) {
                        if (ad < bandAbs) {
                            bandAbs = ad;
                            band = r;
                            bandS = pr.s;
                            bandD = pr.d;
                            bandLane = lane;
                        }
                        continue;
                    }
                    byte st = r.d.type[k];
                    if (st == Profile.TUNNEL && ad <= bandLimit + edge) {
                        wall = r;
                        wallS = pr.s;
                    } else if (st == Profile.GROUND) {
                        double e = ad - bandLimit;
                        int w = Math.floorDiv(r.d.h2(k), 2);
                        los[n] = w - 1 - (int) Math.floor(e);
                        his[n] = w + 1 + (int) Math.floor(e);
                        es[n] = e;
                        n++;
                    }
                }

                if (cRoad) {
                    cp.kind[i] = ChunkPlan.ROAD;
                    cp.h2[i] = (short) (2 * city.level);
                    cp.struct[i] = Profile.GROUND;
                    cp.flags[i] = ChunkPlan.F_CITY;
                    byte zone = cLane ? (cMark ? ChunkPlan.MARK : hit.crosswalk ? ChunkPlan.CROSSWALK : ChunkPlan.LANE) : ChunkPlan.SIDEWALK;
                    if (band != null && bandAbs <= bandLane && zone == ChunkPlan.SIDEWALK) {
                        // where a highway joins the street, the sidewalk opens into lanes (markings stay)
                        zone = ChunkPlan.LANE;
                    }
                    cp.zone[i] = zone;
                    // the street lights: the post and the vine behind it on the sidewalk, the arm over the lanes
                    int[] lamp = StreetLights.lampParts(hit);
                    if (lamp != null && (hit.lamp <= 0) == (zone == ChunkPlan.SIDEWALK)) {
                        cp.flags[i] |= ChunkPlan.F_LAMP;
                        if (lamp[1] != BlockCode.KEEP && !StreetLights.lampVine(lamp[1])) {
                            // a block standing on the street: the post, or the lamp fallen on the lane
                            cp.flags[i] |= ChunkPlan.F_POLE;
                        }
                    }
                } else if (cLot) {
                    // lots win over the highway band (both are 33 wide, so this only trims the curve's outside)
                    cp.kind[i] = ChunkPlan.LOT;
                    cp.h2[i] = (short) (2 * city.level);
                    cp.lot[i] = city.lotAt(hit.bi, hit.bj, bx + lx, bz + lz);
                } else if (band != null) {
                    int k = Math.max(0, Math.min(band.h.length, (int) Math.round(bandS)));
                    byte st = band.d.type[k];
                    cp.kind[i] = ChunkPlan.ROAD;
                    cp.h2[i] = (short) band.d.h2(k);
                    cp.struct[i] = st;
                    cp.highway[i] = band.h;
                    byte flags = ChunkPlan.F_HIGHWAY;
                    byte zone;
                    if (bandAbs <= Params.MARK_HW && Math.floorMod(k, Params.DASH + Params.DASH_GAP) < Params.DASH) {
                        zone = ChunkPlan.MARK_HIGHWAY;
                    } else if (bandAbs <= bandLane) {
                        zone = ChunkPlan.LANE;
                    } else if (bandAbs <= bandLane + Params.HIGHWAY_SHOULDER) {
                        zone = ChunkPlan.SHOULDER;
                    } else {
                        // edge with a wall on top, open where the highway meets the city street
                        boolean junction = band.h.junction(k);
                        zone = junction ? ChunkPlan.SHOULDER : ChunkPlan.PARAPET;
                    }
                    if (st == Profile.BRIDGE && band.d.pillar[k] && bandAbs >= 2.5 && bandAbs <= 5.5) {
                        flags |= ChunkPlan.F_PILLAR;
                    }
                    if (st == Profile.TUNNEL && bandAbs <= Params.MARK_HW
                            && Math.floorMod(k, Params.LIGHT_EVERY) == Params.LIGHT_EVERY / 2) {
                        flags |= ChunkPlan.F_LIGHT;
                    }
                    // the wreck of an abandoned highway: holes through the bridges
                    if (st == Profile.BRIDGE && zone != ChunkPlan.PARAPET && Wreck.inHole(band.d.holes, k, bandD)) {
                        flags |= ChunkPlan.F_HOLE;
                    }
                    cp.zone[i] = zone;
                    cp.flags[i] = flags;
                } else if (wall != null) {
                    int k = Math.max(0, Math.min(wall.h.length, (int) Math.round(wallS)));
                    cp.kind[i] = ChunkPlan.TUNNEL_WALL;
                    cp.h2[i] = (short) wall.d.h2(k);
                    cp.struct[i] = Profile.TUNNEL;
                    cp.highway[i] = wall.h;
                } else {
                    if (city != null && cEdge <= Params.CITY_SKIRT) {
                        los[n] = city.level - 1 - (int) Math.floor(cEdge);
                        his[n] = city.level + 1 + (int) Math.floor(cEdge);
                        es[n] = cEdge;
                        n++;
                    }
                    if (n > 0) {
                        int lo = Integer.MIN_VALUE;
                        int hi = Integer.MAX_VALUE;
                        int nearest = 0;
                        for (int q = 0; q < n; q++) {
                            lo = Math.max(lo, los[q]);
                            hi = Math.min(hi, his[q]);
                            if (es[q] < es[nearest]) {
                                nearest = q;
                            }
                        }
                        if (lo > hi) {
                            lo = los[nearest];
                            hi = his[nearest];
                        }
                        cp.kind[i] = ChunkPlan.SKIRT;
                        cp.lo[i] = (short) lo;
                        cp.hi[i] = (short) hi;
                    }
                }
                if (cp.kind[i] != ChunkPlan.NONE) {
                    cp.any = true;
                }
            }
        }
        return cp;
    }

    /** All cities with a center in the rectangle and the final highways touching it (for audits and renders). */
    public List<Highway> highwaysOf(List<City> cs) {
        Map<Highway, Boolean> seen = new IdentityHashMap<>();
        List<Highway> out = new ArrayList<>();
        for (City c : cs) {
            for (Highway h : highways(c)) {
                if (seen.put(h, Boolean.TRUE) == null) {
                    out.add(h);
                }
            }
        }
        return out;
    }
}
