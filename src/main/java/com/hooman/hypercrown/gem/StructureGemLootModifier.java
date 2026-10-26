package com.hooman.hypercrown.gem;

import com.hooman.hypercrown.config.HypercrownConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/** Adds one configured gem to a matching structure chest with a fixed probability. */
public final class StructureGemLootModifier extends LootModifier {
    public static final MapCodec<StructureGemLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            IGlobalLootModifier.LOOT_CONDITIONS_CODEC.fieldOf("conditions").forGetter(modifier -> modifier.conditions),
            ResourceLocation.CODEC.fieldOf("table").forGetter(modifier -> modifier.table),
            Codec.STRING.fieldOf("gem").forGetter(modifier -> modifier.gemId),
            Codec.DOUBLE.fieldOf("chance").forGetter(modifier -> modifier.chance)
    ).apply(instance, StructureGemLootModifier::new));

    private final ResourceLocation table;
    private final String gemId;
    private final double chance;

    public StructureGemLootModifier(LootItemCondition[] conditions, ResourceLocation table, String gemId, double chance) {
        super(conditions);
        this.table = table;
        this.gemId = gemId;
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!HypercrownConfig.GEMS_ENABLED.get() || !HypercrownConfig.STRUCTURE_GEMS_ENABLED.get()) return generatedLoot;
        if (!context.getQueriedLootTableId().equals(table)) return generatedLoot;
        double effectiveChance = Math.min(1.0, chance * HypercrownConfig.STRUCTURE_GEM_CHANCE.get());
        if (effectiveChance <= 0 || context.getRandom().nextDouble() >= effectiveChance) return generatedLoot;
        CatastropheGem gem = CatastropheGem.byId(gemId);
        if (gem != null) generatedLoot.add(gem.stack());
        return generatedLoot;
    }

    @Override public MapCodec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
