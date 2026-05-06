/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.LogMF;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogMFTest {
    private static final String BUNDLE_NAME = "ch_qos_reload4j.reload4j.LogMFMessages";
    private static final String BUNDLE_KEY = "logmf.bundle.message";
    private static final String LOGGER_NAME = "reload4j.logmf.resource-bundle";

    @Test
    void logrbFormatsMessageFromResourceBundle() {
        Logger logger = Logger.getLogger(LOGGER_NAME);
        RecordingAppender appender = new RecordingAppender();
        logger.setLevel(Level.DEBUG);
        logger.setAdditivity(false);
        logger.addAppender(appender);

        try {
            LogMF.logrb(logger, Level.INFO, BUNDLE_NAME, BUNDLE_KEY, "argument");

            assertThat(appender.events).hasSize(1);
            LoggingEvent event = appender.events.get(0);
            assertThat(event.getLevel()).isSameAs(Level.INFO);
            assertThat(event.getRenderedMessage()).isEqualTo("resource bundle argument");
        } finally {
            logger.removeAppender(appender);
            logger.setLevel(null);
            logger.setAdditivity(true);
            appender.close();
        }
    }

    public static final class RecordingAppender extends AppenderSkeleton {
        private final List<LoggingEvent> events = new ArrayList<>();

        @Override
        protected void append(LoggingEvent event) {
            events.add(event);
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }
}
