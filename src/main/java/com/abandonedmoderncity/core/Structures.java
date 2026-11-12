package com.abandonedmoderncity.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The building structures supplied by the user ({@code .nbt} files in {@code resources/amcs/buildings/}, listed in its
 * {@code index.txt} as {@code type|file|ground}): their trimmed boxes, their rotation and the block code of each cell.
 * The front of every structure is its -Z side (north); {@link #rotationFor} turns it to face the street.
 * <ul>
 *   <li>{@code ground}: the template layer (counted from its lowest solid layer) that lies at the city ground; the
 *   layers under it are buried (asked by the user for structures that stood one block above the ground).</li>
 *   <li>The ground layer is filled out to the box on every side: rows at the edge with no real floor (only vines,
 *   banners, a stray slab) get the floor block of the first row that has one, so the structure meets the sidewalk
 *   without a gap.</li>
 *   <li>Leaves within {@link Flora#LEAF_REACH} (through leaves) of a log of the template become real leaves (not
 *   persistent, with their distance) and decay once the tree is cut; leaves with no log near them (hedges) stay
 *   persistent.</li>
 * </ul>
 * Block code of a structure block: {@code BlockCode.STRUCT + 4 * state + rotation}; the game layer turns it into a
 * block state (rotated by {@code rotation} quarter turns clockwise).
 */
public final class Structures {
    private Structures() {
    }

    /**
     * One structure. The box (w x h x d) is the box of the non-air blocks of the template.
     *
     * @param type   the folder it came from (farmacia, posto_de_gasolina_padrao, casas, ...)
     * @param name   the file name
     * @param group  the spawn-limit group ({@link #LIMITS}), or null
     * @param w      size along x before rotation (the width of the front)
     * @param h      height of the box (buried layers included)
     * @param d      size along z before rotation (the depth, from the front to the back)
     * @param ground layers of the box under the city ground
     * @param turn   quarter turns clockwise that bring the template's front to its -Z side (0 for most; 2 for a
     *               template whose front is its +Z side, as the user found for casa_moderna_abandonada)
     */
    public record Def(String type, String name, String group, Template t, int ox, int oy, int oz, int w, int h, int d,
                      int ground, int turn, int[] state, int[] fill, Map<Integer, Integer> cells, Access.Result access,
                      List<int[]> overhang, String model) {
        /** Footprint size along x after {@code rot} quarter turns. */
        public int sizeX(int rot) {
            return ((rot + turn) & 1) == 0 ? w : d;
        }

        /** Footprint size along z after {@code rot} quarter turns. */
        public int sizeZ(int rot) {
            return ((rot + turn) & 1) == 0 ? d : w;
        }

        /** Width of the front (along the street) and depth (from the street back), the front turned to -Z. */
        public int front() {
            return sizeX(0);
        }

        public int depth() {
            return sizeZ(0);
        }

        /** Highest layer above the city ground, plus one. */
        public int above() {
            return h - ground;
        }

        /**
         * Block code at (rx, ry, rz) of the rotated box, ry = 0 being the city ground (negative: buried):
         * {@link BlockCode#AIR}, {@link BlockCode#KEEP} (nothing in the template, a structure void, or air under the
         * ground) or a structure block.
         */
        public int code(int rx, int ry, int rz, int rot) {
            int[] tc = template(rx, ry, rz, rot);
            if (tc == null) {
                return BlockCode.KEEP;
            }
            int r = (rot + turn) & 3;
            int x = tc[0] - ox;
            int z = tc[2] - oz;
            int p = t.at(tc[0], tc[1], tc[2]);
            int s = p < 0 ? -1 : state[p];
            if (ry == 0 && (p < 0 || s < 0)) {
                // the floor filled out to the edge of the box
                int f = fill[x + w * z];
                if (f >= 0) {
                    return BlockCode.STRUCT + 4 * f + r;
                }
            }
            if (p < 0) {
                return BlockCode.KEEP;
            }
            if (s >= 0 && !cells.isEmpty()) {
                // a real leaf, or a floating cobweb taken out
                Integer o = cells.get(cell(tc[0], tc[1], tc[2]));
                if (o != null) {
                    s = o;
                }
            }
            if (s < 0) {
                // air buried under the ground stays ground
                return s == AIR_STATE && ry >= 0 ? BlockCode.AIR : BlockCode.KEEP;
            }
            return BlockCode.STRUCT + 4 * s + r;
        }

        /** Banner design saved at (rx, ry, rz) of the rotated box ({pattern, color} layers), or null. */
        public List<String[]> banner(int rx, int ry, int rz, int rot) {
            if (t.banners.isEmpty()) {
                return null;
            }
            int[] tc = template(rx, ry, rz, rot);
            return tc == null ? null : t.banners.get(cell(tc[0], tc[1], tc[2]));
        }

        /**
         * Leaves outside the box, completing a tree crown that the edge of the template cut ({@link #crowns}), for the
         * box turned {@code rot}: each {rx, ry, rz, code}, rx or rz out of the box (ry = 0 being the city ground).
         */
        public List<int[]> overhangs(int rot) {
            List<int[]> out = new ArrayList<>(overhang.size());
            int r = (rot + turn) & 3;
            for (int[] o : overhang) {
                int x = o[0] - ox;
                int z = o[2] - oz;
                int rx;
                int rz;
                switch (r) {
                    case 0 -> {
                        rx = x;
                        rz = z;
                    }
                    case 1 -> {
                        rx = d - 1 - z;
                        rz = x;
                    }
                    case 2 -> {
                        rx = w - 1 - x;
                        rz = d - 1 - z;
                    }
                    default -> {
                        rx = z;
                        rz = w - 1 - x;
                    }
                }
                out.add(new int[]{rx, o[1] - oy - ground, rz, BlockCode.STRUCT + 4 * o[3] + r});
            }
            return out;
        }

        /** Kind of loot (folder of its loot tables): casa, farmacia, oficina, posto or predio. */
        public String lootTheme() {
            for (String k : LOOT_THEMES) {
                if (type.startsWith(k)) {
                    return k;
                }
            }
            return "predio";
        }

        /** Whether the container at (rx, ry, rz) of the rotated box is hard to get to ({@link Access}). */
        public boolean hard(int rx, int ry, int rz, int rot) {
            int[] tc = template(rx, ry, rz, rot);
            return tc != null && access.hard().contains(cell(tc[0], tc[1], tc[2]));
        }

        /** Template coordinates of (rx, ry, rz) of the box turned {@code rot}, or null outside the box. */
        private int[] template(int rx, int ry, int rz, int rot) {
            if (rx < 0 || rz < 0 || ry < -ground || ry >= h - ground || rx >= sizeX(rot) || rz >= sizeZ(rot)) {
                return null;
            }
            int x;
            int z;
            switch ((rot + turn) & 3) {
                case 0 -> {
                    x = rx;
                    z = rz;
                }
                case 1 -> {
                    x = rz;
                    z = d - 1 - rx;
                }
                case 2 -> {
                    x = w - 1 - rx;
                    z = d - 1 - rz;
                }
                default -> {
                    x = w - 1 - rz;
                    z = rx;
                }
            }
            return new int[]{ox + x, oy + ground + ry, oz + z};
        }

        private int cell(int x, int y, int z) {
            return x + t.sx * (z + t.sz * y);
        }
    }

    private static final int AIR_STATE = -2;
    /** Distinct block state strings of all structures; a structure block code points into this list. */
    public static final List<String> STATES = new ArrayList<>();
    public static final List<Def> ALL;
    /**
     * The structures grouped by model (asked by the user, 13 nov 2026): the color variants of a structure are one model.
     * The ring draws a model, then one of its variants, so variants do not make a structure more frequent.
     */
    public static final List<List<Def>> MODELS;
    private static final Map<String, String> BANNER_SPECS = new HashMap<>();

    /** The template of a structure as saved, with the banners its index line copies in (before {@link Weathering}). */
    static Template source(String name) {
        Template t = Template.load("buildings/" + name);
        String spec = BANNER_SPECS.get(name);
        if (spec != null) {
            // <source>@<dx>,<dz>:<from color>=<to color>
            String[] a = spec.split("[@,:=]");
            t = t.withBannersFrom(Template.load("buildings/" + a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]), a[3], a[4]);
        }
        return t;
    }

    /** What {@link Weathering} added to each structure (empty when it is off). */
    public static final Map<String, Weathering.Report> WEATHERING = new LinkedHashMap<>();
    /** Smallest depth and front width among the structures (what still fits in a row). */
    public static final int MIN_DEPTH;
    public static final int MIN_WIDTH;
    /**
     * Most structures of a group per city (asked by the user): pharmacies 3 (both kinds), car workshops 2 (both
     * kinds), gas stations 2 (both kinds). The group is the start of the structure's type.
     */
    public static final Map<String, Integer> LIMITS = Map.of("farmacia", 3, "oficina", 2, "posto", 2);
    /** Kinds of loot by the start of the structure type; every other type (the buildings) gets {@code predio}. */
    static final String[] LOOT_THEMES = {"casa", "farmacia", "oficina", "posto"};

    static String blockOf(String state) {
        return state.contains("[") ? state.substring(0, state.indexOf('[')) : state;
    }

    static boolean isLogName(String b) {
        return b.endsWith("_log") || b.endsWith("_wood") || b.endsWith("_stem") || b.endsWith("_hyphae");
    }

    static boolean isLeavesName(String b) {
        return b.endsWith("_leaves");
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

    private static Def define(String type, String name, int ground, int turn, String model, String bannerSpec,
                              Map<String, Integer> ids) {
        if (bannerSpec != null) {
            BANNER_SPECS.put(name, bannerSpec);
        }
        Template t = source(name);
        if (Weathering.enabled) {
            // nature taking over (config structureOvergrowth): before anything below reads the template
            Weathering.Result w = Weathering.apply(t, ground, name.hashCode());
            t = w.template();
            WEATHERING.put(name, w.report());
        }
        int[] state = new int[t.palette.size()];
        boolean[] solid = new boolean[state.length];
        String[] placed = new String[state.length];
        for (int p = 0; p < state.length; p++) {
            String s = swap(t.palette.get(p));
            placed[p] = s;
            String block = blockOf(s);
            if (block.equals("minecraft:air") || block.equals("minecraft:cave_air") || block.equals("minecraft:void_air")) {
                state[p] = AIR_STATE;
            } else if (block.equals("minecraft:structure_void")) {
                state[p] = -1;
            } else {
                state[p] = id(ids, s);
                solid[p] = true;
            }
        }
        int x0 = t.sx;
        int y0 = t.sy;
        int z0 = t.sz;
        int x1 = -1;
        int y1 = -1;
        int z1 = -1;
        for (int y = 0; y < t.sy; y++) {
            for (int z = 0; z < t.sz; z++) {
                for (int x = 0; x < t.sx; x++) {
                    int p = t.at(x, y, z);
                    if (p >= 0 && solid[p]) {
                        x0 = Math.min(x0, x);
                        x1 = Math.max(x1, x);
                        y0 = Math.min(y0, y);
                        y1 = Math.max(y1, y);
                        z0 = Math.min(z0, z);
                        z1 = Math.max(z1, z);
                    }
                }
            }
        }
        if (x1 < 0) {
            throw new IllegalStateException("structure " + name + " is empty");
        }
        int w = x1 - x0 + 1;
        int d = z1 - z0 + 1;
        int gy = y0 + ground;
        int[] fill = floorFill(t, solid, x0, z0, w, d, gy, ids);
        int[] dist = new int[t.sx * t.sy * t.sz];
        Map<Integer, Integer> cells = realLeaves(t, solid, x0, y0, z0, w, y1 - y0 + 1, d, ids, dist);
        List<int[]> overhang = crowns(t, solid, dist, x0, y0, z0, w, y1 - y0 + 1, d, ids);
        removeFloatingWebs(t, cells);
        String group = null;
        for (String g : LIMITS.keySet()) {
            if (type.startsWith(g)) {
                group = g;
            }
        }
        Access.Result access = Access.of(t, placed, x0, y0, z0, w, y1 - y0 + 1, d, ground);
        return new Def(type, name, group, t, x0, y0, z0, w, y1 - y0 + 1, d, ground, turn, state, fill, cells, access,
                overhang, model);
    }

    private static int id(Map<String, Integer> ids, String s) {
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
    private static int[] floorFill(Template t, boolean[] solid, int x0, int z0, int w, int d, int gy,
                                   Map<String, Integer> ids) {
        int[] fill = new int[w * d];
        java.util.Arrays.fill(fill, -1);
        boolean[] floor = new boolean[w * d];
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int p = t.at(x0 + x, gy, z0 + z);
                floor[x + w * z] = p >= 0 && solid[p] && floorName(blockOf(t.palette.get(p)));
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
    private static Map<Integer, Integer> realLeaves(Template t, boolean[] solid, int x0, int y0, int z0, int w, int h,
                                                    int d, Map<String, Integer> ids, int[] dist) {
        boolean[] logP = new boolean[t.palette.size()];
        boolean[] leafP = new boolean[t.palette.size()];
        boolean any = false;
        for (int p = 0; p < logP.length; p++) {
            String b = blockOf(t.palette.get(p));
            logP[p] = solid[p] && isLogName(b);
            leafP[p] = solid[p] && isLeavesName(b);
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
                    out.put(k, id(ids, withProps(t.palette.get(p), c[3] + 1)));
                }
            }
        }
        return out;
    }

    /** Trunks: columns of at least this many natural (not stripped) logs. */
    static final int TRUNK_MIN = 3;
    /** Leaves this far (horizontally) from a trunk, and at least two blocks over its foot, are its crown. */
    static final int CROWN_RADIUS = 4;

    /**
     * Leaves that complete the tree crowns the edge of the template cut (asked by the user, 13 nov 2026, for the tree
     * in front of casa_piscina_abandonada): every real leaf of a crown is mirrored across its trunk, in x, in z and in
     * both; a mirror that falls outside the box (where the template had no room for the crown) becomes a leaf hanging
     * out of the structure, over its sidewalk or the core of the block. Its distance to the trunk is counted through
     * the leaves like the others ({@link #realLeaves}); one farther than {@link Flora#LEAF_REACH} is left out. Each
     * entry is {x, y, z, state} in template coordinates.
     */
    private static List<int[]> crowns(Template t, boolean[] solid, int[] dist, int x0, int y0, int z0, int w, int h,
                                      int d, Map<String, Integer> ids) {
        // trunks: (x, z) -> {logs, lowest y}
        Map<Long, int[]> trunks = new HashMap<>();
        for (int y = y0; y < y0 + h; y++) {
            for (int z = z0; z < z0 + d; z++) {
                for (int x = x0; x < x0 + w; x++) {
                    int p = t.at(x, y, z);
                    if (p >= 0 && solid[p]) {
                        String b = blockOf(t.palette.get(p));
                        if (isLogName(b) && !b.contains("stripped")) {
                            int[] c = trunks.computeIfAbsent(((long) x << 32) | (z & 0xffffffffL), k -> new int[]{0, Integer.MAX_VALUE});
                            c[0]++;
                            c[1] = Math.min(c[1], y);
                        }
                    }
                }
            }
        }
        trunks.values().removeIf(c -> c[0] < TRUNK_MIN);
        List<int[]> out = new ArrayList<>();
        if (trunks.isEmpty()) {
            return out;
        }
        // the mirrors out of the box, with the leaf they mirror
        Map<Long, String> mirror = new java.util.LinkedHashMap<>();
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
                        if (dd < bestD && dd <= CROWN_RADIUS * CROWN_RADIUS && y >= e.getValue()[1] + 2) {
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
                        String b = blockOf(t.palette.get(p));
                        int kk = nx + t.sx * (nz + t.sz * ny);
                        nd = isLogName(b) ? 0 : dist[kk] > 0 ? dist[kk] : Integer.MAX_VALUE;
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
                    (int) (k & 0xFFFFF) - 0x80000, id(ids, withProps(e.getValue(), dd))});
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
     * the user, 12 nov 2026): cell index -> AIR_STATE in {@code cells}.
     */
    private static void removeFloatingWebs(Template t, Map<Integer, Integer> cells) {
        int[][] six = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int y = 0; y < t.sy; y++) {
            for (int z = 0; z < t.sz; z++) {
                for (int x = 0; x < t.sx; x++) {
                    int p = t.at(x, y, z);
                    if (p < 0 || !blockOf(t.palette.get(p)).equals("minecraft:cobweb")) {
                        continue;
                    }
                    boolean held = false;
                    for (int[] q : six) {
                        int n = t.at(x + q[0], y + q[1], z + q[2]);
                        if (n >= 0) {
                            String b = blockOf(t.palette.get(n));
                            held |= !java.util.Arrays.asList(NO_HOLD).contains(b) && !b.endsWith("_carpet");
                        }
                    }
                    if (!held) {
                        cells.put(x + t.sx * (z + t.sz * y), AIR_STATE);
                    }
                }
            }
        }
    }

    /**
     * Blocks of the templates placed as another block (asked by the user, 13 nov 2026): lightning rods become lit
     * redstone torches, standing when the rod points up or down, on the wall when it points sideways (the torch
     * faces the same way, away from the block it hangs on).
     */
    static String swap(String state) {
        if (!blockOf(state).equals("minecraft:lightning_rod")) {
            return state;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("facing=(\\w+)").matcher(state);
        String facing = m.find() ? m.group(1) : "up";
        return facing.equals("up") || facing.equals("down") ? "minecraft:redstone_torch[lit=true]"
                : "minecraft:redstone_wall_torch[facing=" + facing + ",lit=true]";
    }

    /** The leaves state string with persistent=false and the given distance. */
    static String withProps(String state, int distance) {
        Map<String, String> props = new TreeMap<>();
        String block = blockOf(state);
        if (state.contains("[")) {
            for (String kv : state.substring(state.indexOf('[') + 1, state.length() - 1).split(",")) {
                String[] e = kv.split("=");
                props.put(e[0], e[1]);
            }
        }
        props.put("persistent", "false");
        props.put("distance", Integer.toString(distance));
        StringBuilder sb = new StringBuilder(block).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : props.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.append(']').toString();
    }

    /** A structure drawn by {@code h}: a model (all equally likely), then one of its variants. */
    public static Def pick(long h) {
        List<Def> m = MODELS.get(Hash.range(h, 0, MODELS.size() - 1));
        return m.size() == 1 ? m.get(0) : m.get(Hash.range(Hash.next(h, 0x5641), 0, m.size() - 1));
    }

    /** Quarter turns clockwise that turn the front (-Z) to face the street on the given side. */
    public static final int NORTH = 0;
    public static final int EAST = 1;
    public static final int SOUTH = 2;
    public static final int WEST = 3;

    /** Rotation of a structure standing on the {@code side} edge of a block (the street is on that side). */
    public static int rotationFor(int side) {
        return side & 3;
    }
    // loaded last: every constant above is set by then
    static {
        Map<String, Integer> ids = new LinkedHashMap<>();
        List<Def> defs = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                Structures.class.getResourceAsStream("/amcs/buildings/index.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.trim().split("\\|");
                int turn = f.length > 3 && !f[3].isBlank() ? Math.floorMod(Integer.parseInt(f[3].trim()) / 90, 4) : 0;
                String model = f.length > 4 && !f[4].isBlank() ? f[4].trim() : f[1].trim();
                String banners = f.length > 5 && !f[5].isBlank() ? f[5].trim() : null;
                defs.add(define(f[0], f[1], f.length > 2 ? Integer.parseInt(f[2].trim()) : 0, turn, model, banners, ids));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        STATES.addAll(ids.keySet());
        ALL = List.copyOf(defs);
        Map<String, List<Def>> models = new LinkedHashMap<>();
        for (Def d : ALL) {
            models.computeIfAbsent(d.model(), k -> new ArrayList<>()).add(d);
        }
        MODELS = models.values().stream().map(List::copyOf).toList();
        MIN_DEPTH = ALL.stream().mapToInt(Def::depth).min().orElse(0);
        MIN_WIDTH = ALL.stream().mapToInt(Def::front).min().orElse(0);
    }
}
