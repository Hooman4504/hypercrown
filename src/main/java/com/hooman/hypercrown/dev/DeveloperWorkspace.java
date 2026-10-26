package com.hooman.hypercrown.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.hooman.hypercrown.ability.Ability;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Serializable, versioned authoring data stored with each world. */
public final class DeveloperWorkspace {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_JSON_CHARS = 30_000;
    public static final int MAX_SNAPSHOT_JSON_CHARS = 250_000;
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public int schemaVersion = SCHEMA_VERSION;
    public boolean debugEnabled;
    public List<AbilityDraft> abilities = new ArrayList<>();
    public List<ParticlePreset> particles = new ArrayList<>();
    public List<VisualEffect> visualEffects = new ArrayList<>();
    public List<SoundPreset> sounds = new ArrayList<>();
    public List<ConfigBackup> configBackups = new ArrayList<>();

    public static DeveloperWorkspace defaults() {
        DeveloperWorkspace workspace = new DeveloperWorkspace();
        for (Ability ability : Ability.values()) {
            AbilityDraft draft = new AbilityDraft();
            draft.id = ability.key();
            draft.name = title(ability.key());
            draft.description = "Registered Hypercrown ability: " + ability.key();
            draft.core = true;
            draft.active = ability.active;
            draft.enabled = true;
            draft.cooldownSeconds = defaultCooldown(ability);
            draft.action = "damage";
            draft.target = ability.active ? "nearest_enemy" : "self";
            draft.trigger = ability.active ? "keybind" : "passive";
            draft.condition = "requires_registered_ability";
            draft.range = 8.0;
            draft.particle = "minecraft:end_rod";
            draft.sound = "minecraft:entity.player.levelup";
            workspace.abilities.add(draft);
        }

        ParticlePreset ring = new ParticlePreset();
        ring.id = "crown_ring";
        ring.name = "Crown Ring Preview";
        ring.particle = "minecraft:dust";
        ring.shape = "ring";
        ring.position = "player";
        ring.y = 1.5;
        ring.count = 24;
        ring.size = 1.0;
        ring.red = 1.0;
        ring.green = 0.72;
        ring.blue = 0.18;
        ring.speed = 0.02;
        ring.durationTicks = 20;
        ring.intervalTicks = 2;
        workspace.particles.add(ring);

        workspace.visualEffects.add(visual("crown_aura", "Crown Aura", 0.8, 0.72, 0.18,
                "minecraft:dust", "minecraft:end_rod", "minecraft:electric_spark"));
        workspace.visualEffects.add(visual("crown_beam", "Crown Beam", 1, 0.78, 0.18,
                "minecraft:dust", "minecraft:end_rod", "minecraft:electric_spark", "minecraft:enchant", "minecraft:flash", "minecraft:explosion"));
        workspace.visualEffects.add(visual("crown_abilities", "Crown Ability Bursts", 0.8, 0.72, 0.18,
                "minecraft:flash", "minecraft:reverse_portal", "minecraft:totem_of_undying", "minecraft:end_rod", "minecraft:dust"));
        workspace.visualEffects.add(visual("gem_celestial_sapphire", "Celestial Sapphire", 0.2, 0.55, 1,
                "minecraft:flash", "minecraft:explosion_emitter", "minecraft:electric_spark", "minecraft:end_rod"));
        workspace.visualEffects.add(visual("gem_apocalyptic_ruby", "Apocalyptic Ruby", 1, 0.12, 0.08,
                "minecraft:flash", "minecraft:explosion_emitter", "minecraft:campfire_cosy_smoke", "minecraft:flame", "minecraft:soul_fire_flame", "minecraft:lava"));
        workspace.visualEffects.add(visual("gem_void_emerald", "Void Emerald", 0.55, 0.12, 1,
                "minecraft:sweep_attack", "minecraft:reverse_portal", "minecraft:soul_fire_flame"));
        workspace.visualEffects.add(visual("gem_thunder_diamond", "Thunder Diamond", 0.35, 0.8, 1,
                "minecraft:electric_spark", "minecraft:end_rod", "minecraft:flash", "minecraft:enchant"));
        workspace.visualEffects.add(visual("gem_frost_topaz", "Frost Topaz", 0.35, 0.85, 1, "minecraft:snowflake"));
        workspace.visualEffects.add(visual("gem_solar_opal", "Solar Opal", 1, 0.55, 0.12, "minecraft:flash", "minecraft:flame"));
        workspace.visualEffects.add(visual("gem_shadow_amethyst", "Shadow Amethyst", 0.55, 0.12, 1,
                "minecraft:portal", "minecraft:reverse_portal"));
        workspace.visualEffects.add(visual("gem_tectonic_jade", "Tectonic Jade", 0.25, 0.85, 0.35,
                "minecraft:cloud", "minecraft:block"));
        workspace.visualEffects.add(visual("gem_wind_sapphire", "Wind Sapphire", 0.55, 0.85, 1, "minecraft:cloud"));
        workspace.visualEffects.add(visual("gem_time_quartz", "Time Quartz", 0.85, 0.75, 1, "minecraft:end_rod"));

        SoundPreset chime = new SoundPreset();
        chime.id = "crown_chime";
        chime.name = "Crown Chime";
        chime.sound = "minecraft:block.amethyst_block.chime";
        chime.trigger = "cast";
        chime.attachment = "player";
        chime.volume = 0.75;
        chime.pitch = 1.2;
        workspace.sounds.add(chime);
        return workspace;
    }

