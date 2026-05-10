package io.github.sammasr.terrascribe.worldgen.biome;

/**
 * Discrete climate categorization used to group biomes into compatible "buckets" and pick a
 * biome for each {@code (x, z)} position.
 *
 * <p>At Milestone 2 the bucket is 2D — {@code (Coolness, Wetness)} — giving 12 nominal
 * buckets. An {@code Elevation} axis is intentionally absent: {@code BiomeSource.getNoiseBiome}
 * receives quart-scaled {@code (x, y, z)} but does not carry the *generated* surface height of
 * the column, and threading height through every biome lookup at M2 doesn't pay for itself.
 * Elevation-driven biome selection (so oceans get ocean biomes, mountain peaks get stone-peaks
 * biomes) lands at M3 when erosion needs a shared heightmap anyway.
 *
 * <p>Pure-math value object, no Minecraft API references.
 */
public record ClimateBucket(Coolness coolness, Wetness wetness) {

    public ClimateBucket {
        if (coolness == null || wetness == null) {
            throw new IllegalArgumentException("all bucket fields must be non-null");
        }
    }

    public enum Coolness {
        FROZEN,    // temperature < -0.5
        COLD,      // temperature in [-0.5, 0)
        TEMPERATE, // temperature in [0, 0.5)
        HOT        // temperature >= 0.5
    }

    public enum Wetness {
        ARID,     // humidity < -0.33
        MODERATE, // humidity in [-0.33, 0.33)
        WET       // humidity >= 0.33
    }
}
