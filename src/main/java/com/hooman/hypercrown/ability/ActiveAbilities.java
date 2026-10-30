package com.hooman.hypercrown.ability;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.data.CrownPlayerData;
import com.hooman.hypercrown.dev.DeveloperToolService;
import com.hooman.hypercrown.dev.DeveloperWorkspace;
import com.hooman.hypercrown.network.ModNetwork;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side logic for keybind abilities. All validation happens here.
 */
public final class ActiveAbilities {
    private static final float THUNDER_BEAM_DAMAGE_MULTIPLIER = 1.5f;
    private static final double THUNDER_BEAM_WIDTH_MULTIPLIER = 1.5;
    private static final int THUNDER_BEAM_COOLDOWN_MULTIPLIER = 2;
    private static final DustParticleOptions CROWN_GOLD =
            new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.18f), 1.15f);
    private static final DustParticleOptions CROWN_VIOLET =
            new DustParticleOptions(new Vector3f(0.62f, 0.24f, 1.0f), 0.95f);

    private ActiveAbilities() {
    }

    public static void handle(ServerPlayer p, Ability ability, boolean sneaking, boolean released) {
        if (!ability.active || !CrownHelper.has(p, ability)) return;
        CrownPlayerData data = CrownHelper.data(p);
        long now = CrownHelper.serverTime(p);

        if (ability == Ability.ENERGY_BEAM) {
            handleBeam(p, data, now, released);
            return;
        }
        if (released) return;

        long left = data.remaining(ability, now);
        if (left > 0) {
            p.displayClientMessage(Component.translatable("message.hypercrown.cooldown",
                    Component.translatable(ability.langKey()), (left + 19) / 20), true);
            return;
        }
        boolean consumed = switch (ability) {
            case HYPERSTEP -> hyperstep(p);
            case HYPERBREAK -> hyperbreak(p, data, now);
            case CROWN_GUARD -> guard(p, data, now);
            case TACTICAL_AWARENESS -> awareness(p);
            case BLINK -> blink(p);
            case PHASE_DASH -> phaseDash(p);
            case ANCHOR -> anchor(p, data, sneaking);
            case TIME_SNARE -> timeSnare(p);
            case CHAIN_LIGHTNING -> chainLightning(p);
            case WEATHER_CONTROL -> weather(p);
            case CROWN_NOVA -> crownNova(p);
            default -> false;
        };
        if (consumed) {
            startSlotCooldown(data, ability, now, HypercrownConfig.cooldownTicks(ability));
            markUsed(p, data, ability);
            playActivationSound(p, ability);
            ModNetwork.syncTo(p);
        }
    }

    /**
     * Starts the ability's cooldown and locks its slot partner for at least as long, so the client-side
     * alternate selection cannot be bypassed to chain both abilities of one key slot.
     */
    public static void startSlotCooldown(CrownPlayerData data, Ability ability, long now, int ticks) {
        data.startCooldown(ability, now, ticks);
        Ability partner = ability.slotPartner();
        if (partner != null && data.remaining(partner, now) < ticks) data.startCooldown(partner, now, ticks);
    }

    private static void playActivationSound(ServerPlayer player, Ability ability) {
        float pitch = 0.9f + (ability.ordinal() % 5) * 0.12f;
        player.serverLevel().playSound(null, player.blockPosition(), switch (ability) {
            case BLINK, PHASE_DASH, HYPERSTEP -> SoundEvents.ENDERMAN_TELEPORT;
            case ANCHOR -> SoundEvents.RESPAWN_ANCHOR_CHARGE;
            case ENERGY_BEAM, CHAIN_LIGHTNING -> SoundEvents.BEACON_ACTIVATE;
            case WEATHER_CONTROL, CROWN_NOVA -> SoundEvents.EVOKER_CAST_SPELL;
            case HYPERBREAK -> SoundEvents.PLAYER_ATTACK_CRIT;
            case CROWN_GUARD -> SoundEvents.SHIELD_BLOCK;
            case TACTICAL_AWARENESS -> SoundEvents.AMETHYST_BLOCK_CHIME;
            default -> SoundEvents.EXPERIENCE_ORB_PICKUP;
        }, SoundSource.PLAYERS, 0.8f, pitch);
    }

    private static boolean hyperstep(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 impulse = new Vec3(look.x, Mth.clamp(look.y, -0.15, 0.25), look.z).normalize().scale(1.05);
        Vec3 current = p.getDeltaMovement();
        p.setDeltaMovement(current.x * 0.25 + impulse.x, Math.max(current.y, impulse.y), current.z * 0.25 + impulse.z);
        p.hasImpulse = true;
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 1.6f);
        return true;
    }

    private static boolean hyperbreak(ServerPlayer p, CrownPlayerData data, long now) {
        if (data.momentum < 3) {
            p.displayClientMessage(Component.translatable("message.hypercrown.momentum.required"), true);
            return false;
        }
        data.momentum = 0;
        data.hyperbreakUntil = now + 100;
        p.displayClientMessage(Component.translatable("message.hypercrown.hyperbreak.armed"), true);
        return true;
    }

    private static boolean guard(ServerPlayer p, CrownPlayerData data, long now) {
        data.guardUntil = now + 80;
        data.guardRemaining = 6.0f;
        p.serverLevel().sendParticles(ParticleTypes.WAX_ON, p.getX(), p.getY() + 1.0, p.getZ(), 14, 0.35, 0.55, 0.35, 0.02);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.1f);
        return true;
    }

    private static boolean awareness(ServerPlayer p) {
        LivingEntity nearest = p.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                        p.getBoundingBox().inflate(16), other -> other != p && other.isAlive() && !other.isInvisible()
                                && p.distanceToSqr(other) <= 16 * 16 && p.hasLineOfSight(other)).stream()
                .min(java.util.Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
        if (nearest == null) {
            p.displayClientMessage(Component.translatable("message.hypercrown.awareness.none"), true);
            return false;
        }
        Vec3 offset = nearest.position().subtract(p.position());
        double angle = Math.toDegrees(Math.atan2(offset.z, offset.x)) - p.getYRot() + 90;
        String[] directions = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
        int sector = Math.floorMod((int) Math.round(angle / 45.0), 8);
        p.displayClientMessage(Component.translatable("message.hypercrown.awareness.direction", directions[sector]), true);
        p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.4, p.getZ(), 12, 0.35, 0.35, 0.35, 0.02);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.5f);
        return true;
    }

    // ---------- Blink ----------
    private static boolean blink(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        HitResult hit = p.pick(HypercrownConfig.BLINK_RANGE.get(), 1.0F, false);
        Vec3 target = hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK
                ? Vec3.atBottomCenterOf(bhr.getBlockPos().relative(bhr.getDirection()))
                : hit.getLocation();
        for (int up = 0; up < 3; up++) {
            Vec3 t = target.add(0, up, 0);
            AABB box = p.getBoundingBox().move(t.subtract(p.position()));
            if (level.noCollision(p, box)) {
                crownBurst(level, p.position().add(0, p.getBbHeight() * 0.55, 0), 42);
                p.teleportTo(t.x, t.y, t.z);
                p.resetFallDistance();
                crownBurst(level, t.add(0, p.getBbHeight() * 0.55, 0), 48);
                level.playSound(null, t.x, t.y, t.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.4f);
                return true;
            }
        }
        p.displayClientMessage(Component.translatable("message.hypercrown.blink_blocked"), true);
        return false;
    }

    private static boolean phaseDash(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(8));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        Vec3 travel = (hit.getType() == HitResult.Type.BLOCK
                ? hit.getLocation().subtract(eye).subtract(look.scale(0.8))
                : look.scale(8));
        for (double distance = 1.0; distance >= 0.0; distance -= 0.2) {
            Vec3 target = p.position().add(travel.scale(distance));
            if (!level.noCollision(p, p.getBoundingBox().move(target.subtract(p.position())))) continue;
            crownBurst(level, p.position().add(0, p.getBbHeight() * 0.55, 0), 20);
            p.teleportTo(target.x, target.y, target.z);
            crownBurst(level, target.add(0, p.getBbHeight() * 0.55, 0), 24);
            level.playSound(null, target.x, target.y, target.z, SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.PLAYERS, 0.8f, 1.8f);
            return true;
        }
        p.displayClientMessage(Component.translatable("message.hypercrown.blink_blocked"), true);
        return false;
    }

    private static boolean timeSnare(ServerPlayer p) {
        List<LivingEntity> targets = p.serverLevel().getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(8), target -> target != p && target.isAlive()
                        && !target.isAlliedTo(p) && p.distanceToSqr(target) <= 64);
        if (targets.isEmpty()) {
            p.displayClientMessage(Component.translatable("message.hypercrown.snare.none"), true);
            return false;
        }
        for (LivingEntity target : targets) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1, false, true, true));
            p.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    12, 0.35, 0.5, 0.35, 0.02);
        }
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.PLAYERS, 1f, 0.8f);
        return true;
    }

    private static boolean chainLightning(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        List<LivingEntity> candidates = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(24), target -> target != p && target.isAlive()
                        && !target.isAlliedTo(p) && p.distanceToSqr(target) <= 24 * 24
                        && p.hasLineOfSight(target)));
        candidates.sort(java.util.Comparator.comparingDouble(p::distanceToSqr));
        List<LivingEntity> targets = new ArrayList<>();
        LivingEntity previous = p;
        while (targets.size() < 4) {
            LivingEntity from = previous;
            LivingEntity next = candidates.stream().filter(target -> !targets.contains(target)
                            && from.distanceToSqr(target) <= (from == p ? 24 * 24 : 8 * 8))
                    .min(java.util.Comparator.comparingDouble(from::distanceToSqr)).orElse(null);
            if (next == null) break;
            targets.add(next);
            previous = next;
        }
        if (targets.isEmpty()) {
            p.displayClientMessage(Component.translatable("message.hypercrown.lightning.none"), true);
            return false;
        }
        float damage = (float) Math.min(40, 12 * HypercrownConfig.POWER_SCALE.get());
        for (LivingEntity target : targets) {
            target.hurt(p.damageSources().indirectMagic(p, p), damage);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(target.getX(), target.getY(), target.getZ());
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(),
                    target.getY() + target.getBbHeight() * 0.5, target.getZ(), 20, 0.3, 0.5, 0.3, 0.12);
        }
        level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 0.8f, 1.5f);
        return true;
    }

    private static boolean crownNova(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(7), target -> target != p && target.isAlive()
                        && !target.isAlliedTo(p) && p.distanceToSqr(target) <= 49);
        float damage = (float) Math.min(30, 10 * HypercrownConfig.POWER_SCALE.get());
        for (LivingEntity target : targets) {
            target.hurt(p.damageSources().playerAttack(p), damage);
            Vec3 away = target.position().subtract(p.position()).normalize();
            target.push(away.x * 1.3, 0.45, away.z * 1.3);
            target.hurtMarked = true;
        }
        crownBurst(level, p.position().add(0, p.getBbHeight() * 0.55, 0), 52);
        level.explode(p, p.getX(), p.getY() + p.getBbHeight() * 0.55, p.getZ(),
                3.0f, Level.ExplosionInteraction.NONE);
        return true;
    }

    // ---------- Anchor (sneak = set, normal = recall) ----------
    private static boolean anchor(ServerPlayer p, CrownPlayerData d, boolean sneaking) {
        if (sneaking) {
            d.anchorDim = p.level().dimension();
            d.anchorPos = p.blockPosition();
            p.displayClientMessage(Component.translatable("message.hypercrown.anchor_set", d.anchorPos.toShortString()), true);
            ServerLevel level = p.serverLevel();
            crownBurst(level, Vec3.atBottomCenterOf(d.anchorPos).add(0, 0.7, 0), 30);
            level.playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1f, 1f);
            level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.6f);
            return false; // setting is free
        }
        if (d.anchorPos == null) {
            p.displayClientMessage(Component.translatable("message.hypercrown.no_anchor"), true);
            return false;
        }
        ServerLevel target = p.getServer().getLevel(d.anchorDim);
        if (target == null) {
            p.displayClientMessage(Component.translatable("message.hypercrown.anchor_missing_dim"), true);
            return false;
        }
        BlockPos a = d.anchorPos;
        ServerLevel origin = p.serverLevel();
        crownBurst(origin, p.position().add(0, p.getBbHeight() * 0.55, 0), 36);
        p.teleportTo(target, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        crownBurst(target, new Vec3(a.getX() + 0.5, a.getY() + 0.9, a.getZ() + 0.5), 48);
        target.playSound(null, a, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1f, 1.2f);
        return true;
    }

    // ---------- Weather: clear -> rain -> thunder -> clear (global, via overworld) ----------
    private static boolean weather(ServerPlayer p) {
        ServerLevel ow = p.getServer().overworld();
        String state;
        if (ow.isThundering()) {
            ow.setWeatherParameters(12000, 0, false, false);
            state = "clear";
        } else if (ow.isRaining()) {
            ow.setWeatherParameters(0, 12000, true, true);
            state = "thunder";
        } else {
            ow.setWeatherParameters(0, 12000, true, false);
            state = "rain";
        }
        ServerLevel fxLevel = p.serverLevel();
        Vec3 center = p.position().add(0, p.getBbHeight() * 0.55, 0);
        crownBurst(fxLevel, center, 54);
        fxLevel.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 2.4, p.getZ(), 24, 1.2, 0.5, 1.2, 0.02);
        if (state.equals("thunder")) {
            LightningBolt strike = EntityType.LIGHTNING_BOLT.create(fxLevel);
            if (strike != null) {
                strike.moveTo(p.getX() + 4, p.getY(), p.getZ() + 3);
                strike.setVisualOnly(true);
                fxLevel.addFreshEntity(strike);
            }
        }
        p.displayClientMessage(Component.translatable("message.hypercrown.weather",
                Component.translatable("message.hypercrown.weather." + state)), true);
        return true;
    }

    // ---------- Energy beam: press = start charge, release = fire (charge timed server-side) ----------
    private static void handleBeam(ServerPlayer p, CrownPlayerData d, long now, boolean released) {
        if (!released) {
            long left = d.remaining(Ability.ENERGY_BEAM, now);
            if (left > 0) {
                p.displayClientMessage(Component.translatable("message.hypercrown.cooldown",
                        Component.translatable(Ability.ENERGY_BEAM.langKey()), (left + 19) / 20), true);
                d.beamChargeStart = -1;
                return;
            }
            d.beamChargeStart = now;
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 1.5f);
            return;
        }
        if (d.beamChargeStart < 0) return;
        float charge = Mth.clamp((now - d.beamChargeStart) / 40f, 0.2f, 1f); // full charge in 2 s
        d.beamChargeStart = -1;
        boolean thunderDiamond = CrownHelper.isHypercrown(p)
                && "thunder_diamond".equals(CrownHelper.wornCrown(p)
                .get(ModRegistries.SOCKETED_CATASTROPHE_GEM.get()));
        fireBeam(p, charge, thunderDiamond);
        int cooldown = HypercrownConfig.cooldownTicks(Ability.ENERGY_BEAM);
        startSlotCooldown(d, Ability.ENERGY_BEAM, now,
                thunderDiamond ? cooldown * THUNDER_BEAM_COOLDOWN_MULTIPLIER : cooldown);
        markUsed(p, d, Ability.ENERGY_BEAM);
        playActivationSound(p, Ability.ENERGY_BEAM);
        ModNetwork.syncTo(p);
    }

    private static void fireBeam(ServerPlayer p, float charge, boolean thunderDiamond) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getViewVector(1f);
        Vec3 end = eye.add(dir.scale(HypercrownConfig.BEAM_RANGE.get()));
        BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (bh.getType() != HitResult.Type.MISS) end = bh.getLocation();

        float dmg = (float) (HypercrownConfig.scaled(HypercrownConfig.BEAM_DAMAGE) * charge
                * (thunderDiamond ? THUNDER_BEAM_DAMAGE_MULTIPLIER : 1.0f));
        double widthMultiplier = thunderDiamond ? THUNDER_BEAM_WIDTH_MULTIPLIER : 1.0;
        final Vec3 beamEnd = end;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(widthMultiplier),
                e -> e != p && e.isAlive() && !e.isAlliedTo(p))) {
            if (e.getBoundingBox().inflate(0.5 * widthMultiplier).clip(eye, beamEnd).isPresent()) { // piercing
                e.hurt(p.damageSources().indirectMagic(p, p), dmg);
                e.igniteForSeconds(5);
            }
        }
        double len = eye.distanceTo(end);
        Vec3 axis = dir.normalize();
        Vec3 reference = Math.abs(axis.y) > 0.94 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = axis.cross(reference).normalize();
        Vec3 up = right.cross(axis).normalize();
        DeveloperWorkspace.VisualLayer beamVisual = DeveloperToolService.visualLayer(level.getServer(), "crown_beam", "minecraft:dust");
        double visualSpread = beamVisual == null ? 1 : beamVisual.spread;
        double radius = (0.14 + charge * 0.62) * widthMultiplier * visualSpread;
        DustParticleOptions beamCore = new DustParticleOptions(new Vector3f(1.0f, 0.86f, 0.38f), 0.75f + charge * 1.8f);
        DustParticleOptions beamShell = new DustParticleOptions(new Vector3f(0.68f, 0.3f, 1.0f), 0.55f + charge * 1.35f);
        DustParticleOptions beamEdge = new DustParticleOptions(new Vector3f(1.0f, 0.64f, 0.14f), 0.45f + charge * 1.1f);
        for (double t = 0.5; t < len; t += 0.7) {
            Vec3 point = eye.add(axis.scale(t));
            sendBeamParticles(level, beamCore, point.x, point.y, point.z, 1, 0.025, 0.025, 0.025, 0);
            if (((int) (t * 10)) % 14 == 0)
                sendBeamParticles(level, ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.015, 0.015, 0.015, 0);
        }
        // Twin helical shells surround the axis, so the beam reads as a full 3D shaft from every side.
        for (double t = 0.8; t < len; t += 2.0) {
            Vec3 center = eye.add(axis.scale(t));
            double rotation = t * 0.82;
            for (int i = 0; i < 8; i++) {
                double angle = rotation + i * (Math.PI * 2 / 8);
                Vec3 shell = center.add(right.scale(Math.cos(angle) * radius))
                        .add(up.scale(Math.sin(angle) * radius));
                sendBeamParticles(level, beamShell, shell.x, shell.y, shell.z, 1, 0, 0, 0, 0);
            }
            for (int i = 0; i < 4; i++) {
                double angle = -rotation + Math.PI / 4 + i * (Math.PI * 2 / 4);
                Vec3 edge = center.add(right.scale(Math.cos(angle) * radius * 1.5))
                        .add(up.scale(Math.sin(angle) * radius * 1.5));
                sendBeamParticles(level, beamEdge, edge.x, edge.y, edge.z, 1, 0, 0, 0, 0);
            }
        }
        sendBeamParticles(level, ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        sendBeamParticles(level, ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        int impactCount = 24 + Math.round(charge * 72);
        double impactSpread = 0.32 + charge * 0.72;
        sendBeamParticles(level, ParticleTypes.END_ROD, end.x, end.y, end.z, impactCount,
                impactSpread, impactSpread, impactSpread, 0.12);
        sendBeamParticles(level, beamCore, end.x, end.y, end.z, impactCount,
                impactSpread, impactSpread, impactSpread, 0.08);
        sendBeamParticles(level, beamShell, end.x, end.y, end.z, impactCount / 2,
                impactSpread, impactSpread, impactSpread, 0.05);
        double impactRadius = radius * 1.8;
        for (int i = 0; i < 24; i++) {
            double angle = i * (Math.PI * 2 / 24);
            Vec3 flare = end.add(right.scale(Math.cos(angle) * impactRadius))
                    .add(up.scale(Math.sin(angle) * impactRadius));
            sendBeamParticles(level, i % 2 == 0 ? beamCore : beamShell, flare.x, flare.y, flare.z, 1, 0, 0, 0, 0);
        }
        level.explode(p, end.x, end.y, end.z, 1.5f + charge * 2.5f,
                thunderDiamond ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1f, 1.2f);
    }

    /**
     * Layered gold/violet shockwave used when a crown ability changes the world.
     */
    private static void sendBeamParticles(ServerLevel level, net.minecraft.core.particles.ParticleOptions particle,
                                          double x, double y, double z, int count,
                                          double dx, double dy, double dz, double speed) {
        DeveloperToolService.sendVisualParticles(level, "crown_beam", particle, x, y, z, count, dx, dy, dz, speed);
    }

    private static void crownBurst(ServerLevel level, Vec3 center, int strength) {
        DeveloperToolService.sendVisualParticles(level, "crown_abilities", ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        DeveloperToolService.sendVisualParticles(level, "crown_abilities", ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z,
                strength, 0.45, 0.7, 0.45, 0.08);
        DeveloperToolService.sendVisualParticles(level, "crown_abilities", ParticleTypes.TOTEM_OF_UNDYING, center.x, center.y, center.z,
                strength, 0.55, 0.75, 0.55, 0.12);
        DeveloperToolService.sendVisualParticles(level, "crown_abilities", ParticleTypes.END_ROD, center.x, center.y, center.z,
                strength / 2, 0.45, 0.8, 0.45, 0.08);
        DeveloperToolService.sendVisualParticles(level, "crown_abilities", CROWN_GOLD, center.x, center.y, center.z,
                strength, 0.55, 0.55, 0.55, 0.04);
        DeveloperToolService.sendVisualParticles(level, "crown_abilities", CROWN_VIOLET, center.x, center.y, center.z,
                strength / 2, 0.55, 0.65, 0.55, 0.03);

        for (int i = 0; i < 24; i++) {
            double angle = i * (Math.PI * 2 / 24);
            double radius = 0.72 + 0.16 * Math.sin(angle * 3);
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            DeveloperToolService.sendVisualParticles(level, "crown_abilities", CROWN_GOLD, x, center.y - 0.25, z, 1, 0, 0, 0, 0);
            if (i % 2 == 0)
                DeveloperToolService.sendVisualParticles(level, "crown_abilities", CROWN_VIOLET, x * 0.96 + center.x * 0.04,
                        center.y + 0.24, z * 0.96 + center.z * 0.04, 1, 0, 0, 0, 0);
        }
    }

    // ---------- Advancements ----------
    private static void markUsed(ServerPlayer p, CrownPlayerData d, Ability a) {
        award(p, "use_" + a.key());
        d.usedMask |= 1 << a.ordinal();
        int all = 0;
        for (Ability x : List.of(Ability.BLINK, Ability.ANCHOR, Ability.ENERGY_BEAM, Ability.WEATHER_CONTROL))
            if (CrownHelper.has(p, x)) all |= 1 << x.ordinal();
        if ((d.usedMask & all) == all) award(p, "master_of_the_crown");
    }

    public static void award(ServerPlayer p, String path) {
        AdvancementHolder h = p.getServer().getAdvancements().get(HypercrownMod.id(path));
        if (h != null) p.getAdvancements().award(h, "used");
    }
}
