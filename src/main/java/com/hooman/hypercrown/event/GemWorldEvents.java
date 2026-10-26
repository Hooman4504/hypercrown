package com.hooman.hypercrown.event;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.gem.GemActivationHandler;
import com.hooman.hypercrown.gem.GemEffects;
import com.hooman.hypercrown.registry.ModRegistries;
import com.hooman.hypercrown.data.CrownPlayerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import static com.hooman.hypercrown.HypercrownMod.MODID;

/** Deepslate discovery and ticking for channels and persistent gemstone zones. */
@EventBusSubscriber(modid = MODID)
public final class GemWorldEvents {
    private GemWorldEvents() {}

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!com.hooman.hypercrown.config.HypercrownConfig.GEMS_ENABLED.get()) return;
        BlockPos pos = event.getPos();
        if (pos.getY() < -60 || pos.getY() > -40) return;
        String path = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).getPath();
        if (!path.equals("deepslate") && !path.contains("deepslate")) return;
        var random = event.getLevel().getRandom();
        double rate = 0.0001 * com.hooman.hypercrown.config.HypercrownConfig.DEEPSLATE_GEM_CHANCE.get();
        if (rate <= 0) return;
        if (random.nextDouble() < rate) {
            int ordinaryCount = com.hooman.hypercrown.gem.CatastropheGem.values().length - 1;
            int selected = random.nextInt(ordinaryCount);
            var gem = com.hooman.hypercrown.gem.CatastropheGem.values()[selected];
            addDrop(event, pos, gem.stack());
        }
        // Time Quartz has its own independent 1-in-20,000 roll.
        if (random.nextDouble() < rate * 0.5) {
            addDrop(event, pos, com.hooman.hypercrown.gem.CatastropheGem.TIME_QUARTZ.stack());
        }
    }

    private static void addDrop(BlockDropsEvent event, BlockPos pos, ItemStack stack) {
        event.getDrops().add(new ItemEntity(event.getLevel(), pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5, stack));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GemActivationHandler.tick(player);
            if (player.tickCount % 20 == 0) updateGemAdvancements(player);
            if (player.tickCount % 8 == 0) heldGemAura(player);
        }
    }

    private static void heldGemAura(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        com.hooman.hypercrown.item.CatastropheGemItem gem;
        if (held.getItem() instanceof com.hooman.hypercrown.item.CatastropheGemItem mainHandGem) {
            gem = mainHandGem;
        } else {
            held = player.getOffhandItem();
            if (!(held.getItem() instanceof com.hooman.hypercrown.item.CatastropheGemItem offHandGem)) return;
            gem = offHandGem;
        }
        SimpleParticleType particle = switch (gem.gem()) {
            case CELESTIAL_SAPPHIRE -> ParticleTypes.END_ROD;
            case APOCALYPTIC_RUBY -> ParticleTypes.FLAME;
            case VOID_EMERALD -> ParticleTypes.REVERSE_PORTAL;
            case THUNDER_DIAMOND -> ParticleTypes.ELECTRIC_SPARK;
            case FROST_TOPAZ -> ParticleTypes.SNOWFLAKE;
            case SOLAR_OPAL -> ParticleTypes.FLAME;
            case SHADOW_AMETHYST -> ParticleTypes.PORTAL;
            case TECTONIC_JADE -> ParticleTypes.HAPPY_VILLAGER;
            case WIND_SAPPHIRE -> ParticleTypes.CLOUD;
            case TIME_QUARTZ -> ParticleTypes.END_ROD;
        };
        if (gem.isEnhanced()) particle = ParticleTypes.ENCHANT;
        Vec3 look = player.getLookAngle();
        double x = player.getX() - look.z * 0.35;
        double y = player.getY() + player.getBbHeight() * 0.65;
        double z = player.getZ() + look.x * 0.35;
        player.serverLevel().sendParticles(particle, x, y, z, 2, 0.08, 0.1, 0.08, 0.01);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) GemActivationHandler.cancel(player);
    }

    @SubscribeEvent
    public static void onGemPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getOriginalStack().getItem() instanceof com.hooman.hypercrown.item.CatastropheGemItem)) return;
        updateGemAdvancements(player);
    }

    private static void updateGemAdvancements(ServerPlayer player) {
        CrownPlayerData data = player.getData(ModRegistries.PLAYER_DATA.get());
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof com.hooman.hypercrown.item.CatastropheGemItem gem)
                data.discoveredCatastropheGemMask |= 1 << gem.gem().ordinal();
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof com.hooman.hypercrown.item.CatastropheGemItem gem)
                data.discoveredCatastropheGemMask |= 1 << gem.gem().ordinal();
        }
        int found = Integer.bitCount(data.discoveredCatastropheGemMask);
        if (found > 0) GemActivationHandler.award(player, "first_discovery", "found");
        if (found >= 5) GemActivationHandler.award(player, "collector", "collected");
        if (found >= com.hooman.hypercrown.gem.CatastropheGem.values().length)
            GemActivationHandler.award(player, "gem_master", "collected");
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) GemEffects.tick(level);
    }
}
