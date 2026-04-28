/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala3_interfaces

import dotty.tools.dotc.interfaces.AbstractFile
import dotty.tools.dotc.interfaces.CompilerCallback
import dotty.tools.dotc.interfaces.Diagnostic
import dotty.tools.dotc.interfaces.DiagnosticRelatedInformation
import dotty.tools.dotc.interfaces.ReporterResult
import dotty.tools.dotc.interfaces.SimpleReporter
import dotty.tools.dotc.interfaces.SourceFile
import dotty.tools.dotc.interfaces.SourcePosition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.io.File
import java.util.Optional
import java.util.{ArrayList, List as JList}

class Scala3_interfacesTest {
  @Test
  def abstractAndSourceFilesExposeIdentityLocationAndContent(): Unit = {
    val backingFile = new File("build/test-input/Greeter.scala")
    val abstractFile = TestAbstractFile("Greeter.scala", backingFile.getPath, Optional.of(backingFile))
    val sourceFile = TestSourceFile("Generated.scala", "virtual:///Generated.scala", Optional.empty(), "object Generated")

    assertThat(abstractFile.name()).isEqualTo("Greeter.scala")
    assertThat(abstractFile.path()).isEqualTo(backingFile.getPath)
    assertThat(abstractFile.jfile()).contains(backingFile)

    assertThat(sourceFile.name()).isEqualTo("Generated.scala")
    assertThat(sourceFile.path()).isEqualTo("virtual:///Generated.scala")
    assertThat(sourceFile.jfile()).isEmpty()
    assertThat(new String(sourceFile.content())).isEqualTo("object Generated")
  }

  @Test
  def sourceFilesCanBeHandledAsAbstractFilesInSharedFilePipelines(): Unit = {
    val backingFile = new File("build/test-input/Pipeline.scala")
    val sourceFile: SourceFile = TestSourceFile(
      "Pipeline.scala",
      backingFile.getPath,
      Optional.of(backingFile),
      "class Pipeline"
    )
    val generatedFile: AbstractFile = TestAbstractFile("Pipeline.class", "build/classes/Pipeline.class", Optional.empty())
    val files = ArrayList[AbstractFile]()

    files.add(sourceFile)
    files.add(generatedFile)

    assertThat(abstractFileDescriptions(files)).containsExactly(
      s"Pipeline.scala:${backingFile.getPath}:has-java-file",
      "Pipeline.class:build/classes/Pipeline.class:virtual"
    )
  }

  @Test
  def sourcePositionDescribesPrimaryRangeAndOwningSource(): Unit = {
    val sourceFile = TestSourceFile("Calculator.scala", "memory:///Calculator.scala", Optional.empty(), "val result = 40 + 2")
    val position = TestSourcePosition(
      lineContentValue = "val result = 40 + 2",
      pointValue = 13,
      lineValue = 1,
      columnValue = 14,
      startValue = 11,
      startLineValue = 1,
      startColumnValue = 12,
      endValue = 16,
      endLineValue = 1,
      endColumnValue = 17,
      sourceValue = sourceFile
    )

    assertThat(position.lineContent()).isEqualTo("val result = 40 + 2")
    assertThat(position.point()).isEqualTo(13)
    assertThat(position.line()).isEqualTo(1)
    assertThat(position.column()).isEqualTo(14)
    assertThat(position.start()).isEqualTo(11)
    assertThat(position.startLine()).isEqualTo(1)
    assertThat(position.startColumn()).isEqualTo(12)
    assertThat(position.end()).isEqualTo(16)
    assertThat(position.endLine()).isEqualTo(1)
    assertThat(position.endColumn()).isEqualTo(17)
    assertThat(position.source()).isSameAs(sourceFile)
  }

