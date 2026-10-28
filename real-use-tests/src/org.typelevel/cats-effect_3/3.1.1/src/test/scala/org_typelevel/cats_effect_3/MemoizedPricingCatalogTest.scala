/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_effect_3

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises IO memoization to share a lazily loaded pricing catalog across checkout requests. */
class MemoizedPricingCatalogTest {
  private case class Cart(id: String, items: List[(String, Int)])
  private case class Quote(cartId: String, totalCents: Int)

  private def quote(cart: Cart, catalog: IO[Map[String, Int]]): IO[Quote] = {
    catalog.map { prices =>
      Quote(cart.id, cart.items.map { case (sku, quantity) => prices(sku) * quantity }.sum)
    }
  }

  @Test
  @Timeout(30)
  def concurrentCheckoutsAndLaterRequestsReuseOneLazyCatalogLoad(): Unit = {
    val carts: List[Cart] = List(
      Cart("CART-101", List("coffee" -> 2, "tea" -> 1)),
      Cart("CART-102", List("tea" -> 3)),
      Cart("CART-103", List("coffee" -> 1, "cocoa" -> 2)),
      Cart("CART-104", List("cocoa" -> 1))
    )

    val program: IO[(Int, List[Quote], Quote, Int)] = for {
      loads <- Ref.of[IO, Int](0)
      loadStarted <- Deferred[IO, Unit]
      allowLoad <- Deferred[IO, Unit]
      loadCatalog = loads.update(_ + 1) *> loadStarted.complete(()).void *>
        allowLoad.get.as(Map("coffee" -> 750, "tea" -> 400, "cocoa" -> 600))
      catalog <- loadCatalog.memoize
      beforeCheckout <- loads.get
      // Keep the initial load in flight until a separate fiber observes it, without sleeps.
      batch <- IO.both(
        carts.parTraverse(cart => quote(cart, catalog)),
        loadStarted.get *> allowLoad.complete(()).void
      )
      laterQuote <- quote(Cart("CART-105", List("coffee" -> 3, "tea" -> 2)), catalog)
      afterCheckout <- loads.get
    } yield (beforeCheckout, batch._1, laterQuote, afterCheckout)

    val (beforeCheckout, quotes, laterQuote, afterCheckout) =
      program.unsafeRunTimed(20.seconds).getOrElse {
        throw new AssertionError("Checkout pricing did not finish within 20 seconds")
      }

    val expected: List[Quote] = List(
      Quote("CART-101", 1900),
      Quote("CART-102", 1200),
      Quote("CART-103", 1950),
      Quote("CART-104", 600)
    )
    assertEquals(0, beforeCheckout, "Constructing the shared loader must not load the catalog")
    assertEquals(expected, quotes)
    assertEquals(Quote("CART-105", 3050), laterQuote)
    assertEquals(1, afterCheckout, "Concurrent and later checkouts must share one catalog load")
  }
}
