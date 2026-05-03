/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_functional_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import play.api.libs.functional.Alternative
import play.api.libs.functional.Applicative
import play.api.libs.functional.ContravariantFunctor
import play.api.libs.functional.FunctionalCanBuild
import play.api.libs.functional.Functor
import play.api.libs.functional.InvariantFunctor
import play.api.libs.functional.Monoid
import play.api.libs.functional.Reducer
import play.api.libs.functional.~
import play.api.libs.functional.syntax._

class Play_functional_2_13Test {
  import Play_functional_2_13Test._

  @Test
  def optionFunctorAndApplicativeTransformValuesAndPropagateEmptyResults(): Unit = {
    val applicative: Applicative[Option] = Applicative.applicativeOption
    val functor: Functor[Option] = Functor.functorOption

    val mapped: Option[String] = functor.fmap(Option(21), (number: Int) => s"value-${number * 2}")
    val mappedEmpty: Option[String] = applicative.map(Option.empty[Int], (number: Int) => number.toString)
    val applied: Option[String] = applicative.apply(Option((number: Int) => s"id-$number"), Option(7))
    val notApplied: Option[String] = applicative.apply(Option((number: Int) => s"id-$number"), Option.empty[Int])

    assertThat(mapped).isEqualTo(Some("value-42"))
    assertThat(mappedEmpty).isEqualTo(None)
    assertThat(applied).isEqualTo(Some("id-7"))
    assertThat(notApplied).isEqualTo(None)
  }

  @Test
  def applicativeSyntaxBuildsProductsAndKeepsExpectedSideOfCombination(): Unit = {
    import Applicative.applicativeOption

    val user: Option[User] = (Option("Ada") and Option(36))(User.apply _)
    val missingUser: Option[User] = (Option("Ada") and Option.empty[Int])(User.apply _)
    val tupled: Option[(String, Int, Boolean)] = (Option("Grace") and Option(41) and Option(true)).tupled
    val keepRight: Option[Int] = Option("ignored") ~> Option(99)
    val keepLeft: Option[String] = Option("kept") <~ Option(100)
    val chainedFunction: Option[String] = Option((number: Int) => s"n=$number") <~> Option(5)

    assertThat(user).isEqualTo(Some(User("Ada", 36)))
    assertThat(missingUser).isEqualTo(None)
    assertThat(tupled).isEqualTo(Some(("Grace", 41, true)))
    assertThat(keepRight).isEqualTo(Some(99))
    assertThat(keepLeft).isEqualTo(Some("kept"))
    assertThat(chainedFunction).isEqualTo(Some("n=5"))
  }

  @Test
  def productBuilderCombinesUpToWideArityWithOptionApplicative(): Unit = {
    import Applicative.applicativeOption

    val sum3: Option[Int] = (Option(1) and Option(2) and Option(3))(_ + _ + _)
    val sum10: Option[Int] = (Option(1) and Option(2) and Option(3) and Option(4) and Option(5) and
      Option(6) and Option(7) and Option(8) and Option(9) and Option(10))(_ + _ + _ + _ + _ + _ + _ + _ + _ + _)
    val sum22: Option[Int] = (Option(1) and Option(2) and Option(3) and Option(4) and Option(5) and
      Option(6) and Option(7) and Option(8) and Option(9) and Option(10) and Option(11) and Option(12) and
      Option(13) and Option(14) and Option(15) and Option(16) and Option(17) and Option(18) and Option(19) and
      Option(20) and Option(21) and Option(22))(
      _ + _ + _ + _ + _ + _ + _ + _ + _ + _ + _ + _ + _ + _ + _ + _ + _ + _ + _ + _ + _ + _
    )

    assertThat(sum3).isEqualTo(Some(6))
    assertThat(sum10).isEqualTo(Some(55))
    assertThat(sum22).isEqualTo(Some(253))
  }

