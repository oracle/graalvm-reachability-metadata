/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.util.ArrayList;
import java.util.List;
import java.util.ListResourceBundle;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.LogMF;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogMFTest {

    @Test
    void loadsMessagePatternsFromResourceBundles() {
        Logger logger = Logger.getLogger(LogMFTest.class.getName() + "." + System.nanoTime());
        RecordingAppender appender = new RecordingAppender();
        logger.setAdditivity(false);
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        try {
            LogMF.logrb(logger, Level.INFO, TestMessages.class.getName(), "message", "native image");

            assertThat(appender.getMessages()).containsExactly("Hello native image");
        } finally {
            logger.removeAppender(appender);
        }
    }

    public static final class TestMessages extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                    { "message", "Hello {0}" }
            };
        }
    }

    private static final class RecordingAppender extends AppenderSkeleton {
        private final List<String> messages = new ArrayList<>();

        @Override
        protected void append(LoggingEvent event) {
            messages.add(event.getRenderedMessage());
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }

        private List<String> getMessages() {
            return messages;
        }
    }
}
