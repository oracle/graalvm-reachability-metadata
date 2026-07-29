/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseTestRunnerTest {
    @Test
    void loadsAndRunsSuiteMethod() {
        SuiteTestCase.runCount = 0;
        TestRunner runner = new TestRunner();

        TestSuite suite = (TestSuite) runner.getTest(SuiteProvider.class.getName());
        TestResult result = new TestResult();
        suite.run(result);

        assertThat(suite.countTestCases()).isEqualTo(1);
        assertThat(SuiteTestCase.runCount).isEqualTo(1);
        assertThat(result.errorCount()).isZero();
        assertThat(result.failureCount()).isZero();
    }

    public static class SuiteProvider {
        public static TestSuite suite() {
            return new TestSuite(SuiteTestCase.class);
        }
    }

    public static class SuiteTestCase extends TestCase {
        public void testRuns() {
            runCount++;
        }

        private static int runCount;
    }
}
