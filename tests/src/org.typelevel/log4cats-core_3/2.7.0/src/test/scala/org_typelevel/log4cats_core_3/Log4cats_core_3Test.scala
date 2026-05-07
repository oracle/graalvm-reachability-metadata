/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.log4cats_core_3

import cats.*
import cats.data.*
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.log4cats.*
import org.typelevel.log4cats.extras.*
import org.typelevel.log4cats.noop.*
import org.typelevel.log4cats.syntax.*

import java.util.UUID
import scala.collection.mutable.ListBuffer

class Log4cats_core_3Test {
  import Log4cats_core_3Test.*

  @Test
  def noOpLoggersExposeLazyAndStrictEvaluationModes(): Unit = {
    var evaluatedMessages: Int = 0
    val lazyLogger: SelfAwareStructuredLogger[Id] = NoOpLogger[Id]

    lazyLogger.info {
      evaluatedMessages += 1
      "not evaluated"
    }
    lazyLogger.warn(Map("request" -> "a")) {
      evaluatedMessages += 1
      "not evaluated either"
    }

    assertEquals(0, evaluatedMessages)
    assertFalse(lazyLogger.isTraceEnabled)
    assertFalse(lazyLogger.isDebugEnabled)
    assertFalse(lazyLogger.isInfoEnabled)
    assertFalse(lazyLogger.isWarnEnabled)
    assertFalse(lazyLogger.isErrorEnabled)

    val strictLogger: SelfAwareStructuredLogger[Id] = NoOpLogger.strictEvalArgs[Id]
    strictLogger.debug {
      evaluatedMessages += 1
      "evaluated"
    }
    strictLogger.error(Map("request" -> "b")) {
      evaluatedMessages += 1
      "also evaluated"
    }

    assertEquals(2, evaluatedMessages)
    assertTrue(strictLogger.isTraceEnabled)
    assertTrue(strictLogger.isDebugEnabled)
    assertTrue(strictLogger.isInfoEnabled)
    assertTrue(strictLogger.isWarnEnabled)
    assertTrue(strictLogger.isErrorEnabled)

    val factoryLogger: SelfAwareStructuredLogger[Id] =
      NoOpFactory.strictEvalArgs[Id].getLoggerFromName("factory.logger")
    factoryLogger.info {
      evaluatedMessages += 1
      "factory evaluated"
    }

    assertEquals(3, evaluatedMessages)
  }

  @Test
  def writerLoggerCapturesEnabledPlainAndThrowableMessages(): Unit = {
    val failure: IllegalArgumentException = IllegalArgumentException("bad input")
    val logger: SelfAwareLogger[LogMessageWriter] = WriterLogger[List](
      traceEnabled = false,
      debugEnabled = true,
      infoEnabled = true,
      warnEnabled = false,
      errorEnabled = true
    )

    val program: Writer[List[LogMessage], String] = for {
      traceEnabled <- logger.isTraceEnabled
      debugEnabled <- logger.isDebugEnabled
      _ <- logger.trace("discarded")
      _ <- logger.debug(failure)("debugged")
      _ <- logger.info("service started")
      _ <- logger.warn("discarded warning")
      _ <- logger.error("service failed")
    } yield s"trace=$traceEnabled debug=$debugEnabled"

    val (messages: List[LogMessage], result: String) = program.run

    assertEquals("trace=false debug=true", result)
    assertEquals(3, messages.size)
    assertEquals(LogLevel.Debug, messages(0).level)
    assertEquals(Some(failure), messages(0).t)
    assertEquals("debugged", messages(0).message)
    assertEquals(LogMessage(LogLevel.Info, None, "service started"), messages(1))
    assertEquals(LogMessage(LogLevel.Error, None, "service failed"), messages(2))
  }

