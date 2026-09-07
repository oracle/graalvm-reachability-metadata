/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import junit.runner.BaseTestRunner;
import org.junit.jupiter.api.Test;

public class BaseTestRunnerTest {

    @Test
    void loadsAndInvokesAStaticSuiteMethod() {
        RecordingTestRunner runner = new RecordingTestRunner();

        Test suite = runner.getTest(SuiteFixture.class.getName());
        TestResult result = new TestResult();
        suite.run(result);

        assertThat(runner.failureMessage).isNull();
        assertThat(result.wasSuccessful()).isTrue();
        assertThat(result.runCount()).isEqualTo(1);
    }

    public static class SuiteFixture {
        public static Test suite() {
            return new TestSuite(LegacyCase.class);
        }
    }

    public static class LegacyCase extends TestCase {
        public LegacyCase(String name) {
            super(name);
        }

        public void testExecute() {
        }
    }

    public static class RecordingTestRunner extends BaseTestRunner {
        private String failureMessage;

        @Override
        public void testStarted(String testName) {
        }

        @Override
        public void testEnded(String testName) {
        }

        @Override
        public void testFailed(int status, Test test, Throwable throwable) {
        }

        @Override
        protected void runFailed(String message) {
            failureMessage = message;
        }
    }
}
