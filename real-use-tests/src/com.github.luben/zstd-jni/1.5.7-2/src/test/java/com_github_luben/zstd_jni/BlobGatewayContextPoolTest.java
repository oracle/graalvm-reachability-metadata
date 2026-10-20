/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdDictCompress;
import com.github.luben.zstd.ZstdDictDecompress;
import com.github.luben.zstd.ZstdException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reuses pooled `ZstdCompressCtx` and `ZstdDecompressCtx` instances across requests with
 * different per-tenant profiles, relying on `reset()` to clear level, checksum, dictionary and
 * magicless settings, and moves data between heap arrays and direct buffers with
 * `compressDirectByteBuffer`, `decompressDirectByteBufferToByteArray` and
 * `decompressByteArrayToDirectByteBuffer`.
 *
 * A blob-storage gateway keeps one native compression context and one decompression context in a
 * pool, because native contexts are expensive to allocate per request. Each request borrows a
 * context, configures it for its tenant (an archive tier at a high level with checksums, a
 * telemetry tier with a dictionary trained by `Zstd.trainFromBuffer`, an edge tier with magicless
 * frames), and returns it after `reset()`. The scenario checks that a reset context produces the
 * same bytes as a brand-new one, that frame headers carry exactly the tenant's options, and that a
 * reset decompression context no longer accepts frames that only the previous tenant's settings
 * could decode.
 */
class BlobGatewayContextPoolTest {

    private static final int HEADER_BYTES = 16;

    @Test
    void pooledContextsServeEveryTenantProfileAndForgetItAfterReset() {
        byte[] dictionary = trainTelemetryDictionary();
        long dictionaryId = Zstd.getDictIdFromDict(dictionary);
        assertThat(dictionaryId).isNotZero();

        try (ZstdDictCompress telemetryCompressDict = new ZstdDictCompress(dictionary, 5);
                ZstdDictDecompress telemetryDecompressDict = new ZstdDictDecompress(dictionary);
                CodecPool pool = new CodecPool()) {
            BlobGateway gateway = new BlobGateway(pool, telemetryCompressDict, telemetryDecompressDict);
            byte[] reference = blob("plain", 6_000);
            byte[] freshPlainFrame;
            try (ZstdCompressCtx fresh = new ZstdCompressCtx()) {
                freshPlainFrame = fresh.compress(reference);
            }

            byte[] archiveBlob = blob("archive", 20_000);
            byte[] archiveFrame = gateway.store(Tenant.ARCHIVE, archiveBlob);
            assertThat(FrameHeader.of(archiveFrame, true).hasChecksum()).isTrue();
            assertThat(FrameHeader.of(archiveFrame, true).dictionaryId()).isZero();
            assertThat(gateway.load(Tenant.ARCHIVE, archiveFrame, archiveBlob.length)).isEqualTo(archiveBlob);

            byte[] afterArchive = gateway.store(Tenant.STANDARD, reference);
            assertThat(afterArchive).isEqualTo(freshPlainFrame);
            assertThat(FrameHeader.of(afterArchive, true).hasChecksum()).isFalse();

            byte[] telemetryBlob = telemetryRecord(new Random(77), 4_242).getBytes(StandardCharsets.UTF_8);
            byte[] telemetryFrame = gateway.store(Tenant.TELEMETRY, telemetryBlob);
            assertThat(Zstd.getDictIdFromFrame(telemetryFrame)).isEqualTo(dictionaryId);
            assertThat(gateway.load(Tenant.TELEMETRY, telemetryFrame, telemetryBlob.length))
                    .isEqualTo(telemetryBlob);

            byte[] afterTelemetry = gateway.store(Tenant.STANDARD, reference);
            assertThat(afterTelemetry).isEqualTo(freshPlainFrame);
            assertThat(Zstd.getDictIdFromFrame(afterTelemetry)).isZero();
            assertThatThrownBy(() -> gateway.load(Tenant.STANDARD, telemetryFrame, telemetryBlob.length))
                    .isInstanceOfSatisfying(ZstdException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(Zstd.errDictionaryWrong()));

            byte[] edgeBlob = blob("edge", 3_000);
            byte[] edgeFrame = gateway.store(Tenant.EDGE, edgeBlob);
            assertThat(littleEndianInt(edgeFrame, 0)).isNotEqualTo(Zstd.magicNumber());
            assertThat(gateway.load(Tenant.EDGE, edgeFrame, edgeBlob.length)).isEqualTo(edgeBlob);

            byte[] afterEdge = gateway.store(Tenant.STANDARD, reference);
            assertThat(littleEndianInt(afterEdge, 0)).isEqualTo(Zstd.magicNumber());
            assertThat(afterEdge).isEqualTo(freshPlainFrame);
            assertThatThrownBy(() -> gateway.load(Tenant.STANDARD, edgeFrame, edgeBlob.length))
                    .isInstanceOfSatisfying(ZstdException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(Zstd.errPrefixUnknown()));
            assertThat(gateway.load(Tenant.STANDARD, afterEdge, reference.length)).isEqualTo(reference);

            assertThat(pool.compressBorrows).isEqualTo(6);
            assertThat(pool.decompressBorrows).isEqualTo(6);
            assertThat(pool.distinctCompressContexts()).isEqualTo(1);
            assertThat(pool.distinctDecompressContexts()).isEqualTo(1);
        }
    }

