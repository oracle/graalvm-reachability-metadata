/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_schema_macros_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import zio.schema.internal.SourceLocation

import scala.compiletime.{constValue, erasedValue, summonInline}
import scala.deriving.Mirror
import scala.jdk.CollectionConverters.*

private inline def generatedSourceLocation: SourceLocation = ${ SourceLocation.generateSourceLocation }

private inline def sourceLocationMirrorHasExpectedElementTypes(using mirror: Mirror.ProductOf[SourceLocation]): Boolean = {
  summonInline[mirror.MirroredElemTypes =:= (String, Int, Int)]
  true
}

private final case class DerivedProductDescription(typeName: String, fieldNames: List[String], arity: Int)

private object DerivedProductDescription {
  inline def forProduct[A](using mirror: Mirror.ProductOf[A]): DerivedProductDescription =
    DerivedProductDescription(
      constValue[mirror.MirroredLabel].asInstanceOf[String],
      tupleLabels[mirror.MirroredElemLabels],
      tupleSize[mirror.MirroredElemTypes]
    )

  private inline def tupleLabels[Labels <: Tuple]: List[String] =
    inline erasedValue[Labels] match {
      case _: EmptyTuple      => Nil
      case _: (head *: tail) => constValue[head].asInstanceOf[String] :: tupleLabels[tail]
    }

  private inline def tupleSize[Elements <: Tuple]: Int =
    inline erasedValue[Elements] match {
      case _: EmptyTuple      => 0
      case _: (_ *: tail) => 1 + tupleSize[tail]
    }
}

class Zio_schema_macros_3Test {
  @Test
  def constructorAccessorsCopyAndTupleAccessorsPreserveLocationFields(): Unit = {
    val location = SourceLocation("src/main/scala/Example.scala", 12, 5)
    val movedLocation = location.copy(line = 18)

    assertThat(location.path).isEqualTo("src/main/scala/Example.scala")
    assertThat(location.line).isEqualTo(12)
    assertThat(location.col).isEqualTo(5)
    assertThat(location._1).isEqualTo(location.path)
    assertThat(location._2).isEqualTo(location.line)
    assertThat(location._3).isEqualTo(location.col)

    assertThat(movedLocation.path).isEqualTo(location.path)
    assertThat(movedLocation.line).isEqualTo(18)
    assertThat(movedLocation.col).isEqualTo(location.col)
    assertThat(movedLocation).isNotEqualTo(location)
  }

  @Test
  def equalityHashCodeAndStringRepresentationUseAllLocationFields(): Unit = {
    val first = SourceLocation("module/Foo.scala", 3, 7)
    val same = SourceLocation("module/Foo.scala", 3, 7)
    val differentPath = SourceLocation("module/Bar.scala", 3, 7)
    val differentLine = SourceLocation("module/Foo.scala", 4, 7)
    val differentColumn = SourceLocation("module/Foo.scala", 3, 8)

    assertThat(first).isEqualTo(same)
    assertThat(first.hashCode()).isEqualTo(same.hashCode())
    assertThat(first).isNotEqualTo(differentPath)
    assertThat(first).isNotEqualTo(differentLine)
    assertThat(first).isNotEqualTo(differentColumn)
    assertThat(first).isNotEqualTo("module/Foo.scala:3:7")
    assertThat(first.toString).isEqualTo("SourceLocation(module/Foo.scala,3,7)")
  }

  @Test
  def productApiExposesStableCaseClassFieldOrderAndNames(): Unit = {
    val location = SourceLocation("case-class.scala", 21, 34)

    assertThat(location.productPrefix).isEqualTo("SourceLocation")
    assertThat(location.productArity).isEqualTo(3)
    assertThat(location.canEqual(SourceLocation("other.scala", 1, 1))).isTrue()
    assertThat(location.canEqual("not-a-location")).isFalse()
    assertThat(location.productElement(0)).isEqualTo("case-class.scala")
    assertThat(location.productElement(1)).isEqualTo(21)
    assertThat(location.productElement(2)).isEqualTo(34)
    assertThat(location.productElementName(0)).isEqualTo("path")
    assertThat(location.productElementName(1)).isEqualTo("line")
    assertThat(location.productElementName(2)).isEqualTo("col")
    assertThat(location.productIterator.toList.asJava).containsExactly("case-class.scala", Integer.valueOf(21), Integer.valueOf(34))
    assertThat(location.productElementNames.toList.asJava).containsExactly("path", "line", "col")
  }

  @Test
  def patternMatchingAndMirrorConstructionRoundTripThroughPublicProductShape(): Unit = {
    val location = SourceLocation("round-trip.scala", 8, 13)
    val (path, line, column) = location match {
      case SourceLocation(path, line, column) => (path, line, column)
    }
    val rebuilt = SourceLocation.fromProduct(Tuple3(path, line, column))

    assertThat(path).isEqualTo("round-trip.scala")
    assertThat(line).isEqualTo(8)
    assertThat(column).isEqualTo(13)
    assertThat(rebuilt).isEqualTo(location)
    assertThat(SourceLocation.unapply(location)).isEqualTo(location)
  }

  @Test
  def companionMirrorProvidesCompileTimeProductMetadataForGenericDerivation(): Unit = {
    val description = DerivedProductDescription.forProduct[SourceLocation]

    assertThat(description.typeName).isEqualTo("SourceLocation")
    assertThat(description.fieldNames.asJava).containsExactly("path", "line", "col")
    assertThat(description.arity).isEqualTo(3)
    assertThat(sourceLocationMirrorHasExpectedElementTypes).isTrue()
  }

  @Test
  def generateSourceLocationMacroCapturesTheCallSiteAtCompileTime(): Unit = {
    val location = generatedSourceLocation

    assertThat(location.path.replace('\\', '/')).endsWith("Zio_schema_macros_3Test.scala")
    assertThat(location.line).isPositive()
    assertThat(location.col).isPositive()
  }

  @Test
  def generateSourceLocationMacroExpandsAtEachIndividualCallSite(): Unit = {
    val firstLocation = generatedSourceLocation
    val secondLocation = generatedSourceLocation

    assertThat(secondLocation.path).isEqualTo(firstLocation.path)
    assertThat(secondLocation.line).isGreaterThan(firstLocation.line)
    assertThat(firstLocation.col).isPositive()
    assertThat(secondLocation.col).isPositive()
  }
}
