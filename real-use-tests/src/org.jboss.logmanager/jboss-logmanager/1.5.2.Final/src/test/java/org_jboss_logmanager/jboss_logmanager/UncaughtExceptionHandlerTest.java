/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.LoggingUncaughtExceptionHandler;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.OutputStreamHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises uncaught-exception handling so an application records failures from worker threads.
public class UncaughtExceptionHandlerTest {
    @Test
    @Timeout(60)
    public void recordsAnUncaughtWorkerFailureInApplicationLogs() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        OutputStreamHandler handler = new OutputStreamHandler(output,
                new PatternFormatter("%p [%c] %s%n%E"));
        LogContext logContext = LogContext.create();
        Logger logger = logContext.getLogger("application.worker");
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        Thread worker = new Thread(() -> {
            throw new IllegalStateException("inventory worker failed");
        }, "inventory-worker");
        worker.setUncaughtExceptionHandler(new LoggingUncaughtExceptionHandler(logger));

        try {
            worker.start();
            worker.join();

            assertFalse(worker.isAlive());
            String rendered = output.toString(StandardCharsets.UTF_8);
            assertTrue(rendered.contains("ERROR [application.worker] Uncaught exception"));
            assertTrue(rendered.contains("java.lang.IllegalStateException: inventory worker failed"));
        } finally {
            logger.removeHandler(handler);
            handler.close();
        }
    }
}
