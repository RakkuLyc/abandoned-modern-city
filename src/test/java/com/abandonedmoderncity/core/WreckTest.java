package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Holes through the bridges. */
class WreckTest {

    private static byte[] profile(int ground, int run, byte type) {
        byte[] t = new byte[2 * ground + run];
        for (int k = ground; k < ground + run; k++) {
            t[k] = type;
        }
        return t;
    }

    @Test
    void holesAreAboutOnePerEightyBlocksOfBridgeInTheMiddleOfTheLanes() {
        long holes = 0;
        long bridge = 0;
        for (long key = 0; key < 2000; key++) {
            int run = 60 + (int) (key % 400);
            byte[] t = profile(50, run, Profile.BRIDGE);
            Wreck.Site[] s = Wreck.holes(key, t);
            holes += s.length;
            bridge += run;
            for (Wreck.Site h : s) {
                assertTrue(h.k() >= 50 + Wreck.END_MARGIN && h.k() <= 50 + run - Wreck.END_MARGIN, "on the bridge");
                assertTrue(h.halfWid() >= 2.5 && h.halfWid() <= 3.5 && h.halfLen() >= 2 && h.halfLen() <= 2.5);
                // the whole hole stays within the 27 lanes
                assertTrue(Math.abs(h.across()) + h.halfWid() * 1.2 < Params.HIGHWAY_LANE_HW + 1);
                // its middle is a hole, the lanes 8 blocks away are not
                assertTrue(Wreck.inHole(s, h.k(), h.across()));
                assertTrue(!Wreck.inHole(new Wreck.Site[]{h}, h.k() + 8, h.across()));
            }
            assertTrue(Wreck.holes(key, profile(50, run, Profile.GROUND)).length == 0, "no holes off the bridges");
        }
        double every = bridge / (double) holes;
        System.out.println("[WreckTest] one hole per " + Math.round(every) + " blocks of bridge");
        assertTrue(every > 70 && every < 95, "every " + every);
    }
}
