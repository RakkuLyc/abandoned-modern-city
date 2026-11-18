package com.abandonedmoderncity.core;

/**
 * The part of loading a structure that depends on nothing else (read the {@code .nbt}, give a color variant the
 * neutral wall and the banners of its original, apply the {@link Weathering}): done for the 52 structures in parallel
 * when {@link Structures} loads, then everything else (the block state ids, whose order the world depends on) goes on
 * one by one in the order of the index. It must not touch {@link Structures}: that class is still being initialized
 * by the thread waiting for these results, and a call into it from another thread would wait for ever.
 */
final class Preload {
    private Preload() {
    }

    /** A template as saved with what its index line copies in; {@code letters} the neutral cells given back, or -1. */
    record Source(Template template, int letters) {
    }

    /** What {@link #of} gives: the template ready for {@code Structures.of} and what the overgrowth added. */
    record Prepared(Template template, int letters, Weathering.Report report) {
    }

    /**
     * The template of structure {@code name} as saved, the neutral blocks of {@code neutralFrom} given back (the
     * colored letters of a variant) and the banners of {@code bannerSpec} copied in
     * ({@code <source>@<dx>,<dz>:<from color>=<to color>}); both may be null.
     */
    static Source source(String name, String neutralFrom, String bannerSpec) {
        Template t = Template.load("buildings/" + name);
        int letters = -1;
        if (neutralFrom != null) {
            // the colored letters of the variant go: the original's neutral wall comes back
            int[] n = new int[1];
            t = t.withNeutralFrom(Template.load("buildings/" + neutralFrom), n);
            letters = n[0];
        }
        if (bannerSpec != null) {
            String[] a = bannerSpec.split("[@,:=]");
            t = t.withBannersFrom(Template.load("buildings/" + a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]),
                    a[3], a[4]);
        }
        return new Source(t, letters);
    }

    /**
     * {@link #of} for every row of the index ({@code type|name|ground|front|model|banners|neutral|seed|hard}), in
     * parallel; the results in the order of the rows. The work given to the other threads (this method's lambda) is
     * code of this class, not of {@link Structures}: a lambda written there would belong to that class and wait for
     * its initialization, which waits for the lambdas (it was so, and the loading hung).
     */
    static java.util.List<Prepared> all(java.util.List<String[]> rows, boolean weathering) {
        return rows.parallelStream().map(f -> of(f[1],
                f.length > 6 && !f[6].isBlank() ? f[6].trim() : null,
                f.length > 5 && !f[5].isBlank() ? f[5].trim() : null,
                f.length > 2 ? Integer.parseInt(f[2].trim()) : 0,
                // the overgrowth seed: the index's, else the hash of the file name
                f.length > 7 && !f[7].isBlank() ? Integer.parseInt(f[7].trim()) : f[1].hashCode(), weathering)).toList();
    }

    /** The walks of a structure: the access of its containers and, for a building, its floors, rooms and spots. */
    record Walks(Access.Result access, Rooms.Layout rooms) {
    }

    /**
     * {@link Access#of} for every structure and {@link Rooms#of} for every building, from their {@link Structures.Shape}
     * (no block state ids), in parallel; the results in the order of the shapes. Like {@link #all}, the work handed to
     * the other threads must not touch {@link Structures} (Access and Rooms read the block names through
     * {@link Template#blockOf}).
     */
    static java.util.List<Walks> walks(java.util.List<Structures.Shape> shapes, java.util.List<Boolean> building,
                                       java.util.List<Boolean> house, int[] grounds) {
        return java.util.stream.IntStream.range(0, shapes.size()).parallel().mapToObj(k -> {
            Structures.Shape s = shapes.get(k);
            int w = s.x1() - s.x0() + 1;
            int h = s.y1() - s.y0() + 1;
            int d = s.z1() - s.z0() + 1;
            Access.Result access = Access.of(s.t(), s.placed(), s.x0(), s.y0(), s.z0(), w, h, d, grounds[k], house.get(k));
            Rooms.Layout rooms = building.get(k)
                    ? Rooms.of(s.t(), s.placed(), s.x0(), s.y0(), s.z0(), w, h, d, grounds[k], s.openings()) : null;
            return new Walks(access, rooms);
        }).toList();
    }

    /** {@link #source}, then the overgrowth when {@code weathering} (its report, or null). */
    static Prepared of(String name, String neutralFrom, String bannerSpec, int ground, long seed, boolean weathering) {
        Source s = source(name, neutralFrom, bannerSpec);
        if (!weathering) {
            return new Prepared(s.template(), s.letters(), null);
        }
        Weathering.Result w = Weathering.apply(s.template(), ground, seed);
        return new Prepared(w.template(), s.letters(), w.report());
    }
}
