package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The barrels of the buildings ({@link Barrels}): 70% taken out, the others chests, copy by copy. */
class BarrelsTest {
    static final int SEEDS = 200;

    private static boolean building(Structures.Def d) {
        return d.category().startsWith("building_");
    }

    private static Set<Integer> barrelCells(Template t) {
        Set<Integer> out = new HashSet<>();
        for (int y = 0; y < t.sy; y++) {
            for (int z = 0; z < t.sz; z++) {
                for (int x = 0; x < t.sx; x++) {
                    int p = t.at(x, y, z);
                    if (p >= 0 && Structures.blockOf(t.palette.get(p)).equals("minecraft:barrel")) {
                        out.add(x + t.sx * (z + t.sz * y));
                    }
                }
            }
        }
        return out;
    }

    @Test
    void onlyTheBuildingsAndEveryBarrelOfThem() {
        for (Structures.Def d : Structures.ALL) {
            if (building(d)) {
                assertEquals(barrelCells(d.t()), d.barrels().keySet(), d.name());
            } else {
                assertTrue(d.barrels().isEmpty(), d.name() + " is not a building");
            }
        }
    }

    @Test
    void seventyPercentTakenOutForcedNeverStacksFromTheTop() {
        long total = 0;
        long gone = 0;
        int forcedAll = 0;
        int barrelsAll = 0;
        for (Structures.Def d : Structures.ALL) {
            if (!building(d) || d.barrels().isEmpty()) {
                continue;
            }
            int layer = d.t().sx * d.t().sz;
            int forced = 0;
            for (int[] b : d.barrels().values()) {
                forced += b[Barrels.FORCED];
            }
            long dGone = 0;
            for (long seed = 0; seed < SEEDS; seed++) {
                for (Map.Entry<Integer, int[]> e : d.barrels().entrySet()) {
                    int[] b = e.getValue();
                    boolean kept = Barrels.kept(b, seed, layer);
                    assertEquals(kept, Barrels.kept(b, seed, layer), "same seed, same result");
                    if (b[Barrels.FORCED] == 1) {
                        assertTrue(kept, d.name() + ": a barrel holding something is taken out");
                    }
                    if (!kept && b[Barrels.INDEX] + 1 < b[Barrels.HEIGHT]) {
                        int[] above = d.barrels().get(e.getKey() + layer);
                        assertFalse(Barrels.kept(above, seed, layer), d.name() + ": a chest over a barrel taken out");
                    }
                    dGone += kept ? 0 : 1;
                }
            }
            double dShare = dGone / ((double) SEEDS * d.barrels().size());
            if (d.barrels().size() >= 20) {
                assertTrue(Math.abs(dShare - Barrels.GONE) < 0.03, d.name() + ": share taken out " + dShare);
            }
            total += (long) SEEDS * d.barrels().size();
            gone += dGone;
            forcedAll += forced;
            barrelsAll += d.barrels().size();
            System.out.printf("BARRELS %s: %d barrels, %d forced (always chests), %.1f%% taken out, %.1f chests per copy%n",
                    d.name(), d.barrels().size(), forced, 100.0 * dGone / ((long) SEEDS * d.barrels().size()),
                    ((long) SEEDS * d.barrels().size() - dGone) / (double) SEEDS);
        }
        double share = gone / (double) total;
        System.out.printf("BARRELS all buildings: %d barrels, %d forced, %.1f%% taken out over %d copies each%n",
                barrelsAll, forcedAll, 100 * share, SEEDS);
        assertTrue(Math.abs(share - Barrels.GONE) < 0.01, "share taken out " + share);
    }

