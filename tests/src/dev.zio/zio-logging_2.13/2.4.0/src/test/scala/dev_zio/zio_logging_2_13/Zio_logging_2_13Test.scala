/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_logging_2_13

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import zio.Cause
import zio.FiberId
import zio.FiberRefs
import zio.LogLevel
import zio.LogSpan
import zio.Runtime
import zio.Trace
import zio.Unsafe
import zio.ZIO
import zio.ZLogger
import zio.internal.stacktracer.Tracer
import zio.logging.ConsoleLoggerConfig
import zio.logging.FileLoggerConfig
import zio.logging.FilteredLogger
import zio.logging.LogAnnotation
import zio.logging.LogContext
import zio.logging.LogFilter
import zio.logging.LogFormat
import zio.logging.LogGroup
import zio.logging.LoggerNameExtractor
import zio.logging.loggerNameAnnotationKey
import zio.logging.makeFileLogger

class Zio_logging_2_13Test {
  private val trace: Trace = Tracer.instance.empty.asInstanceOf[Trace]

  @Test
  def formatsTextMessagesWithLoggerNameSpansAndAnnotations(): Unit = {
    val format: LogFormat =
      LogFormat.level.fixed(7) +
        LogFormat.space +
        LogFormat.bracketed(LogFormat.loggerName(LoggerNameExtractor.annotation(loggerNameAnnotationKey), "unknown")) +
        LogFormat.space +
        LogFormat.quoted(LogFormat.line) +
        LogFormat.space +
        LogFormat.spans +
        LogFormat.space +
        LogFormat.annotation("request-id")

    val output: String = render(
      format.toLogger,
      "created order",
      level = LogLevel.Info,
      spans = List(LogSpan("checkout", System.currentTimeMillis() - 5L)),
      annotations = Map(loggerNameAnnotationKey -> "orders.service", "request-id" -> "r-100", "ignored" -> "value")
    )

    assertThat(output).contains("INFO")
    assertThat(output).contains("orders.service")
    assertThat(output).contains("\"created order\"")
    assertThat(output).contains("checkout")
    assertThat(output).contains("request-id")
    assertThat(output).contains("r-100")
    assertThat(output).doesNotContain("ignored")
  }

  @Test
  def rendersJsonLoggerWithEscapedMessageAndSelectedFields(): Unit = {
    val format: LogFormat =
      LogFormat.label("message", LogFormat.line) +
        LogFormat.space +
        LogFormat.label("level", LogFormat.level) +
        LogFormat.space +
        LogFormat.label("logger", LogFormat.loggerName(LoggerNameExtractor.annotation(loggerNameAnnotationKey), "unknown"))

    val json: String = render(
      format.toJsonLogger,
      "value with \"quotes\" and\nnew-line",
      level = LogLevel.Warning,
      annotations = Map(loggerNameAnnotationKey -> "json.logger")
    )

    assertThat(json).startsWith("{").endsWith("}")
    assertThat(json).contains("\"message\":")
    assertThat(json).contains("value with \\\"quotes\\\" and\\nnew-line")
    assertThat(json).contains("\"level\":")
    assertThat(json).contains("WARN")
    assertThat(json).contains("json.logger")
  }

