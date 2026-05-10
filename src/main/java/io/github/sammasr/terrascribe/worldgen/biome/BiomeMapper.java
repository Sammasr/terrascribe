package io.github.sammasr.terrascribe.worldgen.biome;

import io.github.sammasr.terrascribe.worldgen.biome.climate.Climate;

/**
 * Pure-math classifier: {@link Climate} → {@link ClimateBucket}.
 *
 * <p>This is the M2 decision matrix. It does not pick a specific biome — that step lives in
 * {@code TerraScribeBiomeSource}, which holds a per-bucket biome pool and a deterministic
 * hash-based picker. Keeping the math layer free of {@code ResourceKey<Biome>} lookups means
 * we can unit-test bucket classification without any Minecraft state.
 *
 * <p>Threshold constants are public so tests and the bucketing utility that classifies
 * discovered modded biomes can agree on the boundaries.
 */
public final class BiomeMapper {

    public static final float FROZEN_THRESHOLD = -0.5f;
    public static final float COLD_THRESHOLD = 0.0f;
    public static final float TEMPERATE_THRESHOLD = 0.5f;

    public static final float ARID_THRESHOLD = -0.33f;
    public static final float MODERATE_THRESHOLD = 0.33f;

    public ClimateBucket bucketFor(final Climate climate) {
        return new ClimateBucket(coolnessOf(climate.temperature()), wetnessOf(climate.humidity()));
    }

    public static ClimateBucket.Coolness coolnessOf(final float temperature) {
        if (temperature < FROZEN_THRESHOLD) {
            return ClimateBucket.Coolness.FROZEN;
        }
        if (temperature < COLD_THRESHOLD) {
            return ClimateBucket.Coolness.COLD;
        }
        if (temperature < TEMPERATE_THRESHOLD) {
            return ClimateBucket.Coolness.TEMPERATE;
        }
        return ClimateBucket.Coolness.HOT;
    }

    public static ClimateBucket.Wetness wetnessOf(final float humidity) {
        if (humidity < ARID_THRESHOLD) {
            return ClimateBucket.Wetness.ARID;
        }
        if (humidity < MODERATE_THRESHOLD) {
            return ClimateBucket.Wetness.MODERATE;
        }
        return ClimateBucket.Wetness.WET;
    }
}
