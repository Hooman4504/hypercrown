package com.hooman.hypercrown.item;

import com.hooman.hypercrown.ability.Ability;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The Hypercrown. Abilities are not here; they live in the event handlers and check CrownHelper. */
public class HypercrownItem extends ArmorItem {

    public HypercrownItem(Holder<ArmorMaterial> material, Properties props) {
        super(material, Type.HELMET, props);
    }

    @Override public boolean isFoil(ItemStack stack) { return true; }
    @Override public boolean isEnchantable(ItemStack stack) { return false; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hypercrown.lore1").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.hypercrown.lore2").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.hypercrown.abilities", Ability.values().length).withStyle(ChatFormatting.LIGHT_PURPLE));
        CrownOwner owner = stack.get(ModRegistries.OWNER.get());
        tooltip.add(owner != null
                ? Component.translatable("tooltip.hypercrown.bound", owner.name()).withStyle(ChatFormatting.YELLOW)
                : Component.translatable("tooltip.hypercrown.unforged").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** A dropped crown never despawns, cannot be destroyed, and refuses to fall into the void. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level().isClientSide) return false;
        entity.setUnlimitedLifetime();
        if (!entity.isInvulnerable()) entity.setInvulnerable(true); // blocks cactus, explosions, etc.

        int minY = entity.level().getMinBuildHeight();
        if (entity.getY() < minY + 1) {
            Entity thrower = entity.getOwner();
            if (thrower != null && thrower.level() == entity.level()) {
                entity.teleportTo(thrower.getX(), thrower.getY() + 1, thrower.getZ());
            } else {
                entity.setPos(entity.getX(), minY + 1, entity.getZ());
                entity.setNoGravity(true); // hover above the void until retrieved
            }
            entity.setDeltaMovement(Vec3.ZERO);
        }
        return false;
    }
}
