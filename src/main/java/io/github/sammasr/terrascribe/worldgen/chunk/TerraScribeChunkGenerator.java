package io.github.sammasr.terrascribe.worldgen.chunk;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sammasr.terrascribe.worldgen.noise.FractalNoise;
import io.github.sammasr.terrascribe.worldgen.noise.NoiseField;
import io.github.sammasr.terrascribe.worldgen.noise.SimplexNoise;
import io.github.sammasr.terrascribe.worldgen.terrain.ErosionSimulator;
import io.github.sammasr.terrascribe.worldgen.terrain.Heightmap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * TerraScribe's custom {@link ChunkGenerator}.
 *
 * <p>Milestone 1 implementation: a noise-driven heightmap fills the world with stone up to the
 * computed surface height, water up to sea level if the surface is below sea, air above.
 * Surface rules (grass/dirt/sand layering), biomes, caves, structures, and erosion all land in
 * later milestones.
 *
 * <p>Deliberately extends {@link ChunkGenerator} directly rather than
 * {@link net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator} — see {@code CLAUDE.md}
 * §"M1 design decisions" for the rationale. At M3 (erosion) or M4 (rivers) we may revisit.
 */
public final class TerraScribeChunkGenerator extends ChunkGenerator {

    // Vanilla overworld dimension envelope.
    private static final int MIN_Y = -64;
    private static final int GEN_DEPTH = 384;
    private static final int SEA_LEVEL = 63;

    // Placeholder terrain parameters. Real per-preset config lands in Milestone 5.
    private static final int BASE_HEIGHT = 70;
    private static final float AMPLITUDE = 30f;
    private static final float FREQUENCY = 0.005f;
    private static final int OCTAVES = 4;
    private static final float LACUNARITY = 2f;
    private static final float GAIN = 0.5f;

