package io.github.sammasr.terrascribe.worldgen.terrain;

import io.github.sammasr.terrascribe.worldgen.noise.NoiseField;

/**
 * Heightmap implementation that derives height from a single {@link NoiseField} sample.
 *
 * <p>This is the Milestone 1 placeholder — rolling terrain only, no terrain types or biome
 * blending. Heights are computed as:
 *
 * <pre>{@code
 *   height = baseHeight + round(amplitude * noise.sample(x, z, seed))
 * }</pre>
 *
 * <p>The {@link NoiseField} is typically pre-configured with its own frequency (e.g., a
 * {@code FractalNoise} with starting frequency ~0.005 producing ~200-block features); this
 * class does not re-scale the inputs.
 *
 * <p>Pure math, deterministic, thread-safe.
 */
public final class BasicHeightmapGenerator implements Heightmap {

    private final NoiseField noise;
    private final int seed;
    private final int baseHeight;
    private final float amplitude;

    /**
     * @param noise      pre-configured noise field; sampled raw at world {@code (x, z)}
     * @param seed       world seed passed to {@code noise.sample}; bakes this generator to a world
     * @param baseHeight median surface height; for the MC 1.21.1 overworld a value around
     *                   {@code 70} keeps most of the world above sea level (63)
     * @param amplitude  half-range of the noise contribution; total height range is
     *                   {@code [baseHeight - amplitude, baseHeight + amplitude]} assuming the
     *                   noise field stays within {@code [-1, 1]}
     */
    public BasicHeightmapGenerator(
            final NoiseField noise,
            final int seed,
            final int baseHeight,
            final float amplitude) {
        if (noise == null) {
            throw new IllegalArgumentException("noise field must not be null");
        }
        if (amplitude < 0f) {
            throw new IllegalArgumentException("amplitude must be >= 0, got " + amplitude);
        }
        this.noise = noise;
        this.seed = seed;
        this.baseHeight = baseHeight;
        this.amplitude = amplitude;
    }

    @Override
    public int heightAt(final int x, final int z) {
        final float n = noise.sample(x, z, seed);
        return baseHeight + Math.round(n * amplitude);
    }
}
