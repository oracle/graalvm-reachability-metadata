/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import org.junit.experimental.max.MaxHistory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.io.File;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MaxHistoryTest {
    @TempDir
    Path temporaryFolder;

    @Test
    public void savesAndReadsSerializedHistory() throws Exception {
        File historyStore = temporaryFolder.resolve("max-history-store.bin").toFile();
        Description passingTest = Description.createTestDescription(HistoryFixture.class, "passingTest");
        Description failingTest = Description.createTestDescription(HistoryFixture.class, "failingTest");

        MaxHistory history = MaxHistory.forFolder(historyStore);
        RunListener listener = history.listener();
        recordPassingTest(listener, passingTest);
        recordFailingTest(listener, failingTest);
        listener.testRunFinished(new Result());

        assertTrue(historyStore.isFile());
        assertTrue(historyStore.length() > 0);

        MaxHistory restoredHistory = MaxHistory.forFolder(historyStore);
        Comparator<Description> comparator = restoredHistory.testComparator();
        Description newTest = Description.createTestDescription(HistoryFixture.class, "newTest");

        assertTrue(comparator.compare(newTest, passingTest) < 0);
        assertTrue(comparator.compare(failingTest, passingTest) < 0);
    }

    private static void recordPassingTest(RunListener listener, Description description) throws Exception {
        listener.testStarted(description);
        listener.testFinished(description);
    }

    private static void recordFailingTest(RunListener listener, Description description) throws Exception {
        listener.testStarted(description);
        listener.testFailure(new Failure(description, new AssertionError("expected failure")));
        listener.testFinished(description);
    }

    public static final class HistoryFixture {
        private HistoryFixture() {
        }
    }
}
