# Changelog

All notable changes to TerraScribe are recorded here.

This project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
