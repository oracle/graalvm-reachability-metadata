/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_scala_logging.scala_logging_3

import com.typesafe.scalalogging.AnyLogging
import com.typesafe.scalalogging.CanLog
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.scalalogging.Logger
import com.typesafe.scalalogging.LoggerTakingImplicit
import com.typesafe.scalalogging.StrictLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.slf4j.Marker
import org.slf4j.MarkerFactory

import scala.collection.mutable.ListBuffer

class ScalaLogging3Test {
  @Test
  def wrapsUnderlyingLoggerAndProvidesFactoryConstructors(): Unit = {
    val underlying: RecordingSlf4jLogger = RecordingSlf4jLogger("wrapped")
    val logger: Logger = Logger(underlying)

    assertSame(underlying, logger.underlying)
    assertNotNull(Logger("named.logger").underlying)
    assertNotNull(Logger(classOf[ScalaLogging3Test]).underlying)
    assertNotNull(Logger[ScalaLogging3Test].underlying)
  }

  @Test
  def delegatesEnabledLoggingCallsAndChecksLevels(): Unit = {
    val underlying: RecordingSlf4jLogger = RecordingSlf4jLogger(
      "delegating",
      enabledLevels = Set("TRACE", "DEBUG", "INFO", "WARN", "ERROR")
    )
    val logger: Logger = Logger(underlying)

    assertEquals(true, logger.underlying.isTraceEnabled)
    logger.trace("trace message")
    logger.debug("debug {}", "value")
    logger.info("info {} {} {}", "first", "second", "third")
    logger.warn("warn {} {}", "left", "right")
    val failure: IllegalStateException = new IllegalStateException("boom")
    logger.error("error message", failure)

    assertEquals(5, underlying.records.size)
    assertRecord(underlying.records(0), "TRACE", None, "trace message", Seq.empty, None)
    assertRecord(underlying.records(1), "DEBUG", None, "debug {}", Seq("value"), None)
    assertRecord(underlying.records(2), "INFO", None, "info {} {} {}", Seq("first", "second", "third"), None)
    assertRecord(underlying.records(3), "WARN", None, "warn {} {}", Seq("left", "right"), None)
    assertRecord(underlying.records(4), "ERROR", None, "error message", Seq.empty, Some(failure))
  }

  @Test
  def doesNotEvaluateMessagesOrArgumentsWhenLevelIsDisabled(): Unit = {
    val underlying: RecordingSlf4jLogger = RecordingSlf4jLogger("disabled", enabledLevels = Set("INFO"))
    val logger: Logger = Logger(underlying)
    var debugMessageEvaluated: Boolean = false
    var debugArgumentEvaluated: Boolean = false
    var traceMessageEvaluated: Boolean = false

    logger.debug({
      debugMessageEvaluated = true
      "hidden debug"
    })
    logger.debug("hidden {}", {
      debugArgumentEvaluated = true
      "argument"
    })
    logger.trace({
      traceMessageEvaluated = true
      "hidden trace"
    })

    assertFalse(debugMessageEvaluated)
    assertFalse(debugArgumentEvaluated)
    assertFalse(traceMessageEvaluated)
    assertEquals(0, underlying.records.size)
  }

  @Test
  def runsGuardedBlocksOnlyForEnabledLevels(): Unit = {
    val underlying: RecordingSlf4jLogger = RecordingSlf4jLogger("guarded", enabledLevels = Set("INFO"))
    val logger: Logger = Logger(underlying)
    var infoBodyEvaluated: Boolean = false
    var debugBodyEvaluated: Boolean = false

    logger.whenInfoEnabled {
      infoBodyEvaluated = true
      logger.info("inside guarded info")
    }
    logger.whenDebugEnabled {
      debugBodyEvaluated = true
      logger.debug("inside guarded debug")
    }

    assertEquals(true, infoBodyEvaluated)
    assertFalse(debugBodyEvaluated)
    assertEquals(1, underlying.records.size)
    assertRecord(underlying.records.head, "INFO", None, "inside guarded info", Seq.empty, None)
  }

  @Test
  def supportsMarkersAndThrowableOverloads(): Unit = {
    val underlying: RecordingSlf4jLogger = RecordingSlf4jLogger(
      "markers",
      enabledLevels = Set("INFO", "WARN", "ERROR")
    )
    val logger: Logger = Logger(underlying)
    val marker: Marker = MarkerFactory.getMarker("integration-test")
    val failure: RuntimeException = new RuntimeException("marker failure")

    logger.info(marker, "marked {}", "payload")
    logger.warn(marker, "marked warn {} {}", "left", "right")
    logger.error(marker, "marked error", failure)

    assertEquals(3, underlying.records.size)
    assertRecord(underlying.records(0), "INFO", Some("integration-test"), "marked {}", Seq("payload"), None)
    assertRecord(underlying.records(1), "WARN", Some("integration-test"), "marked warn {} {}", Seq("left", "right"), None)
    assertRecord(underlying.records(2), "ERROR", Some("integration-test"), "marked error", Seq.empty, Some(failure))
  }

