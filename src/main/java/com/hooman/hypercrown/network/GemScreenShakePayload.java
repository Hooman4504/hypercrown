package com.hooman.hypercrown.network;

import com.hooman.hypercrown.HypercrownMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client camera movement used for the Jade earthquake and nearby impacts. */
public record GemScreenShakePayload(int ticks, float strength) implements CustomPacketPayload {
    public static final Type<GemScreenShakePayload> TYPE = new Type<>(HypercrownMod.id("gem_screen_shake"));
    public static final StreamCodec<ByteBuf, GemScreenShakePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GemScreenShakePayload::ticks,
            ByteBufCodecs.FLOAT, GemScreenShakePayload::strength,
            GemScreenShakePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
