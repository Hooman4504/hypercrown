package com.hooman.hypercrown.dev;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.ability.Ability;
import com.hooman.hypercrown.ability.ActiveAbilities;
import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.network.DeveloperToolSnapshotPayload;
import com.hooman.hypercrown.network.VisualEffectsSyncPayload;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-authoritative persistence, live tuning, previews, and test execution. */
public final class DeveloperToolService {
    private static final int MAX_LOG_ENTRIES = 48;
    private static final int MAX_HISTORY = 20;
    private static final Map<MinecraftServer, ServerState> SERVERS = new WeakHashMap<>();
    private static final Map<String, ModConfigSpec.ConfigValue<?>> CONFIG_VALUES = configValues();

    private DeveloperToolService() {}

    public static void handle(ServerPlayer player, String action, String data) {
        if (player.getServer().getProfilePermissions(player.getGameProfile()) < 2) {
            send(player, failed("Developer tools require operator permission level 2."));
            return;
        }
        if (action == null || data == null || data.length() > DeveloperWorkspace.MAX_JSON_CHARS) {
            send(player, failed("Request is invalid or too large."));
            return;
        }
        ServerState state = null;
        try {
            state = state(player.getServer());
            String message = switch (action) {
                case "open" -> "Connected to the authoritative server workspace.";
                case "save" -> saveWorkspace(player.getServer(), state, data);
                case "reload" -> reloadWorkspace(player.getServer(), state);
                case "undo" -> history(player.getServer(), state, false);
                case "redo" -> history(player.getServer(), state, true);
                case "toggle_debug" -> toggleDebug(player.getServer(), state);
                case "config_set" -> updateConfig(player.getServer(), state, data, false);
                case "config_reset" -> updateConfig(player.getServer(), state, data, true);
                case "config_import" -> importConfig(player.getServer(), state, data);
                case "config_restore" -> restoreConfig(player.getServer(), state, Integer.parseInt(data));
                case "preview_particle" -> previewParticle(player, state, data);
                case "preview_sound" -> previewSound(player, state, data);
                case "test_ability" -> testAbility(player, state, data);
                default -> throw new IllegalArgumentException("Unknown developer tool action.");
            };
            record(state, message);
            send(player, snapshot(player.getServer(), state, message, true));
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? "The request could not be applied." : exception.getMessage();
            HypercrownMod.LOGGER.warn("Hypercrown developer tool rejected action '{}' from {}: {}", action, player.getGameProfile().getName(), message, exception);
            if (state == null) {
                send(player, failed("The developer workspace could not be initialized. Check the server log for details."));
            } else {
                record(state, "ERROR: " + message);
                send(player, snapshot(player.getServer(), state, message, false));
            }
        }
    }

    public static void open(ServerPlayer player) {
        handle(player, "open", "");
    }

