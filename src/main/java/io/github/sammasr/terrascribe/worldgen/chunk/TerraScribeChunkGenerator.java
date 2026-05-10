package io.github.sammasr.terrascribe.worldgen.chunk;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sammasr.terrascribe.worldgen.noise.FractalNoise;
import io.github.sammasr.terrascribe.worldgen.noise.NoiseField;
import io.github.sammasr.terrascribe.worldgen.noise.SimplexNoise;
import io.github.sammasr.terrascribe.worldgen.terrain.BasicHeightmapGenerator;
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

    // M1 placeholder terrain parameters. Real per-preset config lands in Milestone 5.
    private static final int BASE_HEIGHT = 70;
    private static final float AMPLITUDE = 30f;
    private static final float FREQUENCY = 0.005f;
    private static final int OCTAVES = 4;
    private static final float LACUNARITY = 2f;
    private static final float GAIN = 0.5f;

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();

    public static final MapCodec<TerraScribeChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(gen -> gen.biomeSource)
            ).apply(instance, TerraScribeChunkGenerator::new));

    /** Stateless terrain noise — seeded per-call via {@link RandomState}. */
    private final NoiseField terrainNoise;

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
        // M1: everything is stone — no grass/dirt/sand layering yet. Surface rules land at M2.
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
     * Derives a deterministic per-world seed for our heightmap.
     *
     * <p>{@link RandomState} does not expose the level seed directly, so we derive one by
     * sampling its {@link net.minecraft.world.level.levelgen.PositionalRandomFactory} at a
     * fixed coordinate. The factory is itself seeded from the world seed, so the result is
     * stable per-world without us having to capture the seed in a field.
     */
    private Heightmap heightmapFor(final RandomState random) {
        final int seed = random.aquiferRandom().at(0, 0, 0).nextInt();
        return new BasicHeightmapGenerator(this.terrainNoise, seed, BASE_HEIGHT, AMPLITUDE);
    }
}
