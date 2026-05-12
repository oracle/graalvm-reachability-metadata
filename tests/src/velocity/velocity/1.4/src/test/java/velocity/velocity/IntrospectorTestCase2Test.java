/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeSingleton;
import org.apache.velocity.test.IntrospectorTestCase2;
import org.junit.jupiter.api.Test;

public class IntrospectorTestCase2Test {
    @Test
    void resolvesMostSpecificOverloadAndRejectsAmbiguousMatch() throws Exception {
        RuntimeSingleton.setProperty(
                RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
                "org.apache.velocity.runtime.log.NullLogSystem");
        RuntimeSingleton.init();

        IntrospectorTestCase2 testCase = new IntrospectorTestCase2("runTest");
        testCase.runTest();
    }
}
