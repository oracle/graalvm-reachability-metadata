/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class ReflectiveRuntimeMXBeanInnerHolderTest {

    @Test
    void initializesTheRuntimeManagementMethodUsedByDisableOnDebug() throws Throwable {
        AtomicBoolean evaluated = new AtomicBoolean();
        DisableOnDebug disableOnDebug = new DisableOnDebug(new IdentityRule());
        Statement base = new Statement() {
            @Override
            public void evaluate() {
                evaluated.set(true);
            }
        };

        disableOnDebug.apply(base, Description.createSuiteDescription("fixture")).evaluate();

        assertThat(evaluated).isTrue();
    }

    public static class IdentityRule implements TestRule {
        @Override
        public Statement apply(Statement base, Description description) {
            return base;
        }
    }
}
