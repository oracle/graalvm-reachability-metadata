/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_oshai.kotlin_logging_jvm

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.oshai.kotlinlogging.Appender
import io.github.oshai.kotlinlogging.DefaultMessageFormatter
import io.github.oshai.kotlinlogging.DirectLoggerFactory
import io.github.oshai.kotlinlogging.Formatter
import io.github.oshai.kotlinlogging.FormattingAppender
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KLoggerFactory
import io.github.oshai.kotlinlogging.KLoggingEvent
import io.github.oshai.kotlinlogging.KMarkerFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import io.github.oshai.kotlinlogging.Levels
import io.github.oshai.kotlinlogging.Marker
import io.github.oshai.kotlinlogging.slf4j.toKLogger
import io.github.oshai.kotlinlogging.slf4j.toKotlinLogging
import io.github.oshai.kotlinlogging.slf4j.toSlf4j
import io.github.oshai.kotlinlogging.withLoggingContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.slf4j.event.Level as Slf4jLevel

public class Kotlin_logging_jvmTest {
    private lateinit var originalLoggerFactory: KLoggerFactory
    private lateinit var originalAppender: Appender
    private lateinit var originalFormatter: Formatter
    private lateinit var originalLogLevel: Level

    @BeforeEach
    fun configureDirectLogging() {
        originalLoggerFactory = KotlinLoggingConfiguration.loggerFactory
        originalAppender = KotlinLoggingConfiguration.direct.appender
        originalFormatter = KotlinLoggingConfiguration.direct.formatter
        originalLogLevel = KotlinLoggingConfiguration.direct.logLevel

        KotlinLoggingConfiguration.loggerFactory = DirectLoggerFactory
        KotlinLoggingConfiguration.direct.logLevel = Level.TRACE
    }

    @AfterEach
    fun restoreLoggingConfiguration() {
        KotlinLoggingConfiguration.loggerFactory = originalLoggerFactory
        KotlinLoggingConfiguration.direct.appender = originalAppender
        KotlinLoggingConfiguration.direct.formatter = originalFormatter
        KotlinLoggingConfiguration.direct.logLevel = originalLogLevel
    }

    @Test
    fun directLoggerCapturesLazyMessagesMarkersCausesAndAllStandardLevels() {
        val appender = CapturingAppender()
        KotlinLoggingConfiguration.direct.appender = appender
        val logger = KotlinLogging.logger("direct.core")
        val marker: Marker = KMarkerFactory.getMarker("CORE")
        val traceCause = IllegalStateException("trace failure")
        val errorCause = IllegalArgumentException("error failure")

        logger.trace(marker, traceCause) { "trace message" }
        logger.debug { "debug message" }
        logger.info(marker) { 42 }
        logger.warn { "warn message" }
        logger.error(marker, errorCause) { "error message" }

        assertThat(appender.events.map { it.level })
            .containsExactly(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR)
        assertThat(appender.events.map { it.loggerName }).containsOnly("direct.core")
        assertThat(appender.events.map { it.message })
            .containsExactly("trace message", "debug message", "42", "warn message", "error message")
        assertThat(appender.events[0].marker).isSameAs(marker)
        assertThat(appender.events[0].cause).isSameAs(traceCause)
        assertThat(appender.events[2].marker).isSameAs(marker)
        assertThat(appender.events[4].cause).isSameAs(errorCause)
    }

    @Test
    fun configuredThresholdControlsEnabledChecksAndPreventsMessageEvaluation() {
        val appender = CapturingAppender()
        KotlinLoggingConfiguration.direct.appender = appender
        KotlinLoggingConfiguration.direct.logLevel = Level.WARN
        val logger = KotlinLogging.logger("direct.threshold")
        var evaluatedMessages = 0
        var evaluatedBuilderBlocks = 0

        assertThat(logger.isTraceEnabled()).isFalse()
        assertThat(logger.isDebugEnabled()).isFalse()
        assertThat(logger.isInfoEnabled()).isFalse()
        assertThat(logger.isWarnEnabled()).isTrue()
        assertThat(logger.isErrorEnabled()).isTrue()
        assertThat(logger.isLoggingOff()).isFalse()

        logger.debug {
            evaluatedMessages += 1
            "not logged"
        }
        logger.atInfo {
            evaluatedBuilderBlocks += 1
            message = "not logged"
        }
        logger.warn {
            evaluatedMessages += 1
            "logged warning"
        }
        logger.atError {
            evaluatedBuilderBlocks += 1
            message = "logged error"
        }

        assertThat(evaluatedMessages).isEqualTo(1)
        assertThat(evaluatedBuilderBlocks).isEqualTo(1)
        assertThat(appender.events.map { it.message }).containsExactly("logged warning", "logged error")
        assertThat(appender.events.map { it.level }).containsExactly(Level.WARN, Level.ERROR)

        KotlinLoggingConfiguration.direct.logLevel = Level.OFF
        assertThat(logger.isErrorEnabled()).isFalse()
        assertThat(logger.isLoggingOff()).isTrue()
        logger.error {
            evaluatedMessages += 1
            "not logged because logging is off"
        }
        assertThat(evaluatedMessages).isEqualTo(1)
        assertThat(appender.events).hasSize(2)
    }

