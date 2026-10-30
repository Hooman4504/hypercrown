package com.hooman.hypercrown.item;

import com.hooman.hypercrown.forge.CrownWeaponKind;
import com.hooman.hypercrown.gem.GemProperties;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Objects;

/** High-damage, gem-powered weapons created in the Crown Forge. */
public final class CrownForgedWeaponItem extends SwordItem {
    private final CrownWeaponKind kind;
    private final String itemName;
    private final CrownWeaponKind.WeaponType weaponType;
    private final String imprint;
    private final double aethericCharge;
    private final double resonanceLevel;
    private final double temporalStability;

    public CrownForgedWeaponItem(CrownWeaponKind kind, Properties properties) {
        super(Tiers.NETHERITE, properties.attributes(SwordItem.createAttributes(
                Tiers.NETHERITE, kind.bonusAttackDamage(), kind.attackSpeed())));
        this.kind = kind;
        this.itemName = kind.kindName();
        this.weaponType = kind.weaponType();
        this.imprint = kind.gemId();
        GemProperties.GemData gemData = Objects.requireNonNull(
                GemProperties.getGemData(imprint), "No gem properties registered for " + imprint);
        this.aethericCharge = gemData.aethericCharge * 1.5;
        this.resonanceLevel = gemData.resonanceLevel * (100.0 / gemData.temporalStability);
        this.temporalStability = gemData.temporalStability;
    }

    public CrownWeaponKind kind() {
        return kind;
    }

    public String itemName() { return itemName; }
    public CrownWeaponKind.WeaponType weaponType() { return weaponType; }
    public String imprint() { return imprint; }
    public double aethericCharge() { return aethericCharge; }
    public double resonanceLevel() { return resonanceLevel; }
    public double temporalStability() { return temporalStability; }

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
