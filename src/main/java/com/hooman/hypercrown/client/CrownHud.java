package com.hooman.hypercrown.client;

import com.hooman.hypercrown.ability.Ability;
import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.network.ClientCrownState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small bottom-center status strip for the crown's powers. */
public final class CrownHud {
    private static final int MAX_WIDTH = 292;
    private static final int PANEL_HEIGHT = 43;

    private CrownHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !CrownHelper.isCrownActive(mc.player)) return;

        List<Ability> enabled = new ArrayList<>();
        for (Ability ability : Ability.ACTIVES) if (HypercrownConfig.enabled(ability)) enabled.add(ability);

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int panelWidth = Math.max(88, Math.min(MAX_WIDTH, screenWidth - 16));
        int x = (screenWidth - panelWidth) / 2;
        int y = Math.max(4, screenHeight - PANEL_HEIGHT - 62);
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() * 0.002));
        int accent = blend(0xFF8A5BEA, 0xFFFFD86A, pulse);

        g.fill(x, y, x + panelWidth, y + PANEL_HEIGHT, 0xD5080912);
        g.fill(x, y, x + panelWidth, y + 1, accent);
        g.fill(x, y, x + 1, y + PANEL_HEIGHT, 0xFFE5BB58);
        g.fill(x + panelWidth - 1, y, x + panelWidth, y + PANEL_HEIGHT, 0x885C4A82);
        g.fill(x + 1, y + PANEL_HEIGHT - 1, x + panelWidth - 1, y + PANEL_HEIGHT, 0x885C4A82);

        g.drawString(mc.font, Component.translatable("hud.hypercrown.title"), x + 7, y + 3, 0xFFFFE9A5);
        long passives = java.util.Arrays.stream(Ability.values())
                .filter(a -> !a.active && HypercrownConfig.enabled(a)).count();
        Component passiveLabel = Component.translatable("hud.hypercrown.passives_short", passives);
        g.drawString(mc.font, passiveLabel, x + panelWidth - mc.font.width(passiveLabel) - 7,
                y + 3, 0xFFBDB0D5);
        g.fill(x + 6, y + 13, x + panelWidth - 6, y + 14, 0x665E5476);

        if (enabled.isEmpty()) return;
        int gap = 3;
        int innerWidth = panelWidth - 12 - gap * (enabled.size() - 1);
        int cardWidth = innerWidth / enabled.size();
        int remainder = innerWidth % enabled.size();
        int cardX = x + 6;
        int cardY = y + 16;
        for (int i = 0; i < enabled.size(); i++) {
            int width = cardWidth + (i < remainder ? 1 : 0);
            drawAbilityCard(g, mc, enabled.get(i), cardX, cardY, width, accent);
            cardX += width + gap;
        }
    }

    private static void drawAbilityCard(GuiGraphics g, Minecraft mc, Ability ability,
                                       int x, int y, int width, int accent) {
        int remaining = ClientCrownState.remaining(ability);
        boolean charging = ability == Ability.ENERGY_BEAM && ClientEvents.isBeamCharging();
        int total = HypercrownConfig.cooldownTicks(ability);
        float progress;
        Component label;
        int labelColor;

        if (charging) {
            progress = ClientEvents.beamChargeProgress();
            label = Component.translatable("hud.hypercrown.charging_short", Math.round(progress * 100));
            labelColor = 0xFFFFD978;
        } else if (remaining > 0) {
            progress = total > 0 ? Math.min(1.0f, remaining / (float) total) : 0.0f;
            label = Component.translatable("hud.hypercrown.cooldown_short",
                    String.format(Locale.ROOT, "%.1f", remaining / 20.0f));
            labelColor = 0xFFC9A7FF;
        } else {
            progress = 1.0f;
            label = Component.translatable("hud.hypercrown.ready");
            labelColor = 0xFF84EDB4;
        }

        g.fill(x, y, x + width, y + 25, 0xAA11121F);
        g.fill(x, y, x + 2, y + 25, remaining > 0 ? 0xFF9468D7 : accent);
        Component name = Component.translatable("hud.hypercrown.short." + ability.key());
        g.drawString(mc.font, name, x + 5, y + 2, 0xFFE8E2F3);

        String key = ClientSetup.KEYS.get(ability).getTranslatedKeyMessage().getString();
        int keyWidth = Math.min(Math.max(16, mc.font.width(key) + 6), Math.max(16, width - 9));
        int keyX = x + width - keyWidth - 4;
        g.fill(keyX, y + 2, x + width - 3, y + 12, 0xFF29233B);
        g.drawString(mc.font, key, keyX + (keyWidth - mc.font.width(key)) / 2, y + 3, 0xFFD2C0F7);

        g.drawString(mc.font, label, x + 5, y + 12, labelColor);
        int barLeft = x + 5;
        int barRight = x + width - 4;
        int barY = y + 21;
        g.fill(barLeft, barY, barRight, barY + 2, 0xFF292536);
        int fillRight = barLeft + Math.round((barRight - barLeft) * progress);
        if (fillRight > barLeft) g.fill(barLeft, barY, fillRight, barY + 2,
                charging ? 0xFFFFCA61 : remaining > 0 ? 0xFF9B6BE8 : 0xFFE4C66D);
    }

    private static int blend(int from, int to, float amount) {
        int a = Math.round(((from >>> 24) & 0xFF) * (1 - amount) + ((to >>> 24) & 0xFF) * amount);
        int r = Math.round(((from >>> 16) & 0xFF) * (1 - amount) + ((to >>> 16) & 0xFF) * amount);
        int green = Math.round(((from >>> 8) & 0xFF) * (1 - amount) + ((to >>> 8) & 0xFF) * amount);
        int b = Math.round((from & 0xFF) * (1 - amount) + (to & 0xFF) * amount);
        return (a << 24) | (r << 16) | (green << 8) | b;
    }
}
