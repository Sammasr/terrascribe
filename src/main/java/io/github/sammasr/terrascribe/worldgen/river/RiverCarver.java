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

    /**
     * Default ceiling: cells higher than {@code sea level + 30} aren't marked wet, even if
     * they have high accumulated flow. Without this clamp, mountain-top high-flow paths get
     * marked, and either water perches at altitude (broken-looking) or we carve a deep canyon
     * through the mountain to drop them to sea level. Capping just keeps rivers in foothills
     * and lowlands.
     */
    public static final int DEFAULT_MAX_RIVER_ELEVATION_ABOVE_SEA = 30;

    private RiverCarver() {
        // utility class
    }

    /**
     * Computes a wet-cells mask. Same row-major layout as {@code heightmap} and
     * {@code FlowField}; index {@code z * size + x}.
     *
     * @param heightmap         the eroded heightmap (read-only)
     * @param flowField         flow field computed over {@code heightmap}
     * @param seaLevel          columns whose height is below this y are excluded (already water)
     * @param flowThreshold     minimum accumulated upstream cells for a cell to count as wet
     * @param maxElevation      cells above this height are skipped (no perched mountain rivers)
     * @return a {@code size × size} boolean array — {@code true} where water should be placed
     */
    public static boolean[] computeWetMask(
            final float[] heightmap,
            final FlowField flowField,
            final int seaLevel,
            final int flowThreshold,
            final int maxElevation) {
        if (heightmap == null || flowField == null) {
            throw new IllegalArgumentException("heightmap and flowField must not be null");
        }
        if (flowThreshold < 1) {
            throw new IllegalArgumentException("flowThreshold must be >= 1, got " + flowThreshold);
        }
        if (maxElevation < seaLevel) {
            throw new IllegalArgumentException(
                    "maxElevation (" + maxElevation + ") must be >= seaLevel (" + seaLevel + ")");
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
            final int height = Math.round(heightmap[i]);
            if (height < seaLevel) {
                continue;
            }
            if (height > maxElevation) {
                continue;
            }
            wet[i] = true;
        }
        return wet;
    }

    /**
     * Carves wet cells down to {@code seaLevel - 1} so water filled at sea level fills the
     * channel/lake naturally. Modifies {@code heightmap} in place.
     */
    public static void carveWetCells(final float[] heightmap, final boolean[] wet, final int seaLevel) {
        if (heightmap == null || wet == null) {
            throw new IllegalArgumentException("heightmap and wet must not be null");
        }
        if (heightmap.length != wet.length) {
            throw new IllegalArgumentException(
                    "heightmap and wet must be the same length: " + heightmap.length + " vs " + wet.length);
        }
        final float target = seaLevel - 1f;
        for (int i = 0; i < wet.length; i++) {
            if (wet[i] && heightmap[i] > target) {
                heightmap[i] = target;
            }
        }
    }
}
