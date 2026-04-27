/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import junit.framework.TestResult;
import junit.textui.TestRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntrospectorTestCase3Test {
    @Test
    void resolvesOverloadedPrimitiveMethodsWithVelocityIntrospector() throws Exception {
        TestRunner runner = new TestRunner();

        TestResult result = runner.start(new String[] {"org.apache.velocity.test.IntrospectorTestCase3"});

        assertThat(result.errorCount()).isZero();
        assertThat(result.failureCount()).isZero();
        assertThat(result.runCount()).isEqualTo(1);
    }
}
