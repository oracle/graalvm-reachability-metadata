/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import java.lang.management.ManagementFactory;
import java.util.List;

import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgJunitInternalManagementManagementFactoryInnerFactoryHolderTest {
    @Test
    void disableOnDebugReadsRuntimeArgumentsThroughManagementFactoryWrapper() throws Throwable {
        RecordingRule rule = new RecordingRule();
        DisableOnDebug disableOnDebug = new DisableOnDebug(rule);

        Statement statement = disableOnDebug.apply(new Statement() {
            @Override
            public void evaluate() {
            }
        }, Description.createTestDescription(getClass(), "sample"));
        statement.evaluate();

        assertThat(disableOnDebug.isDebugging()).isEqualTo(hasDebugArgument(ManagementFactory.getRuntimeMXBean()
                .getInputArguments()));
        assertThat(rule.applied).isEqualTo(!disableOnDebug.isDebugging());
    }

    private static boolean hasDebugArgument(List<String> arguments) {
        for (String argument : arguments) {
            if ("-Xdebug".equals(argument) || argument.startsWith("-agentlib:jdwp")) {
                return true;
            }
        }
        return false;
    }

    private static final class RecordingRule implements TestRule {
        private boolean applied;

        @Override
        public Statement apply(Statement base, Description description) {
            applied = true;
            return base;
        }
    }
}
