package com.hooman.hypercrown.event;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.data.CrownPlayerData;
import com.hooman.hypercrown.gem.CatastropheGem;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** On-hit abilities for all catastrophe gems socketed in a weapon or the Hypercrown. */
@EventBusSubscriber(modid = HypercrownMod.MODID)
public final class GemSocketEvents {
    private GemSocketEvents() {}

    @SubscribeEvent
    public static void onWeaponHit(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !(event.getEntity().level() instanceof ServerLevel level)) return;

        for (ItemStack stack : socketedItems(player)) {
            CatastropheGem gem = CatastropheGem.byId(stack.get(ModRegistries.SOCKETED_CATASTROPHE_GEM.get()));
            if (gem != null) applyProc(player, level, event.getEntity(), gem);
        }
    }

    private static List<ItemStack> socketedItems(ServerPlayer player) {
        List<ItemStack> result = new ArrayList<>(3);
        addIfSocketed(result, player.getMainHandItem());
        addIfSocketed(result, player.getOffhandItem());
        ItemStack crown = player.getItemBySlot(EquipmentSlot.HEAD);
        if (crown.is(ModRegistries.HYPERCROWN.get())) addIfSocketed(result, crown);
        return result;
    }

    private static void addIfSocketed(List<ItemStack> items, ItemStack stack) {
        if (stack.has(ModRegistries.SOCKETED_CATASTROPHE_GEM.get())) items.add(stack);
    }

    private static void applyProc(ServerPlayer player, ServerLevel level, LivingEntity target, CatastropheGem gem) {
        long now = player.getServer().overworld().getGameTime();
        CrownPlayerData data = player.getData(ModRegistries.PLAYER_DATA.get());
        String cooldownKey = "socketed_gem_" + gem.id();
        if (data.gemRemaining(cooldownKey, now) > 0) return;
        data.startGemCooldown(cooldownKey, now, cooldownTicks(gem));

        float damage = (float) Math.min(18, 4 * HypercrownConfig.GEM_DAMAGE_MULTIPLIER.get());
        switch (gem) {
            case CELESTIAL_SAPPHIRE -> {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0));
                radialDamage(player, level, target, 4.5, damage * 1.2f);
                burst(level, target, ParticleTypes.END_ROD, 45);
                burst(level, target, ParticleTypes.ELECTRIC_SPARK, 55);
                ring(level, target.position(), 4.5, ParticleTypes.END_ROD, 24);
            }
            case APOCALYPTIC_RUBY -> {
                target.igniteForSeconds(8);
                radialDamage(player, level, target, 5.0, damage * 1.5f);
                for (LivingEntity nearby : nearby(level, target, 5.0, player, target)) nearby.igniteForSeconds(5);
                burst(level, target, ParticleTypes.FLAME, 65);
                burst(level, target, ParticleTypes.SOUL_FIRE_FLAME, 35);
                level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                        SoundSource.PLAYERS, 1.1f, 1.25f);
            }
            case VOID_EMERALD -> {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
                player.heal(3.0f);
                for (LivingEntity nearby : nearby(level, target, 6.0, player, target)) {
                    Vec3 pull = target.position().subtract(nearby.position()).normalize();
                    nearby.push(pull.x * 0.75, pull.y * 0.35, pull.z * 0.75);
                    nearby.hurtMarked = true;
                }
                burst(level, target, ParticleTypes.REVERSE_PORTAL, 60);
            }
            case THUNDER_DIAMOND -> chainLightning(player, level, target, damage);
            case FROST_TOPAZ -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 3));
                target.hurt(level.damageSources().freeze(), damage * 0.8f);
                target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze(), target.getTicksFrozen() + 80));
                burst(level, target, ParticleTypes.SNOWFLAKE, 55);
                burst(level, target, ParticleTypes.CLOUD, 18);
            }
            case SOLAR_OPAL -> {
                target.igniteForSeconds(8);
                radialDamage(player, level, target, 3.5, damage);
                burst(level, target, ParticleTypes.FLAME, 55);
                level.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + 1.0, target.getZ(),
                        1, 0, 0, 0, 0);
            }
            case SHADOW_AMETHYST -> {
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, 1));
                player.heal(2.0f);
                burst(level, target, ParticleTypes.SOUL, 30);
                burst(level, target, ParticleTypes.PORTAL, 40);
            }
            case TECTONIC_JADE -> {
                for (LivingEntity nearby : nearby(level, target, 6.0, player, null)) {
                    Vec3 away = nearby.position().subtract(target.position()).normalize();
                    nearby.push(away.x * 1.25, 0.65, away.z * 1.25);
                    nearby.hurtMarked = true;
                    if (nearby != target) nearby.hurt(player.damageSources().playerAttack(player), damage);
                }
                burst(level, target, ParticleTypes.CLOUD, 55);
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                                ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()),
                        target.getX(), target.getY() + 0.2, target.getZ(), 28, 0.55, 0.35, 0.55, 0.1);
            }
            case WIND_SAPPHIRE -> {
                Vec3 away = target.position().subtract(player.position()).normalize();
                target.push(away.x * 0.9, 0.8, away.z * 0.9);
                target.hurtMarked = true;
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1));
                burst(level, target, ParticleTypes.CLOUD, 30);
                burst(level, target, ParticleTypes.SWEEP_ATTACK, 12);
            }
            case TIME_QUARTZ -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 6));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                target.setDeltaMovement(Vec3.ZERO);
                target.hurtMarked = true;
                burst(level, target, ParticleTypes.END_ROD, 45);
                burst(level, target, ParticleTypes.REVERSE_PORTAL, 24);
            }
        }
    }

    private static void chainLightning(ServerPlayer player, ServerLevel level, LivingEntity first, float damage) {
        LivingEntity last = first;
        Set<UUID> struck = new HashSet<>();
        struck.add(first.getUUID());
        for (int count = 0; count < 5; count++) {
            LivingEntity from = last;
            LivingEntity next = level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(7.0), entity ->
                            entity.isAlive() && entity != player && !entity.isAlliedTo(player)
                                    && !struck.contains(entity.getUUID()) && entity.distanceToSqr(from) <= 49.0)
                    .stream().min(Comparator.comparingDouble(from::distanceToSqr)).orElse(null);
            if (next == null) break;
            next.hurt(player.damageSources().indirectMagic(player, player), damage);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, next.getX(), next.getY() + 1.0, next.getZ(),
                    24, 0.45, 0.65, 0.45, 0.15);
            struck.add(next.getUUID());
            last = next;
        }
        burst(level, first, ParticleTypes.ELECTRIC_SPARK, 38);
        level.playSound(null, first.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 1.0f, 1.45f);
    }

    private static void radialDamage(ServerPlayer player, ServerLevel level, LivingEntity center,
                                     double radius, float damage) {
        for (LivingEntity entity : nearby(level, center, radius, player, center))
            entity.hurt(player.damageSources().playerAttack(player), damage);
    }

    private static List<LivingEntity> nearby(ServerLevel level, LivingEntity center, double radius,
                                             ServerPlayer player, LivingEntity excluded) {
        return level.getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius), entity ->
                entity.isAlive() && entity != player && entity != excluded && !entity.isAlliedTo(player)
                        && entity.distanceToSqr(center) <= radius * radius);
    }

    private static void burst(ServerLevel level, LivingEntity target,
                              net.minecraft.core.particles.SimpleParticleType particle, int count) {
        level.sendParticles(particle, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                count, 0.5, 0.55, 0.5, 0.08);
    }

    private static void ring(ServerLevel level, Vec3 center, double radius,
                             net.minecraft.core.particles.SimpleParticleType particle, int points) {
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y + 0.1,
                    center.z + Math.sin(angle) * radius, 1, 0.025, 0.025, 0.025, 0.01);
        }
    }

    private static int cooldownTicks(CatastropheGem gem) {
        return switch (gem) {
            case CELESTIAL_SAPPHIRE, TIME_QUARTZ -> 120;
            case APOCALYPTIC_RUBY -> 140;
            case VOID_EMERALD, SOLAR_OPAL, SHADOW_AMETHYST, TECTONIC_JADE -> 100;
            case THUNDER_DIAMOND -> 40;
            case FROST_TOPAZ, WIND_SAPPHIRE -> 80;
        };
    }
}
