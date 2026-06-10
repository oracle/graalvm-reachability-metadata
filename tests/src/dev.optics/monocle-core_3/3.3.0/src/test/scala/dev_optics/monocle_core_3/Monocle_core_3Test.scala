/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_optics.monocle_core_3

import monocle.Focus
import monocle.Fold
import monocle.Getter
import monocle.Iso
import monocle.Lens
import monocle.Optional
import monocle.Prism
import monocle.Setter
import monocle.Traversal
import monocle.function.all.at
import monocle.function.all.each
import monocle.function.all.index
import monocle.std.either.stdLeft
import monocle.std.either.stdRight
import monocle.std.list.listToVector
import monocle.std.option.none
import monocle.std.option.optionToDisjunction
import monocle.std.option.some
import monocle.std.option.withDefault
import monocle.std.string.stringToBoolean
import monocle.std.string.stringToInt
import monocle.std.string.stringToList
import monocle.std.string.stringToURI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.net.URI

class Monocle_core_3Test {
  @Test
  def lensCompositionUpdatesDeeplyNestedProductValues(): Unit = {
    val cityLens: Lens[User, String] =
      Focus[User](_.address.street.city)
    val addressLens: Lens[User, Address] =
      Lens[User, Address](_.address)(address => user => user.copy(address = address))
    val streetLens: Lens[Address, Street] =
      Lens[Address, Street](_.street)(street => address => address.copy(street = street))
    val numberLens: Lens[Street, Int] =
      Lens[Street, Int](_.number)(number => street => street.copy(number = number))
    val streetNumberLens: Lens[User, Int] =
      addressLens.andThen(streetLens).andThen(numberLens)

    val original: User = User("Ada", 36, Address(Street("London", 12)))
    val moved: User = cityLens.replace("Zürich")(original)
    val renumbered: User = streetNumberLens.modify(_ + 10)(moved)

    assertThat(cityLens.get(original)).isEqualTo("London")
    assertThat(cityLens.get(moved)).isEqualTo("Zürich")
    assertThat(streetNumberLens.get(renumbered)).isEqualTo(22)
    assertThat(streetNumberLens.asOptional.getOption(renumbered)).isEqualTo(Some(22))
  }

  @Test
  def prismsSelectModifyAndConstructAlgebraicDataTypes(): Unit = {
    val cardPrism: Prism[Payment, Card] =
      Prism[Payment, Card] {
        case card: Card => Some(card)
        case _          => None
      }(identity)
    val cashPrism: Prism[Payment, Cash] =
      Prism.partial[Payment, Cash] { case cash: Cash => cash }(identity)

    val cardPayment: Payment = Card("tok_123", 2500)
    val cashPayment: Payment = Cash(1200)
    val discounted: Payment = cardPrism.modify(card => card.copy(cents = card.cents - 500))(cardPayment)

    assertThat(cardPrism.getOption(cardPayment)).isEqualTo(Some(Card("tok_123", 2500)))
    assertThat(cardPrism.getOption(cashPayment)).isEqualTo(None)
    assertThat(discounted).isEqualTo(Card("tok_123", 2000))
    assertThat(cardPrism.reverseGet(Card("tok_999", 1500))).isEqualTo(Card("tok_999", 1500))
    assertThat(cardPrism.replace(Card("tok_new", 3000))(cashPayment)).isEqualTo(cashPayment)
    assertThat(cashPrism.replace(Cash(800))(cashPayment)).isEqualTo(Cash(800))
    assertThat(cardPrism.re.get(Card("tok_re", 1))).isEqualTo(Card("tok_re", 1))
  }

