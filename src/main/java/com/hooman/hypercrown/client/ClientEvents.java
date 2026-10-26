package com.hooman.hypercrown.client;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.ability.Ability;
import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.dev.DeveloperWorkspace;
import com.hooman.hypercrown.network.ActivateAbilityPayload;
import com.hooman.hypercrown.network.ClientCrownState;
import com.hooman.hypercrown.network.OpenCrownForgePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.List;
import java.util.EnumMap;
import java.util.Map;

@EventBusSubscriber(modid = HypercrownMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.2f), 0.8f);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.68f, 0.32f, 1.0f), 0.72f);
    private static boolean beamHeld;
    private static boolean beamAltHeld;
    private static int beamChargeTicks;
    private static final Map<Ability, Ability> SELECTED_ABILITIES = new EnumMap<>(Ability.class);

    public static boolean isBeamCharging() {
        return beamHeld;
    }

    public static float beamChargeProgress() {
        return Math.min(1.0f, beamChargeTicks / 40.0f);
    }

    public static Ability selectedAbility(Ability slot) {
        return SELECTED_ABILITIES.getOrDefault(slot, slot);
    }

    public static List<Ability> displayedAbilities(LocalPlayer player) {
        if (CrownHelper.isHypercrown(player)) {
            return ClientSetup.HYPERCROWN_SLOTS.stream()
                    .map(slot -> selectedAbility(slot.get(0)))
                    .filter(ability -> CrownHelper.has(player, ability))
                    .toList();
        }
        return Ability.ACTIVES.stream().filter(ability -> CrownHelper.has(player, ability)).toList();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ClientCrownState.tick();
        while (ClientSetup.DEVELOPER.consumeClick()) DeveloperToolClient.open();
        handleKeys(mc.player);
        spawnAuras(mc);
    }

    private static void handleKeys(LocalPlayer p) {
        boolean hypercrown = CrownHelper.isHypercrown(p) && CrownHelper.isCrownActive(p);
        while (ClientSetup.FORGE.consumeClick()) {
            if (hypercrown) PacketDistributor.sendToServer(OpenCrownForgePayload.INSTANCE);
        }
        boolean altDown = Screen.hasAltDown();
        for (List<Ability> slot : ClientSetup.HYPERCROWN_SLOTS) {
            Ability base = slot.get(0);
            if (base == Ability.ENERGY_BEAM) continue;
            while (ClientSetup.KEYS.get(base).consumeClick()) {
                if (hypercrown && altDown) cycleAbility(base, slot);
                else {
                    Ability selected = selectedAbility(base);
                    if (CrownHelper.has(p, selected)) send(selected, p.isShiftKeyDown(), false);
                }
            }
        }
        for (Ability ability : List.of(Ability.HYPERSTEP, Ability.HYPERBREAK,
                Ability.CROWN_GUARD, Ability.TACTICAL_AWARENESS)) {
            while (ClientSetup.KEYS.get(ability).consumeClick())
                if (CrownHelper.has(p, ability)) send(ability, p.isShiftKeyDown(), false);
        }
        boolean beamPressed = false;
        while (ClientSetup.BEAM.consumeClick()) beamPressed = true;
        boolean beamDown = ClientSetup.BEAM.isDown();
        Ability beamAbility = selectedAbility(Ability.ENERGY_BEAM);
        if (hypercrown && altDown && beamDown && !beamAltHeld) {
            cycleAbility(Ability.ENERGY_BEAM, ClientSetup.HYPERCROWN_SLOTS.get(2));
            beamAbility = selectedAbility(Ability.ENERGY_BEAM);
        } else if (beamPressed && beamAbility != Ability.ENERGY_BEAM && CrownHelper.has(p, beamAbility)) {
            send(beamAbility, p.isShiftKeyDown(), false);
        }
        boolean down = !altDown && beamAbility == Ability.ENERGY_BEAM
                && CrownHelper.has(p, Ability.ENERGY_BEAM) && beamDown;
        if (down && !beamHeld) beamChargeTicks = 0;
        if (down) beamChargeTicks = Math.min(40, beamChargeTicks + 1);
        if (down != beamHeld) {
            send(Ability.ENERGY_BEAM, p.isShiftKeyDown(), !down);
            beamHeld = down;
            if (!down) beamChargeTicks = 0;
        }
        beamAltHeld = altDown && beamDown;
    }

    private static void cycleAbility(Ability slot, List<Ability> choices) {
        int current = choices.indexOf(selectedAbility(slot));
        Ability next = choices.get((current + 1) % choices.size());
        SELECTED_ABILITIES.put(slot, next);
        Minecraft.getInstance().player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.hypercrown.selected",
                        net.minecraft.network.chat.Component.translatable(next.langKey())), true);
        Minecraft.getInstance().level.playLocalSound(
                Minecraft.getInstance().player.blockPosition(),
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.55f, 1.25f + choices.indexOf(next) * 0.2f, false);
    }

    private static void send(Ability a, boolean sneak, boolean released) {
        PacketDistributor.sendToServer(new ActivateAbilityPayload(a.ordinal(), sneak, released));
    }

    /**
     * Client-only particles for every visible crowned player; zero network cost.
     */
    private static void spawnAuras(Minecraft mc) {
        if (mc.level.getGameTime() % 2 != 0) return;
        RandomSource rand = mc.level.random;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p.isInvisible() || !CrownHelper.isCrownActive(p)) continue;
            boolean self = p == mc.player;
            boolean firstPerson = self && mc.options.getCameraType().isFirstPerson();
            double auraPhase = mc.level.getGameTime() * 0.105 + p.getId() * 0.71;
            if (!firstPerson) { // twin rotating rings form a crown-shaped halo
                double headY = p.getY() + p.getBbHeight() + 0.19 + Math.sin(auraPhase * 0.5) * 0.035;
                ParticleOptions gold = self && ClientCrownState.anyCoolingDown() ? VIOLET : GOLD;
                for (int i = 0; i < 12; i++) {
                    double angle = auraPhase + i * (Math.PI * 2 / 12);
                    double radius = 0.52 * layerSpread("crown_aura", gold);
                    emitVisualParticle(mc, "crown_aura", gold, p.getX() + Math.cos(angle) * radius, headY,
                            p.getZ() + Math.sin(angle) * radius, 0, 0.004, 0);
                }
                for (int i = 0; i < 8; i++) {
                    double angle = -auraPhase * 0.82 + i * (Math.PI * 2 / 8);
                    double radius = 0.7 * layerSpread("crown_aura", ParticleTypes.END_ROD);
                    emitVisualParticle(mc, "crown_aura", ParticleTypes.END_ROD,
                            p.getX() + Math.cos(angle) * radius, headY + 0.13,
                            p.getZ() + Math.sin(angle) * radius, 0, 0.006, 0);
                }
            }
            if (rand.nextDouble() < 0.34) emitVisualParticle(mc, "crown_aura", ParticleTypes.END_ROD,
                    p.getRandomX(0.72), p.getY() + rand.nextDouble() * p.getBbHeight(), p.getRandomZ(0.72), 0, 0.025, 0);
            Vec3 movement = p.getDeltaMovement();
            if (movement.horizontalDistanceSqr() > 0.01) {
                double trailX = p.getX() - movement.x * 1.4;
                double trailZ = p.getZ() - movement.z * 1.4;
                emitVisualParticle(mc, "crown_aura", ParticleTypes.ELECTRIC_SPARK, trailX, p.getY() + 0.18, trailZ, 0, 0.01, 0);
                emitVisualParticle(mc, "crown_aura", VIOLET, trailX, p.getY() + 0.42, trailZ, 0, 0.025, 0);
            }
            if (self && beamHeld) { // charging sparks in front of the eyes
                double beamPhase = mc.level.getGameTime() * 0.14 + p.getId() * 0.71;
                Vec3 forward = p.getViewVector(1f).normalize();
                Vec3 reference = Math.abs(forward.y) > 0.94 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                Vec3 right = forward.cross(reference).normalize();
                Vec3 up = right.cross(forward).normalize();
                Vec3 center = p.getEyePosition().add(forward.scale(0.75));
                float charge = beamChargeProgress();
                double innerRadius = 0.22 + charge * 0.98;
                double outerRadius = 0.4 + charge * 1.55;

                // Both rings are built from a basis perpendicular to the look vector,
                // so they stay face-on to the player instead of lying in world XY.
                int innerPoints = 24;
                for (int i = 0; i < innerPoints; i++) {
                    double angle = beamPhase * 1.7 + i * (Math.PI * 2 / innerPoints);
                    ParticleOptions source = i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : GOLD;
                    double radius = innerRadius * layerSpread("crown_beam", source);
                    Vec3 point = center.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
                    emitVisualParticle(mc, "crown_beam", source, point.x, point.y, point.z, 0, 0, 0);
                }

                // A larger, gently tilted counter-rotating ring makes the full charge
                // feel like a widening aperture around the beam's axis.
                double tilt = Math.toRadians(24);
                Vec3 outerUp = up.scale(Math.cos(tilt)).add(forward.scale(Math.sin(tilt)));
                int outerPoints = 36;
                for (int i = 0; i < outerPoints; i++) {
                    double angle = -beamPhase * 1.05 + i * (Math.PI * 2 / outerPoints);
                    ParticleOptions source = i % 4 == 0 ? GOLD : ParticleTypes.END_ROD;
                    double radius = outerRadius * layerSpread("crown_beam", source);
                    Vec3 point = center.add(right.scale(Math.cos(angle) * radius)).add(outerUp.scale(Math.sin(angle) * radius));
                    emitVisualParticle(mc, "crown_beam", source, point.x, point.y, point.z, 0, 0, 0);
                }

                for (int i = 0; i < 3 + Math.round(charge * 7); i++) {
                    double angle = beamPhase * 2.2 + i * 2.399;
                    double radius = innerRadius * (0.25 + (i % 3) * 0.12);
                    Vec3 spark = center.add(right.scale(Math.cos(angle) * radius))
                            .add(up.scale(Math.sin(angle) * radius));
                    ParticleOptions source = i % 2 == 0 ? ParticleTypes.ENCHANT : ParticleTypes.ELECTRIC_SPARK;
                    emitVisualParticle(mc, "crown_beam", source, spark.x, spark.y, spark.z,
                            -forward.x * 0.02, -forward.y * 0.02, -forward.z * 0.02);
                }
            }
        }
    }

    private static DeveloperWorkspace.VisualLayer layer(String effectId, ParticleOptions source) {
        return DeveloperToolClient.visualLayer(effectId, source);
    }

    private static double layerSpread(String effectId, ParticleOptions source) {
        DeveloperWorkspace.VisualLayer layer = layer(effectId, source);
        return layer == null ? 1 : layer.spread;
    }

    private static void emitVisualParticle(Minecraft mc, String effectId, ParticleOptions source,
                                           double x, double y, double z, double vx, double vy, double vz) {
        DeveloperWorkspace.VisualLayer layer = layer(effectId, source);
        if (layer != null && (!layer.enabled || layer.density <= 0)) return;
        double density = layer == null ? 1 : layer.density;
        int copies = (int) density;
        if (mc.level.random.nextDouble() < density - copies) copies++;
        if (copies == 0) return;
        ParticleOptions output = DeveloperToolClient.visualParticle(layer, source);
        double spread = layer == null ? 1 : layer.spread;
        for (int i = 0; i < Math.min(4, copies); i++)
            mc.level.addParticle(output, x, y, z, vx * spread, vy * spread, vz * spread);
    }
}