  @Test
  def writerStructuredLoggerCombinesContextFilteringAndMessageMapping(): Unit = {
    val failure: RuntimeException = RuntimeException("structured failure")
    val baseLogger: SelfAwareStructuredLogger[StructuredLogMessageWriter] =
      WriterStructuredLogger[Vector](warnEnabled = false)
    val logger: SelfAwareStructuredLogger[StructuredLogMessageWriter] =
      baseLogger
        .addContext(Map("request" -> "r-1", "shared" -> "base"))
        .withModifiedString(message => s"[$message]")

    val program: Writer[Vector[StructuredLogMessage], Unit] = for {
      _ <- logger.info("plain")
      _ <- logger.debug(Map("shared" -> "call", "operation" -> "lookup"))("with context")
      _ <- logger.warn(Map("discarded" -> "true"))("disabled")
      _ <- logger.error(Map("operation" -> "save"), failure)("failed")
    } yield ()

    val (messages: Vector[StructuredLogMessage], _: Unit) = program.run

    assertEquals(3, messages.size)
    assertEquals(LogLevel.Info, messages(0).level)
    assertEquals(Map("request" -> "r-1", "shared" -> "base"), messages(0).context)
    assertEquals("[plain]", messages(0).message)

    assertEquals(LogLevel.Debug, messages(1).level)
    assertEquals(Map("request" -> "r-1", "shared" -> "call", "operation" -> "lookup"), messages(1).context)
    assertEquals("[with context]", messages(1).message)

    assertEquals(LogLevel.Error, messages(2).level)
    assertEquals(Map("request" -> "r-1", "shared" -> "base", "operation" -> "save"), messages(2).context)
    assertEquals(Some(failure), messages(2).throwableOpt)
    assertEquals("[failed]", messages(2).message)
  }

  @Test
  def structuredLoggerWithModifiedContextRewritesDefaultAndCallContexts(): Unit = {
    val failure: IllegalStateException = IllegalStateException("context failure")
    val sink: RecordingSelfAwareStructuredLogger = RecordingSelfAwareStructuredLogger("modified-context")
    val logger: StructuredLogger[Id] =
      StructuredLogger.withModifiedContext[Id](sink) { context =>
        (context - "secret") ++ Map(
          "component" -> context.getOrElse("component", "payments"),
          "context_keys" -> context.keys.toList.sorted.mkString(",")
        )
      }

    logger.info("plain default context")
    logger.debug(Map("request" -> "r-4", "secret" -> "redacted"))("lookup")
    logger.error(Map("component" -> "orders", "request" -> "r-5"), failure)("failed")

    assertEquals(3, sink.entries.size)
    assertEquals(
      RecordedLog(
        "modified-context",
        LogLevel.Info,
        Map("component" -> "payments", "context_keys" -> ""),
        None,
        "plain default context"
      ),
      sink.entries.head
    )
    assertEquals(
      RecordedLog(
        "modified-context",
        LogLevel.Debug,
        Map("request" -> "r-4", "component" -> "payments", "context_keys" -> "request,secret"),
        None,
        "lookup"
      ),
      sink.entries(1)
    )
    assertEquals(LogLevel.Error, sink.entries(2).level)
    assertEquals(
      Map("component" -> "orders", "request" -> "r-5", "context_keys" -> "component,request"),
      sink.entries(2).context
    )
    assertSame(failure, sink.entries(2).throwable.get)
    assertEquals("failed", sink.entries(2).message)
  }

  @Test
  def logMessagesReplayIntoConcreteLoggers(): Unit = {
    val failure: IllegalStateException = IllegalStateException("boom")
    val plainSink: RecordingSelfAwareStructuredLogger = RecordingSelfAwareStructuredLogger("plain")
    val structuredSink: RecordingSelfAwareStructuredLogger = RecordingSelfAwareStructuredLogger("structured")

    val plainProgram: Writer[List[LogMessage], Int] = for {
      _ <- WriterLogger[List]().trace("trace event")
      _ <- WriterLogger[List]().warn(failure)("warn event")
    } yield 42
    val plainResult: Int = WriterLogger.run[Id, List](plainSink).apply(plainProgram)

    val structuredProgram: WriterT[Id, Vector[StructuredLogMessage], String] = for {
      _ <- WriterTStructuredLogger[Id, Vector]().info(Map("request" -> "r-2"))("structured info")
      _ <- WriterTStructuredLogger[Id, Vector]().error(Map("request" -> "r-2"), failure)("structured error")
    } yield "done"
    val structuredResult: String = WriterTStructuredLogger.run[Id, Vector](structuredSink).apply(structuredProgram)

    assertEquals(42, plainResult)
    assertEquals("done", structuredResult)
    assertEquals(
      List(
        RecordedLog("plain", LogLevel.Trace, Map.empty, None, "trace event"),
        RecordedLog("plain", LogLevel.Warn, Map.empty, Some(failure), "warn event")
      ),
      plainSink.entries
    )
    assertEquals(2, structuredSink.entries.size)
    assertEquals(RecordedLog("structured", LogLevel.Info, Map("request" -> "r-2"), None, "structured info"), structuredSink.entries.head)
    assertEquals(LogLevel.Error, structuredSink.entries(1).level)
    assertEquals(Map("request" -> "r-2"), structuredSink.entries(1).context)
    assertSame(failure, structuredSink.entries(1).throwable.get)
    assertEquals("structured error", structuredSink.entries(1).message)
  }

