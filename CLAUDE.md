# CLAUDE.md — TerraScribe operating manual

> This is Claude Code's live working notes for this repo. Read this first at every session start. Update at every session boundary (Section 18 of `docs/SPEC.md`).

## Project summary

TerraScribe is a TerraForged-style terrain generation mod for Minecraft 1.21.1 on NeoForge, built from scratch as a study project. The collaborating human is **new to Java and Minecraft modding** — they approve commands, run the test client, and make subjective design calls; they do not read code or debug stack traces. Be the senior engineer: justify decisions in plain English, push back when the spec is wrong, never dump raw logs.

The full specification is canonical: [`docs/SPEC.md`](docs/SPEC.md). When the spec and your instincts disagree, follow the spec but flag the conflict.

## Current milestone

**Milestone 4 — Rivers + Lakes.** Flow-based rivers carve through terrain; lakes sit at sinks.

See `docs/SPEC.md` §9 for the full milestone table.

### Milestone 4 — Definition of Done (draft — refine at session start)

- [ ] `FlowField` (pure math) — downhill gradient accumulation across an eroded heightmap; output is per-cell "flow received from upstream".
- [ ] `RiverCarver` (pure math) — walks the flow field, carves a channel along high-flow paths, gradient-shape based on accumulated flow.
- [ ] `LakeFinder` (pure math) — locates closed depressions (sinks) where flow accumulates with no outlet → place water blocks.
- [ ] Integrate into `RegionCache` builder: after erosion, compute flow, carve rivers, mark lake cells.
- [ ] River cells produce water blocks in `fillFromNoise`.
- [ ] Lake cells: water-filled depressions, surface blocks below water swap to sand/gravel.
- [ ] Cross-region river continuity: rivers shouldn't snap discontinuously at region boundaries. Achieved either via region padding (preferred) or post-pass smoothing along seams.
- [ ] Unit tests: flow accumulation conserved, river paths reach low ground / map edge, lake cells form closed pools.
- [ ] `docs/PLAYTEST.md` M4 checklist.
- [ ] CHANGELOG.

### Milestone 3 — Erosion (PASSED-pending visual verification 2026-05-10)

