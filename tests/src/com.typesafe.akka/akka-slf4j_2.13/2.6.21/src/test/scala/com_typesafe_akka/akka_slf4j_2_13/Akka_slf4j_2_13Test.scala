/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_slf4j_2_13

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.slf4j.{Logger => Slf4jEventLogger}
import org.slf4j.MDC
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class Akka_slf4j_2_13Test {
  @Test
  def actorLoggingIsRoutedThroughConfiguredSlf4jLogger(): Unit = {
    withActorSystem("Slf4jEventStreamLoggingTest") { system =>
      implicit val timeout: Timeout = Timeout(RequestTimeout)
      val logger = system.actorOf(Props(new EventStreamLoggingActor), "event-stream-logger")

      val result = Await.result(logger ? EventStreamLoggingActor.LogAndAck("alpha"), RequestTimeout)

      assertThat(result).isEqualTo(EventStreamLoggingActor.Ack("event-stream", "alpha"))
    }
  }

  @Test
  def systemLoggingAdapterPublishesMessagesToConfiguredSlf4jBackend(): Unit = {
    withActorSystem("Slf4jSystemLoggingTest") { system =>
      val logger = akka.event.Logging(system, getClass)

      logger.debug("debug message from system logging adapter")
      logger.info("info message from system logging adapter")
      logger.warning("warning message from system logging adapter")
      logger.error(new RuntimeException("adapter failure"), "error message from system logging adapter")

      assertThat(logger).isNotNull
    }
  }

  @Test
  def directSlf4jDiagnosticLoggerSupportsMdcAndAllLogLevels(): Unit = {
    val logger = Slf4jEventLogger("graalvm.reachability.akka.slf4j.direct")
    val cause = new IllegalArgumentException("diagnostic failure")

    MDC.put("requestId", "request-42")
    MDC.put("component", "direct-logger")
    try {
      logger.debug("debug message for {}", "direct logger")
      logger.info("info message for {}", "direct logger")
      logger.warn("warning message for {}", "direct logger")
      logger.error("error message with cause", cause)
    } finally {
      MDC.clear()
    }

    assertThat(logger).isNotNull
  }

  @Test
  def configuredSlf4jLoggerHandlesErrorsWithoutStoppingTheActor(): Unit = {
    withActorSystem("Slf4jErrorLoggingTest") { system =>
      implicit val timeout: Timeout = Timeout(RequestTimeout)
      val logger = system.actorOf(Props(new EventStreamLoggingActor), "error-logger")

      val afterFailure = Await.result(logger ? EventStreamLoggingActor.LogFailureAndAck("charlie"), RequestTimeout)
      val afterRecovery = Await.result(logger ? EventStreamLoggingActor.LogAndAck("delta"), RequestTimeout)

      assertThat(afterFailure).isEqualTo(EventStreamLoggingActor.Ack("failure", "charlie"))
      assertThat(afterRecovery).isEqualTo(EventStreamLoggingActor.Ack("event-stream", "delta"))
    }
  }

  private def withActorSystem[T](name: String)(body: ActorSystem => T): T = {
    val system = ActorSystem(name, Slf4jAkkaConfig)
    try {
      body(system)
    } finally {
      Await.result(system.terminate(), ShutdownTimeout)
    }
  }

  private val RequestTimeout: FiniteDuration = 5.seconds
  private val ShutdownTimeout: FiniteDuration = 10.seconds

  private val Slf4jAkkaConfig = ConfigFactory.parseString(
    """
      |akka.loggers = ["akka.event.slf4j.Slf4jLogger"]
      |akka.logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
      |akka.loglevel = "DEBUG"
      |akka.stdout-loglevel = "OFF"
      |akka.logger-startup-timeout = 5s
      |""".stripMargin
  )

  private object EventStreamLoggingActor {
    final case class LogAndAck(payload: String)
    final case class LogFailureAndAck(payload: String)
    final case class Ack(source: String, payload: String)
  }

  private final class EventStreamLoggingActor extends Actor with ActorLogging {
    import EventStreamLoggingActor._

    override def receive: Receive = {
      case LogAndAck(payload) =>
        log.debug("debugging payload {}", payload)
        log.info("processed payload {}", payload)
        sender() ! Ack("event-stream", payload)
      case LogFailureAndAck(payload) =>
        log.error(new IllegalStateException("simulated failure"), s"failed payload $payload")
        sender() ! Ack("failure", payload)
    }
  }
}