  @Test
  def evaluatesLogFiltersAndFilteredLoggerCombinators(): Unit = {
    val infoOrAbove: LogFilter[String] = LogFilter.logLevel(LogLevel.Info).contramap[String](identity)
    val namedDebug: LogFilter[String] = LogFilter
      .logLevelByName[String](
        LogLevel.Warning,
        "orders.service" -> LogLevel.Debug
      )

    assertThat(accepted(infoOrAbove, "info", LogLevel.Info)).isTrue
    assertThat(accepted(infoOrAbove, "debug", LogLevel.Debug)).isFalse
    assertThat(accepted(infoOrAbove.not, "debug", LogLevel.Debug)).isTrue
    assertThat(accepted(LogFilter.causeNonEmpty.contramap[String](identity), "boom", LogLevel.Error)).isFalse
    assertThat(
      accepted(
        LogFilter.causeNonEmpty.contramap[String](identity),
        "boom",
        LogLevel.Error,
        cause = Cause.fail("failed")
      )
    ).isTrue

    assertThat(
      accepted(namedDebug, "named", LogLevel.Debug, annotations = Map(loggerNameAnnotationKey -> "orders.service"))
    ).isTrue
    assertThat(
      accepted(namedDebug, "unnamed", LogLevel.Debug, annotations = Map(loggerNameAnnotationKey -> "other.service"))
    ).isFalse

    val filteredLogger: FilteredLogger[String, String] = new FilteredLogger(
      ZLogger.simple[String, String](message => s"logged:$message"),
      infoOrAbove && namedDebug
    )

    assertThat(logWith(filteredLogger, "accepted", LogLevel.Error)).isEqualTo(Some("logged:accepted"))
    assertThat(logWith(filteredLogger, "rejected", LogLevel.Debug)).isEqualTo(None)
  }

  @Test
  def extractsLoggerNamesAndBuildsDerivedGroups(): Unit = {
    val annotatedExtractor: LoggerNameExtractor = LoggerNameExtractor.annotation("component")
    val fallbackExtractor: LoggerNameExtractor = LoggerNameExtractor.make((_, _, _) => Some("fallback.component"))
    val extractor: LoggerNameExtractor = annotatedExtractor.or(fallbackExtractor)

    assertThat(extractor(trace, FiberRefs.empty, Map("component" -> "checkout.api"))).isEqualTo(Some("checkout.api"))
    assertThat(extractor(trace, FiberRefs.empty, Map.empty[String, String])).isEqualTo(Some("fallback.component"))

    val group: LogGroup[String, String] = LogGroup
      .fromLoggerNameExtractor(extractor, "missing")
      .contramap[String](identity)
      .map(name => name.toUpperCase)

    assertThat(group(trace, FiberId.None, LogLevel.Info, () => "message", Cause.empty, FiberRefs.empty, Nil, Map.empty))
      .isEqualTo("FALLBACK.COMPONENT")

    val levelAndName: LogGroup[String, String] = LogGroup
      .logLevel
      .zipWith(LogGroup.constant("audit"))((level, name) => s"$name:${level.label}")
      .contramap[String](identity)

    assertThat(levelAndName(trace, FiberId.None, LogLevel.Error, () => "message", Cause.empty, FiberRefs.empty, Nil, Map.empty))
      .isEqualTo("audit:ERROR")
  }

  @Test
  def storesRendersAndMergesTypedLogContextAnnotations(): Unit = {
    val base: LogContext = LogContext.empty
      .annotate(LogAnnotation.UserId, "alice")
      .annotate(LogAnnotation.TraceSpans, List("http", "database"))
    val overrideContext: LogContext = LogContext.empty.annotate(LogAnnotation.UserId, "bob")
    val merged: LogContext = base.merge(overrideContext)

    assertThat(base.get(LogAnnotation.UserId)).isEqualTo(Some("alice"))
    assertThat(base(LogAnnotation.UserId)).isEqualTo(Some("alice"))
    assertThat(base.get(LogAnnotation.UserId.name)).isEqualTo(Some("alice"))
    assertThat(base.asMap.get(LogAnnotation.TraceSpans.name).get).contains("http")
    assertThat(base.asMap.get(LogAnnotation.TraceSpans.name).get).contains("database")
    assertThat(merged.get(LogAnnotation.UserId)).isEqualTo(Some("bob"))
  }

