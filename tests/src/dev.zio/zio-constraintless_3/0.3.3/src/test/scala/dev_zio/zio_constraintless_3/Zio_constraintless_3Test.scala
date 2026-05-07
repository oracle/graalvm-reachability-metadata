/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_constraintless_3

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import zio.constraintless.AreElementsOf
import zio.constraintless.Instances
import zio.constraintless.IsElementOf
import zio.constraintless.TypeList.End
import zio.constraintless.TypeList.::

class Zio_constraintless_3Test {
  trait Label[A] {
    def label(value: A): String
  }

  private given Label[String] with {
    override def label(value: String): String = s"string:$value"
  }

  private given Label[Int] with {
    override def label(value: Int): String = s"int:$value"
  }

  private given Label[Boolean] with {
    override def label(value: Boolean): String = s"boolean:$value"
  }

  @Test
  def resolvesElementEvidenceForHeadAndNestedTailPositions(): Unit = {
    val headEvidence: IsElementOf[String, String :: Int :: Boolean :: End] =
      IsElementOf[String, String :: Int :: Boolean :: End]
    val tailEvidence: IsElementOf[Boolean, String :: Int :: Boolean :: End] =
      IsElementOf[Boolean, String :: Int :: Boolean :: End]

    assertThat(describeElementEvidence(headEvidence)).isEqualTo("head")
    assertThat(describeElementEvidence(tailEvidence)).isEqualTo("tail(tail(head))")
  }

  @Test
  def resolvesEvidenceThatEveryRequestedTypeExistsInLargerTypeList(): Unit = {
    val evidence: AreElementsOf[Boolean :: String :: End, String :: Int :: Boolean :: End] =
      AreElementsOf[Boolean :: String :: End, String :: Int :: Boolean :: End]

    evidence match {
      case AreElementsOf.TypeCollection(booleanEvidence, AreElementsOf.TypeCollection(stringEvidence, AreElementsOf.NilCollection())) =>
        assertThat(describeElementEvidence(booleanEvidence)).isEqualTo("tail(tail(head))")
        assertThat(describeElementEvidence(stringEvidence)).isEqualTo("head")
      case other => fail(s"Expected a two-element type collection followed by NilCollection, got $other")
    }
  }

  @Test
  def resolvesEmptyRequestedTypeListAgainstAnyAvailableTypeList(): Unit = {
    val evidence: AreElementsOf[End, String :: Int :: End] =
      AreElementsOf[End, String :: Int :: End]

    evidence match {
      case nil @ AreElementsOf.NilCollection() =>
        assertThat(nil).isEqualTo(AreElementsOf.NilCollection[String :: Int :: End]())
      case other => fail(s"Expected NilCollection evidence for an empty requested type list, got $other")
    }
  }

  @Test
  def instancesSelectsTypeClassForHeadMiddleAndTailElements(): Unit = {
    type Supported = String :: Int :: Boolean :: End
    val instances: Instances[Label, Supported] = summon[Instances[Label, Supported]]

    assertThat(instances.withInstance[String, String](_.label("zio"))).isEqualTo("string:zio")
    assertThat(instances.withInstance[Int, String](_.label(42))).isEqualTo("int:42")
    assertThat(instances.withInstance[Boolean, String](_.label(false))).isEqualTo("boolean:false")
  }

  @Test
  def instancesCanProduceValuesWithDifferentResultTypes(): Unit = {
    type Supported = String :: Boolean :: End
    val instances: Instances[Label, Supported] = summon[Instances[Label, Supported]]

    val renderedLength: Int = instances.withInstance[String, Int](_.label("constraintless").length)
    val containsBooleanPrefix: Boolean = instances.withInstance[Boolean, Boolean](_.label(true).startsWith("boolean:"))

    assertThat(renderedLength).isEqualTo("string:constraintless".length)
    assertThat(containsBooleanPrefix).isTrue()
  }

  private def describeElementEvidence[A, As <: zio.constraintless.TypeList](
      evidence: IsElementOf[A, As]
  ): String =
    evidence match {
      case IsElementOf.Head() => "head"
      case IsElementOf.Tail(inner) => s"tail(${describeElementEvidence(inner)})"
    }
}
