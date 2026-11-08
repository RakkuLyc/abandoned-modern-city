package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A ring of structures along the four sidewalks of a block (prototype: the structures are drawn at random, no pattern).
 * The north and south rows run along the whole width; the west and east rows fill what is left between them. Every
 * structure touches its sidewalk with its front (the -Z side of the template) turned to the street. Rows are filled
 * left to right (or top to bottom) with a {@link #GAP} between structures; a structure that does not fit in what is
 * left of its row, or whose depth would not leave room for the opposite row, is redrawn.
 */
public final class Ring {
    private Ring() {
    }

    /** Free columns between two structures of a row. */
    public static final int GAP = 2;
    /** Draws of a structure for a row before the row is closed. */
    static final int TRIES = 40;

    /** The lots of the free rectangle x0..x1, z0..z1 of a block. */
    public static List<Lot> lots(int x0, int z0, int x1, int z1, long seed) {
        List<Lot> out = new ArrayList<>();
        int w = x1 - x0 + 1;
        int h = z1 - z0 + 1;
        // random order inside each pair of opposite rows: the one drawn first may be the deeper one
        boolean northFirst = Hash.unit(Hash.next(seed, 1)) < 0.5;
        boolean westFirst = Hash.unit(Hash.next(seed, 2)) < 0.5;
        int dN = 0;
        int dS = 0;
        for (int k = 0; k < 2; k++) {
            boolean north = (k == 0) == northFirst;
            int other = north ? dS : dN;
            int depth = row(out, x0, x1, z0, z1, north ? Structures.NORTH : Structures.SOUTH, h - Math.max(other, Structures.MIN_DEPTH),
                    Hash.next(seed, 10 + k));
            if (north) {
                dN = depth;
            } else {
                dS = depth;
            }
        }
        int zs0 = z0 + dN;
        int zs1 = z1 - dS;
        int dW = 0;
        int dE = 0;
        for (int k = 0; k < 2; k++) {
            boolean west = (k == 0) == westFirst;
            int other = west ? dE : dW;
            int depth = row(out, x0, x1, zs0, zs1, west ? Structures.WEST : Structures.EAST, w - Math.max(other, Structures.MIN_DEPTH),
                    Hash.next(seed, 20 + k));
            if (west) {
                dW = depth;
            } else {
                dE = depth;
            }
        }
        return out;
    }

    /**
     * One row along the {@code side} edge of the rectangle x0..x1, z0..z1 (for the west and east rows the z range is
     * the part between the north and south rows). Returns the depth of its deepest structure (0 for none).
     */
    private static int row(List<Lot> out, int x0, int x1, int z0, int z1, int side, int maxDepth, long seed) {
        boolean alongX = side == Structures.NORTH || side == Structures.SOUTH;
        int length = alongX ? x1 - x0 + 1 : z1 - z0 + 1;
        int at = 0;
        int deepest = 0;
        int failures = 0;
        long h = seed;
        while (length - at >= Structures.MIN_WIDTH && failures < TRIES) {
            h = Hash.next(h, 1);
            Structures.Def def = Structures.ALL.get(Hash.range(h, 0, Structures.ALL.size() - 1));
            if (def.d() > maxDepth || def.w() > length - at) {
                failures++;
                continue;
            }
            int rot = Structures.rotationFor(side);
            int sx = def.sizeX(rot);
            int sz = def.sizeZ(rot);
            int x = switch (side) {
                case Structures.NORTH, Structures.SOUTH -> x0 + at;
                case Structures.WEST -> x0;
                default -> x1 - sx + 1;
            };
            int z = switch (side) {
                case Structures.NORTH -> z0;
                case Structures.SOUTH -> z1 - sz + 1;
                default -> z0 + at;
            };
            out.add(Lot.building(x, z, def, rot, Hash.next(h, 2)));
            at += def.w() + GAP;
            deepest = Math.max(deepest, def.d());
        }
        return deepest;
    }
}
