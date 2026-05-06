/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.sourcepos_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNotEquals, assertThrows, assertTrue}
import org.junit.jupiter.api.Test
import org.tpolecat.sourcepos.SourcePos

class Sourcepos_3Test {
  @Test
  def capturesCompileTimeCallSiteForDirectSummonsAndContextParameters(): Unit = {
    val first: SourcePos = summon[SourcePos]
    val second: SourcePos = summon[SourcePos]
    val contextual: SourcePos = captureContextualSourcePosition

    assertPointsAtThisTest(first)
    assertPointsAtThisTest(second)
    assertPointsAtThisTest(contextual)
    assertEquals(first.line + 1, second.line)
    assertTrue(contextual.line > second.line, s"Expected contextual position after line ${second.line}, got $contextual")
  }

  @Test
  def usesExplicitContextualSourcePositionWhenProvided(): Unit = {
    val provided: SourcePos = SourcePos("Provided.scala", 128)
    val observed: SourcePos = captureWithProvidedSourcePosition(provided)

    assertEquals("Provided.scala", observed.file)
    assertEquals(128, observed.line)
    assertEquals(provided, observed)
  }

  @Test
  def exposesCompanionInstanceForExplicitExpansion(): Unit = {
    val explicit: SourcePos = SourcePos.instance
    val passedExplicitly: SourcePos = captureContextualSourcePosition(using SourcePos.instance)

    assertPointsAtThisTest(explicit)
    assertPointsAtThisTest(passedExplicitly)
    assertEquals(explicit.line + 1, passedExplicitly.line)
    assertNotEquals(explicit, passedExplicitly)
  }

  @Test
  def supportsValueSemanticsCopyingAndStringRendering(): Unit = {
    val position: SourcePos = SourcePos("Main.scala", 42)
    val samePosition: SourcePos = SourcePos.apply("Main.scala", 42)
    val differentFile: SourcePos = position.copy(file = "Other.scala")
    val differentLine: SourcePos = position.copy(line = 43)

    assertEquals("Main.scala", position.file)
    assertEquals(42, position.line)
    assertEquals(position, samePosition)
    assertEquals(position.hashCode, samePosition.hashCode)
    assertEquals(position, position.copy())
    assertEquals(SourcePos("Other.scala", 42), differentFile)
    assertEquals(SourcePos("Main.scala", 43), differentLine)
    assertNotEquals(position, differentFile)
    assertNotEquals(position, differentLine)
    assertEquals("Main.scala:42", position.toString)
  }

  @Test
  def supportsPatternMatchingAndProductOperations(): Unit = {
    val position: SourcePos = SourcePos("Reader.scala", 3)
    val SourcePos(file, line) = position
    val asTuple: (String, Int) = position match {
      case SourcePos(matchedFile, matchedLine) => (matchedFile, matchedLine)
    }

    assertEquals("Reader.scala", file)
    assertEquals(3, line)
    assertEquals(("Reader.scala", 3), asTuple)
    assertEquals(2, position.productArity)
    assertEquals("SourcePos", position.productPrefix)
    assertEquals("Reader.scala", position.productElement(0))
    assertEquals(3, position.productElement(1).asInstanceOf[Int])
    assertEquals("file", position.productElementName(0))
    assertEquals("line", position.productElementName(1))
    assertEquals(List("file", "line"), position.productElementNames.toList)
    assertEquals(List("Reader.scala", 3), position.productIterator.toList)
    assertTrue(position.canEqual(SourcePos("Other.scala", 4)))
    assertFalse(position.canEqual(("Reader.scala", 3)))
    assertThrows(classOf[IndexOutOfBoundsException], () => {
      position.productElement(2)
      ()
    })
  }

  @Test
  def companionBuildsPositionsFromProducts(): Unit = {
    val fromTuple: SourcePos = SourcePos.fromProduct(("Macro.scala", 19))

    assertEquals(SourcePos("Macro.scala", 19), fromTuple)
    assertEquals(SourcePos("Macro.scala", 19), SourcePos.unapply(fromTuple))
    assertEquals("SourcePos", SourcePos.toString)
  }

  private def captureContextualSourcePosition(using sourcePos: SourcePos): SourcePos = sourcePos

  private def captureWithProvidedSourcePosition(provided: SourcePos): SourcePos = {
    given SourcePos = provided

    captureContextualSourcePosition
  }

  private def assertPointsAtThisTest(sourcePos: SourcePos): Unit = {
    val normalizedFile: String = sourcePos.file.replace('\\', '/')

    assertTrue(
      normalizedFile.endsWith("Sourcepos_3Test.scala"),
      s"Expected source position to refer to Sourcepos_3Test.scala, got $sourcePos"
    )
    assertTrue(sourcePos.line > 0, s"Expected a positive line number, got $sourcePos")
  }
}
