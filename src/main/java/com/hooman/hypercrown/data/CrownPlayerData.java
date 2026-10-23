package com.hooman.hypercrown.data;

import com.hooman.hypercrown.ability.Ability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.List;

/** Per-player saved state (NeoForge data attachment, copied on death). */
public class CrownPlayerData implements INBTSerializable<CompoundTag> {
    private final long[] readyAt = new long[Ability.values().length];

    public ResourceKey<Level> anchorDim;
    public BlockPos anchorPos;
    public boolean killedDragon, killedWither, killedElder;
    public int usedMask;                                   // bit per ability used (for advancements)
    public final List<ItemStack> pendingReturn = new ArrayList<>(); // crowns kept through death

    // Transient (not saved)
    public long lastHurtTick = -10_000;
    public long beamChargeStart = -1;

    public long remaining(Ability a, long now) { return Math.max(0, readyAt[a.ordinal()] - now); }
    public void startCooldown(Ability a, long now, int ticks) { readyAt[a.ordinal()] = now + ticks; }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putLongArray("cooldowns", readyAt);
        if (anchorPos != null && anchorDim != null) {
            t.putString("anchorDim", anchorDim.location().toString());
            t.putLong("anchorPos", anchorPos.asLong());
        }
        t.putBoolean("killedDragon", killedDragon);
        t.putBoolean("killedWither", killedWither);
        t.putBoolean("killedElder", killedElder);
        t.putInt("usedMask", usedMask);
        ListTag list = new ListTag();
        for (ItemStack s : pendingReturn) if (!s.isEmpty()) list.add(s.save(provider));
        t.put("pendingReturn", list);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        long[] cd = t.getLongArray("cooldowns");
        System.arraycopy(cd, 0, readyAt, 0, Math.min(cd.length, readyAt.length));
        if (t.contains("anchorPos")) {
            anchorDim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("anchorDim")));
            anchorPos = BlockPos.of(t.getLong("anchorPos"));
        }
        killedDragon = t.getBoolean("killedDragon");
        killedWither = t.getBoolean("killedWither");
        killedElder = t.getBoolean("killedElder");
        usedMask = t.getInt("usedMask");
        pendingReturn.clear();
        ListTag list = t.getList("pendingReturn", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) ItemStack.parse(provider, list.getCompound(i)).ifPresent(pendingReturn::add);
    }
}
