package io.github.sammasr.terrascribe.worldgen.biome;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sammasr.terrascribe.TerraScribeConfig;
import io.github.sammasr.terrascribe.worldgen.biome.climate.Climate;
import io.github.sammasr.terrascribe.worldgen.biome.climate.ClimateSampler;
import io.github.sammasr.terrascribe.worldgen.chunk.TerraScribeChunkGenerator;
import io.github.sammasr.terrascribe.worldgen.noise.FractalNoise;
import io.github.sammasr.terrascribe.worldgen.noise.NoiseField;
import io.github.sammasr.terrascribe.worldgen.noise.SimplexNoise;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate.Sampler;

/**
 * TerraScribe's custom {@link BiomeSource}.
 *
 * <p>Milestone 2: climate-driven biome assignment. A {@link HolderSet}{@code <Biome>} is
 * supplied via the world preset (typically a tag reference like {@code #c:is_overworld} so
 * modded biomes are picked up automatically). At construction the biomes are bucketed by
 * their vanilla climate parameters into a {@link ClimateBucket} map; at lookup time we sample
 * a {@link Climate} for the position and pick a biome from the matching bucket.
 *
 * <p>The {@link ClimateSampler} is currently constructed with a hardcoded seed (0). This
 * makes the climate map identical across all worlds — a deliberate M2 simplification because
 * {@code BiomeSource} doesn't have access to the world seed at construction time, and the
 * obvious workarounds (passing it through the {@link Sampler}, using a positional random)
 * complicate the codec. Climate seeding lands at M3 when we revisit the chunk gen /
 * heightmap / biome source plumbing together.
 */
public final class TerraScribeBiomeSource extends BiomeSource {

