/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.rules.TestName;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

public class FrameworkFieldTest {

    @Test
    void obtainsAnInstanceRuleFromTheTestFixture() {
        Result result = JUnitCore.runClasses(RuleFixture.class);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(RuleFixture.observedMethodName).isEqualTo("execute");
    }

    public static class RuleFixture {
        private static String observedMethodName;

        @Rule
        public final TestName testName = new TestName();

        public RuleFixture() {
        }

        @org.junit.Test
        public void execute() {
            observedMethodName = testName.getMethodName();
        }
    }
}
