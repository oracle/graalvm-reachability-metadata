/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_slf4j_2_13

import java.time.Duration
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.Props
import org.apache.pekko.event.LogMarker
import org.apache.pekko.event.Logging
import org.apache.pekko.event.Logging.Debug
import org.apache.pekko.event.Logging.Error
import org.apache.pekko.event.Logging.Info
import org.apache.pekko.event.Logging.InitializeLogger
import org.apache.pekko.event.Logging.LoggerInitialized
import org.apache.pekko.event.Logging.Warning
import org.apache.pekko.event.Logging.WarningLevel
import org.apache.pekko.event.slf4j.Logger
import org.apache.pekko.event.slf4j.SLF4JLogging
import org.apache.pekko.event.slf4j.Slf4jLogMarker
import org.apache.pekko.event.slf4j.Slf4jLogger
import org.apache.pekko.event.slf4j.Slf4jLoggingFilter
import org.apache.pekko.pattern.Patterns
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory

class Pekko_slf4j_2_13Test {
  @Test
  def loggerFactoryDelegatesToSlf4jForAllPublicFactoryMethods(): Unit = {
    val categoryName: String = "org.apache.pekko.slf4j.test.category"

    assertThat(Logger(categoryName)).isSameAs(LoggerFactory.getLogger(categoryName))
    assertThat(Logger.root).isSameAs(LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
    assertThat(Logger(classOf[Pekko_slf4j_2_13Test], "ignored-source"))
      .isSameAs(LoggerFactory.getLogger(classOf[Pekko_slf4j_2_13Test]))
  }

  @Test
  def slf4jLogMarkerWrapsTheProvidedSlf4jMarkerForScalaAndJavaApis(): Unit = {
    val scalaMarker = MarkerFactory.getMarker("pekko-slf4j-scala-marker")
    val javaMarker = MarkerFactory.getMarker("pekko-slf4j-java-marker")

    val wrappedScalaMarker: Slf4jLogMarker = Slf4jLogMarker(scalaMarker)
    val wrappedJavaMarker: Slf4jLogMarker = Slf4jLogMarker.create(javaMarker)

    assertThat(wrappedScalaMarker.marker).isSameAs(scalaMarker)
    assertThat(wrappedScalaMarker.name).isEqualTo(scalaMarker.getName)
    assertThat(wrappedScalaMarker.properties.isEmpty).isTrue()
    assertThat(wrappedJavaMarker.marker).isSameAs(javaMarker)
    assertThat(wrappedJavaMarker.name).isEqualTo(javaMarker.getName)
    assertThat(wrappedJavaMarker.getProperties).isEmpty()
  }

  @Test
  def slf4jLoggingTraitCreatesALazyLoggerForTheImplementingClass(): Unit = {
    val component: LoggingComponent = new LoggingComponent

    val firstLogger = component.logger
    val secondLogger = component.logger

    assertThat(firstLogger).isSameAs(secondLogger)
    assertThat(firstLogger).isSameAs(LoggerFactory.getLogger(component.getClass.getName))
  }

  @Test
  def slf4jLoggingFilterCombinesEventStreamLevelWithSlf4jBackendLevel(): Unit = {
    withActorSystem("pekko-slf4j-filter-test") { system =>
      val filter: Slf4jLoggingFilter = new Slf4jLoggingFilter(system.settings, system.eventStream)
      val logClass: Class[_] = classOf[Pekko_slf4j_2_13Test]
      val logSource: String = "filter.source"
      val backingLogger = Logger(logClass, logSource)
      val slf4jMarker = MarkerFactory.getMarker("pekko-slf4j-filter-marker")
      val wrappedMarker: Slf4jLogMarker = Slf4jLogMarker(slf4jMarker)
      val genericMarker: LogMarker = LogMarker("pekko-slf4j-generic-filter-marker", Map("requestId" -> "filter-1"))

      system.eventStream.setLogLevel(Logging.DebugLevel)
      assertThat(filter.isErrorEnabled(logClass, logSource)).isEqualTo(backingLogger.isErrorEnabled)
      assertThat(filter.isWarningEnabled(logClass, logSource)).isEqualTo(backingLogger.isWarnEnabled)
      assertThat(filter.isInfoEnabled(logClass, logSource)).isEqualTo(backingLogger.isInfoEnabled)
      assertThat(filter.isDebugEnabled(logClass, logSource)).isEqualTo(backingLogger.isDebugEnabled)
      assertThat(filter.isErrorEnabled(logClass, logSource, wrappedMarker)).isEqualTo(backingLogger.isErrorEnabled(slf4jMarker))
      assertThat(filter.isWarningEnabled(logClass, logSource, wrappedMarker)).isEqualTo(backingLogger.isWarnEnabled(slf4jMarker))
      assertThat(filter.isInfoEnabled(logClass, logSource, wrappedMarker)).isEqualTo(backingLogger.isInfoEnabled(slf4jMarker))
      assertThat(filter.isDebugEnabled(logClass, logSource, wrappedMarker)).isEqualTo(backingLogger.isDebugEnabled(slf4jMarker))

      val convertedGenericMarker = MarkerFactory.getMarker(genericMarker.name)
      assertThat(filter.isInfoEnabled(logClass, logSource, genericMarker))
        .isEqualTo(backingLogger.isInfoEnabled(convertedGenericMarker))

      system.eventStream.setLogLevel(WarningLevel)
      assertThat(filter.isErrorEnabled(logClass, logSource)).isEqualTo(backingLogger.isErrorEnabled)
      assertThat(filter.isWarningEnabled(logClass, logSource)).isEqualTo(backingLogger.isWarnEnabled)
      assertThat(filter.isInfoEnabled(logClass, logSource)).isFalse()
      assertThat(filter.isDebugEnabled(logClass, logSource)).isFalse()
    }
  }

  @Test
  def slf4jLoggerActorInitializesAndHandlesAllLogEventShapes(): Unit = {
    withActorSystem("pekko-slf4j-logger-test") { system =>
      val logger = system.actorOf(
        Props[Slf4jLogger]().withMailbox("pekko.actor.mailbox.logger-queue"),
        "direct-slf4j-logger")
      val initializeReply = Patterns
        .ask(logger, InitializeLogger(system.eventStream), Duration.ofSeconds(3))
        .toCompletableFuture
        .get(3, TimeUnit.SECONDS)

      assertThat(initializeReply).isEqualTo(LoggerInitialized)

      val logClass: Class[_] = classOf[Pekko_slf4j_2_13Test]
      val mdc: Map[String, Any] = Map("requestId" -> "logger-1", "attempt" -> 1)
      val slf4jMarker: Slf4jLogMarker = Slf4jLogMarker(MarkerFactory.getMarker("pekko-slf4j-direct-marker"))
      val genericMarker: LogMarker = LogMarker("pekko-slf4j-direct-generic-marker", Map("component" -> "logger-test"))

      logger ! Error(new IllegalStateException("boom"), "error.source", logClass, "error message", mdc, slf4jMarker)
      logger ! Error("error.no-cause.source", logClass, null, mdc, genericMarker)
      logger ! Warning(new IllegalArgumentException("warn"), "warning.source", logClass, "warning message", mdc, genericMarker)
      logger ! Warning("warning.no-cause.source", logClass, null, mdc, slf4jMarker)
      logger ! Info("info.source", logClass, Map("payload" -> 42), mdc, genericMarker)
      logger ! Debug("debug.source", logClass, "debug message", mdc, slf4jMarker)

      val stopped = Patterns
        .gracefulStop(logger, Duration.ofSeconds(3))
        .toCompletableFuture
        .get(3, TimeUnit.SECONDS)
      assertThat(stopped).isTrue()
    }
  }

  private def withActorSystem(name: String)(testBody: ActorSystem => Unit): Unit = {
    val config = ConfigFactory.parseString("""
        pekko.loggers = []
        pekko.stdout-loglevel = "OFF"
        pekko.loglevel = "DEBUG"
        pekko.actor.allow-java-serialization = off
        pekko.actor.warn-about-java-serializer-usage = off
      """)
    val system: ActorSystem = ActorSystem(name, config)
    try {
      testBody(system)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  private final class LoggingComponent extends SLF4JLogging {
    def logger: org.slf4j.Logger = log
  }
}
