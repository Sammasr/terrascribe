package io.github.sammasr.terrascribe.worldgen.terrain;

import java.util.SplittableRandom;

/**
 * Lague-style droplet hydraulic erosion, applied in-place to a square float heightmap.
 *
 * <p>Algorithm summary (see Sebastian Lague's video for the canonical reference):
 * <ol>
 *   <li>Spawn many droplets at random positions on the heightmap.</li>
 *   <li>Each droplet carries water and sediment. At each step it:
 *       <ul>
 *           <li>bilinearly interpolates height and gradient at its current sub-cell position,</li>
 *           <li>updates velocity from the gradient (mixed with previous velocity via inertia),</li>
 *           <li>moves one unit in the velocity direction,</li>
 *           <li>computes new height, deposits sediment if going uphill or over capacity,
 *               or erodes if under capacity and going downhill,</li>
 *           <li>evaporates a fraction of its water.</li>
 *       </ul>
 *   </li>
 *   <li>Droplets terminate when they run out of water, leave the heightmap, or hit the
 *       step limit.</li>
 * </ol>
 *
 * <p>Pure math — no Minecraft API references. Deterministic per seed. M3's first cut is
 * single-cell (radius-1) erosion/deposit — no brush kernel. The brush kernel produces
 * smoother visuals at modest perf cost and lands at M4 if/when erosion quality needs it.
 */
public final class ErosionSimulator {

    /** Reasonable starting parameters for a 512×512 region. Tunable per-preset later. */
    public record Params(
            int dropletCount,
            int maxStepsPerDroplet,
            float inertia,
            float sedimentCapacityFactor,
            float minSlope,
            float erosionRate,
            float depositionRate,
            float evaporationRate,
            float initialWater,
            float initialSpeed,
            float gravity) {

        public Params {
            if (dropletCount < 0 || maxStepsPerDroplet < 0) {
                throw new IllegalArgumentException("counts must be non-negative");
            }
            if (inertia < 0f || inertia > 1f) {
                throw new IllegalArgumentException("inertia must be in [0, 1], got " + inertia);
            }
            if (evaporationRate < 0f || evaporationRate > 1f) {
                throw new IllegalArgumentException("evaporationRate must be in [0, 1], got " + evaporationRate);
            }
            if (sedimentCapacityFactor <= 0f || erosionRate < 0f || depositionRate < 0f) {
                throw new IllegalArgumentException("rates and capacity must be non-negative; capacity > 0");
            }
        }

        public static Params defaults() {
            return new Params(
                    /* dropletCount         */ 50_000,
                    /* maxStepsPerDroplet   */ 30,
                    /* inertia              */ 0.05f,
                    /* sedimentCapacityFactor */ 4f,
                    /* minSlope             */ 0.01f,
                    /* erosionRate          */ 0.3f,
                    /* depositionRate       */ 0.3f,
                    /* evaporationRate      */ 0.01f,
                    /* initialWater         */ 1f,
                    /* initialSpeed         */ 1f,
                    /* gravity              */ 4f);
        }
    }

    private ErosionSimulator() {
        // utility class — call simulate()
    }

