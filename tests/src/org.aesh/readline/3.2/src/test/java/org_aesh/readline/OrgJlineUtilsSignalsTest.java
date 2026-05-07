/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.utils.Signals;
import org.junit.jupiter.api.Test;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgJlineUtilsSignalsTest {
    private static final String[] TEST_SIGNALS = {"WINCH", "USR2", "USR1", "CONT"};

    @Test
    void signalRegistrationUsesJdkSignalReflection() throws Exception {
        TestSignal testSignal = installTemporaryIgnoredSignal();
        Logger logger = Logger.getLogger("org.jline");
        Level originalLevel = logger.getLevel();

        try {
            logger.setLevel(Level.FINEST);

            Signals.registerDefault(testSignal.name);

            AtomicInteger invocations = new AtomicInteger();
            Signals.register(testSignal.name, invocations::incrementAndGet, getClass().getClassLoader());
            Signal.raise(testSignal.signal);

            awaitInvocation(invocations);
        } finally {
            Signal.handle(testSignal.signal, testSignal.previousHandler);
            logger.setLevel(originalLevel);
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
