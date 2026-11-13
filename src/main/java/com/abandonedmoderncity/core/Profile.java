package com.abandonedmoderncity.core;

/**
 * Height profile of a highway: a sequence of flat stretches joined by ramps.
 *
 * <p>Heights are in eighths of a block, one value per block of path. Guarantees (checked by the unit tests):
 * <ul>
 *   <li>it starts at the first city's level and ends at the second's, flat for {@code flat} blocks at both ends;</li>
 *   <li>consecutive values differ by at most 1 (a ramp rises one block every {@link Params#RAMP_RUN} = 8 blocks, so
 *   the surface goes up half a block every 4 blocks);</li>
 *   <li>flat stretches are at whole block heights (no long runs of slabs);</li>
 *   <li>it never goes below {@code floor} (bridge decks stay above the water).</li>
 * </ul>
 * The profile follows a smoothed terrain target but only starts a ramp when the target is at least {@code deadband}
 * blocks away, so the road stays flat across small hills and dips; higher ground is crossed in a tunnel and lower
 * ground on a bridge (see {@link #classify}).
 */
public final class Profile {
    public static final int E = 8;

    private Profile() {
    }

    /** Whether a path of {@code length} blocks can join the two levels with ramps of the standard grade. */
    public static boolean feasible(int length, int startLevel, int endLevel, int flat) {
        return Math.abs(startLevel - endLevel) * E <= length - 2 * flat;
    }

