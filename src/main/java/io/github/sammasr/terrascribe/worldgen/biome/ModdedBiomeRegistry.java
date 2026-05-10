package io.github.sammasr.terrascribe.worldgen.biome;

import com.mojang.logging.LogUtils;
import io.github.sammasr.terrascribe.TerraScribeConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

/**
 * Side-channel registry of biomes discovered at server-start time and made available to
 * {@link TerraScribeBiomeSource} as additional pool entries.
 *
 * <p>Why a side-channel: tag references in world-preset codecs do NOT resolve when the
 * preset is deserialized (biome tags load *after* world presets). So the BiomeSource's
 * own {@code biomes} codec field must be an explicit list of vanilla biomes. To still hit
 * the "modded biomes auto-discovered" requirement in spec §14, we listen for
 * {@link ServerAboutToStartEvent} — which fires after datapack tags ARE loaded — scan the
 * biome registry for anything tagged {@code minecraft:is_overworld}, classify it by the
 * same rules as our codec biomes, and expose the bucketed result as a static map that the
 * BiomeSource consults at every {@code getNoiseBiome} call.
 *
 * <p>Modded biomes that self-tag {@code minecraft:is_overworld} (the universal Minecraft
 * convention) auto-integrate. Biomes in the user's {@link TerraScribeConfig#BIOME_BLOCKLIST}
 * are skipped.
 *
 * <p>The static map is reset and rebuilt on every server start, so a server reload picks up
 * any datapack changes that touched the overworld tag.
 */
public final class ModdedBiomeRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TagKey<Biome> IS_OVERWORLD = TagKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath("minecraft", "is_overworld"));

    private static volatile Map<ClimateBucket, List<Holder<Biome>>> bucketed = Map.of();

    private ModdedBiomeRegistry() {
        // utility class
    }

    public static Map<ClimateBucket, List<Holder<Biome>>> bucketedBiomes() {
        return bucketed;
    }

    public static void onServerAboutToStart(final ServerAboutToStartEvent event) {
        final Registry<Biome> biomeRegistry = event.getServer().registryAccess().registryOrThrow(Registries.BIOME);
        final HolderSet.Named<Biome> overworldBiomes = biomeRegistry.getOrCreateTag(IS_OVERWORLD);

        final Map<ClimateBucket, List<Holder<Biome>>> buckets = new HashMap<>();
        int total = 0;
        int blocked = 0;
        for (final Holder<Biome> holder : overworldBiomes) {
            final ResourceLocation id = holder.unwrapKey().map(k -> k.location()).orElse(null);
            if (id != null && TerraScribeConfig.isBlocked(id)) {
                blocked++;
                continue;
            }
            final ClimateBucket bucket = TerraScribeBiomeSource.classifyVanillaBiome(holder.value());
            buckets.computeIfAbsent(bucket, k -> new ArrayList<>()).add(holder);
            total++;
        }
        bucketed = Map.copyOf(buckets);
        LOGGER.info(
                "[TerraScribe] biome discovery: {} overworld biomes across {} climate buckets ({} blocked by config)",
                total, buckets.size(), blocked);
    }
}