  @Test
  def sourcePositionCanRepresentUnknownLineAndColumnWhileKeepingContentOffsets(): Unit = {
    val sourceText = """object Demo {
                       |  val answer = 42
                       |}
                       |""".stripMargin
    val sourceFile = TestSourceFile("Demo.scala", "memory:///Demo.scala", Optional.empty(), sourceText)
    val startOffset = sourceText.indexOf("answer")
    val endOffset = startOffset + "answer".length
    val position = TestSourcePosition(
      lineContentValue = "",
      pointValue = startOffset,
      lineValue = -1,
      columnValue = -1,
      startValue = startOffset,
      startLineValue = -1,
      startColumnValue = -1,
      endValue = endOffset,
      endLineValue = -1,
      endColumnValue = -1,
      sourceValue = sourceFile
    )

    assertThat(position.line()).isEqualTo(-1)
    assertThat(position.column()).isEqualTo(-1)
    assertThat(position.startLine()).isEqualTo(-1)
    assertThat(position.startColumn()).isEqualTo(-1)
    assertThat(position.endLine()).isEqualTo(-1)
    assertThat(position.endColumn()).isEqualTo(-1)
    assertThat(positionText(position)).isEqualTo("answer")
    assertThat(position.source().content()(position.point())).isEqualTo('a')
  }

  @Test
  def diagnosticCapturesSeverityLocationAndRelatedInformation(): Unit = {
    val sourceFile = TestSourceFile("Main.scala", "memory:///Main.scala", Optional.empty(), "println(missing)")
    val primaryPosition = TestSourcePosition("println(missing)", 8, 1, 9, 8, 1, 9, 15, 1, 16, sourceFile)
    val definitionPosition = TestSourcePosition("val missing = 42", 4, 2, 5, 4, 2, 5, 11, 2, 12, sourceFile)
    val related = TestDiagnosticRelatedInformation(definitionPosition, "A similarly named value is defined here")
    val diagnostic = TestDiagnostic(
      "Not found: missing",
      Diagnostic.ERROR,
      Optional.of(primaryPosition),
      JList.of(related)
    )

    assertThat(diagnostic.message()).isEqualTo("Not found: missing")
    assertThat(diagnostic.level()).isEqualTo(Diagnostic.ERROR)
    assertThat(diagnostic.position()).contains(primaryPosition)
    assertThat(diagnostic.diagnosticRelatedInformation()).containsExactly(related)
    assertThat(related.position()).isSameAs(definitionPosition)
    assertThat(related.message()).isEqualTo("A similarly named value is defined here")
  }

  @Test
  def simpleReporterAndReporterResultTrackWarningsAndErrors(): Unit = {
    val reporter = RecordingReporter()
    val warning = TestDiagnostic("unused import", Diagnostic.WARNING, Optional.empty(), JList.of())
    val error = TestDiagnostic("type mismatch", Diagnostic.ERROR, Optional.empty(), JList.of())
    val info = TestDiagnostic("compiling module", Diagnostic.INFO, Optional.empty(), JList.of())

    reporter.report(warning)
    reporter.report(error)
    reporter.report(info)

    assertThat(reporter.diagnostics).containsExactly(warning, error, info)

    val result: ReporterResult = reporter.toResult
    assertThat(result.hasErrors()).isTrue()
    assertThat(result.errorCount()).isEqualTo(1)
    assertThat(result.hasWarnings()).isTrue()
    assertThat(result.warningCount()).isEqualTo(1)
  }

  @Test
  def compilerCallbackDefaultMethodsAreSafeNoOps(): Unit = {
    val callback = new CompilerCallback {}
    val sourceFile = TestSourceFile("Noop.scala", "memory:///Noop.scala", Optional.empty(), "class Noop")
    val generatedFile = TestAbstractFile("Noop.class", "memory:///Noop.class", Optional.empty())

    callback.onSourceCompiled(sourceFile)
    callback.onClassGenerated(sourceFile, generatedFile, "example.Noop")
  }

  @Test
  def compilerCallbackImplementationsReceiveSourceAndGeneratedClassEvents(): Unit = {
    val callback = RecordingCompilerCallback()
    val sourceFile = TestSourceFile("Service.scala", "memory:///Service.scala", Optional.empty(), "class Service")
    val generatedFile = TestAbstractFile("Service.class", "memory:///Service.class", Optional.empty())

    callback.onSourceCompiled(sourceFile)
    callback.onClassGenerated(sourceFile, generatedFile, "example.Service")

    assertThat(callback.compiledSources).containsExactly(sourceFile)
    assertThat(callback.generatedClasses).containsExactly(GeneratedClass(sourceFile, generatedFile, "example.Service"))
  }

