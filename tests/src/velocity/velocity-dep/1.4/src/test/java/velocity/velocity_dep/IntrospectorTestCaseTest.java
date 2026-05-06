/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import java.lang.reflect.Field;

import org.apache.velocity.test.IntrospectorTestCase;
import org.junit.jupiter.api.Test;

public class IntrospectorTestCaseTest {
    @Test
    void resolvesPrimitiveMethodsThroughVelocityIntrospector() throws Exception {
        clearMethodProviderClassLiteralCache();
        final IntrospectorTestCase testCase = new IntrospectorTestCase("primitiveMethods");

        testCase.runTest();
    }

    private static void clearMethodProviderClassLiteralCache() throws Exception {
        final Field cacheField = IntrospectorTestCase.class.getDeclaredField(
                "class$org$apache$velocity$test$IntrospectorTestCase$MethodProvider");
        cacheField.setAccessible(true);
        cacheField.set(null, null);
    }
}
