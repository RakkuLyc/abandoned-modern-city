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
                      int ground, int turn, int[] state, int[] fill, Map<Integer, Integer> leaf) {
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
            if (rx < 0 || rz < 0 || ry < -ground || ry >= h - ground || rx >= sizeX(rot) || rz >= sizeZ(rot)) {
                return BlockCode.KEEP;
            }
            rot = (rot + turn) & 3;
            int x;
            int z;
            switch (rot & 3) {
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
            int ty = oy + ground + ry;
            int p = t.at(ox + x, ty, oz + z);
            int s = p < 0 ? -1 : state[p];
            if (ry == 0 && (p < 0 || s < 0)) {
                // the floor filled out to the edge of the box
                int f = fill[x + w * z];
                if (f >= 0) {
                    return BlockCode.STRUCT + 4 * f + (rot & 3);
                }
            }
            if (p < 0) {
                return BlockCode.KEEP;
            }
            if (s < 0) {
                // air buried under the ground stays ground
                return s == AIR_STATE && ry >= 0 ? BlockCode.AIR : BlockCode.KEEP;
            }
            if (!leaf.isEmpty()) {
                Integer real = leaf.get(cell(ox + x, ty, oz + z));
                if (real != null) {
                    s = real;
                }
            }
            return BlockCode.STRUCT + 4 * s + (rot & 3);
        }

        private int cell(int x, int y, int z) {
            return x + t.sx * (z + t.sz * y);
        }
    }

    private static final int AIR_STATE = -2;
    /** Distinct block state strings of all structures; a structure block code points into this list. */
    public static final List<String> STATES = new ArrayList<>();
    public static final List<Def> ALL;
    /** Smallest depth and front width among the structures (what still fits in a row). */
    public static final int MIN_DEPTH;
    public static final int MIN_WIDTH;
    /**
     * Most structures of a group per city (asked by the user): pharmacies 3 (both kinds), car workshops 2 (both
     * kinds), gas stations 2 (both kinds). The group is the start of the structure's type.
     */
    public static final Map<String, Integer> LIMITS = Map.of("farmacia", 3, "oficina", 2, "posto", 2);

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

    private static Def define(String type, String name, int ground, int turn, Map<String, Integer> ids) {
        Template t = Template.load("buildings/" + name);
        int[] state = new int[t.palette.size()];
        boolean[] solid = new boolean[state.length];
        for (int p = 0; p < state.length; p++) {
            String s = t.palette.get(p);
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
        Map<Integer, Integer> leaf = realLeaves(t, solid, x0, y0, z0, w, y1 - y0 + 1, d, ids);
        String group = null;
        for (String g : LIMITS.keySet()) {
            if (type.startsWith(g)) {
                group = g;
            }
        }
        return new Def(type, name, group, t, x0, y0, z0, w, y1 - y0 + 1, d, ground, turn, state, fill, leaf);
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
                                                    int d, Map<String, Integer> ids) {
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
        int[] dist = new int[t.sx * t.sy * t.sz];
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
                int turn = f.length > 3 ? Math.floorMod(Integer.parseInt(f[3].trim()) / 90, 4) : 0;
                defs.add(define(f[0], f[1], f.length > 2 ? Integer.parseInt(f[2].trim()) : 0, turn, ids));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        STATES.addAll(ids.keySet());
        ALL = List.copyOf(defs);
        MIN_DEPTH = ALL.stream().mapToInt(Def::depth).min().orElse(0);
        MIN_WIDTH = ALL.stream().mapToInt(Def::front).min().orElse(0);
    }
}