  @Test
  def enrichesMessagesWithImplicitContextAndRunsCanLogHooks(): Unit = {
    val underlying: RecordingSlf4jLogger = RecordingSlf4jLogger("contextual", enabledLevels = Set("INFO"))
    val hookEvents: ListBuffer[String] = ListBuffer.empty
    given CanLog[RequestContext] with {
      override def logMessage(originalMessage: String, context: RequestContext): String = {
        hookEvents += s"logMessage:${context.id}"
        s"[${context.id}] $originalMessage"
      }

      override def afterLog(context: RequestContext): Unit = {
        hookEvents += s"afterLog:${context.id}"
      }
    }
    given RequestContext = RequestContext("request-7")
    val logger: LoggerTakingImplicit[RequestContext] = Logger.takingImplicit[RequestContext](underlying)

    logger.info("created {}", "record")

    assertEquals(Seq("logMessage:request-7", "afterLog:request-7"), hookEvents.toSeq)
    assertEquals(1, underlying.records.size)
    assertRecord(underlying.records.head, "INFO", None, "[request-7] created {}", Seq("record"), None)
  }

  @Test
  def takingImplicitLoggerSupportsMarkersThrowablesAndMultipleArguments(): Unit = {
    val underlying: RecordingSlf4jLogger = RecordingSlf4jLogger("contextual-markers", enabledLevels = Set("WARN", "ERROR"))
    val hookEvents: ListBuffer[String] = ListBuffer.empty
    given CanLog[RequestContext] with {
      override def logMessage(originalMessage: String, context: RequestContext): String = {
        hookEvents += s"logMessage:${context.id}"
        s"[${context.id}] $originalMessage"
      }

      override def afterLog(context: RequestContext): Unit = {
        hookEvents += s"afterLog:${context.id}"
      }
    }
    given RequestContext = RequestContext("request-8")
    val logger: LoggerTakingImplicit[RequestContext] = Logger.takingImplicit[RequestContext](underlying)
    val marker: Marker = MarkerFactory.getMarker("contextual-marker")
    val failure: RuntimeException = new RuntimeException("contextual failure")

    logger.warn(marker, "contextual warn {} {}", "left", "right")
    logger.error(marker, "contextual error", failure)

    assertEquals(
      Seq("logMessage:request-8", "afterLog:request-8", "logMessage:request-8", "afterLog:request-8"),
      hookEvents.toSeq
    )
    assertEquals(2, underlying.records.size)
    assertRecord(
      underlying.records(0),
      "WARN",
      Some("contextual-marker"),
      "[request-8] contextual warn {} {}",
      Seq("left", "right"),
      None
    )
    assertRecord(
      underlying.records(1),
      "ERROR",
      Some("contextual-marker"),
      "[request-8] contextual error",
      Seq.empty,
      Some(failure)
    )
  }

  @Test
  def takingImplicitLoggerDoesNotEvaluateDisabledMessagesOrRunHooks(): Unit = {
    val underlying: RecordingSlf4jLogger = RecordingSlf4jLogger("contextual-disabled", enabledLevels = Set.empty)
    var messageEvaluated: Boolean = false
    var hooksCalled: Boolean = false
    given CanLog[RequestContext] with {
      override def logMessage(originalMessage: String, context: RequestContext): String = {
        hooksCalled = true
        originalMessage
      }

      override def afterLog(context: RequestContext): Unit = {
        hooksCalled = true
      }
    }
    given RequestContext = RequestContext("disabled")
    val logger: LoggerTakingImplicit[RequestContext] = Logger.takingImplicit[RequestContext](underlying)

    logger.debug({
      messageEvaluated = true
      "hidden contextual message"
    })

    assertFalse(messageEvaluated)
    assertFalse(hooksCalled)
    assertEquals(0, underlying.records.size)
  }

  @Test
  def lazyAndStrictLoggingTraitsExposeStableLoggerInstances(): Unit = {
    val lazyComponent: LazyComponent = LazyComponent()
    val strictComponent: StrictComponent = StrictComponent()

    assertSame(lazyComponent.currentLogger, lazyComponent.currentLogger)
    assertSame(strictComponent.currentLogger, strictComponent.currentLogger)
    assertNotNull(lazyComponent.currentLogger.underlying)
    assertNotNull(strictComponent.currentLogger.underlying)
  }

