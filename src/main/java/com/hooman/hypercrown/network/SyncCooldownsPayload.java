package com.hooman.hypercrown.network;

import com.hooman.hypercrown.HypercrownMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server -> client: remaining cooldown ticks (indexed by Ability ordinal) and whether the worn Hypercrown
 * is the world's canonical crown, which only the server can check.
 */
public record SyncCooldownsPayload(List<Integer> remaining, boolean canonicalHypercrown) implements CustomPacketPayload {
    public static final Type<SyncCooldownsPayload> TYPE = new Type<>(HypercrownMod.id("sync_cooldowns"));
    public static final StreamCodec<ByteBuf, SyncCooldownsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), SyncCooldownsPayload::remaining,
            ByteBufCodecs.BOOL, SyncCooldownsPayload::canonicalHypercrown,
            SyncCooldownsPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
