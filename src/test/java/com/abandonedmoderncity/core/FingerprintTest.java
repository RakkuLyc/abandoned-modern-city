package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Fingerprint of everything the planner hands to the game layer, on the synthetic terrains with fixed seeds: one
 * SHA-256 digest per part.
 * <ul>
 *   <li>{@code layout}: every city of the 7 terrains x 3 seeds (center, level, orientation, exits) and the lots of each
 *   of its blocks (rectangle, kind, seed, structure and rotation);</li>
 *   <li>{@code highways}: every highway of those plans (exits, points, height profile, bridges and tunnels, holes);</li>
 *   <li>{@code village}: where village creatures are taken out around each city;</li>
 *   <li>{@code chunks}: the chunk plans (kind, zone, flags, structure, heights, lot, highway of every column) of the
 *   chunks around two cities and along one highway per terrain;</li>
 *   <li>{@code lots}: in those chunks, the block code of every lot column at every height, with the loot access and the
 *   banner design of the structure blocks;</li>
 *   <li>{@code roads}: in those chunks, the worn road surfaces, plants, bridge stones, walls and vines;</li>
 *   <li>{@code lamps}: in those chunks, the parts of every street light;</li>
 *   <li>{@code crowns}: in those chunks, the leaves completing the crowns cut by the edge of a structure;</li>
 *   <li>{@code structures}: every structure in its 4 rotations, cell by cell, with its openings, loot access, banners
 *   and overhanging leaves;</li>
 *   <li>{@code weathering}: what the overgrowth added to each structure;</li>
 *   <li>{@code benches}: the benches of every park of the flat terrain.</li>
 * </ul>
 * A structure block is hashed as its block state string and rotation, not as its index into
 * {@link Structures#STATES}, so the digests follow what ends up in the world.
 * <p>
 * A change that only reshapes the code (imports, moved methods, comments) keeps every digest. A digest that moves means
 * the generated world changed. The expected values come from commit 4dd94ae, except layout, structures and weathering,
 * which hash the structure names: they changed when the structures got English names (the blocks did not); layout
 * changed again when the special structures became ~1 city in 10 ({@code CityRings.SPECIAL_CHANCE}); structures changed
 * again with the loot theme building_large (buildings with 100+ containers; the blocks did not change); lots and
 * structures changed when the height rule of the hard-to-get-to containers was removed (the loot access); structures
 * changed again with the loot theme police_station (the blocks did not change); lots changed when 70% of the barrels
 * of the buildings were taken out and the others became chests, copy by copy ({@link Barrels}); structures changed
 * again when the loot theme building_large was taken out (the blocks did not change); lots and structures changed
 * when the containers underground, on the terrace and in the attics of the houses became hard to get to (the loot
 * access; the blocks did not change); structures changed again when the buildings were classified by height (the
 * type and the category of 4 of them: the blocks did not change), and again with the margin of 3 blocks on the
 * limits (the type of 3 of them); lots and structures changed when the 556 chests saved in the templates of the 10
 * buildings were taken out (they become air; nothing else changes, the barrels included: BuildingChestsTest); lots
 * changed again when each copy of a building got its drawn chests (ChestDraw: air turned into chests on its spots,
 * nothing else; structures, which hashes the plain code, did not change), and again when the drawn chests
 * underground or on the terrace became hard to get to (the loot access; the blocks did not change), and again when
 * the barrels that hold something stayed barrels instead of chests (26: 25 in the tower, 1 in the crash building).
 * layout, chunks, lots and crowns changed when each city drew 1 or 2 gas stations (City.gasStations; only the
 * cities that had 2 and drew 1 changed); lots, structures and weathering when the 3 medium workshops got 3 chests
 * in their .nbt (2 on the ground floor, 1 on the terrace; the overgrowth moved 4 of its blocks); layout, chunks,
 * lots and crowns when every city got exactly 2 gas stations, never 2 large ones, in reserved rows (Ring.Forced;
 * every city changed); lots when each copy of a building got its zombie spawners (SpawnerDraw: air turned into
 * spawners on the spots left by the chests, nothing else; with zombieSpawners = false lots stays ecf9d723...); lots
 * and structures when the 146 stairs of the stairwell of the apocalyptic building were turned the right way in its
 * .nbt (north and south swapped; nothing else); lots when the spawners of a floor were counted by its area under a
 * ceiling (one per 500 cells, SpawnerDraw.AREA) instead of the height class of the building (only spawners moved:
 * with zombieSpawners = false lots stays 5aab43a5... before and after).
 * Change them
 * only in a commit that is meant to change the world, and say so in it.
 */
class FingerprintTest {
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();

    static {
        EXPECTED.put("layout", "4b410c7efdcdd8eb78616f2dfc8670be7d8fdb9aa5da9fd6c46e3774467c4cd9");
        EXPECTED.put("highways", "480e692cad232384cc5a0d486154e33b13a8ebc6d381e0dfbffa8cd18434ddf9");
        EXPECTED.put("village", "6d77e372e3111d53b3995b84b46308716b65b8107b051b8f4411eca72a61f71c");
        EXPECTED.put("chunks", "78a5d87786413d16206aad039147b442b914166b72cc5578ed3254662a03d875");
        EXPECTED.put("lots", "4736ee67ff47e36bc464122b24c14d0b216654d9751302914b8ea70b532a77cc");
        EXPECTED.put("roads", "bcf16888eff09ec35d77b4c97d496a5d006061d045c8e02abd16c5a56418aef6");
        EXPECTED.put("lamps", "85591f5e99c109803aa1dfb294e112106e69005b6f9706d41bb2abb565addb23");
        EXPECTED.put("crowns", "4a5615a2e28181f6844deeb7e60c98e1c16a8ccd0d63d069395ab5aca3e77b8a");
        EXPECTED.put("structures", "cbe4e5de0996ef38d3c0199f5963f32598e599a1e79501b5a86bcf0ac92f8f17");
        EXPECTED.put("weathering", "f63f167de64de34e2a9b6d570ed53e887a97c24bd40d13a56d9532694e85b0ce");
        EXPECTED.put("benches", "e8ee1c72349ec4768b6c4880b5810913a01abed52a0c8790553a5f97fb15e7e8");
    }

    private final Map<String, Digest> digests = new LinkedHashMap<>();
    private final Map<String, Long> counts = new TreeMap<>();
    /** Lots met in the chunk plans (by identity), to count them once. */
    private final Set<Lot> seenLots = Collections.newSetFromMap(new IdentityHashMap<>());

    @Test
    void plannerOutputIsUnchanged() {
        for (String k : EXPECTED.keySet()) {
            digests.put(k, new Digest());
        }
        long t0 = System.nanoTime();
        layoutAndHighways();
        chunks();
        structures();
        weathering();
        benches();
        StringBuilder out = new StringBuilder();
        Map<String, String> actual = new LinkedHashMap<>();
        for (var e : digests.entrySet()) {
            actual.put(e.getKey(), e.getValue().hex());
            out.append(String.format("  %-10s %s%n", e.getKey(), actual.get(e.getKey())));
        }
        System.out.printf("[FingerprintTest] %.1f s%n%s  coverage %s%n", (System.nanoTime() - t0) / 1e9, out, counts);
        // it must not pass on an empty plan
        for (String k : new String[]{"cities", "highways", "chunks", "road_columns", "bridge_columns", "tunnel_columns",
                "lot_columns", "structure_cells_in_lots", "wild_lots", "park_lots", "lamp_columns", "crown_leaves",
                "structures", "structure_cells", "benches"}) {
            assertTrue(counts.getOrDefault(k, 0L) > 0, "nothing counted for " + k + ": " + counts);
        }
        List<String> moved = new ArrayList<>();
        for (String k : EXPECTED.keySet()) {
            if (!EXPECTED.get(k).equals(actual.get(k))) {
                moved.add(k);
            }
        }
        assertEquals(List.of(), moved, "digests that changed (the generated world is not the same):\n" + out);
    }

    // ------------------------------------------------------------------ cities, lots and highways

    private void layoutAndHighways() {
        Digest layout = digests.get("layout");
        Digest roads = digests.get("highways");
        Digest village = digests.get("village");
        City.Hit hit = new City.Hit();
        for (TestTerrain.Kind kind : TestTerrain.Kind.values()) {
            for (long seed : WorldPlanTest.SEEDS) {
                WorldPlan p = WorldPlanTest.plan(seed, kind);
                layout.s(kind.name());
                layout.l(seed);
                List<City> cities = WorldPlanTest.allCities(p);
                for (City c : cities) {
                    count("cities", 1);
                    city(layout, c);
                    // village creatures: sampled every 8 blocks over the city and its margin
                    int r = c.radius + (int) City.VILLAGE_MARGIN + 48;
                    for (int z = c.cz - r; z <= c.cz + r; z += 8) {
                        for (int x = c.cx - r; x <= c.cx + r; x += 8) {
                            village.b(c.within(x + 0.5, z + 0.5, City.VILLAGE_MARGIN, hit));
                        }
                    }
                }
                List<Highway> hs = new ArrayList<>(p.highwaysOf(cities));
                hs.sort((a, b) -> Long.compare(a.key, b.key));
                roads.s(kind.name());
                roads.l(seed);
                for (Highway h : hs) {
                    count("highways", 1);
                    highway(roads, h, p.terrain);
                }
            }
        }
    }

    private void city(Digest d, City c) {
        d.i(c.ci);
        d.i(c.cj);
        d.l(c.id);
        d.l(c.seed);
        d.i(c.cx);
        d.i(c.cz);
        d.i(c.radius);
        d.i(c.level);
        d.d(c.influence);
        d.b(c.longX);
        d.i(c.SX);
        d.i(c.SZ);
        d.i(c.yardCode());
        for (City.Exit e : c.exits()) {
            exit(d, e);
        }
        int n = c.gridHalf();
        d.i(n);
        for (int bj = -n; bj <= n; bj++) {
            for (int bi = -n; bi <= n; bi++) {
                d.b(c.isCityBlock(bi, bj));
                List<Lot> lots = c.lots(bi, bj);
                d.i(lots.size());
                for (Lot l : lots) {
                    lot(d, l);
                }
            }
        }
    }

    private void lot(Digest d, Lot l) {
        d.i(l.x0);
        d.i(l.z0);
        d.i(l.x1);
        d.i(l.z1);
        d.s(l.type.name());
        d.l(l.seed);
        d.s(l.building == null ? null : l.building.name());
        d.i(l.rot);
        if (l.park != null) {
            d.i(l.park.variant);
            d.b(l.park.chest);
        }
        d.b(l.wild != null);
        d.i(l.depthBelowBase());
        d.i(l.heightAboveBase());
    }

    private static void exit(Digest d, City.Exit e) {
        d.i(e.i());
        d.i(e.j());
        d.i(e.dir());
        // the exits had a "middle of a side" flag, always false, removed with the old city shapes: kept in the digest
        d.b(false);
    }

    private static void highway(Digest d, Highway h, Terrain terrain) {
        d.l(h.key);
        d.l(h.a.id);
        d.l(h.b.id);
        exit(d, h.exitA);
        exit(d, h.exitB);
        d.i(h.length);
        d.d(h.step);
        for (int k = 0; k <= h.length; k++) {
            d.d(h.px[k]);
            d.d(h.pz[k]);
        }
        Highway.Detail det = h.detail(terrain);
        d.b(det.valid);
        d.s(det.reason);
        for (int k = 0; k < det.he.length; k++) {
            d.i(det.he[k]);
            d.i(det.type[k]);
            d.b(det.pillar[k]);
        }
        d.i(det.holes.length);
        for (Wreck.Site s : det.holes) {
            d.i(s.k());
            d.d(s.across());
            d.d(s.halfLen());
            d.d(s.halfWid());
            d.l(s.seed());
        }
    }

    // ------------------------------------------------------------------ chunk plans

    /** The chunks around the first two cities and along the first highway of each terrain (first seed). */
    private void chunks() {
        for (TestTerrain.Kind kind : TestTerrain.Kind.values()) {
            WorldPlan p = WorldPlanTest.plan(WorldPlanTest.SEEDS[0], kind);
            List<City> cities = WorldPlanTest.allCities(p);
            TreeSet<Long> keys = new TreeSet<>();
            for (int n = 0; n < Math.min(2, cities.size()); n++) {
                City c = cities.get(n);
                int r = (int) Math.ceil(c.influence / 16) + 1;
                int ccx = Math.floorDiv(c.cx, 16);
                int ccz = Math.floorDiv(c.cz, 16);
                for (int cz = ccz - r; cz <= ccz + r; cz++) {
                    for (int cx = ccx - r; cx <= ccx + r; cx++) {
                        keys.add(key(cx, cz));
                    }
                }
            }
            List<Highway> hs = new ArrayList<>(p.highwaysOf(cities));
            hs.sort((a, b) -> Long.compare(a.key, b.key));
            if (!hs.isEmpty()) {
                Highway h = hs.get(0);
                for (int k = 0; k <= h.length; k += 8) {
                    int pcx = Math.floorDiv((int) Math.floor(h.px[k]), 16);
                    int pcz = Math.floorDiv((int) Math.floor(h.pz[k]), 16);
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            keys.add(key(pcx + dx, pcz + dz));
                        }
                    }
                }
            }
            for (String k : new String[]{"chunks", "lots", "roads", "lamps", "crowns"}) {
                digests.get(k).s(kind.name());
            }
            for (long k : keys) {
                chunk(p, (int) (k >> 32), (int) k);
            }
        }
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    private void chunk(WorldPlan p, int cx, int cz) {
        Digest plan = digests.get("chunks");
        Digest lots = digests.get("lots");
        Digest roads = digests.get("roads");
        Digest lamps = digests.get("lamps");
        Digest crowns = digests.get("crowns");
        ChunkPlan cp = p.chunk(cx, cz);
        count("chunks", 1);
        plan.i(cx);
        plan.i(cz);
        plan.b(cp.any);
        plan.l(cp.city == null ? -1 : cp.city.id);
        for (int i = 0; i < 256; i++) {
            int x = cx * 16 + (i & 15);
            int z = cz * 16 + (i >> 4);
            plan.i(cp.kind[i]);
            plan.i(cp.zone[i]);
            plan.i(cp.flags[i]);
            plan.i(cp.struct[i]);
            plan.i(cp.h2[i]);
            plan.i(cp.lo[i]);
            plan.i(cp.hi[i]);
            plan.l(cp.highway[i] == null ? 0 : cp.highway[i].key);
            Lot lot = cp.lot[i];
            plan.i(lot == null ? Integer.MIN_VALUE : lot.x0);
            plan.i(lot == null ? Integer.MIN_VALUE : lot.z0);
            // vines under the edge of a bridge are tried on every column
            roads.b(RoadDecay.bridgeVine(p.seed, x, z));
            if (cp.kind[i] == ChunkPlan.ROAD) {
                road(p, cp, i, x, z, roads, lamps);
            } else if (cp.kind[i] == ChunkPlan.LOT) {
                lotColumn(cp, i, x, z, lots);
            }
        }
        Map<Long, Integer> leaves = new TreeMap<>(Crowns.forChunk(p, cx, cz));
        crowns.i(leaves.size());
        for (var e : leaves.entrySet()) {
            count("crown_leaves", 1);
            crowns.l(e.getKey());
            code(crowns, e.getValue());
        }
    }

    private void road(WorldPlan p, ChunkPlan cp, int i, int x, int z, Digest roads, Digest lamps) {
        count("road_columns", 1);
        byte zone = cp.zone[i];
        byte struct = cp.struct[i];
        boolean slab = ChunkPlan.isSlab(cp.h2[i]);
        int decay = RoadDecay.surface(p.seed, x, z, zone, struct, slab);
        code(roads, decay);
        code(roads, RoadDecay.texture(p.seed, x, z, zone, slab));
        if (decay != BlockCode.KEEP) {
            for (int dy = 1; dy <= 2; dy++) {
                code(roads, RoadDecay.plant(p.seed, x, z, dy, decay));
            }
        }
        roads.b(RoadDecay.wallGap(p.seed, x, z));
        roads.b(RoadDecay.mossyWall(p.seed, x, z));
        if (struct == Profile.BRIDGE) {
            count("bridge_columns", 1);
            int ys = ChunkPlan.surfaceY(cp.h2[i]);
            for (int y = ys - 12; y < ys; y++) {
                roads.i(RoadDecay.bridgeStone(p.seed, x, y, z));
            }
        } else if (struct == Profile.TUNNEL) {
            count("tunnel_columns", 1);
        }
        if ((cp.flags[i] & ChunkPlan.F_LAMP) != 0 && cp.city != null) {
            count("lamp_columns", 1);
            int[] parts = cp.city.lampParts(x + 0.5, z + 0.5);
            lamps.i(x);
            lamps.i(z);
            if (parts == null) {
                lamps.i(-1);
            } else {
                lamps.i(parts.length);
                for (int c : parts) {
                    code(lamps, c);
                }
            }
        }
    }

    private void lotColumn(ChunkPlan cp, int i, int x, int z, Digest d) {
        count("lot_columns", 1);
        int base = cp.h2[i] / 2 - 1;
        Lot lot = cp.lot[i];
        if (lot == null) {
            d.i(cp.city == null ? Integer.MIN_VALUE : cp.city.yardCode());
            return;
        }
        if (seenLots.add(lot)) {
            count(lot.wild != null ? "wild_lots" : lot.park != null ? "park_lots" : "structure_lots", 1);
        }
        int depth = lot.depthBelowBase();
        int top = lot.heightAboveBase();
        d.i(depth);
        d.i(top);
        for (int y = base - depth; y <= base + top; y++) {
            int c = lot.code(x, y, z, base);
            code(d, c);
            if (lot.building != null && c >= BlockCode.STRUCT) {
                count("structure_cells_in_lots", 1);
                d.b(lot.hardAt(x, y, z, base));
                banner(d, lot.bannerAt(x, y, z, base));
            }
        }
    }

    // ------------------------------------------------------------------ structures

    private void structures() {
        Digest d = digests.get("structures");
        d.i(Structures.MIN_DEPTH);
        d.i(Structures.MIN_WIDTH);
        for (var e : new TreeMap<>(Structures.LIMITS).entrySet()) {
            d.s(e.getKey());
            d.i(e.getValue());
        }
        for (List<Structures.Def> model : Structures.MODELS) {
            d.i(model.size());
            for (Structures.Def def : model) {
                d.s(def.name());
            }
        }
        for (var e : new TreeMap<>(Structures.LETTERS_REMOVED).entrySet()) {
            d.s(e.getKey());
            d.i(e.getValue());
        }
        for (Structures.Def def : Structures.ALL) {
            count("structures", 1);
            d.s(def.type());
            d.s(def.name());
            d.s(def.group());
            d.s(def.model());
            d.s(def.category());
            d.s(def.lootTheme());
            d.b(def.onePerCity());
            d.i(def.ox());
            d.i(def.oy());
            d.i(def.oz());
            d.i(def.w());
            d.i(def.h());
            d.i(def.d());
            d.i(def.ground());
            d.i(def.turn());
            d.i(def.access().containers());
            d.i(def.access().listed());
            d.i(def.access().closed());
            d.i(def.access().hard().size());
            for (int rot = 0; rot < 4; rot++) {
                int sx = def.sizeX(rot);
                int sz = def.sizeZ(rot);
                d.i(sx);
                d.i(sz);
                for (int ry = -def.ground(); ry < def.above(); ry++) {
                    for (int rz = 0; rz < sz; rz++) {
                        for (int rx = 0; rx < sx; rx++) {
                            int c = def.code(rx, ry, rz, rot);
                            code(d, c);
                            count("structure_cells", 1);
                            if (c >= BlockCode.STRUCT) {
                                d.b(def.hard(rx, ry, rz, rot));
                                banner(d, def.banner(rx, ry, rz, rot));
                            }
                        }
                    }
                }
                for (int rz = 0; rz < sz; rz++) {
                    for (int rx = 0; rx < sx; rx++) {
                        d.b(def.opening(rx, rz, rot));
                    }
                }
                List<int[]> over = def.overhangs(rot);
                d.i(over.size());
                for (int[] o : over) {
                    d.i(o[0]);
                    d.i(o[1]);
                    d.i(o[2]);
                    code(d, o[3]);
                }
            }
        }
    }

    private void weathering() {
        Digest d = digests.get("weathering");
        for (var e : new TreeMap<>(Structures.WEATHERING).entrySet()) {
            Weathering.Report r = e.getValue();
            d.s(e.getKey());
            d.i(r.cracked());
            d.i(r.floors());
            d.i(r.carpets());
            d.i(r.plants());
            d.i(r.vines());
            d.i(r.mossy());
        }
    }

    // ------------------------------------------------------------------ park benches

    private void benches() {
        Digest d = digests.get("benches");
        WorldPlan p = WorldPlanTest.plan(WorldPlanTest.SEEDS[0], TestTerrain.Kind.FLAT);
        for (City c : WorldPlanTest.allCities(p)) {
            int n = c.gridHalf();
            for (int bj = -n; bj <= n; bj++) {
                for (int bi = -n; bi <= n; bi++) {
                    for (Lot l : c.lots(bi, bj)) {
                        if (l.park == null) {
                            continue;
                        }
                        d.i(l.x0);
                        d.i(l.z0);
                        d.i(l.park.benchCount());
                        for (var e : new TreeMap<>(l.park.benches).entrySet()) {
                            count("benches", 1);
                            d.l(e.getKey());
                            d.i(e.getValue());
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private void count(String k, long n) {
        counts.merge(k, n, Long::sum);
    }

    private static final Map<Integer, byte[]> STATE_BYTES = new HashMap<>();

    /** A block code: structure blocks as their state string and rotation, fountain blocks as their state string. */
    private static void code(Digest d, int code) {
        if (code >= BlockCode.STRUCT) {
            int k = code - BlockCode.STRUCT;
            d.i(-2);
            d.raw(STATE_BYTES.computeIfAbsent(k >> 2,
                    s -> Structures.STATES.get(s).getBytes(StandardCharsets.UTF_8)));
            d.i(k & 3);
        } else if (code >= BlockCode.TEMPLATE) {
            d.i(-3);
            d.s(Template.WATERFALL.palette.get(code - BlockCode.TEMPLATE));
        } else {
            d.i(code);
        }
    }

    private static void banner(Digest d, List<String[]> layers) {
        if (layers == null) {
            d.i(-1);
            return;
        }
        d.i(layers.size());
        for (String[] layer : layers) {
            d.i(layer.length);
            for (String s : layer) {
                d.s(s);
            }
        }
    }

    /** SHA-256 over a stream of ints, longs, doubles and strings, through a buffer. */
    static final class Digest {
        private final MessageDigest md;
        private final byte[] buf = new byte[1 << 16];
        private int n;

        Digest() {
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        void i(int v) {
            if (n + 4 > buf.length) {
                flush();
            }
            buf[n++] = (byte) (v >>> 24);
            buf[n++] = (byte) (v >>> 16);
            buf[n++] = (byte) (v >>> 8);
            buf[n++] = (byte) v;
        }

        void l(long v) {
            i((int) (v >>> 32));
            i((int) v);
        }

        void d(double v) {
            l(Double.doubleToLongBits(v));
        }

        void b(boolean v) {
            i(v ? 1 : 0);
        }

        void s(String v) {
            if (v == null) {
                i(-1);
                return;
            }
            raw(v.getBytes(StandardCharsets.UTF_8));
        }

        /** A length-prefixed byte string. */
        void raw(byte[] a) {
            i(a.length);
            if (n + a.length > buf.length) {
                flush();
            }
            if (a.length > buf.length) {
                md.update(a);
                return;
            }
            System.arraycopy(a, 0, buf, n, a.length);
            n += a.length;
        }

        private void flush() {
            md.update(buf, 0, n);
            n = 0;
        }

        String hex() {
            flush();
            return HexFormat.of().formatHex(md.digest());
        }
    }
}
