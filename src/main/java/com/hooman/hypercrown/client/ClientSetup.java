package com.hooman.hypercrown.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.ability.Ability;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

@EventBusSubscriber(modid = HypercrownMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private static final String CATEGORY = "key.categories.hypercrown";
    public static final KeyMapping BLINK = key("blink", GLFW.GLFW_KEY_V);
    public static final KeyMapping ANCHOR = key("anchor", GLFW.GLFW_KEY_H);
    public static final KeyMapping BEAM = key("energy_beam", GLFW.GLFW_KEY_G);
    public static final KeyMapping WEATHER = key("weather_control", GLFW.GLFW_KEY_J);
    public static final Map<Ability, KeyMapping> KEYS = Map.of(
            Ability.BLINK, BLINK, Ability.ANCHOR, ANCHOR, Ability.ENERGY_BEAM, BEAM, Ability.WEATHER_CONTROL, WEATHER);

    private static KeyMapping key(String name, int glfw) {
        return new KeyMapping("key.hypercrown." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, glfw, CATEGORY);
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) { KEYS.values().forEach(event::register); }

    @SubscribeEvent
    public static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(HypercrownMod.id("ability_hud"), CrownHud::render);
    }
}
