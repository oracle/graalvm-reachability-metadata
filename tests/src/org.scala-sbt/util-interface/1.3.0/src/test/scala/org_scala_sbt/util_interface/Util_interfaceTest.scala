/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_sbt.util_interface

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xsbti.Logger
import xsbti.Position
import xsbti.Problem
import xsbti.Severity
import xsbti.T2

import java.io.File
import java.lang.{Integer => JInteger}
import java.util.ArrayList
import java.util.Optional
import java.util.function.Supplier

final class Util_interfaceTest {
  @Test
  def severityEnumContainsAllDiagnosticLevelsInDeclarationOrder(): Unit = {
    assertThat(Severity.values()).containsExactly(Severity.Info, Severity.Warn, Severity.Error)
    assertThat(Severity.Info.name()).isEqualTo("Info")
    assertThat(Severity.Warn.name()).isEqualTo("Warn")
    assertThat(Severity.Error.name()).isEqualTo("Error")
    assertThat(Severity.Info.ordinal()).isEqualTo(0)
    assertThat(Severity.Warn.ordinal()).isEqualTo(1)
    assertThat(Severity.Error.ordinal()).isEqualTo(2)
    assertThat(Severity.valueOf("Info")).isSameAs(Severity.Info)
    assertThat(Severity.valueOf("Warn")).isSameAs(Severity.Warn)
    assertThat(Severity.valueOf("Error")).isSameAs(Severity.Error)
  }

  @Test
  def positionExposesRequiredLocationFieldsAndEmptyRangeDefaults(): Unit = {
    val sourceFile = new File("src/main/scala/example/Calculator.scala")
    val position = new StaticPosition(
      lineNumber = presentInt(12),
      text = "  value + missingSymbol",
      characterOffset = presentInt(148),
      pointerColumn = presentInt(10),
      pointerPadding = Optional.of("  "),
      path = Optional.of(sourceFile.getPath),
      file = Optional.of(sourceFile)
    )

    assertThat(position.line()).contains(JInteger.valueOf(12))
    assertThat(position.lineContent()).isEqualTo("  value + missingSymbol")
    assertThat(position.offset()).contains(JInteger.valueOf(148))
    assertThat(position.pointer()).contains(JInteger.valueOf(10))
    assertThat(position.pointerSpace()).contains("  ")
    assertThat(position.sourcePath()).contains("src/main/scala/example/Calculator.scala")
    assertThat(position.sourceFile()).contains(sourceFile)

    assertThat(position.startOffset()).isEmpty()
    assertThat(position.endOffset()).isEmpty()
    assertThat(position.startLine()).isEmpty()
    assertThat(position.startColumn()).isEmpty()
    assertThat(position.endLine()).isEmpty()
    assertThat(position.endColumn()).isEmpty()
  }

  @Test
  def positionImplementationsCanProvideExtendedRangeInformation(): Unit = {
    val position = new RangePosition(
      lineNumber = presentInt(8),
      text = "import example.Missing",
      characterOffset = presentInt(21),
      pointerColumn = presentInt(15),
      pointerPadding = Optional.of("              "),
      path = Optional.empty[String](),
      file = Optional.empty[File](),
      start = presentInt(21),
      end = presentInt(28),
      startLineNumber = presentInt(8),
      startColumnNumber = presentInt(15),
      endLineNumber = presentInt(8),
      endColumnNumber = presentInt(22)
    )

    assertThat(position.line()).contains(JInteger.valueOf(8))
    assertThat(position.sourcePath()).isEmpty()
    assertThat(position.sourceFile()).isEmpty()
    assertThat(position.startOffset()).contains(JInteger.valueOf(21))
    assertThat(position.endOffset()).contains(JInteger.valueOf(28))
    assertThat(position.startLine()).contains(JInteger.valueOf(8))
    assertThat(position.startColumn()).contains(JInteger.valueOf(15))
    assertThat(position.endLine()).contains(JInteger.valueOf(8))
    assertThat(position.endColumn()).contains(JInteger.valueOf(22))
  }

