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
