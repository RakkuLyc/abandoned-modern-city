package com.abandonedmoderncity.core;

/**
 * Natural terrain as the planner sees it, before anything is built. In game this is the chunk generator's noise
 * ({@code getBaseHeight}), which answers for any column, generated or not; in tests it is a synthetic function.
 * Implementations must be pure (same answer for the same column, on any thread).
 */
public interface Terrain {
    int seaLevel();

    /** Walking height of the natural ground: y of the top solid block + 1 (water is not ground). */
    int ground(int x, int z);

    /** Whether water lies on the ground at this column (ocean, river). */
    boolean water(int x, int z);
}
