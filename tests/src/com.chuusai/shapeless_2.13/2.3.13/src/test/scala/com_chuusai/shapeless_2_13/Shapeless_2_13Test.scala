/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_chuusai.shapeless_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import shapeless.CNil
import shapeless.Coproduct
import shapeless.Generic
import shapeless.HList
import shapeless.HNil
import shapeless.Inl
import shapeless.Inr
import shapeless.Lazy
import shapeless.Nat
import shapeless.Poly1
import shapeless.Sized
import shapeless.::
import shapeless.:+:
import shapeless.lens
import shapeless.nat._
import shapeless.record._
import shapeless.syntax.singleton._
import shapeless.syntax.sized._
import shapeless.syntax.std.traversable._

class Shapeless_2_13Test {
  import Shapeless_2_13Test._

  @Test
  def genericRoundTripConvertsProductsAndCoproducts(): Unit = {
    val personGeneric: Generic.Aux[Person, String :: Int :: Boolean :: HNil] = Generic[Person]
    val person: Person = Person("Ada", 36, active = true)
    val representation: String :: Int :: Boolean :: HNil = personGeneric.to(person)

    assertThat(representation).isEqualTo("Ada" :: 36 :: true :: HNil)
    assertThat(personGeneric.from("Grace" :: 85 :: false :: HNil)).isEqualTo(Person("Grace", 85, active = false))

    val shapeGeneric = Generic[Shape]
    val circle = shapeGeneric.to(Circle(2.5))
    val rectangle = shapeGeneric.to(Rectangle(3, 4))

    assertThat(circle.select[Circle]).isEqualTo(Some(Circle(2.5)))
    assertThat(rectangle.select[Rectangle]).isEqualTo(Some(Rectangle(3, 4)))
    assertThat(shapeGeneric.from(rectangle)).isEqualTo(Rectangle(3, 4))
  }

  @Test
  def hlistOperationsTransformSelectAndReorderValues(): Unit = {
    object ToDescription extends Poly1 {
      implicit val intCase: Case.Aux[Int, String] = at[Int](value => s"int:$value")
      implicit val stringCase: Case.Aux[String, String] = at[String](value => s"string:$value")
      implicit val booleanCase: Case.Aux[Boolean, String] = at[Boolean](value => s"boolean:$value")
    }

    val values: Int :: String :: Boolean :: HNil = 23 :: "shapeless" :: true :: HNil
    val descriptions: String :: String :: String :: HNil = values.map(ToDescription)
    val reordered: Boolean :: Int :: String :: HNil = values.align[Boolean :: Int :: String :: HNil]

    assertThat(values.head).isEqualTo(23)
    assertThat(values.tail).isEqualTo("shapeless" :: true :: HNil)
    assertThat(values.select[String]).isEqualTo("shapeless")
    assertThat(descriptions).isEqualTo("int:23" :: "string:shapeless" :: "boolean:true" :: HNil)
    assertThat(reordered).isEqualTo(true :: 23 :: "shapeless" :: HNil)
    assertThat(values.reverse).isEqualTo(true :: "shapeless" :: 23 :: HNil)
  }

  @Test
  def recordsSupportTypedFieldAccessUpdateRemovalAndExtension(): Unit = {
    val record =
      ("name" ->> "Babbage") ::
        ("language" ->> "Scala") ::
        ("year" ->> 1837) ::
        HNil

    val updated = record.updated("language", "shapeless")
    val extended = updated + ("typed" ->> true)
    val (removedYear, withoutYear) = extended.remove("year")

    assertThat(record.get("name")).isEqualTo("Babbage")
    assertThat(updated.get("language")).isEqualTo("shapeless")
    assertThat(extended.get("typed")).isEqualTo(true)
    assertThat(removedYear).isEqualTo(1837)
    assertThat(withoutYear.get("name")).isEqualTo("Babbage")
    assertThat(withoutYear.get("language")).isEqualTo("shapeless")
    assertThat(withoutYear.get("typed")).isEqualTo(true)
  }

  @Test
  def sizedCollectionsAndNatsCarryLengthInformationAtRuntime(): Unit = {
    val sized: Sized[List[Int], _4] = Sized[List](2, 4, 6, 8)
    val strings: Sized[Vector[String], _3] = Sized[Vector]("a", "b", "c")

    assertThat(Nat.toInt[_4]).isEqualTo(4)
    assertThat(sized.unsized).isEqualTo(List(2, 4, 6, 8))
    assertThat(sized.map(_ / 2).unsized).isEqualTo(List(1, 2, 3, 4))
    assertThat(strings.unsized).isEqualTo(Vector("a", "b", "c"))
    assertThat(strings.head).isEqualTo("a")
    assertThat(strings.last).isEqualTo("c")
  }

