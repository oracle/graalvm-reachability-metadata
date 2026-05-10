/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_specs2.specs2_fp_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.specs2.fp.Applicative
import org.specs2.fp.Foldable
import org.specs2.fp.Memo
import org.specs2.fp.Monad
import org.specs2.fp.Monoid
import org.specs2.fp.Name
import org.specs2.fp.NaturalTransformation
import org.specs2.fp.Need
import org.specs2.fp.Semigroup
import org.specs2.fp.Show
import org.specs2.fp.Traverse
import org.specs2.fp.Tree
import org.specs2.fp.TreeLoc
import org.specs2.fp.Value
import org.specs2.fp.syntax.*
import org.specs2.fp.Id

import scala.collection.immutable.LazyList
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

class Specs2_fp_3Test {

  @Test
  def monadAndApplicativeInstancesComposeStrictAndOptionalValues(): Unit = {
    val optionMonad: Monad[Option] = summon[Monad[Option]]
    val lifted: Option[Int] = optionMonad.point(2)
    val bound: Option[String] = optionMonad.bind(lifted)(i => Some(s"value-${i + 1}"))
    val joined: Option[Int] = optionMonad.join(Some(Some(7)))
    val tailRecursive: Option[String] = optionMonad.tailrecM(1) { i =>
      Some(if i < 4 then Left(i + 1) else Right(s"done-$i"))
    }

    assertEquals(Some("value-3"), bound)
    assertEquals(Some(7), joined)
    assertEquals(Some("done-4"), tailRecursive)
    assertEquals(None, optionMonad.bind(None: Option[Int])(_ => Some("unreachable")))

    val optionApplicative: Applicative[Option] = Applicative[Option]
    val applied: Option[Int] = optionApplicative.ap(Some(3))(Some((i: Int) => i + 4))
    val tupled: Option[(Int, String, Boolean)] = optionApplicative.tuple3(Some(1), Some("two"), Some(true))
    val liftedSum: Option[Int] = optionApplicative.lift3((a: Int, b: Int, c: Int) => a + b + c)(Some(1), Some(2), Some(3))
    val filtered: Option[List[Int]] = optionApplicative.filterM(List(1, 2, 3, 4))(i => Some(i % 2 == 0))

    assertEquals(Some(7), applied)
    assertEquals(Some((1, "two", true)), tupled)
    assertEquals(Some(6), liftedSum)
    assertEquals(Some(List(2, 4)), filtered)
    assertEquals(Some(()), optionApplicative.when(false)(throw new AssertionError("should not evaluate")))
    assertEquals(Some(()), optionApplicative.unless(true)(throw new AssertionError("should not evaluate")))
  }

  @Test
  def monadIterationRepeatsActionsUntilPredicatesChange(): Unit = {
    val nameMonad: Monad[Name] = summon[Monad[Name]]

    var untilCalls: Int = 0
    val untilValue: Name[Int] = nameMonad.iterateUntil(Name {
      untilCalls += 1
      untilCalls
    })(_ >= 3)

    assertEquals(3, untilValue.value)
    assertEquals(3, untilCalls)

    var whileCalls: Int = 0
    val whileValue: Name[Int] = nameMonad.iterateWhile(Name {
      whileCalls += 1
      whileCalls
    })(_ < 4)

    assertEquals(4, whileValue.value)
    assertEquals(4, whileCalls)
  }

  @Test
  def syntaxExtensionsDelegateToFunctorApplicativeMonadAndShowTypeClasses(): Unit = {
    val mapped: Option[Int] = Option(4).map(_ + 1)
    val replaced: Option[String] = Option(4).as("constant")
    val voided: Option[Unit] = Option(4).void
    val combined: Option[String] = Option(2).|@|(Option(3))((a, b) => s"$a+$b=${a + b}")
    val second: Option[String] = Option(1).*>(Option("kept"))
    val monadic: Option[Int] = Option(3).>>=(i => Some(i * 2))

    assertEquals(Some(5), mapped)
    assertEquals(Some("constant"), replaced)
    assertEquals(Some(()), voided)
    assertEquals(Some("2+3=5"), combined)
    assertEquals(Some("kept"), second)
    assertEquals(Some(6), monadic)

    given Show[String] = Show.show(value => s"<$value>")
    assertEquals("<visible>", "visible".show)
    assertEquals("42", Show[Int].show(42))
  }

