/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.internal.AssumptionViolatedException;
import org.junit.jupiter.api.Test;

class JunitTest {

    @Test
    @SuppressWarnings("deprecation")
    void createsAssumptionViolation() {
        AssumptionViolatedException exception = new AssumptionViolatedException("skipped");

        assertThat(exception.getMessage()).contains("skipped");
    }
}
