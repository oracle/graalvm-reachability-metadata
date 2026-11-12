/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ExecConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class GenericContainerTest {
    @Test
    @Timeout(120)
    void copiesFilesAndExecutesCommandsWithContainerAndProcessConfiguration(@TempDir Path directory) throws Exception {
        Path input = directory.resolve("input.txt");
        Files.writeString(input, "initial payload\n");

        try (GenericContainer<?> container = new GenericContainer<>("nginx:1-alpine-slim")
                .withExposedPorts(80)
                .withStartupTimeout(Duration.ofSeconds(60))
                .withEnv("CONTAINER_MESSAGE", "container")
                .withLabel("coverage.scenario", "file-transfer")
                .withCopyToContainer(MountableFile.forHostPath(input), "/tmp/input.txt")) {
            container.start();

            assertThat(container.isRunning()).isTrue();
            assertThat(container.getContainerInfo().getConfig().getLabels())
                    .containsEntry("coverage.scenario", "file-transfer");
            Container.ExecResult initial = container.execInContainer("cat", "/tmp/input.txt");
            assertThat(initial.getExitCode()).isZero();
            assertThat(initial.getStdout()).isEqualTo("initial payload\n");
            assertThat(initial.getStderr()).isEmpty();

            container.copyFileToContainer(Transferable.of("updated payload\n"), "/tmp/input.txt");
            ExecConfig command = ExecConfig.builder()
                    .user("0")
                    .workDir("/tmp")
                    .envVars(Map.of("EXEC_MESSAGE", "process"))
                    .command(new String[]{"sh", "-c", """
                            printf '%s:%s\n' "$CONTAINER_MESSAGE" "$EXEC_MESSAGE" > output.txt
                            cat input.txt output.txt
                            printf 'diagnostic\n' >&2
                            """})
                    .build();
            Container.ExecResult result = container.execInContainer(command);
            assertThat(result.getExitCode()).isZero();
            assertThat(result.getStdout()).isEqualTo("updated payload\ncontainer:process\n");
            assertThat(result.getStderr()).isEqualTo("diagnostic\n");

            Path downloaded = directory.resolve("downloaded.txt");
            container.copyFileFromContainer("/tmp/output.txt", downloaded.toString());
            assertThat(Files.readString(downloaded)).isEqualTo("container:process\n");
        }
    }
}
