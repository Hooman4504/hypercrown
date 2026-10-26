package com.hooman.hypercrown.event;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.ability.Ability;
import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.data.CrownPlayerData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;

/** Offense and defense abilities. */
@EventBusSubscriber(modid = HypercrownMod.MODID)
public final class CombatEvents {

    @SubscribeEvent
    public static void onCritical(CriticalHitEvent event) {
        if (CrownHelper.has(event.getEntity(), Ability.CRITICAL_MASTERY)) event.setCriticalHit(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource src = event.getSource();
        LivingEntity target = event.getEntity();

        // Offense: 1% instant kill on non-boss, non-player mobs (keeps player kill credit and loot).
        if (src.getEntity() instanceof Player attacker && src.getDirectEntity() == attacker
                && !(target instanceof Player) && !target.getType().is(Tags.EntityTypes.BOSSES)
                && CrownHelper.has(attacker, Ability.INSTANT_KILL)
                && attacker.getRandom().nextDouble() < HypercrownConfig.scaled(HypercrownConfig.INSTANT_KILL_CHANCE)) {
            event.setAmount(Float.MAX_VALUE / 4);
            if (target.level() instanceof ServerLevel sl)
                sl.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1, target.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
        }

        long now = src.getEntity() instanceof Player sourcePlayer ? CrownHelper.serverTime(sourcePlayer) : 0;
        if (src.getEntity() instanceof Player attacker && target != attacker && !src.is(DamageTypeTags.IS_EXPLOSION)
                && CrownHelper.has(attacker, Ability.HYPERBREAK)) {
            CrownPlayerData attackData = CrownHelper.data(attacker);
            if (attackData.hyperbreakUntil > now) {
                float bonus = Math.min(3.0f, event.getAmount() * 0.25f);
                event.setAmount(event.getAmount() + bonus);
                attackData.hyperbreakUntil = 0;
                attacker.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.hypercrown.hyperbreak.spent"), true);
            }
        }

        // Defense
        if (!(target instanceof Player p) || !CrownHelper.isCrownActive(p)) return;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // /kill and void still work

        if (CrownHelper.has(p, Ability.ELEMENTAL_IMMUNITY)
                && (src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypeTags.IS_EXPLOSION) || src.is(DamageTypeTags.IS_FALL))) {
            event.setCanceled(true);
            return;
        }
        if (CrownHelper.has(p, Ability.INVINCIBILITY_FRAMES)
                && CrownHelper.serverTime(p) - CrownHelper.data(p).lastHurtTick < HypercrownConfig.IFRAME_TICKS.get()) {
            event.setCanceled(true);
            return;
        }
        // Reflect a share of the *raw* damage (thorns damage is never re-reflected, which prevents loops).
        if (CrownHelper.has(p, Ability.DAMAGE_REFLECTION) && src.getEntity() instanceof LivingEntity attacker
                && attacker != p && !src.is(DamageTypes.THORNS)) {
            attacker.hurt(p.damageSources().thorns(p),
                    event.getAmount() * (float) HypercrownConfig.scaled(HypercrownConfig.REFLECT_FRACTION));
        }
        if (CrownHelper.has(p, Ability.DAMAGE_REDUCTION)) {
            float reduction = (float) (CrownHelper.isTacticalCrown(p)
                    ? HypercrownConfig.balancedGemStrength(HypercrownConfig.DAMAGE_REDUCTION, 0.10)
                    : Mth.clamp(HypercrownConfig.scaled(HypercrownConfig.DAMAGE_REDUCTION), 0.0, 1.0));
            event.setAmount(event.getAmount() * (1f - reduction));
        }
        now = CrownHelper.serverTime(p);
        CrownPlayerData defense = CrownHelper.data(p);
        if (defense.guardUntil > now && defense.guardRemaining > 0) {
            float absorbed = Math.min(event.getAmount(), defense.guardRemaining);
            defense.guardRemaining -= absorbed;
            event.setAmount(event.getAmount() - absorbed);
        }
    }

    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof Player p && CrownHelper.has(p, Ability.INVINCIBILITY_FRAMES))
            CrownHelper.data(p).lastHurtTick = CrownHelper.serverTime(p);
        if (!(event.getSource().getEntity() instanceof Player attacker) || event.getEntity() == attacker
                || event.getSource().is(DamageTypeTags.IS_EXPLOSION)
                || event.getNewDamage() <= 0 || !CrownHelper.has(attacker, Ability.HYPERSTEP)) return;
        CrownPlayerData data = CrownHelper.data(attacker);
        long now = CrownHelper.serverTime(attacker);
        data.lastCombatTick = now;
        if (now - data.lastMomentumGainTick >= 40 && data.momentum < 3) {
            data.momentum++;
            data.lastMomentumGainTick = now;
            attacker.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.hypercrown.momentum", data.momentum), true);
        }
    }
}
