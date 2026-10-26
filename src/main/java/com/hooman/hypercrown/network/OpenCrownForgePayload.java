package com.hooman.hypercrown.network;

import com.hooman.hypercrown.HypercrownMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client request to open the forge while the Hypercrown is equipped. */
public record OpenCrownForgePayload() implements CustomPacketPayload {
    public static final Type<OpenCrownForgePayload> TYPE = new Type<>(HypercrownMod.id("open_crown_forge"));
    public static final OpenCrownForgePayload INSTANCE = new OpenCrownForgePayload();
    public static final StreamCodec<ByteBuf, OpenCrownForgePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
