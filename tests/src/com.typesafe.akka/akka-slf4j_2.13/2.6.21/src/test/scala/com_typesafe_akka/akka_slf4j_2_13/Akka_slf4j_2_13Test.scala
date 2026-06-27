/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_slf4j_2_13

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.DiagnosticActorLogging
import akka.event.Logging
import akka.event.Logging.MDC
import akka.event.slf4j.SLF4JLogging
import akka.event.slf4j.Slf4jLogMarker
import akka.event.slf4j.{Logger => AkkaSlf4jLogger}
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.slf4j.MarkerFactory

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

class Akka_slf4j_2_13Test {
  import Akka_slf4j_2_13Test._

  @Test
  def configuredActorSystemRoutesClassicLoggingThroughSlf4jAdapter(): Unit = {
    val debugMessage: String = "debug message routed through akka-slf4j"
    val infoMessage: String = "info message routed through akka-slf4j"
    val warningMessage: String = "warning message routed through akka-slf4j"
    val errorMessage: String = "error message routed through akka-slf4j"
    val error: IllegalStateException =
      new IllegalStateException("expected slf4j adapter failure marker")

    withCapturingAppender(Set(debugMessage, infoMessage, warningMessage, errorMessage)) { appender =>
      withActorSystem("Slf4jAdapterClassicLoggingTest") { system =>
        val log = Logging(system, "classic-logging-source")

        log.debug(debugMessage)
        log.info(infoMessage)
        log.warning(warningMessage)
        log.error(error, errorMessage)

        appender.awaitExpectedMessages()
        assertCaptured(appender, Level.DEBUG, debugMessage)
        assertCaptured(appender, Level.INFO, infoMessage)
        assertCaptured(appender, Level.WARN, warningMessage)
        val errorEvent: ILoggingEvent = assertCaptured(appender, Level.ERROR, errorMessage)
        assertNotNull(errorEvent.getThrowableProxy)
        assertEquals(
          classOf[IllegalStateException].getName,
          errorEvent.getThrowableProxy.getClassName
        )
      }
    }
  }

  @Test
  def diagnosticActorLoggingPublishesMdcThroughSlf4jAdapter(): Unit = {
    val message: String = "diagnostic actor message routed through akka-slf4j"
    val correlationId: String = "correlation-id-akka-slf4j"

    withCapturingAppender(Set(message)) { appender =>
      withActorSystem("Slf4jAdapterDiagnosticLoggingTest") { system =>
        val actor = system.actorOf(Props(new DiagnosticLoggingActor), "diagnostic-logging-actor")

        actor ! LogWithMdc(correlationId, message)

        appender.awaitExpectedMessages()
        val event: ILoggingEvent = assertCaptured(appender, Level.INFO, message)
        assertEquals(correlationId, event.getMDCPropertyMap.get("correlationId"))
      }
    }
  }

  @Test
  def markerLoggingPublishesSlf4jMarkerThroughAdapter(): Unit = {
    val message: String = "marker message routed through akka-slf4j"
    val markerName: String = "akka-slf4j-functional-marker"
    val slf4jMarker: Marker = MarkerFactory.getMarker(markerName)
    val logMarker: Slf4jLogMarker = Slf4jLogMarker(slf4jMarker)

    withCapturingAppender(Set(message)) { appender =>
      withActorSystem("Slf4jAdapterMarkerLoggingTest") { system =>
        val log = Logging.withMarker(system, "marker-logging-source")

        log.info(logMarker, message)

        appender.awaitExpectedMessages()
        val event: ILoggingEvent = assertCaptured(appender, Level.INFO, message)
        assertNotNull(event.getMarker)
        assertEquals(markerName, event.getMarker.getName)
      }
    }
  }

