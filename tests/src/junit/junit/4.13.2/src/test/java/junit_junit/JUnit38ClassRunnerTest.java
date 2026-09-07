/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

public class JUnit38ClassRunnerTest {

    @Test
    void describesAndRunsAnAnnotatedLegacyTestMethod() {
        Result result = new JUnitCore().run(new TestSuite(LegacyCase.class));

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(result.getRunCount()).isEqualTo(1);
    }

    public static class LegacyCase extends TestCase {
        public LegacyCase(String name) {
            super(name);
        }

        @Deprecated
        public void testExecute() {
        }
    }
}
