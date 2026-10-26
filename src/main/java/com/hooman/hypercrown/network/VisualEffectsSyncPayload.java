package com.hooman.hypercrown.network;

import com.hooman.hypercrown.HypercrownMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Small, shared visual-profile update for the client-side crown aura and charge rings. */
public record VisualEffectsSyncPayload(String json) implements CustomPacketPayload {
    public static final Type<VisualEffectsSyncPayload> TYPE = new Type<>(HypercrownMod.id("visual_effects_sync"));
    public static final StreamCodec<ByteBuf, VisualEffectsSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, VisualEffectsSyncPayload::json, VisualEffectsSyncPayload::new);

    public VisualEffectsSyncPayload {
        if (json == null || json.length() > 30_000) throw new IllegalArgumentException("Visual profile payload is too large.");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