  @Test
  def loggerFactoryCreatesNamedContextualAndMappedLoggers(): Unit = {
    val factory: RecordingLoggerFactory = RecordingLoggerFactory()
    given LoggerFactory[Id] = factory
    given LoggerName = LoggerName("implicit.logger")

    val implicitLogger: SelfAwareStructuredLogger[Id] = LoggerFactory.getLogger[Id]
    implicitLogger.info("implicit message")

    val contextualFactory: LoggerFactory[Id] = factory
      .addContext("component" -> Show.Shown("billing"), "attempt" -> Show.Shown("2"))
      .withModifiedString(_.toUpperCase)
    val namedLogger: SelfAwareStructuredLogger[Id] = contextualFactory.getLoggerFromName("named.logger")
    namedLogger.warn(Map("attempt" -> "3"))("retry")

    val classLogger: SelfAwareStructuredLogger[Id] = contextualFactory.getLoggerFromClass(classOf[Log4cats_core_3Test])
    classLogger.error("class logger")

    val optionFactory: LoggerFactory[Option] = factory.mapK(idToOption)
    val optionLogger: Option[SelfAwareStructuredLogger[Option]] = optionFactory.fromName("option.logger")
    assertTrue(optionLogger.isDefined)
    assertEquals(Some(()), optionLogger.get.debug("mapped option"))

    assertEquals(
      List("implicit.logger", "named.logger", classOf[Log4cats_core_3Test].getName, "option.logger"),
      factory.requestedNames
    )
    assertEquals(4, factory.entries.size)
    assertEquals(RecordedLog("implicit.logger", LogLevel.Info, Map.empty, None, "implicit message"), factory.entries.head)
    assertEquals(
      RecordedLog("named.logger", LogLevel.Warn, Map("component" -> "billing", "attempt" -> "3"), None, "RETRY"),
      factory.entries(1)
    )
    assertEquals(
      RecordedLog(classOf[Log4cats_core_3Test].getName, LogLevel.Error, Map("component" -> "billing", "attempt" -> "2"), None, "CLASS LOGGER"),
      factory.entries(2)
    )
    assertEquals(RecordedLog("option.logger", LogLevel.Debug, Map.empty, None, "mapped option"), factory.entries(3))
  }

  @Test
  def loggerSyntaxAndTransformerInstancesDelegateToUnderlyingLogger(): Unit = {
    val sink: RecordingSelfAwareStructuredLogger = RecordingSelfAwareStructuredLogger("syntax")
    given Logger[Id] = sink

    val userId: Int = 7
    info"user-$userId logged in"
    warn"quota ${90 + 5}%"

    val optionLogger: Logger[OptionId] = Logger.optionTLogger[Id]
    val optionResult: OptionT[Id, Unit] = optionLogger.error("option message")

    val eitherLogger: Logger[EitherId] = Logger.eitherTLogger[Id, String]
    val eitherResult: EitherT[Id, String, Unit] = eitherLogger.debug("either message")

    val kleisliLogger: Logger[KleisliId] = Logger.kleisliLogger[Id, String]
    val kleisliResult: Unit = kleisliLogger.trace("kleisli message").run("environment")

    assertEquals(Some(()), optionResult.value)
    assertEquals(Right(()), eitherResult.value)
    assertEquals((), kleisliResult)
    assertEquals(
      List(
        RecordedLog("syntax", LogLevel.Info, Map.empty, None, "user-7 logged in"),
        RecordedLog("syntax", LogLevel.Warn, Map.empty, None, "quota 95%"),
        RecordedLog("syntax", LogLevel.Error, Map.empty, None, "option message"),
        RecordedLog("syntax", LogLevel.Debug, Map.empty, None, "either message"),
        RecordedLog("syntax", LogLevel.Trace, Map.empty, None, "kleisli message")
      ),
      sink.entries
    )
  }

