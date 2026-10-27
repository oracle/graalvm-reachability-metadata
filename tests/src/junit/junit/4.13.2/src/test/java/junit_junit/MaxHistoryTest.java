/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.experimental.max.MaxCore;
import org.junit.jupiter.api.Test;
import org.junit.runner.Result;

public class MaxHistoryTest {

    @Test
    void persistsAndReloadsTestRunHistory() throws Exception {
        Path directory = Files.createTempDirectory("junit-max-history");
        File historyFile = directory.resolve("history.bin").toFile();
        try {
            Result first = MaxCore.storedLocally(historyFile).run(Fixture.class);
            Result second = MaxCore.storedLocally(historyFile).run(Fixture.class);

            assertThat(first.wasSuccessful()).isTrue();
            assertThat(second.wasSuccessful()).isTrue();
            assertThat(historyFile).isFile().isNotEmpty();
        } finally {
            Files.deleteIfExists(historyFile.toPath());
            Files.deleteIfExists(directory);
        }
    }

    public static class Fixture {
        public Fixture() {
        }

        @org.junit.Test
        public void executes() {
        }
    }
}
