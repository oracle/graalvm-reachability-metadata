/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.EndDirective;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdDirectBufferCompressingStreamNoFinalizer;
import com.github.luben.zstd.ZstdDirectBufferDecompressingStreamNoFinalizer;
import com.github.luben.zstd.ZstdException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compresses off-heap data with the zstd-jni direct `ByteBuffer` API: channel-backed
 * `ZstdDirectBufferCompressingStreamNoFinalizer` and `ZstdDirectBufferDecompressingStreamNoFinalizer`
 * subclasses, frames written into and decoded from a `MappedByteBuffer`, and
 * `ZstdCompressCtx.compressDirectByteBufferStream` with `EndDirective.FLUSH` feeding a peer.
 *
 * A market-data gateway keeps quotes off the Java heap. It journals the feed into segment files
 * through `FileChannel`, keeps the latest quote of every symbol as its own frame in a
 * memory-mapped snapshot so one symbol can be decoded in place, and ships quote batches to a
 * downstream consumer as one long frame that it flushes after every batch.
 */
class OffHeapQuoteFeedTest {

    private static final String[] SYMBOLS = {"ACME", "GLOBX", "INITECH", "UMBRL", "STARK", "WAYNE"};

    private static final int LEVEL = 5;

    private final ObjectMapper mapper = new ObjectMapper();

    /** One top-of-book quote the gateway receives from an exchange. */
    record Quote(String symbol, long sequence, double bid, double ask, int bidSize, int askSize,
            long timestamp, String venue) {
    }

    /** Where one symbol's frame sits inside the memory-mapped snapshot. */
    record Slot(String symbol, int offset, int length, int rawLength) {
    }

    /** Writes a compressed journal segment to a channel whenever the native output buffer fills. */
    static final class SegmentWriter extends ZstdDirectBufferCompressingStreamNoFinalizer {

        private final FileChannel channel;

        private int flushes;

        SegmentWriter(FileChannel channel, ByteBuffer target, int level) throws IOException {
            super(target, level);
            this.channel = channel;
        }

        @Override
        protected ByteBuffer flushBuffer(ByteBuffer toFlush) throws IOException {
            toFlush.flip();
            while (toFlush.hasRemaining()) {
                channel.write(toFlush);
            }
            toFlush.clear();
            flushes++;
            return toFlush;
        }

        int flushes() {
            return flushes;
        }
    }

    /** Reads a compressed journal segment from a channel whenever the native input buffer drains. */
    static final class SegmentReader extends ZstdDirectBufferDecompressingStreamNoFinalizer {

        private final FileChannel channel;

        private int refills;

        SegmentReader(FileChannel channel, ByteBuffer source) {
            super(source);
            this.channel = channel;
        }

        @Override
        protected ByteBuffer refill(ByteBuffer toRefill) {
            toRefill.compact();
            try {
                channel.read(toRefill);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            toRefill.flip();
            refills++;
            return toRefill;
        }

        int refills() {
            return refills;
        }
    }

