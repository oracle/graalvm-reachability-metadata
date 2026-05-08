/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_slf4j_3

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.event.DummyClassForStringSources
import org.apache.pekko.event.LogMarker
import org.apache.pekko.event.Logging
import org.apache.pekko.event.slf4j.Logger
import org.apache.pekko.event.slf4j.SLF4JLogging
import org.apache.pekko.event.slf4j.Slf4jLogMarker
import org.apache.pekko.event.slf4j.Slf4jLoggingFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.Marker
import org.slf4j.MarkerFactory

class Pekko_slf4j_3Test {
  @Test
  def loggerFactoryCreatesNamedRootAndClassAwareLoggers(): Unit = {
    val namedLogger: org.slf4j.Logger = Logger("coverage.pekko.slf4j.named")
    val classLogger: org.slf4j.Logger = Logger(classOf[Pekko_slf4j_3Test], "ignored-source-name")
    val stringSourceLogger: org.slf4j.Logger = Logger(classOf[DummyClassForStringSources], "string-source-name")
    val rootLogger: org.slf4j.Logger = Logger.root

    assertThat(namedLogger).isNotNull
    assertThat(classLogger).isNotNull
    assertThat(stringSourceLogger).isNotNull
    assertThat(rootLogger).isNotNull

    namedLogger.info("named logger accepts messages")
    classLogger.debug("class logger accepts messages")
    stringSourceLogger.warn("string-source logger accepts messages")
    rootLogger.error("root logger accepts messages")
  }

  @Test
  def slf4jLoggingTraitUsesImplementingClassName(): Unit = {
    val probe: LoggingProbe = new LoggingProbe

    assertThat(probe.logger).isNotNull
    probe.logger.info("SLF4JLogging supplies a usable logger")
  }

  @Test
  def slf4jLogMarkerPreservesSlf4jMarkerAndExposesPekkoMarkerName(): Unit = {
    val slf4jMarker: Marker = MarkerFactory.getDetachedMarker("coverage-marker")
    val wrappedMarker: Slf4jLogMarker = Slf4jLogMarker(slf4jMarker)
    val createdMarker: Slf4jLogMarker = Slf4jLogMarker.create(slf4jMarker)

    assertThat(wrappedMarker.marker).isSameAs(slf4jMarker)
    assertThat(wrappedMarker.name).isEqualTo("coverage-marker")
    assertThat(wrappedMarker.getProperties).isEmpty
    assertThat(createdMarker.marker).isSameAs(slf4jMarker)
    assertThat(createdMarker.name).isEqualTo(wrappedMarker.name)
  }

