/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.log4cats_slf4j_3

import cats.effect.IO
import cats.effect.SyncIO
import cats.effect.unsafe.implicits.global
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.slf4j.helpers.MarkerIgnoringBase
import org.slf4j.helpers.MessageFormatter
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.LoggerName
import org.typelevel.log4cats.syntax.*
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*

class Log4cats_slf4j_3Test {
  @Test
  def logsEnabledLevelsAndThrowablesThroughSlf4jAdapter(): Unit = {
    val delegate: RecordingSlf4jLogger = RecordingSlf4jLogger("enabled")
    val logger = Slf4jLogger.getLoggerFromSlf4j[SyncIO](delegate)
    val cause: IllegalStateException = IllegalStateException("boom")

    assertThat(logger.isTraceEnabled.unsafeRunSync()).isTrue()
    assertThat(logger.isDebugEnabled.unsafeRunSync()).isTrue()
    assertThat(logger.isInfoEnabled.unsafeRunSync()).isTrue()
    assertThat(logger.isWarnEnabled.unsafeRunSync()).isTrue()
    assertThat(logger.isErrorEnabled.unsafeRunSync()).isTrue()

    logger.trace("trace-message").unsafeRunSync()
    logger.debug("debug-message").unsafeRunSync()
    logger.info("info-message").unsafeRunSync()
    logger.warn("warn-message").unsafeRunSync()
    logger.error("error-message").unsafeRunSync()
    logger.trace(cause)("trace-throwable").unsafeRunSync()
    logger.debug(cause)("debug-throwable").unsafeRunSync()
    logger.info(cause)("info-throwable").unsafeRunSync()
    logger.warn(cause)("warn-throwable").unsafeRunSync()
    logger.error(cause)("error-throwable").unsafeRunSync()

    assertThat(delegate.events.map(_.level).asJava)
      .containsExactly("trace", "debug", "info", "warn", "error", "trace", "debug", "info", "warn", "error")
    assertThat(delegate.events.map(_.message).asJava)
      .containsExactly(
        "trace-message",
        "debug-message",
        "info-message",
        "warn-message",
        "error-message",
        "trace-throwable",
        "debug-throwable",
        "info-throwable",
        "warn-throwable",
        "error-throwable"
      )
    assertThat(delegate.events.take(5).flatMap(_.throwable).asJava).isEmpty()
    assertThat(delegate.events.drop(5).map(_.throwable.orNull).asJava)
      .containsExactly(cause, cause, cause, cause, cause)
  }

  @Test
  def doesNotEvaluateMessagesOrLogWhenLevelsAreDisabled(): Unit = {
    val delegate: RecordingSlf4jLogger = RecordingSlf4jLogger("disabled")
    delegate.enabled = Map(
      "trace" -> false,
      "debug" -> false,
      "info" -> false,
      "warn" -> false,
      "error" -> false
    )
    val logger = Slf4jLogger.getLoggerFromSlf4j[SyncIO](delegate)
    var evaluatedMessages: Int = 0
    def message: String = {
      evaluatedMessages += 1
      "should-not-be-created"
    }
    val cause: RuntimeException = RuntimeException("disabled")

    logger.trace(message).unsafeRunSync()
    logger.debug(message).unsafeRunSync()
    logger.info(message).unsafeRunSync()
    logger.warn(message).unsafeRunSync()
    logger.error(message).unsafeRunSync()
    logger.trace(cause)(message).unsafeRunSync()
    logger.debug(cause)(message).unsafeRunSync()
    logger.info(cause)(message).unsafeRunSync()
    logger.warn(cause)(message).unsafeRunSync()
    logger.error(cause)(message).unsafeRunSync()
    logger.trace(Map("request" -> "abc"))(message).unsafeRunSync()
    logger.debug(Map("request" -> "abc"))(message).unsafeRunSync()
    logger.info(Map("request" -> "abc"))(message).unsafeRunSync()
    logger.warn(Map("request" -> "abc"))(message).unsafeRunSync()
    logger.error(Map("request" -> "abc"))(message).unsafeRunSync()
    logger.trace(Map("request" -> "abc"), cause)(message).unsafeRunSync()
    logger.debug(Map("request" -> "abc"), cause)(message).unsafeRunSync()
    logger.info(Map("request" -> "abc"), cause)(message).unsafeRunSync()
    logger.warn(Map("request" -> "abc"), cause)(message).unsafeRunSync()
    logger.error(Map("request" -> "abc"), cause)(message).unsafeRunSync()

    assertThat(evaluatedMessages).isZero()
    assertThat(delegate.events.asJava).isEmpty()
    assertThat(delegate.enabledChecks.asJava)
      .contains("trace", "debug", "info", "warn", "error")
  }