  @Test
  def semigroupsAndMonoidsAppendMultiplyAndMergeCollections(): Unit = {
    given Semigroup[String] = Monoid.stringMonoid

    val commaSeparated: Semigroup[String] = Semigroup.instance((left, right) => s"$left,$right")
    val stringMonoid: Monoid[String] = Monoid[String]
    val intMonoid: Monoid[Int] = Monoid[Int]
    val listMonoid: Monoid[List[Int]] = Monoid[List[Int]]
    val mapMonoid: Monoid[Map[String, Int]] = Monoid.mapMonoid[String, Int]

    assertEquals("a,b", commaSeparated.append("a", "b"))
    assertEquals("x,x,x", commaSeparated.multiply1("x", 2))
    assertEquals("ababab", stringMonoid.multiply("ab", 3))
    assertEquals(0, intMonoid.zero)
    assertEquals(10, intMonoid.append(4, 6))
    assertEquals(List(1, 2, 3), listMonoid.append(List(1), List(2, 3)))
    assertEquals(Map("a" -> 3, "b" -> 5), mapMonoid.append(Map("a" -> 1), Map("a" -> 2, "b" -> 5)))
    assertEquals("leftright", "left".|+|("right"))
  }

  @Test
  def foldableInstancesFoldSearchConvertAndTraverseCollections(): Unit = {
    val foldableList: Foldable[List] = Foldable[List]
    val values: List[Int] = List(1, 2, 3, 4)

    assertEquals(10, foldableList.foldMap(values)(identity))
    assertEquals("start-1-2-3-4", foldableList.foldLeft(values, "start")((acc, i) => s"$acc-$i"))
    assertEquals(Some(2), foldableList.findLeft(values)(_ % 2 == 0))
    assertEquals(Some(3), foldableList.findRight(values)(_ % 2 == 1))
    assertEquals(4, foldableList.count(values))
    assertEquals(Some(3), foldableList.index(values, 2))
    assertEquals(99, foldableList.indexOr(values, 99, 40))
    assertEquals(Vector(1, 2, 3, 4), foldableList.toVector(values))
    assertEquals(Set(1, 2, 3, 4), foldableList.toSet(values))
    assertEquals(List(1, 2, 3, 4), foldableList.toStream(values).toList)
    assertTrue(foldableList.all(values)(_ > 0))
    assertTrue(foldableList.any(values)(_ == 3))
    assertFalse(foldableList.empty(values))
    assertTrue(foldableList.empty(List.empty[Int]))
    assertEquals("a,b,c", foldableList.intercalate(List("a", "b", "c"), ","))

    val foldedOption: Option[Int] = values.foldLeftM(0)((acc: Int, value: Int) => Option(acc + value))
    val traversedOption: Option[Unit] = values.traverse_((value: Int) => Option(value * 2))
    assertEquals(Some(10), foldedOption)
    assertEquals(Some(()), traversedOption)
    assertEquals(10, values.sumAll)
  }

