package com.hooman.hypercrown;

import com.mojang.logging.LogUtils;
import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.network.ModNetwork;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/** Entry point. Game-bus handlers register themselves via @EventBusSubscribe. */
@Mod(HypercrownMod.MODID)
public final class HypercrownMod {
    public static final String MODID = "hypercrown";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HypercrownMod(IEventBus modBus, ModContainer container) {
        ModRegistries.register(modBus);
        modBus.addListener(ModNetwork::register);
        // SERVER config: stored per world, synced to clients, editable by server owners.
        container.registerConfig(ModConfig.Type.SERVER, HypercrownConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
