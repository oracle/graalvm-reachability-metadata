/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_awaitility.awaitility;

import java.time.Duration;
import java.util.concurrent.Callable;

import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LambdaErrorMessageGeneratorTest {

    @Test
    void describesTimedOutCallableLambdaCondition() {
        ConditionTimeoutException timeout = assertThrows(
                ConditionTimeoutException.class,
                () -> await()
                        .pollDelay(Duration.ZERO)
                        .pollInterval(Duration.ofMillis(1))
                        .atMost(Duration.ofMillis(20))
                        .until(new Synthetic$$Lambda$Condition()));

        assertThat(timeout.getMessage())
                .contains("Condition with")
                .contains("Synthetic")
                .contains("was not fulfilled");
    }

    @SuppressWarnings("checkstyle:TypeName")
    private static final class Synthetic$$Lambda$Condition implements Callable<Boolean> {
        @Override
        public Boolean call() {
            return false;
        }
    }
}
