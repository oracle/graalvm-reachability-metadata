/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_functional_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.functional._
import play.api.libs.functional.syntax._

import scala.jdk.CollectionConverters._

class Play_functional_3Test {
  @Test
  def optionFunctorAndApplicativeSyntaxComposeValues(): Unit = {
    val mapped: Option[Int] = Option(21).fmap(_ * 2)
    val keptRight: Option[String] = Option("validated") ~> Option("payload")
    val keptLeft: Option[String] = Option("validated") <~ Option("payload")
    val appliedFunction: Option[Int] = Option((value: Int) => value + 5) <~> Option(37)

    val app: Applicative[Option] = implicitly[Applicative[Option]]
    val pureValue: Option[String] = app.pure(f = "created lazily")
    val missingInput: Option[String] = Option.empty[String] ~> Option("not reached")

    assertThat(mapped).isEqualTo(Some(42))
    assertThat(keptRight).isEqualTo(Some("payload"))
    assertThat(keptLeft).isEqualTo(Some("validated"))
    assertThat(appliedFunction).isEqualTo(Some(42))
    assertThat(pureValue).isEqualTo(Some("created lazily"))
    assertThat(missingInput).isEqualTo(None)
  }

  @Test
  def optionProductBuildersMapTupleAndReduceComposedValues(): Unit = {
    val name: Option[FullName] = (Option("Ada") ~ Option("Lovelace")).apply { (first: String, last: String) =>
      FullName(first, last)
    }
    val tuple3: Option[(Int, String, Boolean)] = (Option(1) ~ Option("two") ~ Option(true)).tupled
    val aliasResult: Option[String] = (Option("left").and(Option("right"))).apply { (left: String, right: String) =>
      s"$left-$right"
    }
    val noneResult: Option[(Int, String)] = (Option(1) ~ Option.empty[String]).tupled

    implicit val intVectorMonoid: Monoid[Vector[Int]] = new Monoid[Vector[Int]] {
      override def append(a1: Vector[Int], a2: Vector[Int]): Vector[Int] = a1 ++ a2
      override def identity: Vector[Int] = Vector.empty
    }
    implicit val intVectorReducer: Reducer[Int, Vector[Int]] = Reducer[Int, Vector[Int]](value => Vector(value))
    val numbers =
      Option(1) ~ Option(2) ~ Option(3) ~ Option(4) ~ Option(5) ~ Option(6) ~ Option(7) ~ Option(8) ~ Option(9) ~ Option(10) ~
        Option(11) ~ Option(12) ~ Option(13) ~ Option(14) ~ Option(15) ~ Option(16) ~ Option(17) ~ Option(18) ~ Option(19) ~
        Option(20) ~ Option(21) ~ Option(22)
    val sum: Option[Int] = numbers.apply {
      (
          a1: Int,
          a2: Int,
          a3: Int,
          a4: Int,
          a5: Int,
          a6: Int,
          a7: Int,
          a8: Int,
          a9: Int,
          a10: Int,
          a11: Int,
          a12: Int,
          a13: Int,
          a14: Int,
          a15: Int,
          a16: Int,
          a17: Int,
          a18: Int,
          a19: Int,
          a20: Int,
          a21: Int,
          a22: Int
      ) =>
        List(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20, a21, a22).sum
    }
    val reduced: Option[Vector[Int]] = numbers.reduce[Int, Vector[Int]]

    assertThat(name).isEqualTo(Some(FullName("Ada", "Lovelace")))
    assertThat(tuple3).isEqualTo(Some((1, "two", true)))
    assertThat(aliasResult).isEqualTo(Some("left-right"))
    assertThat(noneResult).isEqualTo(None)
    assertThat(sum).isEqualTo(Some(253))
    assertThat(reduced.map(_.asJava)).isEqualTo(Some((1 to 22).toVector.asJava))
  }

  @Test
  def tildeProductSupportsConstructionCopyAndPatternMatching(): Unit = {
    val pair: Int ~ String = new ~(7, "seven")
    val copied: Int ~ String = pair.copy(_2 = "VII")
    val description: String = pair match {
      case ~(number, word) => s"$number is $word"
    }

    assertThat(pair._1).isEqualTo(7)
    assertThat(pair._2).isEqualTo("seven")
    assertThat(pair).isEqualTo(new ~(7, "seven"))
    assertThat(copied).isNotEqualTo(pair)
    assertThat(copied._2).isEqualTo("VII")
    assertThat(description).isEqualTo("7 is seven")
    assertThat(pair.productArity).isEqualTo(2)
  }