  @Test
  def traverseInstancesSequenceEffectsAcrossListsOptionsAndEitherValues(): Unit = {
    val listTraverse: Traverse[List] = summon[Traverse[List]]
    val optionTraverse: Traverse[Option] = summon[Traverse[Option]]
    val eitherTraverse: Traverse[[A] =>> Either[String, A]] = summon[Traverse[[A] =>> Either[String, A]]]

    val traversedList: Option[List[String]] = listTraverse.traverse(List(1, 2, 3))((i: Int) => Option(s"n$i"))
    val failedList: Option[List[Int]] = listTraverse.traverse(List(1, -1, 3))((i: Int) => if i > 0 then Option(i) else None)
    val sequencedList: Option[List[Int]] = listTraverse.sequence(List[Option[Int]](Some(1), Some(2), Some(3)))
    val traversedOption: Either[String, Option[Int]] = optionTraverse.traverse(Some(5))((i: Int) => Right(i + 1): Either[String, Int])
    val traversedLeft: Option[Either[String, Int]] = eitherTraverse.traverse(Left("stop"): Either[String, Int])((i: Int) => Option(i + 1))
    val syntaxTraverse: Option[List[Int]] = List(1, 2, 3).traverse((i: Int) => Option(i * 10))
    val syntaxSequence: Option[List[Int]] = List[Option[Int]](Some(1), Some(2)).sequence

    assertEquals(Some(List("n1", "n2", "n3")), traversedList)
    assertEquals(None, failedList)
    assertEquals(Some(List(1, 2, 3)), sequencedList)
    assertEquals(Right(Some(6)), traversedOption)
    assertEquals(Some(Left("stop")), traversedLeft)
    assertEquals(Some(List(10, 20, 30)), syntaxTraverse)
    assertEquals(Some(List(1, 2)), syntaxSequence)
  }

  @Test
  def eitherSyntaxProvidesCatsLikeOperationsWithoutUsingReflection(): Unit = {
    val right: Either[String, Int] = Right(2)
    val left: Either[String, Int] = Left("bad")
    val eitherApplicative: Applicative[[A] =>> Either[String, A]] = summon[Applicative[[A] =>> Either[String, A]]]

    assertEquals(Right(2), Right(2): Either[String, Int])
    assertEquals(Left("left"), Left("left"): Either[String, Int])
    assertEquals(Right("present"), Option("present").toRight("missing"))
    assertEquals(Left("missing"), Option.empty[String].toRight("missing"))
    assertLeftThrowable(scala.util.Try("not-a-number".toInt).toEither, _.isInstanceOf[NumberFormatException])
    assertEquals(Right(3), Success(3).toEither)
    assertLeftThrowable(Failure(new IllegalStateException("boom")).toEither, _.isInstanceOf[IllegalStateException])

    assertEquals(Right(3), right.map(_ + 1))
    assertEquals(Right("value-2"), right.bimap(_.toUpperCase, i => s"value-$i"))
    assertEquals(Right(4), right.flatMap(i => Right(i * 2)))
    assertEquals(Right(5), eitherApplicative.ap(right)(Right((i: Int) => i + 3)))
    assertEquals(Right(5), right.append(Right(3))(using intAdditionSemigroup))
    assertEquals(Right(2), right.ensure("too-small")(_ >= 2))
    assertEquals(Some(2), right.toOption)
    assertEquals(List(2), right.toList)
    assertEquals(2, right.getOrElse(0))
    assertEquals(2, right.valueOr(_.length))
    assertEquals(4, right.foldLeft(2)(_ * _))
    assertEquals("Right(2)", right.show(using Show.showFromToString[String], Show.showFromToString[Int]))

    assertEquals(Left("BAD"), left.leftMap(_.toUpperCase))
    assertEquals(Right(7), left.recover { case "bad" => 7 })
    assertEquals(Right(8), left.recoverWith { case "bad" => Right(8) })
    assertEquals(Right(9), left.orElse(Right(9)))
    assertEquals(3, left.valueOr(_.length))
    assertEquals(Left("bad"), left.append(Right(3))(using intAdditionSemigroup))
    assertEquals(Left("bad"), left.ensure("unused")(_ => false))
    assertEquals(None, left.toOption)
    assertEquals(Nil, left.toList)
  }