    @Test
    void chestsOpenAndFaceAHorizontalSide() {
        for (Structures.Def d : Structures.ALL) {
            Template t = d.t();
            int layer = t.sx * t.sz;
            for (Map.Entry<Integer, int[]> e : d.barrels().entrySet()) {
                int[] b = e.getValue();
                String chest = Structures.STATES.get(b[Barrels.CHEST]);
                assertTrue(chest.startsWith("minecraft:chest[facing=") && !chest.contains("facing=up")
                        && !chest.contains("facing=down"), chest);
                if (b[Barrels.INDEX] + 1 < b[Barrels.HEIGHT]) {
                    continue;
                }
                // over the top of the stack: no full block (a chest does not open under one)
                int above = e.getKey() + layer;
                int p = above / layer >= t.sy ? -1 : t.at(above % t.sx, above / layer, (above / t.sx) % t.sz);
                if (p >= 0) {
                    String s = t.palette.get(p);
                    String bl = Structures.blockOf(s);
                    assertTrue(!Weathering.full(bl) || bl.endsWith("air") || bl.equals("minecraft:structure_void"),
                            d.name() + ": a chest under " + s);
                    assertFalse(bl.endsWith("_slab") && s.contains("type=double"), d.name() + ": a chest under " + s);
                }
            }
        }
    }

    @Test
    void theCodeOfACopyHasNoBarrelLeft() {
        for (Structures.Def d : Structures.ALL) {
            if (d.barrels().isEmpty()) {
                continue;
            }
            for (int rot = 0; rot < 4; rot++) {
                long seed = 1234 + rot;
                int barrels = 0;
                int chests = 0;
                int gone = 0;
                int drawn = 0;
                int kept = 0;
                int spawners = 0;
                for (int ry = -d.ground(); ry < d.above(); ry++) {
                    for (int rz = 0; rz < d.sizeZ(rot); rz++) {
                        for (int rx = 0; rx < d.sizeX(rot); rx++) {
                            int plain = d.code(rx, ry, rz, rot);
                            int copy = d.code(rx, ry, rz, rot, seed);
                            boolean wasBarrel = plain >= BlockCode.STRUCT && Structures.blockOf(
                                    Structures.STATES.get((plain - BlockCode.STRUCT) / 4)).equals("minecraft:barrel");
                            if (copy >= BlockCode.STRUCT) {
                                String s = Structures.STATES.get((copy - BlockCode.STRUCT) / 4);
                                barrels += Structures.blockOf(s).equals("minecraft:barrel") ? 1 : 0;
                            }
                            if (wasBarrel && copy == plain) {
                                // a barrel that holds something stays the barrel of the template
                                kept++;
                            } else if (wasBarrel) {
                                if (copy == BlockCode.AIR) {
                                    gone++;
                                } else {
                                    chests++;
                                    assertEquals(plain & 3, copy & 3, "same rotation");
                                }
                            } else if (plain == BlockCode.AIR && copy >= BlockCode.STRUCT && Structures.blockOf(
                                    Structures.STATES.get((copy - BlockCode.STRUCT) / 4)).equals("minecraft:chest")) {
                                // a chest drawn for this copy on a spot of its rooms (ChestDraw), not a barrel
                                drawn++;
                            } else if (plain == BlockCode.AIR && copy == BlockCode.STRUCT + 4 * Structures.SPAWNER_STATE
                                    + (copy & 3)) {
                                // a zombie spawner of this copy (SpawnerDraw), on a spot left by the chests
                                spawners++;
                            } else {
                                assertEquals(plain, copy, d.name() + ": only the barrels, the drawn chests and the"
                                        + " spawners change");
                            }
                        }
                    }
                }
                long forced = d.barrels().values().stream().filter(b -> b[Barrels.FORCED] == 1).count();
                assertEquals(forced, barrels, d.name() + ": only the barrels that hold something stay barrels");
                assertEquals(forced, kept, d.name());
                assertEquals(d.barrels().size(), gone + chests + kept, d.name());
                assertEquals(d.chests(seed).size(), drawn, d.name() + ": the drawn chests");
                assertEquals(d.spawners(seed).size(), spawners, d.name() + ": the spawners");
                assertEquals(SpawnerDraw.of(d, seed).size(), spawners, d.name() + ": the spawners drawn");
            }
        }
    }