    private static VisualEffect visual(String id, String name, double red, double green, double blue, String... sourceParticles) {
        VisualEffect effect = new VisualEffect();
        effect.id = id; effect.name = name;
        for (String source : sourceParticles) {
            VisualLayer layer = new VisualLayer();
            layer.id = source.substring(source.indexOf(':') + 1);
            layer.name = title(layer.id);
            layer.sourceParticle = source;
            layer.particle = "keep";
            layer.red = red; layer.green = green; layer.blue = blue;
            effect.layers.add(layer);
        }
        return effect;
    }

    public static DeveloperWorkspace parse(String json) {
        if (json == null || json.length() > MAX_JSON_CHARS) throw new JsonParseException("Workspace is too large (maximum 30,000 characters).");
        DeveloperWorkspace workspace = GSON.fromJson(JsonParser.parseString(json), DeveloperWorkspace.class);
        if (workspace == null) throw new JsonParseException("Workspace must be a JSON object.");
        if (workspace.schemaVersion > SCHEMA_VERSION || workspace.schemaVersion < 1)
            throw new JsonParseException("Unsupported workspace version " + workspace.schemaVersion + ".");
        workspace.schemaVersion = SCHEMA_VERSION;
        if (workspace.abilities == null) workspace.abilities = new ArrayList<>();
        if (workspace.particles == null) workspace.particles = new ArrayList<>();
        if (workspace.visualEffects == null) workspace.visualEffects = new ArrayList<>();
        if (workspace.sounds == null) workspace.sounds = new ArrayList<>();
        if (workspace.configBackups == null) workspace.configBackups = new ArrayList<>();
        workspace.abilities = new ArrayList<>(workspace.abilities);
        workspace.particles = new ArrayList<>(workspace.particles);
        workspace.visualEffects = new ArrayList<>(workspace.visualEffects);
        workspace.sounds = new ArrayList<>(workspace.sounds);
        workspace.configBackups = new ArrayList<>(workspace.configBackups);
        if (workspace.abilities.size() > 64 || workspace.particles.size() > 64 || workspace.sounds.size() > 64 || workspace.visualEffects.size() > 32)
            throw new JsonParseException("A workspace can contain at most 64 abilities, particle presets, and sound presets.");
        validateIds(workspace.abilities.stream().map(a -> a.id).toList(), "ability");
        validateIds(workspace.particles.stream().map(p -> p.id).toList(), "particle preset");
        validateIds(workspace.sounds.stream().map(s -> s.id).toList(), "sound preset");
        validateIds(workspace.visualEffects.stream().map(v -> v.id).toList(), "visual effect");
        for (AbilityDraft a : workspace.abilities) a.normalize();
        for (ParticlePreset p : workspace.particles) p.normalize();
        for (VisualEffect v : workspace.visualEffects) v.normalize();
        for (VisualEffect visual : defaults().visualEffects) {
            VisualEffect existing = workspace.visualEffects.stream().filter(v -> v.id.equals(visual.id)).findFirst().orElse(null);
            if (existing == null) {
                workspace.visualEffects.add(visual);
            } else if (existing.layers.isEmpty()) {
                existing.layers = GSON.fromJson(GSON.toJson(visual.layers), new com.google.gson.reflect.TypeToken<List<VisualLayer>>() {}.getType());
                // Keep the original source mix when upgrading the old one-particle profile format.
                for (VisualLayer layer : existing.layers) {
                    layer.enabled = existing.enabled;
                    layer.density = existing.density;
                    layer.spread = existing.spread;
                    layer.size = existing.size;
                    layer.red = existing.red; layer.green = existing.green; layer.blue = existing.blue;
                }
            }
        }
        for (SoundPreset s : workspace.sounds) s.normalize();
        List<ConfigBackup> backups = new ArrayList<>();
        for (ConfigBackup backup : workspace.configBackups) {
            if (backup == null || backup.valuesJson == null || backup.valuesJson.length() > MAX_JSON_CHARS)
                throw new JsonParseException("Configuration backup entry is invalid.");
            try { JsonParser.parseString(backup.valuesJson).getAsJsonObject(); }
            catch (RuntimeException exception) { throw new JsonParseException("Configuration backup is not a JSON object.", exception); }
            if (backups.size() < 5) backups.add(backup);
        }
        workspace.configBackups = backups;
        return workspace;
    }

