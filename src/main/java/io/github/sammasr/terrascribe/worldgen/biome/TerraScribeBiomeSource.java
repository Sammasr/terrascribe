package io.github.sammasr.terrascribe.worldgen.biome;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * TerraScribe's custom {@link BiomeSource}.
 *
 * <p>Milestone 1 placeholder: returns a single configured biome for every {@code (x, y, z)}.
 * Behaves like vanilla's {@link net.minecraft.world.level.biome.FixedBiomeSource} but is a
 * distinct codec-registered type so it carries a {@code terrascribe:terrascribe} type ID and
 * can be referenced from world-preset JSON. Climate-driven biome assignment with modded-biome
 * discovery lands in Milestone 2.
 */
public final class TerraScribeBiomeSource extends BiomeSource {

    public static final MapCodec<TerraScribeBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Biome.CODEC.fieldOf("biome").forGetter(source -> source.biome)
            ).apply(instance, TerraScribeBiomeSource::new));

    private final Holder<Biome> biome;

    public TerraScribeBiomeSource(final Holder<Biome> biome) {
        this.biome = biome;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(this.biome);
    }

    @Override
    public Holder<Biome> getNoiseBiome(final int x, final int y, final int z, final Climate.Sampler sampler) {
        return this.biome;
    }
}
