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
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises custom application logging levels registered with a JBoss LogManager context.
public class CustomLevelLoggingTest {
    @Test
    @Timeout(60)
    public void logsAnAuditEventAtAnApplicationDefinedLevel() {
        LogContext logContext = LogContext.create();
        Level auditLevel = new ApplicationLevel("AUDIT", 850);
        logContext.registerLevel(auditLevel);
        Logger logger = logContext.getLogger("application.security");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        OutputStreamHandler handler = new OutputStreamHandler(output,
                new PatternFormatter("%p [%c] %s%n"));
        logger.setUseParentHandlers(false);
        logger.setLevelName("AUDIT");
        logger.addHandler(handler);

        try {
            assertEquals(auditLevel, logContext.getLevelForName("AUDIT"));
            assertEquals(auditLevel, logger.getLevel());
            assertTrue(logger.isLoggable(auditLevel));

            logger.log(auditLevel, "privileged export completed for account-42");

            assertEquals("AUDIT [application.security] privileged export completed for account-42"
                    + System.lineSeparator(), output.toString(StandardCharsets.UTF_8));
        } finally {
            logger.removeHandler(handler);
            handler.close();
            logContext.unregisterLevel(auditLevel);
        }
    }

    private static final class ApplicationLevel extends Level {
        private ApplicationLevel(String name, int value) {
            super(name, value);
        }
    }
}
