package com.abandonedmoderncity.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks again copies of every building ({@link Access#walk}) with their barrels kept (as chests) or taken out and
 * their drawn chests in place ({@link ChestDraw}), and counts the chests out of reach, the chests with nobody able to
 * stand in front and the containers reached before that are no longer reached. {@code ChestDrawTest} does 40 copies;
 * this tool any number (run by {@code chest_walk.sh}). Exits with 1 when anything is found.
 */
public final class ChestWalk {
    private ChestWalk() {
    }

    private static final int[][] SIDE = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    static Access.Walk walk(Structures.Def d, Template t) {
        String[] placed = new String[t.palette.size()];
        for (int p = 0; p < placed.length; p++) {
            placed[p] = Structures.swap(t.palette.get(p));
        }
        return Access.walk(t, placed, d.ox(), d.oy(), d.oz(), d.w(), d.h(), d.d(), d.ground());
    }

    public static void main(String[] a) {
        int copies = a.length > 0 ? Integer.parseInt(a[0]) : 200;
        int problems = 0;
        for (Structures.Def d : Structures.ALL) {
            if (!d.type().startsWith("buildings_")) {
                continue;
            }
            Template t = d.t();
            int layer = t.sx * t.sz;
            String cls = d.type().substring("buildings_".length());
            int notReached = 0;
            int noFront = 0;
            int cut = 0;
            long chests = 0;
            for (int i = 0; i < copies; i++) {
                long seed = (i + 1) * 0x9E3779B97F4A7C15L;
                List<Rooms.Spot> c = ChestDraw.draw(d.rooms(), cls, t.sx, t.sz, seed);
                chests += c.size();
                Map<Integer, String> base = new HashMap<>();
                for (Map.Entry<Integer, int[]> e : d.barrels().entrySet()) {
                    if (!Barrels.kept(e.getValue(), seed, layer)) {
                        base.put(e.getKey(), "minecraft:air");
                    }
                }
                Map<Integer, String> with = new HashMap<>(base);
                for (Rooms.Spot s : c) {
                    with.put(s.x() + t.sx * (s.z() + t.sz * s.y()), "minecraft:chest[facing="
                            + Rooms.FACINGS[s.facing()] + ",type=single,waterlogged=false]");
                }
                Template t0 = base.isEmpty() ? t : t.withStates(base);
                Access.Walk w0 = walk(d, t0);
                Access.Walk w1 = walk(d, t.withStates(with));
                for (Rooms.Spot s : c) {
                    notReached += w1.reaches(s.x(), s.y(), s.z()) ? 0 : 1;
                    noFront += w1.reachedAt(s.x() + SIDE[s.facing()][0], s.y(), s.z() + SIDE[s.facing()][1]) ? 0 : 1;
                }
                for (int y = 0; y < t.sy; y++) {
                    for (int z = 0; z < t.sz; z++) {
                        for (int x = 0; x < t.sx; x++) {
                            int p = t0.at(x, y, z);
                            if (p >= 0 && Access.container(Access.bare(t0.palette.get(p))) && w0.reaches(x, y, z)
                                    && !w1.reaches(x, y, z)) {
                                cut++;
                            }
                        }
                    }
                }
            }
            problems += notReached + noFront + cut;
            System.out.printf("%-28s %d copies, %d chests: out of reach %d, nobody in front %d, containers cut off %d%n",
                    d.name(), copies, chests, notReached, noFront, cut);
        }
        System.out.println(problems == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + problems + ")");
        System.exit(problems == 0 ? 0 : 1);
    }
}
