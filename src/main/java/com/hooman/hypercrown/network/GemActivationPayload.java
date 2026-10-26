package com.hooman.hypercrown.network;

import com.hooman.hypercrown.HypercrownMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server request to activate the catastrophe gem currently held in a hand. */
public record GemActivationPayload(int gem, boolean offhand, boolean enhanced, boolean mount) implements CustomPacketPayload {
    public static final Type<GemActivationPayload> TYPE = new Type<>(HypercrownMod.id("activate_gem"));
    public static final StreamCodec<ByteBuf, GemActivationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GemActivationPayload::gem,
            ByteBufCodecs.BOOL, GemActivationPayload::offhand,
            ByteBufCodecs.BOOL, GemActivationPayload::enhanced,
            ByteBufCodecs.BOOL, GemActivationPayload::mount,
            GemActivationPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
