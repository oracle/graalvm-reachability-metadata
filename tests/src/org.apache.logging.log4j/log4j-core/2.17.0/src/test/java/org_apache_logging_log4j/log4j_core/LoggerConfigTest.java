/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.impl.DefaultLogEventFactory;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class LoggerConfigTest {
    @Test
    void deliversEventsCreatedByTheConfiguredEventFactory() {
        LoggerConfig logger = new LoggerConfig("application", Level.INFO, false);
        assertInstanceOf(ApplicationLogEventFactory.class, logger.getLogEventFactory());
        TestSupport.RecordingAppender appender = new TestSupport.RecordingAppender();
        appender.start();
        logger.addAppender(appender, null, null);
        try {
            logger.log("application", getClass().getName(), null, Level.INFO, new SimpleMessage("factory event"), null);
            assertEquals("factory event", appender.lastEvent().getMessage().getFormattedMessage());
            assertEquals("application", appender.lastEvent().getLoggerName());
        } finally {
            logger.removeAppender(appender.getName());
            appender.stop();
        }
    }

    public static class ApplicationLogEventFactory extends DefaultLogEventFactory {
    }
}
