package com.abandonedmoderncity.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What stands in the blocks of a {@link City}: the ring of structures against the sidewalks of every block (drawn for
 * the whole city at once under the limits per city of {@link Structures#LIMITS}), its 2 gas stations, whether it may
 * have the special structure, the park in a corner of some blocks and the nature that takes over the rest. Pure
 * functions of the city's seed; the lots of each block are cached by {@link City#lots}.
 */
public final class CityRings {
    private final City city;

    CityRings(City city) {
        this.city = city;
    }

    /** The lots of block (bi, bj) of the city (cached by {@link City#lots}). */
    List<Lot> computeLots(int bi, int bj) {
        int[] r = freeRect(bi, bj);
        return blockLots(bi, bj, r[0], r[1], r[2], r[3]);
    }

    /** Free rectangle {x0, z0, x1, z1} of block (bi, bj): the ground between its four sidewalks. */
    private int[] freeRect(int bi, int bj) {
        double hw = Params.STREET_HW;
        int x0 = (int) Math.round(city.U(bi) + hw);
        int x1 = (int) Math.round(city.U(bi + 1) - hw - 1);
        int z0 = (int) Math.round(city.V(bj) + hw);
        int z1 = (int) Math.round(city.V(bj + 1) - hw - 1);
        return new int[]{x0, z0, x1, z1};
    }

    /** Size of the park of a park block, along the block's long and short sides. */
    public static final int PARK_LONG = 110;
    public static final int PARK_SHORT = 64;

    /** The park of a park block, in a corner of its free rectangle f drawn from rs: {x0, z0, x1, z1}. */
    static int[] parkRect(int[] f, long rs) {
        int w = f[2] - f[0] + 1;
        int h = f[3] - f[1] + 1;
        int pw = Math.min(w, w >= h ? PARK_LONG : PARK_SHORT);
        int ph = Math.min(h, w >= h ? PARK_SHORT : PARK_LONG);
        int corner = Hash.range(Hash.next(rs, 4), 0, 3);
        int x0 = (corner & 1) == 0 ? f[0] : f[2] - pw + 1;
        int z0 = (corner & 2) == 0 ? f[1] : f[3] - ph + 1;
        return new int[]{x0, z0, x0 + pw - 1, z0 + ph - 1};
    }

    /** Whether block (bi, bj) has a park (in one of its corners). */
    private boolean parkBlock(int bi, int bj) {
        return Hash.unit(Hash.of(city.seed, 0x52494E47L, bi, bj)) < PARK_BLOCK_CHANCE;
    }

    /** The rings of structures of all the blocks of the city, keyed by {@link Cache#key} of the block (lazy). */
    private volatile Map<Long, List<Lot>> rings;

    /**
     * Rings of the whole city at once, block by block in a fixed order (north to south, west to east), so that the
     * spawn limits per city ({@link Structures#LIMITS}) hold whatever block is asked for first. A city that does not
     * draw {@link #SPECIAL_CHANCE} starts with the {@code special} group full: none of it is ever drawn there. Every
     * city has exactly 2 gas stations ({@link #gasStations}): the {@code gas_station} group starts full, so the rings
     * never draw one, and the 2 are placed first in their reserved rows.
     */
    private Map<Long, List<Lot>> rings() {
        Map<Long, List<Lot>> r = rings;
        if (r != null) {
            return r;
        }
        synchronized (this) {
            if (rings == null) {
                City.Layout L = city.layout();
                List<long[]> blocks = new ArrayList<>();
                for (int bj = -L.n; bj <= L.n; bj++) {
                    for (int bi = -L.n; bi <= L.n; bi++) {
                        if (L.isCity(bi, bj)) {
                            blocks.add(new long[]{bi, bj});
                        }
                    }
                }
                Ring.Forced[] gas = gasStations(blocks.size());
                Map<Long, List<Lot>> out = new HashMap<>();
                // a station still due after the last block (its row closed before its place, and no row after it):
                // the city is drawn again with it first in the first row of its block, then of the next block
                for (int attempt = 0; attempt <= 2 * blocks.size(); attempt++) {
                    Map<String, Integer> used = new HashMap<>();
                    if (!allowsSpecial()) {
                        used.put("special", Structures.LIMITS.get("special"));
                    }
                    // the 2 gas stations come only from their reserved rows, never from the draw of the rings
                    used.put("gas_station", Structures.LIMITS.get("gas_station"));
                    ArrayDeque<Ring.Forced> due = new ArrayDeque<>();
                    for (Ring.Forced g : gas) {
                        g.reset();
                    }
                    out = new HashMap<>();
                    for (int b = 0; b < blocks.size(); b++) {
                        int bi = (int) blocks.get(b)[0];
                        int bj = (int) blocks.get(b)[1];
                        for (Ring.Forced g : gas) {
                            if (g.block == b) {
                                due.add(g);
                            }
                        }
                        int[] f = freeRect(bi, bj);
                        long rs = Hash.of(city.seed, 0x52494E47L, bi, bj);
                        int[] park = parkBlock(bi, bj) ? parkRect(f, rs) : null;
                        out.put(Cache.key(bi, bj),
                                Ring.lots(f[0], f[1], f[2], f[3], Hash.next(rs, 2), used, park, due));
                    }
                    if (due.isEmpty()) {
                        break;
                    }
                    for (Ring.Forced g : due) {
                        if (g.planRow == 0 && g.planSlot == 0) {
                            g.block = (g.block + 1) % blocks.size();
                        }
                        g.planRow = 0;
                        g.planSlot = 0;
                    }
                }
                rings = Map.copyOf(out);
            }
            return rings;
        }
    }

    /**
     * Share of the cities that may have a structure of the {@code special} category (the ramp building), for ~1 city
     * in 10 with it. It only fits some rows, so a city that may have it gets it ~27% of the time:
     * over 2,838 distinct cities of 50 world seeds, 39.0% may have it and 10.5% have it (the cities of the tests, 170
     * distinct ones, 11.2%; 22.7% there before this draw). Only the cities that had it change: those that do not draw
     * it get other structures there. More special structures would raise the share: measure again and adjust.
     */
    public static final double SPECIAL_CHANCE = 0.40;

    /**
     * Whether this city's 2 gas stations are both small; else one small and one large (always 2 stations,
     * never 2 large ones; half the cities each).
     */
    public boolean twoSmallGasStations() {
        return Hash.unit(Hash.of(city.seed, 0x47415353L, 0)) < 0.5; // "GASS"
    }

    /**
     * The 2 gas stations of this city: small and small or small and large ({@link #twoSmallGasStations}), the color drawn, each in its own
     * block (2 different ones of the {@code blocks} of the city, in the order the rings are drawn), in a row drawn
     * from the 4 of its block and as its 1st or 2nd structure. One that does not fit there goes on to the next row (or
     * block; one still due after the last block is moved to the first place of its block, then of the next, and the
     * city drawn again: {@code rings}): {@code WorldPlanTest} checks that every city gets its 2.
     */
    private Ring.Forced[] gasStations(int blocks) {
        long h = Hash.of(city.seed, 0x47415353L, 1);
        boolean small = twoSmallGasStations();
        // which of the 2 is the large one (when there is one)
        int large = Hash.range(Hash.next(h, 1), 0, 1);
        if (blocks == 0) {
            return new Ring.Forced[0];
        }
        int first = Hash.range(Hash.next(h, 2), 0, blocks - 1);
        // a city of one block (none today: every city has its 2 x 2) gets both there
        int second = blocks == 1 ? 0 : Hash.range(Hash.next(h, 3), 0, blocks - 2);
        if (blocks > 1 && second >= first) {
            second++;
        }
        Ring.Forced[] out = new Ring.Forced[2];
        for (int i = 0; i < 2; i++) {
            long g = Hash.next(h, 10 + i);
            String type = !small && i == large ? "gas_station" : "gas_station_small";
            List<Structures.Def> colors = Structures.MODELS.stream()
                    .filter(m -> m.get(0).type().equals(type)).findFirst().orElseThrow();
            Structures.Def def = colors.get(Hash.range(Hash.next(g, 1), 0, colors.size() - 1));
            out[i] = new Ring.Forced(def, i == 0 ? first : second, Hash.range(Hash.next(g, 2), 0, 3),
                    Hash.range(Hash.next(g, 3), 0, 1));
        }
        return out;
    }

    /** Whether this city may have a structure of the {@code special} category ({@link #SPECIAL_CHANCE}). */
    public boolean allowsSpecial() {
        return Hash.unit(Hash.of(city.seed, 0x5350434CL, 0)) < SPECIAL_CHANCE;
    }

    /** Share of the city blocks with a park in a corner (grass, trees, paths, the fountain). */
    public static final double PARK_BLOCK_CHANCE = 0.125;
    /**
     * The lots of block (bi, bj), inside its free rectangle x0..x1, z0..z1 (whose outline touches the sidewalks): a ring of structures against the sidewalks ({@link Ring}, drawn at random
     * under the per-city limits of {@link Structures#LIMITS}); some blocks have a park of {@link #PARK_LONG} x
     * {@link #PARK_SHORT} in a corner, the structures then only along the two other streets; the rest is left to
     * nature ({@link Wild}).
     */
    private List<Lot> blockLots(int bi, int bj, int x0, int z0, int x1, int z1) {
        long rs = Hash.of(city.seed, 0x52494E47L, bi, bj);
        List<Lot> ring = rings().getOrDefault(Cache.key(bi, bj), List.of());
        List<Lot> out = new ArrayList<>(ring);
        if (parkBlock(bi, bj)) {
            // the park in a corner of the block, the structures along the two other streets
            int[] p = parkRect(new int[]{x0, z0, x1, z1}, rs);
            out.add(Lot.park(p[0], p[1], p[2], p[3], Hash.next(rs, 1)));
        }
        // the rest of the block is left to nature (listed last: the structures and the park win where they stand)
        List<Lot> obstacles = List.copyOf(out);
        out.add(Lot.wild(x0, z0, x1, z1, Hash.next(rs, 3), obstacles));
        return List.copyOf(out);
    }
}
