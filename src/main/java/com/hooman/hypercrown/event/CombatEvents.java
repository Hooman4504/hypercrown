package com.hooman.hypercrown.event;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.ability.Ability;
import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.config.HypercrownConfig;
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

        // Defense
        if (!(target instanceof Player p) || !CrownHelper.isCrownActive(p)) return;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // /kill and void still work

        if (HypercrownConfig.enabled(Ability.ELEMENTAL_IMMUNITY)
                && (src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypeTags.IS_EXPLOSION) || src.is(DamageTypeTags.IS_FALL))) {
            event.setCanceled(true);
            return;
        }
        if (HypercrownConfig.enabled(Ability.INVINCIBILITY_FRAMES)
                && p.level().getGameTime() - CrownHelper.data(p).lastHurtTick < HypercrownConfig.IFRAME_TICKS.get()) {
            event.setCanceled(true);
            return;
        }
        // Reflect a share of the *raw* damage (thorns damage is never re-reflected, which prevents loops).
        if (HypercrownConfig.enabled(Ability.DAMAGE_REFLECTION) && src.getEntity() instanceof LivingEntity attacker
                && attacker != p && !src.is(DamageTypes.THORNS)) {
            attacker.hurt(p.damageSources().thorns(p),
                    event.getAmount() * (float) HypercrownConfig.scaled(HypercrownConfig.REFLECT_FRACTION));
        }
        if (HypercrownConfig.enabled(Ability.DAMAGE_REDUCTION)) {
            float reduction = (float) Mth.clamp(HypercrownConfig.scaled(HypercrownConfig.DAMAGE_REDUCTION), 0.0, 1.0);
            event.setAmount(event.getAmount() * (1f - reduction));
        }
    }

    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof Player p && CrownHelper.has(p, Ability.INVINCIBILITY_FRAMES))
            CrownHelper.data(p).lastHurtTick = p.level().getGameTime();
    }
}
