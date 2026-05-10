package io.github.sammasr.terrascribe.worldgen.chunk;

/**
 * A square region of eroded heights plus a per-cell wet mask, indexed in block coordinates.
 *
 * <p>The region's coordinate origin is {@code (regionX * size, regionZ * size)} in block
 * coords. Both arrays are stored row-major: index {@code z * size + x} (local coords).
 *
 * <p>Records here are immutable from the consumer's view; the arrays ARE mutated during
 * region construction (erosion + river carving) but are never modified after the
 * {@code RegionHeightmap} leaves {@link RegionCache}.
 *
 * <p>{@code wet[i] == true} means the column at index {@code i} should have water placed at
 * its surface — typically a river path or a lake sink above sea level. Cells below sea level
 * are NOT marked wet here; the chunk generator fills them with water during {@code
 * fillFromNoise} based on sea-level rule.
 */
public record RegionHeightmap(int regionX, int regionZ, int size, float[] heights, boolean[] wet) {

    public RegionHeightmap {
        if (size < 1) {
            throw new IllegalArgumentException("region size must be >= 1, got " + size);
        }
        if (heights == null || heights.length != size * size) {
            throw new IllegalArgumentException("heights must be size*size = " + (size * size) + " floats");
        }
        if (wet == null || wet.length != size * size) {
            throw new IllegalArgumentException("wet must be size*size = " + (size * size) + " booleans");
        }
    }

    /**
     * Returns the eroded height at the given world block coordinate.
     */
    public float at(final int blockX, final int blockZ) {
        final int localX = blockX - this.regionX * this.size;
        final int localZ = blockZ - this.regionZ * this.size;
        if (localX < 0 || localX >= this.size || localZ < 0 || localZ >= this.size) {
            throw new IllegalArgumentException(
                    "block (" + blockX + "," + blockZ + ") not in region (" + this.regionX + "," + this.regionZ + ")");
        }
        return this.heights[localZ * this.size + localX];
    }

    /**
     * Returns {@code true} if the column at the given world block coordinate should have
     * water at its surface (river or lake cell).
     */
    public boolean isWet(final int blockX, final int blockZ) {
        final int localX = blockX - this.regionX * this.size;
        final int localZ = blockZ - this.regionZ * this.size;
        if (localX < 0 || localX >= this.size || localZ < 0 || localZ >= this.size) {
            throw new IllegalArgumentException(
                    "block (" + blockX + "," + blockZ + ") not in region (" + this.regionX + "," + this.regionZ + ")");
        }
        return this.wet[localZ * this.size + localX];
    }
}
