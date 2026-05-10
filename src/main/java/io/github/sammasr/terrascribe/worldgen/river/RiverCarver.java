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

    /** River channel half-width in cells — cells within this distance of a flow centerline are wet. ~5 blocks wide. */
    public static final int DEFAULT_CHANNEL_RADIUS = 2;
    /** Bank half-width in cells — terrain slopes down from natural height at this radius to channel at {@code CHANNEL_RADIUS}. */
    public static final int DEFAULT_BANK_RADIUS = 8;

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
     * Carves rivers with smoothly-sloped banks and returns a dilated wet mask.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Compute a per-cell distance to the nearest centerline cell from {@code rawWet}
     *       (cells with high flow accumulation). Cells outside {@code bankRadius} get
     *       {@code +infinity}.</li>
     *   <li>For each cell with finite distance, target carved height is:
     *       <ul>
     *         <li>{@code seaLevel - 1} when {@code d <= channelRadius} (the river bed)</li>
     *         <li>smoothstep-blended from {@code seaLevel - 1} (at channel edge) to the
     *             original height (at bank edge) when {@code channelRadius < d <= bankRadius}</li>
     *       </ul>
     *       The heightmap is lowered to the target if it was higher. This produces gently-
     *       sloped river valleys instead of vertical-cliff cookie-cutter channels.</li>
     *   <li>Cells with {@code d <= channelRadius} are returned in the dilated wet mask.
     *       This is the actual array of "water above this column" used by the chunk gen.</li>
     * </ol>
     *
     * <p>Modifies {@code heightmap} in place; returns the dilated wet mask.
     *
     * @param heightmap       float heightmap, row-major; modified in place
     * @param rawWet          centerline mask (e.g. from {@link #computeWetMask}); not modified
     * @param size            heightmap side length; {@code heightmap.length == size*size}
     * @param seaLevel        water level; river beds carve to {@code seaLevel - 1}
     * @param channelRadius   half-width of the river channel in cells (≥ 1)
     * @param bankRadius      half-width of the gradient bank in cells (≥ {@code channelRadius})
     * @return {@code size × size} boolean array — {@code true} for water-bearing cells
     */
    public static boolean[] carveChannelsWithBanks(
            final float[] heightmap,
            final boolean[] rawWet,
            final int size,
            final int seaLevel,
            final int channelRadius,
            final int bankRadius) {
        if (heightmap == null || rawWet == null) {
            throw new IllegalArgumentException("heightmap and rawWet must not be null");
        }
        if (heightmap.length != size * size || rawWet.length != size * size) {
            throw new IllegalArgumentException(
                    "heightmap/rawWet length must equal size*size = " + (size * size));
        }
        if (channelRadius < 1) {
            throw new IllegalArgumentException("channelRadius must be >= 1, got " + channelRadius);
        }
        if (bankRadius < channelRadius) {
            throw new IllegalArgumentException(
                    "bankRadius (" + bankRadius + ") must be >= channelRadius (" + channelRadius + ")");
        }

        // 1. Distance to nearest centerline cell, brute-force within bankRadius.
        // Iterate over each centerline cell and paint distances into the dist array.
        final float[] dist = new float[size * size];
        java.util.Arrays.fill(dist, Float.POSITIVE_INFINITY);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                if (!rawWet[z * size + x]) {
                    continue;
                }
                // Paint distances around this centerline cell.
                final int zMin = Math.max(0, z - bankRadius);
                final int zMax = Math.min(size - 1, z + bankRadius);
                final int xMin = Math.max(0, x - bankRadius);
                final int xMax = Math.min(size - 1, x + bankRadius);
                for (int nz = zMin; nz <= zMax; nz++) {
                    for (int nx = xMin; nx <= xMax; nx++) {
                        final int dz = nz - z;
                        final int dx = nx - x;
                        final float d = (float) Math.sqrt((double) (dx * dx + dz * dz));
                        if (d > bankRadius) {
                            continue;
                        }
                        final int idx = nz * size + nx;
                        if (d < dist[idx]) {
                            dist[idx] = d;
                        }
                    }
                }
            }
        }

        // 2. Carve heightmap + build dilated wet mask in one pass.
        final boolean[] wet = new boolean[size * size];
        final float bedHeight = seaLevel - 1f;
        for (int i = 0; i < heightmap.length; i++) {
            final float d = dist[i];
            if (d > bankRadius) {
                continue; // far from any river — untouched.
            }
            if (d <= channelRadius) {
                wet[i] = true;
                if (heightmap[i] > bedHeight) {
                    heightmap[i] = bedHeight;
                }
                continue;
            }
            // Bank: smoothstep from bedHeight at channel edge to natural height at bank edge.
            final float t = (d - channelRadius) / (float) (bankRadius - channelRadius); // 0..1
            final float smooth = t * t * (3f - 2f * t);
            final float natural = heightmap[i];
            final float target = bedHeight + (natural - bedHeight) * smooth;
            if (heightmap[i] > target) {
                heightmap[i] = target;
            }
        }
        return wet;
    }
}