  @Test
  def createsConfigObjectsAndUsesTheirFilters(): Unit = {
    val filterConfig: LogFilter.LogLevelByNameConfig = new LogFilter.LogLevelByNameConfig(
      LogLevel.Warning,
      Map("orders" -> LogLevel.Debug)
    )
    val consoleConfig: ConsoleLoggerConfig = ConsoleLoggerConfig.default.copy(
      format = LogFormat.line,
      filter = filterConfig.withMapping("payments", LogLevel.Info)
    )

    val filter: LogFilter[String] = consoleConfig.toFilter[String]

    assertThat(consoleConfig.format).isNotNull
    assertThat(consoleConfig.filter.rootLevel).isEqualTo(LogLevel.Warning)
    assertThat(accepted(filter, "debug", LogLevel.Debug, annotations = Map(loggerNameAnnotationKey -> "orders"))).isTrue
    assertThat(accepted(filter, "info", LogLevel.Info, annotations = Map(loggerNameAnnotationKey -> "payments"))).isTrue
    assertThat(accepted(filter, "debug", LogLevel.Debug, annotations = Map(loggerNameAnnotationKey -> "payments"))).isFalse
    assertThat(accepted(filter, "warning", LogLevel.Warning, annotations = Map.empty)).isTrue
  }

  @Test
  def parsesPatternDslIntoStructuredTextFormat(): Unit = {
    val pattern: LogFormat.Pattern = LogFormat.Pattern
      .parse("prefix %% %label{message}{%message} %fixed{7}{%level} %kv{request-id} %span{database} %label{failure}{%cause}")
      .fold(error => throw new AssertionError(error.toString), identity)

    val output: String = render(
      pattern.toLogFormat.toLogger,
      "loaded profile",
      level = LogLevel.Info,
      cause = Cause.fail("database unavailable"),
      spans = List(LogSpan("database", System.currentTimeMillis() - 5L)),
      annotations = Map("request-id" -> "r-200")
    )

    assertThat(output).contains("prefix %")
    assertThat(output).contains("message=loaded profile")
    assertThat(output).contains("INFO")
    assertThat(output).contains("request-id=r-200")
    assertThat(output).contains("database=")
    assertThat(output).contains("failure=")
    assertThat(output).contains("database unavailable")
  }

  @Test
  def writesFormattedMessagesWithFileLogger(): Unit = {
    val path: Path = Files.createTempFile("zio-logging-test", ".log")

    try {
      val config: FileLoggerConfig = FileLoggerConfig(
        path,
        LogFormat.text("file:") + LogFormat.line,
        new LogFilter.LogLevelByNameConfig(LogLevel.Trace, Map.empty),
        StandardCharsets.UTF_8,
        1,
        None,
        None
      )
      val logger: FilteredLogger[String, Any] = unsafeRun(makeFileLogger(config))

      logWith(logger, "first", LogLevel.Info)
      logWith(logger, "second", LogLevel.Debug)

      val content: String = Files.readString(path, StandardCharsets.UTF_8)
      assertThat(content).contains("file:first")
      assertThat(content).contains("file:second")
    } finally {
      Files.deleteIfExists(path)
    }
  }

  private def render(
      logger: ZLogger[String, String],
      message: String,
      level: LogLevel = LogLevel.Info,
      cause: Cause[Any] = Cause.empty,
      spans: List[LogSpan] = Nil,
      annotations: Map[String, String] = Map.empty
  ): String =
    logger(trace, FiberId.None, level, () => message, cause, FiberRefs.empty, spans, annotations)

  private def accepted(
      filter: LogFilter[String],
      message: String,
      level: LogLevel,
      cause: Cause[Any] = Cause.empty,
      annotations: Map[String, String] = Map.empty
  ): Boolean =
    filter(trace, FiberId.None, level, () => message, cause, FiberRefs.empty, Nil, annotations)

  private def logWith[Output](
      logger: ZLogger[String, Output],
      message: String,
      level: LogLevel,
      cause: Cause[Any] = Cause.empty,
      annotations: Map[String, String] = Map.empty
  ): Output =
    logger(trace, FiberId.None, level, () => message, cause, FiberRefs.empty, Nil, annotations)

  private def unsafeRun[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
