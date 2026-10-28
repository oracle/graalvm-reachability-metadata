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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.kafka.common.serialization.LongDeserializer;
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
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.SessionStore;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Kafka Streams session-window aggregation and merging for website visit analytics.
 * A late page view bridges two visits, retracting the old session summaries and publishing one
 * combined visit while preserving independent visitors and visits separated by inactivity.
 */
public class VisitorSessionMergingTest {

    @Test
    @Timeout(60)
    public void mergesVisitsWhenAnOutOfOrderPageViewBridgesTheGap(@TempDir Path stateDirectory) {
        StreamsBuilder builder = new StreamsBuilder();
        builder.<String, String>stream("page-views", Consumed.with(Serdes.String(), Serdes.String()))
                .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .windowedBy(SessionWindows.ofInactivityGapAndGrace(
                        Duration.ofSeconds(30), Duration.ofMinutes(2)))
                .count(Materialized.<String, Long>as(Stores.inMemorySessionStore(
                        "visitor-sessions", Duration.ofMinutes(5)))
                        .withKeySerde(Serdes.String()).withValueSerde(Serdes.Long())
                        .withCachingDisabled())
                .toStream()
                .map((session, count) -> KeyValue.pair(sessionId(session), count))
                .to("visit-summaries", Produced.with(Serdes.String(), Serdes.Long()));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "visitor-session-merging");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        Instant start = Instant.parse("2025-01-01T12:00:00Z");
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties, start)) {
            TestInputTopic<String, String> views = driver.createInputTopic(
                    "page-views", new StringSerializer(), new StringSerializer());
            TestOutputTopic<String, Long> summaries = driver.createOutputTopic(
                    "visit-summaries", new StringDeserializer(), new LongDeserializer());
            SessionStore<String, Long> sessions = driver.getSessionStore("visitor-sessions");

            views.pipeInput("alice", "/home", start);
            assertEquals(Map.of(id("alice", start, 0, 0), 1L), summaries.readKeyValuesToMap());
            views.pipeInput("alice", "/products", start.plusSeconds(10));
            Map<String, Long> extended = summaries.readKeyValuesToMap();
            assertEquals(Set.of(id("alice", start, 0, 0), id("alice", start, 0, 10)), extended.keySet());
            assertNull(extended.get(id("alice", start, 0, 0)));
            assertEquals(Long.valueOf(2L), extended.get(id("alice", start, 0, 10)));

            views.pipeInput("bob", "/home", start.plusSeconds(20));
            views.pipeInput("alice", "/basket", start.plusSeconds(60));
            assertEquals(Map.of(id("bob", start, 20, 20), 1L, id("alice", start, 60, 60), 1L),
                    summaries.readKeyValuesToMap());
            assertEquals(Map.of(id("alice", start, 0, 10), 2L, id("alice", start, 60, 60), 1L),
                    storedSessions(sessions, "alice"));

            // The event arrives late but is within the grace period and 30 seconds of both visits.
            views.pipeInput("alice", "/search", start.plusSeconds(35));
            Map<String, Long> merged = summaries.readKeyValuesToMap();
            assertEquals(Set.of(id("alice", start, 0, 10), id("alice", start, 60, 60),
                    id("alice", start, 0, 60)), merged.keySet());
            assertNull(merged.get(id("alice", start, 0, 10)));
            assertNull(merged.get(id("alice", start, 60, 60)));
            assertEquals(Long.valueOf(4L), merged.get(id("alice", start, 0, 60)));
            assertEquals(Map.of(id("alice", start, 0, 60), 4L), storedSessions(sessions, "alice"));
            assertEquals(Map.of(id("bob", start, 20, 20), 1L), storedSessions(sessions, "bob"));

            views.pipeInput("alice", "/checkout", start.plusSeconds(100));
            assertEquals(Map.of(id("alice", start, 100, 100), 1L), summaries.readKeyValuesToMap());
            assertEquals(Map.of(id("alice", start, 0, 60), 4L, id("alice", start, 100, 100), 1L),
                    storedSessions(sessions, "alice"));
            assertTrue(summaries.isEmpty());
        }
    }

    private static String sessionId(Windowed<String> session) {
        return session.key() + "/" + session.window().startTime() + "/" + session.window().endTime();
    }

    private static String id(String visitor, Instant start, long firstSecond, long lastSecond) {
        return visitor + "/" + start.plusSeconds(firstSecond) + "/" + start.plusSeconds(lastSecond);
    }

    private static Map<String, Long> storedSessions(SessionStore<String, Long> store, String visitor) {
        Map<String, Long> result = new LinkedHashMap<>();
        try (KeyValueIterator<Windowed<String>, Long> entries = store.fetch(visitor)) {
            while (entries.hasNext()) {
                KeyValue<Windowed<String>, Long> entry = entries.next();
                result.put(sessionId(entry.key), entry.value);
            }
        }
        return result;
    }
}
