package com.hooman.hypercrown.forge;

import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.gem.CatastropheGem;
import com.hooman.hypercrown.item.CatastropheGemItem;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumMap;
import java.util.Map;

/** Recipes unique to the Celestial Focusing Table. */
public final class CelestialFocusingRecipes {
    private CelestialFocusingRecipes() {}

    public static CatastropheGem find(Container grid) {
        if (!HypercrownConfig.GEM_ENHANCEMENTS_ENABLED.get()) return null;
        if (grid.getContainerSize() != 9) return null;
        Map<CatastropheGem, Integer> gems = new EnumMap<>(CatastropheGem.class);
        int gold = 0;
        int netherStars = 0;

        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack stack = grid.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof CatastropheGemItem gemItem) {
                if (gemItem.isEnhanced()) return null;
                gems.merge(gemItem.gem(), stack.getCount(), Integer::sum);
            } else if (stack.is(Items.GOLD_INGOT)) {
                gold += stack.getCount();
            } else if (stack.is(Items.NETHER_STAR)) {
                netherStars += stack.getCount();
            } else {
                return null;
            }
        }

        if (gold < 4 || netherStars < 1 || gems.size() != 1) return null;
        Map.Entry<CatastropheGem, Integer> onlyGem = gems.entrySet().iterator().next();
        return onlyGem.getValue() >= 2 ? onlyGem.getKey() : null;
    }
}
