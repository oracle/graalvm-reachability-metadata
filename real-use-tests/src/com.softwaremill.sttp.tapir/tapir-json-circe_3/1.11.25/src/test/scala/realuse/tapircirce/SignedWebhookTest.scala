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
import org.http4s.Header
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
import org.typelevel.ci.CIString
import sttp.tapir.*
import sttp.tapir.json.circe.TapirJsonCirce
import sttp.tapir.server.http4s.Http4sServerInterpreter

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.concurrent.duration.*

/** Exercises Circe raw JSON body capture for signature-verified payment webhooks. */
class SignedWebhookTest extends TapirJsonCirce {
  case class Payment(invoiceId: String, amountCents: Int) derives Codec.AsObject, Schema

  private def signature(body: String): String = {
    val mac: Mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec("test-webhook-signing-secret".getBytes(UTF_8), "HmacSHA256"))
    HexFormat.of().formatHex(mac.doFinal(body.getBytes(UTF_8)))
  }

  @Test
  @Timeout(60)
  def verifiesOriginalPayloadBeforeRecordingPayment(): Unit = {
    val payments: Ref[IO, List[Payment]] = Ref.of[IO, List[Payment]](Nil).unsafeRunSync()
    val webhook = endpoint.post
      .in("payment-webhooks")
      .in(jsonBodyWithRaw[Payment])
      .in(header[String]("X-Webhook-Signature"))
      .errorOut(statusCode(sttp.model.StatusCode.Unauthorized))
      .errorOut(stringBody)
      .out(stringBody)
      .serverLogic[IO] { case (raw, payment, suppliedSignature) =>
        val valid: Boolean = MessageDigest.isEqual(
          signature(raw).getBytes(UTF_8),
          suppliedSignature.getBytes(UTF_8)
        )
        if (valid) {
          payments.update(_ :+ payment).as(Right(s"Recorded ${payment.invoiceId}: ${payment.amountCents}"))
        } else {
          IO.pure(Left("Invalid webhook signature"))
        }
      }
    val app: HttpApp[IO] = Http4sServerInterpreter[IO]().toRoutes(webhook).orNotFound

    def deliver(body: String, signed: String): (Status, String) = {
      val request: Request[IO] = Request[IO](Method.POST, Uri.unsafeFromString("/payment-webhooks"))
        .withEntity(body)
        .putHeaders(
          `Content-Type`(MediaType.application.json),
          Header.Raw(CIString("X-Webhook-Signature"), signed)
        )
      (for {
        response <- app.run(request)
        text <- response.bodyText.compile.string
      } yield (response.status, text)).timeout(30.seconds).unsafeRunSync()
    }

    val original: String = """{
      "invoiceId" : "invoice-42",
      "amountCents" : 2750
    }
    """
    val signed: String = signature(original)
    assertEquals((Status.Ok, "Recorded invoice-42: 2750"), deliver(original, signed))
    assertEquals(List(Payment("invoice-42", 2750)), payments.get.unsafeRunSync())

    // The same JSON value with different whitespace must not reuse the signature.
    val reformatted: String = """{"invoiceId":"invoice-42","amountCents":2750}"""
    assertEquals((Status.Unauthorized, "Invalid webhook signature"), deliver(reformatted, signed))

    val tampered: String = original.replace("2750", "9750")
    assertEquals((Status.Unauthorized, "Invalid webhook signature"), deliver(tampered, signed))
    assertEquals(List(Payment("invoice-42", 2750)), payments.get.unsafeRunSync())
  }
}
