/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.twiddles_core_3

import cats.Applicative
import cats.implicits.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.typelevel.twiddles.*

import scala.language.implicitConversions

final case class Profile(id: Int, name: String)
final case class Label(value: String)
final case class UserId(value: Int)
final case class UserKey(value: String)

class Twiddles_core_3Test {
  @Test
  def syntaxObjectBuildsTuplesWithInvariantSemigroupalInstances(): Unit = {
    import org.typelevel.twiddles.syntax.*

    val id: Option[Int] = Some(7)
    val name: Option[String] = Some("Ada")
    val active: Option[Boolean] = Some(true)

    val pair: Option[Int *: String *: EmptyTuple] = id *: name
    val triple: Option[Int *: (String *: Boolean *: EmptyTuple) *: EmptyTuple] = id *: name *: active
    val missingId: Option[Int] = None
    val missingPair: Option[Int *: String *: EmptyTuple] = missingId *: name

    assertThat(pair).isEqualTo(Some(7 *: "Ada" *: EmptyTuple))
    assertThat(triple).isEqualTo(Some(7 *: "Ada" *: true *: EmptyTuple))
    assertThat(missingPair).isEqualTo(None)
  }

  @Test
  def syntaxObjectConvertsBetweenTuplesAndProducts(): Unit = {
    import org.typelevel.twiddles.syntax.*

    val tupled: Option[Int *: String *: EmptyTuple] = Some(11 *: "Grace" *: EmptyTuple)
    val profile: Option[Profile] = tupled.to[Profile]
    val backToTuple: Option[Int *: String *: EmptyTuple] = profile.to[Int *: String *: EmptyTuple]
    val singleton: Option[Label] = Option("primary").to[Label]

    assertThat(profile).isEqualTo(Some(Profile(11, "Grace")))
    assertThat(backToTuple).isEqualTo(tupled)
    assertThat(singleton).isEqualTo(Some(Label("primary")))
  }

  @Test
  def dropUnitsRemovesAndReinsertsUnitElements(): Unit = {
    type WithUnits = Int *: Unit *: String *: Unit *: EmptyTuple
    type WithoutUnits = Int *: String *: EmptyTuple

    val expanded: WithUnits = 42 *: () *: "Hedy" *: () *: EmptyTuple
    val compact: WithoutUnits = DropUnits.drop[WithUnits](expanded)
    val restored: WithUnits = DropUnits.insert[WithUnits](compact)
    val empty: EmptyTuple = DropUnits.drop[EmptyTuple](EmptyTuple)

    assertThat(compact).isEqualTo(42 *: "Hedy" *: EmptyTuple)
    assertThat(restored).isEqualTo(expanded)
    assertThat(empty).isEqualTo(EmptyTuple)
  }

  @Test
  def isoInstancesHandleIdentityProductsSingletonsInverseAndUnitDropping(): Unit = {
    type ProfileTuple = Int *: String *: EmptyTuple
    type ProfileTupleWithUnits = Int *: Unit *: String *: EmptyTuple

    val identityIso: Iso[Int, Int] = Iso[Int, Int]
    val productIso: Iso[Profile, ProfileTuple] = Iso.product[Profile]
    val tupleToProductIso: Iso[ProfileTuple, Profile] = Iso[ProfileTuple, Profile]
    val singletonIso: Iso[String, Label] = Iso[String, Label]
    val inverseProductIso: Iso[ProfileTuple, Profile] = productIso.inverse
    val unitDroppingIso: Iso[ProfileTupleWithUnits, Profile] = Iso[ProfileTupleWithUnits, Profile]

    assertThat(identityIso.to(5)).isEqualTo(5)
    assertThat(identityIso.from(6)).isEqualTo(6)
    assertThat(productIso.to(Profile(1, "Barbara"))).isEqualTo(1 *: "Barbara" *: EmptyTuple)
    assertThat(productIso.from(2 *: "Evelyn" *: EmptyTuple)).isEqualTo(Profile(2, "Evelyn"))
    assertThat(tupleToProductIso.to(3 *: "Katherine" *: EmptyTuple)).isEqualTo(Profile(3, "Katherine"))
    assertThat(tupleToProductIso.from(Profile(4, "Margaret"))).isEqualTo(4 *: "Margaret" *: EmptyTuple)
    assertThat(singletonIso.to("main")).isEqualTo(Label("main"))
    assertThat(singletonIso.from(Label("secondary"))).isEqualTo("secondary")
    assertThat(inverseProductIso.to(8 *: "Dorothy" *: EmptyTuple)).isEqualTo(Profile(8, "Dorothy"))
    assertThat(unitDroppingIso.to(9 *: () *: "Mary" *: EmptyTuple)).isEqualTo(Profile(9, "Mary"))
    assertThat(unitDroppingIso.from(Profile(10, "Annie"))).isEqualTo(10 *: () *: "Annie" *: EmptyTuple)
  }

