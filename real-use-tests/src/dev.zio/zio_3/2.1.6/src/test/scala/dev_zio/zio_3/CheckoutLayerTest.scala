/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import zio.{Duration, IO, Runtime, Unsafe, ZIO, ZLayer}

/** Exercises ZIO ZLayer service composition by wiring a catalog and pricing policy into checkout quotes. */
class CheckoutLayerTest {
  private case class Catalog(prices: Map[String, Long]) {
    def price(sku: String): IO[String, Long] =
      ZIO.fromOption(prices.get(sku)).orElseFail(s"Unknown product: $sku")
  }

  private case class PricingPolicy(memberDiscountPercent: Int, shippingCents: Long, freeShippingCents: Long)
  private case class Quote(subtotalCents: Long, discountCents: Long, shippingCents: Long, totalCents: Long)

  private class Checkout(catalog: Catalog, policy: PricingPolicy) {
    def quote(lines: List[(String, Int)], member: Boolean): IO[String, Quote] =
      ZIO.foreach(lines) { case (sku, quantity) =>
        catalog.price(sku).map(_ * quantity)
      }.map { lineTotals =>
        val subtotal: Long = lineTotals.sum
        val discount: Long = if (member) subtotal * policy.memberDiscountPercent / 100 else 0L
        val discounted: Long = subtotal - discount
        val shipping: Long = if (discounted >= policy.freeShippingCents) 0L else policy.shippingCents
        Quote(subtotal, discount, shipping, discounted + shipping)
      }
  }

  @Test
  @Timeout(60)
  def composedCheckoutAppliesMembershipAndShippingRulesAndReportsMissingProducts(): Unit = {
    val catalog: ZLayer[Any, Nothing, Catalog] = ZLayer.succeed(
      Catalog(Map("coffee" -> 1200L, "mug" -> 800L))
    )
    val policy: ZLayer[Any, Nothing, PricingPolicy] = ZLayer.succeed(
      PricingPolicy(memberDiscountPercent = 10, shippingCents = 500L, freeShippingCents = 2500L)
    )
    val checkout: ZLayer[Catalog & PricingPolicy, Nothing, Checkout] = ZLayer.fromFunction(
      (catalog: Catalog, policy: PricingPolicy) => new Checkout(catalog, policy)
    )
    val application: ZLayer[Any, Nothing, Checkout] = ZLayer.make[Checkout](catalog, policy, checkout)
    val basket: List[(String, Int)] = List("coffee" -> 2, "mug" -> 1)
    val program = (for {
      regular <- ZIO.serviceWithZIO[Checkout](_.quote(basket, member = false))
      member <- ZIO.serviceWithZIO[Checkout](_.quote(basket, member = true))
      small <- ZIO.serviceWithZIO[Checkout](_.quote(List("mug" -> 1), member = false))
      rejected <- ZIO.serviceWithZIO[Checkout](
        _.quote(List("coffee" -> 1, "missing" -> 1), member = true)
      ).either
      // A rejected basket must not prevent the same composed service from handling the next request.
      next <- ZIO.serviceWithZIO[Checkout](_.quote(basket, member = true))
    } yield (regular, member, small, rejected, next)).provide(application)

    val (regular, member, small, rejected, next) = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(
        program.timeoutFail("Checkout pricing timed out")(Duration.fromSeconds(30))
      ).getOrThrowFiberFailure()
    }

    assertEquals(Quote(3200L, 0L, 0L, 3200L), regular)
    assertEquals(Quote(3200L, 320L, 0L, 2880L), member)
    assertEquals(Quote(800L, 0L, 500L, 1300L), small)
    assertEquals(Left("Unknown product: missing"), rejected)
    assertEquals(member, next)
  }
}
