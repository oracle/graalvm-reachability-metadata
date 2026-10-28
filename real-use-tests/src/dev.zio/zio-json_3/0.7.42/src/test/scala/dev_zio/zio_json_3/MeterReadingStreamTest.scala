/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_json_3

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.{Chunk, Runtime, Unsafe, ZIO}
import zio.json.*
import zio.stream.ZStream

/** Exercises streaming JSON Lines pipelines for importing and exporting meter readings. */
class MeterReadingStreamTest {
  final case class Reading(meter: String, wattHours: Long, note: String)

  given JsonCodec[Reading] = DeriveJsonCodec.gen[Reading]

  private def run[A](effect: ZIO[Any, Throwable, A]): A = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
  }

  private def readings(input: String): ZStream[Any, Throwable, Reading] = {
    ZStream.fromIterable(input).rechunk(7)
      .via(JsonDecoder[Reading].decodeJsonPipeline(JsonStreamDelimiter.Newline))
  }

  @Test
  @Timeout(60)
  def importFragmentedReadingsAndExportBillableRecords(): Unit = {
    // Transport chunks deliberately split fields, escapes, and record boundaries.
    val incoming: String =
      """{"meter":"north","wattHours":1250,"note":"panel {A}, checked"}
        |{"meter":"south","wattHours":0,"note":"offline\ncheck \"fuse\""}
        |{"meter":"north","wattHours":750,"note":"evening"}""".stripMargin
    val expected: Chunk[Reading] = Chunk(
      Reading("north", 1250L, "panel {A}, checked"),
      Reading("south", 0L, "offline\ncheck \"fuse\""),
      Reading("north", 750L, "evening")
    )
    val imported: Chunk[Reading] = run(readings(incoming).runCollect)
    assertEquals(expected, imported, "Every reading, including the final unterminated line, must arrive")

    val totals: Map[String, Long] = imported.toList.groupMapReduce(_.meter)(_.wattHours)(_ + _)
    assertEquals(Map("north" -> 2000L, "south" -> 0L), totals)

    val exported: String = run(
      ZStream.fromChunk(imported)
        .filter(_.wattHours > 0)
        .via(JsonEncoder[Reading].encodeJsonLinesPipeline)
        .runCollect
    ).mkString
    val billable: Chunk[Reading] = Chunk(expected(0), expected(2))
    val exportedLines: List[Either[String, Reading]] = exported.linesIterator
      .map(_.fromJson[Reading]).toList
    assertEquals(billable.toList.map(Right(_)), exportedLines)
    assertEquals(billable, run(readings(exported).runCollect))

    val malformed: String =
      """{"meter":"north","wattHours":1250,"note":"ok"}
        |{"meter":"south","wattHours":"unavailable","note":"fault"}
        |{"meter":"north","wattHours":750,"note":"later"}""".stripMargin
    val rejected: Either[Throwable, Chunk[Reading]] = run(readings(malformed).runCollect.either)
    assertTrue(rejected.isLeft, "A corrupt reading must fail the import, not produce a partial billing batch")
    val error: Throwable = rejected.swap.toOption.get
    assertTrue(error.getMessage.contains("wattHours"), error.toString)
  }
}
