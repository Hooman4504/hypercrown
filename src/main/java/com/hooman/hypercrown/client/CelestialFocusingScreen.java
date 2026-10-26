package com.hooman.hypercrown.client;

import com.hooman.hypercrown.block.CelestialFocusingTableBlock;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated, altar-themed interface for focusing catastrophe gems. */
public final class CelestialFocusingScreen extends AbstractContainerScreen<CelestialFocusingTableBlock.FocusingCraftingMenu> {
    public CelestialFocusingScreen(CelestialFocusingTableBlock.FocusingCraftingMenu menu,
                                   Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 236;
        imageHeight = 224;
        inventoryLabelY = 122;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw the panel here so it is painted before AbstractContainerScreen renders
        // the slots and labels, even on versions that skip the normal texture pass.
        drawPanel(graphics);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // The panel is drawn from renderBackground, before slots and labels.
    }

    private void drawPanel(GuiGraphics graphics) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFE9C778);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFF26203B);
        graphics.fill(x + 5, y + 5, x + imageWidth - 5, y + imageHeight - 5, 0xFF111323);
        graphics.fill(x + 9, y + 34, x + imageWidth - 9, y + 35, 0xFF8063B6);

        graphics.fill(x + 25, y + 43, x + 100, y + 112, 0xFF211B34);
        graphics.fill(x + 25, y + 43, x + 100, y + 44, 0xFF70D6E8);
        graphics.fill(x + 25, y + 111, x + 100, y + 112, 0xFF70D6E8);
        graphics.fill(x + 166, y + 60, x + 202, y + 96, 0xFF37284D);
        graphics.fill(x + 127, y + 77, x + 151, y + 79, 0xFFFFD978);
        graphics.fill(x + 147, y + 73, x + 151, y + 79, 0xFFFFD978);
        graphics.fill(x + 147, y + 79, x + 151, y + 85, 0xFFFFD978);
        graphics.fill(x + 9, y + 129, x + imageWidth - 9, y + 130, 0xFF51436E);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 10, 0xFFFFDE91, false);
        graphics.drawString(font, Component.translatable("screen.hypercrown.focusing.subtitle"), 10, 21,
                0xFFBEB4D9, false);
        graphics.drawString(font, Component.translatable("screen.hypercrown.focusing.grid"), 27, 38,
                0xFF82DDED, false);
        graphics.drawCenteredString(font, Component.translatable("screen.hypercrown.focusing.output"),
                184, 49, 0xFFFFDE91);
        graphics.drawString(font, Component.translatable("container.inventory"), 37, inventoryLabelY,
                0xFFE3DDF0, false);
    }
}
