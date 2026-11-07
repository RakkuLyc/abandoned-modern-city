package com.abandonedmoderncity.core;

/**
 * Gentle waves applied to a city's street grid. The grid is straight in (u, v) "grid space"; in the world,
 * north-south streets follow {@code x = U + wx(z)} and east-west streets {@code z = V + wz(x)}. Since every north-south
 * street gets the same wave (and likewise east-west), all blocks keep the same size and the streets curve together.
 * <p>
 * Uses {@link StrictMath} so the geometry is bit-identical on every JVM.
 */
public final class Warp {
    public static final Warp NONE = new Warp(0, 1, 0, 0, 1, 0);

    private final double ax;
    private final double kx;
    private final double px;
    private final double az;
    private final double kz;
    private final double pz;

    /**
     * @param ax amplitude of the north-south streets' wave, {@code wx(z)}
     * @param lx its wavelength
     * @param px its phase
     */
    public Warp(double ax, double lx, double px, double az, double lz, double pz) {
        this.ax = ax;
        this.kx = 2 * Math.PI / lx;
        this.px = px;
        this.az = az;
        this.kz = 2 * Math.PI / lz;
        this.pz = pz;
    }

    /** x offset of the north-south streets at this z. */
    public double wx(double z) {
        return ax == 0 ? 0 : ax * StrictMath.sin(kx * z + px);
    }

    /** d wx / dz. */
    public double dwx(double z) {
        return ax == 0 ? 0 : ax * kx * StrictMath.cos(kx * z + px);
    }

    /** z offset of the east-west streets at this x. */
    public double wz(double x) {
        return az == 0 ? 0 : az * StrictMath.sin(kz * x + pz);
    }

    /** d wz / dx. */
    public double dwz(double x) {
        return az == 0 ? 0 : az * kz * StrictMath.cos(kz * x + pz);
    }

    public double u(double x, double z) {
        return x - wx(z);
    }

    public double v(double x, double z) {
        return z - wz(x);
    }

    /** World position of a grid-space point (fixed point iteration; the waves are gentle, so it converges fast). */
    public double[] toWorld(double u, double v) {
        double x = u;
        double z = v;
        for (int i = 0; i < 12; i++) {
            double nx = u + wx(z);
            double nz = v + wz(nx);
            if (Math.abs(nx - x) < 1e-9 && Math.abs(nz - z) < 1e-9) {
                x = nx;
                z = nz;
                break;
            }
            x = nx;
            z = nz;
        }
        return new double[]{x, z};
    }

    /** Largest slope of the waves (both are at most this steep). */
    public double maxSlope() {
        return Math.max(Math.abs(ax * kx), Math.abs(az * kz));
    }
}
