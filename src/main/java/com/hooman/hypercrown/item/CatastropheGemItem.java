package com.hooman.hypercrown.item;

import com.hooman.hypercrown.gem.CatastropheGem;
import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.network.GemActivationPayload;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** A single, high-impact gem. All world effects are executed and validated by the server. */
public final class CatastropheGemItem extends Item {
    private final CatastropheGem gem;
    private final boolean enhanced;

    public CatastropheGemItem(Properties properties, CatastropheGem gem) {
        this(properties, gem, false);
    }

    public CatastropheGemItem(Properties properties, CatastropheGem gem, boolean enhanced) {
        super(itemProperties(properties, gem));
        this.gem = gem;
        this.enhanced = enhanced;
    }

    private static Properties itemProperties(Properties properties, CatastropheGem gem) {
        properties.stacksTo(1).fireResistant();
        if (!gem.consumed()) properties.durability(100);
        return properties;
    }

    public CatastropheGem gem() { return gem; }
    public boolean isEnhanced() { return enhanced; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            InteractionHand targetHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            boolean mount = player.isCrouching() && canSocketInto(player, player.getItemInHand(targetHand));
            if (mount) {
                PacketDistributor.sendToServer(new GemActivationPayload(gem.ordinal(), hand == InteractionHand.OFF_HAND, enhanced, true));
            } else if (gem.requiresConfirmation()) {
                com.hooman.hypercrown.client.GemConfirmationScreen.open(gem, hand == InteractionHand.OFF_HAND, enhanced);
            } else {
                PacketDistributor.sendToServer(new GemActivationPayload(gem.ordinal(), hand == InteractionHand.OFF_HAND, enhanced, false));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static boolean canSocketInto(Player player, ItemStack tool) {
        if (tool.is(ModRegistries.HYPERCROWN.get())) return true;
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
                HypercrownMod.id("gem_socket"));
        var enchantment = player.level().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).get(key).orElse(null);
        return !tool.isEmpty() && enchantment != null
                && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(enchantment, tool) > 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hypercrown.catastrophe_gem." + gem.id()).withStyle(ChatFormatting.GRAY));
        if (enhanced) tooltip.add(Component.translatable("tooltip.hypercrown.gem.enhanced").withStyle(ChatFormatting.LIGHT_PURPLE));
        int cooldown = enhanced ? Math.round(gem.cooldownSeconds() * 0.8f) : gem.cooldownSeconds();
        tooltip.add(Component.translatable("tooltip.hypercrown.gem.cooldown", cooldown).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.hypercrown.gem.socket_hint").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.hypercrown.gem.socket_power." + gem.id()).withStyle(ChatFormatting.DARK_PURPLE));
        if (gem.requiresConfirmation()) tooltip.add(Component.translatable("tooltip.hypercrown.gem.confirm").withStyle(ChatFormatting.GOLD));
        if (gem.consumed()) tooltip.add(Component.translatable("tooltip.hypercrown.gem.single_use").withStyle(ChatFormatting.RED));
        else tooltip.add(Component.translatable("tooltip.hypercrown.gem.repair").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hypercrown.gem.bound").withStyle(ChatFormatting.DARK_GRAY));
    }
}
