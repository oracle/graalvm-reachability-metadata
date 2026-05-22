/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;
import org.junit.experimental.max.MaxCore;
import org.junit.jupiter.api.Test;
import org.junit.runner.Result;

public class MaxCoreTest {
    @Test
    void reportsMalformedJUnit3TestClassesThroughMaxCore() throws IOException {
        abstract class MalformedJUnit3Case extends TestCase {
            MalformedJUnit3Case(String name) {
                super(name);
            }

            public void testCannotRunWithoutAPublicJUnit3Constructor() {
            }
        }

        File historyFile = createHistoryFile();

        try {
            MaxCore core = MaxCore.storedLocally(historyFile);
            Class<?> malformedClass = MalformedJUnit3Case.class;
            Result result = core.run(malformedClass);

            assertThat(result.wasSuccessful()).isFalse();
            assertThat(result.getRunCount()).isEqualTo(1);
            assertThat(result.getFailureCount()).isEqualTo(1);
            assertThat(result.getFailures()).hasSize(1);
            assertThat(result.getFailures().get(0).getMessage())
                    .contains(malformedClass.getName());
        } finally {
            deleteIfExists(historyFile);
        }
    }

    private static File createHistoryFile() throws IOException {
        File historyFile = File.createTempFile("junit-max-core-", ".ser");
        if (!historyFile.delete()) {
            throw new IOException("Could not delete empty MaxCore history file " + historyFile);
        }
        return historyFile;
    }

    private static void deleteIfExists(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not delete MaxCore history file " + file);
        }
    }
}
