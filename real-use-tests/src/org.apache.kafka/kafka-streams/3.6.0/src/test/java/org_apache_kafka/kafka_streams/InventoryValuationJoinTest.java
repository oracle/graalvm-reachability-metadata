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
import org.apache.kafka.streams.kstream.KTable;
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
 * Exercises Kafka Streams KTable primary-key joins for a continuously updated inventory valuation.
 * Stock quantities and unit prices arrive independently, and the application publishes and queries
 * each stocked product's current value in cents, removing products when their stock is deleted.
 */
public class InventoryValuationJoinTest {

    @Test
    @Timeout(60)
    public void maintainsValuationsAcrossStockAndPriceChanges(@TempDir Path stateDirectory) {
        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, Long> stock = builder.table("stock-quantities",
                Consumed.with(Serdes.String(), Serdes.Long()),
                Materialized.<String, Long>as(Stores.inMemoryKeyValueStore("stock"))
                        .withKeySerde(Serdes.String()).withValueSerde(Serdes.Long())
                        .withCachingDisabled());
        KTable<String, Long> prices = builder.table("unit-prices",
                Consumed.with(Serdes.String(), Serdes.Long()),
                Materialized.<String, Long>as(Stores.inMemoryKeyValueStore("prices"))
                        .withKeySerde(Serdes.String()).withValueSerde(Serdes.Long())
                        .withCachingDisabled());
        stock.join(prices, (quantity, price) -> quantity * price,
                Materialized.<String, Long>as(Stores.inMemoryKeyValueStore("inventory-values"))
                        .withKeySerde(Serdes.String()).withValueSerde(Serdes.Long())
                        .withCachingDisabled())
                .toStream()
                .to("valuation-updates", Produced.with(Serdes.String(), Serdes.Long()));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "inventory-valuation-join");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        Instant timestamp = Instant.parse("2025-01-01T12:00:00Z");
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties, timestamp)) {
            TestInputTopic<String, Long> quantities = driver.createInputTopic(
                    "stock-quantities", new StringSerializer(), new LongSerializer());
            TestInputTopic<String, Long> unitPrices = driver.createInputTopic(
                    "unit-prices", new StringSerializer(), new LongSerializer());
            TestOutputTopic<String, Long> valuations = driver.createOutputTopic(
                    "valuation-updates", new StringDeserializer(), new LongDeserializer());
            KeyValueStore<String, Long> currentValues = driver.getKeyValueStore("inventory-values");

            quantities.pipeInput("book", 3L, timestamp);
            assertTrue(valuations.isEmpty());
            assertNull(currentValues.get("book"));

            unitPrices.pipeInput("book", 1200L, timestamp.plusSeconds(1));
            quantities.pipeInput("book", 5L, timestamp.plusSeconds(2));
            unitPrices.pipeInput("book", 1000L, timestamp.plusSeconds(3));
            assertEquals(List.of(
                    KeyValue.pair("book", 3600L),
                    KeyValue.pair("book", 6000L),
                    KeyValue.pair("book", 5000L)), valuations.readKeyValuesToList());
            assertEquals(Long.valueOf(5000L), currentValues.get("book"));

            unitPrices.pipeInput("pen", 200L, timestamp.plusSeconds(4));
            assertTrue(valuations.isEmpty());
            quantities.pipeInput("pen", 7L, timestamp.plusSeconds(5));
            assertEquals(KeyValue.pair("pen", 1400L), valuations.readKeyValue());

            quantities.pipeInput("book", (Long) null, timestamp.plusSeconds(6));
            assertEquals(KeyValue.<String, Long>pair("book", null), valuations.readKeyValue());
            assertNull(currentValues.get("book"));
            assertEquals(Long.valueOf(1400L), currentValues.get("pen"));

            quantities.pipeInput("book", 2L, timestamp.plusSeconds(7));
            assertEquals(KeyValue.pair("book", 2000L), valuations.readKeyValue());
            assertEquals(Long.valueOf(2000L), currentValues.get("book"));
            assertEquals(Long.valueOf(1400L), currentValues.get("pen"));
            assertTrue(valuations.isEmpty());
        }
    }
}
