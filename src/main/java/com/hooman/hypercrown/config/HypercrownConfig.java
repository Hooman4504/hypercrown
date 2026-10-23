package com.hooman.hypercrown.config;

import com.hooman.hypercrown.ability.Ability;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

/** Server config (world/serverconfig/hypercrown-server.toml). */
public final class HypercrownConfig {
    private static final Map<Ability, ModConfigSpec.BooleanValue> ENABLED = new EnumMap<>(Ability.class);
    private static final Map<Ability, ModConfigSpec.IntValue> COOLDOWN = new EnumMap<>(Ability.class);

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CROWN_ENABLED, ALLOW_TRANSFER, ALLOW_UNFORGED, KEEP_ON_DEATH;
    public static final ModConfigSpec.IntValue MAX_CROWNS, RITUAL_XP_LEVELS, BLINK_RANGE, BEAM_RANGE, IFRAME_TICKS;
    public static final ModConfigSpec.DoubleValue POWER_SCALE, SPEED_BONUS, DAMAGE_BONUS, DAMAGE_REDUCTION,
            REFLECT_FRACTION, INSTANT_KILL_CHANCE, BEAM_DAMAGE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Global / multiplayer settings").push("general");
        CROWN_ENABLED = b.comment("Master switch. false = crown has no powers and cannot be forged.").define("crownEnabled", true);
        MAX_CROWNS = b.comment("How many crowns may be forged in this world.").defineInRange("maxCrownsPerWorld", 1, 0, 64);
        ALLOW_TRANSFER = b.comment("If false, only the player who forged a crown can use its powers.").define("allowTransfer", false);
        ALLOW_UNFORGED = b.comment("Do crowns from the creative tab / commands (no forger) have powers?").define("allowUnforgedCrowns", true);
        KEEP_ON_DEATH = b.comment("Crown returns to its wearer on respawn instead of dropping.").define("keepOnDeath", true);
        RITUAL_XP_LEVELS = b.comment("XP levels sacrificed in the awakening ritual.").defineInRange("ritualXpLevels", 50, 0, 1000);
        POWER_SCALE = b.comment("Multiplier applied to all numeric ability strengths (0.5 = half power).").defineInRange("powerScale", 1.0, 0.0, 10.0);
        b.pop();

        b.comment("Enable/disable individual abilities").push("abilities");
        for (Ability a : Ability.values()) ENABLED.put(a, b.define(a.key(), true));
        b.pop();

        b.comment("Active ability cooldowns in seconds").push("cooldowns");
        COOLDOWN.put(Ability.BLINK, b.defineInRange("blink", 5, 0, 3600));
        COOLDOWN.put(Ability.ANCHOR, b.defineInRange("anchor", 30, 0, 3600));
        COOLDOWN.put(Ability.ENERGY_BEAM, b.defineInRange("energy_beam", 10, 0, 3600));
        COOLDOWN.put(Ability.WEATHER_CONTROL, b.defineInRange("weather_control", 60, 0, 3600));
        b.pop();

        b.comment("Ability tuning").push("tuning");
        SPEED_BONUS = b.comment("+X * base speed (3.0 = +300%)").defineInRange("speedBonus", 3.0, 0.0, 20.0);
        DAMAGE_BONUS = b.comment("+X * total melee damage (5.0 = +500%)").defineInRange("damageBonus", 5.0, 0.0, 100.0);
        DAMAGE_REDUCTION = b.comment("Fraction of incoming damage removed").defineInRange("damageReduction", 0.9, 0.0, 1.0);
        REFLECT_FRACTION = b.comment("Fraction of incoming damage reflected").defineInRange("reflectFraction", 0.5, 0.0, 10.0);
        INSTANT_KILL_CHANCE = b.defineInRange("instantKillChance", 0.01, 0.0, 1.0);
        IFRAME_TICKS = b.comment("Invulnerability after being hurt, in ticks").defineInRange("invincibilityTicks", 40, 0, 400);
        BLINK_RANGE = b.defineInRange("blinkRange", 20, 1, 128);
        BEAM_RANGE = b.defineInRange("beamRange", 50, 1, 128);
        BEAM_DAMAGE = b.comment("Damage at full charge").defineInRange("beamDamage", 60.0, 0.0, 10000.0);
        b.pop();

        SPEC = b.build();
    }

    public static boolean enabled(Ability a) { return CROWN_ENABLED.get() && ENABLED.get(a).get(); }

    public static int cooldownTicks(Ability a) {
        ModConfigSpec.IntValue v = COOLDOWN.get(a);
        return v == null ? 0 : v.get() * 20;
    }

    public static double scaled(ModConfigSpec.DoubleValue v) { return v.get() * POWER_SCALE.get(); }
}
