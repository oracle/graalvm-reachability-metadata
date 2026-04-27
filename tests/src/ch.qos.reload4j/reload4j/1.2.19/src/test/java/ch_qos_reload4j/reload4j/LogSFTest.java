/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.LogSF;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

public class LogSFTest {
    private static final String BUNDLE_NAME = "ch_qos_reload4j.reload4j.LogSFTestMessages";

    @Test
    void logrbFormatsSlf4jStyleMessageFromNamedResourceBundle() {
        Logger logger = Logger.getLogger("reload4j.logsf." + UUID.randomUUID());
        CapturingAppender appender = new CapturingAppender();
        logger.setAdditivity(false);
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            LogSF.logrb(logger, Level.INFO, BUNDLE_NAME, "processed.items", new Object[] {"Alice", 7});

            assertThat(appender.messages()).containsExactly("User Alice processed 7 items");
        } finally {
            logger.removeAppender(appender);
        }
    }

    private static final class CapturingAppender extends AppenderSkeleton {
        private final List<String> messages = new ArrayList<>();

        @Override
        protected void append(LoggingEvent event) {
            messages.add(event.getRenderedMessage());
        }

        @Override
        public void close() {
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }

        private List<String> messages() {
            return messages;
        }
    }
}
