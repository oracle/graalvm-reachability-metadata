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
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Kafka Streams multi-stream cogroup aggregation for a warehouse stock ledger.
 * Receipts add units, shipments remove units, and physical counts replace the balance, with
 * differently typed event streams updating one queryable stock balance per SKU.
 */
public class WarehouseStockCogroupTest {

    @Test
    @Timeout(60)
    public void combinesMovementsAndPhysicalCountsIntoOneBalance(@TempDir Path stateDirectory) {
        StreamsBuilder builder = new StreamsBuilder();
        KGroupedStream<String, Long> receipts = builder
                .<String, Long>stream("goods-received", Consumed.with(Serdes.String(), Serdes.Long()))
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Long()));
        KGroupedStream<String, Long> shipments = builder
                .<String, Long>stream("goods-shipped", Consumed.with(Serdes.String(), Serdes.Long()))
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Long()));
        KGroupedStream<String, String> counts = builder
                .<String, String>stream("physical-counts", Consumed.with(Serdes.String(), Serdes.String()))
                .groupByKey(Grouped.with(Serdes.String(), Serdes.String()));

        receipts.<Long>cogroup((sku, units, balance) -> balance + units)
                .cogroup(shipments, (sku, units, balance) -> balance - units)
                .cogroup(counts, (sku, countedUnits, balance) -> Long.parseLong(countedUnits))
                .aggregate(() -> 0L,
                        Materialized.<String, Long>as(Stores.inMemoryKeyValueStore("warehouse-stock"))
                                .withKeySerde(Serdes.String()).withValueSerde(Serdes.Long())
                                .withCachingDisabled())
                .toStream()
                .to("stock-balances", Produced.with(Serdes.String(), Serdes.Long()));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "warehouse-stock-cogroup");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        Instant start = Instant.parse("2025-01-01T12:00:00Z");
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties, start)) {
            TestInputTopic<String, Long> deliveries = driver.createInputTopic(
                    "goods-received", new StringSerializer(), new LongSerializer());
            TestInputTopic<String, Long> dispatches = driver.createInputTopic(
                    "goods-shipped", new StringSerializer(), new LongSerializer());
            TestInputTopic<String, String> stocktakes = driver.createInputTopic(
                    "physical-counts", new StringSerializer(), new StringSerializer());
            TestOutputTopic<String, Long> updates = driver.createOutputTopic(
                    "stock-balances", new StringDeserializer(), new LongDeserializer());
            KeyValueStore<String, Long> balances = driver.getKeyValueStore("warehouse-stock");

            deliveries.pipeInput("BOOK", 20L, start);
            dispatches.pipeInput("BOOK", 4L, start.plusSeconds(1));
            deliveries.pipeInput("PEN", 50L, start.plusSeconds(2));
            deliveries.pipeInput("BOOK", 5L, start.plusSeconds(3));
            assertEquals(List.of(KeyValue.pair("BOOK", 20L), KeyValue.pair("BOOK", 16L),
                    KeyValue.pair("PEN", 50L), KeyValue.pair("BOOK", 21L)),
                    updates.readKeyValuesToList());
            assertEquals(Long.valueOf(21L), balances.get("BOOK"));
            assertEquals(Long.valueOf(50L), balances.get("PEN"));

            // A physical count corrects the shared balance rather than contributing a delta.
            stocktakes.pipeInput("BOOK", "18", start.plusSeconds(4));
            dispatches.pipeInput("BOOK", 3L, start.plusSeconds(5));
            deliveries.pipeInput("BOOK", 2L, start.plusSeconds(6));
            dispatches.pipeInput("PEN", 10L, start.plusSeconds(7));
            assertEquals(List.of(KeyValue.pair("BOOK", 18L), KeyValue.pair("BOOK", 15L),
                    KeyValue.pair("BOOK", 17L), KeyValue.pair("PEN", 40L)),
                    updates.readKeyValuesToList());
            assertEquals(Long.valueOf(17L), balances.get("BOOK"));
            assertEquals(Long.valueOf(40L), balances.get("PEN"));

            // The count stream can initialize a previously unseen SKU and record an empty shelf.
            stocktakes.pipeInput("LAMP", "7", start.plusSeconds(8));
            dispatches.pipeInput("LAMP", 2L, start.plusSeconds(9));
            stocktakes.pipeInput("BOOK", "0", start.plusSeconds(10));
            deliveries.pipeInput("BOOK", 6L, start.plusSeconds(11));
            assertEquals(List.of(KeyValue.pair("LAMP", 7L), KeyValue.pair("LAMP", 5L),
                    KeyValue.pair("BOOK", 0L), KeyValue.pair("BOOK", 6L)),
                    updates.readKeyValuesToList());
            assertEquals(Long.valueOf(6L), balances.get("BOOK"));
            assertEquals(Long.valueOf(40L), balances.get("PEN"));
            assertEquals(Long.valueOf(5L), balances.get("LAMP"));
            assertTrue(updates.isEmpty());
        }
    }
}
