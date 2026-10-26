package com.hooman.hypercrown.network;

import com.hooman.hypercrown.HypercrownMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Server response containing a bounded, validated workspace snapshot. */
public record DeveloperToolSnapshotPayload(String json) implements CustomPacketPayload {
    private static final int MAX_COMPRESSED_BYTES = 1_048_576;
    private static final int MAX_UNCOMPRESSED_BYTES = 1_000_000;
    public static final Type<DeveloperToolSnapshotPayload> TYPE = new Type<>(HypercrownMod.id("developer_tool_snapshot"));
    public static final StreamCodec<ByteBuf, DeveloperToolSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            DeveloperToolSnapshotPayload::encode, DeveloperToolSnapshotPayload::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(ByteBuf buffer, DeveloperToolSnapshotPayload payload) {
        byte[] compressed;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(payload.json.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            compressed = bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not compress developer workspace snapshot.", exception);
        }
        if (compressed.length > MAX_COMPRESSED_BYTES) throw new IllegalArgumentException("Compressed developer workspace snapshot is too large.");
        ByteBufCodecs.VAR_INT.encode(buffer, compressed.length);
        buffer.writeBytes(compressed);
    }

    private static DeveloperToolSnapshotPayload decode(ByteBuf buffer) {
        int length = ByteBufCodecs.VAR_INT.decode(buffer);
        if (length < 0 || length > MAX_COMPRESSED_BYTES || length > buffer.readableBytes())
            throw new IllegalArgumentException("Compressed developer workspace snapshot has an invalid size.");
        byte[] compressed = new byte[length];
        buffer.readBytes(compressed);
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = gzip.read(chunk)) != -1) {
                if (output.size() + read > MAX_UNCOMPRESSED_BYTES)
                    throw new IllegalArgumentException("Developer workspace snapshot exceeds the decompressed size limit.");
                output.write(chunk, 0, read);
            }
            return new DeveloperToolSnapshotPayload(output.toString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not decompress developer workspace snapshot.", exception);
        }
    }
}
