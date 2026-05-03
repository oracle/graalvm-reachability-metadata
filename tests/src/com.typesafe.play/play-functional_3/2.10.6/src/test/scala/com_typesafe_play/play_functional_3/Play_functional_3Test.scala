/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_functional_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import play.api.libs.functional.Alternative
import play.api.libs.functional.Applicative
import play.api.libs.functional.ContravariantFunctor
import play.api.libs.functional.ContravariantFunctorExtractor
import play.api.libs.functional.FunctionalCanBuild
import play.api.libs.functional.Functor
import play.api.libs.functional.FunctorExtractor
import play.api.libs.functional.InvariantFunctor
import play.api.libs.functional.InvariantFunctorExtractor
import play.api.libs.functional.Monoid
import play.api.libs.functional.Reducer
import play.api.libs.functional.VariantExtractor
import play.api.libs.functional.{~ => Tilde}
import play.api.libs.functional.syntax._

import scala.language.implicitConversions

class Play_functional_3Test {
  @Test
  def optionApplicativeMapsPureValuesAndAppliesFunctions(): Unit = {
    import play.api.libs.functional.Applicative.applicativeOption

    val applicative: Applicative[Option] = implicitly[Applicative[Option]]

    assertEquals(Some(42), applicative.pure(f = 40 + 2))
    assertEquals(Some(5), applicative.map(Some("play!"), (value: String) => value.length))
    assertEquals(Some(14), applicative.apply(Some((value: Int) => value * 2), Some(7)))
    assertEquals(None, applicative.apply(Some((value: Int) => value * 2), Option.empty[Int]))
    assertEquals(None, applicative.apply(Option.empty[Int => Int], Some(7)))
  }

  @Test
  def applicativeSyntaxKeepsSelectedSideAndAppliesWrappedFunction(): Unit = {
    import play.api.libs.functional.Applicative.applicativeOption

    val keepRight: Option[String] = Option(1).andKeep(Option("right"))
    val keepLeft: Option[String] = Option("left").keepAnd(Option(2))
    val applied: Option[Int] = Option((value: Int) => value + 5) <~> Option(10)

    assertEquals(Some("right"), keepRight)
    assertEquals(Some("left"), keepLeft)
    assertEquals(Some(15), applied)
    assertEquals(None, Option.empty[Int].andKeep(Option("missing left")))
    assertEquals(None, Option("missing right").keepAnd(Option.empty[Int]))
  }

  @Test
  def optionFunctionalBuilderCombinesTuplesAndReducers(): Unit = {
    import play.api.libs.functional.Applicative.applicativeOption
    import play.api.libs.functional.Functor.functorOption

    implicit val stringMonoid: Monoid[String] = new Monoid[String] {
      override def append(first: String, second: String): String = s"$first/$second"
      override def identity: String = ""
    }
    implicit val stringReducer: Reducer[String, String] = Reducer[String, String]((value: String) => value)

    val fullName: Option[String] = (Option("Ada") and Option("Lovelace")) { (first: String, last: String) =>
      s"$first $last"
    }
    val tupled: Option[(Int, String, Boolean)] = (Option(1) and Option("two") and Option(true)).tupled
    val reduced: Option[String] = (Option("red") and Option("green") and Option("blue")).reduce[String, String]
    val missing: Option[Int] = (Option(1) and Option.empty[Int]) { (first: Int, second: Int) => first + second }

    assertEquals(Some("Ada Lovelace"), fullName)
    assertEquals(Some((1, "two", true)), tupled)
    assertEquals(Some("red/green/blue"), reduced)
    assertEquals(None, missing)
  }

  @Test
  def functionalBuilderCombinesMaximumSupportedArity(): Unit = {
    import play.api.libs.functional.Applicative.applicativeOption
    import play.api.libs.functional.Functor.functorOption

    val total: Option[Int] = (Option(1)
      .and(Option(2))
      .and(Option(3))
      .and(Option(4))
      .and(Option(5))
      .and(Option(6))
      .and(Option(7))
      .and(Option(8))
      .and(Option(9))
      .and(Option(10))
      .and(Option(11))
      .and(Option(12))
      .and(Option(13))
      .and(Option(14))
      .and(Option(15))
      .and(Option(16))
      .and(Option(17))
      .and(Option(18))
      .and(Option(19))
      .and(Option(20))
      .and(Option(21))
      .and(Option(22))) {
      (
          n1: Int,
          n2: Int,
          n3: Int,
          n4: Int,
          n5: Int,
          n6: Int,
          n7: Int,
          n8: Int,
          n9: Int,
          n10: Int,
          n11: Int,
          n12: Int,
          n13: Int,
          n14: Int,
          n15: Int,
          n16: Int,
          n17: Int,
          n18: Int,
          n19: Int,
          n20: Int,
          n21: Int,
          n22: Int
      ) =>
        List(
          n1,
          n2,
          n3,
          n4,
          n5,
          n6,
          n7,
          n8,
          n9,
          n10,
          n11,
          n12,
          n13,
          n14,
          n15,
          n16,
          n17,
          n18,
          n19,
          n20,
          n21,
          n22
        ).sum
    }

    assertEquals(Some(253), total)
  }

