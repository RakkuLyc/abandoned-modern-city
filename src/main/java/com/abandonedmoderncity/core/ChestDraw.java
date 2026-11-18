package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The chests of one copy of a building (types {@code buildings_*}), drawn among the spots of its {@link Rooms}. A pure function of the copy's seed (the lot's, or a
 * random one for {@code /amcs spawn}) and of the template: it does not depend on the rotation, the world position or
 * the order in which the chunks are generated.
 * <ol>
 *   <li><b>How many:</b> drawn evenly in the range of the building's height class ({@link #range}): small 12–28, medium
 *   25–47, large 38–65, immense 51–83. The chests of the barrels do not count.</li>
 *   <li><b>Per floor:</b> in proportion to the floor's weight ({@link Rooms.Layout#floorWeight}: how many chests fit
 *   on it {@link Rooms#SPACING} apart, a spot of a lobby open onto the street counting half), largest remainders first.</li>
 *   <li><b>Which spots:</b> a weighted draw without replacement (each spot gets the key {@code -ln(u) / weight}, u from
 *   the seed and its cell; the smallest keys win), the weight of the spot (2 against a wall, 3 in a corner, +1 beside
 *   furniture) halved for a street spot. A spot is skipped when it is closer than
 *   {@link Rooms#SPACING} (sideways, within 2 heights) to a chest already taken, when its floor has its share, or when
 *   its zone already has {@link #ZONE_CAP} chests (a small room is one zone; a hall is cut into zones of at most
 *   {@link Rooms#ZONE_MAX} cells).</li>
 *   <li><b>When it does not fit:</b> the zone cap is raised one by one (up to 4), then the floor shares are dropped
 *   (zone cap up to 8), then the zone cap goes; the spacing always holds. The count is always reached in the 10
 *   buildings ({@code ChestDrawTest}).</li>
 *   <li><b>Exploration:</b> at least {@link #EXPLORATION_MIN} of the chests in a place that asks for exploring (far on
 *   its floor, under the open sky, or hard to get to): a minimum, nothing is reserved for them; the plain draw already
 *   gives 25 to 43%, and a copy under the minimum is drawn again with those spots first.</li>
 * </ol>
 */
public final class ChestDraw {
    private ChestDraw() {
    }

    /** At least this share of the chests of a copy in an exploration place (far, open sky or hard). */
    public static final double EXPLORATION_MIN = 0.15;
    /** At most this many chests per zone, before the cap is raised. */
    public static final int ZONE_CAP = 1;
    /** Weight of a spot of a lobby open onto the street, against the others. */
    public static final double STREET_FACTOR = 0.5;
    private static final long SALT_COUNT = 0x43484E54L; // "CHNT"
    private static final long SALT_KEY = 0x43484B59L; // "CHKY"

    /** The range of chests of a height class ({@code small}, {@code medium}, {@code large}, {@code immense}). */
    public static int[] range(String heightClass) {
        return switch (heightClass) {
            case "small" -> new int[]{12, 28};
            case "medium" -> new int[]{25, 47};
            case "large" -> new int[]{38, 65};
            case "immense" -> new int[]{51, 83};
            default -> throw new IllegalArgumentException("height class " + heightClass);
        };
    }

    /** How many chests the copy of seed {@code seed} gets, for a building of the height class. */
    public static int count(String heightClass, long seed) {
        int[] r = range(heightClass);
        return Hash.range(Hash.of(seed, SALT_COUNT, 0), r[0], r[1]);
    }

    /** Whether a spot is an exploration place: far on its floor, under the open sky, or hard to get to. */
    public static boolean exploration(Rooms.Spot s) {
        return s.is(Rooms.FAR) || s.is(Rooms.OPEN) || s.is(Rooms.HARD);
    }

    /**
     * The chests of the copy of seed {@code seed} of a building of the height class whose template is {@code sx} by
     * {@code sz} (to number the cells), among the spots of {@code layout}: the spots taken, in the order they were taken.
     */
    public static List<Rooms.Spot> draw(Rooms.Layout layout, String heightClass, int sx, int sz, long seed) {
        if (layout.spots().isEmpty()) {
            return List.of();
        }
        int n = count(heightClass, seed);
        int[] quota = quotas(layout.floorWeight(), n);
        List<Rooms.Spot> order = new ArrayList<>(layout.spots());
        Map<Rooms.Spot, Double> key = new HashMap<>();
        for (Rooms.Spot s : order) {
            long cell = s.x() + (long) sx * (s.z() + (long) sz * s.y());
            double u = Math.max(Hash.unit(Hash.of(seed, SALT_KEY, cell)), 1e-12);
            key.put(s, -Math.log(u) / (s.weight() * (s.is(Rooms.STREET) ? STREET_FACTOR : 1)));
        }
        order.sort(Comparator.comparingDouble((Rooms.Spot s) -> key.get(s)).thenComparingInt(Rooms.Spot::y)
                .thenComparingInt(Rooms.Spot::z).thenComparingInt(Rooms.Spot::x));
        List<Rooms.Spot> out = fill(order, quota, n, 0);
        int least = (int) Math.ceil(EXPLORATION_MIN * n);
        if (out.stream().filter(ChestDraw::exploration).count() < least) {
            out = fill(order, quota, n, least);
        }
        return List.copyOf(out);
    }

    /** The floor shares of {@code n} chests, in proportion to the weights, largest remainders first. */
    static int[] quotas(double[] weight, int n) {
        int nf = weight.length;
        double total = Arrays.stream(weight).sum();
        int[] q = new int[nf];
        if (total <= 0) {
            return q;
        }
        double[] rest = new double[nf];
        int sum = 0;
        for (int f = 0; f < nf; f++) {
            double e = n * weight[f] / total;
            q[f] = (int) Math.floor(e);
            rest[f] = e - q[f];
            sum += q[f];
        }
        Integer[] byRest = new Integer[nf];
        for (int f = 0; f < nf; f++) {
            byRest[f] = f;
        }
        Arrays.sort(byRest, (a, b) -> rest[a] != rest[b] ? Double.compare(rest[b], rest[a]) : a - b);
        for (int i = 0; sum < n; i++) {
            int f = byRest[i % nf];
            if (weight[f] > 0) {
                q[f]++;
                sum++;
            }
        }
        return q;
    }

    /** The draw itself: {@code first} exploration spots before the others (0: none put first). */
    private static List<Rooms.Spot> fill(List<Rooms.Spot> order, int[] quota, int n, int first) {
        Taken t = new Taken(quota.length);
        for (int cap = ZONE_CAP; cap <= 3 && t.explore < first; cap++) {
            for (Rooms.Spot s : order) {
                if (t.explore >= first || t.list.size() >= n) {
                    break;
                }
                if (exploration(s)) {
                    t.take(s, quota, cap, true);
                }
            }
        }
        for (int cap = ZONE_CAP; cap <= 4 && t.list.size() < n; cap++) {
            for (Rooms.Spot s : order) {
                if (t.list.size() >= n) {
                    break;
                }
                t.take(s, quota, cap, true);
            }
        }
        for (int cap = ZONE_CAP; cap <= 8 && t.list.size() < n; cap++) {
            for (Rooms.Spot s : order) {
                if (t.list.size() >= n) {
                    break;
                }
                t.take(s, quota, cap, false);
            }
        }
        for (Rooms.Spot s : order) {
            if (t.list.size() >= n) {
                break;
            }
            t.take(s, quota, Integer.MAX_VALUE, false);
        }
        return t.list;
    }

    private static final class Taken {
        final List<Rooms.Spot> list = new ArrayList<>();
        final int[] perFloor;
        final Map<Integer, Integer> perZone = new HashMap<>();
        int explore;

        Taken(int floors) {
            perFloor = new int[floors];
        }

        void take(Rooms.Spot s, int[] quota, int cap, boolean shares) {
            if (shares && perFloor[s.floor()] >= quota[s.floor()] || perZone.getOrDefault(s.zone(), 0) >= cap) {
                return;
            }
            for (Rooms.Spot o : list) {
                if (o == s || Math.abs(o.x() - s.x()) < Rooms.SPACING && Math.abs(o.z() - s.z()) < Rooms.SPACING
                        && Math.abs(o.y() - s.y()) <= 2) {
                    return;
                }
            }
            list.add(s);
            perFloor[s.floor()]++;
            perZone.merge(s.zone(), 1, Integer::sum);
            explore += exploration(s) ? 1 : 0;
        }
    }
}
