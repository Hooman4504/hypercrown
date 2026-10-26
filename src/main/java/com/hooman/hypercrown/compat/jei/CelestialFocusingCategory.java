package com.hooman.hypercrown.compat.jei;

import com.hooman.hypercrown.gem.CatastropheGem;
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
import net.minecraft.world.item.Items;

public final class CelestialFocusingCategory extends AbstractRecipeCategory<CelestialFocusingCategory.FocusRecipe> {
    public CelestialFocusingCategory(IGuiHelper guiHelper) {
        super(HypercrownJeiPlugin.CELESTIAL_FOCUSING,
                Component.translatable("jei.hypercrown.celestial_focusing.title"),
                guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                        ModRegistries.CELESTIAL_FOCUSING_TABLE.get().getDefaultInstance()), 196, 78);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FocusRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(2, 7).setStandardSlotBackground().addItemStack(recipe.gem().stack());
        builder.addInputSlot(24, 7).setStandardSlotBackground().addItemStack(recipe.gem().stack());
        builder.addInputSlot(46, 7).setStandardSlotBackground().addItemStack(new ItemStack(Items.GOLD_INGOT, 4));
        builder.addInputSlot(68, 7).setStandardSlotBackground().addItemStack(new ItemStack(Items.NETHER_STAR));
        builder.addOutputSlot(172, 7).setOutputSlotBackground().addItemStack(
                new ItemStack(ModRegistries.ENHANCED_CATASTROPHE_GEMS.get(recipe.gem()).get()));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, FocusRecipe recipe, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(200).setPosition(112, 7);
    }

    @Override
    public void draw(FocusRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawWordWrap(font, Component.translatable("jei.hypercrown.celestial_focusing.instructions"),
                3, 36, 190, 0xFFE7E2F2);
    }

    public record FocusRecipe(CatastropheGem gem) {}
}
