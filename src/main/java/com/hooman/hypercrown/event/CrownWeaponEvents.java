package com.hooman.hypercrown.event;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.data.CrownPlayerData;
import com.hooman.hypercrown.forge.CrownWeaponKind;
import com.hooman.hypercrown.item.CrownForgedWeaponItem;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Distinct gem powers for each weapon forged by the Hypercrown. */
@EventBusSubscriber(modid = HypercrownMod.MODID)
public final class CrownWeaponEvents {
    private CrownWeaponEvents() {}

    @SubscribeEvent
    public static void onForgedWeaponHit(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !(player.getMainHandItem().getItem() instanceof CrownForgedWeaponItem weapon)) return;

        CrownWeaponKind kind = weapon.kind();
        CrownPlayerData data = player.getData(ModRegistries.PLAYER_DATA.get());
        String cooldownKey = "crown_weapon_" + kind.id();
        long now = player.getServer().overworld().getGameTime();
        if (data.gemRemaining(cooldownKey, now) > 0) return;
        data.startGemCooldown(cooldownKey, now, kind.procCooldownTicks());

        LivingEntity target = event.getEntity();
        switch (kind) {
            case CELESTIAL_EDGE -> {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
                damageNearby(player, level, target, 4.0, 9.0f);
                burst(level, target, ParticleTypes.END_ROD, 45);
            }
            case APOCALYPSE_BLADE -> {
                target.igniteForSeconds(10);
                damageNearby(player, level, target, 5.0, 12.0f);
                for (LivingEntity nearby : nearby(level, target, 5.0, player, target)) nearby.igniteForSeconds(5);
                burst(level, target, ParticleTypes.FLAME, 70);
                level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.6f, 0.8f);
            }
            case VOID_REAPER -> {
                player.heal(5.0f);
                for (LivingEntity nearby : nearby(level, target, 8.0, player, target)) {
                    Vec3 pull = target.position().subtract(nearby.position()).normalize();
                    nearby.push(pull.x * 0.9, pull.y * 0.45, pull.z * 0.9);
                    nearby.hurtMarked = true;
                }
                burst(level, target, ParticleTypes.REVERSE_PORTAL, 55);
            }
            case THUNDER_FANG -> {
                LivingEntity last = target;
                int chains = 0;
                java.util.Set<java.util.UUID> struck = new java.util.HashSet<>();
                struck.add(target.getUUID());
                while (chains < 4) {
                    LivingEntity from = last;
                    LivingEntity next = level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(9.0), entity ->
                                    entity.isAlive() && entity != player && !struck.contains(entity.getUUID()) && !entity.isAlliedTo(player)
                                            && entity.distanceToSqr(from) <= 81.0)
                            .stream().min(java.util.Comparator.comparingDouble(from::distanceToSqr)).orElse(null);
                    if (next == null) break;
                    next.hurt(level.damageSources().playerAttack(player), 12.0f);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, next.getX(), next.getY() + 1.0, next.getZ(),
                            24, 0.45, 0.65, 0.45, 0.15);
                    struck.add(next.getUUID());
                    last = next;
                    chains++;
                }
                burst(level, target, ParticleTypes.ELECTRIC_SPARK, 38);
                level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.3f, 1.45f);
            }
            case FROSTMAW -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 5));
                target.hurt(level.damageSources().freeze(), 6.0f);
                target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze(), target.getTicksFrozen() + 100));
                burst(level, target, ParticleTypes.SNOWFLAKE, 60);
            }
            case SOLAR_BRAND -> {
                target.igniteForSeconds(12);
                damageNearby(player, level, target, 3.5, 10.0f);
                burst(level, target, ParticleTypes.FLAME, 55);
                level.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + 1.0, target.getZ(), 1, 0, 0, 0, 0);
            }
            case SHADOW_SCYTHE -> {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2));
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                player.heal(3.0f);
                burst(level, target, ParticleTypes.SOUL, 45);
            }
            case TECTONIC_BREAKER -> {
                for (LivingEntity nearby : nearby(level, target, 7.0, player, null)) {
                    Vec3 away = nearby.position().subtract(target.position()).normalize();
                    nearby.push(away.x * 1.5, 0.75, away.z * 1.5);
                    nearby.hurtMarked = true;
                    if (nearby != target) nearby.hurt(level.damageSources().playerAttack(player), 8.0f);
                }
                burst(level, target, ParticleTypes.CLOUD, 50);
            }
            case GALE_DANCER -> {
                Vec3 away = target.position().subtract(player.position()).normalize();
                target.push(away.x * 1.1, 1.0, away.z * 1.1);
                target.hurtMarked = true;
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1));
                burst(level, target, ParticleTypes.CLOUD, 45);
            }
            case CHRONO_BLADE -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 240, 8));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
                target.setDeltaMovement(Vec3.ZERO);
                target.hurtMarked = true;
                burst(level, target, ParticleTypes.END_ROD, 55);
            }
        }
    }

    private static void damageNearby(ServerPlayer player, ServerLevel level, LivingEntity center, double radius, float damage) {
        for (LivingEntity entity : nearby(level, center, radius, player, center))
            entity.hurt(level.damageSources().playerAttack(player), damage);
    }

    private static java.util.List<LivingEntity> nearby(ServerLevel level, LivingEntity center, double radius,
                                                       ServerPlayer player, LivingEntity excluded) {
        return level.getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius), entity ->
                entity.isAlive() && entity != player && entity != excluded && !entity.isAlliedTo(player)
                        && entity.distanceToSqr(center) <= radius * radius);
    }

    private static void burst(ServerLevel level, LivingEntity target, net.minecraft.core.particles.SimpleParticleType particle,
                              int count) {
        level.sendParticles(particle, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                count, 0.5, 0.55, 0.5, 0.08);
    }
}