  @Test
  def isoInstanceSupportsCustomBidirectionalConversions(): Unit = {
    import org.typelevel.twiddles.syntax.*

    val userKeyIso: Iso[UserId, UserKey] = Iso.instance[UserId, UserKey](id => UserKey(s"user-${id.value}"))(
      key => UserId(key.value.stripPrefix("user-").toInt)
    )
    given Iso[UserId, UserKey] = userKeyIso

    val userId: Option[UserId] = Some(UserId(101))
    val userKey: Option[UserKey] = userId.to[UserKey]
    val restoredUserId: UserId = userKeyIso.from(UserKey("user-202"))

    assertThat(userKey).isEqualTo(Some(UserKey("user-101")))
    assertThat(restoredUserId).isEqualTo(UserId(202))
  }

  @Test
  def syntaxSupportsContravariantOrderingComposition(): Unit = {
    import org.typelevel.twiddles.syntax.*

    val profileOrdering: Ordering[Profile] =
      (summon[Ordering[Int]] *: summon[Ordering[String]]).to[Profile]

    assertThat(profileOrdering.compare(Profile(1, "Ada"), Profile(2, "Ada"))).isLessThan(0)
    assertThat(profileOrdering.compare(Profile(2, "Ada"), Profile(2, "Grace"))).isLessThan(0)
    assertThat(profileOrdering.compare(Profile(2, "Grace"), Profile(2, "Ada"))).isGreaterThan(0)
    assertThat(profileOrdering.compare(Profile(2, "Grace"), Profile(2, "Grace"))).isEqualTo(0)
  }

  @Test
  def twiddleSyntaxComposesPublicDecoderApi(): Unit = {
    import TestDecoder.*

    val profileDecoder: TestDecoder[Profile] =
      (intField("id") *: unit *: stringField("name") *: unit).to[Profile]
    val compactDecoder: TestDecoder[Int *: String *: EmptyTuple] =
      (intField("id") *: unit *: stringField("name") *: unit).dropUnits
    val labelDecoder: TestDecoder[Label] = stringField("label").to[Label]

    val input: Map[String, String] = Map("id" -> "12", "name" -> "Radia", "label" -> "network")
    val invalidInput: Map[String, String] = Map("id" -> "not-a-number", "name" -> "Radia")

    assertThat(profileDecoder.decode(input)).isEqualTo(Some(Profile(12, "Radia")))
    assertThat(compactDecoder.decode(input)).isEqualTo(Some(12 *: "Radia" *: EmptyTuple))
    assertThat(labelDecoder.decode(input)).isEqualTo(Some(Label("network")))
    assertThat(profileDecoder.decode(invalidInput)).isEqualTo(None)
  }
}

trait TestDecoder[A] {
  def decode(input: Map[String, String]): Option[A]
}

object TestDecoder extends TwiddleSyntax[TestDecoder] {
  implicit val applicative: Applicative[TestDecoder] = new Applicative[TestDecoder] {
    override def pure[A](value: A): TestDecoder[A] = _ => Some(value)

    override def ap[A, B](functionDecoder: TestDecoder[A => B])(valueDecoder: TestDecoder[A]): TestDecoder[B] =
      input =>
        for {
          function <- functionDecoder.decode(input)
          value <- valueDecoder.decode(input)
        } yield function(value)
  }

  val unit: TestDecoder[Unit] = applicative.pure(())

  def intField(name: String): TestDecoder[Int] =
    input => input.get(name).flatMap(_.toIntOption)

  def stringField(name: String): TestDecoder[String] =
    input => input.get(name)
}
