# TerraScribe — Project Specification for Claude Code

A TerraForged-style terrain generation mod for **Minecraft 1.21.1** on **NeoForge**, built from the ground up by Claude Code acting as the lead engineer.

---

## 0. How to read this document

You (Claude Code) are the senior Minecraft mod engineer on this project. The human collaborator is **new to Java and Minecraft modding**. They are not going to read source code or debug compiler errors. They will:

- Approve commands you propose (installs, gradle runs, git operations)
- Launch the test client when you instruct them to and describe what they see
- Make subjective design calls ("this mountain shape looks wrong", "rivers should be wider")

You handle everything else. This spec is the source of truth. When the spec and your instincts disagree, follow the spec, but raise the conflict explicitly and ask the user to adjudicate.

Before writing any code, **read this entire document end-to-end**, then read it again, then begin Section 6 (Environment Bootstrap).

---

## 1. Project Identity

| Field | Value |
|---|---|
| Mod name | **TerraScribe** |
| Mod ID | `terrascribe` |
| Java package | `io.github.<USERNAME>.terrascribe` — **ask the user for their GitHub username during bootstrap** |
| Mod loader | NeoForge |
| Minecraft version | 1.21.1 |
| Java version | 21 (LTS) |
| License | MIT |
| Display name in-game | TerraScribe |
| World type label | "TerraScribe" |

## 2. Mission

Build a full-featured terrain generation mod that matches the original TerraForged in capability:

1. Custom noise-based terrain with distinct terrain types (plains, hills, mountains, plateaus, badlands).
2. Simulated hydraulic erosion applied to the heightmap before chunk generation.
3. Natural-looking rivers derived from flow accumulation across the eroded heightmap.
4. Climate-driven biome assignment (temperature + humidity + elevation).
5. **Modded biome integration** — automatically discover overworld biomes from other installed mods and incorporate them.
6. **In-game preview GUI** on the world-creation screen: live, debounced, multi-layer preview (elevation / temperature / humidity / biomes) with zoom and sliders that mutate generation parameters.
7. Preset save/load system with built-in starter presets and user-defined presets.
8. Custom decorations: trees, foliage, surface features per biome.
9. Singleplayer **and** dedicated-server support.
10. Performance: chunk generation within **2× vanilla** time on the same hardware.

## 3. Reference Materials

**You will reference these throughout. They are not optional reading. Clone the GitHub repos locally to a `references/` folder outside the project root and read the relevant files at the start of each milestone. We are using a hybrid approach: study these for understanding, but write our own implementation from scratch.**

| Resource | Purpose | URL |
|---|---|---|
| TerraForged (original, MIT) | Primary architectural reference; 1.18 branch is most complete | https://github.com/TerraForged/TerraForged |
| ReTerraForged (community fork, MIT) | Modern port — shows how the original adapts to newer worldgen APIs | https://github.com/racoonman2/ReTerraForged |
| NeoForge documentation | Authoritative API docs for our target version | https://docs.neoforged.net/ |
| NeoForge MDK | Starter template — use as project skeleton | https://github.com/neoforged/MDK |
| ModDevGradle | Build system docs | https://github.com/neoforged/ModDevGradle |
| Minecraft Wiki — Custom worldgen | JSON format reference for biomes, density functions, noise settings | https://minecraft.wiki/w/Custom_world_generation |
| Sebastian Lague — Hydraulic Erosion | YouTube video; the canonical implementation reference for our erosion simulator | https://www.youtube.com/watch?v=eaXk97ujbPQ |
| FastNoise Lite (MIT) | Optional vendored noise library | https://github.com/Auburn/FastNoiseLite |

**Attribution requirement:** because we are studying TerraForged, the project README and LICENSE file must explicitly credit Thomas Holmes (won_ton_) and racoonman2 as inspirations, with links to both repos. Do this on day one.

## 4. Tech Stack

