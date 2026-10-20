/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdDictCompress;
import com.github.luben.zstd.ZstdDictDecompress;
import com.github.luben.zstd.ZstdDictTrainer;
import com.github.luben.zstd.ZstdIOException;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Replays one zstd stream whose frames were written under several dictionaries: a
 * `ZstdInputStream` with `setRefMultipleDDicts(true)` holds every referenced
 * `ZstdDictDecompress` and picks, frame by frame, the one whose ID the frame header names,
 * while `ZstdOutputStream.setDict(ZstdDictCompress)` writes each batch frame.
 *
 * An order service appends batches of newline-delimited JSON events to a segment file, one
 * zstd frame per batch, each compressed with the dictionary of the event schema the batch
 * carries. During a schema rollout old and new producers append to the same segment, so the
 * frames of two dictionary generations interleave. The replay job decodes the whole segment
 * as one stream instead of splitting it into frames, which works only when the reader keeps
 * all dictionaries referenced at once.
 */
class RollingDictionarySegmentReplayTest {

    private static final int DICTIONARY_BYTES = 8 * 1024;

    private static final int SAMPLE_BUFFER_BYTES = 2 * 1024 * 1024;

    private static final int LEVEL = 5;

    private static final int BATCH_SIZE = 25;

    private final ObjectMapper mapper = new ObjectMapper();

    /** An order event in the first schema generation. */
    record OrderPlacedV1(String orderId, String customer, String sku, int quantity, long placedAt) {
    }

    /** The same event in the second schema generation, which adds pricing and a channel. */
    record OrderPlacedV2(String orderId, String customerId, List<String> skus, long totalCents,
            String currency, String channel, long placedAt) {
    }

    /** One dictionary generation, digested once for writers and once for readers. */
    record DictionaryGeneration(long id, ZstdDictCompress writer, ZstdDictDecompress reader)
            implements AutoCloseable {

        static DictionaryGeneration of(byte[] dictionary) {
            return new DictionaryGeneration(Zstd.getDictIdFromDict(dictionary),
                    new ZstdDictCompress(dictionary, LEVEL), new ZstdDictDecompress(dictionary));
        }

        @Override
        public void close() {
            writer.close();
            reader.close();
        }
    }

    /** Appends batches to a segment file, one dictionary-compressed frame per batch. */
    static final class SegmentWriter {

        private final Path segment;

        private final List<Long> frameOffsets = new ArrayList<>();

        SegmentWriter(Path segment) {
            this.segment = segment;
        }

