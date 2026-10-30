/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_core_jvm;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ExceptionsConstructorKtInnerCreateSafeConstructorInlinedSafeCtor2Test extends CoroutineTestSupport {
    @Test
    void recoversExceptionUsingCauseConstructor() {
        IOException cause = new IOException("upstream disconnected");
        CauseException original = new CauseException(cause);
        CauseException recovered = sendToClosedChannel(CauseException.class, original);
        assertRecovered(original, recovered);
        assertEquals("upstream disconnected", recovered.getMessage());
        assertSame(cause, recovered.getCause().getCause());
    }

    public static class CauseException extends RuntimeException {
        public CauseException(Throwable cause) {
            super(cause.getMessage(), cause);
        }
    }
}
