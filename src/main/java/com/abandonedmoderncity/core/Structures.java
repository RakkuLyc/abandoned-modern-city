package com.abandonedmoderncity.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The building structures supplied by the user ({@code structures/}, packed in {@code resources/amcs/buildings/} with
 * an {@code index.txt}): their trimmed boxes, their rotation and the block code of each cell. The front of every
 * structure is its -Z side (north); {@link #rotationFor} turns it to face the street.
 * <p>
 * Block code of a structure block: {@code BlockCode.STRUCT + 4 * state + rotation}; the game layer turns it into a
 * block state (rotated by {@code rotation} quarter turns clockwise).
 */
public final class Structures {
    private Structures() {
    }

    /**
     * One structure. The box (w x h x d) is the box of the non-air blocks of the template, so a template with empty
     * margins still touches the sidewalk; its floor layer is placed at the city ground.
     *
     * @param type  the folder it came from (farmacia, posto_de_gasolina_padrao, ...)
     * @param name  the file name
     * @param w     size along x before rotation (the width of the front)
     * @param h     height
     * @param d     size along z before rotation (the depth, from the front to the back)
     */
    public record Def(String type, String name, Template t, int ox, int oy, int oz, int w, int h, int d, int[] state) {
        /** Footprint size along x after {@code rot} quarter turns. */
        public int sizeX(int rot) {
            return (rot & 1) == 0 ? w : d;
        }

        /** Footprint size along z after {@code rot} quarter turns. */
        public int sizeZ(int rot) {
            return (rot & 1) == 0 ? d : w;
        }

        /**
         * Block code at (rx, ry, rz) of the rotated box: {@link BlockCode#AIR}, {@link BlockCode#KEEP} (nothing in the
         * template, or a structure void) or a structure block.
         */
        public int code(int rx, int ry, int rz, int rot) {
            if (rx < 0 || rz < 0 || ry < 0 || ry >= h || rx >= sizeX(rot) || rz >= sizeZ(rot)) {
                return BlockCode.KEEP;
            }
            int x;
            int z;
            switch (rot & 3) {
                case 0 -> {
                    x = rx;
                    z = rz;
                }
                case 1 -> {
                    x = rz;
                    z = d - 1 - rx;
                }
                case 2 -> {
                    x = w - 1 - rx;
                    z = d - 1 - rz;
                }
                default -> {
                    x = w - 1 - rz;
                    z = rx;
                }
            }
            int p = t.at(ox + x, oy + ry, oz + z);
            if (p < 0) {
                return BlockCode.KEEP;
            }
            int s = state[p];
            return s < 0 ? s == AIR_STATE ? BlockCode.AIR : BlockCode.KEEP : BlockCode.STRUCT + 4 * s + (rot & 3);
        }
    }

    private static final int AIR_STATE = -2;
    /** Distinct block state strings of all structures; a structure block code points into this list. */
    public static final List<String> STATES = new ArrayList<>();
    public static final List<Def> ALL;
    /** Smallest depth and front width among the structures (what still fits in a row). */
    public static final int MIN_DEPTH;
    public static final int MIN_WIDTH;

    static {
        Map<String, Integer> ids = new LinkedHashMap<>();
        List<Def> defs = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                Structures.class.getResourceAsStream("/amcs/buildings/index.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.trim().split("\\|");
                defs.add(define(f[0], f[1], ids));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        STATES.addAll(ids.keySet());
        ALL = List.copyOf(defs);
        MIN_DEPTH = ALL.stream().mapToInt(Def::d).min().orElse(0);
        MIN_WIDTH = ALL.stream().mapToInt(Def::w).min().orElse(0);
    }

    private static Def define(String type, String name, Map<String, Integer> ids) {
        Template t = Template.load("buildings/" + name);
        int[] state = new int[t.palette.size()];
        boolean[] solid = new boolean[state.length];
        for (int p = 0; p < state.length; p++) {
            String s = t.palette.get(p);
            String block = s.contains("[") ? s.substring(0, s.indexOf('[')) : s;
            if (block.equals("minecraft:air") || block.equals("minecraft:cave_air") || block.equals("minecraft:void_air")) {
                state[p] = AIR_STATE;
            } else if (block.equals("minecraft:structure_void")) {
                state[p] = -1;
            } else {
                final int next = ids.size();
                state[p] = ids.computeIfAbsent(s, k -> next);
                solid[p] = true;
            }
        }
        int x0 = t.sx;
        int y0 = t.sy;
        int z0 = t.sz;
        int x1 = -1;
        int y1 = -1;
        int z1 = -1;
        for (int y = 0; y < t.sy; y++) {
            for (int z = 0; z < t.sz; z++) {
                for (int x = 0; x < t.sx; x++) {
                    int p = t.at(x, y, z);
                    if (p >= 0 && solid[p]) {
                        x0 = Math.min(x0, x);
                        x1 = Math.max(x1, x);
                        y0 = Math.min(y0, y);
                        y1 = Math.max(y1, y);
                        z0 = Math.min(z0, z);
                        z1 = Math.max(z1, z);
                    }
                }
            }
        }
        if (x1 < 0) {
            throw new IllegalStateException("structure " + name + " is empty");
        }
        return new Def(type, name, t, x0, y0, z0, x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1, state);
    }

    /** Quarter turns clockwise that turn the front (-Z) to face the street on the given side. */
    public static final int NORTH = 0;
    public static final int EAST = 1;
    public static final int SOUTH = 2;
    public static final int WEST = 3;

    /** Rotation of a structure standing on the {@code side} edge of a block (the street is on that side). */
    public static int rotationFor(int side) {
        return side & 3;
    }
}
