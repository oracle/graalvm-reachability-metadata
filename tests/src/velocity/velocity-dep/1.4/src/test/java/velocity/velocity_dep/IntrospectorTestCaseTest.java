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

import org.apache.velocity.test.IntrospectorTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class IntrospectorTestCaseTest {
    @Test
    void runUpstreamPrimitiveIntrospectionTest() {
        IntrospectorTestCase testCase = new IntrospectorTestCase("IntrospectorTestCase");

        testCase.runTest();
    }

    @Test
    void compilerCompatibilityHelperLoadsMethodProviderClass() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                IntrospectorTestCase.class,
                MethodHandles.lookup());
        MethodHandle classLoaderHelper = lookup.findStatic(
                IntrospectorTestCase.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> loadedClass = (Class<?>) classLoaderHelper.invoke(
                "org.apache.velocity.test.IntrospectorTestCase$MethodProvider");

        assertSame(IntrospectorTestCase.MethodProvider.class, loadedClass);
    }
}
