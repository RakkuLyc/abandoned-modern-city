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

    private Template(String name, int sx, int sy, int sz, List<String> palette, int[] cells) {
        this.name = name;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.palette = List.copyOf(palette);
        this.cells = cells;
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
            int[] cells = new int[sx * sy * sz];
            java.util.Arrays.fill(cells, -1);
            for (Object o : (List<?>) root.get("blocks")) {
                Map<?, ?> b = (Map<?, ?>) o;
                List<?> pos = (List<?>) b.get("pos");
                int x = (Integer) pos.get(0);
                int y = (Integer) pos.get(1);
                int z = (Integer) pos.get(2);
                cells[x + sx * (z + sz * y)] = (Integer) b.get("state");
            }
            return new Template(name, sx, sy, sz, palette, cells);
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
