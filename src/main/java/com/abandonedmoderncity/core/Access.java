package com.abandonedmoderncity.core;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Containers of a structure that are hard to get to: their loot is more generous, a reward for the effort.
 * A container is hard to get to when no one walking in from the street reaches it without breaking blocks (pure
 * function of the template), or when the structure's line of {@code index.txt} lists it (column {@code hard}, see
 * {@link Result#withHard}). Its height does not count (the rule "15+ blocks above the city ground" was removed at the
 * user's request after 13 Nov 2026).
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
        String b = Structures.blockOf(state);
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

    /**
     * Result for one template: hard-to-get-to container cells (template cell index), the containers, those out of
     * reach and those listed by hand.
     */
    public record Result(Set<Integer> hard, int containers, int closed, int listed) {
        /** The same with these container cells hard too (the {@code hard} column of {@code index.txt}). */
        public Result withHard(Set<Integer> cells) {
            Set<Integer> all = new HashSet<>(hard);
            all.addAll(cells);
            return new Result(Set.copyOf(all), containers, closed, cells.size());
        }
    }

    /**
     * Hard-to-get-to containers of the template {@code t}, whose box of solid blocks starts at (ox, oy, oz), is w x h x d
     * and has its ground layer at oy + ground. {@code states} gives the block state placed for a palette index (with
     * the swaps of {@link Structures#swap}).
     */
    public static Result of(Template t, String[] states, int ox, int oy, int oz, int w, int h, int d, int ground) {
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
        boolean[] seen = new boolean[kind.length];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Frame f = new Frame(kind, fw, fd, fh);
        // start: the ring around the box, standing on the city ground
        int sy = gy + 1 - fy0;
        for (int z = 0; z < fd; z++) {
            for (int x = 0; x < fw; x++) {
                if ((x == 0 || z == 0 || x == fw - 1 || z == fd - 1) && f.stand(x, sy, z) && !seen[f.i(x, sy, z)]) {
                    seen[f.i(x, sy, z)] = true;
                    queue.add(new int[]{x, sy, z});
                }
            }
        }
        int[][] four = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int x = c[0];
            int y = c[1];
            int z = c[2];
            for (int[] q : four) {
                int nx = x + q[0];
                int nz = z + q[1];
                if (!f.in(nx, y, nz)) {
                    continue;
                }
                if (f.pass(nx, y, nz) && f.pass(nx, y + 1, nz)) {
                    // walk on, or fall down to the first thing that holds
                    int ny = y;
                    while (!f.stand(nx, ny, nz) && ny - 1 >= 0 && f.pass(nx, ny - 1, nz)) {
                        ny--;
                    }
                    f.visit(nx, ny, nz, seen, queue);
                } else if (f.pass(x, y + 2, z) && f.kind(nx, y, nz) != 3) {
                    // jump up one block
                    f.visit(nx, y + 1, nz, seen, queue);
                }
            }
            if (f.climb(x, y, z) || f.climb(x, y + 1, z)) {
                f.visit(x, y + 1, z, seen, queue);
            }
            if (y > 0 && f.climb(x, y - 1, z)) {
                f.visit(x, y - 1, z, seen, queue);
            }
        }
        Set<Integer> hard = new HashSet<>();
        int containers = 0;
        int closed = 0;
        for (int ty = oy; ty < oy + h; ty++) {
            for (int tz = oz; tz < oz + d; tz++) {
                for (int tx = ox; tx < ox + w; tx++) {
                    int p = t.at(tx, ty, tz);
                    if (p < 0 || !container(bare(states[p]))) {
                        continue;
                    }
                    containers++;
                    boolean reached = false;
                    int x = tx - fx0;
                    int y = ty - fy0;
                    int z = tz - fz0;
                    for (int dy = -2; dy <= 1 && !reached; dy++) {
                        for (int dz = -1; dz <= 1 && !reached; dz++) {
                            for (int dx = -1; dx <= 1 && !reached; dx++) {
                                // the feet from 2 under the container (reached over the head) to 1 over it
                                int fyy = y + dy;
                                reached = f.in(x + dx, fyy, z + dz) && seen[f.i(x + dx, fyy, z + dz)];
                            }
                        }
                    }
                    if (!reached) {
                        closed++;
                        hard.add(tx + t.sx * (tz + t.sz * ty));
                    }
                }
            }
        }
        return new Result(Set.copyOf(hard), containers, closed, 0);
    }

    private record Frame(byte[] kind, int fw, int fd, int fh) {
        boolean in(int x, int y, int z) {
            return x >= 0 && z >= 0 && y >= 0 && x < fw && z < fd && y < fh;
        }

        int i(int x, int y, int z) {
            return x + fw * (z + fd * y);
        }

        /** Kind of a cell; above the frame is air, under it solid. */
        int kind(int x, int y, int z) {
            if (y >= fh) {
                return 1;
            }
            if (!in(x, y, z)) {
                return 0;
            }
            return kind[i(x, y, z)];
        }

        boolean pass(int x, int y, int z) {
            int k = kind(x, y, z);
            return k == 1 || k == 2;
        }

        boolean climb(int x, int y, int z) {
            return kind(x, y, z) == 2;
        }

        /** A player can stand with the feet here: two passable cells over something solid, or in a climbable. */
        boolean stand(int x, int y, int z) {
            return pass(x, y, z) && pass(x, y + 1, z) && (!pass(x, y - 1, z) || climb(x, y, z));
        }

        void visit(int x, int y, int z, boolean[] seen, ArrayDeque<int[]> queue) {
            if (in(x, y, z) && stand(x, y, z) && !seen[i(x, y, z)]) {
                seen[i(x, y, z)] = true;
                queue.add(new int[]{x, y, z});
            }
        }
    }
}
