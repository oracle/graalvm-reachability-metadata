/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.scalac_compat_annotation_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.typelevel.scalaccompat.annotation._

final class Scalac_compat_annotation_2_13Test {
  @Test
  def scala213AliasesResolveToTheNativeScala213Annotations(): Unit = {
    assertSameType[nowarn, scala.annotation.nowarn]()
    assertSameType[nowarn2, scala.annotation.nowarn]()
    assertSameType[nowarn213, scala.annotation.nowarn]()
    assertSameType[uncheckedVariance, scala.annotation.unchecked.uncheckedVariance]()
    assertSameType[uncheckedVariance2, scala.annotation.unchecked.uncheckedVariance]()
    assertSameType[uncheckedVariance213, scala.annotation.unchecked.uncheckedVariance]()
    assertSameType[unused, scala.annotation.unused]()
  }

  @Test
  def publicAliasesCanBeConstructedAsScalaAnnotations(): Unit = {
    val annotations: List[scala.annotation.Annotation] = List(
      new nowarn("cat=deprecation"),
      new nowarn2("cat=deprecation"),
      new nowarn212(),
      new nowarn213(),
      new nowarn3("ignored on Scala 2.13"),
      new static3,
      new targetName3("renamedOperator"),
      new threadUnsafe3,
      new uncheckedVariance,
      new uncheckedVariance2,
      new uncheckedVariance212,
      new uncheckedVariance213,
      new uncheckedVariance3,
      new unused("reserved for source compatibility")
    )

    assertThat(annotations.size).isEqualTo(14)
    assertThat(annotations.forall(_ != null)).isTrue()
  }

  @Test
  def aliasesAreAcceptedOnRepresentativeScalaDefinitions(): Unit = {
    val surface: CompatibilitySurface[String] = new CompatibilitySurface("value")

    assertThat(surface.annotatedMethod("label")).isEqualTo("value")
    assertThat(surface ++ 41).isEqualTo(42)
    assertThat(surface.staticallyAnnotatedMethod).isEqualTo("static-marker")
    assertThat(surface.threadUnsafeLazyValue).isEqualTo(42)
    assertThat(surface.accept("native")).isEqualTo("native")
    assertThat(surface.accept2("scala2")).isEqualTo("scala2")
    assertThat(surface.accept213("scala213")).isEqualTo("scala213")
    assertThat(surface.ignoredVariance212).isEqualTo(Some("value"))
    assertThat(surface.ignoredVariance3).isEqualTo(Some("value"))
  }

  @Test
  def nowarnAliasesCanAnnotateDeprecatedCallSites(): Unit = {
    val surface: NowarnSurface = new NowarnSurface

    assertThat(surface.callWithNowarn()).isEqualTo(42)
    assertThat(surface.callWithNowarn2()).isEqualTo(42)
    assertThat(surface.callWithNowarn213()).isEqualTo(42)
    assertThat(surface.cleanScala212OnlyMethod()).isEqualTo(42)
    assertThat(surface.cleanScala3OnlyMethod()).isEqualTo(42)
  }

  @Test
  def staticAndThreadUnsafeCompatibilityAnnotationsDoNotChangeScala213Semantics(): Unit = {
    val cache: ThreadUnsafeCache = new ThreadUnsafeCache

    assertThat(AnnotatedFactory.base).isEqualTo(40)
    assertThat(AnnotatedFactory.answer()).isEqualTo(42)
    assertThat(cache.computed).isEqualTo(42)
    assertThat(cache.computed).isEqualTo(42)
  }

  @Test
  def targetNameAliasCanAnnotateOrdinaryScala213Operators(): Unit = {
    val left: TargetNamedNumber = TargetNamedNumber(40)
    val right: TargetNamedNumber = TargetNamedNumber(2)

    assertThat((left + right).value).isEqualTo(42)
    assertThat((-right).value).isEqualTo(-2)
  }

  @Test
  def uncheckedVarianceAliasesSupportCovariantApiShapes(): Unit = {
    val reader: CovariantReader[String] = new StringReader("stored")
    val widened: CovariantReader[AnyRef] = reader

    assertThat(reader.accept("direct")).isEqualTo("direct")
    assertThat(reader.accept2("scala2")).isEqualTo("scala2")
    assertThat(reader.accept213("scala213")).isEqualTo("scala213")
    assertThat(reader.ignoredVariance212).isEqualTo(Some("stored"))
    assertThat(reader.ignoredVariance3).isEqualTo(Some("stored"))
    assertThat(widened.current).isEqualTo("stored")
  }

  @Test
  def unusedAliasCanAnnotateLocalDefinitions(): Unit = {
    val surface: UnusedLocalDefinitionSurface = new UnusedLocalDefinitionSurface

    assertThat(surface.render("visible")).isEqualTo("visible:complete")
  }

  private def assertSameType[A, B]()(implicit evidence: A =:= B): Unit = {
    assertThat(evidence).isNotNull()
  }

  private final class CompatibilitySurface[+A](private val value: A) {
    @nowarn("cat=deprecation")
    @nowarn2("cat=deprecation")
    @nowarn212
    @nowarn213
    @nowarn3("ignored on Scala 2.13")
    def annotatedMethod(@unused label: String): A = value

    @targetName3("plusIntCompat")
    def ++(right: Int): Int = right + 1

    @static3
    def staticallyAnnotatedMethod: String = "static-marker"

    @threadUnsafe3
    lazy val threadUnsafeLazyValue: Int = 42

    def accept(next: A @uncheckedVariance): A = next

    def accept2(next: A @uncheckedVariance2): A = next

    def accept213(next: A @uncheckedVariance213): A = next

    def ignoredVariance212: Option[A @uncheckedVariance212] = Some(value)

    def ignoredVariance3: Option[A @uncheckedVariance3] = Some(value)
  }

  private object DeprecatedApi {
    @deprecated("exercise nowarn compatibility aliases", "0.0")
    def value: Int = 42
  }

  private final class UnusedLocalDefinitionSurface {
    def render(label: String): String = {
      @unused
      val localValue: String = "intentionally not read"

      @unused
      class LocalMarker

      s"$label:complete"
    }
  }

  private final class NowarnSurface {
    @nowarn("cat=deprecation")
    def callWithNowarn(): Int = DeprecatedApi.value

    @nowarn2("cat=deprecation")
    def callWithNowarn2(): Int = DeprecatedApi.value

    @nowarn213("cat=deprecation")
    def callWithNowarn213(): Int = DeprecatedApi.value

    @nowarn212
    def cleanScala212OnlyMethod(): Int = 42

    @nowarn3("ignored on Scala 2.13")
    def cleanScala3OnlyMethod(): Int = 42
  }

  private object AnnotatedFactory {
    @static3
    final val base: Int = 40

    @static3
    def answer(): Int = base + 2
  }

  private final class ThreadUnsafeCache {
    private var initializations: Int = 0

    @threadUnsafe3
    lazy val computed: Int = {
      initializations += 1
      initializations + 41
    }
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

    def accept2(value: A @uncheckedVariance2): A

    def accept213(value: A @uncheckedVariance213): A

    def ignoredVariance212: Option[A @uncheckedVariance212]

    def ignoredVariance3: Option[A @uncheckedVariance3]
  }

  private final class StringReader(stored: String) extends CovariantReader[String] {
    override def current: String = stored

    override def accept(value: String): String = value

    override def accept2(value: String): String = value

    override def accept213(value: String): String = value

    override def ignoredVariance212: Option[String] = Some(stored)

    override def ignoredVariance3: Option[String] = Some(stored)
  }
}
