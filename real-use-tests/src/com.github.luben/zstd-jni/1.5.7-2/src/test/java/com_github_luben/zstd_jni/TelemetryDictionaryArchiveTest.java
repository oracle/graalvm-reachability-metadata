/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdDictCompress;
import com.github.luben.zstd.ZstdDictDecompress;
import com.github.luben.zstd.ZstdDictTrainer;
import com.github.luben.zstd.ZstdException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compresses small JSON records with zstd-jni dictionaries: a `ZstdDictTrainer` learns a
 * dictionary from sample records, `ZstdCompressCtx` and `ZstdDecompressCtx` encode and decode
 * single records with the digested `ZstdDictCompress` and `ZstdDictDecompress`, and the
 * dictionary ID each frame carries routes it back to the dictionary that wrote it.
 *
 * A fleet telemetry service stores every device event as its own compressed frame so that one
 * event can be read without inflating its neighbours. Such records are too small for zstd to
 * find repetitions inside one record, so the service trains a dictionary on recent traffic.
 * When the fleet starts sending a second record shape, the service trains a second dictionary
 * and keeps both: the archive reads the dictionary ID from each stored frame to pick the
 * decoder.
 */
class TelemetryDictionaryArchiveTest {

    private static final int DICTIONARY_BYTES = 16 * 1024;

    private static final int SAMPLE_BUFFER_BYTES = 4 * 1024 * 1024;

    private static final int LEVEL = 6;

    private final ObjectMapper mapper = new ObjectMapper();

    /** One reading a device in the field reports. */
    record TelemetryEvent(String deviceId, String site, String metric, double value, long timestamp,
            List<String> tags) {
    }

    /** One reading a smart power meter reports, the record shape the second dictionary learns. */
    record MeterReading(String meterId, String feeder, int phase, double kilowatts, double voltage,
            long readAt, String firmware) {
    }

    /** Stores records as individual dictionary-compressed frames and decodes them by dictionary ID. */
    static final class EventArchive implements AutoCloseable {

        private final Map<Long, ZstdDictDecompress> decoders = new LinkedHashMap<>();

        private final List<AutoCloseable> owned = new ArrayList<>();

        private final List<byte[]> frames = new ArrayList<>();

        private ZstdCompressCtx writer;

        /// Makes the dictionary the one new records are written with, keeping older ones readable.
        long rollDictionary(byte[] dictionary) {
            long dictionaryId = Zstd.getDictIdFromDict(dictionary);
            ZstdDictCompress compressDictionary = new ZstdDictCompress(dictionary, LEVEL);
            ZstdDictDecompress decompressDictionary = new ZstdDictDecompress(dictionary);
            owned.add(compressDictionary);
            owned.add(decompressDictionary);
            decoders.put(dictionaryId, decompressDictionary);
            if (writer != null) {
                writer.close();
            }
            writer = new ZstdCompressCtx().setLevel(LEVEL).setChecksum(true).loadDict(compressDictionary);
            return dictionaryId;
        }

        int append(byte[] record) {
            frames.add(writer.compress(record));
            return frames.size() - 1;
        }

        byte[] frame(int position) {
            return frames.get(position);
        }

        byte[] read(int position) {
            byte[] frame = frames.get(position);
            ZstdDictDecompress dictionary = decoders.get(Zstd.getDictIdFromFrame(frame));
            assertThat(dictionary).as("dictionary for frame %d", position).isNotNull();
            try (ZstdDecompressCtx reader = new ZstdDecompressCtx()) {
                reader.loadDict(dictionary);
                return reader.decompress(frame, (int) Zstd.getFrameContentSize(frame));
            }
        }

        long storedBytes() {
            return frames.stream().mapToLong(frame -> frame.length).sum();
        }

        @Override
        public void close() throws Exception {
            if (writer != null) {
                writer.close();
            }
            for (AutoCloseable resource : owned) {
                resource.close();
            }
        }
    }

    @Test
    void trainedDictionaryShrinksSingleEventsAndIsRequiredToReadThem() throws Exception {
        List<TelemetryEvent> history = telemetryEvents(new Random(7), 3000);
        byte[] dictionary = train(history.subList(0, 2500));
        List<TelemetryEvent> incoming = history.subList(2500, 3000);

        try (EventArchive archive = new EventArchive()) {
            long dictionaryId = archive.rollDictionary(dictionary);
            assertThat(dictionaryId).isNotZero();
            assertThat(dictionary.length).isLessThanOrEqualTo(DICTIONARY_BYTES);

            long jsonBytes = 0;
            long plainBytes = 0;
            for (TelemetryEvent event : incoming) {
                byte[] json = mapper.writeValueAsBytes(event);
                jsonBytes += json.length;
                plainBytes += Zstd.compress(json, LEVEL).length;
                archive.append(json);
            }
            assertThat(archive.storedBytes() * 2).isLessThan(plainBytes);
            assertThat(archive.storedBytes() * 3).isLessThan(jsonBytes);

            for (int position = 0; position < incoming.size(); position++) {
                byte[] frame = archive.frame(position);
                assertThat(Zstd.getDictIdFromFrame(frame)).isEqualTo(dictionaryId);
                TelemetryEvent restored = mapper.readValue(archive.read(position), TelemetryEvent.class);
                assertThat(restored).isEqualTo(incoming.get(position));
                assertThat(Zstd.getFrameContentSize(frame))
                        .isEqualTo(mapper.writeValueAsBytes(restored).length);
            }

            byte[] firstFrame = archive.frame(0);
            int firstSize = (int) Zstd.getFrameContentSize(firstFrame);
            try (ZstdDecompressCtx withoutDictionary = new ZstdDecompressCtx()) {
                assertThatThrownBy(() -> withoutDictionary.decompress(firstFrame, firstSize))
                        .isInstanceOf(ZstdException.class);
            }
        }
    }

