package com.hooman.hypercrown.registry;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.data.CrownPlayerData;
import com.hooman.hypercrown.item.CrownOwner;
import com.hooman.hypercrown.item.HypercrownItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.*;

import java.util.List;
import java.util.Map;

import static com.hooman.hypercrown.HypercrownMod.MODID;

/** All registries in one place. */
public final class ModRegistries {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, MODID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);

    /** Enchantability 0, no repair ingredient; worn texture = textures/models/armor/hypercrown_layer_1.png */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CROWN_MATERIAL = ARMOR_MATERIALS.register("hypercrown",
            () -> new ArmorMaterial(Map.of(ArmorItem.Type.HELMET, 10), 0, SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.EMPTY, List.of(new ArmorMaterial.Layer(HypercrownMod.id("hypercrown"))), 5.0F, 1.0F));

    /** Who forged the crown. Crowns without this (creative / commands) are "unforged". */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CrownOwner>> OWNER = COMPONENTS.register("owner",
            () -> DataComponentType.<CrownOwner>builder().persistent(CrownOwner.CODEC).networkSynchronized(CrownOwner.STREAM_CODEC).build());

    public static final DeferredItem<Item> CROWN_FRAGMENT = ITEMS.register("crown_fragment",
            () -> loreItem(new Item.Properties().rarity(Rarity.RARE).fireResistant(), "tooltip.hypercrown.fragment"));
    public static final DeferredItem<Item> UNAWAKENED_CROWN = ITEMS.register("unawakened_crown",
            () -> loreItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), "tooltip.hypercrown.unawakened"));
    // No .durability(...) => max damage 0 => cannot break and cannot be enchanted at a table.
    public static final DeferredItem<HypercrownItem> HYPERCROWN = ITEMS.register("hypercrown",
            () -> new HypercrownItem(CROWN_MATERIAL, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LEGENDARY_TAB = TABS.register("legendary_items",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.hypercrown.legendary_items"))
                    .icon(() -> new ItemStack(HYPERCROWN.get()))
                    .displayItems((params, out) -> {
                        out.accept(CROWN_FRAGMENT.get());
                        out.accept(UNAWAKENED_CROWN.get());
                        out.accept(HYPERCROWN.get());
                    }).build());

    /** Per-player cooldowns, anchor, boss kills, etc. Survives death. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CrownPlayerData>> PLAYER_DATA = ATTACHMENTS.register("player_data",
            () -> AttachmentType.serializable(() -> new CrownPlayerData()).copyOnDeath().build());

    private static Item loreItem(Item.Properties props, String loreKey) {
        return new Item(props) {
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
                tooltip.add(Component.translatable(loreKey).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
            }
        };
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        ARMOR_MATERIALS.register(bus);
        COMPONENTS.register(bus);
        TABS.register(bus);
        ATTACHMENTS.register(bus);
    }
}
