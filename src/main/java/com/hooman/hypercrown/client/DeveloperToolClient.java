package com.hooman.hypercrown.client;

import com.google.gson.JsonParser;
import com.hooman.hypercrown.dev.DeveloperWorkspace;
import com.hooman.hypercrown.network.DeveloperToolRequestPayload;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** Client cache for the server-authoritative developer workspace. */
public final class DeveloperToolClient {
    private static DeveloperWorkspace.Snapshot snapshot;
    private static boolean openPending;
    private static final Map<String, DeveloperWorkspace.VisualEffect> VISUAL_EFFECTS = defaultVisualEffects();

    private DeveloperToolClient() {}

    public static void open() {
        openPending = true;
        request("open", "");
    }

    public static void request(String action, String data) {
        PacketDistributor.sendToServer(new DeveloperToolRequestPayload(action, data == null ? "" : data));
    }

    public static void receive(String json) {
        try {
            snapshot = DeveloperWorkspace.GSON.fromJson(JsonParser.parseString(json), DeveloperWorkspace.Snapshot.class);
            if (snapshot == null) return;
            if (openPending) {
                openPending = false;
                if (!snapshot.success) {
                    if (Minecraft.getInstance().player != null)
                        Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.literal(snapshot.message), false);
                    return;
                }
                Minecraft.getInstance().setScreen(new DeveloperToolScreen(snapshot));
            } else if (Minecraft.getInstance().screen instanceof DeveloperToolScreen screen) {
                screen.updateSnapshot(snapshot);
            } else if (!snapshot.success) {
                Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.literal(snapshot.message), false);
            }
        } catch (RuntimeException exception) {
            openPending = false;
            if (Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.literal("Could not read the developer workspace response."), false);
        }
    }

    public static DeveloperWorkspace.Snapshot snapshot() { return snapshot; }

    public static void updateVisualEffects(String json) {
        try {
            DeveloperWorkspace.VisualEffect[] received = DeveloperWorkspace.GSON.fromJson(json, DeveloperWorkspace.VisualEffect[].class);
            if (received == null) return;
            Map<String, DeveloperWorkspace.VisualEffect> next = defaultVisualEffects();
            for (DeveloperWorkspace.VisualEffect profile : received) {
                if (profile == null) continue;
                profile.normalize();
                next.put(profile.id, profile);
            }
            VISUAL_EFFECTS.clear();
            VISUAL_EFFECTS.putAll(next);
        } catch (RuntimeException ignored) { }
    }

    public static DeveloperWorkspace.VisualEffect visualEffect(String id) {
        return VISUAL_EFFECTS.getOrDefault(id, VISUAL_EFFECTS.get("crown_aura"));
    }

    public static DeveloperWorkspace.VisualLayer visualLayer(String effectId, ParticleOptions source) {
        DeveloperWorkspace.VisualEffect effect = VISUAL_EFFECTS.get(effectId);
        if (effect == null || source == null) return null;
        ResourceLocation sourceId = BuiltInRegistries.PARTICLE_TYPE.getKey(source.getType());
        return effect.layers.stream().filter(layer -> sourceId.equals(ResourceLocation.tryParse(layer.sourceParticle)))
                .findFirst().orElse(null);
    }

    public static ParticleOptions visualParticle(DeveloperWorkspace.VisualLayer layer, ParticleOptions fallback) {
        if (layer == null || layer.particle.equalsIgnoreCase("keep")) {
            if (fallback instanceof DustParticleOptions)
                return layer == null ? fallback : new DustParticleOptions(new Vector3f((float) layer.red, (float) layer.green, (float) layer.blue), (float) layer.size);
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

    /** Compatibility helper retained for old saved profiles and UI migration code. */
    public static ParticleOptions visualParticle(DeveloperWorkspace.VisualEffect profile, ParticleOptions fallback) {
        DeveloperWorkspace.VisualLayer legacy = new DeveloperWorkspace.VisualLayer();
        legacy.particle = profile.particle;
        legacy.size = profile.size;
        legacy.red = profile.red; legacy.green = profile.green; legacy.blue = profile.blue;
        return visualParticle(legacy, fallback);
    }

    private static Map<String, DeveloperWorkspace.VisualEffect> defaultVisualEffects() {
        Map<String, DeveloperWorkspace.VisualEffect> effects = new HashMap<>();
        for (DeveloperWorkspace.VisualEffect profile : DeveloperWorkspace.defaults().visualEffects) effects.put(profile.id, profile);
        return effects;
    }
}
