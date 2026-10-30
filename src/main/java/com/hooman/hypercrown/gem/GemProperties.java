package com.hooman.hypercrown.gem;

import java.util.Map;

/** Repository of immutable inherent properties for each known gem. */
public final class GemProperties {
    private static final Map<String, GemData> GEM_DATA = Map.ofEntries(
            Map.entry("hypercrown:celestial_sapphire", new GemData(150.0, 80.0, 1.2)),
            Map.entry("hypercrown:apocalyptic_ruby", new GemData(250.0, 30.0, 0.9)),
            Map.entry("hypercrown:void_emerald", new GemData(300.0, 10.0, 0.5)),
            Map.entry("hypercrown:thunder_diamond", new GemData(200.0, 60.0, 1.1)),
            Map.entry("hypercrown:frost_topaz", new GemData(120.0, 95.0, 1.4)),
            Map.entry("hypercrown:solar_opal", new GemData(180.0, 70.0, 1.3)),
            Map.entry("hypercrown:shadow_amethyst", new GemData(160.0, 55.0, 1.0)),
            Map.entry("hypercrown:tectonic_jade", new GemData(220.0, 45.0, 1.05)),
            Map.entry("hypercrown:wind_sapphire", new GemData(130.0, 85.0, 1.25)),
            Map.entry("hypercrown:time_quartz", new GemData(400.0, 15.0, 2.0)));

    private GemProperties() {}

    public static GemData getGemData(String gemId) {
        return GEM_DATA.get(gemId);
    }

    public static class GemData {
        public final double aethericCharge;
        public final double resonanceLevel;
        public final double temporalStability;

        public GemData(double aethericCharge, double resonanceLevel, double temporalStability) {
            if (!Double.isFinite(aethericCharge) || aethericCharge < 0
                    || !Double.isFinite(resonanceLevel) || resonanceLevel < 0
                    || !Double.isFinite(temporalStability) || temporalStability <= 0) {
                throw new IllegalArgumentException("Gem properties must be finite and temporal stability must be positive");
            }
            this.aethericCharge = aethericCharge;
            this.resonanceLevel = resonanceLevel;
            this.temporalStability = temporalStability;
        }
    }
}