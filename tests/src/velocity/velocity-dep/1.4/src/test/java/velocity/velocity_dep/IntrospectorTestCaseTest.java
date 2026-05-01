/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.velocity.test.IntrospectorTestCase;
import org.junit.jupiter.api.Test;

public class IntrospectorTestCaseTest {
    @Test
    void runUpstreamPrimitiveIntrospectionTest() {
        IntrospectorTestCase testCase = new IntrospectorTestCase("IntrospectorTestCase");

        testCase.runTest();
    }
}
