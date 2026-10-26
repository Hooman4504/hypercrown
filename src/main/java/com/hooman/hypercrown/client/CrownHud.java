package com.hooman.hypercrown.client;

import com.hooman.hypercrown.ability.Ability;
import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.dev.DeveloperWorkspace;
import com.hooman.hypercrown.network.ClientCrownState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

/**
 * Compact bottom-left status strip for the crown's powers.
 */
public final class CrownHud {
    private static final int PANEL_WIDTH = 160;
    private static final int TITLE_HEIGHT = 14;
    private static final int ROW_HEIGHT = 17;
    private static final int PANEL_PADDING = 5;
    private static final float HUD_SCALE = 0.8f;

    private CrownHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        renderDeveloperOverlay(g, mc);
        if (!CrownHelper.isCrownActive(mc.player)) return;

        List<Ability> enabled = ClientEvents.displayedAbilities(mc.player);

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int panelWidth = Math.min(PANEL_WIDTH, Math.max(100, screenWidth - 16));
        int panelHeight = TITLE_HEIGHT + enabled.size() * ROW_HEIGHT + PANEL_PADDING * 2;
        int scaledPanelHeight = Math.round(panelHeight * HUD_SCALE);
        int x = 8;
        int y = Math.max(4, screenHeight - 30 - scaledPanelHeight);
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() * 0.002));
        int accent = blend(0xFF8A5BEA, 0xFFFFD86A, pulse);

        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(HUD_SCALE, HUD_SCALE, 1);
        g.fill(0, 0, panelWidth, panelHeight, 0xD5080912);
        g.fill(0, 0, panelWidth, 1, accent);
        g.fill(0, 0, 1, panelHeight, 0xFFE5BB58);
        g.fill(panelWidth - 1, 0, panelWidth, panelHeight, 0x885C4A82);
        g.fill(1, panelHeight - 1, panelWidth - 1, panelHeight, 0x885C4A82);

        boolean hypercrown = CrownHelper.isHypercrown(mc.player);
        Component title = Component.translatable(hypercrown
                ? "hud.hypercrown.title" : "hud.tactical_crown.title");
        if (hypercrown) drawRainbowWaveTitle(g, mc, title, 7, 3);
        else g.drawString(mc.font, title, 7, 3, 0xFFFFE9A5);
        if (hypercrown) {
            Component cycleHint = Component.translatable("hud.hypercrown.cycle_hint");
            g.drawString(mc.font, cycleHint, panelWidth - mc.font.width(cycleHint) - 7, 3, 0xFFB4A4D5);
        }
        g.fill(6, TITLE_HEIGHT, panelWidth - 6, TITLE_HEIGHT + 1, 0x665E5476);

        for (int i = 0; i < enabled.size(); i++)
            drawAbilityRow(g, mc, enabled.get(i), 5, TITLE_HEIGHT + PANEL_PADDING + i * ROW_HEIGHT,
                    panelWidth - 10, accent);
        g.pose().popPose();
    }

    private static void renderDeveloperOverlay(GuiGraphics graphics, Minecraft mc) {
        DeveloperWorkspace.Snapshot state = DeveloperToolClient.snapshot();
        if (state == null || !state.debugEnabled || mc.player == null || mc.level == null) return;
        int x = Math.max(4, mc.getWindow().getGuiScaledWidth() - 178);
        int y = 8;
        graphics.fill(x - 6, y - 5, x + 166, y + 66, 0xC90D1323);
        graphics.fill(x - 6, y - 5, x + 166, y - 3, 0xFFFFB84F);
        graphics.drawString(mc.font, "HYPERCROWN DEBUG", x, y, 0xFFFFD47B, false);
        graphics.drawString(mc.font, mc.level.dimension().location().toString(), x, y + 13, 0xFFE1DDED, false);
        graphics.drawString(mc.font, String.format(java.util.Locale.ROOT, "XYZ %.1f / %.1f / %.1f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ()), x, y + 26, 0xFFBAC4D9, false);
        graphics.drawString(mc.font, "Jobs: " + state.activeParticlePreviews + " particle / " + state.pendingSounds + " sound",
                x, y + 39, 0xFFBAC4D9, false);
        graphics.drawString(mc.font, "Crown: " + (CrownHelper.isCrownActive(mc.player) ? "active" : "inactive"), x, y + 52, 0xFF80D6A2, false);
    }

    private static void drawRainbowWaveTitle(GuiGraphics g, Minecraft mc, Component title, int x, int y) {
        String text = title.getString();
        float hueOffset = (System.currentTimeMillis() % 10_000L) / 10_000.0f;
        long time = System.currentTimeMillis();
        int charX = x;
        for (int i = 0; i < text.length(); i++) {
            String character = text.substring(i, i + 1);
            float hue = (hueOffset + i * 0.075f) % 1.0f;
            int color = 0xFF000000 | Mth.hsvToRgb(hue, 1.0f, 1.0f);
            int waveY = y + Math.round((float) Math.sin(time * 0.003 + i * 0.65) * 1.1f);
            g.drawString(mc.font, character, charX, waveY, color);
            charX += mc.font.width(character);
        }
    }

    private static void drawAbilityRow(GuiGraphics g, Minecraft mc, Ability ability,
                                       int x, int y, int width, int accent) {
        int remaining = ClientCrownState.remaining(ability);
        boolean charging = ability == Ability.ENERGY_BEAM && ClientEvents.isBeamCharging();
        Component label;
        int labelColor;

        if (charging) {
            label = Component.translatable("hud.hypercrown.charging_short",
                    Math.round(ClientEvents.beamChargeProgress() * 100));
            labelColor = 0xFFFFD978;
        } else if (remaining > 0) {
            label = Component.translatable("hud.hypercrown.cooldown_short",
                    String.format(Locale.ROOT, "%.1f", remaining / 20.0f));
            labelColor = 0xFFC9A7FF;
        } else {
            label = Component.translatable("hud.hypercrown.ready");
            labelColor = 0xFF84EDB4;
        }

        g.fill(x, y, x + width, y + ROW_HEIGHT - 1, 0xAA11121F);
        g.fill(x, y, x + 2, y + ROW_HEIGHT - 1, remaining > 0 ? 0xFF9468D7 : accent);
        Component name = Component.translatable("hud.hypercrown.short." + ability.key());
        g.drawString(mc.font, name, x + 5, y + 4, 0xFFE8E2F3);

        String key = ClientSetup.KEYS.get(ability).getTranslatedKeyMessage().getString();
        String status = key + "  " + label.getString();
        int statusWidth = mc.font.width(status);
        g.drawString(mc.font, status, x + width - statusWidth - 5, y + 4, labelColor);
    }

    private static int blend(int from, int to, float amount) {
        int a = Math.round(((from >>> 24) & 0xFF) * (1 - amount) + ((to >>> 24) & 0xFF) * amount);
        int r = Math.round(((from >>> 16) & 0xFF) * (1 - amount) + ((to >>> 16) & 0xFF) * amount);
        int green = Math.round(((from >>> 8) & 0xFF) * (1 - amount) + ((to >>> 8) & 0xFF) * amount);
        int b = Math.round((from & 0xFF) * (1 - amount) + (to & 0xFF) * amount);
        return (a << 24) | (r << 16) | (green << 8) | b;
    }
}
