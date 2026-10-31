/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_slf4j2_impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.slf4j.SLF4JServiceProvider;
import org.apache.logging.slf4j.message.ThrowableConsumingMessageFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.MDC.MDCCloseable;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LocationAwareLogger;
import org.slf4j.spi.MDCAdapter;

public class Log4j_slf4j2_implTest {
    private static final String LOGGER_NAME = "org.graalvm.tests.log4j-slf4j2-impl";
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String SCOPE_KEY = "scope";
    private static final String BREADCRUMBS_KEY = "breadcrumbs";

    @AfterEach
    void clearThreadContext() {
        MDC.clear();
        MDC.getMDCAdapter().clearDequeByKey(BREADCRUMBS_KEY);
    }

    @Test
    void serviceProviderSuppliesSlf4jFactoriesBackedByLog4j() {
        SLF4JServiceProvider provider = new SLF4JServiceProvider();
        provider.initialize();

        ILoggerFactory loggerFactory = provider.getLoggerFactory();
        IMarkerFactory markerFactory = provider.getMarkerFactory();
        MDCAdapter mdcAdapter = provider.getMDCAdapter();

        assertThat(provider.getRequestedApiVersion()).startsWith("2.");
        assertThat(loggerFactory).isNotNull();
        assertThat(markerFactory).isNotNull();
        assertThat(mdcAdapter).isNotNull();
        assertThat(loggerFactory.getLogger(LOGGER_NAME).getName()).isEqualTo(LOGGER_NAME);
    }

    @Test
    void loggerFactoryReturnsLog4jBackedSlf4jLoggers() {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        Logger namedLogger = LoggerFactory.getLogger(LOGGER_NAME);
        Logger classLogger = LoggerFactory.getLogger(Log4j_slf4j2_implTest.class);

        assertThat(loggerFactory.getClass().getName()).isEqualTo("org.apache.logging.slf4j.Log4jLoggerFactory");
        assertThat(namedLogger.getName()).isEqualTo(LOGGER_NAME);
        assertThat(classLogger.getName()).isEqualTo(Log4j_slf4j2_implTest.class.getName());
        assertThat(loggerFactory.getLogger(LOGGER_NAME)).isSameAs(namedLogger);
    }

    @Test
    void loggerFactoryMapsSlf4jRootLoggerNameToLog4jRootLogger() {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        Logger slf4jRootLogger = loggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        org.apache.logging.log4j.Logger log4jRootLogger = LogManager.getRootLogger();

        assertThat(slf4jRootLogger.getName()).isEqualTo(Logger.ROOT_LOGGER_NAME);
        assertThat(loggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).isSameAs(slf4jRootLogger);
        assertThat(slf4jRootLogger.isTraceEnabled()).isEqualTo(log4jRootLogger.isTraceEnabled());
        assertThat(slf4jRootLogger.isDebugEnabled()).isEqualTo(log4jRootLogger.isDebugEnabled());
        assertThat(slf4jRootLogger.isInfoEnabled()).isEqualTo(log4jRootLogger.isInfoEnabled());
        assertThat(slf4jRootLogger.isWarnEnabled()).isEqualTo(log4jRootLogger.isWarnEnabled());
        assertThat(slf4jRootLogger.isErrorEnabled()).isEqualTo(log4jRootLogger.isErrorEnabled());

        slf4jRootLogger.error("root logger message is accepted by the Log4j-backed SLF4J bridge");
    }

