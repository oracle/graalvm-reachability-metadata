/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_json_circe_3

import com.sun.net.httpserver.HttpServer
import io.circe.ACursor
import io.circe.Codec as CirceCodec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.HCursor
import io.circe.Json
import io.circe.JsonObject
import io.circe.parser.parse
import io.circe.syntax.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.apispec.openapi.OpenAPI
import sttp.apispec.openapi.circe.*
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
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.json.circe.*
import sttp.tapir.server.jdkhttp.JdkHttpServer

import java.util.concurrent.ConcurrentHashMap

/**
 * Recursive JSON documents: a self-referencing case class with a circe codec and a derived tapir
 * schema that both follow the recursive shape, carried as `jsonBody` on a live JDK HTTP server and
 * documented in OpenAPI as a schema component that references itself.
 *
 * A discussion forum stores whole comment threads, where every comment holds its replies as
 * further comments to any depth. The service accepts a thread, reports its size and depth, serves
 * it back intact, and rejects a thread whose deeply nested reply is malformed.
 */
class DiscussionThreadRecursiveJsonTest {
  import DiscussionThreadRecursiveJsonTest.*

  private var server: HttpServer = null
  private var backend: SttpBackend[Identity, Any] = null
  private var baseUri: Uri = null
  private val forum: Forum = new Forum

  @BeforeEach
  def startServer(): Unit = {
    server = JdkHttpServer()
      .host("localhost")
      .port(0)
      .addEndpoints(forum.serverEndpoints)
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
  def postsAndReadsBackANestedThread(): Unit = {
    val client: SttpClientInterpreter = SttpClientInterpreter()

    val summary: ThreadSummary = client
      .toRequestThrowDecodeFailures(postThread, Some(baseUri))
      .apply(("native-image", SampleThread))
      .send(backend)
      .body
      .fold(error => throw new AssertionError(s"unexpected error $error"), identity)
    assertEquals(ThreadSummary("native-image", 5, 4, List("ana", "bo", "cy", "dee")), summary)

    val fetched: Either[String, Comment] = client
      .toRequestThrowDecodeFailures(getThread, Some(baseUri))
      .apply("native-image")
      .send(backend)
      .body
    assertEquals(Right(SampleThread), fetched)
  }

  @Test
  def servesTheThreadAsNestedJsonObjects(): Unit = {
    forum.store("tapir", SampleThread)

    val raw: Response[Either[String, String]] = basicRequest
      .get(uri"$baseUri/threads/tapir")
      .send(backend)

    assertEquals(StatusCode.Ok, raw.code)
    assertTrue(raw.header("Content-Type").exists(_.startsWith("application/json")))
    val json: Json = parse(raw.body.fold(identity, identity)).fold(throw _, identity)
    val deepest: ACursor = json.hcursor
      .downField("replies").downN(1)
      .downField("replies").downN(0)
      .downField("replies").downN(0)
    assertEquals(Right("dee"), deepest.get[String]("author"))
    assertEquals(Right(List.empty[Json]), deepest.get[List[Json]]("replies"))
    assertEquals(Right(2), json.hcursor.downField("replies").as[List[Json]].map(_.size))
  }

  @Test
  def acceptsAHandWrittenThreadAndRejectsAMalformedDeepReply(): Unit = {
    val accepted: Response[Either[String, String]] = basicRequest
      .post(uri"$baseUri/threads/raw")
      .contentType(MediaType.ApplicationJson)
      .body(
        """{"id":1,"author":"eve","text":"root","replies":[
          |{"id":2,"author":"fay","text":"child","replies":[
          |{"id":3,"author":"eve","text":"grandchild","replies":[]}]}]}""".stripMargin
      )
      .send(backend)
    assertEquals(StatusCode.Ok, accepted.code)
    val summary: Json = parse(accepted.body.fold(identity, identity)).fold(throw _, identity)
    assertEquals(Right(3), summary.hcursor.get[Int]("comments"))
    assertEquals(Right(3), summary.hcursor.get[Int]("depth"))
    assertEquals(Right(List("eve", "fay")), summary.hcursor.get[List[String]]("authors"))

    val rejected: Response[Either[String, String]] = basicRequest
      .post(uri"$baseUri/threads/broken")
      .contentType(MediaType.ApplicationJson)
      .body(
        """{"id":1,"author":"eve","text":"root","replies":[
          |{"id":2,"author":"fay","text":"child","replies":[
          |{"id":"three","author":"eve","text":"bad id","replies":[]}]}]}""".stripMargin
      )
      .send(backend)
    assertEquals(StatusCode.BadRequest, rejected.code)
    assertEquals(None, forum.find("broken"))
  }

  @Test
  def answersAnUnknownThreadWithAJsonlessNotFound(): Unit = {
    val missing: Either[String, Comment] = SttpClientInterpreter()
      .toRequestThrowDecodeFailures(getThread, Some(baseUri))
      .apply("nobody-wrote-this")
      .send(backend)
      .body
    assertEquals(Left("no thread nobody-wrote-this"), missing)
  }

  @Test
  def documentsTheCommentSchemaAsSelfReferencing(): Unit = {
    val docs: OpenAPI = OpenAPIDocsInterpreter().toOpenAPI(List(postThread, getThread), "Forum API", "1.0")
    val root: Json = docs.asJson
    val schemas: ACursor = root.hcursor.downField("components").downField("schemas")

    val replies: ACursor = schemas.downField("Comment").downField("properties").downField("replies")
    assertEquals(Right("array"), replies.get[String]("type"))
    assertEquals(Right("#/components/schemas/Comment"), replies.downField("items").get[String]("$ref"))
    assertEquals(
      Right("#/components/schemas/Comment"),
      root.hcursor
        .downField("paths").downField("/threads/{threadId}").downField("post")
        .downField("requestBody").downField("content").downField("application/json")
        .downField("schema").get[String]("$ref")
    )
    assertEquals(
      Right(List("threadId", "comments", "depth")),
      schemas.downField("ThreadSummary").get[List[String]]("required")
    )
    assertEquals(
      Right(List("id", "author", "text")),
      schemas.downField("Comment").get[List[String]]("required")
    )
  }
}

object DiscussionThreadRecursiveJsonTest {
  final case class Comment(id: Long, author: String, text: String, replies: List[Comment]) {

    def size: Int = 1 + replies.map(_.size).sum

    def depth: Int = 1 + replies.map(_.depth).maxOption.getOrElse(0)

    def authors: List[String] = (author :: replies.flatMap(_.authors)).distinct
  }

