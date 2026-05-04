/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.utils.Signals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class JlineSignalsTest {
    private static final String[] TEST_SIGNALS = {"WINCH", "CONT", "USR2", "USR1"};

    @Test
    @Timeout(10)
    void registersDefaultAndRunnableHandlersThroughJlineSignals() throws Exception {
        Logger logger = Logger.getLogger("org.jline");
        Level previousLevel = logger.getLevel();
        TestSignal testSignal = findSupportedSignal();

        try {
            logger.setLevel(Level.FINEST);

            Object previousDefaultHandler = Signals.registerDefault(testSignal.name);
            assertThat(previousDefaultHandler).isNotNull();

            AtomicInteger invocations = new AtomicInteger();
            Object previousRunnableHandler = Signals.register(
                    testSignal.name,
                    invocations::incrementAndGet,
                    JlineSignalsTest.class.getClassLoader());
            assertThat(previousRunnableHandler).isNotNull();

            Signal.raise(testSignal.signal);
            awaitInvocation(invocations);

            Signals.unregister(testSignal.name, previousRunnableHandler);
            Signals.unregister(testSignal.name, previousDefaultHandler);
        } finally {
            Signal.handle(testSignal.signal, testSignal.previousHandler);
            logger.setLevel(previousLevel);
        }
    }

    private static TestSignal findSupportedSignal() {
        IllegalArgumentException lastFailure = null;
        for (String signalName : TEST_SIGNALS) {
            try {
                Signal signal = new Signal(signalName);
                SignalHandler previousHandler = Signal.handle(signal, SignalHandler.SIG_IGN);
                Signal.handle(signal, previousHandler);
                return new TestSignal(signalName, signal, previousHandler);
            } catch (IllegalArgumentException e) {
                lastFailure = e;
            }
        }
        throw new IllegalStateException("No test signal is supported on this platform", lastFailure);
    }

    private static void awaitInvocation(AtomicInteger invocations) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (invocations.get() == 0 && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10L);
        }
        assertThat(invocations).hasValue(1);
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
