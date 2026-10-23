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
import java.util.Properties;

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
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Kafka Streams event-time tumbling windows and suppression for final per-minute sales totals.
 * A reporting application accepts out-of-order sales within its grace period and publishes each
 * completed minute once, excluding sales that arrive after that minute has closed.
 */
public class FinalWindowSalesTest {

    @Test
    @Timeout(60)
    public void publishesFinalTotalsAfterTheGracePeriod(@TempDir Path stateDirectory) {
        StreamsBuilder builder = new StreamsBuilder();
        builder.<String, Long>stream("sales", Consumed.with(Serdes.String(), Serdes.Long()))
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Long()))
                .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(1), Duration.ofSeconds(10)))
                .reduce(Long::sum,
                        Materialized.<String, Long>as(Stores.inMemoryWindowStore("minute-sales",
                                Duration.ofMinutes(2), Duration.ofMinutes(1), false))
                                .withKeySerde(Serdes.String()).withValueSerde(Serdes.Long())
                                .withCachingDisabled())
                .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
                .toStream()
                .map((window, total) -> KeyValue.pair(window.key(),
                        window.window().startTime() + "/" + window.window().endTime() + "/" + total))
                .to("final-sales", Produced.with(Serdes.String(), Serdes.String()));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "final-window-sales");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        Instant start = Instant.parse("2025-01-01T12:00:00Z");
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties, start)) {
            TestInputTopic<String, Long> sales = driver.createInputTopic(
                    "sales", new StringSerializer(), new LongSerializer());
            TestOutputTopic<String, String> reports = driver.createOutputTopic(
                    "final-sales", new StringDeserializer(), new StringDeserializer());
            WindowStore<String, Long> totals = driver.getWindowStore("minute-sales");

            sales.pipeInput("north", 100L, start);
            sales.pipeInput("north", 250L, start.plusSeconds(20));
            sales.pipeInput("north", 50L, start.plusSeconds(59));
            assertTrue(reports.isEmpty());
            assertEquals(Long.valueOf(400L), totals.fetch("north", start.toEpochMilli()));

            sales.pipeInput("north", 200L, start.plusSeconds(61));
            sales.pipeInput("north", 75L, start.plusSeconds(10));
            assertEquals(Long.valueOf(475L), totals.fetch("north", start.toEpochMilli()));
            assertTrue(reports.isEmpty());

            sales.pipeInput("north", 300L, start.plusSeconds(71));
            assertEquals(KeyValue.pair("north", "2025-01-01T12:00:00Z/2025-01-01T12:01:00Z/475"),
                    reports.readKeyValue());
            assertTrue(reports.isEmpty());

            sales.pipeInput("north", 900L, start.plusSeconds(30));
            assertEquals(Long.valueOf(475L), totals.fetch("north", start.toEpochMilli()));
            assertEquals(Long.valueOf(500L), totals.fetch("north", start.plusSeconds(60).toEpochMilli()));
            assertTrue(reports.isEmpty());

            sales.pipeInput("north", 1L, start.plusSeconds(131));
            assertEquals(KeyValue.pair("north", "2025-01-01T12:01:00Z/2025-01-01T12:02:00Z/500"),
                    reports.readKeyValue());
            assertEquals(Long.valueOf(1L), totals.fetch("north", start.plusSeconds(120).toEpochMilli()));
            assertTrue(reports.isEmpty());
        }
    }
}
