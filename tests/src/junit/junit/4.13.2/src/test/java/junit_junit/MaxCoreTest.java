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
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.junit.Ignore;
import org.junit.experimental.max.MaxCore;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.jupiter.api.Test;
import org.junit.runner.Request;
import org.junit.runner.Result;

public class MaxCoreTest {

    @Test
    void reportsAnInvalidLegacyTestClass() throws Exception {
        Path directory = Files.createTempDirectory("junit-max-core");
        File historyFile = directory.resolve("history.bin").toFile();
        try {
            TestSuite malformedSuite = new TestSuite(InvalidLegacyCase.class);
            Request request = Request.runner(new JUnit38ClassRunner(malformedSuite));

            Result result = MaxCore.storedLocally(historyFile).run(request);

            assertThat(result.getRunCount()).isEqualTo(1);
            assertThat(result.getFailureCount()).isEqualTo(1);
        } finally {
            Files.deleteIfExists(historyFile.toPath());
            Files.deleteIfExists(directory);
        }
    }

    @Ignore
    public static class InvalidLegacyCase extends TestCase {
        private InvalidLegacyCase() {
        }

        public void testExecute() {
        }
    }
}
