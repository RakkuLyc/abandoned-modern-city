package com.abandonedmoderncity.core;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Containers of a structure that are hard to get to: their loot is more generous, a reward for the effort.
 * A container is hard to get to when no one walking in from the street reaches it without breaking blocks (pure
 * function of the template), when the structure's line of {@code index.txt} lists it (column {@code hard}, see
 * {@link Result#withHard}), or by where it stands:
 * <ul>
 *   <li>underground: at or under the ground layer (the basement of the police station);</li>
 *   <li>on the terrace: open sky over it, {@link #ABOVE} or more blocks over the ground layer and at most
 *       {@link #TERRACE_TOP} blocks under the highest layer of the structure (its roof, not a yard or a lower
 *       wing);</li>
 *   <li>in the attic, only in the houses: one ceiling between it and the sky (the roof), {@link #ABOVE} or more blocks
 *       over the ground layer.</li>
 * </ul>
 * Its height alone does not count.
 * <p>
 * The walk: a player stands in two passable cells over something solid (or in a ladder, vine, scaffolding or water);
 * walks to a side neighbour at the same height, jumps up one block (with room overhead, never onto a fence, a wall or
 * a pane, which are 1.5 high), falls any height, and climbs up and down ladders, vines, scaffolding and water.
 * Doors (not iron), trapdoors (not iron) and fence gates open; plants, cobwebs, carpets, torches, signs and other
 * blocks without collision are passable; everything else (leaves included) is solid. Outside the box the city ground
 * is solid up to the ground layer and air above. A container is reached from a standing cell when it lies within one
 * block sideways (diagonals too), from one block under the feet to one block over the head.
 */
public final class Access {
    private Access() {
    }

    private static final String[] PASS_EXACT = {"air", "cave_air", "void_air", "structure_void", "short_grass",
            "tall_grass", "fern", "large_fern", "dead_bush", "vine", "glow_lichen", "sculk_vein", "cobweb", "torch",
            "wall_torch", "redstone_torch", "redstone_wall_torch", "soul_torch", "soul_wall_torch", "redstone_wire",
            "lever", "tripwire", "tripwire_hook", "water", "bubble_column", "light", "snow", "seagrass", "tall_seagrass",
            "kelp", "kelp_plant", "sugar_cane", "sweet_berry_bush", "hanging_roots", "spore_blossom", "cave_vines",
            "cave_vines_plant", "twisting_vines", "twisting_vines_plant", "weeping_vines", "weeping_vines_plant",
            "nether_sprouts", "wheat", "carrots", "potatoes", "beetroots", "brown_mushroom", "red_mushroom", "lily_pad",
            "fire", "poppy", "dandelion", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower",
            "lily_of_the_valley", "wither_rose", "rose_bush", "peony", "lilac", "sunflower", "torchflower",
            "pink_petals", "ladder", "scaffolding", "small_dripleaf"};
    private static final String[] PASS_END = {"_sapling", "_tulip", "_carpet", "_pressure_plate", "_button", "_sign",
            "_banner", "_rail", "rail"};
    private static final String[] CLIMB = {"ladder", "vine", "scaffolding", "water", "twisting_vines",
            "twisting_vines_plant", "weeping_vines", "weeping_vines_plant", "cave_vines", "cave_vines_plant"};

    static String bare(String state) {
        // Template, not Structures: this also runs on other threads while Structures is still being initialized
        String b = Template.blockOf(state);
        return b.startsWith("minecraft:") ? b.substring(10) : b;
    }

    static boolean passable(String b) {
        for (String s : PASS_EXACT) {
            if (b.equals(s)) {
                return true;
            }
        }
        for (String s : PASS_END) {
            if (b.endsWith(s)) {
                return true;
            }
        }
        return b.endsWith("_door") && !b.equals("iron_door") || b.endsWith("_trapdoor") && !b.equals("iron_trapdoor")
                || b.endsWith("_fence_gate");
    }

    static boolean climb(String b) {
        for (String s : CLIMB) {
            if (b.equals(s)) {
                return true;
            }
        }
        return false;
    }

    /** Solid blocks 1.5 high (or thin): no one jumps onto them. */
    static boolean tall(String b) {
        return b.endsWith("_fence") || b.endsWith("_wall") || b.endsWith("pane") || b.equals("iron_bars")
                || b.equals("chain");
    }

    static boolean container(String b) {
        return b.equals("chest") || b.equals("trapped_chest") || b.equals("barrel") || b.equals("dispenser")
                || b.equals("dropper") || b.equals("decorated_pot");
    }

    /** A container on the terrace or in the attic stands this many blocks or more over the ground layer. */
    public static final int ABOVE = 4;
    /** A container on the terrace stands at most this many blocks under the highest layer of the structure. */
    public static final int TERRACE_TOP = 5;

    /**
     * Result for one template: hard-to-get-to container cells (template cell index), the containers, those out of
     * reach, those listed by hand, and those underground, on the terrace and in the attic (a container may be in more
     * than one of these; {@code hard} has each once).
     */
    public record Result(Set<Integer> hard, int containers, int closed, int listed, int underground, int terrace,
                         int attic) {
        /** The same with these container cells hard too (the {@code hard} column of {@code index.txt}). */
        public Result withHard(Set<Integer> cells) {
            Set<Integer> all = new HashSet<>(hard);
            all.addAll(cells);
            return new Result(Set.copyOf(all), containers, closed, cells.size(), underground, terrace, attic);
        }
    }

    /** The same, without the attic rule (it is only for the houses). */
    public static Result of(Template t, String[] states, int ox, int oy, int oz, int w, int h, int d, int ground) {
        return of(t, states, ox, oy, oz, w, h, d, ground, false);
    }

    /**
     * Hard-to-get-to containers of the template {@code t}, whose box of solid blocks starts at (ox, oy, oz), is w x h x d
     * and has its ground layer at oy + ground. {@code states} gives the block state placed for a palette index (with
     * the swaps of {@link Structures#swap}). {@code attics}: whether the attic rule applies (the houses).
     */
    public static Result of(Template t, String[] states, int ox, int oy, int oz, int w, int h, int d, int ground,
                            boolean attics) {
        Walk f = walk(t, states, ox, oy, oz, w, h, d, ground);
        int gy = oy + ground;
        Set<Integer> hard = new HashSet<>();
        int containers = 0;
        int closed = 0;
        int underground = 0;
        int terrace = 0;
        int attic = 0;
        int top = oy + h - 1;
        for (int ty = oy; ty < oy + h; ty++) {
            for (int tz = oz; tz < oz + d; tz++) {
                for (int tx = ox; tx < ox + w; tx++) {
                    int p = t.at(tx, ty, tz);
                    if (p < 0 || !container(bare(states[p]))) {
                        continue;
                    }
                    containers++;
                    if (!f.reaches(tx, ty, tz)) {
                        closed++;
                        hard.add(tx + t.sx * (tz + t.sz * ty));
                    }
                    // where it stands: underground, on the terrace, in the attic
                    int where = place(t, states, tx, ty, tz, gy, top, attics);
                    underground += (where & UNDERGROUND) != 0 ? 1 : 0;
                    terrace += (where & TERRACE) != 0 ? 1 : 0;
                    attic += (where & ATTIC) != 0 ? 1 : 0;
                    if (where != 0) {
                        hard.add(tx + t.sx * (tz + t.sz * ty));
                    }
                }
            }
        }
        return new Result(Set.copyOf(hard), containers, closed, 0, underground, terrace, attic);
    }

    /** Bits of {@link #place}: underground, on the terrace, in the attic. */
    public static final int UNDERGROUND = 1;
    public static final int TERRACE = 2;
    public static final int ATTIC = 4;

    /**
     * Where the cell (tx, ty, tz) of the template stands, as bits: {@link #UNDERGROUND} (at or under the ground layer
     * {@code gy}), {@link #TERRACE} (open sky, {@link #ABOVE}+ over the ground layer, at most {@link #TERRACE_TOP} under
     * the highest layer {@code top}) and, with {@code attics} (the houses), {@link #ATTIC} (one ceiling, {@link #ABOVE}+
     * over the ground layer). A container standing in any of them is hard to get to.
     */
    static int place(Template t, String[] states, int tx, int ty, int tz, int gy, int top, boolean attics) {
        int ceilings = ceilings(t, states, tx, ty, tz, top);
        boolean under = ty <= gy;
        boolean onTerrace = !under && ceilings == 0 && ty - gy >= ABOVE && top - ty <= TERRACE_TOP;
        boolean inAttic = attics && !under && ceilings == 1 && ty - gy >= ABOVE;
        return (under ? UNDERGROUND : 0) | (onTerrace ? TERRACE : 0) | (inAttic ? ATTIC : 0);
    }

    /**
     * The walk of a player through the template {@code t} (box of solid blocks at (ox, oy, oz), w x h x d, ground layer
     * at oy + ground), from the ring of city ground around the box: the cells where the feet stand, reached by the rules
     * of the class, each with its number of steps from the street (breadth first). {@link Rooms} reads it to put chests
     * where a player walks.
     */
    static Walk walk(Template t, String[] states, int ox, int oy, int oz, int w, int h, int d, int ground) {
        int gy = oy + ground;
        // the frame: the box and a ring of one block around it, from the bottom of the box to two over its top
        int fx0 = ox - 1;
        int fz0 = oz - 1;
        int fy0 = oy;
        int fw = w + 2;
        int fd = d + 2;
        int fh = h + 3;
        byte[] kind = new byte[fw * fd * fh]; // 0 solid, 1 passable, 2 climbable (and passable), 3 tall
        for (int y = 0; y < fh; y++) {
            for (int z = 0; z < fd; z++) {
                for (int x = 0; x < fw; x++) {
                    int tx = fx0 + x;
                    int ty = fy0 + y;
                    int tz = fz0 + z;
                    byte k;
                    boolean inBox = tx >= ox && tx < ox + w && tz >= oz && tz < oz + d && ty < oy + h;
                    if (!inBox) {
                        k = ty <= gy ? (byte) 0 : 1;
                    } else {
                        int p = t.at(tx, ty, tz);
                        String b = p < 0 ? "air" : bare(states[p]);
                        // the floor filled to the edge and the buried air are solid ground
                        if (p < 0 && ty <= gy) {
                            b = "stone";
                        }
                        k = climb(b) ? 2 : passable(b) ? (byte) 1 : tall(b) ? (byte) 3 : 0;
                    }
                    kind[x + fw * (z + fd * y)] = k;
                }
            }
        }
        int[] dist = new int[kind.length];
        java.util.Arrays.fill(dist, -1);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Walk f = new Walk(fx0, fy0, fz0, kind, fw, fd, fh, dist);
        // start: the ring around the box, standing on the city ground
        int sy = gy + 1 - fy0;
        for (int z = 0; z < fd; z++) {
            for (int x = 0; x < fw; x++) {
                if ((x == 0 || z == 0 || x == fw - 1 || z == fd - 1) && f.stand(x, sy, z) && dist[f.i(x, sy, z)] < 0) {
                    dist[f.i(x, sy, z)] = 0;
                    queue.add(new int[]{x, sy, z});
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            f.moves(c[0], c[1], c[2], -1, (nx, ny, nz) -> f.visit(nx, ny, nz, c, queue));
        }
        return f;
    }

    /**
     * Ceilings over (x, y, z) up to the top of the structure (over it, the sky): runs of blocks that close the way up
     * (anything but air, plants, vines, cobwebs, torches, carpets and the like; doors, trapdoors, gates, ladders and
     * scaffolding close it).
     */
    static int ceilings(Template t, String[] states, int x, int y, int z, int top) {
        int n = 0;
        boolean inRun = false;
        for (int yy = y + 1; yy <= top; yy++) {
            int q = t.at(x, yy, z);
            String b = q < 0 ? "air" : bare(states[q]);
            boolean closes = !passable(b) || b.endsWith("_door") || b.endsWith("_trapdoor") || b.endsWith("_fence_gate")
                    || b.equals("ladder") || b.equals("scaffolding");
            if (closes && !inRun) {
                n++;
            }
            inRun = closes;
        }
        return n;
    }

    /**
     * The frame of a walk (the box and a ring of one block around it, from the bottom of the box to two over its top):
     * the kind of each cell (0 solid, 1 passable, 2 climbable and passable, 3 tall) and, for the cells where the feet
     * stand, the steps from the street ({@code -1}: not reached). Frame coordinates are template coordinates minus
     * (fx0, fy0, fz0).
     */
    static final class Walk {
        final int fx0;
        final int fy0;
        final int fz0;
        final byte[] kind;
        final int fw;
        final int fd;
        final int fh;
        final int[] dist;

        Walk(int fx0, int fy0, int fz0, byte[] kind, int fw, int fd, int fh, int[] dist) {
            this.fx0 = fx0;
            this.fy0 = fy0;
            this.fz0 = fz0;
            this.kind = kind;
            this.fw = fw;
            this.fd = fd;
            this.fh = fh;
            this.dist = dist;
        }

        boolean in(int x, int y, int z) {
            return x >= 0 && z >= 0 && y >= 0 && x < fw && z < fd && y < fh;
        }

        int i(int x, int y, int z) {
            return x + fw * (z + fd * y);
        }

        /**
         * Kind of a cell (above the frame air, under it solid), with the cell of frame index {@code blocked} solid (a
         * chest put there), or none (-1).
         */
        int kind(int x, int y, int z, int blocked) {
            if (y >= fh) {
                return 1;
            }
            if (!in(x, y, z)) {
                return 0;
            }
            int i = i(x, y, z);
            return i == blocked ? 0 : kind[i];
        }

        boolean pass(int x, int y, int z) {
            return pass(x, y, z, -1);
        }

        boolean pass(int x, int y, int z, int blocked) {
            int k = kind(x, y, z, blocked);
            return k == 1 || k == 2;
        }

        boolean climb(int x, int y, int z, int blocked) {
            return kind(x, y, z, blocked) == 2;
        }

        /** A player can stand with the feet here: two passable cells over something solid, or in a climbable. */
        boolean stand(int x, int y, int z) {
            return stand(x, y, z, -1);
        }

        boolean stand(int x, int y, int z, int blocked) {
            return pass(x, y, z, blocked) && pass(x, y + 1, z, blocked)
                    && (!pass(x, y - 1, z, blocked) || climb(x, y, z, blocked));
        }

        /** Where the feet go from (x, y, z) (frame coordinates). */
        interface Move {
            void to(int x, int y, int z);
        }

        /**
         * The moves of the walk from the feet at (x, y, z), frame coordinates, with the cell {@code blocked} solid (or
         * -1): to a side neighbour at the same height or falling down to the first thing that holds, jumping up one
         * block (never onto a fence, a wall or a pane), climbing up or down.
         */
        void moves(int x, int y, int z, int blocked, Move m) {
            for (int[] q : FOUR) {
                int nx = x + q[0];
                int nz = z + q[1];
                if (!in(nx, y, nz)) {
                    continue;
                }
                if (pass(nx, y, nz, blocked) && pass(nx, y + 1, nz, blocked)) {
                    // walk on, or fall down to the first thing that holds
                    int ny = y;
                    while (!stand(nx, ny, nz, blocked) && ny - 1 >= 0 && pass(nx, ny - 1, nz, blocked)) {
                        ny--;
                    }
                    m.to(nx, ny, nz);
                } else if (pass(x, y + 2, z, blocked) && kind(nx, y, nz, blocked) != 3) {
                    // jump up one block
                    m.to(nx, y + 1, nz);
                }
            }
            if (climb(x, y, z, blocked) || climb(x, y + 1, z, blocked)) {
                m.to(x, y + 1, z);
            }
            if (y > 0 && climb(x, y - 1, z, blocked)) {
                m.to(x, y - 1, z);
            }
        }

        private static final int[][] FOUR = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        /**
         * Whether the feet at template cell (tx, ty, tz) are still reached with a chest at template cell (bx, by, bz):
         * the walk spreads, by the same moves, from the cells reached in fewer steps than the chest's cell (their way
         * from the street does not pass it), inside a window of {@code r} sideways and {@code ry} up and down around
         * the target. Not found inside the window counts as not reached.
         */
        boolean reachedAvoiding(int tx, int ty, int tz, int bx, int by, int bz, int r, int ry) {
            int x0 = tx - fx0;
            int y0 = ty - fy0;
            int z0 = tz - fz0;
            if (!in(bx - fx0, by - fy0, bz - fz0) || !in(x0, y0, z0) || dist[i(x0, y0, z0)] < 0) {
                return false;
            }
            int blocked = i(bx - fx0, by - fy0, bz - fz0);
            int limit = dist[blocked] < 0 ? Integer.MAX_VALUE : dist[blocked];
            int sw = 2 * r + 1;
            int sh = 2 * ry + 1;
            boolean[] seen = new boolean[sw * sw * sh];
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            for (int y = y0 - ry; y <= y0 + ry; y++) {
                for (int z = z0 - r; z <= z0 + r; z++) {
                    for (int x = x0 - r; x <= x0 + r; x++) {
                        if (in(x, y, z) && dist[i(x, y, z)] >= 0 && dist[i(x, y, z)] < limit && stand(x, y, z, blocked)) {
                            seen[(x - x0 + r) + sw * ((z - z0 + r) + sw * (y - y0 + ry))] = true;
                            queue.add(new int[]{x, y, z});
                        }
                    }
                }
            }
            boolean[] found = new boolean[1];
            while (!queue.isEmpty() && !found[0]) {
                int[] c = queue.poll();
                if (c[0] == x0 && c[1] == y0 && c[2] == z0) {
                    return true;
                }
                moves(c[0], c[1], c[2], blocked, (nx, ny, nz) -> {
                    if (Math.abs(nx - x0) > r || Math.abs(nz - z0) > r || Math.abs(ny - y0) > ry || !in(nx, ny, nz)
                            || !stand(nx, ny, nz, blocked)) {
                        return;
                    }
                    int k = (nx - x0 + r) + sw * ((nz - z0 + r) + sw * (ny - y0 + ry));
                    if (!seen[k]) {
                        seen[k] = true;
                        found[0] |= nx == x0 && ny == y0 && nz == z0;
                        queue.add(new int[]{nx, ny, nz});
                    }
                });
            }
            return found[0];
        }

        void visit(int x, int y, int z, int[] from, ArrayDeque<int[]> queue) {
            if (in(x, y, z) && stand(x, y, z) && dist[i(x, y, z)] < 0) {
                dist[i(x, y, z)] = dist[i(from[0], from[1], from[2])] + 1;
                queue.add(new int[]{x, y, z});
            }
        }

        /** Whether the feet were reached at template cell (tx, ty, tz). */
        boolean reachedAt(int tx, int ty, int tz) {
            return steps(tx, ty, tz) >= 0;
        }

        /** Steps from the street to the feet at template cell (tx, ty, tz), or -1. */
        int steps(int tx, int ty, int tz) {
            int x = tx - fx0;
            int y = ty - fy0;
            int z = tz - fz0;
            return in(x, y, z) ? dist[i(x, y, z)] : -1;
        }

        /**
         * Whether a container at template cell (tx, ty, tz) is reached: the feet stand within one block sideways
         * (diagonals too), from two under it (reached over the head) to one over it.
         */
        boolean reaches(int tx, int ty, int tz) {
            int x = tx - fx0;
            int y = ty - fy0;
            int z = tz - fz0;
            for (int dy = -2; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (in(x + dx, y + dy, z + dz) && dist[i(x + dx, y + dy, z + dz)] >= 0) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
