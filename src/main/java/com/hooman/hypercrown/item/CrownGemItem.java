package com.hooman.hypercrown.item;

import com.hooman.hypercrown.ability.Ability;
import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Consumed to unlock one modest passive on a worn Tactical Crown. */
public final class CrownGemItem extends Item {
    private final Ability ability;

    public CrownGemItem(Properties properties, Ability ability) {
        super(properties);
        this.ability = ability;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hypercrown.gem").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable(ability.langKey()).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gem = player.getItemInHand(hand);
        ItemStack crown = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!crown.is(ModRegistries.TACTICAL_CROWN.get()) || (!level.isClientSide
                && !CrownHelper.has(player, Ability.HYPERSTEP))) {
            Component message = Component.translatable("message.hypercrown.gem.wear_crown");
            if (player instanceof ServerPlayer serverPlayer) serverPlayer.displayClientMessage(message, true);
            else if (level.isClientSide) player.displayClientMessage(message, true);
            return InteractionResultHolder.fail(gem);
        }
        int bit = 1 << ability.ordinal();
        int sockets = crown.getOrDefault(ModRegistries.SOCKETED_GEMS.get(), 0);
        if ((sockets & bit) != 0) {
            Component message = Component.translatable("message.hypercrown.gem.already_socketed");
            if (player instanceof ServerPlayer serverPlayer) serverPlayer.displayClientMessage(message, true);
            else if (level.isClientSide) player.displayClientMessage(message, true);
            return InteractionResultHolder.fail(gem);
        }
        if (!level.isClientSide) {
            crown.set(ModRegistries.SOCKETED_GEMS.get(), sockets | bit);
            if (!player.getAbilities().instabuild) gem.shrink(1);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(Component.translatable("message.hypercrown.gem.socketed",
                        Component.translatable(ability.langKey())), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(gem, level.isClientSide);
    }
}
