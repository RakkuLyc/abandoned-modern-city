package com.abandonedmoderncity.core;

/**
 * Everything that shapes the generator. The record holds the few values exposed in the config; the constants below
 * are the fixed geometry (widths, slopes, clearances). Change them here and every part of the planner follows.
 *
 * <h2>Heights</h2>
 * A "walking height" is the y a player stands on: the y of the top full block + 1, or y + 0.5 on a bottom slab.
 * Road heights are kept as {@code h2 = 2 * walking height} (an integer), so half-block ramps are exact.
 *
 * @param cellSize         side of the square world cell that holds at most one city (blocks)
 * @param cityChance       probability that a cell holds a city
 * @param minRadius        smallest city radius (blocks)
 * @param maxRadius        largest city radius (blocks)
 * @param maxCityLevel     highest walking height a city may sit at
 * @param highwayMaxLength longest highway path (blocks)
 * @param highways         whether cities are linked by highways
 */
public record Params(int cellSize, double cityChance, int minRadius, int maxRadius, int maxCityLevel,
                     int highwayMaxLength, boolean highways) {

    public static final Params DEFAULT = new Params(736, 0.7, 128, 230, 150, 4000, true);

    // ------------------------------------------------------------------ city grid

    /**
     * City blocks are BLOCK_LONG_RUN x BLOCK_SHORT_RUN (180 x 120) of free ground between the sidewalks; their street
     * centerlines are a street width further apart (213 and 153). Each city rolls whether the long side runs east-west.
     */
    public static final int BLOCK_LONG_RUN = 180;
    public static final int BLOCK_SHORT_RUN = 120;
    public static final int BLOCK_LONG = BLOCK_LONG_RUN + 33;
    public static final int BLOCK_SHORT = BLOCK_SHORT_RUN + 33;
    /** Every city is GRID x GRID blocks ((GRID + 1) x (GRID + 1) streets). */
    public static final int GRID = 2;
    /** Dash / gap of the street center marking. */
    public static final int DASH = 11;
    public static final int DASH_GAP = 6;
    /** Radius of the curved corners on the outside of the city (convex corners of the street ring). */
    public static final double ARC_RADIUS = 24;
    /** Largest amplitude of the gentle street waves (blocks) and the shortest wavelength. */
    public static final double MAX_WARP = 10;
    public static final double MIN_WAVELENGTH = 288;
    /** Every third grid line is an avenue. */
    public static final int AVENUE_EVERY = 3;

    // ------------------------------------------------------------------ cross sections (half widths from the centerline)

    /** City street: smooth stone sidewalk 3 + polished blackstone 27 (center mark on the 17th block) + sidewalk 3 = 33. */
    public static final double STREET_HW = 16.5;
    public static final double STREET_LANE_HW = 13.5;
    /** There are no wider avenues any more: same section as the streets. */
    public static final double AVENUE_HW = STREET_HW;
    public static final double AVENUE_LANE_HW = STREET_LANE_HW;
    /**
     * Highway, as wide as a city street (33): smooth stone 3 (the outer one with a wall on top) + polished blackstone 27
     * (yellow center mark) + smooth stone 3 (the outer one with a wall).
     */
    public static final double HIGHWAY_HW = STREET_HW;
    public static final double HIGHWAY_LANE_HW = STREET_LANE_HW;
    /** Smooth stone columns between the lanes and the edge column with the wall. */
    public static final int HIGHWAY_SHOULDER = 2;
    /** Tunnel side walls: the column just outside the highway band. */
    public static final double TUNNEL_WALL_HW = HIGHWAY_HW + 1;
    /** Center marking half width. */
    public static final double MARK_HW = 0.5;

    // ------------------------------------------------------------------ terrain shaping

    /** Slopes (1:1) around the city reach this far beyond the outer street edge. */
    public static final int CITY_SKIRT = 32;
    /** Slopes (1:1) beside a highway on the ground reach this far beyond its edge. */
    public static final int HIGHWAY_SKIRT = 20;
    /** City blocks are only built where the terrain is within [level - FILL, level + CUT]. */
    public static final int CITY_MAX_FILL = 10;
    public static final int CITY_MAX_CUT = 14;
    /** Gap between the outer street edge and lots. */
    public static final double LOT_SETBACK = 1.0;

    // ------------------------------------------------------------------ highways

    /** Ramps rise 1 block every RAMP_RUN blocks (half a block every RAMP_RUN / 2). */
    public static final int RAMP_RUN = 8;
    /** Highways stay at the city level for this many blocks after leaving a city. */
    public static final int FLAT_END = 48;
    /** Highways run in a tunnel where the ground is at least this many blocks above the walking height. */
    public static final int TUNNEL_COVER = 11;
    /** Highways run on a bridge where the walking height is at least this many blocks above the ground. */
    public static final int BRIDGE_GAP = 7;
    /** Free height above a road (air blocks above the surface block); tunnels have their ceiling right above. */
    public static final int CLEARANCE = 7;
    /** Smallest curve radius of a highway centerline. */
    public static final double MIN_CURVE_RADIUS = 60;
    /** Highways never get closer than this (centerline to centerline) to each other. */
    public static final double HIGHWAY_SEPARATION = 2 * HIGHWAY_HW + 6;
    /** Highways keep this distance (beyond the street edge) from cities they do not serve. */
    public static final double FOREIGN_CITY_CLEARANCE = 24;
    /** Longest straight-line distance between two linked cities. */
    public static final double MAX_LINK = 3600;
    /** Pillars under bridges every PILLAR_EVERY blocks; tunnel lights every LIGHT_EVERY blocks. */
    public static final int PILLAR_EVERY = 24;
    public static final int LIGHT_EVERY = 12;
    /** Highway decks never go above this walking height (higher ground is crossed in a tunnel). */
    public static final int MAX_HIGHWAY_LEVEL = 180;

    public Params {
        if (cellSize < 2 * margin(maxRadius) + 64) {
            throw new IllegalArgumentException("cellSize " + cellSize + " is too small for maxRadius " + maxRadius
                    + " (needs at least " + (2 * margin(maxRadius) + 64) + ")");
        }
        if (minRadius < 1 || minRadius > maxRadius) {
            throw new IllegalArgumentException("bad city radius range " + minRadius + ".." + maxRadius);
        }
    }

    /**
     * Farthest distance from a city center at which the city changes anything (lots, streets, slopes): the corner of
     * the rectangular city (half diagonal: 286 for 230 x 170), then waves and slopes. Cities of another radius
     * (config) use 1.2 x radius.
     */
    public static double influence(int radius) {
        double corner = radius == GRID_RADIUS ? Math.hypot(GRID_RADIUS, GRID_HALF_SHORT) : radius * 1.2;
        return corner + MAX_WARP + CITY_SKIRT + 4;
    }

    /** Half the long side of a city, streets included: 230 for the 2 x 2 grid (the short side: 170). */
    public static final int GRID_RADIUS = GRID * BLOCK_LONG / 2 + (int) STREET_HW + 1;
    public static final int GRID_HALF_SHORT = GRID * BLOCK_SHORT / 2 + (int) STREET_HW + 1;

    /** City centers are kept this far from the cell edges, so the cities of two cells never overlap. */
    public static int margin(int maxRadius) {
        return (int) Math.ceil(influence(Math.max(maxRadius, GRID_RADIUS))) + 2;
    }

    public int margin() {
        return margin(maxRadius);
    }
}
