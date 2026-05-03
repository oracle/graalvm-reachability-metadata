/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sangria_graphql.macro_visit_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNotSame, assertNull, assertSame}
import org.junit.jupiter.api.Test
import sangria.visitor.*
import sangria.visitor.VisitorCommand.*

sealed trait TestTree
case class Leaf(value: Int, note: String = "leaf") extends TestTree
case class Pair(left: TestTree, right: TestTree, label: String = "pair") extends TestTree
case class Many(
    list: List[TestTree],
    vector: Vector[TestTree],
    seq: Seq[TestTree],
    maybe: Option[TestTree],
    label: String = "many")
    extends TestTree
case class Special(child: TestTree, weight: Int, name: String) extends TestTree
case class Box(child: TestTree, width: Int, height: Int, label: String) extends TestTree

class Macro_visit_3Test {
  @Test
  def visitTransformsRecursiveChildrenAndNamedSpecialFields(): Unit = {
    val root: TestTree = Many(
      list = List(Leaf(1), Pair(Leaf(2), Leaf(3), "inner")),
      vector = Vector(Leaf(4)),
      seq = Seq(Leaf(5)),
      maybe = Some(Special(Leaf(6), weight = 7, name = "tag")),
      label = "root"
    )

    val transformed: TestTree = visit[TestTree](
      root,
      Visit[Leaf](leaf => Transform(leaf.copy(value = leaf.value + 10))),
      VisitAnyFieldByName[Special, Int]("weight", (_, weight) => Transform(weight * 3)),
      VisitAnyFieldByName[Special, String]("name", (_, name) => Transform(name.toUpperCase))
    )

    assertEquals(
      Many(
        list = List(Leaf(11), Pair(Leaf(12), Leaf(13), "inner")),
        vector = Vector(Leaf(14)),
        seq = Vector(Leaf(15)),
        maybe = Some(Special(Leaf(16), weight = 21, name = "TAG")),
        label = "root"
      ),
      transformed
    )
    assertNotSame(root, transformed)
  }

  @Test
  def visitDeletesElementsFromSupportedCollectionAndOptionFields(): Unit = {
    val root: TestTree = Many(
      list = List(Leaf(1), Leaf(2), Leaf(3), Leaf(4)),
      vector = Vector(Leaf(5), Leaf(6)),
      seq = Seq(Leaf(7), Leaf(8)),
      maybe = Some(Leaf(10)),
      label = "delete-even-leaves"
    )

    val transformed: TestTree = visit[TestTree](
      root,
      Visit[Leaf](leaf => if leaf.value % 2 == 0 then Delete else Continue)
    )

    assertEquals(
      Many(
        list = List(Leaf(1), Leaf(3)),
        vector = Vector(Leaf(5)),
        seq = Vector(Leaf(7)),
        maybe = None,
        label = "delete-even-leaves"
      ),
      transformed
    )
  }

  @Test
  def visitAnyFieldTransformsAllMatchingFieldsWithoutFieldNames(): Unit = {
    val root: TestTree = Box(Leaf(1), width = 2, height = 3, label = "wide")

    val transformed: TestTree = visit[TestTree](
      root,
      VisitAnyField[Box, Int]((box, value) => Transform(value + box.label.length))
    )

    assertEquals(Box(Leaf(1), width = 6, height = 7, label = "wide"), transformed)
  }

  @Test
  def skipPreventsTraversalOfChildrenButStillRunsLeaveHandler(): Unit = {
    val events: scala.collection.mutable.ArrayBuffer[String] = scala.collection.mutable.ArrayBuffer.empty
    val root: TestTree = Pair(Leaf(1), Leaf(2), "guarded")

    val transformed: TestTree = visit[TestTree](
      root,
      Visit[Pair](
        pair => {
          events += s"enter:${pair.label}"
          Skip
        },
        pair => {
          events += s"leave:${pair.label}"
          Continue
        }
      ),
      Visit[Leaf](leaf => {
        events += s"leaf:${leaf.value}"
        Transform(leaf.copy(value = leaf.value + 100))
      })
    )

    assertEquals(root, transformed)
    assertEquals(List("enter:guarded", "leave:guarded"), events.toList)
  }