    @Test
    void slf4jLoggingApiRoutesParameterizedMarkerThrowableAndFluentEvents() {
        Logger logger = LoggerFactory.getLogger(LOGGER_NAME + ".events");
        Marker marker = MarkerFactory.getMarker("LOG4J_SLF4J2_IMPL_EVENT_MARKER");
        Throwable failure = new IllegalStateException("expected test exception");
        AtomicBoolean argumentSupplierCalled = new AtomicBoolean(false);
        AtomicBoolean messageSupplierCalled = new AtomicBoolean(false);

        assertThat(logger.isErrorEnabled()).isTrue();

        logger.trace("trace message is accepted with {}", "arguments");
        logger.debug("debug message is accepted with {} and {}", "multiple", "arguments");
        logger.info(marker, "info marker message is accepted with {}", "payload");
        logger.warn("warn message is accepted with throwable", failure);
        logger.error(marker, "error marker message is accepted with {}", "payload", failure);

        logger.atError()
                .addMarker(marker)
                .addArgument("first")
                .addArgument(() -> {
                    argumentSupplierCalled.set(true);
                    return "second";
                })
                .addKeyValue("operation", "fluent-event")
                .setCause(failure)
                .log("fluent message with {} and {}");

        logger.atError().addArgument("payload").log(() -> {
            messageSupplierCalled.set(true);
            return "fluent supplier message with {}";
        });

        assertThat(argumentSupplierCalled).isTrue();
        assertThat(messageSupplierCalled).isTrue();
    }

    @Test
    void levelAwareAndLocationAwareSlf4jApisMapToLog4jLevels() {
        Logger logger = LoggerFactory.getLogger(LOGGER_NAME + ".levels");
        Marker marker = MarkerFactory.getMarker("LOG4J_SLF4J2_IMPL_LEVEL_MARKER");
        Throwable failure = new IllegalStateException("expected level-aware exception");
        AtomicBoolean keyValueSupplierCalled = new AtomicBoolean(false);

        assertThat(logger).isInstanceOf(LocationAwareLogger.class);
        assertThat(logger.isEnabledForLevel(Level.TRACE)).isEqualTo(logger.isTraceEnabled());
        assertThat(logger.isEnabledForLevel(Level.DEBUG)).isEqualTo(logger.isDebugEnabled());
        assertThat(logger.isEnabledForLevel(Level.INFO)).isEqualTo(logger.isInfoEnabled());
        assertThat(logger.isEnabledForLevel(Level.WARN)).isEqualTo(logger.isWarnEnabled());
        assertThat(logger.isEnabledForLevel(Level.ERROR)).isEqualTo(logger.isErrorEnabled());

        assertThatCode(() -> {
            logger.atLevel(Level.ERROR)
                    .addMarker(marker)
                    .addKeyValue("level-api", () -> {
                        keyValueSupplierCalled.set(true);
                        return "error";
                    })
                    .setCause(failure)
                    .setMessage("level-aware fluent message")
                    .log();

            LocationAwareLogger locationAwareLogger = (LocationAwareLogger) logger;
            locationAwareLogger.log(
                    marker,
                    Log4j_slf4j2_implTest.class.getName(),
                    LocationAwareLogger.INFO_INT,
                    "location-aware {} message",
                    new Object[] {"info"},
                    null);
            locationAwareLogger.log(
                    marker,
                    Log4j_slf4j2_implTest.class.getName(),
                    LocationAwareLogger.ERROR_INT,
                    "location-aware error {}",
                    new Object[] {"payload", failure},
                    null);
        }).doesNotThrowAnyException();
        assertThat(keyValueSupplierCalled).isTrue();
    }

