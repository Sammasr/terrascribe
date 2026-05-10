# CLAUDE.md — TerraScribe operating manual

> This is Claude Code's live working notes for this repo. Read this first at every session start. Update at every session boundary (Section 18 of `docs/SPEC.md`).

## Project summary

TerraScribe is a TerraForged-style terrain generation mod for Minecraft 1.21.1 on NeoForge, built from scratch as a study project. The collaborating human is **new to Java and Minecraft modding** — they approve commands, run the test client, and make subjective design calls; they do not read code or debug stack traces. Be the senior engineer: justify decisions in plain English, push back when the spec is wrong, never dump raw logs.

The full specification is canonical: [`docs/SPEC.md`](docs/SPEC.md). When the spec and your instincts disagree, follow the spec but flag the conflict.

## Current milestone

**Milestone 1 — Custom ChunkGenerator.** Selectable "TerraScribe" world type generates a basic noise-driven heightmap, stone-only.

See `docs/SPEC.md` §9 for the full milestone table.

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

### Milestone 1 — Definition of Done

- [ ] Noise stack: `NoiseField` interface + `SimplexNoise` (2D, vendored) + `FractalNoise` (octave-summed fBm). Pure JVM, zero MC imports. JUnit tests: determinism, value range, seed independence.
- [ ] `Heightmap` (functional interface `int heightAt(int x, int z)`) + `BasicHeightmapGenerator` (noise → height). Pure math, unit-testable.
- [ ] `TerraScribeBiomeSource extends BiomeSource` — placeholder, returns `minecraft:plains` for all (x, y, z). Codec via `RecordCodecBuilder`.
- [ ] `TerraScribeChunkGenerator extends ChunkGenerator` (direct, not `NoiseBasedChunkGenerator` — see design note below). Fills stone below heightmap, water at sea level 63 when height < 63, air above. Codec via `RecordCodecBuilder`.
- [ ] DeferredRegisters wired for `BIOME_SOURCE` and `CHUNK_GENERATOR` codec registries. Both registered as `terrascribe:terrascribe`.
- [ ] `data/terrascribe/worldgen/world_preset/terrascribe.json` so "TerraScribe" appears in create-world dropdown. Lang entry `generator.terrascribe.terrascribe` → "TerraScribe".
- [ ] JUnit 5 wired up in `build.gradle`; codec roundtrip tests for chunk gen + biome source; noise math tests.
- [ ] GameTest: load TerraScribe world without errors (deferred — may slip to M2 if it bloats).
- [ ] `docs/PLAYTEST.md` appended with M1 checklist; `docs/ARCHITECTURE.md` first cut.
- [ ] CHANGELOG `[Unreleased]` entries for each substantive piece.
- [ ] Commit + push. `./gradlew runClient` shows TerraScribe world type, creates a world, terrain rolls, stone below surface, water at sea level — no errors in log.

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
- Next session: start Milestone 1 (custom ChunkGenerator). First step is to read `references/TerraForged/.../ChunkGenerator*` and `references/ReTerraForged/.../*ChunkGenerator*` for orientation, then draft the noise / terrain / chunk packages.
