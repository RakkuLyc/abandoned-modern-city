package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Damage along a highway, a pure function of the highway and its profile: holes through the deck of the bridges,
 * about one per {@link #HOLE_EVERY} blocks of bridge, irregular, 5 to 7 blocks across and 4 to 5 along, in the middle
 * of the lanes. The tunnels stay clear (cave-ins were tried and dropped).
 * A site is located by its index {@code k} along the highway and its signed distance {@code across} from the
 * centerline (positive to the left of the direction of travel, as {@code Highway.project} gives it).
 */
public final class Wreck {
    private Wreck() {
    }

    public static final int HOLE_EVERY = 80;
    /** Blocks kept whole at both ends of a bridge or tunnel run. */
    static final int END_MARGIN = 8;

    /** One site: centered at {@code k} along and {@code across}, {@code halfLen} along and {@code halfWid} across. */
    public record Site(int k, double across, double halfLen, double halfWid, long seed) {
    }

    /** Holes of the bridge runs of a highway profile. */
    public static Site[] holes(long key, byte[] type) {
        List<Site> out = new ArrayList<>();
        for (int[] run : runs(type, Profile.BRIDGE)) {
            int len = run[1] - run[0] + 1 - 2 * END_MARGIN;
            long h = Hash.of(key, 0x484F4C45L, run[0]);
            int n = (int) Math.floor(len / (double) HOLE_EVERY + Hash.unit(h));
            for (int q = 0; q < n; q++) {
                long hq = Hash.next(h, q + 1);
                int k = run[0] + END_MARGIN + (int) ((q + Hash.range(Hash.next(hq, 1), 0.25, 0.75)) * len / n);
                double wid = Hash.range(Hash.next(hq, 2), 5, 7);
                double lng = Hash.range(Hash.next(hq, 3), 4, 5);
                // in the middle of the lanes (27 wide): the center stays 6 or less from the centerline
                double across = Hash.range(Hash.next(hq, 4), -6.0, 6.0);
                out.add(new Site(k, across, lng / 2, wid / 2, Hash.next(hq, 5)));
            }
        }
        return out.toArray(new Site[0]);
    }

    /** Runs {start, end} of consecutive points of a profile type, long enough to hold a site. */
    static List<int[]> runs(byte[] type, byte t) {
        List<int[]> out = new ArrayList<>();
        int k = 0;
        while (k < type.length) {
            if (type[k] != t) {
                k++;
                continue;
            }
            int s = k;
            while (k + 1 < type.length && type[k + 1] == t) {
                k++;
            }
            if (k - s + 1 > 2 * END_MARGIN + 8) {
                out.add(new int[]{s, k});
            }
            k++;
        }
        return out;
    }

    /**
     * Normalized distance of (k, across) from the site's middle, 1 at the edge. The edge is irregular but smooth
     * around the middle (a sum of waves of the angle), so the shape never leaves an island of road inside it.
     */
    static double shape(Site s, int k, double across) {
        double dk = (k - s.k()) / s.halfLen();
        double dd = (across - s.across()) / s.halfWid();
        double theta = Math.atan2(dd, dk);
        double p1 = Hash.unit(Hash.next(s.seed(), 1)) * 2 * Math.PI;
        double p2 = Hash.unit(Hash.next(s.seed(), 2)) * 2 * Math.PI;
        double rag = 1 + 0.18 * Math.sin(3 * theta + p1) + 0.10 * Math.sin(5 * theta + p2);
        return Math.sqrt(dk * dk + dd * dd) / rag;
    }

    /** Whether (k, across) of a bridge is in one of its holes. */
    public static boolean inHole(Site[] holes, int k, double across) {
        for (Site s : holes) {
            if (Math.abs(k - s.k()) <= s.halfLen() + 1 && shape(s, k, across) <= 1) {
                return true;
            }
        }
        return false;
    }
}
