package com.hooman.hypercrown.compat.jei;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.gem.CatastropheGem;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

@JeiPlugin
public final class HypercrownJeiPlugin implements IModPlugin {
    public static final RecipeType<FinalAssemblyRecipe> FINAL_ASSEMBLY =
            RecipeType.create(HypercrownMod.MODID, "final_assembly", FinalAssemblyRecipe.class);
    public static final RecipeType<GemGuideCategory.GemGuideRecipe> GEM_GUIDE =
            RecipeType.create(HypercrownMod.MODID, "gem_guide", GemGuideCategory.GemGuideRecipe.class);
    public static final RecipeType<CrownForgeCategory.ForgeRecipe> CROWN_FORGE =
            RecipeType.create(HypercrownMod.MODID, "crown_forge", CrownForgeCategory.ForgeRecipe.class);
    public static final RecipeType<CelestialFocusingCategory.FocusRecipe> CELESTIAL_FOCUSING =
            RecipeType.create(HypercrownMod.MODID, "celestial_focusing", CelestialFocusingCategory.FocusRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return HypercrownMod.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new FinalAssemblyCategory(registration.getJeiHelpers().getGuiHelper()),
                new GemGuideCategory(registration.getJeiHelpers().getGuiHelper()),
                new CrownForgeCategory(registration.getJeiHelpers().getGuiHelper()),
                new CelestialFocusingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(FINAL_ASSEMBLY, List.of(new FinalAssemblyRecipe()));
        registration.addRecipes(GEM_GUIDE, java.util.Arrays.stream(CatastropheGem.values())
                .map(GemGuideCategory.GemGuideRecipe::new).toList());
        registration.addRecipes(CROWN_FORGE, java.util.Arrays.stream(com.hooman.hypercrown.forge.CrownWeaponKind.values())
                .map(CrownForgeCategory.ForgeRecipe::new).toList());
        registration.addRecipes(CELESTIAL_FOCUSING, java.util.Arrays.stream(CatastropheGem.values())
                .map(CelestialFocusingCategory.FocusRecipe::new).toList());
    }

    public record FinalAssemblyRecipe() {}
}
