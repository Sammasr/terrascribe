# CLAUDE.md — TerraScribe operating manual

> This is Claude Code's live working notes for this repo. Read this first at every session start. Update at every session boundary (Section 18 of `docs/SPEC.md`).

## Project summary

TerraScribe is a TerraForged-style terrain generation mod for Minecraft 1.21.1 on NeoForge, built from scratch as a study project. The collaborating human is **new to Java and Minecraft modding** — they approve commands, run the test client, and make subjective design calls; they do not read code or debug stack traces. Be the senior engineer: justify decisions in plain English, push back when the spec is wrong, never dump raw logs.

The full specification is canonical: [`docs/SPEC.md`](docs/SPEC.md). When the spec and your instincts disagree, follow the spec but flag the conflict.

## Current milestone

**Milestone 2 — Surface + Biomes.** Climate model → biome assignment, surface rules apply correct blocks, modded biomes auto-discovered.

See `docs/SPEC.md` §9 for the full milestone table.

### Milestone 2 — Definition of Done (draft — refine at session start)

- [ ] `ClimateSampler` — pure-math `(x, z)` → `(temperature, humidity)`. Layered noise + a latitude-bias term so north is colder. Unit-tested.
- [ ] `BiomeMapper` — pure-math decision matrix `(climate, height, terrainType)` → `ResourceKey<Biome>`. Start with a handful of vanilla overworld biomes (Plains, Desert, Forest, Taiga, Mountains, Beach, Ocean) and a fallback. Unit-tested.
- [ ] `ModdedBiomeRegistry` — at world creation, query the BIOME registry for everything in `is_overworld`, group by climate signature, inject into `BiomeMapper` decision matrix alongside vanilla. Hook `ServerAboutToStartEvent` (NOT static registries — biomes aren't fully populated until world load).
- [ ] Config TOML (`config/terrascribe-common.toml`) with a modded-biome blocklist option.
- [ ] `TerraScribeBiomeSource` rewritten to use `BiomeMapper` + `ClimateSampler` instead of the M1 single-biome placeholder.
- [ ] `TerraScribeChunkGenerator.buildSurface` implemented — at minimum: grass + dirt cap on Plains/Forest-style, sand on Desert/Beach, stone exposed on Mountains. Use `SurfaceRules.sequence(...)` to build programmatically.
- [ ] GameTest: at fixed seed + fixed coordinates, assert specific biomes appear; assert top block matches biome.
- [ ] `docs/PLAYTEST.md` M2 checklist, `docs/COMPATIBILITY.md` first cut (vanilla pass, BoP, Terralith deferred to M8 with config tag-based blocklist hint).
- [ ] CHANGELOG `[Unreleased]` entries.

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

### 2026-05-10 — Session 2 (M1 — custom ChunkGenerator → M1 pass)

- Two Explore agents digested TerraForged and ReTerraForged. Surprise: both references skip subclassing `ChunkGenerator`/`BiomeSource` and use mixin-based hooks plus density-function injection instead. Our spec's "real subclasses + WorldPreset registration" approach is the cleaner modern path; ReTerraForged's senior-engineer assessment agrees.
- Wrote pure-math noise stack (`NoiseField`, `SimplexNoise`, `FractalNoise`) — 9 JUnit tests green. Wired JUnit 5 (5.11.0) into `build.gradle`; had to add `mavenCentral()` and reorder repositories ahead of the neoforged maven (which served 502s for non-MC artifacts).
- Wrote `Heightmap` interface + `BasicHeightmapGenerator` — 7 JUnit tests green.
- Wrote `TerraScribeBiomeSource` (placeholder plains), `TerraScribeChunkGenerator` (direct `ChunkGenerator` subclass, stone fill below heightmap, water at sea level, bedrock at y=-64), `ModWorldgen` registry holder. Compile required reading actual NeoForge `ChunkGenerator.java` source from the gradle cache; `RandomState` doesn't expose the level seed so derived a stable per-world int via `aquiferRandom().at(0,0,0).nextInt()`.
- Wrote `data/terrascribe/worldgen/world_preset/terrascribe.json` (custom overworld, vanilla nether/end) + tag merge into `minecraft:normal` to surface in dropdown + lang entry.
- `runServer` smoke test with `level-type=terrascribe:terrascribe` generated spawn-area chunks in 892 ms, zero errors. Killed an M0 orphan process holding port 25565.
- Commit `78a363d` pushed. User confirmed M1 playtest pass.
- Next session: start Milestone 2 (climate sampler + biome mapper + modded biome discovery + surface rules). Begin by re-reading TerraForged climate/biome assignment in references/.
