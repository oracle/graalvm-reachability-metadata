/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang_modules.scala_collection_compat_2_13

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scala.collection.compat.BuildFrom
import scala.collection.compat.Factory
import scala.collection.compat.IterableOnce
import scala.collection.compat.immutable.ArraySeq
import scala.collection.compat.immutable.LazyList
import scala.collection.immutable.Queue
import scala.util.control.compat.ControlThrowable
import scala.util.matching.compat.Regex

class Scala_collection_compat_2_13Test {
  @Test
  def factoryAliasesCreateStandardCollectionBuilders(): Unit = {
    val vectorFactory: Factory[Int, Vector[Int]] = Vector
    val vectorBuilder: scala.collection.mutable.Builder[Int, Vector[Int]] = vectorFactory.newBuilder
    vectorBuilder += 1
    vectorBuilder ++= Iterator(2, 3, 5)

    val stringBuilder: scala.collection.mutable.Builder[Char, String] = Factory.stringFactory.newBuilder
    stringBuilder ++= List('c', 'o', 'm', 'p', 'a', 't')

    val arrayBuilder: scala.collection.mutable.Builder[String, Array[String]] = Factory.arrayFactory[String].newBuilder
    arrayBuilder += "alpha"
    arrayBuilder ++= Vector("beta", "gamma")

    assertEquals(Vector(1, 2, 3, 5), vectorBuilder.result())
    assertEquals("compat", stringBuilder.result())
    assertEquals(Vector("alpha", "beta", "gamma"), arrayBuilder.result().toVector)
  }

  @Test
  def buildFromAliasBuildsResultsForSourceCollectionShapes(): Unit = {
    val stringBuildFrom: BuildFrom[String, Char, String] = BuildFrom.buildFromString
    val stringBuilder: scala.collection.mutable.Builder[Char, String] = stringBuildFrom.newBuilder("seed")
    stringBuilder ++= Seq('s', 'c', 'a', 'l', 'a')

    val arrayBuildFrom: BuildFrom[Array[Int], Int, Array[Int]] = BuildFrom.buildFromArray[Int]
    val arrayBuilder: scala.collection.mutable.Builder[Int, Array[Int]] = arrayBuildFrom.newBuilder(Array(0))
    arrayBuilder ++= List(8, 13, 21)

    val listBuildFrom: BuildFrom[List[Int], String, List[String]] = implicitly[BuildFrom[List[Int], String, List[String]]]
    val listBuilder: scala.collection.mutable.Builder[String, List[String]] = listBuildFrom.newBuilder(List(1, 2, 3))
    listBuilder += "one"
    listBuilder ++= Iterator("two", "three")

    val mapBuildFrom: BuildFrom[Map[String, Int], (String, Int), Map[String, Int]] =
      implicitly[BuildFrom[Map[String, Int], (String, Int), Map[String, Int]]]
    val mapBuilder: scala.collection.mutable.Builder[(String, Int), Map[String, Int]] =
      mapBuildFrom.newBuilder(Map("ignored" -> 0))
    mapBuilder += "first" -> 1
    mapBuilder += "second" -> 2

    assertEquals("scala", stringBuilder.result())
    assertArrayEquals(Array(8, 13, 21), arrayBuilder.result())
    assertEquals(List("one", "two", "three"), listBuilder.result())
    assertEquals(Map("first" -> 1, "second" -> 2), mapBuilder.result())
  }

  @Test
  def buildFromAliasTransformsSourcesWithDirectFactoryOperations(): Unit = {
    val source: Queue[Int] = Queue(2, 3, 5)
    val queueBuildFrom: BuildFrom[Queue[Int], String, Queue[String]] =
      implicitly[BuildFrom[Queue[Int], String, Queue[String]]]

    val labels: Queue[String] =
      queueBuildFrom.fromSpecific(source)(source.iterator.map(value => s"prime-${value * value}"))
    val queueFactory: Factory[String, Queue[String]] = queueBuildFrom.toFactory(source)
    val rebuilt: Queue[String] = queueFactory.fromSpecific(Iterator("left", "right"))

    assertEquals(Queue("prime-4", "prime-9", "prime-25"), labels)
    assertEquals(Queue("left", "right"), rebuilt)
  }

