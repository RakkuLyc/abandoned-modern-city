package com.abandonedmoderncity.command;

import com.abandonedmoderncity.core.BlockCode;
import com.abandonedmoderncity.core.ChunkPlan;
import com.abandonedmoderncity.core.Lot;
import com.abandonedmoderncity.core.Params;
import com.abandonedmoderncity.core.PlanAudit;
import com.abandonedmoderncity.core.Profile;
import com.abandonedmoderncity.core.RoadDecay;
import com.abandonedmoderncity.core.StreetLights;
import com.abandonedmoderncity.core.WorldPlan;
import com.abandonedmoderncity.gen.Floating;
import com.abandonedmoderncity.gen.Overhangs;
import com.abandonedmoderncity.gen.Palette;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Audit of the blocks actually generated (the chunks must exist). Every failure count must be 0:
 * <ul>
 *   <li>{@code hole}: no surface block (air or liquid) on a road column.</li>
 *   <li>{@code surface_block}: wrong surface block (smooth stone / stone and their bottom slabs, or the taken-over
 *   surface of {@code RoadDecay}).</li>
 *   <li>{@code road_plant}: a plant planned on a taken-over road column is missing or different.</li>
 *   <li>{@code no_base}: nothing solid under the surface block.</li>
 *   <li>{@code height_mismatch}: the walking height measured in the world differs from the plan.</li>
 *   <li>{@code step}: two neighbouring drivable columns more than half a block apart, measured on the blocks
 *   (also across chunk borders).</li>
 *   <li>{@code obstacle} / {@code cover} / {@code fluid_on_road}: something solid, something without collision, or a
 *   liquid in the free height above a drivable column (4 blocks, 5 in tunnels).</li>
 *   <li>{@code vegetation_over_road}: leaves, logs, plants hanging over a road (up to 48 blocks).</li>
 *   <li>{@code tunnel_ceiling} / {@code tunnel_wall}: missing tunnel lining.</li>
 *   <li>{@code pillar_floating}: a bridge pillar that ends in the air.</li>
 *   <li>{@code lot_hole}: a lot without ground (water a structure plans at the ground, a pool, is not one).</li>
 *   <li>{@code lot_foundation}: air, a liquid or a block entity in the 3 layers under a lot's floor where the plan
 *   keeps what the world has (the cells a structure plans there are not checked).</li>
 *   <li>{@code building_mismatch}: a building block that differs from the building's definition (seams between
 *   chunks would show up here).</li>
 *   <li>{@code hard_block}: an unbreakable block in a street, bridge, tunnel or building (any hardness is allowed).</li>
 *   <li>{@code loot_in_city} / {@code container_in_city}: a container (with or without a loot table) in the city.</li>
 *   <li>{@code orphan_road}: a connected piece of drivable road that reaches no city street and does not leave the
 *   audited area.</li>
 *   <li>{@code vegetation_floating}: a group of natural leaves, logs, vines, snow or plants above the ground of its
 *   columns that touches nothing that holds it ({@link Floating}; a group reaching the edge of the audited area is
 *   taken as standing).</li>
 * </ul>
 */
public final class WorldAudit {
    private WorldAudit() {
    }

