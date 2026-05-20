/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.testng.junit.JUnitMethodFinder;

public class JUnitMethodFinderTest {
    private static final String TEST_NAME = "constructor-supplied-test-name";

    @Test
    void instantiatesJUnitTestCaseWithStringConstructor() throws Exception {
        Object instance = instantiateJUnitTestCase(StringConstructorJUnitTestCase.class);

        assertThat(instance).isInstanceOf(StringConstructorJUnitTestCase.class);
        assertThat(((StringConstructorJUnitTestCase) instance).testName).isEqualTo(TEST_NAME);
    }

    @Test
    void instantiatesJUnitTestCaseWithNoArgumentConstructorFallback() throws Exception {
        Object instance = instantiateJUnitTestCase(NoArgumentConstructorJUnitTestCase.class);

        assertThat(instance).isInstanceOf(NoArgumentConstructorJUnitTestCase.class);
        assertThat(((NoArgumentConstructorJUnitTestCase) instance).constructed).isTrue();
    }

    private static Object instantiateJUnitTestCase(Class<?> testCaseClass) throws Exception {
        JUnitMethodFinder finder = new JUnitMethodFinder(TEST_NAME, null);
        Method instantiateMethod = JUnitMethodFinder.class.getDeclaredMethod("instantiate", Class.class);
        instantiateMethod.setAccessible(true);
        return instantiateMethod.invoke(finder, testCaseClass);
    }

    public static final class StringConstructorJUnitTestCase {
        private final String testName;

        public StringConstructorJUnitTestCase(String testName) {
            this.testName = testName;
        }
    }

    public static final class NoArgumentConstructorJUnitTestCase {
        private final boolean constructed;

        public NoArgumentConstructorJUnitTestCase() {
            constructed = true;
        }
    }
}
