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

---

## Milestone 2 — Surface + Biomes

Goal: TerraScribe worlds now have varied biomes with correct surface blocks (grass on plains, sand on desert, snow on cold, gravel on ocean floor) and modded overworld biomes are auto-included.

### What Claude has already auto-verified

- All unit tests green (27 tests covering noise, heightmap, climate sampler, biome mapper).
- `./gradlew runServer` with `level-type=terrascribe:terrascribe` started in 1.735 s. The biome-discovery listener fired at server-start and logged: *"biome discovery: 53 overworld biomes across 9 climate buckets (0 blocked by config)"*. Spawn-area chunks generated cleanly, zero ERROR or FATAL lines.

### What you visually verify

| # | Step | Expected | Pass? |
|---|---|---|---|
| 1 | `./gradlew runClient` (Claude usually launches this). Create a new world with the **TerraScribe** world type as before. | World loads. | |
| 2 | Open F3 to show the biome under your feet. Walk in one direction for ~500 blocks. | You should see the biome name change at least 2-3 times. The biome should match what you see on the ground (e.g., "minecraft:desert" → sand around you, "minecraft:plains" → grass). | |
| 3 | Dig down a few blocks below grass-topped terrain. | You hit dirt (3 blocks), then stone. | |
| 4 | Walk into a desert biome (or use `/locate biome minecraft:desert` to teleport). | Surface is sand; digging shows sand for a few blocks. | |
| 5 | Find a snowy biome (e.g., `/locate biome minecraft:snowy_plains`). | Surface is snow blocks (not snow layer — actual snow block). | |
| 6 | Find an ocean. | The floor of the ocean (below the water) is gravel. | |
| 7 | Pay attention as you travel in the +Z and -Z directions for thousands of blocks. | Climate bands roughly perpendicular to Z — you should notice it gets either colder or hotter as you travel north/south, with bands of warmer-colder-warmer due to the sine-based latitude term. (Approximate band period: ~63,000 blocks.) | |
| 8 | Exit to title and close the game. | Clean shutdown, no terrascribe-related ERROR lines. | |

### Modded biomes (optional, requires a biome mod jar)

If you have a biome mod (BoP, Terralith, OTBYG), drop the jar into `run/mods/` and relaunch the dev client. Any biome that self-tags `minecraft:is_overworld` will be automatically picked up by the discovery listener. The log will show a higher count than the vanilla baseline of 53. This compat work is auto-verified by the discovery mechanism — no extra checklist item required.

### How to report

Reply **"M2 pass"** if 1-8 all check out. If any biome looks wrong, name it and describe (e.g., "desert had grass on top" or "snowy plains had no snow"). For modded biomes, note any mod you tried + whether it integrated.
