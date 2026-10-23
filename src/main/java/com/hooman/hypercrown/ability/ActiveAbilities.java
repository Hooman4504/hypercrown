package com.hooman.hypercrown.ability;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.data.CrownPlayerData;
import com.hooman.hypercrown.network.ModNetwork;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Server-side logic for keybind abilities. All validation happens here. */
public final class ActiveAbilities {
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
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.3, 0.6, 0.3, 0.05);
                p.teleportTo(t.x, t.y, t.z);
                p.resetFallDistance();
                level.sendParticles(ParticleTypes.END_ROD, t.x, t.y + 1, t.z, 30, 0.3, 0.6, 0.3, 0.05);
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
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1f, 1f);
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
        p.teleportTo(target, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.resetFallDistance();
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
        for (double t = 1.0; t < len; t += 0.4) {
            Vec3 pt = eye.add(dir.scale(t)).add(0, -0.2, 0);
            level.sendParticles(ParticleTypes.END_ROD, pt.x, pt.y, pt.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1f, 1.2f);
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
