package io.github.sammasr.terrascribe.worldgen.chunk;

/**
 * A square region of eroded heights, indexed in block coordinates.
 *
 * <p>The region's coordinate origin is {@code (regionX * size, regionZ * size)} in block
 * coords. Heights are stored row-major in {@code heights[z * size + x]} (local coords).
 *
 * <p>Records here are immutable from the consumer's view; the heightmap array IS mutated
 * during erosion at construction time, but is never modified after the {@code RegionHeightmap}
 * leaves {@link RegionCache}.
 */
public record RegionHeightmap(int regionX, int regionZ, int size, float[] heights) {

    public RegionHeightmap {
        if (size < 1) {
            throw new IllegalArgumentException("region size must be >= 1, got " + size);
        }
        if (heights == null || heights.length != size * size) {
            throw new IllegalArgumentException("heights must be size*size = " + (size * size) + " floats");
        }
    }

    /**
     * Returns the eroded height at the given world block coordinate, which must fall within
     * this region's bounds. Caller is expected to compute the right region for arbitrary
     * coords via {@link RegionCache#heightAt}.
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
}
