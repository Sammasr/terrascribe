package io.github.sammasr.terrascribe.worldgen.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RegionCacheTest {

    private static class CountingBuilder implements RegionCache.Builder {
        int buildCount = 0;
        final int size;
        CountingBuilder(final int size) {
            this.size = size;
        }
        @Override
        public RegionHeightmap build(final int regionX, final int regionZ) {
            this.buildCount++;
            final float[] heights = new float[this.size * this.size];
            for (int i = 0; i < heights.length; i++) {
                heights[i] = (float) (regionX * 1000 + regionZ);
            }
            return new RegionHeightmap(regionX, regionZ, this.size, heights);
        }
    }

    @Test
    void rejectsBadArguments() {
        final RegionCache.Builder dummy = (x, z) -> new RegionHeightmap(x, z, 4, new float[16]);
        assertThrows(IllegalArgumentException.class, () -> new RegionCache(0, 8, dummy));
        assertThrows(IllegalArgumentException.class, () -> new RegionCache(8, 0, dummy));
        assertThrows(IllegalArgumentException.class, () -> new RegionCache(8, 8, null));
    }

    @Test
    void buildsRegionOnMissAndCachesOnHit() {
        final CountingBuilder builder = new CountingBuilder(8);
        final RegionCache cache = new RegionCache(8, 4, builder);

        final RegionHeightmap r1 = cache.regionAt(0, 0);
        final RegionHeightmap r2 = cache.regionAt(0, 0);
        assertSame(r1, r2, "second lookup should return cached instance");
        assertEquals(1, builder.buildCount, "builder should run once");
    }

    @Test
    void heightAtMapsBlockCoordsToRegions() {
        final CountingBuilder builder = new CountingBuilder(16);
        final RegionCache cache = new RegionCache(16, 16, builder);

        // Region (0, 0) covers blocks [0, 16) × [0, 16). Region (1, 1) covers [16, 32) × [16, 32).
        // Builder fills regionX*1000 + regionZ, so we can verify coords map right.
        assertEquals(0, cache.heightAt(0, 0));
        assertEquals(0, cache.heightAt(15, 15));
        assertEquals(1001, cache.heightAt(16, 16));
        assertEquals(1001, cache.heightAt(31, 31));
        // Negative coords use floorDiv → region (-1, -1) → builder fills -1000 + -1 = -1001.
        assertEquals(-1001, cache.heightAt(-1, -1));
    }

    @Test
    void evictsLeastRecentlyUsedWhenFull() {
        final CountingBuilder builder = new CountingBuilder(8);
        final RegionCache cache = new RegionCache(8, 3, builder);

        // Fill cache: regions (0,0), (1,0), (2,0).
        cache.regionAt(0, 0);
        cache.regionAt(1, 0);
        cache.regionAt(2, 0);
        assertEquals(3, cache.size());

        // Touch (0,0) so (1,0) becomes least-recently-used.
        cache.regionAt(0, 0);

        // Add (3,0) — should evict (1,0).
        cache.regionAt(3, 0);
        assertEquals(3, cache.size());
        assertEquals(4, builder.buildCount, "four builds so far");

        // (1,0) gone — re-requesting it triggers another build.
        cache.regionAt(1, 0);
        assertEquals(5, builder.buildCount, "(1,0) was evicted and had to be rebuilt");
    }

    @Test
    void clearEmptiesCache() {
        final CountingBuilder builder = new CountingBuilder(8);
        final RegionCache cache = new RegionCache(8, 8, builder);
        cache.regionAt(0, 0);
        cache.regionAt(1, 0);
        assertEquals(2, cache.size());
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void regionHeightmapBoundsChecks() {
        final RegionHeightmap r = new RegionHeightmap(5, 5, 4, new float[16]);
        // Block range for region (5,5) at size 4: blocks [20,24) × [20,24).
        assertTrue(r.at(20, 20) == 0f);
        assertTrue(r.at(23, 23) == 0f);
        assertThrows(IllegalArgumentException.class, () -> r.at(24, 20));
        assertThrows(IllegalArgumentException.class, () -> r.at(19, 20));
    }
}
