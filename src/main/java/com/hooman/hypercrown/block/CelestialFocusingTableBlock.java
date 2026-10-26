package com.hooman.hypercrown.block;

import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import com.hooman.hypercrown.gem.CatastropheGem;
import com.hooman.hypercrown.item.CatastropheGemItem;
import com.hooman.hypercrown.forge.CelestialFocusingRecipes;

/**
 * Dedicated altar menu: its recipes are separate from vanilla crafting recipes.
 */
public final class CelestialFocusingTableBlock extends CraftingTableBlock {
    private static final Component CONTAINER_TITLE = Component.translatable("container.hypercrown.celestial_focusing");

    public CelestialFocusingTableBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        ContainerLevelAccess access = ContainerLevelAccess.create(level, pos);
        return new SimpleMenuProvider((containerId, inventory, player) ->
                new FocusingCraftingMenu(containerId, inventory, access), CONTAINER_TITLE);
    }

    public static final class FocusingCraftingMenu extends AbstractContainerMenu {
        private static final int GRID_SIZE = 9;
        private static final int RESULT_SLOT = 9;
        private static final int PLAYER_START = 10;
        private final SimpleContainer grid = new SimpleContainer(GRID_SIZE);
        private final SimpleContainer result = new SimpleContainer(1);
        private final ContainerLevelAccess access;

        public FocusingCraftingMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf ignoredData) {
            this(containerId, inventory, ContainerLevelAccess.NULL);
        }

        public FocusingCraftingMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
            super(ModRegistries.CELESTIAL_FOCUSING_MENU.get(), containerId);
            this.access = access;

            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    addSlot(new Slot(grid, column + row * 3, 30 + column * 18, 50 + row * 18));
                }
            }
            addSlot(new Slot(result, 0, 177, 68) {
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
                            37 + column * 18, 140 + row * 18));
                }
            }
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column, 37 + column * 18, 198));
            }

            grid.addListener(container -> updateResult());
            updateResult();
        }

        private void updateResult() {
            CatastropheGem gem = CelestialFocusingRecipes.find(grid);
            result.setItem(0, gem == null ? ItemStack.EMPTY
                    : new ItemStack(ModRegistries.ENHANCED_CATASTROPHE_GEMS.get(gem).get()));
            broadcastChanges();
        }

        private void consumeRecipe() {
            if (CelestialFocusingRecipes.find(grid) == null) return;
            int gems = 2;
            int gold = 4;
            int stars = 1;
            for (int slot = 0; slot < GRID_SIZE; slot++) {
                ItemStack stack = grid.getItem(slot);
                if (stack.isEmpty()) continue;
                if (stack.getItem() instanceof CatastropheGemItem && gems > 0) {
                    int used = Math.min(gems, stack.getCount());
                    stack.shrink(used);
                    gems -= used;
                } else if (stack.is(net.minecraft.world.item.Items.GOLD_INGOT) && gold > 0) {
                    int used = Math.min(gold, stack.getCount());
                    stack.shrink(used);
                    gold -= used;
                } else if (stack.is(net.minecraft.world.item.Items.NETHER_STAR) && stars > 0) {
                    int used = Math.min(stars, stack.getCount());
                    stack.shrink(used);
                    stars -= used;
                }
            }
            grid.setChanged();
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
            Slot slot = slots.get(index);
            if (!slot.hasItem()) return ItemStack.EMPTY;
            ItemStack stack = slot.getItem();
            ItemStack original = stack.copy();

            if (index == RESULT_SLOT) {
                if (!moveItemStackTo(stack, PLAYER_START, slots.size(), true)) return ItemStack.EMPTY;
                slot.onQuickCraft(stack, original);
                slot.onTake(player, original);
                return original;
            } else if (index >= PLAYER_START) {
                if (!moveItemStackTo(stack, 0, GRID_SIZE, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, PLAYER_START, slots.size(), false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            return original;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            if (!player.level().isClientSide) clearContainer(player, grid);
        }

        @Override
        public boolean stillValid(Player player) {
            return player.isAlive() && access.evaluate((level, pos) ->
                    level.getBlockState(pos).is(ModRegistries.CELESTIAL_FOCUSING_TABLE_BLOCK.get())
                            && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0,
                    true);
        }
    }
}
