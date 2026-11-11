package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A ring of structures along the four sidewalks of a block (the structures are drawn at random, no pattern). The rows
 * along the short sides of the block run corner to corner; those along the long sides fill what is left between
 * them. Every structure touches its sidewalk with its front turned to the street. Structures never touch (asked by
 * the user, 12 nov 2026): a gap of {@link #GAP_MIN}..{@link #GAP_MAX} blocks is drawn between two structures of a
 * row, and between the deepest structure of a short row and the long rows. The forest of the core always keeps
 * {@link #CORE_MIN}..{@link #CORE_MAX} blocks (drawn per pair of opposite rows) between the backs of opposite
 * structures. A structure that does not fit what is left of its row, or whose depth would not leave that room, is
 * redrawn.
 */
public final class Ring {
    private Ring() {
    }

    /** Free columns between two neighbouring structures (drawn). */
    public static final int GAP_MIN = 10;
    public static final int GAP_MAX = 19;
    /** Forest between the backs of the structures of two opposite rows (drawn per pair). */
    public static final int CORE_MIN = 20;
    public static final int CORE_MAX = 30;
    /** Draws of a structure for a row before the row is closed. */
    static final int TRIES = 40;

    /** The lots of the free rectangle x0..x1, z0..z1 of a block, with no spawn limit. */
    public static List<Lot> lots(int x0, int z0, int x1, int z1, long seed) {
        return lots(x0, z0, x1, z1, seed, new java.util.HashMap<>());
    }

    /**
     * The lots of the free rectangle x0..x1, z0..z1 of a block; {@code used} counts the structures of each limited
     * group ({@link Structures#LIMITS}) already placed in the city, and is updated (a group at its limit is redrawn).
     */
    public static List<Lot> lots(int x0, int z0, int x1, int z1, long seed, java.util.Map<String, Integer> used) {
        if (Structures.ALL.isEmpty()) {
            // no structures in resources/amcs/buildings: the block stays free city ground
            return List.of();
        }
        List<Lot> out = new ArrayList<>();
        int w = x1 - x0 + 1;
        int h = z1 - z0 + 1;
        // random order inside each pair of opposite rows: the one drawn first may be the deeper one
        boolean northFirst = Hash.unit(Hash.next(seed, 1)) < 0.5;
        boolean westFirst = Hash.unit(Hash.next(seed, 2)) < 0.5;
        // the rows along the short sides run corner to corner, those along the long sides fill what is left between
        // them (the other way round, deep structures on the long sides left the short sides almost always empty)
        boolean shortX = w <= h;
        int coreNS = Hash.range(Hash.next(seed, 3), CORE_MIN, CORE_MAX);
        int coreWE = Hash.range(Hash.next(seed, 4), CORE_MIN, CORE_MAX);
        int dN = 0;
        int dS = 0;
        int dW = 0;
        int dE = 0;
        // gaps between the short rows and the long rows that run between them (0 when the short row is empty)
        int gN = 0;
        int gS = 0;
        int gW = 0;
        int gE = 0;
        for (int pass = 0; pass < 2; pass++) {
            boolean ns = (pass == 0) == shortX;
            for (int k = 0; k < 2; k++) {
                long rs = Hash.next(seed, (ns ? 10 : 20) + k);
                if (ns) {
                    boolean north = (k == 0) == northFirst;
                    int other = north ? dS : dN;
                    int depth = row(out, x0 + dW + gW, x1 - dE - gE, z0, z1, north ? Structures.NORTH : Structures.SOUTH,
                            h - coreNS - Math.max(other, Structures.MIN_DEPTH), rs, used);
                    int g = depth > 0 ? Hash.range(Hash.next(rs, 7), GAP_MIN, GAP_MAX) : 0;
                    if (north) {
                        dN = depth;
                        gN = g;
                    } else {
                        dS = depth;
                        gS = g;
                    }
                } else {
                    boolean west = (k == 0) == westFirst;
                    int other = west ? dE : dW;
                    int depth = row(out, x0, x1, z0 + dN + gN, z1 - dS - gS, west ? Structures.WEST : Structures.EAST,
                            w - coreWE - Math.max(other, Structures.MIN_DEPTH), rs, used);
                    int g = depth > 0 ? Hash.range(Hash.next(rs, 7), GAP_MIN, GAP_MAX) : 0;
                    if (west) {
                        dW = depth;
                        gW = g;
                    } else {
                        dE = depth;
                        gE = g;
                    }
                }
            }
        }
        return out;
    }

    /**
     * One row along the {@code side} edge of the rectangle x0..x1, z0..z1 (for the west and east rows the z range is
     * the part between the north and south rows). Returns the depth of its deepest structure (0 for none).
     */
    private static int row(List<Lot> out, int x0, int x1, int z0, int z1, int side, int maxDepth, long seed,
                           java.util.Map<String, Integer> used) {
        boolean alongX = side == Structures.NORTH || side == Structures.SOUTH;
        int length = alongX ? x1 - x0 + 1 : z1 - z0 + 1;
        int at = 0;
        int deepest = 0;
        int failures = 0;
        long h = seed;
        while (length - at >= Structures.MIN_WIDTH && failures < TRIES) {
            h = Hash.next(h, 1);
            Structures.Def def = Structures.ALL.get(Hash.range(h, 0, Structures.ALL.size() - 1));
            boolean full = def.group() != null
                    && used.getOrDefault(def.group(), 0) >= Structures.LIMITS.get(def.group());
            if (full || def.depth() > maxDepth || def.front() > length - at) {
                failures++;
                continue;
            }
            if (def.group() != null) {
                used.merge(def.group(), 1, Integer::sum);
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
            at += def.front() + Hash.range(Hash.next(h, 3), GAP_MIN, GAP_MAX);
            deepest = Math.max(deepest, def.depth());
        }
        return deepest;
    }
}
