/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Order(0)
public class LogFactoryTest {

    @Test
    void createsLoggerWithRequestedName() {
        String loggerName = "factory-created-logger";
        Logger logger = Logger.getLogger(loggerName);
        CapturingHandler handler = new CapturingHandler();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try {
            Log log = LogFactory.getFactory().getInstance(loggerName);
            log.info("ready");

            assertThat(handler.getRecord()).isNotNull();
            assertThat(handler.getRecord().getLoggerName()).isEqualTo(loggerName);
            assertThat(handler.getRecord().getMessage()).isEqualTo("ready");
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }

    private static final class CapturingHandler extends Handler {
        private LogRecord record;

        @Override
        public void publish(LogRecord value) {
            record = value;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        public LogRecord getRecord() {
            return record;
        }
    }
}
