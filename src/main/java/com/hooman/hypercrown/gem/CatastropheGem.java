package com.hooman.hypercrown.gem;

import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.world.item.ItemStack;

/** The ten rare gems and their default timing. Cooldowns are stored per player. */
public enum CatastropheGem {
    CELESTIAL_SAPPHIRE("celestial_sapphire", 300, 60, true, false),
    APOCALYPTIC_RUBY("apocalyptic_ruby", 900, 100, true, false),
    VOID_EMERALD("void_emerald", 180, 0, false, false),
    THUNDER_DIAMOND("thunder_diamond", 60, 0, false, true),
    FROST_TOPAZ("frost_topaz", 600, 40, false, false),
    SOLAR_OPAL("solar_opal", 1200, 80, false, false),
    SHADOW_AMETHYST("shadow_amethyst", 720, 60, false, false),
    TECTONIC_JADE("tectonic_jade", 480, 0, false, false),
    WIND_SAPPHIRE("wind_sapphire", 900, 40, false, false),
    TIME_QUARTZ("time_quartz", 1800, 0, false, true);

    private final String id;
    private final int cooldownSeconds;
    private final int channelTicks;
    private final boolean targetBlock;
    private final boolean consumed;

    CatastropheGem(String id, int cooldownSeconds, int channelTicks, boolean targetBlock, boolean consumed) {
        this.id = id;
        this.cooldownSeconds = cooldownSeconds;
        this.channelTicks = channelTicks;
        this.targetBlock = targetBlock;
        this.consumed = consumed;
    }

    public String id() { return id; }
    public int cooldownSeconds() { return cooldownSeconds; }
    public int channelTicks() { return channelTicks; }
    public boolean targetBlock() { return targetBlock; }
    public boolean consumed() { return consumed; }
    public boolean requiresConfirmation() { return this == CELESTIAL_SAPPHIRE || this == APOCALYPTIC_RUBY; }

    public ItemStack stack() {
        return new ItemStack(ModRegistries.CATASTROPHE_GEMS.get(this).get());
    }

    public static CatastropheGem byId(String id) {
        for (CatastropheGem gem : values()) if (gem.id.equals(id)) return gem;
        return null;
    }
}
