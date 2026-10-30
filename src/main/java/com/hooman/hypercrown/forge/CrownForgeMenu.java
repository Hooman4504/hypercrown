package com.hooman.hypercrown.forge;

import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.event.CrownWeaponEvents;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server-backed inventory and result slot for the worn Hypercrown's forge. */
public final class CrownForgeMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOTS = 3;
    public static final int RESULT_SLOT = 3;
    public static final int PLAYER_INVENTORY_START = 4;

    private final SimpleContainer inputs = new SimpleContainer(INPUT_SLOTS);
    private final SimpleContainer result = new SimpleContainer(1);

    public CrownForgeMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    public CrownForgeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf ignoredData) {
        super(ModRegistries.CROWN_FORGE_MENU.get(), containerId);

        for (int i = 0; i < INPUT_SLOTS; i++) {
            addSlot(new Slot(inputs, i, 28 + i * 36, 57));
        }
        addSlot(new Slot(result, 0, 173, 57) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                if (!player.level().isClientSide) consumeRecipe();
                super.onTake(player, stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        29 + column * 18, 126 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 29 + column * 18, 184));
        }

        inputs.addListener(container -> updateResult());
        updateResult();
    }

    private void updateResult() {
        CrownWeaponKind recipe = CrownForgeRecipes.find(inputs);
        result.setItem(0, recipe == null ? ItemStack.EMPTY : recipe.outputStack());
        broadcastChanges();
    }

    private void consumeRecipe() {
        CrownWeaponKind recipe = CrownForgeRecipes.find(inputs);
        if (recipe == null) return;
        for (CrownWeaponEvents.ForgingStage stage : CrownWeaponEvents.ForgingStage.values()) {
            CrownWeaponEvents.triggerEvent(stage, recipe.kindName());
        }
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack input = inputs.getItem(slot);
            if (!input.isEmpty()) input.shrink(1);
        }
        inputs.setChanged();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == RESULT_SLOT) {
            if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, slots.size(), true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, original);
            slot.onTake(player, original);
            return original;
        } else if (index >= PLAYER_INVENTORY_START) {
            if (!moveItemStackTo(stack, 0, INPUT_SLOTS, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, slots.size(), false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, inputs);
    }

    @Override
    public boolean stillValid(Player player) {
        if (!player.isAlive()) return false;
        return player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                ? CrownHelper.isCanonicalHypercrown(serverPlayer)
                : CrownHelper.isHypercrown(player) && CrownHelper.isCrownActive(player);
    }
}
