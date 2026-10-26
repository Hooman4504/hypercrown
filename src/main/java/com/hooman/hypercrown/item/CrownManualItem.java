package com.hooman.hypercrown.item;

import com.hooman.hypercrown.client.CrownManualScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** In-game reference for the two crowns and every currently available power. */
public final class CrownManualItem extends Item {
    public static final List<String> PAGES = List.of(
            "manual.hypercrown.forging", "manual.hypercrown.assembly", "manual.hypercrown.awakening",
            "manual.hypercrown.controls",
            "manual.hypercrown.blink", "manual.hypercrown.phase_dash",
            "manual.hypercrown.anchor", "manual.hypercrown.time_snare",
            "manual.hypercrown.energy_beam", "manual.hypercrown.thunder_beam",
            "manual.hypercrown.chain_lightning",
            "manual.hypercrown.weather", "manual.hypercrown.nova",
            "manual.hypercrown.flight", "manual.hypercrown.speed",
            "manual.hypercrown.mastery", "manual.hypercrown.critical",
            "manual.hypercrown.knockback", "manual.hypercrown.reduction",
            "manual.hypercrown.elemental", "manual.hypercrown.status",
            "manual.hypercrown.survival", "manual.hypercrown.mining",
            "manual.hypercrown.smelt", "manual.hypercrown.reflection",
            "manual.hypercrown.instant_kill", "manual.hypercrown.pacification",
            "manual.hypercrown.iframes",
            "manual.tactics.hyperstep", "manual.tactics.momentum",
            "manual.tactics.hyperbreak", "manual.tactics.guard",
            "manual.tactics.awareness", "manual.tactics.gems", "manual.hypercrown.catastrophe_gems",
            "manual.hypercrown.catastrophe_gem_crafting", "manual.hypercrown.crown_forge");

    public CrownManualItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            CrownManualScreen.open();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("manual.hint").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("manual.contents").withStyle(ChatFormatting.GRAY));
    }
}
