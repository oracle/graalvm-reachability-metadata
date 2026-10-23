/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises stateless Kafka Streams DSL branching and rekeying for regional order fulfillment.
 * Incoming order lines are normalized, invalid orders go to a rejection topic, and valid orders
 * are keyed by warehouse so downstream fulfillment workers receive the appropriate work.
 */
public class OrderFulfillmentRoutingTest {

    @Test
    @Timeout(60)
    public void routesNormalizedOrdersAndRejectsInvalidRequests() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, Order> orders = builder
                .<String, String>stream("orders", Consumed.with(Serdes.String(), Serdes.String()))
                .filter((orderId, value) -> orderId != null && value != null)
                .mapValues(Order::parse);

        orders.split(Named.as("validation-"))
                .branch((orderId, order) -> !order.valid(), Branched.withConsumer(rejected -> rejected
                        .mapValues(order -> "REJECTED:" + order.sku() + ":" + order.quantity())
                        .to("rejected-orders", Produced.with(Serdes.String(), Serdes.String()))))
                .defaultBranch(Branched.withConsumer(accepted -> accepted
                        .mapValues((orderId, order) -> new Dispatch(orderId, order))
                        .selectKey((orderId, dispatch) -> "warehouse-" + dispatch.order().region())
                        .mapValues(dispatch -> dispatch.orderId() + ":" + dispatch.order().sku()
                                + ":" + dispatch.order().quantity())
                        .to("fulfillment", Produced.with(Serdes.String(), Serdes.String()))));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "order-fulfillment-routing");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        Instant timestamp = Instant.parse("2025-01-01T12:00:00Z");
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties, timestamp)) {
            TestInputTopic<String, String> input = driver.createInputTopic(
                    "orders", new StringSerializer(), new StringSerializer());
            TestOutputTopic<String, String> fulfillment = driver.createOutputTopic(
                    "fulfillment", new StringDeserializer(), new StringDeserializer());
            TestOutputTopic<String, String> rejected = driver.createOutputTopic(
                    "rejected-orders", new StringDeserializer(), new StringDeserializer());

            input.pipeInput("order-101", " North | book | 2 ", timestamp);
            input.pipeInput("order-102", "south|lamp|1", timestamp.plusSeconds(1));
            input.pipeInput("order-103", "north|book|0", timestamp.plusSeconds(2));
            input.pipeInput("order-104", "west|desk|3", timestamp.plusSeconds(3));
            input.pipeInput("order-105", "north|pen|4", timestamp.plusSeconds(4));
            input.pipeInput("order-106", (String) null, timestamp.plusSeconds(5));
            input.pipeInput(null, "north|book|2", timestamp.plusSeconds(6));

            assertEquals(List.of(
                    KeyValue.pair("warehouse-north", "order-101:BOOK:2"),
                    KeyValue.pair("warehouse-south", "order-102:LAMP:1"),
                    KeyValue.pair("warehouse-north", "order-105:PEN:4")),
                    fulfillment.readKeyValuesToList());
            assertEquals(List.of(
                    KeyValue.pair("order-103", "REJECTED:BOOK:0"),
                    KeyValue.pair("order-104", "REJECTED:DESK:3")),
                    rejected.readKeyValuesToList());
            assertTrue(fulfillment.isEmpty());
            assertTrue(rejected.isEmpty());
        }
    }

    private record Order(String region, String sku, int quantity) {
        private static Order parse(String value) {
            String[] fields = value.split("\\|", -1);
            return new Order(fields[0].trim().toLowerCase(Locale.ROOT),
                    fields[1].trim().toUpperCase(Locale.ROOT), Integer.parseInt(fields[2].trim()));
        }

        private boolean valid() {
            return (region.equals("north") || region.equals("south")) && !sku.isEmpty() && quantity > 0;
        }
    }

    private record Dispatch(String orderId, Order order) {
    }
}