  @Test
  def problemUsesSeverityPositionAndEmptyRenderedDefault(): Unit = {
    val position = new StaticPosition(
      lineNumber = presentInt(4),
      text = "val answer: String = 42",
      characterOffset = presentInt(19),
      pointerColumn = presentInt(20),
      pointerPadding = Optional.of("                   "),
      path = Optional.of("src/test/scala/example/AnswerSpec.scala"),
      file = Optional.empty[File]()
    )
    val problem = new DiagnosticProblem(
      diagnosticCategory = "type-mismatch",
      diagnosticSeverity = Severity.Error,
      diagnosticMessage = "found Int, required String",
      diagnosticPosition = position
    )

    assertThat(problem.category()).isEqualTo("type-mismatch")
    assertThat(problem.severity()).isSameAs(Severity.Error)
    assertThat(problem.message()).isEqualTo("found Int, required String")
    assertThat(problem.position()).isSameAs(position)
    assertThat(problem.rendered()).isEmpty()
  }

  @Test
  def problemImplementationsCanSupplyRenderedDiagnostics(): Unit = {
    val position = new StaticPosition(
      lineNumber = Optional.empty[JInteger](),
      text = "",
      characterOffset = Optional.empty[JInteger](),
      pointerColumn = Optional.empty[JInteger](),
      pointerPadding = Optional.empty[String](),
      path = Optional.empty[String](),
      file = Optional.empty[File]()
    )
    val renderedDiagnostic =
      """warning: unused import
        |import scala.collection.mutable
        |       ^
        |""".stripMargin
    val problem = new RenderedDiagnosticProblem(
      diagnosticCategory = "unused-import",
      diagnosticSeverity = Severity.Warn,
      diagnosticMessage = "Unused import",
      diagnosticPosition = position,
      renderedDiagnostic = renderedDiagnostic
    )

    assertThat(problem.category()).isEqualTo("unused-import")
    assertThat(problem.severity()).isSameAs(Severity.Warn)
    assertThat(problem.position().line()).isEmpty()
    assertThat(problem.rendered()).contains(renderedDiagnostic)
  }

  @Test
  def renderedProblemDiagnosticsCanBeDisplayedWithFieldBasedFallback(): Unit = {
    val position = new StaticPosition(
      lineNumber = presentInt(3),
      text = "libraryDependencies +=",
      characterOffset = presentInt(57),
      pointerColumn = presentInt(21),
      pointerPadding = Optional.of("                    "),
      path = Optional.of("src/main/scala/example/Build.scala"),
      file = Optional.empty[File]()
    )
    val fallbackProblem = new DiagnosticProblem(
      diagnosticCategory = "parse-error",
      diagnosticSeverity = Severity.Error,
      diagnosticMessage = "expected expression",
      diagnosticPosition = position
    )
    val renderedDiagnostic =
      """custom sbt diagnostic
        |with compiler-provided context""".stripMargin
    val renderedProblem = new RenderedDiagnosticProblem(
      diagnosticCategory = "parse-error",
      diagnosticSeverity = Severity.Error,
      diagnosticMessage = "expected expression",
      diagnosticPosition = position,
      renderedDiagnostic = renderedDiagnostic
    )

    assertThat(displayDiagnostic(renderedProblem)).isEqualTo(renderedDiagnostic)
    assertThat(displayDiagnostic(fallbackProblem)).isEqualTo(
      """Error parse-error: expected expression
        |src/main/scala/example/Build.scala:3
        |libraryDependencies +=
        |                    ^""".stripMargin
    )
  }

  @Test
  def loggerAcceptsLazyMessageAndThrowableSuppliers(): Unit = {
    val logger = new DeferredLogger()
    var evaluatedMessages = 0
    var evaluatedTraces = 0
    val traceFailure = new IllegalStateException("compilation failed")

    logger.info(countingMessageSupplier("compiler started", () => evaluatedMessages += 1))
    logger.debug(countingMessageSupplier("classpath contains 3 entries", () => evaluatedMessages += 1))
    logger.warn(countingMessageSupplier("incremental cache is stale", () => evaluatedMessages += 1))
    logger.error(countingMessageSupplier("compile failed", () => evaluatedMessages += 1))
    logger.trace(new Supplier[Throwable] {
      override def get(): Throwable = {
        evaluatedTraces += 1
        traceFailure
      }
    })

    assertThat(evaluatedMessages).isZero()
    assertThat(evaluatedTraces).isZero()
    assertThat(logger.pendingMessages).hasSize(4)
    assertThat(logger.pendingTraces).hasSize(1)
    assertThat(logger.messages).isEmpty()
    assertThat(logger.traces).isEmpty()

    logger.flush()

    assertThat(evaluatedMessages).isEqualTo(4)
    assertThat(evaluatedTraces).isEqualTo(1)
    assertThat(logger.messages).containsExactly(
      "info:compiler started",
      "debug:classpath contains 3 entries",
      "warn:incremental cache is stale",
      "error:compile failed"
    )
    assertThat(logger.traces).containsExactly(traceFailure)
  }