  @Test
  def publicSlf4jLoggerFactoriesCreateUsableNamedLoggers(): Unit = {
    val namedLoggerName: String = "akka.slf4j.public.factory"
    val namedLoggerMessage: String = "message written through akka slf4j logger name factory"
    val classLoggerMessage: String = "message written through akka slf4j logger class factory"
    val traitLoggerMessage: String = "message written through akka slf4j logging trait"
    val component: ComponentUsingSlf4jLogging = new ComponentUsingSlf4jLogging

    withCapturingAppender(Set(namedLoggerMessage, classLoggerMessage, traitLoggerMessage)) { appender =>
      AkkaSlf4jLogger(namedLoggerName).info(namedLoggerMessage)
      AkkaSlf4jLogger(classOf[ComponentUsingSlf4jLogging], "ignored-source-name").info(classLoggerMessage)
      component.writeInfo(traitLoggerMessage)

      appender.awaitExpectedMessages()
      val namedEvent: ILoggingEvent = assertCaptured(appender, Level.INFO, namedLoggerMessage)
      assertEquals(namedLoggerName, namedEvent.getLoggerName)
      val classEvent: ILoggingEvent = assertCaptured(appender, Level.INFO, classLoggerMessage)
      assertEquals(classOf[ComponentUsingSlf4jLogging].getName, classEvent.getLoggerName)
      val traitEvent: ILoggingEvent = assertCaptured(appender, Level.INFO, traitLoggerMessage)
      assertEquals(component.getClass.getName, traitEvent.getLoggerName)
    }
  }

  private def withActorSystem(name: String)(testBody: ActorSystem => Unit): Unit = {
    val system: ActorSystem = ActorSystem(name, slf4jLoggingConfig)
    try {
      testBody(system)
    } finally {
      Await.result(system.terminate(), TestTimeout)
    }
  }

  private def withCapturingAppender(
    expectedMessages: Set[String]
  )(testBody: CapturingAppender => Unit): Unit = {
    val context: LoggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val rootLogger: Logger =
      LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
    val previousLevel: Level = rootLogger.getLevel
    val appender: CapturingAppender = new CapturingAppender(expectedMessages)
    appender.setContext(context)
    appender.start()
    rootLogger.setLevel(Level.DEBUG)
    rootLogger.addAppender(appender)
    try {
      testBody(appender)
    } finally {
      rootLogger.detachAppender(appender)
      appender.stop()
      rootLogger.setLevel(previousLevel)
    }
  }

  private def assertCaptured(appender: CapturingAppender, level: Level, message: String): ILoggingEvent = {
    val matchingEvents: Seq[ILoggingEvent] = appender.events.filter { event =>
      level == event.getLevel && event.getFormattedMessage.contains(message)
    }
    assertTrue(
      matchingEvents.nonEmpty,
      s"Did not capture $level event containing '$message'."
    )
    matchingEvents.head
  }
}

object Akka_slf4j_2_13Test {
  private val TestTimeout = 10.seconds

  private val slf4jLoggingConfig: Config = ConfigFactory.parseString(
    """
      |akka.loggers = ["akka.event.slf4j.Slf4jLogger"]
      |akka.logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
      |akka.loglevel = "DEBUG"
      |akka.stdout-loglevel = "OFF"
      |akka.actor.default-dispatcher.shutdown-timeout = 10s
      |""".stripMargin
  )

  final case class LogWithMdc(correlationId: String, message: String)

  final class ComponentUsingSlf4jLogging extends SLF4JLogging {
    def writeInfo(message: String): Unit = log.info(message)
  }

  final class DiagnosticLoggingActor extends Actor with DiagnosticActorLogging {
    override def mdc(currentMessage: Any): MDC = currentMessage match {
      case LogWithMdc(correlationId, _) => Map("correlationId" -> correlationId)
      case _ => Map.empty
    }

    override def receive: Receive = {
      case LogWithMdc(_, message) => log.info(message)
    }
  }

  final class CapturingAppender(expectedMessages: Set[String]) extends AppenderBase[ILoggingEvent] {
    private val remainingMessages = ConcurrentHashMap.newKeySet[String]()
    remainingMessages.addAll(expectedMessages.asJava)

    private val latch: CountDownLatch = new CountDownLatch(expectedMessages.size)
    private val capturedEvents: CopyOnWriteArrayList[ILoggingEvent] =
      new CopyOnWriteArrayList[ILoggingEvent]()

    override protected def append(eventObject: ILoggingEvent): Unit = {
      capturedEvents.add(eventObject)
      expectedMessages.foreach { expectedMessage =>
        val isExpectedMessage: Boolean = eventObject.getFormattedMessage.contains(expectedMessage)
        if (isExpectedMessage && remainingMessages.remove(expectedMessage)) {
          latch.countDown()
        }
      }
    }

    def events: Seq[ILoggingEvent] = capturedEvents.asScala.toSeq

    def awaitExpectedMessages(): Unit = {
      assertTrue(
        latch.await(TestTimeout.toSeconds, TimeUnit.SECONDS),
        s"Timed out waiting for messages: ${remainingMessages.asScala.mkString(", ")}"
      )
    }
  }
}
