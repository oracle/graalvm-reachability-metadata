/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import zio.{Duration, Queue, Ref, Runtime, UIO, Unsafe, ZIO}

/** Exercises ZIO bounded queues by distributing shipping-label jobs to background workers. */
class ShippingLabelQueueTest {
  private case class Parcel(orderId: String, weightGrams: Int, destination: String)
  private case class LabelResult(orderId: String, label: Either[String, String])

  private def createLabel(parcel: Parcel): LabelResult = {
    val label: Either[String, String] =
      if (parcel.weightGrams <= 0) Left("Weight must be positive")
      else if (parcel.weightGrams > 20000) Left("Parcel exceeds carrier weight limit")
      else Right(s"SHIP:${parcel.orderId}:${parcel.destination}:${parcel.weightGrams}")
    LabelResult(parcel.orderId, label)
  }

  private def runWorker(
      jobs: Queue[Option[Parcel]],
      results: Ref[Vector[LabelResult]]
  ): UIO[Unit] =
    jobs.take.flatMap {
      case Some(parcel) => results.update(_ :+ createLabel(parcel)) *> runWorker(jobs, results)
      case None => ZIO.unit
    }

  @Test
  @Timeout(60)
  def workersDrainQueuedJobsAndKeepProcessingAfterRejectedParcels(): Unit = {
    val parcels: List[Parcel] = List(
      Parcel("ORDER-101", 1500, "DE"),
      Parcel("ORDER-102", 25000, "FR"),
      Parcel("ORDER-103", 0, "NL"),
      Parcel("ORDER-104", 20000, "ES"),
      Parcel("ORDER-105", 750, "IT"),
      Parcel("ORDER-106", 3200, "BE")
    )
    val program: UIO[Vector[LabelResult]] = ZIO.scoped {
      for {
        jobs <- ZIO.acquireRelease(Queue.bounded[Option[Parcel]](2))(_.shutdown)
        results <- Ref.make(Vector.empty[LabelResult])
        workers <- ZIO.foreach(1 to 3)(_ => runWorker(jobs, results).forkScoped)
        _ <- ZIO.foreachDiscard(parcels)(parcel => jobs.offer(Some(parcel)))
        // FIFO stop messages follow all jobs, allowing workers to drain before shutdown.
        _ <- ZIO.foreachDiscard(workers)(_ => jobs.offer(None))
        _ <- ZIO.foreachDiscard(workers)(_.join)
        completed <- results.get
      } yield completed
    }

    val completed: Vector[LabelResult] = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(
        program.timeoutFail(new IllegalStateException("Shipping workers timed out"))(Duration.fromSeconds(30))
      ).getOrThrowFiberFailure()
    }

    val expected: Vector[LabelResult] = Vector(
      LabelResult("ORDER-101", Right("SHIP:ORDER-101:DE:1500")),
      LabelResult("ORDER-102", Left("Parcel exceeds carrier weight limit")),
      LabelResult("ORDER-103", Left("Weight must be positive")),
      LabelResult("ORDER-104", Right("SHIP:ORDER-104:ES:20000")),
      LabelResult("ORDER-105", Right("SHIP:ORDER-105:IT:750")),
      LabelResult("ORDER-106", Right("SHIP:ORDER-106:BE:3200"))
    )
    assertEquals(expected, completed.sortBy(_.orderId))
    assertEquals(4, completed.count(_.label.isRight))
    assertEquals(2, completed.count(_.label.isLeft))
  }
}
