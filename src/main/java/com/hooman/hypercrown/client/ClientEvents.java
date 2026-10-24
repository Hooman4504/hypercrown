package com.hooman.hypercrown.client;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.ability.Ability;
import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.network.ActivateAbilityPayload;
import com.hooman.hypercrown.network.ClientCrownState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
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

@EventBusSubscriber(modid = HypercrownMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.2f), 0.8f);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.68f, 0.32f, 1.0f), 0.72f);
    private static boolean beamHeld;
    private static int beamChargeTicks;

    public static boolean isBeamCharging() { return beamHeld; }

    public static float beamChargeProgress() {
        return Math.min(1.0f, beamChargeTicks / 40.0f);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ClientCrownState.tick();
        handleKeys(mc.player);
        spawnAuras(mc);
    }

    private static void handleKeys(LocalPlayer p) {
        boolean active = CrownHelper.isCrownActive(p);
        for (Ability a : List.of(Ability.BLINK, Ability.ANCHOR, Ability.WEATHER_CONTROL)) {
            while (ClientSetup.KEYS.get(a).consumeClick()) if (active) send(a, p.isShiftKeyDown(), false);
        }
        while (ClientSetup.BEAM.consumeClick()) { /* drain; beam uses press/release edges */ }
        boolean down = active && ClientSetup.BEAM.isDown();
        if (down && !beamHeld) beamChargeTicks = 0;
        if (down) beamChargeTicks = Math.min(40, beamChargeTicks + 1);
        if (down != beamHeld) {
            send(Ability.ENERGY_BEAM, p.isShiftKeyDown(), !down);
            beamHeld = down;
            if (!down) beamChargeTicks = 0;
        }
    }

    private static void send(Ability a, boolean sneak, boolean released) {
        PacketDistributor.sendToServer(new ActivateAbilityPayload(a.ordinal(), sneak, released));
    }

    /** Client-only particles for every visible crowned player; zero network cost. */
    private static void spawnAuras(Minecraft mc) {
        if (mc.level.getGameTime() % 2 != 0) return;
        RandomSource rand = mc.level.random;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p.isInvisible() || !CrownHelper.isCrownActive(p)) continue;
            boolean self = p == mc.player;
            boolean firstPerson = self && mc.options.getCameraType().isFirstPerson();
            DustParticleOptions color = self && ClientCrownState.anyCoolingDown() ? VIOLET : GOLD;

            double phase = mc.level.getGameTime() * 0.105 + p.getId() * 0.71;
            if (!firstPerson) { // twin rotating rings form a crown-shaped halo
                double headY = p.getY() + p.getBbHeight() + 0.19 + Math.sin(phase * 0.5) * 0.035;
                for (int i = 0; i < 12; i++) {
                    double angle = phase + i * (Math.PI * 2 / 12);
                    mc.level.addParticle(color, p.getX() + Math.cos(angle) * 0.52, headY,
                            p.getZ() + Math.sin(angle) * 0.52, 0, 0.004, 0);
                }
                for (int i = 0; i < 8; i++) {
                    double angle = -phase * 0.82 + i * (Math.PI * 2 / 8);
                    mc.level.addParticle(VIOLET, p.getX() + Math.cos(angle) * 0.7, headY + 0.13,
                            p.getZ() + Math.sin(angle) * 0.7, 0, 0.006, 0);
                }
            }
            if (rand.nextInt(3) == 0)
                mc.level.addParticle(rand.nextBoolean() ? ParticleTypes.END_ROD : GOLD,
                        p.getRandomX(0.72), p.getY() + rand.nextDouble() * p.getBbHeight(),
                        p.getRandomZ(0.72), 0, 0.025, 0);
            Vec3 movement = p.getDeltaMovement();
            if (movement.horizontalDistanceSqr() > 0.01) {
                double trailX = p.getX() - movement.x * 1.4;
                double trailZ = p.getZ() - movement.z * 1.4;
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, trailX, p.getY() + 0.18, trailZ, 0, 0.01, 0);
                mc.level.addParticle(VIOLET, trailX, p.getY() + 0.42, trailZ, 0, 0.025, 0);
            }
            if (self && beamHeld) { // charging sparks in front of the eyes
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
                for (int i = 0; i < 24; i++) {
                    double angle = phase * 1.7 + i * (Math.PI * 2 / 24);
                    Vec3 point = center.add(right.scale(Math.cos(angle) * innerRadius))
                            .add(up.scale(Math.sin(angle) * innerRadius));
                    mc.level.addParticle(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : GOLD,
                            point.x, point.y, point.z, 0, 0, 0);
                }

                // A larger, gently tilted counter-rotating ring makes the full charge
                // feel like a widening aperture around the beam's axis.
                double tilt = Math.toRadians(24);
                Vec3 outerUp = up.scale(Math.cos(tilt)).add(forward.scale(Math.sin(tilt)));
                for (int i = 0; i < 36; i++) {
                    double angle = -phase * 1.05 + i * (Math.PI * 2 / 36);
                    Vec3 point = center.add(right.scale(Math.cos(angle) * outerRadius))
                            .add(outerUp.scale(Math.sin(angle) * outerRadius));
                    mc.level.addParticle(i % 4 == 0 ? ParticleTypes.END_ROD : VIOLET,
                            point.x, point.y, point.z, 0, 0, 0);
                }

                for (int i = 0; i < 3 + Math.round(charge * 7); i++) {
                    double angle = phase * 2.2 + i * 2.399;
                    double radius = innerRadius * (0.25 + (i % 3) * 0.12);
                    Vec3 spark = center.add(right.scale(Math.cos(angle) * radius))
                            .add(up.scale(Math.sin(angle) * radius));
                    mc.level.addParticle(i % 2 == 0 ? ParticleTypes.ENCHANT : ParticleTypes.ELECTRIC_SPARK,
                            spark.x, spark.y, spark.z, -forward.x * 0.02, -forward.y * 0.02, -forward.z * 0.02);
                }
            }
        }
    }
}
