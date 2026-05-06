/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_scala_logging.scala_logging_2_13

import com.typesafe.scalalogging.Logger
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.Marker

import scala.collection.mutable.ListBuffer
import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

class LoggerMacroInnerAnonfunAnonymous1Test {
  @Test
  def runtimeCompilerExpandsInterpolatedLoggingCall(): Unit = {
    try {
      val toolbox: ToolBox[universe.type] = universe.runtimeMirror(getClass.getClassLoader).mkToolBox()
      val compiled: () => Any = toolbox.compile(toolbox.parse("""
        import com.typesafe.scalalogging.Logger

        val logger: Logger = Logger("runtime-toolbox-macro")
        val count: Int = 7
        logger.info(s"processed $count records from runtime toolbox")
        "compiled"
      """))

      assertEquals("compiled", compiled())
    } catch {
      case error: Error =>
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
          throw error
        }
    }
  }

  @Test
  def interpolatedMessageMacroReadsStringContextExpandeeAttachment(): Unit = {
    val underlying: RecordingSlf4jLogger = RecordingSlf4jLogger("macro-expandee", enabledLevels = Set("INFO"))
    val logger: Logger = Logger(underlying)
    val count: Int = 7
    val source: String = "events"

    logger.info(s"processed $count records from $source with literal {}")

    assertEquals(1, underlying.records.size)
    val record: LogRecord = underlying.records.head
    assertEquals("INFO", record.level)
    assertEquals("processed {} records from {} with literal \\{}", record.message)
    assertEquals(Seq(count, source), record.arguments)
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
