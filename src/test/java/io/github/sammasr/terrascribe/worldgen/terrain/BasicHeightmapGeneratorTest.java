package io.github.sammasr.terrascribe.worldgen.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sammasr.terrascribe.worldgen.noise.FractalNoise;
import io.github.sammasr.terrascribe.worldgen.noise.SimplexNoise;
import org.junit.jupiter.api.Test;

class BasicHeightmapGeneratorTest {

    private Heightmap heightmap(int seed) {
        // Representative M1 config: rolling terrain centered at y=70 with ±30 of variation.
        FractalNoise fbm = new FractalNoise(new SimplexNoise(), 4, 2f, 0.5f, 0.005f);
        return new BasicHeightmapGenerator(fbm, seed, 70, 30f);
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new BasicHeightmapGenerator(null, 0, 70, 30f));
        assertThrows(IllegalArgumentException.class,
                () -> new BasicHeightmapGenerator(new SimplexNoise(), 0, 70, -1f));
    }

    @Test
    void deterministicForSameInputs() {
        Heightmap a = heightmap(2026);
        Heightmap b = heightmap(2026);
        for (int x : new int[] {-1000, -1, 0, 1, 100, 1000}) {
            for (int z : new int[] {-1000, -1, 0, 1, 100, 1000}) {
                assertEquals(a.heightAt(x, z), b.heightAt(x, z),
                        "same seed must yield same height at (" + x + "," + z + ")");
            }
        }
    }

    @Test
    void differentSeedsProduceDifferentHeightmaps() {
        Heightmap a = heightmap(1);
        Heightmap b = heightmap(2);
        int matches = 0;
        int samples = 100;
        for (int i = 0; i < samples; i++) {
            int x = i * 37;
            int z = i * 53;
            if (a.heightAt(x, z) == b.heightAt(x, z)) {
                matches++;
            }
        }
        assertTrue(matches < samples / 2, "different seeds collided too often: " + matches + "/" + samples);
    }

    @Test
    void heightStaysWithinExpectedEnvelope() {
        // Base 70, amplitude 30, base noise capped at ~[-1, 1] → expect heights roughly in [40, 100].
        // Allow ±5 slop for fBm/simplex slight overshoot.
        Heightmap h = heightmap(42);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int x = -500; x < 500; x += 7) {
            for (int z = -500; z < 500; z += 7) {
                int y = h.heightAt(x, z);
                if (y < min) min = y;
                if (y > max) max = y;
            }
        }
        assertTrue(min >= 35, "min height " + min + " below expected envelope (~40)");
        assertTrue(max <= 105, "max height " + max + " above expected envelope (~100)");
        assertTrue(max - min > 20, "heightmap looks too flat: range " + (max - min));
    }

    @Test
    void neighbourColumnsAreNotIdentical() {
        // Smoke check that scanning along x produces variation (not a constant function).
        Heightmap h = heightmap(7);
        boolean sawVariation = false;
        int previous = h.heightAt(0, 0);
        for (int x = 1; x < 200; x++) {
            int curr = h.heightAt(x, 0);
            if (curr != previous) {
                sawVariation = true;
                break;
            }
            previous = curr;
        }
        assertTrue(sawVariation, "heightmap shows zero variation along x — likely broken");
    }

    @Test
    void zeroAmplitudeReturnsFlatBaseHeight() {
        // Edge case: amplitude=0 collapses to baseHeight regardless of noise.
        FractalNoise fbm = new FractalNoise(new SimplexNoise(), 4, 2f, 0.5f, 0.005f);
        Heightmap flat = new BasicHeightmapGenerator(fbm, 0, 70, 0f);
        assertEquals(70, flat.heightAt(0, 0));
        assertEquals(70, flat.heightAt(12345, -67890));
        assertEquals(70, flat.heightAt(99, 99));
    }
}
