/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_json_circe_3

import com.sun.net.httpserver.HttpServer
import io.circe.Json
import io.circe.JsonObject
import io.circe.derivation.Configuration as CirceConfiguration
import io.circe.derivation.ConfiguredCodec
import io.circe.parser.parse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client3.HttpClientSyncBackend
import sttp.client3.Identity
import sttp.client3.Response
import sttp.client3.SttpBackend
import sttp.client3.basicRequest
import sttp.model.MediaType
import sttp.model.StatusCode
import sttp.model.Uri
import sttp.model.Uri.UriContext
import sttp.tapir.*
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.generic.Configuration as TapirConfiguration
import sttp.tapir.json.circe.*
import sttp.tapir.server.jdkhttp.JdkHttpServer

import java.util.concurrent.ConcurrentHashMap

/**
 * Snake-case wire naming and defaults for omitted JSON fields: circe `ConfiguredCodec` bodies with
 * snake_case member names and case-class defaults, paired with a tapir schema configured for the
 * same snake_case field names.
 *
 * A user-settings service stores per-user preferences. Clients send only the fields they care
 * about in snake_case; the server fills in defaults for the rest and answers with the full
 * settings document in snake_case, and the client built from the same endpoints reads it back as
 * Scala values over real HTTP on a JDK HTTP server.
 */
class ProfileSettingsSnakeCaseDefaultsTest {
  import ProfileSettingsSnakeCaseDefaultsTest.*

  private var server: HttpServer = null
  private var backend: SttpBackend[Identity, Any] = null
  private var baseUri: Uri = null
  private val store: SettingsStore = new SettingsStore

  @BeforeEach
  def startServer(): Unit = {
    server = JdkHttpServer()
      .host("localhost")
      .port(0)
      .addEndpoints(store.serverEndpoints)
      .start()
    backend = HttpClientSyncBackend()
    baseUri = uri"http://localhost:${server.getAddress.getPort}"
  }

  @AfterEach
  def stopServer(): Unit = {
    if (backend != null) backend.close()
    if (server != null) server.stop(0)
  }

  @Test
  def fillsDefaultsForFieldsTheClientOmits(): Unit = {
    val raw: Response[Either[String, String]] = basicRequest
      .put(uri"$baseUri/users/ada/settings")
      .contentType(MediaType.ApplicationJson)
      .body("""{"display_name":"Ada L.","email_opt_in":true}""")
      .send(backend)

    assertEquals(StatusCode.Ok, raw.code)
    assertEquals(
      Some(UserSettings("Ada L.", emailOptIn = true, "light", 20, None)),
      store.find("ada")
    )

    val document: JsonObject = parse(raw.body.fold(identity, identity))
      .fold(throw _, identity)
      .asObject
      .getOrElse(throw new AssertionError(raw.body.toString))
    assertEquals(
      List("display_name", "email_opt_in", "theme", "items_per_page", "time_zone"),
      document.keys.toList
    )
    assertEquals(Some(Json.fromString("light")), document("theme"))
    assertEquals(Some(Json.fromInt(20)), document("items_per_page"))
    assertEquals(Some(Json.Null), document("time_zone"))
  }

  @Test
  def roundTripsSettingsThroughTheClientInterpreter(): Unit = {
    val client: SttpClientInterpreter = SttpClientInterpreter()
    val wanted: UserSettings = UserSettings("Grace H.", emailOptIn = false, "dark", 50, Some("America/New_York"))

    val saved: Either[SettingsError, UserSettings] = client
      .toRequestThrowDecodeFailures(putSettings, Some(baseUri))
      .apply(("grace", wanted))
      .send(backend)
      .body
    assertEquals(Right(wanted), saved)

    val loaded: Either[SettingsError, UserSettings] = client
      .toRequestThrowDecodeFailures(getSettings, Some(baseUri))
      .apply("grace")
      .send(backend)
      .body
    assertEquals(Right(wanted), loaded)

    val missing: Response[Either[SettingsError, UserSettings]] = client
      .toRequestThrowDecodeFailures(getSettings, Some(baseUri))
      .apply("nobody")
      .send(backend)
    assertEquals(StatusCode.NotFound, missing.code)
    assertEquals(Left(SettingsError("unknown_user", "no settings stored for nobody")), missing.body)
  }