    public static String title(String id) {
        String[] words = id.replace('_', ' ').replace('-', ' ').split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return result.toString();
    }

    private static int defaultCooldown(Ability ability) {
        return switch (ability) {
            case BLINK -> 5;
            case ANCHOR, TIME_SNARE -> 30;
            case ENERGY_BEAM -> 10;
            case WEATHER_CONTROL -> 60;
            case HYPERSTEP -> 12;
            case HYPERBREAK -> 30;
            case CROWN_NOVA, CROWN_GUARD -> 35;
            case TACTICAL_AWARENESS -> 15;
            case PHASE_DASH -> 18;
            case CHAIN_LIGHTNING -> 25;
            default -> 0;
        };
    }

    private static void validateIds(List<String> ids, String kind) {
        if (ids.stream().anyMatch(Objects::isNull)) throw new JsonParseException("A " + kind + " is missing its id.");
        if (ids.stream().anyMatch(id -> !id.matches("[a-z0-9_.-]{1,48}")))
            throw new JsonParseException("Each " + kind + " id must contain 1–48 lowercase letters, digits, dots, underscores, or dashes.");
        if (ids.stream().distinct().count() != ids.size()) throw new JsonParseException("Duplicate " + kind + " id.");
    }

    public static final class AbilityDraft {
        public String id = "custom_ability";
        public String name = "Custom Ability";
        public String description = "";
        public boolean core;
        public boolean active = true;
        public boolean enabled = true;
        public double cooldownSeconds = 5;
        public double manaCost;
        public double damage = 4;
        public double range = 8;
        public int durationTicks = 40;
        public String trigger = "manual";
        public String condition = "";
        public String target = "nearest_enemy";
        public String action = "damage";
        public String effect = "minecraft:slowness";
        public String particle = "minecraft:end_rod";
        public String sound = "minecraft:entity.player.levelup";
        public String chain = "";