  @Test
  def interpolatorSyntaxBuildsMessagesForSlf4jLogger(): Unit = {
    val delegate: RecordingSlf4jLogger = RecordingSlf4jLogger("interpolator")
    given Logger[SyncIO] = Slf4jLogger.getLoggerFromSlf4j[SyncIO](delegate)
    val userId: Long = 42L
    val action: String = "created"

    info"user $userId was $action".unsafeRunSync()
    warn"user $userId requires follow-up".unsafeRunSync()

    assertThat(delegate.events.map(_.level).asJava).containsExactly("info", "warn")
    assertThat(delegate.events.map(_.message).asJava)
      .containsExactly("user 42 was created", "user 42 requires follow-up")
  }

  @Test
  def supportsStructuredContextAndMessageTransformationWrappers(): Unit = {
    val delegate: RecordingSlf4jLogger = RecordingSlf4jLogger("structured")
    val logger = Slf4jLogger
      .getLoggerFromSlf4j[SyncIO](delegate)
      .addContext(Map("request-id" -> "req-1"))
      .withModifiedString(message => s"transformed:$message")
    val cause: RuntimeException = RuntimeException("with-context")

    logger.info("plain").unsafeRunSync()
    logger.warn(Map("operation" -> "create"))("contextual").unsafeRunSync()
    logger.error(Map("operation" -> "delete"), cause)("contextual-error").unsafeRunSync()

    assertThat(delegate.events.map(_.message).asJava)
      .containsExactly("transformed:plain", "transformed:contextual", "transformed:contextual-error")
    assertThat(delegate.events.map(_.level).asJava).containsExactly("info", "warn", "error")
    assertThat(delegate.events.last.throwable.orNull).isSameAs(cause)

    val capturedContext: Map[String, String] = delegate.events(1).context
    if (capturedContext.nonEmpty) {
      assertThat(capturedContext.asJava)
        .containsEntry("request-id", "req-1")
        .containsEntry("operation", "create")
    }
  }

  @Test
  def factoryCreatesSlf4jBackedLoggersAndCanTransformThem(): Unit = {
    val delegate: RecordingSlf4jLogger = RecordingSlf4jLogger("factory")
    val factory: Slf4jFactory[SyncIO] = Slf4jFactory.create[SyncIO]
    val directLogger = factory
      .getLoggerFromSlf4j(delegate)
      .withModifiedString(message => s"direct:$message")
    val delayedLogger = factory.fromSlf4j(delegate).unsafeRunSync()
    val companionLogger = Slf4jFactory.fromSlf4j[SyncIO](delegate)(factory).unsafeRunSync()

    directLogger.info("one").unsafeRunSync()
    delayedLogger.warn("two").unsafeRunSync()
    companionLogger.error("three").unsafeRunSync()

    assertThat(delegate.events.map(_.message).asJava).containsExactly("direct:one", "two", "three")
    assertThat(delegate.events.map(_.level).asJava).containsExactly("info", "warn", "error")
  }

  @Test
  def slf4jFactoryCanBeUsedThroughTheGenericLoggerFactoryApi(): Unit = {
    val factory: LoggerFactory[SyncIO] = Slf4jFactory.create[SyncIO]
    val logger = factory.fromName("org_typelevel.log4cats_slf4j_3.generic-factory").unsafeRunSync()

    assertThat(logger).isNotNull()
    logger.isInfoEnabled.unsafeRunSync()
    logger.info("message through generic factory").unsafeRunSync()
  }

  @Test
  def publicConstructorsFromNamesClassesLoggerNamesAndBlockingSlf4jAreUsable(): Unit = {
    given LoggerName = LoggerName("org_typelevel.log4cats_slf4j_3.named")

    val namedLogger = Slf4jLogger.getLogger[SyncIO]
    val createdLogger = Slf4jLogger.create[SyncIO].unsafeRunSync()
    val fromNameLogger = Slf4jLogger.fromName[SyncIO]("org_typelevel.log4cats_slf4j_3.from-name").unsafeRunSync()
    val fromClassLogger = Slf4jLogger.fromClass[SyncIO](classOf[Log4cats_slf4j_3Test]).unsafeRunSync()
    val getFromNameLogger = Slf4jLogger.getLoggerFromName[SyncIO]("org_typelevel.log4cats_slf4j_3.get-from-name")
    val getFromClassLogger = Slf4jLogger.getLoggerFromClass[SyncIO](classOf[Log4cats_slf4j_3Test])
    val blockingDelegate: RecordingSlf4jLogger = RecordingSlf4jLogger("blocking")
    val blockingLogger = Slf4jLogger.fromBlockingSlf4j[IO](blockingDelegate).unsafeRunSync()

    namedLogger.debug("named").unsafeRunSync()
    createdLogger.info("created").unsafeRunSync()
    fromNameLogger.warn("from name").unsafeRunSync()
    fromClassLogger.error("from class").unsafeRunSync()
    getFromNameLogger.trace("get from name").unsafeRunSync()
    getFromClassLogger.debug("get from class").unsafeRunSync()
    blockingLogger.info("blocking").unsafeRunSync()

    assertThat(blockingDelegate.events.map(_.message).asJava).containsExactly("blocking")
  }
}

