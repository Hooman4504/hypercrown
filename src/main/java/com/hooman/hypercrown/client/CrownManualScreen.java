package com.hooman.hypercrown.client;

import com.hooman.hypercrown.item.CrownManualItem;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Royal, paginated in-game guide opened by using the Crown Field Manual item. */
public final class CrownManualScreen extends Screen {
    private static final int[] SECTION_STARTS = {0, 2, 3, 4, 13, 28};
    private static final String[] SECTION_KEYS = {
            "manual.section.crafting", "manual.section.ritual", "manual.section.controls",
            "manual.section.active", "manual.section.passive", "manual.section.tactics"
    };
    private static final int[] TAB_COLORS = {0xFFE9B95E, 0xFFDB9B63, 0xFF78D9E8, 0xFFC9A5FF, 0xFF9BE7C2, 0xFFF0A765};
    private static final int PANEL_MAX_WIDTH = 480;
    private static final int PANEL_MAX_HEIGHT = 300;

    private int page;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    private CrownManualScreen() {
        super(Component.translatable("manual.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new CrownManualScreen());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateLayout();
        graphics.fill(0, 0, width, height, 0xA0000000);
        drawFrame(graphics);
        drawHeader(graphics);
        drawTabs(graphics, mouseX, mouseY);
        drawPage(graphics);
        drawFooter(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    private void updateLayout() {
        panelWidth = Math.min(PANEL_MAX_WIDTH, width - 24);
        panelHeight = Math.min(PANEL_MAX_HEIGHT, height - 24);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
    }

    private void drawFrame(GuiGraphics graphics) {
        int x = panelX;
        int y = panelY;
        int right = x + panelWidth;
        int bottom = y + panelHeight;
        graphics.fill(x, y, right, bottom, 0xFFE3B85E);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, 0xFF201D36);
        graphics.fill(x + 3, y + 3, right - 3, bottom - 3, 0xF20B1020);
        graphics.fill(x + 4, y + 4, right - 4, y + 42, 0xFF17152C);
        graphics.fill(x + 12, y + 44, right - 12, bottom - 40, 0xCC101629);

        // Small gold corner marks and a violet spine make the panel read like a relic folio.
        graphics.fill(x + 3, y + 3, x + 8, y + 21, 0xFFE8BF68);
        graphics.fill(right - 8, bottom - 21, right - 3, bottom - 3, 0xFFC7A0F4);
        graphics.fill(x + 8, bottom - 4, right - 8, bottom - 3, 0xFF7759A7);
        graphics.fill(x + 8, y + 41, right - 8, y + 42, 0xFF51436E);
    }

    private void drawHeader(GuiGraphics graphics) {
        int x = panelX;
        int y = panelY;
        graphics.renderItem(new ItemStack(ModRegistries.CROWN_MANUAL.get()), x + 14, y + 10);
        graphics.drawString(font, Component.translatable("manual.title"), x + 38, y + 10, 0xFFFFD980, false);
        graphics.drawString(font, Component.translatable("manual.subtitle"), x + 38, y + 23, 0xFFAAA5C7, false);

        int closeX = x + panelWidth - 26;
        graphics.fill(closeX, y + 8, closeX + 16, y + 24, 0xFF342B4D);
        graphics.drawCenteredString(font, Component.literal("×"), closeX + 8, y + 11, 0xFFE2D7F1);
        graphics.drawString(font, Component.translatable("manual.page_counter", page + 1, CrownManualItem.PAGES.size()),
                x + panelWidth - 89, y + 28, 0xFFBEB6D2, false);
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int gap = 4;
        int left = panelX + 14;
        int top = panelY + 49;
        int available = panelWidth - 28 - gap * (SECTION_KEYS.length - 1);
        int tabWidth = available / SECTION_KEYS.length;
        int active = activeSection();
        for (int i = 0; i < SECTION_KEYS.length; i++) {
            int tabX = left + i * (tabWidth + gap);
            int tabRight = tabX + tabWidth;
            boolean selected = i == active;
            boolean hovered = mouseX >= tabX && mouseX < tabRight && mouseY >= top && mouseY < top + 21;
            int bg = selected ? 0xFF302847 : hovered ? 0xFF29243E : 0xFF19182D;
            graphics.fill(tabX, top, tabRight, top + 21, bg);
            graphics.fill(tabX, top + 19, tabRight, top + 21, selected ? TAB_COLORS[i] : 0xFF403956);
            graphics.drawCenteredString(font, Component.translatable(SECTION_KEYS[i]), (tabX + tabRight) / 2,
                    top + 6, selected ? TAB_COLORS[i] : 0xFFD5CEE3);
        }
    }

    private void drawPage(GuiGraphics graphics) {
        int cardX = panelX + 17;
        int cardY = panelY + 78;
        int cardRight = panelX + panelWidth - 17;
        int cardBottom = panelY + panelHeight - 43;
        graphics.fill(cardX, cardY, cardRight, cardBottom, 0xAA0A0E1C);
        graphics.fill(cardX, cardY, cardX + 2, cardBottom, TAB_COLORS[activeSection()]);

        String bodyKey = CrownManualItem.PAGES.get(page);
        String pageId = bodyKey.substring(bodyKey.lastIndexOf('.') + 1);
        graphics.drawString(font, Component.translatable("manual.page.title." + pageId), cardX + 14, cardY + 12,
                TAB_COLORS[activeSection()], false);

        Component body = Component.translatable(bodyKey);
        int bodyWidth = cardRight - cardX - 28;
        int bodyY = cardY + 31;
        graphics.drawWordWrap(font, body, cardX + 14, bodyY, bodyWidth, 0xFFE4E0EF);

        if (page == 0) {
            drawCraftingRecipe(graphics, cardY + 91, fragmentIngredients(), new ItemStack(ModRegistries.CROWN_FRAGMENT.get()));
        } else if (page == 1) {
            drawCraftingRecipe(graphics, cardY + 91, assemblyIngredients(), new ItemStack(ModRegistries.UNAWAKENED_CROWN.get()));
        } else if (page == 2) {
            drawRitual(graphics, cardY + 93);
        }
    }

    private void drawCraftingRecipe(GuiGraphics graphics, int top, List<ItemStack> ingredients, ItemStack output) {
        int center = panelX + panelWidth / 2;
        int startX = center - 92;
        for (int i = 0; i < ingredients.size(); i++) {
            int column = i % 3;
            int row = i / 3;
            int slotX = startX + column * 22;
            int slotY = top + row * 22;
            graphics.fill(slotX, slotY, slotX + 20, slotY + 20, 0xFF25243B);
            graphics.fill(slotX, slotY, slotX + 20, slotY + 1, 0xFF6B5B86);
            graphics.fill(slotX, slotY + 19, slotX + 20, slotY + 20, 0xFF6B5B86);
            if (!ingredients.get(i).isEmpty()) graphics.renderItem(ingredients.get(i), slotX + 2, slotY + 2);
        }
        graphics.drawCenteredString(font, Component.literal("›"), center + 25, top + 23, 0xFFFFD980);
        int outputX = center + 57;
        int outputY = top + 23;
        graphics.fill(outputX - 3, outputY - 3, outputX + 23, outputY + 23, 0xFF302847);
        graphics.fill(outputX - 3, outputY - 3, outputX + 23, outputY - 2, 0xFFE3B85E);
        graphics.renderItem(output, outputX + 2, outputY + 2);
        graphics.drawCenteredString(font, Component.translatable("manual.crafting_table"), center, top + 70, 0xFFAAA5C7);
    }

    private List<ItemStack> fragmentIngredients() {
        return List.of(
                new ItemStack(Items.NETHERITE_BLOCK), new ItemStack(Items.NETHER_STAR), new ItemStack(Items.NETHERITE_BLOCK),
                new ItemStack(Items.HEART_OF_THE_SEA), new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.HEART_OF_THE_SEA),
                new ItemStack(Items.NETHERITE_BLOCK), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), new ItemStack(Items.NETHERITE_BLOCK));
    }

    private List<ItemStack> assemblyIngredients() {
        return List.of(
                new ItemStack(ModRegistries.CROWN_FRAGMENT.get()), ItemStack.EMPTY, new ItemStack(ModRegistries.CROWN_FRAGMENT.get()),
                ItemStack.EMPTY, new ItemStack(ModRegistries.CROWN_FRAGMENT.get()), ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
    }

    private void drawRitual(GuiGraphics graphics, int top) {
        int center = panelX + panelWidth / 2;
        graphics.renderItem(new ItemStack(ModRegistries.UNAWAKENED_CROWN.get()), center - 99, top + 8);
        graphics.drawCenteredString(font, Component.translatable("manual.ritual.use_on"), center - 53, top + 13, 0xFFBEB6D2);
        graphics.renderItem(new ItemStack(Items.DRAGON_EGG), center - 19, top + 8);
        graphics.drawCenteredString(font, Component.literal("›"), center + 23, top + 13, 0xFFFFD980);
        graphics.renderItem(new ItemStack(ModRegistries.HYPERCROWN.get()), center + 51, top + 8);
        graphics.drawCenteredString(font, Component.translatable("manual.ritual.result"), center + 91, top + 13, 0xFFC9A5FF);
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = panelY + panelHeight - 30;
        drawNavButton(graphics, panelX + 15, y, 86, Component.translatable("manual.previous"), page > 0,
                mouseX, mouseY);
        drawNavButton(graphics, panelX + panelWidth - 101, y, 86, Component.translatable("manual.next"),
                page < CrownManualItem.PAGES.size() - 1, mouseX, mouseY);
        graphics.drawCenteredString(font, Component.translatable("manual.navigation_hint"), panelX + panelWidth / 2,
                y + 7, 0xFF8E89A8);
    }

    private void drawNavButton(GuiGraphics graphics, int x, int y, int buttonWidth, Component text, boolean enabled,
                               int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + buttonWidth && mouseY >= y && mouseY < y + 20;
        int bg = !enabled ? 0xFF171725 : hovered ? 0xFF43365F : 0xFF2C2642;
        graphics.fill(x, y, x + buttonWidth, y + 20, bg);
        graphics.fill(x, y + 19, x + buttonWidth, y + 20, enabled ? 0xFF8D6BBC : 0xFF373348);
        graphics.drawCenteredString(font, text, x + buttonWidth / 2, y + 6, enabled ? 0xFFE8E0F4 : 0xFF77758A);
    }

    private int activeSection() {
        if (page < 2) return 0;
        if (page == 2) return 1;
        if (page == 3) return 2;
        if (page < 13) return 3;
        if (page < 28) return 4;
        return 5;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        updateLayout();
        int closeX = panelX + panelWidth - 26;
        if (mouseX >= closeX && mouseX < closeX + 16 && mouseY >= panelY + 8 && mouseY < panelY + 24) {
            onClose();
            return true;
        }
        int tabY = panelY + 49;
        int gap = 4;
        int tabWidth = (panelWidth - 28 - gap * (SECTION_KEYS.length - 1)) / SECTION_KEYS.length;
        for (int i = 0; i < SECTION_STARTS.length; i++) {
            int tabX = panelX + 14 + i * (tabWidth + gap);
            if (mouseX >= tabX && mouseX < tabX + tabWidth && mouseY >= tabY && mouseY < tabY + 21) {
                page = SECTION_STARTS[i];
                return true;
            }
        }
        int navY = panelY + panelHeight - 30;
        if (mouseY >= navY && mouseY < navY + 20) {
            if (mouseX >= panelX + 15 && mouseX < panelX + 101 && page > 0) page--;
            else if (mouseX >= panelX + panelWidth - 101 && mouseX < panelX + panelWidth - 15
                    && page < CrownManualItem.PAGES.size() - 1) page++;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263 && page > 0) {
            page--;
            return true;
        }
        if (keyCode == 262 && page < CrownManualItem.PAGES.size() - 1) {
            page++;
            return true;
        }
        if (keyCode == 268) {
            page = 0;
            return true;
        }
        if (keyCode == 269) {
            page = CrownManualItem.PAGES.size() - 1;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
