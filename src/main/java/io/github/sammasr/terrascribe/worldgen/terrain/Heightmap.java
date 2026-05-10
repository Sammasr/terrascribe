package io.github.sammasr.terrascribe.worldgen.terrain;

/**
 * Maps a 2D world coordinate to a terrain surface height (the y-value of the top solid block).
 *
 * <p>Pure math — no Minecraft API references. The seed is baked into each implementation so
 * a {@code Heightmap} represents a specific world's terrain, not a generic noise function.
 *
 * <p>Implementations must be deterministic: same {@code (x, z)} pair always returns the same
 * height across JVM runs.
 */
@FunctionalInterface
public interface Heightmap {

    /**
     * Returns the surface height at world coordinate {@code (x, z)}. Height is the y-value of
     * the top solid block — block {@code y == heightAt(x, z)} is solid, block {@code y + 1}
     * is air (in the absence of caves and other modifiers, which are applied later).
     *
     * @param x world x in blocks
     * @param z world z in blocks
     * @return surface y, in blocks
     */
    int heightAt(int x, int z);
}
