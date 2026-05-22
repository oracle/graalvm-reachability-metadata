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
import org.junit.jupiter.api.Test;

public class TestSuiteTest {
    @Test
    void buildsAndRunsTestCasesWithStringConstructors() {
        StringConstructorTestCase.executedNames.clear();
        TestSuite suite = new TestSuite(StringConstructorTestCase.class);

        TestResult result = new TestResult();
        suite.run(result);

        assertThat(suite.countTestCases()).isEqualTo(1);
        assertThat(result.runCount()).isEqualTo(1);
        assertThat(result.failureCount()).isZero();
        assertThat(result.errorCount()).isZero();
        assertThat(StringConstructorTestCase.executedNames).containsExactly("testRunsFromStringConstructor");
    }

    @Test
    void buildsAndRunsTestCasesWithNoArgumentConstructors() {
        NoArgumentConstructorTestCase.executedNames.clear();
        TestSuite suite = new TestSuite(NoArgumentConstructorTestCase.class);

        TestResult result = new TestResult();
        suite.run(result);

        assertThat(suite.countTestCases()).isEqualTo(1);
        assertThat(result.runCount()).isEqualTo(1);
        assertThat(result.failureCount()).isZero();
        assertThat(result.errorCount()).isZero();
        assertThat(NoArgumentConstructorTestCase.executedNames).containsExactly("testRunsAfterSuiteAssignsName");
    }

    public static final class StringConstructorTestCase extends TestCase {
        private static final List<String> executedNames = new ArrayList<>();

        public StringConstructorTestCase(String name) {
            super(name);
        }

        public void testRunsFromStringConstructor() {
            executedNames.add(getName());
        }
    }

    public static final class NoArgumentConstructorTestCase extends TestCase {
        private static final List<String> executedNames = new ArrayList<>();

        public NoArgumentConstructorTestCase() {
        }

        public void testRunsAfterSuiteAssignsName() {
            executedNames.add(getName());
        }
    }
}
