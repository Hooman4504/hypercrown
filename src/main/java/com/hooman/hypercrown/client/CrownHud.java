package com.hooman.hypercrown.client;

import com.hooman.hypercrown.ability.Ability;
import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.network.ClientCrownState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Top-left overlay: active abilities, keybinds and cooldowns. */
public final class CrownHud {
    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !CrownHelper.isCrownActive(mc.player)) return;

        int x = 4, y = 4;
        g.drawString(mc.font, Component.translatable("hud.hypercrown.title"), x, y, 0xFFD700);
        y += 11;
        long passives = java.util.Arrays.stream(Ability.values()).filter(a -> !a.active && HypercrownConfig.enabled(a)).count();
        g.drawString(mc.font, Component.translatable("hud.hypercrown.passives", passives), x, y, 0xC0A060);
        y += 11;
        for (Ability a : Ability.ACTIVES) {
            if (!HypercrownConfig.enabled(a)) continue;
            int rem = ClientCrownState.remaining(a);
            Component line = Component.literal("[").append(ClientSetup.KEYS.get(a).getTranslatedKeyMessage()).append("] ")
                    .append(Component.translatable(a.langKey())).append(" ")
                    .append(rem > 0 ? Component.literal((rem + 19) / 20 + "s") : Component.translatable("hud.hypercrown.ready"));
            g.drawString(mc.font, line, x, y, rem > 0 ? 0xAA66FF : 0x55FF55);
            y += 10;
        }
    }
}
