/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import junit.framework.TestResult;
import junit.framework.TestSuite;

import org.junit.Ignore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestSuiteTest {
    @Test
    public void createsTestCaseWithStringConstructor() {
        NamedConstructorFixture.lastConstructorName = null;

        junit.framework.Test test = TestSuite.createTest(NamedConstructorFixture.class, "testNamedConstructor");

        assertEquals("testNamedConstructor", NamedConstructorFixture.lastConstructorName);
        assertSame(NamedConstructorFixture.class, test.getClass());
    }

    @Test
    public void createsTestCaseWithNoArgumentConstructor() {
        DefaultConstructorFixture.lastInstance = null;

        junit.framework.Test test = TestSuite.createTest(DefaultConstructorFixture.class, "testDefaultConstructor");

        assertSame(DefaultConstructorFixture.lastInstance, test);
    }

    @Ignore
    public static class NamedConstructorFixture implements junit.framework.Test {
        private static String lastConstructorName;

        public NamedConstructorFixture(String name) {
            lastConstructorName = name;
        }

        public int countTestCases() {
            return 1;
        }

        public void run(TestResult result) {
        }
    }

    @Ignore
    public static class DefaultConstructorFixture implements junit.framework.Test {
        private static DefaultConstructorFixture lastInstance;

        public DefaultConstructorFixture() {
            lastInstance = this;
        }

        public int countTestCases() {
            return 1;
        }

        public void run(TestResult result) {
        }
    }
}
