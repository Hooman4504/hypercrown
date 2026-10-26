package com.hooman.hypercrown.compat.jei;

import com.hooman.hypercrown.gem.CatastropheGem;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class GemGuideCategory extends AbstractRecipeCategory<GemGuideCategory.GemGuideRecipe> {
    public GemGuideCategory(IGuiHelper guiHelper) {
        super(HypercrownJeiPlugin.GEM_GUIDE,
                Component.translatable("jei.hypercrown.gem_guide.title"),
                guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, CatastropheGem.CELESTIAL_SAPPHIRE.stack()),
                196, 76);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, GemGuideRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(4, 7).setStandardSlotBackground().addItemStack(recipe.gem().stack());
    }

    @Override
    public void draw(GemGuideRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        String location = locationKey(recipe.gem());
        graphics.drawString(font, Component.translatable("jei.hypercrown.gem_guide.discovery"), 30, 10, 0xFFF4D58A, false);
        graphics.drawString(font, Component.translatable(location), 30, 24, 0xFFE7E2F2, false);
        graphics.drawWordWrap(font, Component.translatable("tooltip.hypercrown.catastrophe_gem." + recipe.gem().id()),
                5, 49, 186, 0xFFBFC8DF);
    }

    private static String locationKey(CatastropheGem gem) {
        return "jei.hypercrown.gem_guide.location." + gem.id();
    }

    public record GemGuideRecipe(CatastropheGem gem) {}
}