    /**
     * Runs erosion on {@code heightmap} (modified in place). Heightmap is a row-major
     * {@code width × width} square; index {@code z * width + x}.
     *
     * @param heightmap the square heightmap, modified in place
     * @param width     side length; must satisfy {@code heightmap.length == width * width}
     * @param seed      RNG seed (passed to {@link SplittableRandom})
     * @param params    erosion parameters; see {@link Params#defaults()}
     */
    public static void simulate(final float[] heightmap, final int width, final long seed, final Params params) {
        if (heightmap == null || params == null) {
            throw new IllegalArgumentException("heightmap and params must not be null");
        }
        if (width < 3) {
            throw new IllegalArgumentException("width must be >= 3, got " + width);
        }
        if (heightmap.length != width * width) {
            throw new IllegalArgumentException(
                    "heightmap.length=" + heightmap.length + " does not match width*width=" + (width * width));
        }
        final SplittableRandom rng = new SplittableRandom(seed);
        final int maxIndex = width - 1;
        for (int d = 0; d < params.dropletCount(); d++) {
            float posX = rng.nextFloat() * maxIndex;
            float posZ = rng.nextFloat() * maxIndex;
            float velX = 0f;
            float velZ = 0f;
            float water = params.initialWater();
            float speed = params.initialSpeed();
            float sediment = 0f;

            for (int step = 0; step < params.maxStepsPerDroplet(); step++) {
                final int cellX = (int) posX;
                final int cellZ = (int) posZ;
                final float offsetX = posX - cellX;
                final float offsetZ = posZ - cellZ;

                // Read four corners of the current cell.
                final int i00 = cellZ * width + cellX;
                final int i10 = i00 + 1;
                final int i01 = i00 + width;
                final int i11 = i00 + width + 1;
                final float h00 = heightmap[i00];
                final float h10 = heightmap[i10];
                final float h01 = heightmap[i01];
                final float h11 = heightmap[i11];

                // Bilinear height interpolation.
                final float h0 = h00 + (h10 - h00) * offsetX;
                final float h1 = h01 + (h11 - h01) * offsetX;
                final float currentHeight = h0 + (h1 - h0) * offsetZ;

                // Gradient from finite differences of the cell corners (bilinear).
                final float gradX = (h10 - h00) * (1f - offsetZ) + (h11 - h01) * offsetZ;
                final float gradZ = (h01 - h00) * (1f - offsetX) + (h11 - h10) * offsetX;

                // Update velocity (negative gradient = downhill).
                velX = velX * params.inertia() - gradX * (1f - params.inertia());
                velZ = velZ * params.inertia() - gradZ * (1f - params.inertia());
                final float velLen = (float) Math.sqrt(velX * velX + velZ * velZ);
                if (velLen <= 1e-6f) {
                    // Stagnant droplet — drop its sediment and stop.
                    deposit(heightmap, cellX, cellZ, width, offsetX, offsetZ, sediment);
                    break;
                }
                velX /= velLen;
                velZ /= velLen;

                final float nextX = posX + velX;
                final float nextZ = posZ + velZ;
                if (nextX < 0f || nextX > maxIndex || nextZ < 0f || nextZ > maxIndex) {
                    // Drop sediment at the boundary and stop.
                    deposit(heightmap, cellX, cellZ, width, offsetX, offsetZ, sediment);
                    break;
                }

                // Height at next position (bilerp again — could be optimized).
                final int nCellX = (int) nextX;
                final int nCellZ = (int) nextZ;
                final float nOffX = nextX - nCellX;
                final float nOffZ = nextZ - nCellZ;
                final int n00 = nCellZ * width + nCellX;
                final float nh00 = heightmap[n00];
                final float nh10 = heightmap[n00 + 1];
                final float nh01 = heightmap[n00 + width];
                final float nh11 = heightmap[n00 + width + 1];
                final float nh0 = nh00 + (nh10 - nh00) * nOffX;
                final float nh1 = nh01 + (nh11 - nh01) * nOffX;
                final float nextHeight = nh0 + (nh1 - nh0) * nOffZ;

                final float deltaHeight = nextHeight - currentHeight;
                final float capacity =
                        Math.max(-deltaHeight, params.minSlope()) * speed * water * params.sedimentCapacityFactor();

                if (sediment > capacity || deltaHeight > 0f) {
                    // Deposit: either over capacity, or moving uphill — drop sediment in current cell.
                    final float amount = deltaHeight > 0f
                            ? Math.min(deltaHeight, sediment)
                            : (sediment - capacity) * params.depositionRate();
                    deposit(heightmap, cellX, cellZ, width, offsetX, offsetZ, amount);
                    sediment -= amount;
                } else {
                    // Erode: scoop up sediment, bounded by how much we'd flatten the slope.
                    final float amount = Math.min((capacity - sediment) * params.erosionRate(), -deltaHeight);
                    erode(heightmap, cellX, cellZ, width, offsetX, offsetZ, amount);
                    sediment += amount;
                }

                // Update speed and water.
                speed = (float) Math.sqrt(Math.max(0f, speed * speed + deltaHeight * params.gravity()));
                water *= (1f - params.evaporationRate());

                posX = nextX;
                posZ = nextZ;
            }
        }
    }

    /**
     * Distributes a deposit across the four corners of cell {@code (cellX, cellZ)} weighted
     * by the sub-cell offsets. Increases heightmap values.
     */
    private static void deposit(
            final float[] heightmap,
            final int cellX,
            final int cellZ,
            final int width,
            final float offsetX,
            final float offsetZ,
            final float amount) {
        if (amount <= 0f) {
            return;
        }
        final int i00 = cellZ * width + cellX;
        heightmap[i00]                 += amount * (1f - offsetX) * (1f - offsetZ);
        heightmap[i00 + 1]             += amount * offsetX        * (1f - offsetZ);
        heightmap[i00 + width]         += amount * (1f - offsetX) * offsetZ;
        heightmap[i00 + width + 1]     += amount * offsetX        * offsetZ;
    }

    /**
     * Removes material across the four corners of cell {@code (cellX, cellZ)} weighted by
     * the sub-cell offsets. Decreases heightmap values.
     */
    private static void erode(
            final float[] heightmap,
            final int cellX,
            final int cellZ,
            final int width,
            final float offsetX,
            final float offsetZ,
            final float amount) {
        if (amount <= 0f) {
            return;
        }
        final int i00 = cellZ * width + cellX;
        heightmap[i00]                 -= amount * (1f - offsetX) * (1f - offsetZ);
        heightmap[i00 + 1]             -= amount * offsetX        * (1f - offsetZ);
        heightmap[i00 + width]         -= amount * (1f - offsetX) * offsetZ;
        heightmap[i00 + width + 1]     -= amount * offsetX        * offsetZ;
    }
}
