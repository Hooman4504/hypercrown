package com.hooman.hypercrown.client;

import com.hooman.hypercrown.gem.CatastropheGem;
import com.hooman.hypercrown.network.GemActivationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Small second-step prompt for the two catastrophic gems with the longest reach. */
public final class GemConfirmationScreen extends Screen {
    private final CatastropheGem gem;
    private final boolean offhand;
    private final boolean enhanced;

    private GemConfirmationScreen(CatastropheGem gem, boolean offhand, boolean enhanced) {
        super(Component.translatable("screen.hypercrown.gem_confirm.title"));
        this.gem = gem;
        this.offhand = offhand;
        this.enhanced = enhanced;
    }

    public static void open(CatastropheGem gem, boolean offhand, boolean enhanced) {
        Minecraft.getInstance().setScreen(new GemConfirmationScreen(gem, offhand, enhanced));
    }

    @Override
    protected void init() {
        int center = width / 2;
        int top = height / 2 + 28;
        addRenderableWidget(Button.builder(Component.translatable("screen.hypercrown.gem_confirm.activate"), button -> {
            PacketDistributor.sendToServer(new GemActivationPayload(gem.ordinal(), offhand, enhanced, false));
            onClose();
        }).bounds(center - 106, top, 102, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.hypercrown.gem_confirm.cancel"), button -> onClose())
                .bounds(center + 4, top, 102, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0000000);
        int center = width / 2;
        int top = height / 2 - 34;
        graphics.fill(center - 148, top - 12, center + 148, top + 92, 0xE80D1325);
        graphics.fill(center - 148, top - 12, center + 148, top - 10, 0xFFFFB84F);
        graphics.drawCenteredString(font, Component.translatable("screen.hypercrown.gem_confirm.title"), center, top, 0xFFFFD47B);
        graphics.drawCenteredString(font, Component.translatable("item.hypercrown." + gem.id()), center, top + 18, 0xFFF0ECF7);
        graphics.drawCenteredString(font, Component.translatable("screen.hypercrown.gem_confirm.warning1"), center, top + 38, 0xFFFF7777);
        graphics.drawCenteredString(font, Component.translatable("screen.hypercrown.gem_confirm.warning2"), center, top + 50, 0xFFD5D1E2);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override public boolean isPauseScreen() { return false; }
}
