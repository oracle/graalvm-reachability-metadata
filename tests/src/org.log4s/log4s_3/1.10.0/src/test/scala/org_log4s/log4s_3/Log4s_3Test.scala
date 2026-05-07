/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_log4s.log4s_3

import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import scala.collection.mutable.ArrayBuffer

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.log4s.Debug
import org.log4s.Error
import org.log4s.Info
import org.log4s.LogLevel
import org.log4s.Logger as Log4sLogger
import org.log4s.MDC
import org.log4s.Trace
import org.log4s.Warn
import org.log4s.getLogger
import org.slf4j.helpers.MarkerIgnoringBase

class Log4s_3Test {
  @AfterEach
  def clearMdc(): Unit = {
    MDC.clear()
  }

  @Test
  def logLevelNamesAreStableAndLookupIsCaseInsensitive(): Unit = {
    val levels: Seq[LogLevel] = Seq(Trace, Debug, Info, Warn, Error)

    levels.foreach { level =>
      assertEquals(level.toString, level.name)
      assertSame(level, LogLevel.forName(level.name.toLowerCase(Locale.ROOT)))
      assertSame(level, LogLevel.forName(level.name.toUpperCase(Locale.ROOT)))
    }

    val thrown: IllegalArgumentException = assertThrows(
      classOf[IllegalArgumentException],
      () => LogLevel.forName("verbose")
    )
    assertTrue(thrown.getMessage.contains("verbose"))
  }

  @Test
  def explicitAndMacroLoggerFactoriesCreateNamedLoggers(): Unit = {
    val explicitName: String = "org_log4s.log4s_3.explicit"
    assertEquals(explicitName, getLogger(explicitName).name)
    assertEquals("org_log4s.log4s_3.Log4s_3Test", getLogger(classOf[Log4s_3Test]).name)

    val probe: MacroLoggerNameProbe = new MacroLoggerNameProbe
    assertEquals("org_log4s.log4s_3.MacroLoggerNameProbe", probe.loggerName)
    assertEquals("org_log4s.log4s_3.MacroLoggerNameProbe", probe.loggerNameFromMethod)
  }

  @Test
  def macroLoggerFactoryUsesSingletonObjectNameWithoutTrailingDollar(): Unit = {
    assertEquals("org_log4s.log4s_3.MacroObjectLoggerNameProbe", MacroObjectLoggerNameProbe.loggerName)
  }

  @Test
  def directLoggingMethodsDelegateMessagesAndThrowablesForEveryLevel(): Unit = {
    val (logger, backend) = capturingLogger(Set(Trace, Debug, Info, Warn, Error))
    val traceFailure: RuntimeException = new RuntimeException("trace failure")
    val debugFailure: RuntimeException = new RuntimeException("debug failure")
    val infoFailure: RuntimeException = new RuntimeException("info failure")
    val warnFailure: RuntimeException = new RuntimeException("warn failure")
    val errorFailure: RuntimeException = new RuntimeException("error failure")

    logger.trace(s"trace-${1 + 1}")
    logger.trace(traceFailure)("trace throwable")
    logger.debug(s"debug-${1 + 1}")
    logger.debug(debugFailure)("debug throwable")
    logger.info(s"info-${1 + 1}")
    logger.info(infoFailure)("info throwable")
    logger.warn(s"warn-${1 + 1}")
    logger.warn(warnFailure)("warn throwable")
    logger.error(s"error-${1 + 1}")
    logger.error(errorFailure)("error throwable")

    assertEquals(
      Vector(
        LogEvent("TRACE", "trace-2", None),
        LogEvent("TRACE", "trace throwable", Some(traceFailure)),
        LogEvent("DEBUG", "debug-2", None),
        LogEvent("DEBUG", "debug throwable", Some(debugFailure)),
        LogEvent("INFO", "info-2", None),
        LogEvent("INFO", "info throwable", Some(infoFailure)),
        LogEvent("WARN", "warn-2", None),
        LogEvent("WARN", "warn throwable", Some(warnFailure)),
        LogEvent("ERROR", "error-2", None),
        LogEvent("ERROR", "error throwable", Some(errorFailure))
      ),
      backend.events
    )
  }

