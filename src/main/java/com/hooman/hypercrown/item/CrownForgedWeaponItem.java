package com.hooman.hypercrown.item;

import com.hooman.hypercrown.forge.CrownWeaponKind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** High-damage, gem-powered weapons created in the Crown Forge. */
public final class CrownForgedWeaponItem extends SwordItem {
    private final CrownWeaponKind kind;

    public CrownForgedWeaponItem(CrownWeaponKind kind, Properties properties) {
        super(Tiers.NETHERITE, properties.attributes(SwordItem.createAttributes(
                Tiers.NETHERITE, kind.bonusAttackDamage(), kind.attackSpeed())));
        this.kind = kind;
    }

    public CrownWeaponKind kind() {
        return kind;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hypercrown.forged_weapon." + kind.id())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.hypercrown.forged_weapon.proc")
                .withStyle(ChatFormatting.GRAY));
    }
}
