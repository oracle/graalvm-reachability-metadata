/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_json_circe_3

import com.sun.net.httpserver.HttpServer
import io.circe.Codec as CirceCodec
import io.circe.Json
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
import sttp.tapir.json.circe.*
import sttp.tapir.server.jdkhttp.JdkHttpServer

import java.util.concurrent.ConcurrentHashMap

/**
 * Optional JSON request and response bodies: `jsonBody[Option[T]]` endpoints where an empty body
 * stands for `None`, on a live JDK HTTP server called through the sttp client interpreter.
 *
 * A note-drafting service saves a draft when the client sends one as JSON, discards it when the
 * client sends an empty body, and answers a draft lookup with the stored draft or with an empty
 * body when there is none. A malformed non-empty body is still rejected.
 */
class NoteDraftOptionalJsonBodyTest {
  import NoteDraftOptionalJsonBodyTest.*

  private var server: HttpServer = null
  private var backend: SttpBackend[Identity, Any] = null
  private var baseUri: Uri = null
  private val drafts: DraftStore = new DraftStore

  @BeforeEach
  def startServer(): Unit = {
    server = JdkHttpServer()
      .host("localhost")
      .port(0)
      .addEndpoints(drafts.serverEndpoints)
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
  def savesADraftAndDiscardsItWithAnEmptyBody(): Unit = {
    val client: SttpClientInterpreter = SttpClientInterpreter()
    val draft: Draft = Draft("Groceries", "milk, eggs", List("home", "weekly"))

    val saved: SaveOutcome = client
      .toRequestThrowDecodeFailures(saveDraft, Some(baseUri))
      .apply(("note-1", Some(draft)))
      .send(backend)
      .body
      .fold(error => throw new AssertionError(s"unexpected error $error"), identity)
    assertEquals(SaveOutcome("note-1", "saved"), saved)
    assertEquals(Right(Some(draft)), loadOf("note-1"))

    val discarded: SaveOutcome = client
      .toRequestThrowDecodeFailures(saveDraft, Some(baseUri))
      .apply(("note-1", None))
      .send(backend)
      .body
      .fold(error => throw new AssertionError(s"unexpected error $error"), identity)
    assertEquals(SaveOutcome("note-1", "discarded"), discarded)
    assertEquals(Right(None), loadOf("note-1"))
  }

  @Test
  def answersAMissingDraftWithAnEmptyBody(): Unit = {
    val raw: Response[Either[String, String]] = basicRequest
      .get(uri"$baseUri/drafts/never-written")
      .send(backend)

    assertEquals(StatusCode.Ok, raw.code)
    assertEquals("", raw.body.fold(identity, identity))
    assertEquals(Right(None), loadOf("never-written"))
  }

  @Test
  def acceptsARawJsonDraftAndAnEmptyRawBody(): Unit = {
    val savedRaw: Response[Either[String, String]] = basicRequest
      .put(uri"$baseUri/drafts/note-2")
      .contentType(MediaType.ApplicationJson)
      .body("""{"title":"Ideas","text":"tapir on native","tags":[]}""")
      .send(backend)
    assertEquals(StatusCode.Ok, savedRaw.code)
    val json: Json = parse(savedRaw.body.fold(identity, identity)).fold(throw _, identity)
    assertEquals(Right("saved"), json.hcursor.get[String]("action"))

    val fetched: Response[Either[String, String]] = basicRequest
      .get(uri"$baseUri/drafts/note-2")
      .send(backend)
    assertTrue(fetched.header("Content-Type").exists(_.startsWith("application/json")))
    val fetchedJson: Json = parse(fetched.body.fold(identity, identity)).fold(throw _, identity)
    assertEquals(Right("tapir on native"), fetchedJson.hcursor.get[String]("text"))

    val discardedRaw: Response[Either[String, String]] = basicRequest
      .put(uri"$baseUri/drafts/note-2")
      .send(backend)
    assertEquals(StatusCode.Ok, discardedRaw.code)
    assertEquals(Right(None), loadOf("note-2"))
  }

  @Test
  def rejectsAMalformedNonEmptyDraft(): Unit = {
    drafts.put("note-3", Draft("Keep", "unchanged", Nil))

    val raw: Response[Either[String, String]] = basicRequest
      .put(uri"$baseUri/drafts/note-3")
      .contentType(MediaType.ApplicationJson)
      .body("""{"title":"Broken","tags":"not-a-list"}""")
      .send(backend)

    assertEquals(StatusCode.BadRequest, raw.code)
    assertEquals(Right(Some(Draft("Keep", "unchanged", Nil))), loadOf("note-3"))
  }

  private def loadOf(noteId: String): Either[Unit, Option[Draft]] =
    SttpClientInterpreter()
      .toRequestThrowDecodeFailures(loadDraft, Some(baseUri))
      .apply(noteId)
      .send(backend)
      .body
}

object NoteDraftOptionalJsonBodyTest {
  final case class Draft(title: String, text: String, tags: List[String]) derives CirceCodec.AsObject, Schema

  final case class SaveOutcome(noteId: String, action: String) derives CirceCodec.AsObject, Schema

  val saveDraft: PublicEndpoint[(String, Option[Draft]), Unit, SaveOutcome, Any] = endpoint.put
    .in("drafts" / path[String]("noteId"))
    .in(jsonBody[Option[Draft]])
    .out(jsonBody[SaveOutcome])

  val loadDraft: PublicEndpoint[String, Unit, Option[Draft], Any] = endpoint.get
    .in("drafts" / path[String]("noteId"))
    .out(jsonBody[Option[Draft]])

  /** In-memory draft storage behind the two endpoints. */
  final class DraftStore {
    private val drafts: ConcurrentHashMap[String, Draft] = new ConcurrentHashMap[String, Draft]()

    def put(noteId: String, draft: Draft): Unit = drafts.put(noteId, draft)

    val serverEndpoints = List(
      saveDraft.handleSuccess {
        case (noteId, Some(draft)) =>
          drafts.put(noteId, draft)
          SaveOutcome(noteId, "saved")
        case (noteId, None) =>
          drafts.remove(noteId)
          SaveOutcome(noteId, "discarded")
      },
      loadDraft.handleSuccess(noteId => Option(drafts.get(noteId)))
    )
  }
}