private final case class LogEvent(
    level: String,
    message: String,
    throwable: Option[Throwable],
    context: Map[String, String]
)

private final class RecordingSlf4jLogger private (loggerName: String) extends MarkerIgnoringBase {
  override def getName: String = loggerName

  var enabled: Map[String, Boolean] = Map(
    "trace" -> true,
    "debug" -> true,
    "info" -> true,
    "warn" -> true,
    "error" -> true
  )
  val events: ArrayBuffer[LogEvent] = ArrayBuffer.empty
  val enabledChecks: ArrayBuffer[String] = ArrayBuffer.empty

  override def isTraceEnabled: Boolean = isEnabled("trace")
  override def isDebugEnabled: Boolean = isEnabled("debug")
  override def isInfoEnabled: Boolean = isEnabled("info")
  override def isWarnEnabled: Boolean = isEnabled("warn")
  override def isErrorEnabled: Boolean = isEnabled("error")

  override def trace(message: String): Unit = record("trace", message, None)
  override def trace(format: String, arg: AnyRef): Unit = record("trace", formatMessage(format, arg), None)
  override def trace(format: String, first: AnyRef, second: AnyRef): Unit = record("trace", formatMessage(format, first, second), None)
  override def trace(format: String, arguments: AnyRef*): Unit = record("trace", formatMessage(format, arguments*), None)
  override def trace(message: String, throwable: Throwable): Unit = record("trace", message, Some(throwable))

  override def debug(message: String): Unit = record("debug", message, None)
  override def debug(format: String, arg: AnyRef): Unit = record("debug", formatMessage(format, arg), None)
  override def debug(format: String, first: AnyRef, second: AnyRef): Unit = record("debug", formatMessage(format, first, second), None)
  override def debug(format: String, arguments: AnyRef*): Unit = record("debug", formatMessage(format, arguments*), None)
  override def debug(message: String, throwable: Throwable): Unit = record("debug", message, Some(throwable))

  override def info(message: String): Unit = record("info", message, None)
  override def info(format: String, arg: AnyRef): Unit = record("info", formatMessage(format, arg), None)
  override def info(format: String, first: AnyRef, second: AnyRef): Unit = record("info", formatMessage(format, first, second), None)
  override def info(format: String, arguments: AnyRef*): Unit = record("info", formatMessage(format, arguments*), None)
  override def info(message: String, throwable: Throwable): Unit = record("info", message, Some(throwable))

  override def warn(message: String): Unit = record("warn", message, None)
  override def warn(format: String, arg: AnyRef): Unit = record("warn", formatMessage(format, arg), None)
  override def warn(format: String, arguments: AnyRef*): Unit = record("warn", formatMessage(format, arguments*), None)
  override def warn(format: String, first: AnyRef, second: AnyRef): Unit = record("warn", formatMessage(format, first, second), None)
  override def warn(message: String, throwable: Throwable): Unit = record("warn", message, Some(throwable))

  override def error(message: String): Unit = record("error", message, None)
  override def error(format: String, arg: AnyRef): Unit = record("error", formatMessage(format, arg), None)
  override def error(format: String, first: AnyRef, second: AnyRef): Unit = record("error", formatMessage(format, first, second), None)
  override def error(format: String, arguments: AnyRef*): Unit = record("error", formatMessage(format, arguments*), None)
  override def error(message: String, throwable: Throwable): Unit = record("error", message, Some(throwable))

  private def isEnabled(level: String): Boolean = {
    enabledChecks += level
    enabled(level)
  }

  private def record(level: String, message: String, throwable: Option[Throwable]): Unit = {
    events += LogEvent(level, message, throwable, currentContext)
  }

  private def currentContext: Map[String, String] = {
    val context = MDC.getCopyOfContextMap
    if (context == null) Map.empty else context.asScala.toMap
  }

  private def formatMessage(format: String, arguments: AnyRef*): String =
    MessageFormatter.arrayFormat(format, arguments.toArray).getMessage
}

private object RecordingSlf4jLogger {
  def apply(name: String): RecordingSlf4jLogger = new RecordingSlf4jLogger(name)
}
