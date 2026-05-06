/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.test.IntrospectorTestCase3;
import org.junit.jupiter.api.Test;

public class IntrospectorTestCase3Test {
    @Test
    void createsJUnit3SuiteForUpstreamTestCase() {
        final int testCaseCount = IntrospectorTestCase3.suite().countTestCases();

        assertThat(testCaseCount).isEqualTo(1);
    }

    @Test
    void resolvesPrimitiveAndNullOverloadsThroughVelocityIntrospector() throws Exception {
        final IntrospectorTestCase3 testCase = new IntrospectorTestCase3("testSimple");

        testCase.testSimple();
    }
}
