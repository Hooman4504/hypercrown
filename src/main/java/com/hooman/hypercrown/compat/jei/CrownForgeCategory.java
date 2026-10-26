package com.hooman.hypercrown.compat.jei;

import com.hooman.hypercrown.forge.CrownWeaponKind;
import com.hooman.hypercrown.registry.ModRegistries;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class CrownForgeCategory extends AbstractRecipeCategory<CrownForgeCategory.ForgeRecipe> {
    public CrownForgeCategory(IGuiHelper guiHelper) {
        super(HypercrownJeiPlugin.CROWN_FORGE,
                Component.translatable("jei.hypercrown.crown_forge.title"),
                guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                        ModRegistries.CROWN_FORGED_WEAPONS.get(CrownWeaponKind.CELESTIAL_EDGE).get().getDefaultInstance()),
                196, 76);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ForgeRecipe recipe, IFocusGroup focuses) {
        CrownWeaponKind kind = recipe.weapon();
        builder.addInputSlot(2, 7).setStandardSlotBackground().addItemStack(kind.gemStack());
        builder.addInputSlot(24, 7).setStandardSlotBackground().addItemStack(new ItemStack(kind.baseWeapon()));
        builder.addInputSlot(46, 7).setStandardSlotBackground().addItemStack(new ItemStack(kind.catalyst()));
        builder.addOutputSlot(172, 7).setOutputSlotBackground().addItemStack(kind.outputStack());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, ForgeRecipe recipe, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(200).setPosition(94, 7);
    }

    @Override
    public void draw(ForgeRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, Component.translatable("jei.hypercrown.crown_forge.instructions"),
                98, 36, 0xFFF4D58A);
        graphics.drawWordWrap(font,
                Component.translatable("tooltip.hypercrown.forged_weapon." + recipe.weapon().id()),
                3, 48, 190, 0xFFE7E2F2);
    }

    public record ForgeRecipe(CrownWeaponKind weapon) {}
}
