package io.github.sammasr.terrascribe.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.sammasr.terrascribe.worldgen.biome.ClimateBucket.Coolness;
import io.github.sammasr.terrascribe.worldgen.biome.ClimateBucket.Wetness;
import io.github.sammasr.terrascribe.worldgen.biome.climate.Climate;
import org.junit.jupiter.api.Test;

class BiomeMapperTest {

    private final BiomeMapper mapper = new BiomeMapper();

    @Test
    void coolnessFromTemperature() {
        assertEquals(Coolness.FROZEN, BiomeMapper.coolnessOf(-1f));
        assertEquals(Coolness.FROZEN, BiomeMapper.coolnessOf(-0.51f));
        assertEquals(Coolness.COLD, BiomeMapper.coolnessOf(-0.5f));
        assertEquals(Coolness.COLD, BiomeMapper.coolnessOf(-0.01f));
        assertEquals(Coolness.TEMPERATE, BiomeMapper.coolnessOf(0f));
        assertEquals(Coolness.TEMPERATE, BiomeMapper.coolnessOf(0.49f));
        assertEquals(Coolness.HOT, BiomeMapper.coolnessOf(0.5f));
        assertEquals(Coolness.HOT, BiomeMapper.coolnessOf(1f));
    }

    @Test
    void wetnessFromHumidity() {
        assertEquals(Wetness.ARID, BiomeMapper.wetnessOf(-1f));
        assertEquals(Wetness.ARID, BiomeMapper.wetnessOf(-0.34f));
        assertEquals(Wetness.MODERATE, BiomeMapper.wetnessOf(-0.33f));
        assertEquals(Wetness.MODERATE, BiomeMapper.wetnessOf(0f));
        assertEquals(Wetness.MODERATE, BiomeMapper.wetnessOf(0.32f));
        assertEquals(Wetness.WET, BiomeMapper.wetnessOf(0.33f));
        assertEquals(Wetness.WET, BiomeMapper.wetnessOf(1f));
    }

    @Test
    void bucketCombinesAxes() {
        ClimateBucket b = mapper.bucketFor(new Climate(0.7f, 0.5f));
        assertEquals(Coolness.HOT, b.coolness());
        assertEquals(Wetness.WET, b.wetness());
    }

    @Test
    void rejectsNullClimate() {
        assertThrows(NullPointerException.class, () -> mapper.bucketFor(null));
    }
}
