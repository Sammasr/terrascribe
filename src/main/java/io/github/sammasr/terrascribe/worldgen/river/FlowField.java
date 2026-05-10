package io.github.sammasr.terrascribe.worldgen.river;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * D8 downhill flow accumulation over a square heightmap.
 *
 * <p>For each cell:
 * <ul>
 *   <li>{@link #downhill} stores the index of the steepest-descent neighbor (one of eight)
 *       or {@code -1} if the cell is a local minimum / map-edge sink.</li>
 *   <li>{@link #flowAccumulation} stores the number of upstream cells (including this one)
 *       that drain through this cell. Computed by topologically traversing cells in
 *       descending-height order and pushing accumulated rain downhill.</li>
 * </ul>
 *
 * <p>Pure math — no Minecraft API references. The implementation is O(N log N) due to the
 * sort by height; for our 256×256 regions this is ~ms.
 *
 * <p>Algorithm: classic D8 (Tarboton 1991) with a simplification — we don't split flow,
 * each cell drains 100% to its single steepest neighbor. Trade-off: simpler and faster but
 * produces slightly stringier rivers than multi-flow algorithms.
 */
public final class FlowField {

    /** -1 in {@link #downhill} means "this cell has no downhill neighbor" (sink / edge). */
    public static final int NO_DOWNHILL = -1;

    private static final int[] D8_DX = {-1,  0,  1, -1, 1, -1, 0, 1};
    private static final int[] D8_DZ = {-1, -1, -1,  0, 0,  1, 1, 1};
    /** Inverse-distance weights for each D8 neighbor: 1/sqrt(2) for diagonals, 1 for cardinals. */
    private static final float[] D8_INV_DIST = {
            (float) (1.0 / Math.sqrt(2.0)), 1f, (float) (1.0 / Math.sqrt(2.0)),
            1f,                                 1f,
            (float) (1.0 / Math.sqrt(2.0)), 1f, (float) (1.0 / Math.sqrt(2.0))
    };

    private final int size;
    private final int[] downhill;
    private final int[] flowAccumulation;

    private FlowField(final int size, final int[] downhill, final int[] flowAccumulation) {
        this.size = size;
        this.downhill = downhill;
        this.flowAccumulation = flowAccumulation;
    }

    public int size() {
        return this.size;
    }

    /** Returns the cell index of the steepest-descent neighbor, or {@link #NO_DOWNHILL}. */
    public int downhillOf(final int x, final int z) {
        return this.downhill[index(x, z)];
    }

    /** Returns the accumulated upstream cell count for this cell. */
    public int flowAt(final int x, final int z) {
        return this.flowAccumulation[index(x, z)];
    }

    public int flowAtIndex(final int index) {
        return this.flowAccumulation[index];
    }

    public int downhillOfIndex(final int index) {
        return this.downhill[index];
    }

    private int index(final int x, final int z) {
        return z * this.size + x;
    }

    /**
     * Computes the D8 downhill field and flow accumulation for the given heightmap.
     *
     * @param heightmap row-major {@code size × size}; index {@code z * size + x}
     * @param size      side length; {@code heightmap.length} must equal {@code size * size}
     */
    public static FlowField compute(final float[] heightmap, final int size) {
        if (heightmap == null) {
            throw new IllegalArgumentException("heightmap must not be null");
        }
        if (size < 2) {
            throw new IllegalArgumentException("size must be >= 2, got " + size);
        }
        if (heightmap.length != size * size) {
            throw new IllegalArgumentException(
                    "heightmap.length=" + heightmap.length + " does not match size*size=" + (size * size));
        }

        final int total = size * size;
        final int[] downhill = new int[total];
        Arrays.fill(downhill, NO_DOWNHILL);

        // 1. Steepest-descent neighbor per cell.
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                final int here = z * size + x;
                final float hereHeight = heightmap[here];
                float bestSlope = 0f;
                int bestNeighbor = NO_DOWNHILL;
                for (int dir = 0; dir < 8; dir++) {
                    final int nx = x + D8_DX[dir];
                    final int nz = z + D8_DZ[dir];
                    if (nx < 0 || nx >= size || nz < 0 || nz >= size) {
                        continue;
                    }
                    final int neighbor = nz * size + nx;
                    final float drop = hereHeight - heightmap[neighbor];
                    if (drop <= 0f) {
                        continue;
                    }
                    final float slope = drop * D8_INV_DIST[dir];
                    if (slope > bestSlope) {
                        bestSlope = slope;
                        bestNeighbor = neighbor;
                    }
                }
                downhill[here] = bestNeighbor;
            }
        }

        // 2. Flow accumulation. Sort cell indices by height descending so each cell can
        // push its accumulator into its downhill neighbor before that neighbor is processed.
        // Using boxed Integer[] is acceptable here — N <= ~65k for our regions.
        final Integer[] order = IntStream.range(0, total).boxed().toArray(Integer[]::new);
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> heightmap[i]).reversed());

        final int[] flow = new int[total];
        Arrays.fill(flow, 1); // each cell starts with one unit of rain.
        for (final int i : order) {
            final int down = downhill[i];
            if (down != NO_DOWNHILL) {
                flow[down] += flow[i];
            }
        }

        return new FlowField(size, downhill, flow);
    }
}
