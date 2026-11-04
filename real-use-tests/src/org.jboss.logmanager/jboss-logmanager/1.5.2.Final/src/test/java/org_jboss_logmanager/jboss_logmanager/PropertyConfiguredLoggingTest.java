/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.PropertyConfigurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises property-based logging configuration for an application that changes its logging pipeline at startup.
public class PropertyConfiguredLoggingTest {
    @Test
    @Timeout(60)
    public void configuresAFileLoggerFromApplicationProperties() throws Exception {
        Path temporaryDirectory = Files.createTempDirectory("jboss-logmanager-properties-test");
        Path logFile = temporaryDirectory.resolve("billing.log");
        LogContext logContext = LogContext.create();
        Logger logger = logContext.getLogger("application.billing");
        Properties properties = new Properties();
        properties.setProperty("loggers", "application.billing");
        properties.setProperty("logger.application.billing.level", "INFO");
        properties.setProperty("logger.application.billing.handlers", "billingFile");
        properties.setProperty("logger.application.billing.useParentHandlers", "false");
        properties.setProperty("handler.billingFile", "org.jboss.logmanager.handlers.FileHandler");
        properties.setProperty("handler.billingFile.level", "INFO");
        properties.setProperty("handler.billingFile.formatter", "billingFormatter");
        properties.setProperty("handler.billingFile.properties", "fileName,append");
        properties.setProperty("handler.billingFile.fileName", logFile.toString());
        properties.setProperty("handler.billingFile.append", "false");
        properties.setProperty("formatter.billingFormatter", "org.jboss.logmanager.formatters.PatternFormatter");
        properties.setProperty("formatter.billingFormatter.properties", "pattern");
        properties.setProperty("formatter.billingFormatter.pattern", "%p [%c] %s%n");

        try {
            new PropertyConfigurator(logContext).configure(properties);

            logger.info("invoice invoice-42 paid");
            logger.warning("invoice invoice-43 requires review");
            logger.getHandlers()[0].flush();

            assertEquals("INFO [application.billing] invoice invoice-42 paid"
                    + System.lineSeparator()
                    + "WARNING [application.billing] invoice invoice-43 requires review"
                    + System.lineSeparator(), Files.readString(logFile, StandardCharsets.UTF_8));
        } finally {
            for (Handler handler : logger.clearHandlers()) {
                handler.close();
            }
            Files.deleteIfExists(logFile);
            Files.deleteIfExists(temporaryDirectory);
        }
    }
}
