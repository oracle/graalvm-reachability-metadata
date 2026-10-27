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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSuiteTest {
    @Test
    void createsAndRunsTestCaseWithStringConstructor() {
        StringConstructorTestCase.runCount = 0;

        TestSuite suite = new TestSuite(StringConstructorTestCase.class);
        TestResult result = new TestResult();
        suite.run(result);

        assertThat(suite.countTestCases()).isEqualTo(1);
        assertThat(suite.testAt(0)).isInstanceOf(StringConstructorTestCase.class);
        assertThat(((TestCase) suite.testAt(0)).getName()).isEqualTo("testRuns");
        assertThat(StringConstructorTestCase.runCount).isEqualTo(1);
        assertThat(result.errorCount()).isZero();
        assertThat(result.failureCount()).isZero();
    }

    public static class StringConstructorTestCase extends TestCase {
        private static int runCount;

        public StringConstructorTestCase(String name) {
            super(name);
        }

        public void testRuns() {
            runCount++;
        }
    }
}
