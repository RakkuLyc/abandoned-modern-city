package com.abandonedmoderncity.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How {@link Structures#of} prepares a template: the floor of the ground layer filled out to the edge of the box, the
 * real (decaying) leaves of its trees, the crowns cut by the edge completed outside the box, the cobwebs left hanging
 * on nothing taken out, and the openings of the ground layer over dug-out space (garage ramps, stairwells). These
 * functions keep no state of their own: the lists of the structures and the order they load in stay in
 * {@link Structures}.
 */
final class StructureLoader {
    private StructureLoader() {
    }

    /** Blocks that are not a floor (plants, decorations, partial blocks): never used to fill a floor. */
    private static final String[] NOT_FLOOR = {"grass", "fern", "flower", "vine", "carpet", "leaves", "sapling", "tulip",
            "poppy", "dandelion", "bush", "torch", "lantern", "button", "rail", "slab", "stairs", "wall", "fence", "pane",
            "bars", "door", "banner", "sign", "pot", "candle", "chain", "lever", "snow", "lily", "roots", "dripleaf",
            "azalea", "orchid", "allium", "bluet", "daisy", "cornflower", "rose", "peony", "lilac", "chest", "barrel",
            "water", "lava", "bed", "skull", "head", "lectern", "campfire", "anvil", "cauldron", "kelp", "plate",
            "ladder", "scaffolding", "cobweb", "hay"};

    static boolean floorName(String b) {
        for (String s : NOT_FLOOR) {
            if (b.contains(s) && !b.endsWith("_block") && !b.equals("minecraft:grass_block")) {
                return false;
            }
        }
        return true;
    }

    /** A floor row counts as real floor from this share of its width up. */
    static final double FLOOR_ROW = 0.25;

    static int id(Map<String, Integer> ids, String s) {
        Integer i = ids.get(s);
        if (i == null) {
            i = ids.size();
            ids.put(s, i);
        }
        return i;
    }

    /**
     * Floor fill of the ground layer gy (box columns x0.., z0.., w x d): for the edge rows / columns in front of the
     * first one that is real floor ({@link #FLOOR_ROW} of it filled), on each side, the empty cells get the floor block
     * found by walking inward (state id, or -1).
     */
    static int[] floorFill(Template t, boolean[] solid, int x0, int z0, int w, int d, int gy,
                                   Map<String, Integer> ids) {
        int[] fill = new int[w * d];
        Arrays.fill(fill, -1);
        boolean[] floor = new boolean[w * d];
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int p = t.at(x0 + x, gy, z0 + z);
                floor[x + w * z] = p >= 0 && solid[p] && floorName(Structures.blockOf(t.palette.get(p)));
            }
        }
        // north (front) and south rows
        int front = firstRow(floor, w, d, true, false);
        int back = firstRow(floor, w, d, true, true);
        int west = firstRow(floor, w, d, false, false);
        int east = firstRow(floor, w, d, false, true);
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int p = t.at(x0 + x, gy, z0 + z);
                if (p >= 0 && solid[p]) {
                    continue;
                }
                int dz = z < front ? 1 : z > back ? -1 : 0;
                int dx = x < west ? 1 : x > east ? -1 : 0;
                if (dz == 0 && dx == 0) {
                    continue;
                }
                // walk inward (toward the real floor) to the first floor block
                int cx = x;
                int cz = z;
                while (cx >= 0 && cz >= 0 && cx < w && cz < d && !floor[cx + w * cz]) {
                    cx += dx;
                    cz += dz;
                }
                if (cx >= 0 && cz >= 0 && cx < w && cz < d) {
                    fill[x + w * z] = id(ids, t.palette.get(t.at(x0 + cx, gy, z0 + cz)));
                }
            }
        }
        return fill;
    }

    /** Index of the first row (alongX: rows of constant z) from one side with real floor over FLOOR_ROW of it. */
    private static int firstRow(boolean[] floor, int w, int d, boolean alongX, boolean fromEnd) {
        int n = alongX ? d : w;
        int len = alongX ? w : d;
        for (int k = 0; k < n; k++) {
            int r = fromEnd ? n - 1 - k : k;
            int c = 0;
            for (int a = 0; a < len; a++) {
                if (alongX ? floor[a + w * r] : floor[r + w * a]) {
                    c++;
                }
            }
            if (c >= FLOOR_ROW * len) {
                return r;
            }
        }
        return fromEnd ? n - 1 : 0;
    }

    /**
     * Real leaves of a template: cell index -> state id of the same leaves, not persistent, with their distance
     * (steps through leaves from a log, 1..6). Leaves farther than that from any log are not in the map.
     */
    static Map<Integer, Integer> realLeaves(Template t, boolean[] solid, int x0, int y0, int z0, int w, int h,
                                                    int d, Map<String, Integer> ids, int[] dist) {
        boolean[] logP = new boolean[t.palette.size()];
        boolean[] leafP = new boolean[t.palette.size()];
        boolean any = false;
        for (int p = 0; p < logP.length; p++) {
            String b = Structures.blockOf(t.palette.get(p));
            logP[p] = solid[p] && Structures.isLogName(b);
            leafP[p] = solid[p] && Structures.isLeavesName(b);
            any |= leafP[p];
        }
        Map<Integer, Integer> out = new HashMap<>();
        if (!any) {
            return out;
        }
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int y = y0; y < y0 + h; y++) {
            for (int z = z0; z < z0 + d; z++) {
                for (int x = x0; x < x0 + w; x++) {
                    int p = t.at(x, y, z);
                    if (p >= 0 && logP[p]) {
                        q.add(new int[]{x, y, z, 0});
                    }
                }
            }
        }
        int[][] step = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            if (c[3] >= Flora.LEAF_REACH) {
                continue;
            }
            for (int[] s : step) {
                int x = c[0] + s[0];
                int y = c[1] + s[1];
                int z = c[2] + s[2];
                int p = t.at(x, y, z);
                if (p < 0 || !leafP[p]) {
                    continue;
                }
                int k = x + t.sx * (z + t.sz * y);
                if (dist[k] == 0) {
                    dist[k] = c[3] + 1;
                    q.add(new int[]{x, y, z, c[3] + 1});
                    out.put(k, id(ids, Structures.withProps(t.palette.get(p), c[3] + 1)));
                }
            }
        }
        // leaves saved as not persistent but out of reach of any trunk (13 nov 2026: casa_moderna_garagem, casa_vidro,
        // predio_brutalista_u, predio_rampa) would fall in the world: they stay persistent, like the hedges
        for (int y = y0; y < y0 + h; y++) {
            for (int z = z0; z < z0 + d; z++) {
                for (int x = x0; x < x0 + w; x++) {
                    int p = t.at(x, y, z);
                    int k = x + t.sx * (z + t.sz * y);
                    if (p >= 0 && leafP[p] && dist[k] == 0 && t.palette.get(p).contains("persistent=false")) {
                        out.put(k, id(ids, t.palette.get(p).replace("persistent=false", "persistent=true")));
                    }
                }
            }
        }
        return out;
    }

    /**
     * Leaves that complete the tree crowns the edge of the template cut (asked by the user, 13 nov 2026, for the tree
     * in front of casa_piscina_abandonada): every real leaf of a crown is mirrored across its trunk, in x, in z and in
     * both; a mirror that falls outside the box (where the template had no room for the crown) becomes a leaf hanging
     * out of the structure, over its sidewalk or the core of the block. Its distance to the trunk is counted through
     * the leaves like the others ({@link #realLeaves}); one farther than {@link Flora#LEAF_REACH} is left out. Each
     * entry is {x, y, z, state} in template coordinates.
     */
    static List<int[]> crowns(Template t, boolean[] solid, int[] dist, int x0, int y0, int z0, int w, int h,
                                      int d, Map<String, Integer> ids) {
        // trunks: (x, z) -> {logs, lowest y}
        Map<Long, int[]> trunks = new HashMap<>();
        for (int y = y0; y < y0 + h; y++) {
            for (int z = z0; z < z0 + d; z++) {
                for (int x = x0; x < x0 + w; x++) {
                    int p = t.at(x, y, z);
                    if (p >= 0 && solid[p]) {
                        String b = Structures.blockOf(t.palette.get(p));
                        if (Structures.isLogName(b) && !b.contains("stripped")) {
                            int[] c = trunks.computeIfAbsent(((long) x << 32) | (z & 0xffffffffL), k -> new int[]{0, Integer.MAX_VALUE});
                            c[0]++;
                            c[1] = Math.min(c[1], y);
                        }
                    }
                }
            }
        }
        trunks.values().removeIf(c -> c[0] < Structures.TRUNK_MIN);
        List<int[]> out = new ArrayList<>();
        if (trunks.isEmpty()) {
            return out;
        }
        // the mirrors out of the box, with the leaf they mirror
        Map<Long, String> mirror = new LinkedHashMap<>();
        for (int y = y0; y < y0 + h; y++) {
            for (int z = z0; z < z0 + d; z++) {
                for (int x = x0; x < x0 + w; x++) {
                    int k = x + t.sx * (z + t.sz * y);
                    if (dist[k] == 0) {
                        continue;
                    }
                    long best = Long.MIN_VALUE;
                    int bestD = Integer.MAX_VALUE;
                    for (Map.Entry<Long, int[]> e : trunks.entrySet()) {
                        int tx = (int) (e.getKey() >> 32);
                        int tz = (int) (long) e.getKey();
                        int dd = (x - tx) * (x - tx) + (z - tz) * (z - tz);
                        if (dd < bestD && dd <= Structures.CROWN_RADIUS * Structures.CROWN_RADIUS && y >= e.getValue()[1] + 2) {
                            bestD = dd;
                            best = e.getKey();
                        }
                    }
                    if (best == Long.MIN_VALUE) {
                        continue;
                    }
                    int tx = (int) (best >> 32);
                    int tz = (int) best;
                    int[][] m = {{2 * tx - x, z}, {x, 2 * tz - z}, {2 * tx - x, 2 * tz - z}};
                    for (int[] q : m) {
                        boolean outside = q[0] < x0 || q[0] >= x0 + w || q[1] < z0 || q[1] >= z0 + d;
                        if (outside) {
                            mirror.putIfAbsent(key(q[0], y, q[1]), t.palette.get(t.at(x, y, z)));
                        }
                    }
                }
            }
        }
        // distances through the leaves: the template's, then the mirrors' among themselves
        Map<Long, Integer> md = new HashMap<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (long k : mirror.keySet()) {
                int x = (int) (k >> 40) - 0x80000;
                int y = (int) ((k >> 20) & 0xFFFFF) - 0x80000;
                int z = (int) (k & 0xFFFFF) - 0x80000;
                int best = md.getOrDefault(k, Integer.MAX_VALUE);
                int[][] six = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                for (int[] s6 : six) {
                    int nx = x + s6[0];
                    int ny = y + s6[1];
                    int nz = z + s6[2];
                    int nd = Integer.MAX_VALUE;
                    int p = t.at(nx, ny, nz);
                    if (p >= 0 && solid[p]) {
                        String b = Structures.blockOf(t.palette.get(p));
                        int kk = nx + t.sx * (nz + t.sz * ny);
                        nd = Structures.isLogName(b) ? 0 : dist[kk] > 0 ? dist[kk] : Integer.MAX_VALUE;
                    }
                    Integer o = md.get(key(nx, ny, nz));
                    if (o != null) {
                        nd = Math.min(nd, o);
                    }
                    if (nd != Integer.MAX_VALUE && nd + 1 < best) {
                        best = nd + 1;
                    }
                }
                if (best != Integer.MAX_VALUE && best <= Flora.LEAF_REACH && !Integer.valueOf(best).equals(md.get(k))) {
                    md.put(k, best);
                    changed = true;
                }
            }
        }
        for (Map.Entry<Long, String> e : mirror.entrySet()) {
            Integer dd = md.get(e.getKey());
            if (dd == null) {
                continue;
            }
            long k = e.getKey();
            out.add(new int[]{(int) (k >> 40) - 0x80000, (int) ((k >> 20) & 0xFFFFF) - 0x80000,
                    (int) (k & 0xFFFFF) - 0x80000, id(ids, Structures.withProps(e.getValue(), dd))});
        }
        return out;
    }

    /** A key for (x, y, z), each in -2^19 .. 2^19. */
    private static long key(int x, int y, int z) {
        return ((long) (x + 0x80000) << 40) | ((long) (y + 0x80000) << 20) | (z + 0x80000);
    }

    /** Neighbours a cobweb may touch and still float: nothing to hang on. */
    private static final String[] NO_HOLD = {"minecraft:air", "minecraft:cave_air", "minecraft:structure_void",
            "minecraft:cobweb", "minecraft:vine", "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern",
            "minecraft:large_fern", "minecraft:glow_lichen"};

    /**
     * Cobwebs that touch nothing to hang on (only air, other cobwebs, vines, plants or carpets) become air (asked by
     * the user, 12 nov 2026): cell index -> Structures.AIR_STATE in {@code cells}.
     */
    static void removeFloatingWebs(Template t, Map<Integer, Integer> cells) {
        int[][] six = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int y = 0; y < t.sy; y++) {
            for (int z = 0; z < t.sz; z++) {
                for (int x = 0; x < t.sx; x++) {
                    int p = t.at(x, y, z);
                    if (p < 0 || !Structures.blockOf(t.palette.get(p)).equals("minecraft:cobweb")) {
                        continue;
                    }
                    boolean held = false;
                    for (int[] q : six) {
                        int n = t.at(x + q[0], y + q[1], z + q[2]);
                        if (n >= 0) {
                            String b = Structures.blockOf(t.palette.get(n));
                            held |= !Arrays.asList(NO_HOLD).contains(b) && !b.endsWith("_carpet");
                        }
                    }
                    if (!held) {
                        cells.put(x + t.sx * (z + t.sz * y), Structures.AIR_STATE);
                    }
                }
            }
        }
    }

    /**
     * The openings of the ground layer {@code gy} (template box x0.., z0.., w x d): air or cave_air there with cave_air
     * under it (the mouth of a ramp or a stairwell over an underground garage), and the air of the ground layer joined
     * to those (the top of the ramp, whose own blocks lie under it: asked by the user, 13 nov 2026, after two rows of
     * terrain closed the ramp of predio_largo). Everywhere else air of the ground layer becomes city ground.
     */
    static boolean[] openings(Template t, int[] state, int x0, int z0, int w, int d, int gy) {
        boolean[] out = new boolean[w * d];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int p = t.at(x0 + x, gy, z0 + z);
                int below = t.at(x0 + x, gy - 1, z0 + z);
                if (open(state, p) && below >= 0 && state[below] == Structures.CARVE_STATE) {
                    out[x + w * z] = true;
                    q.add(new int[]{x, z});
                }
            }
        }
        int[][] four = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] f : four) {
                int x = c[0] + f[0];
                int z = c[1] + f[1];
                if (x < 0 || z < 0 || x >= w || z >= d || out[x + w * z]) {
                    continue;
                }
                if (open(state, t.at(x0 + x, gy, z0 + z))) {
                    out[x + w * z] = true;
                    q.add(new int[]{x, z});
                }
            }
        }
        return out;
    }

    private static boolean open(int[] state, int p) {
        return p >= 0 && (state[p] == Structures.AIR_STATE || state[p] == Structures.CARVE_STATE);
    }
}
