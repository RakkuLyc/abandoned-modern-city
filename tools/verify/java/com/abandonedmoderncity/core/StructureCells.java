package com.abandonedmoderncity.core;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Every cell of every structure in its 4 rotations, one line each: name, rotation, rx,ry,rz, the plain block code
 * ({@code Def.code}), the code of 3 copies (seeds 1, -7, 123456789: barrels and drawn chests), then H when the cell
 * is hard to get to and O when it is an opening; then the overhanging leaves. Run by {@code structure_cells.sh}
 * against a jar, so that two versions can be compared with {@code compare_structure_cells.py}.
 */
public final class StructureCells {
    private StructureCells() {
    }

    static final long[] SEEDS = {1L, -7L, 123456789L};

    static String s(int c) {
        if (c == BlockCode.AIR) {
            return "AIR";
        }
        if (c == BlockCode.KEEP) {
            return "KEEP";
        }
        if (c < BlockCode.STRUCT) {
            return "C" + c;
        }
        return Structures.STATES.get((c - BlockCode.STRUCT) / 4) + "/r" + ((c - BlockCode.STRUCT) & 3);
    }

    public static void main(String[] a) throws IOException {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(a[0])))) {
            for (Structures.Def d : Structures.ALL) {
                for (int rot = 0; rot < 4; rot++) {
                    for (int y = -d.ground(); y < d.above(); y++) {
                        for (int z = 0; z < d.sizeZ(rot); z++) {
                            for (int x = 0; x < d.sizeX(rot); x++) {
                                StringBuilder sb = new StringBuilder(d.name()).append(' ').append(rot).append(' ')
                                        .append(x).append(',').append(y).append(',').append(z).append(' ')
                                        .append(s(d.code(x, y, z, rot)));
                                for (long seed : SEEDS) {
                                    sb.append(' ').append(s(d.code(x, y, z, rot, seed)));
                                }
                                sb.append(d.hard(x, y, z, rot) ? " H" : "").append(d.opening(x, z, rot) ? " O" : "");
                                out.println(sb);
                            }
                        }
                    }
                }
                for (int rot = 0; rot < 4; rot++) {
                    for (int[] o : d.overhangs(rot)) {
                        out.println(d.name() + " over " + rot + " " + o[0] + "," + o[1] + "," + o[2] + " " + s(o[3]));
                    }
                }
            }
        }
    }
}
