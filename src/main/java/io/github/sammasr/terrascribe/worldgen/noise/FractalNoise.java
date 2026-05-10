package io.github.sammasr.terrascribe.worldgen.noise;

/**
 * Multi-octave fractional Brownian motion (fBm) wrapper around a base {@link NoiseField}.
 *
 * <p>Sums {@code octaves} samples of the base field at increasing frequencies and
 * decreasing amplitudes, producing the familiar "fractal" terrain look. Output is
 * normalized so that, with a base field in {@code [-1, 1]}, this field also stays in
 * {@code [-1, 1]}.
 *
 * <p>Parameters, with sensible terrain-generation ranges:
 * <ul>
 *   <li>{@code octaves} (1–8) — number of layers. More octaves = more detail = more cost.</li>
 *   <li>{@code lacunarity} (~2.0) — frequency multiplier per octave. 2.0 doubles frequency each step.</li>
 *   <li>{@code gain} (~0.5) — amplitude multiplier per octave. 0.5 halves contribution each step.</li>
 *   <li>{@code frequency} — starting frequency. Larger values produce smaller features.</li>
 * </ul>
 *
 * <p>Each octave receives a seed perturbed by a Weyl-sequence constant, so a single user-facing
 * seed produces decorrelated noise across octaves without the caller having to manage them.
 */
public final class FractalNoise implements NoiseField {

    /** Golden-ratio Weyl-sequence constant used to perturb the seed per octave. */
    private static final int OCTAVE_SEED_STRIDE = 0x9e3779b9;

    private final NoiseField base;
    private final int octaves;
    private final float lacunarity;
    private final float gain;
    private final float frequency;

    public FractalNoise(
            final NoiseField base,
            final int octaves,
            final float lacunarity,
            final float gain,
            final float frequency) {
        if (base == null) {
            throw new IllegalArgumentException("base noise field must not be null");
        }
        if (octaves < 1) {
            throw new IllegalArgumentException("octaves must be >= 1, got " + octaves);
        }
        if (lacunarity <= 0f || gain <= 0f || frequency <= 0f) {
            throw new IllegalArgumentException(
                    "lacunarity, gain, frequency must all be > 0; got "
                            + lacunarity + ", " + gain + ", " + frequency);
        }
        this.base = base;
        this.octaves = octaves;
        this.lacunarity = lacunarity;
        this.gain = gain;
        this.frequency = frequency;
    }

    @Override
    public float sample(final float x, final float z, final int seed) {
        float sum = 0f;
        float amplitude = 1f;
        float totalAmplitude = 0f;
        float freq = frequency;
        int octaveSeed = seed;

        for (int o = 0; o < octaves; o++) {
            sum += amplitude * base.sample(x * freq, z * freq, octaveSeed);
            totalAmplitude += amplitude;
            amplitude *= gain;
            freq *= lacunarity;
            octaveSeed += OCTAVE_SEED_STRIDE;
        }
        return sum / totalAmplitude;
    }
}
