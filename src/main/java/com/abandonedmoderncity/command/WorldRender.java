package com.abandonedmoderncity.command;

import com.abandonedmoderncity.core.Highway;
import com.abandonedmoderncity.core.WorldPlan;
import java.awt.image.BufferedImage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** PNG renders of the generated blocks (map colors), to look at what was really built. */
public final class WorldRender {
    private WorldRender() {
    }

    private static int shade(int rgb, double f) {
        int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 255) * f));
        int g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 255) * f));
        int b = (int) Math.max(0, Math.min(255, (rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    /** Top-down view of chunks [cx0, cx1] x [cz0, cz1], one pixel per block, shaded by slope like a map. */
    public static BufferedImage topDown(ServerLevel level, int cx0, int cz0, int cx1, int cz1) {
        int w = (cx1 - cx0 + 1) * 16;
        int d = (cz1 - cz0 + 1) * 16;
        int[] height = new int[w * d];
        int[] color = new int[w * d];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int cz = cz0; cz <= cz1; cz++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int x = cx * 16 + lx;
                        int z = cz * 16 + lz;
                        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                        BlockState s = chunk.getBlockState(p.set(x, y, z));
                        while (y > level.getMinBuildHeight() && (s.isAir() || s.getMapColor(level, p) == MapColor.NONE)) {
                            y--;
                            s = chunk.getBlockState(p.set(x, y, z));
                        }
                        int g = (x - cx0 * 16) + (z - cz0 * 16) * w;
                        height[g] = y;
                        color[g] = s.getMapColor(level, p).col;
                    }
                }
            }
        }
        BufferedImage img = new BufferedImage(w, d, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int g = x + z * w;
                int north = z > 0 ? height[g - w] : height[g];
                double f = height[g] > north ? 1.1 : height[g] < north ? 0.82 : 0.96;
                img.setRGB(x, z, shade(color[g], f));
            }
        }
        return img;
    }

    /**
     * Side view along a highway's centerline (its path unrolled): every column of blocks from {@code below} under
     * the deck to {@code above} over it, so ramps, tunnels (lining and ceiling), bridges (deck, pillars) and the
     * terrain around them can be checked by eye. Sky is light blue.
     */
    public static BufferedImage alongHighway(ServerLevel level, WorldPlan plan, Highway h, int k0, int k1, int below, int above) {
        Highway.Detail det = h.detail(plan.terrain);
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int k = k0; k <= k1; k++) {
            int y = det.he[k] / 8;
            minY = Math.min(minY, y - below);
            maxY = Math.max(maxY, y + above);
        }
        minY = Math.max(minY, level.getMinBuildHeight());
        maxY = Math.min(maxY, level.getMaxBuildHeight() - 1);
        int w = k1 - k0 + 1;
        int hgt = maxY - minY + 1;
        BufferedImage img = new BufferedImage(w, hgt, BufferedImage.TYPE_INT_RGB);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int k = k0; k <= k1; k++) {
            int x = (int) Math.floor(h.x(k));
            int z = (int) Math.floor(h.z(k));
            LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
            for (int y = minY; y <= maxY; y++) {
                BlockState s = chunk.getBlockState(p.set(x, y, z));
                int col = s.isAir() ? 0xC8E1FF : s.getMapColor(level, p).col;
                img.setRGB(k - k0, maxY - y, col);
            }
        }
        return scale(img, 3);
    }

    /** Nearest-neighbour enlargement, so single blocks stay visible. */
    static BufferedImage scale(BufferedImage src, int f) {
        BufferedImage out = new BufferedImage(src.getWidth() * f, src.getHeight() * f, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                out.setRGB(x, y, src.getRGB(x / f, y / f));
            }
        }
        return out;
    }
}
