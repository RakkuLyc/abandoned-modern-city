package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The zombie spawners of one copy of a building (types {@code buildings_*}) or a house ({@code houses}: {@link
 * #HOUSE_MIN} to {@link #HOUSE_MAX} in the whole house, near the chests and barrels of its template), drawn among the
 * spots of its {@link Rooms}
 * after its chests. A pure function of the copy's seed and of the template, like {@link ChestDraw}; it does not change
 * the chests.
 * <ol>
 *   <li><b>Which floors:</b> the floors over the ground counted from the street floor, one in two (the 1st, the 3rd,
 *   the 5th...), and every floor under the ground (the garage of the wide building).</li>
 *   <li><b>How many:</b> by the area of the floor under a ceiling ({@link Rooms.Floor#covered}), whatever the height
 *   of the building: one for every {@link #AREA} cells, the rest drawn (1.4 gives 2 in 40% of the copies), from
 *   {@link #MIN} to {@link #MAX}. A wide floor spreads its zombies; a narrow one does not crowd them.</li>
 *   <li><b>Where:</b> a spot of the floor that is not under the open sky (a zombie burns there) nor, while the floor
 *   has room elsewhere, in a lobby open onto the street, no chest on it, touching no chest and no other spawner
 *   ({@link #TOUCH}: within 1 block sideways and 1 up or down, so never on the cell in front of a chest either). A spawner guards a chest: it stands at most
 *   {@link #GUARD} blocks sideways ({@link #GUARD_DY} up or down) from one, the chest with the fewest guards first, the
 *   spots in the order of a weighted draw ({@code -ln(u) / weight}).</li>
 *   <li><b>When it does not fit</b> (no spot left near a chest, or no chest on the floor): the spawner goes to an
 *   exploration spot of the floor (far on its floor or hard to get to) farther than {@link #GUARD} from every chest;
 *   when there is none either, the spots of a lobby open onto the street are taken too, by the same rules; when there
 *   is still none, the floor gets fewer.</li>
 * </ol>
 * The chests of the copy are those drawn by {@link ChestDraw} and the barrels turned into chests ({@link Barrels}).
 */
public final class SpawnerDraw {
    private SpawnerDraw() {
    }

    /**
     * Whether the buildings get their zombie spawners ({@code zombieSpawners} in the config, read at startup before the
     * structures load: {@link Structures#SPAWNER_STATE}).
     */
    public static volatile boolean enabled = true;
    /** One spawner for every this many cells of a floor under a ceiling ({@link Rooms.Floor#covered})... */
    public static final int AREA = 500;
    /** ...at least this many on a floor that gets them... */
    public static final int MIN = 1;
    /** ...and at most this many. */
    public static final int MAX = 5;
    /** A spawner stands at most this far sideways from the chest it guards. */
    public static final int GUARD = 5;
    /** ...and at most this far up or down. */
    public static final int GUARD_DY = 2;
    /** Spawners in a house (type {@code houses}), in the whole house: from this many... */
    public static final int HOUSE_MIN = 1;
    /** ...to this many, each count as likely. */
    public static final int HOUSE_MAX = 3;
    /** Two blocks this close or closer sideways, and up or down, touch. */
    public static final int TOUCH = 1;
    private static final long SALT_COUNT = 0x5350434EL; // "SPCN"
    private static final long SALT_KEY = 0x53504B59L; // "SPKY"

    /** A spawner: its spot, and whether it guards a chest ({@code false}: an exploration spot away from the chests). */
    public record Spawner(Rooms.Spot spot, boolean guards) {
    }

    /**
     * How many spawners the floor {@code f} gets in the copy of seed {@code seed} (before they are placed: a floor where
     * they do not fit gets fewer); 0 on a floor that gets none.
     */
    public static int count(Rooms.Layout layout, int groundY, int f, long seed) {
        List<Rooms.Floor> floors = layout.floors();
        Rooms.Floor fl = floors.get(f);
        if (fl.y() > groundY) {
            int above = 0;
            for (int g = 0; g < f; g++) {
                above += floors.get(g).y() > groundY ? 1 : 0;
            }
            if (above % 2 != 0) {
                return 0;
            }
        }
        int n = (int) Math.floor((double) fl.covered() / AREA + Hash.unit(Hash.of(seed, SALT_COUNT, f)));
        return Math.max(MIN, Math.min(MAX, n));
    }

    /**
     * The spawners of the copy of seed {@code seed} of a building whose template is {@code sx} by {@code sz}, ground
     * layer at template height {@code groundY}, among the spots of {@code layout}; {@code chests}: the template cells
     * {x, y, z} of every chest of the copy. Floor by floor, upwards.
     */
    public static List<Spawner> draw(Rooms.Layout layout, int sx, int sz, int groundY, long seed, List<int[]> chests) {
        return draw(layout, sx, sz, groundY, seed, chests, false);
    }

    /**
     * The spawners of the copy of seed {@code seed} of a house ({@link #houseCount} in the whole house, its floors
     * together), the same way: near the chests and barrels of its template ({@code chests}), else away from them, else
     * in its rooms open onto the street.
     */
    public static List<Spawner> drawHouse(Rooms.Layout layout, int sx, int sz, long seed, List<int[]> chests) {
        return draw(layout, sx, sz, 0, seed, chests, true);
    }

    /** How many spawners a house gets in the copy of seed {@code seed}: {@link #HOUSE_MIN} to {@link #HOUSE_MAX}. */
    public static int houseCount(long seed) {
        return Hash.range(Hash.of(seed, SALT_COUNT, -1), HOUSE_MIN, HOUSE_MAX);
    }

    private static List<Spawner> draw(Rooms.Layout layout, int sx, int sz, int groundY, long seed, List<int[]> chests,
                                      boolean house) {
        if (layout.spots().isEmpty()) {
            return List.of();
        }
        Map<Integer, List<Rooms.Spot>> byFloor = new HashMap<>();
        Map<Integer, List<Rooms.Spot>> streetByFloor = new HashMap<>();
        java.util.Set<Long> chestCells = new java.util.HashSet<>();
        for (int[] c : chests) {
            chestCells.add(key(c[0], c[1], c[2]));
        }
        Map<Rooms.Spot, Double> key = new HashMap<>();
        for (Rooms.Spot s : layout.spots()) {
            if (s.is(Rooms.OPEN) || chestCells.contains(key(s.x(), s.y(), s.z()))) {
                continue;
            }
            long cell = s.x() + (long) sx * (s.z() + (long) sz * s.y());
            double u = Math.max(Hash.unit(Hash.of(seed, SALT_KEY, cell)), 1e-12);
            key.put(s, -Math.log(u) / s.weight());
            // a house draws among the spots of all its floors together
            (s.is(Rooms.STREET) ? streetByFloor : byFloor).computeIfAbsent(house ? 0 : s.floor(), k -> new ArrayList<>())
                    .add(s);
        }
        Comparator<Rooms.Spot> byKey = Comparator.comparingDouble((Rooms.Spot s) -> key.get(s))
                .thenComparingInt(Rooms.Spot::y).thenComparingInt(Rooms.Spot::z).thenComparingInt(Rooms.Spot::x);
        List<Spawner> out = new ArrayList<>();
        int[] guards = new int[chests.size()];
        for (int f = 0; f < (house ? 1 : layout.floors().size()); f++) {
            int n = house ? houseCount(seed) : count(layout, groundY, f, seed);
            if (n == 0) {
                continue;
            }
            List<Rooms.Spot> order = new ArrayList<>(byFloor.getOrDefault(f, List.of()));
            order.sort(byKey);
            int placed = 0;
            for (; placed < n; placed++) {
                Spawner sp = next(order, chests, guards, out);
                if (sp == null) {
                    break;
                }
                out.add(sp);
            }
            // a floor short of its spawners takes the spots of its lobby open onto the street too
            List<Rooms.Spot> street = streetByFloor.getOrDefault(f, List.of());
            if (placed < n && !street.isEmpty()) {
                order.addAll(street);
                order.sort(byKey);
                for (; placed < n; placed++) {
                    Spawner sp = next(order, chests, guards, out);
                    if (sp == null) {
                        break;
                    }
                    out.add(sp);
                }
            }
        }
        return List.copyOf(out);
    }

    /** The next spawner of a floor: near the chest with the fewest guards, else away from every chest, else none. */
    private static Spawner next(List<Rooms.Spot> order, List<int[]> chests, int[] guards, List<Spawner> taken) {
        Rooms.Spot best = null;
        int bestChest = -1;
        Rooms.Spot away = null;
        for (Rooms.Spot s : order) {
            if (touches(s, chests, taken)) {
                continue;
            }
            int chest = -1;
            for (int c = 0; c < chests.size(); c++) {
                if (guards(s, chests.get(c)) && (chest < 0 || guards[c] < guards[chest])) {
                    chest = c;
                }
            }
            if (chest >= 0) {
                if (best == null || guards[chest] < guards[bestChest]) {
                    best = s;
                    bestChest = chest;
                }
            } else if (away == null && ChestDraw.exploration(s)) {
                away = s;
            }
        }
        if (best != null) {
            guards[bestChest]++;
            return new Spawner(best, true);
        }
        return away == null ? null : new Spawner(away, false);
    }

    /** Whether the spot touches a chest or a spawner already taken (or is one). */
    static boolean touches(Rooms.Spot s, List<int[]> chests, List<Spawner> taken) {
        for (int[] c : chests) {
            if (near(s.x() - c[0], s.y() - c[1], s.z() - c[2], TOUCH, TOUCH)) {
                return true;
            }
        }
        for (Spawner o : taken) {
            Rooms.Spot t = o.spot();
            if (near(s.x() - t.x(), s.y() - t.y(), s.z() - t.z(), TOUCH, TOUCH)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a spawner on the spot is close enough to guard the chest {x, y, z}. */
    static boolean guards(Rooms.Spot s, int[] c) {
        return near(s.x() - c[0], s.y() - c[1], s.z() - c[2], GUARD, GUARD_DY);
    }

    private static boolean near(int dx, int dy, int dz, int side, int up) {
        return Math.abs(dx) <= side && Math.abs(dz) <= side && Math.abs(dy) <= up;
    }

    private static long key(int x, int y, int z) {
        return ((long) y << 42) ^ ((long) z << 21) ^ x;
    }

    /**
     * The template cells {x, y, z} of every chest of the copy of seed {@code seed} of a building: those drawn by
     * {@link ChestDraw} and the barrels turned into chests (not those that hold something: they stay barrels).
     */
    public static List<int[]> chestsOf(Structures.Def d, long seed) {
        Template t = d.t();
        int layer = t.sx * t.sz;
        List<Integer> cells = new ArrayList<>(d.chests(seed).keySet());
        for (Map.Entry<Integer, int[]> e : d.barrels().entrySet()) {
            int[] b = e.getValue();
            if (b[Barrels.FORCED] == 0 && Barrels.kept(b, seed, layer)) {
                cells.add(e.getKey());
            }
        }
        cells.sort(null);
        List<int[]> out = new ArrayList<>();
        for (int c : cells) {
            out.add(new int[]{c % t.sx, c / layer, (c / t.sx) % t.sz});
        }
        return out;
    }

    /**
     * The template cells {x, y, z} of the chests and barrels saved in the template of a house (they stay in every copy;
     * a house draws no chest), worked out once per house.
     */
    static List<int[]> houseChests(Structures.Def d) {
        return HOUSE_CHESTS.computeIfAbsent(d.name(), k -> {
            Template t = d.t();
            List<int[]> out = new ArrayList<>();
            for (int y = d.oy(); y < d.oy() + d.h(); y++) {
                for (int z = d.oz(); z < d.oz() + d.d(); z++) {
                    for (int x = d.ox(); x < d.ox() + d.w(); x++) {
                        int q = t.at(x, y, z);
                        if (q < 0) {
                            continue;
                        }
                        String b = Template.blockOf(t.palette.get(q));
                        if (b.equals("minecraft:chest") || b.equals("minecraft:trapped_chest")
                                || b.equals("minecraft:barrel")) {
                            out.add(new int[]{x, y, z});
                        }
                    }
                }
            }
            return List.copyOf(out);
        });
    }

    private static final Map<String, List<int[]>> HOUSE_CHESTS = new java.util.concurrent.ConcurrentHashMap<>();

    /** The spawners of the copy of seed {@code seed} of the structure {@code d} (none but in the buildings and houses). */
    public static List<Spawner> of(Structures.Def d, long seed) {
        if (d.rooms().spots().isEmpty()) {
            return List.of();
        }
        if (Structures.isHouseType(d.type())) {
            return drawHouse(d.rooms(), d.t().sx, d.t().sz, seed, houseChests(d));
        }
        return draw(d.rooms(), d.t().sx, d.t().sz, d.oy() + d.ground(), seed, chestsOf(d, seed));
    }
}
