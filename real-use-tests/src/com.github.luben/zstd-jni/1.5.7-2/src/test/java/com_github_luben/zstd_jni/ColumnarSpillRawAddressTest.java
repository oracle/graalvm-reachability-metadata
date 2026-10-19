/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compresses Panama off-heap memory through the raw-address `Zstd.compressUnsafe` and
 * `Zstd.decompressUnsafe` API, reading frame headers with
 * `Zstd.getDirectByteBufferFrameContentSize` and turning returned codes into `ZstdException`s.
 *
 * A columnar query engine keeps its column chunks in `MemorySegment`s owned by an `Arena`, so it
 * never copies them onto the heap. Worker threads compress the chunks of one batch straight from
 * native memory into frames in a shared arena; when memory runs low the engine spills the frames
 * to a file, maps the file back as a segment, and inflates single columns from the mapping into a
 * fresh chunk. Every call hands zstd raw addresses and capacities, so the engine checks each
 * result with `Zstd.isError` itself.
 */
class ColumnarSpillRawAddressTest {

    private static final int ROWS = 16 * 1024;

    private static final int LEVEL = 6;

    private static final int INDEX_ENTRY_BYTES = 3 * Long.BYTES;

    /** A compressed column chunk: the frame plus the decoded size the engine must reserve. */
    record EncodedChunk(String column, MemorySegment frame, long rawBytes) {
    }

    /** Compresses and inflates column chunks by handing zstd the addresses of native segments. */
    static final class RawAddressChunkCodec {

        private final Arena frames;

        RawAddressChunkCodec(Arena frames) {
            this.frames = frames;
        }

        EncodedChunk encode(String column, MemorySegment chunk) {
            long bound = Zstd.compressBound(chunk.byteSize());
            MemorySegment out = frames.allocate(bound);
            long written = Zstd.compressUnsafe(out.address(), bound,
                    chunk.address(), chunk.byteSize(), LEVEL, true);
            return new EncodedChunk(column, out.asSlice(0, checked(written)), chunk.byteSize());
        }

        static long declaredSize(MemorySegment frame) {
            ByteBuffer view = frame.asByteBuffer();
            return Zstd.getDirectByteBufferFrameContentSize(view, 0, view.capacity());
        }

        static long inflate(MemorySegment frame, MemorySegment target) {
            long read = Zstd.decompressUnsafe(target.address(), target.byteSize(),
                    frame.address(), frame.byteSize());
            return checked(read);
        }

        static long checked(long result) {
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            return result;
        }
    }

