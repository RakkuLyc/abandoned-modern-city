package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nature taking over the structures (asked by the user, 13 nov 2026), modeled on predio_apocaliptico_57x45: applied to
 * every template when it is loaded, before anything else reads it, and switched off by the config option
 * {@code structureOvergrowth} ({@link #enabled}). It only adds what a structure lacks to reach the measures of that
 * building; one that already has as much keeps what it has. Above the ground layer only (the yard stays as built):
 * <ul>
 *   <li>cracked stone: plain stone bricks, deepslate bricks and tiles, polished blackstone bricks and nether bricks
 *   turn cracked, in patches, up to {@link #CRACKED} of them (that building: 9 863 of 31 332);</li>
 *   <li>green on the floors: scattered spots on the full blocks with air over them, up to {@link #GREEN_COVERED} of
 *   those under a roof and {@link #GREEN_OPEN} of those under the open sky. A stone or concrete floor turns into
 *   moss, grass or dirt (in the shares of that building), often with a moss carpet, grass, a fern or a dead bush on
 *   it; any other full block (wood) only gets a moss carpet;</li>
 *   <li>vines on the facades (asked by the user, 13 nov 2026: longer, aggressive, on all sides): strands of 3 to 12
 *   blocks hanging down the outer walls, in patches, up to {@link #VINES} of the outer facade of each side, and a few
 *   under the eaves;</li>
 *   <li>cobblestone and stone bricks next to the new green (2 blocks or less) turn mossy, half of them.</li>
 * </ul>
 * Never touched: leaves, logs, glass, block entities (containers, banners, beds...), doors, water, partial blocks.
 * Plants only stand on soil (moss, grass, dirt), carpets only on full blocks, vines only hang on a full block or on the
 * vine above them.
 */
public final class Weathering {
    private Weathering() {
    }

    /** Set from the config before the structures load ({@code structureOvergrowth}); on by default. */
    public static volatile boolean enabled = true;

    /** The measures of predio_apocaliptico_57x45. */
    static final double CRACKED = 0.31;
    static final double GREEN_COVERED = 0.082;
    static final double GREEN_OPEN = 0.358;
    static final double MOSSY_NEAR_GREEN = 0.5;

    private static final String[][] CRACK = {
            {"minecraft:stone_bricks", "minecraft:cracked_stone_bricks"},
            {"minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks"},
            {"minecraft:deepslate_tiles", "minecraft:cracked_deepslate_tiles"},
            {"minecraft:polished_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks"},
            {"minecraft:nether_bricks", "minecraft:cracked_nether_bricks"}};
    private static final String[][] MOSSY = {
            {"minecraft:cobblestone", "minecraft:mossy_cobblestone"},
            {"minecraft:cobblestone_stairs", "minecraft:mossy_cobblestone_stairs"},
            {"minecraft:cobblestone_slab", "minecraft:mossy_cobblestone_slab"},
            {"minecraft:cobblestone_wall", "minecraft:mossy_cobblestone_wall"},
            {"minecraft:stone_bricks", "minecraft:mossy_stone_bricks"},
            {"minecraft:stone_brick_stairs", "minecraft:mossy_stone_brick_stairs"},
            {"minecraft:stone_brick_slab", "minecraft:mossy_stone_brick_slab"},
            {"minecraft:stone_brick_wall", "minecraft:mossy_stone_brick_wall"}};
    /** Not a full block (or not to be touched): any block whose name has one of these. */
    private static final String[] NOT_FULL = {"stairs", "slab", "wall", "fence", "pane", "bars", "door", "carpet", "glass",
            "leaves", "log", "wood", "stem", "hyphae", "bed", "banner", "sign", "button", "plate", "torch", "lantern",
            "chain", "rail", "candle", "pot", "head", "skull", "chest", "barrel", "furnace", "smoker", "lectern", "anvil",
            "cauldron", "campfire", "hopper", "ladder", "scaffolding", "snow", "cake", "bell", "grindstone", "stonecutter",
            "composter", "jukebox", "daylight", "dispenser", "dropper", "observer", "piston", "lever", "repeater",
            "comparator", "redstone", "tripwire", "shulker", "_rod", "bookshelf", "crafting", "brewing", "beacon",
            "conduit", "spawner", "web", "vine", "grass", "fern", "bush", "flower", "sapling", "tulip", "water", "lava",
            "air", "void", "honey", "slime", "_ice", "powder", "sculk", "lichen", "roots", "dripleaf", "azalea",
            "mushroom", "cactus", "cane", "kelp", "coral", "pickle", "egg", "frame", "barrier", "end_", "_bud",
            "cluster", "amethyst", "dirt", "podzol", "mycelium", "gravel", "farmland", "path", "hay", "target", "tnt",
            "table", "grate", "bulb", "vault", "crafter", "lamp", "heavy_core", "beehive", "bee_nest", "lodestone",
            "anchor", "dragon", "petals", "spore", "frogspawn", "turtle", "sniffer", "decorated", "suspicious"};
    /** Not full blocks either, matched by the whole name (a part of it would catch light_gray_concrete, sandstone...). */
    private static final String[] NOT_FULL_EXACT = {"minecraft:light", "minecraft:ice", "minecraft:sand",
            "minecraft:red_sand", "minecraft:mud", "minecraft:clay", "minecraft:snow_block",
            "minecraft:powder_snow"};
    /** Stone-like floors that may turn into moss, grass or dirt. */
    private static final String[] MINERAL = {"stone", "cobble", "brick", "deepslate", "blackstone", "basalt", "tuff",
            "andesite", "diorite", "granite", "calcite", "concrete", "terracotta", "sandstone", "quartz", "prismarine",
            "purpur", "packed_mud", "obsidian"};
    /** Blocks plants and vines see through (as in the measures). */
    private static final String[] SOFT = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air",
            "minecraft:structure_void", "minecraft:vine", "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern",
            "minecraft:large_fern", "minecraft:dead_bush", "minecraft:cobweb", "minecraft:moss_carpet",
            "minecraft:glow_lichen"};

    static boolean full(String block) {
        for (String k : NOT_FULL_EXACT) {
            if (block.equals(k)) {
                return false;
            }
        }
        for (String k : NOT_FULL) {
            if (block.contains(k)) {
                return false;
            }
        }
        return true;
    }

    static boolean mineral(String block) {
        if (!full(block)) {
            return false;
        }
        for (String k : MINERAL) {
            if (block.contains(k)) {
                return true;
            }
        }
        return false;
    }

    static boolean soft(String block) {
        for (String k : SOFT) {
            if (block.equals(k)) {
                return true;
            }
        }
        return false;
    }

    static boolean air(String block) {
        return block.equals("minecraft:air") || block.equals("minecraft:cave_air") || block.equals("minecraft:void_air");
    }

    /** What was added to one template, for the tests and the log. */
    public record Report(int cracked, int floors, int carpets, int plants, int vines, int mossy) {
        public int total() {
            return cracked + floors + carpets + plants + vines + mossy;
        }
    }

    /** The template with nature taking over, and what changed. */
    public record Result(Template template, Report report) {
    }

    /**
     * Applies the overgrowth to {@code t}, whose ground layer is {@code ground} layers over its lowest solid block;
     * {@code seed} makes it differ between structures and stay the same between loads.
     */
    public static Result apply(Template t, int ground, long seed) {
        Editor e = new Editor(t);
        // the box of the solid blocks, and the ground layer
        int x0 = t.sx;
        int y0 = t.sy;
        int z0 = t.sz;
        int x1 = -1;
        int y1 = -1;
        int z1 = -1;
        for (int y = 0; y < t.sy; y++) {
            for (int z = 0; z < t.sz; z++) {
                for (int x = 0; x < t.sx; x++) {
                    if (!soft(e.block(x, y, z))) {
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
            return new Result(t, new Report(0, 0, 0, 0, 0, 0));
        }
        int gy = y0 + ground;
        // nothing is added outside the box of the solid blocks: the structure keeps its size and its place
        e.box = new int[]{x0, y0, z0, x1, y1, z1};
        int cracked = crack(e, gy, seed);
        int[] green = green(e, gy, seed);
        int vines = vines(e, gy, x0, z0, x1, z1, seed);
        int mossy = mossy(e, gy, seed);
        Report r = new Report(cracked, green[0], green[1], green[2], vines, mossy);
        return new Result(r.total() == 0 ? t : e.build(), r);
    }

    // ------------------------------------------------------------------ cracked stone

    private static int crack(Editor e, int gy, long seed) {
        int family = 0;
        int already = 0;
        List<int[]> plain = new ArrayList<>();
        for (int y = gy + 1; y < e.t.sy; y++) {
            for (int z = 0; z < e.t.sz; z++) {
                for (int x = 0; x < e.t.sx; x++) {
                    String b = e.block(x, y, z);
                    for (String[] c : CRACK) {
                        if (b.equals(c[0])) {
                            family++;
                            plain.add(new int[]{x, y, z});
                        } else if (b.equals(c[1])) {
                            family++;
                            already++;
                        }
                    }
                }
            }
        }
        int want = (int) Math.round(CRACKED * family) - already;
        if (want <= 0) {
            return 0;
        }
        // in patches: the plain blocks where the noise is highest crack first
        plain.sort((a, b) -> Double.compare(crackRank(seed, b), crackRank(seed, a)));
        int n = Math.min(want, plain.size());
        for (int k = 0; k < n; k++) {
            int[] p = plain.get(k);
            String b = e.block(p[0], p[1], p[2]);
            for (String[] c : CRACK) {
                if (b.equals(c[0])) {
                    e.set(p[0], p[1], p[2], c[1]);
                }
            }
        }
        return n;
    }

    private static double crackRank(long seed, int[] p) {
        double noise = Flora.noise(seed, 0x435241L, p[0] + 0.41 * p[1], p[2] - 0.37 * p[1], 3);
        return noise + 0.25 * Hash.unit(Hash.of(seed, 0x435242L, p[0], p[1], p[2]));
    }

    // ------------------------------------------------------------------ green on the floors

    /** Returns {floors turned to soil, carpets, plants}. */
    private static int[] green(Editor e, int gy, long seed) {
        int sx = e.t.sx;
        int sy = e.t.sy;
        int sz = e.t.sz;
        // the highest solid block of each column: a top under it is covered by a roof
        int[] roof = new int[sx * sz];
        Arrays.fill(roof, -1);
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    if (!soft(e.block(x, y, z))) {
                        roof[x + sx * z] = y;
                    }
                }
            }
        }
        List<int[]> covered = new ArrayList<>();
        List<int[]> open = new ArrayList<>();
        int greenCovered = 0;
        int greenOpen = 0;
        for (int y = gy + 1; y < sy - 1; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    String b = e.block(x, y, z);
                    String above = e.block(x, y + 1, z);
                    if (soft(b) || !soft(above)) {
                        continue;
                    }
                    boolean isCovered = roof[x + sx * z] > y + 1;
                    boolean isGreen = b.equals("minecraft:moss_block") || b.equals("minecraft:grass_block")
                            || above.equals("minecraft:moss_carpet") || above.equals("minecraft:short_grass")
                            || above.equals("minecraft:fern") || above.equals("minecraft:dead_bush")
                            || above.equals("minecraft:tall_grass") || above.equals("minecraft:large_fern");
                    if (isCovered) {
                        greenCovered += isGreen ? 1 : 0;
                    } else {
                        greenOpen += isGreen ? 1 : 0;
                    }
                    // a spot may only go on a full block with plain air over it, not on one already changed (cracked)
                    if (!isGreen && full(b) && air(above) && !e.changed(x, y, z) && e.inBox(x, y + 1, z)) {
                        (isCovered ? covered : open).add(new int[]{x, y, z});
                    }
                }
            }
        }
        int[] out = new int[3];
        for (int pass = 0; pass < 2; pass++) {
            List<int[]> cand = pass == 0 ? covered : open;
            int all = cand.size() + (pass == 0 ? greenCovered : greenOpen);
            // the target share of all the tops (green or not; tops on partial blocks or under a plant are not counted)
            int want = (int) Math.round((pass == 0 ? GREEN_COVERED : GREEN_OPEN) * all) - (pass == 0 ? greenCovered : greenOpen);
            if (want <= 0 || cand.isEmpty()) {
                continue;
            }
            // scattered spots, slightly gathered: a random rank mixed with a little noise
            cand.sort((a, b) -> Double.compare(spotRank(seed, b), spotRank(seed, a)));
            int n = Math.min(want, cand.size());
            for (int k = 0; k < n; k++) {
                int[] p = cand.get(k);
                spot(e, p[0], p[1], p[2], seed, out);
            }
        }
        return out;
    }

    private static double spotRank(long seed, int[] p) {
        double noise = Flora.noise(seed, 0x475245L, p[0] + 0.53 * p[1], p[2] + 0.29 * p[1], 4);
        return Hash.unit(Hash.of(seed, 0x475246L, p[0], p[1], p[2])) + 0.35 * noise;
    }

    /** One green spot on the full block at (x, y, z), air over it. */
    private static void spot(Editor e, int x, int y, int z, long seed, int[] out) {
        String b = e.block(x, y, z);
        long h = Hash.of(seed, 0x53504FL, x, y, z);
        double r = Hash.unit(h);
        double q = Hash.unit(Hash.next(h, 1));
        if (!mineral(b)) {
            // wood and other full blocks: only a carpet of moss
            e.set(x, y + 1, z, "minecraft:moss_carpet");
            out[1]++;
            return;
        }
        // the soil of that building: moss 63%, grass 20%, coarse dirt 7%, rooted dirt 5%, dirt 5%
        String soil = r < 0.63 ? "minecraft:moss_block" : r < 0.83 ? "minecraft:grass_block[snowy=false]"
                : r < 0.90 ? "minecraft:coarse_dirt" : r < 0.95 ? "minecraft:rooted_dirt" : "minecraft:dirt";
        e.set(x, y, z, soil);
        out[0]++;
        String plant;
        if (soil.equals("minecraft:moss_block")) {
            // moss carpet 47%, short grass 28%, fern 10%, dead bush 1%, nothing 14%
            plant = q < 0.47 ? "minecraft:moss_carpet" : q < 0.75 ? "minecraft:short_grass" : q < 0.85 ? "minecraft:fern"
                    : q < 0.86 ? "minecraft:dead_bush" : null;
        } else if (soil.startsWith("minecraft:grass_block")) {
            // short grass 52%, moss carpet 19%, fern 17%, dead bush 4%, nothing 8%
            plant = q < 0.52 ? "minecraft:short_grass" : q < 0.71 ? "minecraft:moss_carpet" : q < 0.88 ? "minecraft:fern"
                    : q < 0.92 ? "minecraft:dead_bush" : null;
        } else {
            // dirt: short grass 35%, fern 10%, dead bush 5%, nothing 50%
            plant = q < 0.35 ? "minecraft:short_grass" : q < 0.45 ? "minecraft:fern" : q < 0.50 ? "minecraft:dead_bush" : null;
        }
        if (plant != null) {
            e.set(x, y + 1, z, plant);
            if (plant.equals("minecraft:moss_carpet")) {
                out[1]++;
            } else {
                out[2]++;
            }
        }
    }

    // ------------------------------------------------------------------ vines

    /**
     * The outer facade of a template: the air cells above the ground layer, inside the box, next to a full block (the
     * wall) on side {@code k} (0 north, 1 east, 2 south, 3 west of the cell) and looking out of the box the other way.
     * Returns, per side, {facade cells, of them with a vine}.
     */
    static int[][] facade(Template t, int gy) {
        Editor e = new Editor(t);
        int[] b = solidBox(e);
        int[][] out = new int[4][2];
        if (b == null) {
            return out;
        }
        for (int y = gy + 1; y <= b[4]; y++) {
            for (int z = b[2]; z <= b[5]; z++) {
                for (int x = b[0]; x <= b[3]; x++) {
                    String c = e.block(x, y, z);
                    if (!air(c) && !c.equals("minecraft:vine")) {
                        continue;
                    }
                    for (int k = 0; k < 4; k++) {
                        if (facadeSide(e, x, y, z, k, b)) {
                            out[k][0]++;
                            out[k][1] += c.equals("minecraft:vine") ? 1 : 0;
                        }
                    }
                }
            }
        }
        return out;
    }

    private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // north, east, south, west

    private static boolean facadeSide(Editor e, int x, int y, int z, int k, int[] b) {
        String w = e.block(x + SIDES[k][0], y, z + SIDES[k][1]);
        return full(w) && !soft(w) && outside(e, x, y, z, -SIDES[k][0], -SIDES[k][1], b[0], b[2], b[3], b[5]);
    }

    private static int[] solidBox(Editor e) {
        if (e.box != null) {
            return e.box;
        }
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1, -1};
        for (int y = 0; y < e.t.sy; y++) {
            for (int z = 0; z < e.t.sz; z++) {
                for (int x = 0; x < e.t.sx; x++) {
                    if (!soft(e.block(x, y, z))) {
                        b[0] = Math.min(b[0], x);
                        b[1] = Math.min(b[1], y);
                        b[2] = Math.min(b[2], z);
                        b[3] = Math.max(b[3], x);
                        b[4] = Math.max(b[4], y);
                        b[5] = Math.max(b[5], z);
                    }
                }
            }
        }
        return b[3] < 0 ? null : b;
    }

    /**
     * Strands of vines on every side, up to {@link #VINES} of the outer facade of that side (asked by the user, 13 nov
     * 2026: longer, aggressive, on all sides). Strands of {@link #STRAND_MIN} to {@link #STRAND_MAX} blocks hang down
     * from where they start to a floor or the ground; they start where a value noise over the facade is highest, so
     * they gather in patches that cover stretches of wall and leave others bare. A side already that covered keeps
     * what it has. Under the eaves (a full block above, outside), one vine in {@link #EAVES} holds by its top.
     */
    private static int vines(Editor e, int gy, int x0, int z0, int x1, int z1, long seed) {
        int[] b = e.box;
        int added = 0;
        for (int k = 0; k < 4; k++) {
            List<int[]> cand = new ArrayList<>();
            int cells = 0;
            int have = 0;
            for (int y = gy + 1; y <= b[4]; y++) {
                for (int z = b[2]; z <= b[5]; z++) {
                    for (int x = b[0]; x <= b[3]; x++) {
                        String c = e.block(x, y, z);
                        boolean vine = c.equals("minecraft:vine");
                        if (!air(c) && !vine || !facadeSide(e, x, y, z, k, b)) {
                            continue;
                        }
                        cells++;
                        if (vine) {
                            have++;
                        } else if (y > gy + 1) {
                            cand.add(new int[]{x, y, z});
                        }
                    }
                }
            }
            int want = (int) Math.round(VINES * cells) - have;
            if (want <= 0 || cand.isEmpty()) {
                continue;
            }
            // patches: the noise along the wall (x for the north and south sides, z for east and west) and up it
            final int side = k;
            cand.sort((p, q) -> Double.compare(vineRank(seed, side, q), vineRank(seed, side, p)));
            String state = vine(k == 1, k == 0, k == 2, k == 3, false);
            int got = 0;
            for (int[] c : cand) {
                if (got >= want) {
                    break;
                }
                if (!air(e.block(c[0], c[1], c[2]))) {
                    continue;
                }
                // 3 to 12 blocks, about 6 on average
                double u = Hash.unit(Hash.of(seed, 0x52554EL, c[0], c[1] * 4L + k, c[2]));
                int len = STRAND_MIN + (int) Math.floor((STRAND_MAX - STRAND_MIN + 1) * Math.pow(u, 1.6));
                len = Math.min(len, STRAND_MAX);
                for (int n = 0; n < len; n++) {
                    int y = c[1] - n;
                    if (y <= gy || !air(e.block(c[0], y, c[2]))) {
                        break;
                    }
                    // the cells under the first hold on the vine above them (same face), wall or not; only those on the
                    // facade count toward its share
                    e.set(c[0], y, c[2], state);
                    if (facadeSide(e, c[0], y, c[2], k, b)) {
                        got++;
                    }
                    added++;
                }
            }
        }
        // a few vines under the eaves
        for (int y = gy + 2; y <= b[4]; y++) {
            for (int z = b[2]; z <= b[5]; z++) {
                for (int x = b[0]; x <= b[3]; x++) {
                    String up = e.block(x, y + 1, z);
                    if (air(e.block(x, y, z)) && full(up) && !soft(up) && e.inBox(x, y + 1, z)
                            && outsideAny(e, x, y, z, b[0], b[2], b[3], b[5])
                            && Hash.unit(Hash.of(seed, 0x454156L, x, y, z)) < EAVES) {
                        e.set(x, y, z, vine(false, false, false, false, true));
                        added++;
                    }
                }
            }
        }
        return added;
    }

    /** Share of the outer facade of each side with vines (asked by the user: strong, ~25%). */
    static final double VINES = 0.25;
    static final int STRAND_MIN = 3;
    static final int STRAND_MAX = 12;
    /** Share of the cells under an eave (outside) that get a vine holding by its top. */
    static final double EAVES = 0.10;

    private static double vineRank(long seed, int side, int[] p) {
        double along = side == 0 || side == 2 ? p[0] : p[2];
        double noise = Flora.noise(seed, 0x56494E0L + side, along, p[1] * 0.6, 5);
        return noise + 0.35 * Hash.unit(Hash.of(seed, 0x56494EL, p[0], p[1] * 4L + side, p[2]));
    }

    static String vine(boolean east, boolean north, boolean south, boolean west, boolean up) {
        return "minecraft:vine[east=" + east + ",north=" + north + ",south=" + south + ",up=" + up + ",west=" + west + "]";
    }

    /** Whether the cell (x, y, z) sees out of the box going (dx, dz): only soft blocks up to the edge. */
    private static boolean outside(Editor e, int x, int y, int z, int dx, int dz, int x0, int z0, int x1, int z1) {
        int cx = x;
        int cz = z;
        while (cx >= x0 && cx <= x1 && cz >= z0 && cz <= z1) {
            if (!soft(e.block(cx, y, cz))) {
                return false;
            }
            cx += dx;
            cz += dz;
        }
        return true;
    }

    private static boolean outsideAny(Editor e, int x, int y, int z, int x0, int z0, int x1, int z1) {
        return outside(e, x, y, z, 1, 0, x0, z0, x1, z1) || outside(e, x, y, z, -1, 0, x0, z0, x1, z1)
                || outside(e, x, y, z, 0, 1, x0, z0, x1, z1) || outside(e, x, y, z, 0, -1, x0, z0, x1, z1);
    }

    // ------------------------------------------------------------------ mossy stones next to the green

    private static int mossy(Editor e, int gy, long seed) {
        int n = 0;
        List<int[]> turn = new ArrayList<>();
        for (int y = gy + 1; y < e.t.sy; y++) {
            for (int z = 0; z < e.t.sz; z++) {
                for (int x = 0; x < e.t.sx; x++) {
                    String b = e.block(x, y, z);
                    String to = null;
                    for (String[] m : MOSSY) {
                        if (b.equals(m[0])) {
                            to = m[1];
                        }
                    }
                    if (to == null || e.changed(x, y, z) || Hash.unit(Hash.of(seed, 0x4D4F53L, x, y, z)) >= MOSSY_NEAR_GREEN
                            || !nearNewGreen(e, x, y, z)) {
                        continue;
                    }
                    turn.add(new int[]{x, y, z});
                }
            }
        }
        for (int[] p : turn) {
            String full = e.state(p[0], p[1], p[2]);
            String b = Structures.blockOf(full);
            for (String[] m : MOSSY) {
                if (b.equals(m[0])) {
                    e.set(p[0], p[1], p[2], m[1] + full.substring(b.length()));
                    n++;
                }
            }
        }
        return n;
    }

    private static boolean nearNewGreen(Editor e, int x, int y, int z) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    if (e.added(x + dx, y + dy, z + dz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ editing

    /** A copy-on-write view of a template's cells. */
    private static final class Editor {
        final Template t;
        final Map<Integer, String> changed = new HashMap<>();
        int[] box;

        Editor(Template t) {
            this.t = t;
        }

        String state(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= t.sx || y >= t.sy || z >= t.sz) {
                return "minecraft:air";
            }
            String c = changed.get(x + t.sx * (z + t.sz * y));
            if (c != null) {
                return c;
            }
            int p = t.at(x, y, z);
            return p < 0 ? "minecraft:air" : t.palette.get(p);
        }

        String block(int x, int y, int z) {
            return Structures.blockOf(state(x, y, z));
        }

        boolean added(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= t.sx || y >= t.sy || z >= t.sz) {
                return false;
            }
            String c = changed.get(x + t.sx * (z + t.sz * y));
            return c != null && !c.contains("cracked_");
        }

        boolean changed(int x, int y, int z) {
            return changed.containsKey(x + t.sx * (z + t.sz * y));
        }

        boolean inBox(int x, int y, int z) {
            return box == null || x >= box[0] && y >= box[1] && z >= box[2] && x <= box[3] && y <= box[4] && z <= box[5];
        }

        void set(int x, int y, int z, String state) {
            if (!inBox(x, y, z)) {
                throw new IllegalStateException("overgrowth outside the box at " + x + "," + y + "," + z);
            }
            changed.put(x + t.sx * (z + t.sz * y), state);
        }

        Template build() {
            return t.withStates(changed);
        }
    }
}
