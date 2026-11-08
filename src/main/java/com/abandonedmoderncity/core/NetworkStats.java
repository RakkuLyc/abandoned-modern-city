package com.abandonedmoderncity.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * How well the cities of an area are linked: cells with a city (and why the others have none), candidate links
 * (Gabriel graph), highways kept (and why the others were dropped), cities without any highway, and connected groups
 * of cities. Used by the unit tests and by the in-world self-test.
 */
public final class NetworkStats {
    public int cells;
    public int cities;
    public final Map<String, Integer> noCity = new TreeMap<>();
    public int links;
    public int kept;
    public final Map<String, Integer> dropped = new TreeMap<>();
    public int isolated;
    public int components;
    /** City blocks in total, and cities of one or two blocks only. */
    public int blocks;
    public int tiny;
    public final List<Highway> highways = new ArrayList<>();
    public final List<City> cityList = new ArrayList<>();

    /** Cells ci0..ci1 x cj0..cj1. */
    public static NetworkStats of(WorldPlan plan, int ci0, int cj0, int ci1, int cj1) {
        NetworkStats s = new NetworkStats();
        for (int cj = cj0; cj <= cj1; cj++) {
            for (int ci = ci0; ci <= ci1; ci++) {
                s.cells++;
                City c = plan.city(ci, cj);
                if (c != null) {
                    s.cities++;
                    s.cityList.add(c);
                    s.blocks += c.blockCount();
                    if (c.blockCount() <= 2) {
                        s.tiny++;
                    }
                } else {
                    s.noCity.merge(City.diagnose(plan.seed, plan.params, plan.terrain, ci, cj), 1, Integer::sum);
                }
            }
        }
        Set<Long> seen = new HashSet<>();
        Map<Long, Integer> degree = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        for (City a : s.cityList) {
            parent.put(a.id, a.id);
        }
        for (City a : s.cityList) {
            for (City b : plan.links(a)) {
                long key = a.id < b.id ? a.id * 31 + b.id : b.id * 31 + a.id;
                if (!seen.add(key)) {
                    continue;
                }
                s.links++;
                Highway h = plan.candidate(a, b);
                if (h != null && plan.isFinal(h)) {
                    s.kept++;
                    s.highways.add(h);
                    degree.merge(a.id, 1, Integer::sum);
                    degree.merge(b.id, 1, Integer::sum);
                    parent.putIfAbsent(a.id, a.id);
                    parent.putIfAbsent(b.id, b.id);
                    union(parent, a.id, b.id);
                } else {
                    String why = plan.rejection(a, b).replaceAll(" at point.*", "").replaceAll("\\(.*\\)", "").trim();
                    s.dropped.merge(why, 1, Integer::sum);
                }
            }
        }
        Set<Long> roots = new HashSet<>();
        for (City c : s.cityList) {
            if (!degree.containsKey(c.id)) {
                s.isolated++;
            }
            roots.add(find(parent, c.id));
        }
        s.components = roots.size();
        return s;
    }

    public double keptRatio() {
        return links == 0 ? 1 : kept / (double) links;
    }

    public double isolatedRatio() {
        return cities == 0 ? 0 : isolated / (double) cities;
    }

    @Override
    public String toString() {
        return String.format("cells %d, cities %d (no city: %s; %.1f blocks per city, %d of 1-2 blocks), candidate links %d, highways %d (%.0f%%, dropped: %s), "
                        + "cities without highway %d (%.0f%%), groups of linked cities %d",
                cells, cities, noCity, cities == 0 ? 0.0 : blocks / (double) cities, tiny, links, kept, 100 * keptRatio(), dropped,
                isolated, 100 * isolatedRatio(), components);
    }

    private static long find(Map<Long, Long> p, long x) {
        while (p.get(x) != x) {
            p.put(x, p.get(p.get(x)));
            x = p.get(x);
        }
        return x;
    }

    private static void union(Map<Long, Long> p, long a, long b) {
        p.put(find(p, a), find(p, b));
    }
}
