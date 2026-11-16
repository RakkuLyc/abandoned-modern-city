package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The chests saved in the templates of the buildings (types {@code buildings_*}) are taken out when they load
 * ({@link Structures#withoutChests}, asked by the user on 15 Nov 2026, the first step of the procedural chests); every
 * other structure, the barrels and the park fountain keep theirs.
 */
class BuildingChestsTest {
    /** Chests saved in each building's .nbt (all single, none trapped). */
    static final Map<String, Integer> SAVED = Map.of("apartment_building", 10, "skeleton_building_31x29", 16,
            "bluish_building", 15, "tower", 29, "apocalyptic_building_57x45", 36, "abandoned_small_building", 19,
            "brutalist_u_building_75x58", 168, "abandoned_wide_building", 133, "abandoned_green_tower", 62,
            "abandoned_crash_building", 68);

    private static boolean building(Structures.Def d) {
        return d.type().startsWith("buildings_");
    }

    /** The overgrowth seed of each structure (column {@code seed} of index.txt). */
    private static Map<String, Integer> seeds() throws IOException {
        Map<String, Integer> out = new HashMap<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                Structures.class.getResourceAsStream("/amcs/buildings/index.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.trim().split("\\|");
                out.put(f[1], f.length > 7 && !f[7].isBlank() ? Integer.parseInt(f[7].trim()) : f[1].hashCode());
            }
        }
        return out;
    }

    @Test
    void onlyTheBuildingsLoseTheirSavedChests() {
        int removed = 0;
        int buildings = 0;
        for (Structures.Def d : Structures.ALL) {
            int saved = Structures.chestsIn(Structures.source(d.name()));
            if (building(d)) {
                buildings++;
                assertEquals(SAVED.get(d.name()), saved, d.name() + ": chests in the .nbt");
                assertEquals(0, Structures.chestsIn(d.t()), d.name() + ": a chest left in the loaded template");
                assertEquals(saved, Structures.CHESTS_REMOVED.get(d.name()), d.name());
                removed += saved;
            } else {
                // the overgrowth never touches a container: the loaded template has the chests of the .nbt
                assertEquals(saved, Structures.chestsIn(d.t()), d.name() + " is not a building");
                assertFalse(Structures.CHESTS_REMOVED.containsKey(d.name()), d.name());
            }
        }
        assertEquals(10, buildings);
        assertEquals(556, removed);
        assertEquals(SAVED.keySet(), Structures.CHESTS_REMOVED.keySet());
        System.out.println("[BuildingChestsTest] chests removed from the buildings: " + removed + " "
                + new LinkedHashMap<>(Structures.CHESTS_REMOVED));
    }

    @Test
    void noBuildingChestStoodAtOrUnderItsGroundLayer() {
        // so each one becomes plain air above the ground, not city ground nor dug-out space
        for (Structures.Def d : Structures.ALL) {
            if (!building(d)) {
                continue;
            }
            Template t = Structures.source(d.name());
            int gy = d.oy() + d.ground();
            for (int y = 0; y <= gy; y++) {
                for (int z = 0; z < t.sz; z++) {
                    for (int x = 0; x < t.sx; x++) {
                        int p = t.at(x, y, z);
                        assertFalse(p >= 0 && Structures.isChestName(Structures.blockOf(t.palette.get(p))),
                                d.name() + ": chest at " + x + "," + y + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void theOnlyChestsOfABuildingAreItsBarrelsTurnedIntoChests() {
        for (Structures.Def d : Structures.ALL) {
            if (!building(d)) {
                continue;
            }
            for (int rot = 0; rot < 4; rot++) {
                for (int y = -d.ground(); y < d.above(); y++) {
                    for (int z = 0; z < d.sizeZ(rot); z++) {
                        for (int x = 0; x < d.sizeX(rot); x++) {
                            int c = d.code(x, y, z, rot);
                            assertFalse(c >= BlockCode.STRUCT && Structures.isChestName(Structures.blockOf(
                                    Structures.STATES.get((c - BlockCode.STRUCT) / 4))),
                                    d.name() + ": chest at " + x + "," + y + "," + z + " rot " + rot);
                        }
                    }
                }
            }
        }
    }

    @Test
    void inACopyTheChestsAreItsBarrelsAndItsDrawnChests() {
        // with a seed: every chest of a building is a barrel turned into a chest or one drawn on a spot (ChestDraw)
        for (Structures.Def d : Structures.ALL) {
            if (!building(d)) {
                continue;
            }
            for (int rot = 0; rot < 4; rot++) {
                long seed = 77 + 1000L * rot;
                int barrelChests = 0;
                int drawn = 0;
                for (int y = -d.ground(); y < d.above(); y++) {
                    for (int z = 0; z < d.sizeZ(rot); z++) {
                        for (int x = 0; x < d.sizeX(rot); x++) {
                            int c = d.code(x, y, z, rot, seed);
                            if (c < BlockCode.STRUCT || !Structures.isChestName(Structures.blockOf(
                                    Structures.STATES.get((c - BlockCode.STRUCT) / 4)))) {
                                continue;
                            }
                            int plain = d.code(x, y, z, rot);
                            if (plain == BlockCode.AIR) {
                                drawn++;
                            } else {
                                assertTrue(Structures.blockOf(Structures.STATES.get((plain - BlockCode.STRUCT) / 4))
                                        .equals("minecraft:barrel"), d.name() + ": a chest that was not a barrel");
                                barrelChests++;
                            }
                        }
                    }
                }
                assertEquals(d.chests(seed).size(), drawn, d.name() + " rot " + rot);
                int layer = d.t().sx * d.t().sz;
                // the barrels that hold something stay barrels; the others kept become chests
                long kept = d.barrels().values().stream()
                        .filter(b -> b[Barrels.FORCED] == 0 && Barrels.kept(b, seed, layer)).count();
                assertEquals(kept, barrelChests, d.name() + " rot " + rot);
            }
        }
    }

    @Test
    void theOtherContainersKeepTheirAccessAndTheBuildingItsShape() throws IOException {
        // Access, box and barrels as they were with the chests of the .nbt (overgrowth applied as when loading)
        Map<String, Integer> seeds = seeds();
        boolean weathering = Weathering.enabled;
        for (Structures.Def d : Structures.ALL) {
            if (!building(d)) {
                continue;
            }
            Template t = Structures.source(d.name());
            if (weathering) {
                t = Weathering.apply(t, d.ground(), seeds.get(d.name())).template();
            }
            String[] placed = new String[t.palette.size()];
            for (int p = 0; p < placed.length; p++) {
                placed[p] = Structures.swap(t.palette.get(p));
            }
            Access.Result before = Access.of(t, placed, d.ox(), d.oy(), d.oz(), d.w(), d.h(), d.d(), d.ground());
            Set<Integer> hardOthers = new HashSet<>();
            for (int c : before.hard()) {
                int x = c % t.sx;
                int z = (c / t.sx) % t.sz;
                int y = c / (t.sx * t.sz);
                if (!Structures.isChestName(Structures.blockOf(placed[t.at(x, y, z)]))) {
                    hardOthers.add(c);
                }
            }
            assertEquals(hardOthers, d.access().hard(), d.name() + ": hard containers other than the chests");
            assertEquals(before.containers() - SAVED.get(d.name()), d.access().containers(), d.name());
            // the barrels exactly as before, the facing of the chests they turn into included
            Map<String, Integer> ids = new LinkedHashMap<>();
            Map<Integer, int[]> barrels = Barrels.of(t, ids);
            java.util.List<String> idStates = new java.util.ArrayList<>(ids.keySet());
            assertEquals(barrels.keySet(), d.barrels().keySet(), d.name());
            for (Map.Entry<Integer, int[]> e : barrels.entrySet()) {
                int[] a = e.getValue();
                int[] b = d.barrels().get(e.getKey());
                assertEquals(idStates.get(a[Barrels.CHEST]), Structures.STATES.get(b[Barrels.CHEST]), d.name());
                for (int i = 1; i < a.length; i++) {
                    assertEquals(a[i], b[i], d.name() + " barrel field " + i);
                }
            }
            // the same box: the ring places the building as before
            Structures.Def with = Structures.of("houses", d.name(), t, d.ground(), d.turn(), d.model(), new HashMap<>());
            assertEquals(with.w() + "x" + with.h() + "x" + with.d() + "@" + with.ox() + "," + with.oy() + "," + with.oz(),
                    d.w() + "x" + d.h() + "x" + d.d() + "@" + d.ox() + "," + d.oy() + "," + d.oz(), d.name());
        }
    }

    @Test
    void theFountainKeepsItsChest() {
        // the park fountain is not a structure of the index: its template keeps its one chest
        assertEquals(1, Structures.chestsIn(Template.WATERFALL));
    }

    @Test
    void aSmallBuildingTemplateLosesItsChestsAndKeepsItsBarrels() {
        // a 3 x 3 x 3 box: a floor of stone, a chest and a barrel on it
        java.util.List<String> palette = java.util.List.of("minecraft:stone", "minecraft:air",
                "minecraft:chest[facing=north,type=single,waterlogged=false]", "minecraft:barrel[facing=up,open=false]");
        int[] cells = new int[27];
        java.util.Arrays.fill(cells, 1);
        for (int i = 0; i < 9; i++) {
            cells[i] = 0;
        }
        cells[9] = 2;
        cells[11] = 3;
        Template t = Template.of("test", 3, 3, 3, palette, cells);
        Structures.Def b = Structures.of("buildings_small", "test", t, 0, 0, "test", new HashMap<>());
        Structures.Def h = Structures.of("houses", "test", t, 0, 0, "test", new HashMap<>());
        assertEquals(0, Structures.chestsIn(b.t()));
        assertEquals(1, Structures.chestsIn(h.t()));
        assertEquals(1, b.barrels().size());
        assertTrue(h.barrels().isEmpty());
        assertEquals(1, b.access().containers());
        assertEquals(2, h.access().containers());
    }
}
