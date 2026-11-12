package com.abandonedmoderncity.core;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * A structure template saved by a structure block (.nbt), read without Minecraft classes: its size, its palette as
 * block state strings ({@code minecraft:name[key=value,...]}) and the palette index of every position (-1 where the
 * template has nothing). The game layer turns the strings into block states.
 */
public final class Template {
    public final String name;
    public final int sx;
    public final int sy;
    public final int sz;
    public final List<String> palette;
    private final int[] cells;
    /**
     * Banner designs saved in the template (block entity data): cell index ({@code x + sx * (z + sz * y)}) -> the
     * layers, each {@code {pattern, color}} (e.g. {@code {"minecraft:stripe_left", "red"}}).
     */
    public final Map<Integer, List<String[]>> banners;

    private Template(String name, int sx, int sy, int sz, List<String> palette, int[] cells,
                     Map<Integer, List<String[]>> banners) {
        this.banners = Map.copyOf(banners);
        this.name = name;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.palette = List.copyOf(palette);
        this.cells = cells;
    }

    /** A template built in code (tests): {@code cells[x + sx * (z + sz * y)]} = palette index or -1. */
    static Template of(String name, int sx, int sy, int sz, List<String> palette, int[] cells) {
        return new Template(name, sx, sy, sz, palette, cells, Map.of());
    }