    @Test
    void archiveDecodesFramesOfEveryDictionaryGenerationItHolds() throws Exception {
        List<TelemetryEvent> events = telemetryEvents(new Random(11), 2600);
        List<MeterReading> readings = meterReadings(new Random(13), 2600);

        try (EventArchive archive = new EventArchive()) {
            long eventsDictionary = archive.rollDictionary(train(events.subList(0, 2500)));
            List<Integer> eventPositions = new ArrayList<>();
            for (TelemetryEvent event : events.subList(2500, 2600)) {
                eventPositions.add(archive.append(mapper.writeValueAsBytes(event)));
            }

            long readingsDictionary = archive.rollDictionary(train(readings.subList(0, 2500)));
            assertThat(readingsDictionary).isNotEqualTo(eventsDictionary).isNotZero();
            List<Integer> readingPositions = new ArrayList<>();
            for (MeterReading reading : readings.subList(2500, 2600)) {
                readingPositions.add(archive.append(mapper.writeValueAsBytes(reading)));
            }

            for (int index = 0; index < eventPositions.size(); index++) {
                int position = eventPositions.get(index);
                assertThat(Zstd.getDictIdFromFrame(archive.frame(position))).isEqualTo(eventsDictionary);
                assertThat(mapper.readValue(archive.read(position), TelemetryEvent.class))
                        .isEqualTo(events.get(2500 + index));
            }
            for (int index = 0; index < readingPositions.size(); index++) {
                int position = readingPositions.get(index);
                assertThat(Zstd.getDictIdFromFrame(archive.frame(position))).isEqualTo(readingsDictionary);
                assertThat(mapper.readValue(archive.read(position), MeterReading.class))
                        .isEqualTo(readings.get(2500 + index));
            }
        }
    }

    /// Trains a dictionary on the JSON form of the sample records.
    private byte[] train(List<?> samples) throws IOException {
        ZstdDictTrainer trainer = new ZstdDictTrainer(SAMPLE_BUFFER_BYTES, DICTIONARY_BYTES);
        for (Object sample : samples) {
            assertThat(trainer.addSample(mapper.writeValueAsBytes(sample))).isTrue();
        }
        return trainer.trainSamples();
    }

    private static List<TelemetryEvent> telemetryEvents(Random random, int count) {
        List<String> sites = List.of("berlin-north", "lisbon-docks", "osaka-plant-2", "denver-hangar");
        List<String> metrics = List.of("temperature_celsius", "humidity_percent", "vibration_mm_s",
                "battery_volts");
        List<String> tags = List.of("firmware:4.2.1", "zone:outdoor", "zone:indoor", "priority:low",
                "priority:high", "calibrated");
        List<TelemetryEvent> events = new ArrayList<>(count);
        long timestamp = 1_760_000_000_000L;
        for (int index = 0; index < count; index++) {
            timestamp += 250 + random.nextInt(4000);
            events.add(new TelemetryEvent(
                    "sensor-%05d".formatted(random.nextInt(800)),
                    sites.get(random.nextInt(sites.size())),
                    metrics.get(random.nextInt(metrics.size())),
                    Math.round(random.nextDouble() * 100_000) / 100.0,
                    timestamp,
                    List.of(tags.get(random.nextInt(2) + 1), tags.get(random.nextInt(2) + 3), tags.get(0))));
        }
        return events;
    }

    private static List<MeterReading> meterReadings(Random random, int count) {
        List<String> feeders = List.of("feeder-a17", "feeder-b02", "feeder-c44");
        List<MeterReading> readings = new ArrayList<>(count);
        long readAt = 1_760_500_000_000L;
        for (int index = 0; index < count; index++) {
            readAt += 900_000;
            readings.add(new MeterReading(
                    "meter-%06d".formatted(random.nextInt(50_000)),
                    feeders.get(random.nextInt(feeders.size())),
                    1 + random.nextInt(3),
                    Math.round(random.nextDouble() * 1_500_000) / 1000.0,
                    Math.round((228 + random.nextDouble() * 4) * 10) / 10.0,
                    readAt,
                    random.nextBoolean() ? "kamstrup-2.9.0" : "landis-gyr-7.1.4"));
        }
        return readings;
    }
}
