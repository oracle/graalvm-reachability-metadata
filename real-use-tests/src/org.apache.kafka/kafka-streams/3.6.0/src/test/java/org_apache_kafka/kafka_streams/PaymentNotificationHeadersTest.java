/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.processor.api.ContextualProcessor;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Kafka Streams low-level topology routing and record-header processing for payment notifications.
 * Payment status events fan out to an internal audit topic and an external notification topic, retaining
 * correlation metadata in both while removing private customer headers from external messages.
 */
public class PaymentNotificationHeadersTest {

    @Test
    @Timeout(60)
    public void forwardsIndependentHeaderSetsToNamedDestinations(@TempDir Path stateDirectory) {
        Topology topology = new Topology();
        topology.addSource("payment-source", new StringDeserializer(), new StringDeserializer(),
                "payment-status");
        topology.addProcessor("notification-fanout", NotificationFanoutProcessor::new, "payment-source");
        topology.addSink("audit-sink", "payment-audit", new StringSerializer(), new StringSerializer(),
                "notification-fanout");
        topology.addSink("external-sink", "payment-notifications", new StringSerializer(),
                new StringSerializer(), "notification-fanout");

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "payment-notification-headers");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        Instant start = Instant.parse("2025-01-01T12:00:00Z");
        try (TopologyTestDriver driver = new TopologyTestDriver(topology, properties, start)) {
            TestInputTopic<String, String> payments = driver.createInputTopic(
                    "payment-status", new StringSerializer(), new StringSerializer());
            TestOutputTopic<String, String> audit = driver.createOutputTopic(
                    "payment-audit", new StringDeserializer(), new StringDeserializer());
            TestOutputTopic<String, String> notifications = driver.createOutputTopic(
                    "payment-notifications", new StringDeserializer(), new StringDeserializer());

            Headers firstHeaders = new RecordHeaders();
            firstHeaders.add("correlation-id", bytes("checkout-101"));
            firstHeaders.add("customer-email", bytes("alice@example.test"));
            payments.pipeInput(new TestRecord<>("payment-101", "SETTLED", firstHeaders, start));
            Headers firstAudit = new RecordHeaders(firstHeaders).add("audience", bytes("internal"));
            Headers firstExternal = new RecordHeaders()
                    .add("correlation-id", bytes("checkout-101"))
                    .add("audience", bytes("external"));
            assertEquals(new TestRecord<>("payment-101", "SETTLED", firstAudit, start), audit.readRecord());
            assertEquals(new TestRecord<>("payment-101", "SETTLED", firstExternal, start),
                    notifications.readRecord());

            // Kafka permits repeated header keys; no private value may survive the external copy.
            Headers retryHeaders = new RecordHeaders()
                    .add("customer-email", bytes("old@example.test"))
                    .add("correlation-id", bytes("checkout-102"))
                    .add("customer-email", bytes("bob@example.test"))
                    .add("audience", bytes("upstream"));
            Instant second = start.plusSeconds(1);
            payments.pipeInput(new TestRecord<>("payment-102", "DECLINED", retryHeaders, second));
            Headers secondAudit = new RecordHeaders()
                    .add("customer-email", bytes("old@example.test"))
                    .add("correlation-id", bytes("checkout-102"))
                    .add("customer-email", bytes("bob@example.test"))
                    .add("audience", bytes("internal"));
            Headers secondExternal = new RecordHeaders()
                    .add("correlation-id", bytes("checkout-102"))
                    .add("audience", bytes("external"));
            assertEquals(new TestRecord<>("payment-102", "DECLINED", secondAudit, second), audit.readRecord());
            assertEquals(new TestRecord<>("payment-102", "DECLINED", secondExternal, second),
                    notifications.readRecord());

            // A headerless event must not inherit either preceding customer's metadata.
            Instant third = start.plusSeconds(2);
            payments.pipeInput(new TestRecord<>("payment-103", "REFUNDED", new RecordHeaders(), third));
            assertEquals(new TestRecord<>("payment-103", "REFUNDED",
                    new RecordHeaders().add("audience", bytes("internal")), third), audit.readRecord());
            assertEquals(new TestRecord<>("payment-103", "REFUNDED",
                    new RecordHeaders().add("audience", bytes("external")), third), notifications.readRecord());

            payments.pipeInput("payment-101", (String) null, start.plusSeconds(3));
            assertTrue(audit.isEmpty());
            assertTrue(notifications.isEmpty());
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class NotificationFanoutProcessor
            extends ContextualProcessor<String, String, String, String> {
        @Override
        public void process(Record<String, String> record) {
            if (record.value() == null) {
                return;
            }
            Headers internal = new RecordHeaders(record.headers());
            internal.remove("audience");
            internal.add("audience", bytes("internal"));
            context().forward(record.withHeaders(internal), "audit-sink");

            Headers external = new RecordHeaders(record.headers());
            external.remove("customer-email");
            external.remove("audience");
            external.add("audience", bytes("external"));
            context().forward(record.withHeaders(external), "external-sink");
        }
    }
}
