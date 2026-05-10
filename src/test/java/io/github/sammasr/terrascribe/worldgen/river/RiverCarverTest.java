package io.github.sammasr.terrascribe.worldgen.river;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RiverCarverTest {

    @Test
    void rejectsBadArguments() {
        final float[] heights = new float[64];
        final FlowField flow = FlowField.compute(heights, 8);
        assertThrows(IllegalArgumentException.class,
                () -> RiverCarver.computeWetMask(null, flow, 63, 10, 200));
        assertThrows(IllegalArgumentException.class,
                () -> RiverCarver.computeWetMask(heights, null, 63, 10, 200));
        assertThrows(IllegalArgumentException.class,
                () -> RiverCarver.computeWetMask(heights, flow, 63, 0, 200));
        assertThrows(IllegalArgumentException.class,
                () -> RiverCarver.computeWetMask(new float[100], flow, 63, 10, 200));
        assertThrows(IllegalArgumentException.class,
                () -> RiverCarver.computeWetMask(heights, flow, 63, 10, 40));
    }

    @Test
    void marksHighFlowCellsAsWet() {
        // Ramp heightmap (decreases west to east). Easternmost column accumulates entire
        // row of upstream rain → high flow. Should be marked wet (above sea level).
        final int size = 6;
        final float[] heights = new float[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                // Heights well above sea level (63).
                heights[z * size + x] = 100f - x;
            }
        }
        final FlowField flow = FlowField.compute(heights, size);
        final boolean[] wet = RiverCarver.computeWetMask(heights, flow, 63, size, 200);

        // Easternmost column flow accumulates the whole row → all should be wet at threshold=size.
        for (int z = 0; z < size; z++) {
            assertTrue(wet[z * size + (size - 1)],
                    "east-column cell at z=" + z + " should be wet (flow >= threshold)");
        }
        // Westernmost column has flow=1 per cell — should not be wet.
        for (int z = 0; z < size; z++) {
            assertFalse(wet[z * size],
                    "west-column cell at z=" + z + " should NOT be wet (flow=1 < threshold)");
        }
    }

    @Test
    void cellsAboveMaxElevationAreNotMarkedWet() {
        // Same ramp, but with the maxElevation clamp set below the high cells. Westernmost
        // columns (height ~100) exceed the clamp and should be skipped.
        final int size = 6;
        final float[] heights = new float[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                heights[z * size + x] = 100f - x; // x=0 → height 100, x=5 → height 95
            }
        }
        final FlowField flow = FlowField.compute(heights, size);
        final boolean[] wet = RiverCarver.computeWetMask(heights, flow, 63, size, /* maxElevation */ 96);
        // x=0 cells are 100 — above clamp — must NOT be wet regardless of flow.
        for (int z = 0; z < size; z++) {
            assertFalse(wet[z * size + 0],
                    "x=0 cell (height=100) above clamp=96 must not be wet, z=" + z);
        }
    }

    @Test
    void belowSeaLevelCellsAreNotMarkedWet() {
        // High-flow cells below sea level are already getting water from the chunk gen's
        // sea-level fill, so we skip them.
        final int size = 4;
        final float[] heights = new float[size * size];
        // All cells below sea level.
        java.util.Arrays.fill(heights, 40f);
        // Inject a clear downhill to one corner so flow accumulates there.
        for (int i = 0; i < heights.length; i++) {
            final int x = i % size;
            final int z = i / size;
            heights[i] = 50f - x - z; // top-left high, bottom-right low; all below sea level.
        }
        final FlowField flow = FlowField.compute(heights, size);
        final boolean[] wet = RiverCarver.computeWetMask(heights, flow, 63, 2, 200);
        for (final boolean w : wet) {
            assertFalse(w, "no cell below sea level should be marked wet");
        }
    }

    @Test
    void lowFlowFlatMapHasNoWetCells() {
        // Flat map: every cell is a sink with flow=1. Nothing should be marked wet at
        // any threshold > 1.
        final int size = 5;
        final float[] heights = new float[size * size];
        java.util.Arrays.fill(heights, 80f);
        final FlowField flow = FlowField.compute(heights, size);
        final boolean[] wet = RiverCarver.computeWetMask(heights, flow, 63, 2, 200);
        for (final boolean w : wet) {
            assertFalse(w);
        }
    }
}
