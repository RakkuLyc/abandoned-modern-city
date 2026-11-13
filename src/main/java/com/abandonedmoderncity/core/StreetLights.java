package com.abandonedmoderncity.core;

import java.util.Arrays;

/**
 * Street lights (modeled on the example structure poste_exemplo): on every segment of street,
 * one every {@link #LAMP_EVERY} blocks on both sides, facing each other, centered on the segment (7 a side on the
 * segments of 180, 5 on those of 120, away from the crosswalks and the crossings). The post ({@link #LAMP_POST}
 * high) stands on the sidewalk row next to the lanes; its arm (slabs) reaches {@link #LAMP_ARM} blocks over the
 * lanes at the height {@link #LAMP_ARM_Y}, and the lamp (glass, unlit) hangs under its end. Abandoned (vines, dark
 * tones, mossy blocks): {@link #LAMP_GONE} of them are gone, the others are one
 * of {@link #LAMP_VARIANTS} variants ({@link #lampParts}): mossy, dark, broken arm, fallen lamp, overgrown.
 */
public final class StreetLights {
    private StreetLights() {
    }

    public static final int LAMP_EVERY = 24;
    public static final int LAMP_POST = 13;
    public static final int LAMP_ARM = 4;
    public static final int LAMP_ARM_Y = 12;
    /** Highest block of a light over the street surface (the tuft of leaves on top of the overgrown post). */
    public static final int LAMP_TOP = LAMP_POST + 1;
    public static final double LAMP_GONE = 0.10;
    public static final int LAMP_VARIANTS = 5;
    public static final int NO_LAMP = Integer.MIN_VALUE;
    public static final int V_MOSSY = 0;
    public static final int V_DARK = 1;
    public static final int V_BROKEN = 2;
    public static final int V_FALLEN = 3;
    public static final int V_OVERGROWN = 4;

    /** Blocks of the lights: {@link BlockCode#LAMP} + one of these (the palette knows the states). */
    public static final int L_MOSSY_COBBLE = 0;
    public static final int L_MOSSY_BRICKS = 1;
    public static final int L_POLISHED_DEEPSLATE = 2;
    public static final int L_CRACKED_DEEPSLATE_BRICKS = 3;
    public static final int L_COBBLED_DEEPSLATE = 4;
    public static final int L_TUFF = 5;
    public static final int L_ANDESITE = 6;
    public static final int L_MOSS_BLOCK = 7;
    public static final int L_MOSSY_BRICK_SLAB = 8;
    public static final int L_DEEPSLATE_SLAB = 9;
    public static final int L_COBBLED_DEEPSLATE_SLAB = 10;
    public static final int L_GRAY_GLASS = 11;
    public static final int L_TINTED_GLASS = 12;
    public static final int L_CHAIN = 13;
    public static final int L_LEAVES = 14;
    /** Vines: + 0 holding by the top (under the arm), + 1 + face (0 north .. 3 west) holding on the post. */
    public static final int L_VINE = 15;

    /** Positions along a segment {@code run} long where its lights stand (centered, {@link #LAMP_EVERY} apart). */
    static boolean lampAt(int t, int run) {
        int n = (run - 2 * 10) / LAMP_EVERY + 1;
        int first = (run - (n - 1) * LAMP_EVERY) / 2;
        return t >= first && t <= first + (n - 1) * LAMP_EVERY && (t - first) % LAMP_EVERY == 0;
    }

    /**
     * The light (if any) at signed distance {@code across} from the centerline of a segment, {@code along} blocks into
     * it, in the city of seed {@code seed}; {@code axis} (0: the street runs north-south), {@code a} and {@code b} name
     * the segment.
     */
    static void lampCandidate(City.Hit h, long seed, double across, double along, int run, int axis, int a, int b) {
        double lane = Params.STREET_LANE_HW;
        double d = Math.abs(across);
        // 0 the post (the sidewalk row next to the lanes), -1 the row behind it, 1..LAMP_ARM the lanes under the arm
        int offset = (int) Math.floor(lane + 1 - d);
        int t = (int) Math.floor(along);
        if (offset < -1 || offset > LAMP_ARM || t < 0 || t >= run || !lampAt(t, run)) {
            return;
        }
        long key = Hash.of(seed, 0x4C414D50L + axis, a * 4096L + b, t * 2L + (across < 0 ? 0 : 1));
        double r = Hash.unit(key);
        if (r < LAMP_GONE) {
            return;
        }
        h.lamp = offset;
        h.lampVariant = Math.min(LAMP_VARIANTS - 1, (int) ((r - LAMP_GONE) / ((1 - LAMP_GONE) / LAMP_VARIANTS)));
        // from the row behind the post, the post is toward the centerline
        h.lampFace = axis == 0 ? (across > 0 ? 3 : 1) : (across > 0 ? 0 : 2);
        h.lampSeed = Hash.next(key, 7);
    }

