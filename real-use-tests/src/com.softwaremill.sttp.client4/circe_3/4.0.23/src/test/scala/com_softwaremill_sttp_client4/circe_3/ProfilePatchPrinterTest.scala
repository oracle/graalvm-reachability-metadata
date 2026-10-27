/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.circe_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.circe.Printer
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.circe.*
import sttp.model.StatusCode

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration.*

/**
 * Exercises request serialization through a caller-supplied circe `Printer` together with
 * `Option`-typed decoding of empty bodies via `asJsonOrFail` and `asJsonAlways`: a profile client
 * sends sparse PATCH documents without null fields and reads absent or no-content replies as `None`.
 */
class ProfilePatchPrinterTest {
  private final case class ProfilePatch(displayName: Option[String], timezone: Option[String], newsletter: Option[Boolean])
  private final case class Profile(user: String, displayName: String, timezone: String, newsletter: Boolean)
  private final case class Problem(title: String, status: Int)

  private var server: HttpServer = null
  private var backend: SyncBackend = null
  private val profiles: ConcurrentHashMap[String, Profile] = new ConcurrentHashMap[String, Profile]()
  private val receivedPatches: ConcurrentHashMap[String, String] = new ConcurrentHashMap[String, String]()

  @BeforeEach
  def startServer(): Unit = {
    profiles.put("ada", Profile("ada", "Ada", "UTC", newsletter = false))
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/profiles/",
      (exchange: HttpExchange) => {
        try {
          val user: String = exchange.getRequestURI.getPath.stripPrefix("/profiles/")
          exchange.getRequestMethod match {
            case "PATCH" =>
              val payload: String = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
              receivedPatches.put(user, payload)
              decode[ProfilePatch](payload) match {
                case Right(patch) if profiles.containsKey(user) =>
                  val current: Profile = profiles.get(user)
                  profiles.put(
                    user,
                    current.copy(
                      displayName = patch.displayName.getOrElse(current.displayName),
                      timezone = patch.timezone.getOrElse(current.timezone),
                      newsletter = patch.newsletter.getOrElse(current.newsletter)
                    )
                  )
                  respond(exchange, 204, "")
                case Right(_) =>
                  respond(exchange, 404, Problem("Unknown profile", 404).asJson.noSpaces)
                case Left(error) =>
                  respond(exchange, 400, Problem(error.getMessage, 400).asJson.noSpaces)
              }
            case "GET" =>
              Option(profiles.get(user)) match {
                case Some(profile) => respond(exchange, 200, profile.asJson.noSpaces)
                case None          => respond(exchange, 404, Problem("Unknown profile", 404).asJson.noSpaces)
              }
            case _ =>
              respond(exchange, 405, "")
          }
        } finally {
          exchange.close()
        }
      }
    )
    server.start()
    backend = DefaultSyncBackend(BackendOptions.connectionTimeout(10.seconds))
  }

  @AfterEach
  def stopServer(): Unit = {
    backend.close()
    server.stop(0)
  }

  @Test
  def sendsSparsePatchesThroughCustomPrinterAndReadsNoContentAsNone(): Unit = {
    given Printer = Printer.noSpaces.copy(dropNullValues = true)

    val patch: ProfilePatch = ProfilePatch(displayName = None, timezone = Some("Europe/London"), newsletter = Some(true))
    val patched: Response[Option[Profile]] = basicRequest
      .patch(profileUri("ada"))
      .body(asJson(patch))
      .response(asJsonOrFail[Option[Profile]])
      .readTimeout(10.seconds)
      .send(backend)

    assertThat(patched.code).isEqualTo(StatusCode.NoContent)
    assertThat(patched.body).isEqualTo(None)
    assertThat(receivedPatches.get("ada")).isEqualTo("""{"timezone":"Europe/London","newsletter":true}""")

    val profile: Option[Profile] = basicRequest
      .get(profileUri("ada"))
      .response(asJsonOrFail[Option[Profile]])
      .readTimeout(10.seconds)
      .send(backend)
      .body
    assertThat(profile).isEqualTo(Some(Profile("ada", "Ada", "Europe/London", newsletter = true)))
  }

  @Test
  def defaultPrinterKeepsNullFieldsInTheRequestBody(): Unit = {
    val patch: ProfilePatch = ProfilePatch(displayName = Some("Countess Ada"), timezone = None, newsletter = None)
    val patched: Response[Either[ResponseException[String], Option[Profile]]] = basicRequest
      .patch(profileUri("ada"))
      .body(asJson(patch))
      .response(asJson[Option[Profile]])
      .readTimeout(10.seconds)
      .send(backend)

    assertThat(patched.body).isEqualTo(Right(None))
    assertThat(receivedPatches.get("ada"))
      .isEqualTo("""{"displayName":"Countess Ada","timezone":null,"newsletter":null}""")
    assertThat(profiles.get("ada")).isEqualTo(Profile("ada", "Countess Ada", "UTC", newsletter = false))
  }

  @Test
  def alwaysVariantDecodesProblemBodiesRegardlessOfStatus(): Unit = {
    val missing: Response[Either[ResponseException.DeserializationException, Problem]] = basicRequest
      .get(profileUri("grace"))
      .response(asJsonAlways[Problem])
      .readTimeout(10.seconds)
      .send(backend)

    assertThat(missing.code).isEqualTo(StatusCode.NotFound)
    assertThat(missing.body).isEqualTo(Right(Problem("Unknown profile", 404)))

    val rejected: Response[Either[ResponseException.DeserializationException, Problem]] = basicRequest
      .patch(profileUri("ada"))
      .body("""{"newsletter":"sometimes"}""")
      .contentType("application/json")
      .response(asJsonAlways[Problem])
      .readTimeout(10.seconds)
      .send(backend)

    assertThat(rejected.code).isEqualTo(StatusCode.BadRequest)
    assertThat(rejected.body.map(_.status)).isEqualTo(Right(400))

    val notAProblem: Response[Either[ResponseException.DeserializationException, Problem]] = basicRequest
      .get(profileUri("ada"))
      .response(asJsonAlways[Problem])
      .readTimeout(10.seconds)
      .send(backend)

    notAProblem.body match {
      case Left(ResponseException.DeserializationException(body, _, metadata)) =>
        assertThat(metadata.code).isEqualTo(StatusCode.Ok)
        assertThat(body).contains("\"user\":\"ada\"")
      case other =>
        throw new AssertionError(s"Expected a profile body to fail decoding as a problem, got $other")
    }
  }

  private def profileUri(user: String) = uri"http://127.0.0.1:${server.getAddress.getPort}/profiles/$user"

  private def respond(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes: Array[Byte] = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, if (bytes.isEmpty) -1 else bytes.length.toLong)
    if (bytes.nonEmpty) {
      exchange.getResponseBody.write(bytes)
    }
  }
}
