package io.github.sammasr.terrascribe.worldgen.chunk;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded LRU cache of {@link RegionHeightmap}s. Builds a region on miss using a caller-
 * supplied {@link Builder} (which generates the base noise heightmap and applies erosion).
 *
 * <p>Thread-safe: a single {@code synchronized} on the cache map serializes lookups and
 * builds. Chunk generation runs on Minecraft's worker pool so multiple threads can call
 * {@link #heightAt} concurrently; serializing here is simpler than a striped lock for M3
 * and is rarely the bottleneck because most lookups are cache hits.
 *
 * <p>Memory profile at the M3 default (256×256 floats, 256-entry cap): each region is
 * ~256 KB, so the cap is ~64 MB. That's slightly over the spec §15 "~32 MB" target — we
 * can tighten by halving region size or evicting more aggressively if profiling demands.
 */
public final class RegionCache {

    public interface Builder {
        /**
         * Builds and erodes a region. Called under the cache's monitor — keep this fast
         * (typically tens of milliseconds for a 256-side region).
         *
         * @param regionX region x-coord (block-region = regionX * size)
         * @param regionZ region z-coord (block-region = regionZ * size)
         * @return a fully populated, eroded {@link RegionHeightmap}
         */
        RegionHeightmap build(int regionX, int regionZ);
    }

    private final int regionSize;
    private final int maxEntries;
    private final Builder builder;
    private final LinkedHashMap<Long, RegionHeightmap> cache;

    public RegionCache(final int regionSize, final int maxEntries, final Builder builder) {
        if (regionSize < 1) {
            throw new IllegalArgumentException("regionSize must be >= 1, got " + regionSize);
        }
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be >= 1, got " + maxEntries);
        }
        if (builder == null) {
            throw new IllegalArgumentException("builder must not be null");
        }
        this.regionSize = regionSize;
        this.maxEntries = maxEntries;
        this.builder = builder;
        // access-order LRU (third constructor arg = true).
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<Long, RegionHeightmap> eldest) {
                return size() > RegionCache.this.maxEntries;
            }
        };
    }

    public int regionSize() {
        return this.regionSize;
    }

    public synchronized RegionHeightmap regionAt(final int regionX, final int regionZ) {
        final long key = packKey(regionX, regionZ);
        RegionHeightmap region = this.cache.get(key);
        if (region == null) {
            region = this.builder.build(regionX, regionZ);
            this.cache.put(key, region);
        }
        return region;
    }

    /**
     * Returns the eroded height at the given block coordinate, building and caching the
     * containing region on miss.
     */
    public int heightAt(final int blockX, final int blockZ) {
        final int regionX = Math.floorDiv(blockX, this.regionSize);
        final int regionZ = Math.floorDiv(blockZ, this.regionSize);
        return Math.round(regionAt(regionX, regionZ).at(blockX, blockZ));
    }

    public synchronized int size() {
        return this.cache.size();
    }

    public synchronized void clear() {
        this.cache.clear();
    }

    private static long packKey(final int regionX, final int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
    }
}