  @Test
  def disabledDirectLoggingMethodsDoNotEvaluateMessages(): Unit = {
    val (logger, backend) = capturingLogger(Set.empty)
    var evaluations: Int = 0
    def message(value: String): String = {
      evaluations += 1
      value
    }

    logger.trace(message("trace"))
    logger.trace(new RuntimeException("trace"))(message("trace throwable"))
    logger.debug(message("debug"))
    logger.debug(new RuntimeException("debug"))(message("debug throwable"))
    logger.info(message("info"))
    logger.info(new RuntimeException("info"))(message("info throwable"))
    logger.warn(message("warn"))
    logger.warn(new RuntimeException("warn"))(message("warn throwable"))
    logger.error(message("error"))
    logger.error(new RuntimeException("error"))(message("error throwable"))

    assertEquals(0, evaluations)
    assertTrue(backend.events.isEmpty)
  }

  @Test
  def levelLoggerApiSupportsStaticAndRuntimeLevels(): Unit = {
    val (logger, backend) = capturingLogger(Set(Debug, Warn))
    val debugFailure: RuntimeException = new RuntimeException("debug failure")
    var evaluations: Int = 0
    def message(level: LogLevel): String = {
      evaluations += 1
      s"${level.name} through LevelLogger"
    }

    assertFalse(logger.isTraceEnabled)
    assertTrue(logger.isDebugEnabled)
    assertFalse(logger.isInfoEnabled)
    assertTrue(logger.isWarnEnabled)
    assertFalse(logger.isErrorEnabled)
    assertFalse(logger(Trace).isEnabled)
    assertTrue(logger(Debug).isEnabled)
    assertFalse(logger(Info).isEnabled)
    assertTrue(logger(Warn).isEnabled)
    assertFalse(logger(Error).isEnabled)

    val runtimeLevels: Seq[LogLevel] = Seq(Trace, Debug, Info, Warn, Error)
    runtimeLevels.foreach { level =>
      logger(level)(message(level))
    }
    logger(Debug)(debugFailure)("debug throwable through LevelLogger")

    assertEquals(2, evaluations)
    assertEquals(
      Vector(
        LogEvent("DEBUG", "Debug through LevelLogger", None),
        LogEvent("WARN", "Warn through LevelLogger", None),
        LogEvent("DEBUG", "debug throwable through LevelLogger", Some(debugFailure))
      ),
      backend.events
    )
  }

  @Test
  def mdcBehavesAsAMutableMapAndRestoresScopedValues(): Unit = {
    MDC.clear()
    assertTrue(MDC.isEmpty)

    MDC("request") = "r-1"
    MDC += ("user" -> "alice")
    assertEquals(Some("r-1"), MDC.get("request"))
    assertEquals("alice", MDC("user"))
    assertEquals(Map("request" -> "r-1", "user" -> "alice"), MDC.toMap)

    val scopedResult: String = MDC.withCtx("request" -> "r-2", "span" -> "s-1") {
      assertEquals(Map("request" -> "r-2", "user" -> "alice", "span" -> "s-1"), MDC.toMap)
      "completed"
    }

    assertEquals("completed", scopedResult)
    assertEquals(Map("request" -> "r-1", "user" -> "alice"), MDC.toMap)

    MDC -= "user"
    assertFalse(MDC.contains("user"))
    MDC.clear()
    assertTrue(MDC.isEmpty)
  }

  @Test
  def mdcWithCtxRestoresOriginalValuesWhenBlockThrows(): Unit = {
    MDC.clear()
    MDC("request") = "r-1"

    val failure: IllegalStateException = new IllegalStateException("context failure")
    val thrown: IllegalStateException = assertThrows(
      classOf[IllegalStateException],
      () =>
        MDC.withCtx("request" -> "r-2", "span" -> "s-1") {
          assertEquals(Map("request" -> "r-2", "span" -> "s-1"), MDC.toMap)
          throw failure
        }
    )

    assertSame(failure, thrown)
    assertEquals(Map("request" -> "r-1"), MDC.toMap)
  }

  @Test
  def mdcContextIsIsolatedByThread(): Unit = {
    MDC.clear()
    MDC("thread") = "main"

    val executor = Executors.newSingleThreadExecutor()
    try {
      val result = executor.submit(new Callable[Map[String, String]] {
        override def call(): Map[String, String] = {
          val before: Option[String] = MDC.get("thread")
          MDC("thread") = "worker"
          Map("before" -> before.getOrElse("missing"), "during" -> MDC("thread"))
        }
      }).get(5, TimeUnit.SECONDS)

      assertEquals(Map("before" -> "missing", "during" -> "worker"), result)
      assertEquals("main", MDC("thread"))
    } finally {
      executor.shutdownNow()
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
    }
  }

