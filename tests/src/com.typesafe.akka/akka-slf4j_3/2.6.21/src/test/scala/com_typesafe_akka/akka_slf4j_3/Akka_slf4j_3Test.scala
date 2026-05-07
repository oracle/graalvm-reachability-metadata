/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_slf4j_3

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*

import akka.actor.ActorSystem
import akka.event.DummyClassForStringSources
import akka.event.LogMarker
import akka.event.Logging
import akka.event.MarkerLoggingAdapter
import akka.event.slf4j.Logger
import akka.event.slf4j.Slf4jLogMarker
import akka.event.slf4j.Slf4jLoggingFilter
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory

class Akka_slf4j_3Test {
  @Test
  def createsSlf4jLoggersFromAkkaSources(): Unit = {
    val namedLogger: org.slf4j.Logger = Logger("akka.slf4j.named")
    val classLogger: org.slf4j.Logger = Logger(classOf[Akka_slf4j_3Test], "ignored-source")
    val stringSourceLogger: org.slf4j.Logger = Logger(classOf[DummyClassForStringSources], "string-source")
    val rootLogger: org.slf4j.Logger = Logger.root

    assertThat(namedLogger.getName).isEqualTo("akka.slf4j.named")
    assertThat(classLogger.getName).isEqualTo(classOf[Akka_slf4j_3Test].getName)
    assertThat(stringSourceLogger.getName).isEqualTo("string-source")
    assertThat(rootLogger.getName).isEqualTo(org.slf4j.Logger.ROOT_LOGGER_NAME)
  }

  @Test
  def wrapsSlf4jMarkersAsAkkaLogMarkers(): Unit = {
    val parentMarker: org.slf4j.Marker = MarkerFactory.getMarker("security-audit")
    val childMarker: org.slf4j.Marker = MarkerFactory.getDetachedMarker("permission-check")
    parentMarker.add(childMarker)

    val akkaMarker: Slf4jLogMarker = Slf4jLogMarker(parentMarker)
    val javaFactoryMarker: Slf4jLogMarker = Slf4jLogMarker.create(parentMarker)

    assertThat(akkaMarker.name).isEqualTo("security-audit")
    assertThat(akkaMarker.marker).isSameAs(parentMarker)
    assertThat(akkaMarker.marker.contains(childMarker)).isTrue
    assertThat(akkaMarker.properties.isEmpty).isTrue
    assertThat(javaFactoryMarker.marker).isSameAs(parentMarker)
  }

  @Test
  def loggingFilterCombinesAkkaEventStreamLevelWithSlf4jLevelAndMarkers(): Unit = {
    val system: ActorSystem = actorSystem("filter-system")
    withConfiguredLogger(classOf[Akka_slf4j_3Test], Level.DEBUG) { _ =>
      try {
        val filter: Slf4jLoggingFilter = new Slf4jLoggingFilter(system.settings, system.eventStream)
        val slf4jMarker: Slf4jLogMarker = Slf4jLogMarker(MarkerFactory.getMarker("filter-slf4j-marker"))
        val markerProperties: java.util.Map[String, Any] = Map[String, Any]("component" -> "filter").asJava
        val akkaMarker: LogMarker = LogMarker.create("filter-akka-marker", markerProperties)

        system.eventStream.setLogLevel(Logging.DebugLevel)
        assertThat(filter.isDebugEnabled(classOf[Akka_slf4j_3Test], "source")).isTrue
        assertThat(filter.isInfoEnabled(classOf[Akka_slf4j_3Test], "source")).isTrue
        assertThat(filter.isWarningEnabled(classOf[Akka_slf4j_3Test], "source")).isTrue
        assertThat(filter.isErrorEnabled(classOf[Akka_slf4j_3Test], "source")).isTrue
        assertThat(filter.isDebugEnabled(classOf[Akka_slf4j_3Test], "source", slf4jMarker)).isTrue
        assertThat(filter.isInfoEnabled(classOf[Akka_slf4j_3Test], "source", akkaMarker)).isTrue

        system.eventStream.setLogLevel(Logging.WarningLevel)
        assertThat(filter.isDebugEnabled(classOf[Akka_slf4j_3Test], "source")).isFalse
        assertThat(filter.isInfoEnabled(classOf[Akka_slf4j_3Test], "source")).isFalse
        assertThat(filter.isWarningEnabled(classOf[Akka_slf4j_3Test], "source", slf4jMarker)).isTrue
        assertThat(filter.isErrorEnabled(classOf[Akka_slf4j_3Test], "source", akkaMarker)).isTrue
      } finally {
        terminate(system)
      }
    }
  }

  @Test
  def slf4jLoggerPublishesAkkaLogEventsWithMarkerAndMdc(): Unit = {
    val system: ActorSystem = actorSystem("mdc-system")
    val latch: CountDownLatch = new CountDownLatch(1)
    val appender: RecordingAppender = new RecordingAppender(latch)

    withConfiguredLogger(classOf[Akka_slf4j_3Test], Level.INFO, Some(appender)) { _ =>
      try {
        val marker: Slf4jLogMarker = Slf4jLogMarker(MarkerFactory.getMarker("business-event"))
        val event = Logging.Info(
          "akka.slf4j.test-source",
          classOf[Akka_slf4j_3Test],
          "important message",
          Map("requestId" -> "request-123"),
          marker
        )

        system.eventStream.publish(event)

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue
        val loggedEvent: ILoggingEvent = appender.events.asScala.find(_.getFormattedMessage == "important message").get
        val mdc: java.util.Map[String, String] = loggedEvent.getMDCPropertyMap

        assertThat(loggedEvent.getLevel).isEqualTo(Level.INFO)
        assertThat(loggedEvent.getLoggerName).isEqualTo(classOf[Akka_slf4j_3Test].getName)
        assertThat(loggedEvent.getMarker.getName).isEqualTo("business-event")
        assertThat(mdc).containsEntry("requestId", "request-123")
        assertThat(mdc).containsEntry("akkaSource", "akka.slf4j.test-source")
        assertThat(mdc).containsEntry("sourceActorSystem", "mdc-system")
        assertThat(mdc).containsKeys("sourceThread", "akkaTimestamp", "akkaAddress", "akkaUid")
      } finally {
        terminate(system)
      }
    }
  }

