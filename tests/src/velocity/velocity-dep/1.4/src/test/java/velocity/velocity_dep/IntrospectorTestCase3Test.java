/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.velocity.test.IntrospectorTestCase3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class IntrospectorTestCase3Test {
    @org.junit.jupiter.api.Test
    void suiteBuildsFromCompiledTestClass() throws Throwable {
        clearCachedClass("class$org$apache$velocity$test$IntrospectorTestCase3");

        Test suite = IntrospectorTestCase3.suite();

        assertInstanceOf(TestSuite.class, suite);
        assertEquals(1, suite.countTestCases());
    }

    @org.junit.jupiter.api.Test
    void resolvesAndInvokesOverloadedPrimitiveMethods() throws Throwable {
        clearCachedClass("class$org$apache$velocity$test$IntrospectorTestCase3$MethodProvider");
        IntrospectorTestCase3 testCase = new IntrospectorTestCase3("testSimple");

        testCase.testSimple();
    }

    private static void clearCachedClass(String fieldName) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                IntrospectorTestCase3.class,
                MethodHandles.lookup());
        VarHandle cachedClass = lookup.findStaticVarHandle(IntrospectorTestCase3.class, fieldName, Class.class);

        cachedClass.set(null);
    }
}
