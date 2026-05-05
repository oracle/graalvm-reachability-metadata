/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang_modules.scala_collection_compat_3

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import scala.collection.compat.BuildFrom
import scala.collection.compat.Factory
import scala.collection.compat.IterableOnce
import scala.collection.compat.immutable.ArraySeq
import scala.collection.compat.immutable.LazyList
import scala.util.control.compat.ControlThrowable

class Scala_collection_compat_3Test {
  @Test
  def factoryAliasesBuildStandardCollectionResults(): Unit = {
    val vectorFactory: Factory[Int, Vector[Int]] = Vector
    val vectorBuilder: scala.collection.mutable.Builder[Int, Vector[Int]] = vectorFactory.newBuilder
    vectorBuilder ++= Iterator(1, 2, 3, 5)

    val builtVector: Vector[Int] = vectorBuilder.result()
    val builtString: String = Iterator('c', 'o', 'm', 'p', 'a', 't').to(Factory.stringFactory)

    assertEquals(Vector(1, 2, 3, 5), builtVector)
    assertEquals("compat", builtString)
  }

  @Test
  def buildFromAliasCreatesBuildersForStringsIterablesAndMaps(): Unit = {
    val stringBuildFrom: BuildFrom[String, Char, String] = BuildFrom.buildFromString
    val stringBuilder: scala.collection.mutable.Builder[Char, String] = stringBuildFrom.newBuilder("seed")
    stringBuilder ++= List('s', 'c', 'a', 'l', 'a')

    val listBuildFrom: BuildFrom[List[Int], String, List[String]] = summon[BuildFrom[List[Int], String, List[String]]]
    val listBuilder: scala.collection.mutable.Builder[String, List[String]] = listBuildFrom.newBuilder(List(1, 2, 3))
    listBuilder += "one"
    listBuilder ++= Seq("two", "three")

    val mapBuildFrom: BuildFrom[Map[String, Int], (String, Int), Map[String, Int]] =
      summon[BuildFrom[Map[String, Int], (String, Int), Map[String, Int]]]
    val mapBuilder: scala.collection.mutable.Builder[(String, Int), Map[String, Int]] = mapBuildFrom.newBuilder(Map("ignored" -> 0))
    mapBuilder += "first" -> 1
    mapBuilder += "second" -> 2

    assertEquals("scala", stringBuilder.result())
    assertEquals(List("one", "two", "three"), listBuilder.result())
    assertEquals(Map("first" -> 1, "second" -> 2), mapBuilder.result())
  }

  @Test
  def iterableOnceAliasAcceptsSinglePassAndReusableCollections(): Unit = {
    val listTotal: Int = total(List(1, 2, 3, 4))
    val iteratorTotal: Int = total(Iterator(10, 20, 30))
    val target: Array[Int] = Array.fill(6)(0)
    val copiedCount: Int = List(7, 8, 9).iterator.copyToArray(target, 2, 2)

    assertEquals(10, listTotal)
    assertEquals(60, iteratorTotal)
    assertEquals(2, copiedCount)
    assertArrayEquals(Array(0, 0, 7, 8, 0, 0), target)
  }

  @Test
  def immutableArraySeqAliasProvidesStrictIndexedSequences(): Unit = {
    val fromVarargs: ArraySeq[Int] = ArraySeq(1, 2, 3, 4)
    val fromIterable: ArraySeq[String] = ArraySeq.from(List("red", "green", "blue"))
    val appended: ArraySeq[Int] = fromVarargs :+ 5

    assertEquals(Vector(2, 4, 6, 8), fromVarargs.map(_ * 2).toVector)
    assertEquals("red,green,blue", fromIterable.mkString(","))
    assertEquals(List(1, 2, 3, 4, 5), appended.toList)
  }

  @Test
  def immutableLazyListAliasSupportsLazyFinitePrefixes(): Unit = {
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
  def controlThrowableAliasSupportsBoundedControlFlow(): Unit = {
    val visited: scala.collection.mutable.ListBuffer[Int] = scala.collection.mutable.ListBuffer.empty[Int]
    val totalBeforeStop: Int = sumUntilLimit(List(1, 2, 3, 4, 5), 6, visited)

    assertEquals(6, totalBeforeStop)
    assertEquals(List(1, 2, 3), visited.toList)
  }

  private def total(values: IterableOnce[Int]): Int = values.iterator.sum

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
      case control: ControlThrowable =>
        control match {
          case stop: StopTraversal => stop.partialTotal
        }
    }
  }

  private def fibonacciFrom(first: Int, second: Int): LazyList[Int] = first #:: fibonacciFrom(second, first + second)

  private final class StopTraversal(val partialTotal: Int) extends ControlThrowable
}
