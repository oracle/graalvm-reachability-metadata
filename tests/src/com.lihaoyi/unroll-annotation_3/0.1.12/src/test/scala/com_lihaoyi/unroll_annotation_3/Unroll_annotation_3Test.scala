/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.unroll_annotation_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertNotSame, assertTrue}
import org.junit.jupiter.api.Test
import scala.annotation.{Annotation, StaticAnnotation, unroll}

class Unroll_annotation_3Test {
  @Test
  def annotationCanBeConstructedAsAScalaStaticAnnotation(): Unit = {
    val first: unroll = new unroll
    val second: unroll = new unroll

    assertTrue(first.isInstanceOf[Annotation])
    assertTrue(first.isInstanceOf[StaticAnnotation])
    assertNotSame(first, second)
  }

  @Test
  def methodAnnotationsPreserveDefaultAndNamedArgumentSemantics(): Unit = {
    val formatter: AnnotatedFormatter = AnnotatedFormatter("item")

    assertEquals("item:3:active", formatter.render())
    assertEquals("custom:3:active", formatter.render(label = "custom"))
    assertEquals("item:9:active", formatter.render(count = 9))
    assertEquals("item:3:archived", formatter.render(enabled = false))
    assertEquals("custom:9:archived", formatter.render("custom", 9, enabled = false))
  }

  @Test
  def classAndConstructorAnnotationsPreservePublicConstruction(): Unit = {
    val defaultGreeter: AnnotatedGreeter = new AnnotatedGreeter()
    val excitedGreeter: AnnotatedGreeter = new AnnotatedGreeter(greeting = "Welcome", punctuation = "!!!")

    assertEquals("Hello, world!", defaultGreeter.greet())
    assertEquals("Hello, Scala!", defaultGreeter.greet(name = "Scala"))
    assertEquals("Welcome, world!!!", excitedGreeter.greet())
    assertEquals("Welcome, guests!!!", excitedGreeter.greet("guests"))
  }

  @Test
  def caseClassAnnotationPreservesCompanionCopyAndProductBehavior(): Unit = {
    val defaultOrder: AnnotatedOrder = AnnotatedOrder()
    val rushOrder: AnnotatedOrder = defaultOrder.copy(quantity = 4, expedited = true)

    assertEquals("notebook", defaultOrder.product)
    assertEquals(1, defaultOrder.quantity)
    assertEquals(false, defaultOrder.expedited)
    assertEquals("notebook x 1 (standard)", defaultOrder.summary())
    assertEquals("notebook x 4 (expedited)", rushOrder.summary())
    assertEquals(AnnotatedOrder("notebook", 4, expedited = true), rushOrder)
    assertEquals(List("notebook", 4, true), rushOrder.productIterator.toList)
  }

  @Test
  def genericAnnotatedMembersWorkWithHigherOrderDefaults(): Unit = {
    val names: AnnotatedBox[List[String]] = AnnotatedBox(List("ada", "grace"))
    val numbers: AnnotatedBox[List[Int]] = AnnotatedBox(List(1, 2, 3), label = "numbers")

    assertEquals("value=ada,grace", names.describe(_.mkString(",")))
    assertEquals("names=ADA|GRACE", names.describe(_.map(_.toUpperCase).mkString("|"), label = "names"))
    assertEquals("numbers=1 + 2 + 3", numbers.describe(_.mkString(" + ")))
  }
}

final class AnnotatedFormatter(private val defaultLabel: String) {
  @unroll
  def render(label: String = defaultLabel, count: Int = 3, enabled: Boolean = true): String = {
    val state: String = if enabled then "active" else "archived"

    s"$label:$count:$state"
  }
}

@unroll
final class AnnotatedGreeter @unroll (val greeting: String = "Hello", val punctuation: String = "!") {
  @unroll
  def greet(name: String = "world"): String = s"$greeting, $name$punctuation"
}

@unroll
final case class AnnotatedOrder(product: String = "notebook", quantity: Int = 1, expedited: Boolean = false) {
  @unroll
  def summary(unit: String = product, amount: Int = quantity): String = {
    val shipping: String = if expedited then "expedited" else "standard"

    s"$unit x $amount ($shipping)"
  }
}

@unroll
final case class AnnotatedBox[A](value: A, label: String = "value") {
  @unroll
  def describe(render: A => String, label: String = this.label): String = s"$label=${render(value)}"
}
