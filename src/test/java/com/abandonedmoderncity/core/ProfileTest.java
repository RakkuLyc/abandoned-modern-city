package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Properties of the highway height profile and of the ground/bridge/tunnel classification, on random terrain. */
class ProfileTest {

    private static double[] randomTarget(long seed, int length, int base) {
        double[] t = new double[length + 1];
        double v = base;
        for (int s = 0; s <= length; s++) {
            long h = Hash.of(seed, 11, s);
            v += (Hash.unit(h) - 0.5) * 3;
            if (Hash.unit(Hash.next(h, 1)) < 0.004) {
                v += (Hash.unit(Hash.next(h, 2)) - 0.5) * 80; // cliffs
            }
            v = Math.max(40, Math.min(220, v));
            t[s] = v;
        }
        return t;
    }

    @Test
    void profilesAreFlatOrRampAndJoinTheCities() {
        int checked = 0;
        long ramps = 0;
        for (long seed = 0; seed < 4000; seed++) {
            long h = Hash.of(seed, 1, 2);
            int length = Hash.range(h, 2 * Params.FLAT_END + 32, 1800);
            int a = Hash.range(Hash.next(h, 1), 65, 150);
            int maxDiff = (length - 2 * Params.FLAT_END) / Profile.E;
            int b = a + Hash.range(Hash.next(h, 2), -Math.min(maxDiff, 80), Math.min(maxDiff, 80));
            b = Math.max(65, Math.min(150, b));
            if (!Profile.feasible(length, a, b, Params.FLAT_END)) {
                continue;
            }
            double[] target = randomTarget(seed, length, a);
            int floor = 65;
            int[] he = Profile.build(length, a, b, target, Params.FLAT_END, 5, floor);
            assertEquals(a * 8, he[0], "start, seed " + seed);
            assertEquals(b * 8, he[length], "end, seed " + seed);
            for (int s = 0; s <= length; s++) {
                if (s <= Params.FLAT_END) {
                    assertEquals(a * 8, he[s], "flat start at " + s + ", seed " + seed);
                }
                if (s >= length - Params.FLAT_END) {
                    assertEquals(b * 8, he[s], "flat end at " + s + ", seed " + seed);
                }
                assertTrue(he[s] >= floor * 8, "below floor at " + s + ", seed " + seed);
                if (s > 0) {
                    int step = Math.abs(he[s] - he[s - 1]);
                    assertTrue(step <= 1, "grade at " + s + ", seed " + seed);
                    if (step == 0) {
                        assertEquals(0, Math.floorMod(he[s], 8), "fractional flat at " + s + ", seed " + seed);
                    } else {
                        ramps++;
                    }
                }
            }
            // the rendered surface (half blocks) never changes by more than one half block between neighbors
            for (int s = 1; s <= length; s++) {
                int h2a = Math.floorDiv(he[s - 1] + 2, 4);
                int h2b = Math.floorDiv(he[s] + 2, 4);
                assertTrue(Math.abs(h2a - h2b) <= 1);
            }
            checked++;
        }
        assertTrue(checked > 3000, "checked " + checked);
        System.out.println("[ProfileTest] profiles checked: " + checked + ", ramp points: " + ramps);
    }

    @Test
    void halfBlockStepsAreEvenlySpaced() {
        // a straight climb from 70 to 90: the surface rises half a block every 4 blocks, never two in a row
        int length = 400;
        double[] target = new double[length + 1];
        java.util.Arrays.fill(target, 90);
        int[] he = Profile.build(length, 70, 90, target, Params.FLAT_END, 5, 65);
        int last = -1;
        for (int s = 1; s <= length; s++) {
            int h2a = Math.floorDiv(he[s - 1] + 2, 4);
            int h2b = Math.floorDiv(he[s] + 2, 4);
            if (h2a != h2b) {
                if (last >= 0) {
                    assertTrue(s - last >= 4, "steps " + last + " and " + s + " closer than 4 blocks");
                }
                last = s;
            }
        }
    }

    @Test
    void classificationRunsAreClean() {
        for (long seed = 0; seed < 2000; seed++) {
            int length = 600;
            double[] ground = randomTarget(seed * 7 + 3, length, 80);
            boolean[] water = new boolean[length + 1];
            for (int s = 0; s <= length; s++) {
                water[s] = ground[s] < 62;
            }
            int[] he = Profile.build(length, 80, 84, ground, Params.FLAT_END, 5, 65);
            byte[] t = Profile.classify(he, ground, water);
            for (int s = 0; s <= length; s++) {
                if (s < Profile.JUNCTION || s > length - Profile.JUNCTION) {
                    assertEquals(Profile.GROUND, t[s], "junction must be ground, seed " + seed);
                }
            }
            int s = 0;
            while (s <= length) {
                int e = s;
                while (e <= length && t[e] == t[s]) {
                    e++;
                }
                int len = e - s;
                if (t[s] == Profile.TUNNEL) {
                    assertTrue(len >= Profile.MIN_TUNNEL, "short tunnel " + len + ", seed " + seed);
                }
                if (t[s] == Profile.BRIDGE) {
                    boolean wet = false;
                    for (int k = s; k < e; k++) {
                        wet |= water[k];
                    }
                    assertTrue(wet || len >= Profile.MIN_DRY_BRIDGE, "short dry bridge " + len + ", seed " + seed);
                }
                s = e;
            }
            for (int k = 0; k <= length; k++) {
                if (water[k] && k >= Profile.JUNCTION && k <= length - Profile.JUNCTION) {
                    assertEquals(Profile.BRIDGE, t[k], "water must be bridged, seed " + seed + " at " + k);
                }
            }
        }
    }
}