  @Test
  def slf4jLoggerPreservesThrowableCausesForErrorEvents(): Unit = {
    val system: ActorSystem = actorSystem("error-system")
    val latch: CountDownLatch = new CountDownLatch(1)
    val appender: RecordingAppender = new RecordingAppender(latch)

    withConfiguredLogger(classOf[Akka_slf4j_3Test], Level.ERROR, Some(appender)) { _ =>
      try {
        val failure: RuntimeException = new RuntimeException("database unavailable")
        val event = Logging.Error(
          failure,
          "akka.slf4j.error-source",
          classOf[Akka_slf4j_3Test],
          "failed to persist order"
        )

        system.eventStream.publish(event)

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue
        val loggedEvent: ILoggingEvent = appender.events.asScala.find(_.getFormattedMessage == "failed to persist order").get

        assertThat(loggedEvent.getLevel).isEqualTo(Level.ERROR)
        assertThat(loggedEvent.getLoggerName).isEqualTo(classOf[Akka_slf4j_3Test].getName)
        assertThat(loggedEvent.getThrowableProxy).isNotNull
        assertThat(loggedEvent.getThrowableProxy.getClassName).isEqualTo(classOf[RuntimeException].getName)
        assertThat(loggedEvent.getThrowableProxy.getMessage).isEqualTo("database unavailable")
      } finally {
        terminate(system)
      }
    }
  }

  @Test
  def markerLoggingAdapterEmitsFormattedMessagesThroughSlf4jLogger(): Unit = {
    val system: ActorSystem = actorSystem("adapter-system")
    val latch: CountDownLatch = new CountDownLatch(1)
    val appender: RecordingAppender = new RecordingAppender(latch)

    withConfiguredLogger("adapter-source", Level.DEBUG, Some(appender)) { _ =>
      try {
        val filter: Slf4jLoggingFilter = new Slf4jLoggingFilter(system.settings, system.eventStream)
        val adapter: MarkerLoggingAdapter =
          new MarkerLoggingAdapter(system.eventStream, "adapter-source", classOf[DummyClassForStringSources], filter)
        val markerProperties: java.util.Map[String, Any] = Map[String, Any]("tenant" -> "blue").asJava
        val marker: LogMarker = LogMarker.create("adapter-marker", markerProperties)

        system.eventStream.setLogLevel(Logging.DebugLevel)
        adapter.info(marker, "processed {} records for {}", Int.box(7), "orders")

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue
        val loggedEvent: ILoggingEvent = appender.events.asScala.find(_.getFormattedMessage == "processed 7 records for orders").get

        assertThat(loggedEvent.getLevel).isEqualTo(Level.INFO)
        assertThat(loggedEvent.getLoggerName).isEqualTo("adapter-source")
        assertThat(loggedEvent.getMarker.getName).isEqualTo("adapter-marker")
        assertThat(loggedEvent.getMDCPropertyMap).containsEntry("tenant", "blue")
      } finally {
        terminate(system)
      }
    }
  }

  private def actorSystem(name: String): ActorSystem = {
    val config = ConfigFactory.parseString(
      """
      akka {
        loglevel = "DEBUG"
        stdout-loglevel = "OFF"
        loggers = ["akka.event.slf4j.Slf4jLogger"]
        logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
      }
      """
    )
    ActorSystem(name, config)
  }

  private def terminate(system: ActorSystem): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }

  private def withConfiguredLogger[A](
      loggerClass: Class[?],
      level: Level,
      appender: Option[RecordingAppender] = None
  )(body: LogbackLogger => A): A = {
    val logger: LogbackLogger = LoggerFactory.getLogger(loggerClass).asInstanceOf[LogbackLogger]
    configureLogger(logger, level, appender)(body)
  }

  private def withConfiguredLogger[A](
      loggerName: String,
      level: Level,
      appender: Option[RecordingAppender]
  )(body: LogbackLogger => A): A = {
    val logger: LogbackLogger = LoggerFactory.getLogger(loggerName).asInstanceOf[LogbackLogger]
    configureLogger(logger, level, appender)(body)
  }

  private def configureLogger[A](
      logger: LogbackLogger,
      level: Level,
      appender: Option[RecordingAppender]
  )(body: LogbackLogger => A): A = {
    val previousLevel: Level = logger.getLevel
    val previousAdditive: Boolean = logger.isAdditive
    val context: LoggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    appender.foreach { recordingAppender =>
      recordingAppender.setContext(context)
      recordingAppender.start()
      logger.addAppender(recordingAppender)
    }
    logger.setLevel(level)
    logger.setAdditive(false)
    try {
      body(logger)
    } finally {
      appender.foreach { recordingAppender =>
        logger.detachAppender(recordingAppender)
        recordingAppender.stop()
      }
      logger.setLevel(previousLevel)
      logger.setAdditive(previousAdditive)
    }
  }

  private final class RecordingAppender(latch: CountDownLatch) extends AppenderBase[ILoggingEvent] {
    val events: CopyOnWriteArrayList[ILoggingEvent] = new CopyOnWriteArrayList[ILoggingEvent]()

    override protected def append(eventObject: ILoggingEvent): Unit = {
      eventObject.prepareForDeferredProcessing()
      events.add(eventObject)
      latch.countDown()
    }
  }
}
