package com.hooman.hypercrown.ability;

import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.data.CrownPlayerData;
import com.hooman.hypercrown.data.CrownWorldData;
import com.hooman.hypercrown.item.CrownOwner;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Central "does this player get crown powers?" check, safe to call on both sides. */
public final class CrownHelper {
    private CrownHelper() {}

    public static ItemStack wornCrown(Player p) {
        ItemStack s = p.getItemBySlot(EquipmentSlot.HEAD);
        return isCrown(s) ? s : ItemStack.EMPTY;
    }

    public static boolean isCrown(ItemStack stack) {
        return stack.is(ModRegistries.HYPERCROWN.get()) || stack.is(ModRegistries.TACTICAL_CROWN.get());
    }

    public static boolean isHypercrown(Player p) {
        return p.getItemBySlot(EquipmentSlot.HEAD).is(ModRegistries.HYPERCROWN.get());
    }

    public static boolean isTacticalCrown(Player p) {
        return p.getItemBySlot(EquipmentSlot.HEAD).is(ModRegistries.TACTICAL_CROWN.get());
    }

    public static boolean isCrownActive(Player p) {
        if (!HypercrownConfig.SPEC.isLoaded() || !HypercrownConfig.CROWN_ENABLED.get()) return false;
        ItemStack stack = wornCrown(p);
        if (stack.isEmpty()) return false;
        if (stack.is(ModRegistries.TACTICAL_CROWN.get())) return true;
        if (!stack.isEmpty()) {
            CrownOwner owner = stack.get(ModRegistries.OWNER.get());
            if (owner == null) return HypercrownConfig.ALLOW_UNFORGED.get();
            return HypercrownConfig.ALLOW_TRANSFER.get() || owner.id().equals(p.getUUID());
        }
        return false;
    }

    /**
     * Server-side: is the worn Hypercrown the world's recorded crown, held by its recorded owner?
     * Use this (not {@link #isCrownActive}) for anything that must reject reset, stale, or copied crowns.
     */
    public static boolean isCanonicalHypercrown(ServerPlayer p) {
        return isHypercrown(p) && isCrownActive(p) && isCanonical(p, wornCrown(p));
    }

    private static boolean isCanonical(ServerPlayer serverPlayer, ItemStack crown) {
        CrownWorldData worldData = CrownWorldData.get(serverPlayer.getServer());
        CrownOwner owner = crown.get(ModRegistries.OWNER.get());
        if (owner == null) {
            if (!HypercrownConfig.ALLOW_UNFORGED.get() || worldData.currentOwner() != null) return false;
        } else if (!owner.id().equals(worldData.currentOwner())) {
            return false;
        }
        UUID itemId = crown.get(ModRegistries.CROWN_ID.get());
        if (itemId == null) {
            if (worldData.crownId() != null) return false;
            itemId = UUID.randomUUID();
            crown.set(ModRegistries.CROWN_ID.get(), itemId);
            worldData.claimLegacyCrownId(itemId);
        }
        return itemId.equals(worldData.crownId());
    }

    public static boolean has(Player p, Ability a) {
        if (!isCrownActive(p) || !HypercrownConfig.enabled(a)) return false;
        ItemStack crown = wornCrown(p);
        boolean balanced = crown.is(ModRegistries.TACTICAL_CROWN.get());
        boolean tacticalAbility = a == Ability.HYPERSTEP || a == Ability.HYPERBREAK
                || a == Ability.CROWN_GUARD || a == Ability.TACTICAL_AWARENESS;
        if (balanced && !tacticalAbility && (a.active || (a != Ability.SPEED && a != Ability.DAMAGE_REDUCTION
                && a != Ability.WEAPON_MASTERY && a != Ability.KNOCKBACK_IMMUNITY))) return false;
        if (!balanced && tacticalAbility) return false;
        if (!balanced && p instanceof ServerPlayer serverPlayer && !isCanonical(serverPlayer, crown)) return false;
        if (!balanced) return true;
        if (a.active) return true;
        if (crown.isEmpty()) return false;
        if (a != Ability.SPEED && a != Ability.DAMAGE_REDUCTION && a != Ability.WEAPON_MASTERY
                && a != Ability.KNOCKBACK_IMMUNITY) return false;
        int sockets = crown.getOrDefault(ModRegistries.SOCKETED_GEMS.get(), 0);
        return (sockets & (1 << a.ordinal())) != 0;
    }

    public static CrownPlayerData data(Player p) {
        return p.getData(ModRegistries.PLAYER_DATA);
    }

    public static long serverTime(Player p) {
        return p instanceof ServerPlayer serverPlayer
                ? serverPlayer.getServer().overworld().getGameTime()
                : p.level().getGameTime();
    }
}
