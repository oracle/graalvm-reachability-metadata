/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ibatis.ibatis_sqlmap;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.ibatis.common.logging.Log;
import com.ibatis.common.logging.LogFactory;
import org.apache.log4j.LogManager;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.apache.log4j.Level.DEBUG;
import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class LogFactoryTest {
    @Test
    void createsLoggersThatDeliverMessagesToJavaLogging() {
        Logger logger = Logger.getLogger(LogFactoryTest.class.getName());
        Level previous = logger.getLevel();
        boolean parentHandlers = logger.getUseParentHandlers();
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
                messages.clear();
            }

            @Override
            public void close() {
                messages.clear();
            }
        };
        handler.setLevel(Level.ALL);
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            LogFactory.selectJavaLogging();
            Log log = LogFactory.getLog(LogFactoryTest.class);
            assertThat(log.isDebugEnabled()).isTrue();
            log.debug("java debug");
            log.warn("java warning");
            log.error("java error");
            assertThat(messages).containsExactly("java debug", "java warning", "java error");
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previous);
            logger.setUseParentHandlers(parentHandlers);
            handler.close();
        }
    }

    @Test
    void selectsLog4jAndDeliversMessagesToItsAppender() {
        var logger = LogManager.getLogger(LogFactoryTest.class);
        var previous = logger.getLevel();
        StringWriter output = new StringWriter();
        WriterAppender appender = new WriterAppender(new PatternLayout("%m%n"), output);
        logger.setLevel(DEBUG);
        logger.addAppender(appender);
        try {
            LogFactory.selectLog4JLogging();
            Log log = LogFactory.getLog(LogFactoryTest.class);
            assertThat(log.isDebugEnabled()).isTrue();
            log.debug("log4j debug");
            log.warn("log4j warning");
            assertThat(output.toString()).contains("log4j debug", "log4j warning");
        } finally {
            logger.removeAppender(appender);
            logger.setLevel(previous);
            appender.close();
            LogFactory.selectJavaLogging();
        }
    }
}
