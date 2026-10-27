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
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Kafka Streams partitioned KStream-KTable lookup joins for card transaction limit decisions.
 * Each purchase is checked against the account's current per-transaction limit, including delayed
 * purchases, without issuing new decisions when limits change or accounts are removed.
 */
public class CardLimitAuthorizationTest {

    @Test
    @Timeout(60)
    public void checksEachPurchaseAgainstTheLatestAccountLimit(@TempDir Path stateDirectory) {
        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, Long> limits = builder.table("account-limits",
                Consumed.with(Serdes.String(), Serdes.Long()),
                Materialized.<String, Long>as(Stores.inMemoryKeyValueStore("current-account-limits"))
                        .withKeySerde(Serdes.String()).withValueSerde(Serdes.Long())
                        .withCachingDisabled());
        builder.<String, String>stream("card-purchases", Consumed.with(Serdes.String(), Serdes.String()))
                .mapValues(Purchase::parse)
                .leftJoin(limits, (purchase, limit) -> purchase.id() + ":"
                        + (limit == null ? "UNKNOWN_ACCOUNT"
                                : purchase.cents() <= limit ? "APPROVED" : "LIMIT_EXCEEDED"))
                .to("card-decisions", Produced.with(Serdes.String(), Serdes.String()));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "card-limit-authorization");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        Instant start = Instant.parse("2025-01-01T12:00:00Z");
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties, start)) {
            TestInputTopic<String, Long> accountLimits = driver.createInputTopic(
                    "account-limits", new StringSerializer(), new LongSerializer());
            TestInputTopic<String, String> purchases = driver.createInputTopic(
                    "card-purchases", new StringSerializer(), new StringSerializer());
            TestOutputTopic<String, String> decisions = driver.createOutputTopic(
                    "card-decisions", new StringDeserializer(), new StringDeserializer());

            accountLimits.pipeInput("alice", 5000L, start);
            accountLimits.pipeInput("bob", 1000L, start);
            assertTrue(decisions.isEmpty());
            purchases.pipeInput("alice", "purchase-1|4500", start.plusSeconds(1));
            purchases.pipeInput("alice", "purchase-2|5001", start.plusSeconds(2));
            purchases.pipeInput("bob", "purchase-3|1000", start.plusSeconds(3));
            purchases.pipeInput("carol", "purchase-4|100", start.plusSeconds(4));
            assertEquals(List.of(
                    KeyValue.pair("alice", "purchase-1:APPROVED"),
                    KeyValue.pair("alice", "purchase-2:LIMIT_EXCEEDED"),
                    KeyValue.pair("bob", "purchase-3:APPROVED"),
                    KeyValue.pair("carol", "purchase-4:UNKNOWN_ACCOUNT")), decisions.readKeyValuesToList());

            accountLimits.pipeInput("alice", 2000L, start.plusSeconds(10));
            accountLimits.pipeInput("carol", 500L, start.plusSeconds(11));
            assertTrue(decisions.isEmpty());
            // An unversioned table supplies its current limit, not the limit at purchase event time.
            purchases.pipeInput("alice", "purchase-5|4500", start.plusSeconds(5));
            purchases.pipeInput("alice", "purchase-6|2000", start.plusSeconds(12));
            purchases.pipeInput("carol", "purchase-7|100", start.plusSeconds(13));
            assertEquals(List.of(
                    KeyValue.pair("alice", "purchase-5:LIMIT_EXCEEDED"),
                    KeyValue.pair("alice", "purchase-6:APPROVED"),
                    KeyValue.pair("carol", "purchase-7:APPROVED")), decisions.readKeyValuesToList());

            accountLimits.pipeInput("alice", (Long) null, start.plusSeconds(14));
            assertTrue(decisions.isEmpty());
            purchases.pipeInput("alice", "purchase-8|100", start.plusSeconds(15));
            purchases.pipeInput("bob", "purchase-9|1001", start.plusSeconds(16));
            assertEquals(List.of(
                    KeyValue.pair("alice", "purchase-8:UNKNOWN_ACCOUNT"),
                    KeyValue.pair("bob", "purchase-9:LIMIT_EXCEEDED")), decisions.readKeyValuesToList());

            accountLimits.pipeInput("alice", 10000L, start.plusSeconds(17));
            assertTrue(decisions.isEmpty());
            purchases.pipeInput("alice", "purchase-10|4500", start.plusSeconds(18));
            assertEquals(KeyValue.pair("alice", "purchase-10:APPROVED"), decisions.readKeyValue());
            assertTrue(decisions.isEmpty());
        }
    }

    private record Purchase(String id, long cents) {
        private static Purchase parse(String value) {
            String[] fields = value.split("\\|", -1);
            return new Purchase(fields[0], Long.parseLong(fields[1]));
        }
    }
}
