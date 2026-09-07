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
import org.junit.jupiter.api.Test;

public class TestSuiteTest {

    @Test
    void createsLegacyTestsWithBothSupportedConstructorShapes() {
        Test stringConstructed = TestSuite.createTest(StringConstructorCase.class, "testExecute");
        Test noArgConstructed = TestSuite.createTest(NoArgConstructorCase.class, "testExecute");
        TestResult result = new TestResult();

        stringConstructed.run(result);
        noArgConstructed.run(result);

        assertThat(result.runCount()).isEqualTo(2);
        assertThat(result.wasSuccessful()).isTrue();
        assertThat(StringConstructorCase.executions).isEqualTo(1);
        assertThat(NoArgConstructorCase.executions).isEqualTo(1);
    }

    public static class StringConstructorCase extends TestCase {
        private static int executions;

        public StringConstructorCase(String name) {
            super(name);
        }

        public void testExecute() {
            executions++;
        }
    }

    public static class NoArgConstructorCase extends TestCase {
        private static int executions;

        public NoArgConstructorCase() {
        }

        public void testExecute() {
            executions++;
        }
    }
}
