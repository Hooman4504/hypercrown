package com.hooman.hypercrown.event;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.dev.DeveloperToolService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = HypercrownMod.MODID)
public final class DeveloperToolEvents {
    private DeveloperToolEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DeveloperToolService.tick(event);
    }
}
