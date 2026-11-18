package com.abandonedmoderncity.gen;

import com.abandonedmoderncity.core.BlockCode;
import com.abandonedmoderncity.core.ChunkPlan;
import com.abandonedmoderncity.core.Lot;
import com.abandonedmoderncity.core.Params;
import com.abandonedmoderncity.core.Profile;
import com.abandonedmoderncity.core.RoadDecay;
import com.abandonedmoderncity.core.StreetLights;
import com.abandonedmoderncity.core.WorldPlan;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * The repair {@link ChunkBuilder} runs after every chunk ({@link #repairNeighbours}): the neighbours already built get
 * their road structure, walls and lots back where a chunk decorated later reached into them.
 */
final class NeighbourRepair {
    private NeighbourRepair() {
    }

    /** Tallest vanilla trees stand below ~40 blocks over their ground. */
    private static final int OVERHANG = 48;

    /**
     * Vanilla decorates each chunk before this feature runs for it, so the trees, plants, rocks, lakes and ore blobs
     * of a chunk can reach into a neighbour that was built earlier: leaves over a street, a mushroom on a building's
     * floor, a dirt or granite blob eating the stone of a sidewalk or a tunnel wall. So after every chunk, the
     * neighbours that are already built get their road structure restored and the intruders removed:
     * <ul>
     *   <li>road columns: surface and base blocks, parapets, tunnel ceilings, and the free height above the lanes;</li>
     *   <li>tunnel wall columns: the wall;</li>
     *   <li>over the whole neighbour (the crowns of this chunk's trees, its lakes and ore blobs can reach anywhere in
     *   it): crowns and plants over roads (up to {@link #OVERHANG}) and inside / on top of buildings.</li>
     * </ul>
     * Neighbours not built yet are left alone: they will be built from scratch, over whatever was put there. The
     * loose blocks around every block removed are added to {@code seeds} (what rested on it may float now:
     * {@link Floating}).
     */
    static void repairNeighbours(WorldPlan plan, WorldGenRegion region, ChunkPos c, LongArrayList seeds) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                ChunkPlan np = plan.chunk(c.x + dx, c.z + dz);
                if (!np.any) {
                    continue;
                }
                ChunkAccess nc = region.getChunk(c.x + dx, c.z + dz);
                if (!nc.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
                    continue;
                }
                // the leaves completing structure crowns planned there are not stray decoration
                Map<Long, BlockState> crowns = Overhangs.forChunk(plan, c.x + dx, c.z + dz);
                int maxY = nc.getMaxBuildHeight();
                int nbx = (c.x + dx) << 4;
                int nbz = (c.z + dz) << 4;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int i = ChunkPlan.idx(lx, lz);
                        byte kind = np.kind[i];
                        int x = nbx + lx;
                        int z = nbz + lz;
                        if (kind == ChunkPlan.TUNNEL_WALL) {
                            int ys = ChunkPlan.surfaceY(np.h2[i]);
                            for (int y = ys - 1; y <= ys + ChunkBuilder.CEILING; y++) {
                                restore(region, nc, p.set(x, y, z), Palette.STONE);
                            }
                            continue;
                        }
                        if (kind == ChunkPlan.LOT) {
                            Lot lot = np.lot[i];
                            // a structure is put back over its whole height (ore blobs of the chunk decorated later
                            // reach it at any height)
                            int above = lot != null ? Math.max(LOT_REPAIR_ABOVE, lot.heightAboveBase()) : LOT_REPAIR_ABOVE;
                            repairLot(region, nc, p, n, crowns, x, z, np.h2[i] / 2 - 1, lot,
                                    np.city != null ? np.city.yardCode() : BlockCode.GRASS, above, seeds);
                            continue;
                        }
                        if (kind != ChunkPlan.ROAD) {
                            continue;
                        }
                        int h2 = np.h2[i];
                        int ys = ChunkPlan.surfaceY(h2);
                        byte zone = np.zone[i];
                        boolean tunnel = np.struct[i] == Profile.TUNNEL;
                        boolean bridge = np.struct[i] == Profile.BRIDGE;
                        if ((np.flags[i] & ChunkPlan.F_HOLE) != 0) {
                            // a hole through a bridge stays open: the crowns and plants that a chunk decorated later
                            // grew into it go, from the deck down (finding 76); what is under the deck stays
                            int top = Math.min(maxY - 1, ys + OVERHANG);
                            for (int y = ys - 1; y <= top; y++) {
                                var section = nc.getSection(nc.getSectionIndex(y));
                                if (section.hasOnlyAir()) {
                                    y |= 15;
                                    continue;
                                }
                                BlockState s = section.getBlockState(lx, y & 15, lz);
                                if (ChunkBuilder.isDecoration(s) && !crowns.containsKey(BlockPos.asLong(x, y, z))) {
                                    region.setBlock(p.set(x, y, z), Palette.AIR, Block.UPDATE_CLIENTS);
                                    Floating.seedAround(p.asLong(), seeds);
                                }
                            }
                            continue;
                        }
                        int decay = RoadDecay.surface(plan.seed, x, z, zone, np.struct[i], ChunkPlan.isSlab(h2));
                        int[] lamp = Palette.lampParts(np, i, x, z);
                        if (lamp != null && lamp[1] != BlockCode.KEEP) {
                            // a street light stands on the column: no plant there
                            decay = BlockCode.KEEP;
                        }
                        restore(region, nc, p.set(x, ys, z), Palette.roadSurfaceAt(plan.seed, x, z, zone, np.struct[i], ChunkPlan.isSlab(h2)));
                        restore(region, nc, p.set(x, ys - 1, z), bridge
                                ? Palette.BRIDGE_STONES[RoadDecay.bridgeStone(plan.seed, x, ys - 1, z)] : Palette.roadBase(zone));
                        if (ChunkPlan.walled(zone)) {
                            for (int y = ys + 1; y <= ChunkBuilder.wallTop(plan, x, z); y++) {
                                restore(region, nc, p.set(x, y, z), ChunkBuilder.wallState(plan, x, y, z));
                            }
                        }
                        if (tunnel) {
                            BlockState ceiling = nc.getBlockState(p.set(x, ys + ChunkBuilder.CEILING, z));
                            if (!ceiling.is(Blocks.STONE) && !ceiling.is(Blocks.GLOWSTONE)) {
                                region.setBlock(p, (np.flags[i] & ChunkPlan.F_LIGHT) != 0 ? Palette.LIGHT : Palette.STONE,
                                        Block.UPDATE_CLIENTS);
                            }
                        }
                        // free height above the lanes
                        int wall = ChunkPlan.walled(zone) ? ChunkBuilder.wallTop(plan, x, z) : Integer.MIN_VALUE;
                        int free0 = wall != Integer.MIN_VALUE ? wall + 1 : ys + 1;
                        int free1 = ys + (tunnel ? Params.CLEARANCE : 4);
                        for (int y = free0; y <= free1 && y < maxY; y++) {
                            BlockState s = nc.getBlockState(p.set(x, y, z));
                            BlockState post = Palette.lampBlock(lamp, y - ys);
                            if (post != null) {
                                // the foot of a street light post
                                if (s != post) {
                                    region.setBlock(p, post, Block.UPDATE_CLIENTS);
                                }
                                continue;
                            }
                            int plant = decay == BlockCode.KEEP ? BlockCode.KEEP : RoadDecay.plant(plan.seed, x, z, y - ys, decay);
                            if (plant != BlockCode.KEEP) {
                                // the plan's own plant on a taken-over column
                                BlockState want = Palette.of(plant, null);
                                if (s != want) {
                                    region.setBlock(p, want, Block.UPDATE_CLIENTS);
                                }
                                continue;
                            }
                            if (s.isAir()) {
                                continue;
                            }
                            boolean liquid = !s.getFluidState().isEmpty();
                            region.setBlock(p, Palette.AIR, Block.UPDATE_CLIENTS);
                            Floating.seedAround(p.asLong(), seeds);
                            if (liquid) {
                                for (Direction d : Direction.Plane.HORIZONTAL) {
                                    n.set(x + d.getStepX(), y, z + d.getStepZ());
                                    if (canWrite(region, n) && !region.getBlockState(n).getFluidState().isEmpty()) {
                                        region.setBlock(n, Palette.STONE, Block.UPDATE_CLIENTS);
                                    }
                                }
                            }
                        }
                        // the rest of a street light (post above the free height, arm, lamp)
                        for (int y = free1 + 1; y <= ys + StreetLights.LAMP_TOP && y < maxY; y++) {
                            BlockState part = Palette.lampBlock(lamp, y - ys);
                            if (part != null && nc.getBlockState(p.set(x, y, z)) != part) {
                                region.setBlock(p, part, Block.UPDATE_CLIENTS);
                            }
                        }
                        if (tunnel) {
                            continue;
                        }
                        // crowns and plants hanging over the road
                        for (int y = free1 + 1; y <= Math.min(maxY - 1, ys + OVERHANG); y++) {
                            var section = nc.getSection(nc.getSectionIndex(y));
                            if (section.hasOnlyAir()) {
                                y |= 15;
                                continue;
                            }
                            BlockState s = section.getBlockState(lx, y & 15, lz);
                            // (the planned crowns and the vines and leaves of a street light stay)
                            if (ChunkBuilder.isDecoration(s) && !crowns.containsKey(BlockPos.asLong(x, y, z))
                                    && Palette.lampBlock(lamp, y - ys) == null) {
                                region.setBlock(p.set(x, y, z), Palette.AIR, Block.UPDATE_CLIENTS);
                                Floating.seedAround(p.asLong(), seeds);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Height above a lot's ground that lakes (8 tall) and dungeon rooms of a later chunk can reach. */
    private static final int LOT_REPAIR_ABOVE = 8;

    /**
     * Lakes (with their cave air and the stone rim of lava lakes) and dungeon rooms of a chunk decorated later can cut
     * into a lot built earlier next door: the ground under the lot is filled again, the lot surface and the low part
     * of buildings, park trees and plazas are put back, and liquids are removed (with the liquid next to them sealed).
     */
    private static void repairLot(WorldGenRegion region, ChunkAccess nc, BlockPos.MutableBlockPos p,
                                  BlockPos.MutableBlockPos n, Map<Long, BlockState> crowns, int x, int z,
                                  int base, Lot lot, int yard, int above, LongArrayList seeds) {
        boolean paved = yard != BlockCode.GRASS;
        int depth = lot == null ? 0 : lot.depthBelowBase();
        // the foundation (ChunkBuilder.FOUNDATION): no air, liquid or block entity where the plan keeps the world
        for (int y = base - ChunkBuilder.FOUNDATION; y < base - depth; y++) {
            if (lot != null && lot.code(x, y, z, base) != BlockCode.KEEP) {
                continue;
            }
            if (ChunkBuilder.foundationGap(nc.getBlockState(p.set(x, y, z)))) {
                region.setBlock(p, Palette.DIRT, Block.UPDATE_CLIENTS);
            }
        }
        for (int y = base - depth; y <= base + above; y++) {
            int code = lot == null ? (y == base ? yard : BlockCode.KEEP) : lot.code(x, y, z, base);
            BlockState s = nc.getBlockState(p.set(x, y, z));
            boolean liquid = !s.getFluidState().isEmpty();
            if (y < base && code == BlockCode.KEEP) {
                // ground between the buried blocks of a structure: refilled like the ground under any lot (and in the
                // foundation no block entity either)
                if (s.isAir() || liquid || y >= base - ChunkBuilder.FOUNDATION && s.hasBlockEntity()) {
                    region.setBlock(p, Palette.DIRT, Block.UPDATE_CLIENTS);
                }
                continue;
            }
            if (code == BlockCode.KEEP || code == BlockCode.AIR || code == BlockCode.SHRUB) {
                // inside a building everything that is not the building goes; over a yard only liquids do
                boolean rock = paved && s.is(Blocks.MOSSY_COBBLESTONE);
                boolean crown = code == BlockCode.KEEP && crowns.containsKey(BlockPos.asLong(x, y, z));
                if (liquid || code == BlockCode.AIR && !s.isAir()
                        || code == BlockCode.KEEP && y > base && !crown && (ChunkBuilder.isDecoration(s) || rock)) {
                    region.setBlock(p, Palette.AIR, Block.UPDATE_CLIENTS);
                    Floating.seedAround(p.asLong(), seeds);
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        n.set(x + d.getStepX(), y, z + d.getStepZ());
                        if (canWrite(region, n) && !region.getBlockState(n).getFluidState().isEmpty()
                                && !ChunkBuilder.plannedFluid(lot, n.getX(), y, n.getZ(), base)) {
                            region.setBlock(n, Palette.STONE, Block.UPDATE_CLIENTS);
                        }
                    }
                }
                continue;
            }
            BlockState want = Palette.of(code, null);
            if (code == BlockCode.GRASS) {
                // yards may legitimately be sand (desert) or turned into another soil: only refill holes
                if (s.isAir() || liquid) {
                    region.setBlock(p, want, Block.UPDATE_CLIENTS);
                }
            } else if (s.getBlock() != want.getBlock()) {
                region.setBlock(p, want, Block.UPDATE_CLIENTS);
                if (want.getBlock() instanceof IronBarsBlock) {
                    nc.markPosForPostprocessing(p.immutable());
                }
            }
        }
        // crowns of trees from chunks decorated later, hanging over the lot (their trunk is usually cut afterwards,
        // when that chunk builds its own part of the city)
        for (int y = base + above + 1; y <= Math.min(nc.getMaxBuildHeight() - 1, base + Math.max(OVERHANG, above)); y++) {
            var section = nc.getSection(nc.getSectionIndex(y));
            if (section.hasOnlyAir()) {
                y |= 15;
                continue;
            }
            int code = lot == null ? BlockCode.KEEP : lot.code(x, y, z, base);
            if ((code == BlockCode.KEEP || code == BlockCode.AIR) && ChunkBuilder.isDecoration(section.getBlockState(x & 15, y & 15, z & 15))
                    && !crowns.containsKey(BlockPos.asLong(x, y, z))) {
                region.setBlock(p.set(x, y, z), Palette.AIR, Block.UPDATE_CLIENTS);
                Floating.seedAround(p.asLong(), seeds);
            }
        }
    }

    /**
     * Whether the region may write at {@code p}: asked before touching the neighbour of a repaired block, which can lie
     * two chunks from the center ({@code ensureCanWrite} would log "setBlock in a far chunk" for it).
     */
    private static boolean canWrite(WorldGenRegion region, BlockPos p) {
        ChunkPos c = region.getCenter();
        return Math.abs((p.getX() >> 4) - c.x) <= 1 && Math.abs((p.getZ() >> 4) - c.z) <= 1 && region.ensureCanWrite(p);
    }

    /** Puts back one block of the road structure if something else took its place. */
    private static void restore(WorldGenRegion region, ChunkAccess nc, BlockPos.MutableBlockPos p, BlockState want) {
        if (nc.getBlockState(p) != want) {
            region.setBlock(p, want, Block.UPDATE_CLIENTS);
        }
    }
}
