package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/** cave_air in a template is dug out even under the city ground (underground garages); plain air stays ground. */
class StructuresCarveTest {
    @Test
    void caveAirIsDugUnderTheGround() {
        // 3 x 3 x 3: stone floor (y 0), a layer of cave_air with plain air in the middle (y 1), stone at the ground (y 2)
        List<String> pal = List.of("minecraft:stone", "minecraft:cave_air", "minecraft:air");
        int[] cells = new int[27];
        for (int z = 0; z < 3; z++) {
            for (int x = 0; x < 3; x++) {
                cells[x + 3 * (z + 3 * 1)] = x == 1 && z == 1 ? 2 : 1;
            }
        }
        Template t = Template.of("teste_subsolo", 3, 3, 3, pal, cells);
        // ground = 2: layer 2 lies at the city ground, layer 1 is one deep, layer 0 two deep
        Structures.Def d = Structures.of("teste", "teste_subsolo", t, 2, 0, "teste_subsolo", new HashMap<>());
        for (int z = 0; z < 3; z++) {
            for (int x = 0; x < 3; x++) {
                int want = x == 1 && z == 1 ? BlockCode.KEEP : BlockCode.AIR;
                assertEquals(want, d.code(x, -1, z, 0), "cell " + x + "," + z + " one deep");
                assertTrue(d.code(x, -2, z, 0) >= BlockCode.STRUCT, "stone floor two deep");
                assertTrue(d.code(x, 0, z, 0) >= BlockCode.STRUCT, "stone at the ground");
            }
        }
    }
}