  @Test
  def coproductsSelectMapUnifyAndFoldAlternatives(): Unit = {
    object Normalize extends Poly1 {
      implicit val intCase: Case.Aux[Int, String] = at[Int](value => s"number:$value")
      implicit val stringCase: Case.Aux[String, String] = at[String](identity)
      implicit val booleanCase: Case.Aux[Boolean, String] = at[Boolean](value => if (value) "yes" else "no")
    }

    val number: Int :+: String :+: Boolean :+: CNil = Coproduct[Int :+: String :+: Boolean :+: CNil](7)
    val text: Int :+: String :+: Boolean :+: CNil = Coproduct[Int :+: String :+: Boolean :+: CNil]("value")
    val flag: Int :+: String :+: Boolean :+: CNil = Coproduct[Int :+: String :+: Boolean :+: CNil](true)

    assertThat(number.select[Int]).isEqualTo(Some(7))
    assertThat(number.select[String]).isEqualTo(None)
    assertThat(text.map(Normalize).unify).isEqualTo("value")
    assertThat(flag.map(Normalize).unify).isEqualTo("yes")
    assertThat(number.map(Normalize).unify).isEqualTo("number:7")
  }

  @Test
  def genericEncoderDerivesInstancesForNestedProductsAndCoproducts(): Unit = {
    val personEncoder: DelimitedEncoder[Person] = implicitly[DelimitedEncoder[Person]]
    val eventEncoder: DelimitedEncoder[Event] = implicitly[DelimitedEncoder[Event]]

    assertThat(personEncoder.encode(Person("Katherine", 99, active = true))).isEqualTo(List("Katherine", "99", "true"))
    assertThat(eventEncoder.encode(Login(Person("Margaret", 87, active = false), "console")))
      .isEqualTo(List("Margaret", "87", "false", "console"))
    assertThat(eventEncoder.encode(Logout(Person("Evelyn", 94, active = true), 30)))
      .isEqualTo(List("Evelyn", "94", "true", "30"))
  }

  @Test
  def traversableConversionUsesTypeableEvidenceForRuntimeSafeHlists(): Unit = {
    val matching: Option[Int :: String :: Double :: HNil] = List(1, "two", 3.0).toHList[Int :: String :: Double :: HNil]
    val mismatched: Option[Int :: String :: Double :: HNil] = List(1, 2, 3.0).toHList[Int :: String :: Double :: HNil]
    val tooShort: Option[Int :: String :: Double :: HNil] = List(1, "two").toHList[Int :: String :: Double :: HNil]

    assertThat(matching).isEqualTo(Some(1 :: "two" :: 3.0 :: HNil))
    assertThat(mismatched).isEqualTo(None)
    assertThat(tooShort).isEqualTo(None)
  }

  @Test
  def lensesReadAndUpdateNestedProductFields(): Unit = {
    val profile: Profile = Profile(Person("Dorothy", 97, active = true), Address("Arlington", "Virginia"))
    val ownerNameLens = lens[Profile].owner.name
    val cityLens = lens[Profile].address.city

    val moved: Profile = cityLens.set(profile)("Hampton")
    val renamed: Profile = ownerNameLens.modify(moved)(_.toUpperCase)

    assertThat(ownerNameLens.get(profile)).isEqualTo("Dorothy")
    assertThat(cityLens.get(profile)).isEqualTo("Arlington")
    assertThat(moved).isEqualTo(Profile(Person("Dorothy", 97, active = true), Address("Hampton", "Virginia")))
    assertThat(renamed).isEqualTo(Profile(Person("DOROTHY", 97, active = true), Address("Hampton", "Virginia")))
  }
}

object Shapeless_2_13Test {
  final case class Person(name: String, age: Int, active: Boolean)
  final case class Address(city: String, state: String)
  final case class Profile(owner: Person, address: Address)

  sealed trait Shape
  final case class Circle(radius: Double) extends Shape
  final case class Rectangle(width: Int, height: Int) extends Shape

  sealed trait Event
  final case class Login(person: Person, source: String) extends Event
  final case class Logout(person: Person, durationSeconds: Int) extends Event

  trait DelimitedEncoder[A] {
    def encode(value: A): List[String]
  }

  implicit val stringEncoder: DelimitedEncoder[String] = value => List(value)
  implicit val intEncoder: DelimitedEncoder[Int] = value => List(value.toString)
  implicit val booleanEncoder: DelimitedEncoder[Boolean] = value => List(value.toString)
  implicit val hnilEncoder: DelimitedEncoder[HNil] = _ => Nil
  implicit val cnilEncoder: DelimitedEncoder[CNil] = _ => Nil

  implicit def hlistEncoder[H, T <: HList](implicit
    headEncoder: Lazy[DelimitedEncoder[H]],
    tailEncoder: DelimitedEncoder[T]
  ): DelimitedEncoder[H :: T] = value => headEncoder.value.encode(value.head) ++ tailEncoder.encode(value.tail)

  implicit def coproductEncoder[H, T <: Coproduct](implicit
    headEncoder: Lazy[DelimitedEncoder[H]],
    tailEncoder: DelimitedEncoder[T]
  ): DelimitedEncoder[H :+: T] = {
    case Inl(head) => headEncoder.value.encode(head)
    case Inr(tail) => tailEncoder.encode(tail)
  }

  implicit def genericEncoder[A, R](implicit
    generic: Generic.Aux[A, R],
    encoder: Lazy[DelimitedEncoder[R]]
  ): DelimitedEncoder[A] = value => encoder.value.encode(generic.to(value))
}