  @Test
  def monoidAndReducerCombineValuesInOrder(): Unit = {
    val increment: Int => Int = _ + 1
    val triple: Int => Int = _ * 3
    val subtractFour: Int => Int = _ - 4
    val composed: Int => Int = increment |+| triple |+| subtractFour
    val identity: Int => Int = implicitly[Monoid[Int => Int]].identity

    implicit val textMonoid: Monoid[String] = new Monoid[String] {
      override def append(a1: String, a2: String): String = s"$a1,$a2"
      override def identity: String = ""
    }
    val reducer: Reducer[Int, String] = Reducer[Int, String](_.toString)

    assertThat(composed(2)).isEqualTo(5)
    assertThat(identity(99)).isEqualTo(99)
    assertThat(reducer.unit(7)).isEqualTo("7")
    assertThat(reducer.prepend(6, "7,8")).isEqualTo("6,7,8")
    assertThat(reducer.append("6,7", 8)).isEqualTo("6,7,8")
  }

  @Test
  def alternativeSyntaxSelectsFirstSuccessfulOption(): Unit = {
    implicit val optionAlternative: Alternative[Option] = new Alternative[Option] {
      override def app: Applicative[Option] = implicitly[Applicative[Option]]

      override def |[A, B >: A](alt1: Option[A], alt2: Option[B]): Option[B] = alt1.orElse(alt2)

      override def empty: Option[Nothing] = None
    }

    val primary: Option[String] = (Some("primary"): Option[String]) | (Some("fallback"): Option[String])
    val fallback: Option[String] = (None: Option[String]).or(Some("fallback"))
    val mappedViaApplicative: Option[Int] = optionAlternative.app.map(Some(2), _ + 3)

    assertThat(primary).isEqualTo(Some("primary"))
    assertThat(fallback).isEqualTo(Some("fallback"))
    assertThat(optionAlternative.empty).isEqualTo(None)
    assertThat(mappedViaApplicative).isEqualTo(Some(5))
  }

  @Test
  def contravariantFunctorSyntaxAndBuildersCreateReusablePredicates(): Unit = {
    val positive: Predicate[Int] = Predicate(_ > 0)
    val even: Predicate[Int] = Predicate(_ % 2 == 0)
    val nonBlank: Predicate[String] = Predicate(_.trim.nonEmpty)

    val positivePurchase: Predicate[Purchase] = positive.contramap[Purchase](_.quantity)
    val validPurchase: Predicate[Purchase] = (positive ~ nonBlank).apply[Purchase] { (purchase: Purchase) =>
      (purchase.quantity, purchase.sku)
    }
    val positiveEven: Predicate[Int] = (positive ~ even).join[Int]
    val tuplePredicate: Predicate[(Int, String)] = (positive ~ nonBlank).tupled

    assertThat(positivePurchase(Purchase(3, "book"))).isTrue()
    assertThat(positivePurchase(Purchase(0, "book"))).isFalse()
    assertThat(validPurchase(Purchase(3, "book"))).isTrue()
    assertThat(validPurchase(Purchase(3, "   "))).isFalse()
    assertThat(positiveEven(4)).isTrue()
    assertThat(positiveEven(5)).isFalse()
    assertThat(tuplePredicate((1, "sku"))).isTrue()
    assertThat(tuplePredicate((-1, "sku"))).isFalse()
  }

  @Test
  def invariantFunctorSyntaxAndBuildersRoundTripTextCodecs(): Unit = {
    val intCodec: TextCodec[Int] = TextCodec(_.toInt, _.toString)
    val stringCodec: TextCodec[String] = TextCodec(value => value, value => value)

    val trimmedCodec: TextCodec[String] = stringCodec.inmap[String](_.trim, value => value)
    val userCodec: TextCodec[UserRecord] = (intCodec ~ stringCodec).apply[UserRecord](
      (id: Int, name: String) => UserRecord(id, name),
      (user: UserRecord) => (user.id, user.name)
    )
    val tupleCodec: TextCodec[(Int, String)] = (intCodec ~ stringCodec).tupled

    assertThat(trimmedCodec.read("  Grace Hopper  ")).isEqualTo("Grace Hopper")
    assertThat(trimmedCodec.write("unchanged")).isEqualTo("unchanged")
    assertThat(userCodec.read("7|Ada")).isEqualTo(UserRecord(7, "Ada"))
    assertThat(userCodec.write(UserRecord(9, "Alan"))).isEqualTo("9|Alan")
    assertThat(tupleCodec.read("11|compiler")).isEqualTo((11, "compiler"))
    assertThat(tupleCodec.write((12, "runtime"))).isEqualTo("12|runtime")
  }

