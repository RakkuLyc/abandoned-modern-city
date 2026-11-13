package com.abandonedmoderncity.command;

import com.abandonedmoderncity.core.BlockCode;
import com.abandonedmoderncity.core.ChunkPlan;
import com.abandonedmoderncity.core.City;
import com.abandonedmoderncity.core.Lot;
import com.abandonedmoderncity.core.Params;
import com.abandonedmoderncity.core.Profile;
import com.abandonedmoderncity.core.WorldPlan;
import com.abandonedmoderncity.gen.Overhangs;
import com.abandonedmoderncity.gen.Palette;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * SHA-256 of the blocks the mod decides in a rectangle of chunks: the positions {@link WorldAudit} compares with the
 * plan. On roads, the surface and the block under it, the air or plants over it, the street lights, the tunnel ceiling
 * and the bridge pillars; the tunnel walls; on lots, every block the plan gives but the codes the audit leaves out;
 * the leaves completing the crowns of structures; and the block entities of the structures (their saved data
 * with the keys sorted: loot table and seed, banner designs). Block states are hashed with their properties, except
 * those Minecraft works out again from the neighbours when it finishes a chunk ({@link #normal}).
 * <p>
 * Vanilla generation is not the same from one run to the next (features of neighbouring chunks placed in a different
 * order change ores, sculk, trees and chests), so the rest of the world is left out. On these positions two runs on
 * the same seed give the same digest (checked on worlds generated apart, fresh and reloaded); a change that maps a
 * plan code to another block, which the audit would not notice since it uses the same palette, changes it.
 */
public final class WorldFingerprint {
    private WorldFingerprint() {
    }

    private static final int CEILING = Params.CLEARANCE + 1;

    public record Result(String sha256, int chunks, long positions, int blockEntities) {
        @Override
        public String toString() {
            return sha256 + " (" + chunks + " chunks, " + positions + " positions, " + blockEntities + " block entities)";
        }
    }

    /**
     * The digest of chunks cx0..cx1 x cz0..cz1. With {@code detail} non-null, also writes one line per chunk there (a
     * short digest of its roads, its lots and the rest, and one hex digit per column), to find where two runs differ.
     */
    public static Result of(ServerLevel level, WorldPlan plan, int cx0, int cz0, int cx1, int cz1, File detail) {
        MessageDigest all = sha256();
        StringBuilder lines = detail == null ? null : new StringBuilder();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int chunks = 0;
        long positions = 0;
        int entities = 0;
        for (int cz = cz0; cz <= cz1; cz++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                LevelChunk c = level.getChunk(cx, cz);
                ChunkPlan cp = plan.chunk(cx, cz);
                Part roads = new Part();
                Part lots = new Part();
                Part rest = new Part();
                int[] columns = new int[256];
                roads.columns = columns;
                lots.columns = columns;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int i = ChunkPlan.idx(lx, lz);
                        int x = cx * 16 + lx;
                        int z = cz * 16 + lz;
                        roads.column = i;
                        lots.column = i;
                        boolean edge = lx == 0 || lx == 15 || lz == 0 || lz == 15;
                        roads.edge = edge;
                        lots.edge = edge;
                        switch (cp.kind[i]) {
                            case ChunkPlan.ROAD -> road(roads, c, cp, i, x, z, p);
                            case ChunkPlan.TUNNEL_WALL -> {
                                int ys = ChunkPlan.surfaceY(cp.h2[i]);
                                for (int y = ys - 1; y <= ys + CEILING; y++) {
                                    roads.state(c.getBlockState(p.set(x, y, z)));
                                }
                            }
                            case ChunkPlan.LOT -> lot(lots, c, cp, i, x, z, p);
                            default -> {
                            }
                        }
                    }
                }
                for (long k : new TreeSet<>(Overhangs.forChunk(plan, cx, cz).keySet())) {
                    rest.state(c.getBlockState(p.set(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k))));
                }
                entities += blockEntities(rest, level, c, cp);
                byte[] r = roads.digest();
                byte[] l = lots.digest();
                byte[] e = rest.digest();
                all.update(new byte[]{(byte) (cx >>> 24), (byte) (cx >>> 16), (byte) (cx >>> 8), (byte) cx,
                        (byte) (cz >>> 24), (byte) (cz >>> 16), (byte) (cz >>> 8), (byte) cz});
                all.update(r);
                all.update(l);
                all.update(e);
                positions += roads.count + lots.count + rest.count;
                chunks++;
                if (lines != null) {
                    lines.append(cx).append(' ').append(cz).append(" roads ").append(HexFormat.of().formatHex(r, 0, 4))
                            .append(" lots ").append(HexFormat.of().formatHex(l, 0, 4)).append(" rest ")
                            .append(HexFormat.of().formatHex(e, 0, 4)).append(" columns ");
                    for (int v : columns) {
                        lines.append(Character.forDigit((v ^ (v >>> 4) ^ (v >>> 8) ^ (v >>> 16)) & 15, 16));
                    }
                    lines.append('\n');
                }
            }
        }
        if (lines != null) {
            try {
                detail.getParentFile().mkdirs();
                Files.writeString(detail.toPath(), lines, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        return new Result(HexFormat.of().formatHex(all.digest()), chunks, positions, entities);
    }

    private static void road(Part part, LevelChunk c, ChunkPlan cp, int i, int x, int z, BlockPos.MutableBlockPos p) {
        int ys = ChunkPlan.surfaceY(cp.h2[i]);
        if ((cp.flags[i] & ChunkPlan.F_HOLE) != 0) {
            // a hole through a bridge deck: air at the surface and under it
            part.state(c.getBlockState(p.set(x, ys, z)));
            part.state(c.getBlockState(p.set(x, ys - 1, z)));
            return;
        }
        boolean tunnel = cp.struct[i] == Profile.TUNNEL;
        int top = ys + (tunnel ? CEILING : 4);
        for (int y = ys - 1; y <= top; y++) {
            part.state(c.getBlockState(p.set(x, y, z)));
        }
        int[] lamp = Palette.lampParts(cp, i, x, z);
        if (lamp != null) {
            for (int y = top + 1; y <= ys + City.LAMP_TOP; y++) {
                if (Palette.lampBlock(lamp, y - ys) != null) {
                    part.state(c.getBlockState(p.set(x, y, z)));
                }
            }
        }
        if ((cp.flags[i] & ChunkPlan.F_PILLAR) != 0) {
            int n = 0;
            for (int y = ys - 2; y > c.getMinBuildHeight(); y--) {
                BlockState s = c.getBlockState(p.set(x, y, z));
                if (!isPillar(s)) {
                    break;
                }
                part.state(s);
                n++;
            }
            part.i(n);
        }
    }

    /** A block of a bridge pillar (as {@link WorldAudit} tells it). */
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

    private static void lot(Part part, LevelChunk c, ChunkPlan cp, int i, int x, int z, BlockPos.MutableBlockPos p) {
        int base = cp.h2[i] / 2 - 1;
        Lot lot = cp.lot[i];
        if (lot == null) {
            part.state(c.getBlockState(p.set(x, base, z)));
            return;
        }
        int top = base + lot.heightAboveBase();
        for (int y = base - lot.depthBelowBase(); y <= top; y++) {
            int code = lot.code(x, y, z, base);
            // the codes the audit also leaves out (nothing planned, grass, short grass, moss carpet)
            if (code == BlockCode.KEEP || code == BlockCode.GRASS || code == BlockCode.SHRUB
                    || code == BlockCode.ROOF_MOSS) {
                continue;
            }
            part.state(c.getBlockState(p.set(x, y, z)));
        }
    }

    /**
     * The block entities of the structures, buried layers included, in order of position: the mod asks for each one while
     * it places it (and sets its loot table and seed, or its banner design), so they are there right away. Other block
     * entities are left out: the empty chest of a park fountain, for one, only shows up in the chunk once it has been
     * saved and loaded again (seen in the battery: missing in a freshly generated world, there after a reload).
     */
    private static int blockEntities(Part part, ServerLevel level, LevelChunk c, ChunkPlan cp) {
        List<BlockEntity> bes = new ArrayList<>();
        for (BlockEntity be : c.getBlockEntities().values()) {
            BlockPos bp = be.getBlockPos();
            int i = ChunkPlan.idx(bp.getX() & 15, bp.getZ() & 15);
            Lot lot = cp.lot[i];
            if (cp.kind[i] != ChunkPlan.LOT || lot == null || lot.building == null) {
                continue;
            }
            int base = cp.h2[i] / 2 - 1;
            if (bp.getY() >= base - lot.depthBelowBase() && bp.getY() <= base + lot.heightAboveBase()
                    && Palette.exempt(c.getBlockState(bp))
                    && lot.code(bp.getX(), bp.getY(), bp.getZ(), base) >= BlockCode.STRUCT) {
                bes.add(be);
            }
        }
        bes.sort((a, b) -> Long.compare(a.getBlockPos().asLong(), b.getBlockPos().asLong()));
        part.i(bes.size());
        for (BlockEntity be : bes) {
            StringBuilder sb = new StringBuilder();
            sb.append(BlockEntityType.getKey(be.getType())).append('@').append(be.getBlockPos().asLong()).append(':');
            canonical(sb, be.saveWithoutMetadata(level.registryAccess()));
            part.bytes(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        return bes.size();
    }

    /** Saved data as text, compound keys sorted (the order a compound keeps its keys in is not fixed). */
    private static void canonical(StringBuilder sb, Tag t) {
        if (t instanceof CompoundTag c) {
            sb.append('{');
            for (String k : new TreeSet<>(c.getAllKeys())) {
                sb.append(k).append(':');
                canonical(sb, c.get(k));
                sb.append(',');
            }
            sb.append('}');
        } else if (t instanceof ListTag l) {
            sb.append('[');
            for (Tag e : l) {
                canonical(sb, e);
                sb.append(',');
            }
            sb.append(']');
        } else {
            sb.append(t);
        }
    }

    /**
     * The state with the properties Minecraft recomputes from the neighbours when it finishes a chunk set to a fixed
     * value: the distance of leaves to a log, the shape of stairs (straight or a corner), the sides iron bars, panes and
     * fences connect to, whether grass has snow on it, whether a connected wall side is low or tall, and the faces of a
     * vine on the edge of the chunk; and any air as plain air. They depend on whether the next chunk was there yet,
     * which changes from one run to the next (seen in the battery on the same seed: leaves, bars and panes, a stair,
     * snowy grass, cave_air, a wall side, a vine).
     */
    static BlockState normal(BlockState s, boolean edge) {
        if (s.isAir()) {
            // cave_air left by a carver or a neighbour's feature is air like any other
            return Blocks.AIR.defaultBlockState();
        }
        if (s.hasProperty(SnowyDirtBlock.SNOWY)) {
            s = s.setValue(SnowyDirtBlock.SNOWY, false);
        }
        if (s.hasProperty(LeavesBlock.DISTANCE)) {
            s = s.setValue(LeavesBlock.DISTANCE, LeavesBlock.DECAY_DISTANCE);
        }
        if (s.hasProperty(StairBlock.SHAPE)) {
            s = s.setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
        }
        if (s.getBlock() instanceof CrossCollisionBlock) {
            s = s.setValue(CrossCollisionBlock.NORTH, false).setValue(CrossCollisionBlock.EAST, false)
                    .setValue(CrossCollisionBlock.SOUTH, false).setValue(CrossCollisionBlock.WEST, false);
        }
        if (s.getBlock() instanceof WallBlock) {
            // a connected side may be drawn low or tall: only whether it connects counts
            s = s.setValue(WallBlock.EAST_WALL, low(s.getValue(WallBlock.EAST_WALL)))
                    .setValue(WallBlock.NORTH_WALL, low(s.getValue(WallBlock.NORTH_WALL)))
                    .setValue(WallBlock.SOUTH_WALL, low(s.getValue(WallBlock.SOUTH_WALL)))
                    .setValue(WallBlock.WEST_WALL, low(s.getValue(WallBlock.WEST_WALL)));
        }
        if (edge && s.getBlock() instanceof VineBlock) {
            // a face held by a block of the next chunk is dropped if that chunk was not there yet
            s = s.getBlock().defaultBlockState();
        }
        return s;
    }

    private static WallSide low(WallSide side) {
        return side == WallSide.TALL ? WallSide.LOW : side;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** One digest over a stream of block states and ints, through a buffer. */
    private static final class Part {
        private final MessageDigest md = sha256();
        private final byte[] buf = new byte[4096];
        private int n;
        long count;

        /** Detail only: a small hash per column of the chunk (index {@code column}), or null. */
        int[] columns;
        int column;
        /** Whether the current column is on the edge of the chunk. */
        boolean edge;

        void state(BlockState s) {
            int id = Block.getId(normal(s, edge));
            i(id);
            count++;
            if (columns != null) {
                columns[column] = columns[column] * 31 + id;
            }
        }

        void i(int v) {
            if (n + 4 > buf.length) {
                md.update(buf, 0, n);
                n = 0;
            }
            buf[n++] = (byte) (v >>> 24);
            buf[n++] = (byte) (v >>> 16);
            buf[n++] = (byte) (v >>> 8);
            buf[n++] = (byte) v;
        }

        void bytes(byte[] a) {
            i(a.length);
            md.update(buf, 0, n);
            n = 0;
            md.update(a);
        }

        byte[] digest() {
            md.update(buf, 0, n);
            n = 0;
            return md.digest();
        }
    }
}