  @Test
  def monoidSyntaxAndReducerComposeDeterministically(): Unit = {
    import play.api.libs.functional.Monoid.endomorphismMonoid

    implicit val intAdditionMonoid: Monoid[Int] = new Monoid[Int] {
      override def append(first: Int, second: Int): Int = first + second
      override def identity: Int = 0
    }

    val composed: Int => Int = ((value: Int) => value + 2) |+| ((value: Int) => value * 3)
    val reducer: Reducer[Int, Int] = Reducer[Int, Int]((value: Int) => value * 2)

    assertEquals(18, composed(4))
    assertEquals(11, endomorphismMonoid[Int].identity(11))
    assertEquals(6, reducer.unit(3))
    assertEquals(18, reducer.prepend(4, 10))
    assertEquals(18, reducer.append(10, 4))
  }

  @Test
  def functorSyntaxSupportsCovariantContravariantAndInvariantMapping(): Unit = {
    implicit val readerFunctor: Functor[Reader] = new Functor[Reader] {
      override def fmap[A, B](reader: Reader[A], f: A => B): Reader[B] = Reader(input => f(reader.read(input)))
    }
    implicit val printerContravariant: ContravariantFunctor[Printer] = new ContravariantFunctor[Printer] {
      override def contramap[A, B](printer: Printer[A], f: B => A): Printer[B] = Printer(value => printer.print(f(value)))
    }
    implicit val codecInvariant: InvariantFunctor[Codec] = new InvariantFunctor[Codec] {
      override def inmap[A, B](codec: Codec[A], f1: A => B, f2: B => A): Codec[B] =
        Codec(input => f1(codec.parse(input)), value => codec.print(f2(value)))
    }

    val labelledReader: Reader[String] = Reader((input: String) => input.toInt).fmap(value => s"value=$value")
    val orderPrinter: Printer[Order] = Printer((quantity: Int) => s"#$quantity").contramap((order: Order) => order.quantity)
    val dollarsCodec: Codec[BigDecimal] = Codec((input: String) => input.toInt, (value: Int) => value.toString)
      .inmap(cents => BigDecimal(cents) / 100, dollars => (dollars * 100).toInt)

    assertEquals("value=12", labelledReader.read("12"))
    assertEquals("#3", orderPrinter.print(Order("tea", 3)))
    assertEquals(BigDecimal("12.34"), dollarsCodec.parse("1234"))
    assertEquals("987", dollarsCodec.print(BigDecimal("9.87")))
  }

  @Test
  def alternativeSyntaxSelectsFallbackAndExposesApplicative(): Unit = {
    import play.api.libs.functional.Applicative.applicativeOption

    implicit val optionAlternative: Alternative[Option] = new Alternative[Option] {
      override def app: Applicative[Option] = applicativeOption
      override def |[A, B >: A](alt1: Option[A], alt2: Option[B]): Option[B] = alt1.orElse(alt2)
      override def empty: Option[Nothing] = None
    }

    assertEquals(Some("primary"), Option("primary") | Option("fallback"))
    assertEquals(Some("fallback"), Option.empty[String].or(Option("fallback")))
    assertEquals(None, optionAlternative.empty)
    assertEquals(Some("from-applicative"), optionAlternative.app.pure(f = "from-applicative"))
  }

