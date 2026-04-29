/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.log4j_over_slf4j;

import org.apache.log4j.Appender;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Category;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.NDC;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.WriterAppender;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.NullEnumeration;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggerFactory;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.xml.DOMConfigurator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static java.util.logging.Level.ALL;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Log4j_over_slf4jTest {
    @Test
    void loggerLookupUsesLog4jCompatibleNamesAndCaching() {
        String loggerName = uniqueLoggerName("lookup");

        Logger first = Logger.getLogger(loggerName);
        Logger second = Logger.getLogger(loggerName);
        Category category = Category.getInstance(loggerName);

        assertThat(second).isSameAs(first);
        assertThat(LogManager.getLogger(loggerName)).isSameAs(first);
        assertThat(category).isSameAs(first);
        assertThat(first.getName()).isEqualTo(loggerName);
        assertThat(Logger.getLogger(Log4j_over_slf4jTest.class).getName())
                .isEqualTo(Log4j_over_slf4jTest.class.getName());
        assertThat(Logger.getRootLogger()).isSameAs(LogManager.getRootLogger());
        assertThat(Logger.getRootLogger().getName()).isEqualTo("ROOT");
        assertThat(first.getParent()).isNull();
        assertThat(first.getAppender("missing")).isNull();
        assertThat(first.getAllAppenders()).isSameAs(NullEnumeration.getInstance());
        assertThat(first.getAllAppenders().hasMoreElements()).isFalse();
        assertThatThrownBy(() -> first.getAllAppenders().nextElement())
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void customLoggerFactoryIsUsedForUncachedNamesOnly() {
        String loggerName = uniqueLoggerName("factory");
        AtomicInteger firstFactoryInvocations = new AtomicInteger();
        AtomicInteger secondFactoryInvocations = new AtomicInteger();

        Logger logger = Logger.getLogger(loggerName, new CountingLoggerFactory(firstFactoryInvocations));
        Logger cached = Logger.getLogger(loggerName, new CountingLoggerFactory(secondFactoryInvocations));

        assertThat(logger).isInstanceOf(CountingLogger.class);
        assertThat(cached).isSameAs(logger);
        assertThat(firstFactoryInvocations).hasValue(1);
        assertThat(secondFactoryInvocations).hasValue(0);
    }

    @Test
    void logManagerFactoryLookupDoesNotPopulateTheSharedLoggerCache() {
        String loggerName = uniqueLoggerName("log-manager-factory");
        AtomicInteger factoryInvocations = new AtomicInteger();
        CountingLoggerFactory factory = new CountingLoggerFactory(factoryInvocations);

        Logger first = LogManager.getLogger(loggerName, factory);
        Logger second = LogManager.getLogger(loggerName, factory);
        Logger cached = LogManager.getLogger(loggerName);

        assertThat(first).isInstanceOf(CountingLogger.class);
        assertThat(second).isInstanceOf(CountingLogger.class);
        assertThat(first).isNotSameAs(second);
        assertThat(first.getName()).isEqualTo(loggerName);
        assertThat(second.getName()).isEqualTo(loggerName);
        assertThat(factoryInvocations).hasValue(2);
        assertThat(cached).isNotInstanceOf(CountingLogger.class);
        assertThat(LogManager.getLogger(loggerName)).isSameAs(cached);
    }

    @Test
    void loggingMethodsDelegateMessagesLevelsAndThrowablesToSlf4jBinding() {
        String loggerName = uniqueLoggerName("delegation");
        RecordingHandler handler = new RecordingHandler();

        withJulLogger(loggerName, handler, () -> {
            Logger logger = Logger.getLogger(loggerName);
            RuntimeException infoFailure = new RuntimeException("info failure");
            IllegalStateException fqcnFailure = new IllegalStateException("fqcn failure");

            assertThat(logger.isTraceEnabled()).isTrue();
            assertThat(logger.isDebugEnabled()).isTrue();
            assertThat(logger.isInfoEnabled()).isTrue();
            assertThat(logger.isWarnEnabled()).isTrue();
            assertThat(logger.isErrorEnabled()).isTrue();
            assertThat(logger.isEnabledFor(Level.TRACE)).isTrue();
            assertThat(logger.getEffectiveLevel()).isEqualTo(Level.TRACE);

            logger.trace("trace message");
            logger.debug(new Message("debug message"));
            logger.info("info message", infoFailure);
            logger.warn("warn message");
            logger.error("error message");
            logger.fatal("fatal message");
            logger.log(Level.INFO, "generic info");
            logger.log("custom.fqcn", Level.WARN, "fqcn warn", fqcnFailure);

            assertThat(handler.records())
                    .extracting(LogRecord::getLevel)
                    .containsExactly(FINEST, FINE, INFO, WARNING, SEVERE, SEVERE, INFO, WARNING);
            assertThat(handler.records())
                    .extracting(LogRecord::getMessage)
                    .containsExactly(
                            "trace message",
                            "debug message",
                            "info message",
                            "warn message",
                            "error message",
                            "fatal message",
                            "generic info",
                            "fqcn warn");
            assertThat(handler.records().get(2).getThrown()).isSameAs(infoFailure);
            assertThat(handler.records().get(7).getThrown()).isSameAs(fqcnFailure);
        });
    }

    @Test
    void levelConversionsFollowLog4jSemantics() {
        assertThat(Level.toLevel("trace")).isEqualTo(Level.TRACE);
        assertThat(Level.toLevel("DEBUG")).isEqualTo(Level.DEBUG);
        assertThat(Level.toLevel("info")).isEqualTo(Level.INFO);
        assertThat(Level.toLevel("WARN")).isEqualTo(Level.WARN);
        assertThat(Level.toLevel("error")).isEqualTo(Level.ERROR);
        assertThat(Level.toLevel("fatal")).isEqualTo(Level.FATAL);
        assertThat(Level.toLevel("off")).isEqualTo(Level.OFF);
        assertThat(Level.toLevel("all")).isEqualTo(Level.ALL);
        assertThat(Level.toLevel("unknown", Level.ERROR)).isEqualTo(Level.ERROR);
        assertThat(Level.toLevel(5000)).isEqualTo(Level.TRACE);
        assertThat(Level.toLevel(12345, Level.WARN)).isEqualTo(Level.WARN);

        assertThat(Level.INFO.isGreaterOrEqual(Level.DEBUG)).isTrue();
        assertThat(Level.DEBUG.isGreaterOrEqual(Level.ERROR)).isFalse();
        assertThat(Level.WARN.toString()).isEqualTo("WARN");
        assertThat(Level.WARN.toInt()).isEqualTo(Level.WARN_INT);
        assertThat(Level.WARN.getSyslogEquivalent()).isEqualTo(4);
    }

    @Test
    void mdcAndNdcBridgeToSlf4jDiagnosticContext() {
        MDC.clear();
        NDC.clear();

        MDC.put("requestId", 42);
        MDC.put("user", "alice");

        assertThat(MDC.get("requestId")).isEqualTo("42");
        assertThat(MDC.get("user")).isEqualTo("alice");
        MDC.remove("requestId");
        assertThat(MDC.get("requestId")).isNull();

        NDC.push("outer");
        NDC.push("inner");
        assertThat(NDC.getDepth()).isEqualTo(2);
        assertThat(NDC.peek()).isEqualTo("inner");
        assertThat(NDC.pop()).isEqualTo("inner");
        assertThat(NDC.peek()).isEqualTo("outer");
        NDC.clear();
        assertThat(NDC.getDepth()).isZero();
        assertThat(NDC.pop()).isEmpty();

        MDC.clear();
        assertThat(MDC.get("user")).isNull();
    }

    @Test
    void logLogEmitsInternalStatusMessagesAndHonorsFlags() {
        PrintStream previousOut = System.out;
        PrintStream previousErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        try (PrintStream capturedOut = new PrintStream(out, true, StandardCharsets.UTF_8);
                PrintStream capturedErr = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            System.setErr(capturedErr);
            LogLog.setQuietMode(false);
            LogLog.setInternalDebugging(false);

            LogLog.debug("debug disabled");
            LogLog.warn("warning");
            LogLog.error("error");

            assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
            assertThat(err.toString(StandardCharsets.UTF_8))
                    .contains("log4j:WARN warning", "log4j:ERROR error");

            out.reset();
            err.reset();
            LogLog.setInternalDebugging(true);
            LogLog.debug("debug enabled");

            assertThat(out.toString(StandardCharsets.UTF_8)).contains("log4j: debug enabled");
            assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();

            out.reset();
            err.reset();
            LogLog.setQuietMode(true);
            LogLog.debug("quiet debug");
            LogLog.warn(" quiet warn");
            LogLog.error(" quiet error");

            assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
            assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
        } finally {
            LogLog.setQuietMode(false);
            LogLog.setInternalDebugging(false);
            System.setOut(previousOut);
            System.setErr(previousErr);
        }
    }

    @Test
    void configurationLayoutAndAppenderCompatibilityTypesAreSafeNoOps() throws IOException {
        Appender appender = new TestAppender("compatibility-appender");
        Layout layout = new PatternLayout("%p %c - %m%n");
        SimpleLayout simpleLayout = new SimpleLayout();
        URL configurationUrl = new URL("file:/tmp/log4j-over-slf4j.properties");
        Properties properties = new Properties();
        properties.setProperty("log4j.rootLogger", "INFO, stdout");

        assertThatCode(BasicConfigurator::configure).doesNotThrowAnyException();
        assertThatCode(() -> BasicConfigurator.configure(appender)).doesNotThrowAnyException();
        assertThatCode(BasicConfigurator::resetConfiguration).doesNotThrowAnyException();
        assertThatCode(() -> PropertyConfigurator.configure(properties)).doesNotThrowAnyException();
        assertThatCode(() -> PropertyConfigurator.configure("log4j.properties")).doesNotThrowAnyException();
        assertThatCode(() -> PropertyConfigurator.configure(configurationUrl)).doesNotThrowAnyException();
        assertThatCode(() -> PropertyConfigurator.configureAndWatch("log4j.properties", 10L))
                .doesNotThrowAnyException();
        assertThatCode(() -> DOMConfigurator.configure("log4j.xml")).doesNotThrowAnyException();
        assertThatCode(() -> DOMConfigurator.configure(configurationUrl)).doesNotThrowAnyException();
        assertThat(DOMConfigurator.subst("${unchanged}", properties)).isEqualTo("${unchanged}");

        assertThatCode(ConsoleAppender::new).doesNotThrowAnyException();
        assertThatCode(WriterAppender::new).doesNotThrowAnyException();
        assertThatCode(() -> new FileAppender(layout, "ignored.log")).doesNotThrowAnyException();
        assertThatCode(() -> new FileAppender(simpleLayout, "ignored.log", true)).doesNotThrowAnyException();
        assertThatCode(() -> new FileAppender(layout, "ignored.log", true, false, 8192)).doesNotThrowAnyException();
        assertThatCode(() -> new RollingFileAppender(layout, "ignored.log")).doesNotThrowAnyException();
        assertThatCode(() -> new RollingFileAppender(layout, "ignored.log", true)).doesNotThrowAnyException();

        RollingFileAppender rollingFileAppender = new RollingFileAppender();
        rollingFileAppender.setMaxBackupIndex(3);
        rollingFileAppender.setMaximumFileSize(1024L);

        Logger logger = Logger.getLogger(uniqueLoggerName("configuration"));
        logger.addAppender(appender);
        logger.setAdditivity(true);
        logger.setLevel(Level.INFO);
        logger.assertLog(true, "not logged");

        assertThat(logger.getAdditivity()).isFalse();
        assertThat(logger.getLevel()).isNull();
        assertThat(logger.getAllAppenders().hasMoreElements()).isFalse();
    }

    private static String uniqueLoggerName(String suffix) {
        return Log4j_over_slf4jTest.class.getName() + "." + suffix + "." + System.nanoTime();
    }

    private static void withJulLogger(String name, RecordingHandler handler, Runnable action) {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(name);
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        java.util.logging.Level previousLevel = logger.getLevel();
        Handler[] previousHandlers = logger.getHandlers();
        for (Handler previousHandler : previousHandlers) {
            logger.removeHandler(previousHandler);
        }
        logger.setUseParentHandlers(false);
        logger.setLevel(ALL);
        handler.setLevel(ALL);
        logger.addHandler(handler);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
            for (Handler previousHandler : previousHandlers) {
                logger.addHandler(previousHandler);
            }
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }
    }

    private static final class CountingLogger extends Logger {
        private CountingLogger(String name) {
            super(name);
        }
    }

    private static final class CountingLoggerFactory implements LoggerFactory {
        private final AtomicInteger invocations;

        private CountingLoggerFactory(AtomicInteger invocations) {
            this.invocations = invocations;
        }

        @Override
        public Logger makeNewLoggerInstance(String name) {
            invocations.incrementAndGet();
            return new CountingLogger(name);
        }
    }

    private static final class Message {
        private final String text;

        private Message(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private static final class RecordingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        private List<LogRecord> records() {
            return records;
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static final class TestAppender implements Appender {
        private String name;
        private Layout layout;
        private ErrorHandler errorHandler;
        private Filter filter;
        private final List<LoggingEvent> events = new ArrayList<>();

        private TestAppender(String name) {
            this.name = name;
        }

        @Override
        public void addFilter(Filter filter) {
            this.filter = filter;
        }

        @Override
        public Filter getFilter() {
            return filter;
        }

        @Override
        public void clearFilters() {
            filter = null;
        }

        @Override
        public void close() {
            events.clear();
        }

        @Override
        public void doAppend(LoggingEvent event) {
            events.add(event);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setErrorHandler(ErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
        }

        @Override
        public ErrorHandler getErrorHandler() {
            return errorHandler;
        }

        @Override
        public void setLayout(Layout layout) {
            this.layout = layout;
        }

        @Override
        public Layout getLayout() {
            return layout;
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }

        @Override
        public boolean requiresLayout() {
            return true;
        }
    }
}
