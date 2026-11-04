/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.handlers.OutputStreamHandler;
import org.jboss.logmanager.handlers.QueueHandler;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises startup log buffering and replay through JBoss LogManager's QueueHandler API.
public class StartupLogQueueTest {
    @Test
    @Timeout(60)
    public void replaysTheMostRecentStartupMessagesWhenTheHandlerIsReady() {
        String lineSeparator = System.lineSeparator();
        QueueHandler startupQueue = new QueueHandler(2);
        startupQueue.setFormatter(new PatternFormatter("%s%n"));

        Logger logger = LogContext.create().getLogger("application.bootstrap");
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(startupQueue);

        try {
            logger.info("application starting");
            logger.info("configuration loaded");
            logger.info("ready to accept requests");

            assertEquals(2, startupQueue.getQueue().length);
            assertArrayEquals(new String[] {
                    "configuration loaded" + lineSeparator,
                    "ready to accept requests" + lineSeparator
            }, startupQueue.getQueueAsStrings());

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            OutputStreamHandler applicationHandler = new OutputStreamHandler(output,
                    new PatternFormatter("%p [%c] %s%n"));
            startupQueue.addHandler(applicationHandler);
            try {
                startupQueue.replay();

                assertEquals("INFO [application.bootstrap] configuration loaded" + lineSeparator
                        + "INFO [application.bootstrap] ready to accept requests" + lineSeparator,
                        output.toString(StandardCharsets.UTF_8));
            } finally {
                startupQueue.removeHandler(applicationHandler);
                applicationHandler.close();
            }
        } finally {
            logger.removeHandler(startupQueue);
            startupQueue.close();
        }
    }
}
