/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_scala_logging.scala_logging_2_13

import ch.qos.logback.classic.{Level, LoggerContext, Logger => LogbackLogger}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.typesafe.scalalogging.Logger
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox

class LoggerMacroAnonymous1Test {
  @Test
  def runtimeTypecheckingExpandsInterpolatedLoggerMacroCall(): Unit = {
    try {
      val toolbox: ToolBox[universe.type] = universe.runtimeMirror(getClass.getClassLoader).mkToolBox()
      val typedTree: universe.Tree = toolbox.typecheck(toolbox.parse("""
          {
            import com.typesafe.scalalogging.Logger

            val logger: Logger = Logger("com.typesafe.scalalogging.runtime-typechecked")
            val user: String = "alice"
            val attempt: Int = 3

            logger.info(s"accepted $user on attempt $attempt")
          }
        """))

      val expandedSource: String = typedTree.toString
      assertThat(expandedSource).contains("isInfoEnabled")
      assertThat(expandedSource).contains("accepted {} on attempt {}")
    } catch {
      case error: Error =>
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
          throw error
        }
    }
  }

  @Test
  def interpolatedMessageIsConvertedToSlf4jFormatAtCompileTime(): Unit = {
    val loggerName: String = s"${getClass.getName}.interpolatedMessage"
    val logbackLogger: LogbackLogger = LoggerFactory.getLogger(loggerName).asInstanceOf[LogbackLogger]
    val context: LoggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val appender: ListAppender[ILoggingEvent] = new ListAppender[ILoggingEvent]
    appender.setContext(context)
    appender.start()

    val previousAdditive: Boolean = logbackLogger.isAdditive
    val previousLevel: Level = logbackLogger.getLevel
    logbackLogger.setAdditive(false)
    logbackLogger.setLevel(Level.INFO)
    logbackLogger.addAppender(appender)

    try {
      val logger: Logger = Logger(loggerName)
      val component: String = "orders"
      val shard: Int = 7

      logger.info(s"started $component shard $shard")

      assertThat(appender.list).hasSize(1)
      val event: ILoggingEvent = appender.list.get(0)
      assertThat(event.getMessage).isEqualTo("started {} shard {}")
      assertThat(event.getFormattedMessage).isEqualTo("started orders shard 7")
      assertThat(event.getArgumentArray).containsExactly("orders", Int.box(7))
    } finally {
      logbackLogger.detachAppender(appender)
      logbackLogger.setLevel(previousLevel)
      logbackLogger.setAdditive(previousAdditive)
      appender.stop()
    }
  }
}