    @Test
    void networkBuffersMoveBetweenHeapAndDirectMemoryAtOffsets() {
        try (CodecPool pool = new CodecPool()) {
            byte[] upload = blob("upload", 9_000);
            ByteBuffer receiveBuffer = ByteBuffer.allocateDirect(HEADER_BYTES + upload.length);
            byte[] header = Arrays.copyOf("PUT /b/42\n".getBytes(StandardCharsets.US_ASCII), HEADER_BYTES);
            receiveBuffer.put(header).put(upload).flip();

            ByteBuffer staging = ByteBuffer.allocateDirect(HEADER_BYTES + (int) Zstd.compressBound(upload.length));
            int frameLength = pool.withCompressor(ctx -> {
                ctx.setLevel(9).setChecksum(true);
                return ctx.compressDirectByteBuffer(staging, HEADER_BYTES, staging.capacity() - HEADER_BYTES,
                        receiveBuffer, HEADER_BYTES, upload.length);
            });
            assertThat(frameLength).isPositive().isLessThan(upload.length);
            assertThat(receiveBuffer.position()).isZero();
            assertThat(Zstd.getDirectByteBufferFrameContentSize(staging, HEADER_BYTES, frameLength))
                    .isEqualTo(upload.length);

            byte[] stored = new byte[frameLength];
            staging.get(HEADER_BYTES, stored);
            assertThat(FrameHeader.of(stored, true).hasChecksum()).isTrue();

            byte[] response = new byte[4 + upload.length];
            int restored = pool.withDecompressor(ctx -> ctx.decompressDirectByteBufferToByteArray(
                    response, 4, upload.length, staging, HEADER_BYTES, frameLength));
            assertThat(restored).isEqualTo(upload.length);
            assertThat(Arrays.copyOfRange(response, 4, response.length)).isEqualTo(upload);

            ByteBuffer sendBuffer = ByteBuffer.allocateDirect(HEADER_BYTES + upload.length);
            int sent = pool.withDecompressor(ctx -> ctx.decompressByteArrayToDirectByteBuffer(
                    sendBuffer, HEADER_BYTES, upload.length, stored, 0, stored.length));
            assertThat(sent).isEqualTo(upload.length);
            byte[] sentBody = new byte[upload.length];
            sendBuffer.get(HEADER_BYTES, sentBody);
            assertThat(sentBody).isEqualTo(upload);

            stored[stored.length - 1] ^= 0x5A;
            assertThatThrownBy(() -> pool.withDecompressor(ctx -> ctx.decompressByteArrayToDirectByteBuffer(
                    sendBuffer, HEADER_BYTES, upload.length, stored, 0, stored.length)))
                    .isInstanceOfSatisfying(ZstdException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(Zstd.errChecksumWrong()));

            ByteBuffer tooSmall = ByteBuffer.allocateDirect(upload.length / 2);
            stored[stored.length - 1] ^= 0x5A;
            assertThatThrownBy(() -> pool.withDecompressor(ctx -> ctx.decompressByteArrayToDirectByteBuffer(
                    tooSmall, 0, tooSmall.capacity(), stored, 0, stored.length)))
                    .isInstanceOfSatisfying(ZstdException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(Zstd.errDstSizeTooSmall()));

            assertThat(pool.distinctCompressContexts()).isEqualTo(1);
            assertThat(pool.distinctDecompressContexts()).isEqualTo(1);
        }
    }