  @Test
  def anyLoggingUsesUserProvidedLogger(): Unit = {
    val underlying: RecordingSlf4jLogger = RecordingSlf4jLogger("custom-any-logging", enabledLevels = Set("INFO"))
    val component: AnyLoggingComponent = AnyLoggingComponent(Logger(underlying))

    component.info("provided logger message")

    assertSame(underlying, component.currentLogger.underlying)
    assertEquals(1, underlying.records.size)
    assertRecord(underlying.records.head, "INFO", None, "provided logger message", Seq.empty, None)
  }

  private def assertRecord(
    record: LogRecord,
    level: String,
    markerName: Option[String],
    message: String,
    arguments: Seq[Any],
    throwable: Option[Throwable]
  ): Unit = {
    assertEquals(level, record.level)
    assertEquals(markerName, record.markerName)
    assertEquals(message, record.message)
    assertEquals(arguments, record.arguments)
    assertEquals(throwable, record.throwable)
  }

  private final case class RequestContext(id: String)

  private final class LazyComponent extends LazyLogging {
    def currentLogger: Logger = logger
  }

  private object LazyComponent {
    def apply(): LazyComponent = new LazyComponent()
  }

  private final class StrictComponent extends StrictLogging {
    def currentLogger: Logger = logger
  }

  private object StrictComponent {
    def apply(): StrictComponent = new StrictComponent()
  }

  private final class AnyLoggingComponent(override protected val logger: Logger) extends AnyLogging {
    def currentLogger: Logger = logger

    def info(message: String): Unit = logger.info(message)
  }

  private object AnyLoggingComponent {
    def apply(logger: Logger): AnyLoggingComponent = new AnyLoggingComponent(logger)
  }

  private final case class LogRecord(
    level: String,
    markerName: Option[String],
    message: String,
    arguments: Seq[Any],
    throwable: Option[Throwable]
  )

