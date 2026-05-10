# TerraScribe — Playtest Checklist

Manual playtest checklist updated per milestone. The collaborating user runs through this and reports back.

---

## Milestone 0 — Skeleton loads

Goal: the mod compiles, packages, and loads into a vanilla-generated world without errors. No worldgen content yet.

### Setup

1. From the project root, run:

   ```bash
   ./gradlew runClient
   ```

   This launches a dev-mode Minecraft 1.21.1 client with TerraScribe already loaded. The first launch downloads Minecraft assets and takes ~30-60 seconds longer than subsequent launches.

### Checks

| # | Step | Expected | Pass? |
|---|---|---|---|
| 1 | At the Minecraft main menu, click **Mods**. | A "TerraScribe" entry appears in the mod list with version `0.0.1-alpha`, license `MIT`, author `sammasr`, and the canonical description. | |
| 2 | Look at the terminal where you ran `./gradlew runClient`. | You see a log line containing `[TerraScribe] mod constructor invoked — Milestone 0 skeleton loaded`. | |
| 3 | Look for `ERROR` lines in the same terminal output. | There are none related to `terrascribe`. (Unrelated NeoForge/Minecraft warnings are fine.) | |
| 4 | Back at the main menu, click **Singleplayer → Create New World**. Pick the **Default** world type (the **TerraScribe** world type doesn't exist yet — that's Milestone 1). Create and load a world. | World loads normally. You spawn in vanilla terrain. No crash, no error popup. | |
| 5 | Exit to title and close the game cleanly. | The terminal returns to a prompt with no stack trace. | |

### How to report

Reply **"M0 pass"** if all five checks pass. If anything fails, paste the failing terminal log lines (just the section that looks wrong — I'll ask for more if needed).

---

## Milestone 1 — Custom ChunkGenerator

Goal: "TerraScribe" appears as a selectable world type; selecting it produces a stone-only world with a rolling noise-driven heightmap and water at sea level.

### What Claude has already auto-verified (you don't need to repeat)

- `./gradlew build test` green — pure-math noise + heightmap layer has unit tests (16 of 16 pass).
- `./gradlew runServer` with `level-type=terrascribe:terrascribe` started cleanly, generated spawn-area chunks in under a second, saved 4 region files to `run/tsm1/`, and produced zero ERROR / FATAL lines. Codec deserialization, WorldPreset registry binding, and `fillFromNoise` all work end-to-end.

### What you visually verify

| # | Step | Expected | Pass? |
|---|---|---|---|
| 1 | `./gradlew runClient` (Claude usually launches this for you). At main menu → **Singleplayer → Create New World → More tab**. | A "TerraScribe" entry appears in the **World Type** dropdown (the dropdown reads "World Type: TerraScribe"). The vanilla types are still there too. | |
| 2 | Select **TerraScribe**, leave the seed blank, click **Create New World**. | World loads, you spawn into a stone world. No crash, no error popup. | |
| 3 | Open F3 (debug overlay) and look at the heightmap line. Walk around for ~30 seconds. | Terrain rolls — clearly varying heights, not a single flat plane. Approximate y-range you should see in F3: 40–100. | |
| 4 | Drop a few blocks deep (F3 + dig) or use `/setblock` to inspect a column below the surface. | Stone below the surface all the way down to bedrock at y=-64. No dirt/grass surface yet (that's Milestone 2). | |
| 5 | Walk until you find a valley low enough to be below sea level (y < 63). | Water fills the valley up to y=63. | |
| 6 | Exit to title and close the game. | Clean shutdown, no terrascribe-related ERROR lines in the terminal. | |

### How to report

Reply **"M1 pass"** if all six pass and I'll start Milestone 2 (surface rules + climate-driven biome assignment with modded-biome discovery) in the next session. If anything looks off, screenshot or describe the terrain — for terrain quality, a quick sentence is enough ("looks like flat boring rolling hills" vs "everything is at exactly y=70" vs "checkerboard pattern").
