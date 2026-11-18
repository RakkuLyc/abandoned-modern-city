package com.abandonedmoderncity.core;

/**
 * What the planner wants in each of the 256 columns of a chunk. Built by {@link WorldPlan#chunk}; read by the block
 * writer, the audits and the renders. Index: {@code (lz << 4) | lx}.
 */
public final class ChunkPlan {
    // kind
    public static final byte NONE = 0;
    /** A road surface (city street or highway; ground, bridge or tunnel). */
    public static final byte ROAD = 1;
    /** City ground at the city level (yard, park, plaza, building). */
    public static final byte LOT = 2;
    /** Natural terrain clamped to [lo, hi] (1:1 slopes around cities and highways). */
    public static final byte SKIRT = 3;
    /** Side wall of a highway tunnel. */
    public static final byte TUNNEL_WALL = 4;

    // zone of a road column
    public static final byte LANE = 1;
    public static final byte MARK = 2;
    public static final byte SIDEWALK = 3;
    public static final byte SHOULDER = 4;
    public static final byte PARAPET = 5;
    /** Highway center marking (yellow); city marks are {@link #MARK} (white). */
    public static final byte MARK_HIGHWAY = 6;
    /** White stripe of a zebra crossing (a city lane). */
    public static final byte CROSSWALK = 7;

    /** Whether a road zone carries a wall. */
    public static boolean walled(byte zone) {
        return zone == PARAPET;
    }

    // flags
    public static final byte F_CITY = 1;
    public static final byte F_HIGHWAY = 2;
    public static final byte F_PILLAR = 4;
    public static final byte F_LIGHT = 8;
    /** A hole through the deck of a bridge ({@link Wreck}): nothing there, not drivable. */
    public static final byte F_HOLE = 16;
    /** Street lights ({@link StreetLights#lampParts}): a column with a part of one (F_LAMP), and a column where a part stands
     *  on the street (the post, a fallen lamp: F_POLE, not drivable). */
    public static final byte F_POLE = 32;
    public static final byte F_LAMP = 64;

    public final int cx;
    public final int cz;
    public final byte[] kind = new byte[256];
    /** 2 x walking height (ROAD, LOT, TUNNEL_WALL). */
    public final short[] h2 = new short[256];
    public final byte[] zone = new byte[256];
    /** {@link Profile#GROUND}, {@link Profile#BRIDGE} or {@link Profile#TUNNEL}. */
    public final byte[] struct = new byte[256];
    public final byte[] flags = new byte[256];
    /** Allowed natural walking height range of a SKIRT column. */
    public final short[] lo = new short[256];
    public final short[] hi = new short[256];
    /** Lot of a LOT column (null for the yard between lots). */
    public final Lot[] lot = new Lot[256];
    /** Highway of a highway ROAD / TUNNEL_WALL column. */
    public final Highway[] highway = new Highway[256];
    /** City of this chunk, if any. */
    public City city;
    public boolean any;

    public ChunkPlan(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }

    public static int idx(int lx, int lz) {
        return (lz << 4) | lx;
    }

    /** Whether a road column can be driven / walked on (everything but the raised parapet). */
    public boolean drivable(int i) {
        return kind[i] == ROAD && !walled(zone[i]) && (flags[i] & (F_HOLE | F_POLE)) == 0;
    }

    /** y of the surface block of a road column (a full block, or a bottom slab when h2 is odd). */
    public static int surfaceY(int h2) {
        return (h2 & 1) == 0 ? h2 / 2 - 1 : (h2 - 1) / 2;
    }

    public static boolean isSlab(int h2) {
        return (h2 & 1) != 0;
    }
}