  @Test
  def iterableOnceAliasHandlesReusableAndSinglePassSources(): Unit = {
    val reusableTotal: Int = sumIterableOnce(List(1, 2, 3, 4))
    val singlePassTotal: Int = sumIterableOnce(Iterator(10, 20, 30))
    val target: Array[Int] = Array.fill(6)(0)
    val copiedCount: Int = Iterator(7, 8, 9).copyToArray(target, 2, 2)

    assertEquals(10, reusableTotal)
    assertEquals(60, singlePassTotal)
    assertEquals(2, copiedCount)
    assertArrayEquals(Array(0, 0, 7, 8, 0, 0), target)
  }

  @Test
  def immutableArraySeqAliasProvidesStrictIndexedSequences(): Unit = {
    val fromVarargs: ArraySeq[Int] = ArraySeq(1, 2, 3, 4)
    val fromIterable: ArraySeq[String] = ArraySeq.from(List("red", "green", "blue"))
    val appended: ArraySeq[Int] = fromVarargs :+ 5
    val collected: ArraySeq[String] = fromVarargs.collect {
      case value if value % 2 == 0 => s"even-$value"
    }

    assertEquals(Vector(2, 4, 6, 8), fromVarargs.map(_ * 2).toVector)
    assertEquals("red,green,blue", fromIterable.mkString(","))
    assertEquals(List(1, 2, 3, 4, 5), appended.toList)
    assertEquals(ArraySeq("even-2", "even-4"), collected)
  }

  @Test
  def immutableLazyListAliasEvaluatesOnlyDemandedElements(): Unit = {
    val naturals: LazyList[Int] = LazyList.iterate(1)(_ + 1)
    val fibonacci: LazyList[Int] = fibonacciFrom(0, 1)
    var evaluated: Int = 0
    val measured: LazyList[Int] = LazyList.from(1).map { value =>
      evaluated += 1
      value * 10
    }

    assertEquals(List(1, 2, 3, 4, 5), naturals.take(5).toList)
    assertEquals(List(0, 1, 1, 2, 3, 5, 8), fibonacci.take(7).toList)
    assertEquals(0, evaluated)
    assertEquals(List(10, 20, 30), measured.take(3).toList)
    assertEquals(3, evaluated)
  }

  @Test
  def controlThrowableAliasSupportsNonLocalBoundedControlFlow(): Unit = {
    val visited: scala.collection.mutable.ListBuffer[Int] = scala.collection.mutable.ListBuffer.empty[Int]
    val totalBeforeStop: Int = sumUntilLimit(List(1, 2, 3, 4, 5), 6, visited)

    assertEquals(6, totalBeforeStop)
    assertEquals(List(1, 2, 3), visited.toList)
  }

  @Test
  def regexAliasMatchesExtractsAndQuotesPatterns(): Unit = {
    val productPattern: Regex = raw"item-(\d+):([a-z]+)".r
    val input: String = "item-123:book item-45:pen ignored"
    val matches: List[(String, String)] = productPattern.findAllMatchIn(input).map { matched =>
      matched.group(1) -> matched.group(2)
    }.toList
    val quotedPattern: Regex = Regex.quote("total(1.0)").r

    val Product: Regex = productPattern
    val extracted: Option[(String, String)] = "item-77:pencil" match {
      case Product(id, name) => Some(id -> name)
      case _ => None
    }

    assertEquals(List("123" -> "book", "45" -> "pen"), matches)
    assertEquals(Some("77" -> "pencil"), extracted)
    assertTrue(quotedPattern.matches("total(1.0)"))
    assertFalse(quotedPattern.matches("total-1x0"))
  }

  private def sumIterableOnce(values: IterableOnce[Int]): Int = values.iterator.sum

  private def sumUntilLimit(
      values: List[Int],
      limit: Int,
      visited: scala.collection.mutable.ListBuffer[Int]
  ): Int = {
    var total: Int = 0
    try {
      values.foreach { value =>
        total += value
        visited += value
        if (total >= limit) {
          throw new StopTraversal(total)
        }
      }
      total
    } catch {
      case stop: StopTraversal => stop.partialTotal
      case control: ControlThrowable => throw control
    }
  }

  private def fibonacciFrom(first: Int, second: Int): LazyList[Int] = first #:: fibonacciFrom(second, first + second)

  private final class StopTraversal(val partialTotal: Int) extends ControlThrowable
}
