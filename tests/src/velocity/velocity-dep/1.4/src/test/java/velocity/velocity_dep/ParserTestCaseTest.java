/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import junit.framework.TestSuite;
import org.apache.velocity.test.ParserTestCase;
import org.apache.velocity.util.EnumerationIterator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParserTestCaseTest {
    @Test
    void exposesJUnit3SuiteForParserTests() throws Throwable {
        clearCachedTestClass();

        Object suite = ParserTestCase.suite();

        assertTrue(suite instanceof TestSuite);
        assertEquals(3, ((TestSuite) suite).countTestCases());
    }

    @Test
    void compilerCompatibilityHelperLoadsNamedClass() throws Throwable {
        MethodHandle classLoaderHelper = lookupForParserTestCase().findStatic(
                ParserTestCase.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> loadedClass = (Class<?>) classLoaderHelper.invoke("org.apache.velocity.util.EnumerationIterator");

        assertEquals(EnumerationIterator.class, loadedClass);
    }

    @Test
    void runsUpstreamParserValidationScenarios() throws Exception {
        ParserTestCase testCase = new ParserTestCase("ParserTestCase");

        testCase.testEquals();
        testCase.testMacro();
        testCase.testArgs();
    }

    private static void clearCachedTestClass() throws Throwable {
        VarHandle cachedClass = lookupForParserTestCase().findStaticVarHandle(
                ParserTestCase.class,
                "class$org$apache$velocity$test$ParserTestCase",
                Class.class);

        cachedClass.set(null);
    }

    private static MethodHandles.Lookup lookupForParserTestCase() throws IllegalAccessException {
        return MethodHandles.privateLookupIn(ParserTestCase.class, MethodHandles.lookup());
    }
}