  @Test
  def loggerImplementationsCanFilterDisabledLevelsWithoutEvaluatingMessages(): Unit = {
    val logger = new FilteringLogger(enabledMessageLevels = Set("warn", "error"), tracesEnabled = false)
    var debugEvaluated = 0
    var infoEvaluated = 0
    var warnEvaluated = 0
    var errorEvaluated = 0
    var traceEvaluated = 0
    val suppressedTrace = new IllegalArgumentException("suppressed trace")

    logger.debug(countingMessageSupplier("debug details", () => debugEvaluated += 1))
    logger.info(countingMessageSupplier("compile started", () => infoEvaluated += 1))
    logger.warn(countingMessageSupplier("cache is stale", () => warnEvaluated += 1))
    logger.error(countingMessageSupplier("compile failed", () => errorEvaluated += 1))
    logger.trace(new Supplier[Throwable] {
      override def get(): Throwable = {
        traceEvaluated += 1
        suppressedTrace
      }
    })

    assertThat(debugEvaluated).isZero()
    assertThat(infoEvaluated).isZero()
    assertThat(traceEvaluated).isZero()
    assertThat(warnEvaluated).isEqualTo(1)
    assertThat(errorEvaluated).isEqualTo(1)
    assertThat(logger.messages).containsExactly("warn:cache is stale", "error:compile failed")
    assertThat(logger.traces).isEmpty()
  }

  @Test
  def tupleInterfacePreservesBothGenericValues(): Unit = {
    val severityAndCategory: T2[Severity, String] = Pair(Severity.Info, "lint")
    val nested: T2[String, T2[Severity, String]] = Pair("diagnostic", severityAndCategory)
    val sourceFile = new File("build.sbt")
    val pathAndFile: T2[String, File] = Pair(sourceFile.getPath, sourceFile)

    assertThat(severityAndCategory.get1()).isSameAs(Severity.Info)
    assertThat(severityAndCategory.get2()).isEqualTo("lint")
    assertThat(nested.get1()).isEqualTo("diagnostic")
    assertThat(nested.get2().get1()).isSameAs(Severity.Info)
    assertThat(nested.get2().get2()).isEqualTo("lint")
    assertThat(pathAndFile.get1()).isEqualTo("build.sbt")
    assertThat(pathAndFile.get2()).isSameAs(sourceFile)
  }

  private def presentInt(value: Int): Optional[JInteger] = Optional.of(JInteger.valueOf(value))

  private def countingMessageSupplier(message: String, markEvaluated: () => Unit): Supplier[String] =
    new Supplier[String] {
      override def get(): String = {
        markEvaluated()
        message
      }
    }

  private def displayDiagnostic(problem: Problem): String =
    problem.rendered().orElseGet(new Supplier[String] {
      override def get(): String = fieldBasedDiagnostic(problem)
    })

  private def fieldBasedDiagnostic(problem: Problem): String = {
    val position = problem.position()
    val lineSuffix = if (position.line().isPresent) s":${position.line().get()}" else ""
    val source = if (position.sourcePath().isPresent) s"${position.sourcePath().get()}$lineSuffix" else "<unknown>"
    val pointer = s"${position.pointerSpace().orElse("")}^"

    s"${problem.severity()} ${problem.category()}: ${problem.message()}\n$source\n${position.lineContent()}\n$pointer"
  }

  private class StaticPosition(
    lineNumber: Optional[JInteger],
    text: String,
    characterOffset: Optional[JInteger],
    pointerColumn: Optional[JInteger],
    pointerPadding: Optional[String],
    path: Optional[String],
    file: Optional[File]
  ) extends Position {
    override def line(): Optional[JInteger] = lineNumber

    override def lineContent(): String = text

    override def offset(): Optional[JInteger] = characterOffset

    override def pointer(): Optional[JInteger] = pointerColumn

    override def pointerSpace(): Optional[String] = pointerPadding

    override def sourcePath(): Optional[String] = path

    override def sourceFile(): Optional[File] = file
  }

