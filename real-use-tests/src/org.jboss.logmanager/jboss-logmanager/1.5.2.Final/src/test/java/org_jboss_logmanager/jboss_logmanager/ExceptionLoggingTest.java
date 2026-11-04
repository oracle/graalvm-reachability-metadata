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

import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises exception logging so an application can preserve a failure and its stack trace in its log output.
public class ExceptionLoggingTest {
    @Test
    @Timeout(60)
    public void rendersApplicationFailureDetailsForAnOperationalError() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        OutputStreamHandler handler = new OutputStreamHandler(output,
                new PatternFormatter("%p [%c] %s%n%E"));
        LogContext logContext = LogContext.create();
        Logger logger = logContext.getLogger("application.payments");
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        IllegalStateException failure = new IllegalStateException("payment gateway unavailable");
        try {
            logger.log(Level.SEVERE, "payment authorization failed", failure);
            handler.flush();

            String rendered = output.toString(StandardCharsets.UTF_8);
            assertTrue(rendered.startsWith("SEVERE [application.payments] payment authorization failed"));
            assertTrue(rendered.contains("java.lang.IllegalStateException: payment gateway unavailable"));
            assertTrue(rendered.contains("at " + ExceptionLoggingTest.class.getName()
                    + ".rendersApplicationFailureDetailsForAnOperationalError"));
        } finally {
            logger.removeHandler(handler);
            handler.close();
        }
    }
}
