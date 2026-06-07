/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class ManagementFactoryInnerFactoryHolderTest {

    @Test
    void disableOnDebugReadsRuntimeBeanThroughJunitManagementFactory() throws Throwable {
        DisableOnDebug disableOnDebug = new DisableOnDebug((base, description) -> base);

        assertThat(disableOnDebug.isDebugging()).isEqualTo(isDebugging());

        AtomicBoolean evaluated = new AtomicBoolean();
        Statement baseStatement = new Statement() {
            @Override
            public void evaluate() {
                evaluated.set(true);
            }
        };

        disableOnDebug.apply(baseStatement, Description.createTestDescription(getClass(), "runtimeBean"))
                .evaluate();

        assertThat(evaluated).isTrue();
    }

    private static boolean isDebugging() {
        List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        return inputArguments.stream()
                .anyMatch(argument -> argument.equals("-Xdebug") || argument.startsWith("-agentlib:jdwp"));
    }
}
