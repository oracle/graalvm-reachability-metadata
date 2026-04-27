/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import junit.framework.TestResult;
import org.apache.velocity.test.IntrospectorTestCase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntrospectorTestCaseTest {
    @Test
    void resolvesPrimitiveArgumentMethodsWithVelocityIntrospector() {
        TestResult result = new TestResult();

        IntrospectorTestCase.suite().run(result);

        assertThat(result.errorCount()).isZero();
        assertThat(result.failureCount()).isZero();
        assertThat(result.runCount()).isEqualTo(1);
    }
}
