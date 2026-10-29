/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_core_jvm;

import org.junit.jupiter.api.Test;

public class ExceptionsConstructorKtInnerCreateSafeConstructorInlinedSafeCtor1Test extends CoroutineTestSupport {
    @Test
    void recoversExceptionUsingMessageAndCauseConstructor() {
        MessageAndCauseException original = new MessageAndCauseException("delivery failed", null);
        MessageAndCauseException recovered = sendToClosedChannel(MessageAndCauseException.class, original);
        assertRecovered(original, recovered);
    }

    public static class MessageAndCauseException extends RuntimeException {
        public MessageAndCauseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
