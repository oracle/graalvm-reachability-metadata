/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.rules.Timeout;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runners.model.TestTimedOutException;

public class ReflectiveThreadMXBeanInnerHolderTest {

    @Test
    void initializesThreadManagementMethodsDuringStuckThreadInspection() {
        Result result = JUnitCore.runClasses(BusyFixture.class);

        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getException()).isInstanceOf(TestTimedOutException.class);
    }

    public static class BusyFixture {
        @Rule
        public final Timeout timeout = Timeout.builder()
                .withTimeout(1, TimeUnit.SECONDS)
                .withLookingForStuckThread(true)
                .build();

        public BusyFixture() {
        }

        @org.junit.Test
        public void remainsRunnableUntilInterrupted() {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.yield();
            }
        }
    }
}
