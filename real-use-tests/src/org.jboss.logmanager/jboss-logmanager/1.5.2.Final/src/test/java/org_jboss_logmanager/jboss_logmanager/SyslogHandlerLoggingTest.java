/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.handlers.SyslogHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises RFC 5424 syslog delivery for an application that forwards deployment events to a collector.
public class SyslogHandlerLoggingTest {
    @Test
    @Timeout(60)
    public void writesDeploymentEventsAsRfc5424Messages() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SyslogHandler handler = new SyslogHandler();
        handler.setSyslogType(SyslogHandler.SyslogType.RFC5424);
        handler.setAppName("orders");
        handler.setHostname("app.example");
        handler.setOutputStream(output);

        Logger logger = LogContext.create().getLogger("application.deployments");
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try {
            logger.info("deployment completed");
            handler.flush();

            String rendered = output.toString(StandardCharsets.UTF_8);
            assertTrue(rendered.startsWith("<"));
            assertTrue(rendered.contains(">1 "));
            assertTrue(rendered.contains(" app.example orders "));
            assertTrue(rendered.contains(" application.deployments - "));
            assertTrue(rendered.contains("deployment completed"));
        } finally {
            logger.clearHandlers();
            handler.close();
        }
    }
}
