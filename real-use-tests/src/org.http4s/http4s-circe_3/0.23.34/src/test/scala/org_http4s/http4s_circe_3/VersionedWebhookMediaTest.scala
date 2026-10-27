/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_circe_3

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import io.circe.Decoder
import io.circe.Json
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.EntityDecoder
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe.jsonDecoder
import org.http4s.circe.jsonEncoder
import org.http4s.circe.jsonOfWithMedia
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises custom-media JSON decoding for a versioned shipment webhook. */
class VersionedWebhookMediaTest {
  private final case class ShipmentNotice(shipment: String, tracking: String)

  private val versionOne: MediaType =
    MediaType.unsafeParse("application/vnd.warehouse.shipment.v1+json")
  private given Decoder[ShipmentNotice] = deriveDecoder[ShipmentNotice]
  private given EntityDecoder[IO, ShipmentNotice] =
    jsonOfWithMedia[IO, ShipmentNotice](versionOne, MediaType.application.json)

  @Test
  @Timeout(60)
  def acceptSupportedWebhookFormatsWithoutProcessingUnsupportedVersions(): Unit = {
    val result: IO[Unit] = for {
      shipments <- Ref.of[IO, List[ShipmentNotice]](Nil)
      routes = HttpRoutes.of[IO] {
        case request @ POST -> Root / "shipment-events" =>
          request.decodeStrict[ShipmentNotice] { notice =>
            shipments.update(_ :+ notice) *>
              Accepted(Json.obj(
                "shipment" -> Json.fromString(notice.shipment),
                "tracking" -> Json.fromString(notice.tracking)
              ))
          }
      }
      vendorResponse <- routes.orNotFound.run(delivery(versionOne, "shipment-21", "TRACK-21"))
      vendorReceipt <- vendorResponse.as[Json]
      _ <- IO {
        assertEquals(Status.Accepted, vendorResponse.status)
        assertEquals(receipt("shipment-21", "TRACK-21"), vendorReceipt)
      }
      futureVersion <- routes.orNotFound.run(delivery(
        MediaType.unsafeParse("application/vnd.warehouse.shipment.v2+json"),
        "shipment-22",
        "TRACK-22"
      ))
      plainText <- routes.orNotFound.run(delivery(MediaType.text.plain, "shipment-23", "TRACK-23"))
      _ <- futureVersion.body.compile.drain
      _ <- plainText.body.compile.drain
      storedAfterRejections <- shipments.get
      _ <- IO {
        assertEquals(Status.UnsupportedMediaType, futureVersion.status)
        assertEquals(Status.UnsupportedMediaType, plainText.status)
        assertEquals(List(ShipmentNotice("shipment-21", "TRACK-21")), storedAfterRejections)
      }
      legacyResponse <- routes.orNotFound.run(delivery(
        MediaType.application.json,
        "shipment-24",
        "TRACK-24"
      ))
      legacyReceipt <- legacyResponse.as[Json]
      stored <- shipments.get
      _ <- IO {
        assertEquals(Status.Accepted, legacyResponse.status)
        assertEquals(receipt("shipment-24", "TRACK-24"), legacyReceipt)
        assertEquals(List(
          ShipmentNotice("shipment-21", "TRACK-21"),
          ShipmentNotice("shipment-24", "TRACK-24")
        ), stored)
      }
    } yield ()

    result.timeout(30.seconds).unsafeRunSync()
  }

  private def receipt(shipment: String, tracking: String): Json =
    Json.obj("shipment" -> Json.fromString(shipment), "tracking" -> Json.fromString(tracking))

  private def delivery(mediaType: MediaType, shipment: String, tracking: String): Request[IO] =
    Request[IO](Method.POST, Uri.unsafeFromString("/shipment-events"))
      .withEntity(receipt(shipment, tracking))
      .withContentType(`Content-Type`(mediaType))
}
