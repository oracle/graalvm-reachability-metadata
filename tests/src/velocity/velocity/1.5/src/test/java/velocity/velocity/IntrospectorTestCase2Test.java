/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeSingleton;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.apache.velocity.test.IntrospectorTestCase2;
import org.junit.jupiter.api.Test;

public class IntrospectorTestCase2Test {
    @Test
    void resolvesMostSpecificOverloadAndRejectsAmbiguousMatch() throws Exception {
        RuntimeSingleton.setProperty(
                RuntimeConstants.RUNTIME_LOG_LOGSYSTEM,
                new NullLogSystem());
        resolveNestedClassThroughCompilerGeneratedHelper();
        clearClassLiteralCache("class$org$apache$velocity$test$IntrospectorTestCase2$Tester");
        clearClassLiteralCache("class$org$apache$velocity$test$IntrospectorTestCase2$Tester2");

        IntrospectorTestCase2 testCase = new IntrospectorTestCase2("runTest");
        testCase.runTest();
    }

    private static void resolveNestedClassThroughCompilerGeneratedHelper() throws Exception {
        /*
         * `IntrospectorTestCase2` was compiled with a package-private synthetic
         * `class$(String)` helper for nested class literals. Calling the helper
         * verifies the same class resolution path used by `runTest()`.
         */
        Method classResolver = IntrospectorTestCase2.class.getDeclaredMethod("class$", String.class);
        classResolver.setAccessible(true);

        Object resolvedClass = classResolver.invoke(
                null,
                "org.apache.velocity.test.IntrospectorTestCase2$Tester");

        assertSame(IntrospectorTestCase2.Tester.class, resolvedClass);
    }

    private static void clearClassLiteralCache(String fieldName) throws Exception {
        /*
         * The Velocity 1.4 artifact was compiled with synthetic `Class` caches for
         * the nested overload test classes. Clear them so `runTest()` exercises
         * the original `Class.forName(String)` resolution path.
         */
        Field classCache = IntrospectorTestCase2.class.getDeclaredField(fieldName);
        classCache.setAccessible(true);
        classCache.set(null, null);
    }
}
