/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import junit.framework.AssertionFailedError;
import junit.framework.TestResult;
import junit.runner.BaseTestRunner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BaseTestRunnerTest {
    private static final CountingTest SUITE = new CountingTest();
    private static int suiteInvocationCount;

    @Test
    public void loadsAndInvokesSuiteMethodByClassName() {
        RecordingTestRunner runner = new RecordingTestRunner();
        runner.setLoading(false);
        int invocationsBefore = suiteInvocationCount;

        junit.framework.Test test = runner.getTest(BaseTestRunnerTest.class.getName());

        assertSame(SUITE, test);
        assertTrue(suiteInvocationCount > invocationsBefore);
        assertNull(runner.failureMessage);
    }

    public static junit.framework.Test suite() {
        suiteInvocationCount++;
        return SUITE;
    }

    public static final class CountingTest implements junit.framework.Test {
        @Override
        public int countTestCases() {
            return 1;
        }

        @Override
        public void run(TestResult result) {
            result.startTest(this);
            result.endTest(this);
        }
    }

    private static final class RecordingTestRunner extends BaseTestRunner {
        private String failureMessage;

        @Override
        public void addError(junit.framework.Test test, Throwable error) {
        }

        @Override
        public void addFailure(junit.framework.Test test, AssertionFailedError failure) {
        }

        @Override
        public void startTest(junit.framework.Test test) {
        }

        @Override
        public void endTest(junit.framework.Test test) {
        }

        public void testStarted(String testName) {
        }

        public void testEnded(String testName) {
        }

        public void testFailed(int status, junit.framework.Test test, Throwable error) {
        }

        @Override
        protected void runFailed(String message) {
            failureMessage = message;
        }

        @Override
        protected void clearStatus() {
            failureMessage = null;
        }
    }
}
