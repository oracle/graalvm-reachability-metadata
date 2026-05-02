/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.utils.Signals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class SignalsTest {

    @Test
    @Timeout(10)
    void signalHandlersCanBeRegisteredIgnoredAndRestoredToDefault() {
        AtomicInteger invocations = new AtomicInteger();

        try {
            assertDoesNotThrow(() -> Signals.register("WINCH", invocations::incrementAndGet));
            assertDoesNotThrow(() -> Signals.registerIgnore("WINCH"));
        } finally {
            assertDoesNotThrow(() -> Signals.registerDefault("WINCH"));
        }

        assertThat(invocations.get()).isZero();
    }
}
