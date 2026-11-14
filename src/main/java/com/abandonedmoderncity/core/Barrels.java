package com.abandonedmoderncity.core;

import java.util.HashMap;
import java.util.Map;

/**
 * The barrels of the buildings (the {@code building} category of {@code /amcs spawn}, types {@code buildings_*}): in
 * each copy, {@link #GONE} of them are taken out (air) and the others become chests (asked by the user after 13 Nov
 * 2026), {@link #GONE} of each building's barrels on average. The draw is per copy: a pure function of the lot's seed and the barrel's cell in the template, so the chunk
 * builder, the neighbour repair, the audit and the fingerprint agree.
 * <ul>
 *   <li>A barrel that holds something (a block standing on the top of its stack, such as a slab or a pot; something
 *       hanging under it; or a block fixed to one of its faces: torch, sign, banner, ladder, button, lever, a vine or a
 *       lichen holding on it) is never taken out: it always becomes a chest. A vine near a barrel but held by a wall
 *       or by the vine over it does not count.</li>
 *   <li>In a stack of barrels (one over the other), the barrels taken out are always the top ones: the number kept
 *       is drawn barrel by barrel, and they are the bottom ones, so nothing floats.</li>
 *   <li>The chest faces the way the barrel's opening faced; a barrel facing up or down gives a chest facing the first
 *       side (north, east, south, west) with air in front of it.</li>
 * </ul>
 */
public final class Barrels {
    private Barrels() {
    }

    /** Share of the barrels taken out of each copy. */
    public static final double GONE = 0.70;
    private static final long SALT = 0x4241524CL; // "BARL"

    /** Index of the fields of a barrel entry ({@link #of}). */
    static final int CHEST = 0;
    static final int FORCED = 1;
    static final int BOTTOM = 2;
    static final int INDEX = 3;
    static final int HEIGHT = 4;
    /** Chance (in millionths) of a barrel not forced to be taken out: {@link #GONE} of all the building's barrels. */
    static final int CHANCE = 5;

