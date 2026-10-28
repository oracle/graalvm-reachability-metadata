/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.druid.support.logging.Jdk14LoggingImpl;
import com.alibaba.druid.support.logging.Log;
import com.alibaba.druid.support.logging.Log4jImpl;
import com.alibaba.druid.support.logging.LogFactory;
import com.alibaba.druid.support.logging.SLF4JImpl;
import org.apache.log4j.LogManager;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class LogFactoryTest {
    @Test
    void selectsBackendsAndDeliversMessages() {
        String name = "druid.test.backends";
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Level previous = context.getLogger(name).getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        context.getLogger(name).addAppender(appender);
        context.getLogger(name).setLevel(Level.INFO);
        try {
            Log log = LogFactory.getLog(name);
            assertInstanceOf(SLF4JImpl.class, log);
            log.info("pool monitoring ready");
            assertTrue(appender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().equals("pool monitoring ready")));
        } finally {
            context.getLogger(name).detachAppender(appender);
            appender.stop();
            context.getLogger(name).setLevel(previous);
        }

        StringWriter output = new StringWriter();
        WriterAppender log4jAppender = new WriterAppender(new PatternLayout("%m%n"), output);
        LogManager.getLogger(name).addAppender(log4jAppender);
        try {
            LogFactory.selectLog4JLogging();
            Log log = LogFactory.getLog(name);
            assertInstanceOf(Log4jImpl.class, log);
            log.error("pool diagnostic");
            assertTrue(output.toString().contains("pool diagnostic"));
        } finally {
            LogManager.getLogger(name).removeAppender(log4jAppender);
            log4jAppender.close();
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        StreamHandler handler = new StreamHandler(bytes, new SimpleFormatter());
        Logger jul = Logger.getLogger(name);
        jul.addHandler(handler);
        try {
            LogFactory.selectJavaLogging();
            Log log = LogFactory.getLog(name);
            assertInstanceOf(Jdk14LoggingImpl.class, log);
            log.error("connection diagnostic");
            handler.flush();
            assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("connection diagnostic"));
        } finally {
            jul.removeHandler(handler);
            handler.close();
        }
    }
}
