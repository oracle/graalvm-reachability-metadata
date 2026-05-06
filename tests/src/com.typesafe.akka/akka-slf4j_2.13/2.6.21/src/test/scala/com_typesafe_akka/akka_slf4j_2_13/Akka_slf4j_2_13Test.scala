/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_slf4j_2_13

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.DummyClassForStringSources
import akka.event.LogMarker
import akka.event.Logging
import akka.event.slf4j.Logger
import akka.event.slf4j.SLF4JLogging
import akka.event.slf4j.Slf4jLogMarker
import akka.event.slf4j.Slf4jLogger
import akka.event.slf4j.Slf4jLoggingFilter
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MarkerFactory

class Akka_slf4j_2_13Test {
  @Test
  def loggerFactoryReturnsSlf4jLoggersForNamesClassesAndRoot(): Unit = {
    val namedLogger = Logger("com.example.NamedLogger")
    val sameNamedLogger = Logger("com.example.NamedLogger")
    val classLogger = Logger(classOf[Akka_slf4j_2_13Test], "ignored-source-name")
    val sameClassLogger = Logger(classOf[Akka_slf4j_2_13Test], "another-ignored-source-name")
    val stringSourceLogger = Logger(classOf[DummyClassForStringSources], "akka-source-name")

    assertThat(namedLogger).isNotNull
    assertThat(sameNamedLogger).isSameAs(namedLogger)
    assertThat(classLogger).isNotNull
    assertThat(sameClassLogger).isSameAs(classLogger)
    assertThat(stringSourceLogger).isSameAs(Logger("akka-source-name"))
    assertThat(Logger.root).isSameAs(Logger(org.slf4j.Logger.ROOT_LOGGER_NAME))
  }

  @Test
  def slf4jLoggingTraitProvidesStableLogger(): Unit = {
    val component = new ComponentWithSlf4jLogging

    assertThat(component.logger).isNotNull
    assertThat(component.logger).isSameAs(component.logger)
    assertThat(component.canLogAtAnyLevel).isTrue
  }

  @Test
  def slf4jLogMarkerWrapsSlf4jMarkersForScalaAndJavaApis(): Unit = {
    val slf4jMarker = MarkerFactory.getDetachedMarker("audit-event")
    slf4jMarker.add(MarkerFactory.getDetachedMarker("child-event"))

    val scalaMarker = Slf4jLogMarker(slf4jMarker)
    val javaMarker = Slf4jLogMarker.create(slf4jMarker)

    assertThat(scalaMarker.marker).isSameAs(slf4jMarker)
    assertThat(scalaMarker.name).isEqualTo("audit-event")
    assertThat(scalaMarker.properties.isEmpty).isTrue
    assertThat(javaMarker.marker).isSameAs(slf4jMarker)
    assertThat(javaMarker.name).isEqualTo(scalaMarker.name)
  }

