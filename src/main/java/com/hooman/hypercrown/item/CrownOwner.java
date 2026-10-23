package com.hooman.hypercrown.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** Immutable data component: the player who forged this crown. */
public record CrownOwner(UUID id, String name) {
    public static final Codec<CrownOwner> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(CrownOwner::id),
            Codec.STRING.fieldOf("name").forGetter(CrownOwner::name)
    ).apply(i, CrownOwner::new));

    public static final StreamCodec<ByteBuf, CrownOwner> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CrownOwner::id,
            ByteBufCodecs.STRING_UTF8, CrownOwner::name,
            CrownOwner::new);
}