  private def capturingLogger(enabledLevels: Set[LogLevel]): (Log4sLogger, CapturingSlf4jLogger) = {
    val enabledNames: Set[String] = enabledLevels.map(level => level.name.toUpperCase(Locale.ROOT))
    val backend: CapturingSlf4jLogger = new CapturingSlf4jLogger("captured", enabledNames)
    (new Log4sLogger(backend), backend)
  }
}

private final class MacroLoggerNameProbe {
  private val logger = getLogger

  def loggerName: String = logger.name

  def loggerNameFromMethod: String = {
    val local = getLogger
    local.name
  }
}

private object MacroObjectLoggerNameProbe {
  private val logger = getLogger

  def loggerName: String = logger.name
}

private final case class LogEvent(level: String, message: String, throwable: Option[Throwable])

private final class CapturingSlf4jLogger(loggerName: String, enabledLevels: Set[String]) extends MarkerIgnoringBase {
  name = loggerName

  private val recordedEvents: ArrayBuffer[LogEvent] = ArrayBuffer.empty

  def events: Vector[LogEvent] = recordedEvents.toVector

  override def isTraceEnabled(): Boolean = isEnabled("TRACE")

  override def isDebugEnabled(): Boolean = isEnabled("DEBUG")

  override def isInfoEnabled(): Boolean = isEnabled("INFO")

  override def isWarnEnabled(): Boolean = isEnabled("WARN")

  override def isErrorEnabled(): Boolean = isEnabled("ERROR")

  override def trace(message: String): Unit = record("TRACE", message, None)

  override def trace(format: String, argument: AnyRef): Unit = trace(render(format, Seq(argument)))

  override def trace(format: String, firstArgument: AnyRef, secondArgument: AnyRef): Unit = {
    trace(render(format, Seq(firstArgument, secondArgument)))
  }

  override def trace(format: String, arguments: AnyRef*): Unit = trace(render(format, arguments))

  override def trace(message: String, throwable: Throwable): Unit = record("TRACE", message, Option(throwable))

  override def debug(message: String): Unit = record("DEBUG", message, None)

  override def debug(format: String, argument: AnyRef): Unit = debug(render(format, Seq(argument)))

  override def debug(format: String, firstArgument: AnyRef, secondArgument: AnyRef): Unit = {
    debug(render(format, Seq(firstArgument, secondArgument)))
  }

  override def debug(format: String, arguments: AnyRef*): Unit = debug(render(format, arguments))

  override def debug(message: String, throwable: Throwable): Unit = record("DEBUG", message, Option(throwable))

  override def info(message: String): Unit = record("INFO", message, None)

  override def info(format: String, argument: AnyRef): Unit = info(render(format, Seq(argument)))

  override def info(format: String, firstArgument: AnyRef, secondArgument: AnyRef): Unit = {
    info(render(format, Seq(firstArgument, secondArgument)))
  }

  override def info(format: String, arguments: AnyRef*): Unit = info(render(format, arguments))

  override def info(message: String, throwable: Throwable): Unit = record("INFO", message, Option(throwable))

  override def warn(message: String): Unit = record("WARN", message, None)

  override def warn(format: String, argument: AnyRef): Unit = warn(render(format, Seq(argument)))

  override def warn(format: String, arguments: AnyRef*): Unit = warn(render(format, arguments))

  override def warn(format: String, firstArgument: AnyRef, secondArgument: AnyRef): Unit = {
    warn(render(format, Seq(firstArgument, secondArgument)))
  }

  override def warn(message: String, throwable: Throwable): Unit = record("WARN", message, Option(throwable))

  override def error(message: String): Unit = record("ERROR", message, None)

  override def error(format: String, argument: AnyRef): Unit = error(render(format, Seq(argument)))

  override def error(format: String, firstArgument: AnyRef, secondArgument: AnyRef): Unit = {
    error(render(format, Seq(firstArgument, secondArgument)))
  }

  override def error(format: String, arguments: AnyRef*): Unit = error(render(format, arguments))

  override def error(message: String, throwable: Throwable): Unit = record("ERROR", message, Option(throwable))

  private def isEnabled(level: String): Boolean = enabledLevels.contains(level)

  private def record(level: String, message: String, throwable: Option[Throwable]): Unit = {
    recordedEvents += LogEvent(level, message, throwable)
  }

  private def render(format: String, arguments: Seq[AnyRef]): String = {
    arguments.foldLeft(format) { (message, argument) =>
      message.replaceFirst("\\{}", String.valueOf(argument))
    }
  }
}