  @Test
  def breakStopsTraversalAfterTheCurrentNodeFinishes(): Unit = {
    val enteredLeaves: scala.collection.mutable.ArrayBuffer[Int] = scala.collection.mutable.ArrayBuffer.empty
    val leftLeaves: scala.collection.mutable.ArrayBuffer[Int] = scala.collection.mutable.ArrayBuffer.empty
    val root: TestTree = Many(
      list = List(Leaf(1), Leaf(2), Leaf(3)),
      vector = Vector.empty,
      seq = Seq.empty,
      maybe = None,
      label = "break-after-first"
    )

    val transformed: TestTree = visit[TestTree](
      root,
      Visit[Leaf](
        leaf => {
          enteredLeaves += leaf.value
          Break
        },
        leaf => {
          leftLeaves += leaf.value
          Continue
        }
      )
    )

    assertEquals(root, transformed)
    assertEquals(List(1), enteredLeaves.toList)
    assertEquals(List(1), leftLeaves.toList)
  }

  @Test
  def deleteAndBreakDeletesCurrentNodeAndStopsTraversalOfRemainingSiblings(): Unit = {
    val enteredLeaves: scala.collection.mutable.ArrayBuffer[Int] = scala.collection.mutable.ArrayBuffer.empty
    val leftLeaves: scala.collection.mutable.ArrayBuffer[Int] = scala.collection.mutable.ArrayBuffer.empty
    val root: TestTree = Many(
      list = List(Leaf(1), Leaf(2), Leaf(3)),
      vector = Vector.empty,
      seq = Seq.empty,
      maybe = None,
      label = "delete-and-break"
    )

    val transformed: TestTree = visit[TestTree](
      root,
      Visit[Leaf](
        leaf => {
          enteredLeaves += leaf.value
          if leaf.value == 2 then DeleteAndBreak else Continue
        },
        leaf => {
          leftLeaves += leaf.value
          Continue
        }
      )
    )

    assertEquals(
      Many(
        list = List(Leaf(1), Leaf(3)),
        vector = Vector.empty,
        seq = Seq.empty,
        maybe = None,
        label = "delete-and-break"
      ),
      transformed
    )
    assertEquals(List(1, 2), enteredLeaves.toList)
    assertEquals(List(1, 2), leftLeaves.toList)
  }

  @Test
  def leaveTransformObservesChildrenUpdatedEarlierInTraversal(): Unit = {
    val root: TestTree = Pair(Leaf(2), Leaf(3), "sum")

    val transformed: TestTree = visit[TestTree](
      root,
      Visit[Leaf](
        _ => Continue,
        leaf => Transform(leaf.copy(value = leaf.value * 2))
      ),
      Visit[Pair](
        _ => Continue,
        pair => {
          val sum: Int = (pair.left, pair.right) match {
            case (Leaf(left, _), Leaf(right, _)) => left + right
            case other => throw new IllegalStateException(s"Unexpected children: $other")
          }
          Transform(pair.copy(label = s"${pair.label}:$sum"))
        }
      )
    )

    assertEquals(Pair(Leaf(4), Leaf(6), "sum:10"), transformed)
  }

  @Test
  def visitorCommandAndTransformerDataTypesExposeStablePublicApi(): Unit = {
    val enter: Leaf => VisitorCommand = leaf => Transform(leaf.copy(value = leaf.value + 1), Skip)
    val leave: Leaf => VisitorCommand = _ => Continue
    val visitLeaf: Visit[Leaf] = Visit(enter, leave)
    val defaultLeaveVisit: Visit[Leaf] = Visit(_ => Skip)
    val anyField: VisitAnyField[Special, Int] = VisitAnyField((_, weight) => Transform(weight + 1))
    val namedField: VisitAnyFieldByName[Special, String] =
      VisitAnyFieldByName("name", (_, name) => Transform(name.reverse))
    val stack: VisitorStack[TestTree] = VisitorStack.initial[TestTree](Leaf(7))

    assertSame(enter, visitLeaf.enter)
    assertSame(leave, visitLeaf.leave)
    assertEquals(Continue, defaultLeaveVisit.leave(Leaf(0)))
    assertEquals(Transform(Leaf(2), Skip), visitLeaf.enter(Leaf(1)))
    assertEquals(Transform(8), anyField.fn(Special(Leaf(1), 7, "node"), 7))
    assertEquals("name", namedField.fieldName)
    assertEquals(Transform("edon"), namedField.fn(Special(Leaf(1), 7, "node"), "node"))
    assertEquals(Some(Leaf(7)), stack.node)
    assertFalse(stack.updated)
    assertFalse(stack.deleted)
    assertNull(stack.prev)
    assertNull(stack.next)
  }
}
