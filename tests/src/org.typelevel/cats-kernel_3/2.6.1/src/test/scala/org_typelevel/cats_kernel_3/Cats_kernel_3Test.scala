/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_kernel_3

import java.lang.Double as JDouble

import scala.collection.immutable.Queue
import scala.collection.immutable.SortedMap
import scala.collection.immutable.SortedSet
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.MILLISECONDS
import scala.concurrent.duration.NANOSECONDS

import cats.kernel.BoundedEnumerable
import cats.kernel.BoundedSemilattice
import cats.kernel.Comparison
import cats.kernel.CommutativeGroup
import cats.kernel.CommutativeMonoid
import cats.kernel.Eq
import cats.kernel.Group
import cats.kernel.Hash
import cats.kernel.LowerBounded
import cats.kernel.Monoid
import cats.kernel.Order
import cats.kernel.PartialOrder
import cats.kernel.Semigroup
import cats.kernel.Semilattice
import cats.kernel.UpperBounded
import cats.kernel.instances.all.*
import org.junit.jupiter.api.Test

class Cats_kernel_3Test:
  @Test
  def combinesValuesWithSemigroupMonoidGroupAndCommutativeInstances(): Unit =
    val stringSemigroup: Semigroup[String] = Semigroup[String]
    assert(stringSemigroup.combine("cats", " kernel") == "cats kernel")
    assert(stringSemigroup.combineN("ha", 3) == "hahaha")
    assert(stringSemigroup.combineAllOption(List("a", "b", "c")) == Some("abc"))
    assert(stringSemigroup.reverse.combine("left", "right") == "rightleft")
    assert(stringSemigroup.intercalate(",").combine("a", "b") == "a,b")

    val stringMonoid: Monoid[String] = Monoid[String]
    assert(stringMonoid.empty == "")
    assert(stringMonoid.combineAll(Vector("type", "level", " cats")) == "typelevel cats")
    assert(stringMonoid.combineAllOption(Vector.empty[String]).isEmpty)
    assert(stringMonoid.isEmpty(""))

    val intGroup: Group[Int] = Group[Int]
    assert(intGroup.combine(7, 5) == 12)
    assert(intGroup.inverse(7) == -7)
    assert(intGroup.remove(10, 3) == 7)
    assert(intGroup.combineN(4, -3) == -12)

    val tupleGroup: CommutativeGroup[(Int, Long)] = CommutativeGroup[(Int, Long)]
    assert(tupleGroup.combine((2, 7L), (3, -2L)) == (5, 5L))
    assert(tupleGroup.inverse((2, -7L)) == (-2, 7L))

    val optionMonoid: Monoid[Option[Int]] = Monoid[Option[Int]]
    assert(optionMonoid.combine(Some(2), Some(5)) == Some(7))
    assert(optionMonoid.combine(Some(2), None) == Some(2))

    val functionMonoid: Monoid[Int => Int] = Monoid[Int => Int]
    val combinedFunction: Int => Int = functionMonoid.combine(_ + 1, _ * 2)
    assert(combinedFunction(4) == 13)

  @Test
  def combinesCollectionsAndTuplesUsingStandardInstances(): Unit =
    val listMonoid: Monoid[List[Int]] = Monoid[List[Int]]
    assert(listMonoid.combine(List(1, 2), List(3, 4)) == List(1, 2, 3, 4))

    val vectorMonoid: Monoid[Vector[String]] = Monoid[Vector[String]]
    assert(vectorMonoid.combineAll(List(Vector("a"), Vector("b", "c"))) == Vector("a", "b", "c"))

    val queueMonoid: Monoid[Queue[Int]] = Monoid[Queue[Int]]
    assert(queueMonoid.combine(Queue(1, 2), Queue(3)).toList == List(1, 2, 3))

    val mapMonoid: Monoid[Map[String, Int]] = Monoid[Map[String, Int]]
    val merged: Map[String, Int] = mapMonoid.combine(Map("a" -> 1, "b" -> 2), Map("a" -> 4, "c" -> 8))
    assert(merged == Map("a" -> 5, "b" -> 2, "c" -> 8))

    val sortedMapMonoid: Monoid[SortedMap[String, Int]] = Monoid[SortedMap[String, Int]]
    val sortedMerged: SortedMap[String, Int] = sortedMapMonoid.combine(
      SortedMap("b" -> 2),
      SortedMap("a" -> 1, "b" -> 3)
    )
    assert(sortedMerged.toVector == Vector("a" -> 1, "b" -> 5))

    val tupleMonoid: Monoid[(String, Int, Vector[Int])] = Monoid[(String, Int, Vector[Int])]
    assert(tupleMonoid.combine(("a", 1, Vector(1)), ("b", 2, Vector(2))) == ("ab", 3, Vector(1, 2)))

    val eitherEq: Eq[Either[String, Int]] = Eq[Either[String, Int]]
    assert(eitherEq.eqv(Right(42), Right(42)))
    assert(eitherEq.neqv(Left("missing"), Right(42)))

  @Test
  def comparesAndOrdersValuesWithEqHashPartialOrderAndOrder(): Unit =
    final case class Account(id: String, priority: Int)

    val accountEq: Eq[Account] = Eq.by[Account, String](_.id)
    assert(accountEq.eqv(Account("a-1", 10), Account("a-1", 99)))
    assert(accountEq.neqv(Account("a-1", 10), Account("a-2", 10)))

    val accountOrder: Order[Account] = Order.by[Account, Int](_.priority)
    assert(accountOrder.compare(Account("low", 1), Account("high", 10)) < 0)
    assert(accountOrder.max(Account("low", 1), Account("high", 10)).id == "high")
    assert(Order.reverse(accountOrder).min(Account("low", 1), Account("high", 10)).id == "high")

    val fallbackOrder: Order[Account] = Order.whenEqual(
      Order.by[Account, Int](_.priority),
      Order.by[Account, String](_.id)
    )
    assert(fallbackOrder.compare(Account("a", 1), Account("b", 1)) < 0)

    val accountHash: Hash[Account] = Hash.by[Account, String](_.id)
    assert(accountHash.hash(Account("stable", 1)) == accountHash.hash(Account("stable", 2)))
    assert(accountHash.eqv(Account("stable", 1), Account("stable", 2)))

    val listOrder: Order[List[Int]] = Order[List[Int]]
    assert(listOrder.comparison(List(1, 2), List(1, 3)) == Comparison.LessThan)
    assert(listOrder.partialComparison(List(1, 2), List(1, 2)) == Some(Comparison.EqualTo))
    assert(List(List(2), List(1, 2), List(1, 1)).sorted(listOrder.toOrdering) == List(List(1, 1), List(1, 2), List(2)))

    val setPartialOrder: PartialOrder[Set[Int]] = PartialOrder[Set[Int]]
    assert(setPartialOrder.lteqv(Set(1), Set(1, 2)))
    assert(setPartialOrder.pmax(Set(1), Set(1, 2)) == Some(Set(1, 2)))
    assert(setPartialOrder.pmin(Set(1), Set(1, 2)) == Some(Set(1)))
    assert(JDouble.isNaN(setPartialOrder.partialCompare(Set(1), Set(2))))
    assert(setPartialOrder.tryCompare(Set(1), Set(2)).isEmpty)

  @Test
  def enumeratesBoundedIntegralCharacterAndBooleanValues(): Unit =
    val booleanEnumerable: BoundedEnumerable[Boolean] = BoundedEnumerable[Boolean]
    assert(booleanEnumerable.partialNext(false) == Some(true))
    assert(booleanEnumerable.partialNext(true).isEmpty)
    assert(booleanEnumerable.partialPrevious(true) == Some(false))
    assert(booleanEnumerable.partialPrevious(false).isEmpty)
    assert(booleanEnumerable.cycleNext(true) == false)
    assert(booleanEnumerable.cyclePrevious(false) == true)
    assert(booleanEnumerable.minBound == false)
    assert(booleanEnumerable.maxBound == true)
    assert(booleanEnumerable.order.compare(false, true) < 0)

    val byteEnumerable: BoundedEnumerable[Byte] = BoundedEnumerable[Byte]
    assert(byteEnumerable.partialNext(0.toByte) == Some(1.toByte))
    assert(byteEnumerable.partialPrevious(0.toByte) == Some((-1).toByte))
    assert(byteEnumerable.partialNext(Byte.MaxValue).isEmpty)
    assert(byteEnumerable.cycleNext(Byte.MaxValue) == Byte.MinValue)

    val charEnumerable: BoundedEnumerable[Char] = BoundedEnumerable[Char]
    assert(charEnumerable.partialNext('a') == Some('b'))
    assert(charEnumerable.partialPrevious('b') == Some('a'))
    assert(charEnumerable.minBound == Char.MinValue)
    assert(charEnumerable.maxBound == Char.MaxValue)

  @Test
  def exposesLowerAndUpperBoundsForSupportedTypes(): Unit =
    val stringLowerBounded: LowerBounded[String] = LowerBounded[String]
    assert(stringLowerBounded.minBound == "")
    assert(stringLowerBounded.partialOrder.lteqv("", "abc"))

    val intLowerBounded: LowerBounded[Int] = LowerBounded[Int]
    val intUpperBounded: UpperBounded[Int] = UpperBounded[Int]
    assert(intLowerBounded.minBound == Int.MinValue)
    assert(intUpperBounded.maxBound == Int.MaxValue)

    val finiteDurationUpperBounded: UpperBounded[FiniteDuration] = UpperBounded[FiniteDuration]
    assert(finiteDurationUpperBounded.maxBound == Duration(Long.MaxValue, NANOSECONDS))

    val durationGroup: Group[Duration] = Group[Duration]
    assert(durationGroup.combine(Duration(100, MILLISECONDS), Duration(50, MILLISECONDS)) == Duration(150, MILLISECONDS))
    assert(durationGroup.inverse(Duration(25, MILLISECONDS)) == Duration(-25, MILLISECONDS))

  @Test
  def usesSemilatticeAndBoundedSemilatticeInstancesForSetsAndBitSets(): Unit =
    val stringSetSemilattice: Semilattice[Set[String]] = Semilattice[Set[String]]
    assert(stringSetSemilattice.combine(Set("a", "b"), Set("b", "c")) == Set("a", "b", "c"))

    val setSemilattice: Semilattice[Set[Int]] = Semilattice[Set[Int]]

    val setBoundedSemilattice: BoundedSemilattice[Set[Int]] = BoundedSemilattice[Set[Int]]
    assert(setBoundedSemilattice.empty == Set.empty[Int])
    assert(setBoundedSemilattice.combineAll(List(Set(1), Set(2, 3))) == Set(1, 2, 3))
    assert(setBoundedSemilattice.combineN(Set(1, 2), 5) == Set(1, 2))

    val sortedSetSemilattice: Semilattice[SortedSet[Int]] = Semilattice[SortedSet[Int]]
    assert(sortedSetSemilattice.combine(SortedSet(3, 1), SortedSet(2)).toVector == Vector(1, 2, 3))

    val meetPartialOrder: PartialOrder[Set[Int]] = setSemilattice.asMeetPartialOrder(Eq[Set[Int]])
    assert(meetPartialOrder.gteqv(Set(1), Set(1, 2)))
    assert(meetPartialOrder.lteqv(Set(1, 2), Set(1)))

    val joinPartialOrder: PartialOrder[Set[Int]] = setSemilattice.asJoinPartialOrder(Eq[Set[Int]])
    assert(joinPartialOrder.lteqv(Set(1), Set(1, 2)))
    assert(joinPartialOrder.gteqv(Set(1, 2), Set(1)))

  @Test
  def supportsComparisonConversionsAndUniversalConstructors(): Unit =
    assert(Comparison.fromInt(-10) == Comparison.LessThan)
    assert(Comparison.fromInt(0) == Comparison.EqualTo)
    assert(Comparison.fromInt(10) == Comparison.GreaterThan)
    assert(Comparison.fromDouble(Double.NaN).isEmpty)
    assert(Comparison.fromDouble(-0.5) == Some(Comparison.LessThan))
    assert(Comparison.LessThan.toInt < 0)
    assert(Comparison.GreaterThan.toDouble > 0.0)

    val universalEq: Eq[List[Int]] = Eq.fromUniversalEquals[List[Int]]
    assert(universalEq.eqv(List(1, 2), List(1, 2)))
    assert(universalEq.neqv(List(1, 2), List(2, 1)))

    val allEqualEq: Eq[String] = Eq.allEqual[String]
    assert(allEqualEq.eqv("left", "right"))

    val universalHash: Hash[String] = Hash.fromUniversalHashCode[String]
    assert(universalHash.eqv("same", "same"))
    assert(universalHash.hash("same") == "same".hashCode)

    val orderingBackedOrder: Order[String] = Order.fromOrdering(Ordering.String.reverse)
    assert(orderingBackedOrder.compare("a", "b") > 0)

    val lessThanOrder: Order[Int] = Order.fromLessThan[Int](_ > _)
    assert(lessThanOrder.compare(9, 3) < 0)

  @Test
  def derivesCustomInstancesWithCompanionConstructors(): Unit =
    val caseInsensitiveEq: Eq[String] = Eq.instance[String]((left: String, right: String) => left.equalsIgnoreCase(right))
    assert(caseInsensitiveEq.eqv("Cats", "cats"))
    assert(caseInsensitiveEq.neqv("Cats", "dogs"))

    val conjunction: Eq[String] = Eq.and(Eq[String], caseInsensitiveEq)
    assert(conjunction.eqv("Cats", "Cats"))
    assert(conjunction.neqv("Cats", "cats"))

    val disjunction: Eq[String] = Eq.or(Eq[String], caseInsensitiveEq)
    assert(disjunction.eqv("Cats", "cats"))

    val multiplicationMonoid: CommutativeMonoid[Int] = CommutativeMonoid.instance(1, _ * _)
    assert(multiplicationMonoid.empty == 1)
    assert(multiplicationMonoid.combineAll(List(2, 3, 4)) == 24)

    val maxSemigroup: Semigroup[Int] = Semigroup.instance((left: Int, right: Int) => left.max(right))
    assert(maxSemigroup.combineAllOption(List(3, 9, 4)) == Some(9))

    val additionMonoid: Monoid[Int] = Monoid.instance(0, _ + _)
    assert(additionMonoid.combineAll(List(1, 2, 3, 4)) == 10)
