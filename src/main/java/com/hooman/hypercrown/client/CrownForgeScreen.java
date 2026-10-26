package com.hooman.hypercrown.client;

import com.hooman.hypercrown.forge.CrownForgeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact crown-themed forge UI; the world stays visible around the panel. */
public final class CrownForgeScreen extends AbstractContainerScreen<CrownForgeMenu> {
    public CrownForgeScreen(CrownForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 220;
        imageHeight = 210;
        inventoryLabelY = 112;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Paint the custom panel before AbstractContainerScreen renders slots and labels.
        drawPanel(graphics);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // The panel is drawn from renderBackground, before slots and labels.
    }

    private void drawPanel(GuiGraphics graphics) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFE0B85C);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFF17152C);
        graphics.fill(x + 5, y + 5, x + imageWidth - 5, y + imageHeight - 5, 0xFF0D1020);
        graphics.fill(x + 8, y + 29, x + imageWidth - 8, y + 30, 0xFF6D5793);
        graphics.fill(x + 8, y + 107, x + imageWidth - 8, y + 108, 0xFF51436E);

        graphics.fill(x + 20, y + 50, x + 122, y + 83, 0xFF17182D);
        graphics.fill(x + 163, y + 50, x + 199, y + 83, 0xFF302441);
        graphics.fill(x + 134, y + 61, x + 151, y + 63, 0xFFFFD674);
        graphics.fill(x + 147, y + 58, x + 151, y + 63, 0xFFFFD674);
        graphics.fill(x + 147, y + 66, x + 151, y + 71, 0xFFFFD674);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 10, 0xFFFFD980, false);
        graphics.drawString(font, Component.translatable("screen.hypercrown.crown_forge.subtitle"), 8, 20,
                0xFFB8ADC9, false);
        graphics.drawCenteredString(font, Component.translatable("screen.hypercrown.crown_forge.gem"), 36, 40, 0xFF79D8FF);
        graphics.drawCenteredString(font, Component.translatable("screen.hypercrown.crown_forge.weapon"), 72, 40, 0xFFFFD980);
        graphics.drawCenteredString(font, Component.translatable("screen.hypercrown.crown_forge.catalyst"), 108, 40, 0xFFC99BFF);
        graphics.drawCenteredString(font, Component.translatable("screen.hypercrown.crown_forge.output"), 181, 40, 0xFFFFD980);
        graphics.drawString(font, Component.translatable("container.inventory"), 29, inventoryLabelY, 0xFFD9D2E7, false);
    }
}
