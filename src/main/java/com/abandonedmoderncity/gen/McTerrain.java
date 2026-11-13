package com.abandonedmoderncity.gen;

import com.abandonedmoderncity.core.Cache;
import com.abandonedmoderncity.core.Terrain;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * The natural terrain as the chunk generator's noise describes it ({@code getBaseHeight}): available for any column,
 * generated or not, identical on every thread, and unaffected by what the mod builds. That is what lets every chunk
 * agree with its neighbours about cities and highways.
 */
public final class McTerrain implements Terrain {
    /** Noise samples taken (each one builds a noise column in the game, ~1 ms) and time spent, for the self-test. */
    public static final AtomicLong SAMPLES = new AtomicLong();
    public static final AtomicLong NANOS = new AtomicLong();
    private final ChunkGenerator generator;
    private final RandomState random;
    private final LevelHeightAccessor heights;
    private final int sea;
    private final Cache<Integer> cache = new Cache<>(1 << 18);

    McTerrain(ChunkGenerator generator, RandomState random, LevelHeightAccessor heights) {
        this.generator = generator;
        this.random = random;
        this.heights = heights;
        this.sea = generator.getSeaLevel();
    }

    @Override
    public int seaLevel() {
        return sea;
    }

    private int sample(int x, int z) {
        return cache.get(Cache.key(x, z), k -> {
            long t0 = System.nanoTime();
            int ground = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random);
            boolean water = false;
            if (ground < sea) {
                // ocean and river floors lie below the sea; the surface (water included) tells whether it is wet
                water = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, random) > ground;
            }
            SAMPLES.incrementAndGet();
            NANOS.addAndGet(System.nanoTime() - t0);
            return (ground << 1) | (water ? 1 : 0);
        });
    }

    @Override
    public int ground(int x, int z) {
        return sample(x, z) >> 1;
    }

    @Override
    public boolean water(int x, int z) {
        return (sample(x, z) & 1) != 0;
    }
}
