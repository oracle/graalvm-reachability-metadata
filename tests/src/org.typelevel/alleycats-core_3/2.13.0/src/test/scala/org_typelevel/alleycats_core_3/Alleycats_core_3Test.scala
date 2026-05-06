/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.alleycats_core_3

import alleycats.ConsK
import alleycats.Empty
import alleycats.EmptyK
import alleycats.Extract
import alleycats.One
import alleycats.Pure
import alleycats.ReferentialEq
import alleycats.SystemIdentityHash
import alleycats.Zero
import cats.Alternative
import cats.Bimonad
import cats.Eval
import cats.Hash
import cats.Monad
import cats.Traverse
import cats.TraverseFilter
import cats.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class Alleycats_core_3Test {
  @Test
  def createsAndSummonsValueLevelEmptyOneAndZeroTypeClasses(): Unit = {
    var emptyEvaluations: Int = 0
    val explicitEmpty: Empty[String] = Empty {
      emptyEvaluations += 1
      "empty"
    }

    assertEquals(0, emptyEvaluations)
    assertEquals("empty", explicitEmpty.empty)
    assertEquals("empty", explicitEmpty.empty)
    assertEquals(1, emptyEvaluations)
    assertTrue(explicitEmpty.isEmpty("empty"))
    assertFalse(explicitEmpty.isEmpty("non-empty"))
    assertTrue(explicitEmpty.nonEmpty("non-empty"))

    val one: One[Int] = One(1)
    assertEquals(1, one.one)
    assertTrue(one.isOne(1))
    assertFalse(one.isOne(2))
    assertTrue(one.nonOne(2))

    val zero: Zero[BigDecimal] = Zero(BigDecimal(0))
    assertEquals(BigDecimal(0), zero.zero)
    assertTrue(zero.isZero(BigDecimal(0)))
    assertFalse(zero.isZero(BigDecimal("0.25")))
    assertTrue(zero.nonZero(BigDecimal("0.25")))
  }

  @Test
  def derivesEmptyInstancesFromCatsMonoidsIterableFactoriesAndEmptyK(): Unit = {
    import alleycats.std.option.*

    assertEquals(0, Empty[Int].empty)
    assertTrue(Empty[Int].isEmpty(0))
    assertTrue(Empty[Int].nonEmpty(42))

    val vectorEmpty: Empty[Vector[String]] = Empty[Vector[String]]
    assertEquals(Vector.empty[String], vectorEmpty.empty)
    assertTrue(vectorEmpty.isEmpty(Vector.empty[String]))
    assertTrue(vectorEmpty.nonEmpty(Vector("value")))

    val optionEmpty: Empty[Option[Int]] = Empty.fromEmptyK[Option, Int]
    assertEquals(None, optionEmpty.empty)
    assertTrue(optionEmpty.isEmpty(None))
    assertTrue(optionEmpty.nonEmpty(Some(1)))
  }

  @Test
  def providesEmptyKAndConsKForStandardContainers(): Unit = {
    import alleycats.std.list.*
    import alleycats.std.option.*

    type StringKeyMap[A] = Map[String, A]

    assertEquals(List.empty[Int], EmptyK[List].empty[Int])
    assertEquals(None, EmptyK[Option].empty[String])
    assertEquals(Map.empty[String, Int], EmptyK[StringKeyMap].empty[Int])

    val listCons: ConsK[List] = ConsK[List]
    assertEquals(List(1, 2, 3), listCons.cons(1, List(2, 3)))

    val optionCons: ConsK[Option] = ConsK[Option]
    assertEquals(Some("first"), optionCons.cons("first", Some("second")))
    assertEquals(Some("only"), optionCons.cons("only", None))
  }

  @Test
  def derivesPureAndExtractFromCatsTypeClassesAndStdInstances(): Unit = {
    import alleycats.std.future.*
    import alleycats.std.try_.*
    import alleycats.syntax.extract.*

    val listPure: Pure[List] = Pure[List]
    assertEquals(List(7), listPure.pure(7))

    val futurePure: Pure[Future] = Pure[Future]
    assertEquals("completed", Await.result(futurePure.pure("completed"), 5.seconds))

    val tryBimonad: Bimonad[Try] = Bimonad[Try]
    assertEquals(Success(11), tryBimonad.pure(11))
    assertEquals(Success(12), tryBimonad.flatMap(Success(6))(n => Success(n * 2)))
    assertTrue(tryBimonad.flatMap(Failure[Int](new IllegalStateException("boom")))(n => Success(n)).isFailure)
    assertEquals("ok", tryBimonad.extract(Success("ok")))
    assertEquals(Success(3), tryBimonad.coflatMap(Success("abc"))(_.get.length))

    val tryExtract: Extract[Try] = Extract[Try]
    assertEquals(99, tryExtract.extract(Success(99)))
    val successfulTry: Try[String] = Success("syntax")
    assertEquals("syntax", successfulTry.extract)
    assertThrows(classOf[RuntimeException], () => tryExtract.extract(Failure(new RuntimeException("expected"))))
  }

  @Test
  def suppliesMonadAlternativeTraverseAndTraverseFilterForSet(): Unit = {
    import alleycats.std.set.*

    val setMonad: Monad[Set] = Monad[Set]
    assertEquals(Set(4), setMonad.pure(4))
    assertEquals(Set(1, 2, 3), setMonad.flatMap(Set(1, 2))(n => Set(n, n + 1)))
    assertEquals(Set(0, 1, 2, 3), setMonad.tailRecM(0) { n =>
      if (n < 3) Set(Left(n + 1), Right(n)) else Set(Right(n))
    })

    val setAlternative: Alternative[Set] = Alternative[Set]
    assertEquals(Set.empty[String], setAlternative.empty[String])
    assertEquals(Set("a", "b", "c"), setAlternative.combineK(Set("a", "b"), Set("b", "c")))
    assertEquals(Set("start", "tail"), setAlternative.prependK("start", Set("tail")))
    assertEquals(Set("head", "end"), setAlternative.appendK(Set("head"), "end"))
    assertEquals(Set((1, "a"), (1, "b"), (2, "a"), (2, "b")), setAlternative.product(Set(1, 2), Set("a", "b")))

    val setTraverse: Traverse[Set] = Traverse[Set]
    assertEquals(Some(Set(2, 4, 6)), setTraverse.traverse(Set(1, 2, 3))(n => Option(n * 2)))
    assertEquals(None, setTraverse.traverse(Set(1, 2, 3))(n => Option.when(n < 3)(n)))
    val orderedSet: Set[Int] = scala.collection.immutable.SortedSet(1, 2, 3)
    assertEquals((6, Set(1, 3, 6)), setTraverse.mapAccumulate(0, orderedSet) { (sum, n) =>
      val next: Int = sum + n
      (next, next)
    })
    assertTrue(setTraverse.get(Set(1, 2, 3))(1).exists(Set(1, 2, 3).contains))
    assertEquals(None, setTraverse.get(Set(1, 2, 3))(-1))
    assertEquals(3L, setTraverse.size(Set(1, 2, 3)))
    assertTrue(setTraverse.exists(Set(1, 2, 3))(_ == 2))
    assertTrue(setTraverse.forall(Set(1, 2, 3))(_ > 0))

    val setTraverseFilter: TraverseFilter[Set] = TraverseFilter[Set]
    assertEquals(Some(Set("even-2", "even-4")), setTraverseFilter.traverseFilter(Set(1, 2, 3, 4)) { n =>
      Option(Option.when(n % 2 == 0)(s"even-$n"))
    })
  }

  @Test
  def suppliesTraverseAndTraverseFilterForIterable(): Unit = {
    import alleycats.std.iterable.*

    val iterableTraverse: Traverse[Iterable] = Traverse[Iterable]
    val values: Iterable[Int] = List(1, 2, 3)

    assertEquals(6, iterableTraverse.foldLeft(values, 0)(_ + _))
    assertEquals(6, iterableTraverse.foldRight(values, Eval.now(0))((n, total) => total.map(_ + n)).value)
    assertEquals(Some(List(2, 4, 6)), iterableTraverse.traverse(values)(n => Option(n * 2)).map(_.toList))
    assertEquals(None, iterableTraverse.traverse(values)(n => Option.when(n < 3)(n)))
    val accumulated: (Int, Iterable[Int]) = iterableTraverse.mapAccumulate(0, values) { (sum, n) =>
      val next: Int = sum + n
      (next, next)
    }
    assertEquals((6, List(1, 3, 6)), accumulated._1 -> accumulated._2.toList)
    assertEquals(List((1, 0), (2, 1), (3, 2)), iterableTraverse.zipWithIndex(values).toList)
    assertEquals(List("1@0", "2@1", "3@2"), iterableTraverse.mapWithIndex(values)((n, index) => s"$n@$index").toList)
    assertEquals(Some(2), iterableTraverse.collectFirst(values) { case n if n % 2 == 0 => n })
    assertTrue(iterableTraverse.exists(values)(_ == 3))
    assertTrue(iterableTraverse.forall(values)(_ < 4))
    assertFalse(iterableTraverse.isEmpty(values))

    val iterableTraverseFilter: TraverseFilter[Iterable] = TraverseFilter[Iterable]
    assertEquals(Some(List("odd-1", "odd-3")), iterableTraverseFilter.traverseFilter(values) { n =>
      Option(Option.when(n % 2 == 1)(s"odd-$n"))
    }.map(_.toList))
  }

  @Test
  def suppliesTraverseAndTraverseFilterForMapValuesWhilePreservingKeys(): Unit = {
    import alleycats.std.map.*

    type StringKeyMap[A] = Map[String, A]

    val mapTraverse: Traverse[StringKeyMap] = Traverse[StringKeyMap]
    val inventory: Map[String, Int] = scala.collection.immutable.ListMap("apples" -> 2, "bananas" -> 3, "pears" -> 4)

    assertEquals(Map("apples" -> 20, "bananas" -> 30, "pears" -> 40), mapTraverse.map(inventory)(_ * 10))
    assertEquals(9, mapTraverse.foldLeft(inventory, 0)(_ + _))
    assertEquals(9, mapTraverse.foldRight(inventory, Eval.now(0))((n, total) => total.map(_ + n)).value)
    assertEquals(Some(Map("apples" -> 3, "bananas" -> 4, "pears" -> 5)), mapTraverse.traverse(inventory)(n => Option(n + 1)))
    assertEquals(None, mapTraverse.traverse(inventory)(n => Option.when(n < 4)(n)))
    assertEquals((9, Map("apples" -> 2, "bananas" -> 5, "pears" -> 9)), mapTraverse.mapAccumulate(0, inventory) { (sum, n) =>
      val next: Int = sum + n
      (next, next)
    })
    assertEquals(3L, mapTraverse.size(inventory))
    assertTrue(mapTraverse.get(inventory)(0).exists(inventory.values.toSet.contains))
    assertEquals(None, mapTraverse.get(inventory)(5))
    assertEquals(Some("large-4"), mapTraverse.collectFirst(inventory) { case n if n > 3 => s"large-$n" })
    assertEquals(Some("even-2"), mapTraverse.collectFirstSome(inventory)(n => Option.when(n % 2 == 0)(s"even-$n")))
    assertEquals(9, mapTraverse.fold(inventory))
    assertEquals(inventory.values.toSet, mapTraverse.toList(inventory).toSet)
    assertEquals(inventory.values.toSet, mapTraverse.toIterable(inventory).toSet)

    val mapTraverseFilter: TraverseFilter[StringKeyMap] = TraverseFilter[StringKeyMap]
    assertEquals(Some(Map("apples" -> "even-2", "pears" -> "even-4")), mapTraverseFilter.traverseFilter(inventory) { n =>
      Option(Option.when(n % 2 == 0)(s"even-$n"))
    })
  }

  @Test
  def providesTryBimonadFailureHandlingAndTailRecM(): Unit = {
    import alleycats.std.try_.*

    val bimonad: Bimonad[Try] = Bimonad[Try]
    val failure: Try[Int] = Failure(new IllegalArgumentException("bad input"))

    assertEquals(Success(8), bimonad.map(Success(4))(_ * 2))
    assertTrue(bimonad.map(failure)(_ * 2).isFailure)
    assertEquals(Success(10), bimonad.tailRecM(0) { n =>
      if (n < 10) Success(Left(n + 1)) else Success(Right(n))
    })
    assertTrue(bimonad.tailRecM(0)(_ => failure.map(Right(_))).isFailure)
    assertThrows(classOf[IllegalArgumentException], () => bimonad.extract(failure))
  }

  @Test
  def comparesAndHashesObjectsByReferenceIdentity(): Unit = {
    val left: String = new String("same")
    val sameReference: String = left
    val equalButDifferentReference: String = new String("same")

    val referentialEq = ReferentialEq[String]
    assertTrue(referentialEq.eqv(left, sameReference))
    assertFalse(referentialEq.eqv(left, equalButDifferentReference))
    assertTrue(left == equalButDifferentReference)

    val identityHash: Hash[String] = SystemIdentityHash[String]
    assertTrue(identityHash.eqv(left, sameReference))
    assertFalse(identityHash.eqv(left, equalButDifferentReference))
    assertEquals(System.identityHashCode(left), identityHash.hash(left))
    assertEquals(System.identityHashCode(equalButDifferentReference), identityHash.hash(equalButDifferentReference))
    assertSame(SystemIdentityHash[String], SystemIdentityHash[String])
  }
}