        public void normalize() {
            name = text(name, "Unnamed ability", 80);
            description = text(description, "", 240);
            cooldownSeconds = clamp(cooldownSeconds, 0, 3600, 5);
            manaCost = clamp(manaCost, 0, 100_000, 0);
            damage = clamp(damage, 0, 1_000, 4);
            range = clamp(range, 0, 64, 8);
            durationTicks = Math.max(0, Math.min(72_000, durationTicks));
            trigger = text(trigger, "manual", 40);
            condition = text(condition, "", 160);
            target = oneOf(target, "self", "nearest_enemy", "all_enemies");
            action = oneOf(action, "damage", "effect", "launch", "heal", "visual");
            effect = text(effect, "minecraft:slowness", 128);
            particle = text(particle, "minecraft:end_rod", 128);
            sound = text(sound, "minecraft:entity.player.levelup", 128);
            chain = text(chain, "", 48);
        }
    }

    public static final class ParticlePreset {
        public String id = "new_particle";
        public String name = "New Particle Preset";
        public String particle = "minecraft:end_rod";
        public String position = "player";
        public double x, y, z;
        public int delayTicks;
        public int durationTicks = 1;
        public int intervalTicks = 1;
        public int count = 20;
        public double vx, vy, vz;
        public double speed = 0.02;
        public double accelerationX, accelerationY, accelerationZ;
        public double randomness;
        public double radius = 1.5;
        public double height = 2;
        public double size = 1;
        public double red = 1, green = 1, blue = 1, alpha = 1;
        public String colorAnimation = "none";
        public String shape = "sphere";
        public String condition = "always";
        public String path = "0,0,0;1,1,0;0,2,0";

        public void normalize() {
            name = text(name, "Unnamed particle preset", 80);
            particle = text(particle, "minecraft:end_rod", 128);
            position = oneOf(position, "player", "target", "world");
            x = clamp(x, -30_000_000, 30_000_000, 0); y = clamp(y, -30_000_000, 30_000_000, 0); z = clamp(z, -30_000_000, 30_000_000, 0);
            delayTicks = Math.max(0, Math.min(72_000, delayTicks));
            durationTicks = Math.max(1, Math.min(200, durationTicks));
            intervalTicks = Math.max(1, Math.min(200, intervalTicks));
            count = Math.max(1, Math.min(120, count));
            vx = clamp(vx, -4, 4, 0); vy = clamp(vy, -4, 4, 0); vz = clamp(vz, -4, 4, 0);
            speed = clamp(speed, 0, 4, 0.02);
            accelerationX = clamp(accelerationX, -1, 1, 0); accelerationY = clamp(accelerationY, -1, 1, 0); accelerationZ = clamp(accelerationZ, -1, 1, 0);
            randomness = clamp(randomness, 0, 8, 0);
            radius = clamp(radius, 0, 64, 1.5);
            height = clamp(height, 0, 64, 2);
            size = clamp(size, 0.01, 4, 1);
            red = clamp(red, 0, 1, 1); green = clamp(green, 0, 1, 1); blue = clamp(blue, 0, 1, 1); alpha = clamp(alpha, 0, 1, 1);
            colorAnimation = oneOf(colorAnimation, "none", "fade", "pulse", "rainbow");
            shape = oneOf(shape, "sphere", "cylinder", "cone", "line", "ring", "path");
            condition = text(condition, "always", 120);
            path = text(path, "0,0,0;1,1,0;0,2,0", 256);
        }
    }

    public static final class SoundPreset {
        public String id = "new_sound";
        public String name = "New Sound Preset";
        public String sound = "minecraft:block.amethyst_block.chime";
        public double volume = 1;
        public double pitch = 1;
        public double distance = 16;
        public String trigger = "cast";
        public String attachment = "player";
        public double x, y, z;
        public double volumeVariance;
        public double pitchVariance;
        public String soundPool = "";
        public String sequence = "";

