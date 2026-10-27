/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.http4s

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import fs2.Stream
import org.http4s.{Header, HttpApp, HttpRoutes, MediaType, Method, Request, ResponseCookie, Status, Uri, UrlForm}
import org.http4s.dsl.io.*
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.http4s.server.middleware.Logger
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.*

import java.nio.charset.StandardCharsets.UTF_8

/** Exercises request and response logging with credential redaction for partner inventory uploads. */
class InventoryLoggingTest {
  @Test
  @Timeout(60)
  def recordsInventoryReceiptsWithoutLeakingCredentialsOrChangingBodies(): Unit = {
    val partnerKey: String = "private-partner-key-731"
    val bearer: String = "private-bearer-token-731"
    val incomingSession: String = "private-incoming-session-731"
    val outgoingSession: String = "private-outgoing-session-731"
    val payload: String = "sku=book-42&quantity=3"
    val request: Request[IO] = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString("/inventory/receipts")
    ).withBodyStream(
      Stream.emits(payload.getBytes(UTF_8).toSeq).covary[IO].chunkN(4).unchunks
    ).putHeaders(
      `Content-Type`(MediaType.application.`x-www-form-urlencoded`),
      Header.Raw(ci"Authorization", s"Bearer $bearer"),
      Header.Raw(ci"Cookie", s"partner-session=$incomingSession"),
      Header.Raw(ci"X-Partner-Key", partnerKey)
    )

    val checks: IO[Unit] = for {
      inventory <- Ref.of[IO, Map[String, Int]](Map("book-42" -> 10))
      entries <- Ref.of[IO, Vector[String]](Vector.empty)
      routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
        case upload @ POST -> Root / "inventory" / "receipts" =>
          if (!upload.headers.get(ci"X-Partner-Key").exists(_.head.value == partnerKey))
            Forbidden("Unknown inventory partner")
          else for {
            form <- upload.as[UrlForm]
            sku <- IO.fromOption(form.values.get("sku").flatMap(_.headOption))(
              new IllegalArgumentException("Receipt requires a SKU")
            )
            quantity <- IO.fromOption(
              form.values.get("quantity").flatMap(_.headOption).flatMap(_.toIntOption)
            )(new IllegalArgumentException("Receipt requires a quantity"))
            _ <- inventory.update(stock => stock.updated(sku, stock.getOrElse(sku, 0) + quantity))
            response <- Created(s"receipt-731: added $quantity of $sku")
          } yield response.addCookie(ResponseCookie("partner-session", outgoingSession, httpOnly = true))
      }
      application: HttpApp[IO] = Logger.httpApp[IO](
        logHeaders = true,
        logBody = true,
        redactHeadersWhen = name => Logger.defaultRedactHeadersWhen(name) || name == ci"X-Partner-Key",
        logAction = Some(message => entries.update(_ :+ message))
      )(routes.orNotFound)
      response <- application.run(request)
      body <- response.as[String]
      stock <- inventory.get
      // Consuming the response completes both body-aware log actions, without polling or sleeping.
      logged <- entries.get
    } yield {
      assertEquals(Status.Created, response.status)
      assertEquals("receipt-731: added 3 of book-42", body)
      assertEquals(Map("book-42" -> 13), stock)
      assertEquals(Some(outgoingSession), response.cookies.find(_.name == "partner-session").map(_.content))
      assertEquals(2, logged.size)
      assertTrue(logged.exists(line => line.contains("POST") && line.contains("/inventory/receipts")))
      assertTrue(logged.exists(_.contains(payload)))
      assertTrue(logged.exists(line => line.contains("201") && line.contains(body)))
      val combined: String = logged.mkString("\n")
      assertTrue(combined.contains("X-Partner-Key"))
      assertTrue(combined.contains("Authorization"))
      assertTrue(combined.contains("Set-Cookie"))
      for (secret <- List(partnerKey, bearer, incomingSession, outgoingSession)) {
        assertFalse(combined.contains(secret), "Credentials must be absent from inventory diagnostics")
      }
    }

    checks.unsafeRunSync()
  }
}
