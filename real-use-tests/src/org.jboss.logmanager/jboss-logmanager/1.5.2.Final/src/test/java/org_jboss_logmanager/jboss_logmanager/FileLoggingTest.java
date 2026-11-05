/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.handlers.FileHandler;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises file logging for an application that persists formatted log events.
public class FileLoggingTest {
    @Test
    @Timeout(60)
    public void writesFormattedApplicationEventsToAFile() throws Exception {
        Path temporaryDirectory = Files.createTempDirectory("jboss-logmanager-file-test");
        Path logFile = temporaryDirectory.resolve("application.log");
        FileHandler handler = new FileHandler(logFile.toFile());
        handler.setFormatter(new PatternFormatter("%p [%c] %s%n"));

        Logger logger = LogContext.create().getLogger("application.billing");
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try {
            logger.info("invoice invoice-42 paid");
            logger.warning("invoice invoice-43 requires review");
            handler.flush();

            assertEquals("INFO [application.billing] invoice invoice-42 paid"
                    + System.lineSeparator()
                    + "WARNING [application.billing] invoice invoice-43 requires review"
                    + System.lineSeparator(), Files.readString(logFile, StandardCharsets.UTF_8));
        } finally {
            logger.clearHandlers();
            handler.close();
            Files.deleteIfExists(logFile);
            Files.deleteIfExists(temporaryDirectory);
        }
    }
}
