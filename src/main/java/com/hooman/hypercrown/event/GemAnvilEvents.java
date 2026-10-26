package com.hooman.hypercrown.event;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.item.CatastropheGemItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/** Repair matching multi-use gems by combining two in an anvil for experience levels. */
@EventBusSubscriber(modid = HypercrownMod.MODID)
public final class GemAnvilEvents {
    private GemAnvilEvents() {}

    @SubscribeEvent
    public static void repairGem(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!(left.getItem() instanceof CatastropheGemItem leftGem)
                || !(right.getItem() instanceof CatastropheGemItem rightGem)
                || leftGem.gem() != rightGem.gem()
                || leftGem.isEnhanced() != rightGem.isEnhanced()
                || leftGem.gem().consumed()) return;

        ItemStack output = left.copy();
        int maxDamage = output.getMaxDamage();
        int remaining = Math.min(maxDamage,
                (maxDamage - left.getDamageValue()) + (maxDamage - right.getDamageValue()) + maxDamage / 8);
        output.setDamageValue(maxDamage - remaining);
        event.setOutput(output);
        event.setCost(5);
        event.setMaterialCost(1);
    }
}
