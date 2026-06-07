/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.experimental.max.MaxCore;
import org.junit.jupiter.api.Test;
import org.junit.runner.Result;

public class MaxHistoryTest {

    @Test
    void persistsAndReloadsHistoryThroughMaxCore() throws IOException {
        Path temporaryDirectory = Files.createTempDirectory("junit-max-history");
        try {
            Path historyStore = temporaryDirectory.resolve("history.ser");

            Result firstRun = MaxCore.storedLocally(historyStore.toFile())
                    .run(PassingJUnit4Case.class);
            assertThat(firstRun.wasSuccessful()).isTrue();
            assertThat(firstRun.getRunCount()).isEqualTo(1);
            assertThat(historyStore).isRegularFile();
            assertThat(Files.size(historyStore)).isGreaterThan(0L);

            Result secondRun = MaxCore.storedLocally(historyStore.toFile())
                    .run(PassingJUnit4Case.class);
            assertThat(secondRun.wasSuccessful()).isTrue();
            assertThat(secondRun.getRunCount()).isEqualTo(1);
        } finally {
            deleteRecursively(temporaryDirectory);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(MaxHistoryTest::deleteIfExists);
        }
    }

    private static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete " + path, exception);
        }
    }

    public static class PassingJUnit4Case {
        @org.junit.Test
        public void passes() {
            assertThat(true).isTrue();
        }
    }
}
