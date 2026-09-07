/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestTimedOutException;

public class ReflectiveThreadMXBeanInnerHolderTest {

    @Test
    void initializesThreadManagementMethodsDuringStuckThreadInspection() throws Throwable {
        Timeout timeout = Timeout.builder()
                .withTimeout(1, TimeUnit.SECONDS)
                .withLookingForStuckThread(true)
                .build();
        Statement busyStatement = new Statement() {
            @Override
            public void evaluate() {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.yield();
                }
            }
        };
        Statement timedStatement = timeout.apply(
                busyStatement, Description.createTestDescription(getClass(), "busyStatement"));

        boolean timedOut = false;
        try {
            timedStatement.evaluate();
        } catch (TestTimedOutException expected) {
            timedOut = true;
        }

        assertThat(timedOut).isTrue();
    }
}
