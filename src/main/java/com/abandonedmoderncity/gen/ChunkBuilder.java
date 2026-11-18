package com.abandonedmoderncity.gen;

import com.abandonedmoderncity.core.BlockCode;
import com.abandonedmoderncity.core.ChunkPlan;
import com.abandonedmoderncity.core.City;
import com.abandonedmoderncity.core.Hash;
import com.abandonedmoderncity.core.Lot;
import com.abandonedmoderncity.core.Params;
import com.abandonedmoderncity.core.Profile;
import com.abandonedmoderncity.core.RoadDecay;
import com.abandonedmoderncity.core.WorldPlan;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;

/**
 * Writes the plan of one chunk into the proto chunk.
 *
 * <ol>
 *   <li>Each planned column is read once (highest block, natural ground, its surface block), then built:
 *   <b>roads</b> on the ground cut everything above and fill below with stone; <b>bridges</b> get a two-block deck,
 *   parapets and pillars down to the ground; <b>tunnels</b> get a floor, 5 blocks of air and a ceiling (glowstone
 *   every 12 blocks), between two {@code TUNNEL_WALL} columns; <b>lots</b> are flattened to the city level and get
 *   their yard, park, plaza or building; <b>slopes</b> clamp the natural ground to the planned range.</li>
 *   <li>Liquids next to any air the chunk opened become stone (no water or lava flowing onto the streets later).</li>
 *   <li>Leaves left without their trunk are marked for post-processing, so vanilla lets them decay.</li>
 *   <li>Trees, lakes and plants that chunks decorated later put on neighbouring roads are removed again
 *   ({@link NeighbourRepair#repairNeighbours}).</li>
 *   <li>Natural vegetation left hanging in the air by what this chunk cut or removed (pieces of trees, vines, snow)
 *   is removed ({@link Floating}).</li>
 * </ol>
 * Writes stay in this chunk, except steps 2-4 which only touch the 3 x 3 chunks the region allows.
 */
final class ChunkBuilder {
    static final int CEILING = Params.CLEARANCE + 1;

    private final WorldPlan plan;
    private final WorldGenRegion region;
    private final ChunkAccess chunk;
    private final ChunkPlan cp;
    private final int bx;
    private final int bz;
    private final int minY;
    private final int maxY;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final int[] airLo = new int[256];
    private final int[] airHi = new int[256];
    private final List<int[]> removedTrunks = new ArrayList<>();
    /** The orphan leaves removed ({@link #removeOrphanLeaves}): what rested on them may float. */
    private final LongArrayList orphansRemoved = new LongArrayList();
    private final int scanTop;

    // column scan results
    private int top;
    private int groundY;
    private BlockState natural;

