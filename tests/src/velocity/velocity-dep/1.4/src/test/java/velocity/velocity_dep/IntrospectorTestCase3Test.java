/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.velocity.test.IntrospectorTestCase3;
import org.junit.jupiter.api.Test;

public class IntrospectorTestCase3Test {
    @Test
    void createsJUnit3SuiteForUpstreamTestCase() throws Exception {
        clearClassLiteralCache("class$org$apache$velocity$test$IntrospectorTestCase3");

        final int testCaseCount = IntrospectorTestCase3.suite().countTestCases();

        assertThat(testCaseCount).isEqualTo(1);
    }

    @Test
    void resolvesPrimitiveAndNullOverloadsThroughVelocityIntrospector() throws Exception {
        clearClassLiteralCache("class$org$apache$velocity$test$IntrospectorTestCase3$MethodProvider");

        final IntrospectorTestCase3 testCase = new IntrospectorTestCase3("testSimple");

        testCase.testSimple();
    }

    @Test
    void resolvesClassThroughCompilerGeneratedClassLiteralHelper() throws Exception {
        final Method classResolver = IntrospectorTestCase3.class.getDeclaredMethod("class$", String.class);
        classResolver.setAccessible(true);

        final Object resolvedClass = classResolver.invoke(null, IntrospectorTestCase3.class.getName());

        assertThat(resolvedClass).isSameAs(IntrospectorTestCase3.class);
    }

    private static void clearClassLiteralCache(String fieldName) throws Exception {
        final Field cacheField = IntrospectorTestCase3.class.getDeclaredField(fieldName);
        cacheField.setAccessible(true);
        cacheField.set(null, null);
    }
}