    public static PlanAudit.Report run(ServerLevel level, WorldPlan plan, int cx0, int cz0, int cx1, int cz1) {
        PlanAudit.Report r = new PlanAudit.Report();
        r.label = "WORLD";
        int w = (cx1 - cx0 + 1) * 16;
        int d = (cz1 - cz0 + 1) * 16;
        int x0 = cx0 * 16;
        int z0 = cz0 * 16;
        double[] walk = new double[w * d];
        boolean[] drivable = new boolean[w * d];
        boolean[] city = new boolean[w * d];
        Arrays.fill(walk, Double.NaN);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int cz = cz0; cz <= cz1; cz++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                ChunkPlan cp = plan.chunk(cx, cz);
                Map<Long, BlockState> crowns = Overhangs.forChunk(plan, cx, cz);
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int i = ChunkPlan.idx(lx, lz);
                        int x = cx * 16 + lx;
                        int z = cz * 16 + lz;
                        int g = (x - x0) + (z - z0) * w;
                        switch (cp.kind[i]) {
                            case ChunkPlan.ROAD -> {
                                double m = road(r, plan, chunk, cp, i, x, z, p, crowns);
                                if (cp.drivable(i)) {
                                    drivable[g] = true;
                                    walk[g] = m;
                                    city[g] = (cp.flags[i] & ChunkPlan.F_CITY) != 0;
                                }
                            }
                            case ChunkPlan.TUNNEL_WALL -> tunnelWall(r, chunk, cp, i, x, z, p);
                            case ChunkPlan.LOT -> lot(r, chunk, cp, i, x, z, p);
                            default -> {
                            }
                        }
                    }
                }
                containers(r, chunk, cp);
                // the leaves completing the crowns a structure's edge cut: placed where there was air
                for (Map.Entry<Long, BlockState> e : crowns.entrySet()) {
                    BlockPos b = BlockPos.of(e.getKey());
                    BlockState s = chunk.getBlockState(b);
                    if (s.is(BlockTags.LEAVES)) {
                        r.stat("crown_leaves", 1);
                    } else if (s.isAir()) {
                        r.fail("crown_leaf_missing", b.toShortString() + " expected " + id(e.getValue()));
                    }
                }
            }
        }
        // steps between neighbours, measured on the blocks
        for (int gz = 0; gz < d; gz++) {
            for (int gx = 0; gx < w; gx++) {
                int g = gx + gz * w;
                if (!drivable[g] || Double.isNaN(walk[g])) {
                    continue;
                }
                if (gx + 1 < w && drivable[g + 1] && !Double.isNaN(walk[g + 1]) && Math.abs(walk[g] - walk[g + 1]) > 0.5 + 1e-6) {
                    r.fail("step", (x0 + gx) + "," + (z0 + gz) + " E " + walk[g] + "->" + walk[g + 1]);
                }
                if (gz + 1 < d && drivable[g + w] && !Double.isNaN(walk[g + w]) && Math.abs(walk[g] - walk[g + w]) > 0.5 + 1e-6) {
                    r.fail("step", (x0 + gx) + "," + (z0 + gz) + " S " + walk[g] + "->" + walk[g + w]);
                }
            }
        }
        components(r, x0, z0, w, d, drivable, walk, city);
        floating(r, level, plan, cx0, cz0, cx1, cz1);
        return r;
    }

    /**
     * The loose natural blocks above the ground of each column (the highest block that is neither air nor loose) are
     * grouped ({@link Floating#find}); a group that touches nothing that holds it fails {@code vegetation_floating}
     * (one failure per group, with its first block, its size and its height above what is under it).
     */
    private static void floating(PlanAudit.Report r, ServerLevel level, WorldPlan plan, int cx0, int cz0, int cx1,
                                 int cz1) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        int cw = cx1 - cx0 + 1;
        LevelChunk[] chunks = new LevelChunk[cw * (cz1 - cz0 + 1)];
        for (int cz = cz0; cz <= cz1; cz++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                chunks[(cx - cx0) + (cz - cz0) * cw] = level.getChunk(cx, cz);
            }
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        Floating.Reader world = (x, y, z) -> chunks[((x >> 4) - cx0) + ((z >> 4) - cz0) * cw].getBlockState(p.set(x, y, z));
        LongArrayList seeds = new LongArrayList();
        for (LevelChunk c : chunks) {
            int highest = c.getHighestFilledSectionIndex();
            if (highest < 0) {
                continue;
            }
            int top = Math.min(maxY, SectionPos.sectionToBlockCoord(c.getSectionYFromSectionIndex(highest)) + 15);
            int bx = c.getPos().getMinBlockX();
            int bz = c.getPos().getMinBlockZ();
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int y = top; y >= minY; y--) {
                        BlockState s = c.getBlockState(p.set(bx + lx, y, bz + lz));
                        if (s.isAir()) {
                            continue;
                        }
                        if (!Floating.loose(s)) {
                            break;
                        }
                        seeds.add(BlockPos.asLong(bx + lx, y, bz + lz));
                    }
                }
            }
        }
        r.stat("vegetation_checked", seeds.size());
        Floating.find(seeds, world, new Floating.Planned(plan), cx0 * 16, cz0 * 16, cx1 * 16 + 15, cz1 * 16 + 15,
                minY, maxY, new Long2ByteOpenHashMap(), g -> {
                    long first = g.getLong(0);
                    int x = BlockPos.getX(first);
                    int y = BlockPos.getY(first);
                    int z = BlockPos.getZ(first);
                    String name = id(world.get(x, y, z));
                    int under = y - 1;
                    while (under > minY && world.get(x, under, z).isAir()) {
                        under--;
                    }
                    r.fail("vegetation_floating", x + "," + y + "," + z + " " + name + ", " + g.size() + " blocks, "
                            + (y - under) + " above " + id(world.get(x, under, z)));
                });
    }

    /** A block of a bridge pillar: stone (old worlds) or the old bridge stones. */
    private static boolean isPillar(BlockState s) {
        if (s.is(Blocks.STONE)) {
            return true;
        }
        for (BlockState b : Palette.BRIDGE_STONES) {
            if (s.is(b.getBlock())) {
                return true;
            }
        }
        return false;
    }

    private static BlockState at(LevelChunk c, BlockPos.MutableBlockPos p, int x, int y, int z) {
        return c.getBlockState(p.set(x, y, z));
    }

    private static String id(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
    }

    private static boolean hard(BlockState s) {
        if (Palette.exempt(s) || !s.getFluidState().isEmpty()) {
            return false;
        }
        float h = s.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return h < 0;
    }

    /** Walking height measured on the blocks: top of the highest colliding block in [ys - 2, ys + 1]. */
    private static double measure(LevelChunk c, BlockPos.MutableBlockPos p, int x, int z, int ys) {
        for (int y = ys + 1; y >= ys - 2; y--) {
            BlockState s = at(c, p, x, y, z);
            VoxelShape shape = s.getCollisionShape(c, p);
            if (!shape.isEmpty()) {
                return y + shape.max(Direction.Axis.Y);
            }
        }
        return Double.NaN;
    }

    private static double road(PlanAudit.Report r, WorldPlan plan, LevelChunk c, ChunkPlan cp, int i, int x, int z,
                               BlockPos.MutableBlockPos p, Map<Long, BlockState> crowns) {
        int h2 = cp.h2[i];
        boolean slab = ChunkPlan.isSlab(h2);
        int ys = ChunkPlan.surfaceY(h2);
        byte zone = cp.zone[i];
        byte st = cp.struct[i];
        boolean tunnel = st == Profile.TUNNEL;
        String where = x + "," + ys + "," + z;
        r.stat("road_columns", 1);
        r.stat(st == Profile.BRIDGE ? "bridge_columns" : tunnel ? "tunnel_columns" : "ground_road_columns", 1);
        if (slab) {
            r.stat("slab_columns", 1);
        }
        if ((cp.flags[i] & ChunkPlan.F_HOLE) != 0) {
            // a hole through the deck of a bridge: nothing at the surface nor under it
            r.stat("bridge_hole_columns", 1);
            if (!at(c, p, x, ys, z).isAir() || !at(c, p, x, ys - 1, z).isAir()) {
                r.fail("hole_filled", where + " " + id(at(c, p, x, ys, z)) + " / " + id(at(c, p, x, ys - 1, z)));
            }
            return Double.NaN;
        }
        BlockState surface = at(c, p, x, ys, z);
        int decay = RoadDecay.surface(plan.seed, x, z, zone, st, slab);
        BlockState expected = Palette.roadSurfaceAt(plan.seed, x, z, zone, st, slab);
        if (decay != BlockCode.KEEP) {
            r.stat("taken_road_columns", 1);
        }
        if (surface.isAir() || !surface.getFluidState().isEmpty()) {
            r.fail("hole", where + " " + id(surface));
        } else if (surface.getBlock() != expected.getBlock()
                || surface.getBlock() instanceof SlabBlock && surface.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
            r.fail("surface_block", where + " " + id(surface) + " expected " + id(expected));
        }
        BlockState base = at(c, p, x, ys - 1, z);
        if (base.isAir() || !base.getFluidState().isEmpty()) {
            r.fail("no_base", where);
        }
        for (int y = ys - 1; y <= ys + (tunnel ? CEILING : 1); y++) {
            BlockState s = at(c, p, x, y, z);
            r.stat("blocks_checked_for_hardness", 1);
            if (!s.isAir() && hard(s)) {
                r.fail("hard_block", x + "," + y + "," + z + " " + id(s));
            }
        }
        double m = measure(c, p, x, z, ys);
        // a column that is not drivable carries a wall (a street light post is checked above, by street_light)
        if (!cp.drivable(i) && (cp.flags[i] & ChunkPlan.F_POLE) == 0) {
            BlockState w = at(c, p, x, ys + 1, z);
            boolean gap = st == Profile.BRIDGE && RoadDecay.wallGap(plan.seed, x, z);
            if (gap) {
                // a fallen stretch of a bridge wall
                r.stat("wall_gap_columns", 1);
                if (!w.isAir()) {
                    r.fail("parapet", where + " fallen wall " + id(w));
                }
            } else if (!w.is(Blocks.STONE_BRICK_WALL) && !w.is(Blocks.MOSSY_STONE_BRICK_WALL)) {
                r.fail("parapet", where);
            }
            return m;
        }
        // (a street light post stands on its column: the walking height is not measured there)
        if ((cp.flags[i] & ChunkPlan.F_POLE) == 0 && (Double.isNaN(m) || Math.abs(m - h2 / 2.0) > 1e-6)) {
            r.fail("height_mismatch", where + " plan " + h2 / 2.0 + " world " + m);
        }
        int free = tunnel ? Params.CLEARANCE : 4;
        // the street light of the column (post, arm, lamp, vines): planned there, checked whole
        int[] lamp = Palette.lampParts(cp, i, x, z);
        if (lamp != null && lamp[1] != BlockCode.KEEP) {
            // no plant under a street light standing on the column
            decay = BlockCode.KEEP;
        }
        for (int y = ys + 1; y <= ys + StreetLights.LAMP_TOP; y++) {
            BlockState want = Palette.lampBlock(lamp, y - ys);
            if (want != null) {
                BlockState s = at(c, p, x, y, z);
                if (s.getBlock() != want.getBlock()) {
                    r.fail("street_light", x + "," + y + "," + z + " " + id(s) + " expected " + id(want));
                } else {
                    r.stat("street_light_blocks", 1);
                }
            }
        }
        for (int y = ys + 1; y <= ys + free; y++) {
            BlockState s = at(c, p, x, y, z);
            if (Palette.lampBlock(lamp, y - ys) != null) {
                continue;
            }
            int plant = decay == BlockCode.KEEP ? BlockCode.KEEP
                    : RoadDecay.plant(plan.seed, x, z, y - ys, decay);
            if (plant != BlockCode.KEEP) {
                // the plan's own plant on a taken-over column (no collision)
                if (s.getBlock() != Palette.of(plant, null).getBlock()) {
                    r.fail("road_plant", x + "," + y + "," + z + " " + id(s) + " expected " + id(Palette.of(plant, null)));
                } else {
                    r.stat("road_plants", 1);
                }
                continue;
            }
            if (s.isAir()) {
                // the entrance canopy of a building over the sidewalk is checked by overhang()
                continue;
            }
            if (!s.getFluidState().isEmpty()) {
                r.fail("fluid_on_road", x + "," + y + "," + z + " " + id(s));
            } else if (!s.getCollisionShape(c, p).isEmpty()) {
                r.fail("obstacle", x + "," + y + "," + z + " " + id(s));
            } else {
                r.fail("cover", x + "," + y + "," + z + " " + id(s));
            }
        }
        if (tunnel) {
            BlockState ceil = at(c, p, x, ys + CEILING, z);
            if (!ceil.is(Blocks.STONE) && !ceil.is(Blocks.GLOWSTONE)) {
                r.fail("tunnel_ceiling", where + " " + id(ceil));
            }
            if (ceil.is(Blocks.GLOWSTONE)) {
                r.stat("tunnel_lights", 1);
            }
        } else {
            for (int y = ys + free + 1; y <= ys + 48 && y < c.getMaxBuildHeight(); y++) {
                BlockState s = at(c, p, x, y, z);
                if (Palette.lampBlock(lamp, y - ys) != null) {
                    // a vine or the leaves of a street light (checked above, by street_light)
                    continue;
                }
                if (crowns.containsKey(BlockPos.asLong(x, y, z)) && s.is(BlockTags.LEAVES)) {
                    // a crown of a structure's tree completed over the sidewalk, high over it
                    continue;
                }
                if (!s.isAir() && (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)
                        || s.getBlock() instanceof BushBlock
                        || s.getBlock() instanceof VineBlock)) {
                    r.fail("vegetation_over_road", x + "," + y + "," + z + " " + id(s));
                }
            }
        }
        if ((cp.flags[i] & ChunkPlan.F_PILLAR) != 0) {
            int y = ys - 2;
            while (y > c.getMinBuildHeight() && isPillar(at(c, p, x, y, z))) {
                y--;
            }
            BlockState below = at(c, p, x, y, z);
            r.stat("pillar_columns", 1);
            // the pillar may stand on a natural rock ledge with a cave under it: only air or liquid above the natural
            // ground (as the terrain noise describes it) means the pillar stops short
            if ((below.isAir() || !below.getFluidState().isEmpty()) && y >= plan.terrain.ground(x, z)) {
                r.fail("pillar_floating", x + "," + y + "," + z + " ends on " + id(below));
            }
        }
        return m;
    }

    /** Height of a tunnel's ceiling over the road surface, as the builder places it ({@code ChunkBuilder.CEILING}). */
    private static final int CEILING = Params.CLEARANCE + 1;

    private static void tunnelWall(PlanAudit.Report r, LevelChunk c, ChunkPlan cp, int i, int x, int z, BlockPos.MutableBlockPos p) {
        int ys = ChunkPlan.surfaceY(cp.h2[i]);
        for (int y = ys - 1; y <= ys + CEILING; y++) {
            BlockState s = at(c, p, x, y, z);
            if (!s.is(Blocks.STONE)) {
                r.fail("tunnel_wall", x + "," + y + "," + z + " " + id(s));
            }
        }
        r.stat("tunnel_wall_columns", 1);
    }

    private static void lot(PlanAudit.Report r, LevelChunk c, ChunkPlan cp, int i, int x, int z, BlockPos.MutableBlockPos p) {
        int base = cp.h2[i] / 2 - 1;
        BlockState ground = at(c, p, x, base, z);
        Lot lot = cp.lot[i];
        // a structure may have water at the ground level (a pool): that is its plan, checked block by block below
        int planned = lot == null ? BlockCode.KEEP : lot.code(x, base, z, base);
        BlockState plannedState = planned >= BlockCode.STRUCT ? Palette.of(planned, null) : null;
        boolean plannedFluid = plannedState != null && !plannedState.getFluidState().isEmpty();
        // the mouth of a garage ramp or stairwell is planned open
        if ((ground.isAir() || !ground.getFluidState().isEmpty()) && !plannedFluid && planned != BlockCode.AIR) {
            r.fail("lot_hole", x + "," + base + "," + z + " " + id(ground));
        }
        // the 3 layers under the floor, where the plan keeps what the world has (finding 75): no air, liquid or block
        // entity left there by what stood before the city (a cave, the house of a village)
        for (int y = base - 3; y < base; y++) {
            if (lot != null && lot.code(x, y, z, base) != BlockCode.KEEP) {
                continue;
            }
            BlockState s = at(c, p, x, y, z);
            r.stat("foundation_checked", 1);
            if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity()) {
                r.fail("lot_foundation", x + "," + y + "," + z + " " + id(s));
            }
        }
        if (lot == null) {
            if (cp.city != null) {
                r.stat("yard_columns", 1);
                BlockState want = Palette.of(cp.city.yardCode(), null);
                if (ground.getBlock() != want.getBlock()) {
                    r.fail("yard_ground", x + "," + base + "," + z + " " + id(ground) + " expected " + id(want));
                }
            }
            return;
        }
        int top = base + lot.heightAboveBase();
        for (int y = base - lot.depthBelowBase(); y <= top; y++) {
            BlockState s = at(c, p, x, y, z);
            r.stat("blocks_checked_for_hardness", 1);
            if (!s.isAir() && hard(s)) {
                r.fail("hard_block", x + "," + y + "," + z + " " + id(s));
            }
            int code = lot.code(x, y, z, base);
            if (code == BlockCode.KEEP || code == BlockCode.GRASS || code == BlockCode.SHRUB || code == BlockCode.ROOF_MOSS) {
                continue;
            }
            if (code == BlockCode.AIR) {
                // cave_air (left by lakes and dungeons) is air too
                if (!s.isAir()) {
                    r.fail("building_mismatch", x + "," + y + "," + z + " " + id(s) + " expected air");
                }
                continue;
            }
            BlockState want = Palette.of(code, null);
            if (want != null && s.getBlock() != want.getBlock()) {
                r.fail("building_mismatch", x + "," + y + "," + z + " " + id(s) + " expected " + id(want));
            }
        }
    }

    private static void containers(PlanAudit.Report r, LevelChunk c, ChunkPlan cp) {
        for (BlockEntity be : c.getBlockEntities().values()) {
            BlockPos bp = be.getBlockPos();
            int i = ChunkPlan.idx(bp.getX() & 15, bp.getZ() & 15);
            boolean loot = be instanceof RandomizableContainer rc && rc.getLootTable() != null;
            boolean container = be instanceof BaseContainerBlockEntity;
            if (loot) {
                r.stat("loot_containers_anywhere", 1);
            }
            if (cp.kind[i] == ChunkPlan.NONE || cp.kind[i] == ChunkPlan.SKIRT) {
                continue;
            }
            // only what stands in the volume the mod builds (not buried below the fill, not in the mountain over a
            // tunnel, not in the valley under a bridge)
            int floor = ChunkPlan.surfaceY(cp.h2[i]);
            int ceiling = cp.kind[i] == ChunkPlan.LOT
                    ? floor + (cp.lot[i] == null ? 2 : cp.lot[i].heightAboveBase())
                    : floor + CEILING;
            // a structure's buried layers too (the garage of the wide building, the basement of the police station)
            int lowest = floor - 1;
            if (cp.kind[i] == ChunkPlan.LOT && cp.lot[i] != null && cp.lot[i].building != null) {
                lowest = Math.min(lowest, cp.h2[i] / 2 - 1 - cp.lot[i].depthBelowBase());
            }
            if (bp.getY() < lowest || bp.getY() > ceiling) {
                continue;
            }
            if (cp.kind[i] == ChunkPlan.LOT && cp.lot[i] != null && cp.lot[i].building != null
                    && Palette.exempt(c.getBlockState(bp))
                    && cp.lot[i].code(bp.getX(), bp.getY(), bp.getZ(), cp.h2[i] / 2 - 1) >= BlockCode.STRUCT) {
                // a block entity of a building structure: its containers carry the structure's loot table, its banners
                // the design saved in the template
                r.stat("structure_block_entities", 1);
                List<String[]> design = cp.lot[i].bannerAt(bp.getX(), bp.getY(), bp.getZ(), cp.h2[i] / 2 - 1);
                if (design != null) {
                    r.stat("structure_banners", 1);
                    if (!(be instanceof BannerBlockEntity bb)
                            || bb.getPatterns().layers().size() != design.size()) {
                        r.fail("banner_without_design", bp.toShortString() + " " + id(c.getBlockState(bp)));
                    }
                }
                if (be instanceof RandomizableContainer rc) {
                    r.stat("structure_loot_containers", 1);
                    boolean hard = cp.lot[i].hardAt(bp.getX(), bp.getY(), bp.getZ(), cp.h2[i] / 2 - 1);
                    if (hard) {
                        r.stat("structure_loot_hard", 1);
                    }
                    if (!loot) {
                        r.fail("structure_without_loot", bp.toShortString() + " " + id(c.getBlockState(bp)));
                    } else if (!rc.getLootTable().equals(Palette.lootTable(cp.lot[i].building, c.getBlockState(bp), hard))) {
                        // the table of the kind of structure, of the kind of container, and of its access
                        r.fail("structure_wrong_loot", bp.toShortString() + " " + id(c.getBlockState(bp)) + " "
                                + rc.getLootTable().location());
                    }
                }
                continue;
            }
            if (cp.kind[i] == ChunkPlan.LOT && cp.lot[i] != null && cp.lot[i].park != null
                    && Palette.exempt(c.getBlockState(bp))
                    && cp.lot[i].code(bp.getX(), bp.getY(), bp.getZ(), cp.h2[i] / 2 - 1) >= BlockCode.TEMPLATE) {
                // the empty chest of a park fountain
                r.stat("fountain_chests", 1);
                if (loot) {
                    r.fail("loot_in_city", bp.toShortString() + " fountain chest with a loot table");
                }
                continue;
            }
            if (loot) {
                r.fail("loot_in_city", bp.toShortString() + " " + id(c.getBlockState(bp)));
            } else if (container) {
                r.fail("container_in_city", bp.toShortString() + " " + id(c.getBlockState(bp)));
            }
        }
    }

    private static void components(PlanAudit.Report r, int x0, int z0, int w, int d, boolean[] drivable, double[] walk,
                                   boolean[] city) {
        int[] comp = new int[w * d];
        int next = 0;
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int s = 0; s < w * d; s++) {
            if (!drivable[s] || comp[s] != 0 || Double.isNaN(walk[s])) {
                continue;
            }
            next++;
            comp[s] = next;
            q.add(s);
            boolean hasCity = false;
            boolean border = false;
            int size = 0;
            while (!q.isEmpty()) {
                int g = q.poll();
                size++;
                int gx = g % w;
                int gz = g / w;
                hasCity |= city[g];
                if (gx == 0 || gz == 0 || gx == w - 1 || gz == d - 1) {
                    border = true;
                }
                int[][] nb = {{gx + 1, gz}, {gx - 1, gz}, {gx, gz + 1}, {gx, gz - 1}};
                for (int[] n : nb) {
                    if (n[0] < 0 || n[1] < 0 || n[0] >= w || n[1] >= d) {
                        continue;
                    }
                    int k = n[0] + n[1] * w;
                    if (comp[k] == 0 && drivable[k] && !Double.isNaN(walk[k]) && Math.abs(walk[k] - walk[g]) <= 0.5 + 1e-6) {
                        comp[k] = next;
                        q.add(k);
                    }
                }
            }
            r.stat("road_components", 1);
            if (!hasCity && !border) {
                r.fail("orphan_road", "component of " + size + " columns at " + (x0 + s % w) + "," + (z0 + s / w));
            }
        }
    }
}
