/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.internals.StateDirectory;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives task and named-topology state-directory cleanup through its public lifecycle API. */
public class DeepStateDirectoryCoverageTest {
    @Test
    void shouldCreateAndCleanTaskDirectories() throws Exception {
        Path statePath = Path.of("build", "deep-state-directory");
        deleteRecursively(statePath);
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "deep-state-directory");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, statePath.toString());
        StateDirectory directory = new StateDirectory(new StreamsConfig(properties), Time.SYSTEM, true, true);
        try {
            directory.initializeProcessId();
            File taskDirectory = directory.getOrCreateDirectoryForTask(new TaskId(2, 3));
            Files.writeString(taskDirectory.toPath().resolve("state"), "state");
            assertThat(taskDirectory).exists();
            directory.clean();
            assertThat(directory.getOrCreateDirectoryForTask(new TaskId(2, 3))).exists();
            directory.clearLocalStateForNamedTopology("deep-named-topology");
        } finally {
            directory.close();
            deleteRecursively(statePath);
        }
    }

    private static void deleteRecursively(Path path) throws Exception {
        if (Files.exists(path)) {
            try (java.util.stream.Stream<Path> paths = Files.walk(path)) {
                paths.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }
}
