/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_core_jvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

public class ExceptionsConstructorKtInnerCreateSafeConstructorInlinedSafeCtor4Test extends CoroutineTestSupport {
    @Test
    void recoversExceptionUsingNoArgumentConstructor() {
        EmptyException original = new EmptyException();
        EmptyException recovered = sendToClosedChannel(EmptyException.class, original);
        assertRecovered(original, recovered);
        assertNull(recovered.getMessage());
    }

    public static class EmptyException extends RuntimeException {
        public EmptyException() {
            super();
        }
    }
}
