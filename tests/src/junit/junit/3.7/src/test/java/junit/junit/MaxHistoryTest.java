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
import org.junit.experimental.max.MaxCore;
import org.junit.jupiter.api.Test;
import org.junit.runner.Result;

public class MaxHistoryTest {
    @Test
    void maxCorePersistsAndReloadsRunHistory() throws IOException {
        File historyDirectory = createTemporaryDirectory();
        File historyFile = new File(historyDirectory, "history.ser");

        try {
            MaxCore firstCore = MaxCore.storedLocally(historyFile);
            Result firstResult = firstCore.run(PassingJUnit4Test.class);

            assertThat(firstResult.wasSuccessful()).isTrue();
            assertThat(firstResult.getRunCount()).isEqualTo(1);
            assertThat(historyFile).isFile();
            assertThat(historyFile.length()).isPositive();

            MaxCore secondCore = MaxCore.storedLocally(historyFile);
            Result secondResult = secondCore.run(PassingJUnit4Test.class);

            assertThat(secondResult.wasSuccessful()).isTrue();
            assertThat(secondResult.getRunCount()).isEqualTo(1);
        } finally {
            deleteRecursively(historyDirectory);
        }
    }

    public static final class PassingJUnit4Test {
        @org.junit.Test
        public void succeeds() {
        }
    }

    private static File createTemporaryDirectory() throws IOException {
        File temporaryDirectory = new File(System.getProperty("java.io.tmpdir"));
        for (int index = 0; index < 100; index++) {
            File historyDirectory = new File(
                    temporaryDirectory,
                    "junit-max-history-" + System.nanoTime() + "-" + index);
            if (historyDirectory.mkdir()) {
                return historyDirectory;
            }
        }
        throw new IOException("Could not create MaxHistory directory in " + temporaryDirectory);
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }

        if (!file.delete() && file.exists()) {
            throw new IOException("Could not delete " + file);
        }
    }
}
