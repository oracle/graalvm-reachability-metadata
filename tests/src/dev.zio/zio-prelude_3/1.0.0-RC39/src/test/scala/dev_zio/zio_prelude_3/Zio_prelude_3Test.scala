/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_prelude_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import zio.prelude.NonEmptyList
import zio.prelude.NonEmptyMap
import zio.prelude.NonEmptySet
import zio.prelude.These
import zio.prelude.ZSet
import zio.prelude.ZValidation
import zio.prelude.data.Optional
import zio.prelude.fx.ZPure

class Zio_prelude_3Test {
  @Test
  def nonEmptyListSupportsSafeCollectionOperations(): Unit = {
    val numbers: NonEmptyList[Int] = NonEmptyList(3, 1, 4, 1, 5)

    assertEquals(3, numbers.head)
    assertEquals(5, numbers.length)
    assertEquals(List(3, 1, 4, 1, 5), numbers.toCons)
    assertEquals(List(4, 1, 5), numbers.drop(2))
    assertEquals(List(3, 1, 4), numbers.take(3))
    assertEquals(Some(NonEmptyList(1, 4, 1, 5)), numbers.tailNonEmpty)
    assertEquals(NonEmptyList(6, 2, 8, 2, 10), numbers.map(_ * 2))
    assertEquals(NonEmptyList(3, -3, 1, -1, 4, -4, 1, -1, 5, -5), numbers.flatMap(n => NonEmptyList(n, -n)))
    assertEquals(14, numbers.reduceLeft(_ + _))
    assertEquals("[3, 1, 4, 1, 5]", numbers.mkString("[", ", ", "]"))
    assertEquals(NonEmptyList((3, 0), (1, 1), (4, 2), (1, 3), (5, 4)), numbers.zipWithIndex)
    assertTrue(numbers.exists(_ == 4))
    assertFalse(numbers.forall(_ > 3))
  }

  @Test
  def nonEmptyMapGroupsAndTransformsEntriesWithoutLosingNonEmptyInvariant(): Unit = {
    val grouped: Option[NonEmptyMap[Int, Iterable[String]]] =
      NonEmptyMap.groupByOption(List("pear", "plum", "fig", "kiwi"))(_.length)

    assertEquals(Some(Map(4 -> List("pear", "plum", "kiwi"), 3 -> List("fig"))), grouped.map(_.mapValues(_.toList).toMap))
    assertTrue(NonEmptyMap.groupByOption(List.empty[String])(_.length).isEmpty)

    val inventory: NonEmptyMap[String, Int] =
      (NonEmptyMap.single("apples" -> 2) + ("bananas" -> 3)) ++ List("pears" -> 4, "apples" -> 5)

    assertEquals(Map("apples" -> 5, "bananas" -> 3, "pears" -> 4), inventory.toMap)
    assertEquals(Set("apples", "bananas", "pears"), inventory.keySet.toSet)
    assertEquals(Map("apples" -> "5 items", "bananas" -> "3 items", "pears" -> "4 items"), inventory.mapValues(count => s"$count items").toMap)
    assertEquals(Map("bananas" -> 3, "pears" -> 4), inventory.remove("apples"))
    assertTrue(inventory.tailNonEmpty.isDefined)
    assertTrue(NonEmptyMap.fromMapOption(Map.empty[String, Int]).isEmpty)
    assertEquals(Some(NonEmptyMap.single("only" -> 1)), NonEmptyMap.fromMapOption(Map("only" -> 1)))
  }

  @Test
  def nonEmptySetMaintainsNonEmptyInvariantWhileTransformingMembers(): Unit = {
    val fruits: NonEmptySet[String] = NonEmptySet("apple", "banana", "apple")
    val expanded: NonEmptySet[String] = fruits + "pear"

    assertEquals(Set("apple", "banana"), fruits.toSet)
    assertEquals(Set("apple", "banana", "pear"), expanded.toSet)
    assertEquals(Some(Set("banana")), fruits.removeNonEmpty("apple").map(_.toSet))
    assertEquals(None, NonEmptySet.single("only").removeNonEmpty("only"))
    assertEquals(Set("apple"), fruits.remove("banana"))
    assertEquals(Set(4, 5, 6), expanded.map(_.length).toSet)
    assertEquals(Some(Set("apple", "banana")), NonEmptySet.fromIterableOption(List("apple", "banana", "apple")).map(_.toSet))
    assertTrue(NonEmptySet.fromIterableOption(List.empty[String]).isEmpty)
  }

