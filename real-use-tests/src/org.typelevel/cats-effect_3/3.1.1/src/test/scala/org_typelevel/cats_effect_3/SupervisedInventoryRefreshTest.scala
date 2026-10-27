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
import cats.effect.kernel.Outcome
import cats.effect.std.Supervisor
import cats.effect.syntax.all._
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises Supervisor lifetime management for background inventory catalog refreshes. */
class SupervisedInventoryRefreshTest {
  private case class Catalog(revision: Int, stock: Map[String, Int])

  private def refresh(
      candidate: Catalog,
      approval: IO[Unit],
      published: Ref[IO, Catalog],
      audit: Ref[IO, Vector[String]]
  ): IO[Unit] = {
    (approval *> published.set(candidate) *>
      audit.update(_ :+ s"published:${candidate.revision}"))
      .onCancel(audit.update(_ :+ s"cancelled:${candidate.revision}"))
  }

  @Test
  @Timeout(30)
  def shutdownCancelsUnapprovedRefreshWithoutReplacingPublishedCatalog(): Unit = {
    val initial: Catalog = Catalog(1, Map("coffee" -> 5, "tea" -> 8))
    val accepted: Catalog = Catalog(2, Map("coffee" -> 12, "tea" -> 10))
    val awaitingReview: Catalog = Catalog(3, Map("coffee" -> 1200, "tea" -> 0))

    val program: IO[(Catalog, Vector[String], Boolean)] = for {
      published <- Ref.of[IO, Catalog](initial)
      audit <- Ref.of[IO, Vector[String]](Vector.empty)
      reviewRequested <- Deferred[IO, Unit]
      approval <- Deferred[IO, Unit]
      pending <- Supervisor[IO].use { supervisor =>
        for {
          first <- supervisor.supervise(refresh(accepted, IO.unit, published, audit))
          _ <- first.joinWithNever
          second <- supervisor.supervise(refresh(
            awaitingReview,
            reviewRequested.complete(()).void *> approval.get,
            published,
            audit
          ))
          // The service shuts down only after the refresh has requested manual review.
          _ <- reviewRequested.get
        } yield second
      }
      // Leaving the supervisor scope must finish cancellation, including its audit finalizer.
      snapshot <- published.get
      events <- audit.get
      outcome <- pending.join
      cancelled = outcome match {
        case Outcome.Canceled() => true
        case _ => false
      }
    } yield (snapshot, events, cancelled)

    val (snapshot, events, cancelled) = program.unsafeRunTimed(20.seconds).getOrElse {
      throw new AssertionError("Inventory service did not shut down within 20 seconds")
    }

    assertEquals(accepted, snapshot, "An unapproved refresh must not replace the published catalog")
    assertEquals(Vector("published:2", "cancelled:3"), events)
    assertTrue(cancelled, "Shutdown must cancel the supervised refresh, not leave it running")
  }
}
