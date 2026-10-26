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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.processor.Cancellable;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.ContextualProcessor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Kafka Streams Processor API state stores and wall-clock punctuation for shipment status batches.
 * A notification service coalesces repeated shipment updates and periodically publishes only the latest
 * status, even when no new records arrive to advance stream time.
 */
public class ShipmentStatusBatchTest {

    @Test
    @Timeout(60)
    public void publishesOnlyLatestPendingStatusesOnEachFlush(@TempDir Path stateDirectory) {
        StreamsBuilder builder = new StreamsBuilder();
        builder.addStateStore(Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore("pending-statuses"),
                Serdes.String(), Serdes.String()));
        builder.<String, String>stream("shipment-events", Consumed.with(Serdes.String(), Serdes.String()))
                .process(StatusBatchProcessor::new, Named.as("batch-statuses"), "pending-statuses")
                .to("shipment-notifications", Produced.with(Serdes.String(), Serdes.String()));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "shipment-status-batch");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        Instant start = Instant.parse("2025-01-01T12:00:00Z");
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties, start)) {
            TestInputTopic<String, String> events = driver.createInputTopic(
                    "shipment-events", new StringSerializer(), new StringSerializer());
            TestOutputTopic<String, String> notifications = driver.createOutputTopic(
                    "shipment-notifications", new StringDeserializer(), new StringDeserializer());

            events.pipeInput("parcel-1", "ACCEPTED", start);
            events.pipeInput("parcel-2", "ACCEPTED", start.plusSeconds(1));
            events.pipeInput("parcel-1", "IN_TRANSIT", start.plusSeconds(2));
            events.pipeInput("parcel-3", "ACCEPTED", start.plusSeconds(3));
            events.pipeInput("parcel-3", (String) null, start.plusSeconds(4));
            assertTrue(notifications.isEmpty());

            driver.advanceWallClockTime(Duration.ofSeconds(29));
            assertTrue(notifications.isEmpty());
            driver.advanceWallClockTime(Duration.ofSeconds(1));
            assertEquals(Map.of("parcel-1", "IN_TRANSIT", "parcel-2", "ACCEPTED"),
                    notifications.readKeyValuesToMap());
            assertTrue(notifications.isEmpty());

            events.pipeInput("parcel-1", "OUT_FOR_DELIVERY", start.plusSeconds(5));
            events.pipeInput("parcel-1", "DELIVERED", start.plusSeconds(6));
            assertTrue(notifications.isEmpty());
            driver.advanceWallClockTime(Duration.ofSeconds(30));
            assertEquals(List.of(KeyValue.pair("parcel-1", "DELIVERED")),
                    notifications.readKeyValuesToList());

            driver.advanceWallClockTime(Duration.ofSeconds(30));
            assertTrue(notifications.isEmpty());
        }
    }

    private static final class StatusBatchProcessor extends ContextualProcessor<String, String, String, String> {
        private KeyValueStore<String, String> pending;
        private Cancellable flushSchedule;

        @Override
        public void init(ProcessorContext<String, String> context) {
            super.init(context);
            pending = context.getStateStore("pending-statuses");
            flushSchedule = context.schedule(Duration.ofSeconds(30), PunctuationType.WALL_CLOCK_TIME,
                    this::flush);
        }

        @Override
        public void process(Record<String, String> record) {
            if (record.key() == null) {
                return;
            }
            if (record.value() == null) {
                pending.delete(record.key());
            } else {
                pending.put(record.key(), record.value());
            }
        }

        private void flush(long timestamp) {
            List<KeyValue<String, String>> batch = new ArrayList<>();
            try (KeyValueIterator<String, String> entries = pending.all()) {
                while (entries.hasNext()) {
                    batch.add(entries.next());
                }
            }
            for (KeyValue<String, String> entry : batch) {
                context().forward(new Record<>(entry.key, entry.value, timestamp));
                pending.delete(entry.key);
            }
        }

        @Override
        public void close() {
            if (flushSchedule != null) {
                flushSchedule.cancel();
            }
        }
    }
}
