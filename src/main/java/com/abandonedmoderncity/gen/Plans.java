package com.abandonedmoderncity.gen;

import com.abandonedmoderncity.AbandonedModernCity;
import com.abandonedmoderncity.AmcsConfig;
import com.abandonedmoderncity.core.Params;
import com.abandonedmoderncity.core.WorldPlan;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** One {@link WorldPlan} per dimension (only the overworld gets cities); dropped on config reload and server stop. */
public final class Plans {
    private static final ConcurrentHashMap<ResourceKey<Level>, Optional<WorldPlan>> PLANS = new ConcurrentHashMap<>();

    private Plans() {
    }

    public static WorldPlan get(ServerLevel level) {
        return PLANS.computeIfAbsent(level.dimension(), k -> create(level)).orElse(null);
    }

    private static Optional<WorldPlan> create(ServerLevel level) {
        if (!AmcsConfig.ENABLED.get() || level.dimension() != Level.OVERWORLD) {
            return Optional.empty();
        }
        Params params = AmcsConfig.params();
        var source = level.getChunkSource();
        McTerrain terrain = new McTerrain(source.getGenerator(), source.randomState(), level);
        AbandonedModernCity.LOGGER.info("[AMCS] Planning cities in {} (seed {}, {})", level.dimension().location(), level.getSeed(), params);
        return Optional.of(new WorldPlan(level.getSeed(), params, terrain));
    }

    public static void clear() {
        PLANS.clear();
    }

    /** Village creatures removed from cities so far (ChunkBuilder.removeVillageMobs). */
    public static int villageMobsRemoved() {
        return ChunkBuilder.VILLAGE_MOBS_REMOVED.get();
    }

    /** Blocks of floating vegetation removed so far (ChunkBuilder.removeFloating). */
    public static long floatingRemoved() {
        return ChunkBuilder.FLOATING_REMOVED.get();
    }
}
