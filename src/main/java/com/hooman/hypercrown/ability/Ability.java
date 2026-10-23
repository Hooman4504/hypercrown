package com.hooman.hypercrown.ability;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Every crown ability. Used as the key for config toggles, cooldowns, HUD and lang.
 * NOTE: cooldowns are saved by ordinal, so append new entries at the end.
 */
public enum Ability {
    // Passive
    FLIGHT(false), SPEED(false), WEAPON_MASTERY(false), CRITICAL_MASTERY(false), KNOCKBACK_IMMUNITY(false),
    DAMAGE_REDUCTION(false), ELEMENTAL_IMMUNITY(false), STATUS_IMMUNITY(false), SURVIVAL(false),
    INSTANT_MINING(false), AUTO_SMELT(false), DAMAGE_REFLECTION(false), INSTANT_KILL(false),
    MOB_PACIFICATION(false), INVINCIBILITY_FRAMES(false),
    // Active (keybind)
    BLINK(true), ANCHOR(true), ENERGY_BEAM(true), WEATHER_CONTROL(true);

    public final boolean active;

    Ability(boolean active) { this.active = active; }

    public String key() { return name().toLowerCase(Locale.ROOT); }

    public String langKey() { return "ability.hypercrown." + key(); }

    public static final List<Ability> ACTIVES = Arrays.stream(values()).filter(a -> a.active).toList();
}
