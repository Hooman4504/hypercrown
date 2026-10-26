package com.hooman.hypercrown.forge;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Small, server-authoritative recipe matcher for the Crown Forge's three input slots. */
public final class CrownForgeRecipes {
    private CrownForgeRecipes() {}

    public static CrownWeaponKind find(Container inputs) {
        if (inputs.getContainerSize() != 3) return null;
        for (CrownWeaponKind kind : CrownWeaponKind.values()) {
            boolean gem = false;
            boolean weapon = false;
            boolean catalyst = false;
            boolean matched = true;
            for (int slot = 0; slot < 3; slot++) {
                ItemStack stack = inputs.getItem(slot);
                if (stack.isEmpty()) {
                    matched = false;
                    break;
                }
                if (!gem && stack.is(kind.gemStack().getItem())) gem = true;
                else if (!weapon && stack.is(kind.baseWeapon())) weapon = true;
                else if (!catalyst && stack.is(kind.catalyst())) catalyst = true;
                else {
                    matched = false;
                    break;
                }
            }
            if (matched && gem && weapon && catalyst) return kind;
        }
        return null;
    }
}
