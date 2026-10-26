package com.hooman.hypercrown.registry;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.data.CrownPlayerData;
import com.hooman.hypercrown.item.CrownOwner;
import com.hooman.hypercrown.item.HypercrownItem;
import com.hooman.hypercrown.item.CrownManualItem;
import com.hooman.hypercrown.item.CatastropheGemItem;
import com.hooman.hypercrown.gem.CatastropheGem;
import com.hooman.hypercrown.gem.StructureGemLootModifier;
import com.hooman.hypercrown.block.CelestialFocusingTableBlock;
import com.hooman.hypercrown.forge.CrownForgeMenu;
import com.hooman.hypercrown.forge.CrownWeaponKind;
import com.hooman.hypercrown.item.CrownForgedWeaponItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

import java.util.List;
import java.util.Map;
import java.util.EnumMap;

import static com.hooman.hypercrown.HypercrownMod.MODID;

/**
 * All registries in one place.
 */
public final class ModRegistries {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, MODID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<StructureGemLootModifier>> STRUCTURE_GEM_LOOT_MODIFIER =
            LOOT_MODIFIERS.register("structure_gem", () -> StructureGemLootModifier.CODEC);
    public static final DeferredHolder<MenuType<?>, MenuType<CrownForgeMenu>> CROWN_FORGE_MENU = MENUS.register(
            "crown_forge", () -> IMenuTypeExtension.create(CrownForgeMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<CelestialFocusingTableBlock.FocusingCraftingMenu>> CELESTIAL_FOCUSING_MENU = MENUS.register(
            "celestial_focusing", () -> IMenuTypeExtension.create(CelestialFocusingTableBlock.FocusingCraftingMenu::new));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SOCKETED_GEMS = COMPONENTS.register("socketed_gems",
            () -> DataComponentType.<Integer>builder().persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SOCKETED_CATASTROPHE_GEM = COMPONENTS.register("socketed_catastrophe_gem",
            () -> DataComponentType.<String>builder().persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8).build());

    public static final Map<CatastropheGem, DeferredItem<CatastropheGemItem>> CATASTROPHE_GEMS = new EnumMap<>(CatastropheGem.class);
    public static final Map<CatastropheGem, DeferredItem<CatastropheGemItem>> ENHANCED_CATASTROPHE_GEMS = new EnumMap<>(CatastropheGem.class);
    public static final Map<CrownWeaponKind, DeferredItem<CrownForgedWeaponItem>> CROWN_FORGED_WEAPONS = new EnumMap<>(CrownWeaponKind.class);
    public static final DeferredHolder<Block, CelestialFocusingTableBlock> CELESTIAL_FOCUSING_TABLE_BLOCK = BLOCKS.register(
            "celestial_focusing_table", () -> new CelestialFocusingTableBlock(
                    BlockBehaviour.Properties.of().strength(3.5F).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> CELESTIAL_FOCUSING_TABLE = ITEMS.register("celestial_focusing_table",
            () -> new BlockItem(CELESTIAL_FOCUSING_TABLE_BLOCK.get(), new Item.Properties()));

    static {
        for (CatastropheGem gem : CatastropheGem.values()) {
            CATASTROPHE_GEMS.put(gem, ITEMS.register(gem.id(), () -> new CatastropheGemItem(
                    new Item.Properties().rarity(Rarity.EPIC), gem)));
            ENHANCED_CATASTROPHE_GEMS.put(gem, ITEMS.register("enhanced_" + gem.id(), () -> new CatastropheGemItem(
                    new Item.Properties().rarity(Rarity.EPIC), gem, true)));
        }
        for (CrownWeaponKind weapon : CrownWeaponKind.values()) {
            CROWN_FORGED_WEAPONS.put(weapon, ITEMS.register(weapon.id(), () -> new CrownForgedWeaponItem(weapon,
                    new Item.Properties().stacksTo(1).durability(4096).rarity(Rarity.EPIC).fireResistant())));
        }
    }

    /**
     * Original Hypercrown material; enchantability 0, no repair ingredient.
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CROWN_MATERIAL = ARMOR_MATERIALS.register("hypercrown",
            () -> new ArmorMaterial(Map.of(ArmorItem.Type.HELMET, 10), 0, SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.EMPTY, List.of(new ArmorMaterial.Layer(HypercrownMod.id("hypercrown"))), 5.0F, 1.0F));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> TACTICAL_MATERIAL = ARMOR_MATERIALS.register("tactical",
            () -> new ArmorMaterial(Map.of(ArmorItem.Type.HELMET, 2), 12, SoundEvents.ARMOR_EQUIP_GOLD,
                    () -> Ingredient.of(Items.GOLD_INGOT), List.of(new ArmorMaterial.Layer(HypercrownMod.id("hypercrown"))), 0.0F, 0.0F));

    /**
     * Who forged the crown. Crowns without this (creative / commands) are "unforged".
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CrownOwner>> OWNER = COMPONENTS.register("owner",
            () -> DataComponentType.<CrownOwner>builder().persistent(CrownOwner.CODEC).networkSynchronized(CrownOwner.STREAM_CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> CROWN_ID = COMPONENTS.register("crown_id",
            () -> DataComponentType.<java.util.UUID>builder().persistent(net.minecraft.core.UUIDUtil.CODEC)
                    .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC).build());

    public static final DeferredItem<Item> CROWN_FRAGMENT = ITEMS.register("crown_fragment",
            () -> loreItem(new Item.Properties().rarity(Rarity.RARE).fireResistant(), "tooltip.hypercrown.fragment"));
    public static final DeferredItem<Item> UNAWAKENED_CROWN = ITEMS.register("unawakened_crown",
            () -> loreItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), "tooltip.hypercrown.unawakened"));
    public static final DeferredItem<CrownManualItem> CROWN_MANUAL = ITEMS.register("crown_manual",
            () -> new CrownManualItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    // No .durability(...) => max damage 0 => cannot break and cannot be enchanted at a table.
    public static final DeferredItem<HypercrownItem> HYPERCROWN = ITEMS.register("hypercrown",
            () -> new HypercrownItem(CROWN_MATERIAL, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<com.hooman.hypercrown.item.TacticalCrownItem> TACTICAL_CROWN = ITEMS.register("tactical_crown",
            () -> new com.hooman.hypercrown.item.TacticalCrownItem(TACTICAL_MATERIAL,
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> STRIDER_GEM = ITEMS.register("strider_gem", () -> new com.hooman.hypercrown.item.CrownGemItem(
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE), com.hooman.hypercrown.ability.Ability.SPEED));
    public static final DeferredItem<Item> BULWARK_GEM = ITEMS.register("bulwark_gem", () -> new com.hooman.hypercrown.item.CrownGemItem(
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE), com.hooman.hypercrown.ability.Ability.DAMAGE_REDUCTION));
    public static final DeferredItem<Item> EDGE_GEM = ITEMS.register("edge_gem", () -> new com.hooman.hypercrown.item.CrownGemItem(
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE), com.hooman.hypercrown.ability.Ability.WEAPON_MASTERY));
    public static final DeferredItem<Item> GRAVITY_GEM = ITEMS.register("gravity_gem", () -> new com.hooman.hypercrown.item.CrownGemItem(
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE), com.hooman.hypercrown.ability.Ability.KNOCKBACK_IMMUNITY));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LEGENDARY_TAB = TABS.register("legendary_items",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.hypercrown.legendary_items"))
                    .icon(() -> new ItemStack(HYPERCROWN.get()))
                    .displayItems((params, out) -> {
                        out.accept(CROWN_FRAGMENT.get());
                        out.accept(UNAWAKENED_CROWN.get());
                        out.accept(CROWN_MANUAL.get());
                        out.accept(HYPERCROWN.get());
                        out.accept(TACTICAL_CROWN.get());
                        out.accept(STRIDER_GEM.get());
                        out.accept(BULWARK_GEM.get());
                        out.accept(EDGE_GEM.get());
                        out.accept(GRAVITY_GEM.get());
                        out.accept(CELESTIAL_FOCUSING_TABLE.get());
                        CATASTROPHE_GEMS.values().forEach(gem -> out.accept(gem.get()));
                        ENHANCED_CATASTROPHE_GEMS.values().forEach(gem -> out.accept(gem.get()));
                        CROWN_FORGED_WEAPONS.values().forEach(weapon -> out.accept(weapon.get()));
                    }).build());

    /**
     * Per-player cooldowns, anchor, boss kills, etc. Survives death.
     */
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
        BLOCKS.register(bus);
        MENUS.register(bus);
        LOOT_MODIFIERS.register(bus);
        ARMOR_MATERIALS.register(bus);
        COMPONENTS.register(bus);
        TABS.register(bus);
        ATTACHMENTS.register(bus);
    }
}