        public void normalize() {
            name = text(name, "Unnamed sound preset", 80);
            sound = text(sound, "minecraft:block.amethyst_block.chime", 128);
            volume = clamp(volume, 0, 4, 1);
            pitch = clamp(pitch, 0.01, 2, 1);
            distance = clamp(distance, 0, 128, 16);
            trigger = text(trigger, "cast", 40);
            attachment = oneOf(attachment, "player", "target", "world");
            x = clamp(x, -30_000_000, 30_000_000, 0); y = clamp(y, -30_000_000, 30_000_000, 0); z = clamp(z, -30_000_000, 30_000_000, 0);
            volumeVariance = clamp(volumeVariance, 0, 1, 0);
            pitchVariance = clamp(pitchVariance, 0, 1, 0);
            soundPool = text(soundPool, "", 512);
            sequence = text(sequence, "", 1024);
        }
    }

    /** Tuning applied to a built-in crown or gem particle effect, not just a preview preset. */
    public static final class VisualEffect {
        public String id = "crown_aura";
        public String name = "Crown Aura";
        public String particle = "keep";
        public boolean enabled = true;
        public double density = 1;
        public double spread = 1;
        public double size = 1;
        public double red = 1, green = 0.75, blue = 0.2;
        public List<VisualLayer> layers = new ArrayList<>();

        public void normalize() {
            id = text(id, "crown_aura", 48).toLowerCase(Locale.ROOT);
            name = text(name, title(id), 80);
            particle = text(particle, "keep", 128);
            density = clamp(density, 0, 4, 1);
            spread = clamp(spread, 0, 4, 1);
            size = clamp(size, 0.1, 4, 1);
            red = clamp(red, 0, 1, 1); green = clamp(green, 0, 1, 0.75); blue = clamp(blue, 0, 1, 0.2);
            if (layers == null) layers = new ArrayList<>();
            layers = new ArrayList<>(layers);
            for (VisualLayer layer : layers) if (layer != null) layer.normalize();
            layers.removeIf(Objects::isNull);
        }
    }

    /** One source particle stream and its independently tuned output in an effect graph. */
    public static final class VisualLayer {
        public String id = "particle_layer";
        public String name = "Particle Layer";
        public String sourceParticle = "minecraft:dust";
        public String particle = "keep";
        public boolean enabled = true;
        public double density = 1;
        public double spread = 1;
        public double size = 1;
        public double red = 1, green = 0.75, blue = 0.2;

        public void normalize() {
            id = text(id, "particle_layer", 48).toLowerCase(Locale.ROOT);
            name = text(name, title(id), 80);
            sourceParticle = text(sourceParticle, "minecraft:dust", 128);
            particle = text(particle, "keep", 128);
            density = clamp(density, 0, 4, 1);
            spread = clamp(spread, 0, 4, 1);
            size = clamp(size, 0.1, 4, 1);
            red = clamp(red, 0, 1, 1); green = clamp(green, 0, 1, 0.75); blue = clamp(blue, 0, 1, 0.2);
        }
    }

    public static final class ConfigBackup {
        public long createdAt;
        public String valuesJson = "{}";
    }

    public static final class ConfigEntry {
        public String path = "";
        public String value = "";
        public String defaultValue = "";
        public String type = "";
        public String range = "";
        public String comment = "";
        public String restart = "NONE";
    }

    public static final class Snapshot {
        public String workspaceJson = "{}";
        public List<ConfigEntry> config = new ArrayList<>();
        public List<String> logs = new ArrayList<>();
        public boolean debugEnabled;
        public int activeParticlePreviews;
        public int pendingSounds;
        public String message = "";
        public boolean success = true;
    }

    private static String oneOf(String value, String... options) {
        if (value != null) for (String option : options) if (option.equals(value)) return value;
        return options[0];
    }

    private static String text(String value, String fallback, int max) {
        if (value == null) return fallback;
        String trimmed = value.strip();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static double clamp(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
