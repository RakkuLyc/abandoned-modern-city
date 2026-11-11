package com.abandonedmoderncity.core;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Top-down PNG renders of the plan (no blocks): the detailed view colors every column by what the planner wants
 * there; the network view shows terrain, cities and highways over a large area.
 */
public final class PlanRender {
    private PlanRender() {
    }

    private static int rgb(int r, int g, int b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static int terrainColor(Terrain t, int x, int z) {
        int g = t.ground(x, z);
        if (t.water(x, z)) {
            int depth = Math.max(0, t.seaLevel() - g);
            return rgb(40, 90 - depth, 190 - depth * 2);
        }
        int h = g - t.seaLevel();
        if (h > 60) {
            return rgb(130 + h / 3, 130 + h / 3, 130 + h / 3);
        }
        return rgb(80 + h, 130 + h, 60 + h / 2);
    }

    /** One pixel per block over chunks [cx0, cx1] x [cz0, cz1]. */
    public static BufferedImage detail(WorldPlan plan, int cx0, int cz0, int cx1, int cz1) {
        int w = (cx1 - cx0 + 1) * 16;
        int d = (cz1 - cz0 + 1) * 16;
        BufferedImage img = new BufferedImage(w, d, BufferedImage.TYPE_INT_RGB);
        java.util.Map<Long, Integer> terrain = new java.util.HashMap<>();
        for (int cz = cz0; cz <= cz1; cz++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                ChunkPlan cp = plan.chunk(cx, cz);
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int i = ChunkPlan.idx(lx, lz);
                        int x = cx * 16 + lx;
                        int z = cz * 16 + lz;
                        int col;
                        if (cp.kind[i] == ChunkPlan.NONE) {
                            // the game's terrain noise is slow to sample: one sample per 8 x 8 blocks
                            int sx = x & ~7;
                            int sz = z & ~7;
                            col = terrain.computeIfAbsent(Cache.key(sx, sz), k -> terrainColor(plan.terrain, sx + 4, sz + 4));
                        } else {
                            col = color(plan, cp, i, x, z);
                        }
                        img.setRGB(x - cx0 * 16, z - cz0 * 16, col);
                    }
                }
            }
        }
        return img;
    }

    static int color(WorldPlan plan, ChunkPlan cp, int i, int x, int z) {
        switch (cp.kind[i]) {
            case ChunkPlan.ROAD -> {
                boolean city = (cp.flags[i] & ChunkPlan.F_CITY) != 0;
                if ((cp.flags[i] & ChunkPlan.F_PILLAR) != 0) {
                    return rgb(220, 30, 30);
                }
                if ((cp.flags[i] & ChunkPlan.F_LIGHT) != 0) {
                    return rgb(255, 255, 120);
                }
                int base = switch (cp.zone[i]) {
                    case ChunkPlan.MARK -> rgb(235, 235, 235);
                    case ChunkPlan.SIDEWALK -> rgb(165, 165, 165);
                    case ChunkPlan.MARK_HIGHWAY -> rgb(230, 190, 40);
                    case ChunkPlan.SHOULDER, ChunkPlan.PARAPET -> rgb(120, 120, 120);
                    default -> rgb(65, 65, 70);
                };
                if (!city) {
                    if (cp.struct[i] == Profile.BRIDGE && cp.zone[i] != ChunkPlan.MARK) {
                        base = cp.zone[i] == ChunkPlan.PARAPET ? rgb(200, 120, 220) : rgb(140, 60, 160);
                    } else if (cp.struct[i] == Profile.TUNNEL && cp.zone[i] != ChunkPlan.MARK) {
                        base = rgb(220, 130, 30);
                    }
                    if (ChunkPlan.isSlab(cp.h2[i]) && cp.zone[i] == ChunkPlan.LANE) {
                        base = rgb(((base >> 16) & 255) - 15, ((base >> 8) & 255) - 5, (base & 255) + 60);
                    }
                }
                return base;
            }
            case ChunkPlan.LOT -> {
                Lot lot = cp.lot[i];
                if (lot == null) {
                    if (cp.city != null) {
                        return rgb(155, 155, 150);
                    }
                    return rgb(120, 175, 100);
                }
                int base = cp.h2[i] / 2 - 1;
                if (lot.type == Lot.Type.WILD) {
                    return wildColor(lot, x, z, base);
                }
                for (int y = base + lot.heightAboveBase(); y > base; y--) {
                    int c = lot.code(x, y, z, base);
                    int v = overgrowth(c);
                    if (v == -2) {
                        continue;
                    }
                    if (v >= 0) {
                        return v;
                    }
                    if (c >= BlockCode.TEMPLATE && c < BlockCode.STRUCT) {
                        String st = Template.WATERFALL.palette.get(c - BlockCode.TEMPLATE);
                        if (st.contains("air")) {
                            continue;
                        }
                        return st.startsWith("minecraft:water") ? rgb(70, 110, 220) : st.contains("leaves") ? rgb(40, 120, 50)
                                : rgb(120, 125, 115);
                    }
                    if (c == BlockCode.RING_SLAB) {
                        return rgb(215, 215, 215);
                    }
                    if (lot.park != null && (c == BlockCode.SHRUB || c == BlockCode.TALL_GRASS_LOWER
                            || c == BlockCode.TALL_GRASS_UPPER)) {
                        continue;
                    }
                    if (c >= BlockCode.FLOWER && c < BlockCode.FLOWER + BlockCode.FLOWERS) {
                        return rgb(230, 90, 120);
                    }
                    if (c == BlockCode.SHRUB) {
                        return rgb(170, 175, 60);
                    }
                    if (c == BlockCode.LEAVES || c == BlockCode.LOG) {
                        return rgb(30, 110, 40);
                    }
                    if (c != BlockCode.KEEP && c != BlockCode.AIR) {
                        return rgb(150, 140, 160);
                    }
                }
                return switch (lot.type) {
                    case BUILDING -> {
                        int g = 90 + (int) (Hash.unit(Hash.of(lot.seed, 0x42L, 0, 0)) * 70);
                        yield rgb(g + 40, g, g - 20);
                    }
                    case PARK -> groundColor(lot.code(x, base, z, base));
                    case PLAZA -> rgb(200, 195, 175);
                    case EMPTY -> rgb(170, 150, 110);
                    default -> rgb(120, 175, 100);
                };
            }
            case ChunkPlan.SKIRT -> {
                return rgb(160, 130, 90);
            }
            case ChunkPlan.TUNNEL_WALL -> {
                return rgb(110, 60, 20);
            }
            default -> {
                return terrainColor(plan.terrain, x, z);
            }
        }
    }

    /** Top view of an overgrown block: crowns and rubble, else the ground (plants are see-through). */
    private static int wildColor(Lot lot, int x, int z, int base) {
        for (int y = base + lot.heightAboveBase(); y > base; y--) {
            int v = overgrowth(lot.code(x, y, z, base));
            if (v >= 0) {
                return v;
            }
        }
        return groundColor(lot.code(x, base, z, base));
    }

    /** Color of an overgrowth block seen from above; -2 for see-through plants and vines, -1 for anything else. */
    private static int overgrowth(int c) {
        if (c >= BlockCode.WILD_LEAVES && c < BlockCode.WILD_LEAVES + 4) {
            int k = c - BlockCode.WILD_LEAVES;
            return k == 1 ? rgb(95, 150, 60) : k == 3 ? rgb(35, 85, 55) : k == 2 ? rgb(30, 90, 30) : rgb(50, 125, 45);
        }
        if (c >= BlockCode.WILD_LOG && c < BlockCode.WILD_LOG + 4) {
            return rgb(100, 75, 45);
        }
        if (c >= BlockCode.RUBBLE && c < BlockCode.RUBBLE + BlockCode.RUBBLES) {
            return rgb(120, 120, 115);
        }
        if (c == BlockCode.MOSSY_SLAB) {
            return rgb(150, 165, 140);
        }
        if (c >= BlockCode.VINE && c < BlockCode.VINE + 4 || c == BlockCode.FERN || c == BlockCode.LARGE_FERN_LOWER
                || c == BlockCode.LARGE_FERN_UPPER || c == BlockCode.ROOF_MOSS || c == BlockCode.SHRUB
                || c == BlockCode.TALL_GRASS_LOWER || c == BlockCode.TALL_GRASS_UPPER || c == BlockCode.KEEP
                || c == BlockCode.AIR) {
            return -2;
        }
        return -1;
    }

    private static int groundColor(int c) {
        return switch (c) {
            case BlockCode.CITY_GROUND -> rgb(155, 155, 150);
            case BlockCode.MOSS_BLOCK -> rgb(85, 125, 45);
            case BlockCode.COARSE_DIRT -> rgb(125, 100, 75);
            case BlockCode.PATH -> rgb(150, 115, 70);
            default -> rgb(105, 165, 75);
        };
    }

    /** Overview: {@code bpp} blocks per pixel, cities sampled from their geometry, highways drawn as lines. */
    public static BufferedImage network(WorldPlan plan, int x0, int z0, int x1, int z1, int bpp) {
        return network(plan, x0, z0, x1, z1, bpp, true);
    }

    /** As above; without {@code withTerrain} the background is plain (sampling the game's terrain noise is slow). */
    public static BufferedImage network(WorldPlan plan, int x0, int z0, int x1, int z1, int bpp, boolean withTerrain) {
        int w = (x1 - x0) / bpp;
        int d = (z1 - z0) / bpp;
        BufferedImage img = new BufferedImage(w, d, BufferedImage.TYPE_INT_RGB);
        City.Hit hit = new City.Hit();
        // terrain sampled every 4 pixels
        int[] terrain = new int[(w / 4 + 1) * (d / 4 + 1)];
        for (int tz = 0; tz <= d / 4; tz++) {
            for (int tx = 0; tx <= w / 4; tx++) {
                terrain[tx + tz * (w / 4 + 1)] = withTerrain
                        ? terrainColor(plan.terrain, x0 + tx * 4 * bpp + 2 * bpp, z0 + tz * 4 * bpp + 2 * bpp)
                        : rgb(205, 220, 190);
            }
        }
        for (int pz = 0; pz < d; pz++) {
            for (int px = 0; px < w; px++) {
                int x = x0 + px * bpp + bpp / 2;
                int z = z0 + pz * bpp + bpp / 2;
                int col = terrain[px / 4 + (pz / 4) * (w / 4 + 1)];
                City c = plan.cityAt(x, z);
                if (c != null) {
                    c.query(x + 0.5, z + 0.5, hit);
                    if (hit.road) {
                        col = rgb(40, 40, 45);
                    } else if (hit.lot) {
                        col = rgb(175, 160, 150);
                    }
                }
                img.setRGB(px, pz, col);
            }
        }
        List<City> cities = plan.citiesIn(x0 - 2000, z0 - 2000, x1 + 2000, z1 + 2000);
        for (Highway h : plan.highwaysOf(cities)) {
            Highway.Detail det = h.detail(plan.terrain);
            for (int k = 0; k <= h.length; k++) {
                int px = (int) Math.floor((h.x(k) - x0) / bpp);
                int pz = (int) Math.floor((h.z(k) - z0) / bpp);
                int col = switch (det.type[k]) {
                    case Profile.BRIDGE -> rgb(190, 40, 220);
                    case Profile.TUNNEL -> rgb(255, 140, 0);
                    default -> rgb(10, 10, 10);
                };
                for (int oz = -1; oz <= 1; oz++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        int qx = px + ox;
                        int qz = pz + oz;
                        if (qx >= 0 && qz >= 0 && qx < w && qz < d) {
                            img.setRGB(qx, qz, col);
                        }
                    }
                }
            }
        }
        // city centers
        for (City c : cities) {
            int px = (c.cx - x0) / bpp;
            int pz = (c.cz - z0) / bpp;
            for (int oz = -3; oz <= 3; oz++) {
                for (int ox = -3; ox <= 3; ox++) {
                    int qx = px + ox;
                    int qz = pz + oz;
                    if (qx >= 0 && qz >= 0 && qx < w && qz < d && Math.abs(ox) + Math.abs(oz) <= 3) {
                        img.setRGB(qx, qz, rgb(255, 30, 30));
                    }
                }
            }
        }
        return img;
    }

    /** Side view of a highway, unrolled along its path: terrain (green/blue), deck (gray), bridge, tunnel. */
    public static BufferedImage profile(WorldPlan plan, Highway h, int scale) {
        Highway.Detail det = h.detail(plan.terrain);
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int k = 0; k <= h.length; k++) {
            minY = Math.min(minY, (int) det.ground[k]);
            maxY = Math.max(maxY, Math.max((int) det.ground[k], det.he[k] / 8));
        }
        minY = Math.min(minY, plan.sea) - 4;
        maxY += 10;
        int w = h.length / scale + 1;
        int hgt = maxY - minY + 1;
        BufferedImage img = new BufferedImage(w, hgt, BufferedImage.TYPE_INT_RGB);
        for (int px = 0; px < w; px++) {
            int k = Math.min(h.length, px * scale);
            int g = (int) det.ground[k];
            int road = (int) Math.floor(det.he[k] / 8.0);
            for (int y = minY; y <= maxY; y++) {
                int col;
                if (y == road - 1) {
                    col = switch (det.type[k]) {
                        case Profile.BRIDGE -> rgb(190, 40, 220);
                        case Profile.TUNNEL -> rgb(255, 140, 0);
                        default -> rgb(20, 20, 20);
                    };
                } else if (y < g) {
                    col = rgb(90, 140, 70);
                } else if (det.water[k] && y < plan.sea) {
                    col = rgb(40, 90, 190);
                } else {
                    col = rgb(200, 225, 255);
                }
                img.setRGB(px, maxY - y, col);
            }
        }
        return img;
    }

    public static void write(BufferedImage img, File file) throws IOException {
        file.getParentFile().mkdirs();
        ImageIO.write(img, "png", file);
    }
}