    public static final MapCodec<TerraScribeBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(s -> s.allBiomes)
            ).apply(instance, TerraScribeBiomeSource::new));

    private static final int CLIMATE_SEED = 0;
    private static final int VARIANT_SEED = 0x1f8b9c5d;
    private static final int SEA_LEVEL = 63;

    private final HolderSet<Biome> allBiomes;
    private final ClimateSampler climateSampler;
    private final BiomeMapper biomeMapper;
    private final NoiseField biomeVariantNoise;
    /** Matches {@link TerraScribeChunkGenerator}'s terrain noise so elevation decisions align. */
    private final NoiseField heightmapNoise;
    private final Map<ClimateBucket, List<Holder<Biome>>> bucketedLandBiomes;
    private final List<Holder<Biome>> oceanBiomes;
    private final Holder<Biome> fallback;

    public TerraScribeBiomeSource(final HolderSet<Biome> allBiomes) {
        if (allBiomes == null) {
            throw new IllegalArgumentException("biome holder set must not be null");
        }
        final List<Holder<Biome>> snapshot = StreamSupport.stream(allBiomes.spliterator(), false).toList();
        if (snapshot.isEmpty()) {
            throw new IllegalArgumentException(
                    "TerraScribe biome source requires at least one biome — the configured HolderSet was empty"
                            + " (check the #c:is_overworld tag is populated)");
        }

        this.allBiomes = allBiomes;
        this.fallback = snapshot.get(0);

        final NoiseField temperatureNoise = new FractalNoise(new SimplexNoise(), 4, 2f, 0.5f, 0.0008f);
        final NoiseField humidityNoise = new FractalNoise(new SimplexNoise(), 4, 2f, 0.5f, 0.0008f);
        this.climateSampler = new ClimateSampler(temperatureNoise, humidityNoise, CLIMATE_SEED);
        this.biomeMapper = new BiomeMapper();
        // Variant noise picks between biomes within a climate bucket. Low frequency (features
        // ~600 blocks across) so patches are naturally large — the per-quart hash version of
        // this produced 4-block speckle which read as broken to the eye.
        this.biomeVariantNoise = new FractalNoise(new SimplexNoise(), 2, 2f, 0.5f, 0.0015f);
        // Same params as TerraScribeChunkGenerator's terrain noise — must stay in sync.
        this.heightmapNoise = new FractalNoise(
                new SimplexNoise(),
                TerraScribeChunkGenerator.OCTAVES,
                TerraScribeChunkGenerator.LACUNARITY,
                TerraScribeChunkGenerator.GAIN,
                TerraScribeChunkGenerator.FREQUENCY);

        final List<Holder<Biome>> oceans = new ArrayList<>();
        final List<Holder<Biome>> land = new ArrayList<>();
        partitionByOceanLandClassification(snapshot, oceans, land);
        this.oceanBiomes = List.copyOf(oceans);
        this.bucketedLandBiomes = bucketBiomes(land);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return StreamSupport.stream(this.allBiomes.spliterator(), false);
    }

    @Override
    public Holder<Biome> getNoiseBiome(final int xQuart, final int yQuart, final int zQuart, final Sampler sampler) {
        // Climate / heightmap noise are sampled in block coordinates, not quart coordinates.
        final int blockX = QuartPos.toBlock(xQuart);
        final int blockZ = QuartPos.toBlock(zQuart);

        // Approximate column height from the same noise field the chunk generator uses
        // (without erosion — erosion shifts heights by ~5-10 blocks, which is small enough
        // to not matter for the "is this an ocean column?" decision).
        final int worldSeed = TerraScribeChunkGenerator.currentWorldSeed();
        final float heightNoise =
                this.heightmapNoise.sample(blockX, blockZ, worldSeed);
        final int approxHeight =
                TerraScribeChunkGenerator.BASE_HEIGHT + Math.round(heightNoise * TerraScribeChunkGenerator.AMPLITUDE);

        // Variant noise picks a deterministic index within the matched pool. Same noise used
        // for both ocean and land selection.
        final float variant = this.biomeVariantNoise.sample(blockX, blockZ, VARIANT_SEED);
        final float normalized = (variant + 1f) * 0.5f;

        if (approxHeight < SEA_LEVEL) {
            // Ocean column: pick from the ocean pools (own + modded).
            final List<Holder<Biome>> moddedOceans = ModdedBiomeRegistry.oceanBiomes();
            return pickFromPools(this.oceanBiomes, moddedOceans, normalized);
        }

        // Land column: bucket by climate and pick from the matching land pool.
        final Climate climate = this.climateSampler.sample(blockX, blockZ);
        final ClimateBucket bucket = this.biomeMapper.bucketFor(climate);
        final List<Holder<Biome>> ownLand = this.bucketedLandBiomes.get(bucket);
        final List<Holder<Biome>> moddedLand = ModdedBiomeRegistry.bucketedLandBiomes().getOrDefault(bucket, List.of());
        return pickFromPools(ownLand, moddedLand, normalized);
    }

    private Holder<Biome> pickFromPools(
            final List<Holder<Biome>> ownPool,
            final List<Holder<Biome>> moddedPool,
            final float normalizedVariant) {
        final int ownSize = ownPool == null ? 0 : ownPool.size();
        final int moddedSize = moddedPool == null ? 0 : moddedPool.size();
        final int total = ownSize + moddedSize;
        if (total == 0) {
            return this.fallback;
        }
        int index = (int) (normalizedVariant * total);
        if (index >= total) {
            index = total - 1;
        }
        return index < ownSize ? ownPool.get(index) : moddedPool.get(index - ownSize);
    }

    private static Map<ClimateBucket, List<Holder<Biome>>> bucketBiomes(final List<Holder<Biome>> biomes) {
        final Map<ClimateBucket, List<Holder<Biome>>> map = new HashMap<>();
        for (final Holder<Biome> holder : biomes) {
            // Honor the user's blocklist — skip any biome whose ID matches the configured
            // blocklist or a namespace wildcard. Biomes without a registry key are pseudo-
            // anonymous and we let them through.
            if (holder.unwrapKey().map(k -> TerraScribeConfig.isBlocked(k.location())).orElse(false)) {
                continue;
            }
            final ClimateBucket bucket = classifyVanillaBiome(holder.value());
            map.computeIfAbsent(bucket, k -> new ArrayList<>()).add(holder);
        }
        return Map.copyOf(map);
    }

    /**
     * Splits the given biome holders into "ocean" and "land" lists. The classifier is path-
     * based: anything whose registry path contains {@code "ocean"} goes in the ocean pool;
     * everything else is treated as land. Blocklisted biomes are skipped from both pools.
     * Public so {@link ModdedBiomeRegistry} can apply identical rules to the biomes it
     * discovers at server start.
     */
    public static void partitionByOceanLandClassification(
            final List<Holder<Biome>> biomes,
            final List<Holder<Biome>> oceansOut,
            final List<Holder<Biome>> landOut) {
        for (final Holder<Biome> holder : biomes) {
            if (holder.unwrapKey().map(k -> TerraScribeConfig.isBlocked(k.location())).orElse(false)) {
                continue;
            }
            if (isOcean(holder)) {
                oceansOut.add(holder);
            } else {
                landOut.add(holder);
            }
        }
    }

    public static boolean isOcean(final Holder<Biome> holder) {
        return holder.unwrapKey().map(k -> k.location().getPath().contains("ocean")).orElse(false);
    }

    /**
     * Bucket a vanilla {@link Biome} by its declared temperature and downfall. Maps vanilla
     * conventions {@code (temperature in roughly [-0.5, 2.0], downfall in [0, 1])} to our
     * {@code [-1, 1]} climate axes.
     *
     * <p>Public so {@code ModdedBiomeRegistry} can apply the same bucketing rules to biomes
     * it discovers at server start. Pure function over {@link Biome.ClimateSettings}.
     */
    public static ClimateBucket classifyVanillaBiome(final Biome biome) {
        final Biome.ClimateSettings settings = biome.getModifiedClimateSettings();
        // Vanilla temperature center is ~0.75 (plains). Shift by -0.75 then clamp to [-1, 1].
        final float vanillaTemp = settings.temperature();
        final float normalizedTemp = clamp(vanillaTemp - 0.75f, -1f, 1f);
        // Vanilla downfall is [0, 1]; rescale to [-1, 1].
        final float vanillaDownfall = settings.downfall();
        final float normalizedHumidity = clamp(vanillaDownfall * 2f - 1f, -1f, 1f);
        return new ClimateBucket(
                BiomeMapper.coolnessOf(normalizedTemp),
                BiomeMapper.wetnessOf(normalizedHumidity));
    }

    private static float clamp(final float value, final float min, final float max) {
        return value < min ? min : (value > max ? max : value);
    }
}
