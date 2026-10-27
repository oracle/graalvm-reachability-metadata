/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import junit.framework.TestCase;
import junit.framework.TestResult;
import org.junit.jupiter.api.Test;

public class TestCaseTest {

    @Test
    void invokesTheNamedLegacyTestMethod() {
        LegacyCase legacyCase = new LegacyCase("testRecordExecution");
        TestResult result = new TestResult();

        legacyCase.run(result);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(legacyCase.executed).isTrue();
    }

    public static class LegacyCase extends TestCase {
        private boolean executed;

        public LegacyCase(String name) {
            super(name);
        }

        public void testRecordExecution() {
            executed = true;
        }
    }
}
