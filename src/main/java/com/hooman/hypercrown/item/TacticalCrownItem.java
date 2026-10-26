package com.hooman.hypercrown.item;

import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Separate, balanced crown with tactical abilities and optional passive gem sockets. */
public final class TacticalCrownItem extends ArmorItem {
    public TacticalCrownItem(Holder<ArmorMaterial> material, Properties properties) {
        super(material, Type.HELMET, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hypercrown.tactical").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.hypercrown.combat").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hypercrown.defense").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hypercrown.progression").withStyle(ChatFormatting.GRAY));
        int sockets = stack.getOrDefault(ModRegistries.SOCKETED_GEMS.get(), 0);
        tooltip.add(Component.translatable("tooltip.hypercrown.sockets", Integer.bitCount(sockets), 4)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