  private def positionText(position: SourcePosition): String =
    position.source().content().slice(position.start(), position.end()).mkString

  private def abstractFileDescriptions(files: JList[AbstractFile]): ArrayList[String] = {
    val descriptions = ArrayList[String]()
    files.forEach { file =>
      val fileKind = if file.jfile().isPresent then "has-java-file" else "virtual"
      descriptions.add(s"${file.name()}:${file.path()}:$fileKind")
    }
    descriptions
  }

  private final case class TestAbstractFile(
    nameValue: String,
    pathValue: String,
    javaFileValue: Optional[File]
  ) extends AbstractFile {
    override def name(): String = nameValue

    override def path(): String = pathValue

    override def jfile(): Optional[File] = javaFileValue
  }

  private final case class TestSourceFile(
    nameValue: String,
    pathValue: String,
    javaFileValue: Optional[File],
    contentValue: String
  ) extends SourceFile {
    override def name(): String = nameValue

    override def path(): String = pathValue

    override def jfile(): Optional[File] = javaFileValue

    override def content(): Array[Char] = contentValue.toCharArray
  }

  private final case class TestSourcePosition(
    lineContentValue: String,
    pointValue: Int,
    lineValue: Int,
    columnValue: Int,
    startValue: Int,
    startLineValue: Int,
    startColumnValue: Int,
    endValue: Int,
    endLineValue: Int,
    endColumnValue: Int,
    sourceValue: SourceFile
  ) extends SourcePosition {
    override def lineContent(): String = lineContentValue

    override def point(): Int = pointValue

    override def line(): Int = lineValue

    override def column(): Int = columnValue

    override def start(): Int = startValue

    override def startLine(): Int = startLineValue

    override def startColumn(): Int = startColumnValue

    override def end(): Int = endValue

    override def endLine(): Int = endLineValue

    override def endColumn(): Int = endColumnValue

    override def source(): SourceFile = sourceValue
  }

  private final case class TestDiagnosticRelatedInformation(
    positionValue: SourcePosition,
    messageValue: String
  ) extends DiagnosticRelatedInformation {
    override def position(): SourcePosition = positionValue

    override def message(): String = messageValue
  }

  private final case class TestDiagnostic(
    messageValue: String,
    levelValue: Int,
    positionValue: Optional[SourcePosition],
    relatedInformationValue: JList[DiagnosticRelatedInformation]
  ) extends Diagnostic {
    override def message(): String = messageValue

    override def level(): Int = levelValue

    override def position(): Optional[SourcePosition] = positionValue

    override def diagnosticRelatedInformation(): JList[DiagnosticRelatedInformation] = relatedInformationValue
  }

  private final class RecordingReporter extends SimpleReporter {
    val diagnostics: ArrayList[Diagnostic] = ArrayList[Diagnostic]()

    override def report(diagnostic: Diagnostic): Unit = diagnostics.add(diagnostic)

    def toResult: ReporterResult = TestReporterResult(
      errorCountValue = count(Diagnostic.ERROR),
      warningCountValue = count(Diagnostic.WARNING)
    )

    private def count(level: Int): Int = diagnostics.stream().filter(_.level() == level).count().toInt
  }

  private final case class TestReporterResult(errorCountValue: Int, warningCountValue: Int) extends ReporterResult {
    override def hasErrors(): Boolean = errorCountValue > 0

    override def errorCount(): Int = errorCountValue

    override def hasWarnings(): Boolean = warningCountValue > 0

    override def warningCount(): Int = warningCountValue
  }

  private final class RecordingCompilerCallback extends CompilerCallback {
    val compiledSources: ArrayList[SourceFile] = ArrayList[SourceFile]()
    val generatedClasses: ArrayList[GeneratedClass] = ArrayList[GeneratedClass]()

    override def onSourceCompiled(source: SourceFile): Unit = compiledSources.add(source)

    override def onClassGenerated(source: SourceFile, outputFile: AbstractFile, className: String): Unit =
      generatedClasses.add(GeneratedClass(source, outputFile, className))
  }

  private final case class GeneratedClass(source: SourceFile, outputFile: AbstractFile, className: String)
}
