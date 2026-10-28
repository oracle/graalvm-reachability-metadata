/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.tapircirce

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import io.circe.Codec
import io.circe.Json
import io.circe.parser.parse
import org.http4s.HttpApp
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import sttp.tapir.*
import sttp.tapir.json.circe.TapirJsonCirce
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises Circe coproduct JSON body codecs for polymorphic fulfillment commands. */
class FulfillmentCommandJsonTest extends TapirJsonCirce {
  enum Command derives Codec.AsObject, Schema {
    case Ship(orderId: String, address: String)
    case Collect(orderId: String, storeId: String)
  }

  enum Confirmation derives Codec.AsObject, Schema {
    case ShipmentScheduled(orderId: String, destination: String)
    case ReadyForCollection(orderId: String, storeId: String)
  }

  @Test
  @Timeout(60)
  def dispatchesBothCommandVariantsAndRejectsUnknownCommands(): Unit = {
    val accepted: Ref[IO, List[Command]] = Ref.of[IO, List[Command]](Nil).unsafeRunSync()
    val fulfill = endpoint.post
      .in("fulfillments")
      .in(jsonBody[Command])
      .out(jsonBody[Confirmation])
      .serverLogicSuccess[IO] { command =>
        val confirmation: Confirmation = command match {
          case Command.Ship(orderId, address) => Confirmation.ShipmentScheduled(orderId, address)
          case Command.Collect(orderId, storeId) => Confirmation.ReadyForCollection(orderId, storeId)
        }
        accepted.update(_ :+ command).as(confirmation)
      }
    val app: HttpApp[IO] = Http4sServerInterpreter[IO]().toRoutes(fulfill).orNotFound

    def submit(body: String): (Status, Option[MediaType], String) = {
      val request: Request[IO] = Request[IO](Method.POST, Uri.unsafeFromString("/fulfillments"))
        .withEntity(body)
        .putHeaders(`Content-Type`(MediaType.application.json))
      (for {
        response <- app.run(request)
        text <- response.bodyText.compile.string
      } yield (response.status, response.contentType.map(_.mediaType), text))
        .timeout(15.seconds).unsafeRunSync()
    }

    val shipment: (Status, Option[MediaType], String) = submit(
      """{"Ship":{"orderId":"order-17","address":"42 High Street"}}"""
    )
    val expectedShipment: Json = parse(
      """{"ShipmentScheduled":{"orderId":"order-17","destination":"42 High Street"}}"""
    ).fold(throw _, identity)
    assertEquals(Status.Ok, shipment._1)
    assertEquals(Some(MediaType.application.json), shipment._2)
    assertEquals(expectedShipment, parse(shipment._3).fold(throw _, identity))

    val collection: (Status, Option[MediaType], String) = submit(
      """{"Collect":{"orderId":"order-18","storeId":"store-3"}}"""
    )
    val expectedCollection: Json = parse(
      """{"ReadyForCollection":{"orderId":"order-18","storeId":"store-3"}}"""
    ).fold(throw _, identity)
    assertEquals(Status.Ok, collection._1)
    assertEquals(Some(MediaType.application.json), collection._2)
    assertEquals(expectedCollection, parse(collection._3).fold(throw _, identity))

    val unknown: (Status, Option[MediaType], String) = submit(
      """{"Teleport":{"orderId":"order-19","coordinates":"51.75,-1.25"}}"""
    )
    assertEquals(Status.BadRequest, unknown._1)
    assertEquals(
      List(Command.Ship("order-17", "42 High Street"), Command.Collect("order-18", "store-3")),
      accepted.get.unsafeRunSync()
    )
  }
}