    @Test
    fun eventBuilderApiCapturesStructuredFieldsAndSkipsDisabledBlocks() {
        val appender = CapturingAppender()
        KotlinLoggingConfiguration.direct.appender = appender
        KotlinLoggingConfiguration.direct.logLevel = Level.INFO
        val logger = KotlinLogging.logger("direct.builder")
        val marker: Marker = KMarkerFactory.getMarker("AUDIT")
        val cause = IllegalStateException("structured failure")
        val startedAt = System.currentTimeMillis()
        var disabledBlockInvocations = 0

        logger.atTrace(marker) {
            disabledBlockInvocations += 1
            message = "disabled trace"
        }
        logger.atInfo(marker) {
            message = "user signed in"
            this.cause = cause
            payload = linkedMapOf("user" to "alice", "attempt" to 1, "success" to true)
            arguments = arrayOf("retained by builder", 7)
        }

        assertThat(disabledBlockInvocations).isZero()
        assertThat(appender.events).hasSize(1)
        val event = appender.events.single()
        assertThat(event.level).isEqualTo(Level.INFO)
        assertThat(markerName(event.marker)).isEqualTo("AUDIT")
        assertThat(event.loggerName).isEqualTo("direct.builder")
        assertThat(event.message).isEqualTo("user signed in")
        assertThat(event.cause).isSameAs(cause)
        assertThat(event.payload).containsEntry("user", "alice")
            .containsEntry("attempt", 1)
            .containsEntry("success", true)
        assertThat(event.timestamp).isBetween(startedAt, System.currentTimeMillis())
    }

    @Test
    fun convenienceEntryExitThrowingAndCatchingMethodsEmitTraceAndErrorEvents() {
        val appender = CapturingAppender()
        KotlinLoggingConfiguration.direct.appender = appender
        val logger = KotlinLogging.logger("direct.convenience")
        val result = listOf("ok", "done")
        val throwingCause = IllegalStateException("leaving method")
        val catchingCause = IllegalArgumentException("handled problem")

        logger.entry("alpha", 7)
        logger.exit()
        val returnedResult = logger.exit(result)
        val returnedThrowable = logger.throwing(throwingCause)
        logger.catching(catchingCause)

        assertThat(returnedResult).isSameAs(result)
        assertThat(returnedThrowable).isSameAs(throwingCause)
        assertThat(appender.events.map { it.level })
            .containsExactly(Level.TRACE, Level.TRACE, Level.TRACE, Level.ERROR, Level.ERROR)
        assertThat(appender.events.map { it.message })
            .containsExactly(
                "entry(alpha, 7)",
                "exit",
                "exit([ok, done])",
                "throwing(java.lang.IllegalStateException: leaving method)",
                "catching(java.lang.IllegalArgumentException: handled problem)",
            )
        assertThat(appender.events[3].cause).isSameAs(throwingCause)
        assertThat(appender.events[4].cause).isSameAs(catchingCause)
    }

