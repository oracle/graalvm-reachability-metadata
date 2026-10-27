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
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Kafka Streams GlobalKTable foreign-key lookup enrichment against a product catalog.
 * An order pricing service looks up each line's SKU while preserving its order ID, using catalog
 * changes only for subsequent orders and explicitly reporting unavailable products.
 */
public class ProductCatalogEnrichmentTest {

    @Test
    @Timeout(60)
    public void pricesNewOrdersWithTheCurrentCatalog(@TempDir Path stateDirectory) {
        StreamsBuilder builder = new StreamsBuilder();
        GlobalKTable<String, Long> catalog = builder.globalTable("product-catalog",
                Consumed.with(Serdes.String(), Serdes.Long()),
                Materialized.<String, Long>as(Stores.inMemoryKeyValueStore("catalog-prices"))
                        .withKeySerde(Serdes.String()).withValueSerde(Serdes.Long())
                        .withCachingDisabled());
        builder.<String, String>stream("order-lines", Consumed.with(Serdes.String(), Serdes.String()))
                .mapValues(OrderLine::parse)
                .leftJoin(catalog, (orderId, line) -> line.sku(),
                        (line, unitPrice) -> unitPrice == null
                                ? line.sku() + ":UNAVAILABLE"
                                : line.sku() + ":" + line.quantity() * unitPrice)
                .to("priced-orders", Produced.with(Serdes.String(), Serdes.String()));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "product-catalog-enrichment");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        Instant start = Instant.parse("2025-01-01T12:00:00Z");
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties, start)) {
            TestInputTopic<String, Long> products = driver.createInputTopic(
                    "product-catalog", new StringSerializer(), new LongSerializer());
            TestInputTopic<String, String> orders = driver.createInputTopic(
                    "order-lines", new StringSerializer(), new StringSerializer());
            TestOutputTopic<String, String> pricedOrders = driver.createOutputTopic(
                    "priced-orders", new StringDeserializer(), new StringDeserializer());

            products.pipeInput("BOOK", 1200L, start);
            products.pipeInput("PEN", 200L, start);
            orders.pipeInput("order-101", "BOOK|2", start.plusSeconds(1));
            orders.pipeInput("order-102", "PEN|5", start.plusSeconds(2));
            orders.pipeInput("order-103", "LAMP|1", start.plusSeconds(3));
            assertEquals(List.of(
                    KeyValue.pair("order-101", "BOOK:2400"),
                    KeyValue.pair("order-102", "PEN:1000"),
                    KeyValue.pair("order-103", "LAMP:UNAVAILABLE")), pricedOrders.readKeyValuesToList());

            products.pipeInput("BOOK", 1000L, start.plusSeconds(4));
            products.pipeInput("LAMP", 3500L, start.plusSeconds(5));
            assertTrue(pricedOrders.isEmpty());
            orders.pipeInput("order-104", "BOOK|3", start.plusSeconds(6));
            orders.pipeInput("order-105", "LAMP|2", start.plusSeconds(7));
            assertEquals(List.of(
                    KeyValue.pair("order-104", "BOOK:3000"),
                    KeyValue.pair("order-105", "LAMP:7000")), pricedOrders.readKeyValuesToList());

            products.pipeInput("BOOK", (Long) null, start.plusSeconds(8));
            assertTrue(pricedOrders.isEmpty());
            orders.pipeInput("order-106", "BOOK|1", start.plusSeconds(9));
            orders.pipeInput("order-107", "PEN|2", start.plusSeconds(10));
            assertEquals(List.of(
                    KeyValue.pair("order-106", "BOOK:UNAVAILABLE"),
                    KeyValue.pair("order-107", "PEN:400")), pricedOrders.readKeyValuesToList());

            products.pipeInput("BOOK", 900L, start.plusSeconds(11));
            assertTrue(pricedOrders.isEmpty());
            orders.pipeInput("order-108", "BOOK|4", start.plusSeconds(12));
            assertEquals(KeyValue.pair("order-108", "BOOK:3600"), pricedOrders.readKeyValue());
            assertTrue(pricedOrders.isEmpty());
        }
    }

    private record OrderLine(String sku, int quantity) {
        private static OrderLine parse(String value) {
            String[] fields = value.split("\\|", -1);
            return new OrderLine(fields[0], Integer.parseInt(fields[1]));
        }
    }
}
