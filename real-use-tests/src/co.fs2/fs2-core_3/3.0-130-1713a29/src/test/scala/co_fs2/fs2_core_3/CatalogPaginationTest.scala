/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_core_3

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.{Chunk, Stream}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import scala.collection.immutable.SortedMap
import scala.concurrent.duration.*

/** Exercises FS2 effectful stream unfolding to export an in-memory catalog using keyset pagination. */
class CatalogPaginationTest {
  private case class Product(id: Int, sku: String, priceCents: Long)
  private case class Export(rows: Vector[Product], requestedCursors: Vector[Int])

  private def catalogRows(
      catalog: SortedMap[Int, Product],
      requestedCursors: Ref[IO, Vector[Int]]
  ): Stream[IO, Product] = Stream
    .unfoldEval[IO, Int, Chunk[Product]](0) { after =>
      requestedCursors.update(_ :+ after) >> IO {
        // IDs are positive but not contiguous. Resume after the last returned ID, not an offset.
        val page: Vector[Product] = catalog.iterator
          .dropWhile { case (id, _) => id <= after }
          .take(2)
          .map { case (_, product) => product }
          .toVector
        page.lastOption.map(last => (Chunk.vector(page), last.id))
      }
    }
    .unchunks

  private def collectExport(catalog: SortedMap[Int, Product], limit: Long): IO[Export] = for {
    requested <- Ref.of[IO, Vector[Int]](Vector.empty)
    rows <- catalogRows(catalog, requested).take(limit).compile.toVector
    cursors <- requested.get
  } yield Export(rows, cursors)

  @Test
  @Timeout(60)
  def exportsTheFinalPartialPageAndStopsFetchingWhenAPreviewIsComplete(): Unit = {
    val products: Vector[Product] = Vector(
      Product(10, "mug", 1250L),
      Product(40, "tea", 400L),
      Product(70, "cup", 900L),
      Product(110, "card", 200L),
      Product(200, "tray", 1800L)
    )
    val catalog: SortedMap[Int, Product] = SortedMap.from(products.map(p => p.id -> p))

    val complete: Export = collectExport(catalog, Long.MaxValue)
      .timeout(20.seconds).unsafeRunSync()

    assertEquals(products, complete.rows)
    assertEquals(Vector(0, 40, 110, 200), complete.requestedCursors)
    assertEquals(4550L, complete.rows.map(_.priceCents).sum)

    // A preview needs two pages, but must not fetch the third page or probe for exhaustion.
    val preview: Export = collectExport(catalog, 3L).timeout(20.seconds).unsafeRunSync()
    assertEquals(products.take(3), preview.rows)
    assertEquals(Vector(0, 40), preview.requestedCursors)

    val empty: Export = collectExport(SortedMap.empty[Int, Product], Long.MaxValue)
      .timeout(20.seconds).unsafeRunSync()
    assertEquals(Vector.empty[Product], empty.rows)
    assertEquals(Vector(0), empty.requestedCursors)
  }
}
