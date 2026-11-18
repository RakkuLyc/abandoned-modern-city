package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Containers hard to get to ({@link Access}) and the loot tables of the structures. */
class AccessTest {
    static final String[] TIERS = {"barrel", "barrel_hard", "chest", "chest_hard", "pot", "pot_hard"};

    /** A small template: a floor of stone at y = 0 (the ground layer), drawn layer by layer from y = 1. */
    private static final class Box {
        final int sx;
        final int sy;
        final int sz;
        final List<String> palette = new ArrayList<>();
        final int[] cells;

        Box(int sx, int sy, int sz) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            cells = new int[sx * sy * sz];
            Arrays.fill(cells, idx("minecraft:air"));
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    set(x, 0, z, "minecraft:stone");
                }
            }
        }

        int idx(String s) {
            int i = palette.indexOf(s);
            if (i < 0) {
                palette.add(s);
                i = palette.size() - 1;
            }
            return i;
        }

        Box set(int x, int y, int z, String s) {
            cells[x + sx * (z + sz * y)] = idx(s);
            return this;
        }

        /** Walls of stone around the whole box from y = 1 to {@code top}, and a roof over it. */
        Box room(int top) {
            for (int y = 1; y <= top + 1; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        if (y == top + 1 || x == 0 || z == 0 || x == sx - 1 || z == sz - 1) {
                            set(x, y, z, "minecraft:stone");
                        }
                    }
                }
            }
            return this;
        }

        Access.Result access() {
            return access(false);
        }

        /** With the attic rule ({@code attics}: a house) or without it. */
        Access.Result access(boolean attics) {
            Template t = Template.of("test", sx, sy, sz, palette, cells);
            return Access.of(t, palette.toArray(new String[0]), 0, 0, 0, sx, sy, sz, 0, attics);
        }

        /** A stone pillar at (x, z) from y = 1 to {@code top}, with a ladder on its north side up to top + 1. */
        Box pillar(int x, int z, int top) {
            for (int y = 1; y <= top; y++) {
                set(x, y, z, "minecraft:stone");
            }
            for (int y = 1; y <= top + 1; y++) {
                set(x, y, z - 1, "minecraft:ladder[facing=north]");
            }
            return this;
        }
    }

    @Test
    void closedRoomsDoorsAndLadders() {
        // a sealed room: the chest inside is hard to get to
        Box sealed = new Box(7, 6, 7).room(3).set(3, 1, 3, "minecraft:chest");
        assertEquals(1, sealed.access().closed());
        // an oak door in the wall opens: reached
        Box door = new Box(7, 6, 7).room(3).set(3, 1, 3, "minecraft:chest")
                .set(3, 1, 0, "minecraft:oak_door[half=lower]").set(3, 2, 0, "minecraft:oak_door[half=upper]");
        assertEquals(0, door.access().closed());
        assertTrue(door.access().hard().isEmpty());
        // an iron door does not open without redstone
        Box iron = new Box(7, 6, 7).room(3).set(3, 1, 3, "minecraft:chest")
                .set(3, 1, 0, "minecraft:iron_door[half=lower]").set(3, 2, 0, "minecraft:iron_door[half=upper]");
        assertEquals(1, iron.access().closed());
        // a fence is no door: no one jumps over it
        Box fence = new Box(7, 6, 7).room(3).set(3, 1, 3, "minecraft:chest").set(3, 1, 0, "minecraft:oak_fence")
                .set(3, 2, 0, "minecraft:air");
        assertEquals(1, fence.access().closed());
        // a chest on a pillar 4 high: reached only with a ladder up the pillar
        Box pillar = new Box(5, 8, 5);
        for (int y = 1; y <= 4; y++) {
            pillar.set(2, y, 2, "minecraft:stone");
        }
        pillar.set(2, 5, 2, "minecraft:chest");
        assertEquals(1, pillar.access().closed());
        for (int y = 1; y <= 5; y++) {
            pillar.set(2, y, 1, "minecraft:ladder[facing=north]");
        }
        assertEquals(0, pillar.access().closed());
        // stairs of single blocks up to it: one jump at a time
        Box steps = new Box(7, 8, 3);
        for (int k = 1; k <= 4; k++) {
            for (int y = 1; y <= k; y++) {
                steps.set(k, y, 1, "minecraft:stone");
            }
        }
        steps.set(5, 5, 1, "minecraft:chest");
        assertEquals(0, steps.access().closed());
    }

    @Test
    void heightAloneDoesNotMakeAContainerHard() {
        // a barrel 20 blocks up, reached by a ladder: not hard (the height rule was removed); the box goes 9 blocks
        // over it, so it is not on the terrace either (at most Access.TERRACE_TOP under the top)
        Box tower = new Box(3, 30, 3);
        tower.set(1, 20, 1, "minecraft:barrel").set(1, 19, 1, "minecraft:stone");
        for (int y = 1; y <= 20; y++) {
            tower.set(1, y, 0, "minecraft:ladder[facing=north]");
        }
        tower.set(2, 1, 2, "minecraft:chest");
        Access.Result r = tower.access();
        assertEquals(2, r.containers());
        assertEquals(0, r.closed());
        assertTrue(r.hard().isEmpty());
    }

    @Test
    void theChestInTheTreeOfTheModernHouseIsHard() {
        // reached by jumping up the leaves, but listed as hard by hand (index.txt, column hard)
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        Structures.Def d = Structures.ALL.stream().filter(s -> s.name().equals("abandoned_modern_house")).findFirst()
                .orElseThrow();
        assertEquals(1, d.access().listed());
        assertEquals(0, d.access().closed());
        assertTrue(d.hard(24, 12, 11, 0), "the chest in the tree");
    }

    @Test
    void undergroundTerraceAndAttic() {
        // underground: a chest in the ground layer, reached from the street, is hard
        Access.Result under = new Box(5, 6, 5).set(2, 0, 2, "minecraft:chest").access();
        assertEquals(0, under.closed());
        assertEquals(1, under.underground());
        assertEquals(1, under.hard().size());
        // terrace: a chest 6 up with open sky, 3 under the top of the structure, reached by a ladder: hard
        Access.Result roof = new Box(5, 10, 5).pillar(2, 2, 5).set(2, 6, 2, "minecraft:chest").access();
        assertEquals(0, roof.closed());
        assertEquals(1, roof.terrace());
        assertEquals(1, roof.hard().size());
        // the same chest in a structure 19 high (13 under its top): a lower roof, not the terrace
        assertTrue(new Box(5, 20, 5).pillar(2, 2, 5).set(2, 6, 2, "minecraft:chest").access().hard().isEmpty());
        // a chest in the yard (1 over the ground, open sky): not the terrace
        assertTrue(new Box(5, 4, 5).set(2, 1, 2, "minecraft:chest").access().hard().isEmpty());
        // attic: a chest 5 up under one roof (a block 2 over it), only in a house
        Box attic = new Box(5, 10, 5).pillar(2, 2, 4).set(2, 5, 2, "minecraft:chest").set(2, 7, 2, "minecraft:stone");
        assertEquals(1, attic.access(true).attic());
        assertEquals(1, attic.access(true).hard().size());
        assertTrue(attic.access(false).hard().isEmpty(), "not a house: no attic rule");
        // two ceilings over it (a floor, then the roof): not the attic
        attic.set(2, 9, 2, "minecraft:stone");
        assertTrue(attic.access(true).hard().isEmpty(), "a floor over it");
        // under one roof but on the ground floor (2 over the ground): not the attic
        Box low = new Box(5, 6, 5).pillar(2, 2, 1).set(2, 2, 2, "minecraft:chest").set(2, 4, 2, "minecraft:stone");
        assertTrue(low.access(true).hard().isEmpty(), "ground floor");
    }

    @Test
    void structuresHardContainers() {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        int containers = 0;
        int hard = 0;
        int houses = 0;
        int housesHard = 0;
        for (Structures.Def d : Structures.ALL) {
            Access.Result a = d.access();
            containers += a.containers();
            hard += a.hard().size();
            assertTrue(a.hard().size() <= a.containers(), d.name());
            int parts = a.closed() + a.listed() + a.underground() + a.terrace() + a.attic();
            assertTrue(a.hard().size() <= parts && a.hard().size() >= Math.max(a.closed(), a.underground()), d.name());
            if (!d.category().equals("house")) {
                assertEquals(0, a.attic(), d.name() + ": the attic rule is only for the houses");
            }
            // the same containers are hard to get to in every rotation of the box
            for (int rot = 0; rot < 4; rot++) {
                int n = 0;
                for (int y = -d.ground(); y < d.above(); y++) {
                    for (int z = 0; z < d.sizeZ(rot); z++) {
                        for (int x = 0; x < d.sizeX(rot); x++) {
                            n += d.hard(x, y, z, rot) ? 1 : 0;
                        }
                    }
                }
                // the containers of the template, and the spots of the drawn chests hard to get to (Def.hard)
                assertEquals(a.hard().size() + d.hardSpots().size(), n, d.name() + " rot " + rot);
            }
            System.out.printf("[AccessTest] %-32s containers %3d, out of reach %3d, listed %d, underground %2d, terrace %d,"
                    + " attic %2d, hard %3d%n", d.name(), a.containers(), a.closed(), a.listed(), a.underground(),
                    a.terrace(), a.attic(), a.hard().size());
            if (d.type().startsWith("house")) {
                houses += a.containers();
                housesHard += a.hard().size();
            }
        }
        System.out.println("[AccessTest] containers " + containers + ", hard to get to " + hard + "; houses "
                + houses + ", hard " + housesHard);
        // houses: their attics make about a fifth of their containers hard
        assertTrue(housesHard * 3 <= houses && housesHard * 10 >= houses, housesHard + " of " + houses + " in houses");
        // closed rooms, the hand list, underground, terraces and the attics of the houses: 125 of 2,550 (about 5%; the
        // chests saved in the buildings are taken out)
        assertTrue(hard * 50 >= containers && hard * 20 <= containers, hard + " of " + containers);
        for (String tall : List.of("tower", "abandoned_green_tower", "brutalist_u_building_75x58")) {
            assertEquals(0, Structures.ALL.stream().filter(d -> d.name().equals(tall)).findFirst().orElseThrow()
                    .access().hard().size(), tall + ": its floors are reached by its stairs");
        }
    }

    @Test
    void everyStructureHasItsLootTables() throws IOException {
        assertFalse(Structures.ALL.isEmpty(), "no structures loaded");
        for (Structures.Def d : Structures.ALL) {
            for (String tier : TIERS) {
                String path = "/data/abandonedmoderncity/loot_table/chests/" + d.lootTheme() + "/" + tier + ".json";
                try (InputStream in = AccessTest.class.getResourceAsStream(path)) {
                    assertNotNull(in, path);
                    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    // barrels and pots are often empty (40%, 75% and 80% have anything); a chest always has items
                    String chance = switch (tier) {
                        case "barrel", "pot" -> "\"chance\": 0.4";
                        case "barrel_hard" -> "\"chance\": 0.75";
                        case "pot_hard" -> "\"chance\": 0.8";
                        default -> null;
                    };
                    if (chance != null) {
                        assertTrue(json.contains(chance), path + " " + chance);
                    } else {
                        assertFalse(json.contains("\"chance\": 0.4") || json.contains("\"chance\": 0.75"), path);
                    }
                    assertFalse(json.contains("lostcities") || json.contains("oamp"), path);
                }
            }
        }
        assertEquals("workshop", Structures.ALL.stream().filter(d -> d.type().startsWith("workshop")).findFirst()
                .orElseThrow().lootTheme());
        assertEquals("building", Structures.ALL.stream().filter(d -> d.name().equals("apocalyptic_building_57x45"))
                .findFirst().orElseThrow().lootTheme());
        // the police station: its own theme (same vanilla tables as building), for add-ons
        assertEquals(Set.of("abandoned_police_station"), Structures.ALL.stream()
                .filter(d -> d.lootTheme().equals("police_station")).map(Structures.Def::name).collect(Collectors.toSet()));
        // every building and the special one use building, whatever their number of containers (the theme
        // building_large of the buildings with 100+ containers was taken out)
        for (Structures.Def d : Structures.ALL) {
            if (d.category().startsWith("building_") || d.category().equals("special")) {
                assertEquals("building", d.lootTheme(), d.name());
            }
        }
        assertTrue(Structures.ALL.stream().noneMatch(d -> d.lootTheme().equals("building_large")));
        assertFalse(AccessTest.class.getResource("/data/abandonedmoderncity/loot_table/chests/building_large/chest.json")
                != null, "the building_large tables are gone");
    }
}
