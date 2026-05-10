package io.github.sammasr.terrascribe.registry;

import com.mojang.serialization.MapCodec;
import io.github.sammasr.terrascribe.TerraScribe;
import io.github.sammasr.terrascribe.worldgen.biome.TerraScribeBiomeSource;
import io.github.sammasr.terrascribe.worldgen.chunk.TerraScribeChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers TerraScribe's worldgen codec types so they can be referenced from data files.
 *
 * <p>The {@code ChunkGenerator} and {@code BiomeSource} systems are codec-dispatched: the
 * {@code type} field in a world-preset JSON looks up which {@link MapCodec} to deserialize
 * with. Both of ours are registered as {@code terrascribe:terrascribe}.
 */
public final class ModWorldgen {

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, TerraScribe.MODID);

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, TerraScribe.MODID);

    public static final java.util.function.Supplier<MapCodec<TerraScribeChunkGenerator>> TERRASCRIBE_CHUNK_GENERATOR =
            CHUNK_GENERATORS.register("terrascribe", () -> TerraScribeChunkGenerator.CODEC);

    public static final java.util.function.Supplier<MapCodec<TerraScribeBiomeSource>> TERRASCRIBE_BIOME_SOURCE =
            BIOME_SOURCES.register("terrascribe", () -> TerraScribeBiomeSource.CODEC);

    private ModWorldgen() {
        // utility class; do not instantiate
    }

    public static void register(final IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
        BIOME_SOURCES.register(modEventBus);
    }
}
