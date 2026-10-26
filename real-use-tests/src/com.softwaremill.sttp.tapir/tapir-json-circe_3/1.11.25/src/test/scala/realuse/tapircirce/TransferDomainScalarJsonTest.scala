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
import io.circe.Decoder
import io.circe.Encoder
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

/** Exercises custom Circe scalar codecs for domain account IDs in a transfer API. */
class TransferDomainScalarJsonTest extends TapirJsonCirce {
  case class AccountId(value: String)

  given Encoder[AccountId] = Encoder.encodeString.contramap(_.value)
  given Decoder[AccountId] = Decoder.decodeString.emap { value =>
    if (value.matches("acct-[0-9]{4}")) Right(AccountId(value))
    else Left("Account IDs must have the form acct-0000")
  }
  given Schema[AccountId] = Schema.schemaForString.map(value => Some(AccountId(value)))(_.value)

  case class Transfer(from: AccountId, to: AccountId, amountCents: Long)
      derives Codec.AsObject, Schema
  case class Receipt(number: Int, from: AccountId, to: AccountId, amountCents: Long)
      derives Codec.AsObject, Schema

  @Test
  @Timeout(60)
  def validatesScalarAccountIdsBeforeRecordingTransfers(): Unit = {
    val ledger: Ref[IO, List[Transfer]] = Ref.of[IO, List[Transfer]](Nil).unsafeRunSync()
    val submit = endpoint.post
      .in("transfers")
      .in(jsonBody[Transfer])
      .out(statusCode(sttp.model.StatusCode.Created))
      .out(jsonBody[Receipt])
      .serverLogicSuccess[IO] { transfer =>
        ledger.modify { previous =>
          val number: Int = previous.size + 1
          (previous :+ transfer, Receipt(number, transfer.from, transfer.to, transfer.amountCents))
        }
      }
    val app: HttpApp[IO] = Http4sServerInterpreter[IO]().toRoutes(submit).orNotFound

    def send(body: String): IO[(Status, Option[MediaType], String)] = {
      val request: Request[IO] = Request[IO](Method.POST, Uri.unsafeFromString("/transfers"))
        .withEntity(body)
        .putHeaders(`Content-Type`(MediaType.application.json))
      app.run(request).flatMap { response =>
        response.bodyText.compile.string.map { text =>
          (response.status, response.contentType.map(_.mediaType), text)
        }
      }
    }

    val invalidBodies: List[String] = List(
      """{"from":"not-an-account","to":"acct-0027","amountCents":12500}""",
      """{"from":"acct-0017","to":{"value":"acct-0027"},"amountCents":12500}""",
      """{"from":"acct-0017","to":27,"amountCents":12500}"""
    )
    invalidBodies.foreach { body =>
      val (status, _, _) = send(body).timeout(30.seconds).unsafeRunSync()
      assertEquals(Status.BadRequest, status)
      assertEquals(List.empty[Transfer], ledger.get.unsafeRunSync())
    }

    val (status, contentType, body) = send(
      """{"from":"acct-0017","to":"acct-0027","amountCents":12500}"""
    ).timeout(30.seconds).unsafeRunSync()
    val expected: Json = parse(
      """{"number":1,"from":"acct-0017","to":"acct-0027","amountCents":12500}"""
    ).fold(throw _, identity)
    assertEquals(Status.Created, status)
    assertEquals(Some(MediaType.application.json), contentType)
    assertEquals(expected, parse(body).fold(throw _, identity))
    assertEquals(
      List(Transfer(AccountId("acct-0017"), AccountId("acct-0027"), 12500L)),
      ledger.get.unsafeRunSync()
    )
  }
}
