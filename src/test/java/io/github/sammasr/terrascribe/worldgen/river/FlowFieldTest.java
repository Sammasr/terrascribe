package io.github.sammasr.terrascribe.worldgen.river;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlowFieldTest {

    @Test
    void rejectsBadArguments() {
        assertThrows(IllegalArgumentException.class, () -> FlowField.compute(null, 8));
        assertThrows(IllegalArgumentException.class, () -> FlowField.compute(new float[4], 1));
        assertThrows(IllegalArgumentException.class, () -> FlowField.compute(new float[100], 8));
    }

    @Test
    void flatHeightmapHasNoDownhill() {
        // Every cell is the same height → no cell has any neighbor strictly lower → all sinks.
        final int size = 8;
        final float[] heights = new float[size * size];
        java.util.Arrays.fill(heights, 50f);
        final FlowField flow = FlowField.compute(heights, size);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                assertEquals(FlowField.NO_DOWNHILL, flow.downhillOf(x, z),
                        "flat map cell (" + x + "," + z + ") should be a sink");
                assertEquals(1, flow.flowAt(x, z),
                        "flat map cell (" + x + "," + z + ") should hold only its own rain");
            }
        }
    }

    @Test
    void singlePeakDrainsToAllEdges() {
        // 5×5 with a peak in the centre. Flow from the peak should propagate to the edges.
        final int size = 5;
        final float[] heights = new float[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                final float dx = x - 2f;
                final float dz = z - 2f;
                heights[z * size + x] = 10f - (float) Math.sqrt(dx * dx + dz * dz);
            }
        }
        final FlowField flow = FlowField.compute(heights, size);
        // Peak should drain (downhill != NO_DOWNHILL).
        assertNotEquals(FlowField.NO_DOWNHILL, flow.downhillOf(2, 2), "peak should have a downhill neighbor");
        // Total flow across all cells equals total rain (size*size).
        int totalFlow = 0;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                totalFlow += flow.flowAt(x, z);
            }
        }
        assertTrue(totalFlow >= size * size, "total flow >= total rain");
    }

    @Test
    void inclinedRampDrainsLinearlyDownhill() {
        // Heightmap is a linear ramp from west to east — height decreases as x increases.
        // Flow should point west→east; the easternmost row should accumulate everything.
        final int size = 6;
        final float[] heights = new float[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                heights[z * size + x] = 100f - x; // strictly decreasing in x
            }
        }
        final FlowField flow = FlowField.compute(heights, size);

        // Easternmost column (x = size-1) cells are edge sinks (no further east neighbor).
        // Their flow should be size (entire row drains through each).
        for (int z = 0; z < size; z++) {
            assertEquals(FlowField.NO_DOWNHILL, flow.downhillOf(size - 1, z),
                    "easternmost cell at z=" + z + " should be a sink");
            assertEquals(size, flow.flowAt(size - 1, z),
                    "easternmost cell at z=" + z + " should have accumulated entire row of rain");
        }
    }

    @Test
    void deterministicForSameInput() {
        final int size = 16;
        final float[] a = noisyHeightmap(size, 42);
        final float[] b = noisyHeightmap(size, 42);
        final FlowField fa = FlowField.compute(a, size);
        final FlowField fb = FlowField.compute(b, size);
        for (int i = 0; i < size * size; i++) {
            assertEquals(fa.downhillOfIndex(i), fb.downhillOfIndex(i));
            assertEquals(fa.flowAtIndex(i), fb.flowAtIndex(i));
        }
    }

    @Test
    void totalRainConservedAcrossSinks() {
        // Sum of flow accumulation at all sinks should equal total rain (every cell starts
        // with 1 unit). This holds because rain only leaves the map through sinks.
        final int size = 24;
        final float[] heights = noisyHeightmap(size, 7);
        final FlowField flow = FlowField.compute(heights, size);
        int sinkTotal = 0;
        for (int i = 0; i < size * size; i++) {
            if (flow.downhillOfIndex(i) == FlowField.NO_DOWNHILL) {
                sinkTotal += flow.flowAtIndex(i);
            }
        }
        assertEquals(size * size, sinkTotal,
                "sum of flow at sinks must equal total rain (= cell count)");
    }

    private static float[] noisyHeightmap(final int size, final long seed) {
        final java.util.SplittableRandom rng = new java.util.SplittableRandom(seed);
        final float[] map = new float[size * size];
        for (int i = 0; i < map.length; i++) {
            map[i] = rng.nextFloat() * 100f;
        }
        return map;
    }
}
