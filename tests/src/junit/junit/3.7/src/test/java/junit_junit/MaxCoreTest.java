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

import junit.framework.TestCase;
import org.junit.experimental.max.MaxCore;
import org.junit.jupiter.api.Test;
import org.junit.runner.Request;
import org.junit.runner.Result;

public class MaxCoreTest {

    @Test
    void runsMalformedJUnit3ClassThroughSortedMaxRequest() throws IOException {
        Path temporaryDirectory = Files.createTempDirectory("junit-max-core");
        try {
            Path historyStore = temporaryDirectory.resolve("history.ser");
            MaxCore maxCore = MaxCore.storedLocally(historyStore.toFile());

            Result result = maxCore.run(Request.aClass(MalformedJUnit3Case.class));

            assertThat(result.getRunCount()).isEqualTo(1);
            assertThat(result.getFailureCount()).isEqualTo(1);
            assertThat(result.wasSuccessful()).isFalse();
            assertThat(result.getFailures().get(0).getMessage())
                    .contains(MalformedJUnit3Case.class.getName());
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
                    .forEach(MaxCoreTest::deleteIfExists);
        }
    }

    private static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete " + path, exception);
        }
    }

    private static class MalformedJUnit3Case extends TestCase {
        public void testPasses() {
            assertTrue(true);
        }
    }
}
