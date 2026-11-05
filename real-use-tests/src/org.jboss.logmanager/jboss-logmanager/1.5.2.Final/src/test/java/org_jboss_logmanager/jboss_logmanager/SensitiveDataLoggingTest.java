/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.filters.SubstituteFilter;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.OutputStreamHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises message redaction through JBoss LogManager's SubstituteFilter subsystem.
public class SensitiveDataLoggingTest {
    @Test
    @Timeout(60)
    public void redactsSensitiveValuesBeforeWritingApplicationLogs() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        OutputStreamHandler handler = new OutputStreamHandler(output, new PatternFormatter("%s%n"));
        handler.setFilter(new SubstituteFilter("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[redacted]", true));

        Logger logger = LogContext.create().getLogger("application.accounts");
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try {
            logger.info("password reset sent to alice@example.com and copied to audit@example.com");

            String rendered = output.toString(StandardCharsets.UTF_8);
            assertEquals("password reset sent to [redacted] and copied to [redacted]"
                    + System.lineSeparator(), rendered);
            assertFalse(rendered.contains("alice@example.com"));
            assertFalse(rendered.contains("audit@example.com"));
        } finally {
            logger.clearHandlers();
            handler.close();
        }
    }
}
