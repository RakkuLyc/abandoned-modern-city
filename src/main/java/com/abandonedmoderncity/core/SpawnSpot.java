package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Where the spawn of a new world goes (option {@code spawnNearCity}, {@code gen/SpawnPoint}): {@link #DISTANCE} blocks
 * outside the border streets of the city nearest to the spot the game picked, on dry ground, away from the highways and
 * from every other city. The game's spot is near 0, 0, which is always the corner of 4 world cells, the farthest place
 * from the cities in the middle of those cells (their streets ~235 blocks away, ~880 when none of the 4 has a city).
 * Pure function of the plan and the game's spot.
 */
public final class SpawnSpot {
    private SpawnSpot() {
    }

    /** From the border streets of the city (blocks): the city in view, its slopes ({@link Params#CITY_SKIRT}) far behind. */
    public static final double DISTANCE = 150;
    /** The cities looked at: those with a center at most this many cells from the game's spot, nearest first. */
    public static final int CELLS = 3;
    /** Points of the ring around a city, at even angles from its center (2 degrees apart, ~13 blocks). */
    static final int STEPS = 180;
    /**
     * Clearance from the slopes of a highway, by tier: first the whole square in which Outpost Zero looks for its
     * starter bunker (about 70 blocks around the spawn) 48 or more from them (its {@code REACH}, {@code gen/OutpostZero}),
     * then only the spawn itself 48 or more from them.
     */
    public static final double[] HIGHWAY_CLEAR = {48 + 70, 48};
    /** Dry: the spot is not water and at most {@link #WET_MAX} of the 8 points {@link #WET_R} around it are. */
    static final int WET_R = 24;
    static final int WET_MAX = 1;

    /** A spawn: the point, the city it is outside of, and the tier of {@link #HIGHWAY_CLEAR} it met. */
    public record Spot(double x, double z, City city, int tier) {
    }

    /**
     * The first spot, in order (the cities nearest to (x, z) first; around each the tiers in order, the points nearest
     * to (x, z) first), that is dry and passes {@code real} (the check of the world, called at most {@code tries}
     * times), or null.
     */
    public static Spot find(WorldPlan plan, double x, double z, Predicate<Spot> real, int tries) {
        double r = CELLS * plan.params.cellSize();
        List<City> cities = new ArrayList<>(plan.citiesIn(x - r, z - r, x + r, z + r));
        cities.sort(Comparator.<City>comparingDouble(c -> c.distanceOutside(x, z)).thenComparingLong(c -> c.id));
        int left = tries;
        for (City c : cities) {
            List<double[]> ring = ring(plan, c);
            ring.sort(Comparator.comparingDouble(p -> Math.hypot(p[0] - x, p[1] - z)));
            boolean[] taken = new boolean[ring.size()];
            for (int tier = 0; tier < HIGHWAY_CLEAR.length; tier++) {
                for (int k = 0; k < ring.size(); k++) {
                    double[] p = ring.get(k);
                    if (taken[k] || plan.nearHighway(p[0], p[1], HIGHWAY_CLEAR[tier])) {
                        continue;
                    }
                    taken[k] = true;
                    if (!dry(plan.terrain, p[0], p[1])) {
                        continue;
                    }
                    Spot s = new Spot(p[0], p[1], c, tier);
                    if (left-- <= 0) {
                        return null;
                    }
                    if (real.test(s)) {
                        return s;
                    }
                }
            }
        }
        return null;
    }

    /** The points {@link #DISTANCE} outside the border streets of c that are as far from every other city. */
    static List<double[]> ring(WorldPlan plan, City c) {
        List<double[]> out = new ArrayList<>();
        double reach = Params.influence() + DISTANCE;
        for (int k = 0; k < STEPS; k++) {
            double a = 2 * Math.PI * k / STEPS;
            double dx = StrictMath.cos(a);
            double dz = StrictMath.sin(a);
            // the distance outside the streets grows along the ray (the city is a rectangle): halve to DISTANCE
            double lo = 0;
            double hi = 2 * reach;
            for (int i = 0; i < 40; i++) {
                double t = (lo + hi) / 2;
                if (c.distanceOutside(c.cx + dx * t, c.cz + dz * t) < DISTANCE) {
                    lo = t;
                } else {
                    hi = t;
                }
            }
            double px = Math.floor(c.cx + dx * hi) + 0.5;
            double pz = Math.floor(c.cz + dz * hi) + 0.5;
            boolean clear = true;
            for (City o : plan.citiesIn(px - reach, pz - reach, px + reach, pz + reach)) {
                if (o != c && o.distanceOutside(px, pz) < DISTANCE) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                out.add(new double[]{px, pz});
            }
        }
        return out;
    }

    /** Whether (x, z) is dry ground (see {@link #WET_MAX}). */
    static boolean dry(Terrain t, double x, double z) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        if (t.water(ix, iz)) {
            return false;
        }
        int wet = 0;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            if (t.water(ix + (int) Math.round(StrictMath.cos(a) * WET_R), iz + (int) Math.round(StrictMath.sin(a) * WET_R))) {
                wet++;
            }
        }
        return wet <= WET_MAX;
    }
}
