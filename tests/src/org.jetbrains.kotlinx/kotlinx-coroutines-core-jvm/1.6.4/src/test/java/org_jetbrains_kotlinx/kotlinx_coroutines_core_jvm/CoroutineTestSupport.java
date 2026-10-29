/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_core_jvm;

import kotlinx.coroutines.channels.BufferOverflow;
import kotlinx.coroutines.channels.Channel;
import kotlinx.coroutines.channels.ChannelKt;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class CoroutineTestSupport {
    @BeforeAll
    static void enableStackTraceRecovery() {
        // Debug mode enables exception copying at coroutine boundaries.
        System.setProperty("kotlinx.coroutines.debug", "on");
        System.setProperty("kotlinx.coroutines.stacktrace.recovery", "true");
    }

    @SuppressWarnings("deprecation")
    static <E extends RuntimeException> E sendToClosedChannel(Class<E> failureType, E failure) {
        Channel<String> channel = ChannelKt.Channel(1, BufferOverflow.SUSPEND, null);
        assertTrue(channel.close(failure));
        return assertThrows(failureType, () -> channel.offer("undelivered"));
    }

    static void assertRecovered(RuntimeException original, RuntimeException recovered) {
        assertNotSame(original, recovered);
        assertEquals(original.getMessage(), recovered.getMessage());
        assertSame(original, recovered.getCause());
    }
}