    /** Blocks fixed by their {@code facing} to the block behind them (facing away from it). */
    private static final String[] FACING_FIXED = {"wall_torch", "wall_sign", "wall_banner", "wall_head", "wall_skull",
            "ladder", "tripwire_hook"};
    /** Blocks that hold on one face or more ({@code north=true}...: the face touching the block that holds it). */
    private static final String[] FACES = {"minecraft:vine", "minecraft:glow_lichen", "minecraft:sculk_vein"};
    /** Blocks hanging from the block over them. */
    private static final String[] HANGING = {"minecraft:chain", "minecraft:hanging_roots", "minecraft:spore_blossom",
            "minecraft:cave_vines", "minecraft:cave_vines_plant"};
    private static final String[] AIRS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air",
            "minecraft:structure_void"};

    /**
     * The barrels of a building's template, by cell index: {chest state id, forced (1: always a chest), cell of the
     * bottom of its stack, index in the stack (0 = bottom), height of the stack, chance of a barrel not forced to be
     * taken out, in millionths (above {@link #GONE} when some are forced, so that GONE of them all go)}. The chest states go into
     * {@code ids} (the structure block states).
     */
    static Map<Integer, int[]> of(Template t, Map<String, Integer> ids) {
        Map<Integer, int[]> out = new HashMap<>();
        int layer = t.sx * t.sz;
        for (int y = 0; y < t.sy; y++) {
            for (int z = 0; z < t.sz; z++) {
                for (int x = 0; x < t.sx; x++) {
                    if (!barrel(t, x, y, z) || barrel(t, x, y - 1, z)) {
                        continue;
                    }
                    // the bottom of a stack: measure it, then whether it holds anything
                    int height = 0;
                    while (barrel(t, x, y + height, z)) {
                        height++;
                    }
                    boolean forced = onTop(t, x, y + height, z) || hangs(t, x, y - 1, z);
                    for (int i = 0; i < height && !forced; i++) {
                        forced = fixed(t, x + 1, y + i, z, "east", "west") || fixed(t, x - 1, y + i, z, "west", "east")
                                || fixed(t, x, y + i, z + 1, "south", "north")
                                || fixed(t, x, y + i, z - 1, "north", "south");
                    }
                    int bottom = x + t.sx * (z + t.sz * y);
                    for (int i = 0; i < height; i++) {
                        String chest = "minecraft:chest[facing=" + facing(t, x, y + i, z)
                                + ",type=single,waterlogged=false]";
                        out.put(bottom + i * layer,
                                new int[]{StructureLoader.id(ids, chest), forced ? 1 : 0, bottom, i, height, 0});
                    }
                }
            }
        }
        // the barrels forced to stay are made up for by the others, so that GONE of all of them go
        int forced = 0;
        for (int[] b : out.values()) {
            forced += b[FORCED];
        }
        int chance = forced == out.size() ? 0
                : (int) Math.round(1e6 * Math.min(1.0, GONE * out.size() / (out.size() - forced)));
        for (int[] b : out.values()) {
            b[CHANCE] = chance;
        }
        return out;
    }

    /** Whether the barrel entry {@code b} becomes a chest in the copy of seed {@code seed} (else it is taken out). */
    static boolean kept(int[] b, long seed, int layer) {
        if (b[FORCED] == 1) {
            return true;
        }
        int keep = 0;
        for (int i = 0; i < b[HEIGHT]; i++) {
            if (Hash.unit(Hash.of(seed, SALT, b[BOTTOM] + (long) i * layer)) >= b[CHANCE] / 1e6) {
                keep++;
            }
        }
        return b[INDEX] < keep;
    }

    private static String name(Template t, int x, int y, int z) {
        int p = x < 0 || y < 0 || z < 0 || x >= t.sx || y >= t.sy || z >= t.sz ? -1 : t.at(x, y, z);
        return p < 0 ? "minecraft:air" : t.palette.get(p);
    }

    private static boolean barrel(Template t, int x, int y, int z) {
        return Structures.blockOf(name(t, x, y, z)).equals("minecraft:barrel");
    }

    private static boolean air(Template t, int x, int y, int z) {
        String b = Structures.blockOf(name(t, x, y, z));
        for (String a : AIRS) {
            if (b.equals(a)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the block over a stack of barrels stands on it: anything but air, except a vine (it hangs from the
     * walls or from the vine over it, never on the top of a block) or a lichen not fixed to its bottom face.
     */
    private static boolean onTop(Template t, int x, int y, int z) {
        if (air(t, x, y, z)) {
            return false;
        }
        String s = name(t, x, y, z);
        String b = Structures.blockOf(s);
        if (faces(b)) {
            return !b.equals("minecraft:vine") && s.contains("down=true");
        }
        return true;
    }

    /** Whether the block at (x, y, z), beside a barrel ({@code away}: the side it lies on), is fixed to the barrel. */
    private static boolean fixed(Template t, int x, int y, int z, String away, String toward) {
        String s = name(t, x, y, z);
        String b = Structures.blockOf(s);
        if (faces(b)) {
            return s.contains(toward + "=true");
        }
        if (b.endsWith("_button") || b.equals("minecraft:lever")) {
            return s.contains("face=wall") && s.contains("facing=" + away);
        }
        for (String k : FACING_FIXED) {
            if (b.endsWith(k)) {
                return s.contains("facing=" + away);
            }
        }
        return false;
    }

    /** Whether the block under a barrel hangs from it. */
    private static boolean hangs(Template t, int x, int y, int z) {
        String s = name(t, x, y, z);
        String b = Structures.blockOf(s);
        if (faces(b)) {
            return s.contains("up=true");
        }
        if (b.endsWith("_hanging_sign") && !b.endsWith("_wall_hanging_sign")) {
            return true;
        }
        if (b.endsWith("lantern")) {
            return s.contains("hanging=true");
        }
        if (b.equals("minecraft:pointed_dripstone")) {
            return s.contains("vertical_direction=down");
        }
        for (String k : HANGING) {
            if (b.equals(k)) {
                return true;
            }
        }
        return false;
    }

    private static boolean faces(String block) {
        for (String k : FACES) {
            if (block.equals(k)) {
                return true;
            }
        }
        return false;
    }

    /** Facing of the chest that replaces the barrel at (x, y, z). */
    private static String facing(Template t, int x, int y, int z) {
        String s = name(t, x, y, z);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("facing=(\\w+)").matcher(s);
        String f = m.find() ? m.group(1) : "up";
        if (!f.equals("up") && !f.equals("down")) {
            return f;
        }
        String[] sides = {"north", "east", "south", "west"};
        int[][] step = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            if (air(t, x + step[i][0], y, z + step[i][1])) {
                return sides[i];
            }
        }
        return "north";
    }
}