    /**
     * @param length     L, the path has L + 1 points (s = 0..L)
     * @param target     desired walking height per point (blocks), length L + 1
     * @param deadband   minimum distance (blocks) between the road and the target before a ramp starts
     * @param floor      lowest walking height allowed
     * @return walking height in eighths of a block per point
     */
    public static int[] build(int length, int startLevel, int endLevel, double[] target, int flat, int deadband, int floor) {
        int a8 = startLevel * E;
        int b8 = endLevel * E;
        int f8 = floor * E;
        int db = deadband * E;
        int[] he = new int[length + 1];
        he[0] = a8;
        int dir = 0;
        for (int s = 1; s <= length; s++) {
            int fromStart = Math.max(0, s - flat);
            int fromEnd = Math.max(0, length - flat - s);
            int lo = Math.max(a8 - fromStart, b8 - fromEnd);
            int hi = Math.min(a8 + fromStart, b8 + fromEnd);
            lo = Math.max(lo, Math.min(f8, hi));
            int cur = he[s - 1];
            int tgt = clamp((int) Math.round(target[s]) * E, lo, hi);
            if (dir == 0 && cur % E != 0) {
                // never rest between two whole heights
                dir = tgt > cur ? 1 : -1;
            }
            if (dir == 0) {
                if (tgt - cur >= db) {
                    dir = 1;
                } else if (cur - tgt >= db) {
                    dir = -1;
                }
            } else if (dir > 0) {
                if (Math.floorMod(cur, E) == 0 && cur >= tgt) {
                    dir = 0;
                }
            } else if (Math.floorMod(cur, E) == 0 && cur <= tgt) {
                dir = 0;
            }
            int next = clamp(cur + dir, lo, hi);
            if (next == cur && Math.floorMod(cur, E) != 0) {
                // the band blocks the ramp between two whole heights (the top of a peak or the bottom of a dip
                // forced by the cones): turn around instead of resting at a fractional height
                if (cur + 1 <= hi && cur + 1 >= lo && tgt > cur) {
                    next = cur + 1;
                } else if (cur - 1 >= lo && cur - 1 <= hi) {
                    next = cur - 1;
                } else if (cur + 1 <= hi && cur + 1 >= lo) {
                    next = cur + 1;
                }
            }
            if (next != cur + dir) {
                dir = Integer.signum(next - cur);
            }
            he[s] = next;
        }
        return he;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static final byte GROUND = 0;
    public static final byte BRIDGE = 1;
    public static final byte TUNNEL = 2;

    /** No tunnel or bridge in the first and last blocks: the highway joins the street on the ground. */
    public static final int JUNCTION = 16;
    public static final int MIN_TUNNEL = 16;
    public static final int MIN_DRY_BRIDGE = 8;
    public static final int TUNNEL_GAP = 24;
    public static final int BRIDGE_GAP = 16;

    /**
     * Ground, bridge or tunnel per point. Raw decision from the cover (ground above the road) and the gap (road
     * above the ground), then cleaned: short ground gaps inside a tunnel or between two bridges are absorbed, too
     * short tunnels become cuts and too short dry bridges become embankments (bridges over water always stay).
     */
    public static byte[] classify(int[] he, double[] ground, boolean[] water) {
        int n = he.length;
        byte[] t = new byte[n];
        for (int s = 0; s < n; s++) {
            double walk = he[s] / (double) E;
            if (water[s] || walk - ground[s] >= Params.BRIDGE_GAP) {
                t[s] = BRIDGE;
            } else if (ground[s] - walk >= Params.TUNNEL_COVER) {
                t[s] = TUNNEL;
            } else {
                t[s] = GROUND;
            }
        }
        forceJunctions(t);
        fillGaps(t, TUNNEL, TUNNEL_GAP);
        fillGaps(t, BRIDGE, BRIDGE_GAP);
        dropShort(t, TUNNEL, MIN_TUNNEL, null);
        dropShort(t, BRIDGE, MIN_DRY_BRIDGE, water);
        forceJunctions(t);
        return t;
    }

    private static void forceJunctions(byte[] t) {
        int n = t.length;
        for (int s = 0; s < n; s++) {
            if (s < JUNCTION || s > n - 1 - JUNCTION) {
                t[s] = GROUND;
            }
        }
    }

    /** Turns runs of GROUND shorter than maxGap lying between two runs of {@code type} into {@code type}. */
    static void fillGaps(byte[] t, byte type, int maxGap) {
        int n = t.length;
        int s = 0;
        while (s < n) {
            if (t[s] != type) {
                s++;
                continue;
            }
            int end = s;
            while (end < n && t[end] == type) {
                end++;
            }
            // [end, g) is the gap after this run
            int g = end;
            while (g < n && t[g] == GROUND) {
                g++;
            }
            if (g < n && t[g] == type && g - end < maxGap) {
                for (int k = end; k < g; k++) {
                    t[k] = type;
                }
                // continue from the same run start so chains of short gaps merge
                continue;
            }
            s = end;
        }
    }

    /** Turns runs of {@code type} shorter than minLen into GROUND, unless the run has water under it. */
    static void dropShort(byte[] t, byte type, int minLen, boolean[] water) {
        int n = t.length;
        int s = 0;
        while (s < n) {
            if (t[s] != type) {
                s++;
                continue;
            }
            int end = s;
            boolean wet = false;
            while (end < n && t[end] == type) {
                if (water != null && water[end]) {
                    wet = true;
                }
                end++;
            }
            if (end - s < minLen && !wet) {
                for (int k = s; k < end; k++) {
                    t[k] = GROUND;
                }
            }
            s = end;
        }
    }

    /** Pillar positions under bridges: every PILLAR_EVERY blocks of path, 3 blocks thick, away from the run ends. */
    public static boolean[] pillars(byte[] t) {
        int n = t.length;
        boolean[] p = new boolean[n];
        int s = 0;
        while (s < n) {
            if (t[s] != BRIDGE) {
                s++;
                continue;
            }
            int end = s;
            while (end < n && t[end] == BRIDGE) {
                end++;
            }
            int first = ((s + 3 + Params.PILLAR_EVERY - 1) / Params.PILLAR_EVERY) * Params.PILLAR_EVERY;
            for (int m = first; m <= end - 1 - 3; m += Params.PILLAR_EVERY) {
                for (int k = m - 1; k <= m + 1; k++) {
                    if (k >= s && k < end) {
                        p[k] = true;
                    }
                }
            }
            s = end;
        }
        return p;
    }
}
