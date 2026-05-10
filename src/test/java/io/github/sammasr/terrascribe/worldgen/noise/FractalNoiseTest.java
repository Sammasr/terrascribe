package io.github.sammasr.terrascribe.worldgen.noise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FractalNoiseTest {

    private final SimplexNoise simplex = new SimplexNoise();

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new FractalNoise(null, 4, 2f, 0.5f, 0.01f));
        assertThrows(IllegalArgumentException.class, () -> new FractalNoise(simplex, 0, 2f, 0.5f, 0.01f));
        assertThrows(IllegalArgumentException.class, () -> new FractalNoise(simplex, -1, 2f, 0.5f, 0.01f));
        assertThrows(IllegalArgumentException.class, () -> new FractalNoise(simplex, 4, 0f, 0.5f, 0.01f));
        assertThrows(IllegalArgumentException.class, () -> new FractalNoise(simplex, 4, 2f, 0f, 0.01f));
        assertThrows(IllegalArgumentException.class, () -> new FractalNoise(simplex, 4, 2f, 0.5f, 0f));
    }

    @Test
    void deterministicForSameInputs() {
        FractalNoise fbm = new FractalNoise(simplex, 4, 2f, 0.5f, 0.01f);
        float a = fbm.sample(1234.5f, -789.1f, 42);
        float b = fbm.sample(1234.5f, -789.1f, 42);
        assertEquals(a, b, 0f);
    }

    @Test
    void outputStaysApproximatelyInRange() {
        FractalNoise fbm = new FractalNoise(simplex, 6, 2f, 0.5f, 0.01f);
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (int i = -50; i < 50; i++) {
            for (int j = -50; j < 50; j++) {
                float v = fbm.sample(i * 7f, j * 11f, 2026);
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        // Normalization should keep fBm output within roughly the same envelope as base.
        assertTrue(min >= -1.1f, "min was " + min);
        assertTrue(max <= 1.1f, "max was " + max);
    }

    @Test
    void singleOctaveMatchesBaseAtScaledCoordinate() {
        // With octaves=1 and totalAmplitude=1, fBm should pass through base(x*freq, z*freq, seed) unchanged.
        float frequency = 0.05f;
        FractalNoise oneOctave = new FractalNoise(simplex, 1, 2f, 0.5f, frequency);
        for (int seed : new int[] {0, 7, -3}) {
            for (float x : new float[] {0f, 1.5f, -42.7f, 1000f}) {
                float fbm = oneOctave.sample(x, 0f, seed);
                float base = simplex.sample(x * frequency, 0f, seed);
                assertEquals(base, fbm, 1e-6f, "1-octave fBm must match base for x=" + x + " seed=" + seed);
            }
        }
    }

    @Test
    void multipleOctavesProduceDifferentOutputThanSingle() {
        // Higher octaves mix in additional layers; output at the same point should differ from
        // the one-octave version, proving the extra layers are actually being summed in.
        FractalNoise oneOctave = new FractalNoise(simplex, 1, 2f, 0.5f, 0.05f);
        FractalNoise sixOctaves = new FractalNoise(simplex, 6, 2f, 0.5f, 0.05f);

        int matches = 0;
        int samples = 50;
        for (int i = 0; i < samples; i++) {
            float a = oneOctave.sample(i * 3.7f, i * 1.9f, 42);
            float b = sixOctaves.sample(i * 3.7f, i * 1.9f, 42);
            if (Math.abs(a - b) < 1e-4f) {
                matches++;
            }
        }
        assertTrue(matches < samples / 2,
                "added octaves had no measurable effect at most sample points: matched at " + matches + "/" + samples);
    }
}
