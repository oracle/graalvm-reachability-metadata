/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.nio.file.Path;
import java.time.Instant;
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
 * Exercises Kafka Streams foreign-key KTable joins and subscription updates for order delivery addresses.
 * A fulfillment view fans customer address changes out to all linked orders, follows customer
 * reassignments, and keeps orders with missing addresses visible as awaiting an address.
 */
public class OrderAddressSubscriptionTest {

    @Test
    @Timeout(60)
    public void keepsOrderAddressesCurrentAcrossCustomerChanges(@TempDir Path stateDirectory) {
        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, String> orders = builder.table("open-orders",
                Consumed.with(Serdes.String(), Serdes.String()),
                Materialized.<String, String>as(Stores.inMemoryKeyValueStore("open-order-records"))
                        .withKeySerde(Serdes.String()).withValueSerde(Serdes.String())
                        .withCachingDisabled());
        KTable<String, String> addresses = builder.table("customer-addresses",
                Consumed.with(Serdes.String(), Serdes.String()),
                Materialized.<String, String>as(Stores.inMemoryKeyValueStore("address-records"))
                        .withKeySerde(Serdes.String()).withValueSerde(Serdes.String())
                        .withCachingDisabled());
        orders.leftJoin(addresses, value -> Order.parse(value).customerId(),
                (value, address) -> Order.parse(value).item() + ":"
                        + (address == null ? "AWAITING_ADDRESS" : address),
                Materialized.<String, String>as(Stores.inMemoryKeyValueStore("delivery-address-view"))
                        .withKeySerde(Serdes.String()).withValueSerde(Serdes.String())
                        .withCachingDisabled())
                .toStream()
                .to("delivery-address-updates", Produced.with(Serdes.String(), Serdes.String()));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "order-address-subscriptions");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        properties.put(StreamsConfig.DEFAULT_DSL_STORE_CONFIG, StreamsConfig.IN_MEMORY);
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        Instant start = Instant.parse("2025-01-01T12:00:00Z");
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties, start)) {
            TestInputTopic<String, String> orderChanges = driver.createInputTopic(
                    "open-orders", new StringSerializer(), new StringSerializer());
            TestInputTopic<String, String> addressChanges = driver.createInputTopic(
                    "customer-addresses", new StringSerializer(), new StringSerializer());
            TestOutputTopic<String, String> deliveries = driver.createOutputTopic(
                    "delivery-address-updates", new StringDeserializer(), new StringDeserializer());
            KeyValueStore<String, String> view = driver.getKeyValueStore("delivery-address-view");

            addressChanges.pipeInput("alice", "Boston", start);
            addressChanges.pipeInput("bob", "Seattle", start.plusSeconds(1));
            assertTrue(deliveries.isEmpty());
            orderChanges.pipeInput("order-1", "alice|BOOK", start.plusSeconds(2));
            orderChanges.pipeInput("order-2", "alice|PEN", start.plusSeconds(3));
            orderChanges.pipeInput("order-3", "carol|LAMP", start.plusSeconds(4));
            assertEquals(Map.of("order-1", "BOOK:Boston", "order-2", "PEN:Boston",
                    "order-3", "LAMP:AWAITING_ADDRESS"), deliveries.readKeyValuesToMap());

            // One customer update must refresh both orders without another order event.
            addressChanges.pipeInput("alice", "Denver", start.plusSeconds(5));
            assertEquals(Map.of("order-1", "BOOK:Denver", "order-2", "PEN:Denver"),
                    deliveries.readKeyValuesToMap());
            assertEquals("BOOK:Denver", view.get("order-1"));
            assertEquals("PEN:Denver", view.get("order-2"));

            orderChanges.pipeInput("order-2", "bob|PEN", start.plusSeconds(6));
            assertEquals(Map.of("order-2", "PEN:Seattle"), deliveries.readKeyValuesToMap());
            addressChanges.pipeInput("alice", "Portland", start.plusSeconds(7));
            assertEquals(Map.of("order-1", "BOOK:Portland"), deliveries.readKeyValuesToMap());
            assertEquals("PEN:Seattle", view.get("order-2"));
            addressChanges.pipeInput("bob", "Chicago", start.plusSeconds(8));
            assertEquals(Map.of("order-2", "PEN:Chicago"), deliveries.readKeyValuesToMap());

            addressChanges.pipeInput("carol", "Austin", start.plusSeconds(9));
            assertEquals(Map.of("order-3", "LAMP:Austin"), deliveries.readKeyValuesToMap());
            addressChanges.pipeInput("alice", (String) null, start.plusSeconds(10));
            assertEquals(Map.of("order-1", "BOOK:AWAITING_ADDRESS"), deliveries.readKeyValuesToMap());
            assertEquals("BOOK:AWAITING_ADDRESS", view.get("order-1"));

            orderChanges.pipeInput("order-1", (String) null, start.plusSeconds(11));
            assertEquals(KeyValue.<String, String>pair("order-1", null), deliveries.readKeyValue());
            assertNull(view.get("order-1"));
            addressChanges.pipeInput("alice", "Miami", start.plusSeconds(12));
            assertTrue(deliveries.isEmpty());
            assertNull(view.get("order-1"));
            assertEquals("PEN:Chicago", view.get("order-2"));
            assertEquals("LAMP:Austin", view.get("order-3"));
        }
    }

    private record Order(String customerId, String item) {
        private static Order parse(String value) {
            String[] fields = value.split("\\|", -1);
            return new Order(fields[0], fields[1]);
        }
    }
}
