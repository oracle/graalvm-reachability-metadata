/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.scalac_compat_annotation_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.typelevel.scalaccompat.annotation.*

import scala.compiletime.testing.typeChecks

final class Scalac_compat_annotation_3Test {
  @Test
  def scala3AliasesResolveToTheNativeScala3Annotations(): Unit = {
    assertSameType[nowarn, scala.annotation.nowarn]()
    assertSameType[nowarn3, scala.annotation.nowarn]()
    assertSameType[targetName3, scala.annotation.targetName]()
    assertSameType[uncheckedVariance, scala.annotation.unchecked.uncheckedVariance]()
    assertSameType[uncheckedVariance3, scala.annotation.unchecked.uncheckedVariance]()
    assertSameType[unused, scala.annotation.unused]()
  }

  @Test
  def publicAliasesCanBeConstructedAsScalaAnnotations(): Unit = {
    val annotations: List[scala.annotation.Annotation] = List(
      new nowarn("msg=.*"),
      new nowarn2("ignored on Scala 3"),
      new nowarn212(),
      new nowarn213(),
      new nowarn3("msg=.*"),
      new targetName3("renamedOperator"),
      new uncheckedVariance,
      new uncheckedVariance2,
      new uncheckedVariance212,
      new uncheckedVariance213,
      new uncheckedVariance3,
      new unused
    )

    assertThat(annotations.size).isEqualTo(12)
    assertThat(annotations.forall(_ != null)).isTrue()
  }

  @Test
  def annotationsAreAcceptedOnRepresentativeScalaDefinitions(): Unit = {
    assertThat(typeChecks("""
      import org.typelevel.scalaccompat.annotation.*

      @nowarn("msg=.*")
      final class CompatibilitySurface[+A](private val value: A) {
        @nowarn2("ignored on Scala 3")
        @nowarn212
        @nowarn213
        @nowarn3("msg=.*")
        def annotatedMethod(@unused label: String): A = value

        @targetName3("combineWithInt")
        def ++(right: Int): Int = right + 1

        def accept(value: A @uncheckedVariance): Int = 1
        def accept3(value: A @uncheckedVariance3): Int = 3
        def ignoredVariance2: Option[A @uncheckedVariance2] = Some(value)
        def ignoredVariance212: Option[A @uncheckedVariance212] = Some(value)
        def ignoredVariance213: Option[A @uncheckedVariance213] = Some(value)
      }
      """)).isTrue()
  }

  @Test
  def targetNameAliasCanBeUsedByOrdinaryScalaCallSites(): Unit = {
    val left = TargetNamedNumber(40)
    val right = TargetNamedNumber(2)

    assertThat((left + right).value).isEqualTo(42)
    assertThat((-right).value).isEqualTo(-2)
  }

  @Test
  def uncheckedVarianceAliasesSupportCovariantApiShapes(): Unit = {
    val reader: CovariantReader[String] = new StringReader("stored")
    val widened: CovariantReader[AnyRef] = reader

    assertThat(reader.accept("direct")).isEqualTo("direct")
    assertThat(reader.accept3("scala3")).isEqualTo("scala3")
    assertThat(widened.current).isEqualTo("stored")
  }

  private def assertSameType[A, B]()(using evidence: A =:= B): Unit = {
    assertThat(evidence).isNotNull()
  }

  private final case class TargetNamedNumber(value: Int) {
    @targetName3("plusCompat")
    def +(right: TargetNamedNumber): TargetNamedNumber = TargetNamedNumber(value + right.value)

    @targetName3("unaryMinusCompat")
    def unary_- : TargetNamedNumber = TargetNamedNumber(-value)
  }

  private trait CovariantReader[+A] {
    def current: A

    def accept(value: A @uncheckedVariance): A

    def accept3(value: A @uncheckedVariance3): A
  }

  private final class StringReader(stored: String) extends CovariantReader[String] {
    override def current: String = stored

    override def accept(value: String): String = value

    override def accept3(value: String): String = value
  }
}
