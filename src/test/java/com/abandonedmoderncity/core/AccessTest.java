package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
            Template t = Template.of("test", sx, sy, sz, palette, cells);
            return Access.of(t, palette.toArray(new String[0]), 0, 0, 0, sx, sy, sz, 0);
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
    void highContainersAreHard() {
        Box tower = new Box(3, Access.HIGH + 3, 3);
        tower.set(1, Access.HIGH, 1, "minecraft:barrel").set(1, Access.HIGH - 1, 1, "minecraft:stone");
        for (int y = 1; y <= Access.HIGH; y++) {
            tower.set(1, y, 0, "minecraft:ladder[facing=north]");
        }
        tower.set(2, 1, 2, "minecraft:chest");
        Access.Result r = tower.access();
        assertEquals(2, r.containers());
        assertEquals(1, r.high());
        assertEquals(0, r.closed());
        assertEquals(1, r.hard().size());
    }

    @Test
    void structuresHardContainers() {
        assumeFalse(Structures.ALL.isEmpty(), "no structures");
        int containers = 0;
        int hard = 0;
        int houses = 0;
        int housesHard = 0;
        for (Structures.Def d : Structures.ALL) {
            Access.Result a = d.access();
            containers += a.containers();
            hard += a.hard().size();
            assertTrue(a.hard().size() <= a.containers(), d.name());
            assertTrue(a.hard().size() >= Math.max(a.high(), a.closed()), d.name());
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
                assertEquals(a.hard().size(), n, d.name() + " rot " + rot);
            }
            System.out.printf("[AccessTest] %-28s containers %3d, high %3d, closed %3d, hard %3d%n", d.name(),
                    a.containers(), a.high(), a.closed(), a.hard().size());
            if (d.type().startsWith("buildings_large") || d.type().startsWith("buildings_medium")) {
                assertTrue(a.high() > 0, d.name() + " has no container up high");
            }
            if (d.type().startsWith("house")) {
                houses += a.containers();
                housesHard += a.hard().size();
            }
        }
        System.out.println("[AccessTest] containers " + containers + ", hard to get to " + hard + "; houses "
                + houses + ", hard " + housesHard);
        // houses are low and open: nearly all their containers are reached on foot (the mansion's room 2 blocks over
        // its yard, with no step, is one of the few that are not)
        assertTrue(housesHard * 20 <= houses, housesHard + " of " + houses + " in houses");
        // the tall buildings (tower, tower verde, building da rampa, brutalista...) have most of theirs 15+ blocks up
        assertTrue(hard * 10 >= containers && hard * 2 <= containers, hard + " of " + containers);
    }

    @Test
    void everyStructureHasItsLootTables() throws IOException {
        assumeFalse(Structures.ALL.isEmpty(), "no structures");
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
        assertEquals("building", Structures.ALL.stream().filter(d -> d.name().equals("tower")).findFirst()
                .orElseThrow().lootTheme());
    }
}
