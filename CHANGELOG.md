# Changelog

All notable changes to TerraScribe are recorded here.

This project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added — Milestone 3 (Erosion)

- `ErosionSimulator` (pure math, `worldgen.terrain`) — Lague droplet hydraulic erosion. Configurable inertia, sediment capacity, erosion/deposit rates, gravity, evaporation. 8 JUnit tests.
- `RegionHeightmap` record + `RegionCache` LRU (pure math, `worldgen.chunk`) — 256×256 region with configurable max-entry cap. 6 JUnit tests covering LRU eviction, build-once semantics, block-coord routing.
- `TerraScribeChunkGenerator` rewired — heightmap queries now go through `RegionCache`. On region miss, base noise heightmap is generated, hydraulic erosion is applied with 25k droplets at 30 steps, result is cached. Per-region erosion seed mixes world seed + region coords so adjacent regions don't share droplet starting points (avoids visible seams).

### Fixed

- Within-bucket biome picker switched from per-quart positionHash to low-frequency variant noise (FractalNoise, freq 0.0015). Without this, every 4×4-block area inside a climate bucket re-picked a different biome, producing visible 4-block speckle. Patches are now ~500 blocks across with organic boundaries.

### Added — Milestone 2 (Surface + Biomes)

- `Climate(float temperature, float humidity)` record + `ClimateSampler` (pure math, `worldgen.biome.climate`) — two-channel noise plus a `sin(z * f) * s` latitude bias for climate bands. 7 JUnit tests.
- `ClimateBucket(Coolness, Wetness)` record + `BiomeMapper` decision matrix (pure math, `worldgen.biome`) — climate → discrete bucket. 4 JUnit tests.
- `TerraScribeBiomeSource` rewritten — codec field is now a `HolderSet<Biome>` (explicit list of vanilla overworld biomes); buckets biomes by their vanilla `temperature` + `downfall`; picks deterministically per quart-coord.
- `ModdedBiomeRegistry` — listens to `ServerAboutToStartEvent`, scans the biome registry for everything tagged `minecraft:is_overworld`, buckets it by the same rules, exposes as a side-channel pool. World preset codec deserialization sees tags as empty (Mojang's tag/preset load order), so this side-channel is the only way to autopick up modded biomes — and it does, automatically, no config required for well-behaved mods.
- `TerraScribeConfig` — `config/terrascribe-common.toml` with `biomeBlocklist` setting (full IDs or `namespace:*` wildcards).
- `SurfaceLayers` — biome-ID-based top/subsurface block mapper (grass/dirt by default; sand on desert/beach, red sand on badlands, gravel on ocean, snow on cold biomes, stone on stony peaks/windswept, mycelium on mushroom).
- `TerraScribeChunkGenerator.buildSurface` implemented — paints top + 3 subsurface blocks per column based on biome.
- World preset JSON updated to list ~47 vanilla overworld biomes explicitly (tags don't resolve at preset-codec time).

### Added — Milestone 1 (custom ChunkGenerator)

- Pure-math noise stack at `worldgen.noise`: `NoiseField` interface, vendored 2D `SimplexNoise` (Stefan Gustavson algorithm), normalized fBm `FractalNoise`. Zero Minecraft API references; JUnit-tested (9 tests).
- Pure-math terrain layer at `worldgen.terrain`: `Heightmap` functional interface + `BasicHeightmapGenerator`. JUnit-tested (7 tests).
- `TerraScribeBiomeSource` (extends `BiomeSource`) — single-biome placeholder returning `minecraft:plains`. Codec via `RecordCodecBuilder`.
- `TerraScribeChunkGenerator` (extends `ChunkGenerator` directly, not `NoiseBasedChunkGenerator` — see `CLAUDE.md` for the design-decision rationale). Fills stone below the heightmap, water up to sea level 63, air above, with bedrock at y=-64.
- `ModWorldgen` registry holder wiring `DeferredRegister`s for `CHUNK_GENERATOR` and `BIOME_SOURCE` codec types under `terrascribe:terrascribe`.
- World-preset datapack file at `data/terrascribe/worldgen/world_preset/terrascribe.json` — custom overworld dimension, vanilla nether/end.
- Tag entry `data/minecraft/tags/worldgen/world_preset/normal.json` adds TerraScribe to the "Normal" world-type dropdown.
- Lang key `generator.terrascribe.terrascribe` → "TerraScribe".
- JUnit 5 testing infrastructure wired into `build.gradle` (5.11.0 + platform launcher). `mavenCentral()` added to repositories.
- `docs/PLAYTEST.md` extended with the Milestone 1 checklist.

### Added — Milestone 0 (bootstrap)

- Initial NeoForge MDK skeleton for Minecraft 1.21.1.
- Main mod class `io.github.sammasr.terrascribe.TerraScribe` emitting a startup log line.
- Pinned versions: NeoForge 21.1.228, ModDevGradle 2.0.141, Parchment `1.21.1 / 2024.11.17`, Java 21 (Temurin).
- Repository scaffolding: `LICENSE` (MIT), `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `CLAUDE.md` operating manual, `docs/SPEC.md`.

## [0.0.1-alpha] — 2026-05-10

Initial tag — Milestone 0 skeleton.
