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
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises the SLF4J facade integration with JBoss LogManager for application logging.
public class Slf4jFacadeLoggingTest {
    @Test
    @Timeout(60)
    public void routesParameterizedApplicationLogsToConfiguredHandler() {
        String loggerName = "application.fulfillment";
        LogContext logContext = LogContext.getLogContext();
        Logger logger = logContext.getLogger(loggerName);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        OutputStreamHandler handler = new OutputStreamHandler(output,
                new PatternFormatter("%p [%c] %s%n"));
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try {
            org.slf4j.Logger applicationLogger = LoggerFactory.getLogger(loggerName);
            applicationLogger.info("shipment {} is {}", "shipment-42", "ready");

            assertEquals("INFO [application.fulfillment] shipment shipment-42 is ready"
                    + System.lineSeparator(), output.toString(StandardCharsets.UTF_8));
        } finally {
            logger.removeHandler(handler);
            handler.close();
        }
    }
}
