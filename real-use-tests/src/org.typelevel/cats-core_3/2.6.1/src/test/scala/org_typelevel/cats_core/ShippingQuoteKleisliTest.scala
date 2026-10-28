/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_core

import cats.data.Kleisli
import cats.syntax.all.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Cats Kleisli to compose a configuration-dependent shipping quote service.
  * The same workflow runs against different carrier policies and returns the first business error.
  */
@Timeout(60)
class ShippingQuoteKleisliTest {
  private case class Rate(baseCents: Long, perKilogramCents: Long)

  private case class CarrierPolicy(
    zoneRates: Map[String, Rate],
    tierDiscountPercent: Map[String, Int]
  )

  private case class Shipment(zone: String, kilograms: Int, customerTier: String)

  private case class Quote(subtotalCents: Long, discountCents: Long, payableCents: Long)

  private type Result[A] = Either[String, A]
  private type PricingStep[A] = Kleisli[Result, CarrierPolicy, A]

  private def rateFor(zone: String): PricingStep[Rate] = {
    Kleisli { policy =>
      policy.zoneRates.get(zone).toRight(s"destination not served: $zone")
    }
  }

  private def discountFor(tier: String): PricingStep[Int] = {
    Kleisli { policy =>
      policy.tierDiscountPercent.get(tier).toRight(s"unknown customer tier: $tier")
    }
  }

  private def quote(shipment: Shipment): PricingStep[Quote] = {
    val weight: Result[Int] = Either.cond(
      shipment.kilograms > 0,
      shipment.kilograms,
      "weight must be positive"
    )
    for {
      kilograms <- Kleisli.liftF[Result, CarrierPolicy, Int](weight)
      rate <- rateFor(shipment.zone)
      percent <- discountFor(shipment.customerTier)
    } yield {
      val subtotal: Long = rate.baseCents + rate.perKilogramCents * kilograms
      val discount: Long = subtotal * percent / 100
      Quote(subtotal, discount, subtotal - discount)
    }
  }

  @Test
  def reusesTheQuoteWorkflowWithEachCarriersRatesAndDiscounts(): Unit = {
    val standard: CarrierPolicy = CarrierPolicy(
      Map("domestic" -> Rate(500L, 200L)),
      Map("regular" -> 0, "gold" -> 10)
    )
    val alternative: CarrierPolicy = CarrierPolicy(
      Map("domestic" -> Rate(300L, 150L)),
      Map("regular" -> 0, "gold" -> 20)
    )
    val goldQuote: PricingStep[Quote] = quote(Shipment("domestic", 2, "gold"))

    assertEquals(Right(Quote(900L, 90L, 810L)), goldQuote.run(standard))
    assertEquals(Right(Quote(600L, 120L, 480L)), goldQuote.run(alternative))
    assertEquals(
      Right(Quote(900L, 0L, 900L)),
      quote(Shipment("domestic", 2, "regular")).run(standard)
    )
    assertEquals(Right(Quote(900L, 90L, 810L)), goldQuote.run(standard))
  }

  @Test
  def returnsTheFirstBusinessErrorInsteadOfProducingAPartialQuote(): Unit = {
    val policy: CarrierPolicy = CarrierPolicy(
      Map("domestic" -> Rate(500L, 200L)),
      Map("regular" -> 0)
    )

    assertEquals(
      Left("weight must be positive"),
      quote(Shipment("unserved", 0, "unknown")).run(policy)
    )
    assertEquals(
      Left("destination not served: unserved"),
      quote(Shipment("unserved", 2, "unknown")).run(policy)
    )
    assertEquals(
      Left("unknown customer tier: unknown"),
      quote(Shipment("domestic", 2, "unknown")).run(policy)
    )
    assertEquals(
      Right(Quote(900L, 0L, 900L)),
      quote(Shipment("domestic", 2, "regular")).run(policy)
    )
  }
}
