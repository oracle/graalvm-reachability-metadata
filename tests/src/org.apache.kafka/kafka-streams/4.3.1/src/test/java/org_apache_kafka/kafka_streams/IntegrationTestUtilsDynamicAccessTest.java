/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.integration.utils.IntegrationTestUtils;
import org.apache.kafka.streams.processor.internals.namedtopology.KafkaStreamsNamedTopologyWrapper;
import org.apache.kafka.test.MockClientSupplier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IntegrationTestUtilsDynamicAccessTest {

    @Test
    void shouldReadKafkaStreamsStateListenerWhenWaitingForStartup() {
        KafkaStreams streams = new KafkaStreams(
                streamsBuilder().build(),
                streamsProperties(),
                new MockClientSupplier());
        streams.setStateListener(IntegrationTestUtilsDynamicAccessTest::recordStateTransition);

        assertStartupFails(streams);
    }

    @Test
    void shouldReadNamedTopologyWrapperStateListenerWhenWaitingForStartup() {
        KafkaStreamsNamedTopologyWrapper streams = new KafkaStreamsNamedTopologyWrapper(
                streamsProperties(),
                new MockClientSupplier());
        streams.setStateListener(IntegrationTestUtilsDynamicAccessTest::recordStateTransition);

        assertStartupFails(streams);
    }

    private static void recordStateTransition(KafkaStreams.State newState, KafkaStreams.State oldState) {
        if (newState == null || oldState == null) {
            throw new IllegalStateException("State transition should include both states.");
        }
    }

    private static void assertStartupFails(KafkaStreams streams) {
        try {
            assertThatThrownBy(() -> IntegrationTestUtils.startApplicationAndWaitUntilRunning(
                    List.of(streams),
                    Duration.ZERO))
                    .isInstanceOfAny(AssertionError.class, RuntimeException.class);
        } finally {
            streams.close(Duration.ZERO);
        }
    }

    private static StreamsBuilder streamsBuilder() {
        StreamsBuilder builder = new StreamsBuilder();
        builder.stream("dynamic-access-input").to("dynamic-access-output");
        return builder;
    }

    private static Properties streamsProperties() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "integration-test-utils-" + UUID.randomUUID());
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        properties.put(StreamsConfig.STATE_DIR_CONFIG, System.getProperty("java.io.tmpdir"));
        return properties;
    }
}
