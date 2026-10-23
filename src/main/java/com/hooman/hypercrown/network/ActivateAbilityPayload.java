package com.hooman.hypercrown.network;

import com.hooman.hypercrown.HypercrownMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: a key was pressed (or released, for the charged beam). */
public record ActivateAbilityPayload(int ability, boolean sneaking, boolean released) implements CustomPacketPayload {
    public static final Type<ActivateAbilityPayload> TYPE = new Type<>(HypercrownMod.id("activate_ability"));
    public static final StreamCodec<ByteBuf, ActivateAbilityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ActivateAbilityPayload::ability,
            ByteBufCodecs.BOOL, ActivateAbilityPayload::sneaking,
            ByteBufCodecs.BOOL, ActivateAbilityPayload::released,
            ActivateAbilityPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
