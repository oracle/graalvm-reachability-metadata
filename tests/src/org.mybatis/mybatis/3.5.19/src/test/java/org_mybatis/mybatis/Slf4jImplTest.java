/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Slf4jImplTest {
    @Test
    void dispatchesMessagesToALocationAwareSlf4jBackend() {
        String loggerName = "mybatis.location-aware-test";
        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
        Level previousLevel = logger.getLevel();
        boolean previousAdditive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.TRACE);
        logger.setAdditive(false);
        try {
            Log log = new Slf4jImpl(loggerName);
            assertTrue(log.isDebugEnabled());
            assertTrue(log.isTraceEnabled());
            log.trace("binding parameter");
            log.debug("executing query");
            log.warn("slow query");
            assertEquals(List.of("binding parameter", "executing query", "slow query"),
                    appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
            assertEquals(List.of(Level.TRACE, Level.DEBUG, Level.WARN),
                    appender.list.stream().map(ILoggingEvent::getLevel).toList());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditive);
        }
    }
}
