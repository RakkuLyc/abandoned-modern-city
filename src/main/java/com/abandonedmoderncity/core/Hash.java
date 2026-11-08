package com.abandonedmoderncity.core;

/**
 * Stateless position hashing (SplitMix64 finalizer). Every random decision of the generator goes through here, so the
 * result never depends on chunk generation order or on threads.
 */
public final class Hash {
    private Hash() {
    }

    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public static long of(long seed, long salt, long a) {
        return mix(mix(seed ^ salt) + a * 0x9E3779B97F4A7C15L);
    }

    public static long of(long seed, long salt, long a, long b) {
        long h = mix(seed ^ salt);
        h = mix(h + a * 0x9E3779B97F4A7C15L);
        return mix(h + b * 0xC2B2AE3D27D4EB4FL);
    }

    public static long of(long seed, long salt, long a, long b, long c) {
        return mix(of(seed, salt, a, b) + c * 0x165667B19E3779F9L);
    }

    /** Derives a new hash from a previous one (a cheap stream of values from one seed). */
    public static long next(long h, int index) {
        return mix(h + (index + 1) * 0x27D4EB2F165667C5L);
    }

    /** Uniform double in [0, 1). */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Uniform int in [min, maxInclusive]. */
    public static int range(long h, int min, int maxInclusive) {
        if (maxInclusive <= min) {
            return min;
        }
        return min + (int) Math.floorMod(h, (long) (maxInclusive - min + 1));
    }

    /** Uniform double in [min, max). */
    public static double range(long h, double min, double max) {
        return min + unit(h) * (max - min);
    }
}