  @Test
  def nameNeedValueAndMemoControlEvaluationAndCaching(): Unit = {
    var nameEvaluations: Int = 0
    val name: Name[Int] = Name {
      nameEvaluations += 1
      nameEvaluations
    }
    assertEquals(1, name.value)
    assertEquals(2, name.value)

    var needEvaluations: Int = 0
    val need: Need[Int] = Need {
      needEvaluations += 1
      needEvaluations * 10
    }
    assertEquals(10, need.value)
    assertEquals(10, need.value)
    assertEquals(1, needEvaluations)

    val value: Value[String] = Value("cached")
    assertEquals("cached", value.value)
    assertEquals("cached", Value.unapply(value).value)
    assertEquals(Some(10), Need.unapply(need))
    assertEquals(3, summon[Monad[Name]].bind(Name(1))(i => Name(i + 2)).value)

    var mutableCalls: Int = 0
    val mutableMemoized: Int => String = Memo.mutableHashMapMemo[Int, String].apply { key =>
      mutableCalls += 1
      s"value-$key-$mutableCalls"
    }
    assertEquals("value-1-1", mutableMemoized(1))
    assertEquals("value-1-1", mutableMemoized(1))
    assertEquals(1, mutableCalls)

    var immutableCalls: Int = 0
    val immutableMemoized: Int => Int = Memo.immutableTreeMapMemo[Int, Int].apply { key =>
      immutableCalls += 1
      key * 10 + immutableCalls
    }
    assertEquals(21, immutableMemoized(2))
    assertEquals(21, immutableMemoized(2))
    assertEquals(1, immutableCalls)

    var nilCalls: Int = 0
    val notMemoized: Int => Int = Memo.nilMemo[Int, Int].apply { key =>
      nilCalls += 1
      key + nilCalls
    }
    assertEquals(6, notMemoized(5))
    assertEquals(7, notMemoized(5))
  }

  @Test
  def treeSupportsTraversalDrawingMappingBindingAndZipperEditing(): Unit = {
    val tree: Tree[String] = Tree.Node(
      "root",
      LazyList(
        Tree.Node("left", LazyList(Tree.Leaf("left.leaf"))),
        Tree.Leaf("right")
      )
    )

    assertEquals("root", tree.rootLabel)
    assertEquals(List("root", "left", "left.leaf", "right"), tree.flatten.toList)
    assertEquals(4, tree.size)
    assertEquals(List(List("root"), List("left", "right"), List("left.leaf")), tree.levels.map(_.toList).toList)
    assertTrue(tree.drawTree(using Show.showFromToString[String]).contains("left.leaf"))
    assertEquals(List(4, 4, 9, 5), tree.map(_.length).flatten.toList)
    assertEquals(22, tree.foldMap(_.length))
    assertEquals("root/left/left.leaf/right/end", tree.foldRight("end")((label, rest) => s"$label/$rest"))

    val scanned: Tree[Int] = tree.scanr((label, children) => label.length + children.map(_.rootLabel).sum)
    assertEquals(22, scanned.rootLabel)

    val duplicatedLabels: Tree[String] = tree.flatMap(label => Tree.Node(label.toUpperCase, LazyList(Tree.Leaf(label.toLowerCase))))
    assertEquals(List("ROOT", "root", "LEFT", "left", "LEFT.LEAF", "left.leaf", "RIGHT", "right"), duplicatedLabels.flatten.toList)

    val loc: TreeLoc[String] = tree.loc
    val leftChild: TreeLoc[String] = loc.firstChild.getOrElse(failLocation("missing left child"))
    val rightChild: TreeLoc[String] = leftChild.right.getOrElse(failLocation("missing right child"))
    val editedRoot: TreeLoc[String] = rightChild
      .setLabel("right-updated")
      .insertLeft(Tree.Leaf("middle"))
      .root

    assertTrue(loc.isRoot)
    assertTrue(leftChild.isChild)
    assertTrue(leftChild.hasChildren)
    assertEquals(LazyList("left", "root").toList, leftChild.path.toList)
    assertEquals("right", rightChild.getLabel)
    assertEquals(List("root", "left", "left.leaf", "middle", "right-updated"), editedRoot.toTree.flatten.toList)
    assertEquals(Some("left"), loc.findChild(_.rootLabel == "left").map(_.getLabel))
    assertEquals(Some("left.leaf"), loc.find(_.getLabel == "left.leaf").map(_.getLabel))
    assertEquals(List("root"), loc.toForest.map(_.rootLabel).toList)
    assertEquals(List(4, 4, 9, 5), loc.map(_.length).toTree.flatten.toList)
  }

