package com.abandonedmoderncity.gen;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

/**
 * The zombie spawners of the buildings ({@link com.abandonedmoderncity.core.SpawnerDraw} decides where): a spawner
 * placed from a block state is empty, so each one is set up when it is placed (in the chunk, by the neighbour repair
 * and by {@code /amcs spawn}): a zombie, in any light (its own spawn rules: block and sky light 0 to 15, so a torch
 * does not stop it; nothing in Peaceful, as for every spawner with its own rules), calmer than the game's: it wakes
 * with a player within {@link #PLAYER_RANGE}, {@link #COUNT} a wave, stops at {@link #MAX_NEARBY} around it, waits
 * {@link #MIN_DELAY} to {@link #MAX_DELAY} ticks between waves.
 */
public final class Spawners {
    private Spawners() {
    }

    public static final String MOB = "minecraft:zombie";
    public static final int PLAYER_RANGE = 12;
    public static final int COUNT = 2;
    public static final int MAX_NEARBY = 4;
    public static final int MIN_DELAY = 200;
    public static final int MAX_DELAY = 800;
    /** As in the game: how far around the spawner the zombies come. */
    public static final int RANGE = 4;
    /** Ticks before the first wave once a player is near (the game's 20). */
    public static final int FIRST_DELAY = 20;

    /** The data of the spawner as the game saves it. */
    static CompoundTag data() {
        CompoundTag entity = new CompoundTag();
        entity.putString("id", MOB);
        SpawnData spawn = new SpawnData(entity, Optional.of(new SpawnData.CustomSpawnRules(
                new InclusiveRange<>(0, 15), new InclusiveRange<>(0, 15))), Optional.empty());
        CompoundTag tag = new CompoundTag();
        tag.putShort("Delay", (short) FIRST_DELAY);
        tag.putShort("MinSpawnDelay", (short) MIN_DELAY);
        tag.putShort("MaxSpawnDelay", (short) MAX_DELAY);
        tag.putShort("SpawnCount", (short) COUNT);
        tag.putShort("MaxNearbyEntities", (short) MAX_NEARBY);
        tag.putShort("RequiredPlayerRange", (short) PLAYER_RANGE);
        tag.putShort("SpawnRange", (short) RANGE);
        tag.put("SpawnData", SpawnData.CODEC.encodeStart(NbtOps.INSTANCE, spawn).getOrThrow());
        return tag;
    }

    /** Sets up the spawner just placed at {@code pos} (nothing for another block entity, or none). */
    public static void configure(BlockEntity be, BlockPos pos) {
        if (be instanceof SpawnerBlockEntity s) {
            // no level: the block entity of a chunk being generated is not in a level yet (no block update is sent)
            s.getSpawner().load(null, pos.immutable(), data());
            s.setChanged();
        }
    }

    /**
     * What is wrong with a spawner of a building, as the game saved it (read back by the audit), or null: a zombie, its
     * own light rules over the whole range, and the 7 values.
     */
    public static String problem(BlockEntity be) {
        if (!(be instanceof SpawnerBlockEntity s)) {
            return "not a spawner";
        }
        CompoundTag t = s.getSpawner().save(new CompoundTag());
        CompoundTag want = data();
        for (String k : new String[]{"MinSpawnDelay", "MaxSpawnDelay", "SpawnCount", "MaxNearbyEntities",
                "RequiredPlayerRange", "SpawnRange"}) {
            if (t.getShort(k) != want.getShort(k)) {
                return k + " " + t.getShort(k);
            }
        }
        SpawnData d = SpawnData.CODEC.parse(NbtOps.INSTANCE, t.getCompound("SpawnData")).result().orElse(null);
        if (d == null || !MOB.equals(d.getEntityToSpawn().getString("id"))) {
            return "mob " + (d == null ? "none" : d.getEntityToSpawn().getString("id"));
        }
        if (d.getCustomSpawnRules().isEmpty()) {
            return "the game's light rules";
        }
        SpawnData.CustomSpawnRules r = d.getCustomSpawnRules().get();
        if (r.blockLightLimit().minInclusive() != 0 || r.blockLightLimit().maxInclusive() != 15
                || r.skyLightLimit().minInclusive() != 0 || r.skyLightLimit().maxInclusive() != 15) {
            return "light rules " + r;
        }
        return null;
    }
}