    @Test
    void everyCopyOfABuildingIsDrawnApart() {
        // the copies of each building in the cities of the flat terrain: each with its own seed, and the copies differ
        WorldPlan p = WorldPlanTest.plan(WorldPlanTest.SEEDS[0], TestTerrain.Kind.FLAT);
        Map<String, Set<Long>> seeds = new HashMap<>();
        Map<String, Set<String>> kept = new HashMap<>();
        Map<String, Integer> copies = new HashMap<>();
        for (City c : WorldPlanTest.allCities(p)) {
            int n = c.gridHalf();
            for (int bj = -n; bj <= n; bj++) {
                for (int bi = -n; bi <= n; bi++) {
                    for (Lot l : c.lots(bi, bj)) {
                        if (l.building == null || l.building.barrels().size() < 20) {
                            continue;
                        }
                        String name = l.building.name();
                        int layer = l.building.t().sx * l.building.t().sz;
                        StringBuilder k = new StringBuilder();
                        for (Map.Entry<Integer, int[]> e : new java.util.TreeMap<>(l.building.barrels()).entrySet()) {
                            k.append(Barrels.kept(e.getValue(), l.seed, layer) ? '1' : '0');
                        }
                        copies.merge(name, 1, Integer::sum);
                        seeds.computeIfAbsent(name, x -> new HashSet<>()).add(l.seed);
                        kept.computeIfAbsent(name, x -> new HashSet<>()).add(k.toString());
                    }
                }
            }
        }
        assertFalse(copies.isEmpty());
        for (String name : copies.keySet()) {
            System.out.println("BARRELS copies of " + name + ": " + copies.get(name) + ", distinct seeds "
                    + seeds.get(name).size() + ", distinct sets of chests " + kept.get(name).size());
            assertEquals(copies.get(name), seeds.get(name).size(), name + ": two copies with one seed");
            assertEquals(copies.get(name), kept.get(name).size(), name + ": two copies with the same chests");
        }
    }

    @Test
    void barrelsByLayer() {
        // barrels on the ground layer (ry 0) would become city ground when taken out: there are none
        for (Structures.Def d : Structures.ALL) {
            if (d.barrels().isEmpty()) {
                continue;
            }
            Template t = d.t();
            int layer = t.sx * t.sz;
            int below = 0;
            int atGround = 0;
            for (int c : d.barrels().keySet()) {
                int ry = c / layer - d.oy() - d.ground();
                below += ry < 0 ? 1 : 0;
                atGround += ry == 0 ? 1 : 0;
            }
            System.out.println("BARRELS " + d.name() + ": " + below + " under the ground, " + atGround + " on the ground layer");
            assertEquals(0, atGround, d.name() + ": a barrel on the ground layer");
        }
    }

    @Test
    void theHardContainersDoNotChange() {
        // with every barrel taken out, or every barrel a chest, the containers hard to get to are the same (the hard
        // flags are worked out once, on the template with its barrels)
        for (Structures.Def d : Structures.ALL) {
            if (d.barrels().isEmpty()) {
                continue;
            }
            Set<Integer> cells = d.barrels().keySet();
            Set<Integer> others = new HashSet<>(d.access().hard());
            others.removeAll(cells);
            assertEquals(others, hard(d, cells, "minecraft:air"), d.name() + " without barrels");
            assertEquals(d.access().hard(),
                    hard(d, cells, "minecraft:chest[facing=north,type=single,waterlogged=false]"), d.name() + " chests");
        }
    }

    private static Set<Integer> hard(Structures.Def d, Set<Integer> cells, String to) {
        Map<Integer, String> changed = new HashMap<>();
        for (int c : cells) {
            changed.put(c, to);
        }
        Template v = d.t().withStates(changed);
        String[] placed = new String[v.palette.size()];
        for (int p = 0; p < placed.length; p++) {
            placed[p] = Structures.swap(v.palette.get(p));
        }
        return Access.of(v, placed, d.ox(), d.oy(), d.oz(), d.w(), d.h(), d.d(), d.ground()).hard();
    }
}