  object Comment {
    /** A `def`, not a lazy given: the nested lookup re-enters derivation, which emits a reference. */
    implicit def commentSchema: Schema[Comment] = Schema.derived

    /** Recurses through `this`, so the nested replies never see a half-initialized codec. */
    implicit val commentCodec: CirceCodec.AsObject[Comment] = new CirceCodec.AsObject[Comment] {
      private val repliesEncoder: Encoder[List[Comment]] = Encoder.encodeList(this)
      private val repliesDecoder: Decoder[List[Comment]] = Decoder.decodeList(this)

      override def encodeObject(comment: Comment): JsonObject = JsonObject(
        "id" -> Json.fromLong(comment.id),
        "author" -> Json.fromString(comment.author),
        "text" -> Json.fromString(comment.text),
        "replies" -> repliesEncoder(comment.replies)
      )

      override def apply(cursor: HCursor): Decoder.Result[Comment] =
        for {
          id <- cursor.get[Long]("id")
          author <- cursor.get[String]("author")
          text <- cursor.get[String]("text")
          replies <- cursor.downField("replies").as(repliesDecoder)
        } yield Comment(id, author, text, replies)
    }
  }

  final case class ThreadSummary(threadId: String, comments: Int, depth: Int, authors: List[String])
      derives CirceCodec.AsObject, Schema

  val SampleThread: Comment = Comment(
    1L,
    "ana",
    "Does the forum run on Native Image?",
    List(
      Comment(2L, "bo", "Yes, with reachability metadata.", Nil),
      Comment(
        3L,
        "cy",
        "Even recursive bodies?",
        List(Comment(4L, "ana", "Let us find out.", List(Comment(5L, "dee", "They do.", Nil))))
      )
    )
  )

  val postThread: PublicEndpoint[(String, Comment), Unit, ThreadSummary, Any] = endpoint.post
    .in("threads" / path[String]("threadId"))
    .in(jsonBody[Comment])
    .out(jsonBody[ThreadSummary])

  val getThread: PublicEndpoint[String, String, Comment, Any] = endpoint.get
    .in("threads" / path[String]("threadId"))
    .out(jsonBody[Comment])
    .errorOut(statusCode(StatusCode.NotFound).and(stringBody))

  /** In-memory thread storage behind the two endpoints. */
  final class Forum {
    private val threads: ConcurrentHashMap[String, Comment] = new ConcurrentHashMap[String, Comment]()

    def store(threadId: String, root: Comment): Unit = threads.put(threadId, root)

    def find(threadId: String): Option[Comment] = Option(threads.get(threadId))

    val serverEndpoints = List(
      postThread.handleSuccess { case (threadId, root) =>
        store(threadId, root)
        ThreadSummary(threadId, root.size, root.depth, root.authors)
      },
      getThread.handle(threadId => find(threadId).toRight(s"no thread $threadId"))
    )
  }
}