  private final class RangePosition(
    lineNumber: Optional[JInteger],
    text: String,
    characterOffset: Optional[JInteger],
    pointerColumn: Optional[JInteger],
    pointerPadding: Optional[String],
    path: Optional[String],
    file: Optional[File],
    start: Optional[JInteger],
    end: Optional[JInteger],
    startLineNumber: Optional[JInteger],
    startColumnNumber: Optional[JInteger],
    endLineNumber: Optional[JInteger],
    endColumnNumber: Optional[JInteger]
  ) extends StaticPosition(lineNumber, text, characterOffset, pointerColumn, pointerPadding, path, file) {
    override def startOffset(): Optional[JInteger] = start

    override def endOffset(): Optional[JInteger] = end

    override def startLine(): Optional[JInteger] = startLineNumber

    override def startColumn(): Optional[JInteger] = startColumnNumber

    override def endLine(): Optional[JInteger] = endLineNumber

    override def endColumn(): Optional[JInteger] = endColumnNumber
  }

  private class DiagnosticProblem(
    diagnosticCategory: String,
    diagnosticSeverity: Severity,
    diagnosticMessage: String,
    diagnosticPosition: Position
  ) extends Problem {
    override def category(): String = diagnosticCategory

    override def severity(): Severity = diagnosticSeverity

    override def message(): String = diagnosticMessage

    override def position(): Position = diagnosticPosition
  }

  private final class RenderedDiagnosticProblem(
    diagnosticCategory: String,
    diagnosticSeverity: Severity,
    diagnosticMessage: String,
    diagnosticPosition: Position,
    renderedDiagnostic: String
  ) extends DiagnosticProblem(diagnosticCategory, diagnosticSeverity, diagnosticMessage, diagnosticPosition) {
    override def rendered(): Optional[String] = Optional.of(renderedDiagnostic)
  }

  private final class DeferredLogger extends Logger {
    val pendingMessages: ArrayList[T2[String, Supplier[String]]] = new ArrayList[T2[String, Supplier[String]]]()
    val pendingTraces: ArrayList[Supplier[Throwable]] = new ArrayList[Supplier[Throwable]]()
    val messages: ArrayList[String] = new ArrayList[String]()
    val traces: ArrayList[Throwable] = new ArrayList[Throwable]()

    override def error(message: Supplier[String]): Unit = pendingMessages.add(Pair("error", message))

    override def warn(message: Supplier[String]): Unit = pendingMessages.add(Pair("warn", message))

    override def info(message: Supplier[String]): Unit = pendingMessages.add(Pair("info", message))

    override def debug(message: Supplier[String]): Unit = pendingMessages.add(Pair("debug", message))

    override def trace(exception: Supplier[Throwable]): Unit = pendingTraces.add(exception)

    def flush(): Unit = {
      var messageIndex = 0
      while (messageIndex < pendingMessages.size()) {
        val entry = pendingMessages.get(messageIndex)
        messages.add(s"${entry.get1()}:${entry.get2().get()}")
        messageIndex += 1
      }

      var traceIndex = 0
      while (traceIndex < pendingTraces.size()) {
        traces.add(pendingTraces.get(traceIndex).get())
        traceIndex += 1
      }
    }
  }

  private final class FilteringLogger(enabledMessageLevels: Set[String], tracesEnabled: Boolean) extends Logger {
    val messages: ArrayList[String] = new ArrayList[String]()
    val traces: ArrayList[Throwable] = new ArrayList[Throwable]()

    override def error(message: Supplier[String]): Unit = accept("error", message)

    override def warn(message: Supplier[String]): Unit = accept("warn", message)

    override def info(message: Supplier[String]): Unit = accept("info", message)

    override def debug(message: Supplier[String]): Unit = accept("debug", message)

    override def trace(exception: Supplier[Throwable]): Unit = {
      if (tracesEnabled) {
        traces.add(exception.get())
      }
    }

    private def accept(level: String, message: Supplier[String]): Unit = {
      if (enabledMessageLevels.contains(level)) {
        messages.add(s"$level:${message.get()}")
      }
    }
  }

  private final case class Pair[A1, A2](first: A1, second: A2) extends T2[A1, A2] {
    override def get1(): A1 = first

    override def get2(): A2 = second
  }
}