    private static byte[] trainTelemetryDictionary() {
        Random random = new Random(20_251_019L);
        byte[][] samples = new byte[600][];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = telemetryRecord(random, i).getBytes(StandardCharsets.UTF_8);
        }
        byte[] dictionaryBuffer = new byte[16 * 1024];
        long size = Zstd.trainFromBuffer(samples, dictionaryBuffer);
        assertThat(Zstd.isError(size)).as(() -> Zstd.getErrorName(size)).isFalse();
        return Arrays.copyOf(dictionaryBuffer, (int) size);
    }

    private static String telemetryRecord(Random random, int sequence) {
        String[] regions = {"eu-west-1", "us-east-2", "ap-south-1"};
        String[] metrics = {"cpu.utilization", "heap.used", "gc.pause", "requests.active"};
        StringBuilder record = new StringBuilder();
        int points = 3 + random.nextInt(4);
        for (int i = 0; i < points; i++) {
            record.append("""
                    {"seq":%d,"region":"%s","metric":"%s","value":%d,"unit":"ms","host":"node-%02d"}
                    """.formatted(sequence, regions[random.nextInt(regions.length)],
                    metrics[random.nextInt(metrics.length)], random.nextInt(10_000), random.nextInt(40)));
        }
        return record.toString();
    }

    private static byte[] blob(String kind, int length) {
        Random random = new Random(kind.hashCode());
        StringBuilder text = new StringBuilder();
        while (text.length() < length) {
            text.append(kind).append(" object ").append(random.nextInt(500)).append(" checksum ")
                    .append(Integer.toHexString(random.nextInt())).append('\n');
        }
        return text.substring(0, length).getBytes(StandardCharsets.US_ASCII);
    }

    private static int littleEndianInt(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    /// Storage tiers whose blobs need different compression settings.
    enum Tenant {
        STANDARD,
        ARCHIVE,
        TELEMETRY,
        EDGE
    }

    /// The fields of a zstd frame header that tell which options produced the frame.
    record FrameHeader(boolean hasChecksum, long dictionaryId) {

        static FrameHeader of(byte[] frame, boolean withMagic) {
            int descriptor = frame[withMagic ? 4 : 0] & 0xFF;
            return new FrameHeader((descriptor & 0x04) != 0, withMagic ? Zstd.getDictIdFromFrame(frame) : 0);
        }
    }

    /// A gateway that configures a borrowed context per tenant and hands it back reset.
    static final class BlobGateway {

        private final CodecPool pool;
        private final ZstdDictCompress telemetryCompressDict;
        private final ZstdDictDecompress telemetryDecompressDict;

        BlobGateway(CodecPool pool, ZstdDictCompress telemetryCompressDict,
                ZstdDictDecompress telemetryDecompressDict) {
            this.pool = pool;
            this.telemetryCompressDict = telemetryCompressDict;
            this.telemetryDecompressDict = telemetryDecompressDict;
        }

        byte[] store(Tenant tenant, byte[] blob) {
            return pool.withCompressor(ctx -> {
                switch (tenant) {
                    case ARCHIVE -> ctx.setLevel(19).setChecksum(true);
                    case TELEMETRY -> ctx.loadDict(telemetryCompressDict);
                    case EDGE -> ctx.setLevel(1).setMagicless(true).setContentSize(false);
                    case STANDARD -> {
                    }
                }
                return ctx.compress(blob);
            });
        }

        byte[] load(Tenant tenant, byte[] frame, int originalSize) {
            return pool.withDecompressor(ctx -> {
                switch (tenant) {
                    case TELEMETRY -> ctx.loadDict(telemetryDecompressDict);
                    case EDGE -> ctx.setMagicless(true);
                    case ARCHIVE, STANDARD -> {
                    }
                }
                return ctx.decompress(frame, originalSize);
            });
        }
    }

    /// A single-slot pool per direction, so every request reuses the same native context.
    static final class CodecPool implements AutoCloseable {

        private final BlockingQueue<ZstdCompressCtx> compressors = new ArrayBlockingQueue<>(1);
        private final BlockingQueue<ZstdDecompressCtx> decompressors = new ArrayBlockingQueue<>(1);
        private final List<ZstdCompressCtx> seenCompressors = new ArrayList<>();
        private final List<ZstdDecompressCtx> seenDecompressors = new ArrayList<>();
        int compressBorrows;
        int decompressBorrows;

        CodecPool() {
            compressors.add(new ZstdCompressCtx());
            decompressors.add(new ZstdDecompressCtx());
        }

        <T> T withCompressor(Function<ZstdCompressCtx, T> work) {
            ZstdCompressCtx ctx = borrow(compressors);
            compressBorrows++;
            remember(seenCompressors, ctx);
            return useAndReturn(ctx, work, ZstdCompressCtx::reset, compressors);
        }

        <T> T withDecompressor(Function<ZstdDecompressCtx, T> work) {
            ZstdDecompressCtx ctx = borrow(decompressors);
            decompressBorrows++;
            remember(seenDecompressors, ctx);
            return useAndReturn(ctx, work, ZstdDecompressCtx::reset, decompressors);
        }

        int distinctCompressContexts() {
            return seenCompressors.size();
        }

        int distinctDecompressContexts() {
            return seenDecompressors.size();
        }

        @Override
        public void close() {
            compressors.forEach(ZstdCompressCtx::close);
            decompressors.forEach(ZstdDecompressCtx::close);
        }

        private static <C> C borrow(BlockingQueue<C> queue) {
            C ctx = queue.poll();
            assertThat(ctx).as("pooled context is available").isNotNull();
            return ctx;
        }

        private static <C> void remember(List<C> seen, C ctx) {
            if (seen.stream().noneMatch(known -> known == ctx)) {
                seen.add(ctx);
            }
        }

        private static <C, T> T useAndReturn(C ctx, Function<C, T> work, Consumer<C> reset, BlockingQueue<C> queue) {
            try {
                return work.apply(ctx);
            } finally {
                reset.accept(ctx);
                queue.add(ctx);
            }
        }
    }
}
