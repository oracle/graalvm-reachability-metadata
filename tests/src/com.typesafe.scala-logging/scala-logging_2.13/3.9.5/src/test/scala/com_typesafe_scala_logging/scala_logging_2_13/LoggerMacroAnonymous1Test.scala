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
import org.junit.jupiter.api.Test

class LoggerMacroAnonymous1Test {
  @Test
  def interpolatedMessageIsConvertedToSlf4jFormatAtCompileTime(): Unit = {
    val loggerName: String = s"${getClass.getName}.interpolatedMessage"
    val context: LoggerContext = new LoggerContext()
    val logbackLogger: LogbackLogger = context.getLogger(loggerName)
    val appender: ListAppender[ILoggingEvent] = new ListAppender[ILoggingEvent]
    appender.setContext(context)
    appender.start()

    val previousAdditive: Boolean = logbackLogger.isAdditive
    val previousLevel: Level = logbackLogger.getLevel
    logbackLogger.setAdditive(false)
    logbackLogger.setLevel(Level.INFO)
    logbackLogger.addAppender(appender)

    try {
      val logger: Logger = Logger(logbackLogger)
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
      context.stop()
    }
  }
}
