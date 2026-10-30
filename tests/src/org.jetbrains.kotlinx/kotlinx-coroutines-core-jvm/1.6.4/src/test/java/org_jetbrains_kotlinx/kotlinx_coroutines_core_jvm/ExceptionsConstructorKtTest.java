/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_core_jvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ExceptionsConstructorKtTest extends CoroutineTestSupport {
    @Test
    void recoversStandardExceptionWhenSendingToClosedChannel() {
        IllegalStateException original = new IllegalStateException("channel closed");
        IllegalStateException recovered = sendToClosedChannel(IllegalStateException.class, original);
        assertRecovered(original, recovered);
    }

    @Test
    void preservesAdditionalExceptionStateWhenSendingToClosedChannel() {
        StatefulException original = new StatefulException("channel closed", 42);
        StatefulException received = sendToClosedChannel(StatefulException.class, original);
        assertSame(original, received);
        assertEquals(42, received.getRequestId());
        assertEquals("channel closed", received.getMessage());
    }

    public static class StatefulException extends RuntimeException {
        private final int requestId;

        public StatefulException(String message, int requestId) {
            super(message);
            this.requestId = requestId;
        }

        public int getRequestId() {
            return requestId;
        }
    }
}
