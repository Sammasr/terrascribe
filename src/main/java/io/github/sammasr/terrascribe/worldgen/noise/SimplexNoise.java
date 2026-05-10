package io.github.sammasr.terrascribe.worldgen.noise;

/**
 * 2D simplex noise (Stefan Gustavson's algorithm, vendored implementation).
 *
 * <p>Returns values in approximately {@code [-1, 1]} — the algorithm's theoretical peak is
 * slightly outside that range, so callers that depend on a strict bound should clamp.
 *
 * <p>Stateless and thread-safe. Seed is passed per-sample so a single instance can serve
 * multiple seeded contexts (e.g., one instance per noise *kind*, called with different
 * seeds for temperature, humidity, terrain height, etc.).
 */
public final class SimplexNoise implements NoiseField {

    // Standard 2D simplex skew / unskew constants. F2 = (sqrt(3) - 1) / 2; G2 = (3 - sqrt(3)) / 6.
    private static final float F2 = 0.366025403784438646f;
    private static final float G2 = 0.211324865405187118f;

    @Override
    public float sample(final float x, final float z, final int seed) {
        // Skew input coords into simplex (skewed-grid) space.
        final float s = (x + z) * F2;
        final int i = floor(x + s);
        final int j = floor(z + s);

        // Unskew the cell origin back to (x, z) space.
        final float t = (i + j) * G2;
        final float x0 = x - (i - t);
        final float z0 = z - (j - t);

        // Determine which of the two triangles in the unit cell contains our point.
        final int i1;
        final int j1;
        if (x0 > z0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }

        // Offsets for the other two simplex corners in (x, z) space.
        final float x1 = x0 - i1 + G2;
        final float z1 = z0 - j1 + G2;
        final float x2 = x0 - 1f + 2f * G2;
        final float z2 = z0 - 1f + 2f * G2;

        // Sum the three corner contributions and scale to roughly [-1, 1].
        // The 70.0 multiplier is the classical normalization for 2D simplex.
        final float n0 = corner(x0, z0, i, j, seed);
        final float n1 = corner(x1, z1, i + i1, j + j1, seed);
        final float n2 = corner(x2, z2, i + 1, j + 1, seed);
        return 70f * (n0 + n1 + n2);
    }

    private static float corner(final float x, final float z, final int gx, final int gz, final int seed) {
        float t = 0.5f - x * x - z * z;
        if (t < 0f) {
            return 0f;
        }
        t *= t;
        return t * t * grad(hash(gx, gz, seed), x, z);
    }

    /** 8 evenly-spaced gradient directions on the unit circle, selected by hash. */
    private static float grad(final int h, final float x, final float z) {
        switch (h & 7) {
            case 0:  return  x + z;
            case 1:  return -x + z;
            case 2:  return  x - z;
            case 3:  return -x - z;
            case 4:  return  x;
            case 5:  return -x;
            case 6:  return  z;
            default: return -z;
        }
    }

    /** Cheap 2D integer hash with seed perturbation (xor-shift + multiply-rotate). */
    private static int hash(final int x, final int z, final int seed) {
        int h = seed * 0x27d4eb2d ^ x * 1597334677 ^ z * (int) 2654435761L;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    /** {@code Math.floor} for floats that returns an int without boxing or autoboxing. */
    private static int floor(final float v) {
        final int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
