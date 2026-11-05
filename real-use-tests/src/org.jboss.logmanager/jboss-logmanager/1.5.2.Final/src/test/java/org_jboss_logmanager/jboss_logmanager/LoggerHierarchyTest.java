/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.OutputStreamHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises hierarchical logger configuration for an application that routes service events through a shared handler.
public class LoggerHierarchyTest {
    @Test
    @Timeout(60)
    public void sendsChildLoggerEventsToTheConfiguredApplicationHandler() {
        LogContext logContext = LogContext.create();
        Logger applicationLogger = logContext.getLogger("application");
        Logger checkoutLogger = logContext.getLogger("application.orders");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        OutputStreamHandler handler = new OutputStreamHandler(output,
                new PatternFormatter("%p [%c] %s%n"));
        applicationLogger.setLevel(Level.INFO);
        applicationLogger.setUseParentHandlers(false);
        applicationLogger.addHandler(handler);

        try {
            assertEquals("application", checkoutLogger.getParent().getName());
            assertEquals(Level.INFO.intValue(), checkoutLogger.getEffectiveLevel());

            checkoutLogger.info("checkout completed for order-42");

            assertEquals("INFO [application.orders] checkout completed for order-42"
                    + System.lineSeparator(), output.toString(StandardCharsets.UTF_8));
        } finally {
            checkoutLogger.clearHandlers();
            applicationLogger.removeHandler(handler);
            handler.close();
        }
    }
}
