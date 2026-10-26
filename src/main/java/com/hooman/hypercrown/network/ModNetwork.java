package com.hooman.hypercrown.network;

import com.hooman.hypercrown.ability.Ability;
import com.hooman.hypercrown.ability.ActiveAbilities;
import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.data.CrownPlayerData;
import com.hooman.hypercrown.dev.DeveloperToolService;
import com.hooman.hypercrown.gem.GemActivationHandler;
import com.hooman.hypercrown.forge.CrownForgeHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/** Server particle emissions and shared client visual settings. */
public final class ModNetwork {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(ActivateAbilityPayload.TYPE, ActivateAbilityPayload.STREAM_CODEC, ModNetwork::handleActivate);
        r.playToServer(GemActivationPayload.TYPE, GemActivationPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) GemActivationHandler.activate(player, p);
        }));
        r.playToServer(OpenCrownForgePayload.TYPE, OpenCrownForgePayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) CrownForgeHandler.open(player);
        }));
        r.playToServer(DeveloperToolRequestPayload.TYPE, DeveloperToolRequestPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) DeveloperToolService.handle(player, p.action(), p.data());
        }));
        r.playToClient(SyncCooldownsPayload.TYPE, SyncCooldownsPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientCrownState.update(p.remaining())));
        r.playToClient(GemScreenShakePayload.TYPE, GemScreenShakePayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() ->
                com.hooman.hypercrown.client.GemVisualEvents.shake(p.ticks(), p.strength())));
        r.playToClient(DeveloperToolSnapshotPayload.TYPE, DeveloperToolSnapshotPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() ->
                com.hooman.hypercrown.client.DeveloperToolClient.receive(p.json())));
        r.playToClient(VisualEffectsSyncPayload.TYPE, VisualEffectsSyncPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() ->
                com.hooman.hypercrown.client.DeveloperToolClient.updateVisualEffects(p.json())));
    }

    private static void handleActivate(ActivateAbilityPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (p.ability() < 0 || p.ability() >= Ability.values().length) return; // reject garbage
            ActiveAbilities.handle(sp, Ability.values()[p.ability()], p.sneaking(), p.released());
        });
    }

    public static void syncTo(ServerPlayer sp) {
        CrownPlayerData d = CrownHelper.data(sp);
        long now = sp.getServer().overworld().getGameTime();
        List<Integer> list = new ArrayList<>();
        for (Ability a : Ability.values()) list.add((int) Math.min(Integer.MAX_VALUE, d.remaining(a, now)));
        PacketDistributor.sendToPlayer(sp, new SyncCooldownsPayload(list));
    }
}
