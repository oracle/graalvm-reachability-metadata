/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_slf4j_2_13

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.Props
import org.apache.pekko.event.DummyClassForStringSources
import org.apache.pekko.event.LogMarker
import org.apache.pekko.event.Logging
import org.apache.pekko.event.slf4j.Logger
import org.apache.pekko.event.slf4j.SLF4JLogging
import org.apache.pekko.event.slf4j.Slf4jLogMarker
import org.apache.pekko.event.slf4j.Slf4jLoggingFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MarkerFactory

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class Pekko_slf4j_2_13Test {
  @Test
  def loggerFactoryCreatesRootNamedAndClassLoggers(): Unit = {
    val namedLoggerName: String = "org.apache.pekko.slf4j.test.named"
    val rootLogger: org.slf4j.Logger = Logger.root
    val namedLogger: org.slf4j.Logger = Logger(namedLoggerName)
    val classLogger: org.slf4j.Logger = Logger(classOf[Pekko_slf4j_2_13Test], "ignored-source")

    assertThat(rootLogger).isNotNull()
    assertThat(namedLogger).isNotNull()
    assertThat(classLogger).isNotNull()
    rootLogger.isErrorEnabled
    namedLogger.isInfoEnabled
    classLogger.isDebugEnabled
  }

  @Test
  def loggerFactoryUsesSourceNameForPekkoStringSources(): Unit = {
    val sourceLoggerName: String = "org.apache.pekko.slf4j.test.string-source"
    val stringSourceLogger: org.slf4j.Logger = Logger(classOf[DummyClassForStringSources], sourceLoggerName)
    val namedLogger: org.slf4j.Logger = Logger(sourceLoggerName)

    assertThat(stringSourceLogger).isNotNull()
    assertThat(namedLogger).isNotNull()
    assertThat(stringSourceLogger.getName).isEqualTo(namedLogger.getName)
    assertThat(stringSourceLogger.isTraceEnabled).isEqualTo(namedLogger.isTraceEnabled)
    assertThat(stringSourceLogger.isWarnEnabled).isEqualTo(namedLogger.isWarnEnabled)
  }

  @Test
  def slf4jLogMarkerKeepsOriginalMarkerAndPekkoMarkerName(): Unit = {
    val marker = MarkerFactory.getDetachedMarker("audit-marker")
    val child = MarkerFactory.getDetachedMarker("audit-child")
    marker.add(child)

    val pekkoMarker: Slf4jLogMarker = Slf4jLogMarker.create(marker)

    assertThat(pekkoMarker.marker).isSameAs(marker)
    assertThat(pekkoMarker.name).isEqualTo("audit-marker")
    assertThat(pekkoMarker.getProperties).isEmpty()
    assertThat(pekkoMarker.marker.contains("audit-child")).isTrue()
  }

  @Test
  def slf4jLoggingFilterMatchesUnderlyingSlf4jLoggerForPlainAndMarkedQueries(): Unit = {
    withActorSystem("Slf4jFilterTest") { system: ActorSystem =>
      val filter = new Slf4jLoggingFilter(system.settings, system.eventStream)
      val source: String = "org.apache.pekko.slf4j.filter"
      val slf4jLogger = Logger(classOf[Pekko_slf4j_2_13Test], source)
      val slf4jMarker = MarkerFactory.getDetachedMarker("filter-marker")
      val pekkoSlf4jMarker: Slf4jLogMarker = Slf4jLogMarker.create(slf4jMarker)
      val genericPekkoMarker: LogMarker = LogMarker.create("generic-filter-marker")

      assertThat(filter.isErrorEnabled(classOf[Pekko_slf4j_2_13Test], source))
        .isEqualTo(slf4jLogger.isErrorEnabled)
      assertThat(filter.isWarningEnabled(classOf[Pekko_slf4j_2_13Test], source))
        .isEqualTo(slf4jLogger.isWarnEnabled)
      assertThat(filter.isInfoEnabled(classOf[Pekko_slf4j_2_13Test], source))
        .isEqualTo(slf4jLogger.isInfoEnabled)
      assertThat(filter.isDebugEnabled(classOf[Pekko_slf4j_2_13Test], source))
        .isEqualTo(slf4jLogger.isDebugEnabled)

      assertThat(filter.isErrorEnabled(classOf[Pekko_slf4j_2_13Test], source, pekkoSlf4jMarker))
        .isEqualTo(slf4jLogger.isErrorEnabled(slf4jMarker))
      assertThat(filter.isWarningEnabled(classOf[Pekko_slf4j_2_13Test], source, pekkoSlf4jMarker))
        .isEqualTo(slf4jLogger.isWarnEnabled(slf4jMarker))
      assertThat(filter.isInfoEnabled(classOf[Pekko_slf4j_2_13Test], source, pekkoSlf4jMarker))
        .isEqualTo(slf4jLogger.isInfoEnabled(slf4jMarker))
      assertThat(filter.isDebugEnabled(classOf[Pekko_slf4j_2_13Test], source, pekkoSlf4jMarker))
        .isEqualTo(slf4jLogger.isDebugEnabled(slf4jMarker))

      filter.isErrorEnabled(classOf[Pekko_slf4j_2_13Test], source, genericPekkoMarker)
      filter.isWarningEnabled(classOf[Pekko_slf4j_2_13Test], source, genericPekkoMarker)
      filter.isInfoEnabled(classOf[Pekko_slf4j_2_13Test], source, genericPekkoMarker)
      filter.isDebugEnabled(classOf[Pekko_slf4j_2_13Test], source, genericPekkoMarker)
    }
  }

  @Test
  def actorCanUseSlf4jLoggingTrait(): Unit = {
    withActorSystem("Slf4jTraitTest") { system: ActorSystem =>
      val loggerName = new CompletableFuture[String]()
      val actor = system.actorOf(Props(new Slf4jLoggingActor(loggerName)), "slf4jLoggingActor")

      actor ! "inspect-logger"

      assertThat(loggerName.get(5, TimeUnit.SECONDS))
        .isIn("NOP", classOf[Slf4jLoggingActor].getName)
    }
  }

  @Test
  def pekkoLoggingBusPublishesPlainAndMarkedEventsToConfiguredSlf4jLogger(): Unit = {
    withActorSystem("Slf4jLoggerBusTest") { system: ActorSystem =>
      val receivedEvents = new CountDownLatch(4)
      val probe = system.actorOf(Props(new LogEventProbe(receivedEvents)), "logEventProbe")
      assertThat(system.eventStream.subscribe(probe, classOf[Logging.LogEvent])).isTrue()

      system.eventStream.publish(
        Logging.Debug("direct-slf4j-source", classOf[Pekko_slf4j_2_13Test], "debug event from pekko-slf4j"))
      system.eventStream.publish(
        Logging.Info("direct-slf4j-source", classOf[Pekko_slf4j_2_13Test], "info event from pekko-slf4j"))
      system.eventStream.publish(
        Logging.Warning("direct-slf4j-source", classOf[Pekko_slf4j_2_13Test], "warning event from pekko-slf4j"))
      system.eventStream.publish(
        Logging.Error(
          new IllegalStateException("expected test exception"),
          "direct-slf4j-source",
          classOf[Pekko_slf4j_2_13Test],
          "error event from pekko-slf4j"))

      assertThat(receivedEvents.await(5, TimeUnit.SECONDS)).isTrue()
      system.eventStream.unsubscribe(probe)
    }
  }

  private def withActorSystem(name: String)(testBody: ActorSystem => Unit): Unit = {
    val config = ConfigFactory.parseString("""
      pekko.loggers = ["org.apache.pekko.event.slf4j.Slf4jLogger"]
      pekko.loglevel = "DEBUG"
      pekko.stdout-loglevel = "OFF"
      pekko.actor.default-dispatcher.shutdown-timeout = 1s
      """)
    val system: ActorSystem = ActorSystem(name, config)
    try {
      testBody(system)
    } finally {
      Await.result(system.terminate(), 5.seconds)
    }
  }
}

final class Slf4jLoggingActor(result: CompletableFuture[String]) extends Actor with SLF4JLogging {
  override def receive: Receive = {
    case "inspect-logger" =>
      val loggerName: String = log.getName
      log.info("inspected SLF4J logger {}", loggerName)
      result.complete(loggerName)
  }
}

final class LogEventProbe(receivedEvents: CountDownLatch) extends Actor {
  override def receive: Receive = {
    case _: Logging.LogEvent => receivedEvents.countDown()
  }
}
