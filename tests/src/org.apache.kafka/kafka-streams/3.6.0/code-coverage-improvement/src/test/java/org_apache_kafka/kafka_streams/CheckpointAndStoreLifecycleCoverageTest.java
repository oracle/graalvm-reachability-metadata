/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.streams.state.internals.OffsetCheckpoint;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises checkpoint persistence through its public file-backed API. */
public class CheckpointAndStoreLifecycleCoverageTest {
    @Test
    void shouldWriteReadAndDeleteOffsetCheckpoint() throws Exception {
        File checkpointFile = new File("build/deep-offset-checkpoint/checkpoint");
        Files.createDirectories(checkpointFile.toPath().getParent());
        OffsetCheckpoint checkpoint = new OffsetCheckpoint(checkpointFile);
        TopicPartition first = new TopicPartition("deep-topic", 0);
        TopicPartition second = new TopicPartition("deep-topic", 1);
        checkpoint.write(Map.of(first, 17L, second, 23L));
        assertThat(checkpoint.read()).containsEntry(first, 17L).containsEntry(second, 23L);
        checkpoint.delete();
        assertThat(checkpoint.read()).isEmpty();
    }
}
