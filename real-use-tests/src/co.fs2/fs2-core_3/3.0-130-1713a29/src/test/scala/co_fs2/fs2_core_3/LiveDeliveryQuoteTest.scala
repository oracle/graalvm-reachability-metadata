/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_core_3

import cats.effect.{Deferred, IO}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.SignallingRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import scala.concurrent.duration.*

/** Exercises FS2 signalling references to refresh a checkout quote when delivery rates change. */
class LiveDeliveryQuoteTest {
  private case class RateCard(revision: Int, deliveryCents: Long, freeDeliveryFromCents: Long)
  private case class Quote(revision: Int, deliveryCents: Long, totalCents: Long)

  @Test
  @Timeout(60)
  def repricesAnOpenBasketForPromotionalAndStandardDeliveryRates(): Unit = {
    val basketCents: Long = 6500L
    val cards: Vector[RateCard] = Vector(
      RateCard(0, 500L, 10000L),
      RateCard(1, 250L, 5000L),
      RateCard(2, 700L, 10000L)
    )

    val program: IO[Vector[Quote]] = for {
      rates <- SignallingRef[IO, RateCard](cards.head)
      displayed <- cards.traverse(_ => Deferred[IO, Unit])
      quotes <- {
        val refreshed: Stream[IO, Quote] = rates.discrete.evalMap { card =>
          val delivery: Long =
            if (basketCents >= card.freeDeliveryFromCents) 0L else card.deliveryCents
          val quote: Quote = Quote(card.revision, delivery, basketCents + delivery)
          displayed(card.revision).complete(()).as(quote)
        }.take(cards.size.toLong)

        // A signal represents current state, not an event queue. Wait for each quote to be
        // displayed before applying the next change, so no assertion relies on scheduler timing.
        val updates: Stream[IO, Unit] = Stream.emits(cards.tail).covary[IO].evalMap { card =>
          displayed(card.revision - 1).get >> rates.set(card)
        }

        refreshed.concurrently(updates).compile.toVector
      }
    } yield quotes

    val quotes: Vector[Quote] = program.timeout(20.seconds).unsafeRunSync()

    assertEquals(Vector(
      Quote(0, 500L, 7000L),
      Quote(1, 0L, 6500L),
      Quote(2, 700L, 7200L)
    ), quotes)
  }
}
