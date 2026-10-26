package com.hooman.hypercrown.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.ability.Ability;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.List;

@EventBusSubscriber(modid = HypercrownMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private static final String CATEGORY = "key.categories.hypercrown";
    public static final KeyMapping BLINK = key("blink", GLFW.GLFW_KEY_V);
    public static final KeyMapping ANCHOR = key("anchor", GLFW.GLFW_KEY_H);
    public static final KeyMapping BEAM = key("energy_beam", GLFW.GLFW_KEY_G);
    public static final KeyMapping WEATHER = key("weather_control", GLFW.GLFW_KEY_J);
    public static final KeyMapping HYPERSTEP = key("hyperstep", GLFW.GLFW_KEY_C);
    public static final KeyMapping HYPERBREAK = key("hyperbreak", GLFW.GLFW_KEY_B);
    public static final KeyMapping GUARD = key("crown_guard", GLFW.GLFW_KEY_N);
    public static final KeyMapping AWARENESS = key("tactical_awareness", GLFW.GLFW_KEY_M);
    public static final KeyMapping FORGE = key("forge", GLFW.GLFW_KEY_K);
    public static final KeyMapping DEVELOPER = key("developer", GLFW.GLFW_KEY_F8);
    public static final Map<Ability, KeyMapping> KEYS = Map.ofEntries(
            Map.entry(Ability.BLINK, BLINK), Map.entry(Ability.PHASE_DASH, BLINK),
            Map.entry(Ability.ANCHOR, ANCHOR), Map.entry(Ability.TIME_SNARE, ANCHOR),
            Map.entry(Ability.ENERGY_BEAM, BEAM), Map.entry(Ability.CHAIN_LIGHTNING, BEAM),
            Map.entry(Ability.WEATHER_CONTROL, WEATHER), Map.entry(Ability.CROWN_NOVA, WEATHER),
            Map.entry(Ability.HYPERSTEP, HYPERSTEP), Map.entry(Ability.HYPERBREAK, HYPERBREAK),
            Map.entry(Ability.CROWN_GUARD, GUARD), Map.entry(Ability.TACTICAL_AWARENESS, AWARENESS));
    public static final List<List<Ability>> HYPERCROWN_SLOTS = List.of(
            List.of(Ability.BLINK, Ability.PHASE_DASH),
            List.of(Ability.ANCHOR, Ability.TIME_SNARE),
            List.of(Ability.ENERGY_BEAM, Ability.CHAIN_LIGHTNING),
            List.of(Ability.WEATHER_CONTROL, Ability.CROWN_NOVA));

    private static KeyMapping key(String name, int glfw) {
        return new KeyMapping("key.hypercrown." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, glfw, CATEGORY);
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        List.of(BLINK, ANCHOR, BEAM, WEATHER, HYPERSTEP, HYPERBREAK, GUARD, AWARENESS)
                .forEach(event::register);
        event.register(FORGE);
        event.register(DEVELOPER);
    }

    @SubscribeEvent
    public static void onMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistries.CROWN_FORGE_MENU.get(), CrownForgeScreen::new);
        event.register(ModRegistries.CELESTIAL_FOCUSING_MENU.get(), CelestialFocusingScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public void setupModelAnimations(LivingEntity entity, ItemStack stack, EquipmentSlot slot, Model model,
                                             float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                             float netHeadYaw, float headPitch) {
                if (slot == EquipmentSlot.HEAD && model instanceof HumanoidModel<?> humanoid) {
                    raisePartAlongHead(humanoid.head);
                    raisePartAlongHead(humanoid.hat);
                }
            }
        }, ModRegistries.HYPERCROWN.get(), ModRegistries.TACTICAL_CROWN.get());
    }

    private static void raisePartAlongHead(ModelPart part) {
        float offset = -2.0F;
        float cosX = (float) Math.cos(part.xRot);
        float sinX = (float) Math.sin(part.xRot);
        float cosY = (float) Math.cos(part.yRot);
        float sinY = (float) Math.sin(part.yRot);
        float cosZ = (float) Math.cos(part.zRot);
        float sinZ = (float) Math.sin(part.zRot);

        float rotatedX = sinY * sinX * offset;
        float rotatedY = sinZ * rotatedX + cosZ * cosX * offset;
        float rotatedZ = cosY * sinX * offset;
        float adjustedX = cosZ * rotatedX - sinZ * cosX * offset;

        part.x += adjustedX;
        part.y += rotatedY;
        part.z += rotatedZ;
    }

    @SubscribeEvent
    public static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(HypercrownMod.id("ability_hud"), CrownHud::render);
    }
}
