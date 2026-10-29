/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_core_jvm;

import org.junit.jupiter.api.Test;

public class ExceptionsConstructorKtInnerCreateSafeConstructorInlinedSafeCtor3Test extends CoroutineTestSupport {
    @Test
    void recoversExceptionUsingMessageConstructor() {
        MessageException original = new MessageException("channel unavailable");
        MessageException recovered = sendToClosedChannel(MessageException.class, original);
        assertRecovered(original, recovered);
    }

    public static class MessageException extends RuntimeException {
        public MessageException(String message) {
            super(message);
        }
    }
}
