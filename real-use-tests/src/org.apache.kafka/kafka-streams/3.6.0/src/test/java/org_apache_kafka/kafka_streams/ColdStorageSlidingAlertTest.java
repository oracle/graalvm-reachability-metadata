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

import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
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
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Kafka Streams sliding-window aggregation for cold-storage temperature alerts.
 * A monitoring application flags three excessive readings within any ten-second interval,
 * including both endpoints, without mixing rooms or retaining readings outside the interval.
 */
public class ColdStorageSlidingAlertTest {

    @Test
    @Timeout(60)
    public void detectsOverlappingBurstsWithoutFixedWindowAlignment(@TempDir Path stateDirectory) {
        StreamsBuilder builder = new StreamsBuilder();
        builder.<String, Long>stream("room-temperatures", Consumed.with(Serdes.String(), Serdes.Long()))
                .filter((room, temperature) -> temperature > 8L)
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Long()))
                .windowedBy(SlidingWindows.ofTimeDifferenceAndGrace(
                        Duration.ofSeconds(10), Duration.ofSeconds(20)))
                .count(Materialized.<String, Long>as(Stores.inMemoryWindowStore("hot-readings",
                        Duration.ofMinutes(1), Duration.ofSeconds(10), false))
                        .withKeySerde(Serdes.String()).withValueSerde(Serdes.Long())
                        .withCachingDisabled())
                .toStream()
                .filter((window, count) -> count >= 3L)
                .map((window, count) -> KeyValue.pair(window.key() + "/" + window.window().startTime(), count))
                .to("temperature-alerts", Produced.with(Serdes.String(), Serdes.Long()));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "cold-storage-sliding-alert");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        Instant start = Instant.parse("2025-01-01T12:00:00Z");
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties, start)) {
            TestInputTopic<String, Long> temperatures = driver.createInputTopic(
                    "room-temperatures", new StringSerializer(), new LongSerializer());
            TestOutputTopic<String, Long> alerts = driver.createOutputTopic(
                    "temperature-alerts", new StringDeserializer(), new LongDeserializer());
            WindowStore<String, Long> readings = driver.getWindowStore("hot-readings");

            temperatures.pipeInput("room-a", 10L, start);
            temperatures.pipeInput("room-a", 11L, start.plusSeconds(5));
            temperatures.pipeInput("room-a", 4L, start.plusSeconds(6));
            temperatures.pipeInput("room-b", 12L, start.plusSeconds(9));
            assertTrue(alerts.isEmpty());

            // Ten seconds apart is inclusive; the normal reading does not contribute.
            temperatures.pipeInput("room-a", 12L, start.plusSeconds(10));
            assertEquals(List.of(KeyValue.pair("room-a/" + start, 3L)), alerts.readKeyValuesToList());
            assertEquals(Long.valueOf(3L), readings.fetch("room-a", start.toEpochMilli()));

            // One millisecond later, the first reading must leave the overlapping interval.
            temperatures.pipeInput("room-a", 13L, start.plusMillis(10_001));
            assertEquals(List.of(KeyValue.pair("room-a/" + start.plusMillis(1), 3L)),
                    alerts.readKeyValuesToList());
            assertEquals(Long.valueOf(3L), readings.fetch("room-a", start.toEpochMilli()));
            assertEquals(Long.valueOf(3L), readings.fetch("room-a", start.plusMillis(1).toEpochMilli()));

            // A separate burst is detected even though it crosses a fixed ten-second boundary.
            temperatures.pipeInput("room-a", 10L, start.plusSeconds(25));
            temperatures.pipeInput("room-a", 11L, start.plusSeconds(30));
            assertTrue(alerts.isEmpty());
            temperatures.pipeInput("room-a", 12L, start.plusSeconds(35));
            assertEquals(List.of(KeyValue.pair("room-a/" + start.plusSeconds(25), 3L)),
                    alerts.readKeyValuesToList());
            assertEquals(Long.valueOf(3L), readings.fetch("room-a", start.plusSeconds(25).toEpochMilli()));
            assertTrue(alerts.isEmpty());
        }
    }
}