    /**
     * The blocks of the street light at a queried column ({@link City.Hit#lamp}): {@code parts[dy]} for dy 1 to
     * {@link #LAMP_TOP} over the street surface ({@link BlockCode#KEEP} where it has nothing), or null. The variants:
     * <ul>
     *   <li>mossy: post of mossy cobblestone and mossy stone bricks, arm of mossy slabs, gray glass, a vine up the
     *   back of the post and two under the arm;</li>
     *   <li>dark: post of polished, cracked and cobbled deepslate, deepslate slabs, tinted glass, vines;</li>
     *   <li>broken: post of andesite, tuff and cobbled deepslate, half the arm gone, no lamp;</li>
     *   <li>fallen lamp: the whole arm, only a chain hanging at its end, the lamp fallen on the lane under it (a block
     *   in the way: that column is not drivable);</li>
     *   <li>overgrown: post of mossy bricks and moss, a tuft of leaves on top, vines up the post and under the arm.</li>
     * </ul>
     * Vines start 3 above the street (the sidewalk's tall grass is 2 high) and hang from the arm only 1 block (a vine
     * holding by its top holds nothing under it). Over the lanes nothing is lower than {@link #LAMP_ARM_Y} - 1 but the
     * fallen lamp.
     */
    public static int[] lampParts(City.Hit h) {
        if (h.lamp == NO_LAMP) {
            return null;
        }
        int[] p = new int[LAMP_TOP + 1];
        Arrays.fill(p, BlockCode.KEEP);
        int v = h.lampVariant;
        int[] post;
        int slab;
        int arm = LAMP_ARM;
        int lamp = -1;
        int vineLow;
        int vineHigh;
        int[] hanging;
        switch (v) {
            case V_MOSSY -> {
                post = new int[]{L_MOSSY_COBBLE, L_MOSSY_BRICKS, L_MOSSY_BRICKS};
                slab = L_MOSSY_BRICK_SLAB;
                lamp = L_GRAY_GLASS;
                vineLow = 3;
                vineHigh = 10;
                hanging = new int[]{1, 3};
            }
            case V_DARK -> {
                post = new int[]{L_POLISHED_DEEPSLATE, L_CRACKED_DEEPSLATE_BRICKS, L_COBBLED_DEEPSLATE};
                slab = L_DEEPSLATE_SLAB;
                lamp = L_TINTED_GLASS;
                vineLow = 6;
                vineHigh = 12;
                hanging = new int[]{2};
            }
            case V_BROKEN -> {
                post = new int[]{L_ANDESITE, L_TUFF, L_COBBLED_DEEPSLATE};
                slab = L_COBBLED_DEEPSLATE_SLAB;
                arm = 2;
                vineLow = 3;
                vineHigh = 7;
                hanging = new int[]{2};
            }
            case V_FALLEN -> {
                post = new int[]{L_MOSSY_COBBLE, L_POLISHED_DEEPSLATE, L_MOSSY_BRICKS};
                slab = L_COBBLED_DEEPSLATE_SLAB;
                vineLow = 4;
                vineHigh = 9;
                hanging = new int[]{1};
            }
            default -> {
                post = new int[]{L_MOSSY_BRICKS, L_MOSS_BLOCK, L_MOSSY_COBBLE};
                slab = L_MOSSY_BRICK_SLAB;
                lamp = L_GRAY_GLASS;
                vineLow = 3;
                vineHigh = 12;
                hanging = new int[]{1, 2, 3};
            }
        }
        int o = h.lamp;
        if (o == -1) {
            // a vine up the back of the post
            for (int dy = vineLow; dy <= vineHigh; dy++) {
                p[dy] = BlockCode.LAMP + L_VINE + 1 + h.lampFace;
            }
        } else if (o == 0) {
            for (int dy = 1; dy <= LAMP_POST; dy++) {
                p[dy] = BlockCode.LAMP + post[Hash.range(Hash.next(h.lampSeed, dy), 0, post.length - 1)];
            }
            if (v == V_OVERGROWN) {
                p[LAMP_POST] = BlockCode.LAMP + L_LEAVES;
                p[LAMP_POST + 1] = BlockCode.LAMP + L_LEAVES;
            }
        } else if (o <= arm) {
            p[LAMP_ARM_Y] = BlockCode.LAMP + slab;
            if (o == LAMP_ARM && lamp >= 0) {
                p[LAMP_ARM_Y - 1] = BlockCode.LAMP + lamp;
            } else if (o == LAMP_ARM && v == V_FALLEN) {
                p[LAMP_ARM_Y - 1] = BlockCode.LAMP + L_CHAIN;
            }
            for (int k : hanging) {
                if (k == o && p[LAMP_ARM_Y - 1] == BlockCode.KEEP) {
                    p[LAMP_ARM_Y - 1] = BlockCode.LAMP + L_VINE;
                }
            }
        }
        if (v == V_FALLEN && o == LAMP_ARM) {
            // the lamp, fallen on the lane under the end of the arm
            p[1] = BlockCode.LAMP + L_GRAY_GLASS;
        }
        boolean any = false;
        for (int c : p) {
            any |= c != BlockCode.KEEP;
        }
        return any ? p : null;
    }

    /** Whether a light part is a vine (no collision: the column stays drivable / the sidewalk walkable). */
    public static boolean lampVine(int code) {
        return code >= BlockCode.LAMP + L_VINE && code < BlockCode.LAMP + L_VINE + 5;
    }
}
