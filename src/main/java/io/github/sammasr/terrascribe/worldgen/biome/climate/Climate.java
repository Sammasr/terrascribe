package io.github.sammasr.terrascribe.worldgen.biome.climate;

/**
 * Per-location climate values used to drive biome assignment.
 *
 * <p>Both fields are normalized to {@code [-1, 1]}:
 * <ul>
 *   <li>{@code temperature}: {@code -1} = polar / frozen, {@code 0} = temperate, {@code 1} = tropical</li>
 *   <li>{@code humidity}: {@code -1} = arid, {@code 0} = moderate, {@code 1} = humid</li>
 * </ul>
 *
 * <p>Records are immutable and {@code equals}/{@code hashCode} are value-based, which is what
 * we want for deterministic worldgen.
 */
public record Climate(float temperature, float humidity) {

    public Climate {
        if (Float.isNaN(temperature) || Float.isNaN(humidity)) {
            throw new IllegalArgumentException(
                    "climate values must not be NaN: t=" + temperature + " h=" + humidity);
        }
    }
}