  @Test
  def variantExtractorsClassifyAndExposeWrappedFunctorInstances(): Unit = {
    val readerFunctor: Functor[Reader] = new Functor[Reader] {
      override def fmap[A, B](reader: Reader[A], f: A => B): Reader[B] = Reader(input => f(reader.read(input)))
    }
    val printerContravariant: ContravariantFunctor[Printer] = new ContravariantFunctor[Printer] {
      override def contramap[A, B](printer: Printer[A], f: B => A): Printer[B] = Printer(value => printer.print(f(value)))
    }
    val codecInvariant: InvariantFunctor[Codec] = new InvariantFunctor[Codec] {
      override def inmap[A, B](codec: Codec[A], f1: A => B, f2: B => A): Codec[B] =
        Codec(input => f1(codec.parse(input)), value => codec.print(f2(value)))
    }

    val readerExtractor: VariantExtractor[Reader] = VariantExtractor.functor(readerFunctor)
    val printerExtractor: VariantExtractor[Printer] = VariantExtractor.contravariantFunctor(printerContravariant)
    val codecExtractor: VariantExtractor[Codec] = VariantExtractor.invariantFunctor(codecInvariant)

    val readerResult: String = readerExtractor match {
      case FunctorExtractor(functor) =>
        functor.fmap(Reader((input: String) => input.length), (length: Int) => s"chars=$length").read("play")
    }
    val printerResult: String = printerExtractor match {
      case ContravariantFunctorExtractor(contravariantFunctor) =>
        val printer: Printer[Order] = contravariantFunctor.contramap(Printer((quantity: Int) => s"qty=$quantity"), (order: Order) => order.quantity)
        printer.print(Order("coffee", 4))
    }
    val codecResult: String = codecExtractor match {
      case InvariantFunctorExtractor(invariantFunctor) =>
        val codec: Codec[Boolean] = invariantFunctor.inmap(
          Codec((input: String) => input.toInt, (value: Int) => value.toString),
          (value: Int) => value > 0,
          (value: Boolean) => if (value) 1 else 0
        )
        s"${codec.parse("7")}/${codec.print(false)}"
    }

    assertEquals("chars=4", readerResult)
    assertEquals("qty=4", printerResult)
    assertEquals("true/0", codecResult)
  }

  @Test
  def productTypeBehavesLikeCaseClassAndSupportsPatternMatching(): Unit = {
    val pair: Tilde[Int, String] = Tilde(7, "seven")
    val copied: Tilde[Int, String] = pair.copy(_2 = "VII")
    val description: String = pair match {
      case Tilde(number, word) => s"$number is $word"
    }

    assertEquals(7, pair._1)
    assertEquals("seven", pair._2)
    assertEquals(Tilde(7, "seven"), pair)
    assertFalse(pair == copied)
    assertEquals("VII", copied._2)
    assertEquals("7 is seven", description)
    assertTrue(pair.productElementNames.toList.contains("_1"))
  }

  @Test
  def functionalBuilderUsesContravariantTupledAndJoin(): Unit = {
    implicit val printerContravariant: ContravariantFunctor[Printer] = new ContravariantFunctor[Printer] {
      override def contramap[A, B](printer: Printer[A], f: B => A): Printer[B] = Printer(value => printer.print(f(value)))
    }
    implicit val printerCanBuild: FunctionalCanBuild[Printer] = new FunctionalCanBuild[Printer] {
      override def apply[A, B](first: Printer[A], second: Printer[B]): Printer[Tilde[A, B]] =
        Printer(pair => s"${first.print(pair._1)}:${second.print(pair._2)}")
    }

    val tuplePrinter: Printer[(Int, String)] = (Printer((value: Int) => value.toString) and Printer(identity[String])).tupled
    val joinedPrinter: Printer[String] = (Printer((value: String) => s"'$value'") and Printer((value: String) => value.toUpperCase)).join[String]

    assertEquals("7:days", tuplePrinter.print((7, "days")))
    assertEquals("'play':PLAY", joinedPrinter.print("play"))
  }

  @Test
  def functionalBuilderUsesInvariantApplyAndTupled(): Unit = {
    implicit val sampleInvariant: InvariantFunctor[Sample] = new InvariantFunctor[Sample] {
      override def inmap[A, B](sample: Sample[A], f1: A => B, f2: B => A): Sample[B] =
        Sample(f1(sample.value), value => sample.render(f2(value)))
    }
    implicit val sampleCanBuild: FunctionalCanBuild[Sample] = new FunctionalCanBuild[Sample] {
      override def apply[A, B](first: Sample[A], second: Sample[B]): Sample[Tilde[A, B]] =
        Sample(new Tilde(first.value, second.value), pair => s"${first.render(pair._1)} ${second.render(pair._2)}")
    }

    val summary: Sample[String] = (Sample(2, (value: Int) => value.toString) and Sample("cats", identity[String]))(
      (count: Int, noun: String) => s"$count $noun",
      (summary: String) => {
        val parts: Array[String] = summary.split(" ", 2)
        (parts(0).toInt, parts(1))
      }
    )
    val tupled: Sample[(Int, String)] = (Sample(1, (value: Int) => s"n=$value") and Sample("dog", (value: String) => s"word=$value")).tupled

    assertEquals("2 cats", summary.value)
    assertEquals("5 mice", summary.render("5 mice"))
    assertEquals((1, "dog"), tupled.value)
    assertEquals("n=4 word=birds", tupled.render((4, "birds")))
  }

  private final case class Reader[A](read: String => A)

  private final case class Printer[A](print: A => String)

  private final case class Codec[A](parse: String => A, print: A => String)

  private final case class Sample[A](value: A, render: A => String)

  private final case class Order(item: String, quantity: Int)
}
