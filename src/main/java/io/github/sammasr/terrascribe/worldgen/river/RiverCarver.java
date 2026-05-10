package io.github.sammasr.terrascribe.worldgen.river;

/**
 * Derives a "wet cells" mask from a {@link FlowField} so the chunk generator knows where to
 * place water at the surface for rivers and lake pools.
 *
 * <p>Algorithm: any cell with accumulated flow {@code >= threshold} is marked wet. This
 * captures both rivers (cells along a high-flow path) and lake centers (sinks that
 * accumulate all upstream rain). The same threshold serves both — cells naturally fall into
 * the "high flow" set when they're either:
 * <ul>
 *   <li>An outlet for a sizeable upstream basin → river</li>
 *   <li>A terminal sink (no downhill outlet) collecting upstream rain → lake</li>
 * </ul>
 *
 * <p>Cells already below sea level are not marked wet by this carver — the chunk generator's
 * {@code fillFromNoise} already fills water from the surface up to sea level for low cells.
 * Marking them would just be noise.
 *
 * <p>For M4 first cut: no heightmap mutation. Erosion already carved channel-shaped valleys
 * during M3; we just mark them wet. Explicit channel carving (further subtracting blocks
 * along high-flow cells) is a polish step if rivers look "floating" or perched.
 */
public final class RiverCarver {

    /** Reasonable default for our 256-side regions; ~1-3% of cells become wet. */
    public static final int DEFAULT_FLOW_THRESHOLD = 80;

    private RiverCarver() {
        // utility class
    }

    /**
     * Computes a wet-cells mask. Same row-major layout as {@code heightmap} and
     * {@code FlowField}; index {@code z * size + x}.
     *
     * @param heightmap        the eroded heightmap (read-only)
     * @param flowField        flow field computed over {@code heightmap}
     * @param seaLevel         columns whose height is below this y are excluded (already water)
     * @param flowThreshold    minimum accumulated upstream cells for a cell to count as wet
     * @return a {@code size × size} boolean array — {@code true} where water should be placed
     */
    public static boolean[] computeWetMask(
            final float[] heightmap,
            final FlowField flowField,
            final int seaLevel,
            final int flowThreshold) {
        if (heightmap == null || flowField == null) {
            throw new IllegalArgumentException("heightmap and flowField must not be null");
        }
        if (flowThreshold < 1) {
            throw new IllegalArgumentException("flowThreshold must be >= 1, got " + flowThreshold);
        }
        final int size = flowField.size();
        if (heightmap.length != size * size) {
            throw new IllegalArgumentException(
                    "heightmap.length=" + heightmap.length + " does not match flowField size " + size);
        }
        final boolean[] wet = new boolean[size * size];
        for (int i = 0; i < wet.length; i++) {
            if (flowField.flowAtIndex(i) < flowThreshold) {
                continue;
            }
            if (Math.round(heightmap[i]) < seaLevel) {
                continue;
            }
            wet[i] = true;
        }
        return wet;
    }
}
