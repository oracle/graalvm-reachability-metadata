/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import zio.{Duration, IO, Promise, Ref, Runtime, Semaphore, Unsafe, ZIO}

/** Exercises ZIO Semaphore by limiting concurrent document processing and releasing permits after rejection. */
class DocumentProcessingSemaphoreTest {
  private case class Document(id: String, text: String)
  private case class IndexedDocument(id: String, words: Vector[String])
  private case class Activity(active: Int, peak: Int)

  private def index(document: Document): IO[String, IndexedDocument] =
    if (document.text.trim.isEmpty) ZIO.fail(s"Empty document: ${document.id}")
    else ZIO.succeed(IndexedDocument(
      document.id,
      document.text.trim.toLowerCase(Locale.ROOT).split("\\s+").toVector.distinct.sorted
    ))

  private def process(
      document: Document,
      permits: Semaphore,
      activity: Ref[Activity],
      saturated: Promise[Nothing, Unit],
      release: Promise[Nothing, Unit]
  ): IO[String, IndexedDocument] = permits.withPermit {
    ZIO.acquireReleaseWith(
      activity.updateAndGet { state =>
        val active: Int = state.active + 1
        Activity(active, math.max(state.peak, active))
      }
    )(_ => activity.update(state => state.copy(active = state.active - 1))) { state =>
      for {
        _ <- ZIO.when(state.active == 2)(saturated.succeed(()))
        // Hold the first two processors until the coordinator observes full capacity.
        _ <- release.await
        result <- index(document)
      } yield result
    }
  }

  @Test
  @Timeout(60)
  def boundedProcessingReclaimsAllPermitsAfterSuccessfulAndRejectedDocuments(): Unit = {
    val documents: List[Document] = List(
      Document("DOC-101", "Scala ZIO Scala"),
      Document("DOC-102", "  "),
      Document("DOC-103", "Native Image"),
      Document("DOC-104", "Bounded parallel work")
    )
    val program = ZIO.scoped {
      for {
        permits <- Semaphore.make(2)
        activity <- Ref.make(Activity(0, 0))
        saturated <- Promise.make[Nothing, Unit]
        release <- Promise.make[Nothing, Unit]
        workers <- ZIO.foreachPar(documents)(document =>
          process(document, permits, activity, saturated, release).either
        ).forkScoped
        _ <- saturated.await
        atCapacity <- activity.get
        _ <- release.succeed(())
        outcomes <- workers.join
        finished <- activity.get
        // Taking the entire capacity would block if either a success or a failure leaked a permit.
        next <- permits.withPermits(2)(index(Document("DOC-105", "Next Batch")))
      } yield (atCapacity, outcomes, finished, next)
    }

    val (atCapacity, outcomes, finished, next) = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(
        program.timeoutFail(new IllegalStateException("Document processing timed out"))(Duration.fromSeconds(30))
      ).getOrThrowFiberFailure()
    }

    assertEquals(Activity(2, 2), atCapacity)
    assertEquals(
      List(
        Right(IndexedDocument("DOC-101", Vector("scala", "zio"))),
        Left("Empty document: DOC-102"),
        Right(IndexedDocument("DOC-103", Vector("image", "native"))),
        Right(IndexedDocument("DOC-104", Vector("bounded", "parallel", "work")))
      ),
      outcomes
    )
    assertEquals(Activity(0, 2), finished)
    assertEquals(IndexedDocument("DOC-105", Vector("batch", "next")), next)
  }
}