  @Test
  def slf4jLoggingFilterCombinesEventStreamLevelWithBackendAndMarkerChecks(): Unit = {
    val system = ActorSystem(uniqueSystemName("Slf4jFilter"), ConfigFactory.parseString("akka.loglevel = ERROR"))
    try {
      val filter = new Slf4jLoggingFilter(system.settings, system.eventStream)
      val logClass = classOf[Akka_slf4j_2_13Test]
      val logSource = "filter-source"
      val slf4jMarker = MarkerFactory.getMarker("slf4j-filter-marker")
      val akkaMarker = LogMarker("akka-filter-marker", Map("requestId" -> "request-1"))

      system.eventStream.setLogLevel(Logging.ErrorLevel)
      assertThat(filter.isWarningEnabled(logClass, logSource)).isFalse
      assertThat(filter.isInfoEnabled(logClass, logSource)).isFalse
      assertThat(filter.isDebugEnabled(logClass, logSource)).isFalse
      assertThat(filter.isWarningEnabled(logClass, logSource, Slf4jLogMarker(slf4jMarker))).isFalse
      assertThat(filter.isInfoEnabled(logClass, logSource, akkaMarker)).isFalse
      assertThat(filter.isDebugEnabled(logClass, logSource, null)).isFalse

      system.eventStream.setLogLevel(Logging.DebugLevel)
      assertThat(filter.isErrorEnabled(logClass, logSource)).isEqualTo(Logger(logClass, logSource).isErrorEnabled)
      assertThat(filter.isWarningEnabled(logClass, logSource)).isEqualTo(Logger(logClass, logSource).isWarnEnabled)
      assertThat(filter.isInfoEnabled(logClass, logSource)).isEqualTo(Logger(logClass, logSource).isInfoEnabled)
      assertThat(filter.isDebugEnabled(logClass, logSource)).isEqualTo(Logger(logClass, logSource).isDebugEnabled)
      assertThat(filter.isErrorEnabled(logClass, logSource, Slf4jLogMarker(slf4jMarker)))
        .isEqualTo(Logger(logClass, logSource).isErrorEnabled(slf4jMarker))
      assertThat(filter.isWarningEnabled(logClass, logSource, akkaMarker))
        .isEqualTo(Logger(logClass, logSource).isWarnEnabled(MarkerFactory.getMarker(akkaMarker.name)))
      assertThat(filter.isInfoEnabled(logClass, logSource, null))
        .isEqualTo(Logger(logClass, logSource).isInfoEnabled(null))
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  @Test
  def slf4jLoggerAcknowledgesInitializationRequests(): Unit = {
    val latch = new CountDownLatch(1)
    val system = ActorSystem(uniqueSystemName("Slf4jLoggerInitialization"))

    try {
      val logger = system.actorOf(Props(new Slf4jLogger), "slf4j-logger")
      system.actorOf(Props(new LoggerInitializationRequester(logger, latch)), "logger-initialization-requester")

      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  @Test
  def slf4jLoggerExposesMdcAttributeNamesAndUtcTimestampFormatting(): Unit = {
    val latch = new CountDownLatch(1)
    val snapshot = new AtomicReference[MdcConfigurationSnapshot]
    val system = ActorSystem(uniqueSystemName("Slf4jLoggerMdcConfiguration"))

    try {
      system.actorOf(Props(new MdcConfigurationSnapshotter(snapshot, latch)), "mdc-configuration-snapshotter")

      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue
      assertThat(snapshot.get()).isEqualTo(
        MdcConfigurationSnapshot(
          threadAttributeName = "sourceThread",
          actorSystemAttributeName = "sourceActorSystem",
          akkaSourceAttributeName = "akkaSource",
          akkaTimestampAttributeName = "akkaTimestamp",
          akkaAddressAttributeName = "akkaAddress",
          akkaUidAttributeName = "akkaUid",
          formattedTimestamp = "01:02:03.123UTC"))
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  @Test
  def configuredSlf4jLoggerConsumesAkkaLogEventsWithMarkersMdcAndCauses(): Unit = {
    val config = ConfigFactory.parseString(
      """
        akka.loglevel = DEBUG
        akka.stdout-loglevel = OFF
        akka.loggers = ["akka.event.slf4j.Slf4jLogger"]
      """)
    val expectedEvents = 8
    val latch = new CountDownLatch(expectedEvents)
    val system = ActorSystem(uniqueSystemName("Slf4jLoggerEvents"), config)

    try {
      val collector = system.actorOf(Props(new LogEventCollector(latch)), "log-event-collector")
      assertThat(system.eventStream.subscribe(collector, classOf[Logging.Error])).isTrue
      assertThat(system.eventStream.subscribe(collector, classOf[Logging.Warning])).isTrue
      assertThat(system.eventStream.subscribe(collector, classOf[Logging.Info])).isTrue
      assertThat(system.eventStream.subscribe(collector, classOf[Logging.Debug])).isTrue

      val slf4jMarker = Slf4jLogMarker(MarkerFactory.getDetachedMarker("slf4j-event-marker"))
      val akkaMarker = LogMarker("akka-event-marker", Map("tenant" -> "blue", "attempt" -> 2))
      val cause = new IllegalArgumentException("invalid payload")
      val logClass = classOf[Akka_slf4j_2_13Test]

      system.eventStream.publish(
        Logging.Error(cause, "error-with-cause", classOf[DummyClassForStringSources], null, Map("operation" -> "write"), akkaMarker))
      system.eventStream.publish(Logging.Error("plain-error", logClass, null))
      system.eventStream.publish(
        Logging.Warning(cause, "warning-with-cause", logClass, null, Map("operation" -> "validate"), slf4jMarker))
      system.eventStream.publish(Logging.Warning("plain-warning", logClass, "warning message", Map("component" -> "collector")))
      system.eventStream.publish(
        Logging.Info("info-with-slf4j-marker", logClass, MessageWithToString("info message"), Map("level" -> "info"), slf4jMarker))
      system.eventStream.publish(Logging.Info("plain-info", classOf[DummyClassForStringSources], "info message"))
      system.eventStream.publish(
        Logging.Debug("debug-with-akka-marker", logClass, MessageWithToString("debug message"), Map("level" -> "debug"), akkaMarker))
      system.eventStream.publish(Logging.Debug("plain-debug", logClass, "debug message"))

      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  private def uniqueSystemName(prefix: String): String = s"$prefix${System.nanoTime()}"

  private final class ComponentWithSlf4jLogging extends SLF4JLogging {
    def logger: org.slf4j.Logger = log

    def canLogAtAnyLevel: Boolean = {
      logger.trace("trace through SLF4JLogging")
      logger.debug("debug through SLF4JLogging")
      logger.info("info through SLF4JLogging")
      logger.warn("warn through SLF4JLogging")
      logger.error("error through SLF4JLogging")
      true
    }
  }

  private final class LoggerInitializationRequester(logger: ActorRef, latch: CountDownLatch) extends Actor {
    override def preStart(): Unit = {
      logger.tell(Logging.InitializeLogger(context.system.eventStream), self)
    }

    override def receive: Receive = {
      case Logging.LoggerInitialized =>
        latch.countDown()
        context.stop(self)
    }
  }

  private final class MdcConfigurationSnapshotter(snapshot: AtomicReference[MdcConfigurationSnapshot], latch: CountDownLatch)
      extends Slf4jLogger {
    override def preStart(): Unit = {
      snapshot.set(
        MdcConfigurationSnapshot(
          threadAttributeName = mdcThreadAttributeName,
          actorSystemAttributeName = mdcActorSystemAttributeName,
          akkaSourceAttributeName = mdcAkkaSourceAttributeName,
          akkaTimestampAttributeName = mdcAkkaTimestamp,
          akkaAddressAttributeName = mdcAkkaAddressAttributeName,
          akkaUidAttributeName = mdcAkkaUidAttributeName,
          formattedTimestamp = formatTimestamp(3723123L)))
      latch.countDown()
      context.stop(self)
    }
  }

  private final class LogEventCollector(latch: CountDownLatch) extends Actor {
    override def receive: Receive = {
      case _: Logging.LogEvent => latch.countDown()
    }
  }

  private final case class MessageWithToString(value: String) {
    override def toString: String = s"payload:$value"
  }

  private final case class MdcConfigurationSnapshot(
      threadAttributeName: String,
      actorSystemAttributeName: String,
      akkaSourceAttributeName: String,
      akkaTimestampAttributeName: String,
      akkaAddressAttributeName: String,
      akkaUidAttributeName: String,
      formattedTimestamp: String)
}