  @Test
  def reducerUsesProvidedMonoidForUnitPrependAppendAndBuilderReduction(): Unit = {
    import Applicative.applicativeOption

    implicit val vectorMonoid: Monoid[Vector[String]] = new Monoid[Vector[String]] {
      override def append(left: Vector[String], right: Vector[String]): Vector[String] = left ++ right
      override def identity: Vector[String] = Vector.empty
    }
    implicit val reducer: Reducer[Int, Vector[String]] = Reducer((number: Int) => Vector(s"#$number"))

    val reduced: Option[Vector[String]] = (Option(1) and Option(2) and Option(3)).reduce[Int, Vector[String]]

    assertThat(vectorMonoid.identity).isEqualTo(Vector.empty)
    assertThat(reducer.unit(4)).isEqualTo(Vector("#4"))
    assertThat(reducer.prepend(1, Vector("#2"))).isEqualTo(Vector("#1", "#2"))
    assertThat(reducer.append(Vector("#1"), 2)).isEqualTo(Vector("#1", "#2"))
    assertThat(reduced).isEqualTo(Some(Vector("#1", "#2", "#3")))
  }

  @Test
  def monoidSyntaxComposesEndomorphismsInAppendOrder(): Unit = {
    val addPrefix: String => String = (value: String) => s"user:$value"
    val emphasize: String => String = (value: String) => value.toUpperCase
    val wrap: String => String = (value: String) => s"[$value]"

    val combined: String => String = addPrefix |+| emphasize |+| wrap
    val unchanged: String => String = Monoid.endomorphismMonoid[String].identity

    assertThat(combined("ada")).isEqualTo("[USER:ADA]")
    assertThat(unchanged("grace")).isEqualTo("grace")
  }

  @Test
  def alternativeSyntaxChoosesFallbackOrEmptyValueUsingCustomAlternative(): Unit = {
    implicit val optionAlternative: Alternative[Option] = new Alternative[Option] {
      override def app: Applicative[Option] = Applicative.applicativeOption
      override def |[A, B >: A](left: Option[A], right: Option[B]): Option[B] = left.orElse(right)
      override def empty: Option[Nothing] = None
    }

    val present: Option[Int] = Option(1).or(Option(2))
    val fallback: Option[Int] = Option.empty[Int].or(Option(2))
    val empty: Option[String] = optionAlternative.empty

    assertThat(present).isEqualTo(Some(1))
    assertThat(fallback).isEqualTo(Some(2))
    assertThat(empty).isEqualTo(None)
  }

  @Test
  def contravariantFunctorMapsInputBeforeRunningValidator(): Unit = {
    val adultUserValidator: Validator[User] = Validator[Int](_ >= 18).contramap[User](_.age)
    val namedUserValidator: Validator[User] = Validator[String](_.nonEmpty).contramap[User](_.name)

    assertThat(adultUserValidator.run(User("Ada", 36))).isTrue()
    assertThat(adultUserValidator.run(User("Minor", 12))).isFalse()
    assertThat(namedUserValidator.run(User("", 36))).isFalse()
  }

  @Test
  def invariantFunctorMapsBothReadAndWriteDirections(): Unit = {
    val intCodec: Codec[Int] = Codec(_.toInt, _.toString)
    val userIdCodec: Codec[UserId] = intCodec.inmap[UserId](UserId.apply, _.value)

    assertThat(userIdCodec.read("123")).isEqualTo(UserId(123))
    assertThat(userIdCodec.write(UserId(456))).isEqualTo("456")
  }

  @Test
  def functionalBuilderCombinesInvariantCodecsBidirectionally(): Unit = {
    val stringCodec: Codec[String] = Codec(identity, identity)
    val intCodec: Codec[Int] = Codec(_.toInt, _.toString)
    val booleanCodec: Codec[Boolean] = Codec(_.toBoolean, _.toString)

    val userCodec: Codec[User] = (stringCodec and intCodec)(User.apply _, (user: User) => (user.name, user.age))
    val accountCodec: Codec[Account] = (stringCodec and intCodec and booleanCodec)(
      Account.apply _,
      (account: Account) => (account.owner, account.loginCount, account.active)
    )

    assertThat(userCodec.read("Ada|36")).isEqualTo(User("Ada", 36))
    assertThat(userCodec.write(User("Grace", 41))).isEqualTo("Grace|41")
    assertThat(accountCodec.read("root|7|true")).isEqualTo(Account("root", 7, active = true))
    assertThat(accountCodec.write(Account("guest", 0, active = false))).isEqualTo("guest|0|false")
  }

