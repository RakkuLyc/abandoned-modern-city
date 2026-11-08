package com.abandonedmoderncity.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;

/**
 * Bounded cache for values that are pure functions of a long key. The value is computed outside the map, so a
 * computation may query the same cache recursively; two threads may compute the same value, which is harmless because
 * the results are identical. Eviction is generational: when the current map is full it becomes the previous one and
 * the old previous one is dropped, so the cache never empties at once.
 */
public final class Cache<V> {
    private final int generationSize;
    private volatile ConcurrentHashMap<Long, V> current;
    private volatile ConcurrentHashMap<Long, V> previous;

    public Cache(int maxSize) {
        this.generationSize = Math.max(1, maxSize / 2);
        this.current = new ConcurrentHashMap<>();
        this.previous = new ConcurrentHashMap<>();
    }

    public V get(long key, LongFunction<V> compute) {
        V v = current.get(key);
        if (v != null) {
            return v;
        }
        v = previous.get(key);
        if (v == null) {
            v = compute.apply(key);
        }
        ConcurrentHashMap<Long, V> cur = current;
        if (cur.size() >= generationSize) {
            synchronized (this) {
                if (current == cur) {
                    previous = cur;
                    current = new ConcurrentHashMap<>();
                }
                cur = current;
            }
        }
        V prev = cur.putIfAbsent(key, v);
        return prev != null ? prev : v;
    }

    public static long key(int a, int b) {
        return ((long) a << 32) ^ (b & 0xFFFFFFFFL);
    }
}
