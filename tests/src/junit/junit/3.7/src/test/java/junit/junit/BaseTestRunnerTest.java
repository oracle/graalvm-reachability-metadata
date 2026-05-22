/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import junit.runner.BaseTestRunner;
import org.junit.jupiter.api.Test;

public class BaseTestRunnerTest {
    @Test
    void loadsSuiteClassAndInvokesSuiteMethodByName() {
        SuiteBackedTestCase.executedNames.clear();
        RecordingTestRunner runner = new RecordingTestRunner();

        Object loadedTest = runner.getTest(SuiteProvider.class.getName());

        assertThat(runner.failures).isEmpty();
        assertThat(runner.clearedStatusCount).isEqualTo(1);
        assertThat(loadedTest).isInstanceOf(TestSuite.class);

        TestResult result = new TestResult();
        ((TestSuite) loadedTest).run(result);

        assertThat(result.runCount()).isEqualTo(1);
        assertThat(result.failureCount()).isZero();
        assertThat(result.errorCount()).isZero();
        assertThat(SuiteBackedTestCase.executedNames).containsExactly("testRunsFromSuiteMethod");
    }

    public static final class SuiteProvider {
        private SuiteProvider() {
        }

        public static TestSuite suite() {
            return new TestSuite(SuiteBackedTestCase.class);
        }
    }

    public static final class SuiteBackedTestCase extends TestCase {
        private static final List<String> executedNames = new ArrayList<>();

        public SuiteBackedTestCase(String name) {
            super(name);
        }

        public void testRunsFromSuiteMethod() {
            executedNames.add(getName());
        }
    }

    private static final class RecordingTestRunner extends BaseTestRunner {
        private final List<String> failures = new ArrayList<>();
        private int clearedStatusCount;

        @Override
        public void testStarted(String testName) {
        }

        @Override
        public void testEnded(String testName) {
        }

        @Override
        public void testFailed(int status, junit.framework.Test test, Throwable throwable) {
        }

        @Override
        protected void runFailed(String message) {
            failures.add(message);
        }

        @Override
        protected void clearStatus() {
            clearedStatusCount++;
        }
    }
}
