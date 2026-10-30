/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertThrows}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatest.PrivateMethodTester.*

class PriceCalculator {
  private def total(unitPrice: Int, quantity: Int): Int = unitPrice * quantity
  private def label(prefix: String): String = prefix + " total"
  private def reject(): Unit = throw new IllegalArgumentException("negative quantity")
}

@Timeout(120)
class PrivateMethodTesterInnerInvokerTest {
  @Test
  def testsPrivateCalculationsThroughThePrivateMethodTesterDsl(): Unit = {
    val calculator: PriceCalculator = new PriceCalculator
    val total = PrivateMethod[Int](Symbol("total"))
    val label = PrivateMethod[String](Symbol("label"))
    assertEquals(42, calculator.invokePrivate(total(7, 6)))
    assertEquals("invoice total", calculator.invokePrivate(label("invoice")))
  }

  @Test
  def exposesTheExceptionThrownByThePrivateMethod(): Unit = {
    val calculator: PriceCalculator = new PriceCalculator
    val reject = PrivateMethod[Unit](Symbol("reject"))
    val error = assertThrows(classOf[IllegalArgumentException], () => calculator.invokePrivate(reject()))
    assertEquals("negative quantity", error.getMessage)
  }
}
