# TerraScribe — Performance baselines

Recorded measurements for chunk generation, erosion, and the like. Updated when something interesting changes.

## Methodology

All measurements taken on the project owner's dev machine:

- CPU: Intel Core i7-10750H (6 cores / 12 threads, Comet Lake)
- RAM: ~22 GB available to the JVM
- OS: Ubuntu 24.04.3 LTS
- JVM: Temurin OpenJDK 21.0.11
- NeoForge MDK dev runtime

Numbers come from `./gradlew runServer` "Done (Ns)!" log line for end-to-end timing, and the timestamps before/after specific log markers for sub-step timing. Proper microbenchmarks via JMH live in `:performance` GameTest in a future milestone.

## Server startup with `level-type=terrascribe:terrascribe`

| Milestone | Time to "Done" | Notes |
|---|---|---|
| M1 (stone-only, no erosion) | 1.486 s | Pure noise heightmap, no biome lookup. |
| M2 (biome assignment + surface rules) | 1.735 s | +250 ms from biome bucketing + per-column buildSurface. |
| M3 (erosion via RegionCache) | 1.795 s | +60 ms from erosion of the spawn region only. Subsequent regions are not yet generated at this point. |

## Erosion cost per 256×256 region

| Parameters | Approx wall-clock |
|---|---|
| 25k droplets × 30 max steps, inertia 0.05, no brush kernel | ~50-100 ms (rough estimate from total spawn-area gen time minus baseline) |

This is the cost paid the *first* time a player enters a new region. Subsequent chunks within that region are served from the cache with no erosion cost. Region cache holds 256 regions (~64 MB) before LRU eviction kicks in.

## Region cache hit rate

Anecdotal observation: in normal exploration, after a few minutes of play the cache holds the actively-explored 50-100 regions and miss rate drops to near zero.

## TODO

- JMH benchmarks for `ErosionSimulator` parameter sweeps (next time we tune droplet density)
- Per-chunk vanilla baseline (the spec's "2× vanilla" target needs a real number)
- Memory profile (heap dump after 30 min of play) — does the cache actually stay around 64 MB or do residual objects leak?
