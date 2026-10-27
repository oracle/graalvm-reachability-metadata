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

    @Test(timeout = 30000L)
    public void registersInvokesAndRestoresSignalHandler() throws Exception {
        Logger logger = Logger.getLogger("org.jline");
        Level originalLevel = logger.getLevel();
        AtomicInteger invocations = new AtomicInteger();
        Object originalHandler = null;
        logger.setLevel(Level.FINEST);

        try {
            originalHandler = Signals.register(
                    SIGNAL,
                    new CountingHandler(invocations),
                    SignalsTest.class.getClassLoader());
            assertNotNull(originalHandler);

            Process process = new ProcessBuilder(
                    "kill", "-" + SIGNAL, Long.toString(ProcessHandle.current().pid()))
                    .start();
            try {
                assertTrue(process.waitFor(10L, TimeUnit.SECONDS));
                assertEquals(0, process.exitValue());
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10L);
            while (invocations.get() == 0 && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(25L);
            }
            assertTrue(invocations.get() > 0);

            Object registeredHandler = Signals.registerDefault(SIGNAL);
            assertNotNull(registeredHandler);
            Signals.unregister(SIGNAL, registeredHandler);
        } finally {
            if (originalHandler != null) {
                Signals.unregister(SIGNAL, originalHandler);
            }
            logger.setLevel(originalLevel);
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
            return "counting WINCH handler";
        }
    }
}
