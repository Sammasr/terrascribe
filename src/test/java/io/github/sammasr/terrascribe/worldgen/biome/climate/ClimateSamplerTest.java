package io.github.sammasr.terrascribe.worldgen.biome.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sammasr.terrascribe.worldgen.noise.FractalNoise;
import io.github.sammasr.terrascribe.worldgen.noise.NoiseField;
import io.github.sammasr.terrascribe.worldgen.noise.SimplexNoise;
import org.junit.jupiter.api.Test;

class ClimateSamplerTest {

    private static ClimateSampler sampler(int seed) {
        NoiseField temp = new FractalNoise(new SimplexNoise(), 4, 2f, 0.5f, 0.001f);
        NoiseField humid = new FractalNoise(new SimplexNoise(), 4, 2f, 0.5f, 0.001f);
        return new ClimateSampler(temp, humid, seed);
    }

    @Test
    void rejectsNullNoiseFields() {
        NoiseField anyNoise = new SimplexNoise();
        assertThrows(IllegalArgumentException.class, () -> new ClimateSampler(null, anyNoise, 0));
        assertThrows(IllegalArgumentException.class, () -> new ClimateSampler(anyNoise, null, 0));
    }

    @Test
    void rejectsNegativeLatitudeParameters() {
        NoiseField anyNoise = new SimplexNoise();
        assertThrows(IllegalArgumentException.class,
                () -> new ClimateSampler(anyNoise, anyNoise, 0, -1f, 0.5f));
        assertThrows(IllegalArgumentException.class,
                () -> new ClimateSampler(anyNoise, anyNoise, 0, 0.001f, -0.1f));
    }

    @Test
    void deterministicForSameInputs() {
        ClimateSampler a = sampler(42);
        ClimateSampler b = sampler(42);
        for (int z = -1000; z <= 1000; z += 100) {
            Climate ca = a.sample(0, z);
            Climate cb = b.sample(0, z);
            assertEquals(ca, cb, "same seed must produce same climate at z=" + z);
        }
    }

    @Test
    void climateStaysInRange() {
        ClimateSampler s = sampler(7);
        for (int x = -1000; x < 1000; x += 50) {
            for (int z = -1000; z < 1000; z += 50) {
                Climate c = s.sample(x, z);
                assertTrue(c.temperature() >= -1f && c.temperature() <= 1f,
                        "temperature out of range at (" + x + "," + z + "): " + c.temperature());
                assertTrue(c.humidity() >= -1f && c.humidity() <= 1f,
                        "humidity out of range at (" + x + "," + z + "): " + c.humidity());
            }
        }
    }

    @Test
    void latitudeBiasMakesFarZColderOrHotterThanCenter() {
        // sin(z * 1e-4) reaches its first max at z = π / (2 * 1e-4) ≈ 15708
        // and its first min at z = -π / (2 * 1e-4) ≈ -15708.
        // So z=+15708 should bias positive (hot), z=-15708 should bias negative (cold).
        // Sample many x values to average out the underlying noise.
        ClimateSampler s = sampler(2026);
        double avgHotBand = 0;
        double avgColdBand = 0;
        int samples = 100;
        for (int i = 0; i < samples; i++) {
            int x = i * 137;
            avgHotBand += s.sample(x, 15708).temperature();
            avgColdBand += s.sample(x, -15708).temperature();
        }
        avgHotBand /= samples;
        avgColdBand /= samples;
        assertTrue(avgHotBand > avgColdBand + 0.5,
                "hot latitude band should average warmer than cold band by at least 0.5: hot="
                        + avgHotBand + " cold=" + avgColdBand);
    }

    @Test
    void zeroLatitudeStrengthDisablesBias() {
        NoiseField temp = new FractalNoise(new SimplexNoise(), 4, 2f, 0.5f, 0.001f);
        NoiseField humid = new FractalNoise(new SimplexNoise(), 4, 2f, 0.5f, 0.001f);
        ClimateSampler s = new ClimateSampler(temp, humid, 0, 1e-4f, 0f);

        // Without latitude bias, sampling at z = +π/(2f) and z = -π/(2f) should give
        // similar averages — only the noise contributes.
        double avgPos = 0;
        double avgNeg = 0;
        int samples = 200;
        for (int i = 0; i < samples; i++) {
            int x = i * 211;
            avgPos += s.sample(x, 15708).temperature();
            avgNeg += s.sample(x, -15708).temperature();
        }
        avgPos /= samples;
        avgNeg /= samples;
        assertTrue(Math.abs(avgPos - avgNeg) < 0.2,
                "with strength=0 the two bands should average close: pos=" + avgPos + " neg=" + avgNeg);
    }

    @Test
    void temperatureAndHumidityChannelsAreDecorrelated() {
        // Sampling at many points, the (temp, humidity) pairs should not be near-identical.
        ClimateSampler s = sampler(42);
        int matches = 0;
        int samples = 200;
        for (int i = 0; i < samples; i++) {
            int x = i * 53;
            int z = i * 79;
            Climate c = s.sample(x, z);
            if (Math.abs(c.temperature() - c.humidity()) < 0.05f) {
                matches++;
            }
        }
        assertTrue(matches < samples / 2,
                "temp and humidity channels look correlated — matched at " + matches + "/" + samples);
    }
}
