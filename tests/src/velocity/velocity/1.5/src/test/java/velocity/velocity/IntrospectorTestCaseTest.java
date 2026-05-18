/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import java.lang.reflect.Field;

import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeSingleton;
import org.apache.velocity.test.IntrospectorTestCase;
import org.junit.jupiter.api.Test;

public class IntrospectorTestCaseTest {
    @Test
    void runsVelocityPrimitiveIntrospectionTestCase() throws Exception {
        RuntimeSingleton.setProperty(
                RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
                "org.apache.velocity.runtime.log.NullLogSystem");
        RuntimeSingleton.init();
        clearMethodProviderClassCache();

        IntrospectorTestCase testCase = new IntrospectorTestCase("runTest");
        testCase.runTest();
    }

    private static void clearMethodProviderClassCache() throws Exception {
        /*
         * The Velocity 1.4 artifact was compiled with a synthetic `Class` cache for
         * `MethodProvider.class`. Clear it so `runTest()` exercises the original
         * `Class.forName(String)` resolution path even if test discovery initialized it.
         */
        Field classCache = IntrospectorTestCase.class.getDeclaredField(
                "class$org$apache$velocity$test$IntrospectorTestCase$MethodProvider");
        classCache.setAccessible(true);
        classCache.set(null, null);
    }
}