  @Test
  def optionalHandlesPresentAndMissingTargetsWithoutChangingMisses(): Unit = {
    val headOptional: Optional[List[Int], Int] =
      Optional[List[Int], Int](_.headOption)(head => list => if list.isEmpty then list else head :: list.tail)
    val positiveOptional: Optional[Int, Int] = Optional.filter[Int](_ > 0)
    val positiveHead: Optional[List[Int], Int] = headOptional.andThen(positiveOptional)

    assertThat(headOptional.getOption(List(1, 2, 3))).isEqualTo(Some(1))
    assertThat(headOptional.getOption(Nil)).isEqualTo(None)
    assertThat(headOptional.modifyOption(_ * 10)(List(1, 2, 3))).isEqualTo(Some(List(10, 2, 3)))
    assertThat(headOptional.modifyOption(_ * 10)(Nil)).isEqualTo(None)
    assertThat(positiveHead.replaceOption(7)(List(1, 2, 3))).isEqualTo(Some(List(7, 2, 3)))
    assertThat(positiveHead.replaceOption(7)(List(-1, 2, 3))).isEqualTo(None)
    assertThat(positiveHead.isEmpty(List(-1, 2, 3))).isTrue()
    assertThat(positiveHead.nonEmpty(List(1, 2, 3))).isTrue()
  }

  @Test
  def traversalVisitsEveryConfiguredFieldAndProvidesFoldOperations(): Unit = {
    val scoresTraversal: Traversal[Scores, Int] =
      Traversal.apply3[Scores, Int](_.math, _.science, _.literature) { (math, science, literature, scores) =>
        scores.copy(math = math, science = science, literature = literature)
      }
    val scores: Scores = Scores(8, 9, 7)

    assertThat(scoresTraversal.getAll(scores)).isEqualTo(List(8, 9, 7))
    assertThat(scoresTraversal.modify(_ + 1)(scores)).isEqualTo(Scores(9, 10, 8))
    assertThat(scoresTraversal.replace(0)(scores)).isEqualTo(Scores(0, 0, 0))
    assertThat(scoresTraversal.length(scores)).isEqualTo(3)
    assertThat(scoresTraversal.headOption(scores)).isEqualTo(Some(8))
    assertThat(scoresTraversal.lastOption(scores)).isEqualTo(Some(7))
    assertThat(scoresTraversal.find(_ > 8)(scores)).isEqualTo(Some(9))
    assertThat(scoresTraversal.exist(_ == 7)(scores)).isEqualTo(true)
    assertThat(scoresTraversal.all(_ >= 7)(scores)).isEqualTo(true)
  }

  @Test
  def isoTransformsBothDirectionsAndComposesWithOtherIsos(): Unit = {
    val stringChars: Iso[String, List[Char]] = Iso[String, List[Char]](_.toList)(_.mkString)
    val charsVector: Iso[List[Char], Vector[Char]] = Iso[List[Char], Vector[Char]](_.toVector)(_.toList)
    val stringVector: Iso[String, Vector[Char]] = stringChars.andThen(charsVector)
    val booleanInvolution: Iso[Boolean, Boolean] = Iso.involuted[Boolean](!_)
    val firstStringChars: Iso[(String, Int), (List[Char], Int)] = stringChars.first[Int]

    assertThat(stringVector.get("abc")).isEqualTo(Vector('a', 'b', 'c'))
    assertThat(stringVector.reverseGet(Vector('x', 'y'))).isEqualTo("xy")
    assertThat(stringVector.modify(_.map(_.toUpper))("abc")).isEqualTo("ABC")
    assertThat(stringVector.reverse.get(Vector('o', 'k'))).isEqualTo("ok")
    assertThat(firstStringChars.get(("ab", 1))).isEqualTo((List('a', 'b'), 1))
    assertThat(booleanInvolution.get(false)).isEqualTo(true)
    assertThat(booleanInvolution.reverseGet(true)).isEqualTo(false)
  }

