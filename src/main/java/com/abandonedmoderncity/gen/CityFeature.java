package com.abandonedmoderncity.gen;

import com.abandonedmoderncity.AMCSimple;
import com.abandonedmoderncity.core.WorldPlan;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Entry point: placed once per chunk in every overworld biome at the {@code top_layer_modification} step (after
 * vanilla terrain features, trees and lakes of this chunk), it builds the whole chunk from its plan.
 */
public final class CityFeature extends Feature<NoneFeatureConfiguration> {
    /** Exceptions while generating (the self-test requires 0). */
    public static final AtomicInteger ERRORS = new AtomicInteger();
    public static final AtomicLong CHUNKS = new AtomicLong();
    public static final AtomicLong NANOS = new AtomicLong();

    public CityFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!(context.level() instanceof WorldGenRegion region)) {
            return false;
        }
        WorldPlan plan = Plans.get(region.getLevel());
        if (plan == null) {
            return false;
        }
        ChunkPos center = region.getCenter();
        ChunkAccess chunk = region.getChunk(center.x, center.z);
        long start = System.nanoTime();
        try {
            return ChunkBuilder.build(plan, region, chunk);
        } catch (RuntimeException e) {
            ERRORS.incrementAndGet();
            AMCSimple.LOGGER.error("[AMCS] Failed to generate chunk {}", center, e);
            return false;
        } finally {
            CHUNKS.incrementAndGet();
            NANOS.addAndGet(System.nanoTime() - start);
        }
    }
}