    @Test
    fun formattersMarkersAndFormattingAppenderUsePublicEventModel() {
        val marker: Marker = KMarkerFactory.getMarker("SECURITY")
        val cause = IllegalStateException("outer", IllegalArgumentException("inner"))
        val event = KLoggingEvent(
            level = Level.WARN,
            marker = marker,
            loggerName = "formatter.logger",
            message = "login failed",
            cause = cause,
            payload = mapOf("ip" to "127.0.0.1"),
            timestamp = 123L,
        )

        val prefixed = DefaultMessageFormatter(includePrefix = true).formatMessage(event)
        val unprefixed = DefaultMessageFormatter(includePrefix = false).formatMessage(event)

        assertThat(prefixed).startsWith("WARN: [formatter.logger]")
        assertThat(prefixed).contains("SECURITY login failed")
        assertThat(prefixed).contains("Caused by: 'outer'")
        assertThat(prefixed).contains("Caused by: 'inner'")
        assertThat(unprefixed).startsWith("SECURITY login failed")
        assertThat(unprefixed).doesNotContain("[formatter.logger]")

        val formattingAppender = CapturingFormattingAppender()
        KotlinLoggingConfiguration.direct.formatter = object : Formatter {
            override fun formatMessage(loggingEvent: KLoggingEvent): String {
                return "${loggingEvent.level}:${loggingEvent.loggerName}:${loggingEvent.message}"
            }
        }
        formattingAppender.log(event)

        assertThat(formattingAppender.events).containsExactly(event)
        assertThat(formattingAppender.formattedMessages)
            .containsExactly("WARN:formatter.logger:login failed")
        assertThat(markerName(KMarkerFactory.getMarker("SECURITY"))).isEqualTo(markerName(marker))
        assertThat(KMarkerFactory.getMarker("AUTH")).isNotEqualTo(marker)
    }