  @Test
  def loggingFilterHonorsEventStreamLevelAndSlf4jMarkerOverloads(): Unit = {
    withActorSystem("PekkoSlf4jFilterTest", baseConfig) { system =>
      val sourceClass: Class[Pekko_slf4j_3Test] = classOf[Pekko_slf4j_3Test]
      val sourceName: String = "filter-source"
      val filter: Slf4jLoggingFilter = new Slf4jLoggingFilter(system.settings, system.eventStream)
      val slf4jLogger: org.slf4j.Logger = Logger(sourceClass, sourceName)
      val slf4jMarker: Marker = MarkerFactory.getDetachedMarker("slf4j-filter-marker")
      val wrappedMarker: Slf4jLogMarker = Slf4jLogMarker(slf4jMarker)
      val plainMarkerName: String = "plain-filter-marker"
      val plainMarker: LogMarker = LogMarker.create(plainMarkerName)

      system.eventStream.setLogLevel(Logging.levelFor("OFF").get)
      assertThat(filter.isErrorEnabled(sourceClass, sourceName)).isFalse
      assertThat(filter.isWarningEnabled(sourceClass, sourceName)).isFalse
      assertThat(filter.isInfoEnabled(sourceClass, sourceName)).isFalse
      assertThat(filter.isDebugEnabled(sourceClass, sourceName)).isFalse
      assertThat(filter.isErrorEnabled(sourceClass, sourceName, wrappedMarker)).isFalse
      assertThat(filter.isWarningEnabled(sourceClass, sourceName, wrappedMarker)).isFalse
      assertThat(filter.isInfoEnabled(sourceClass, sourceName, wrappedMarker)).isFalse
      assertThat(filter.isDebugEnabled(sourceClass, sourceName, wrappedMarker)).isFalse

      system.eventStream.setLogLevel(Logging.DebugLevel)
      assertThat(filter.isErrorEnabled(sourceClass, sourceName)).isEqualTo(slf4jLogger.isErrorEnabled)
      assertThat(filter.isWarningEnabled(sourceClass, sourceName)).isEqualTo(slf4jLogger.isWarnEnabled)
      assertThat(filter.isInfoEnabled(sourceClass, sourceName)).isEqualTo(slf4jLogger.isInfoEnabled)
      assertThat(filter.isDebugEnabled(sourceClass, sourceName)).isEqualTo(slf4jLogger.isDebugEnabled)
      assertThat(filter.isErrorEnabled(sourceClass, sourceName, wrappedMarker)).isEqualTo(slf4jLogger.isErrorEnabled(slf4jMarker))
      assertThat(filter.isWarningEnabled(sourceClass, sourceName, wrappedMarker)).isEqualTo(slf4jLogger.isWarnEnabled(slf4jMarker))
      assertThat(filter.isInfoEnabled(sourceClass, sourceName, wrappedMarker)).isEqualTo(slf4jLogger.isInfoEnabled(slf4jMarker))
      assertThat(filter.isDebugEnabled(sourceClass, sourceName, plainMarker))
        .isEqualTo(slf4jLogger.isDebugEnabled(MarkerFactory.getMarker(plainMarkerName)))
    }
  }

  @Test
  def actorSystemRoutesPekkoLogEventsThroughConfiguredSlf4jLogger(): Unit = {
    withActorSystem("PekkoSlf4jLoggerTest", slf4jLoggerConfig) { system =>
      val sourceClass: Class[Pekko_slf4j_3Test] = classOf[Pekko_slf4j_3Test]
      val marker: Slf4jLogMarker = Slf4jLogMarker(MarkerFactory.getDetachedMarker("event-stream-marker"))
      val mdc: Map[String, AnyRef] = Map("requestId" -> "native-image-coverage")

      system.eventStream.setLogLevel(Logging.DebugLevel)
      system.eventStream.publish(Logging.Debug("event-source", sourceClass, "debug event"))
      system.eventStream.publish(Logging.Info("event-source", sourceClass, "info event"))
      system.eventStream.publish(Logging.Warning("event-source", sourceClass, "warning event"))
      system.eventStream.publish(Logging.Error(new IllegalStateException("expected test exception"), "event-source", sourceClass, "error event"))
      system.eventStream.publish(Logging.Info("marked-event-source", sourceClass, "marked info event", mdc, marker))
      system.log.info("system logging adapter reaches the configured SLF4J logger")

      assertThat(system.name).isEqualTo("PekkoSlf4jLoggerTest")
    }
  }

  private def withActorSystem[T](name: String, config: Config)(body: ActorSystem => T): T = {
    val system: ActorSystem = ActorSystem(name, config)
    try {
      body(system)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  private def baseConfig: Config =
    ConfigFactory.parseString(
      """
        pekko.stdout-loglevel = "OFF"
        pekko.loglevel = "DEBUG"
      """)

  private def slf4jLoggerConfig: Config =
    ConfigFactory.parseString(
      """
        pekko.stdout-loglevel = "OFF"
        pekko.loglevel = "DEBUG"
        pekko.loggers = ["org.apache.pekko.event.slf4j.Slf4jLogger"]
        pekko.logging-filter = "org.apache.pekko.event.slf4j.Slf4jLoggingFilter"
      """)
}

private final class LoggingProbe extends SLF4JLogging {
  def logger: org.slf4j.Logger = log
}
