package io.github.sammasr.terrascribe.worldgen.noise;

/**
 * Deterministic 2D noise function. Pure math — no Minecraft API references.
 *
 * <p>Implementations return values that, sampled across a representative input space, fall
 * within {@link #minValue()} and {@link #maxValue()} inclusive. The default range
 * {@code [-1, 1]} matches both simplex and Perlin noise conventions.
 *
 * <p>Same {@code (x, z, seed)} triple must always produce the same output. This determinism
 * is load-bearing for worldgen — chunks regenerated from the same seed must be identical.
 */
@FunctionalInterface
public interface NoiseField {

    /**
     * Samples the noise field at world coordinate {@code (x, z)} for the given seed.
     *
     * @param x world x in blocks (not pre-scaled)
     * @param z world z in blocks (not pre-scaled)
     * @param seed integer seed; same seed must produce identical output across JVM runs
     * @return noise value, typically in {@code [minValue(), maxValue()]}
     */
    float sample(float x, float z, int seed);

    /** Lower bound of {@link #sample} output. Default {@code -1}. */
    default float minValue() {
        return -1f;
    }

    /** Upper bound of {@link #sample} output. Default {@code 1}. */
    default float maxValue() {
        return 1f;
    }
}