  private final class RecordingSlf4jLogger private (
    loggerName: String,
    enabledLevels: Set[String]
  ) extends org.slf4j.Logger {
    val records: ListBuffer[LogRecord] = ListBuffer.empty

    override def getName: String = loggerName

    override def isTraceEnabled: Boolean = isEnabled("TRACE")
    override def isDebugEnabled: Boolean = isEnabled("DEBUG")
    override def isInfoEnabled: Boolean = isEnabled("INFO")
    override def isWarnEnabled: Boolean = isEnabled("WARN")
    override def isErrorEnabled: Boolean = isEnabled("ERROR")

    override def isTraceEnabled(marker: Marker): Boolean = isTraceEnabled
    override def isDebugEnabled(marker: Marker): Boolean = isDebugEnabled
    override def isInfoEnabled(marker: Marker): Boolean = isInfoEnabled
    override def isWarnEnabled(marker: Marker): Boolean = isWarnEnabled
    override def isErrorEnabled(marker: Marker): Boolean = isErrorEnabled

    override def trace(message: String): Unit = append("TRACE", None, message)
    override def trace(format: String, argument: Object): Unit = append("TRACE", None, format, Seq(argument))
    override def trace(format: String, first: Object, second: Object): Unit = append("TRACE", None, format, Seq(first, second))
    override def trace(format: String, arguments: Object*): Unit = append("TRACE", None, format, arguments)
    override def trace(message: String, throwable: Throwable): Unit = append("TRACE", None, message, throwable = Some(throwable))
    override def trace(marker: Marker, message: String): Unit = append("TRACE", Some(marker), message)
    override def trace(marker: Marker, format: String, argument: Object): Unit = append("TRACE", Some(marker), format, Seq(argument))
    override def trace(marker: Marker, format: String, first: Object, second: Object): Unit = append("TRACE", Some(marker), format, Seq(first, second))
    override def trace(marker: Marker, format: String, arguments: Object*): Unit = append("TRACE", Some(marker), format, arguments)
    override def trace(marker: Marker, message: String, throwable: Throwable): Unit = append("TRACE", Some(marker), message, throwable = Some(throwable))

    override def debug(message: String): Unit = append("DEBUG", None, message)
    override def debug(format: String, argument: Object): Unit = append("DEBUG", None, format, Seq(argument))
    override def debug(format: String, first: Object, second: Object): Unit = append("DEBUG", None, format, Seq(first, second))
    override def debug(format: String, arguments: Object*): Unit = append("DEBUG", None, format, arguments)
    override def debug(message: String, throwable: Throwable): Unit = append("DEBUG", None, message, throwable = Some(throwable))
    override def debug(marker: Marker, message: String): Unit = append("DEBUG", Some(marker), message)
    override def debug(marker: Marker, format: String, argument: Object): Unit = append("DEBUG", Some(marker), format, Seq(argument))
    override def debug(marker: Marker, format: String, first: Object, second: Object): Unit = append("DEBUG", Some(marker), format, Seq(first, second))
    override def debug(marker: Marker, format: String, arguments: Object*): Unit = append("DEBUG", Some(marker), format, arguments)
    override def debug(marker: Marker, message: String, throwable: Throwable): Unit = append("DEBUG", Some(marker), message, throwable = Some(throwable))

    override def info(message: String): Unit = append("INFO", None, message)
    override def info(format: String, argument: Object): Unit = append("INFO", None, format, Seq(argument))
    override def info(format: String, first: Object, second: Object): Unit = append("INFO", None, format, Seq(first, second))
    override def info(format: String, arguments: Object*): Unit = append("INFO", None, format, arguments)
    override def info(message: String, throwable: Throwable): Unit = append("INFO", None, message, throwable = Some(throwable))
    override def info(marker: Marker, message: String): Unit = append("INFO", Some(marker), message)
    override def info(marker: Marker, format: String, argument: Object): Unit = append("INFO", Some(marker), format, Seq(argument))
    override def info(marker: Marker, format: String, first: Object, second: Object): Unit = append("INFO", Some(marker), format, Seq(first, second))
    override def info(marker: Marker, format: String, arguments: Object*): Unit = append("INFO", Some(marker), format, arguments)
    override def info(marker: Marker, message: String, throwable: Throwable): Unit = append("INFO", Some(marker), message, throwable = Some(throwable))

    override def warn(message: String): Unit = append("WARN", None, message)
    override def warn(format: String, argument: Object): Unit = append("WARN", None, format, Seq(argument))
    override def warn(format: String, first: Object, second: Object): Unit = append("WARN", None, format, Seq(first, second))
    override def warn(format: String, arguments: Object*): Unit = append("WARN", None, format, arguments)
    override def warn(message: String, throwable: Throwable): Unit = append("WARN", None, message, throwable = Some(throwable))
    override def warn(marker: Marker, message: String): Unit = append("WARN", Some(marker), message)
    override def warn(marker: Marker, format: String, argument: Object): Unit = append("WARN", Some(marker), format, Seq(argument))
    override def warn(marker: Marker, format: String, first: Object, second: Object): Unit = append("WARN", Some(marker), format, Seq(first, second))
    override def warn(marker: Marker, format: String, arguments: Object*): Unit = append("WARN", Some(marker), format, arguments)
    override def warn(marker: Marker, message: String, throwable: Throwable): Unit = append("WARN", Some(marker), message, throwable = Some(throwable))

    override def error(message: String): Unit = append("ERROR", None, message)
    override def error(format: String, argument: Object): Unit = append("ERROR", None, format, Seq(argument))
    override def error(format: String, first: Object, second: Object): Unit = append("ERROR", None, format, Seq(first, second))
    override def error(format: String, arguments: Object*): Unit = append("ERROR", None, format, arguments)
    override def error(message: String, throwable: Throwable): Unit = append("ERROR", None, message, throwable = Some(throwable))
    override def error(marker: Marker, message: String): Unit = append("ERROR", Some(marker), message)
    override def error(marker: Marker, format: String, argument: Object): Unit = append("ERROR", Some(marker), format, Seq(argument))
    override def error(marker: Marker, format: String, first: Object, second: Object): Unit = append("ERROR", Some(marker), format, Seq(first, second))
    override def error(marker: Marker, format: String, arguments: Object*): Unit = append("ERROR", Some(marker), format, arguments)
    override def error(marker: Marker, message: String, throwable: Throwable): Unit = append("ERROR", Some(marker), message, throwable = Some(throwable))

    private def isEnabled(level: String): Boolean = enabledLevels.contains(level)

    private def append(
      level: String,
      marker: Option[Marker],
      message: String,
      arguments: Seq[Any] = Seq.empty,
      throwable: Option[Throwable] = None
    ): Unit = {
      records += LogRecord(level, marker.map(_.getName), message, arguments, throwable)
    }
  }

  private object RecordingSlf4jLogger {
    def apply(
      loggerName: String,
      enabledLevels: Set[String] = Set("TRACE", "DEBUG", "INFO", "WARN", "ERROR")
    ): RecordingSlf4jLogger = new RecordingSlf4jLogger(loggerName, enabledLevels)
  }
}
