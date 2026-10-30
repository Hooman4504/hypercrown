package com.hooman.hypercrown.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** World-wide record of forged crowns (stored in the overworld's data folder). */
public class CrownWorldData extends SavedData {
    private int crownsForged;
    private final List<UUID> forgers = new ArrayList<>();
    private UUID currentOwner;
    private UUID crownId;

    public static CrownWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CrownWorldData::new, CrownWorldData::load, null), "hypercrown_world");
    }

    private static CrownWorldData load(CompoundTag tag, HolderLookup.Provider provider) {
        CrownWorldData d = new CrownWorldData();
        d.crownsForged = tag.getInt("crownsForged");
        ListTag list = tag.getList("forgers", 11); // 11 = int array
        for (int i = 0; i < list.size(); i++) d.forgers.add(NbtUtils.loadUUID(list.get(i)));
        if (tag.hasUUID("currentOwner")) d.currentOwner = tag.getUUID("currentOwner");
        else if (!d.forgers.isEmpty()) d.currentOwner = d.forgers.get(d.forgers.size() - 1);
        if (tag.hasUUID("crownId")) d.crownId = tag.getUUID("crownId");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("crownsForged", crownsForged);
        ListTag list = new ListTag();
        forgers.forEach(u -> list.add(NbtUtils.createUUID(u)));
        tag.put("forgers", list);
        if (currentOwner != null) tag.putUUID("currentOwner", currentOwner);
        if (crownId != null) tag.putUUID("crownId", crownId);
        return tag;
    }

    public int crownsForged() { return crownsForged; }
    public UUID currentOwner() { return currentOwner; }
    public UUID crownId() { return crownId; }
    public void recordForge(UUID player, UUID id) { crownsForged++; forgers.add(player); currentOwner = player; crownId = id; setDirty(); }
    /** Makes a crown canonical without using up the world's forge limit (creative test rituals). */
    public void recordUncountedForge(UUID player, UUID id) { currentOwner = player; crownId = id; setDirty(); }
    public void claimLegacyCrownId(UUID id) { if (crownId == null) { crownId = id; setDirty(); } }
    public void transferOwnership(UUID player) { currentOwner = player; setDirty(); }
    public void reset() { crownsForged = 0; forgers.clear(); currentOwner = null; crownId = null; setDirty(); }
}
