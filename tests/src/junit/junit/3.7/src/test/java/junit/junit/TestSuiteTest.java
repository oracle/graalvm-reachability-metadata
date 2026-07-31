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

        TestCase test = (TestCase) TestSuite.createTest(StringConstructorTestCase.class, "testRuns");
        TestResult result = new TestResult();
        test.run(result);

        assertThat(test).isInstanceOf(StringConstructorTestCase.class);
        assertThat(test.getName()).isEqualTo("testRuns");
        assertThat(StringConstructorTestCase.runCount).isEqualTo(1);
        assertThat(result.errorCount()).isZero();
        assertThat(result.failureCount()).isZero();
    }

    @Test
    void createsAndRunsTestCaseWithDefaultConstructor() {
        DefaultConstructorTestCase.runCount = 0;

        TestCase test = (TestCase) TestSuite.createTest(DefaultConstructorTestCase.class, "testRuns");
        TestResult result = new TestResult();
        test.run(result);

        assertThat(test).isInstanceOf(DefaultConstructorTestCase.class);
        assertThat(test.getName()).isEqualTo("testRuns");
        assertThat(DefaultConstructorTestCase.runCount).isEqualTo(1);
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

    public static class DefaultConstructorTestCase extends TestCase {
        private static int runCount;

        public DefaultConstructorTestCase() {
        }

        public void testRuns() {
            runCount++;
        }
    }
}
