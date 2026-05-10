package io.github.sammasr.terrascribe;

import com.mojang.logging.LogUtils;
import io.github.sammasr.terrascribe.registry.ModWorldgen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Main entry point for the TerraScribe mod.
 *
 * <p>A TerraForged-style terrain generation overhaul for Minecraft 1.21.1. This class is
 * the FML-discovered {@code @Mod} entry point: registration of worldgen content, biome
 * sources, chunk generators, and event listeners hangs off the constructor.
 */
@Mod(TerraScribe.MODID)
public final class TerraScribe {

    /** Mod identifier. Must match {@code modId} in {@code META-INF/neoforge.mods.toml}. */
    public static final String MODID = "terrascribe";

    private static final Logger LOGGER = LogUtils.getLogger();

    public TerraScribe(final IEventBus modEventBus, final ModContainer modContainer) {
        ModWorldgen.register(modEventBus);
        LOGGER.info("[TerraScribe] mod constructor invoked — chunk generator + biome source codecs registered");
    }
}