    @Test
    void workersCompressColumnChunksStraightFromNativeMemory() throws Exception {
        try (Arena batch = Arena.ofShared()) {
            List<String> columns = List.of("ts", "price", "qty", "venue");
            List<MemorySegment> chunks = new ArrayList<>();
            for (String column : columns) {
                chunks.add(columnChunk(batch, column));
            }

            RawAddressChunkCodec codec = new RawAddressChunkCodec(batch);
            ExecutorService workers = Executors.newFixedThreadPool(columns.size());
            List<EncodedChunk> encoded = new ArrayList<>();
            try {
                List<Future<EncodedChunk>> pending = new ArrayList<>();
                for (int i = 0; i < columns.size(); i++) {
                    String column = columns.get(i);
                    MemorySegment chunk = chunks.get(i);
                    pending.add(workers.submit(() -> codec.encode(column, chunk)));
                }
                for (Future<EncodedChunk> future : pending) {
                    encoded.add(future.get(30, TimeUnit.SECONDS));
                }
            } finally {
                workers.shutdown();
                assertThat(workers.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            }

            long rawTotal = 0;
            long frameTotal = 0;
            for (int i = 0; i < encoded.size(); i++) {
                EncodedChunk chunk = encoded.get(i);
                assertThat(chunk.column()).isEqualTo(columns.get(i));
                assertThat(RawAddressChunkCodec.declaredSize(chunk.frame())).isEqualTo(chunk.rawBytes());

                MemorySegment restored = batch.allocate(chunk.rawBytes(), Long.BYTES);
                assertThat(RawAddressChunkCodec.inflate(chunk.frame(), restored)).isEqualTo(chunk.rawBytes());
                assertThat(restored.mismatch(chunks.get(i))).isEqualTo(-1L);
                rawTotal += chunk.rawBytes();
                frameTotal += chunk.frame().byteSize();
            }
            assertThat(frameTotal).isLessThan(rawTotal / 4);
        }
    }

    @Test
    void spilledFramesAreMappedBackAndInflatedPerColumn(@TempDir Path spillDir) throws IOException {
        Path spillFile = spillDir.resolve("batch-0007.spill");
        List<String> columns = List.of("ts", "price", "qty");
        MemorySegment originalPrices;
        try (Arena batch = Arena.ofConfined()) {
            RawAddressChunkCodec codec = new RawAddressChunkCodec(batch);
            List<EncodedChunk> encoded = new ArrayList<>();
            for (String column : columns) {
                MemorySegment chunk = columnChunk(batch, column);
                encoded.add(codec.encode(column, chunk));
            }
            originalPrices = MemorySegment.ofArray(columnChunk(batch, "price").toArray(ValueLayout.JAVA_INT));
            writeSpill(spillFile, encoded);
        }

        try (Arena reload = Arena.ofConfined();
                FileChannel channel = FileChannel.open(spillFile, StandardOpenOption.READ)) {
            MemorySegment mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), reload);
            int entries = mapped.get(ValueLayout.JAVA_INT, 0);
            assertThat(entries).isEqualTo(columns.size());

            long base = Integer.BYTES + (long) columns.indexOf("price") * INDEX_ENTRY_BYTES;
            long priceOffset = mapped.get(ValueLayout.JAVA_LONG_UNALIGNED, base);
            long priceLength = mapped.get(ValueLayout.JAVA_LONG_UNALIGNED, base + Long.BYTES);
            long priceRaw = mapped.get(ValueLayout.JAVA_LONG_UNALIGNED, base + 2L * Long.BYTES);
            MemorySegment priceFrame = mapped.asSlice(priceOffset, priceLength);
            assertThat(RawAddressChunkCodec.declaredSize(priceFrame)).isEqualTo(priceRaw);

            MemorySegment prices = reload.allocate(priceRaw, Integer.BYTES);
            assertThat(RawAddressChunkCodec.inflate(priceFrame, prices)).isEqualTo((long) ROWS * Integer.BYTES);
            assertThat(prices.mismatch(originalPrices)).isEqualTo(-1L);
            assertThat(prices.getAtIndex(ValueLayout.JAVA_INT, 0)).isEqualTo(price(0));
            assertThat(prices.getAtIndex(ValueLayout.JAVA_INT, ROWS - 1)).isEqualTo(price(ROWS - 1));
        }
    }

    @Test
    void rawAddressCallsReportZstdErrorCodes() {
        try (Arena arena = Arena.ofConfined()) {
            RawAddressChunkCodec codec = new RawAddressChunkCodec(arena);
            MemorySegment timestamps = columnChunk(arena, "ts");
            EncodedChunk chunk = codec.encode("ts", timestamps);

            MemorySegment halfChunk = arena.allocate(chunk.rawBytes() / 2);
            long tooSmall = Zstd.decompressUnsafe(halfChunk.address(), halfChunk.byteSize(),
                    chunk.frame().address(), chunk.frame().byteSize());
            assertThat(Zstd.isError(tooSmall)).isTrue();
            assertThat(Zstd.getErrorCode(tooSmall)).isEqualTo(Zstd.errDstSizeTooSmall());

            MemorySegment damaged = arena.allocate(chunk.frame().byteSize());
            damaged.copyFrom(chunk.frame());
            long lastByte = damaged.byteSize() - 1;
            damaged.set(ValueLayout.JAVA_BYTE, lastByte,
                    (byte) (damaged.get(ValueLayout.JAVA_BYTE, lastByte) ^ 0x5A));
            MemorySegment target = arena.allocate(chunk.rawBytes());
            assertThatThrownBy(() -> RawAddressChunkCodec.inflate(damaged, target))
                    .isInstanceOfSatisfying(ZstdException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(Zstd.errChecksumWrong()));

            MemorySegment notAFrame = arena.allocate(64);
            notAFrame.fill((byte) 'x');
            assertThatThrownBy(() -> RawAddressChunkCodec.inflate(notAFrame, target))
                    .isInstanceOfSatisfying(ZstdException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(Zstd.errPrefixUnknown()));

            byte[] noise = new byte[4096];
            new Random(11).nextBytes(noise);
            MemorySegment noisy = arena.allocate(noise.length);
            noisy.copyFrom(MemorySegment.ofArray(noise));
            MemorySegment tightSlot = arena.allocate(noise.length / 8);
            long overflow = Zstd.compressUnsafe(tightSlot.address(), tightSlot.byteSize(),
                    noisy.address(), noisy.byteSize(), LEVEL);
            assertThat(Zstd.isError(overflow)).isTrue();
            assertThat(Zstd.getErrorCode(overflow)).isEqualTo(Zstd.errDstSizeTooSmall());

            assertThat(RawAddressChunkCodec.inflate(chunk.frame(), target)).isEqualTo(chunk.rawBytes());
            assertThat(target.mismatch(timestamps)).isEqualTo(-1L);
        }
    }

    private static void writeSpill(Path spillFile, List<EncodedChunk> encoded) throws IOException {
        int indexBytes = Integer.BYTES + encoded.size() * INDEX_ENTRY_BYTES;
        ByteBuffer index = ByteBuffer.allocate(indexBytes).order(ByteOrder.nativeOrder());
        index.putInt(encoded.size());
        long offset = indexBytes;
        for (EncodedChunk chunk : encoded) {
            index.putLong(offset).putLong(chunk.frame().byteSize()).putLong(chunk.rawBytes());
            offset += chunk.frame().byteSize();
        }
        index.flip();
        try (FileChannel channel = FileChannel.open(spillFile, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            writeFully(channel, index);
            for (EncodedChunk chunk : encoded) {
                writeFully(channel, chunk.frame().asByteBuffer());
            }
        }
    }

    private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static MemorySegment columnChunk(Arena arena, String column) {
        return switch (column) {
            case "ts" -> {
                MemorySegment chunk = arena.allocate(ValueLayout.JAVA_LONG, ROWS);
                for (int row = 0; row < ROWS; row++) {
                    chunk.setAtIndex(ValueLayout.JAVA_LONG, row, 1_760_000_000_000L + row * 250L + row % 7);
                }
                yield chunk;
            }
            case "price" -> {
                MemorySegment chunk = arena.allocate(ValueLayout.JAVA_INT, ROWS);
                for (int row = 0; row < ROWS; row++) {
                    chunk.setAtIndex(ValueLayout.JAVA_INT, row, price(row));
                }
                yield chunk;
            }
            case "qty" -> {
                MemorySegment chunk = arena.allocate(ValueLayout.JAVA_SHORT, ROWS);
                for (int row = 0; row < ROWS; row++) {
                    chunk.setAtIndex(ValueLayout.JAVA_SHORT, row, (short) (100 * (1 + row % 5)));
                }
                yield chunk;
            }
            case "venue" -> {
                MemorySegment chunk = arena.allocate(ValueLayout.JAVA_BYTE, ROWS);
                byte[] venues = {'X', 'N', 'A', 'B'};
                for (int row = 0; row < ROWS; row++) {
                    chunk.set(ValueLayout.JAVA_BYTE, row, venues[(row / 64) % venues.length]);
                }
                yield chunk;
            }
            default -> throw new IllegalArgumentException("unknown column " + column);
        };
    }

    private static int price(int row) {
        return 18_250 + (row / 32) % 40 - 20;
    }
}
