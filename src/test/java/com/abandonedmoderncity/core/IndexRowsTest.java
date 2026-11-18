package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A malformed line of index.txt stops the loading with its number and the field that is wrong. */
class IndexRowsTest {

    @Test
    void goodRowsAreSplitAtTheBars() throws IOException {
        List<String[]> rows = IndexRows.read(new BufferedReader(new StringReader(
                "# header\n\nhouses|abandoned_house|0|||||-1087930116\n"
                        + "pharmacy|pharmacy_blue|0||pharmacy|pharmacy_red@0,0:red=blue|pharmacy_red|5|1,2,3;4,5,6\n")));
        assertEquals(2, rows.size());
        assertEquals("abandoned_house", rows.get(0)[1]);
        assertEquals("1,2,3;4,5,6", rows.get(1)[8]);
    }

    @Test
    void aMalformedLineNamesItsNumberAndField() {
        String[][] cases = {
                {"houses", "needs at least a type and a file name"},
                {"houses|prédio|0", "the file name may only use"},
                {"houses|a|zero", "ground 'zero' is not a whole number"},
                {"houses|a|0|ninety", "front 'ninety'"},
                {"houses|a|0|||src@1:red=blue", "banners 'src@1:red=blue' is not"},
                {"houses|a|0||||||1,2", "hard cell '1,2' is not x,y,z"},
                {"houses|a|0|||||seed", "seed 'seed'"},
        };
        for (String[] c : cases) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> IndexRows.read(new BufferedReader(new StringReader("# header\n" + c[0] + "\n"))), c[0]);
            assertTrue(e.getMessage().startsWith("amcs/buildings/index.txt line 2 ("), e.getMessage());
            assertTrue(e.getMessage().contains(c[1]), e.getMessage());
        }
    }
}