        void appendBatch(DictionaryGeneration generation, List<String> lines) throws IOException {
            frameOffsets.add(Files.exists(segment) ? Files.size(segment) : 0L);
            try (OutputStream file = new FileOutputStream(segment.toFile(), true);
                    ZstdOutputStream frame = new ZstdOutputStream(file)) {
                frame.setLevel(LEVEL).setChecksum(true).setDict(generation.writer());
                for (String line : lines) {
                    frame.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        List<Long> frameOffsets() {
            return frameOffsets;
        }
    }

    /// Reads every line of the segment, referencing the given dictionaries on one stream.
    private static List<String> replay(Path segment, boolean multipleDictionaries,
            List<String> salvaged, DictionaryGeneration... generations) throws IOException {
        try (ZstdInputStream zstd = new ZstdInputStream(Files.newInputStream(segment))) {
            zstd.setRefMultipleDDicts(multipleDictionaries);
            for (DictionaryGeneration generation : generations) {
                zstd.setDict(generation.reader());
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(zstd, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    salvaged.add(line);
                }
            }
        }
        return salvaged;
    }

    @Test
    void replaysInterleavedFramesOfTwoDictionaryGenerationsAsOneStream(@TempDir Path directory) throws Exception {
        List<String> v1Events = toJson(ordersV1(new Random(21), 2100));
        List<String> v2Events = toJson(ordersV2(new Random(22), 2100));
        Path segment = directory.resolve("orders-000017.zst");

        try (DictionaryGeneration v1 = DictionaryGeneration.of(train(v1Events.subList(0, 2000)));
                DictionaryGeneration v2 = DictionaryGeneration.of(train(v2Events.subList(0, 2000)))) {
            assertThat(v1.id()).isNotZero();
            assertThat(v2.id()).isNotZero().isNotEqualTo(v1.id());

            SegmentWriter writer = new SegmentWriter(segment);
            List<String> expected = new ArrayList<>();
            List<Long> expectedDictionaryIds = new ArrayList<>();
            for (int batch = 0; batch < 4; batch++) {
                List<String> oldProducer = batch(v1Events, 2000, batch);
                List<String> newProducer = batch(v2Events, 2000, batch);
                writer.appendBatch(v1, oldProducer);
                writer.appendBatch(v2, newProducer);
                expected.addAll(oldProducer);
                expected.addAll(newProducer);
                expectedDictionaryIds.add(v1.id());
                expectedDictionaryIds.add(v2.id());
            }

            byte[] stored = Files.readAllBytes(segment);
            List<Long> headerDictionaryIds = new ArrayList<>();
            for (long offset : writer.frameOffsets()) {
                byte[] header = Arrays.copyOfRange(stored, (int) offset, Math.min(stored.length, (int) offset + 18));
                headerDictionaryIds.add(Zstd.getDictIdFromFrame(header));
            }
            assertThat(headerDictionaryIds).isEqualTo(expectedDictionaryIds);
            long plainBytes = expected.stream().mapToLong(line -> line.length() + 1L).sum();
            assertThat(stored.length * 3L).isLessThan(plainBytes);

            List<String> replayed = replay(segment, true, new ArrayList<>(), v1, v2);
            assertThat(replayed).isEqualTo(expected);
            assertThat(mapper.readValue(replayed.get(0), OrderPlacedV1.class))
                    .isEqualTo(mapper.readValue(v1Events.get(2000), OrderPlacedV1.class));
            assertThat(mapper.readValue(replayed.get(BATCH_SIZE), OrderPlacedV2.class))
                    .isEqualTo(mapper.readValue(v2Events.get(2000), OrderPlacedV2.class));

            List<String> reversedReferenceOrder = replay(segment, true, new ArrayList<>(), v2, v1);
            assertThat(reversedReferenceOrder).isEqualTo(expected);
        }
    }

    @Test
    void singleDictionaryReaderKeepsOnlyTheLastReferencedGeneration(@TempDir Path directory) throws Exception {
        List<String> v1Events = toJson(ordersV1(new Random(31), 2050));
        List<String> v2Events = toJson(ordersV2(new Random(32), 2050));
        Path segment = directory.resolve("orders-000018.zst");

        try (DictionaryGeneration v1 = DictionaryGeneration.of(train(v1Events.subList(0, 2000)));
                DictionaryGeneration v2 = DictionaryGeneration.of(train(v2Events.subList(0, 2000)))) {
            SegmentWriter writer = new SegmentWriter(segment);
            writer.appendBatch(v1, batch(v1Events, 2000, 0));
            writer.appendBatch(v2, batch(v2Events, 2000, 0));

            List<String> salvaged = new ArrayList<>();
            assertThatThrownBy(() -> replay(segment, false, salvaged, v1, v2))
                    .isInstanceOfSatisfying(ZstdIOException.class, error ->
                            assertThat(error.getErrorCode()).isEqualTo(Zstd.errDictionaryWrong()));
            assertThat(salvaged).isEmpty();
        }
    }

    @Test
    void replayStopsAtTheFirstFrameOfAnUnknownGeneration(@TempDir Path directory) throws Exception {
        List<String> v1Events = toJson(ordersV1(new Random(41), 2050));
        List<String> v2Events = toJson(ordersV2(new Random(42), 2050));
        Path segment = directory.resolve("orders-000019.zst");

        try (DictionaryGeneration v1 = DictionaryGeneration.of(train(v1Events.subList(0, 2000)));
                DictionaryGeneration v2 = DictionaryGeneration.of(train(v2Events.subList(0, 2000)))) {
            SegmentWriter writer = new SegmentWriter(segment);
            List<String> known = batch(v1Events, 2000, 0);
            writer.appendBatch(v1, known);
            writer.appendBatch(v2, batch(v2Events, 2000, 0));
            writer.appendBatch(v1, batch(v1Events, 2000, 1));

            List<String> salvaged = new ArrayList<>();
            assertThatThrownBy(() -> replay(segment, true, salvaged, v1))
                    .isInstanceOfSatisfying(ZstdIOException.class, error ->
                            assertThat(error.getErrorCode()).isEqualTo(Zstd.errDictionaryWrong()));
            assertThat(salvaged.size()).isLessThanOrEqualTo(known.size());
            assertThat(salvaged).isEqualTo(known.subList(0, salvaged.size()));

            assertThat(replay(segment, true, new ArrayList<>(), v1, v2)).hasSize(3 * BATCH_SIZE);
        }
    }

    /// Returns the `index`-th batch of lines after the first `offset` training samples.
    private static List<String> batch(List<String> lines, int offset, int index) {
        int from = offset + index * BATCH_SIZE;
        return lines.subList(from, from + BATCH_SIZE);
    }

    /// Trains a dictionary on sample event lines.
    private static byte[] train(List<String> samples) {
        ZstdDictTrainer trainer = new ZstdDictTrainer(SAMPLE_BUFFER_BYTES, DICTIONARY_BYTES);
        for (String sample : samples) {
            assertThat(trainer.addSample(sample.getBytes(StandardCharsets.UTF_8))).isTrue();
        }
        return trainer.trainSamples();
    }

    private List<String> toJson(List<?> events) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Object event : events) {
            lines.add(mapper.writeValueAsString(event));
        }
        return lines;
    }

    private static List<OrderPlacedV1> ordersV1(Random random, int count) {
        String[] skus = {"BOOK-0001", "MUG-0042", "LAMP-0310", "DESK-1200", "PEN-0007"};
        List<OrderPlacedV1> orders = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            orders.add(new OrderPlacedV1("ord-" + (100000 + index), "customer-" + random.nextInt(400),
                    skus[random.nextInt(skus.length)], 1 + random.nextInt(5),
                    1_760_000_000_000L + index * 1_000L + random.nextInt(1_000)));
        }
        return orders;
    }

    private static List<OrderPlacedV2> ordersV2(Random random, int count) {
        String[] skus = {"BOOK-0001", "MUG-0042", "LAMP-0310", "DESK-1200", "PEN-0007"};
        String[] channels = {"web", "ios", "android", "pos"};
        String[] currencies = {"EUR", "USD", "GBP"};
        List<OrderPlacedV2> orders = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            List<String> basket = new ArrayList<>();
            for (int item = 0; item < 1 + random.nextInt(3); item++) {
                basket.add(skus[random.nextInt(skus.length)]);
            }
            orders.add(new OrderPlacedV2("ord-" + (500000 + index), "c-" + random.nextInt(400), basket,
                    random.nextInt(50_000), currencies[random.nextInt(currencies.length)],
                    channels[random.nextInt(channels.length)],
                    1_770_000_000_000L + index * 1_000L + random.nextInt(1_000)));
        }
        return orders;
    }
}
