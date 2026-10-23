package com.hooman.hypercrown.network;

import com.hooman.hypercrown.ability.Ability;
import com.hooman.hypercrown.ability.ActiveAbilities;
import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.data.CrownPlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/** Particles need no custom packet: ServerLevel#sendParticles broadcasts, and auras are client-side. */
public final class ModNetwork {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(ActivateAbilityPayload.TYPE, ActivateAbilityPayload.STREAM_CODEC, ModNetwork::handleActivate);
        r.playToClient(SyncCooldownsPayload.TYPE, SyncCooldownsPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientCrownState.update(p.remaining())));
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
        long now = sp.level().getGameTime();
        List<Integer> list = new ArrayList<>();
        for (Ability a : Ability.values()) list.add((int) Math.min(Integer.MAX_VALUE, d.remaining(a, now)));
        PacketDistributor.sendToPlayer(sp, new SyncCooldownsPayload(list));
    }
}
