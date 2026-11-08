package com.abandonedmoderncity.core;

/**
 * Synthetic terrains for the planner tests (value noise fBm). Water where the surface falls below the sea.
 */
final class TestTerrain implements Terrain {
    enum Kind { FLAT, HILLS, MOUNTAINS, ISLANDS, MIXED, CANYONS, RIVERS }

    static final int SEA = 63;
    private final long seed;
    final Kind kind;

    TestTerrain(long seed, Kind kind) {
        this.seed = seed;
        this.kind = kind;
    }

    @Override
    public int seaLevel() {
        return SEA;
    }

    private double lattice(long ix, long iz, int octave) {
        return Hash.unit(Hash.of(seed, 0x7E44A1L + octave, ix, iz)) * 2 - 1;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    double noise(double x, double z, int octave) {
        long ix = (long) Math.floor(x);
        long iz = (long) Math.floor(z);
        double fx = smooth(x - ix);
        double fz = smooth(z - iz);
        double a = lattice(ix, iz, octave);
        double b = lattice(ix + 1, iz, octave);
        double c = lattice(ix, iz + 1, octave);
        double d = lattice(ix + 1, iz + 1, octave);
        double ab = a + (b - a) * fx;
        double cd = c + (d - c) * fx;
        return ab + (cd - ab) * fz;
    }

    double fbm(double x, double z, int octaves, int base) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        for (int o = 0; o < octaves; o++) {
            sum += amp * noise(x, z, base + o);
            norm += amp;
            x *= 2.03;
            z *= 2.03;
            amp *= 0.5;
        }
        return sum / norm;
    }

    double height(int x, int z) {
        return switch (kind) {
            case FLAT -> 70;
            case HILLS -> 74 + 16 * fbm(x / 320.0, z / 320.0, 4, 0);
            case MOUNTAINS -> {
                double m = Math.max(0, fbm(x / 700.0, z / 700.0, 3, 10) + 0.15);
                yield 72 + 150 * m * m + 12 * fbm(x / 120.0, z / 120.0, 3, 20);
            }
            case ISLANDS -> 64 + 30 * fbm(x / 900.0, z / 900.0, 4, 30) + 4;
            case MIXED -> {
                double base = 66 + 28 * fbm(x / 1100.0, z / 1100.0, 3, 40);
                double ridge = 1 - Math.abs(fbm(x / 500.0, z / 500.0, 3, 50));
                yield base + 70 * Math.pow(Math.max(0, ridge - 0.55), 1.4) * 2.2 + 6 * fbm(x / 80.0, z / 80.0, 2, 60);
            }
            case CANYONS -> {
                double c = Math.abs(fbm(x / 400.0, z / 400.0, 2, 70));
                double plateau = 90 + 8 * fbm(x / 200.0, z / 200.0, 2, 80);
                yield c < 0.06 ? 58 + c * 200 : plateau;
            }
            case RIVERS -> {
                // like the real overworld around the self-test seeds: rolling land cut by rivers, dotted with lakes
                double land = 70 + 9 * fbm(x / 450.0, z / 450.0, 3, 90);
                double river = Math.abs(fbm(x / 650.0, z / 650.0, 3, 91));
                double lake = fbm(x / 240.0, z / 240.0, 2, 92);
                yield river < 0.035 || lake > 0.42 ? 57 : land;
            }
        };
    }

    @Override
    public int ground(int x, int z) {
        return (int) Math.floor(height(x, z));
    }

    @Override
    public boolean water(int x, int z) {
        return height(x, z) < SEA;
    }
}
