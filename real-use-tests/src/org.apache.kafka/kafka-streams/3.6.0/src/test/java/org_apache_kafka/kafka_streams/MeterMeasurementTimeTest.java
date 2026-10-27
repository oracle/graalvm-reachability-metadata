/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.processor.TimestampExtractor;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Kafka Streams source timestamp extraction for delayed meter measurement uploads.
 * An energy reporting service assigns usage to the measurement hour embedded in each payload,
 * not the Kafka ingestion hour, including out-of-order measurements and exact hour boundaries.
 */
public class MeterMeasurementTimeTest {

    @Test
    @Timeout(60)
    public void billsUsageInTheMeasurementHourInsteadOfTheUploadHour(@TempDir Path stateDirectory) {
        StreamsBuilder builder = new StreamsBuilder();
        builder.<String, String>stream("meter-uploads", Consumed.with(Serdes.String(), Serdes.String())
                        .withTimestampExtractor(new MeasurementTimestampExtractor()))
                .mapValues(value -> Measurement.parse(value).wattHours())
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Long()))
                .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofHours(1), Duration.ofHours(2)))
                .reduce(Long::sum,
                        Materialized.<String, Long>as(Stores.inMemoryWindowStore("hourly-usage",
                                Duration.ofHours(4), Duration.ofHours(1), false))
                                .withKeySerde(Serdes.String()).withValueSerde(Serdes.Long())
                                .withCachingDisabled())
                .toStream()
                .map((hour, usage) -> KeyValue.pair(hour.key() + "/" + hour.window().startTime(), usage))
                .to("hourly-energy-reports", Produced.with(Serdes.String(), Serdes.Long()));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "meter-measurement-time");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        Instant uploadTime = Instant.parse("2025-01-02T08:00:00Z");
        Instant firstHour = Instant.parse("2025-01-01T10:00:00Z");
        Instant secondHour = firstHour.plus(Duration.ofHours(1));
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties, uploadTime)) {
            TestInputTopic<String, String> uploads = driver.createInputTopic(
                    "meter-uploads", new StringSerializer(), new StringSerializer());
            TestOutputTopic<String, Long> reports = driver.createOutputTopic(
                    "hourly-energy-reports", new StringDeserializer(), new LongDeserializer());
            WindowStore<String, Long> usage = driver.getWindowStore("hourly-usage");

            // A gateway reconnects the next day and uploads measurements in a different order.
            uploads.pipeInput("meter-1", "2025-01-01T10:05:00Z|120", uploadTime);
            uploads.pipeInput("meter-1", "2025-01-01T11:05:00Z|200", uploadTime.plusSeconds(1));
            uploads.pipeInput("meter-1", "2025-01-01T10:50:00Z|30", uploadTime.plusSeconds(2));
            uploads.pipeInput("meter-1", "2025-01-01T10:01:00Z|10", uploadTime.plusSeconds(3));
            uploads.pipeInput("meter-2", "2025-01-01T10:30:00Z|70", uploadTime.plusSeconds(4));
            assertEquals(List.of(
                    KeyValue.pair("meter-1/" + firstHour, 120L),
                    KeyValue.pair("meter-1/" + secondHour, 200L),
                    KeyValue.pair("meter-1/" + firstHour, 150L),
                    KeyValue.pair("meter-1/" + firstHour, 160L),
                    KeyValue.pair("meter-2/" + firstHour, 70L)), reports.readKeyValuesToList());

            // A reading exactly on the hour belongs to the new hour, even when uploaded last.
            uploads.pipeInput("meter-1", "2025-01-01T11:00:00Z|25", uploadTime.plusSeconds(5));
            assertEquals(KeyValue.pair("meter-1/" + secondHour, 225L), reports.readKeyValue());
            assertEquals(Long.valueOf(160L), usage.fetch("meter-1", firstHour.toEpochMilli()));
            assertEquals(Long.valueOf(225L), usage.fetch("meter-1", secondHour.toEpochMilli()));
            assertEquals(Long.valueOf(70L), usage.fetch("meter-2", firstHour.toEpochMilli()));
            assertNull(usage.fetch("meter-1", uploadTime.toEpochMilli()));
            assertTrue(reports.isEmpty());
        }
    }

    private static final class MeasurementTimestampExtractor implements TimestampExtractor {
        @Override
        public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
            return Measurement.parse((String) record.value()).measuredAt().toEpochMilli();
        }
    }

    private record Measurement(Instant measuredAt, long wattHours) {
        private static Measurement parse(String value) {
            String[] fields = value.split("\\|", -1);
            return new Measurement(Instant.parse(fields[0]), Long.parseLong(fields[1]));
        }
    }
}
