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
    public static final ModConfigSpec.BooleanValue GEMS_ENABLED, STRUCTURE_GEMS_ENABLED, ALLOW_GEMS_IN_SPAWN,
            ALLOW_GEMS_IN_CLAIMED_LAND, GEM_OWNER_BINDING, GEM_ENHANCEMENTS_ENABLED;
    public static final ModConfigSpec.DoubleValue DEEPSLATE_GEM_CHANCE, STRUCTURE_GEM_CHANCE,
            GEM_DAMAGE_MULTIPLIER, PLAYER_DAMAGE_FROM_GEMS;
    public static final ModConfigSpec.IntValue MAX_CRATER_SIZE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Global / multiplayer settings").push("general");
        CROWN_ENABLED = b.comment("Master switch. false = crown has no powers and cannot be forged.").define("crownEnabled", true);
        MAX_CROWNS = b.comment("Only one Hypercrown may be forged per world.").defineInRange("maxCrownsPerWorld", 1, 1, 1);
        ALLOW_TRANSFER = b.comment("If false, only the player who forged the Hypercrown can use its powers.").define("allowTransfer", false);
        ALLOW_UNFORGED = b.comment("Do crowns from the creative tab / commands (no forger) have powers?").define("allowUnforgedCrowns", true);
        KEEP_ON_DEATH = b.comment("Hypercrown returns to its wearer on respawn instead of dropping.").define("keepOnDeath", true);
        RITUAL_XP_LEVELS = b.comment("XP levels sacrificed in the awakening ritual.").defineInRange("ritualXpLevels", 50, 0, 1000);
        POWER_SCALE = b.comment("Multiplier applied to Hypercrown ability strengths.").defineInRange("powerScale", 1.0, 0.0, 10.0);
        b.pop();

        b.comment("Enable/disable individual abilities").push("abilities");
        for (Ability a : Ability.values()) ENABLED.put(a, b.define(a.key(), true));
        b.pop();

        b.comment("Active ability cooldowns in seconds").push("cooldowns");
        COOLDOWN.put(Ability.BLINK, b.defineInRange("blink", 5, 0, 3600));
        COOLDOWN.put(Ability.ANCHOR, b.defineInRange("anchor", 30, 0, 3600));
        COOLDOWN.put(Ability.ENERGY_BEAM, b.defineInRange("energy_beam", 10, 0, 3600));
        COOLDOWN.put(Ability.WEATHER_CONTROL, b.defineInRange("weather_control", 60, 0, 3600));
        COOLDOWN.put(Ability.HYPERSTEP, b.defineInRange("hyperstep", 12, 1, 3600));
        COOLDOWN.put(Ability.HYPERBREAK, b.defineInRange("hyperbreak", 30, 1, 3600));
        COOLDOWN.put(Ability.CROWN_GUARD, b.defineInRange("crown_guard", 35, 1, 3600));
        COOLDOWN.put(Ability.TACTICAL_AWARENESS, b.defineInRange("tactical_awareness", 15, 1, 3600));
        COOLDOWN.put(Ability.PHASE_DASH, b.defineInRange("phase_dash", 18, 0, 3600));
        COOLDOWN.put(Ability.TIME_SNARE, b.defineInRange("time_snare", 30, 0, 3600));
        COOLDOWN.put(Ability.CHAIN_LIGHTNING, b.defineInRange("chain_lightning", 25, 0, 3600));
        COOLDOWN.put(Ability.CROWN_NOVA, b.defineInRange("crown_nova", 35, 0, 3600));
        b.pop();

        b.comment("Ability tuning").push("tuning");
        SPEED_BONUS = b.comment("Hypercrown speed bonus (+3.0 = +300%)").defineInRange("speedBonus", 3.0, 0.0, 20.0);
        DAMAGE_BONUS = b.comment("Hypercrown damage bonus (+5.0 = +500%)").defineInRange("damageBonus", 5.0, 0.0, 100.0);
        DAMAGE_REDUCTION = b.comment("Hypercrown incoming damage reduction fraction").defineInRange("damageReduction", 0.9, 0.0, 1.0);
        REFLECT_FRACTION = b.comment("Hypercrown reflected damage fraction").defineInRange("reflectFraction", 0.5, 0.0, 10.0);
        INSTANT_KILL_CHANCE = b.defineInRange("instantKillChance", 0.01, 0.0, 1.0);
        IFRAME_TICKS = b.comment("Hypercrown invulnerability after being hurt, in ticks").defineInRange("invincibilityTicks", 40, 0, 400);
        BLINK_RANGE = b.defineInRange("blinkRange", 20, 1, 128);
        BEAM_RANGE = b.defineInRange("beamRange", 50, 1, 128);
        BEAM_DAMAGE = b.comment("Hypercrown beam damage at full charge").defineInRange("beamDamage", 60.0, 0.0, 10000.0);
        b.pop();

        b.comment("Ultra-rare catastrophe gems").push("gems");
        GEMS_ENABLED = b.comment("Enable gem activation, effects, and deepslate discovery.").define("enableGems", true);
        STRUCTURE_GEMS_ENABLED = b.comment("Enable gem additions to supported structure chest loot tables.").define("enableStructures", true);
        DEEPSLATE_GEM_CHANCE = b.comment("Spawn-rate multiplier (1.0 = 1 in 10,000 per eligible deepslate block; Fortune has no effect).")
                .defineInRange("deepslateGemChance", 1.0, 0.0, 10000.0);
        STRUCTURE_GEM_CHANCE = b.comment("Multiplier applied to each configured structure loot chance.")
                .defineInRange("structureGemChance", 1.0, 0.0, 100.0);
        GEM_DAMAGE_MULTIPLIER = b.comment("Multiplier applied to damage from catastrophe gems.")
                .defineInRange("gemDamageMultiplier", 1.0, 0.0, 100.0);
        PLAYER_DAMAGE_FROM_GEMS = b.comment("Damage multiplier for players struck by a catastrophe gem (self-use is not exempt).")
                .defineInRange("playerDamageFromGems", 0.5, 0.0, 10.0);
        GEM_OWNER_BINDING = b.comment("Bind each catastrophe gem to its first activating player.").define("ownerBinding", true);
        GEM_ENHANCEMENTS_ENABLED = b.comment("Allow the Celestial Focusing Table to make enhanced gems.").define("enhancementsEnabled", true);
        b.pop();

        b.comment("World interaction limits").push("gemWorld");
        ALLOW_GEMS_IN_SPAWN = b.comment("Allow catastrophe gems inside the world spawn protection radius.").define("allowGemsInSpawn", false);
        ALLOW_GEMS_IN_CLAIMED_LAND = b.comment("Allow gem effects inside land claimed by supported claim mods.").define("allowGemsInClaimedLand", true);
        MAX_CRATER_SIZE = b.comment("Maximum radius of gem-created craters and block clearing (blocks).")
                .defineInRange("maxCraterSize", 25, 1, 25);
        b.pop();

        SPEC = b.build();
    }

    public static boolean enabled(Ability a) {
        if (!CROWN_ENABLED.get()) return false;
        return ENABLED.get(a).get();
    }

    public static int cooldownTicks(Ability a) {
        ModConfigSpec.IntValue v = COOLDOWN.get(a);
        if (v == null) return 0;
        int configured = v.get() * 20;
        return switch (a) {
            case HYPERSTEP -> Math.max(12 * 20, configured);
            case HYPERBREAK -> Math.max(30 * 20, configured);
            case CROWN_GUARD -> Math.max(35 * 20, configured);
            case TACTICAL_AWARENESS -> Math.max(15 * 20, configured);
            default -> configured;
        };
    }

    public static double scaled(ModConfigSpec.DoubleValue v) { return v.get() * POWER_SCALE.get(); }
    public static double balancedGemStrength(ModConfigSpec.DoubleValue v, double maximum) {
        return Math.min(maximum, v.get()) ;
    }
}
