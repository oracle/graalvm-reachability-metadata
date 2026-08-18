/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_log4j12;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class Log4jLoggerAdapterTest {
    @Test
    void logsWithTheLog4jBackend() {
        String loggerName = Log4jLoggerAdapterTest.class.getName();
        CapturingAppender appender = new CapturingAppender();
        Level previousLevel = LogManager.getLogger(loggerName).getLevel();
        LogManager.getLogger(loggerName).setLevel(Level.WARN);
        LogManager.getLogger(loggerName).addAppender(appender);

        try {
            Logger logger = LoggerFactory.getLogger(loggerName);
            logger.warn("adapter message");

            assertThat(appender.events)
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getRenderedMessage()).isEqualTo("adapter message");
                    });
        } finally {
            LogManager.getLogger(loggerName).removeAppender(appender);
            LogManager.getLogger(loggerName).setLevel(previousLevel);
            appender.close();
        }
    }

    private static final class CapturingAppender extends AppenderSkeleton {
        private final List<LoggingEvent> events = new ArrayList<LoggingEvent>();

        @Override
        protected void append(LoggingEvent event) {
            events.add(event);
        }

        @Override
        public void close() {
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }
}