    // M3 region cache + erosion config.
    private static final int REGION_SIZE = 256;
    private static final int MAX_CACHED_REGIONS = 256;
    private static final ErosionSimulator.Params EROSION_PARAMS = new ErosionSimulator.Params(
            /* dropletCount         */ 25_000,
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

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();

    public static final MapCodec<TerraScribeChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(gen -> gen.biomeSource)
            ).apply(instance, TerraScribeChunkGenerator::new));

    /** Stateless terrain noise — seeded per-call via {@link RandomState}. */
    private final NoiseField terrainNoise;

    /** Lazily built on first chunk fill once we have a {@link RandomState} to derive a seed from. */
    private volatile RegionCache regionCache;
    private volatile int worldSeed;

    public TerraScribeChunkGenerator(final BiomeSource biomeSource) {
        super(biomeSource);
        this.terrainNoise = new FractalNoise(new SimplexNoise(), OCTAVES, LACUNARITY, GAIN, FREQUENCY);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getSeaLevel() {
        return SEA_LEVEL;
    }

    @Override
    public int getGenDepth() {
        return GEN_DEPTH;
    }

    @Override
    public int getBaseHeight(
            final int x,
            final int z,
            final net.minecraft.world.level.levelgen.Heightmap.Types type,
            final LevelHeightAccessor level,
            final RandomState random) {
        // Vanilla expects "max block + 1" for the WORLD_SURFACE heightmap kind. The surface
        // height itself (top solid block) is what our internal Heightmap returns.
        final Heightmap heightmap = heightmapFor(random);
        return heightmap.heightAt(x, z) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(final int x, final int z, final LevelHeightAccessor level, final RandomState random) {
        final int min = level.getMinBuildHeight();
        final int max = level.getMaxBuildHeight();
        final BlockState[] column = new BlockState[max - min];
        final Heightmap heightmap = heightmapFor(random);
        final int surfaceY = heightmap.heightAt(x, z);
        for (int y = min; y < max; y++) {
            final BlockState state;
            if (y == min) {
                state = BEDROCK;
            } else if (y <= surfaceY) {
                state = STONE;
            } else if (y <= SEA_LEVEL) {
                state = WATER;
            } else {
                state = Blocks.AIR.defaultBlockState();
            }
            column[y - min] = state;
        }
        return new NoiseColumn(min, column);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            final Blender blender,
            final RandomState random,
            final StructureManager structureManager,
            final ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> fillChunk(chunk, random), net.minecraft.Util.backgroundExecutor());
    }

    private ChunkAccess fillChunk(final ChunkAccess chunk, final RandomState random) {
        final int chunkMinX = chunk.getPos().getMinBlockX();
        final int chunkMinZ = chunk.getPos().getMinBlockZ();
        final int minY = chunk.getMinBuildHeight();
        final Heightmap heightmap = heightmapFor(random);

        final net.minecraft.world.level.levelgen.Heightmap oceanFloor =
                chunk.getOrCreateHeightmapUnprimed(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG);
        final net.minecraft.world.level.levelgen.Heightmap worldSurface =
                chunk.getOrCreateHeightmapUnprimed(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG);

        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                final int worldX = chunkMinX + localX;
                final int worldZ = chunkMinZ + localZ;
                final int surfaceY = heightmap.heightAt(worldX, worldZ);
                final int topFilledY = Math.max(surfaceY, SEA_LEVEL);

                for (int y = minY; y <= topFilledY; y++) {
                    final BlockState state;
                    if (y == minY) {
                        state = BEDROCK;
                    } else if (y <= surfaceY) {
                        state = STONE;
                    } else {
                        state = WATER;
                    }
                    pos.set(worldX, y, worldZ);
                    chunk.setBlockState(pos, state, false);
                }

                oceanFloor.update(localX, surfaceY, localZ, STONE);
                worldSurface.update(localX, topFilledY, localZ, surfaceY >= SEA_LEVEL ? STONE : WATER);
            }
        }
        return chunk;
    }

    @Override
    public void applyCarvers(
            final WorldGenRegion level,
            final long seed,
            final RandomState random,
            final BiomeManager biomeManager,
            final StructureManager structureManager,
            final ChunkAccess chunk,
            final GenerationStep.Carving step) {
        // M1: no caves or carving. Vanilla caves return at M2/M3 when we re-evaluate extending
        // NoiseBasedChunkGenerator instead.
    }

    @Override
    public void buildSurface(
            final WorldGenRegion level,
            final StructureManager structureManager,
            final RandomState random,
            final ChunkAccess chunk) {
        // M2: read the biome at each column and paint a top + a couple subsurface blocks.
        // We deliberately don't use vanilla SurfaceRules / SurfaceSystem because we don't
        // extend NoiseBasedChunkGenerator and therefore don't have a NoiseChunk to feed it.
        // See SurfaceLayers for the biome → top/sub block mapping.
        final int chunkMinX = chunk.getPos().getMinBlockX();
        final int chunkMinZ = chunk.getPos().getMinBlockZ();
        final int minY = chunk.getMinBuildHeight();
        final Heightmap heightmap = heightmapFor(random);
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                final int worldX = chunkMinX + localX;
                final int worldZ = chunkMinZ + localZ;
                final int surfaceY = heightmap.heightAt(worldX, worldZ);
                if (surfaceY < minY) {
                    continue;
                }

                final int biomeQX = net.minecraft.core.QuartPos.fromBlock(worldX);
                final int biomeQY = net.minecraft.core.QuartPos.fromBlock(Math.max(surfaceY, SEA_LEVEL));
                final int biomeQZ = net.minecraft.core.QuartPos.fromBlock(worldZ);
                final SurfaceLayers.Layer layer = SurfaceLayers.forBiome(chunk.getNoiseBiome(biomeQX, biomeQY, biomeQZ));

                pos.set(worldX, surfaceY, worldZ);
                chunk.setBlockState(pos, layer.top(), false);
                for (int dy = 1; dy <= 3; dy++) {
                    final int y = surfaceY - dy;
                    if (y <= minY) {
                        break;
                    }
                    pos.set(worldX, y, worldZ);
                    chunk.setBlockState(pos, layer.subsurface(), false);
                }
            }
        }
    }

    @Override
    public void spawnOriginalMobs(final WorldGenRegion level) {
        // M1: no initial mob spawn pass.
    }

    @Override
    public void addDebugScreenInfo(final List<String> info, final RandomState random, final BlockPos pos) {
        info.add("TerraScribe — Milestone 1 (stone heightmap)");
    }

    /**
     * Returns a {@link Heightmap} backed by the region cache. Lazy-initialized on first call,
     * keyed by a seed derived from {@link RandomState} (which captures the world seed via its
     * positional random factory).
     */
    private Heightmap heightmapFor(final RandomState random) {
        final RegionCache cache = regionCache(random);
        return cache::heightAt;
    }

    private RegionCache regionCache(final RandomState random) {
        RegionCache cache = this.regionCache;
        if (cache != null) {
            return cache;
        }
        synchronized (this) {
            cache = this.regionCache;
            if (cache != null) {
                return cache;
            }
            this.worldSeed = random.aquiferRandom().at(0, 0, 0).nextInt();
            cache = new RegionCache(REGION_SIZE, MAX_CACHED_REGIONS, this::buildRegion);
            this.regionCache = cache;
            return cache;
        }
    }

    private RegionHeightmap buildRegion(final int regionX, final int regionZ) {
        // 1. Sample the base noise heightmap across the region.
        final float[] heights = new float[REGION_SIZE * REGION_SIZE];
        for (int localZ = 0; localZ < REGION_SIZE; localZ++) {
            for (int localX = 0; localX < REGION_SIZE; localX++) {
                final int blockX = regionX * REGION_SIZE + localX;
                final int blockZ = regionZ * REGION_SIZE + localZ;
                heights[localZ * REGION_SIZE + localX] =
                        BASE_HEIGHT + AMPLITUDE * this.terrainNoise.sample(blockX, blockZ, this.worldSeed);
            }
        }
        // 2. Apply hydraulic erosion to the region. Per-region seed mixes worldSeed and
        // the region coords so adjacent regions don't get identical droplet starting points
        // (which would otherwise leave a visible seam).
        final long regionSeed = ((long) this.worldSeed * 0x9e3779b97f4a7c15L)
                ^ (((long) regionX) * 0xbf58476d1ce4e5b9L)
                ^ (((long) regionZ) * 0x94d049bb133111ebL);
        ErosionSimulator.simulate(heights, REGION_SIZE, regionSeed, EROSION_PARAMS);
        return new RegionHeightmap(regionX, regionZ, REGION_SIZE, heights);
    }
}
