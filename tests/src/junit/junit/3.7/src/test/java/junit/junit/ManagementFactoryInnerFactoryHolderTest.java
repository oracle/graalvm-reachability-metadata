/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class ManagementFactoryInnerFactoryHolderTest {
    @Test
    void disableOnDebugReadsRuntimeArgumentsThroughManagementFactoryHolder() {
        TestRule timeoutRule = Timeout.millis(250L);
        DisableOnDebug rule = new DisableOnDebug(timeoutRule);

        Statement originalStatement = new Statement() {
            @Override
            public void evaluate() {
            }
        };
        Statement appliedStatement = rule.apply(
                originalStatement,
                Description.createTestDescription(ManagedJUnit4Test.class, "passes"));

        if (rule.isDebugging()) {
            assertThat(appliedStatement).isSameAs(originalStatement);
        } else {
            assertThat(appliedStatement).isNotSameAs(originalStatement);
        }
    }

    public static final class ManagedJUnit4Test {
        @org.junit.Test
        public void passes() {
        }
    }
}