  @Test
  def getterSetterAndFoldExposeReadOnlyWriteOnlyAndAggregateViews(): Unit = {
    val user: User = User("Grace", 42, Address(Street("Arlington", 7)))
    val nameGetter: Getter[User, String] = Getter[User, String](_.name)
    val ageSetter: Setter[User, Int] = Setter[User, Int](update => user => user.copy(age = update(user.age)))
    val nonEmptyFold: Fold[List[Int], Int] = Fold.select[List[Int]](_.nonEmpty).andThen(Fold.fromFoldable[List, Int])

    assertThat(nameGetter.get(user)).isEqualTo("Grace")
    assertThat(nameGetter.to(_.length).get(user)).isEqualTo(5)
    assertThat(nameGetter.find(_.startsWith("G"))(user)).isEqualTo(Some("Grace"))
    assertThat(ageSetter.modify(_ + 1)(user)).isEqualTo(user.copy(age = 43))
    assertThat(ageSetter.replace(50)(user)).isEqualTo(user.copy(age = 50))
    assertThat(nonEmptyFold.getAll(List(1, 2, 3))).isEqualTo(List(1, 2, 3))
    assertThat(nonEmptyFold.getAll(Nil)).isEqualTo(Nil)
    assertThat(nonEmptyFold.isEmpty(Nil)).isTrue()
  }

  @Test
  def standardOpticsCoverOptionsEitherListsAndStrings(): Unit = {
    val uri: URI = URI.create("https://example.com/path")

    assertThat(some[Int].getOption(Some(5))).isEqualTo(Some(5))
    assertThat(some[Int].getOption(None)).isEqualTo(None)
    assertThat(none[Int].getOption(None)).isEqualTo(Some(()))
    assertThat(withDefault(10).get(None)).isEqualTo(10)
    assertThat(withDefault(10).reverseGet(3)).isEqualTo(Some(3))
    assertThat(optionToDisjunction[Int].get(Some(4))).isEqualTo(Right(4))
    assertThat(optionToDisjunction[Int].get(None)).isEqualTo(Left(()))
    assertThat(stdRight[String, Int].getOption(Right(7))).isEqualTo(Some(7))
    assertThat(stdRight[String, Int].getOption(Left("missing"))).isEqualTo(None)
    assertThat(stdLeft[String, Int].replace("updated")(Left("old"))).isEqualTo(Left("updated"))
    assertThat(listToVector[Int].get(List(1, 2, 3))).isEqualTo(Vector(1, 2, 3))
    assertThat(listToVector[Int].reverseGet(Vector(4, 5))).isEqualTo(List(4, 5))
    assertThat(stringToInt.getOption("42")).isEqualTo(Some(42))
    assertThat(stringToInt.getOption("not-an-int")).isEqualTo(None)
    assertThat(stringToBoolean.getOption("true")).isEqualTo(Some(true))
    assertThat(stringToURI.reverseGet(uri)).isEqualTo(uri.toString)
    assertThat(stringToList.modify(_.reverse)("abc")).isEqualTo("cba")
  }

  @Test
  def collectionFunctionOpticsUpdateIndexedTraversedAndMapEntries(): Unit = {
    val numbers: List[Int] = List(1, 2, 3)
    val inventory: Map[String, Int] = Map("apple" -> 2)

    assertThat(each[List[Int], Int].modify(_ * 2)(numbers)).isEqualTo(List(2, 4, 6))
    assertThat(each[List[Int], Int].getAll(numbers)).isEqualTo(numbers)
    assertThat(index[List[Int], Int, Int](1).getOption(numbers)).isEqualTo(Some(2))
    assertThat(index[List[Int], Int, Int](1).replace(20)(numbers)).isEqualTo(List(1, 20, 3))
    assertThat(index[List[Int], Int, Int](10).replaceOption(99)(numbers)).isEqualTo(None)
    assertThat(at[Map[String, Int], String, Option[Int]]("banana").replace(Some(5))(inventory))
      .isEqualTo(Map("apple" -> 2, "banana" -> 5))
    assertThat(at[Map[String, Int], String, Option[Int]]("apple").replace(None)(inventory)).isEqualTo(Map.empty)
  }
}

final case class User(name: String, age: Int, address: Address)
final case class Address(street: Street)
final case class Street(city: String, number: Int)
final case class Scores(math: Int, science: Int, literature: Int)

sealed trait Payment
final case class Card(token: String, cents: Int) extends Payment
final case class Cash(cents: Int) extends Payment