  @Test
  def functionalBuilderCombinesContravariantEncodersAndJoinsEquivalentInputs(): Unit = {
    val stringEncoder: Encoder[String] = Encoder(identity)
    val intEncoder: Encoder[Int] = Encoder(_.toString)
    val userEncoder: Encoder[User] = (stringEncoder and intEncoder)((user: User) => (user.name, user.age))
    val joinedEncoder: Encoder[String] = (stringEncoder and stringEncoder).join

    assertThat(userEncoder.write(User("Ada", 36))).isEqualTo("Ada|36")
    assertThat(joinedEncoder.write("same")).isEqualTo("same|same")
  }

  @Test
  def unliftBuildsFunctionFromOptionalResultFunction(): Unit = {
    val parseEvenNumber: String => Option[Int] = (raw: String) => raw.toIntOption.filter(_ % 2 == 0)
    val evenNumber: String => Int = unlift(parseEvenNumber)
    val rejectedOddNumber: MatchError = assertThrows(classOf[MatchError], () => evenNumber("41"))

    assertThat(evenNumber("42")).isEqualTo(42)
    assertThat(rejectedOddNumber).isInstanceOf(classOf[MatchError])
  }

  @Test
  def tildeProductExposesCaseClassOperationsAndPatternMatching(): Unit = {
    val pair: ~[Int, String] = new ~(1, "one")
    val copied: ~[Int, String] = pair.copy(_2 = "uno")
    val matched: String = pair match {
      case number ~ word => s"$number:$word"
    }

    assertThat(pair._1).isEqualTo(1)
    assertThat(pair._2).isEqualTo("one")
    assertThat(pair.productArity).isEqualTo(2)
    assertThat(pair.productElement(0)).isEqualTo(1)
    assertThat(copied).isEqualTo(new ~(1, "uno"))
    assertThat(matched).isEqualTo("1:one")
  }
}

object Play_functional_2_13Test {
  final case class User(name: String, age: Int)
  final case class Account(owner: String, loginCount: Int, active: Boolean)
  final case class UserId(value: Int)
  final case class Validator[A](run: A => Boolean)
  final case class Encoder[A](write: A => String)
  final case class Codec[A](read: String => A, write: A => String)

  implicit val validatorContravariant: ContravariantFunctor[Validator] = new ContravariantFunctor[Validator] {
    override def contramap[A, B](validator: Validator[A], inputMapper: B => A): Validator[B] =
      Validator((input: B) => validator.run(inputMapper(input)))
  }

  implicit val encoderContravariant: ContravariantFunctor[Encoder] = new ContravariantFunctor[Encoder] {
    override def contramap[A, B](encoder: Encoder[A], inputMapper: B => A): Encoder[B] =
      Encoder((input: B) => encoder.write(inputMapper(input)))
  }

  implicit val codecInvariant: InvariantFunctor[Codec] = new InvariantFunctor[Codec] {
    override def inmap[A, B](codec: Codec[A], readMapper: A => B, writeMapper: B => A): Codec[B] =
      Codec((raw: String) => readMapper(codec.read(raw)), (value: B) => codec.write(writeMapper(value)))
  }

  implicit val encoderCanBuild: FunctionalCanBuild[Encoder] = new FunctionalCanBuild[Encoder] {
    override def apply[A, B](left: Encoder[A], right: Encoder[B]): Encoder[~[A, B]] =
      Encoder((product: ~[A, B]) => s"${left.write(product._1)}|${right.write(product._2)}")
  }

  implicit val codecCanBuild: FunctionalCanBuild[Codec] = new FunctionalCanBuild[Codec] {
    override def apply[A, B](left: Codec[A], right: Codec[B]): Codec[~[A, B]] =
      Codec(
        (raw: String) => {
          val separatorIndex: Int = raw.lastIndexOf('|')
          new ~(left.read(raw.substring(0, separatorIndex)), right.read(raw.substring(separatorIndex + 1)))
        },
        (product: ~[A, B]) => s"${left.write(product._1)}|${right.write(product._2)}"
      )
  }
}
