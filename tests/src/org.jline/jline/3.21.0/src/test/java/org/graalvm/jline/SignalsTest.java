/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.utils.Signals;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SignalsTest {

    private static final String SIGNAL = "WINCH";

    @Test
    public void registersInvokesAndRestoresSignalHandlers() throws Exception {
        Logger logger = Logger.getLogger("org.jline");
        Level previousLevel = logger.getLevel();
        AtomicInteger invocations = new AtomicInteger();
        Object previousHandler = null;
        logger.setLevel(Level.FINEST);

        try {
            previousHandler = Signals.register(
                    SIGNAL,
                    new CountingHandler(invocations),
                    SignalsTest.class.getClassLoader());
            assertNotNull(previousHandler);

            Process signal = new ProcessBuilder(
                    "kill",
                    "-" + SIGNAL,
                    Long.toString(ProcessHandle.current().pid()))
                    .start();
            try {
                assertTrue(signal.waitFor(10L, TimeUnit.SECONDS));
                assertEquals(0, signal.exitValue());
            } finally {
                if (signal.isAlive()) {
                    signal.destroyForcibly();
                }
            }

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10L);
            while (invocations.get() == 0 && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(25L);
            }
            assertEquals(1, invocations.get());

            Object registeredHandler = Signals.registerDefault(SIGNAL);
            assertNotNull(registeredHandler);
            Signals.unregister(SIGNAL, registeredHandler);
        } finally {
            if (previousHandler != null) {
                Signals.unregister(SIGNAL, previousHandler);
            }
            logger.setLevel(previousLevel);
        }
    }

    private static final class CountingHandler implements Runnable {
        private final AtomicInteger invocations;

        private CountingHandler(AtomicInteger invocations) {
            this.invocations = invocations;
        }

        @Override
        public void run() {
            invocations.incrementAndGet();
        }

        @Override
        public String toString() {
            return "counting signal handler";
        }
    }
}