    public static void syncClient(ServerPlayer player) {
        ServerState state = state(player.getServer());
        PacketDistributor.sendToPlayer(player, new VisualEffectsSyncPayload(DeveloperWorkspace.GSON.toJson(state.workspace.visualEffects)));
        if (player.getServer().getProfilePermissions(player.getGameProfile()) >= 2)
            send(player, snapshot(player.getServer(), state, "Developer status synchronized.", true));
    }

    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerState state;
        synchronized (SERVERS) {
            state = SERVERS.get(server);
        }
        if (state == null) return;
        long now = server.getTickCount();
        tickParticles(server, state, now);
        tickSounds(server, state, now);
        tickBeamTests(server, state, now);
        if (state.workspaceDirty && now - state.lastSaveTick >= 600) {
            try { persist(server, state); }
            catch (IOException e) { record(state, "ERROR: auto-save failed: " + e.getMessage()); }
        }
    }

    private static String saveWorkspace(MinecraftServer server, ServerState state, String json) throws IOException {
        DeveloperWorkspace candidate = DeveloperWorkspace.parse(json);
        validateVisualEffects(candidate);
        preserveRegisteredAbilities(candidate, state.workspace);
        pushHistory(state);
        if (coreTuningWouldChange(candidate)) createConfigBackup(server, state);
        candidate.configBackups = new ArrayList<>(state.workspace.configBackups);
        applyCoreTuning(candidate);
        state.workspace = candidate;
        state.workspaceDirty = true;
        persist(server, state);
        syncVisualEffects(server, state);
        HypercrownMod.LOGGER.info("Hypercrown developer workspace saved for world {}", server.getWorldData().getLevelName());
        return "Workspace saved. Ability tuning and per-particle effect graphs are active.";
    }

    private static void validateVisualEffects(DeveloperWorkspace workspace) {
        for (DeveloperWorkspace.VisualEffect profile : workspace.visualEffects) {
            if (profile.layers.size() > 32) throw new IllegalArgumentException("A visual effect can have at most 32 particle layers.");
            Set<String> layerIds = new HashSet<>();
            Set<ResourceLocation> sourceIds = new HashSet<>();
            for (DeveloperWorkspace.VisualLayer layer : profile.layers) {
                if (!layerIds.add(layer.id)) throw new IllegalArgumentException("Particle layer ids must be unique within effect '" + profile.id + "'.");
                ResourceLocation sourceId = ResourceLocation.tryParse(layer.sourceParticle);
                if (sourceId == null || BuiltInRegistries.PARTICLE_TYPE.getOptional(sourceId).isEmpty())
                    throw new IllegalArgumentException("Unknown source particle in effect '" + profile.id + "': " + layer.sourceParticle);
                if (!sourceIds.add(sourceId)) throw new IllegalArgumentException("Each source particle can have only one layer in effect '" + profile.id + "'.");
                if (layer.particle.equalsIgnoreCase("keep")) continue;
                ResourceLocation outputId = ResourceLocation.tryParse(layer.particle);
                ParticleType<?> type = outputId == null ? null : BuiltInRegistries.PARTICLE_TYPE.getOptional(outputId).orElse(null);
                if (type == null) throw new IllegalArgumentException("Unknown output particle in layer '" + layer.id + "': " + layer.particle);
                if (!(type instanceof SimpleParticleType) && !outputId.equals(ResourceLocation.withDefaultNamespace("dust")))
                    throw new IllegalArgumentException("Particle layers support simple particles, minecraft:dust, or keep.");
            }
        }
    }

    private static void syncVisualEffects(MinecraftServer server, ServerState state) {
        PacketDistributor.sendToAllPlayers(new VisualEffectsSyncPayload(DeveloperWorkspace.GSON.toJson(state.workspace.visualEffects)));
    }

    /** Emit a built-in effect through the server-authoritative visual profile editor. */
    public static void sendVisualParticles(ServerLevel level, String effectId, ParticleOptions fallback,
                                           double x, double y, double z, int count,
                                           double dx, double dy, double dz, double speed) {
        DeveloperWorkspace.VisualEffect profile = null;
        if (level.getServer() != null) {
            ServerState state = state(level.getServer());
            profile = state.workspace.visualEffects.stream().filter(effect -> effect.id.equals(effectId)).findFirst().orElse(null);
        }
        DeveloperWorkspace.VisualLayer layer = profile == null ? null : visualLayer(profile, fallback);
        if (layer != null && !layer.enabled) return;
        ParticleOptions options = layer == null ? fallback : visualParticle(layer, fallback);
        double density = layer == null ? 1 : layer.density;
        if (density <= 0) return;
        double expectedCount = Math.max(0, count) * density;
        int adjustedCount = (int) expectedCount;
        if (level.random.nextDouble() < expectedCount - adjustedCount) adjustedCount++;
        adjustedCount = Math.min(10_000, adjustedCount);
        if (adjustedCount == 0) return;
        double spread = layer == null ? 1 : layer.spread;
        level.sendParticles(options, x, y, z, adjustedCount, dx * spread, dy * spread, dz * spread, speed);
    }

    public static DeveloperWorkspace.VisualEffect visualEffect(MinecraftServer server, String effectId) {
        if (server == null) return null;
        return state(server).workspace.visualEffects.stream()
                .filter(effect -> effect.id.equals(effectId)).findFirst().orElse(null);
    }

    public static DeveloperWorkspace.VisualLayer visualLayer(MinecraftServer server, String effectId, String sourceParticle) {
        DeveloperWorkspace.VisualEffect effect = visualEffect(server, effectId);
        if (effect == null) return null;
        ResourceLocation sourceId = ResourceLocation.tryParse(sourceParticle);
        return effect.layers.stream().filter(layer -> sourceId != null && sourceId.equals(ResourceLocation.tryParse(layer.sourceParticle)))
                .findFirst().orElse(null);
    }

    public static DeveloperWorkspace.VisualLayer visualLayer(MinecraftServer server, String effectId, ParticleOptions source) {
        return source == null ? null : visualLayer(server, effectId, BuiltInRegistries.PARTICLE_TYPE.getKey(source.getType()).toString());
    }

    private static DeveloperWorkspace.VisualLayer visualLayer(DeveloperWorkspace.VisualEffect profile, ParticleOptions fallback) {
        ResourceLocation sourceId = BuiltInRegistries.PARTICLE_TYPE.getKey(fallback.getType());
        return profile.layers.stream().filter(layer -> sourceId.equals(ResourceLocation.tryParse(layer.sourceParticle)))
                .findFirst().orElse(null);
    }

    private static ParticleOptions visualParticle(DeveloperWorkspace.VisualLayer layer, ParticleOptions fallback) {
        if (layer.particle.equalsIgnoreCase("keep")) {
            if (fallback instanceof DustParticleOptions)
                return new DustParticleOptions(new Vector3f((float) layer.red, (float) layer.green, (float) layer.blue), (float) layer.size);
            return fallback;
        }
        ResourceLocation id = ResourceLocation.tryParse(layer.particle);
        ParticleType<?> type = id == null ? null : BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
        if (id != null && id.equals(ResourceLocation.withDefaultNamespace("dust")))
            return new DustParticleOptions(new Vector3f((float) layer.red, (float) layer.green, (float) layer.blue), (float) layer.size);
        if (type instanceof SimpleParticleType simple) return simple;
        if (type == null && fallback instanceof DustParticleOptions)
            return new DustParticleOptions(new Vector3f((float) layer.red, (float) layer.green, (float) layer.blue), (float) layer.size);
        return fallback;
    }

    private static void preserveRegisteredAbilities(DeveloperWorkspace candidate, DeveloperWorkspace previous) {
        Map<String, Ability> registered = new HashMap<>();
        for (Ability ability : Ability.values()) registered.put(ability.key(), ability);
        Map<String, DeveloperWorkspace.AbilityDraft> byId = new HashMap<>();
        for (DeveloperWorkspace.AbilityDraft draft : candidate.abilities) {
            Ability core = registered.get(draft.id);
            if (core != null) {
                if (!draft.core || draft.active != core.active) throw new IllegalArgumentException("Registered ability identity and type cannot be changed.");
                draft.core = true;
                draft.active = core.active;
            } else if (draft.core) {
                throw new IllegalArgumentException("Unknown custom ability cannot be marked as registered core content.");
            }
            byId.put(draft.id, draft);
        }
        for (Ability ability : Ability.values()) {
            DeveloperWorkspace.AbilityDraft old = previous.abilities.stream().filter(d -> d.id.equals(ability.key())).findFirst().orElse(null);
            DeveloperWorkspace.AbilityDraft updated = byId.get(ability.key());
            if (updated == null) {
                if (old != null) candidate.abilities.add(old);
            }
        }
    }

    private static void applyCoreTuning(DeveloperWorkspace workspace) {
        boolean changed = false;
        for (DeveloperWorkspace.AbilityDraft ability : workspace.abilities) {
            if (!ability.core) continue;
            ModConfigSpec.ConfigValue<?> enabled = CONFIG_VALUES.get("abilities." + ability.id);
            ModConfigSpec.ConfigValue<?> cooldown = CONFIG_VALUES.get("cooldowns." + ability.id);
            if (enabled instanceof ModConfigSpec.BooleanValue boolValue && boolValue.get() != ability.enabled) {
                boolValue.set(ability.enabled);
                changed = true;
            }
            if (cooldown instanceof ModConfigSpec.IntValue intValue) {
                int seconds = (int) Math.round(ability.cooldownSeconds);
                if (intValue.get() != seconds) {
                    intValue.set(seconds);
                    changed = true;
                }
            }
        }
        if (changed && HypercrownConfig.SPEC.isLoaded()) HypercrownConfig.SPEC.save();
    }

    private static boolean coreTuningWouldChange(DeveloperWorkspace workspace) {
        if (!HypercrownConfig.SPEC.isLoaded()) return false;
        for (DeveloperWorkspace.AbilityDraft ability : workspace.abilities) {
            if (!ability.core) continue;
            ModConfigSpec.ConfigValue<?> enabled = CONFIG_VALUES.get("abilities." + ability.id);
            ModConfigSpec.ConfigValue<?> cooldown = CONFIG_VALUES.get("cooldowns." + ability.id);
            if (enabled instanceof ModConfigSpec.BooleanValue boolValue && boolValue.get() != ability.enabled) return true;
            if (cooldown instanceof ModConfigSpec.IntValue intValue && intValue.get() != (int) Math.round(ability.cooldownSeconds)) return true;
        }
        return false;
    }

    private static String reloadWorkspace(MinecraftServer server, ServerState state) throws IOException {
        Path file = workspacePath(server);
        if (!Files.exists(file)) return "No saved developer workspace was found; defaults remain loaded.";
        DeveloperWorkspace reloaded = DeveloperWorkspace.parse(Files.readString(file, StandardCharsets.UTF_8));
        preserveRegisteredAbilities(reloaded, state.workspace);
        if (coreTuningWouldChange(reloaded)) createConfigBackup(server, state);
        reloaded.configBackups = new ArrayList<>(state.workspace.configBackups);
        state.workspace = reloaded;
        state.workspaceDirty = false;
        applyCoreTuning(reloaded);
        syncVisualEffects(server, state);
        return "Workspace reloaded and registered core tuning applied.";
    }

    private static String history(MinecraftServer server, ServerState state, boolean redo) throws IOException {
        Deque<String> source = redo ? state.redo : state.undo;
        Deque<String> destination = redo ? state.undo : state.redo;
        if (source.isEmpty()) return redo ? "Nothing to redo." : "Nothing to undo.";
        destination.addLast(DeveloperWorkspace.GSON.toJson(state.workspace));
        String json = source.removeLast();
        DeveloperWorkspace restored = DeveloperWorkspace.parse(json);
        if (coreTuningWouldChange(restored)) createConfigBackup(server, state);
        restored.configBackups = new ArrayList<>(state.workspace.configBackups);
        state.workspace = restored;
        applyCoreTuning(restored);
        state.workspaceDirty = true;
        persist(server, state);
        syncVisualEffects(server, state);
        return redo ? "Last workspace change restored." : "Last workspace change undone.";
    }

    private static String toggleDebug(MinecraftServer server, ServerState state) throws IOException {
        pushHistory(state);
        state.workspace.debugEnabled = !state.workspace.debugEnabled;
        state.workspaceDirty = true;
        persist(server, state);
        return "Debug overlay " + (state.workspace.debugEnabled ? "enabled." : "disabled.");
    }

    private static String updateConfig(MinecraftServer server, ServerState state, String json, boolean reset) throws IOException {
        JsonObject request = JsonParser.parseString(json).getAsJsonObject();
        String path = request.has("path") ? request.get("path").getAsString() : "";
        String category = request.has("category") ? request.get("category").getAsString() : "";
        if (!reset && !CONFIG_VALUES.containsKey(path)) throw new IllegalArgumentException("Unknown configuration option.");
        if (reset && !path.isEmpty() && !category.isEmpty()) throw new IllegalArgumentException("Choose one reset scope.");
        if (!HypercrownConfig.SPEC.isLoaded()) throw new IllegalStateException("The server configuration has not finished loading.");
        List<Map.Entry<ModConfigSpec.ConfigValue<?>, Object>> changes = new ArrayList<>();
        for (Map.Entry<String, ModConfigSpec.ConfigValue<?>> entry : CONFIG_VALUES.entrySet()) {
            if (reset && !path.isEmpty() && !entry.getKey().equals(path)) continue;
            if (reset && !category.isEmpty() && !entry.getKey().startsWith(category + ".")) continue;
            JsonElement encoded = reset ? null : request.get("value");
            if (!reset && !entry.getKey().equals(path)) continue;
            Object value = reset ? entry.getValue().getDefault() : decodeConfigValue(entry.getValue(), encoded);
            if (!entry.getValue().getSpec().test(value)) throw new IllegalArgumentException("Value is outside the allowed range or type for " + entry.getKey() + ".");
            changes.add(Map.entry(entry.getValue(), value));
        }
        if (changes.isEmpty()) throw new IllegalArgumentException("No configuration options matched the request.");
        createConfigBackup(server, state);
        for (Map.Entry<ModConfigSpec.ConfigValue<?>, Object> change : changes) setConfigValue(change.getKey(), change.getValue());
        HypercrownConfig.SPEC.save();
        syncCoreFromSpec(state.workspace);
        state.workspaceDirty = true;
        persist(server, state);
        record(state, "Config updated: " + (reset ? (path.isEmpty() ? (category.isEmpty() ? "all" : category) : path) : path));
        return reset ? "Reset " + changes.size() + " configuration option(s) to their defaults." : "Configuration saved immediately: " + path + ".";
    }

    private static String importConfig(MinecraftServer server, ServerState state, String json) throws IOException {
        if (!HypercrownConfig.SPEC.isLoaded()) throw new IllegalStateException("The server configuration has not finished loading.");
        JsonObject values = JsonParser.parseString(json).getAsJsonObject();
        List<Map.Entry<ModConfigSpec.ConfigValue<?>, Object>> changes = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            ModConfigSpec.ConfigValue<?> value = CONFIG_VALUES.get(entry.getKey());
            if (value == null) continue;
            Object decoded = decodeConfigValue(value, entry.getValue());
            if (!value.getSpec().test(decoded)) throw new IllegalArgumentException("Invalid imported value for " + entry.getKey() + ".");
            changes.add(Map.entry(value, decoded));
        }
        if (changes.isEmpty()) throw new IllegalArgumentException("Import did not contain any recognized Hypercrown settings.");
        createConfigBackup(server, state);
        for (Map.Entry<ModConfigSpec.ConfigValue<?>, Object> change : changes) setConfigValue(change.getKey(), change.getValue());
        HypercrownConfig.SPEC.save();
        syncCoreFromSpec(state.workspace);
        state.workspaceDirty = true;
        persist(server, state);
        return "Imported and applied " + changes.size() + " recognized server setting(s).";
    }

    private static String restoreConfig(MinecraftServer server, ServerState state, int index) throws IOException {
        if (!HypercrownConfig.SPEC.isLoaded()) throw new IllegalStateException("The server configuration has not finished loading.");
        if (index < 0 || index >= state.workspace.configBackups.size()) throw new IllegalArgumentException("Configuration backup is unavailable.");
        String valuesJson = state.workspace.configBackups.get(index).valuesJson;
        createConfigBackup(server, state);
        JsonObject values = JsonParser.parseString(valuesJson).getAsJsonObject();
        List<Map.Entry<ModConfigSpec.ConfigValue<?>, Object>> changes = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            ModConfigSpec.ConfigValue<?> config = CONFIG_VALUES.get(entry.getKey());
            if (config == null) continue;
            Object decoded = decodeConfigValue(config, entry.getValue());
            if (!config.getSpec().test(decoded)) throw new IllegalArgumentException("Backup contains an invalid value for " + entry.getKey() + ".");
            changes.add(Map.entry(config, decoded));
        }
        if (changes.isEmpty()) throw new IllegalArgumentException("Configuration backup contains no recognized settings.");
        for (Map.Entry<ModConfigSpec.ConfigValue<?>, Object> change : changes) setConfigValue(change.getKey(), change.getValue());
        HypercrownConfig.SPEC.save();
        syncCoreFromSpec(state.workspace);
        state.workspaceDirty = true;
        persist(server, state);
        return "Restored the selected configuration backup.";
    }

    private static void syncCoreFromSpec(DeveloperWorkspace workspace) {
        for (DeveloperWorkspace.AbilityDraft ability : workspace.abilities) {
            syncCoreAbilityFromSpec(ability);
        }
    }

    private static void syncCoreAbilityFromSpec(DeveloperWorkspace.AbilityDraft ability) {
        if (!ability.core) return;
        ModConfigSpec.ConfigValue<?> enabled = CONFIG_VALUES.get("abilities." + ability.id);
        ModConfigSpec.ConfigValue<?> cooldown = CONFIG_VALUES.get("cooldowns." + ability.id);
        if (enabled instanceof ModConfigSpec.BooleanValue boolValue) ability.enabled = boolValue.get();
        if (cooldown instanceof ModConfigSpec.IntValue intValue) ability.cooldownSeconds = intValue.get();
    }

    private static Object decodeConfigValue(ModConfigSpec.ConfigValue<?> config, JsonElement encoded) {
        if (encoded == null || encoded.isJsonNull()) throw new IllegalArgumentException("A configuration value is required.");
        Class<?> type = config.getSpec().getClazz();
        try {
            if (type == Boolean.class || type == boolean.class) return encoded.getAsBoolean();
            if (type == Integer.class || type == int.class) return encoded.getAsInt();
            if (type == Double.class || type == double.class) {
                double parsed = encoded.getAsDouble();
                if (!Double.isFinite(parsed)) throw new IllegalArgumentException("Number must be finite.");
                return parsed;
            }
            if (type == Long.class || type == long.class) return encoded.getAsLong();
            if (type == String.class) return encoded.getAsString();
            if (type.isEnum()) {
                String name = encoded.getAsString();
                for (Object constant : type.getEnumConstants()) if (((Enum<?>) constant).name().equalsIgnoreCase(name)) return constant;
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("The value has the wrong type for this option.");
        }
        throw new IllegalArgumentException("This configuration option uses an unsupported value type.");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setConfigValue(ModConfigSpec.ConfigValue config, Object value) { config.set(value); }

    private static void createConfigBackup(MinecraftServer server, ServerState state) throws IOException {
        DeveloperWorkspace.ConfigBackup backup = new DeveloperWorkspace.ConfigBackup();
        backup.createdAt = System.currentTimeMillis();
        backup.valuesJson = configValuesJson();
        state.workspace.configBackups.add(0, backup);
        while (state.workspace.configBackups.size() > 5) state.workspace.configBackups.remove(state.workspace.configBackups.size() - 1);
        state.workspaceDirty = true;
        persist(server, state);
    }

    private static String configValuesJson() {
        JsonObject values = new JsonObject();
        CONFIG_VALUES.forEach((path, config) -> values.add(path, DeveloperWorkspace.GSON.toJsonTree(config.get())));
        return DeveloperWorkspace.GSON.toJson(values);
    }

    private static String previewParticle(ServerPlayer player, ServerState state, String json) {
        DeveloperWorkspace.ParticlePreset preset = DeveloperWorkspace.GSON.fromJson(JsonParser.parseString(json), DeveloperWorkspace.ParticlePreset.class);
        if (preset == null) throw new IllegalArgumentException("Particle preset is empty.");
        preset.normalize();
        ResourceLocation id = ResourceLocation.tryParse(preset.particle);
        if (id == null || BuiltInRegistries.PARTICLE_TYPE.getOptional(id).isEmpty()) throw new IllegalArgumentException("Unknown particle type: " + preset.particle);
        if (particleOptions(preset) == null) throw new IllegalArgumentException("Preview supports simple particle types and minecraft:dust; this particle needs extra options data.");
        validateParticleCondition(preset.condition);
        if (state.particles.size() >= 8) throw new IllegalArgumentException("Eight particle previews are already running on this server.");
        state.particles.add(new ParticleJob(player.getUUID(), preset, player.serverLevel().getGameTime() + preset.delayTicks));
        record(state, "Particle preview queued: " + preset.id);
        return "Particle preview queued. Test particles are capped at 120 per preview.";
    }

    private static void tickParticles(MinecraftServer server, ServerState state, long serverTick) {
        Iterator<ParticleJob> jobs = state.particles.iterator();
        while (jobs.hasNext()) {
            ParticleJob job = jobs.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(job.player);
            if (owner == null || owner.serverLevel().getGameTime() - job.startTick > job.preset.durationTicks || job.sent >= 120) {
                jobs.remove();
                continue;
            }
            long age = owner.serverLevel().getGameTime() - job.startTick;
            if (age < 0 || age % job.preset.intervalTicks != 0 || age > job.preset.durationTicks) continue;
            if (!conditionMet(owner, job.preset.condition, job.preset.position.equals("target"))) continue;
            emitParticleBurst(owner, job);
        }
    }

    private static boolean conditionMet(ServerPlayer player, String condition, boolean requireTarget) {
        if (requireTarget && nearestTarget(player, 64) == null) return false;
        if ("always".equals(condition) || condition.isBlank()) return true;
        if ("player_sprinting".equals(condition)) return player.isSprinting();
        if ("player_sneaking".equals(condition)) return player.isShiftKeyDown();
        if ("target_exists".equals(condition)) return nearestTarget(player, 64) != null;
        if (condition.startsWith("target_distance:")) {
            try {
                double max = Double.parseDouble(condition.substring("target_distance:".length()));
                LivingEntity target = nearestTarget(player, 64);
                return target != null && player.distanceTo(target) <= Mth.clamp(max, 0, 64);
            } catch (NumberFormatException ignored) { return false; }
        }
        return false;
    }

    private static void validateParticleCondition(String condition) {
        if (condition == null || condition.isBlank() || List.of("always", "player_sprinting", "player_sneaking", "target_exists").contains(condition)) return;
        if (!condition.startsWith("target_distance:")) throw new IllegalArgumentException("Unsupported particle condition: " + condition);
        try {
            double distance = Double.parseDouble(condition.substring("target_distance:".length()));
            if (!Double.isFinite(distance)) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Target distance condition must be a finite number.");
        }
    }

    private static void emitParticleBurst(ServerPlayer owner, ParticleJob job) {
        ParticleOptions options = particleOptions(job.preset);
        if (options == null) return;
        ServerLevel level = owner.serverLevel();
        Vec3 origin = particleOrigin(owner, job.preset);
        RandomSource random = level.random;
        int amount = Math.min(24, Math.min(job.preset.count, 120 - job.sent));
        LivingEntity target = nearestTarget(owner, 64);
        for (int i = 0; i < amount; i++) {
            Vec3 offset = shapeOffset(job.preset, owner, i, amount, random);
            double scatter = job.preset.randomness;
            double x = origin.x + offset.x + random.nextGaussian() * scatter * 0.12;
            double y = origin.y + offset.y + random.nextGaussian() * scatter * 0.12;
            double z = origin.z + offset.z + random.nextGaussian() * scatter * 0.12;
            double age = Math.max(0, owner.serverLevel().getGameTime() - job.startTick) / (double) Math.max(1, job.preset.durationTicks);
            double vx = job.preset.vx + job.preset.accelerationX * age + random.nextGaussian() * scatter * 0.02;
            double vy = job.preset.vy + job.preset.accelerationY * age + random.nextGaussian() * scatter * 0.02;
            double vz = job.preset.vz + job.preset.accelerationZ * age + random.nextGaussian() * scatter * 0.02;
            level.sendParticles(owner, options, true, x, y, z, 0, vx, vy, vz, job.preset.speed);
            job.sent++;
        }
    }

    private static ParticleOptions particleOptions(DeveloperWorkspace.ParticlePreset preset) {
        ResourceLocation id = ResourceLocation.tryParse(preset.particle);
        if (id == null) return null;
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
        if (type == null) return null;
        if (id.equals(ResourceLocation.withDefaultNamespace("dust")))
            return new DustParticleOptions(new Vector3f((float) preset.red, (float) preset.green, (float) preset.blue), (float) preset.size);
        return type instanceof SimpleParticleType simple ? simple : null;
    }

    private static Vec3 particleOrigin(ServerPlayer player, DeveloperWorkspace.ParticlePreset preset) {
        if (preset.position.equals("world")) return new Vec3(preset.x, preset.y, preset.z);
        if (preset.position.equals("target")) {
            LivingEntity target = nearestTarget(player, 64);
            if (target != null) return target.position().add(0, target.getBbHeight() * 0.5, 0).add(preset.x, preset.y, preset.z);
        }
        return player.position().add(0, player.getBbHeight() * 0.5, 0).add(preset.x, preset.y, preset.z);
    }

    private static Vec3 shapeOffset(DeveloperWorkspace.ParticlePreset preset, ServerPlayer player, int index, int count, RandomSource random) {
        double radius = preset.radius;
        double angle = Math.PI * 2 * index / Math.max(1, count);
        return switch (preset.shape) {
            case "ring" -> new Vec3(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            case "line" -> {
                Vec3 forward = player.getLookAngle().multiply(1, 0, 1).normalize();
                if (forward.lengthSqr() < 0.001) forward = new Vec3(0, 0, 1);
                double distance = (index / (double) Math.max(1, count - 1) - 0.5) * radius * 2;
                yield forward.scale(distance);
            }
            case "cylinder" -> new Vec3(Math.cos(angle) * radius * Math.sqrt(random.nextDouble()), (random.nextDouble() - 0.5) * preset.height, Math.sin(angle) * radius * Math.sqrt(random.nextDouble()));
            case "cone" -> {
                double y = index / (double) Math.max(1, count - 1) * preset.height;
                yield new Vec3(Math.cos(angle) * y * 0.5, y, Math.sin(angle) * y * 0.5);
            }
            case "path" -> pathPoint(preset.path, index, count);
            default -> {
                double y = random.nextDouble() * 2 - 1;
                double theta = random.nextDouble() * Math.PI * 2;
                double r = Math.cbrt(random.nextDouble()) * radius;
                double horizontal = Math.sqrt(1 - y * y);
                yield new Vec3(Math.cos(theta) * horizontal * r, y * r, Math.sin(theta) * horizontal * r);
            }
        };
    }

    private static Vec3 pathPoint(String path, int index, int count) {
        try {
            String[] points = path.split(";");
            if (points.length == 0) return Vec3.ZERO;
            double progress = index / (double) Math.max(1, count - 1) * (points.length - 1);
            int a = (int) Math.floor(progress), b = Math.min(points.length - 1, a + 1);
            double t = progress - a;
            Vec3 p1 = parsePoint(points[a]), p2 = parsePoint(points[b]);
            return p1.lerp(p2, t);
        } catch (RuntimeException ignored) { return Vec3.ZERO; }
    }

    private static Vec3 parsePoint(String point) {
        String[] parts = point.strip().split(",");
        if (parts.length != 3) throw new IllegalArgumentException();
        double x = Double.parseDouble(parts[0]), y = Double.parseDouble(parts[1]), z = Double.parseDouble(parts[2]);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException();
        return new Vec3(Mth.clamp(x, -64, 64), Mth.clamp(y, -64, 64), Mth.clamp(z, -64, 64));
    }

    private static String previewSound(ServerPlayer player, ServerState state, String json) {
        DeveloperWorkspace.SoundPreset preset = DeveloperWorkspace.GSON.fromJson(JsonParser.parseString(json), DeveloperWorkspace.SoundPreset.class);
        if (preset == null) throw new IllegalArgumentException("Sound preset is empty.");
        preset.normalize();
        ResourceLocation id = pickSound(preset, player.serverLevel().random);
        if (id == null || BuiltInRegistries.SOUND_EVENT.getOptional(id).isEmpty()) throw new IllegalArgumentException("Unknown sound event: " + preset.sound);
        if (state.sounds.size() >= 128) throw new IllegalArgumentException("The sound preview queue is full; wait for pending sounds to play.");
        int scheduled = 0;
        List<ScheduledSound> sequence = new ArrayList<>();
        if (!preset.sequence.isBlank()) {
            for (String step : preset.sequence.split(";")) {
                if (scheduled >= 15 || state.sounds.size() + sequence.size() >= 128) break;
                String[] columns = step.strip().split("@");
                if (columns.length != 4) continue;
                ResourceLocation stepSound = ResourceLocation.tryParse(columns[0]);
                if (stepSound == null || BuiltInRegistries.SOUND_EVENT.getOptional(stepSound).isEmpty()) continue;
                int delay = Math.max(0, Math.min(72_000, Integer.parseInt(columns[1])));
                double rawVolume = Double.parseDouble(columns[2]);
                double rawPitch = Double.parseDouble(columns[3]);
                if (!Double.isFinite(rawVolume) || !Double.isFinite(rawPitch)) continue;
                float volume = Mth.clamp((float) rawVolume, 0, 4);
                float pitch = Mth.clamp((float) rawPitch, 0.01f, 2);
                sequence.add(new ScheduledSound(player.getUUID(), stepSound, serverTick(player), delay, volume, pitch, preset.attachment, preset.x, preset.y, preset.z));
                scheduled++;
            }
        }
        if (scheduled == 0) sequence.add(new ScheduledSound(player.getUUID(), id, serverTick(player), 0, varied(preset.volume, preset.volumeVariance, player.serverLevel().random), varied(preset.pitch, preset.pitchVariance, player.serverLevel().random), preset.attachment, preset.x, preset.y, preset.z));
        state.sounds.addAll(sequence);
        return scheduled == 0 ? "Sound preview played." : "Queued " + scheduled + " sound(s) in the sequence.";
    }

    private static long serverTick(ServerPlayer player) { return player.getServer().getTickCount(); }

    private static ResourceLocation pickSound(DeveloperWorkspace.SoundPreset preset, RandomSource random) {
        List<String> pool = new ArrayList<>();
        if (!preset.soundPool.isBlank()) for (String entry : preset.soundPool.split(",")) if (!entry.isBlank()) pool.add(entry.strip());
        if (pool.isEmpty()) return ResourceLocation.tryParse(preset.sound);
        return ResourceLocation.tryParse(pool.get(random.nextInt(pool.size())));
    }

    private static float varied(double base, double variance, RandomSource random) {
        return (float) Math.max(0, base + (random.nextDouble() * 2 - 1) * variance * base);
    }

    private static void tickSounds(MinecraftServer server, ServerState state, long now) {
        Iterator<ScheduledSound> iterator = state.sounds.iterator();
        while (iterator.hasNext()) {
            ScheduledSound sound = iterator.next();
            if (now < sound.startTick + sound.delay) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(sound.player);
            if (player != null) {
                SoundEvent event = BuiltInRegistries.SOUND_EVENT.getOptional(sound.sound).orElse(null);
                if (event != null) {
                    Vec3 position = sound.attachment.equals("world") ? new Vec3(sound.x, sound.y, sound.z)
                            : sound.attachment.equals("target") && nearestTarget(player, 64) != null
                            ? nearestTarget(player, 64).position() : player.position();
                    player.serverLevel().playSound(null, position.x, position.y, position.z, event, SoundSource.PLAYERS, sound.volume, sound.pitch);
                }
            }
            iterator.remove();
        }
    }

    private static void tickBeamTests(MinecraftServer server, ServerState state, long now) {
        Iterator<BeamTest> iterator = state.beams.iterator();
        while (iterator.hasNext()) {
            BeamTest test = iterator.next();
            if (now < test.releaseTick) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(test.player);
            if (player != null && CrownHelper.has(player, Ability.ENERGY_BEAM))
                ActiveAbilities.handle(player, Ability.ENERGY_BEAM, false, true);
            iterator.remove();
        }
    }

    private static String testAbility(ServerPlayer player, ServerState state, String request) {
        DeveloperWorkspace.AbilityDraft draft;
        if (request.stripLeading().startsWith("{")) {
            draft = DeveloperWorkspace.GSON.fromJson(JsonParser.parseString(request), DeveloperWorkspace.AbilityDraft.class);
            if (draft == null) throw new IllegalArgumentException("Ability draft is empty.");
            draft.normalize();
            if (!draft.id.matches("[a-z0-9_.-]{1,48}")) throw new IllegalArgumentException("Ability id is invalid.");
            if (draft.core) throw new IllegalArgumentException("Registered abilities must be selected from the server workspace.");
        } else {
            draft = state.workspace.abilities.stream().filter(a -> a.id.equals(request)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Ability is not in the current workspace."));
        }
        if (!draft.enabled) throw new IllegalArgumentException("This ability is disabled in the workspace.");
        if (draft.core) {
            Ability ability = Ability.valueOf(draft.id.toUpperCase(Locale.ROOT));
            if (!ability.active) throw new IllegalArgumentException("Passive abilities cannot be manually activated; observe them while wearing the appropriate crown.");
            if (!CrownHelper.has(player, ability)) throw new IllegalArgumentException("Equip a crown with this registered ability before testing it.");
            ActiveAbilities.handle(player, ability, player.isShiftKeyDown(), false);
            if (ability == Ability.ENERGY_BEAM) {
                state.beams.removeIf(test -> test.player.equals(player.getUUID()));
                state.beams.add(new BeamTest(player.getUUID(), player.getServer().getTickCount() + 40));
                return "Energy Beam is charging; it fires after two seconds using the registered server logic.";
            }
            return "Registered ability test requested: " + draft.name + ".";
        }
        executeCustom(player, state, draft, 0);
        return "Custom ability test executed: " + draft.name + ".";
    }

    private static void executeCustom(ServerPlayer player, ServerState state, DeveloperWorkspace.AbilityDraft draft, int depth) {
        if (depth > 3) throw new IllegalArgumentException("Ability chains are limited to four steps.");
        if (!draft.enabled) throw new IllegalArgumentException("Chained ability '" + draft.id + "' is disabled in the workspace.");
        List<LivingEntity> targets = new ArrayList<>();
        if (draft.target.equals("self")) targets.add(player);
        else {
            List<LivingEntity> candidates = player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(draft.range), entity -> entity != player && entity.isAlive() && !entity.isAlliedTo(player)
                            && player.distanceToSqr(entity) <= draft.range * draft.range);
            candidates.sort(Comparator.comparingDouble(player::distanceToSqr));
            if (draft.target.equals("nearest_enemy")) {
                if (!candidates.isEmpty()) targets.add(candidates.get(0));
            } else targets.addAll(candidates.subList(0, Math.min(32, candidates.size())));
        }
        if (draft.target.equals("nearest_enemy") && targets.isEmpty()) throw new IllegalArgumentException("No valid target is in range.");
        switch (draft.action) {
            case "damage" -> targets.forEach(target -> target.hurt(player.damageSources().indirectMagic(player, player), (float) draft.damage));
            case "effect" -> {
                ResourceLocation id = ResourceLocation.tryParse(draft.effect);
                var effect = id == null ? null : BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
                if (effect == null) throw new IllegalArgumentException("Unknown mob effect: " + draft.effect);
                for (LivingEntity target : targets) target.addEffect(new MobEffectInstance(effect, Math.max(1, draft.durationTicks), 0, false, true, true));
            }
            case "launch" -> targets.forEach(target -> {
                Vec3 away = target.position().subtract(player.position()).normalize();
                target.push(away.x * 1.2, 0.6, away.z * 1.2);
                target.hurtMarked = true;
            });
            case "heal" -> targets.forEach(target -> target.heal((float) draft.damage));
            case "visual" -> { }
            default -> throw new IllegalArgumentException("Unsupported custom ability action.");
        }
        playAbilityPreviewParticle(player, draft.particle);
        ResourceLocation soundId = ResourceLocation.tryParse(draft.sound);
        SoundEvent sound = soundId == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(soundId).orElse(null);
        if (sound != null) player.serverLevel().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 0.8f, 1f);
        if (!draft.chain.isBlank()) {
            DeveloperWorkspace.AbilityDraft next = state.workspace.abilities.stream().filter(a -> a.id.equals(draft.chain) && !a.core).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Chained custom ability was not found."));
            executeCustom(player, state, next, depth + 1);
        }
    }

    private static void playAbilityPreviewParticle(ServerPlayer player, String particleId) {
        ResourceLocation id = ResourceLocation.tryParse(particleId);
        ParticleType<?> type = id == null ? null : BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
        ParticleOptions options = type instanceof SimpleParticleType simple ? simple :
                id != null && id.equals(ResourceLocation.withDefaultNamespace("dust"))
                        ? new DustParticleOptions(new Vector3f(1f, 0.72f, 0.18f), 1f) : null;
        if (options != null) player.serverLevel().sendParticles(player, options, true,
                player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 18, 0.6, 0.8, 0.6, 0.02);
    }

    private static LivingEntity nearestTarget(ServerPlayer player, double range) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range), target -> target != player
                        && target.isAlive() && !target.isAlliedTo(player) && player.distanceToSqr(target) <= range * range)
                .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }

    private static ServerState state(MinecraftServer server) {
        synchronized (SERVERS) {
            return SERVERS.computeIfAbsent(server, DeveloperToolService::load);
        }
    }

    private static ServerState load(MinecraftServer server) {
        ServerState state = new ServerState();
        boolean loadedFromDisk = false;
        Path file = workspacePath(server);
        try {
            if (Files.exists(file)) {
                state.workspace = DeveloperWorkspace.parse(Files.readString(file, StandardCharsets.UTF_8));
                loadedFromDisk = true;
            }
        } catch (Exception exception) {
            Path backup = file.resolveSibling(file.getFileName() + ".bak");
            try {
                if (Files.exists(backup)) {
                    state.workspace = DeveloperWorkspace.parse(Files.readString(backup, StandardCharsets.UTF_8));
                    loadedFromDisk = true;
                    Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING);
                    HypercrownMod.LOGGER.warn("Recovered the Hypercrown developer workspace from its backup file");
                }
            } catch (Exception recoveryFailure) {
                HypercrownMod.LOGGER.error("Could not load or recover the Hypercrown developer workspace", recoveryFailure);
            }
            if (!loadedFromDisk) {
                HypercrownMod.LOGGER.error("Could not load the Hypercrown developer workspace; using defaults", exception);
                state.workspace = DeveloperWorkspace.defaults();
            }
        }
        if (state.workspace == null) state.workspace = DeveloperWorkspace.defaults();
        if (loadedFromDisk) {
            Set<String> loadedIds = new HashSet<>(state.workspace.abilities.stream().map(a -> a.id).toList());
            preserveRegisteredAbilities(state.workspace, DeveloperWorkspace.defaults());
            for (DeveloperWorkspace.AbilityDraft ability : state.workspace.abilities)
                if (ability.core && !loadedIds.contains(ability.id)) syncCoreAbilityFromSpec(ability);
        }
        try {
            if (loadedFromDisk) applyCoreTuning(state.workspace);
            else syncCoreFromSpec(state.workspace);
        } catch (RuntimeException exception) { HypercrownMod.LOGGER.warn("Could not apply stored core ability tuning", exception); }
        return state;
    }

    private static void pushHistory(ServerState state) {
        state.undo.addLast(DeveloperWorkspace.GSON.toJson(state.workspace));
        while (state.undo.size() > MAX_HISTORY) state.undo.removeFirst();
        state.redo.clear();
    }

    private static void persist(MinecraftServer server, ServerState state) throws IOException {
        Path file = workspacePath(server);
        Files.createDirectories(file.getParent());
        if (Files.exists(file)) Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, DeveloperWorkspace.GSON.toJson(state.workspace), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
        state.lastSaveTick = server.getTickCount();
        state.workspaceDirty = false;
    }

    private static Path workspacePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("hypercrown").resolve("developer-workspace.json");
    }

    private static DeveloperWorkspace.Snapshot snapshot(MinecraftServer server, ServerState state, String message, boolean success) {
        DeveloperWorkspace.Snapshot snapshot = new DeveloperWorkspace.Snapshot();
        snapshot.workspaceJson = DeveloperWorkspace.GSON.toJson(state.workspace);
        snapshot.config = configSnapshot();
        snapshot.logs = new ArrayList<>(state.logs);
        snapshot.debugEnabled = state.workspace.debugEnabled;
        snapshot.activeParticlePreviews = state.particles.size();
        snapshot.pendingSounds = state.sounds.size();
        snapshot.message = message;
        snapshot.success = success;
        return snapshot;
    }

    private static List<DeveloperWorkspace.ConfigEntry> configSnapshot() {
        List<DeveloperWorkspace.ConfigEntry> rows = new ArrayList<>();
        for (Map.Entry<String, ModConfigSpec.ConfigValue<?>> entry : CONFIG_VALUES.entrySet()) {
            ModConfigSpec.ConfigValue<?> value = entry.getValue();
            DeveloperWorkspace.ConfigEntry row = new DeveloperWorkspace.ConfigEntry();
            row.path = entry.getKey();
            row.type = value.getSpec().getClazz().getSimpleName();
            row.defaultValue = DeveloperWorkspace.GSON.toJson(value.getDefault());
            try { row.value = DeveloperWorkspace.GSON.toJson(value.get()); }
            catch (RuntimeException exception) { row.value = row.defaultValue; }
            var range = value.getSpec().getRange();
            row.range = range == null ? "" : range.toString();
            row.comment = value.getSpec().getComment() == null ? "" : value.getSpec().getComment();
            row.restart = value.getSpec().restartType().name();
            rows.add(row);
        }
        rows.sort(Comparator.comparing(row -> row.path));
        return rows;
    }

    private static Map<String, ModConfigSpec.ConfigValue<?>> configValues() {
        Map<String, ModConfigSpec.ConfigValue<?>> result = new HashMap<>();
        collectConfigValues(HypercrownConfig.SPEC.getValues(), result);
        return result;
    }

    private static void collectConfigValues(UnmodifiableConfig config, Map<String, ModConfigSpec.ConfigValue<?>> values) {
        for (Object value : config.valueMap().values()) {
            if (value instanceof ModConfigSpec.ConfigValue<?> configValue)
                values.put(String.join(".", configValue.getPath()), configValue);
            else if (value instanceof UnmodifiableConfig nested) collectConfigValues(nested, values);
        }
    }

    private static void record(ServerState state, String message) {
        state.logs.addLast("[" + java.time.LocalTime.now().withNano(0) + "] " + message);
        while (state.logs.size() > MAX_LOG_ENTRIES) state.logs.removeFirst();
    }

    private static DeveloperWorkspace.Snapshot failed(String message) {
        DeveloperWorkspace.Snapshot snapshot = new DeveloperWorkspace.Snapshot();
        snapshot.success = false;
        snapshot.message = message;
        return snapshot;
    }

    private static void send(ServerPlayer player, DeveloperWorkspace.Snapshot snapshot) {
        String json = DeveloperWorkspace.GSON.toJson(snapshot);
        if (json.length() > DeveloperWorkspace.MAX_SNAPSHOT_JSON_CHARS) {
            snapshot.workspaceJson = "{}";
            snapshot.config = new ArrayList<>();
            snapshot.logs = new ArrayList<>();
            snapshot.message = "Server workspace exceeds the network snapshot limit.";
            snapshot.success = false;
            json = DeveloperWorkspace.GSON.toJson(snapshot);
        }
        PacketDistributor.sendToPlayer(player, new DeveloperToolSnapshotPayload(json));
    }

    private static final class ServerState {
        DeveloperWorkspace workspace;
        final Deque<String> undo = new ArrayDeque<>();
        final Deque<String> redo = new ArrayDeque<>();
        final Deque<String> logs = new ArrayDeque<>();
        final List<ParticleJob> particles = new ArrayList<>();
        final List<ScheduledSound> sounds = new ArrayList<>();
        final List<BeamTest> beams = new ArrayList<>();
        long lastSaveTick;
        boolean workspaceDirty;
    }

    private static final class ParticleJob {
        final UUID player;
        final DeveloperWorkspace.ParticlePreset preset;
        final long startTick;
        int sent;
        ParticleJob(UUID player, DeveloperWorkspace.ParticlePreset preset, long startTick) { this.player = player; this.preset = preset; this.startTick = startTick; }
    }

    private record ScheduledSound(UUID player, ResourceLocation sound, long startTick, int delay, float volume, float pitch,
                                  String attachment, double x, double y, double z) {}
    private record BeamTest(UUID player, long releaseTick) {}
}
