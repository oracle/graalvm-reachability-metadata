/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_http4s_server_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.decode
import org.http4s.{Header, HttpApp, Method, Request, Response}
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.CIString
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises response content negotiation using alternative bodies in Tapir's http4s interpreter. */
class NegotiatedInvoiceReceiptTest {
  final case class Invoice(number: String, totalCents: Int)

  private given Encoder[Invoice] = deriveEncoder[Invoice]
  private given Decoder[Invoice] = deriveDecoder[Invoice]
  private given Schema[Invoice] = Schema.derived[Invoice]

  @Test
  @Timeout(60)
  def servesTheSameInvoiceToJsonClientsAndPlainTextConsumers(): Unit = {
    val invoice: Invoice = Invoice("INV-42", 3750)
    val plainText: EndpointIO.Body[String, Invoice] = stringBody.mapDecode[Invoice](parseReceipt) {
      value => s"${value.number}:${value.totalCents}"
    }
    val receipt: PublicEndpoint[Unit, Unit, Invoice, Any] = endpoint.get
      .in("invoices" / "INV-42" / "receipt")
      .out(oneOfBody(jsonBody[Invoice], plainText))
    val app: HttpApp[IO] = Http4sServerInterpreter[IO]().toRoutes(
      receipt.serverLogicSuccess[IO](_ => IO.pure(invoice))
    ).orNotFound

    val scenario: IO[Unit] = for {
      json <- app.run(receiptRequest(Some("application/json")))
      jsonBody <- json.as[String]
      _ <- IO {
        assertEquals(200, json.status.code)
        assertContentType(json, "application/json")
        assertEquals(Right(invoice), decode[Invoice](jsonBody))
      }
      text <- app.run(receiptRequest(Some("text/plain")))
      textBody <- text.as[String]
      _ <- IO {
        assertEquals(200, text.status.code)
        assertContentType(text, "text/plain")
        assertEquals("INV-42:3750", textBody)
      }
      preferred <- app.run(receiptRequest(Some("application/json;q=0.3, text/plain;q=0.9")))
      preferredBody <- preferred.as[String]
      _ <- IO {
        assertEquals(200, preferred.status.code)
        assertContentType(preferred, "text/plain")
        assertEquals("INV-42:3750", preferredBody)
      }
      default <- app.run(receiptRequest(None))
      defaultBody <- default.as[String]
      _ <- IO {
        assertEquals(200, default.status.code)
        assertContentType(default, "application/json")
        assertEquals(Right(invoice), decode[Invoice](defaultBody))
      }
      unsupported <- app.run(receiptRequest(Some("application/pdf")))
      _ <- unsupported.body.compile.drain
      _ <- IO(assertEquals(406, unsupported.status.code))
    } yield ()

    scenario.timeout(45.seconds).unsafeRunSync()
  }

  private def parseReceipt(value: String): DecodeResult[Invoice] = {
    value.split(":", -1).toList match {
      case number :: total :: Nil if number.nonEmpty =>
        total.toIntOption match {
          case Some(cents) => DecodeResult.Value(Invoice(number, cents))
          case None => DecodeResult.Error(value, new IllegalArgumentException("Invalid receipt total"))
        }
      case _ => DecodeResult.Error(value, new IllegalArgumentException("Invalid receipt format"))
    }
  }

  private def receiptRequest(accept: Option[String]): Request[IO] = {
    val request: Request[IO] = Request[IO](Method.GET, uri"/invoices/INV-42/receipt")
    accept.fold(request)(value => request.putHeaders(Header.Raw(CIString("Accept"), value)))
  }

  private def assertContentType(response: Response[IO], expected: String): Unit =
    assertTrue(response.headers.headers.exists { header =>
      header.name == CIString("Content-Type") && header.value.takeWhile(_ != ';') == expected
    }, s"Expected $expected representation")
}
