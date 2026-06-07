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
import org.junit.jupiter.api.Test;

public class TestSuiteTest {

    @Test
    void createsTestCaseWithStringConstructor() {
        junit.framework.Test test = TestSuite.createTest(StringConstructorCase.class, "testPasses");

        assertThat(test).isInstanceOf(StringConstructorCase.class);
        assertThat(((StringConstructorCase) test).getName()).isEqualTo("testPasses");

        TestResult result = new TestResult();
        test.run(result);

        assertThat(result.runCount()).isEqualTo(1);
        assertThat(result.failureCount()).isZero();
        assertThat(result.errorCount()).isZero();
    }

    @Test
    void createsTestCaseWithNoArgumentConstructorAndAssignsName() {
        junit.framework.Test test = TestSuite.createTest(NoArgumentConstructorCase.class, "testPasses");

        assertThat(test).isInstanceOf(NoArgumentConstructorCase.class);
        assertThat(((NoArgumentConstructorCase) test).getName()).isEqualTo("testPasses");

        TestResult result = new TestResult();
        test.run(result);

        assertThat(result.runCount()).isEqualTo(1);
        assertThat(result.failureCount()).isZero();
        assertThat(result.errorCount()).isZero();
    }

    public static class StringConstructorCase extends TestCase {
        public StringConstructorCase(String name) {
            super(name);
        }

        public void testPasses() {
            assertEquals("testPasses", getName());
        }
    }

    public static class NoArgumentConstructorCase extends TestCase {
        public void testPasses() {
            assertEquals("testPasses", getName());
        }
    }
}
