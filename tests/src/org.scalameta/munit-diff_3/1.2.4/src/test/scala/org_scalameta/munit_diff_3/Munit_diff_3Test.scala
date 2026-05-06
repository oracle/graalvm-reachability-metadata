/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.munit_diff_3

import java.util

import munit.diff.ChangeDelta
import munit.diff.Chunk
import munit.diff.DeleteDelta
import munit.diff.Diff
import munit.diff.DiffNode
import munit.diff.DiffOptions
import munit.diff.DiffUtils
import munit.diff.EmptyPrinter
import munit.diff.Equalizer
import munit.diff.InsertDelta
import munit.diff.MyersDiff
import munit.diff.Patch
import munit.diff.Printer
import munit.diff.Snake
import munit.diff.console.AnsiColors
import munit.diff.console.Printers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Munit_diff_3Test {
  @Test
  def unifiedDiffIncludesChangedLinesContextAndOptionalLineNumbers(): Unit = {
    val options: DiffOptions = DiffOptions
      .withForceAnsi(Some(false))
      .withContextSize(1)
      .withShowLines(true)
    val obtained: String = "alpha\nbravo changed\ncharlie\ndelta"
    val expected: String = "alpha\nbravo\ncharlie\necho"

    val report: String = Diff.unifiedDiff(obtained, expected)(options)

    assertEquals(
      "@@ -1,4 +1,4 @@\n" +
        " alpha\n" +
        "-bravo\n" +
        "+bravo changed\n" +
        " charlie\n" +
        "-echo\n" +
        "+delta",
      report,
    )
  }

  @Test
  def diffFiltersAnsiCodesAndDetectsEmptyDiffs(): Unit = {
    val options: DiffOptions = DiffOptions.withForceAnsi(Some(false))
    val obtained: String = AnsiColors.LightGreen + "same value" + AnsiColors.Reset

    val diff: Diff = new Diff(obtained, "same value", options)

    assertEquals("same value", diff.obtainedClean)
    assertEquals("same value", diff.expectedClean)
    assertTrue(diff.isEmpty)
    assertEquals("", diff.unifiedDiff)
  }

  @Test
  def reportIncludesObtainedValueAndDiffWithoutAnsiWhenDisabled(): Unit = {
    val options: DiffOptions = DiffOptions.withForceAnsi(Some(false))
    val diff: Diff = new Diff("left\nright changed", "left\nright", options)

    val report: String = diff.createReport(
      title = "comparison failed",
      printObtainedAsStripMargin = true,
    )

    assertTrue(report.startsWith("comparison failed\n=> Obtained\n"))
    assertTrue(
      report.contains(
        "    \"\"\"|left\n" +
          "       |right changed\n" +
          "       |\"\"\".stripMargin"
      )
    )
    assertTrue(report.contains("=> Diff (- expected, + obtained)\n"))
    assertTrue(report.contains("-right\n+right changed"))
    assertFalse(report.contains(AnsiColors.Bold))
  }

  @Test
  def diffOnlyReportOmitsObtainedSection(): Unit = {
    val options: DiffOptions = DiffOptions.withForceAnsi(Some(false))
    val report: String = Diff.createDiffOnlyReport("actual", "expected")(options)

    assertEquals(
      "=> Diff (- expected, + obtained)\n-expected\n+actual",
      report,
    )
    assertFalse(report.contains("Obtained"))
  }

  @Test
  def unifiedDiffMarksTrailingSpacesInChangedLines(): Unit = {
    val options: DiffOptions = DiffOptions
      .withForceAnsi(Some(false))
      .withContextSize(0)
    val obtained: String = "first \nsecond"
    val expected: String = "first\nsecond"

    val report: String = Diff.unifiedDiff(obtained, expected)(options)

    assertEquals("-first\n+first ∙", report)
  }

  @Test
  def optionsAreImmutableAndExposeConfiguredValues(): Unit = {
    val printer: Printer = Printer(height = 2) { case value: Int => s"int=$value" }
    val customized: DiffOptions = DiffOptions
      .withForceAnsi(Some(false))
      .withContextSize(3)
      .withShowLines(true)
      .withObtainedAsStripMargin(true)
      .withPrinter(Some(printer))

    assertEquals(None, DiffOptions.forceAnsi)
    assertEquals(1, DiffOptions.contextSize)
    assertFalse(DiffOptions.showLines)
    assertFalse(DiffOptions.obtainedAsStripMargin)
    assertEquals(None, DiffOptions.printer)
    assertTrue(DiffOptions.ansi(orElse = true))

    assertEquals(Some(false), customized.forceAnsi)
    assertFalse(customized.ansi(orElse = true))
    assertEquals(3, customized.contextSize)
    assertTrue(customized.showLines)
    assertTrue(customized.obtainedAsStripMargin)
    assertSame(printer, customized.printer.get)
  }

  @Test
  def printerCompositionTriesFirstPrinterBeforeFallback(): Unit = {
    val integers: Printer = Printer(height = 2) { case value: Int =>
      s"int=$value"
    }
    val strings: Printer = Printer { case value: String => s"str=$value" }
    val combined: Printer = integers.orElse(strings)
    val out: StringBuilder = new StringBuilder

    assertEquals(Printer.defaultHeight, strings.height)
    assertEquals(Printer.defaultHeight, combined.height)
    assertTrue(combined.print(42, out, indent = 0))
    assertEquals("int=42", out.toString())

    out.clear()
    assertTrue(combined.print("hello", out, indent = 0))
    assertEquals("str=hello", out.toString())

    out.clear()
    assertFalse(combined.print(1.5d, out, indent = 0))
    assertFalse(EmptyPrinter.print("unused", out, indent = 0))
    assertEquals("", out.toString())
  }

  @Test
  def consolePrintersEscapeSingleLineStringsAndPreserveMultilineStrings(): Unit = {
    val escapedSingleLine: String = "\"" +
      "a" +
      "\\\"" +
      "\\\\" +
      "\\t" +
      "\\u0001" +
      "\\u2603" +
      "é" +
      "\""

    assertEquals(escapedSingleLine, Printers.print("a\"\\\t\u0001☃é"))

    val builder: StringBuilder = new StringBuilder
    Printers.printString("line 1\nline 2", builder, EmptyPrinter)

    assertEquals("\"\"\"line 1\nline 2\"\"\"", builder.toString())
  }

  @Test
  def ansiUtilitiesWrapAndRemoveConsoleEscapeSequences(): Unit = {
    val colored: String = AnsiColors.c("warning", AnsiColors.YELLOW)

    assertEquals("warning", AnsiColors.filterAnsi(colored))
    assertNull(AnsiColors.filterAnsi(null))
    assertEquals("plain", AnsiColors.c("plain", null))
  }

  @Test
  def myersDiffIdentifiesChangesInsertionsAndDeletions(): Unit = {
    val algorithm: MyersDiff[String] = new MyersDiff[String]()
    val patch: Patch[String] = algorithm.diff(
      javaList("a", "b", "c"),
      javaList("a", "B", "c", "d"),
    )
    val deltas: util.List[munit.diff.Delta[String]] = patch.getDeltas

    assertEquals(2, deltas.size())

    val changed: munit.diff.Delta[String] = deltas.get(0)
    assertEquals(changed.TYPE.CHANGE, changed.getType)
    assertEquals(1, changed.getOriginal.getPosition)
    assertEquals(javaList("b"), changed.getOriginal.getLines)
    assertEquals(javaList("B"), changed.getRevised.getLines)

    val inserted: munit.diff.Delta[String] = deltas.get(1)
    assertEquals(inserted.TYPE.INSERT, inserted.getType)
    assertEquals(3, inserted.getOriginal.getPosition)
    assertEquals(javaList(), inserted.getOriginal.getLines)
    assertEquals(javaList("d"), inserted.getRevised.getLines)

    val deletionPatch: Patch[String] = algorithm.diff(
      javaList("a", "b", "c"),
      javaList("a", "c"),
    )
    val utilityPatch: Patch[String] = DiffUtils.diff(javaList("x"), javaList("y"))
    val deleted: munit.diff.Delta[String] = deletionPatch.getDeltas.get(0)
    assertEquals(deleted.TYPE.DELETE, deleted.getType)
    assertEquals(javaList("b"), deleted.getOriginal.getLines)
    assertEquals(javaList(), deleted.getRevised.getLines)
    assertEquals(1, utilityPatch.getDeltas.size())
  }

  @Test
  def customEqualizerControlsDiffEquality(): Unit = {
    val caseInsensitive: MyersDiff[String] = new MyersDiff[String](
      new CaseInsensitiveEqualizer
    )
    val defaultDiff: Patch[String] = new MyersDiff[String]().diff(
      javaList("Alpha"),
      javaList("alpha"),
    )
    val caseInsensitiveDiff: Patch[String] = caseInsensitive.diff(
      javaList("Alpha"),
      javaList("alpha"),
    )

    assertEquals(1, defaultDiff.getDeltas.size())
    assertTrue(caseInsensitiveDiff.getDeltas.isEmpty)
  }

  @Test
  def patchSortsDeltasByOriginalPosition(): Unit = {
    val patch: Patch[String] = new Patch[String]
    val later: ChangeDelta[String] = new ChangeDelta[String](
      new Chunk[String](3, javaList("d")),
      new Chunk[String](3, javaList("D")),
    )
    val earlier: ChangeDelta[String] = new ChangeDelta[String](
      new Chunk[String](1, javaList("b")),
      new Chunk[String](1, javaList("B")),
    )

    patch.addDelta(later)
    patch.addDelta(earlier)

    val deltas: util.List[munit.diff.Delta[String]] = patch.getDeltas
    assertSame(earlier, deltas.get(0))
    assertSame(later, deltas.get(1))
    assertTrue(patch.toString.contains("Delta(CHANGE"))
  }

  @Test
  def diffUtilsCanCreateUnifiedDiffFromManualPatch(): Unit = {
    val patch: Patch[String] = new Patch[String]
    patch.addDelta(
      new ChangeDelta[String](
        new Chunk[String](1, javaList("b")),
        new Chunk[String](1, javaList("B")),
      )
    )
    patch.addDelta(
      new InsertDelta[String](
        new Chunk[String](2, javaList()),
        new Chunk[String](2, javaList("c")),
      )
    )

    val unified: util.List[String] = DiffUtils.generateUnifiedDiff(
      "old.txt",
      "new.txt",
      javaList("a", "b"),
      patch,
      contextSize = 0,
    )

    assertEquals(
      javaList(
        "--- old.txt",
        "+++ new.txt",
        "@@ -2,1 +2,2 @@",
        "-b",
        "+B",
        "+c",
      ),
      unified,
    )
  }

  @Test
  def chunkDeltaAndPathNodesExposeUsefulState(): Unit = {
    val original: Chunk[String] = new Chunk[String](4, javaList("old"))
    val revised: Chunk[String] = new Chunk[String](4, javaList("new"))
    val change: ChangeDelta[String] = new ChangeDelta[String](original, revised)
    val insert: InsertDelta[String] = new InsertDelta[String](
      new Chunk[String](5, javaList()),
      new Chunk[String](5, javaList("added")),
    )
    val delete: DeleteDelta[String] = new DeleteDelta[String](
      new Chunk[String](6, javaList("removed")),
      new Chunk[String](6, javaList()),
    )

    assertEquals(4, original.getPosition)
    assertEquals(1, original.size)
    assertSame(original, change.getOriginal)
    assertSame(revised, change.getRevised)
    assertEquals(change.TYPE.CHANGE, change.getType)
    assertEquals(insert.TYPE.INSERT, insert.getType)
    assertEquals(delete.TYPE.DELETE, delete.getType)
    assertEquals("Chunk(4, [old], 1)", original.toString)
    assertTrue(change.toString.contains("Delta(CHANGE"))

    val bootstrap: Snake = new Snake(0, -1, null)
    val snake: Snake = new Snake(1, 1, bootstrap)
    val diffNode: DiffNode = new DiffNode(2, 1, snake)

    assertTrue(bootstrap.isBootstrap)
    assertTrue(snake.isSnake)
    assertFalse(diffNode.isSnake)
    assertSame(snake, diffNode.previousSnake)
    assertNotNull(diffNode.toString)
  }

  private def javaList(values: String*): util.List[String] = {
    val list: util.ArrayList[String] = new util.ArrayList[String]()
    values.foreach(value => list.add(value))
    list
  }
}

class CaseInsensitiveEqualizer extends Equalizer[String] {
  override def equals(original: String, revised: String): Boolean =
    original.equalsIgnoreCase(revised)
}
