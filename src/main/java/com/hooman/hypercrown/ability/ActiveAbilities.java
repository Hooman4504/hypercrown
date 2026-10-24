package com.hooman.hypercrown.ability;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.data.CrownPlayerData;
import com.hooman.hypercrown.network.ModNetwork;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;

/** Server-side logic for keybind abilities. All validation happens here. */
public final class ActiveAbilities {
    private static final DustParticleOptions CROWN_GOLD =
            new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.18f), 1.15f);
    private static final DustParticleOptions CROWN_VIOLET =
            new DustParticleOptions(new Vector3f(0.62f, 0.24f, 1.0f), 0.95f);

    private ActiveAbilities() {}

    public static void handle(ServerPlayer p, Ability ability, boolean sneaking, boolean released) {
        if (!ability.active || !CrownHelper.has(p, ability)) return;
        CrownPlayerData data = CrownHelper.data(p);
        long now = p.level().getGameTime();

        if (ability == Ability.ENERGY_BEAM) { handleBeam(p, data, now, released); return; }
        if (released) return;

        long left = data.remaining(ability, now);
        if (left > 0) {
            p.displayClientMessage(Component.translatable("message.hypercrown.cooldown",
                    Component.translatable(ability.langKey()), (left + 19) / 20), true);
            return;
        }
        boolean consumed = switch (ability) {
            case BLINK -> blink(p);
            case ANCHOR -> anchor(p, data, sneaking);
            case WEATHER_CONTROL -> weather(p);
            default -> false;
        };
        if (consumed) {
            data.startCooldown(ability, now, HypercrownConfig.cooldownTicks(ability));
            markUsed(p, data, ability);
            ModNetwork.syncTo(p);
        }
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

    // ---------- Anchor (sneak = set, normal = recall) ----------
    private static boolean anchor(ServerPlayer p, CrownPlayerData d, boolean sneaking) {
        if (sneaking) {
            d.anchorDim = p.level().dimension();
            d.anchorPos = p.blockPosition();
            p.displayClientMessage(Component.translatable("message.hypercrown.anchor_set", d.anchorPos.toShortString()), true);
            ServerLevel level = p.serverLevel();
            crownBurst(level, Vec3.atBottomCenterOf(d.anchorPos).add(0, 0.7, 0), 30);
            level.playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1f, 1f);
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
        if (ow.isThundering()) { ow.setWeatherParameters(12000, 0, false, false); state = "clear"; }
        else if (ow.isRaining()) { ow.setWeatherParameters(0, 12000, true, true); state = "thunder"; }
        else { ow.setWeatherParameters(0, 12000, true, false); state = "rain"; }
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
        fireBeam(p, charge);
        d.startCooldown(Ability.ENERGY_BEAM, now, HypercrownConfig.cooldownTicks(Ability.ENERGY_BEAM));
        markUsed(p, d, Ability.ENERGY_BEAM);
        ModNetwork.syncTo(p);
    }

    private static void fireBeam(ServerPlayer p, float charge) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getViewVector(1f);
        Vec3 end = eye.add(dir.scale(HypercrownConfig.BEAM_RANGE.get()));
        BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (bh.getType() != HitResult.Type.MISS) end = bh.getLocation();

        float dmg = (float) (HypercrownConfig.scaled(HypercrownConfig.BEAM_DAMAGE) * charge);
        final Vec3 beamEnd = end;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.0),
                e -> e != p && e.isAlive() && !e.isAlliedTo(p))) {
            if (e.getBoundingBox().inflate(0.5).clip(eye, beamEnd).isPresent()) { // piercing
                e.hurt(p.damageSources().indirectMagic(p, p), dmg);
                e.igniteForSeconds(5);
            }
        }
        double len = eye.distanceTo(end);
        Vec3 axis = dir.normalize();
        Vec3 reference = Math.abs(axis.y) > 0.94 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = axis.cross(reference).normalize();
        Vec3 up = right.cross(axis).normalize();
        double radius = 0.14 + charge * 0.62;
        DustParticleOptions beamCore = new DustParticleOptions(new Vector3f(1.0f, 0.86f, 0.38f), 0.75f + charge * 1.8f);
        DustParticleOptions beamShell = new DustParticleOptions(new Vector3f(0.68f, 0.3f, 1.0f), 0.55f + charge * 1.35f);
        DustParticleOptions beamEdge = new DustParticleOptions(new Vector3f(1.0f, 0.64f, 0.14f), 0.45f + charge * 1.1f);
        for (double t = 0.5; t < len; t += 0.7) {
            Vec3 point = eye.add(axis.scale(t));
            level.sendParticles(beamCore, point.x, point.y, point.z, 1, 0.025, 0.025, 0.025, 0);
            if (((int) (t * 10)) % 14 == 0)
                level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.015, 0.015, 0.015, 0);
        }
        // Twin helical shells surround the axis, so the beam reads as a full 3D shaft from every side.
        for (double t = 0.8; t < len; t += 2.0) {
            Vec3 center = eye.add(axis.scale(t));
            double rotation = t * 0.82;
            for (int i = 0; i < 8; i++) {
                double angle = rotation + i * (Math.PI * 2 / 8);
                Vec3 shell = center.add(right.scale(Math.cos(angle) * radius))
                        .add(up.scale(Math.sin(angle) * radius));
                level.sendParticles(beamShell, shell.x, shell.y, shell.z, 1, 0, 0, 0, 0);
            }
            for (int i = 0; i < 4; i++) {
                double angle = -rotation + Math.PI / 4 + i * (Math.PI * 2 / 4);
                Vec3 edge = center.add(right.scale(Math.cos(angle) * radius * 1.5))
                        .add(up.scale(Math.sin(angle) * radius * 1.5));
                level.sendParticles(beamEdge, edge.x, edge.y, edge.z, 1, 0, 0, 0, 0);
            }
        }
        level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        int impactCount = 24 + Math.round(charge * 72);
        double impactSpread = 0.32 + charge * 0.72;
        level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, impactCount,
                impactSpread, impactSpread, impactSpread, 0.12);
        level.sendParticles(beamCore, end.x, end.y, end.z, impactCount,
                impactSpread, impactSpread, impactSpread, 0.08);
        level.sendParticles(beamShell, end.x, end.y, end.z, impactCount / 2,
                impactSpread, impactSpread, impactSpread, 0.05);
        double impactRadius = radius * 1.8;
        for (int i = 0; i < 24; i++) {
            double angle = i * (Math.PI * 2 / 24);
            Vec3 flare = end.add(right.scale(Math.cos(angle) * impactRadius))
                    .add(up.scale(Math.sin(angle) * impactRadius));
            level.sendParticles(i % 2 == 0 ? beamCore : beamShell, flare.x, flare.y, flare.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1f, 1.2f);
    }

    /** Layered gold/violet shockwave used when a crown ability changes the world. */
    private static void crownBurst(ServerLevel level, Vec3 center, int strength) {
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z,
                strength, 0.45, 0.7, 0.45, 0.08);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, center.x, center.y, center.z,
                strength, 0.55, 0.75, 0.55, 0.12);
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z,
                strength / 2, 0.45, 0.8, 0.45, 0.08);
        level.sendParticles(CROWN_GOLD, center.x, center.y, center.z,
                strength, 0.55, 0.55, 0.55, 0.04);
        level.sendParticles(CROWN_VIOLET, center.x, center.y, center.z,
                strength / 2, 0.55, 0.65, 0.55, 0.03);

        for (int i = 0; i < 24; i++) {
            double angle = i * (Math.PI * 2 / 24);
            double radius = 0.72 + 0.16 * Math.sin(angle * 3);
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            level.sendParticles(CROWN_GOLD, x, center.y - 0.25, z, 1, 0, 0, 0, 0);
            if (i % 2 == 0)
                level.sendParticles(CROWN_VIOLET, x * 0.96 + center.x * 0.04,
                        center.y + 0.24, z * 0.96 + center.z * 0.04, 1, 0, 0, 0, 0);
        }
    }

    // ---------- Advancements ----------
    private static void markUsed(ServerPlayer p, CrownPlayerData d, Ability a) {
        award(p, "use_" + a.key());
        d.usedMask |= 1 << a.ordinal();
        int all = 0;
        for (Ability x : Ability.ACTIVES) all |= 1 << x.ordinal();
        if ((d.usedMask & all) == all) award(p, "master_of_the_crown");
    }

    public static void award(ServerPlayer p, String path) {
        AdvancementHolder h = p.getServer().getAdvancements().get(HypercrownMod.id(path));
        if (h != null) p.getAdvancements().award(h, "used");
    }
}
