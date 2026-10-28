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
import zio.NonEmptyChunk
import zio.json.*
import zio.json.ast.Json

/** Exercises ZIO non-empty collection codecs for accepting and publishing dispatch batches. */
class DispatchBatchTest {
  final case class Stop(orderId: String, address: String, parcels: Int)
  final case class DispatchBatch(driver: String, stops: NonEmptyChunk[Stop])

  given JsonCodec[Stop] = DeriveJsonCodec.gen[Stop]
  given JsonCodec[DispatchBatch] = DeriveJsonCodec.gen[DispatchBatch]

  @Test
  @Timeout(30)
  def acceptNonEmptyRouteAndPublishUpdatedDriverManifest(): Unit = {
    val incoming: String = """
      {
        "driver": "driver-19",
        "stops": [
          {"orderId":"order-81","address":"14 Oak Street","parcels":2},
          {"orderId":"order-82","address":"9 Pine Avenue","parcels":1}
        ]
      }
      """
    val first: Stop = Stop("order-81", "14 Oak Street", 2)
    val second: Stop = Stop("order-82", "9 Pine Avenue", 1)
    val batch: DispatchBatch = incoming.fromJson[DispatchBatch].fold(
      error => fail[DispatchBatch](error),
      identity
    )
    assertEquals(DispatchBatch("driver-19", NonEmptyChunk(first, second)), batch)
    assertEquals(first, batch.stops.head, "The driver must start at the first supplied stop")
    assertEquals(3, batch.stops.toChunk.map(_.parcels).sum)

    val corrected: DispatchBatch = batch.copy(stops = batch.stops.map { stop =>
      if (stop.orderId == "order-82") stop.copy(address = "11 Pine Avenue") else stop
    })
    val published: String = corrected.toJson
    val expected: String = """
      {
        "driver":"driver-19",
        "stops":[
          {"orderId":"order-81","address":"14 Oak Street","parcels":2},
          {"orderId":"order-82","address":"11 Pine Avenue","parcels":1}
        ]
      }
      """
    assertEquals(expected.fromJson[Json], published.fromJson[Json],
      "Driver manifests must contain ordinary ordered JSON arrays")
    assertEquals(Right(corrected), published.fromJson[DispatchBatch])
    assertEquals(second, batch.stops.toChunk(1), "Correcting a manifest must not mutate the imported batch")

    val finalStop: DispatchBatch = corrected.copy(stops = NonEmptyChunk(corrected.stops.toChunk(1)))
    assertEquals(Right(finalStop), finalStop.toJson.fromJson[DispatchBatch],
      "A one-stop batch is still dispatchable")

    val invalidBatches: List[String] = List(
      """{"driver":"driver-19","stops":[]}""",
      """{"driver":"driver-19","stops":null}""",
      """{"driver":"driver-19"}""",
      """{"driver":"driver-19","stops":[{"orderId":"order-81","address":"14 Oak Street","parcels":"two"}]}"""
    )
    invalidBatches.foreach { document =>
      val rejected: Either[String, DispatchBatch] = document.fromJson[DispatchBatch]
      assertTrue(rejected.isLeft, s"An empty or malformed batch must not reach a driver: $document")
      assertTrue(rejected.swap.toOption.get.contains("stops"),
        "The dispatcher needs a field-level diagnostic for an invalid route")
    }
  }
}