    @Test
    void mdcAdapterMaintainsContextMapsAndPerKeyStacks() {
        MDC.put(REQUEST_ID_KEY, "request-1");
        MDC.put(SCOPE_KEY, "outer");

        assertThat(MDC.get(REQUEST_ID_KEY)).isEqualTo("request-1");
        assertThat(MDC.getCopyOfContextMap())
                .containsEntry(REQUEST_ID_KEY, "request-1")
                .containsEntry(SCOPE_KEY, "outer");

        try (MDCCloseable ignored = MDC.putCloseable(SCOPE_KEY, "inner")) {
            assertThat(MDC.get(SCOPE_KEY)).isEqualTo("inner");
        }
        assertThat(MDC.get(SCOPE_KEY)).isNull();

        Map<String, String> replacementContext = new LinkedHashMap<>();
        replacementContext.put(REQUEST_ID_KEY, "request-2");
        replacementContext.put("tenant", "native-image-tests");
        MDC.setContextMap(replacementContext);
        assertThat(MDC.getCopyOfContextMap()).containsExactlyInAnyOrderEntriesOf(replacementContext);

        MDC.remove("tenant");
        assertThat(MDC.getCopyOfContextMap()).containsEntry(REQUEST_ID_KEY, "request-2").doesNotContainKey("tenant");

        MDC.pushByKey(BREADCRUMBS_KEY, "first");
        MDC.pushByKey(BREADCRUMBS_KEY, "second");
        assertThat(MDC.getMDCAdapter().getCopyOfDequeByKey(BREADCRUMBS_KEY)).containsExactly("second", "first");
        assertThat(MDC.popByKey(BREADCRUMBS_KEY)).isEqualTo("second");
        assertThat(MDC.popByKey(BREADCRUMBS_KEY)).isEqualTo("first");
        assertThat(MDC.getMDCAdapter().getCopyOfDequeByKey(BREADCRUMBS_KEY)).isNullOrEmpty();
    }

    @Test
    void markerFactoryCreatesAttachedAndNestedMarkers() {
        String markerSuffix = Long.toHexString(System.nanoTime());
        String parentName = "LOG4J_SLF4J2_IMPL_PARENT_" + markerSuffix;
        String childName = "LOG4J_SLF4J2_IMPL_CHILD_" + markerSuffix;

        Marker parent = MarkerFactory.getMarker(parentName);
        Marker sameParent = MarkerFactory.getMarker(parentName);
        Marker child = MarkerFactory.getMarker(childName);

        parent.add(child);

        assertThat(sameParent).isSameAs(parent);
        assertThat(parent.contains(child)).isTrue();
        assertThat(parent.contains(childName)).isTrue();
        assertThat(child.contains(parent)).isFalse();
        assertThat(MarkerFactory.getIMarkerFactory().exists(parentName)).isTrue();
        assertThat(MarkerFactory.getIMarkerFactory().exists(childName)).isTrue();

        assertThat(parent.remove(child)).isTrue();
        assertThat(parent.contains(child)).isFalse();
        assertThat(MarkerFactory.getIMarkerFactory().detachMarker(parentName)).isFalse();
    }

    @Test
    void throwableConsumingMessageFactoryPreservesSlf4jThrowableSemantics() {
        ThrowableConsumingMessageFactory messageFactory = new ThrowableConsumingMessageFactory();
        Throwable failure = new IllegalArgumentException("expected factory exception");

        Message simpleMessage = messageFactory.newMessage("plain message");
        Message charSequenceMessage = messageFactory.newMessage(new StringBuilder("chars message"));
        Message objectMessage = messageFactory.newMessage(Integer.valueOf(42));
        Message parameterizedMessage = messageFactory.newMessage("Hello, {}", "GraalVM");
        Message parameterizedWithThrowable = messageFactory.newMessage(
                "{} failed at {}", "operation", "startup", failure);
        Message varargsWithThrowable = messageFactory.newMessage("values {} {}", new Object[] {"one", "two", failure});

        assertThat(simpleMessage.getFormattedMessage()).isEqualTo("plain message");
        assertThat(charSequenceMessage.getFormattedMessage()).isEqualTo("chars message");
        assertThat(objectMessage.getFormattedMessage()).isEqualTo("42");
        assertThat(parameterizedMessage.getFormattedMessage()).isEqualTo("Hello, GraalVM");
        assertThat(parameterizedMessage.getThrowable()).isNull();
        assertThat(parameterizedWithThrowable.getFormattedMessage()).isEqualTo("operation failed at startup");
        assertThat(parameterizedWithThrowable.getThrowable()).isSameAs(failure);
        assertThat(varargsWithThrowable.getFormattedMessage()).isEqualTo("values one two");
        assertThat(varargsWithThrowable.getThrowable()).isSameAs(failure);
    }
}
