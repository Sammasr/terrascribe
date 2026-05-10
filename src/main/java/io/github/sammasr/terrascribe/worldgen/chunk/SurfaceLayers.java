package io.github.sammasr.terrascribe.worldgen.chunk;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Looks up the top and subsurface block states for a given biome.
 *
 * <p>This is M2's stand-in for the full vanilla {@code SurfaceRules} stack. Since
 * {@link TerraScribeChunkGenerator} extends {@code ChunkGenerator} directly and not
 * {@code NoiseBasedChunkGenerator}, the vanilla {@code SurfaceSystem} (which requires a
 * {@code NoiseChunk}) is unavailable to us. We instead use a small, biome-id-based mapper —
 * crude but adequate for M2 and easy to swap for proper {@code SurfaceRules} later.
 *
 * <p>Matching is by biome path substring (e.g., "desert", "beach", "ocean"). For unrecognized
 * biomes we default to grass-on-dirt, which is the right answer for the majority of vanilla
 * and modded biomes that don't fall into one of the special categories below.
 */
public final class SurfaceLayers {

    public record Layer(BlockState top, BlockState subsurface) {}

    private static final Layer GRASS = new Layer(
            Blocks.GRASS_BLOCK.defaultBlockState(),
            Blocks.DIRT.defaultBlockState());
    private static final Layer SAND = new Layer(
            Blocks.SAND.defaultBlockState(),
            Blocks.SAND.defaultBlockState());
    private static final Layer RED_SAND = new Layer(
            Blocks.RED_SAND.defaultBlockState(),
            Blocks.RED_SANDSTONE.defaultBlockState());
    private static final Layer GRAVEL = new Layer(
            Blocks.GRAVEL.defaultBlockState(),
            Blocks.GRAVEL.defaultBlockState());
    private static final Layer SNOW = new Layer(
            Blocks.SNOW_BLOCK.defaultBlockState(),
            Blocks.DIRT.defaultBlockState());
    private static final Layer STONE = new Layer(
            Blocks.STONE.defaultBlockState(),
            Blocks.STONE.defaultBlockState());
    private static final Layer MYCELIUM = new Layer(
            Blocks.MYCELIUM.defaultBlockState(),
            Blocks.DIRT.defaultBlockState());

    private SurfaceLayers() {
        // utility class
    }

    public static Layer forBiome(final Holder<Biome> biome) {
        final Optional<ResourceKey<Biome>> key = biome.unwrapKey();
        if (key.isEmpty()) {
            return GRASS;
        }
        final ResourceLocation id = key.get().location();
        final String path = id.getPath();
        // Order matters: more-specific substrings before more-general ones.
        if (path.contains("badlands")) {
            return RED_SAND;
        }
        if (path.contains("desert") || path.equals("beach") || path.contains("snowy_beach")) {
            return SAND;
        }
        if (path.contains("ocean")) {
            return GRAVEL;
        }
        if (path.startsWith("snowy_") || path.startsWith("frozen_") || path.contains("ice_spikes")
                || path.equals("snowy_slopes") || path.equals("frozen_peaks") || path.equals("jagged_peaks")) {
            return SNOW;
        }
        if (path.equals("stony_peaks") || path.equals("stony_shore") || path.startsWith("windswept_")) {
            return STONE;
        }
        if (path.contains("mushroom")) {
            return MYCELIUM;
        }
        return GRASS;
    }
}
