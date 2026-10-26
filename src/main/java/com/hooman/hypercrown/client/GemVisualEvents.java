package com.hooman.hypercrown.client;

import com.hooman.hypercrown.HypercrownMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;

@EventBusSubscriber(modid = HypercrownMod.MODID, value = Dist.CLIENT)
public final class GemVisualEvents {
    private static int shakeTicks;
    private static float shakeStrength;
    private static long frame;

    private GemVisualEvents() {
    }

    public static void shake(int ticks, float strength) {
        shakeTicks = Math.max(shakeTicks, ticks);
        shakeStrength = Math.max(shakeStrength, strength);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (shakeTicks > 0) {
            shakeTicks--;
            shakeStrength *= 0.975f;
        } else shakeStrength = 0;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shakeTicks <= 0 || shakeStrength <= 0 || Minecraft.getInstance().screen != null) return;
        frame++;
        double phase = frame * 2.73;
        float amount = shakeStrength * 1.25f;
        event.setYaw(event.getYaw() + (float) Math.sin(phase) * amount);
        event.setPitch(event.getPitch() + (float) Math.cos(phase * 1.31) * amount);
        event.setRoll(event.getRoll() + (float) Math.sin(phase * 0.77) * amount * 0.6f);
    }
}