  @Test
  def treeCobindAndUnzipCreateContextAwareAndSplitTrees(): Unit = {
    val tree: Tree[String] = Tree.Node(
      "root",
      LazyList(
        Tree.Node("left", LazyList(Tree.Leaf("left.leaf"))),
        Tree.Leaf("right")
      )
    )

    val contextSizes: Tree[String] = tree.cobind(subtree => s"${subtree.rootLabel}:${subtree.size}")
    val labelled: Tree[(String, Int)] = tree.map(label => (label, label.length))
    val unzipped: (Tree[String], Tree[Int]) = labelled.unzip(using (pair: (String, Int)) => pair)

    assertEquals(List("root:4", "left:2", "left.leaf:1", "right:1"), contextSizes.flatten.toList)
    assertEquals(List("root", "left", "left.leaf", "right"), unzipped._1.flatten.toList)
    assertEquals(List(4, 4, 9, 5), unzipped._2.flatten.toList)
  }

  @Test
  def unfoldBuildsTreesAndForestsFromSeeds(): Unit = {
    val unfolded: Tree[Int] = Tree.unfoldTree(1) { seed =>
      val children: LazyList[Int] = if seed < 3 then LazyList(seed + 1, seed + 2) else LazyList.empty
      (seed, () => children)
    }
    val unfoldedForest: LazyList[Tree[Int]] = Tree.unfoldForest(LazyList(1, 2)) { seed =>
      (seed * 10, () => LazyList.empty[Int])
    }
    val counted: LazyList[Int] = TreeLoc.unfold(0)(i => if i < 3 then Some((i + 1, i + 1)) else None)

    assertEquals(List(1, 2, 3, 4, 3), unfolded.flatten.toList)
    assertEquals(List(10, 20), unfoldedForest.map(_.rootLabel).toList)
    assertEquals(List(1, 2, 3), counted.toList)
  }

  @Test
  def futureInstancesAndNaturalTransformationsProduceExpectedValuesWithBoundedAwait(): Unit = {
    given ExecutionContext = ExecutionContext.fromExecutor((command: Runnable) => command.run())

    val futureMonad: Monad[Future] = summon[Monad[Future]]
    val futureApplicative: Applicative[Future] = summon[Applicative[Future]]
    val futureValue: Future[Int] = futureMonad.bind(Future.successful(4))(i => Future.successful(i * 3))
    val futureTuple: Future[(String, Int)] = futureApplicative.tuple2(Future.successful("age"), Future.successful(42))

    assertEquals(12, Await.result(futureValue, 5.seconds))
    assertEquals(("age", 42), Await.result(futureTuple, 5.seconds))

    val natural: NaturalTransformation[Id, Option] = summon[NaturalTransformation[Id, Option]]
    assertEquals(Some("lifted"), natural.apply("lifted"))
  }

  private val intAdditionSemigroup: Semigroup[Int] = new Semigroup[Int] {
    override def append(left: Int, right: => Int): Int = left + right
  }

  private def assertLeftThrowable(either: Either[Throwable, ?], expected: Throwable => Boolean): Unit =
    either match {
      case Left(error) => assertTrue(expected(error), s"unexpected error: $error")
      case Right(value) => throw new AssertionError(s"expected a Left but got Right($value)")
    }

  private def failLocation[A](message: String): A =
    throw new AssertionError(message)
}