  @Test
  def validationAccumulatesErrorsAndPreservesSuccessLogs(): Unit = {
    final case class User(name: String, age: Int)

    def validateName(name: String): ZValidation[String, String, String] = {
      val checkedName: ZValidation[Nothing, String, String] =
        if (name.nonEmpty) ZValidation.succeed(name) else ZValidation.fail("name is empty")

      checkedName.log("checked name")
    }

    def validateAge(age: Int): ZValidation[String, String, Int] = {
      val checkedAge: ZValidation[Nothing, String, Int] =
        if (age > 0) ZValidation.succeed(age) else ZValidation.fail("age must be positive")

      checkedAge.log("checked age")
    }

    val valid: ZValidation[String, String, User] =
      ZValidation.validateWith(validateName("Ada"), validateAge(42))(User.apply)
    val (validLog, validResult) = valid.runLog

    assertEquals(List("checked name", "checked age"), validLog.toList)
    assertEquals(Right(User("Ada", 42)), validResult)
    assertEquals(Some(User("Ada", 42)), valid.toOption)

    val invalid: ZValidation[String, String, User] =
      ZValidation.validateWith(validateName(""), validateAge(0))(User.apply)
    val (invalidLog, invalidResult) = invalid.runLog

    assertEquals(List("checked name", "checked age"), invalidLog.toList)
    val invalidEither: Either[List[String], User] = invalidResult match {
      case Left(errors) => Left(errors.toList)
      case Right(user)  => Right(user)
    }

    assertEquals(Left(List("name is empty", "age must be positive")), invalidEither)
    assertEquals(User("fallback", 1), invalid.getOrElse(User("fallback", 1)))
  }

  @Test
  def optionalProvidesOptionLikeCombinatorsWithExplicitAbsence(): Unit = {
    val present: Optional[Int] = Optional.Present(21)
    val absent: Optional[Int] = Optional.Absent

    assertTrue(present.isDefined)
    assertFalse(absent.isDefined)
    assertEquals(Some(21), present.toOption)
    assertEquals(None, absent.toOption)
    assertEquals(Optional.Present(42), present.map(_ * 2))
    assertEquals(Optional.Absent, present.filter(_ < 10))
    assertEquals(Optional.Present("large"), present.collect { case n if n > 20 => "large" })
    assertEquals(Optional.Present(99), absent.orElse(Optional.Present(99)))
    assertEquals(Right(21), present.toRight("missing"))
    assertEquals(Left("missing"), absent.toRight("missing"))
    assertEquals(List(21), present.toList)
    assertEquals(List.empty[Int], absent.toList)
  }

  @Test
  def theseModelsInclusiveOrValues(): Unit = {
    val left: These[String, Int] = These.left("warning")
    val right: These[String, Int] = These.right(7)
    val both: These[String, Int] = These.both("warning", 7)

    assertTrue(left.isLeft)
    assertTrue(right.isRight)
    assertTrue(both.isBoth)
    assertEquals(These.both("WARNING", 8), both.bimap(_.toUpperCase, _ + 1))
    assertEquals(These.both("warning", 14), both.map(_ * 2))
    assertEquals(These.both(7, "warning"), both.flip)
    assertEquals(Left("warning"), left.toEither)
    assertEquals(Right(7), right.toEither)
    assertEquals(Right(7), both.toEither)
    assertEquals(Some(7), both.toOption)
    assertEquals("both warning 7", both.fold(e => s"left $e", a => s"right $a")((e, a) => s"both $e $a"))
  }

  @Test
  def zSetBehavesAsAMultisetWithTransformations(): Unit = {
    val inventory: ZSet[String, Int] = ZSet.fromIterable(List("apple", "banana", "apple", "pear"))
    val extra: ZSet[String, Int] = ZSet.fromMap(Map("apple" -> 2, "orange" -> 3))

    assertEquals(Map("apple" -> 2, "banana" -> 1, "pear" -> 1), inventory.toMap)
    assertEquals(Set("apple", "banana", "pear"), inventory.toSet)
    assertEquals(Map("APPLE" -> 2, "BANANA" -> 1, "PEAR" -> 1), inventory.map(_.toUpperCase).toMap)
    assertEquals(Map("apple" -> 20, "banana" -> 10, "pear" -> 10), inventory.transform(_ * 10).toMap)
    assertEquals(Map("apple" -> 2, "banana" -> 1, "pear" -> 1, "orange" -> 3), inventory.union(extra).toMap)
    assertEquals(Map("apple" -> 2), inventory.intersect(extra).toMap)
    assertEquals(2, inventory("apple"))
    assertEquals(0, inventory("missing"))
    assertTrue(inventory.toNonEmptyZSet.isDefined)
    assertTrue(ZSet.empty.toNonEmptyZSet.isEmpty)
  }

  @Test
  def zPureRunsPureStateLogAndFailurePrograms(): Unit = {
    val program: ZPure[String, Int, Int, Any, Nothing, Int] =
      for {
        initial <- ZPure.get[Int]
        _ <- ZPure.log[Int, String](s"initial=$initial")
        _ <- ZPure.update[Int, Int](_ + 5)
        updated <- ZPure.get[Int]
      } yield updated * 2

    val (log, result) = program.runAll(10)

    assertEquals(List("initial=10"), log.toList)
    assertEquals(Right((15, 30)), result)
    assertEquals((15, 30), program.run(10))
    assertEquals(15, program.runState(10))
    assertEquals(30, program.runResult(10))

    val recovered: ZPure[Nothing, Unit, Unit, Any, Nothing, Int] =
      ZPure.fail[String]("not a number").catchAll(message => ZPure.succeed[Unit, Int](message.length))

    assertEquals(12, recovered.run)
  }
}