- **Java 21** — required by 1.21+. Use modern Java: records, sealed types, pattern matching, `var` where it improves readability.
- **NeoForge** (latest stable for 1.21.1 — verify at install time, do not hardcode an old version)
- **ModDevGradle** — the current NeoForge build plugin. NeoGradle is deprecated; do not use it.
- **Gradle wrapper** (committed to repo)
- **Official Mojang mappings** — bundled with NeoForge MDK by default
- **JUnit 5** — unit tests for pure-math code
- **NeoForge GameTest framework** — in-game integration tests
- **JOML** — vector/matrix math (already included by Minecraft)
- **SLF4J** — logging facade (already provided)
- **Mixin** — only if necessary. Prefer NeoForge events first.

## 5. Target Versions to Lock at Bootstrap

When you bootstrap the project, **search the web for the current latest stable versions** of:

- NeoForge for 1.21.1 (form: `21.1.x`)
- ModDevGradle plugin
- Parchment mappings for 1.21.1 (parameter name overlay — optional but improves readability)

Pin them in `gradle.properties`. Document the pinned versions in `README.md` under "Build Requirements."

---

## 6. Environment Bootstrap (Linux)

The user has Git and an IDE preference (none installed yet), and needs Java tooling installed. Walk through this carefully. **Show each command before running it**, briefly explain what it does, wait for approval, run it, verify it succeeded, report the result.

### 6.1 Required installs

| Tool | Why | Install method |
|---|---|---|
| SDKMAN! | Java version manager — keeps JDK 21 isolated from system Java | `curl -s "https://get.sdkman.io" \| bash` |
| Temurin JDK 21 | Required by Minecraft 1.21.1 | `sdk install java 21.0.5-tem` (verify latest 21.x at install time) |
| IntelliJ IDEA Community Edition | Best NeoForge tooling support; the human will not look at code, but IntelliJ is what gradle integration assumes | `sudo snap install intellij-idea-community --classic` (preferred) or via JetBrains Toolbox |
| GitHub CLI (`gh`) | Streamlines repo creation, releases, and CI setup | `sudo apt install gh` then `gh auth login` |

### 6.2 Verification steps

After each install, verify and report:

```bash
java -version            # expect 21.x
javac -version           # expect 21.x
git --version            # any modern version
gh --version             # logged in?
gradle --version         # we'll use the wrapper, but worth confirming
```

If any step fails, **stop and ask the user** before proceeding. Do not silently retry or fall back to alternatives.

### 6.3 IntelliJ headless setup note

The user has stated they will not read code. IntelliJ runs in the background to support Gradle and run configurations. You do not need to walk them through IntelliJ's UI. Generate run configurations programmatically by running `./gradlew :runClient :runServer :runData` once during bootstrap — this populates IntelliJ-compatible run configs into `.idea/runConfigurations/`.

---

## 7. Project Bootstrap Sequence

Execute in this exact order. Commit after each step with the suggested message.

### 7.1 Generate skeleton

1. Download the NeoForge MDK for **1.21.1** (latest stable NeoForge).
2. Extract to `~/projects/terrascribe`.
3. Rename package paths from `com.example.examplemod` to `io.github.<USERNAME>.terrascribe`.
4. Update `gradle.properties`:
   - `mod_id=terrascribe`
   - `mod_name=TerraScribe`
   - `mod_license=MIT`
   - `mod_version=0.0.1-alpha`
   - `mod_group_id=io.github.<USERNAME>.terrascribe`
   - `mod_authors=<USERNAME>`
   - `mod_description` — see Section 17 for the canonical description text.
5. Update `META-INF/neoforge.mods.toml` to match.
6. Rename main mod class to `TerraScribe.java`.
7. Run `./gradlew build` — must succeed.
8. Run `./gradlew runClient` — Minecraft must launch, mod must load, and a `[TerraScribe]` log line must appear in the console.

**Commit:** `chore: bootstrap NeoForge 1.21.1 MDK as TerraScribe skeleton`

### 7.2 Initialize git and GitHub

