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
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class StateDirectoryInnerStateDirectoryProcessFileTest {

    private static final String APPLICATION_ID = "persistent-process-test";

    @TempDir
    Path stateDirectory;

    @Test
    @Timeout(120)
    void shouldPersistAndRestoreProcessIdentityAcrossClientRecreation() throws IOException {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, APPLICATION_ID);
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
        properties.put(StreamsConfig.GROUP_PROTOCOL_CONFIG, "classic");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());

        Path processFile = stateDirectory.resolve(APPLICATION_ID).resolve("kafka-streams-process-metadata");
        String consumerClientId;
        String persistedProcess;
        KafkaStreams first = new KafkaStreams(persistentTopology(), properties);
        try {
            assertThat(first.metadataForLocalThreads()).hasSize(1);
            ThreadMetadata thread = first.metadataForLocalThreads().iterator().next();
            consumerClientId = thread.consumerClientId();
            String processId = consumerClientId.substring(
                    (APPLICATION_ID + "-").length(), consumerClientId.indexOf("-StreamThread-"));
            assertThat(UUID.fromString(processId).toString()).isEqualTo(processId);
            assertThat(processFile).isRegularFile();
            persistedProcess = Files.readString(processFile);
            assertThat(persistedProcess).contains("\"processId\"", processId);
        } finally {
            assertThat(first.close(Duration.ofSeconds(30))).isTrue();
        }

        KafkaStreams reopened = new KafkaStreams(persistentTopology(), properties);
        try {
            assertThat(reopened.state()).isEqualTo(KafkaStreams.State.CREATED);
            assertThat(reopened.metadataForLocalThreads())
                    .extracting(ThreadMetadata::consumerClientId).containsExactly(consumerClientId);
            assertThat(Files.readString(processFile)).isEqualTo(persistedProcess);
        } finally {
            assertThat(reopened.close(Duration.ofSeconds(30))).isTrue();
        }
    }

    private Topology persistentTopology() {
        StreamsBuilder builder = new StreamsBuilder();
        builder.stream("process-input", Consumed.with(Serdes.String(), Serdes.String()))
                .groupByKey()
                .count(Materialized.as("process-counts"));
        return builder.build();
    }
}
