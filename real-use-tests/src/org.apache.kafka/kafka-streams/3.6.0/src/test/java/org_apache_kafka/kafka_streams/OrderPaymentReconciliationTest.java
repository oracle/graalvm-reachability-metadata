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
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Kafka Streams windowed stream-stream joins for order and payment reconciliation.
 * A checkout service releases paid orders only when their order ID and event times match,
 * regardless of which event arrives first, while excluding payments outside the checkout window.
 */
public class OrderPaymentReconciliationTest {

    @Test
    @Timeout(60)
    public void releasesOrdersWithMatchingPaymentsWithinFiveMinutes(@TempDir Path stateDirectory) {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> orders = builder.stream("checkout-orders",
                Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> payments = builder.stream("confirmed-payments",
                Consumed.with(Serdes.String(), Serdes.String()));
        orders.join(payments, (order, payment) -> order + ":PAID:" + payment,
                JoinWindows.ofTimeDifferenceAndGrace(Duration.ofMinutes(5), Duration.ofMinutes(1)),
                StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String())
                        .withThisStoreSupplier(Stores.inMemoryWindowStore("unmatched-orders",
                                Duration.ofMinutes(11), Duration.ofMinutes(10), true))
                        .withOtherStoreSupplier(Stores.inMemoryWindowStore("unmatched-payments",
                                Duration.ofMinutes(11), Duration.ofMinutes(10), true)))
                .to("paid-orders", Produced.with(Serdes.String(), Serdes.String()));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "order-payment-reconciliation");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        Instant start = Instant.parse("2025-01-01T12:00:00Z");
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties, start)) {
            TestInputTopic<String, String> checkouts = driver.createInputTopic(
                    "checkout-orders", new StringSerializer(), new StringSerializer());
            TestInputTopic<String, String> confirmations = driver.createInputTopic(
                    "confirmed-payments", new StringSerializer(), new StringSerializer());
            TestOutputTopic<String, String> paidOrders = driver.createOutputTopic(
                    "paid-orders", new StringDeserializer(), new StringDeserializer());

            checkouts.pipeInput("order-101", "BOOK|2", start);
            assertTrue(paidOrders.isEmpty());
            confirmations.pipeInput("order-101", "txn-1", start.plusSeconds(30));
            assertEquals(KeyValue.pair("order-101", "BOOK|2:PAID:txn-1"), paidOrders.readKeyValue());

            confirmations.pipeInput("order-102", "txn-2", start.plusSeconds(40));
            assertTrue(paidOrders.isEmpty());
            checkouts.pipeInput("order-102", "PEN|5", start.plusSeconds(10));
            assertEquals(KeyValue.pair("order-102", "PEN|5:PAID:txn-2"), paidOrders.readKeyValue());

            checkouts.pipeInput("order-103", "LAMP|1", start.plusSeconds(50));
            confirmations.pipeInput("order-104", "txn-unmatched", start.plusSeconds(60));
            assertTrue(paidOrders.isEmpty());

            checkouts.pipeInput("order-105", "DESK|1", start.plusSeconds(70));
            confirmations.pipeInput("order-105", "txn-too-late", start.plusSeconds(371));
            assertTrue(paidOrders.isEmpty());

            // This payment arrives out of order, but its event time is still within five minutes.
            confirmations.pipeInput("order-103", "txn-3", start.plusSeconds(349));
            assertEquals(List.of(KeyValue.pair("order-103", "LAMP|1:PAID:txn-3")),
                    paidOrders.readKeyValuesToList());

            checkouts.pipeInput("order-106", "BAG|1", start.plusSeconds(400));
            confirmations.pipeInput("order-106", "txn-6", start.plusSeconds(700));
            assertEquals(KeyValue.pair("order-106", "BAG|1:PAID:txn-6"), paidOrders.readKeyValue());
            assertTrue(paidOrders.isEmpty());
        }
    }
}
