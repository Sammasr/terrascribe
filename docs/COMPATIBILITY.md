# TerraScribe — Modded biome compatibility

Compatibility results for popular biome and worldgen mods. Updated each milestone.

## How TerraScribe integrates modded biomes

At server start, `ModdedBiomeRegistry.onServerAboutToStart` scans the biome registry for every biome holder tagged `minecraft:is_overworld`. Each is classified into a 2D climate bucket (`Coolness` × `Wetness`) using its vanilla `temperature` and `downfall` values, and added to the pool of candidates TerraScribe's `BiomeSource` picks from at each chunk column.

**For a biome to be auto-discovered, it must:**

1. Be tagged `minecraft:is_overworld` (the universal Minecraft tag).
2. Have meaningful values for `temperature` (typically 0.0–2.0) and `downfall` (0.0–1.0) on its `Biome.ClimateSettings`.

Mods that follow standard NeoForge biome conventions hit both requirements automatically and need no special handling.

## Excluding biomes

If a discovered biome generates unwanted terrain (e.g., it places its own custom blocks via a `BiomeModifier` that conflict with TerraScribe surface rules), exclude it via `config/terrascribe-common.toml`:

```toml
biomeBlocklist = [
    "biomesoplenty:bayou",       # specific biome
    "someawfulmod:*"              # all biomes from that mod
]
```

The blocklist applies at server start; reload the world after edits.

## Compatibility table

| Mod | Version tested | MC | Result | Notes |
|---|---|---|---|---|
| (vanilla baseline) | MC 1.21.1 | 1.21.1 | Pass | 53 biomes auto-discovered in 9 climate buckets at M2 |

| _Pending milestone:_ ||||
| Biomes O' Plenty | TBD | TBD | TBD | Spec §8 (M8 polish target) |
| Terralith | TBD | TBD | TBD | Spec §8 |
| Oh The Biomes You'll Go | TBD | TBD | TBD | Spec §8 |

## Reporting compatibility issues

When reporting a mod that doesn't integrate cleanly, include:

- Mod name and version.
- A screenshot of the biome that looks wrong (F3 biome name visible).
- The relevant section of the server/client log around world load (look for `ModdedBiomeRegistry` and `TerraScribe` lines).
- The biome's ID — `gh issue create --title "compat: ..."` is fine.
