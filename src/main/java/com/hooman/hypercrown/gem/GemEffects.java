package com.hooman.hypercrown.gem;

import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.dev.DeveloperToolService;
import com.hooman.hypercrown.dev.DeveloperWorkspace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Catastrophic gem combat, terrain edits, and short-lived area effects. */
public final class GemEffects {
    private static final List<Zone> ZONES = new ArrayList<>();
    private static final List<Trail> TRAILS = new ArrayList<>();
    private static final int BLOCK_CHANGE_BUDGET = 24_000;
    private static final ThreadLocal<String> ACTIVE_VISUAL = new ThreadLocal<>();

    private GemEffects() {}

    public static void execute(ServerLevel level, ServerPlayer user, CatastropheGem gem, Vec3 target,
                               Vec3 direction, boolean enhanced) {
        double strength = HypercrownConfig.GEM_DAMAGE_MULTIPLIER.get() * (enhanced && HypercrownConfig.GEM_ENHANCEMENTS_ENABLED.get() ? 1.5 : 1.0);
        ACTIVE_VISUAL.set("gem_" + gem.id());
        try {
        if (enhanced && HypercrownConfig.GEM_ENHANCEMENTS_ENABLED.get()) {
            burst(level, target, 0.12, ParticleTypes.ENCHANT, 100);
            emitParticles(level, ParticleTypes.FLASH, target.x, target.y, target.z, 1, 0, 0, 0, 0);
        }
        switch (gem) {
            case CELESTIAL_SAPPHIRE -> orbitalStrike(level, user, target, strength);
            case APOCALYPTIC_RUBY -> nuclearBlast(level, user, target, strength);
            case VOID_EMERALD -> giantSlash(level, user, direction, strength);
            case THUNDER_DIAMOND -> railgun(level, user, direction, strength);
            case FROST_TOPAZ -> {
                freezeTerrain(level, target);
                ZONES.add(new Zone(level, ZoneKind.FROST, user.getUUID(), target, 600));
                level.playSound(null, BlockPos.containing(target), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0f, 0.55f);
                burst(level, target, 0.35, ParticleTypes.SNOWFLAKE, 90);
            }
            case SOLAR_OPAL -> solarFlare(level, target, strength);
            case SHADOW_AMETHYST -> {
                ZONES.add(new Zone(level, ZoneKind.VOID_RIFT, user.getUUID(), target, 300));
                level.playSound(null, BlockPos.containing(target), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 2.0f, 0.65f);
                burst(level, target, 0.2, ParticleTypes.PORTAL, 160);
            }
            case TECTONIC_JADE -> earthquake(level, user, target, strength);
            case WIND_SAPPHIRE -> {
                ZONES.add(new Zone(level, ZoneKind.TORNADO, user.getUUID(), target, 400));
                level.playSound(null, BlockPos.containing(target), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 2.0f, 0.55f);
                burst(level, target, 0.15, ParticleTypes.CLOUD, 120);
            }
            case TIME_QUARTZ -> {
                ZONES.add(new Zone(level, ZoneKind.TIME_STOP, user.getUUID(), target, 200));
                level.playSound(null, BlockPos.containing(target), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0f, 0.5f);
                burst(level, target, 0.04, ParticleTypes.END_ROD, 180);
            }
        }
        } finally {
            ACTIVE_VISUAL.remove();
        }
    }