  @Test
  def variantExtractorsSelectVarianceSpecificTypeClassImplementations(): Unit = {
    val functorExtractor: VariantExtractor[Option] = FunctorExtractor[Option](implicitly[Functor[Option]])
    val invariantExtractor: VariantExtractor[TextCodec] = InvariantFunctorExtractor[TextCodec](textCodecInvariantFunctor)
    val contravariantExtractor: VariantExtractor[Predicate] =
      ContravariantFunctorExtractor[Predicate](predicateContravariantFunctor)

    val mappedOption: Option[Int] = functorExtractor match {
      case FunctorExtractor(functor) => functor.fmap(Option(12), (value: Int) => value + 30)
      case _                        => None
    }
    val prefixedCodec: TextCodec[String] = invariantExtractor match {
      case InvariantFunctorExtractor(invariantFunctor) =>
        invariantFunctor.inmap(
          TextCodec[Int](_.toInt, _.toString),
          (value: Int) => s"item-$value",
          (value: String) => value.stripPrefix("item-").toInt
        )
      case _ => TextCodec[String](value => value, value => value)
    }
    val purchasePredicate: Predicate[Purchase] = contravariantExtractor match {
      case ContravariantFunctorExtractor(contravariantFunctor) =>
        contravariantFunctor.contramap(Predicate[Int](_ >= 2), (purchase: Purchase) => purchase.quantity)
      case _ => Predicate[Purchase](_ => false)
    }

    assertThat(mappedOption).isEqualTo(Some(42))
    assertThat(prefixedCodec.read("7")).isEqualTo("item-7")
    assertThat(prefixedCodec.write("item-9")).isEqualTo("9")
    assertThat(purchasePredicate(Purchase(2, "notebook"))).isTrue()
    assertThat(purchasePredicate(Purchase(1, "notebook"))).isFalse()
  }

  private final case class FullName(first: String, last: String)

  private final case class Purchase(quantity: Int, sku: String)

  private final case class UserRecord(id: Int, name: String)

  private final case class Predicate[A](test: A => Boolean) {
    def apply(value: A): Boolean = test(value)
  }

  private implicit val predicateContravariantFunctor: ContravariantFunctor[Predicate] = new ContravariantFunctor[Predicate] {
    override def contramap[A, B](m: Predicate[A], f1: B => A): Predicate[B] = Predicate(value => m(f1(value)))
  }

  private implicit val predicateCanBuild: FunctionalCanBuild[Predicate] = new FunctionalCanBuild[Predicate] {
    override def apply[A, B](ma: Predicate[A], mb: Predicate[B]): Predicate[A ~ B] = Predicate { (pair: A ~ B) =>
      ma(pair._1) && mb(pair._2)
    }
  }

  private final case class TextCodec[A](read: String => A, write: A => String)

  private implicit val textCodecInvariantFunctor: InvariantFunctor[TextCodec] = new InvariantFunctor[TextCodec] {
    override def inmap[A, B](m: TextCodec[A], f1: A => B, f2: B => A): TextCodec[B] =
      TextCodec(value => f1(m.read(value)), value => m.write(f2(value)))
  }

  private implicit val textCodecCanBuild: FunctionalCanBuild[TextCodec] = new FunctionalCanBuild[TextCodec] {
    override def apply[A, B](ma: TextCodec[A], mb: TextCodec[B]): TextCodec[A ~ B] = TextCodec(
      value => {
        val parts: Array[String] = value.split("\\|", 2)
        new ~(ma.read(parts(0)), mb.read(parts(1)))
      },
      pair => s"${ma.write(pair._1)}|${mb.write(pair._2)}"
    )
  }
}
