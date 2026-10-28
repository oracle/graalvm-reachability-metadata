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
import org.apache.kafka.streams.ThreadMetadata;
import org.apache.kafka.streams.kstream.Consumed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class StreamsPartitionAssignorTest {

    @TempDir
    Path stateDirectory;

    @Test
    @Timeout(120)
    void shouldConstructClassicConsumersForEachStreamThread() {
        StreamsBuilder builder = new StreamsBuilder();
        builder.stream("assignor-input", Consumed.with(Serdes.String(), Serdes.String()))
                .filter((key, value) -> value != null)
                .to("assignor-output");

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "assignor-construction-test");
        properties.put(StreamsConfig.CLIENT_ID_CONFIG, "assignor-client");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
        properties.put(StreamsConfig.GROUP_PROTOCOL_CONFIG, "classic");
        properties.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 2);
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());

        // Construction configures real consumers and loads the Streams assignor by name, without polling a broker.
        KafkaStreams streams = new KafkaStreams(builder.build(), properties);
        try {
            assertThat(streams.state()).isEqualTo(KafkaStreams.State.CREATED);
            Set<ThreadMetadata> threads = streams.metadataForLocalThreads();
            assertThat(threads).hasSize(2);
            assertThat(threads).extracting(ThreadMetadata::consumerClientId).containsExactlyInAnyOrder(
                    "assignor-client-StreamThread-1-consumer",
                    "assignor-client-StreamThread-2-consumer");
            assertThat(threads).allSatisfy(thread -> {
                assertThat(thread.threadState()).isEqualTo("CREATED");
                assertThat(thread.activeTasks()).isEmpty();
                assertThat(thread.standbyTasks()).isEmpty();
            });
        } finally {
            assertThat(streams.close(Duration.ofSeconds(30))).isTrue();
        }
        assertThat(streams.state()).isEqualTo(KafkaStreams.State.NOT_RUNNING);
    }
}
