/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_slf4j_2_13

import akka.actor.ActorSystem
import akka.event.{DummyClassForStringSources, LogMarker, Logging}
import akka.event.slf4j.{Logger, SLF4JLogging, Slf4jLogMarker, Slf4jLoggingFilter}
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.{Marker, MarkerFactory}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class Akka_slf4j_2_13Test {
  import Akka_slf4j_2_13Test._

  @Test
  def loggerFactoryUsesClassOrStringSources(): Unit = {
    configureSimpleLogger()

    val namedLogger: org.slf4j.Logger = Logger("akka.slf4j.named-source")
    val classLogger: org.slf4j.Logger = Logger(classOf[Akka_slf4j_2_13Test], "ignored-source")
    val stringSourceLogger: org.slf4j.Logger = Logger(classOf[DummyClassForStringSources], "akka.slf4j.string-source")

    assertThat(namedLogger.getName).isEqualTo("akka.slf4j.named-source")
    assertThat(Logger.root.getName).isEqualTo(org.slf4j.Logger.ROOT_LOGGER_NAME)
    assertThat(classLogger.getName).isEqualTo(classOf[Akka_slf4j_2_13Test].getName)
    assertThat(stringSourceLogger.getName).isEqualTo("akka.slf4j.string-source")
  }

  @Test
  def slf4jLoggingTraitCreatesLoggerForConcreteClass(): Unit = {
    configureSimpleLogger()

    val component: LoggingComponent = new LoggingComponent

    assertThat(component.log.getName).contains("LoggingComponent")
  }

  @Test
  def slf4jLogMarkerPreservesWrappedMarker(): Unit = {
    configureSimpleLogger()

    val marker: Marker = MarkerFactory.getMarker("audit-marker")
    val wrappedByApply: Slf4jLogMarker = Slf4jLogMarker(marker)
    val wrappedByCreate: Slf4jLogMarker = Slf4jLogMarker.create(MarkerFactory.getMarker("created-marker"))

    assertThat(wrappedByApply.marker).isSameAs(marker)
    assertThat(wrappedByApply.name).isEqualTo("audit-marker")
    assertThat(wrappedByApply.properties.isEmpty).isTrue()
    assertThat(wrappedByApply.getProperties).isEmpty()
    assertThat(wrappedByCreate.marker.getName).isEqualTo("created-marker")
    assertThat(wrappedByCreate.name).isEqualTo("created-marker")
  }

  @Test
  def loggingFilterCombinesAkkaLogLevelWithSlf4jLoggerLevels(): Unit = {
    withActorSystem("Filter", "INFO") { system: ActorSystem =>
      val filter: Slf4jLoggingFilter = new Slf4jLoggingFilter(system.settings, system.eventStream)
      val source: String = "filter.source"
      val slf4jMarker: Slf4jLogMarker = Slf4jLogMarker(MarkerFactory.getMarker("filter-slf4j-marker"))
      val akkaMarker: LogMarker = LogMarker("filter-akka-marker")

      assertThat(filter.isErrorEnabled(classOf[Akka_slf4j_2_13Test], source)).isTrue()
      assertThat(filter.isWarningEnabled(classOf[Akka_slf4j_2_13Test], source)).isTrue()
      assertThat(filter.isInfoEnabled(classOf[Akka_slf4j_2_13Test], source)).isTrue()
      assertThat(filter.isDebugEnabled(classOf[Akka_slf4j_2_13Test], source)).isFalse()

      assertThat(filter.isErrorEnabled(classOf[Akka_slf4j_2_13Test], source, slf4jMarker)).isTrue()
      assertThat(filter.isWarningEnabled(classOf[Akka_slf4j_2_13Test], source, slf4jMarker)).isTrue()
      assertThat(filter.isInfoEnabled(classOf[Akka_slf4j_2_13Test], source, slf4jMarker)).isTrue()
      assertThat(filter.isDebugEnabled(classOf[Akka_slf4j_2_13Test], source, slf4jMarker)).isFalse()

      assertThat(filter.isErrorEnabled(classOf[Akka_slf4j_2_13Test], source, akkaMarker)).isTrue()
      assertThat(filter.isWarningEnabled(classOf[Akka_slf4j_2_13Test], source, akkaMarker)).isTrue()
      assertThat(filter.isInfoEnabled(classOf[Akka_slf4j_2_13Test], source, akkaMarker)).isTrue()
      assertThat(filter.isDebugEnabled(classOf[Akka_slf4j_2_13Test], source, akkaMarker)).isFalse()
    }
  }

  @Test
  def markerLoggingAdapterPublishesEventsToSlf4jLogger(): Unit = {
    withActorSystem("Adapter", "INFO") { system: ActorSystem =>
      val adapter = Logging.withMarker(system, "adapter.source")
      val markerProperties: java.util.Map[String, Any] = new java.util.HashMap[String, Any]()
      markerProperties.put("requestId", "request-123")
      markerProperties.put("attempt", Integer.valueOf(2))
      val akkaMarker: LogMarker = LogMarker.create("business-event", markerProperties)
      val slf4jMarker: Slf4jLogMarker = Slf4jLogMarker(MarkerFactory.getMarker("direct-slf4j-event"))

      assertThat(system.eventStream.logLevel).isEqualTo(Logging.InfoLevel)
      assertThat(adapter.isDebugEnabled(akkaMarker)).isFalse()
      assertThat(adapter.isInfoEnabled(akkaMarker)).isTrue()
      assertThat(adapter.isWarningEnabled(slf4jMarker)).isTrue()
      assertThat(adapter.isErrorEnabled(slf4jMarker)).isTrue()

      adapter.info(akkaMarker, "info event {}", "two")
      adapter.warning(slf4jMarker, "warning event {}", "three")
      adapter.error(slf4jMarker, new IllegalStateException("expected test exception"), "error event {}", "four")
    }
  }

  private final class LoggingComponent extends SLF4JLogging
}

object Akka_slf4j_2_13Test {
  private val nextSystemId: AtomicInteger = new AtomicInteger()

  private def configureSimpleLogger(): Unit = {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
    System.setProperty("org.slf4j.simpleLogger.showDateTime", "false")
    System.setProperty("org.slf4j.simpleLogger.showThreadName", "false")
  }

  private def withActorSystem(testName: String, logLevel: String)(body: ActorSystem => Unit): Unit = {
    configureSimpleLogger()

    val config = ConfigFactory.parseString(
      s"""
         |akka {
         |  loggers = ["akka.event.slf4j.Slf4jLogger"]
         |  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
         |  loglevel = "$logLevel"
         |  stdout-loglevel = "OFF"
         |  log-dead-letters = 0
         |  log-dead-letters-during-shutdown = false
         |}
         |""".stripMargin
    ).withFallback(ConfigFactory.load())
    val systemName: String = s"Slf4j${testName}${nextSystemId.incrementAndGet()}"
    val system: ActorSystem = ActorSystem(systemName, config)

    try {
      body(system)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
