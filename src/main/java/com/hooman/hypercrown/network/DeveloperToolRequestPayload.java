package com.hooman.hypercrown.network;

import com.hooman.hypercrown.HypercrownMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client request for a developer workspace operation. The server validates all content and permissions. */
public record DeveloperToolRequestPayload(String action, String data) implements CustomPacketPayload {
    public static final Type<DeveloperToolRequestPayload> TYPE = new Type<>(HypercrownMod.id("developer_tool_request"));
    public static final StreamCodec<ByteBuf, DeveloperToolRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DeveloperToolRequestPayload::action,
            ByteBufCodecs.STRING_UTF8, DeveloperToolRequestPayload::data,
            DeveloperToolRequestPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