    private static void orbitalStrike(ServerLevel level, ServerPlayer user, Vec3 center, double strength) {
        level.playSound(null, BlockPos.containing(center), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6.0f, 0.65f);
        emitParticles(level, ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        emitParticles(level, ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        burst(level, center, 0.55, ParticleTypes.ELECTRIC_SPARK, 260);
        burst(level, center, 0.35, ParticleTypes.END_ROD, 160);
        for (double y = center.y + 4; y <= center.y + 40; y += 4) {
            emitParticles(level, ParticleTypes.END_ROD, center.x, y, center.z, 5, 0.3, 0.25, 0.3, 0.02);
            emitParticles(level, ParticleTypes.ELECTRIC_SPARK, center.x, y, center.z, 9, 0.55, 0.35, 0.55, 0.18);
        }
        ring(level, center, 5, 0.2, ParticleTypes.END_ROD, 28);
        ring(level, center, 10, 0.3, ParticleTypes.ELECTRIC_SPARK, 36);
        ring(level, center, 15, 0.4, ParticleTypes.END_ROD, 44);
        radialDamage(level, user, center, 30, 100 * strength);
        crater(level, BlockPos.containing(center), Math.min(15, HypercrownConfig.MAX_CRATER_SIZE.get()), false, 3);
        igniteArea(level, center, 20);
    }

    private static void nuclearBlast(ServerLevel level, ServerPlayer user, Vec3 center, double strength) {
        level.playSound(null, BlockPos.containing(center), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 8.0f, 0.45f);
        level.playSound(null, BlockPos.containing(center), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 8.0f, 0.55f);
        emitParticles(level, ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        emitParticles(level, ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 6, 2, 2, 2, 0.1);
        burst(level, center, 0.7, ParticleTypes.CAMPFIRE_COSY_SMOKE, 220);
        burst(level, center, 0.5, ParticleTypes.FLAME, 220);
        burst(level, center, 0.45, ParticleTypes.SOUL_FIRE_FLAME, 140);
        burst(level, center, 0.8, ParticleTypes.LAVA, 40);
        for (int y = 2; y <= 26; y += 2) {
            double spread = 0.7 + y * 0.045;
            emitParticles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    center.x, center.y + y, center.z, 14, spread, 0.45, spread, 0.025);
            if (y <= 12) emitParticles(level, ParticleTypes.SOUL_FIRE_FLAME,
                    center.x, center.y + y, center.z, 8, spread * 0.45, 0.35, spread * 0.45, 0.04);
        }
        ring(level, center, 8, 0.2, ParticleTypes.FLAME, 28);
        ring(level, center, 16, 0.3, ParticleTypes.SOUL_FIRE_FLAME, 36);
        ring(level, center, 24, 0.4, ParticleTypes.CAMPFIRE_COSY_SMOKE, 44);
        int radius = Math.min(25, HypercrownConfig.MAX_CRATER_SIZE.get());
        radialDamage(level, user, center, 50, 180 * strength);
        applyWither(level, center, 50, 600);
        crater(level, BlockPos.containing(center), radius, true, 8);
    }

    private static void giantSlash(ServerLevel level, ServerPlayer user, Vec3 direction, double strength) {
        Vec3 start = user.getEyePosition();
        Vec3 axis = direction.normalize();
        Vec3 end = start.add(axis.scale(50));
        Vec3 reference = Math.abs(axis.y) > 0.94 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = axis.cross(reference).normalize();
        Vec3 up = right.cross(axis).normalize();
        for (double t = 0; t <= 50; t += 0.8) {
            Vec3 point = start.add(axis.scale(t));
            for (int w = -2; w <= 2; w++) for (int h = -2; h <= 2; h++) {
                BlockPos pos = BlockPos.containing(point.add(right.scale(w)).add(up.scale(h)));
                eraseBlock(level, pos, false);
            }
        }
        for (LivingEntity entity : livingAlong(level, start, end, 3.0)) {
            if (entity != user) entity.hurt(damageSource(level, user), damageFor(entity, 10_000 * strength));
        }
        TRAILS.add(new Trail(level, start, end, right, up, 200));
        level.playSound(null, BlockPos.containing(start), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 3, 0.7f);
        for (int i = 0; i < 64; i++) {
            double t = i * 0.78;
            Vec3 point = start.add(axis.scale(t));
            emitParticles(level, ParticleTypes.SWEEP_ATTACK, point.x, point.y, point.z, 2, 0.2, 1.1, 1.8, 0);
            emitParticles(level, ParticleTypes.REVERSE_PORTAL, point.x, point.y, point.z, 3, 0.5, 0.5, 0.5, 0.04);
        }
    }

    private static void railgun(ServerLevel level, ServerPlayer user, Vec3 direction, double strength) {
        Vec3 start = user.getEyePosition();
        Vec3 end = start.add(direction.normalize().scale(100));
        for (double t = 0; t < 100; t += 0.65) {
            Vec3 point = start.add(direction.normalize().scale(t));
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                Vec3 offset = Math.abs(direction.y) > 0.85 ? new Vec3(a, 0, b) : new Vec3(a, b, 0);
                eraseBlock(level, BlockPos.containing(point.add(offset)), false);
            }
        }
        List<LivingEntity> victims = livingAlong(level, start, end, 1.5).stream().filter(entity -> entity != user).toList();
        DamageSource source = damageSource(level, user);
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity victim : victims) {
            victim.hurt(source, damageFor(victim, 90 * strength));
            hit.add(victim);
        }
        LivingEntity previous = victims.isEmpty() ? null : victims.get(0);
        while (hit.size() < 6 && previous != null) {
            LivingEntity from = previous;
            LivingEntity next = level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(10), e -> e.isAlive()
                    && e != user && !hit.contains(e)).stream().min(java.util.Comparator.comparingDouble(from::distanceToSqr)).orElse(null);
            if (next == null) break;
            next.hurt(source, damageFor(next, 65 * strength));
            emitParticles(level, ParticleTypes.ELECTRIC_SPARK, next.getX(), next.getY() + 1, next.getZ(), 30, 0.5, 0.7, 0.5, 0.2);
            hit.add(next);
            previous = next;
        }
        for (double t = 0; t < 100; t += 2.2) {
            Vec3 p = start.add(direction.normalize().scale(t));
            emitParticles(level, ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 12, 0.25, 0.25, 0.25, 0.25);
            if (t % 6 < 2.2) emitParticles(level, ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.03);
        }
        emitParticles(level, ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        level.playSound(null, user.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3, 1.2f);
    }

