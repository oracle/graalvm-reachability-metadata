/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import junit.runner.BaseTestRunner;
import org.junit.jupiter.api.Test;

public class BaseTestRunnerTest {

    @Test
    void loadsSuiteClassAndInvokesStaticSuiteMethod() {
        RecordingRunner runner = new RecordingRunner();

        junit.framework.Test test = runner.getTest(SuiteProvider.class.getName());

        assertThat(test).isInstanceOf(TestSuite.class);
        assertThat(runner.failedMessage).isNull();

        TestResult result = new TestResult();
        test.run(result);

        assertThat(result.runCount()).isEqualTo(1);
        assertThat(result.failureCount()).isZero();
        assertThat(result.errorCount()).isZero();
    }

    public static class SuiteProvider {
        public static junit.framework.Test suite() {
            TestSuite suite = new TestSuite();
            suite.addTest(new PassingTestCase("testPasses"));
            return suite;
        }
    }

    public static class PassingTestCase extends TestCase {
        public PassingTestCase(String name) {
            super(name);
        }

        public void testPasses() {
            assertEquals("testPasses", getName());
        }
    }

    private static class RecordingRunner extends BaseTestRunner {
        private String failedMessage;

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
            failedMessage = message;
        }
    }
}
