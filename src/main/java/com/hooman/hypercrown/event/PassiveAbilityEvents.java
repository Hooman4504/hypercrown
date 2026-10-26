package com.hooman.hypercrown.event;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.ability.Ability;
import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.data.CrownPlayerData;
import com.hooman.hypercrown.network.ModNetwork;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.function.DoubleSupplier;

import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.*;

/** Always-on abilities: attributes, survival, mining, status immunity, pacification. */
@EventBusSubscriber(modid = HypercrownMod.MODID)
public final class PassiveAbilityEvents {

    private record AttrBonus(Ability ability, Holder<Attribute> attribute, ResourceLocation id,
                             AttributeModifier.Operation op, DoubleSupplier amount) {}

    // Transient attribute modifiers: removed automatically when the crown comes off.
    private static final List<AttrBonus> BONUSES = List.of(
            new AttrBonus(Ability.FLIGHT, NeoForgeMod.CREATIVE_FLIGHT, HypercrownMod.id("flight"), ADD_VALUE, () -> 1.0),
            new AttrBonus(Ability.SPEED, Attributes.MOVEMENT_SPEED, HypercrownMod.id("speed"), ADD_MULTIPLIED_BASE,
                    () -> HypercrownConfig.scaled(HypercrownConfig.SPEED_BONUS)),
            new AttrBonus(Ability.WEAPON_MASTERY, Attributes.ATTACK_DAMAGE, HypercrownMod.id("weapon_mastery"), ADD_MULTIPLIED_TOTAL,
                    () -> HypercrownConfig.scaled(HypercrownConfig.DAMAGE_BONUS)),
            new AttrBonus(Ability.KNOCKBACK_IMMUNITY, Attributes.KNOCKBACK_RESISTANCE, HypercrownMod.id("knockback"), ADD_VALUE, () -> 1.0),
            new AttrBonus(Ability.KNOCKBACK_IMMUNITY, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, HypercrownMod.id("explosion_knockback"), ADD_VALUE, () -> 1.0));

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        boolean active = CrownHelper.isCrownActive(p);

        // Both sides, so client-side movement prediction matches the server.
        for (AttrBonus b : BONUSES) {
            AttributeInstance inst = p.getAttribute(b.attribute());
            if (inst == null) continue;
            if (CrownHelper.has(p, b.ability())) {
                double amount = CrownHelper.isTacticalCrown(p)
                        ? switch (b.ability()) {
                            case SPEED -> HypercrownConfig.balancedGemStrength(HypercrownConfig.SPEED_BONUS, 0.05);
                            case WEAPON_MASTERY -> HypercrownConfig.balancedGemStrength(HypercrownConfig.DAMAGE_BONUS, 0.08);
                            case KNOCKBACK_IMMUNITY -> 0.25;
                            default -> b.amount().getAsDouble();
                        }
                        : b.amount().getAsDouble();
                AttributeModifier existing = inst.getModifier(b.id());
                if (existing == null || existing.amount() != amount) // avoid re-syncing every tick
                    inst.addOrUpdateTransientModifier(new AttributeModifier(b.id(), amount, b.op()));
            } else if (inst.hasModifier(b.id())) {
                inst.removeModifier(b.id());
            }
        }

        if (p.level().isClientSide || !active) return;
        ServerPlayer sp = (ServerPlayer) p;

        if (CrownHelper.has(p, Ability.SURVIVAL) && p.tickCount % 20 == 0) {
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(20f);
            if (p.getHealth() < p.getMaxHealth()) p.heal(2.0f * HypercrownConfig.POWER_SCALE.get().floatValue()); // 1 heart/s
            p.setAirSupply(p.getMaxAirSupply());
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
        }
        if (CrownHelper.has(p, Ability.STATUS_IMMUNITY) && p.tickCount % 10 == 0) {
            p.getActiveEffects().stream().map(MobEffectInstance::getEffect)
                    .filter(h -> h.value().getCategory() == MobEffectCategory.HARMFUL)
                    .toList().forEach(p::removeEffect);
        }
        if (CrownHelper.has(p, Ability.ELEMENTAL_IMMUNITY) && p.isOnFire()) p.clearFire();
        if (CrownHelper.isCrownActive(p) && p.tickCount % 20 == 0) {
            CrownPlayerData data = CrownHelper.data(p);
            if (CrownHelper.isTacticalCrown(p) && data.momentum > 0
                    && CrownHelper.serverTime(p) - data.lastCombatTick >= 100) data.momentum--;
        }
        if (p.tickCount % 10 == 0) ModNetwork.syncTo(sp); // HUD cooldowns
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof Player p && CrownHelper.has(p, Ability.STATUS_IMMUNITY)
                && event.getEffectInstance().getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    // ---------- Instant mining (no tool restrictions; bedrock stays unbreakable) ----------
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (CrownHelper.has(event.getEntity(), Ability.INSTANT_MINING)) event.setNewSpeed(100_000f);
    }

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (CrownHelper.has(event.getEntity(), Ability.INSTANT_MINING)) event.setCanHarvest(true);
    }

    // ---------- Auto-smelt (only ores / raw materials, so sand doesn't become glass) ----------
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player p) || !CrownHelper.has(p, Ability.AUTO_SMELT)) return;
        ServerLevel level = event.getLevel();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (!stack.is(Tags.Items.ORES) && !stack.is(Tags.Items.RAW_MATERIALS)) continue;
            level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level).ifPresent(r -> {
                ItemStack out = r.value().getResultItem(level.registryAccess());
                drop.setItem(out.copyWithCount(out.getCount() * stack.getCount()));
            });
        }
    }

    // ---------- Mob pacification: hostiles ignore you unless you hit them ----------
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof Player p && event.getEntity() instanceof Enemy
                && CrownHelper.has(p, Ability.MOB_PACIFICATION) && event.getEntity().getLastHurtByMob() != p) {
            event.setCanceled(true);
        }
    }
}
