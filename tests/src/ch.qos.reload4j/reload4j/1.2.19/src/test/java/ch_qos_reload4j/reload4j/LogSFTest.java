/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.LogSF;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class LogSFTest {
    private static final String LOGGER_NAME = LogSFTest.class.getName() + ".resourceBundle";
    private static final String BUNDLE_NAME = "ch_qos_reload4j.reload4j.LogSFTestMessages";

    @AfterEach
    void resetRepository() {
        LogManager.resetConfiguration();
    }

    @Test
    void logrbFormatsSlf4jStyleMessageLoadedFromResourceBundle() {
        Logger logger = Logger.getLogger(LOGGER_NAME);
        CapturingAppender appender = new CapturingAppender();
        logger.removeAllAppenders();
        logger.setAdditivity(false);
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        LogSF.logrb(logger, Level.INFO, BUNDLE_NAME, "localized.info", new Object[] { "alpha", 7 });

        assertThat(appender.lastEvent).isNotNull();
        assertThat(appender.lastEvent.getLevel()).isSameAs(Level.INFO);
        assertThat(appender.lastEvent.getRenderedMessage()).isEqualTo("Bundle message alpha 7");
    }

    private static final class CapturingAppender extends AppenderSkeleton {
        private LoggingEvent lastEvent;

        @Override
        protected void append(LoggingEvent event) {
            lastEvent = event;
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
