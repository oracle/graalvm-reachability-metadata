/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.hsqldb.lib.FrameworkLogger;
import org.junit.jupiter.api.Test;

import static java.util.logging.Level.WARNING;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FrameworkLoggerTest {
    @Test
    void forwardsApplicationAndEndUserMessagesToLog4j() {
        String name = "hsqldb-test-" + UUID.randomUUID();
        Logger backend = (Logger) LogManager.getLogger(name);
        Level oldLevel = backend.getLevel();
        boolean oldAdditive = backend.isAdditive();
        RecordingAppender appender = new RecordingAppender();
        appender.start();
        backend.addAppender(appender);
        backend.setLevel(Level.ALL);
        backend.setAdditive(false);
        try {
            FrameworkLogger logger = FrameworkLogger.getLog(name);
            logger.info("application message");
            logger.enduserlog(WARNING, "end user message");
            assertEquals(List.of("application message", "end user message"), appender.messages);
        } finally {
            FrameworkLogger.clearLoggers(name);
            backend.removeAppender(appender);
            backend.setLevel(oldLevel);
            backend.setAdditive(oldAdditive);
            appender.stop();
        }
    }

    private static class RecordingAppender extends AbstractAppender {
        private final List<String> messages = new ArrayList<>();

        RecordingAppender() {
            super("capture", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }
    }
}
