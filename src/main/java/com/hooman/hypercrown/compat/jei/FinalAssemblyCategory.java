package com.hooman.hypercrown.compat.jei;

import com.hooman.hypercrown.config.HypercrownConfig;
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
import net.minecraft.world.level.block.Blocks;

public final class FinalAssemblyCategory extends AbstractRecipeCategory<HypercrownJeiPlugin.FinalAssemblyRecipe> {
    public FinalAssemblyCategory(IGuiHelper guiHelper) {
        super(HypercrownJeiPlugin.FINAL_ASSEMBLY,
                Component.translatable("jei.hypercrown.final_assembly.title"),
                guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, ModRegistries.HYPERCROWN.get().getDefaultInstance()),
                194,
                76);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, HypercrownJeiPlugin.FinalAssemblyRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(2, 7)
                .setStandardSlotBackground()
                .addItemStack(new ItemStack(ModRegistries.UNAWAKENED_CROWN.get()));
        builder.addInputSlot(24, 7)
                .setStandardSlotBackground()
                .addItemStack(Blocks.DRAGON_EGG.asItem().getDefaultInstance());
        builder.addOutputSlot(172, 7)
                .setOutputSlotBackground()
                .addItemStack(new ItemStack(ModRegistries.HYPERCROWN.get()));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, HypercrownJeiPlugin.FinalAssemblyRecipe recipe, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(200).setPosition(91, 7);
    }

    @Override
    public void draw(HypercrownJeiPlugin.FinalAssemblyRecipe recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, Component.translatable("jei.hypercrown.final_assembly.step1"), 97, 31, 0xFFF4D58A);
        graphics.drawCenteredString(font, Component.translatable("jei.hypercrown.final_assembly.step2"), 97, 39, 0xFFE7E2F2);
        graphics.drawCenteredString(font, Component.translatable("jei.hypercrown.final_assembly.step3"), 97, 47, 0xFFE7E2F2);
        graphics.drawCenteredString(font, Component.translatable("jei.hypercrown.final_assembly.step4"), 97, 55, 0xFFE7E2F2);
        graphics.drawCenteredString(font,
                Component.translatable("jei.hypercrown.final_assembly.step5", HypercrownConfig.RITUAL_XP_LEVELS.get()),
                97, 63, 0xFFBFA7F4);
    }
}
