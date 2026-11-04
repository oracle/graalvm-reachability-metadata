/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.handlers.SizeRotatingFileHandler;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises size-based log rotation for an application that retains recent log files.
public class SizeRotatingFileLoggingTest {
    @Test
    @Timeout(60)
    public void rotatesAFullLogFileBeforeWritingTheNextEvent() throws Exception {
        Path temporaryDirectory = Files.createTempDirectory("jboss-logmanager-size-test");
        Path logFile = temporaryDirectory.resolve("application.log");
        Path backupFile = temporaryDirectory.resolve("application.log.1");
        Logger logger = LogContext.create().getLogger("application.fulfillment");
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        SizeRotatingFileHandler handler = new SizeRotatingFileHandler(logFile.toFile(), false, 60, 2);
        handler.setFormatter(new PatternFormatter("%s%n"));
        logger.addHandler(handler);

        String firstMessage = "shipment shipment-42 was dispatched from warehouse north terminal";
        String secondMessage = "shipment shipment-43 was dispatched from warehouse south terminal";
        try {
            logger.info(firstMessage);
            logger.info(secondMessage);
            handler.flush();
            handler.close();

            assertTrue(Files.exists(backupFile));
            assertEquals(firstMessage + System.lineSeparator(),
                    Files.readString(backupFile, StandardCharsets.UTF_8));
            assertEquals(secondMessage + System.lineSeparator(),
                    Files.readString(logFile, StandardCharsets.UTF_8));
        } finally {
            logger.removeHandler(handler);
            handler.close();
            Files.deleteIfExists(logFile);
            Files.deleteIfExists(backupFile);
            Files.deleteIfExists(temporaryDirectory);
        }
    }
}
