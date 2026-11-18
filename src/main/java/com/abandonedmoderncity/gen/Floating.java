package com.abandonedmoderncity.gen;

import com.abandonedmoderncity.core.BlockCode;
import com.abandonedmoderncity.core.ChunkPlan;
import com.abandonedmoderncity.core.WorldPlan;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Natural vegetation left hanging in the air (in cities built where there were mountains, pieces of trees, vines and
 * snow floated well above the ground). A chunk decorated after its neighbour was
 * built can grow a tree on the old, uncut ground whose branches reach into that neighbour; when the city then cuts the
 * trunk, what reached the neighbour stays: the cleanup of crowns over roads and lots stopped 48 blocks up, the slopes
 * had none, a piece of branch kept its leaves "alive", and snow and vines resting on removed leaves stayed.
 *
 * <p>A <b>loose</b> block is natural decoration ({@link ChunkBuilder#isDecoration}: leaves that are not persistent,
 * logs, vines, snow layers, plants, huge mushrooms...). A group of loose blocks (joined by a face, an edge or a corner:
 * the trunks of acacias and the branches of large oaks only touch by an edge) <b>stands</b> when one of its blocks
 * touches any other block that is not air (the ground, stone, water, a structure), a block the plan places there, or
 * the edge of the area that can be read (what lies beyond is not known: it counts as support). A group that does not
 * stand floats.
 */
public final class Floating {
    private Floating() {
    }

    /** A group larger than this is taken as standing (a whole forest canopy is never floating). */
    static final int MAX_GROUP = 20_000;

    /** Reads a block of the world (inside the bounds given to {@link #find}). */
    public interface Reader {
        BlockState get(int x, int y, int z);
    }

    /** Natural decoration that only stands when it touches something that is not decoration. */
    public static boolean loose(BlockState s) {
        return ChunkBuilder.isDecoration(s);
    }

    /**
     * Whether the plan places a block at (x, y, z): a block of a structure, of a tree of a block's core or park (any
     * code of a lot other than keep and air), a leaf completing a structure's crown or a part of a street light. Those
     * are never removed and count as support.
     */
    public static final class Planned {
        private final WorldPlan plan;
        private final Map<Long, Map<Long, BlockState>> overhangs = new HashMap<>();

        public Planned(WorldPlan plan) {
            this.plan = plan;
        }

        public boolean at(int x, int y, int z) {
            int cx = x >> 4;
            int cz = z >> 4;
            if (overhangs.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> Overhangs.forChunk(plan, cx, cz))
                    .containsKey(BlockPos.asLong(x, y, z))) {
                return true;
            }
            ChunkPlan p = plan.chunk(cx, cz);
            int i = ChunkPlan.idx(x & 15, z & 15);
            if (p.kind[i] == ChunkPlan.LOT && p.lot[i] != null) {
                int code = p.lot[i].code(x, y, z, p.h2[i] / 2 - 1);
                return code != BlockCode.KEEP && code != BlockCode.AIR;
            }
            if (p.kind[i] == ChunkPlan.ROAD) {
                int[] lamp = Palette.lampParts(p, i, x, z);
                return lamp != null && Palette.lampBlock(lamp, y - ChunkPlan.surfaceY(p.h2[i])) != null;
            }
            return false;
        }
    }

    private static final byte STANDS = 1;
    private static final byte FLOATS = 2;

    /**
     * The floating groups reached from the loose blocks in {@code seeds} (packed {@link BlockPos#asLong}), inside
     * x0..x1, z0..z1, y0..y1 (a block next to the bounds counts as support). Each floating group is given to
     * {@code out} once, as its positions. {@code state} remembers what was decided, so the same group is not walked
     * twice when it is seeded again (pass the same map to later calls with the same bounds, or a new one).
     */
    public static void find(LongArrayList seeds, Reader world, Planned planned, int x0, int z0, int x1, int z1,
                            int y0, int y1, Long2ByteOpenHashMap state, Consumer<LongArrayList> out) {
        LongArrayList group = new LongArrayList();
        LongArrayList queue = new LongArrayList();
        for (int s = 0; s < seeds.size(); s++) {
            long seed = seeds.getLong(s);
            if (state.containsKey(seed)) {
                continue;
            }
            int sx = BlockPos.getX(seed);
            int sy = BlockPos.getY(seed);
            int sz = BlockPos.getZ(seed);
            if (sx < x0 || sx > x1 || sz < z0 || sz > z1 || sy < y0 || sy > y1
                    || !loose(world.get(sx, sy, sz)) || planned.at(sx, sy, sz)) {
                continue;
            }
            group.clear();
            queue.clear();
            queue.add(seed);
            state.put(seed, FLOATS);
            boolean stands = false;
            for (int q = 0; q < queue.size() && !stands; q++) {
                long p = queue.getLong(q);
                group.add(p);
                int px = BlockPos.getX(p);
                int py = BlockPos.getY(p);
                int pz = BlockPos.getZ(p);
                for (int dy = -1; dy <= 1 && !stands; dy++) {
                    for (int dz = -1; dz <= 1 && !stands; dz++) {
                        for (int dx = -1; dx <= 1 && !stands; dx++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            int nx = px + dx;
                            int ny = py + dy;
                            int nz = pz + dz;
                            if (nx < x0 || nx > x1 || nz < z0 || nz > z1 || ny < y0 || ny > y1) {
                                // beyond what can be read: it may stand there
                                stands = true;
                                break;
                            }
                            long n = BlockPos.asLong(nx, ny, nz);
                            byte known = state.get(n);
                            if (known == STANDS) {
                                stands = true;
                                break;
                            }
                            if (known == FLOATS) {
                                continue;
                            }
                            BlockState b = world.get(nx, ny, nz);
                            if (b.isAir()) {
                                continue;
                            }
                            if (!loose(b) || planned.at(nx, ny, nz)) {
                                stands = true;
                                break;
                            }
                            state.put(n, FLOATS);
                            queue.add(n);
                        }
                    }
                }
                if (queue.size() > MAX_GROUP) {
                    stands = true;
                }
            }
            if (stands) {
                // the whole walked part (and what was queued) stands with it
                for (int q = 0; q < queue.size(); q++) {
                    state.put(queue.getLong(q), STANDS);
                }
            } else {
                out.accept(new LongArrayList(group));
            }
        }
    }

    /** The loose blocks around a position (its 26 neighbours) added to {@code seeds}. */
    static void seedAround(long pos, LongArrayList seeds) {
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        seeds.add(BlockPos.asLong(x + dx, y + dy, z + dz));
                    }
                }
            }
        }
    }


    /** How far (horizontally) from a column the mod changed a floating group may come from it (the reach of a crown). */
    public static final int MOD_REACH = 7;

    /**
     * Whether the mod could have left this floating group: one of its blocks lies within {@link #MOD_REACH} columns of a
     * column it builds (a road, a lot, a tunnel wall) or of a slope whose ground it moved, cut down to or filled up to a
     * limit of the plan (the ground right at {@code lo} or {@code hi}; a slope whose ground lies strictly between them
     * kept the natural ground). A group far from those is the natural world's own: Minecraft hangs vines that hold on
     * nothing under jungle crowns and leaves plants over a lake a later chunk dug under them, with or without the mod
     * (review for 1.0.0, B1 and B2). The slope's ground is read through {@code world}, only inside x0..x1, z0..z1.
     */
    public static boolean nearModColumns(WorldPlan plan, Reader world, LongArrayList group, int x0, int z0, int x1, int z1,
                                         int minY, int maxY) {
        Map<Long, Boolean> seen = new HashMap<>();
        for (int k = 0; k < group.size(); k++) {
            int gx = BlockPos.getX(group.getLong(k));
            int gz = BlockPos.getZ(group.getLong(k));
            for (int dz = -MOD_REACH; dz <= MOD_REACH; dz++) {
                for (int dx = -MOD_REACH; dx <= MOD_REACH; dx++) {
                    int x = gx + dx;
                    int z = gz + dz;
                    Boolean touched = seen.get(BlockPos.asLong(x, 0, z));
                    if (touched == null) {
                        touched = modColumn(plan, world, x, z, x0, z0, x1, z1, minY, maxY);
                        seen.put(BlockPos.asLong(x, 0, z), touched);
                    }
                    if (touched) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean modColumn(WorldPlan plan, Reader world, int x, int z, int x0, int z0, int x1, int z1,
                                     int minY, int maxY) {
        ChunkPlan cp = plan.chunk(x >> 4, z >> 4);
        int i = ChunkPlan.idx(x & 15, z & 15);
        byte kind = cp.kind[i];
        if (kind == ChunkPlan.ROAD || kind == ChunkPlan.LOT || kind == ChunkPlan.TUNNEL_WALL) {
            return true;
        }
        if (kind != ChunkPlan.SKIRT || x < x0 || x > x1 || z < z0 || z > z1) {
            return false;
        }
        int y = maxY;
        while (y > minY && !ChunkBuilder.isGround(world.get(x, y, z))) {
            y--;
        }
        int walk = y + 1;
        return walk <= cp.lo[i] || walk >= cp.hi[i];
    }
}