1. `git init`, `.gitignore` from MDK plus `references/`, `.idea/` (selectively — keep `runConfigurations/`).
2. Create `LICENSE` (MIT, current year, author = user's GitHub name).
3. Create `README.md` with placeholder sections (see 17).
4. Create `CHANGELOG.md` following [Keep a Changelog](https://keepachangelog.com/).
5. Create `CONTRIBUTING.md` with a basic contributor guide.
6. Initial commit: `chore: initial commit`.
7. `gh repo create terrascribe --public --source=. --remote=origin --push` — confirm with user first.
8. Tag and push `v0.0.1-alpha`.

### 7.3 CLAUDE.md

Create `CLAUDE.md` at the repo root. This is your operating manual for future sessions. It must contain:

- One-paragraph project summary
- Pointer to this spec (`docs/SPEC.md` — copy this document into the repo)
- Current milestone status
- Active design decisions and their rationale
- Known issues and TODOs
- "How to run the game": exact gradle commands
- "How to test": which tests live where, how to run them
- "Where to look for reference": paths to TerraForged/ReTerraForged clones in `references/`

Update `CLAUDE.md` at the end of every working session.

### 7.4 Clone references

```bash
mkdir -p references
git clone --depth 1 --branch 1.18 https://github.com/TerraForged/TerraForged.git references/TerraForged
git clone --depth 1 https://github.com/racoonman2/ReTerraForged.git references/ReTerraForged
```

Add `references/` to `.gitignore`. These are read-only study material.

### 7.5 CI

Create `.github/workflows/build.yml`:

- Triggers: push to `main`, all PRs
- Steps: checkout, setup-java@v4 with Temurin 21, `./gradlew build test`, upload `build/libs/*.jar` artifact

Create `.github/workflows/release.yml`:

- Triggers: push of tag matching `v*`
- Steps: build, create GitHub release with jar attached, upload to Modrinth via official action (token stored as repo secret — leave a TODO for the user to add it later), upload to CurseForge (same).

**The Modrinth/CurseForge tokens are user actions.** Leave clear TODO comments and explain in the README which env vars/secrets need setting.

---

## 8. Architecture

Top-level package layout:

```
io.github.<USERNAME>.terrascribe
├── TerraScribe                    # @Mod main class, registry bus, lifecycle
├── client
│   └── gui
│       ├── PreviewScreen          # custom world-create screen extension
│       ├── PreviewMapWidget       # the live map
│       ├── PreviewExecutor        # background thread pool
│       └── SettingsPanel          # sliders, dropdowns, view-mode toggles
├── worldgen
│   ├── noise
│   │   ├── NoiseField             # interface
│   │   ├── SimplexNoise           # vendored implementation
│   │   ├── DomainWarp             # warped noise
│   │   ├── FractalNoise           # multi-octave
│   │   └── CellularNoise          # for region partitioning
│   ├── terrain
│   │   ├── TerrainType            # enum: PLAINS, HILLS, MOUNTAINS, PLATEAU, BADLANDS, COAST
│   │   ├── TerrainBlender         # weighted blend of terrain types
│   │   ├── Heightmap              # immutable heightmap region
│   │   ├── HeightmapGenerator     # produces a Heightmap for a region
│   │   └── ErosionSimulator       # hydraulic erosion (Lague algorithm)
│   ├── river
│   │   ├── FlowField              # downhill gradient accumulation
│   │   ├── RiverCarver            # applies river channels to heightmap
│   │   └── LakeFinder             # detects sinks for lake placement
│   ├── biome
│   │   ├── ClimateSampler         # (x,z) → (temperature, humidity)
│   │   ├── BiomeMapper            # (climate, height, terrainType) → ResourceKey<Biome>
│   │   ├── ModdedBiomeRegistry    # discovers overworld biomes from all mods
│   │   └── TerraScribeBiomeSource # subclass of BiomeSource, codec-driven
│   ├── chunk
│   │   ├── TerraScribeChunkGenerator   # extends NoiseBasedChunkGenerator
│   │   ├── SurfaceRuleBuilder          # constructs surface rules
│   │   └── RegionCache                 # caches heightmaps + erosion per region
│   ├── decoration
│   │   ├── TreeFeatures           # custom configured features
│   │   ├── BiomeModifiers         # injects features into existing biomes
│   │   └── feature/*              # individual Feature implementations
│   └── preset
│       ├── Preset                 # record + codec
│       ├── PresetSettings         # tunable parameters record
│       ├── PresetManager          # load/save/list
│       └── BuiltInPresets         # 5 starters
├── network
│   ├── PacketHandler              # channel setup
│   └── packets/                   # if needed; likely empty for v1
├── registry
│   ├── ModRegistries              # DeferredRegister setup
│   ├── ModFeatures
│   ├── ModWorldPresets
│   └── ModBiomeModifiers
└── util
    ├── MathHelpers
    ├── NoiseHelpers
    └── ColorRamps                 # for preview rendering
```

### 8.1 Design principles

- **Worldgen layer is pure** — `worldgen.noise`, `worldgen.terrain`, `worldgen.river`, `worldgen.biome` (climate part) contain **zero** Minecraft API references. Pure math. Unit-testable on the JVM without Minecraft.
- **The MC bridge is `worldgen.chunk`** — this is where pure logic meets `ChunkGenerator`, `BiomeSource`, `LevelChunk`.
- **Codec-everything** — every data class that needs to be saved (presets, biome source state, chunk generator state) is a `record` with a static `Codec<X> CODEC`. Use Mojang's Codec API. Do not use Gson directly for any worldgen data.
- **Deterministic** — same seed + same preset = identical output. No `Random` without an explicit seed derived from the world seed.
- **Region-scoped caching** — caches keyed by region coordinate (512×512 blocks). Bounded LRU, default 256 regions. Evict on world close.

### 8.2 Things that will bite you

Surface these explicitly in `CLAUDE.md` as you discover them:

- `BiomeSource` requires a `Codec` and must be registered to the `BiomeSource` codec registry.
- `ChunkGenerator` codecs go in the worldgen registry — registration order matters relative to biomes.
- The world-creation screen is **not** trivial to extend. Use NeoForge's `RegisterMenuScreensEvent`-style hooks where available; otherwise extend `WorldCreationContextEvent` or fall back to a Mixin on `CreateWorldScreen` (last resort).
- Modded biomes must be discovered **lazily** — the biome registry isn't fully populated until world load. Hook `ServerAboutToStartEvent` or query the level's registry access, not the static registries.
- Surface rules in 1.21 are codec-driven JSON, but you can build them programmatically with `SurfaceRules.sequence(...)`.
- Erosion is expensive. Run it once per region, cache the result, never re-run during chunk gen.

## 9. Implementation Milestones

Each milestone ends with:

1. A green build (`./gradlew build` succeeds, all tests pass).
2. A successful `./gradlew runClient` smoke test.
3. A commit + push.
4. An updated `CLAUDE.md` reflecting current state.
5. A **Playtest Checklist** posted to the user with concrete steps they will perform in-game and report back on.

Do not move to the next milestone until the user confirms the playtest checklist passes.

| # | Milestone | Target duration | Key deliverable |
|---|---|---|---|
| 0 | Bootstrap | Day 1 | Skeleton mod loads in 1.21.1, GitHub repo live, CI green |
| 1 | Custom ChunkGenerator | Week 1 | Selectable "TerraScribe" world type generates a basic noise-driven heightmap, stone-only |
| 2 | Surface + Biomes | Week 2 | Climate model → biome assignment, surface rules apply correct blocks, modded biomes auto-discovered |
| 3 | Erosion | Week 3 | Hydraulic erosion visible in terrain; mountains look weathered |
| 4 | Rivers + Lakes | Week 4 | Flow-based rivers carve through terrain; lakes at sinks |
| 5 | Preset System | Week 5 | 5 built-in presets selectable; user presets save/load |
| 6 | Preview GUI | Weeks 6–7 | Live debounced preview with view-mode toggles, zoom, sliders that mutate the preset |
| 7 | Decoration | Week 8 | Custom trees, biome features injected via biome modifiers |
| 8 | Polish + Server | Week 9 | Dedicated server tested, performance within target, modded biome compat verified with BoP and Terralith |
| 9 | Release | Week 10 | `v0.1.0` tagged, GitHub release, Modrinth + CurseForge first publish |

Estimates assume ~10 hours of human collaboration per week. Slower is fine. Faster is suspicious.

### 9.1 Definition of done for each milestone

You will write a specific definition-of-done at the start of each milestone in `CLAUDE.md`. Use this template:

```
## Milestone N: <name> — Definition of Done
- [ ] Code change list (high level)
- [ ] Tests added/updated
- [ ] Manual playtest steps with expected outcomes
- [ ] Documentation updates
- [ ] CHANGELOG entry under [Unreleased]
- [ ] Performance check (if applicable)
- [ ] Commit + push
```

## 10. Coding Standards

- **Immutability by default.** Records for data carriers. `final` on fields and parameters. Mutable state lives behind explicit, well-named classes (`RegionCache`, `PreviewExecutor`).
- **Sealed types** where you have a closed set of variants (e.g., `TerrainType` could be a sealed interface with permitted record implementations if behaviors differ).
- **No nulls in public APIs.** Use `Optional` or sentinel objects. Annotate with `@Nullable` (from JetBrains annotations, included by NeoForge) only when truly necessary.
- **Naming.** PascalCase classes, camelCase methods/vars, SCREAMING_SNAKE constants. Package names all lowercase. Mod ID matches package leaf.
- **`ResourceLocation`.** Always `ResourceLocation.fromNamespaceAndPath(TerraScribe.MODID, "name")`. Never the deprecated constructor.
- **Logging.** SLF4J via `LogUtils.getLogger()`. Log level discipline: TRACE = math noise, DEBUG = once-per-chunk events, INFO = milestone events (world load), WARN = recoverable surprises, ERROR = real failures. No `System.out.println` anywhere, ever.
- **Javadoc** on every public class and public method. Brief and useful, not ceremonial.
- **Magic numbers.** Pull into named constants. Especially terrain math — every constant should have a comment explaining what it controls and a sensible range.
- **Codec discipline.** When you define a codec, write a roundtrip test (`encode → decode → assertEquals`) the same day.
- **Threading.** Worldgen runs on Minecraft's worker pool. Preview GUI uses its own `ExecutorService`. Never call MC API from a non-MC thread. Use `Minecraft.getInstance().execute(...)` to bounce work back to the main thread.

## 11. Testing Strategy

### 11.1 Unit tests (JUnit 5)

Pure-math code in `worldgen.noise`, `worldgen.terrain` (excluding `Heightmap` MC integration), `worldgen.river`, `worldgen.biome` (climate part), `worldgen.preset` (codecs).

Target: **70%+ line coverage on these packages**. Enforce a coverage floor in CI via JaCoCo.

### 11.2 GameTest integration tests

NeoForge supports Minecraft's GameTest framework. Write tests for:

- Chunk gen smoke test: load a TerraScribe world, verify chunks generate without errors.
- Biome assignment: at fixed seed + fixed coordinates, assert specific biomes appear.
- Surface rules: verify block types at depth.
- River carving: at a known river coordinate, verify water present at expected y.

Run via `./gradlew runGameTestServer`.

### 11.3 Manual playtest checklist

Maintained at `docs/PLAYTEST.md`, updated per milestone. The user runs this and reports back. Items include:

- Create world with each built-in preset
- Walk 10,000 blocks from spawn in each cardinal direction
- Visit each major biome category (cold, temperate, hot; humid, dry)
- Find at least one river that flows from high terrain to a lake/ocean
- Test in dedicated server mode (`./gradlew runServer`, connect from a second client if available, otherwise just verify server starts cleanly)
- Test with Biomes O' Plenty installed (drop the jar in `run/mods/`)
- Test with Terralith installed (datapack)

### 11.4 Performance benchmark

`PerformanceBenchmark` GameTest: time 16×16 chunks of generation, fail if average per-chunk time exceeds **2× vanilla on the same machine**. Vanilla baseline measured on first run and pinned in `docs/PERF.md`.

## 12. Preview GUI — Detailed Spec

The user specifically called out wanting this to match the original TerraForged behavior. Implementation notes:

- **Custom screen:** extend or replace the world-creation flow. When the user selects the "TerraScribe" world type, a "Customize" button appears that opens `PreviewScreen`.
- **Layout:** map widget fills ~60% of the screen width on the left; sliders and view-mode controls on the right; bottom bar with [Cancel] [Reset to Preset] [Save Preset] [Done].
- **Update model:** live with debounce. Slider drag emits change events; a 250 ms debouncer collapses bursts and triggers a regeneration. Background thread pool computes tiles; finished tiles uploaded to a `DynamicTexture` and drawn.
- **Tile size:** 64×64 px. World area shown: configurable zoom, default `1 px = 16 blocks` so the visible map covers ~10,000 × 10,000 blocks at 1280×720 game window.
- **View modes:** Elevation (grayscale ramp + hillshading), Temperature (blue→red ramp), Humidity (yellow→blue ramp), Biomes (mapped colors). Toggle buttons cycle modes without recomputing the heightmap — just re-render from cached data.
- **Sliders (v1):** world scale, mountain frequency, mountain height multiplier, erosion strength, river density, sea level, temperature offset, humidity offset. Each slider has a tooltip explaining its effect.
- **Thread safety:** preview computation reads an immutable snapshot of the current `PresetSettings`. Slider changes create new immutable snapshots. Never mutate a snapshot in flight.
- **Cancellation:** if a new regeneration starts while the previous is still running, the previous is cancelled cleanly (cooperative cancellation via interrupt + flag check).

## 13. Preset System — Detailed Spec

- **Format:** JSON, codec-driven, located in `data/terrascribe/presets/*.json`.
- **Built-ins:** `default`, `mountains`, `continents`, `islands`, `plateau`. Each has hand-tuned values that demonstrate a clearly different terrain feel.
- **User presets:** saved via the GUI's "Save Preset" button to `<gameDir>/config/terrascribe/presets/`. Loaded automatically at world creation.
- **Preset record fields:** seed override (optional), world scale, mountain config (frequency, height, falloff), erosion config (droplet count, evaporation rate, sediment capacity), river config (density, width factor, lake threshold), climate config (temp offset, humidity offset, latitude factor), sea level, surface depth.
- **Versioning:** include a `formatVersion` field. Bump when fields change. Implement a migration step on load.

## 14. Modded Biome Integration

- On world creation, query the `BIOME` registry for all biomes tagged `minecraft:is_overworld` (or with category compatible with overworld).
- Group by climate signature using the biome's vanilla climate parameters where available, fall back to heuristics for biomes without explicit climate data.
- Inject into `BiomeMapper`'s decision matrix alongside vanilla biomes.
- Provide config option (`config/terrascribe-common.toml`) to blacklist specific biomes or biome mods entirely.
- Test compatibility explicitly with: vanilla, Biomes O' Plenty, Terralith, Oh The Biomes You'll Go. Document results in `docs/COMPATIBILITY.md`.

## 15. Performance Budget

- Chunk generation: ≤ 2× vanilla on the same hardware (measured via `PerformanceBenchmark` GameTest).
- Preview tile generation: ≤ 100 ms per 64×64 tile at default zoom on a 4-core 8GB machine.
- Region cache: 256 regions max, eviction LRU. Memory ceiling ~32 MB.
- Erosion: precomputed per region, never on chunk gen hot path.
- No allocations in hot loops where avoidable. Reuse `int[]`/`float[]` buffers via thread-local pools.

If you cannot meet the chunk-gen budget after a good-faith optimization pass, surface the problem to the user and propose either (a) a quality reduction or (b) a redesign — don't silently ship a slow mod.

## 16. Release Pipeline

- **Versioning:** SemVer. `0.x.y` until the mod is feature-complete; `1.0.0` when all milestones are done and a real player has played 5+ hours without crashes.
- **Tags:** `vX.Y.Z`. Push tag → CI runs release workflow.
- **CHANGELOG.md:** Keep a Changelog format. Every PR/commit-of-substance adds a line under `[Unreleased]`. On release, move `[Unreleased]` to `[X.Y.Z] - YYYY-MM-DD`.
- **Modrinth/CurseForge:** publish from CI. User adds API tokens as repo secrets (TODO surfaced in README). Project descriptions for both platforms live in `docs/PUBLISHING.md`.
- **Pre-release etiquette:** every `v0.x.y` release is marked as pre-release on GitHub and "alpha" or "beta" on Modrinth/CurseForge. Only `v1.0.0+` ships as "release."

## 17. Documentation Deliverables

Maintain these files:

- `README.md` — what the mod is, how to install, screenshots placeholder, build instructions, credits to TerraForged and ReTerraForged.
- `CLAUDE.md` — your live operating manual (Section 7.3).
- `docs/SPEC.md` — this document, copied into the repo.
- `docs/ARCHITECTURE.md` — updated each milestone with current module diagram and rationale.
- `docs/PRESET_FORMAT.md` — JSON schema with examples.
- `docs/PLAYTEST.md` — running playtest checklist.
- `docs/PERF.md` — performance baselines.
- `docs/COMPATIBILITY.md` — modded biome compat results.
- `docs/PUBLISHING.md` — Modrinth/CurseForge metadata, descriptions.
- `CHANGELOG.md` — Keep a Changelog.
- `CONTRIBUTING.md` — for future contributors.
- `LICENSE` — MIT.

### 17.1 Canonical mod description

Use this verbatim in `neoforge.mods.toml`, README intro, and store listings:

> TerraScribe is a terrain generation overhaul for Minecraft 1.21.1. It replaces vanilla worldgen with a simulated-erosion heightmap, flow-based rivers, climate-driven biome assignment, and a live in-game preview that lets you sculpt the world before generating it. Built as a spiritual successor to TerraForged and ReTerraForged, with full modded-biome compatibility.

---

## 18. Operating Principles for Claude Code

These are your behavioral rules.

1. **Senior engineer mindset.** You make architectural decisions, justify trade-offs, refuse bad ideas. When the spec is wrong, say so and propose a fix; don't silently deviate.

2. **Educate as you go.** The user is new. After each non-trivial change, write 2–4 sentences explaining what you did and why, in plain English. Not a code review — a designer's summary. Example: *"Added the climate sampler. It generates a temperature and humidity value for any (x, z) in the world using two layered noise functions plus a latitude term that makes the north colder. Biome assignment will use this in the next step."*

3. **Tool autonomy.** Install whatever you need. Verify each install. Report versions. Never assume; always check.

4. **Use TodoWrite religiously.** Every milestone gets a todo list. Every multi-step task gets a todo list. Update them in real time.

5. **Phase commits.** One logical change per commit. Conventional Commits format: `feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`, `perf:`. Body explains *why* when not obvious.

6. **Self-validate before claiming done.** Run `./gradlew build test` after every change set. Never tell the user "this works" without having actually run the test client at least once for any change touching runtime behavior.

7. **Read references first.** At the start of each milestone, read the corresponding TerraForged and ReTerraForged sources for orientation. Make notes in `CLAUDE.md` about what they do that we're replicating, what we're doing differently, and why.

8. **Ask before destructive ops.** Confirm before: `git push --force`, deleting branches, deleting files outside the project, modifying global config, mass refactors.

9. **Document as you go.** `CLAUDE.md` updated at every session boundary. `docs/ARCHITECTURE.md` updated at every milestone. `CHANGELOG.md` updated at every commit of substance.

10. **Honor milestone boundaries.** At the end of each milestone: build green, push, update docs, write the playtest checklist, **stop and hand off to the user**. Do not start the next milestone until the user confirms playtest pass.

11. **Surface uncertainty.** If you are guessing, say you are guessing. If you can't decide between two approaches, present both with trade-offs and ask. Better to pause for 30 seconds than to ship a wrong architectural choice.

12. **Respect the licenses.** TerraForged and ReTerraForged are MIT. You may study them. You may take inspiration. You may not copy code verbatim. If you find yourself wanting to copy more than ~10 lines, stop and write your own version. Always credit in commit messages when an idea came from a reference (`feat: implement hydraulic erosion (inspired by TerraForged/won_ton_)`).

13. **Don't ship secrets.** No API tokens, no personal info, no `.env` files committed. `.gitignore` is sacred.

14. **Beginner-safe failure modes.** When a build breaks or a test fails: explain in plain English what failed, what you're going to try, and ask the user if they want to take a look at logs (which you'll summarize, not dump raw). Never just hand them a stack trace.

15. **Time-box stuck states.** If you've spent more than 45 minutes on a single bug without progress, stop and ask the user to bring in a second pair of eyes (Anthropic, a forum, a Discord, etc.). Don't grind.

## 19. Communication Protocol with the User

- **Beginning of session:** read `CLAUDE.md`, summarize current state in 3–5 sentences, propose the next 2–3 actions, wait for approval.
- **During work:** after every install, every gradle command of substance, every commit — one-line confirmation. After every milestone subtask — short paragraph.
- **End of milestone:** playtest checklist (Section 11.3 template), wait for user to play and report back.
- **On error:** plain-English summary, proposed fix, estimated effort, ask before acting.
- **End of session:** update `CLAUDE.md`, summarize what got done, list what's next, push to GitHub.

## 20. Out of Scope (for v1)

To prevent scope creep, the following are **explicitly not in v1**:

- Nether/End worldgen changes — overworld only.
- Custom dimensions.
- Distant Horizons compatibility (compatibility may incidentally work; explicit support is later).
- Cubic Chunks compatibility.
- Structure generation overhaul (villages, strongholds, etc. work as vanilla).
- Cave generation overhaul (vanilla caves only).
- A Bedrock Edition port (Java only).
- A Fabric port.

These can come in v1.x or v2 once v1 is stable.

---

## 21. First Session Action List

When you (Claude Code) start the first working session, do exactly this:

1. Read this entire spec.
2. Confirm with the user: "Ready to begin TerraScribe Milestone 0. I will install JDK 21, IntelliJ, and GitHub CLI, then bootstrap the NeoForge MDK. This will take roughly 30–45 minutes including downloads. Confirm to proceed?"
3. On confirmation: run Section 6 environment bootstrap, step by step, with approval for each command.
4. Run Section 7 project bootstrap.
5. Verify `./gradlew runClient` launches Minecraft 1.21.1 with the TerraScribe mod loaded.
6. Update `CLAUDE.md` with current state.
7. Push to GitHub.
8. Post Milestone 0 playtest checklist:
   - Open the modded Minecraft client
   - Check that TerraScribe appears in the mods list
   - Create a vanilla world (TerraScribe world type doesn't exist yet)
   - Confirm no errors in the log
   - Reply "M0 pass" to proceed to Milestone 1.

---

## 22. Glossary

- **Heightmap** — a 2D grid of height values, one per `(x, z)` block column, used to determine terrain shape before chunks generate.
- **Hydraulic erosion** — a simulation that drops virtual water droplets on a heightmap and lets them carry and deposit "sediment" downhill, producing weathered, naturalistic terrain.
- **Flow accumulation** — for each cell of a heightmap, the total upstream area that drains through it. Used to identify where rivers should run.
- **Codec** — Mojang's serialization framework. Defines how a data class is read from and written to JSON/NBT.
- **DeferredRegister** — NeoForge utility for registering content (items, blocks, biomes, features) at the correct lifecycle moment.
- **Biome modifier** — NeoForge mechanism for injecting features/mobs/etc. into existing biomes without overwriting them.
- **GameTest** — Minecraft's official in-game integration test framework; tests run inside a real (test) world.
- **MDK** — Mod Development Kit; the official starter template for a NeoForge mod.

---

*End of specification. Read it again before you start.*