    @Test
    void journalSegmentRoundTripsThroughChannelBackedDirectStreams(@TempDir Path directory) throws IOException {
        List<Quote> quotes = feed(3_000, 1_000L);
        Path segment = directory.resolve("quotes-000001.zst");
        long rawBytes = 0;

        int flushes;
        try (FileChannel channel = FileChannel.open(segment, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            SegmentWriter writer = new SegmentWriter(channel, ByteBuffer.allocateDirect(4 * 1024), LEVEL);
            ByteBuffer staging = ByteBuffer.allocateDirect(1024);
            for (Quote quote : quotes) {
                byte[] line = jsonLine(quote);
                rawBytes += line.length;
                staging.clear();
                staging.put(line).flip();
                writer.compress(staging);
                assertThat(staging.hasRemaining()).isFalse();
            }
            writer.close();
            flushes = writer.flushes();
        }

        long segmentBytes = Files.size(segment);
        assertThat(flushes).isGreaterThan(1);
        assertThat(segmentBytes).isLessThan(rawBytes / 3);
        byte[] header = new byte[4];
        try (FileChannel channel = FileChannel.open(segment, StandardOpenOption.READ)) {
            channel.read(ByteBuffer.wrap(header));
        }
        assertThat(header).containsExactly(0x28, 0xB5, 0x2F, 0xFD);

        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        int refills;
        try (FileChannel channel = FileChannel.open(segment, StandardOpenOption.READ)) {
            SegmentReader reader = new SegmentReader(channel, ByteBuffer.allocateDirect(1024).flip());
            ByteBuffer block = ByteBuffer.allocateDirect(
                    ZstdDirectBufferDecompressingStreamNoFinalizer.recommendedTargetBufferSize());
            while (reader.hasRemaining()) {
                block.clear();
                reader.read(block);
                block.flip();
                decoded.write(drain(block));
            }
            reader.close();
            refills = reader.refills();
        }

        assertThat(refills).isGreaterThanOrEqualTo((int) (segmentBytes / 1024));
        assertThat((long) decoded.size()).isEqualTo(rawBytes);
        assertThat(parseLines(decoded.toByteArray())).containsExactlyElementsOf(quotes);
    }

    @Test
    void memoryMappedSnapshotDecodesOneSymbolInPlace(@TempDir Path directory) throws IOException {
        List<Quote> latest = feed(SYMBOLS.length * 40, 50_000L).subList(SYMBOLS.length * 39, SYMBOLS.length * 40);
        Path snapshot = directory.resolve("snapshot.bin");
        List<Slot> slots = new ArrayList<>();

        try (FileChannel channel = FileChannel.open(snapshot, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
                ZstdCompressCtx compressor = new ZstdCompressCtx()
                        .setLevel(LEVEL).setChecksum(true).setContentSize(true)) {
            MappedByteBuffer map = channel.map(FileChannel.MapMode.READ_WRITE, 0, 64 * 1024);
            for (Quote quote : latest) {
                byte[] json = mapper.writeValueAsBytes(quote);
                ByteBuffer source = ByteBuffer.allocateDirect(json.length).put(json).flip();
                int offset = map.position();
                int written = compressor.compress(map, source);
                assertThat(map.position()).isEqualTo(offset + written);
                assertThat(source.hasRemaining()).isFalse();
                slots.add(new Slot(quote.symbol(), offset, written, json.length));
            }
            map.force();
        }

        Slot wanted = slots.get(3);
        assertThat(wanted.symbol()).isEqualTo(SYMBOLS[3]);
        try (FileChannel channel = FileChannel.open(snapshot, StandardOpenOption.READ);
                ZstdDecompressCtx decompressor = new ZstdDecompressCtx()) {
            MappedByteBuffer map = channel.map(FileChannel.MapMode.READ_ONLY, 0, 64 * 1024);
            ByteBuffer frame = map.slice(wanted.offset(), wanted.length());
            assertThat(frame.isDirect()).isTrue();
            assertThat(Zstd.getDirectByteBufferFrameContentSize(frame, 0, frame.remaining()))
                    .isEqualTo(wanted.rawLength());

            ByteBuffer target = ByteBuffer.allocateDirect(wanted.rawLength());
            int produced = decompressor.decompress(target, frame);
            assertThat(produced).isEqualTo(wanted.rawLength());
            assertThat(frame.hasRemaining()).isFalse();
            target.flip();
            assertThat(mapper.readValue(drain(target), Quote.class)).isEqualTo(latest.get(3));

            for (Slot slot : slots) {
                ByteBuffer out = ByteBuffer.allocateDirect(slot.rawLength());
                decompressor.decompress(out, map.slice(slot.offset(), slot.length()));
                out.flip();
                assertThat(mapper.readValue(drain(out), Quote.class).symbol()).isEqualTo(slot.symbol());
            }

            ByteBuffer damaged = ByteBuffer.allocateDirect(wanted.length());
            damaged.put(map.slice(wanted.offset(), wanted.length())).flip();
            int middle = wanted.length() / 2;
            damaged.put(middle, (byte) (damaged.get(middle) ^ 0x5A));
            ByteBuffer scratch = ByteBuffer.allocateDirect(wanted.rawLength());
            assertThatThrownBy(() -> decompressor.decompress(scratch, damaged))
                    .isInstanceOf(ZstdException.class);
        }
    }

    @Test
    void flushedBatchesReachTheConsumerBeforeTheFrameEnds() throws IOException {
        List<Quote> quotes = feed(600, 90_000L);
        ByteBuffer wire = ByteBuffer.allocateDirect(256 * 1024);
        ByteBuffer encoderOut = ByteBuffer.allocateDirect(512);
        ByteBuffer decoderOut = ByteBuffer.allocateDirect(2 * 1024);
        List<Quote> received = new ArrayList<>();
        long sentBytes = 0;

        try (ZstdCompressCtx encoder = new ZstdCompressCtx().setLevel(LEVEL).setChecksum(true);
                ZstdDecompressCtx decoder = new ZstdDecompressCtx()) {
            for (int start = 0; start < quotes.size(); start += 100) {
                List<Quote> batch = quotes.subList(start, start + 100);
                ByteArrayOutputStream text = new ByteArrayOutputStream();
                for (Quote quote : batch) {
                    text.write(jsonLine(quote));
                }
                byte[] payload = text.toByteArray();
                sentBytes += payload.length;
                ByteBuffer source = ByteBuffer.allocateDirect(payload.length).put(payload).flip();
                encode(encoder, source, encoderOut, wire, EndDirective.FLUSH);
                assertThat(encoder.getFrameProgression().getIngested()).isEqualTo(sentBytes);

                wire.flip();
                ByteArrayOutputStream arrived = new ByteArrayOutputStream();
                boolean frameEnded = decode(decoder, wire, decoderOut, arrived);
                wire.compact();

                assertThat(frameEnded).isFalse();
                assertThat(arrived.toByteArray()).isEqualTo(payload);
                received.addAll(parseLines(arrived.toByteArray()));
            }

            encode(encoder, ByteBuffer.allocateDirect(0), encoderOut, wire, EndDirective.END);
            wire.flip();
            ByteArrayOutputStream trailer = new ByteArrayOutputStream();
            boolean frameEnded = decode(decoder, wire, decoderOut, trailer);

            assertThat(frameEnded).isTrue();
            assertThat(trailer.size()).isZero();
            assertThat(wire.hasRemaining()).isFalse();
        }

        assertThat(received).containsExactlyElementsOf(quotes);
    }

    /// Encodes the whole source with the directive, moving every produced byte onto the wire.
    private static void encode(ZstdCompressCtx encoder, ByteBuffer source, ByteBuffer out, ByteBuffer wire,
            EndDirective directive) {
        boolean done;
        do {
            out.clear();
            done = encoder.compressDirectByteBufferStream(out, source, directive);
            out.flip();
            wire.put(out);
        } while (!done || source.hasRemaining());
    }

    /// Decodes what is on the wire, returning whether the frame was completed.
    private static boolean decode(ZstdDecompressCtx decoder, ByteBuffer wire, ByteBuffer out,
            ByteArrayOutputStream sink) throws IOException {
        boolean frameEnded;
        boolean outputFull;
        do {
            out.clear();
            frameEnded = decoder.decompressDirectByteBufferStream(out, wire);
            out.flip();
            outputFull = out.limit() == out.capacity();
            sink.write(drain(out));
        } while (!frameEnded && (wire.hasRemaining() || outputFull));
        return frameEnded;
    }

    private List<Quote> feed(int count, long firstSequence) {
        List<Quote> quotes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String symbol = SYMBOLS[i % SYMBOLS.length];
            double mid = 100.0 + (i % SYMBOLS.length) * 25.0 + (i % 17) * 0.05;
            quotes.add(new Quote(symbol, firstSequence + i, mid - 0.01, mid + 0.01, 100 * (1 + i % 9),
                    100 * (1 + i % 7), 1_717_000_000_000L + i * 3L, i % 4 == 0 ? "XNAS" : "XNYS"));
        }
        return quotes;
    }

    private byte[] jsonLine(Quote quote) throws IOException {
        return (mapper.writeValueAsString(quote) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private List<Quote> parseLines(byte[] text) throws IOException {
        List<Quote> quotes = new ArrayList<>();
        for (String line : new String(text, StandardCharsets.UTF_8).split("\n")) {
            if (!line.isEmpty()) {
                quotes.add(mapper.readValue(line, Quote.class));
            }
        }
        return quotes;
    }

    private static byte[] drain(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }
}
