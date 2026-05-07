/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.aesh.readline.terminal.utils.Signals;
import org.junit.jupiter.api.Test;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class SignalsTest {
    private static final String[] TEST_SIGNALS = {"WINCH", "USR2", "USR1", "CONT"};

    @Test
    void signalRegistrationUtilitiesReachNativeSignalApi() throws Exception {
        TestSignal testSignal = installTemporaryIgnoredSignal();

        try {
            AtomicInteger registeredHandlerInvocations = new AtomicInteger();
            Signals.register(
                    testSignal.name,
                    registeredHandlerInvocations::incrementAndGet,
                    getClass().getClassLoader());

            Signal.raise(testSignal.signal);
            awaitInvocation(registeredHandlerInvocations);

            Signals.registerIgnore(testSignal.name);
            Signals.registerDefault(testSignal.name);

            AtomicInteger invokedHandlerInvocations = new AtomicInteger();
            AtomicReference<String> invokedSignalName = new AtomicReference<>();
            SignalHandler handler = handledSignal -> {
                invokedSignalName.set(handledSignal.getName());
                invokedHandlerInvocations.incrementAndGet();
            };

            Signals.invokeHandler(testSignal.name, handler);

            assertThat(invokedHandlerInvocations.get()).isEqualTo(1);
            assertThat(invokedSignalName.get()).isEqualTo(testSignal.name);
        } finally {
            Signal.handle(testSignal.signal, testSignal.previousHandler);
        }
    }

    private static TestSignal installTemporaryIgnoredSignal() {
        IllegalArgumentException lastFailure = null;
        for (String signalName : TEST_SIGNALS) {
            try {
                Signal signal = new Signal(signalName);
                SignalHandler previousHandler = Signal.handle(signal, SignalHandler.SIG_IGN);
                return new TestSignal(signalName, signal, previousHandler);
            } catch (IllegalArgumentException e) {
                lastFailure = e;
            }
        }
        throw new IllegalStateException("No test signal is supported on this platform", lastFailure);
    }

    private static void awaitInvocation(AtomicInteger invocations) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (invocations.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(invocations.get()).isGreaterThanOrEqualTo(1);
    }

    private static final class TestSignal {
        private final String name;
        private final Signal signal;
        private final SignalHandler previousHandler;

        private TestSignal(String name, Signal signal, SignalHandler previousHandler) {
            this.name = name;
            this.signal = signal;
            this.previousHandler = previousHandler;
        }
    }
}
