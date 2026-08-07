/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import java.io.File;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.experimental.max.MaxHistory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgJunitExperimentalMaxMaxHistoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsAndReadsRememberedRunHistory() throws Exception {
        File historyFile = temporaryDirectory.resolve("max-history.ser").toFile();
        Description completedTest = Description.createTestDescription(SampleTestCase.class, "testSample");
        Description newTest = Description.createTestDescription(SampleTestCase.class, "testOther");

        MaxHistory history = MaxHistory.forFolder(historyFile);
        RunListener listener = history.listener();
        listener.testStarted(completedTest);
        listener.testFinished(completedTest);
        listener.testRunFinished(new Result());

        MaxHistory reloadedHistory = MaxHistory.forFolder(historyFile);
        Comparator<Description> comparator = reloadedHistory.testComparator();

        assertThat(historyFile).isFile();
        assertThat(historyFile.length()).isPositive();
        assertThat(comparator.compare(newTest, completedTest)).isLessThan(0);
    }

    public static class SampleTestCase {
        public void testSample() {
        }

        public void testOther() {
        }
    }
}