    /**
     * This template with the banners of {@code src} copied in (asked by the user, 13 nov 2026: the color variants of the
     * pharmacies were saved without the D R U G S T O R E letters): each banner of {@code src} at (x, y, z) goes to
     * (x + dx, y, z + dz), with the color {@code from} replaced by {@code to} in its block and in its layers. The
     * template grows when a banner falls just outside it. Every banner must land on air (or outside) and hang on a
     * block of this template, or loading fails.
     */
    Template withBannersFrom(Template src, int dx, int dz, String from, String to) {
        int nsx = sx;
        int nsz = sz;
        int ox = 0;
        int oz = 0;
        for (int k : src.banners.keySet()) {
            int x = k % src.sx + dx;
            int z = (k / src.sx) % src.sz + dz;
            nsx = Math.max(nsx, x + 1);
            nsz = Math.max(nsz, z + 1);
            ox = Math.max(ox, -x);
            oz = Math.max(oz, -z);
        }
        nsx += ox;
        nsz += oz;
        int[] nc = new int[nsx * sy * nsz];
        java.util.Arrays.fill(nc, -1);
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    nc[(x + ox) + nsx * ((z + oz) + nsz * y)] = cells[x + sx * (z + sz * y)];
                }
            }
        }
        List<String> np = new ArrayList<>(palette);
        Map<Integer, List<String[]>> nb = new java.util.HashMap<>();
        for (Map.Entry<Integer, List<String[]>> e : banners.entrySet()) {
            int k = e.getKey();
            int x = k % sx + ox;
            int z = (k / sx) % sz + oz;
            int y = k / (sx * sz);
            nb.put(x + nsx * (z + nsz * y), e.getValue());
        }
        for (Map.Entry<Integer, List<String[]>> e : src.banners.entrySet()) {
            int k = e.getKey();
            int y = k / (src.sx * src.sz);
            int x = k % src.sx + dx + ox;
            int z = (k / src.sx) % src.sz + dz + oz;
            String state = src.palette.get(src.at(k % src.sx, y, (k / src.sx) % src.sz));
            int i = x + nsx * (z + nsz * y);
            int here = nc[i];
            if (here >= 0 && !palette.get(here).startsWith("minecraft:air") && !palette.get(here).startsWith("minecraft:cave_air")) {
                throw new IllegalStateException(name + ": banner of " + src.name + " at " + x + "," + y + "," + z + " over " + palette.get(here));
            }
            // a wall banner hangs on the block behind it (opposite to where it faces)
            String facing = state.replaceAll(".*facing=(\\w+).*", "$1");
            int bx = x + (facing.equals("east") ? -1 : facing.equals("west") ? 1 : 0);
            int bz = z + (facing.equals("south") ? -1 : facing.equals("north") ? 1 : 0);
            int behind = bx >= 0 && bz >= 0 && bx < nsx && bz < nsz ? nc[bx + nsx * (bz + nsz * y)] : -1;
            if (state.contains("_wall_banner") && (behind < 0 || palette.get(behind).startsWith("minecraft:air"))) {
                throw new IllegalStateException(name + ": banner of " + src.name + " at " + x + "," + y + "," + z + " hangs on nothing");
            }
            String block = state.replace("minecraft:" + from + "_", "minecraft:" + to + "_");
            int id = np.indexOf(block);
            if (id < 0) {
                np.add(block);
                id = np.size() - 1;
            }
            nc[i] = id;
            List<String[]> layers = new ArrayList<>();
            for (String[] l : e.getValue()) {
                layers.add(new String[]{l[0], l[1].equals(from) ? to : l[1]});
            }
            nb.put(i, List.copyOf(layers));
        }
        return new Template(name, nsx, sy, nsz, np, nc, nb);
    }

    /** Neutral facade blocks (white and light stone) that a color variant may have painted. */
    static final java.util.Set<String> NEUTRAL = java.util.Set.of("minecraft:white_concrete", "minecraft:smooth_quartz",
            "minecraft:calcite", "minecraft:polished_diorite");

    /**
     * This template with the neutral blocks of {@code src} (same size and place) put back where this one painted them
     * another color (asked by the user, 13 nov 2026: the color variants of the pharmacies spell F A R M Á C I A in
     * colored blocks on a white wall, which the originals do not have). Returns the cells changed in {@code count[0]}.
     */
    Template withNeutralFrom(Template src, int[] count) {
        // the same place; the original may be a little larger (the big pharmacy has a column of banners more)
        if (src.sy != sy || Math.abs(src.sx - sx) > 1 || Math.abs(src.sz - sz) > 1) {
            throw new IllegalStateException(name + ": " + src.name + " has another size");
        }
        Map<Integer, String> changed = new java.util.HashMap<>();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < Math.min(sz, src.sz); z++) {
                for (int x = 0; x < Math.min(sx, src.sx); x++) {
                    int ps = src.at(x, y, z);
                    int pv = at(x, y, z);
                    if (ps < 0 || pv < 0) {
                        continue;
                    }
                    String want = src.palette.get(ps);
                    String have = palette.get(pv);
                    if (NEUTRAL.contains(Structures.blockOf(want)) && !want.equals(have)
                            && !Structures.blockOf(have).equals("minecraft:air")) {
                        changed.put(x + sx * (z + sz * y), want);
                    }
                }
            }
        }
        count[0] = changed.size();
        return withStates(changed);
    }

    /** This template with the blocks of some cells changed: cell index -> block state string ({@link Weathering}). */
    Template withStates(Map<Integer, String> changed) {
        List<String> np = new ArrayList<>(palette);
        int[] nc = cells.clone();
        for (Map.Entry<Integer, String> e : new java.util.TreeMap<>(changed).entrySet()) {
            int id = np.indexOf(e.getValue());
            if (id < 0) {
                np.add(e.getValue());
                id = np.size() - 1;
            }
            nc[e.getKey()] = id;
        }
        return new Template(name, sx, sy, sz, np, nc, banners);
    }

    /** Palette index at template position (x, y, z), or -1 (outside or nothing there). */
    public int at(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
            return -1;
        }
        return cells[x + sx * (z + sz * y)];
    }

    public int indexOf(String state) {
        return palette.indexOf(state);
    }

    /** The park fountain (waterfall.nbt, supplied by the user). */
    public static final Template WATERFALL = load("waterfall");

    static Template load(String name) {
        try (InputStream in = Template.class.getResourceAsStream("/amcs/" + name + ".nbt")) {
            if (in == null) {
                throw new IllegalStateException("missing template /amcs/" + name + ".nbt");
            }
            DataInputStream d = new DataInputStream(new GZIPInputStream(in));
            int type = d.readUnsignedByte();
            d.readUTF();
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) read(d, type);
            List<?> size = (List<?>) root.get("size");
            int sx = (Integer) size.get(0);
            int sy = (Integer) size.get(1);
            int sz = (Integer) size.get(2);
            List<String> palette = new ArrayList<>();
            for (Object o : (List<?>) root.get("palette")) {
                Map<?, ?> e = (Map<?, ?>) o;
                StringBuilder sb = new StringBuilder((String) e.get("Name"));
                Object props = e.get("Properties");
                if (props instanceof Map<?, ?> m && !m.isEmpty()) {
                    sb.append('[');
                    boolean first = true;
                    for (Map.Entry<?, ?> p : new TreeMap<>(m).entrySet()) {
                        if (!first) {
                            sb.append(',');
                        }
                        first = false;
                        sb.append(p.getKey()).append('=').append(p.getValue());
                    }
                    sb.append(']');
                }
                palette.add(sb.toString());
            }
            Map<Integer, List<String[]>> banners = new java.util.HashMap<>();
            int[] cells = new int[sx * sy * sz];
            java.util.Arrays.fill(cells, -1);
            for (Object o : (List<?>) root.get("blocks")) {
                Map<?, ?> b = (Map<?, ?>) o;
                List<?> pos = (List<?>) b.get("pos");
                int x = (Integer) pos.get(0);
                int y = (Integer) pos.get(1);
                int z = (Integer) pos.get(2);
                cells[x + sx * (z + sz * y)] = (Integer) b.get("state");
                if (b.get("nbt") instanceof Map<?, ?> nbt && nbt.get("patterns") instanceof List<?> layers) {
                    List<String[]> out = new ArrayList<>();
                    for (Object layer : layers) {
                        if (layer instanceof Map<?, ?> l && l.get("pattern") instanceof String pat && l.get("color") instanceof String col) {
                            out.add(new String[]{pat, col});
                        }
                    }
                    if (!out.isEmpty()) {
                        banners.put(x + sx * (z + sz * y), List.copyOf(out));
                    }
                }
            }
            return new Template(name, sx, sy, sz, palette, cells, banners);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Object read(DataInputStream d, int type) throws IOException {
        switch (type) {
            case 1:
                return d.readByte();
            case 2:
                return d.readShort();
            case 3:
                return d.readInt();
            case 4:
                return d.readLong();
            case 5:
                return d.readFloat();
            case 6:
                return d.readDouble();
            case 7: {
                byte[] a = new byte[d.readInt()];
                d.readFully(a);
                return a;
            }
            case 8:
                return d.readUTF();
            case 9: {
                int et = d.readUnsignedByte();
                int n = d.readInt();
                List<Object> list = new ArrayList<>(Math.max(0, n));
                for (int k = 0; k < n; k++) {
                    list.add(read(d, et));
                }
                return list;
            }
            case 10: {
                Map<String, Object> m = new TreeMap<>();
                while (true) {
                    int et = d.readUnsignedByte();
                    if (et == 0) {
                        return m;
                    }
                    String key = d.readUTF();
                    m.put(key, read(d, et));
                }
            }
            case 11: {
                int[] a = new int[d.readInt()];
                for (int k = 0; k < a.length; k++) {
                    a[k] = d.readInt();
                }
                return a;
            }
            case 12: {
                long[] a = new long[d.readInt()];
                for (int k = 0; k < a.length; k++) {
                    a[k] = d.readLong();
                }
                return a;
            }
            default:
                throw new IOException("bad NBT tag " + type);
        }
    }
}
