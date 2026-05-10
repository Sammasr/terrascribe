package io.github.sammasr.terrascribe.worldgen.noise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimplexNoiseTest {

    private final SimplexNoise noise = new SimplexNoise();

    @Test
    void sameInputProducesSameOutput() {
        for (int seed : new int[] {0, 1, 42, -17, Integer.MAX_VALUE}) {
            float a = noise.sample(12.34f, 56.78f, seed);
            float b = noise.sample(12.34f, 56.78f, seed);
            assertEquals(a, b, 0f, "noise must be deterministic for seed " + seed);
        }
    }

    @Test
    void differentSeedsGenerallyDiffer() {
        // At a single point, two different seeds *can* coincidentally match, so sample a few
        // points and require at least one to differ — this protects against a buggy seed wiring.
        int matches = 0;
        for (int i = 0; i < 20; i++) {
            float a = noise.sample(i * 0.7f, i * 1.3f, 1);
            float b = noise.sample(i * 0.7f, i * 1.3f, 2);
            if (a == b) {
                matches++;
            }
        }
        assertTrue(matches < 20, "different seeds collided on every sample — seed wiring is broken");
    }

    @Test
    void outputStaysApproximatelyInRange() {
        // Theoretical peak of 2D simplex is slightly outside [-1, 1]; allow a small overshoot.
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (int i = -100; i < 100; i++) {
            for (int j = -100; j < 100; j++) {
                float v = noise.sample(i * 0.13f, j * 0.17f, 1234);
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        assertTrue(min >= -1.1f, "min was " + min);
        assertTrue(max <= 1.1f, "max was " + max);
        // Sanity: there's actual variation.
        assertTrue(max - min > 0.5f, "output looks constant: range " + (max - min));
    }

    @Test
    void zeroDistanceProducesContinuousField() {
        // Nearby points should produce similar values (no hash-style discontinuities).
        float epsilon = 0.001f;
        float a = noise.sample(5f, 5f, 99);
        float b = noise.sample(5f + epsilon, 5f, 99);
        assertEquals(a, b, 0.01f, "noise should be continuous (small step != large jump)");
    }

    @Test
    void differentCoordinatesGenerallyDiffer() {
        float a = noise.sample(0f, 0f, 7);
        float b = noise.sample(100f, 100f, 7);
        assertNotEquals(a, b, "noise at distant points should not match");
    }
}
