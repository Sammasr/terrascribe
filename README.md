# TerraScribe

> TerraScribe is a terrain generation overhaul for Minecraft 1.21.1. It replaces vanilla worldgen with a simulated-erosion heightmap, flow-based rivers, climate-driven biome assignment, and a live in-game preview that lets you sculpt the world before generating it. Built as a spiritual successor to TerraForged and ReTerraForged, with full modded-biome compatibility.

**Status:** Pre-alpha (Milestone 0 — skeleton). See [`CHANGELOG.md`](CHANGELOG.md) and [`docs/SPEC.md`](docs/SPEC.md).

---

## Build requirements

| Tool | Pinned version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.228 |
| Java | Temurin 21 (LTS) — tested against 21.0.11 |
| ModDevGradle plugin | 2.0.141 |
| Parchment mappings | `1.21.1` / `2024.11.17` |
| Gradle | via included wrapper |

Versions are pinned in [`gradle.properties`](gradle.properties).

## Building from source

```bash
./gradlew build
```

The first build downloads Minecraft, NeoForge, and the Parchment overlay (a few minutes). The packaged mod jar lands in `build/libs/`.

## Running the mod locally

```bash
./gradlew runClient       # launch a dev client with TerraScribe loaded
./gradlew runServer       # launch a dedicated dev server
./gradlew runGameTestServer  # run the GameTest suite
./gradlew runData         # run data generators
```

## Installing into a real Minecraft instance

1. Install **NeoForge 21.1.x** for Minecraft 1.21.1 — see <https://neoforged.net/>.
2. Drop the TerraScribe jar from `build/libs/` (or a downloaded release) into your `mods/` folder.
3. Launch Minecraft. Create a new world and pick the **TerraScribe** world type.
   *(World type registration lands in Milestone 1 — this works in real releases, not the pre-alpha skeleton.)*

## Credits & inspiration

TerraScribe is an independent implementation that draws design inspiration from two prior projects:

- **TerraForged** by Thomas Holmes (won_ton_) — <https://github.com/TerraForged/TerraForged> (MIT)
- **ReTerraForged** by racoonman2 — <https://github.com/racoonman2/ReTerraForged> (MIT)

We study these for understanding and credit them everywhere ideas originate, but the implementation here is written from scratch. See [`LICENSE`](LICENSE) for license terms.

## Publishing (TODO for project owner)

For CI release publishing, set the following GitHub repository secrets:

- `MODRINTH_TOKEN` — Modrinth API token (Settings → Account → Personal access tokens)
- `CURSEFORGE_TOKEN` — CurseForge API token

Without these the release workflow will skip the corresponding upload step.

## License

[MIT](LICENSE).
