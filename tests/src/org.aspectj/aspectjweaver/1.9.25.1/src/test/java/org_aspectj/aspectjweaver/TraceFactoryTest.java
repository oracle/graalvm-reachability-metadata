/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.aspectj.weaver.tools.Jdk14TraceFactory;
import org.aspectj.weaver.tools.Trace;
import org.aspectj.weaver.tools.TraceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TraceFactoryTest {
    @Test
    void discoversJdkTraceFactoryAndRoutesMessagesToLogger() {
        TraceFactory factory = TraceFactory.getTraceFactory();
        assertThat(factory).isInstanceOf(Jdk14TraceFactory.class);
        Logger logger = Logger.getLogger(TraceFactoryTest.class.getName());
        RecordingHandler handler = new RecordingHandler();
        Level previousLevel = logger.getLevel();
        boolean previousParentHandlers = logger.getUseParentHandlers();
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        try {
            Trace trace = factory.getTrace(TraceFactoryTest.class);
            trace.info("weaving diagnostic");
            assertThat(handler.message).isEqualTo("weaving diagnostic");
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousParentHandlers);
            handler.close();
        }
    }

    private static class RecordingHandler extends Handler {
        private String message;

        @Override
        public void publish(LogRecord record) {
            message = record.getMessage();
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
