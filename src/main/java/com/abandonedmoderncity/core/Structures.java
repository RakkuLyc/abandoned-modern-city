package com.abandonedmoderncity.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The building structures ({@code .nbt} files in {@code resources/amcs/buildings/}, listed in its {@code index.txt},
 * whose header describes the columns): their trimmed boxes, their rotation and the block code of each cell.
 * The front of every structure is its -Z side (north); {@link #rotationFor} turns it to face the street.
 * <ul>
 *   <li>{@code ground}: the template layer (counted from its lowest solid layer) that lies at the city ground; the
 *   layers under it are buried (for structures saved with a layer of ground or foundation under their floor).</li>
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
     * @param type   the kind of structure (houses, pharmacy, gas_station_small, buildings_large, ...)
     * @param name   the file name
     * @param group  the spawn-limit group ({@link #LIMITS}), or null
     * @param w      size along x before rotation (the width of the front)
     * @param h      height of the box (buried layers included)
     * @param d      size along z before rotation (the depth, from the front to the back)
     * @param ground layers of the box under the city ground
     * @param turn   quarter turns clockwise that bring the template's front to its -Z side (0 for most; 2 for a
     *               template whose front is its +Z side, such as abandoned_modern_house)
     */
    public record Def(String type, String name, String group, Template t, int ox, int oy, int oz, int w, int h, int d,
                      int ground, int turn, int[] state, int[] fill, Map<Integer, Integer> cells, Access.Result access,
                      List<int[]> overhang, String model, boolean[] openings, Map<Integer, int[]> barrels) {
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
                // air buried under the ground stays ground; cave_air is dug out even there (underground garages)
                if (s == CARVE_STATE) {
                    return BlockCode.AIR;
                }
                return s == AIR_STATE && ry >= 0 ? BlockCode.AIR : BlockCode.KEEP;
            }
            return BlockCode.STRUCT + 4 * s + r;
        }

        /**
         * Block code at (rx, ry, rz) in one copy of the structure, of seed {@code seed} (the lot's): the same as
         * {@link #code(int, int, int, int)}, but the barrels of a building are taken out or become chests
         * ({@link Barrels}). A barrel taken out is air, also under the ground (it stood in a dug-out room).
         */
        public int code(int rx, int ry, int rz, int rot, long seed) {
            int c = code(rx, ry, rz, rot);
            if (barrels.isEmpty() || c < BlockCode.STRUCT) {
                return c;
            }
            int[] tc = template(rx, ry, rz, rot);
            int[] b = barrels.get(cell(tc[0], tc[1], tc[2]));
            if (b == null) {
                return c;
            }
            return Barrels.kept(b, seed, t.sx * t.sz) ? BlockCode.STRUCT + 4 * b[Barrels.CHEST] + ((rot + turn) & 3)
                    : BlockCode.AIR;
        }

        /**
         * Whether the ground layer at (rx, rz) of the rotated box is an opening over dug-out space: air or cave_air with
         * cave_air under it (the mouth of a car ramp or a stairwell going down to an underground garage). It stays open
         * instead of becoming city ground.
         */
        public boolean opening(int rx, int rz, int rot) {
            int[] tc = template(rx, 0, rz, rot);
            return tc != null && openings[(tc[0] - ox) + w * (tc[2] - oz)];
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
         * Leaves outside the box, completing a tree crown that the edge of the template cut
         * ({@link StructureLoader#crowns}), for the box turned {@code rot}: each {rx, ry, rz, code}, rx or rz out of the
         * box (ry = 0 being the city ground).
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

        /**
         * Whether at most one of this structure (any color of it) may stand in a city: the buildings, which are big
         * and recognizable, not the houses, pharmacies, workshops or gas stations.
         */
        public boolean onePerCity() {
            return type.startsWith("buildings") || type.equals("special");
        }

        /**
         * Category of the structure for {@code /amcs spawn <category> <name>}: house, pharmacy, workshop, gas_station,
         * building, police_station or special.
         */
        public String category() {
            return categoryOf(type);
        }

        /**
         * Kind of loot (folder of its loot tables): house, pharmacy, workshop, gas_station, police_station, or building
         * for every other structure (the buildings and the special one, whatever their number of containers).
         */
        public String lootTheme() {
            for (String k : LOOT_THEMES) {
                if (type.startsWith(k)) {
                    return k;
                }
            }
            return "building";
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

    static final int AIR_STATE = -2;
    /**
     * State of {@code minecraft:cave_air} in a template: air even under the city ground, dug out of the terrain (an
     * underground garage, a basement). Plain air under the ground stays ground.
     */
    static final int CARVE_STATE = -3;
    /** Distinct block state strings of all structures; a structure block code points into this list. */
    public static final List<String> STATES = new ArrayList<>();
    public static final List<Def> ALL;
    /**
     * The structures grouped by model: the color variants of a structure are one model.
     * The ring draws a model, then one of its variants, so variants do not make a structure more frequent.
     */
    public static final List<List<Def>> MODELS;
    private static final Map<String, String> BANNER_SPECS = new HashMap<>();
    private static final Map<String, String> NEUTRAL_FROM = new HashMap<>();
    /** Cells of each color variant given back the neutral blocks of its original (the F A R M Á C I A letters). */
    public static final Map<String, Integer> LETTERS_REMOVED = new LinkedHashMap<>();

    /** The template of a structure as saved, with the banners its index line copies in (before {@link Weathering}). */
    static Template source(String name) {
        Template t = Template.load("buildings/" + name);
        String from = NEUTRAL_FROM.get(name);
        if (from != null) {
            // the colored letters of the variant go: the original's neutral wall comes back
            int[] n = new int[1];
            t = t.withNeutralFrom(Template.load("buildings/" + from), n);
            LETTERS_REMOVED.put(name, n[0]);
        }
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
     * Most structures of a group per city: pharmacies 3 (both kinds), car workshops 2 (both kinds), gas stations 2
     * (both kinds), the special building 1, the police station 1. The group is the start of the structure's type.
     */
    public static final Map<String, Integer> LIMITS = Map.of("pharmacy", 3, "workshop", 2, "gas_station", 2, "special", 1,
            "police_station", 1);
    /**
     * Kinds of loot by the start of the structure type; every other type (the buildings) gets {@code building}.
     * {@code police_station} has its own theme (same vanilla tables as {@code building}) so an
     * add-on such as AMC TACZ Loot can give the police station more weapons.
     */
    static final String[] LOOT_THEMES = {"house", "pharmacy", "workshop", "gas_station", "police_station"};
    /** Categories of {@code /amcs spawn}, by the start of the structure type. */
    public static final String[] CATEGORIES = {"house", "pharmacy", "workshop", "gas_station", "building", "police_station",
            "special"};

    /** Category of {@code /amcs spawn} of a structure type (by the start of the type). */
    static String categoryOf(String type) {
        for (String k : CATEGORIES) {
            if (type.startsWith(k)) {
                return k;
            }
        }
        return type;
    }

    static String blockOf(String state) {
        return state.contains("[") ? state.substring(0, state.indexOf('[')) : state;
    }

    static boolean isLogName(String b) {
        return b.endsWith("_log") || b.endsWith("_wood") || b.endsWith("_stem") || b.endsWith("_hyphae");
    }

    static boolean isLeavesName(String b) {
        return b.endsWith("_leaves");
    }

    private static Def define(String type, String name, int ground, int turn, String model, String bannerSpec,
                              String neutralFrom, int seed, String hardCells, Map<String, Integer> ids) {
        if (bannerSpec != null) {
            BANNER_SPECS.put(name, bannerSpec);
        }
        if (neutralFrom != null) {
            NEUTRAL_FROM.put(name, neutralFrom);
        }
        Template t = source(name);
        if (Weathering.enabled) {
            // nature taking over (config structureOvergrowth): before anything below reads the template
            Weathering.Result w = Weathering.apply(t, ground, seed);
            t = w.template();
            WEATHERING.put(name, w.report());
        }
        return of(type, name, t, ground, turn, model, hardCells, ids);
    }

    /** A structure from a template already loaded (and weathered); the tests build small templates with it. */
    static Def of(String type, String name, Template t, int ground, int turn, String model, Map<String, Integer> ids) {
        return of(type, name, t, ground, turn, model, null, ids);
    }

    /** The same, with containers made hard by hand ({@code hardCells}: the {@code hard} column of index.txt). */
    static Def of(String type, String name, Template t, int ground, int turn, String model, String hardCells,
                  Map<String, Integer> ids) {
        int[] state = new int[t.palette.size()];
        boolean[] solid = new boolean[state.length];
        String[] placed = new String[state.length];
        for (int p = 0; p < state.length; p++) {
            String s = swap(t.palette.get(p));
            placed[p] = s;
            String block = blockOf(s);
            if (block.equals("minecraft:cave_air")) {
                state[p] = CARVE_STATE;
            } else if (block.equals("minecraft:air") || block.equals("minecraft:void_air")) {
                state[p] = AIR_STATE;
            } else if (block.equals("minecraft:structure_void")) {
                state[p] = -1;
            } else {
                state[p] = StructureLoader.id(ids, s);
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
        int[] fill = StructureLoader.floorFill(t, solid, x0, z0, w, d, gy, ids);
        int[] dist = new int[t.sx * t.sy * t.sz];
        Map<Integer, Integer> cells = StructureLoader.realLeaves(t, solid, x0, y0, z0, w, y1 - y0 + 1, d, ids, dist);
        List<int[]> overhang = StructureLoader.crowns(t, solid, dist, x0, y0, z0, w, y1 - y0 + 1, d, ids);
        StructureLoader.removeFloatingWebs(t, cells);
        String group = null;
        for (String g : LIMITS.keySet()) {
            if (type.startsWith(g)) {
                group = g;
            }
        }
        // the attic rule of the hard containers is only for the houses
        Access.Result access = Access.of(t, placed, x0, y0, z0, w, y1 - y0 + 1, d, ground,
                categoryOf(type).equals("house"));
        if (hardCells != null) {
            // containers made hard by hand: x,y,z of the .nbt (as the structure block shows them), separated by ;
            Set<Integer> listed = new HashSet<>();
            for (String c : hardCells.split(";")) {
                String[] xyz = c.trim().split(",");
                int hx = Integer.parseInt(xyz[0].trim());
                int hy = Integer.parseInt(xyz[1].trim());
                int hz = Integer.parseInt(xyz[2].trim());
                int p = hx < 0 || hy < 0 || hz < 0 || hx >= t.sx || hy >= t.sy || hz >= t.sz ? -1 : t.at(hx, hy, hz);
                if (p < 0 || !Access.container(Access.bare(placed[p]))) {
                    throw new IllegalStateException("structure " + name + ": the hard cell " + c.trim()
                            + " of index.txt is not a container");
                }
                listed.add(hx + t.sx * (hz + t.sz * hy));
            }
            access = access.withHard(listed);
        }
        boolean[] openings = StructureLoader.openings(t, state, x0, z0, w, d, y0 + ground);
        // the barrels of the buildings: taken out or turned into chests, copy by copy
        Map<Integer, int[]> barrels = categoryOf(type).equals("building") ? Barrels.of(t, ids) : Map.of();
        return new Def(type, name, group, t, x0, y0, z0, w, y1 - y0 + 1, d, ground, turn, state, fill, cells, access,
                overhang, model, openings, barrels);
    }

    /** Trunks: columns of at least this many natural (not stripped) logs. */
    static final int TRUNK_MIN = 3;
    /** Leaves this far (horizontally) from a trunk, and at least two blocks over its foot, are its crown. */
    static final int CROWN_RADIUS = 4;

    /**
     * Blocks of the templates placed as another block: lightning rods become lit
     * redstone torches, standing when the rod points up or down, on the wall when it points sideways (the torch
     * faces the same way, away from the block it hangs on).
     */
    static String swap(String state) {
        if (!blockOf(state).equals("minecraft:lightning_rod")) {
            return state;
        }
        Matcher m = Pattern.compile("facing=(\\w+)").matcher(state);
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
                String neutral = f.length > 6 && !f[6].isBlank() ? f[6].trim() : null;
                // the overgrowth seed: the index's, else the hash of the file name
                int seed = f.length > 7 && !f[7].isBlank() ? Integer.parseInt(f[7].trim()) : f[1].hashCode();
                String hard = f.length > 8 && !f[8].isBlank() ? f[8].trim() : null;
                defs.add(define(f[0], f[1], f.length > 2 ? Integer.parseInt(f[2].trim()) : 0, turn, model, banners,
                        neutral, seed, hard, ids));
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
