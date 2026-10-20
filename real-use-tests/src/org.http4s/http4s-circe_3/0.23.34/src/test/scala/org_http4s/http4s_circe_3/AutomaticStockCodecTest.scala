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
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises automatic Circe entity codecs for a typed stock-adjustment API and client. */
class AutomaticStockCodecTest {
  private final case class Adjustment(delta: Int, reason: String)
  private final case class Snapshot(available: Int, lastReason: Option[String])
  private final case class Problem(code: String, available: Int)

  private given Encoder[Adjustment] = deriveEncoder[Adjustment]
  private given Decoder[Adjustment] = deriveDecoder[Adjustment]
  private given Encoder[Snapshot] = deriveEncoder[Snapshot]
  private given Decoder[Snapshot] = deriveDecoder[Snapshot]
  private given Encoder[Problem] = deriveEncoder[Problem]
  private given Decoder[Problem] = deriveDecoder[Problem]

  @Test
  @Timeout(60)
  def adjustStockAndDecodeSuccessAndConflictResponses(): Unit = {
    // CirceEntityCodec supplies HTTP codecs for each domain type from its Circe instances.
    // Neither the client nor the routes declares an EntityEncoder or EntityDecoder.
    val result: IO[Unit] = for {
      stock <- Ref.of[IO, Snapshot](Snapshot(0, None))
      routes = HttpRoutes.of[IO] {
        case GET -> Root / "stock" => stock.get.flatMap(Ok(_))
        case request @ POST -> Root / "stock" / "adjustments" =>
          request.as[Adjustment].flatMap { adjustment =>
            stock.modify { previous =>
              val nextQuantity: Int = previous.available + adjustment.delta
              if (nextQuantity < 0) {
                (previous, Left(Problem("insufficient-stock", previous.available)))
              } else {
                val updated: Snapshot = Snapshot(nextQuantity, Some(adjustment.reason))
                (updated, Right(updated))
              }
            }.flatMap {
              case Left(problem) => Conflict(problem)
              case Right(updated) => Ok(updated)
            }
          }
      }
      client = Client.fromHttpApp(routes.orNotFound)
      initial <- client.expect[Snapshot](Uri.unsafeFromString("/stock"))
      received <- client.expect[Snapshot](submission(Adjustment(12, "Delivery \"north\"")))
      reserved <- client.expect[Snapshot](submission(Adjustment(-5, "Order #42")))
      _ <- client.run(submission(Adjustment(-8, "Order #43"))).use { response =>
        response.as[Problem].flatMap { problem =>
          IO {
            assertEquals(Status.Conflict, response.status)
            assertEquals(Some(`Content-Type`(MediaType.application.json)), response.contentType)
            assertEquals(Problem("insufficient-stock", 7), problem)
          }
        }
      }
      remaining <- client.expect[Snapshot](Uri.unsafeFromString("/stock"))
      _ <- IO {
        assertEquals(Snapshot(0, None), initial)
        assertEquals(Snapshot(12, Some("Delivery \"north\"")), received)
        assertEquals(Snapshot(7, Some("Order #42")), reserved)
        // A rejected adjustment must not change the quantity or the last successful reason.
        assertEquals(reserved, remaining)
      }
    } yield ()

    result.timeout(30.seconds).unsafeRunSync()
  }

  private def submission(adjustment: Adjustment): Request[IO] =
    Request[IO](Method.POST, Uri.unsafeFromString("/stock/adjustments")).withEntity(adjustment)
}
