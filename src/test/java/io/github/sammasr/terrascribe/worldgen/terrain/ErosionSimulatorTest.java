package io.github.sammasr.terrascribe.worldgen.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sammasr.terrascribe.worldgen.noise.FractalNoise;
import io.github.sammasr.terrascribe.worldgen.noise.SimplexNoise;
import org.junit.jupiter.api.Test;

class ErosionSimulatorTest {

    /** Builds a fresh noise-based heightmap to exercise erosion. */
    private static float[] noiseHeightmap(final int width, final int seed) {
        final FractalNoise noise = new FractalNoise(new SimplexNoise(), 4, 2f, 0.5f, 0.02f);
        final float[] map = new float[width * width];
        for (int z = 0; z < width; z++) {
            for (int x = 0; x < width; x++) {
                map[z * width + x] = 50f + 20f * noise.sample(x, z, seed);
            }
        }
        return map;
    }

    @Test
    void rejectsBadArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> ErosionSimulator.simulate(null, 64, 0L, ErosionSimulator.Params.defaults()));
        assertThrows(IllegalArgumentException.class,
                () -> ErosionSimulator.simulate(new float[100], 10, 0L, null));
        assertThrows(IllegalArgumentException.class,
                () -> ErosionSimulator.simulate(new float[4], 2, 0L, ErosionSimulator.Params.defaults()));
        assertThrows(IllegalArgumentException.class,
                () -> ErosionSimulator.simulate(new float[99], 10, 0L, ErosionSimulator.Params.defaults()));
    }

    @Test
    void paramsRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new ErosionSimulator.Params(-1, 30, 0.05f, 4f, 0.01f, 0.3f, 0.3f, 0.01f, 1f, 1f, 4f));
        assertThrows(IllegalArgumentException.class,
                () -> new ErosionSimulator.Params(1000, 30, 1.5f, 4f, 0.01f, 0.3f, 0.3f, 0.01f, 1f, 1f, 4f));
        assertThrows(IllegalArgumentException.class,
                () -> new ErosionSimulator.Params(1000, 30, 0.05f, 0f, 0.01f, 0.3f, 0.3f, 0.01f, 1f, 1f, 4f));
    }

    @Test
    void deterministicForSameSeed() {
        final int width = 64;
        final float[] a = noiseHeightmap(width, 42);
        final float[] b = noiseHeightmap(width, 42);
        ErosionSimulator.simulate(a, width, 12345L, ErosionSimulator.Params.defaults());
        ErosionSimulator.simulate(b, width, 12345L, ErosionSimulator.Params.defaults());
        assertEquals(a.length, b.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i], 0f, "erosion must be deterministic; differ at i=" + i);
        }
    }

    @Test
    void modifiesHeightmap() {
        final int width = 64;
        final float[] before = noiseHeightmap(width, 7);
        final float[] after = before.clone();
        ErosionSimulator.simulate(after, width, 999L,
                new ErosionSimulator.Params(2000, 30, 0.05f, 4f, 0.01f, 0.3f, 0.3f, 0.01f, 1f, 1f, 4f));

        int changed = 0;
        for (int i = 0; i < before.length; i++) {
            if (before[i] != after[i]) {
                changed++;
            }
        }
        assertTrue(changed > 0, "erosion did not modify any cells");
        // Sanity: the change should affect a meaningful fraction of the map.
        assertTrue(changed > before.length / 20,
                "erosion changed only " + changed + " of " + before.length + " cells — likely broken");
    }

    @Test
    void preservesEnvelopeWithinReasonableBounds() {
        // After erosion, the height range shouldn't explode — sediment redistribution conserves
        // mass approximately (deposition matches erosion across a closed map). Allow some
        // overshoot for boundary effects.
        final int width = 96;
        final float[] map = noiseHeightmap(width, 3);
        float beforeMin = Float.POSITIVE_INFINITY;
        float beforeMax = Float.NEGATIVE_INFINITY;
        for (final float v : map) {
            if (v < beforeMin) beforeMin = v;
            if (v > beforeMax) beforeMax = v;
        }

        ErosionSimulator.simulate(map, width, 1L, ErosionSimulator.Params.defaults());

        float afterMin = Float.POSITIVE_INFINITY;
        float afterMax = Float.NEGATIVE_INFINITY;
        for (final float v : map) {
            if (v < afterMin) afterMin = v;
            if (v > afterMax) afterMax = v;
        }
        // Erosion can carry sediment quite a way; we just want to ensure it's not unbounded.
        // Expanded envelope of ±50 is a generous bound — actual changes should be much smaller.
        assertTrue(afterMin > beforeMin - 50f,
                "post-erosion min " + afterMin + " too far below pre-erosion min " + beforeMin);
        assertTrue(afterMax < beforeMax + 50f,
                "post-erosion max " + afterMax + " too far above pre-erosion max " + beforeMax);
    }

    @Test
    void differentSeedsProduceDifferentResults() {
        // Same input, different erosion seeds — should produce visibly different output
        // (droplets land in different places).
        final int width = 64;
        final float[] a = noiseHeightmap(width, 11);
        final float[] b = noiseHeightmap(width, 11);
        ErosionSimulator.simulate(a, width, 100L, ErosionSimulator.Params.defaults());
        ErosionSimulator.simulate(b, width, 200L, ErosionSimulator.Params.defaults());

        int differing = 0;
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > 0.001f) {
                differing++;
            }
        }
        assertNotEquals(0, differing, "different erosion seeds produced identical results");
    }

    @Test
    void zeroDropletsIsNoOp() {
        final int width = 32;
        final float[] before = noiseHeightmap(width, 99);
        final float[] after = before.clone();
        ErosionSimulator.simulate(after, width, 0L,
                new ErosionSimulator.Params(0, 30, 0.05f, 4f, 0.01f, 0.3f, 0.3f, 0.01f, 1f, 1f, 4f));
        for (int i = 0; i < before.length; i++) {
            assertEquals(before[i], after[i], 0f, "zero-droplet erosion should leave heightmap untouched");
        }
    }
}