  @Test
  def pagedLoggerSplitsLongMessagesAndAddsPagingContext(): Unit = {
    given UUIDGen[Id] with {
      override def randomUUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    }

    val sink: RecordingSelfAwareStructuredLogger = RecordingSelfAwareStructuredLogger("paged")
    val pagedLogger: SelfAwareStructuredLogger[Id] =
      PagingSelfAwareStructuredLogger.paged[Id](pageSizeK = 1, maxPageNeeded = 2)(sink)
    val message: String = "a" * 1300

    pagedLogger.info(Map("request" -> "r-3"))(message)

    assertEquals(2, sink.entries.size)
    sink.entries.zipWithIndex.foreach { case (entry: RecordedLog, index: Int) =>
      assertEquals(LogLevel.Info, entry.level)
      assertEquals("r-3", entry.context("request"))
      assertEquals("12345678-1234-1234-1234-123456789abc", entry.context("log_split_id"))
      assertEquals("1 Kib", entry.context("page_size"))
      assertEquals("1300 Byte", entry.context("log_size"))
      assertTrue(entry.message.contains(s"Page ${index + 1}/2 12345678"))
      assertTrue(entry.message.contains("log_split_id=12345678-1234-1234-1234-123456789abc"))
    }

    val thrown: IllegalArgumentException = assertThrows(
      classOf[IllegalArgumentException],
      () => PagingSelfAwareStructuredLogger.paged[Id](pageSizeK = 0, maxPageNeeded = 1)(sink)
    )
    assertTrue(thrown.getMessage.contains("must be positive"))
  }

  @Test
  def logLevelParsingShowingAndOrderingCoverAllLevels(): Unit = {
    assertEquals(Some(LogLevel.Error), LogLevel.fromString("error"))
    assertEquals(Some(LogLevel.Warn), LogLevel.fromString("LogLevel.Warn"))
    assertEquals(Some(LogLevel.Info), LogLevel.fromString("INFO"))
    assertEquals(Some(LogLevel.Debug), LogLevel.fromString("debug"))
    assertEquals(Some(LogLevel.Trace), LogLevel.fromString("loglevel.trace"))
    assertEquals(None, LogLevel.fromString("verbose"))

    assertEquals("LogLevel.Error", LogLevel.logLevelShow.show(LogLevel.Error))
    assertTrue(LogLevel.logLevelOrder.gt(LogLevel.Error, LogLevel.Warn))
    assertTrue(LogLevel.logLevelOrder.gt(LogLevel.Warn, LogLevel.Info))
    assertTrue(LogLevel.logLevelOrder.gt(LogLevel.Info, LogLevel.Debug))
    assertTrue(LogLevel.logLevelOrder.gt(LogLevel.Debug, LogLevel.Trace))
  }
}

object Log4cats_core_3Test {
  private final case class RecordedLog(
      loggerName: String,
      level: LogLevel,
      context: Map[String, String],
      throwable: Option[Throwable],
      message: String
  )

