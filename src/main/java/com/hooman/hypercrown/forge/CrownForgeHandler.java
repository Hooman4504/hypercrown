package com.hooman.hypercrown.forge;

import com.hooman.hypercrown.ability.CrownHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Validates the equipped crown on the server before opening the forge menu. */
public final class CrownForgeHandler {
    private CrownForgeHandler() {}

    public static void open(ServerPlayer player) {
        if (!CrownHelper.isCanonicalHypercrown(player)) return;
        player.openMenu(new SimpleMenuProvider((containerId, inventory, user) ->
                new CrownForgeMenu(containerId, inventory),
                Component.translatable("container.hypercrown.crown_forge")));
    }
}
