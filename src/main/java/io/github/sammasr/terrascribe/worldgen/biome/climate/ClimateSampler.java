package io.github.sammasr.terrascribe.worldgen.biome.climate;

import io.github.sammasr.terrascribe.worldgen.noise.NoiseField;

/**
 * Deterministic per-world {@link Climate} sampler: {@code (x, z) → (temperature, humidity)}.
 *
 * <p>Climate is the sum of two contributions:
 * <ul>
 *   <li>An independent {@link NoiseField} sample for temperature and humidity respectively.
 *       Seeds for the two are decorrelated via fixed Weyl-sequence strides so a single
 *       user-facing seed produces independent noise across the two channels.</li>
 *   <li>A latitude bias on temperature: {@code sin(z * latitudeFrequency) * latitudeStrength}.
 *       This produces wide climate bands roughly perpendicular to the Z axis (one full sine
 *       cycle every {@code 2π / latitudeFrequency} blocks). With the default frequency this
 *       gives bands of order tens of thousands of blocks across — large enough that a player
 *       experiences a coherent climate region, small enough that long-distance exploration
 *       reveals different climates.</li>
 * </ul>
 *
 * <p>Pure math, no Minecraft API references. Thread-safe (stateless aside from final fields).
 */
public final class ClimateSampler {

    private static final int TEMPERATURE_SEED_STRIDE = 0x517cc1b7;
    private static final int HUMIDITY_SEED_STRIDE = 0x6c8e9cf5;

    /** Defaults give one latitude cycle every ~63,000 blocks. */
    public static final float DEFAULT_LATITUDE_FREQUENCY = 1e-4f;
    /** Defaults give a latitude contribution of up to ±0.6 — enough to flip climate buckets. */
    public static final float DEFAULT_LATITUDE_STRENGTH = 0.6f;

    private final NoiseField temperatureNoise;
    private final NoiseField humidityNoise;
    private final int seed;
    private final float latitudeFrequency;
    private final float latitudeStrength;

    /** Constructs a sampler with default latitude parameters. */
    public ClimateSampler(final NoiseField temperatureNoise, final NoiseField humidityNoise, final int seed) {
        this(temperatureNoise, humidityNoise, seed, DEFAULT_LATITUDE_FREQUENCY, DEFAULT_LATITUDE_STRENGTH);
    }

    public ClimateSampler(
            final NoiseField temperatureNoise,
            final NoiseField humidityNoise,
            final int seed,
            final float latitudeFrequency,
            final float latitudeStrength) {
        if (temperatureNoise == null || humidityNoise == null) {
            throw new IllegalArgumentException("noise fields must not be null");
        }
        if (latitudeFrequency < 0f) {
            throw new IllegalArgumentException("latitudeFrequency must be >= 0, got " + latitudeFrequency);
        }
        if (latitudeStrength < 0f) {
            throw new IllegalArgumentException("latitudeStrength must be >= 0, got " + latitudeStrength);
        }
        this.temperatureNoise = temperatureNoise;
        this.humidityNoise = humidityNoise;
        this.seed = seed;
        this.latitudeFrequency = latitudeFrequency;
        this.latitudeStrength = latitudeStrength;
    }

    public Climate sample(final int x, final int z) {
        final int tempSeed = this.seed + TEMPERATURE_SEED_STRIDE;
        final int humidSeed = this.seed + HUMIDITY_SEED_STRIDE;
        final float tempNoise = this.temperatureNoise.sample(x, z, tempSeed);
        final float humidNoise = this.humidityNoise.sample(x, z, humidSeed);
        final float latitudeBias = (float) Math.sin(z * (double) this.latitudeFrequency) * this.latitudeStrength;
        return new Climate(
                clamp(tempNoise + latitudeBias, -1f, 1f),
                clamp(humidNoise, -1f, 1f));
    }

    private static float clamp(final float value, final float min, final float max) {
        return value < min ? min : (value > max ? max : value);
    }
}