- [x] `ErosionSimulator` — Lague droplet hydraulic erosion. Inertia 0.05, capacity factor 4, erosion/deposit rates 0.3, gravity 4, 1% evaporation. 25k droplets × 30 steps for our 256-side region. 8 JUnit tests.
- [x] `RegionCache` — 256-entry LRU with 256×256 float regions (~64 MB cap, slightly over spec's ~32 MB). 6 JUnit tests.
- [x] `TerraScribeChunkGenerator` heightmap path replaced — lazy `RegionCache` keyed by world seed (derived from `RandomState.aquiferRandom().at(0,0,0).nextInt()`); builder generates base noise + applies erosion with a per-region scrambled seed.
- [x] `runServer` smoke test: server Done in 1.795 s (vs 1.486 s baseline). +~300 ms for erosion of the spawn region. Zero errors.
- [ ] Visual playtest pass — pending.
- [x] `docs/PERF.md` first cut.
- [x] `docs/PLAYTEST.md` M3 section appended.
- [ ] `docs/ARCHITECTURE.md` — still owed (slipping to M4).
- [x] CHANGELOG entries.

### Milestone 2 — Surface + Biomes (PASSED 2026-05-10)

- [x] `ClimateSampler` (pure math, `worldgen.biome.climate`) — `(x, z) → Climate(temperature, humidity)`. Two-channel noise plus a `sin(z * f) * s` latitude bias for climate bands. 7 tests.
- [x] `BiomeMapper` decision matrix (`worldgen.biome`) — pure math, `Climate → ClimateBucket(Coolness, Wetness)`. Elevation axis deliberately deferred to M3+. 4 tests.
- [x] `TerraScribeBiomeSource` rewritten — codec field is `HolderSet<Biome>` (explicit list); buckets biomes by vanilla `temperature`+`downfall`; picks deterministically per quart-coord.
- [x] `ModdedBiomeRegistry` — `ServerAboutToStartEvent` listener that scans the biome registry for `minecraft:is_overworld`-tagged entries, classifies them, and feeds them into the BiomeSource lookup pool via a static map. Sidesteps the Mojang tag/preset load-order issue (tag refs in preset codecs resolve as empty).
- [x] `TerraScribeConfig` — `config/terrascribe-common.toml` with `biomeBlocklist` (full IDs or `namespace:*` wildcards).
- [x] `SurfaceLayers` biome-ID-based top/sub block mapper + `TerraScribeChunkGenerator.buildSurface` implemented. No vanilla `SurfaceRules` (would require a `NoiseChunk` and switching to `NoiseBasedChunkGenerator`).
- [x] `runServer` smoke test: 53 overworld biomes discovered across 9 climate buckets, server Done in 1.735 s, zero errors.
- [x] User-confirmed visual playtest pass — "looks better" after the variant-noise patch-size fix (`ba802e1`).
- [ ] ~~GameTest~~ — deferred again; design slipping toward M4 when we have rivers worth asserting.
- [x] `docs/PLAYTEST.md` M2 section appended.
- [x] `docs/COMPATIBILITY.md` first cut.
- [ ] `docs/ARCHITECTURE.md` — still owed from M1, deferred to M3.
- [x] CHANGELOG entries.

## M2 design decisions (locked at session start)

### Milestone 0 — Bootstrap (PASSED 2026-05-10)

- [x] NeoForge MDK 1.21.1 skeleton extracted and re-packaged as `io.github.sammasr.terrascribe`.
- [x] Mod class `TerraScribe.java` written, emits `[TerraScribe] mod constructor invoked …` on startup.
- [x] Pinned versions: NeoForge `21.1.228`, ModDevGradle `2.0.141`, Parchment `1.21.1 / 2024.11.17`, Java Temurin `21.0.11`.
- [x] Reference repos cloned into `references/` (gitignored).
- [x] `LICENSE` (MIT, crediting TerraForged + ReTerraForged), `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `docs/SPEC.md` mirror.
- [x] `./gradlew build` green (3m 46s first run).
- [x] `./gradlew runClient` launches MC 1.21.1, mod appears in mod list, `[TerraScribe]` log line present in modloading-worker. Zero terrascribe-related errors. World creation + save + shutdown clean.
- [x] `git init`, `gh repo create terrascribe`, pushed to <https://github.com/Sammasr/terrascribe>, tag `v0.0.1-alpha` pushed.
- [x] CI `build.yml` + `release.yml` workflows committed and triggering on push/tag.
- [x] User confirmed "M0 pass" 2026-05-10.

### Milestone 1 — Custom ChunkGenerator (PASSED 2026-05-10)

- [x] Noise stack: `NoiseField` interface + `SimplexNoise` (2D, vendored) + `FractalNoise` (octave-summed fBm). Pure JVM, zero MC imports. 9 JUnit tests green.
- [x] `Heightmap` (functional interface `int heightAt(int x, int z)`) + `BasicHeightmapGenerator` (noise → height). Pure math, 7 JUnit tests green.
- [x] `TerraScribeBiomeSource extends BiomeSource` — placeholder, returns `minecraft:plains` for all (x, y, z). Codec via `RecordCodecBuilder`.
- [x] `TerraScribeChunkGenerator extends ChunkGenerator`. Fills stone below heightmap, water at sea level 63 when height < 63, air above, bedrock at y=-64.
- [x] DeferredRegisters wired for `BIOME_SOURCE` and `CHUNK_GENERATOR` codec registries. Both registered as `terrascribe:terrascribe`.
- [x] `data/terrascribe/worldgen/world_preset/terrascribe.json` + tag entry adding to `minecraft:normal` so "TerraScribe" appears in create-world dropdown. Lang entry `generator.terrascribe.terrascribe` → "TerraScribe".
- [x] JUnit 5 wired up in `build.gradle` (5.11.0).
- [ ] ~~GameTest: load TerraScribe world without errors~~ — deferred to M2 per design note in CLAUDE.md M1 decisions.
- [x] `docs/PLAYTEST.md` appended with M1 checklist.
- [ ] `docs/ARCHITECTURE.md` first cut — also deferred to M2 (only 4 packages so far; not worth a doc yet).
- [x] CHANGELOG `[Unreleased]` entries for each substantive piece.
- [x] Commit + push (`78a363d`). `./gradlew runServer` headless smoke test generated chunks in 892 ms with zero errors. `./gradlew runClient` end-to-end playtest verified visually by user.

## Reference-study notes for Milestone 1

Two Explore agents digested `references/TerraForged/` and `references/ReTerraForged/`. Both repos use the **same architectural shortcut** that our spec explicitly rejects: they do NOT subclass `ChunkGenerator` or `BiomeSource`; they mixin into vanilla `NoiseBasedChunkGenerator` and inject custom density functions for climate values. Their senior-engineer assessment of *that* approach: "for 1.21.1, use proper WorldPreset registration and DimensionGeneratorSettings codecs… write a true custom ChunkGenerator if you're starting fresh on NeoForge." That matches our spec §8. We do the proper-subclass thing.

**What we ARE replicating:**
- Pure-math noise layer with zero MC imports (`raccoonman.reterraforged.world.worldgen.noise.module.*` has ~40 noise operators implementing a `Noise compute(x,z,seed)` interface — exactly the pattern we want for `worldgen.noise.*`).
- A `Cell`-style value object that carries per-location data (height, temperature, humidity, terrain type) through the pipeline. Cleaner than passing 5 floats around.
- `RecordCodecBuilder` for record-style codecs; dispatched codecs (`byNameCodec().dispatch(...)`) for variant types when we add `TerrainType` later.
- Tile-based caching of heightmap data per region. (Deferred to M3 when erosion makes recomputation expensive.)

**What we are NOT taking:**
- No mixins. NeoForge has clean API extension points; we use those.
- No "density-function-as-glue" hack to drive vanilla biome placement. We subclass `BiomeSource` cleanly.
- No patching `NoiseGeneratorSettings` at runtime. We register a real `WorldPreset` JSON.

## M2 reference notes (session 3, 2026-05-10)

Two more Explore agents on TerraForged + ReTerraForged. Both landed on similar M2-relevant takeaways:

- **Climate is noise + latitude.** `BiomeNoise.java` does two-stage noise (region + local) plus a sine-based latitude bias plus a height falloff term so mountains are colder. We adopt the simple shape: independent temperature + humidity FractalNoise fields plus a `sin(z * f) * strength` latitude term.
- **Biome assignment is a 2D lookup table.** `BiomeType.java` indexes by `(temperature_bucket, humidity_bucket)` with O(1) lookup and a curve that caps humidity by temperature. We do a simpler bucket grid (`Coolness × Wetness × Elevation`) but the lookup-table shape is right.
- **Surface rules: RTF builds custom `SurfaceRules.RuleSource` types** (Strata, Layered, Noise) and registers them in the codec registry. **Premature for us at M2** — vanilla `SurfaceRules` requires a `NoiseChunk` which requires a `NoiseRouter` which requires `NoiseGeneratorSettings`. Since we don't extend `NoiseBasedChunkGenerator`, we sidestep the whole stack and write a simple per-column surface layer in `buildSurface` that just rewrites top blocks based on the biome at each column. Full SurfaceRules can land at M3/M4 when we revisit.
- **Cell value object** — RTF threads a 23-field mutable `Cell` through the pipeline. For M2 we use a `Climate(float temperature, float humidity)` record only; we don't need the full Cell yet.
- **Modded biome integration — RTF doesn't do it dynamically.** RTF tags vanilla biomes statically in `PresetBiomeTagsProvider` at data-gen time. We do better: our biome source's codec field is a `HolderSet<Biome>` referencing a tag (`#c:is_overworld`), so any biome from any mod that self-tags `c:is_overworld` automatically participates. NeoForge's biome tag convention covers this for well-behaved mods. Misbehaving mods can be added explicitly via config tag entry; user-blocked biomes via the blocklist config.

## M2 design decisions (locked at session start)

| Decision | Rationale |
|---|---|
| Still NOT extending `NoiseBasedChunkGenerator`. | M2 introduces `buildSurface`, which in vanilla pipeline requires a `NoiseChunk` (needing `NoiseRouter` / `NoiseGeneratorSettings`). We instead implement a simple per-column surface rewrite in `buildSurface`: read biome → pick top block → place top + a few subsurface blocks. Sidesteps the whole vanilla NoiseChunk machinery. SurfaceRules-proper revisit at M3/M4. |
| Biome source codec field: `HolderSet<Biome>` referencing the `c:is_overworld` tag. | NeoForge convention. Modded biomes self-tagging get automatic inclusion — no `ServerAboutToStartEvent` registry walk needed. Skips a class of fragility (mod load order, missing registry sync). |
| Climate buckets: 3 temperature × 3 humidity = 9 buckets (Elevation axis dropped during implementation). | Elevation in the bucket would require height info that `BiomeSource.getNoiseBiome` doesn't carry; threading it through is more plumbing than M2 needs. Elevation-driven biome selection waits for M3 when erosion needs a shared heightmap anyway. |
| Bucketing modded biomes by their vanilla `temperature` and `downfall`. | These are the only universally-available climate values on `Biome`. Spec §14 explicitly accepts heuristics for biomes without explicit climate data. |
| Config TOML for blocklist via `ModConfigSpec`. | Per-spec §14. Single setting: list of biome resource locations (or `namespace:*` wildcards) to exclude. |
| Tag references in world-preset codec are empty at deserialization time — we use an explicit ~47-biome vanilla list in the preset JSON and a `ServerAboutToStartEvent`-driven side-channel for runtime discovery. | Discovered during M2 implementation: Mojang's load order resolves tags AFTER world presets, so `#minecraft:is_overworld` decodes as an empty `HolderSet`. The side-channel approach is spec §14's intent anyway; the explicit list just keeps the preset codec working without depending on the discovery to have run. |
| No vanilla `SurfaceRules` at M2; per-column biome-id-based top/sub mapper in `buildSurface` instead. | Vanilla `SurfaceSystem` needs a `NoiseChunk` built from a `NoiseRouter`, which means `NoiseBasedChunkGenerator`. Sidestep the whole stack: directly read biome and write top + 3 subsurface blocks. We can swap in real `SurfaceRules` at M3/M4 if it pays for itself. |

## M1 design decisions (locked at session start)

| Decision | Rationale |
|---|---|
| Extend `ChunkGenerator` directly, NOT `NoiseBasedChunkGenerator`. | Spec §8 says `NoiseBasedChunkGenerator`, but at M1 a direct subclass is much simpler — no `NoiseGeneratorSettings` JSON, no `NoiseRouter` density function plumbing, no entanglement with vanilla cave / aquifer code we're not ready to wire to. We override the few abstract methods we actually need (`fillFromNoise`, `getBaseHeight`, `getBaseColumn`, sea level, gen depth) and stub the rest. Will revisit at M3 (erosion) or M4 (rivers) once integration with vanilla worldgen actually starts paying off. **Deviation from spec — flagged here.** |
| `TerraScribeBiomeSource` returns `minecraft:plains` everywhere at M1. | Per spec §9, biomes land in M2 ("Surface + Biomes"). For M1 we need *something* that compiles; the placeholder isolates "world type registers correctly" from "biome assignment works correctly." |
| WorldPreset registered via data-pack JSON (`data/terrascribe/worldgen/world_preset/`). | Modern Minecraft (1.21+) world types in the create-world dropdown come from the `WorldPreset` registry. Datapack JSON is the canonical Mojang pattern. Avoids the registry-patching kludge ReTerraForged uses on 1.20.2. |
| No GameTest at M1 — defer to M2. | Setting up the GameTest harness for "world loads without errors" is meaningful infrastructure work. M1 is already a chunky milestone; let it land as runClient-verified for now, write the GameTest at M2 when we have actual biome assignment to assert against. |
| Stone-only for M1, no surface rules. | Spec calls this out — "stone-only" is part of M1's DoD. Dirt + grass on top is M2 (surface rules + biomes). |

## How to run the game

```bash
source ~/.sdkman/bin/sdkman-init.sh   # if Java 21 is not already on PATH
./gradlew build                       # compile + tests
./gradlew runClient                   # launch dev Minecraft client
./gradlew runServer                   # launch dev dedicated server
./gradlew runGameTestServer           # run GameTest suite (no tests yet at M0)
./gradlew runData                     # data generators (no generators yet at M0)
```

## How to test

- **Unit tests** (when they exist): `src/test/java/...`, run via `./gradlew test`.
- **GameTests**: run via `./gradlew runGameTestServer`. Spec §11.2.
- **Manual playtest checklist**: `docs/PLAYTEST.md` (created at end of each milestone).

## Where to look for reference

- `references/TerraForged/` — Thomas Holmes (won_ton_), branch `1.18`, MIT. Primary architectural reference.
- `references/ReTerraForged/` — racoonman2, MIT. Modern port — shows how the original adapts to newer worldgen APIs.

Both are gitignored, read-only study material. **Do not copy code verbatim** — write our own. Credit in commit messages when ideas originate there (`feat: ... (inspired by TerraForged/won_ton_)`).

## Active design decisions

| Decision | Rationale |
|---|---|
| Project root is `/home/sammasr/dev/Terrascribe`, not `~/projects/terrascribe`. | Deviation from spec §6 — user-confirmed 2026-05-10. |
| Use MDK branch `archive/1.21-mdg` as skeleton. | No `1.21.1` branch exists; the `-mdg` (ModDevGradle) variant of the 1.21 branch already targets MC 1.21.1 in its `gradle.properties`. Spec explicitly prefers ModDevGradle over deprecated NeoGradle. |
| Strip the MDK's `ExampleMod`/`Config`/example block/item/tab/lang entries entirely. | They demonstrate Item/Block registries we won't use, would collide with real worldgen registries in later milestones, and add cruft to the diff. |
| Mod class is a single slim `TerraScribe.java` with just MODID + LOGGER. | Milestone 0's only job is to prove the mod loads. Real wiring (registries, world-type, biome source) lands in Milestone 1. |
| Pin NeoForge at `21.1.228`. | Latest stable for MC 1.21.1 as of 2026-05-10 (queried from `maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`). |
| `.gitignore` keeps `.idea/runConfigurations/` selectively. | Spec §6.3 — IntelliJ run configs generated by `./gradlew :runClient :runServer :runData` should ride along. |

## Known issues / TODOs

- `sudo` is not cached in Claude's shell, so `apt install gh` and `snap install intellij-idea-community` cannot be run autonomously. User needs to install `gh` via `! sudo apt install -y gh` (small, fast) before we can run `gh repo create`. IntelliJ is optional for the build pipeline — defer until/unless user wants it.
- Modrinth and CurseForge API tokens not set. `release.yml` will reference them as repo secrets with TODO comments; the user adds them via GitHub Settings → Secrets when ready to publish.
- Performance baseline (`docs/PERF.md`) not yet established. Will measure vanilla chunk-gen time at the start of Milestone 8.

## Operating principles (cheat sheet)

Pulled from `docs/SPEC.md` §18:

1. Senior engineer mindset — push back on the spec when wrong; never deviate silently.
2. **Autonomy override (user-set 2026-05-10):** only ask for permission when literally blocked (sudo password, interactive auth, milestone gate, unauthorized destructive op). Otherwise act and report status briefly.
3. Educate as you go — 2-4 sentence designer's summary after non-trivial changes.
4. `TaskCreate` religiously, one logical change per commit, Conventional Commits format.
5. Self-validate with `./gradlew build test` after every change set.
6. **Milestone boundaries are hard stops.** Post the playtest checklist and wait for the user's "Mn pass".
7. Ask before destructive ops not pre-authorized by the spec.
8. No raw stack traces — plain-English summaries.
9. Time-box stuck states at 45 min — escalate.
10. Read references at each milestone start; credit ideas in commits.

## Session log

### 2026-05-10 — Session 1 (bootstrap → M0 pass)

- Installed Temurin 21.0.11 via SDKMAN! (manual tarball extraction — SDKMAN's installer exited silently after download; root cause not fully diagnosed but workaround documented above).
- Pinned NeoForge `21.1.228`, ModDevGradle `2.0.141`, Parchment `2024.11.17`.
- Cloned MDK `archive/1.21-mdg`, stripped example content, re-packaged to `io.github.sammasr.terrascribe`.
- Wrote `LICENSE`, `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `CLAUDE.md`, `docs/PLAYTEST.md`, `docs/SPEC.md` mirror.
- Cloned references (`TerraForged` 1.18, `ReTerraForged`) into gitignored `references/`.
- `./gradlew build` green (3m 46s). `runServer` and `runClient` smoke tests both showed the `[TerraScribe]` mod-load line and zero errors.
- Initialized git repo. Repo-local identity `Sammasr / Samueltherobinson@gmail.com`. Initial commit `73c804f`.
- Created and pushed public GitHub repo: <https://github.com/Sammasr/terrascribe>. Tagged `v0.0.1-alpha`. CI build + release workflows triggered.
- User reply "M0 pass" — milestone closed.

### 2026-05-10 — Session 4 (M3 — Erosion)

- Two more Explore agents on TF/RTF erosion; both confirmed Lague droplet sim, per-region not per-chunk, LRU-cached. RTF uses 100 droplets per chunk × 32×32 chunk tiles ≈ 100k droplets per region; we run 25k droplets over 256×256 (smaller region, comparable density).
- Wrote `ErosionSimulator` (pure math, bilerp gradient + sediment capacity + deposit/erode). 8 tests green.
- Wrote `RegionHeightmap` record + `RegionCache` LRU. 6 tests. Caught a test-self bug where the counting-builder used a `Set` (so rebuilding the same key was a no-op) — switched to an `int buildCount`.
- Rewired `TerraScribeChunkGenerator.heightmapFor` to lazy-init a `RegionCache` from `RandomState`. Per-region erosion seed mixes world seed with region coords via Weyl constants so adjacent regions get different droplet streams.
- `runServer` smoke test: Done in 1.795 s (+300 ms vs no-erosion baseline). Zero errors.
- Pending: visual playtest.
- Next: M4 — rivers + lakes via flow accumulation across the eroded heightmap.

### 2026-05-10 — Session 3 (M2 — Surface + Biomes)

- Two more Explore agents on TF/RTF; both confirmed the climate noise + 2D-bucket-lookup pattern and called out the dynamic modded-biome discovery as the right modern approach (RTF only does static tag providers at data-gen).
- Wrote `Climate`/`ClimateSampler`/`ClimateBucket`/`BiomeMapper` pure-math layer. 11 new JUnit tests, all green.
- Rewrote `TerraScribeBiomeSource` with HolderSet codec + per-bucket pools. Found and worked around the Mojang tag/preset load-order issue: `#minecraft:is_overworld` decodes as empty at preset-codec time. Switched preset JSON to an explicit ~47-biome vanilla list + added `ModdedBiomeRegistry` static side-channel populated at `ServerAboutToStartEvent` with biomes that ARE in the tag (registry is populated by then).
- Added `TerraScribeConfig` (`ModConfigSpec`) with `biomeBlocklist` setting; honored at both static bucketing time and runtime discovery time.
- Wrote `SurfaceLayers` + implemented `TerraScribeChunkGenerator.buildSurface` — biome-ID-based top + 3 subsurface blocks per column. No vanilla `SurfaceRules`.
- `runServer` smoke test: "biome discovery: 53 overworld biomes across 9 climate buckets", Done in 1.735 s, zero errors.
- First visual playtest reported "biomes in 4×4 patches" — per-quart hash picker was the culprit. Replaced with low-frequency `FractalNoise` variant picker (freq 0.0015 → ~500-block patches with organic boundaries) in `ba802e1`. Second playtest: "looks better" — M2 closed.
- Next: M3 — hydraulic erosion + region cache.

### 2026-05-10 — Session 2 (M1 — custom ChunkGenerator → M1 pass)

- Two Explore agents digested TerraForged and ReTerraForged. Surprise: both references skip subclassing `ChunkGenerator`/`BiomeSource` and use mixin-based hooks plus density-function injection instead. Our spec's "real subclasses + WorldPreset registration" approach is the cleaner modern path; ReTerraForged's senior-engineer assessment agrees.
- Wrote pure-math noise stack (`NoiseField`, `SimplexNoise`, `FractalNoise`) — 9 JUnit tests green. Wired JUnit 5 (5.11.0) into `build.gradle`; had to add `mavenCentral()` and reorder repositories ahead of the neoforged maven (which served 502s for non-MC artifacts).
- Wrote `Heightmap` interface + `BasicHeightmapGenerator` — 7 JUnit tests green.
- Wrote `TerraScribeBiomeSource` (placeholder plains), `TerraScribeChunkGenerator` (direct `ChunkGenerator` subclass, stone fill below heightmap, water at sea level, bedrock at y=-64), `ModWorldgen` registry holder. Compile required reading actual NeoForge `ChunkGenerator.java` source from the gradle cache; `RandomState` doesn't expose the level seed so derived a stable per-world int via `aquiferRandom().at(0,0,0).nextInt()`.
- Wrote `data/terrascribe/worldgen/world_preset/terrascribe.json` (custom overworld, vanilla nether/end) + tag merge into `minecraft:normal` to surface in dropdown + lang entry.
- `runServer` smoke test with `level-type=terrascribe:terrascribe` generated spawn-area chunks in 892 ms, zero errors. Killed an M0 orphan process holding port 25565.
- Commit `78a363d` pushed. User confirmed M1 playtest pass.
- Next session: start Milestone 2 (climate sampler + biome mapper + modded biome discovery + surface rules). Begin by re-reading TerraForged climate/biome assignment in references/.