    private static void freezeTerrain(ServerLevel level, Vec3 center) {
        int radius = 30;
        int vertical = 16;
        BlockPos origin = BlockPos.containing(center);
        int changed = 0;
        for (int x = -radius; x <= radius && changed < BLOCK_CHANGE_BUDGET; x++)
            for (int z = -radius; z <= radius && changed < BLOCK_CHANGE_BUDGET; z++) {
                if (x * x + z * z > radius * radius) continue;
                for (int y = -vertical; y <= vertical && changed < BLOCK_CHANGE_BUDGET; y++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getFluidState().is(FluidTags.WATER) && !state.hasBlockEntity()) {
                        level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState()); changed++;
                    } else if (state.getFluidState().is(FluidTags.LAVA) && !state.hasBlockEntity()) {
                        level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState()); changed++;
                    } else if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS)) {
                        level.destroyBlock(pos, false); changed++;
                    }
                }
            }
    }

    private static void solarFlare(ServerLevel level, Vec3 center, double strength) {
        level.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 4, 1.6f);
        emitParticles(level, ParticleTypes.FLASH, center.x, center.y + 12, center.z, 1, 0, 0, 0, 0);
        burst(level, center, 0.75, ParticleTypes.FLAME, 240);
        AABB range = new AABB(center, center).inflate(40, 20, 40);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, range,
                e -> e.position().distanceToSqr(center) <= 1600)) {
            entity.hurt(level.damageSources().inFire(), damageFor(entity, 80 * strength));
            entity.igniteForSeconds(10);
            if (entity instanceof ServerPlayer player)
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 300, 0, false, true));
        }
        meltGlassAndIce(level, center, 40);
    }

    private static void meltGlassAndIce(ServerLevel level, Vec3 center, int radius) {
        if (!mayAlterTerrain(level)) return;
        BlockPos origin = BlockPos.containing(center);
        int changed = 0;
        int yRadius = Math.min(radius, 20);
        for (int x = -radius; x <= radius && changed < BLOCK_CHANGE_BUDGET; x++)
            for (int z = -radius; z <= radius && changed < BLOCK_CHANGE_BUDGET; z++) {
                if (x * x + z * z > radius * radius) continue;
                for (int y = -yRadius; y <= yRadius && changed < BLOCK_CHANGE_BUDGET; y++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)
                            || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.GLASS)
                            || state.is(Blocks.GLASS_PANE) || isColoredGlass(state)) {
                        eraseBlock(level, pos, false); changed++;
                    }
                }
            }
    }

    private static boolean isColoredGlass(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.endsWith("_stained_glass") || path.endsWith("_stained_glass_pane") || path.equals("tinted_glass");
    }

    private static void earthquake(ServerLevel level, ServerPlayer user, Vec3 center, double strength) {
        level.playSound(null, BlockPos.containing(center), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 8, 0.5f);
        level.playSound(null, BlockPos.containing(center), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 8, 0.65f);
        burst(level, center, 0.5, ParticleTypes.CLOUD, 240);
        radialDamage(level, user, center, 60, 30 * strength);
        AABB range = new AABB(center, center).inflate(60, 20, 60);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, range, e -> e != user && e.isAlive()
                && e.position().distanceToSqr(center) <= 3600)) {
            Vec3 away = entity.position().subtract(center).normalize();
            entity.push(away.x * 2.3, 0.85 + entity.getRandom().nextDouble() * 0.8, away.z * 2.3);
            entity.hurtMarked = true;
            if (entity instanceof ServerPlayer player) GemActivationHandler.requestShake(player, 36, 1.0f);
        }
        if (!mayAlterTerrain(level)) return;
        BlockPos base = BlockPos.containing(center);
        for (int fissure = 0; fissure < 55; fissure++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(level.getRandom().nextDouble()) * 60;
            int x = base.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = base.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, base.getY(), z));
            int length = 5 + level.getRandom().nextInt(9);
            for (int i = 0; i < length; i++) {
                int sx = surface.getX() + (int) Math.round(Math.cos(angle) * i);
                int sz = surface.getZ() + (int) Math.round(Math.sin(angle) * i);
                for (int depth = 0; depth < 3 + level.getRandom().nextInt(3); depth++) {
                    BlockPos pos = new BlockPos(sx, surface.getY() - depth, sz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        emitParticles(level, new BlockParticleOption(ParticleTypes.BLOCK, state), sx + 0.5, pos.getY() + 0.5, sz + 0.5,
                                3, 0.3, 0.3, 0.3, 0.08);
                        eraseBlock(level, pos, false);
                    }
                }
            }
        }
    }

    private static void crater(ServerLevel level, BlockPos center, int radius, boolean glassFloor, int depth) {
        if (!mayAlterTerrain(level)) return;
        int changed = 0;
        for (int x = -radius; x <= radius && changed < BLOCK_CHANGE_BUDGET; x++)
            for (int z = -radius; z <= radius && changed < BLOCK_CHANGE_BUDGET; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance > radius) continue;
                int surfaceY = center.getY();
                while (surfaceY > level.getMinBuildHeight() + 2 && level.getBlockState(new BlockPos(center.getX() + x, surfaceY, center.getZ() + z)).isAir()) surfaceY--;
                int columnDepth = Math.max(1, (int) Math.ceil(depth * (1 - distance / (radius + 1.0))));
                for (int y = 0; y < columnDepth + 3 && changed < BLOCK_CHANGE_BUDGET; y++) {
                    if (eraseBlock(level, center.offset(x, y, z), false)) changed++;
                }
                for (int y = 0; y < columnDepth + 3 && changed < BLOCK_CHANGE_BUDGET; y++) {
                    BlockPos pos = new BlockPos(center.getX() + x, surfaceY - y, center.getZ() + z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (glassFloor && y == columnDepth - 1 && !state.hasBlockEntity()) {
                        level.setBlockAndUpdate(pos, Blocks.BLACK_STAINED_GLASS.defaultBlockState()); changed++;
                    } else if (y < columnDepth) {
                        if (eraseBlock(level, pos, false)) changed++;
                    }
                }
            }
    }

    private static void igniteArea(ServerLevel level, Vec3 center, int radius) {
        if (!mayAlterTerrain(level)) return;
        BlockPos origin = BlockPos.containing(center);
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (x * x + z * z > radius * radius) continue;
            int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, origin.offset(x, 0, z)).getY();
            BlockPos fire = new BlockPos(origin.getX() + x, y, origin.getZ() + z);
            if (level.getBlockState(fire).isAir() && level.getBlockState(fire.below()).isSolidRender(level, fire.below()))
                level.setBlockAndUpdate(fire, Blocks.FIRE.defaultBlockState());
        }
    }

    private static void applyWither(ServerLevel level, Vec3 center, int radius, int ticks) {
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e.position().distanceToSqr(center) <= radius * radius)) {
            entity.addEffect(new MobEffectInstance(MobEffects.WITHER, ticks, 1, false, true));
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1, false, true));
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, true));
        }
    }

    private static void radialDamage(ServerLevel level, ServerPlayer owner, Vec3 center, int radius, double damage) {
        AABB box = new AABB(center, center).inflate(radius);
        DamageSource source = damageSource(level, owner);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
            e -> e.isAlive() && e.position().distanceToSqr(center) <= (double) radius * radius)) {
            double distance = entity.position().distanceTo(center);
            float amount = damageFor(entity, damage * (0.6 + 0.4 * (1.0 - distance / radius)));
            entity.hurt(source, amount);
            if (entity instanceof ServerPlayer player) GemActivationHandler.requestShake(player, 20, 0.65f);
        }
    }

    private static float damageFor(LivingEntity entity, double damage) {
        double amount = damage;
        if (entity instanceof ServerPlayer) amount *= HypercrownConfig.PLAYER_DAMAGE_FROM_GEMS.get();
        return (float) Math.min(100_000, Math.max(0, amount));
    }

    private static DamageSource damageSource(ServerLevel level, Entity owner) {
        return level.damageSources().indirectMagic(owner, owner);
    }

    private static List<LivingEntity> livingAlong(ServerLevel level, Vec3 start, Vec3 end, double radius) {
        AABB box = new AABB(start, end).inflate(radius + 1);
        Vec3 axis = end.subtract(start);
        double lengthSq = axis.lengthSqr();
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> {
            if (!e.isAlive()) return false;
            Vec3 delta = e.position().subtract(start);
            double projection = Mth.clamp(delta.dot(axis) / lengthSq, 0, 1);
            return e.position().distanceToSqr(start.add(axis.scale(projection))) <= radius * radius;
        });
    }

    private static boolean mayAlterTerrain(ServerLevel level) {
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    private static boolean eraseBlock(ServerLevel level, BlockPos pos, boolean drop) {
        if (!mayAlterTerrain(level) || !level.isInWorldBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0
                || state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) || state.is(Blocks.COMMAND_BLOCK)
                || state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.END_PORTAL_FRAME)) return false;
        if (drop) Block.dropResources(state, level, pos);
        return level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    private static void burst(ServerLevel level, Vec3 center, double speed, net.minecraft.core.particles.SimpleParticleType type, int count) {
        emitParticles(level, type, center.x, center.y, center.z, count, 2.5, 2.0, 2.5, speed);
    }

    private static void ring(ServerLevel level, Vec3 center, double radius, double height,
                             net.minecraft.core.particles.SimpleParticleType particle, int points) {
        String effectId = ACTIVE_VISUAL.get();
        DeveloperWorkspace.VisualLayer profile = effectId == null ? null
                : DeveloperToolService.visualLayer(level.getServer(), effectId, particle);
        if (profile != null) radius *= profile.spread;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            emitParticles(level, particle, center.x + Math.cos(angle) * radius, center.y + height,
                    center.z + Math.sin(angle) * radius, 1, 0.025, 0.025, 0.025, 0.015);
        }
    }

    private static void emitParticles(ServerLevel level, ParticleOptions particle, double x, double y, double z,
                                      int count, double dx, double dy, double dz, double speed) {
        String visualId = ACTIVE_VISUAL.get();
        DeveloperToolService.sendVisualParticles(level, visualId == null ? "" : visualId,
                particle, x, y, z, count, dx, dy, dz, speed);
    }

    public static void tick(ServerLevel level) {
        Iterator<Zone> zones = ZONES.iterator();
        while (zones.hasNext()) {
            Zone zone = zones.next();
            if (zone.level != level) continue;
            ACTIVE_VISUAL.set(switch (zone.kind) {
                case FROST -> "gem_frost_topaz";
                case VOID_RIFT -> "gem_shadow_amethyst";
                case TORNADO -> "gem_wind_sapphire";
                case TIME_STOP -> "gem_time_quartz";
            });
            try {
            zone.ticksLeft--;
            switch (zone.kind) {
                case FROST -> tickFrost(level, zone);
                case VOID_RIFT -> tickVoid(level, zone);
                case TORNADO -> tickTornado(level, zone);
                case TIME_STOP -> tickTimeStop(level, zone);
            }
            if (zone.ticksLeft <= 0) {
                if (zone.kind == ZoneKind.TIME_STOP) releaseFrozen(level, zone);
                if (zone.kind == ZoneKind.TORNADO) throwCaptured(level, zone);
                zones.remove();
            }
            } finally {
                ACTIVE_VISUAL.remove();
            }
        }
        Iterator<Trail> trails = TRAILS.iterator();
        while (trails.hasNext()) {
            Trail trail = trails.next();
            if (trail.level != level) continue;
            ACTIVE_VISUAL.set("gem_void_emerald");
            try {
            trail.ticksLeft--;
            if (trail.ticksLeft % 3 == 0) {
                for (int i = 0; i <= 50; i += 2) {
                    Vec3 point = trail.start.add(trail.end.subtract(trail.start).normalize().scale(i));
                    emitParticles(level, ParticleTypes.REVERSE_PORTAL, point.x, point.y, point.z, 2, 1.2, 1.2, 1.2, 0.03);
                    if ((i / 2) % 2 == 0) emitParticles(level, ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y, point.z, 1, 0.2, 0.2, 0.2, 0.01);
                }
            }
            if (trail.ticksLeft <= 0) trails.remove();
            } finally {
                ACTIVE_VISUAL.remove();
            }
        }
    }

    private static void tickFrost(ServerLevel level, Zone zone) {
        if (level.getGameTime() % 20 != 0) return;
        double radius = 30;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(zone.center, zone.center).inflate(radius),
                e -> e.position().distanceToSqr(zone.center) <= radius * radius)) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 5, true, true));
            entity.hurt(level.damageSources().freeze(), 2.0f);
            entity.setTicksFrozen(Math.min(entity.getTicksRequiredToFreeze(), entity.getTicksFrozen() + 20));
        }
        if (level.getGameTime() % 10 == 0) burst(level, zone.center, 0.01, ParticleTypes.SNOWFLAKE, 25);
    }

    private static void tickVoid(ServerLevel level, Zone zone) {
        if (level.getGameTime() % 10 == 0 && mayAlterTerrain(level)) {
            for (int i = 0; i < 10; i++) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2;
                double distance = Math.sqrt(level.getRandom().nextDouble()) * 20;
                int x = (int) Math.floor(zone.center.x + Math.cos(angle) * distance);
                int z = (int) Math.floor(zone.center.z + Math.sin(angle) * distance);
                BlockPos pos = new BlockPos(x, (int) zone.center.y + level.getRandom().nextInt(-2, 8), z);
                if (eraseBlock(level, pos, true)) emitParticles(level, ParticleTypes.PORTAL, x + 0.5, pos.getY() + 0.5, z + 0.5, 8, 0.3, 0.3, 0.3, 0.08);
            }
        }
        AABB box = new AABB(zone.center, zone.center).inflate(20);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box, e -> e.isAlive() && e.position().distanceToSqr(zone.center) < 400)) {
            Vec3 pull = zone.center.subtract(entity.position()).normalize();
            entity.push(pull.x * 0.12, pull.y * 0.12 - 0.04, pull.z * 0.12);
            entity.hurtMarked = true;
            if (entity instanceof LivingEntity living && level.getGameTime() % 20 == 0) living.hurt(level.damageSources().fellOutOfWorld(), 4.0f);
        }
        if (level.getGameTime() % 6 == 0) burst(level, zone.center, 0.02, ParticleTypes.REVERSE_PORTAL, 30);
    }

    private static void tickTornado(ServerLevel level, Zone zone) {
        if (level.getGameTime() % 5 == 0) {
            zone.direction = zone.direction.add((level.getRandom().nextDouble() - 0.5) * 0.08, 0, (level.getRandom().nextDouble() - 0.5) * 0.08).normalize();
            zone.center = zone.center.add(zone.direction.scale(0.12));
            int x = Mth.floor(zone.center.x + level.getRandom().nextInt(-6, 7));
            int z = Mth.floor(zone.center.z + level.getRandom().nextInt(-6, 7));
            int y = Mth.floor(zone.center.y + level.getRandom().nextInt(0, 25));
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0
                    && !state.is(Blocks.BEDROCK) && !state.is(Blocks.BARRIER) && mayAlterTerrain(level)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
                if (block != null) {
                    block.setDeltaMovement((level.getRandom().nextDouble() - 0.5) * 1.3, 0.35, (level.getRandom().nextDouble() - 0.5) * 1.3);
                    zone.captured.add(block.getUUID());
                }
            }
        }
        AABB box = new AABB(zone.center.x - 10, zone.center.y, zone.center.z - 10,
                zone.center.x + 10, zone.center.y + 40, zone.center.z + 10);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box, e -> e.isAlive())) {
            double dx = zone.center.x - entity.getX();
            double dz = zone.center.z - entity.getZ();
            double distance = Math.max(1, Math.sqrt(dx * dx + dz * dz));
            entity.push(dx / distance * 0.08 - dz / distance * 0.12, 0.18, dz / distance * 0.08 + dx / distance * 0.12);
            entity.hurtMarked = true;
            if (level.getGameTime() % 10 == 0) emitParticles(level, ParticleTypes.CLOUD, entity.getX(), entity.getY(), entity.getZ(), 2, 0.2, 0.2, 0.2, 0.03);
        }
        if (level.getGameTime() % 4 == 0) {
            for (int y = 0; y < 40; y += 4) burst(level, zone.center.add(0, y, 0), 0.01, ParticleTypes.CLOUD, 12);
        }
    }

    private static void throwCaptured(ServerLevel level, Zone zone) {
        for (UUID id : zone.captured) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2;
                entity.setDeltaMovement(Math.cos(angle) * 1.8, 1.2 + level.getRandom().nextDouble(), Math.sin(angle) * 1.8);
                entity.hurtMarked = true;
            }
        }
        level.playSound(null, BlockPos.containing(zone.center), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 2.5f, 0.7f);
    }

    private static void tickTimeStop(ServerLevel level, Zone zone) {
        AABB box = new AABB(zone.center, zone.center).inflate(50);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box,
                e -> e.isAlive() && !e.getUUID().equals(zone.owner) && e.position().distanceToSqr(zone.center) <= 2500)) {
            Frozen frozen = zone.frozen.computeIfAbsent(entity.getUUID(), id -> new Frozen(entity.getDeltaMovement(), entity.isNoGravity(),
                    entity instanceof Mob mob && mob.isNoAi(), entity.position()));
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setNoGravity(true);
            if (entity instanceof Mob mob) mob.setNoAi(true);
            if (entity instanceof ServerPlayer) entity.setPos(frozen.position.x, frozen.position.y, frozen.position.z);
            entity.hurtMarked = true;
        }
        if (level.getGameTime() % 8 == 0) burst(level, zone.center, 0, ParticleTypes.END_ROD, 24);
    }

    private static void releaseFrozen(ServerLevel level, Zone zone) {
        for (Map.Entry<UUID, Frozen> entry : zone.frozen.entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null) continue;
            Frozen frozen = entry.getValue();
            entity.setNoGravity(frozen.noGravity);
            entity.setDeltaMovement(frozen.velocity);
            if (entity instanceof Mob mob) mob.setNoAi(frozen.noAi);
            entity.hurtMarked = true;
        }
    }

    private enum ZoneKind { FROST, VOID_RIFT, TORNADO, TIME_STOP }

    private static final class Zone {
        private final ZoneKind kind;
        private final UUID owner;
        private Vec3 center;
        private int ticksLeft;
        private Vec3 direction = new Vec3(1, 0, 0);
        private final Map<UUID, Frozen> frozen = new HashMap<>();
        private final List<UUID> captured = new ArrayList<>();
        private final ServerLevel level;

        private Zone(ServerLevel level, ZoneKind kind, UUID owner, Vec3 center, int ticksLeft) {
            this.kind = kind;
            this.owner = owner;
            this.center = center;
            this.ticksLeft = ticksLeft;
            this.level = level;
        }
    }

    private record Frozen(Vec3 velocity, boolean noGravity, boolean noAi, Vec3 position) {}

    private static final class Trail {
        private final ServerLevel level;
        private final Vec3 start, end, right, up;
        private int ticksLeft;
        private Trail(ServerLevel level, Vec3 start, Vec3 end, Vec3 right, Vec3 up, int ticksLeft) {
            this.level = level; this.start = start; this.end = end; this.right = right; this.up = up; this.ticksLeft = ticksLeft;
        }
    }
}