    @Test
    fun levelsAndEventsSupportRoundTripsDestructuringCopyingAndInvalidLevelChecks() {
        val levels = listOf(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR, Level.OFF)

        assertThat(levels.map { it.toInt() }).containsExactly(0, 10, 20, 30, 40, 50)
        assertThat(levels.map { Levels.intToLevel(it.toInt()) }).containsExactlyElementsOf(levels)
        assertThat(levels.map { it.toString() })
            .containsExactly("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF")
        assertThatThrownBy { Levels.intToLevel(15) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("15")

        val marker: Marker = KMarkerFactory.getMarker("DATA")
        val payload = linkedMapOf<String, Any>("rows" to 3)
        val event = KLoggingEvent(Level.INFO, marker, "event.logger", "loaded", null, payload, 77L)
        val (level, destructuredMarker, loggerName, message, cause, destructuredPayload, timestamp) = event
        val copied = event.copy(level = Level.ERROR, message = "failed")

        assertThat(level).isEqualTo(Level.INFO)
        assertThat(destructuredMarker).isSameAs(marker)
        assertThat(loggerName).isEqualTo("event.logger")
        assertThat(message).isEqualTo("loaded")
        assertThat(cause).isNull()
        assertThat(destructuredPayload).isSameAs(payload)
        assertThat(timestamp).isEqualTo(77L)
        assertThat(copied.level).isEqualTo(Level.ERROR)
        assertThat(copied.message).isEqualTo("failed")
        assertThat(copied.loggerName).isEqualTo(event.loggerName)
        assertThat(event).isEqualTo(KLoggingEvent(Level.INFO, marker, "event.logger", "loaded", null, payload, 77L))
    }

    @Test
    fun withLoggingContextScopesMdcValuesAndRestoresPreviousValues() {
        val traceKey = "kotlin.logging.test.trace"
        val userKey = "kotlin.logging.test.user"
        MDC.put(traceKey, "outer-trace")
        MDC.remove(userKey)

        try {
            val observedValues = withLoggingContext(mapOf(traceKey to "inner-trace", userKey to "alice")) {
                val valuesInOuterScope = linkedMapOf(
                    traceKey to MDC.get(traceKey),
                    userKey to MDC.get(userKey),
                )

                val valuesInNestedScope = withLoggingContext(userKey to "bob", restorePrevious = false) {
                    linkedMapOf(
                        traceKey to MDC.get(traceKey),
                        userKey to MDC.get(userKey),
                    )
                }

                val valuesAfterNestedScope = linkedMapOf(
                    traceKey to MDC.get(traceKey),
                    userKey to MDC.get(userKey),
                )

                listOf(valuesInOuterScope, valuesInNestedScope, valuesAfterNestedScope)
            }

            assertThat(observedValues[0])
                .containsEntry(traceKey, "inner-trace")
                .containsEntry(userKey, "alice")
            assertThat(observedValues[1])
                .containsEntry(traceKey, "inner-trace")
                .containsEntry(userKey, "bob")
            assertThat(observedValues[2])
                .containsEntry(traceKey, "inner-trace")
                .containsEntry(userKey, null)
            assertThat(MDC.get(traceKey)).isEqualTo("outer-trace")
            assertThat(MDC.get(userKey)).isNull()
        } finally {
            MDC.remove(traceKey)
            MDC.remove(userKey)
        }
    }

    @Test
    fun configurableLoggerFactoryCreatesExpectedLoggerNames() {
        val appender = CapturingAppender()
        val recordingFactory = RecordingLoggerFactory(DirectLoggerFactory)
        KotlinLoggingConfiguration.direct.appender = appender
        KotlinLoggingConfiguration.loggerFactory = recordingFactory

        val explicitLogger = KotlinLogging.logger("factory.explicit")
        val secondaryLogger = KotlinLogging.logger("factory.secondary")
        val childLogger = KotlinLogging.logger("factory.child")

        explicitLogger.info { "explicit" }
        secondaryLogger.info { "secondary" }
        childLogger.info { "child" }

        assertThat(recordingFactory.requestedNames)
            .containsExactly("factory.explicit", "factory.secondary", "factory.child")
        assertThat(appender.events.map { it.loggerName })
            .containsExactly("factory.explicit", "factory.secondary", "factory.child")
        assertThat(appender.events.map { it.message }).containsExactly("explicit", "secondary", "child")
    }

    @Test
    fun slf4jInteropConvertsMarkersLevelsAndForwardsToWrappedLogger() {
        val underlyingLogger = LoggerFactory.getLogger("slf4j.bridge") as Logger
        val appender = ListAppender<ILoggingEvent>()
        val originalLevel = underlyingLogger.level
        val originalAdditive = underlyingLogger.isAdditive
        val marker: Marker = KMarkerFactory.getMarker("SLF4J_BRIDGE")
        val slf4jMarker = marker.toSlf4j()
        val roundTrippedMarker = slf4jMarker.toKotlinLogging()
        val cause = IllegalStateException("slf4j bridge failure")

        appender.start()
        underlyingLogger.addAppender(appender)
        underlyingLogger.level = LogbackLevel.TRACE
        underlyingLogger.isAdditive = false
        try {
            assertThat(slf4jMarker.name).isEqualTo("SLF4J_BRIDGE")
            assertThat(roundTrippedMarker.getName()).isEqualTo("SLF4J_BRIDGE")
            assertThat(listOf(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR).map { it.toSlf4j() })
                .containsExactly(Slf4jLevel.TRACE, Slf4jLevel.DEBUG, Slf4jLevel.INFO, Slf4jLevel.WARN, Slf4jLevel.ERROR)
            assertThatThrownBy { Level.OFF.toSlf4j() }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("OFF")

            val logger = underlyingLogger.toKLogger()
            logger.warn(roundTrippedMarker, cause) { "forwarded through slf4j" }

            assertThat(logger.name).isEqualTo("slf4j.bridge")
            assertThat(appender.list).hasSize(1)
            val event = appender.list.single()
            assertThat(event.level).isEqualTo(LogbackLevel.WARN)
            assertThat(event.loggerName).isEqualTo("slf4j.bridge")
            assertThat(event.formattedMessage).isEqualTo("forwarded through slf4j")
            assertThat(event.markerList.map { it.name }).containsExactly("SLF4J_BRIDGE")
            assertThat(event.throwableProxy.message).isEqualTo("slf4j bridge failure")
        } finally {
            underlyingLogger.detachAppender(appender)
            underlyingLogger.level = originalLevel
            underlyingLogger.isAdditive = originalAdditive
            appender.stop()
        }
    }

    private fun markerName(marker: Marker?): String? {
        return marker?.getName()
    }

    private class CapturingAppender : Appender {
        val events: MutableList<KLoggingEvent> = mutableListOf()

        override fun log(loggingEvent: KLoggingEvent) {
            events += loggingEvent
        }
    }

    private class CapturingFormattingAppender : FormattingAppender() {
        val events: MutableList<KLoggingEvent> = mutableListOf()
        val formattedMessages: MutableList<Any?> = mutableListOf()

        override fun logFormattedMessage(loggingEvent: KLoggingEvent, formattedMessage: Any?) {
            events += loggingEvent
            formattedMessages += formattedMessage
        }
    }

    private class RecordingLoggerFactory(private val delegate: KLoggerFactory) : KLoggerFactory {
        val requestedNames: MutableList<String> = mutableListOf()

        override fun logger(name: String): KLogger {
            requestedNames += name
            return delegate.logger(name)
        }
    }
}
