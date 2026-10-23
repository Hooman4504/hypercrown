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
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.2f), 1.0f);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.7f, 0.3f, 1.0f), 1.0f);
    private static boolean beamHeld;

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
        if (down != beamHeld) { send(Ability.ENERGY_BEAM, p.isShiftKeyDown(), !down); beamHeld = down; }
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

            if (!firstPerson) { // rotating halo above the head
                double base = (mc.level.getGameTime() % 40) / 40.0 * Math.PI * 2;
                double y = p.getY() + p.getBbHeight() + 0.2;
                for (int i = 0; i < 3; i++) {
                    double a = base + i * (Math.PI * 2 / 3);
                    mc.level.addParticle(color, p.getX() + Math.cos(a) * 0.5, y, p.getZ() + Math.sin(a) * 0.5, 0, 0, 0);
                }
            }
            if (rand.nextInt(6) == 0)
                mc.level.addParticle(ParticleTypes.END_ROD, p.getRandomX(0.8), p.getY() + rand.nextDouble() * p.getBbHeight(),
                        p.getRandomZ(0.8), 0, 0.02, 0);
            if (p.getDeltaMovement().horizontalDistanceSqr() > 0.01)
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.1, p.getZ(), 0, 0, 0);
            if (self && beamHeld) { // charging sparks in front of the eyes
                Vec3 eye = p.getEyePosition().add(p.getViewVector(1f).scale(0.8));
                mc.level.addParticle(ParticleTypes.ENCHANT, eye.x, eye.y - 0.2, eye.z, 0, 0, 0);
            }
        }
    }
}
