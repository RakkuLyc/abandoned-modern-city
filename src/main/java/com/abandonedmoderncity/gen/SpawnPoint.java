package com.abandonedmoderncity.gen;

import com.abandonedmoderncity.AbandonedModernCity;
import com.abandonedmoderncity.AmcsConfig;
import com.abandonedmoderncity.core.SpawnSpot;
import com.abandonedmoderncity.core.WorldPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * The spawn of a new world (option {@code spawnNearCity}): when the game sets it up, {@link SpawnSpot} picks a point
 * {@link SpawnSpot#DISTANCE} outside the city nearest to the spot the game would have used, and it is checked in the
 * world (its chunk is generated, as the game does with its own). The game's own setup is skipped only once the spawn is
 * set; then the bonus chest, when asked for, is placed as the game does. Any failure leaves the game's spawn. Worlds
 * not made of noise (superflat) have no cities and keep the game's spawn. Existing worlds are never touched: the event
 * only comes when a world is created.
 */
public final class SpawnPoint {
    private SpawnPoint() {
    }

    /** Spots checked in the world (each generates a chunk) before keeping the game's spawn. */
    static final int TRIES = 8;

    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition e) {
        if (!AmcsConfig.SPAWN_NEAR_CITY.get() || !(e.getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) {
            return;
        }
        var source = level.getChunkSource();
        if (!(source.getGenerator() instanceof NoiseBasedChunkGenerator)) {
            AbandonedModernCity.LOGGER.info("[AMCS] Spawn: the world has no cities ({}), the game's spawn kept",
                    source.getGenerator().getClass().getSimpleName());
            return;
        }
        try {
            WorldPlan plan = Plans.get(level);
            if (plan == null) {
                return;
            }
            long t0 = System.currentTimeMillis();
            BlockPos game = source.randomState().sampler().findSpawnPosition();
            BlockPos[] found = new BlockPos[1];
            SpawnSpot.Spot s = SpawnSpot.find(plan, game.getX() + 0.5, game.getZ() + 0.5, sp -> {
                found[0] = surface(level, (int) Math.floor(sp.x()), (int) Math.floor(sp.z()));
                return found[0] != null;
            }, TRIES);
            if (s == null) {
                AbandonedModernCity.LOGGER.info("[AMCS] Spawn: no dry spot {} blocks from a city within {} cells of {}, "
                        + "{}; the game's spawn kept", (int) SpawnSpot.DISTANCE, SpawnSpot.CELLS, game.getX(),
                        game.getZ());
                return;
            }
            BlockPos pos = found[0];
            e.getSettings().setSpawn(pos, 0.0F);
            if (level.getServer().getWorldData().worldGenOptions().generateBonusChest()) {
                level.registryAccess().registry(Registries.CONFIGURED_FEATURE)
                        .flatMap(r -> r.getHolder(MiscOverworldFeatures.BONUS_CHEST))
                        .ifPresent(f -> f.value().place(level, source.getGenerator(), level.random, pos));
            }
            e.setCanceled(true);
            AbandonedModernCity.LOGGER.info("[AMCS] Spawn of the new world at {}, {}, {}: {} blocks outside {} (the "
                    + "game's spot was {}, {}; highway clearance {}; {} ms)", pos.getX(), pos.getY(), pos.getZ(),
                    (int) SpawnSpot.DISTANCE, s.city(), game.getX(), game.getZ(),
                    (int) SpawnSpot.HIGHWAY_CLEAR[s.tier()], System.currentTimeMillis() - t0);
        } catch (RuntimeException ex) {
            AbandonedModernCity.LOGGER.warn("[AMCS] Spawn: could not put it near a city; the game's spawn kept", ex);
        }
    }

    /** Where a player stands at (x, z): on the top full block under the open sky that is not under water, or null. */
    static BlockPos surface(ServerLevel level, int x, int z) {
        LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
        int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
        if (top <= level.getMinBuildHeight()) {
            return null;
        }
        BlockPos below = new BlockPos(x, top - 1, z);
        BlockState state = level.getBlockState(below);
        if (!state.getFluidState().isEmpty()
                || !Block.isFaceFull(state.getCollisionShape(level, below), Direction.UP)) {
            return null;
        }
        return below.above();
    }
}