    private ChunkBuilder(WorldPlan plan, WorldGenRegion region, ChunkAccess chunk, ChunkPlan cp) {
        this.plan = plan;
        this.region = region;
        this.chunk = chunk;
        this.cp = cp;
        this.bx = chunk.getPos().getMinBlockX();
        this.bz = chunk.getPos().getMinBlockZ();
        this.minY = chunk.getMinBuildHeight();
        this.maxY = chunk.getMaxBuildHeight();
        int highest = chunk.getHighestFilledSectionIndex();
        this.scanTop = highest < 0 ? minY - 1
                : Math.min(maxY - 1, SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(highest)) + 15);
        Arrays.fill(airLo, Integer.MAX_VALUE);
        Arrays.fill(airHi, Integer.MIN_VALUE);
    }

    static boolean build(WorldPlan plan, WorldGenRegion region, ChunkAccess chunk) {
        ChunkPos c = chunk.getPos();
        removeVillageMobs(plan, chunk);
        ChunkPlan cp = plan.chunk(c.x, c.z);
        boolean built = false;
        // where vegetation may have lost what held it: around what this chunk cut and what the repair removed
        LongArrayList seeds = new LongArrayList();
        if (cp.any) {
            ChunkBuilder b = new ChunkBuilder(plan, region, chunk, cp);
            b.run();
            b.seeds(seeds);
            built = true;
        }
        built |= bridgeVines(plan, chunk);
        NeighbourRepair.repairNeighbours(plan, region, c, seeds);
        built |= removeFloating(plan, region, c, seeds);
        return built;
    }

    /**
     * The loose blocks ({@link Floating#loose}) beside the columns this chunk opened (from 1 under to 1 over the
     * opened range, in the column and its 8 neighbours) and around the orphan leaves it removed.
     */
    private void seeds(LongArrayList out) {
        int rx0 = bx - 16;
        int rz0 = bz - 16;
        int rx1 = bx + 31;
        int rz1 = bz + 31;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 256; i++) {
            if (airLo[i] > airHi[i]) {
                continue;
            }
            int x = bx + (i & 15);
            int z = bz + (i >> 4);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx;
                    int nz = z + dz;
                    if (nx < rx0 || nx > rx1 || nz < rz0 || nz > rz1) {
                        continue;
                    }
                    for (int y = Math.max(minY, airLo[i] - 1); y <= Math.min(maxY - 1, airHi[i] + 1); y++) {
                        if (Floating.loose(region.getBlockState(p.set(nx, y, nz)))) {
                            out.add(BlockPos.asLong(nx, y, nz));
                        }
                    }
                }
            }
        }
        for (int k = 0; k < orphansRemoved.size(); k++) {
            Floating.seedAround(orphansRemoved.getLong(k), out);
        }
    }

    /**
     * Removes the groups of natural vegetation reached from {@code seeds} that touch nothing holding them, inside the
     * 3 x 3 chunks this chunk may write ({@link Floating#find}; a group reaching the edge counts as standing, and the
     * chunk built next to it looks again). Nothing the plan places is removed.
     */
    private static boolean removeFloating(WorldPlan plan, WorldGenRegion region, ChunkPos c, LongArrayList seeds) {
        if (seeds.isEmpty()) {
            return false;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        ChunkAccess center = region.getChunk(c.x, c.z);
        int minY = center.getMinBuildHeight();
        int maxY = center.getMaxBuildHeight() - 1;
        long[] removed = {0};
        Floating.find(seeds, (x, y, z) -> region.getBlockState(p.set(x, y, z)), new Floating.Planned(plan),
                (c.x - 1) << 4, (c.z - 1) << 4, ((c.x + 1) << 4) + 15, ((c.z + 1) << 4) + 15, minY, maxY,
                new Long2ByteOpenHashMap(), g -> {
                    for (int k = 0; k < g.size(); k++) {
                        region.setBlock(p.set(g.getLong(k)), Palette.AIR, Block.UPDATE_CLIENTS);
                    }
                    removed[0] += g.size();
                });
        FLOATING_REMOVED.addAndGet(removed[0]);
        return removed[0] > 0;
    }

    /** Creatures a village brings with its houses ({@link #removeVillageMobs}). */
    private static final Set<String> VILLAGE_MOBS = Set.of("minecraft:villager",
            "minecraft:zombie_villager", "minecraft:iron_golem", "minecraft:cat");
    /** Village creatures removed so far (the self-test reports it). */
    static final AtomicInteger VILLAGE_MOBS_REMOVED = new AtomicInteger();
    /** Blocks of floating vegetation removed so far ({@link #removeFloating}). */
    static final AtomicLong FLOATING_REMOVED = new AtomicLong();

    /**
     * A village generated where a city is: its houses are placed before the city, each
     * with its villagers, iron golem and cats, which stay when the city is built over them. They are taken out of the
     * chunk being generated (the village is placed in this chunk before this feature runs) when they stand in the
     * city or {@link City#VILLAGE_MARGIN} or less from it. Other creatures stay.
     */
    static int removeVillageMobs(WorldPlan plan, ChunkAccess chunk) {
        if (!(chunk instanceof ProtoChunk pc) || pc.getEntities().isEmpty()) {
            return 0;
        }
        ChunkPos c = chunk.getPos();
        City city = plan.cityNear(c.getMiddleBlockX(), c.getMiddleBlockZ(),
                16 + City.VILLAGE_MARGIN);
        if (city == null) {
            return 0;
        }
        City.Hit hit = new City.Hit();
        int before = pc.getEntities().size();
        pc.getEntities().removeIf(tag -> {
            if (!VILLAGE_MOBS.contains(tag.getString("id"))) {
                return false;
            }
            ListTag pos = tag.getList("Pos", Tag.TAG_DOUBLE);
            return pos.size() == 3 && city.within(pos.getDouble(0), pos.getDouble(2),
                    City.VILLAGE_MARGIN, hit);
        });
        int removed = before - pc.getEntities().size();
        VILLAGE_MOBS_REMOVED.addAndGet(removed);
        return removed;
    }

    private void run() {
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = ChunkPlan.idx(lx, lz);
                byte kind = cp.kind[i];
                if (kind == ChunkPlan.NONE) {
                    continue;
                }
                scan(lx, lz);
                switch (kind) {
                    case ChunkPlan.ROAD -> road(i, lx, lz);
                    case ChunkPlan.LOT -> lot(i, lx, lz);
                    case ChunkPlan.SKIRT -> skirt(i, lx, lz);
                    case ChunkPlan.TUNNEL_WALL -> tunnelWall(i, lx, lz);
                    default -> {
                    }
                }
            }
        }
        seal();
        overhangs();
        removeOrphanLeaves();
    }

    /** Chunk key -> the crown leaves hanging out of structures planned there ({@link Overhangs}). */
    private final Map<Long, Map<Long, BlockState>> overhangCache = new HashMap<>();

    private Map<Long, BlockState> overhangsOf(int cx, int cz) {
        return overhangCache.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> Overhangs.forChunk(plan, cx, cz));
    }

    /** The leaves that complete the crowns cut by a structure's edge, where this chunk has air for them. */
    private void overhangs() {
        for (Map.Entry<Long, BlockState> e : overhangsOf(chunk.getPos().x, chunk.getPos().z).entrySet()) {
            BlockPos b = BlockPos.of(e.getKey());
            if (chunk.getBlockState(b).isAir()) {
                set(b.getX(), b.getY(), b.getZ(), e.getValue());
            }
        }
    }

    /**
     * Vines hanging from the bridges down to the ground: in {@link
     * RoadDecay#BRIDGE_VINES} of the columns just outside a bridge edge (the one with the wall), a strand hangs on the
     * side of the deck from its surface down to the first block that is not air (ground or water). Done for every
     * chunk, also those with nothing planned in them, since the column outside the bridge may be the only part of a
     * chunk next to it.
     */
    private static boolean bridgeVines(WorldPlan plan, ChunkAccess chunk) {
        ChunkPos c = chunk.getPos();
        int bx = c.getMinBlockX();
        int bz = c.getMinBlockZ();
        int minY = chunk.getMinBuildHeight();
        ChunkPlan cp = plan.chunk(c.x, c.z);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean any = false;
        // west, east, north, south: the side the bridge is on, as Palette.VINES counts them
        int[][] side = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                byte kind = cp.kind[ChunkPlan.idx(lx, lz)];
                if (kind != ChunkPlan.NONE && kind != ChunkPlan.SKIRT) {
                    continue;
                }
                int x = bx + lx;
                int z = bz + lz;
                if (!RoadDecay.bridgeVine(plan.seed, x, z)) {
                    continue;
                }
                for (int k = 0; k < 4; k++) {
                    int nx = x + side[k][0];
                    int nz = z + side[k][1];
                    ChunkPlan np = plan.chunk(nx >> 4, nz >> 4);
                    int ni = ChunkPlan.idx(nx & 15, nz & 15);
                    if (np.kind[ni] != ChunkPlan.ROAD || np.struct[ni] != Profile.BRIDGE || !ChunkPlan.walled(np.zone[ni])) {
                        continue;
                    }
                    BlockState vine = Palette.VINES[k];
                    for (int y = ChunkPlan.surfaceY(np.h2[ni]); y > minY; y--) {
                        if (!chunk.getBlockState(p.set(x, y, z)).isAir()) {
                            break;
                        }
                        chunk.setBlockState(p, vine, false);
                        any = true;
                    }
                    break;
                }
            }
        }
        return any;
    }

    // ------------------------------------------------------------------ column reading

    private void scan(int lx, int lz) {
        int x = bx + lx;
        int z = bz + lz;
        int y = scanTop;
        while (y >= minY && chunk.getBlockState(pos.set(x, y, z)).isAir()) {
            y--;
        }
        top = y;
        while (y >= minY && !isGround(chunk.getBlockState(pos.set(x, y, z)))) {
            y--;
        }
        groundY = y;
        natural = y >= minY ? chunk.getBlockState(pos.set(x, y, z)) : null;
    }

    /** Natural ground: solid terrain, not trees, plants, liquids, snow layers or mushrooms. */
    static boolean isGround(BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty()) {
            return false;
        }
        if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(Blocks.MANGROVE_ROOTS) || s.is(Blocks.BEE_NEST)
                || s.is(Blocks.MUSHROOM_STEM)) {
            return false;
        }
        Block b = s.getBlock();
        if (b instanceof HugeMushroomBlock || b instanceof CactusBlock || b instanceof BambooStalkBlock) {
            return false;
        }
        return s.isSolid();
    }

    /** Natural decoration that must never stand on or hang over a road. */
    static boolean isDecoration(BlockState s) {
        if (s.isAir()) {
            return false;
        }
        Block b = s.getBlock();
        return s.is(BlockTags.LEAVES) && !s.getValue(LeavesBlock.PERSISTENT) || s.is(BlockTags.LOGS)
                || b instanceof VineBlock || b instanceof MultifaceBlock || b instanceof SnowLayerBlock
                || b instanceof BushBlock || b instanceof CocoaBlock || b instanceof BeehiveBlock
                || s.is(Blocks.MANGROVE_ROOTS) || b instanceof HugeMushroomBlock || s.is(Blocks.MUSHROOM_STEM)
                || b instanceof CarpetBlock || b instanceof HangingRootsBlock || b instanceof PointedDripstoneBlock
                || b instanceof SporeBlossomBlock || b instanceof CaveVinesBlock || b instanceof CaveVinesPlantBlock
                || b instanceof BambooStalkBlock || b instanceof SugarCaneBlock || b instanceof CactusBlock
                || b instanceof WebBlock || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON);
    }

    // ------------------------------------------------------------------ writing

    private void set(int x, int y, int z, BlockState s) {
        if (y < minY || y >= maxY) {
            return;
        }
        pos.set(x, y, z);
        BlockState old = chunk.getBlockState(pos);
        if (old == s) {
            return;
        }
        if (old.hasBlockEntity() || s.hasBlockEntity() || PoiTypes.forState(old).isPresent()) {
            // through the region: removes the old block entity / point of interest properly, and creates the new one
            // (the fountain chest)
            region.setBlock(pos, s, Block.UPDATE_CLIENTS);
        } else {
            chunk.setBlockState(pos, s, false);
        }
        if (s.getBlock() instanceof IronBarsBlock) {
            // glass panes connect to their neighbours when the chunk is promoted (walls are connected by wallState)
            chunk.markPosForPostprocessing(pos.immutable());
        }
    }

    /** Air from y0 to y1 (inclusive) in column i; remembers the opened range and removed tree trunks. */
    private void clear(int i, int x, int z, int y0, int y1) {
        y0 = Math.max(y0, minY);
        y1 = Math.min(y1, maxY - 1);
        if (y1 < y0) {
            return;
        }
        int trunkTop = Integer.MIN_VALUE;
        for (int y = y0; y <= y1; y++) {
            BlockState old = chunk.getBlockState(pos.set(x, y, z));
            if (old.isAir()) {
                continue;
            }
            if (old.is(BlockTags.LOGS)) {
                trunkTop = y;
            }
            set(x, y, z, Palette.AIR);
        }
        markAir(i, y0, y1);
        if (trunkTop != Integer.MIN_VALUE) {
            removedTrunks.add(new int[]{x, trunkTop, z});
        }
    }

    private void markAir(int i, int y0, int y1) {
        airLo[i] = Math.min(airLo[i], y0);
        airHi[i] = Math.max(airHi[i], y1);
    }

    // ------------------------------------------------------------------ column kinds

    private void road(int i, int lx, int lz) {
        int x = bx + lx;
        int z = bz + lz;
        int h2 = cp.h2[i];
        boolean slab = ChunkPlan.isSlab(h2);
        int ys = ChunkPlan.surfaceY(h2);
        byte zone = cp.zone[i];
        byte flags = cp.flags[i];
        // abandoned roads: some columns taken over by grass, moss, dirt or cracks, the others worn (core/RoadDecay)
        BlockState surface = Palette.roadSurfaceAt(plan.seed, x, z, zone, cp.struct[i], slab);
        BlockState base = Palette.roadBase(zone);
        int decay = RoadDecay.surface(plan.seed, x, z, zone, cp.struct[i], slab);
        switch (cp.struct[i]) {
            case Profile.BRIDGE -> {
                if ((flags & ChunkPlan.F_HOLE) != 0) {
                    // a hole through the deck: nothing at all
                    clear(i, x, z, ys - 1, top);
                } else {
                    clear(i, x, z, ys + 1, top);
                    set(x, ys, z, surface);
                    // the old slab: mossy and cracked stones under the surface
                    set(x, ys - 1, z, Palette.BRIDGE_STONES[RoadDecay.bridgeStone(plan.seed, x, ys - 1, z)]);
                }
                if ((flags & ChunkPlan.F_PILLAR) != 0) {
                    for (int y = ys - 2; y > groundY; y--) {
                        set(x, y, z, Palette.BRIDGE_STONES[RoadDecay.bridgeStone(plan.seed, x, y, z)]);
                    }
                }
            }
            case Profile.TUNNEL -> {
                set(x, ys, z, surface);
                set(x, ys - 1, z, base);
                for (int y = ys + 1; y < ys + CEILING; y++) {
                    set(x, y, z, Palette.AIR);
                }
                markAir(i, ys + 1, ys + CEILING - 1);
                set(x, ys + CEILING, z, (flags & ChunkPlan.F_LIGHT) != 0 ? Palette.LIGHT : Palette.STONE);
            }
            default -> {
                clear(i, x, z, ys + 1, top);
                set(x, ys, z, surface);
                set(x, ys - 1, z, base);
                for (int y = ys - 2; y > groundY; y--) {
                    set(x, y, z, Palette.STONE);
                }
            }
        }
        int[] lamp = Palette.lampParts(cp, i, x, z);
        // no plant under a street light standing on the column (its post, or the lamp fallen on the lane)
        boolean lampFoot = lamp != null && lamp[1] != BlockCode.KEEP;
        if (decay != BlockCode.KEEP && (flags & ChunkPlan.F_HOLE) == 0 && !lampFoot) {
            // plants of a taken-over column (on the ground or on a bridge)
            for (int dy = 1; dy <= 2; dy++) {
                int plant = RoadDecay.plant(plan.seed, x, z, dy, decay);
                if (plant != BlockCode.KEEP) {
                    set(x, ys + dy, z, Palette.of(plant, null));
                }
            }
        }
        for (int dy = 1; lamp != null && dy < lamp.length; dy++) {
            BlockState part = Palette.lampBlock(lamp, dy);
            if (part != null) {
                set(x, ys + dy, z, part);
            }
        }
        if (ChunkPlan.walled(zone)) {
            int top = wallTop(plan, x, z);
            for (int y = ys + 1; y <= top; y++) {
                set(x, y, z, wallState(plan, x, y, z));
            }
        }
    }

    private void tunnelWall(int i, int lx, int lz) {
        int x = bx + lx;
        int z = bz + lz;
        int ys = ChunkPlan.surfaceY(cp.h2[i]);
        for (int y = ys - 1; y <= ys + CEILING; y++) {
            set(x, y, z, Palette.STONE);
        }
    }

    private void lot(int i, int lx, int lz) {
        int x = bx + lx;
        int z = bz + lz;
        int base = cp.h2[i] / 2 - 1;
        clear(i, x, z, base + 1, top);
        for (int y = base - 1; y > groundY; y--) {
            set(x, y, z, y >= base - 3 ? Palette.DIRT : Palette.STONE);
        }
        Lot lot = cp.lot[i];
        // the foundation: what stood there before the city (a cave, the house of a village) is not left under the floor
        for (int y = base - FOUNDATION; y < base; y++) {
            if ((lot == null || lot.code(x, y, z, base) == BlockCode.KEEP) && foundationGap(chunk.getBlockState(pos.set(x, y, z)))) {
                set(x, y, z, Palette.DIRT);
            }
        }
        if (lot == null) {
            set(x, base, z, cp.city != null ? Palette.of(cp.city.yardCode(), null) : Palette.lotGround(natural));
            return;
        }
        int maxH = base + lot.heightAboveBase();
        for (int y = base - lot.depthBelowBase(); y <= maxH; y++) {
            BlockState s = Palette.of(lot.code(x, y, z, base), natural);
            if (s != null) {
                set(x, y, z, s);
                if (lot.building != null && s.hasBlockEntity()) {
                    loot(lot, s, lot.hardAt(x, y, z, base));
                    List<String[]> banner = lot.bannerAt(x, y, z, base);
                    if (banner != null) {
                        Palette.applyBanner(region.getBlockEntity(pos.set(x, y, z)), banner, region.registryAccess());
                    }
                }
            }
        }
    }

    /**
     * Layers under a lot's floor that never keep air, a liquid or a block entity where the plan keeps what the world has
     * (finding 75: a village chest and beds, caves, water and the top of a dungeon were left right under
     * the floor; the cells a structure plans there, its buried layers, are never touched). The neighbour repair applies
     * the same rule ({@link NeighbourRepair}).
     */
    static final int FOUNDATION = 3;

    /** What the foundation does not keep: air, a liquid or a block entity (a chest, a bed, a spawner...). */
    static boolean foundationGap(BlockState s) {
        return s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity();
    }

    /**
     * Loot table of the container just placed at {@link #pos} in a structure ({@link Palette#lootTable}: the kind of
     * structure, the kind of container, and whether it is hard to get to). Set through the region, like vanilla
     * structures do (the block entity of a chunk being generated only exists once asked for), with a seed from the
     * lot and the position.
     */
    private void loot(Lot lot, BlockState s, boolean hard) {
        if (region.getBlockEntity(pos) instanceof RandomizableContainer c) {
            long seed = Hash.of(lot.seed, 0x4C4F4F54L, pos.getX(), pos.getY() * 31L + pos.getZ());
            c.setLootTable(Palette.lootTable(lot.building, s, hard), seed);
        }
    }

    private void skirt(int i, int lx, int lz) {
        if (groundY < minY) {
            return;
        }
        int x = bx + lx;
        int z = bz + lz;
        int g = groundY + 1;
        int target = Math.max(cp.lo[i], Math.min(cp.hi[i], g));
        if (target < g) {
            clear(i, x, z, target, top);
            set(x, target - 1, z, Palette.surfaceFor(natural));
        } else if (target > g) {
            BlockState surface = Palette.surfaceFor(natural);
            BlockState sub = Palette.subsurfaceFor(natural);
            for (int y = g; y < target; y++) {
                set(x, y, z, y == target - 1 ? surface : y >= target - 3 ? sub : Palette.STONE);
            }
        }
    }

    // ------------------------------------------------------------------ after the columns

    /** Liquids next to air this chunk opened become stone, so nothing flows onto streets and lots later. */
    private void seal() {
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 256; i++) {
            if (airLo[i] > airHi[i]) {
                continue;
            }
            int x = bx + (i & 15);
            int z = bz + (i >> 4);
            int yEnd = Math.min(airHi[i], airLo[i] + 64);
            for (int y = airLo[i]; y <= yEnd; y++) {
                if (!chunk.getBlockState(pos.set(x, y, z)).isAir()) {
                    continue;
                }
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    n.set(x + d.getStepX(), y, z + d.getStepZ());
                    BlockState s = region.getBlockState(n);
                    if (!s.getFluidState().isEmpty() && !plannedFluid(n.getX(), n.getY(), n.getZ())) {
                        region.setBlock(n, Palette.STONE, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /**
     * Whether the plan itself puts leaves at (x, y, z) (a tree of a block's core or park, or of a structure): those
     * are real leaves too, but their trunk may stand in a chunk not built yet, so they are never taken for orphans.
     */
    private boolean plannedLeaves(int x, int y, int z) {
        if (overhangsOf(x >> 4, z >> 4).containsKey(BlockPos.asLong(x, y, z))) {
            return true;
        }
        ChunkPlan p = plan.chunk(x >> 4, z >> 4);
        int i = ChunkPlan.idx(x & 15, z & 15);
        if (p.kind[i] != ChunkPlan.LOT || p.lot[i] == null) {
            return false;
        }
        int code = p.lot[i].code(x, y, z, p.h2[i] / 2 - 1);
        if (code == BlockCode.KEEP || code == BlockCode.AIR) {
            return false;
        }
        BlockState s = Palette.of(code, null);
        return s != null && s.getBlock() instanceof LeavesBlock;
    }

    /** Whether the plan itself puts a fluid at (x, y, z) (fountain water, field channels): it is never sealed. */
    private boolean plannedFluid(int x, int y, int z) {
        ChunkPlan p = plan.chunk(x >> 4, z >> 4);
        int i = ChunkPlan.idx(x & 15, z & 15);
        return p.kind[i] == ChunkPlan.LOT && plannedFluid(p.lot[i], x, y, z, p.h2[i] / 2 - 1);
    }

    static boolean plannedFluid(Lot lot, int x, int y, int z, int base) {
        if (lot == null || !lot.contains(x, z)) {
            return false;
        }
        int code = lot.code(x, y, z, base);
        if (code == BlockCode.KEEP) {
            return false;
        }
        BlockState s = Palette.of(code, null);
        return s != null && !s.getFluidState().isEmpty();
    }

    /** How far (through leaves) a leaf block may be from a log before vanilla lets it decay. */
    private static final int LEAF_REACH = 6;
    /** Leaves are removed up to this far (horizontally) from a cut trunk. */
    private static final int ORPHAN_RADIUS = 7;

    /**
     * Leaves of a tree whose trunk the city or a slope cut are removed right away, the way vanilla decay would remove
     * them: within {@link #ORPHAN_RADIUS} of a cut trunk, every natural leaf block that is no longer within
     * {@link #LEAF_REACH} steps (through leaves) of any log goes. Logs are searched in a wider box, so leaves held by
     * a tree standing just outside stay.
     */
    private void removeOrphanLeaves() {
        if (removedTrunks.isEmpty()) {
            return;
        }
        int reach = ORPHAN_RADIUS + LEAF_REACH;
        int x0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z0 = Integer.MAX_VALUE;
        int z1 = Integer.MIN_VALUE;
        int y0 = Integer.MAX_VALUE;
        int y1 = Integer.MIN_VALUE;
        for (int[] t : removedTrunks) {
            x0 = Math.min(x0, t[0]);
            x1 = Math.max(x1, t[0]);
            y0 = Math.min(y0, t[1]);
            y1 = Math.max(y1, t[1]);
            z0 = Math.min(z0, t[2]);
            z1 = Math.max(z1, t[2]);
        }
        // the box stays inside the 3 x 3 chunks the region may touch
        x0 = Math.max(bx - 16, x0 - reach);
        x1 = Math.min(bx + 31, x1 + reach);
        z0 = Math.max(bz - 16, z0 - reach);
        z1 = Math.min(bz + 31, z1 + reach);
        y0 = Math.max(minY, y0 - 8);
        y1 = Math.min(maxY - 1, y1 + 12);
        int w = x1 - x0 + 1;
        int d = z1 - z0 + 1;
        int h = y1 - y0 + 1;
        byte[] dist = new byte[w * d * h];
        Arrays.fill(dist, Byte.MAX_VALUE);
        boolean[] leaf = new boolean[w * d * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    BlockState s = region.getBlockState(p.set(x, y, z));
                    int k = (x - x0) + w * ((z - z0) + d * (y - y0));
                    if (s.is(BlockTags.LOGS)) {
                        dist[k] = 0;
                        queue.add(k);
                    } else if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) {
                        leaf[k] = true;
                    }
                }
            }
        }
        int[] step = {1, -1, w, -w, w * d, -w * d};
        while (!queue.isEmpty()) {
            int k = queue.poll();
            int next = dist[k] + 1;
            if (next > LEAF_REACH) {
                continue;
            }
            int kx = k % w;
            int kz = (k / w) % d;
            int ky = k / (w * d);
            for (int s = 0; s < 6; s++) {
                if (s == 0 && kx == w - 1 || s == 1 && kx == 0 || s == 2 && kz == d - 1 || s == 3 && kz == 0
                        || s == 4 && ky == h - 1 || s == 5 && ky == 0) {
                    continue;
                }
                int n = k + step[s];
                if (leaf[n] && dist[n] > next) {
                    dist[n] = (byte) next;
                    queue.add(n);
                }
            }
        }
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    int k = (x - x0) + w * ((z - z0) + d * (y - y0));
                    if (!leaf[k] || dist[k] <= LEAF_REACH || !nearCutTrunk(x, z) || plannedLeaves(x, y, z)) {
                        continue;
                    }
                    region.setBlock(p.set(x, y, z), Palette.AIR, Block.UPDATE_CLIENTS);
                    orphansRemoved.add(BlockPos.asLong(x, y, z));
                }
            }
        }
    }

    private boolean nearCutTrunk(int x, int z) {
        for (int[] t : removedTrunks) {
            if (Math.abs(t[0] - x) <= ORPHAN_RADIUS && Math.abs(t[2] - z) <= ORPHAN_RADIUS) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ highway edge walls

    /** Surface y of a highway edge column (one with a wall), or Integer.MIN_VALUE when the column is not one. */
    private static int edgeSurface(WorldPlan plan, int x, int z) {
        ChunkPlan cp = plan.chunk(x >> 4, z >> 4);
        int i = ChunkPlan.idx(x & 15, z & 15);
        boolean wall = cp.kind[i] == ChunkPlan.ROAD && ChunkPlan.walled(cp.zone[i]) && !wallGap(plan, cp, i, x, z);
        return wall ? ChunkPlan.surfaceY(cp.h2[i]) : Integer.MIN_VALUE;
    }

    /** Whether the wall of an edge column has fallen (on the bridges only). */
    static boolean wallGap(WorldPlan plan, ChunkPlan cp, int i, int x, int z) {
        return cp.struct[i] == Profile.BRIDGE && RoadDecay.wallGap(plan.seed, x, z);
    }

    private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /**
     * Highest wall block of an edge column: one above its surface, or higher when the next edge column along a ramp
     * starts higher, so that the two walls share a level and connect.
     */
    static int wallTop(WorldPlan plan, int x, int z) {
        int own = edgeSurface(plan, x, z);
        if (own == Integer.MIN_VALUE) {
            // no wall here (a fallen stretch of a bridge wall): Integer.MIN_VALUE, nothing to build or to keep free
            return Integer.MIN_VALUE;
        }
        int top = own + 1;
        for (int[] d : SIDES) {
            int n = edgeSurface(plan, x + d[0], z + d[1]);
            if (n != Integer.MIN_VALUE) {
                top = Math.max(top, n + 1);
            }
        }
        return top;
    }

    /**
     * Wall block with its connections worked out from the plan (not left to vanilla when the chunk is promoted: the
     * neighbouring chunk may not exist yet, and the wall would stay unconnected on that side).
     */
    static BlockState wallState(WorldPlan plan, int x, int y, int z) {
        boolean[] c = new boolean[4];
        for (int k = 0; k < 4; k++) {
            int nx = x + SIDES[k][0];
            int nz = z + SIDES[k][1];
            int n = edgeSurface(plan, nx, nz);
            c[k] = n != Integer.MIN_VALUE && y >= n + 1 && y <= wallTop(plan, nx, nz);
        }
        boolean straight = c[0] && c[2] && !c[1] && !c[3] || c[1] && c[3] && !c[0] && !c[2];
        boolean up = !straight || y < wallTop(plan, x, z);
        ChunkPlan cp = plan.chunk(x >> 4, z >> 4);
        boolean mossy = cp.struct[ChunkPlan.idx(x & 15, z & 15)] == Profile.BRIDGE && RoadDecay.mossyWall(plan.seed, x, z);
        return (mossy ? Palette.MOSSY_WALL : Palette.WALL)
                .setValue(WallBlock.NORTH_WALL, c[0] ? WallSide.LOW : WallSide.NONE)
                .setValue(WallBlock.EAST_WALL, c[1] ? WallSide.LOW : WallSide.NONE)
                .setValue(WallBlock.SOUTH_WALL, c[2] ? WallSide.LOW : WallSide.NONE)
                .setValue(WallBlock.WEST_WALL, c[3] ? WallSide.LOW : WallSide.NONE)
                .setValue(WallBlock.UP, up);
    }
}
