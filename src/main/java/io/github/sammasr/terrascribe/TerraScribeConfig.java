package io.github.sammasr.terrascribe;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * TerraScribe's common-side config.
 *
 * <p>Loaded by NeoForge from {@code config/terrascribe-common.toml} (auto-created on first
 * run). Currently exposes a single setting: an opt-out blocklist for biomes that should be
 * excluded from biome integration. Specific biomes are matched by full
 * {@code namespace:path} ID; whole mods can be excluded with a {@code namespace:*} wildcard.
 *
 * <p>The config spec must be registered in {@link TerraScribe}'s constructor via
 * {@code ModContainer.registerConfig(...)}. After that, {@link #isBlocked(ResourceLocation)}
 * is safe to call from {@code BiomeSource} construction (config values are loaded before
 * world load).
 */
public final class TerraScribeConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_BLOCKLIST;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        BIOME_BLOCKLIST = builder
                .comment(
                        "Biomes excluded from TerraScribe biome integration.",
                        "Match a specific biome by full ID: \"biomesoplenty:bayou\".",
                        "Match a whole mod with a wildcard: \"someawfulmod:*\".",
                        "Default: empty (include everything tagged c:is_overworld).")
                .defineListAllowEmpty(
                        "biomeBlocklist",
                        List.of(),
                        () -> "minecraft:example",
                        TerraScribeConfig::isValidBlocklistEntry);
        SPEC = builder.build();
    }

    private TerraScribeConfig() {
        // utility class
    }

    /**
     * Returns {@code true} if the given biome ID matches any entry in the configured
     * blocklist. Safe to call before the world loads; if the config hasn't been loaded yet,
     * the underlying list is empty and this returns {@code false} for all inputs.
     */
    public static boolean isBlocked(final ResourceLocation id) {
        final List<? extends String> entries;
        try {
            entries = BIOME_BLOCKLIST.get();
        } catch (final IllegalStateException notLoadedYet) {
            // Config not loaded yet — happens during early registration phases.
            return false;
        }
        if (entries.isEmpty()) {
            return false;
        }
        final String full = id.toString();
        final String wildcard = id.getNamespace() + ":*";
        for (final String entry : entries) {
            if (entry.equals(full) || entry.equals(wildcard)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidBlocklistEntry(final Object obj) {
        return obj instanceof String s && !s.isBlank();
    }
}
