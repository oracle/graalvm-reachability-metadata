/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.common.serialization.ByteArraySerializer;
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
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Kafka Streams source deserialization error handling for a live sensor dashboard.
 * Malformed binary readings are logged and discarded without replacing the last valid temperature
 * or preventing subsequent readings and device removals from reaching the dashboard.
 */
public class SensorDeserializationRecoveryTest {

    @Test
    @Timeout(60)
    public void preservesValidReadingsAndContinuesAfterMalformedRecords(@TempDir Path stateDirectory) {
        StreamsBuilder builder = new StreamsBuilder();
        builder.table("sensor-readings", Consumed.with(Serdes.String(), Serdes.Long()),
                Materialized.<String, Long>as(Stores.inMemoryKeyValueStore("latest-temperatures"))
                        .withKeySerde(Serdes.String()).withValueSerde(Serdes.Long())
                        .withCachingDisabled())
                .mapValues(temperature -> (temperature >= 800L ? "HOT:" : "OK:") + temperature)
                .toStream()
                .to("sensor-dashboard", Produced.with(Serdes.String(), Serdes.String()));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "sensor-deserialization-recovery");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        properties.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueExceptionHandler.class);
        Instant start = Instant.parse("2025-01-01T12:00:00Z");
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties, start)) {
            TestInputTopic<String, Long> readings = driver.createInputTopic(
                    "sensor-readings", new StringSerializer(), new LongSerializer());
            TestInputTopic<String, byte[]> malformedReadings = driver.createInputTopic(
                    "sensor-readings", new StringSerializer(), new ByteArraySerializer());
            TestOutputTopic<String, String> dashboard = driver.createOutputTopic(
                    "sensor-dashboard", new StringDeserializer(), new StringDeserializer());
            KeyValueStore<String, Long> temperatures = driver.getKeyValueStore("latest-temperatures");

            // Devices publish temperatures in tenths of a degree using Kafka's long wire format.
            readings.pipeInput("oven-1", 450L, start);
            readings.pipeInput("oven-2", 820L, start.plusSeconds(1));
            assertEquals(List.of(KeyValue.pair("oven-1", "OK:450"), KeyValue.pair("oven-2", "HOT:820")),
                    dashboard.readKeyValuesToList());

            // Truncated payloads are not tombstones and must not erase a healthy reading.
            malformedReadings.pipeInput("oven-1", new byte[] {1, 2, 3}, start.plusSeconds(2));
            malformedReadings.pipeInput("oven-3", new byte[0], start.plusSeconds(3));
            assertTrue(dashboard.isEmpty());
            assertEquals(Long.valueOf(450L), temperatures.get("oven-1"));
            assertEquals(Long.valueOf(820L), temperatures.get("oven-2"));
            assertNull(temperatures.get("oven-3"));

            readings.pipeInput("oven-1", 850L, start.plusSeconds(4));
            readings.pipeInput("oven-3", 600L, start.plusSeconds(5));
            readings.pipeInput("oven-2", 790L, start.plusSeconds(6));
            assertEquals(List.of(KeyValue.pair("oven-1", "HOT:850"), KeyValue.pair("oven-3", "OK:600"),
                    KeyValue.pair("oven-2", "OK:790")), dashboard.readKeyValuesToList());
            assertEquals(Long.valueOf(850L), temperatures.get("oven-1"));
            assertEquals(Long.valueOf(790L), temperatures.get("oven-2"));
            assertEquals(Long.valueOf(600L), temperatures.get("oven-3"));

            // A valid null payload explicitly removes a retired device from the dashboard.
            readings.pipeInput("oven-1", (Long) null, start.plusSeconds(7));
            assertEquals(KeyValue.<String, String>pair("oven-1", null), dashboard.readKeyValue());
            assertNull(temperatures.get("oven-1"));
            assertEquals(Long.valueOf(790L), temperatures.get("oven-2"));
            assertEquals(Long.valueOf(600L), temperatures.get("oven-3"));
            assertTrue(dashboard.isEmpty());
        }
    }
}
