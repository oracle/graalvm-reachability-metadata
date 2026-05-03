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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class SignalsTest {

    private static final String SIGNAL = "WINCH";

    @Test
    @Timeout(10)
    void registersRunnableDefaultAndIgnoreSignalHandlers() throws Exception {
        AtomicInteger invocations = new AtomicInteger();

        try {
            Signals.registerDefault(SIGNAL);
            Signals.register(SIGNAL, invocations::incrementAndGet, SignalsTest.class.getClassLoader());

            sendSignalToCurrentProcess();
            awaitInvocation(invocations, 1);

            Signals.registerIgnore(SIGNAL);
            sendSignalToCurrentProcess();
            TimeUnit.MILLISECONDS.sleep(200L);

            assertThat(invocations).hasValue(1);
        } finally {
            Signals.registerDefault(SIGNAL);
        }
    }

    private static void sendSignalToCurrentProcess() throws Exception {
        Process process = new ProcessBuilder(
                "kill",
                "-" + SIGNAL,
                Long.toString(ProcessHandle.current().pid()))
                .start();

        assertThat(process.waitFor(5L, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
    }

    private static void awaitInvocation(AtomicInteger invocations, int expectedValue) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (invocations.get() < expectedValue && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(25L);
        }
        assertThat(invocations).hasValue(expectedValue);
    }
}
