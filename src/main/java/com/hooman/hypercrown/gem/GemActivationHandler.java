package com.hooman.hypercrown.gem;

import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.data.CrownPlayerData;
import com.hooman.hypercrown.event.GemProtectionCheckEvent;
import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.item.CatastropheGemItem;
import com.hooman.hypercrown.item.CrownOwner;
import com.hooman.hypercrown.network.GemActivationPayload;
import com.hooman.hypercrown.network.GemScreenShakePayload;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative checks, channel timers, and cooldown handling for all ten gems. */
public final class GemActivationHandler {
    private static final Map<UUID, Charge> CHARGING = new HashMap<>();

    private GemActivationHandler() {}

    public static void activate(ServerPlayer player, GemActivationPayload payload) {
        if (!HypercrownConfig.GEMS_ENABLED.get()) {
            message(player, "message.hypercrown.gem.disabled");
            return;
        }
        if (payload.gem() < 0 || payload.gem() >= CatastropheGem.values().length) return;
        CatastropheGem gem = CatastropheGem.values()[payload.gem()];
        InteractionHand hand = payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof CatastropheGemItem item)
                || item.gem() != gem || item.isEnhanced() != payload.enhanced()) return;
        if (payload.mount()) {
            mountGem(player, hand, stack, gem);
            return;
        }
        if (CHARGING.containsKey(player.getUUID())) {
            message(player, "message.hypercrown.gem.already_charging");
            return;
        }

        long now = player.getServer().overworld().getGameTime();
        CrownPlayerData data = player.getData(ModRegistries.PLAYER_DATA.get());
        long remaining = data.gemRemaining(gem.id(), now);
        if (remaining > 0) {
            player.displayClientMessage(Component.translatable("message.hypercrown.gem.cooldown", (remaining + 19) / 20), true);
            return;
        }

        if (HypercrownConfig.GEM_OWNER_BINDING.get()) {
            CrownOwner owner = stack.get(ModRegistries.OWNER.get());
            if (owner != null && !owner.id().equals(player.getUUID())) {
                message(player, "message.hypercrown.gem.bound_to_other");
                return;
            }
            if (owner == null) stack.set(ModRegistries.OWNER.get(), new CrownOwner(player.getUUID(), player.getGameProfile().getName()));
        }

        ServerLevel level = player.serverLevel();
        Vec3 target = player.position().add(0, 0.1, 0);
        if (gem.targetBlock()) {
            BlockHitResult hit = level.clip(new ClipContext(player.getEyePosition(),
                    player.getEyePosition().add(player.getLookAngle().scale(100)), ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                message(player, "message.hypercrown.gem.no_target");
                return;
            }
            BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
            target = Vec3.atCenterOf(pos);
        }
        int protectionRadius = effectRadius(gem);
        if (!HypercrownConfig.ALLOW_GEMS_IN_SPAWN.get() && GemProtection.inSpawnProtection(level, target, protectionRadius)) {
            message(player, "message.hypercrown.gem.spawn_protected");
            return;
        }
        if (!HypercrownConfig.ALLOW_GEMS_IN_CLAIMED_LAND.get()) {
            GemProtectionCheckEvent protection = new GemProtectionCheckEvent(player, level, BlockPos.containing(target), protectionRadius);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(protection);
            if (protection.isCanceled()) {
                message(player, "message.hypercrown.gem.claim_protected");
                return;
            }
        }
        if (gem == CatastropheGem.SOLAR_OPAL && (level.getDayTime() % 24000L >= 12000L
                || !level.canSeeSky(BlockPos.containing(target)))) {
            message(player, "message.hypercrown.gem.sunlight_required");
            return;
        }

        int cooldownTicks = gem.cooldownSeconds() * 20;
        if (payload.enhanced() && HypercrownConfig.GEM_ENHANCEMENTS_ENABLED.get()) cooldownTicks = Math.round(cooldownTicks * 0.8f);
        data.startGemCooldown(gem.id(), now, cooldownTicks);
        Vec3 direction = player.getLookAngle().normalize();
        if (gem.channelTicks() > 0) {
            int duration = gem.channelTicks() + (gem == CatastropheGem.APOCALYPTIC_RUBY ? 40 : 0);
            CHARGING.put(player.getUUID(), new Charge(gem, payload.enhanced(), payload.offhand(), level.dimension(),
                    target, direction, duration));
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 0.55f);
            if (gem == CatastropheGem.APOCALYPTIC_RUBY)
                level.playSound(null, BlockPos.containing(target), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 2.4f, 0.85f);
            player.displayClientMessage(Component.translatable("message.hypercrown.gem.channeling", gemName(gem)), true);
        } else {
            GemEffects.execute(level, player, gem, target, direction, payload.enhanced());
            finishStack(player, stack, gem);
        }
    }

    public static void tick(ServerPlayer player) {
        Charge charge = CHARGING.get(player.getUUID());
        if (charge == null) return;
        ItemStack held = player.getItemInHand(charge.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (player.isRemoved() || !player.serverLevel().dimension().equals(charge.dimension)
                || !(held.getItem() instanceof CatastropheGemItem item) || item.gem() != charge.gem
                || item.isEnhanced() != charge.enhanced) {
            CHARGING.remove(player.getUUID());
            message(player, "message.hypercrown.gem.charge_cancelled");
            return;
        }
        charge.ticksLeft--;
        if (charge.ticksLeft % 5 == 0) {
            ServerLevel level = player.serverLevel();
            int elapsed = charge.duration - charge.ticksLeft;
            if (charge.gem == CatastropheGem.CELESTIAL_SAPPHIRE) {
                double chargeSpread = 0.45 + Math.min(1.8, elapsed * 0.025);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        charge.target.x, charge.target.y + 0.5, charge.target.z, 18, chargeSpread, 0.35, chargeSpread, 0.04);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                        charge.target.x, charge.target.y + 0.5, charge.target.z, 30, chargeSpread, 0.7, chargeSpread, 0.15);
                if (charge.ticksLeft % 20 == 0)
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                            charge.target.x, charge.target.y + 0.5, charge.target.z, 1, 0, 0, 0, 0);
            } else if (charge.gem == CatastropheGem.APOCALYPTIC_RUBY) {
                double blastSpread = 0.8 + Math.min(5.0, elapsed * 0.045);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        charge.target.x, charge.target.y + 0.5, charge.target.z, 24, blastSpread, 0.5, blastSpread, 0.025);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                        charge.target.x, charge.target.y + 0.5, charge.target.z, 22, blastSpread * 0.55, 0.45, blastSpread * 0.55, 0.06);
                if (charge.ticksLeft <= 40) {
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                            charge.target.x, charge.target.y + 0.5, charge.target.z, 18, blastSpread * 0.7, 0.55, blastSpread * 0.7, 0.08);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                            charge.target.x, charge.target.y + 0.5, charge.target.z, 5, blastSpread * 0.45, 0.35, blastSpread * 0.45, 0.02);
                    if (charge.ticksLeft % 20 == 0)
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                                charge.target.x, charge.target.y + 0.5, charge.target.z, 1, 0, 0, 0, 0);
                }
            }
        }
        if (charge.ticksLeft > 0) return;

        CHARGING.remove(player.getUUID());
        ItemStack current = player.getItemInHand(charge.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        GemEffects.execute(player.serverLevel(), player, charge.gem, charge.target, charge.direction, charge.enhanced);
        finishStack(player, current, charge.gem);
    }

    public static void cancel(ServerPlayer player) {
        CHARGING.remove(player.getUUID());
    }

    private static void finishStack(ServerPlayer player, ItemStack stack, CatastropheGem gem) {
        if (!player.getAbilities().instabuild) {
            if (gem.consumed()) stack.shrink(1);
            else if (!stack.isEmpty()) {
                net.minecraft.world.entity.EquipmentSlot slot = player.getMainHandItem() == stack
                        ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND;
                stack.hurtAndBreak(1, player, slot);
            }
        }
        CrownPlayerData data = player.getData(ModRegistries.PLAYER_DATA.get());
        data.usedCatastropheGemMask |= 1 << gem.ordinal();
        if (gem == CatastropheGem.APOCALYPTIC_RUBY) award(player, "devastation", "used");
        if (gem == CatastropheGem.TIME_QUARTZ) award(player, "time_lord", "used");
        int allGems = (1 << CatastropheGem.values().length) - 1;
        if ((data.usedCatastropheGemMask & allGems) == allGems) award(player, "apocalypse", "used");
    }

    private static void mountGem(ServerPlayer player, InteractionHand gemHand, ItemStack gemStack, CatastropheGem gem) {
        if (HypercrownConfig.GEM_OWNER_BINDING.get()) {
            CrownOwner owner = gemStack.get(ModRegistries.OWNER.get());
            if (owner != null && !owner.id().equals(player.getUUID())) {
                message(player, "message.hypercrown.gem.bound_to_other");
                return;
            }
            if (owner == null) gemStack.set(ModRegistries.OWNER.get(), new CrownOwner(player.getUUID(), player.getGameProfile().getName()));
        }
        InteractionHand toolHand = gemHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack tool = player.getItemInHand(toolHand);
        if (tool.is(ModRegistries.HYPERCROWN.get())) {
            if (tool.has(ModRegistries.SOCKETED_CATASTROPHE_GEM.get())) {
                message(player, "message.hypercrown.gem.socket_occupied");
                return;
            }
            tool.set(ModRegistries.SOCKETED_CATASTROPHE_GEM.get(), gem.id());
            if (!player.getAbilities().instabuild) gemStack.shrink(1);
            if (gem == CatastropheGem.THUNDER_DIAMOND)
                message(player, "message.hypercrown.gem.thunder_mounted_crown");
            else player.displayClientMessage(Component.translatable("message.hypercrown.catastrophe_gem.socketed_crown", gemName(gem)), true);
            return;
        }
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
                HypercrownMod.id("gem_socket"));
        var socketEnchant = player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).get(key).orElse(null);
        if (tool.isEmpty() || socketEnchant == null || net.minecraft.world.item.enchantment.EnchantmentHelper
                .getItemEnchantmentLevel(socketEnchant, tool) <= 0) {
            message(player, "message.hypercrown.gem.socket_requires_enchantment");
            return;
        }
        if (tool.has(ModRegistries.SOCKETED_CATASTROPHE_GEM.get())) {
            message(player, "message.hypercrown.gem.socket_occupied");
            return;
        }
        tool.set(ModRegistries.SOCKETED_CATASTROPHE_GEM.get(), gem.id());
        if (!player.getAbilities().instabuild) gemStack.shrink(1);
        player.displayClientMessage(Component.translatable("message.hypercrown.catastrophe_gem.socketed", gemName(gem)), true);
    }

    public static void award(ServerPlayer player, String advancementId, String criterion) {
        var advancement = player.getServer().getAdvancements().get(HypercrownMod.id(advancementId));
        if (advancement != null) player.getAdvancements().award(advancement, criterion);
    }

    private static int effectRadius(CatastropheGem gem) {
        return switch (gem) {
            case CELESTIAL_SAPPHIRE -> 30;
            case APOCALYPTIC_RUBY -> 50;
            case VOID_EMERALD -> 55;
            case THUNDER_DIAMOND -> 100;
            case FROST_TOPAZ -> 30;
            case SOLAR_OPAL -> 40;
            case SHADOW_AMETHYST -> 20;
            case TECTONIC_JADE -> 60;
            case WIND_SAPPHIRE -> 40;
            case TIME_QUARTZ -> 50;
        };
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    public static void requestShake(ServerPlayer player, int ticks, float strength) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new GemScreenShakePayload(ticks, strength));
    }

    private static Component gemName(CatastropheGem gem) {
        return Component.translatable("item.hypercrown." + gem.id());
    }

    private static final class Charge {
        private final CatastropheGem gem;
        private final boolean enhanced;
        private final boolean offhand;
        private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        private final Vec3 target;
        private final Vec3 direction;
        private final int duration;
        private int ticksLeft;

        private Charge(CatastropheGem gem, boolean enhanced, boolean offhand,
                       net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                       Vec3 target, Vec3 direction, int ticksLeft) {
            this.gem = gem;
            this.enhanced = enhanced;
            this.offhand = offhand;
            this.dimension = dimension;
            this.target = target;
            this.direction = direction;
            this.duration = ticksLeft;
            this.ticksLeft = ticksLeft;
        }
    }
}
