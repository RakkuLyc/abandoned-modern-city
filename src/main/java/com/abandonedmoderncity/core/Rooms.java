package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Floors, rooms and the places where a chest can stand in a building (types {@code buildings_*}), read from its
 * template once when it loads (asked by the user on 15 Nov 2026, stage 2 of the procedural chests: the analysis only,
 * nothing is placed yet). Built on the walk of {@link Access}: only where a player walking in from the street stands.
 * <ol>
 *   <li><b>Floors.</b> The cells where the feet stand on something solid, counted by height. A height with
 *   {@link #FLOOR_MIN_CELLS} or more of them, and no busier height within {@link #FLOOR_GAP}, is the base of a floor;
 *   the other heights (stair treads, furniture tops, landings, the slanted floors of the crash building) join the floor
 *   up to {@link #FRAGMENT_BELOW} under them, else the one up to {@link #FLOOR_GAP} over them, else the nearest.</li>
 *   <li><b>Rooms.</b> The plan of a floor (where its feet stand, seen from above) is cut at the doors and at the
 *   passages {@link #NARROW} blocks wide or less (doorways without a door, corridors): what stays joined is a room. Only
 *   what blocks from the floor to the head separates (walls); a table or a bed does not. A room larger than
 *   {@link #ZONE_MAX} cells (open floors) is split in zones, halving its longer side, so that the chests can spread in
 *   it. A room thin and long ({@link #CORRIDOR_WIDTH}, {@link #CORRIDOR_LONG}) is a corridor and gets no chest. The
 *   rooms are a soft signal: an open plan is one room.</li>
 *   <li><b>Places for a chest ({@link Spot}).</b> A floor cell of a room (not a passage) where in the game there is
 *   air (plain air at or under the ground layer becomes ground there) with air over it (the chest opens), on a full
 *   building block ({@link #floorBlock}, never a barrel, slab, stair, bed, leaves or container), against a wall
 *   (blocked from the floor to the head; a barrel never counts), with a player standing beside it, at the same height,
 *   in the walk with every barrel and in the walk without any (each copy keeps a different 30%), not cutting the
 *   passage around it (the cells around stay joined), no barrel within one block, no container beside it, no door,
 *   trapdoor, gate, ladder or scaffolding within {@link #DOOR_CLEAR}, no stairs within {@link #STAIRS_CLEAR}, not
 *   next to a passage or a door, and indoors (something over it) unless it is up on a terrace or a roof
 *   ({@link Access#ABOVE}+ over the ground layer): a yard or a plaza inside the box gets none.</li>
 * </ol>
 * The facing of the chest (away from the wall, toward where the player stands) and whether it is hard to get to (by
 * where it stands, {@link Access#place}: underground or on the terrace; it is never out of reach) are worked out here
 * too, so that the drawing of each copy (next stage) only picks among the spots.
 */
public final class Rooms {
    private Rooms() {
    }

    /** Cells where the feet stand that a height needs to be the base of a floor. */
    public static final int FLOOR_MIN_CELLS = 60;
    /** A height within this many blocks of a busier base is a fragment of it, not a floor. */
    public static final int FLOOR_GAP = 3;
    /** A fragment joins the floor up to this many blocks under it. */
    public static final int FRAGMENT_BELOW = 4;
    /** A floor cell whose plan is this wide or less, along x or z, is a passage (doorway, corridor). */
    public static final int NARROW = 2;
    /**
     * A room this thick or less (the median over its cells of the narrower of its widths along x and z, inside the room),
     * and at least {@link #CORRIDOR_LONG} times as long as thick (its cells over the square of its thickness), is a
     * corridor: no chest in it.
     */
    public static final int CORRIDOR_WIDTH = 4;
    public static final int CORRIDOR_LONG = 4;
    /**
     * Obstacles of a floor plan this small (cells) and away from the edge of the box (pillars, a tall cupboard) are not
     * walls when the width of a room is measured: a hall with pillars is not a set of corridors.
     */
    public static final int PILLAR_MAX = 9;
    /** A room larger than this (cells) is split into zones. */
    public static final int ZONE_MAX = 180;
    /** No door, trapdoor, gate, ladder or scaffolding this close (sideways) to a chest. */
    public static final int DOOR_CLEAR = 2;
    /** No stairs this close (sideways) to a chest. */
    public static final int STAIRS_CLEAR = 1;
    /** The spots farthest from the street (by steps), this share of them, are far. */
    public static final double FAR_SHARE = 0.20;
    /** Smallest distance (sideways, on one floor) between two chests used to measure the capacity. */
    public static final int SPACING = 4;

    /** Codes of a cell in a floor plan (besides a zone number, 0 or more). */
    public static final int NONE = -1;
    public static final int PASSAGE = -2;
    public static final int DOOR = -3;
    public static final int CORRIDOR = -4;

    /** Flags of a spot. */
    public static final int HARD = 1;
    public static final int UNDER = 2;
    public static final int TERRACE = 4;
    public static final int OPEN = 8;
    public static final int FAR = 16;
    public static final int CORNER = 32;
    public static final int FURNITURE = 64;

    /** Facings of a chest, in the template's orientation: the side its front faces. */
    static final String[] FACINGS = {"north", "east", "south", "west"};
    private static final int[][] SIDE = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /**
     * A place for a chest: template cell (x, y, z); its floor, zone and room; its weight (2 against a wall, 3 in a
     * corner, one more beside furniture); its facing ({@link #FACINGS}); its flags; the steps from the street to where
     * the player stands to open it.
     */
    public record Spot(int x, int y, int z, int floor, int zone, int room, int weight, int facing, int flags,
                       int steps) {
        public boolean is(int flag) {
            return (flags & flag) != 0;
        }
    }

    /**
     * A floor: the template height of its base, the lowest and highest heights joined to it, its cells, and its plan
     * ({@code w * d}, index {@code (x - ox) + w * (z - oz)}): a zone number, {@link #NONE}, {@link #PASSAGE},
     * {@link #DOOR} or {@link #CORRIDOR}.
     */
    public record Floor(int y, int from, int to, int cells, int[] plan) {
    }

    /**
     * What {@link #of} found: the box size, the floors, the room of each zone, the number of rooms and of corridors, and
     * the spots.
     */
    public record Layout(int w, int d, List<Floor> floors, int[] zoneRoom, int rooms, int corridors, List<Spot> spots) {
        public static final Layout EMPTY = new Layout(0, 0, List.of(), new int[0], 0, 0, List.of());

        public int zones() {
            return zoneRoom.length;
        }
    }

    /** Whether the block state is a full building block a chest stands on (fails closed: unknown blocks are not). */
    public static boolean floorBlock(String state) {
        String b = Access.bare(state);
        if (b.endsWith("_slab")) {
            return state.contains("type=double");
        }
        for (String s : NOT_FLOOR) {
            if (b.contains(s)) {
                return false;
            }
        }
        for (String s : FLOOR_END) {
            if (b.endsWith(s)) {
                return true;
            }
        }
        for (String s : FLOOR_EXACT) {
            if (b.equals(s)) {
                return true;
            }
        }
        return false;
    }

    private static final String[] NOT_FLOOR = {"stairs", "_wall", "fence", "pane", "leaves", "_bed", "carpet",
            "door", "button", "pressure_plate", "sign", "banner", "glass", "grate", "bars", "chest", "barrel"};
    private static final String[] FLOOR_END = {"_planks", "_concrete", "_concrete_powder", "_terracotta", "_bricks",
            "_tiles", "_wool", "_log", "_wood", "_hyphae", "_copper"};
    private static final String[] FLOOR_EXACT = {"stone", "smooth_stone", "cobblestone", "mossy_cobblestone",
            "cobbled_deepslate", "andesite", "diorite", "granite", "polished_andesite", "polished_diorite",
            "polished_granite", "deepslate", "polished_deepslate", "chiseled_deepslate", "blackstone",
            "polished_blackstone", "chiseled_polished_blackstone", "tuff", "polished_tuff", "chiseled_tuff", "calcite",
            "basalt", "polished_basalt", "smooth_basalt", "smooth_quartz", "quartz_block", "quartz_pillar",
            "chiseled_quartz_block", "sandstone", "cut_sandstone", "smooth_sandstone", "chiseled_sandstone",
            "dark_prismarine", "prismarine", "packed_mud", "moss_block", "grass_block", "dirt", "coarse_dirt",
            "rooted_dirt", "podzol", "gravel", "coal_block", "warped_wart_block", "sculk", "hay_block", "terracotta",
            "copper_block", "crimson_stem", "warped_stem", "stripped_crimson_stem", "stripped_warped_stem"};

    private static boolean barrel(String state) {
        return Structures.blockOf(state).equals("minecraft:barrel");
    }

    /** Blocks a chest keeps away from: doors, trapdoors, gates, ladders, scaffolding (where people pass). */
    private static boolean passageBlock(String b) {
        return b.endsWith("_door") || b.endsWith("_trapdoor") || b.endsWith("_fence_gate") || b.equals("ladder")
                || b.equals("scaffolding");
    }

    /** Blocks with an inventory a chest does not stand beside (it would hide or crowd them). */
    private static boolean storage(String b) {
        return Access.container(b) || b.equals("furnace") || b.equals("smoker") || b.equals("blast_furnace")
                || b.equals("brewing_stand") || b.equals("hopper") || b.equals("lectern") || b.equals("jukebox")
                || b.equals("chiseled_bookshelf");
    }

    /**
     * The floors, rooms and spots of the template {@code t} (states placed per palette index, box of solid blocks at
     * (ox, oy, oz), w x h x d, ground layer at oy + ground; {@code openings}: the ground cells left open over dug-out
     * space, index {@code (x - ox) + w * (z - oz)}).
     */
    static Layout of(Template t, String[] states, int ox, int oy, int oz, int w, int h, int d, int ground,
                     boolean[] openings) {
        return new Scan(t, states, ox, oy, oz, w, h, d, ground, openings).run();
    }

    private static final class Scan {
        final Template t;
        final String[] states;
        final int ox;
        final int oy;
        final int oz;
        final int w;
        final int h;
        final int d;
        final int gy;
        final int top;
        final boolean[] openings;
        final Access.Walk a;
        final Access.Walk b;

        Scan(Template t, String[] states, int ox, int oy, int oz, int w, int h, int d, int ground, boolean[] openings) {
            this.t = t;
            this.states = states;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.w = w;
            this.h = h;
            this.d = d;
            this.gy = oy + ground;
            this.top = oy + h - 1;
            this.openings = openings;
            String[] noBarrels = states.clone();
            for (int p = 0; p < states.length; p++) {
                if (barrel(states[p])) {
                    noBarrels[p] = "minecraft:air";
                }
            }
            a = Access.walk(t, states, ox, oy, oz, w, h, d, ground);
            b = Access.walk(t, noBarrels, ox, oy, oz, w, h, d, ground);
        }

        String state(int x, int y, int z) {
            int p = t.at(x, y, z);
            return p < 0 ? "minecraft:air" : states[p];
        }

        String bare(int x, int y, int z) {
            return Access.bare(state(x, y, z));
        }

        boolean inBox(int x, int y, int z) {
            return x >= ox && x < ox + w && z >= oz && z < oz + d && y >= oy && y < oy + h;
        }

        /** Passable in the walk (template coordinates). */
        boolean pass(Access.Walk k, int x, int y, int z) {
            return k.pass(x - k.fx0, y - k.fy0, z - k.fz0);
        }

        /** Blocked from the floor up (template coordinates), in the walk with the barrels. */
        boolean solid(int x, int y, int z) {
            return !pass(a, x, y, z);
        }

        /**
         * Open in the game: passable, and not plain air (or nothing) at or under the ground layer, which becomes ground
         * there (cave_air stays dug out; the ground layer stays open only over an opening).
         */
        boolean open(int x, int y, int z) {
            if (!pass(a, x, y, z)) {
                return false;
            }
            if (y > gy || !inBox(x, y, z)) {
                return y > gy;
            }
            String bb = bare(x, y, z);
            boolean plain = t.at(x, y, z) < 0 || bb.equals("air") || bb.equals("void_air") || bb.equals("structure_void");
            if (!plain) {
                return true;
            }
            return y == gy && openings[(x - ox) + w * (z - oz)];
        }

        /** The feet stand here on something solid, reached from the street, with room for the head in the game. */
        boolean onFloor(int x, int y, int z) {
            return a.reachedAt(x, y, z) && !pass(a, x, y - 1, z) && open(x, y, z) && open(x, y + 1, z);
        }

        Layout run() {
            // 1. floors: the heights of the feet on something solid, the busiest ones the bases
            TreeMap<Integer, Integer> levels = new TreeMap<>();
            for (int y = oy; y < oy + h; y++) {
                int n = 0;
                for (int z = oz; z < oz + d; z++) {
                    for (int x = ox; x < ox + w; x++) {
                        n += onFloor(x, y, z) ? 1 : 0;
                    }
                }
                if (n > 0) {
                    levels.put(y, n);
                }
            }
            List<Map.Entry<Integer, Integer>> byCount = new ArrayList<>(levels.entrySet());
            byCount.sort(Comparator.comparingInt((Map.Entry<Integer, Integer> e) -> -e.getValue())
                    .thenComparingInt(Map.Entry::getKey));
            List<Integer> bases = new ArrayList<>();
            for (Map.Entry<Integer, Integer> e : byCount) {
                if (e.getValue() < FLOOR_MIN_CELLS) {
                    break;
                }
                boolean near = false;
                for (int base : bases) {
                    near |= Math.abs(base - e.getKey()) <= FLOOR_GAP;
                }
                if (!near) {
                    bases.add(e.getKey());
                }
            }
            if (bases.isEmpty()) {
                return Layout.EMPTY;
            }
            bases.sort(null);
            Map<Integer, Integer> floorOf = new TreeMap<>();
            for (int y : levels.keySet()) {
                floorOf.put(y, floorOf(bases, y));
            }
            int nf = bases.size();
            // 2. each floor's plan: the lowest feet of the floor in each column
            int[][] foot = new int[nf][w * d];
            for (int[] f : foot) {
                Arrays.fill(f, Integer.MIN_VALUE);
            }
            int[] cells = new int[nf];
            int[] from = new int[nf];
            int[] to = new int[nf];
            Arrays.fill(from, Integer.MAX_VALUE);
            Arrays.fill(to, Integer.MIN_VALUE);
            for (int y : levels.keySet()) {
                int f = floorOf.get(y);
                from[f] = Math.min(from[f], y);
                to[f] = Math.max(to[f], y);
                for (int z = oz; z < oz + d; z++) {
                    for (int x = ox; x < ox + w; x++) {
                        int c = (x - ox) + w * (z - oz);
                        if (onFloor(x, y, z) && foot[f][c] == Integer.MIN_VALUE) {
                            foot[f][c] = y;
                            cells[f]++;
                        }
                    }
                }
            }
            // 3. passages, doors, rooms and zones
            List<Floor> floors = new ArrayList<>();
            List<Integer> zoneRoom = new ArrayList<>();
            int rooms = 0;
            int corridors = 0;
            for (int f = 0; f < nf; f++) {
                int[] plan = new int[w * d];
                Arrays.fill(plan, NONE);
                int[] fy = foot[f];
                for (int z = 0; z < d; z++) {
                    for (int x = 0; x < w; x++) {
                        int c = x + w * z;
                        if (fy[c] == Integer.MIN_VALUE) {
                            continue;
                        }
                        String here = bare(ox + x, fy[c], oz + z);
                        if (here.endsWith("_door") || here.endsWith("_fence_gate")) {
                            plan[c] = DOOR;
                        } else if (width(fy, x, z, 1, 0) <= NARROW || width(fy, x, z, 0, 1) <= NARROW) {
                            plan[c] = PASSAGE;
                        } else {
                            plan[c] = Integer.MAX_VALUE; // a room cell, numbered below
                        }
                    }
                }
                boolean[] pillar = pillars(plan);
                int[] room = new int[w * d];
                Arrays.fill(room, -1);
                for (int c0 = 0; c0 < w * d; c0++) {
                    if (plan[c0] != Integer.MAX_VALUE || room[c0] >= 0) {
                        continue;
                    }
                    List<Integer> members = new ArrayList<>();
                    int[] queue = new int[w * d];
                    int qh = 0;
                    int qt = 0;
                    queue[qt++] = c0;
                    int id = rooms + corridors; // numbers this region while it is filled
                    room[c0] = id;
                    while (qh < qt) {
                        int c = queue[qh++];
                        members.add(c);
                        int x = c % w;
                        int z = c / w;
                        for (int[] s : SIDE) {
                            int nx = x + s[0];
                            int nz = z + s[1];
                            if (nx < 0 || nz < 0 || nx >= w || nz >= d) {
                                continue;
                            }
                            int n = nx + w * nz;
                            if (plan[n] == Integer.MAX_VALUE && room[n] < 0 && Math.abs(fy[n] - fy[c]) <= 1) {
                                room[n] = id;
                                queue[qt++] = n;
                            }
                        }
                    }
                    if (corridor(members, room, pillar, id)) {
                        for (int c : members) {
                            plan[c] = CORRIDOR;
                        }
                        corridors++;
                        continue;
                    }
                    for (List<Integer> zone : zones(members)) {
                        for (int c : zone) {
                            plan[c] = zoneRoom.size();
                        }
                        zoneRoom.add(rooms);
                    }
                    rooms++;
                }
                floors.add(new Floor(bases.get(f), from[f], to[f], cells[f], plan));
            }
            // 4. spots
            List<int[]> raw = new ArrayList<>();
            for (int f = 0; f < nf; f++) {
                int[] plan = floors.get(f).plan();
                int[] fy = foot[f];
                for (int z = 0; z < d; z++) {
                    for (int x = 0; x < w; x++) {
                        int c = x + w * z;
                        if (plan[c] < 0) {
                            continue;
                        }
                        int[] s = spot(f, plan, fy, x, z);
                        if (s != null) {
                            raw.add(new int[]{s[0], s[1], s[2], f, plan[c], zoneRoom.get(plan[c]), s[3], s[4], s[5], s[6]});
                        }
                    }
                }
            }
            // the farthest ones from the street
            int[] steps = raw.stream().mapToInt(s -> s[9]).sorted().toArray();
            int far = steps.length == 0 ? Integer.MAX_VALUE
                    : Math.max(1, steps[Math.min(steps.length - 1, (int) Math.floor((1 - FAR_SHARE) * steps.length))]);
            List<Spot> spots = new ArrayList<>(raw.size());
            for (int[] s : raw) {
                int flags = s[8] | (s[9] >= far ? FAR : 0);
                spots.add(new Spot(s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7], flags, s[9]));
            }
            return new Layout(w, d, List.copyOf(floors), zoneRoom.stream().mapToInt(Integer::intValue).toArray(), rooms,
                    corridors, List.copyOf(spots));
        }

        /** Floor cells in a row through (x, z) along (dx, dz), the cell itself included. */
        int width(int[] fy, int x, int z, int dx, int dz) {
            int n = 1;
            for (int k = 1; ; k++) {
                int nx = x + k * dx;
                int nz = z + k * dz;
                if (nx >= w || nz >= d || fy[nx + w * nz] == Integer.MIN_VALUE) {
                    break;
                }
                n++;
            }
            for (int k = 1; ; k++) {
                int nx = x - k * dx;
                int nz = z - k * dz;
                if (nx < 0 || nz < 0 || fy[nx + w * nz] == Integer.MIN_VALUE) {
                    break;
                }
                n++;
            }
            return n;
        }

        /** Whether the room {@code id} (its cells) is a corridor: thin and long ({@link #CORRIDOR_WIDTH}). */
        boolean corridor(List<Integer> cells, int[] room, boolean[] pillar, int id) {
            int[] thick = new int[cells.size()];
            for (int i = 0; i < thick.length; i++) {
                int c = cells.get(i);
                thick[i] = Math.min(inRoom(room, pillar, id, c % w, c / w, 1, 0),
                        inRoom(room, pillar, id, c % w, c / w, 0, 1));
            }
            // the median: where two corridors cross, both widths are long
            Arrays.sort(thick);
            int t = thick[thick.length / 2];
            return t <= CORRIDOR_WIDTH && cells.size() >= CORRIDOR_LONG * t * t;
        }

        /**
         * Cells of the room {@code id}, or of pillars inside it, in a row through (x, z) along (dx, dz), the cell itself
         * included.
         */
        int inRoom(int[] room, boolean[] pillar, int id, int x, int z, int dx, int dz) {
            int n = 1;
            for (int k = 1; x + k * dx < w && z + k * dz < d; k++) {
                int c = (x + k * dx) + w * (z + k * dz);
                if (room[c] != id && !pillar[c]) {
                    break;
                }
                n++;
            }
            for (int k = 1; x - k * dx >= 0 && z - k * dz >= 0; k++) {
                int c = (x - k * dx) + w * (z - k * dz);
                if (room[c] != id && !pillar[c]) {
                    break;
                }
                n++;
            }
            return n;
        }

        /** The cells of the plan where nobody stands that form a small obstacle away from the edge ({@link #PILLAR_MAX}). */
        boolean[] pillars(int[] plan) {
            boolean[] out = new boolean[w * d];
            boolean[] seen = new boolean[w * d];
            int[] queue = new int[w * d];
            for (int c0 = 0; c0 < w * d; c0++) {
                if (plan[c0] != NONE || seen[c0]) {
                    continue;
                }
                int qh = 0;
                int qt = 0;
                queue[qt++] = c0;
                seen[c0] = true;
                boolean edge = false;
                while (qh < qt) {
                    int c = queue[qh++];
                    int x = c % w;
                    int z = c / w;
                    edge |= x == 0 || z == 0 || x == w - 1 || z == d - 1;
                    for (int[] s : SIDE) {
                        int nx = x + s[0];
                        int nz = z + s[1];
                        if (nx >= 0 && nz >= 0 && nx < w && nz < d && plan[nx + w * nz] == NONE && !seen[nx + w * nz]) {
                            seen[nx + w * nz] = true;
                            queue[qt++] = nx + w * nz;
                        }
                    }
                }
                if (!edge && qt <= PILLAR_MAX) {
                    for (int i = 0; i < qt; i++) {
                        out[queue[i]] = true;
                    }
                }
            }
            return out;
        }

        /** The room's cells in zones of at most {@link #ZONE_MAX}: halved across its longer side, again and again. */
        List<List<Integer>> zones(List<Integer> cells) {
            List<List<Integer>> out = new ArrayList<>();
            if (cells.size() <= ZONE_MAX) {
                out.add(cells);
                return out;
            }
            int x0 = Integer.MAX_VALUE;
            int x1 = Integer.MIN_VALUE;
            int z0 = Integer.MAX_VALUE;
            int z1 = Integer.MIN_VALUE;
            for (int c : cells) {
                x0 = Math.min(x0, c % w);
                x1 = Math.max(x1, c % w);
                z0 = Math.min(z0, c / w);
                z1 = Math.max(z1, c / w);
            }
            boolean alongX = x1 - x0 >= z1 - z0;
            List<Integer> sorted = new ArrayList<>(cells);
            sorted.sort(alongX ? Comparator.comparingInt((Integer c) -> c % w).thenComparingInt(c -> c / w)
                    : Comparator.comparingInt((Integer c) -> c / w).thenComparingInt(c -> c % w));
            int half = sorted.size() / 2;
            out.addAll(zones(sorted.subList(0, half)));
            out.addAll(zones(sorted.subList(half, sorted.size())));
            return out;
        }

        /**
         * The spot at plan cell (x, z) of floor f, or null: {x, y, z (template), weight, facing, flags, steps}.
         */
        int[] spot(int f, int[] plan, int[] fy, int lx, int lz) {
            int x = ox + lx;
            int z = oz + lz;
            int y = fy[lx + w * lz];
            String here = bare(x, y, z);
            if (!(here.equals("air") || here.equals("cave_air")) || y <= gy && !here.equals("cave_air") || y == gy) {
                return null;
            }
            if (!open(x, y, z) || !open(x, y + 1, z) || !floorBlock(state(x, y - 1, z))) {
                return null;
            }
            // no barrel within one block, no door, trapdoor, gate, ladder or scaffolding within DOOR_CLEAR, no stairs
            // within STAIRS_CLEAR, and not next to a passage or a door of the plan
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -DOOR_CLEAR; dz <= DOOR_CLEAR; dz++) {
                    for (int dx = -DOOR_CLEAR; dx <= DOOR_CLEAR; dx++) {
                        String n = bare(x + dx, y + dy, z + dz);
                        boolean low = dy <= 1;
                        boolean barrelNear = low && Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && n.equals("barrel");
                        boolean stairsNear = low && Math.abs(dx) <= STAIRS_CLEAR && Math.abs(dz) <= STAIRS_CLEAR
                                && n.endsWith("_stairs");
                        if (passageBlock(n) || barrelNear || stairsNear) {
                            return null;
                        }
                    }
                }
            }
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = lx + dx;
                    int nz = lz + dz;
                    if (nx >= 0 && nz >= 0 && nx < w && nz < d && (plan[nx + w * nz] == PASSAGE || plan[nx + w * nz] == DOOR)) {
                        return null;
                    }
                }
            }
            // walls, furniture, storage and where the player stands
            boolean[] wall = new boolean[4];
            boolean furniture = false;
            boolean[] standing = new boolean[4];
            for (int i = 0; i < 4; i++) {
                int nx = x + SIDE[i][0];
                int nz = z + SIDE[i][1];
                String n = bare(nx, y, nz);
                if (storage(n)) {
                    return null;
                }
                boolean low = solid(nx, y, nz);
                wall[i] = low && solid(nx, y + 1, nz);
                furniture |= low && !wall[i];
                standing[i] = a.reachedAt(nx, y, nz) && b.reachedAt(nx, y, nz) && open(nx, y, nz) && open(nx, y + 1, nz)
                        && !pass(a, nx, y - 1, nz);
            }
            int walls = 0;
            for (boolean k : wall) {
                walls += k ? 1 : 0;
            }
            if (walls == 0) {
                return null;
            }
            boolean corner = (wall[0] || wall[2]) && (wall[1] || wall[3]);
            // the front: toward where the player stands, best with the wall behind
            int facing = -1;
            for (int i = 0; i < 4 && facing < 0; i++) {
                if (standing[i] && wall[(i + 2) & 3]) {
                    facing = i;
                }
            }
            for (int i = 0; i < 4 && facing < 0; i++) {
                if (standing[i]) {
                    facing = i;
                }
            }
            if (facing < 0 || !simple(a, x, y, z) || !simple(b, x, y, z)) {
                return null;
            }
            int steps = Integer.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                if (standing[i]) {
                    steps = Math.min(steps, a.steps(x + SIDE[i][0], y, z + SIDE[i][1]));
                }
            }
            int where = Access.place(t, states, x, y, z, gy, top, false);
            int ceilings = Access.ceilings(t, states, x, y, z, top);
            if (ceilings == 0 && y - gy < Access.ABOVE) {
                // outdoors at the street level (a yard, a plaza inside the box): only indoors, or up on a terrace
                return null;
            }
            int flags = (where != 0 ? HARD : 0) | ((where & Access.UNDERGROUND) != 0 ? UNDER : 0)
                    | ((where & Access.TERRACE) != 0 ? TERRACE : 0)
                    | (ceilings == 0 && y - gy >= Access.ABOVE ? OPEN : 0) | (corner ? CORNER : 0)
                    | (furniture ? FURNITURE : 0);
            int weight = (corner ? 3 : 2) + (furniture ? 1 : 0);
            return new int[]{x, y, z, weight, facing, flags, steps};
        }

        /**
         * Whether a chest at (x, y, z) leaves the cells around it joined: the reached feet among its 8 neighbours at its
         * height form one run around it (each diagonal counts only with both its sides).
         */
        boolean simple(Access.Walk k, int x, int y, int z) {
            int[][] ring = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
            boolean[] in = new boolean[8];
            for (int i = 0; i < 8; i++) {
                in[i] = k.reachedAt(x + ring[i][0], y, z + ring[i][1]);
            }
            for (int i = 1; i < 8; i += 2) {
                in[i] = in[i] && in[i - 1] && in[(i + 1) & 7];
            }
            int runs = 0;
            for (int i = 0; i < 8; i++) {
                runs += in[i] && !in[(i + 7) & 7] ? 1 : 0;
            }
            return runs <= 1;
        }
    }

    /** The floor of the height y, given the bases (sorted). */
    static int floorOf(List<Integer> bases, int y) {
        int below = -1;
        int above = -1;
        for (int i = 0; i < bases.size(); i++) {
            if (bases.get(i) <= y) {
                below = i;
            } else if (above < 0) {
                above = i;
            }
        }
        if (below >= 0 && y - bases.get(below) <= FRAGMENT_BELOW) {
            return below;
        }
        if (above >= 0 && bases.get(above) - y <= FLOOR_GAP) {
            return above;
        }
        if (below < 0) {
            return above;
        }
        if (above < 0) {
            return below;
        }
        return y - bases.get(below) <= bases.get(above) - y ? below : above;
    }

    /**
     * How many chests fit at most {@link #SPACING} apart (sideways, within 2 heights), taking the heaviest spots first:
     * {total, then one per floor}. A measure of room for the next stage, not the drawing itself.
     */
    public static int[] capacity(Layout l, int spacing) {
        List<Spot> order = new ArrayList<>(l.spots());
        order.sort(Comparator.comparingInt((Spot s) -> -s.weight()).thenComparingInt(Spot::y)
                .thenComparingInt(Spot::z).thenComparingInt(Spot::x));
        List<Spot> taken = new ArrayList<>();
        int[] out = new int[1 + l.floors().size()];
        for (Spot s : order) {
            boolean ok = true;
            for (Spot o : taken) {
                if (Math.abs(o.x() - s.x()) < spacing && Math.abs(o.z() - s.z()) < spacing
                        && Math.abs(o.y() - s.y()) <= 2) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                taken.add(s);
                out[0]++;
                out[1 + s.floor()]++;
            }
        }
        return out;
    }
}
