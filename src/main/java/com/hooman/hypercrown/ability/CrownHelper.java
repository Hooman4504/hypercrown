package com.hooman.hypercrown.ability;

import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.data.CrownPlayerData;
import com.hooman.hypercrown.item.CrownOwner;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Central "does this player get crown powers?" check, safe to call on both sides. */
public final class CrownHelper {
    private CrownHelper() {}

    public static ItemStack wornCrown(Player p) {
        ItemStack s = p.getItemBySlot(EquipmentSlot.HEAD);
        return s.is(ModRegistries.HYPERCROWN.get()) ? s : ItemStack.EMPTY;
    }

    public static boolean isCrownActive(Player p) {
        if (!HypercrownConfig.SPEC.isLoaded() || !HypercrownConfig.CROWN_ENABLED.get()) return false;
        ItemStack s = wornCrown(p);
        if (s.isEmpty()) return false;
        CrownOwner owner = s.get(ModRegistries.OWNER.get());
        if (owner == null) return HypercrownConfig.ALLOW_UNFORGED.get();           // creative/command crowns
        return HypercrownConfig.ALLOW_TRANSFER.get() || owner.id().equals(p.getUUID()); // soulbound by default off
    }

    public static boolean has(Player p, Ability a) {
        return isCrownActive(p) && HypercrownConfig.enabled(a);
    }

    public static CrownPlayerData data(Player p) {
        return p.getData(ModRegistries.PLAYER_DATA);
    }
}