  private final class RecordingSelfAwareStructuredLogger(
      loggerName: String,
      sink: ListBuffer[RecordedLog],
      enabled: Map[LogLevel, Boolean]
  ) extends SelfAwareStructuredLogger[Id] {
    def entries: List[RecordedLog] = sink.toList

    override def isTraceEnabled: Boolean = enabled(LogLevel.Trace)
    override def isDebugEnabled: Boolean = enabled(LogLevel.Debug)
    override def isInfoEnabled: Boolean = enabled(LogLevel.Info)
    override def isWarnEnabled: Boolean = enabled(LogLevel.Warn)
    override def isErrorEnabled: Boolean = enabled(LogLevel.Error)

    override def trace(message: => String): Unit = append(LogLevel.Trace, Map.empty, None, message)
    override def trace(t: Throwable)(message: => String): Unit = append(LogLevel.Trace, Map.empty, Some(t), message)
    override def trace(ctx: Map[String, String])(message: => String): Unit = append(LogLevel.Trace, ctx, None, message)
    override def trace(ctx: Map[String, String], t: Throwable)(message: => String): Unit = append(LogLevel.Trace, ctx, Some(t), message)

    override def debug(message: => String): Unit = append(LogLevel.Debug, Map.empty, None, message)
    override def debug(t: Throwable)(message: => String): Unit = append(LogLevel.Debug, Map.empty, Some(t), message)
    override def debug(ctx: Map[String, String])(message: => String): Unit = append(LogLevel.Debug, ctx, None, message)
    override def debug(ctx: Map[String, String], t: Throwable)(message: => String): Unit = append(LogLevel.Debug, ctx, Some(t), message)

    override def info(message: => String): Unit = append(LogLevel.Info, Map.empty, None, message)
    override def info(t: Throwable)(message: => String): Unit = append(LogLevel.Info, Map.empty, Some(t), message)
    override def info(ctx: Map[String, String])(message: => String): Unit = append(LogLevel.Info, ctx, None, message)
    override def info(ctx: Map[String, String], t: Throwable)(message: => String): Unit = append(LogLevel.Info, ctx, Some(t), message)

    override def warn(message: => String): Unit = append(LogLevel.Warn, Map.empty, None, message)
    override def warn(t: Throwable)(message: => String): Unit = append(LogLevel.Warn, Map.empty, Some(t), message)
    override def warn(ctx: Map[String, String])(message: => String): Unit = append(LogLevel.Warn, ctx, None, message)
    override def warn(ctx: Map[String, String], t: Throwable)(message: => String): Unit = append(LogLevel.Warn, ctx, Some(t), message)

    override def error(message: => String): Unit = append(LogLevel.Error, Map.empty, None, message)
    override def error(t: Throwable)(message: => String): Unit = append(LogLevel.Error, Map.empty, Some(t), message)
    override def error(ctx: Map[String, String])(message: => String): Unit = append(LogLevel.Error, ctx, None, message)
    override def error(ctx: Map[String, String], t: Throwable)(message: => String): Unit = append(LogLevel.Error, ctx, Some(t), message)

    private def append(
        level: LogLevel,
        context: Map[String, String],
        throwable: Option[Throwable],
        message: => String
    ): Unit =
      sink += RecordedLog(loggerName, level, context, throwable, message)
  }

  private object RecordingSelfAwareStructuredLogger {
    def apply(loggerName: String): RecordingSelfAwareStructuredLogger =
      RecordingSelfAwareStructuredLogger(loggerName, ListBuffer.empty, allLevelsEnabled)

    def apply(
        loggerName: String,
        sink: ListBuffer[RecordedLog],
        enabled: Map[LogLevel, Boolean] = allLevelsEnabled
    ): RecordingSelfAwareStructuredLogger =
      new RecordingSelfAwareStructuredLogger(loggerName, sink, enabled)
  }

  private final class RecordingLoggerFactory(
      sink: ListBuffer[RecordedLog],
      requested: ListBuffer[String]
  ) extends LoggerFactory[Id] {
    def entries: List[RecordedLog] = sink.toList
    def requestedNames: List[String] = requested.toList

    override def getLoggerFromName(name: String): SelfAwareStructuredLogger[Id] = {
      requested += name
      RecordingSelfAwareStructuredLogger(name, sink)
    }

    override def fromName(name: String): SelfAwareStructuredLogger[Id] = getLoggerFromName(name)
  }

  private object RecordingLoggerFactory {
    def apply(): RecordingLoggerFactory = RecordingLoggerFactory(ListBuffer.empty, ListBuffer.empty)

    def apply(sink: ListBuffer[RecordedLog], requested: ListBuffer[String]): RecordingLoggerFactory =
      new RecordingLoggerFactory(sink, requested)
  }

  private type LogMessageWriter[A] = Writer[List[LogMessage], A]
  private type StructuredLogMessageWriter[A] = Writer[Vector[StructuredLogMessage], A]
  private type OptionId[A] = OptionT[Id, A]
  private type EitherId[A] = EitherT[Id, String, A]
  private type KleisliId[A] = Kleisli[Id, String, A]

  private val allLevelsEnabled: Map[LogLevel, Boolean] = Map(
    LogLevel.Trace -> true,
    LogLevel.Debug -> true,
    LogLevel.Info -> true,
    LogLevel.Warn -> true,
    LogLevel.Error -> true
  )

  private val idToOption: Id ~> Option = new (Id ~> Option) {
    override def apply[A](value: Id[A]): Option[A] = Some(value)
  }
}