  @Test
  def readsTheErrorBodyWithSnakeCaseKeys(): Unit = {
    val raw: Response[Either[String, String]] = basicRequest
      .put(uri"$baseUri/users/eve/settings")
      .contentType(MediaType.ApplicationJson)
      .body("""{"display_name":"Eve","items_per_page":500}""")
      .send(backend)

    assertEquals(StatusCode.UnprocessableEntity, raw.code)
    val error: Json = parse(raw.body.fold(identity, identity)).fold(throw _, identity)
    assertEquals(Right("page_size_too_large"), error.hcursor.get[String]("error_code"))
    assertEquals(Right("items_per_page must be at most 100"), error.hcursor.get[String]("detail_message"))
    assertEquals(None, store.find("eve"))
  }

  @Test
  def rejectsCamelCaseKeysForARequiredField(): Unit = {
    val raw: Response[Either[String, String]] = basicRequest
      .put(uri"$baseUri/users/bob/settings")
      .contentType(MediaType.ApplicationJson)
      .body("""{"displayName":"Bob","emailOptIn":true}""")
      .send(backend)

    assertEquals(StatusCode.BadRequest, raw.code)
    assertEquals(None, store.find("bob"))
  }

  @Test
  def declaresTheSnakeCaseFieldNamesInTheEndpointSchema(): Unit = {
    val schema: Schema[UserSettings] = summon[Schema[UserSettings]]

    schema.schemaType match {
      case product: SchemaType.SProduct[UserSettings] =>
        assertEquals(
          List("display_name", "email_opt_in", "theme", "items_per_page", "time_zone"),
          product.fields.map(_.name.encodedName)
        )
        assertTrue(product.required.map(_.encodedName).contains("display_name"))
        assertTrue(!product.required.map(_.encodedName).contains("time_zone"))
      case other =>
        throw new AssertionError(s"expected a product schema, got $other")
    }
  }
}

object ProfileSettingsSnakeCaseDefaultsTest {
  given CirceConfiguration = CirceConfiguration.default.withSnakeCaseMemberNames.withDefaults

  given TapirConfiguration = TapirConfiguration.default.withSnakeCaseMemberNames

  final case class UserSettings(
      displayName: String,
      emailOptIn: Boolean = false,
      theme: String = "light",
      itemsPerPage: Int = 20,
      timeZone: Option[String] = None
  ) derives ConfiguredCodec, Schema

  final case class SettingsError(errorCode: String, detailMessage: String) derives ConfiguredCodec, Schema

  val settingsErrors: EndpointOutput.OneOf[SettingsError, SettingsError] = oneOf[SettingsError](
    oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[SettingsError]) { case SettingsError(code, _) =>
      code == "unknown_user"
    },
    oneOfDefaultVariant(statusCode(StatusCode.UnprocessableEntity).and(jsonBody[SettingsError]))
  )

  val putSettings: PublicEndpoint[(String, UserSettings), SettingsError, UserSettings, Any] = endpoint.put
    .in("users" / path[String]("userId") / "settings")
    .in(jsonBody[UserSettings])
    .out(jsonBody[UserSettings])
    .errorOut(settingsErrors)

  val getSettings: PublicEndpoint[String, SettingsError, UserSettings, Any] = endpoint.get
    .in("users" / path[String]("userId") / "settings")
    .out(jsonBody[UserSettings])
    .errorOut(settingsErrors)

  /** In-memory settings storage that enforces the page-size limit before saving. */
  final class SettingsStore {
    private val settings: ConcurrentHashMap[String, UserSettings] = new ConcurrentHashMap[String, UserSettings]()

    def find(userId: String): Option[UserSettings] = Option(settings.get(userId))

    val serverEndpoints = List(
      putSettings.handle((userId, wanted) => save(userId, wanted)),
      getSettings.handle(userId =>
        find(userId).toRight(SettingsError("unknown_user", s"no settings stored for $userId"))
      )
    )

    private def save(userId: String, wanted: UserSettings): Either[SettingsError, UserSettings] =
      if (wanted.itemsPerPage > SettingsStore.MaxItemsPerPage)
        Left(SettingsError("page_size_too_large", s"items_per_page must be at most ${SettingsStore.MaxItemsPerPage}"))
      else {
        settings.put(userId, wanted)
        Right(wanted)
      }
  }

  object SettingsStore {
    val MaxItemsPerPage: Int = 100
  }
}
