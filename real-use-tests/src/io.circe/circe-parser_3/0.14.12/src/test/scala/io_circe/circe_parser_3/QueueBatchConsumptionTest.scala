/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_parser_3

import io.circe.{Decoder, DecodingFailure, Error, ParsingFailure}
import io.circe.parser.decode
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises nested JSON document decoding for queue batches with JSON-encoded message bodies. */
@Timeout(60)
class QueueBatchConsumptionTest {
  private final case class QueueMessage(messageId: String, body: String)
  private final case class Shipment(orderId: String, trackingNumber: String)
  private final case class BatchResult(
      accepted: Vector[(String, Shipment)],
      rejected: Vector[(String, Error)]
  )

  private given Decoder[QueueMessage] =
    Decoder.forProduct2("messageId", "body")(QueueMessage.apply)
  private given Decoder[Shipment] =
    Decoder.forProduct2("orderId", "trackingNumber")(Shipment.apply)

  private def consume(batch: String): Either[Error, BatchResult] =
    decode[Vector[QueueMessage]](batch).map { messages =>
      messages.foldLeft(BatchResult(Vector.empty, Vector.empty)) { (result, message) =>
        decode[Shipment](message.body) match {
          case Right(shipment) =>
            result.copy(accepted = result.accepted :+ (message.messageId -> shipment))
          case Left(error) =>
            result.copy(rejected = result.rejected :+ (message.messageId -> error))
        }
      }
    }

  @Test
  def decodesEachBodyAsACompleteDocumentAndRetriesOnlyRejectedMessages(): Unit = {
    // The transport envelope is valid even when a producer has sent an invalid body.
    val batch: String =
      """[
        |  {
        |    "messageId": "delivery-1",
        |    "body": "{\"orderId\":\"order-41\",\"trackingNumber\":\"TRACK-41\"}"
        |  },
        |  {
        |    "messageId": "delivery-2",
        |    "body": "{\"orderId\":\"order-42\",\"trackingNumber\":"
        |  },
        |  {
        |    "messageId": "delivery-3",
        |    "body": "{\"orderId\":\"order-43\",\"trackingNumber\":false}"
        |  },
        |  {
        |    "messageId": "delivery-4",
        |    "body": "{\"orderId\":\"order-44\",\"trackingNumber\":\"TRACK-44\"} {}"
        |  },
        |  {
        |    "messageId": "delivery-5",
        |    "body": "  {\"orderId\":\"order-45\",\"trackingNumber\":\"TRACK-45\"}\n"
        |  }
        |]""".stripMargin

    val result: BatchResult = consume(batch).fold(error => throw error, identity)

    assertEquals(
      Vector(
        "delivery-1" -> Shipment("order-41", "TRACK-41"),
        "delivery-5" -> Shipment("order-45", "TRACK-45")
      ),
      result.accepted
    )
    assertEquals(Vector("delivery-2", "delivery-3", "delivery-4"), result.rejected.map(_._1))
    val errors: Map[String, Error] = result.rejected.toMap
    assertTrue(errors("delivery-2").isInstanceOf[ParsingFailure])
    assertTrue(errors("delivery-3").isInstanceOf[DecodingFailure])
    assertTrue(errors("delivery-4").isInstanceOf[ParsingFailure])

    // Redelivery of a corrected body can be acknowledged on the next invocation.
    val redelivery: String =
      """[{"messageId":"delivery-2","body":"{\"orderId\":\"order-42\",\"trackingNumber\":\"TRACK-42\"}"}]"""
    val retried: BatchResult = consume(redelivery).fold(error => throw error, identity)
    assertEquals(Vector("delivery-2" -> Shipment("order-42", "TRACK-42")), retried.accepted)
    assertTrue(retried.rejected.isEmpty)
  }

  @Test
  def refusesAnInvalidTransportEnvelopeBeforeAcknowledgingAnyMessage(): Unit = {
    val validEnvelope: String =
      """[{"messageId":"delivery-1","body":"{\"orderId\":\"order-41\",\"trackingNumber\":\"TRACK-41\"}"}]"""
    val truncatedEnvelope: String = validEnvelope.dropRight(1)
    val nonStringBody: String =
      """[{"messageId":"delivery-1","body":{"orderId":"order-41","trackingNumber":"TRACK-41"}}]"""

    assertTrue(consume(truncatedEnvelope).swap.exists(_.isInstanceOf[ParsingFailure]))
    assertTrue(consume(nonStringBody).swap.exists(_.isInstanceOf[DecodingFailure]))
    assertEquals(Right(BatchResult(Vector.empty, Vector.empty)), consume("[]"))
  }
}
